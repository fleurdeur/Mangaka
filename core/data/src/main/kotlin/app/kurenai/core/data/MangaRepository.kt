package app.kurenai.core.data

import app.kurenai.core.common.AppEvent
import app.kurenai.core.common.AppEventBus
import app.kurenai.core.database.dao.ChapterDao
import app.kurenai.core.database.dao.HistoryDao
import app.kurenai.core.database.dao.MangaDao
import app.kurenai.core.database.entity.HistoryEntity
import app.kurenai.core.datastore.SettingsStore
import app.kurenai.core.model.Manga
import app.kurenai.core.model.MangaChapter
import app.kurenai.core.model.MangaPage
import app.kurenai.core.model.SearchFilters
import app.kurenai.core.source.MangaSource
import app.kurenai.core.source.SourceRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.UnknownHostException
import java.util.LinkedHashMap
import javax.inject.Inject
import javax.inject.Singleton

data class SourceSearchChunk(
    val sourceId: String,
    val sourceName: String,
    val results: List<Manga>,
    val error: String? = null,
)

private data class SearchCacheKey(
    val sourceId: String,
    val filters: SearchFilters,
    val offset: Int,
    val limit: Int,
)

private data class SearchCacheEntry(
    val savedAt: Long,
    val results: List<Manga>,
)

