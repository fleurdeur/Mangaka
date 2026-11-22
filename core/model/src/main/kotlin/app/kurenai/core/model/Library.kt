package app.kurenai.core.model

import kotlinx.serialization.Serializable

@Serializable
data class LibraryEntry(
    val manga: Manga,
    val categoryIds: Set<Long> = emptySet(),
    val addedAt: Long = System.currentTimeMillis(),
    val lastReadAt: Long = 0L,
    val unreadCount: Int = 0,
    val isPinned: Boolean = false,
    val notes: String? = null,
)

@Serializable
data class Category(
    val id: Long,
    val name: String,
    val order: Int = 0,
    val isSystem: Boolean = false,
    val colorSeed: Int = 0,
)

@Serializable
data class HistoryEntry(
    val manga: Manga,
    val chapter: MangaChapter,
    val page: Int = 0,
    val readAt: Long = System.currentTimeMillis(),
    val totalPages: Int = 0,
)

@Serializable
data class UpdateEntry(
    val manga: Manga,
    val chapters: List<MangaChapter>,
    val discoveredAt: Long = System.currentTimeMillis(),
    val score: Float = 0f,
    val reason: String = "",
)

@Serializable
data class DownloadTask(
    val id: String,
    val manga: Manga,
    val chapter: MangaChapter,
    val state: DownloadState = DownloadState.QUEUED,
    val progress: Int = 0,
    val total: Int = 0,
    val bytesDownloaded: Long = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val error: String? = null,
)

@Serializable
enum class DownloadState {
    QUEUED,
    RUNNING,
    PAUSED,
    COMPLETED,
    FAILED,
    CANCELED,
}

@Serializable
data class ReadingProgress(
    val mangaId: String,
    val chapterId: String,
    val page: Int,
    val totalPages: Int,
    val updatedAt: Long = System.currentTimeMillis(),
) {
    val fraction: Float
        get() = if (totalPages <= 0) 0f else (page.toFloat() / totalPages).coerceIn(0f, 1f)
}

@Serializable
data class ReadingStats(
    val chaptersRead: Int = 0,
    val pagesRead: Long = 0L,
    val minutesRead: Long = 0L,
    val streakDays: Int = 0,
    val longestStreak: Int = 0,
    val lastReadDay: String? = null,
    val topGenres: Map<String, Int> = emptyMap(),
    val seriesFinished: Int = 0,
)

@Serializable
data class YearInReview(
    val year: Int,
    val chapters: Int,
    val minutes: Long,
    val titles: Int,
    val favoriteGenre: String?,
    val topSeries: List<String>,
)

@Serializable
data class Recommendation(
    val manga: Manga,
    val score: Float,
    val reasons: List<String>,
)

@Serializable
data class CrossSourceMatch(
    val canonicalTitle: String,
    val entries: List<Manga>,
    val preferredSourceId: String? = null,
    val confidence: Float = 0f,
)

@Serializable
data class TrackerLink(
    val mangaId: String,
    val tracker: TrackerService,
    val remoteId: String,
    val remoteUrl: String? = null,
    val status: TrackerStatus = TrackerStatus.READING,
    val progress: Int = 0,
    val score: Float? = null,
    val syncedAt: Long = 0L,
)

@Serializable
enum class TrackerService {
    ANILIST,
    MYANIMELIST,
    SHIKIMORI,
    KITSU,
    ;

    val title: String
        get() = when (this) {
            ANILIST -> "AniList"
            MYANIMELIST -> "MyAnimeList"
            SHIKIMORI -> "Shikimori"
            KITSU -> "Kitsu"
        }
}

@Serializable
enum class TrackerStatus {
    READING,
    COMPLETED,
    ON_HOLD,
    DROPPED,
    PLAN_TO_READ,
}
