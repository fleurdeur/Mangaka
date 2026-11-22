package app.kurenai.core.data

import app.kurenai.core.common.AppEvent
import app.kurenai.core.common.AppEventBus
import app.kurenai.core.database.dao.CategoryDao
import app.kurenai.core.database.dao.ChapterDao
import app.kurenai.core.database.dao.HistoryDao
import app.kurenai.core.database.dao.MangaDao
import app.kurenai.core.database.dao.StatsDao
import app.kurenai.core.database.entity.CategoryEntity
import app.kurenai.core.database.entity.MangaCategoryEntity
import app.kurenai.core.database.entity.ReadingSessionEntity
import app.kurenai.core.model.Category
import app.kurenai.core.model.HistoryEntry
import app.kurenai.core.model.LibraryEntry
import app.kurenai.core.model.Manga
import app.kurenai.core.model.MangaChapter
import app.kurenai.core.model.ReadingStats
import app.kurenai.core.model.YearInReview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

data class ContinueReadingItem(
    val manga: Manga,
    val chapter: MangaChapter?,
    val page: Int,
    val totalPages: Int,
    val readAt: Long,
) {
    val fraction: Float get() = if (totalPages <= 0) 0f else (page.toFloat() / totalPages).coerceIn(0f, 1f)
}

@Singleton
class LibraryRepository @Inject constructor(
    private val mangaDao: MangaDao,
    private val chapterDao: ChapterDao,
    private val historyDao: HistoryDao,
    private val categoryDao: CategoryDao,
    private val statsDao: StatsDao,
    private val eventBus: AppEventBus,
) {
    val favorites: Flow<List<LibraryEntry>> = combine(
        mangaDao.observeFavorites(),
        chapterDao.observeDownloaded(),
    ) { mangas, _ ->
        mangas.map { entity ->
            val manga = entity.toModel()
            LibraryEntry(manga = manga, addedAt = entity.addedAt, lastReadAt = entity.lastReadAt)
        }
    }

    fun observeCategory(categoryId: Long?): Flow<List<Manga>> = mangaDao.observeCategory(categoryId).map { list -> list.map { it.toModel() } }

    val categories: Flow<List<Category>> = categoryDao.observe().map { list ->
        list.map { Category(it.id, it.name, it.order, it.isSystem, it.colorSeed) }
    }

    val history: Flow<List<ContinueReadingItem>> = historyDao.observe(40).map { rows ->
        rows.map { row ->
            val manga = mangaDao.get(row.mangaId)?.toModel()
            val chapter = chapterDao.get(row.chapterId)?.toModel()
            ContinueReadingItem(manga ?: return@map null, chapter, row.page, row.totalPages, row.readAt)
        }.filterNotNull()
    }

    fun observeHistoryEntries(limit: Int = 50): Flow<List<HistoryEntry>> = historyDao.observe(limit).map { rows ->
        rows.mapNotNull { row ->
            val manga = mangaDao.get(row.mangaId)?.toModel() ?: return@mapNotNull null
            val chapter = chapterDao.get(row.chapterId)?.toModel() ?: return@mapNotNull null
            HistoryEntry(manga, chapter, row.page, row.readAt, row.totalPages)
        }
    }

    fun observeCategoriesFor(mangaId: String): Flow<List<Long>> = categoryDao.observeCategoriesFor(mangaId)

    suspend fun ensureDefaultCategories() {
        if (categoryDao.count() == 0) {
            categoryDao.upsert(CategoryEntity(name = "Reading", order = 0, isSystem = true, colorSeed = 1))
            categoryDao.upsert(CategoryEntity(name = "Plan to read", order = 1, isSystem = true, colorSeed = 2))
            categoryDao.upsert(CategoryEntity(name = "Finished", order = 2, isSystem = true, colorSeed = 3))
        }
    }

    suspend fun createCategory(name: String): Long = categoryDao.upsert(CategoryEntity(name = name, order = 99))

    suspend fun deleteCategory(id: Long) = categoryDao.delete(id)

    suspend fun addToCategory(mangaId: String, categoryId: Long) {
        categoryDao.addManga(MangaCategoryEntity(mangaId, categoryId))
        eventBus.emit(AppEvent.LibraryChanged)
    }

    suspend fun removeFromCategory(mangaId: String, categoryId: Long) {
        categoryDao.removeManga(mangaId, categoryId)
        eventBus.emit(AppEvent.LibraryChanged)
    }

    suspend fun restoreProgress(manga: Manga, chapter: MangaChapter, page: Int, totalPages: Int) {
        historyDao.upsert(
            app.kurenai.core.database.entity.HistoryEntity(
                mangaId = manga.id,
                chapterId = chapter.id,
                page = page,
                totalPages = totalPages,
                readAt = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun recordSession(manga: Manga, chapter: MangaChapter, pages: Int, startedAt: Long, endedAt: Long) {
        if (pages <= 0) return
        statsDao.addSession(
            ReadingSessionEntity(
                mangaId = manga.id,
                chapterId = chapter.id,
                pages = pages,
                startedAt = startedAt,
                endedAt = endedAt,
                minutes = ((endedAt - startedAt) / 60_000L).coerceAtLeast(1),
            ),
        )
    }

    suspend fun stats(): ReadingStats {
        val sessions = statsDao.observeSessions(500)
        val all = sessions.first()
        val days = all.map { Instant.ofEpochMilli(it.startedAt).atZone(ZoneId.systemDefault()).toLocalDate() }.distinct().sortedDescending()
        var streak = 0
        var cursor = LocalDate.now()
        for (day in days) {
            if (day == cursor) {
                streak++
                cursor = cursor.minusDays(1)
            } else if (day == cursor.minusDays(1)) {
                cursor = day
                streak++
                cursor = cursor.minusDays(1)
            } else if (day < cursor.minusDays(1)) {
                break
            }
        }
        return ReadingStats(
            chaptersRead = all.size,
            pagesRead = all.sumOf { it.pages.toLong() },
            minutesRead = all.sumOf { it.minutes },
            streakDays = streak,
            longestStreak = streak,
            lastReadDay = days.firstOrNull()?.toString(),
        )
    }

    suspend fun yearInReview(year: Int): YearInReview {
        val start = LocalDate.of(year, 1, 1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val end = LocalDate.of(year + 1, 1, 1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val sessions = statsDao.observeSessions(2000).first()
            .filter { it.startedAt in start until end }
        return YearInReview(
            year = year,
            chapters = sessions.size,
            minutes = sessions.sumOf { it.minutes },
            titles = sessions.map { it.mangaId }.distinct().size,
            favoriteGenre = null,
            topSeries = sessions.groupingBy { it.mangaId }.eachCount().entries.sortedByDescending { it.value }.take(5).map { it.key },
        )
    }

    suspend fun clearHistory() = historyDao.clear()
}
