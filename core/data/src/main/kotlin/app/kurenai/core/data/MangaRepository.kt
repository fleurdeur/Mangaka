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
import javax.inject.Inject
import javax.inject.Singleton

data class SourceSearchChunk(
    val sourceId: String,
    val sourceName: String,
    val results: List<Manga>,
    val error: String? = null,
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
    fun observeFavorites(): Flow<List<Manga>> = mangaDao.observeFavorites().map { list -> list.map { it.toModel() } }

    fun observeCachedManga(): Flow<List<Manga>> = mangaDao.observeFavorites().map { list -> list.map { it.toModel() } }

    suspend fun manga(id: String): Manga? = mangaDao.get(id)?.toModel()

    suspend fun search(sourceId: String, filters: SearchFilters, offset: Int, limit: Int): List<Manga> {
        val source = sourceOf(sourceId) ?: return emptyList()
        val started = System.currentTimeMillis()
        return try {
            val results = source.search(filters, offset, limit)
            sourceRepository.recordSuccess(sourceId, System.currentTimeMillis() - started)
            results
        } catch (t: Throwable) {
            sourceRepository.recordFailure(sourceId, t.message ?: t::class.simpleName.orEmpty())
            throw t
        }
    }

    fun searchAll(filters: SearchFilters, limitPerSource: Int = 24, maxSources: Int = 24): Flow<SourceSearchChunk> = channelFlow {
        val sources = sourceRepository.visibleSources().take(maxSources)
        val gate = Semaphore(6)
        coroutineScope {
            sources.map { source ->
                async(Dispatchers.IO) {
                    gate.withPermit {
                        val started = System.currentTimeMillis()
                        val result = withTimeoutOrNull(25_000L) {
                            runCatching { source.search(filters, 0, limitPerSource) }
                        }
                        val latency = System.currentTimeMillis() - started
                        val value = result?.getOrNull()
                        if (value != null) {
                            sourceRepository.recordSuccess(source.info.id, latency)
                        } else if (result != null) {
                            sourceRepository.recordFailure(source.info.id, result.exceptionOrNull()?.message ?: "timeout")
                        }
                        send(
                            SourceSearchChunk(
                                source.info.id,
                                source.info.name,
                                value.orEmpty(),
                                result?.exceptionOrNull()?.message,
                            ),
                        )
                    }
                }
            }.awaitAll()
        }
    }

    suspend fun details(manga: Manga, refresh: Boolean = true): Manga {
        val source = sourceOf(manga.sourceId) ?: return manga
        val remote = if (refresh) {
            val started = System.currentTimeMillis()
            try {
                source.details(manga).also { sourceRepository.recordSuccess(manga.sourceId, System.currentTimeMillis() - started) }
            } catch (t: Throwable) {
                sourceRepository.recordFailure(manga.sourceId, t.message ?: "details failed")
                mangaDao.get(manga.id)?.toModel() ?: manga
            }
        } else {
            mangaDao.get(manga.id)?.toModel() ?: manga
        }
        persist(remote)
        return remote
    }

    suspend fun chapters(manga: Manga): List<MangaChapter> = withContext(Dispatchers.IO) {
        val source = sourceOf(manga.sourceId)
        val remote = if (source != null) {
            val started = System.currentTimeMillis()
            try {
                source.chapters(manga).also {
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
            source.pages(chapter).also {
                sourceRepository.recordSuccess(chapter.sourceId, System.currentTimeMillis() - started)
            }
        } catch (t: Throwable) {
            sourceRepository.recordFailure(chapter.sourceId, t.message ?: "pages failed")
            emptyList()
        }
    }

    suspend fun filterOptions(sourceId: String) = runCatching { sourceOf(sourceId)?.filterOptions() }.getOrNull()

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