@Singleton
class MangaRepository @Inject constructor(
    private val registry: SourceRegistry,
    private val sourceRepository: SourceRepository,
    private val mangaDao: MangaDao,
    private val chapterDao: ChapterDao,
    private val historyDao: HistoryDao,
    private val settingsStore: SettingsStore,
    private val eventBus: AppEventBus,
) {
    private val searchCache = object : LinkedHashMap<SearchCacheKey, SearchCacheEntry>(96, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<SearchCacheKey, SearchCacheEntry>?): Boolean =
            size > 160
    }
    private val searchCacheLock = Any()

    private suspend fun <T> withDomainRetry(source: MangaSource, block: suspend () -> T): T {
        return try {
            block()
        } catch (t: Throwable) {
            if (t.isHostResolutionFailure() && runCatching { source.tryAlternateDomain() }.getOrDefault(false)) {
                block()
            } else {
                throw t
            }
        }
    }

    private fun Throwable.isHostResolutionFailure(): Boolean =
        generateSequence(this) { it.cause }.any { cause ->
            cause is UnknownHostException ||
                cause.message?.contains("unable to resolve host", ignoreCase = true) == true ||
                cause.message?.contains("no address associated", ignoreCase = true) == true ||
                cause.message?.contains("nodename nor servname", ignoreCase = true) == true
        }

    fun observeFavorites(): Flow<List<Manga>> = mangaDao.observeFavorites().map { list -> list.map { it.toModel() } }

    fun observeCachedManga(): Flow<List<Manga>> = mangaDao.observeFavorites().map { list -> list.map { it.toModel() } }

    suspend fun manga(id: String): Manga? = mangaDao.get(id)?.toModel()

    suspend fun search(sourceId: String, filters: SearchFilters, offset: Int, limit: Int): List<Manga> =
        withContext(Dispatchers.IO) {
            val source = sourceOf(sourceId) ?: return@withContext emptyList()
            val key = SearchCacheKey(sourceId, filters, offset, limit)
            val now = System.currentTimeMillis()
            synchronized(searchCacheLock) {
                searchCache[key]?.let { cached ->
                    if (now - cached.savedAt < 5 * 60_000L) return@withContext cached.results
                    searchCache.remove(key)
                }
            }
            val started = System.currentTimeMillis()
            try {
                val results = withDomainRetry(source) { source.search(filters, offset, limit) }
                sourceRepository.recordSuccess(sourceId, System.currentTimeMillis() - started)
                synchronized(searchCacheLock) {
                    searchCache[key] = SearchCacheEntry(now, results)
                }
                results
            } catch (t: Throwable) {
                sourceRepository.recordFailure(sourceId, t.message ?: t::class.simpleName.orEmpty())
                synchronized(searchCacheLock) { searchCache.keys.removeAll { it.sourceId == sourceId } }
                throw t
            }
        }

    fun searchAll(filters: SearchFilters, limitPerSource: Int = 24, maxSources: Int = 30): Flow<SourceSearchChunk> = channelFlow {
        val sources = sourceRepository.searchCandidates(maxSources)
        // Ten independent hosts in flight. Per-host politeness is enforced by the
        // HTTP layer, so this improves aggregate latency without hammering any one
        // source.
        val gate = Semaphore(10)
        coroutineScope {
            sources.map { source ->
                async(Dispatchers.IO) {
                    gate.withPermit {
                        val result = withTimeoutOrNull(15_000L) {
                            runCatching { search(source.info.id, filters, 0, limitPerSource) }
                        }
                        if (result == null) {
                            sourceRepository.recordFailure(source.info.id, "Search timed out")
                        }
                        val value = result?.getOrNull()
                        val error = result?.exceptionOrNull()?.message
                            ?: if (result == null) "Search timed out" else null
                        send(
                            SourceSearchChunk(
                                source.info.id,
                                source.info.name,
                                value.orEmpty(),
                                error,
                            ),
                        )
                    }
                }
            }.awaitAll()
        }
    }

    suspend fun details(manga: Manga, refresh: Boolean = true): Manga = withContext(Dispatchers.IO) {
        val source = sourceOf(manga.sourceId) ?: return@withContext manga
        val remote = if (refresh) {
            val started = System.currentTimeMillis()
            try {
                withDomainRetry(source) { source.details(manga) }
                    .also { sourceRepository.recordSuccess(manga.sourceId, System.currentTimeMillis() - started) }
            } catch (t: Throwable) {
                sourceRepository.recordFailure(manga.sourceId, t.message ?: "details failed")
                mangaDao.get(manga.id)?.toModel() ?: manga
            }
        } else {
            mangaDao.get(manga.id)?.toModel() ?: manga
        }
        persist(remote)
        remote
    }

    suspend fun chapters(manga: Manga): List<MangaChapter> = withContext(Dispatchers.IO) {
        val source = sourceOf(manga.sourceId)
        val remote = if (source != null) {
            val started = System.currentTimeMillis()
            try {
                withDomainRetry(source) { source.chapters(manga) }.also {
                    sourceRepository.recordSuccess(manga.sourceId, System.currentTimeMillis() - started)
                }
            } catch (t: Throwable) {
                sourceRepository.recordFailure(manga.sourceId, t.message ?: "chapters failed")
                emptyList()
            }
        } else {
            emptyList()
        }
        val local = chapterDao.getForManga(manga.id).associateBy { it.id }
        val merged = if (remote.isEmpty()) local.values.map { it.toModel() } else remote.map { chapter ->
            chapter.copy(
                isRead = local[chapter.id]?.isRead ?: chapter.isRead,
                isBookmarked = local[chapter.id]?.isBookmarked ?: chapter.isBookmarked,
                isDownloaded = local[chapter.id]?.downloaded ?: chapter.isDownloaded,
            )
        }
        if (merged.isNotEmpty()) chapterDao.upsertAll(merged.map { it.toEntity() })
        merged.sortedByDescending { it.number }
    }

    suspend fun pages(chapter: MangaChapter): List<MangaPage> = withContext(Dispatchers.IO) {
        val source = sourceOf(chapter.sourceId) ?: return@withContext emptyList()
        val started = System.currentTimeMillis()
        try {
            withDomainRetry(source) { source.pages(chapter) }.also {
                sourceRepository.recordSuccess(chapter.sourceId, System.currentTimeMillis() - started)
            }
        } catch (t: Throwable) {
            sourceRepository.recordFailure(chapter.sourceId, t.message ?: "pages failed")
            emptyList()
        }
    }

    suspend fun filterOptions(sourceId: String) = withContext(Dispatchers.IO) {
        runCatching { sourceOf(sourceId)?.filterOptions() }.getOrNull()
    }

    suspend fun toggleFavorite(manga: Manga) {
        val existing = mangaDao.get(manga.id)
        val newFavorite = !(existing?.isFavorite ?: manga.isFavorite)
        mangaDao.upsert(manga.copy(isFavorite = newFavorite).toEntity(existing?.addedAt ?: System.currentTimeMillis(), newFavorite))
        eventBus.emit(AppEvent.LibraryChanged)
    }

    suspend fun setFavorite(manga: Manga, favorite: Boolean) {
        val existing = mangaDao.get(manga.id)
        mangaDao.upsert(manga.toEntity(existing?.addedAt ?: System.currentTimeMillis(), favorite))
        eventBus.emit(AppEvent.LibraryChanged)
    }

    suspend fun saveProgress(manga: Manga, chapter: MangaChapter, page: Int, totalPages: Int) {
        persist(manga)
        chapterDao.upsert(chapter.copy(pageCount = totalPages).toEntity())
        historyDao.upsert(
            HistoryEntity(
                mangaId = manga.id,
                chapterId = chapter.id,
                page = page,
                totalPages = totalPages,
                readAt = System.currentTimeMillis(),
            ),
        )
        mangaDao.updateLastRead(manga.id, System.currentTimeMillis())
        eventBus.emit(AppEvent.HistoryChanged)
    }

    suspend fun markRead(chapter: MangaChapter, read: Boolean = true) {
        chapterDao.setRead(chapter.id, read)
        eventBus.emit(AppEvent.LibraryChanged)
    }

    suspend fun markDownloaded(chapter: MangaChapter, downloaded: Boolean, pages: Int) {
        chapterDao.setDownloaded(chapter.id, downloaded, pages)
    }

    suspend fun persist(manga: Manga) {
        val existing = mangaDao.get(manga.id)
        mangaDao.upsert(manga.toEntity(existing?.addedAt ?: 0L, existing?.isFavorite ?: manga.isFavorite))
    }

    suspend fun sourceOf(sourceId: String): MangaSource? = registry.source(sourceId)

    suspend fun sourceInfo(sourceId: String) = registry.source(sourceId)?.info

    suspend fun historyFor(mangaId: String) = historyDao.forManga(mangaId)

    suspend fun updateNotification(manga: Manga, chapter: MangaChapter, newChapter: MangaChapter?) {
        // reserved for cross-source best-chapter selection
    }

    suspend fun firstUnread(mangaId: String): MangaChapter? = chapterDao.firstUnread(mangaId)?.toModel()

    suspend fun chapter(id: String): MangaChapter? = chapterDao.get(id)?.toModel()
}
