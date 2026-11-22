package app.kurenai.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "manga",
    indices = [Index("sourceId"), Index("title"), Index("isFavorite"), Index("updatedAt")],
)
data class MangaEntity(
    @PrimaryKey val id: String,
    val sourceId: String,
    val title: String,
    val altTitles: Set<String> = emptySet(),
    val url: String = "",
    val publicUrl: String = "",
    val coverUrl: String? = null,
    val largeCoverUrl: String? = null,
    val description: String? = null,
    val authors: Set<String> = emptySet(),
    val tags: Set<String> = emptySet(),
    val status: String = "UNKNOWN",
    val kind: String = "MANGA",
    val contentRating: String = "SAFE",
    val rating: Float = 0f,
    val chapterCount: Int = 0,
    val latestChapter: String? = null,
    val updatedAt: Long = 0L,
    val isFavorite: Boolean = false,
    val addedAt: Long = 0L,
    val lastReadAt: Long = 0L,
    val notes: String? = null,
)

@Entity(
    tableName = "chapters",
    indices = [Index("mangaId"), Index(value = ["mangaId", "number"]), Index("isRead"), Index("downloaded")],
)
data class ChapterEntity(
    @PrimaryKey val id: String,
    val mangaId: String,
    val sourceId: String,
    val title: String? = null,
    val number: Float = 0f,
    val volume: Int = 0,
    val url: String = "",
    val scanlator: String? = null,
    val uploadedAt: Long = 0L,
    val branch: String? = null,
    val isRead: Boolean = false,
    val isBookmarked: Boolean = false,
    val downloaded: Boolean = false,
    val pageCount: Int = 0,
)

@Entity(tableName = "history", indices = [Index("readAt"), Index("mangaId")])
data class HistoryEntity(
    @PrimaryKey val mangaId: String,
    val chapterId: String,
    val page: Int = 0,
    val totalPages: Int = 0,
    val readAt: Long = 0L,
)

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val order: Int = 0,
    val isSystem: Boolean = false,
    val colorSeed: Int = 0,
)

@Entity(
    tableName = "manga_categories",
    primaryKeys = ["mangaId", "categoryId"],
    indices = [Index("categoryId")],
    foreignKeys = [
        ForeignKey(MangaEntity::class, ["id"], ["mangaId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(CategoryEntity::class, ["id"], ["categoryId"], onDelete = ForeignKey.CASCADE),
    ],
)
data class MangaCategoryEntity(val mangaId: String, val categoryId: Long)

@Entity(tableName = "downloads", indices = [Index("state"), Index("mangaId")])
data class DownloadEntity(
    @PrimaryKey val id: String,
    val mangaId: String,
    val chapterId: String,
    val state: String = "QUEUED",
    val progress: Int = 0,
    val total: Int = 0,
    val bytesDownloaded: Long = 0,
    val createdAt: Long = 0,
    val error: String? = null,
)

@Entity(tableName = "updates", indices = [Index("discoveredAt"), Index("mangaId")])
data class UpdateEntity(
    @PrimaryKey val chapterId: String,
    val mangaId: String,
    val discoveredAt: Long,
    val score: Float = 0f,
    val reason: String = "",
    val seen: Boolean = false,
)

@Entity(tableName = "tracker_links", primaryKeys = ["mangaId", "tracker"])
data class TrackerLinkEntity(
    val mangaId: String,
    val tracker: String,
    val remoteId: String,
    val remoteUrl: String? = null,
    val status: String = "READING",
    val progress: Int = 0,
    val score: Float? = null,
    val syncedAt: Long = 0L,
)

@Entity(tableName = "reading_sessions", indices = [Index("startedAt"), Index("mangaId")])
data class ReadingSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val mangaId: String,
    val chapterId: String,
    val pages: Int,
    val startedAt: Long,
    val endedAt: Long,
    val minutes: Long,
)

@Entity(tableName = "search_history")
data class SearchHistoryEntity(
    @PrimaryKey val query: String,
    val usedAt: Long,
)

@Entity(tableName = "source_health")
data class SourceHealthEntity(
    @PrimaryKey val sourceId: String,
    val consecutiveFailures: Int = 0,
    val totalSuccesses: Long = 0,
    val totalFailures: Long = 0,
    val lastCheckedAt: Long = 0,
    val lastError: String? = null,
    val averageLatencyMs: Long = 0,
    val disabledAutomatically: Boolean = false,
) {
    val health: app.kurenai.core.model.SourceHealth
        get() = when {
            disabledAutomatically -> app.kurenai.core.model.SourceHealth.DISABLED
            consecutiveFailures >= 5 -> app.kurenai.core.model.SourceHealth.BROKEN
            consecutiveFailures >= 2 -> app.kurenai.core.model.SourceHealth.DEGRADED
            totalSuccesses > 0 -> app.kurenai.core.model.SourceHealth.HEALTHY
            else -> app.kurenai.core.model.SourceHealth.UNKNOWN
        }
}

@Entity(tableName = "ai_cache")
data class AiCacheEntity(
    @PrimaryKey val key: String,
    val value: String,
    val createdAt: Long,
)
