package app.kurenai.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import app.kurenai.core.database.entity.CategoryEntity
import app.kurenai.core.database.entity.HistoryEntity
import app.kurenai.core.database.entity.MangaCategoryEntity
import app.kurenai.core.database.entity.MangaEntity
import app.kurenai.core.database.entity.ReadingSessionEntity
import app.kurenai.core.database.entity.SearchHistoryEntity
import app.kurenai.core.database.entity.SourceHealthEntity
import app.kurenai.core.database.entity.TrackerLinkEntity
import app.kurenai.core.database.entity.UpdateEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: HistoryEntity)

    @Query("SELECT * FROM history ORDER BY readAt DESC LIMIT :limit")
    fun observe(limit: Int = 50): Flow<List<HistoryEntity>>

    @Query("SELECT * FROM history WHERE mangaId = :mangaId")
    suspend fun forManga(mangaId: String): HistoryEntity?

    @Query("SELECT manga.* FROM history INNER JOIN manga ON manga.id = history.mangaId ORDER BY history.readAt DESC LIMIT :limit")
    fun observeManga(limit: Int = 30): Flow<List<MangaEntity>>

    @Query("DELETE FROM history WHERE mangaId = :mangaId")
    suspend fun deleteForManga(mangaId: String)

    @Query("DELETE FROM history")
    suspend fun clear()
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY `order` ASC, name ASC")
    fun observe(): Flow<List<CategoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: CategoryEntity): Long

    @Query("DELETE FROM categories WHERE id = :id AND isSystem = 0")
    suspend fun delete(id: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addManga(entity: MangaCategoryEntity)

    @Query("DELETE FROM manga_categories WHERE mangaId = :mangaId AND categoryId = :categoryId")
    suspend fun removeManga(mangaId: String, categoryId: Long)

    @Query("SELECT categoryId FROM manga_categories WHERE mangaId = :mangaId")
    fun observeCategoriesFor(mangaId: String): Flow<List<Long>>

    @Query("SELECT COUNT(*) FROM categories")
    suspend fun count(): Int
}

@Dao
interface DownloadDao {
    @Query("SELECT * FROM downloads ORDER BY createdAt ASC")
    fun observeAll(): Flow<List<app.kurenai.core.database.entity.DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE state IN ('QUEUED','RUNNING') ORDER BY createdAt ASC")
    suspend fun pending(): List<app.kurenai.core.database.entity.DownloadEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: app.kurenai.core.database.entity.DownloadEntity)

    @Query("UPDATE downloads SET state = :state, progress = :progress, total = :total, bytesDownloaded = :bytes, error = :error WHERE id = :id")
    suspend fun updateProgress(id: String, state: String, progress: Int, total: Int, bytes: Long, error: String? = null)

    @Query("DELETE FROM downloads WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface UpdateDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<UpdateEntity>)

    @Query("SELECT * FROM updates ORDER BY discoveredAt DESC LIMIT :limit")
    fun observe(limit: Int = 100): Flow<List<UpdateEntity>>

    @Query("UPDATE updates SET seen = 1 WHERE chapterId = :chapterId")
    suspend fun markSeen(chapterId: String)

    @Query("SELECT COUNT(*) FROM updates WHERE seen = 0")
    fun observeUnreadCount(): Flow<Int>

    @Query("DELETE FROM updates WHERE discoveredAt < :before")
    suspend fun prune(before: Long)
}

@Dao
interface TrackerDao {
    @Query("SELECT * FROM tracker_links WHERE mangaId = :mangaId")
    fun observeForManga(mangaId: String): Flow<List<TrackerLinkEntity>>

    @Query("SELECT * FROM tracker_links")
    suspend fun all(): List<TrackerLinkEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: TrackerLinkEntity)

    @Query("DELETE FROM tracker_links WHERE mangaId = :mangaId AND tracker = :tracker")
    suspend fun delete(mangaId: String, tracker: String)
}

@Dao
interface StatsDao {
    @Insert
    suspend fun addSession(entity: ReadingSessionEntity)

    @Query("SELECT * FROM reading_sessions ORDER BY startedAt DESC LIMIT :limit")
    fun observeSessions(limit: Int = 200): Flow<List<ReadingSessionEntity>>

    @Query("SELECT COALESCE(SUM(pages), 0) FROM reading_sessions")
    fun observePages(): Flow<Long>

    @Query("SELECT COALESCE(SUM(minutes), 0) FROM reading_sessions")
    fun observeMinutes(): Flow<Long>

    @Query("SELECT COUNT(*) FROM chapters WHERE isRead = 1")
    fun observeChaptersRead(): Flow<Int>

    @Query("SELECT COUNT(DISTINCT mangaId) FROM reading_sessions WHERE startedAt >= :since")
    suspend fun distinctTitlesSince(since: Long): Int

    @Query("SELECT COUNT(*) FROM chapters WHERE isRead = 1 AND uploadedAt >= :since")
    suspend fun chaptersSince(since: Long): Int
}

@Dao
interface SourceHealthDao {
    @Query("SELECT * FROM source_health")
    fun observeAll(): Flow<List<SourceHealthEntity>>

    @Query("SELECT * FROM source_health WHERE sourceId = :sourceId")
    suspend fun get(sourceId: String): SourceHealthEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: SourceHealthEntity)
}

@Dao
interface SearchHistoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun add(entity: SearchHistoryEntity)

    @Query("SELECT * FROM search_history ORDER BY usedAt DESC LIMIT :limit")
    fun observe(limit: Int = 20): Flow<List<SearchHistoryEntity>>

    @Query("DELETE FROM search_history")
    suspend fun clear()
}

@Dao
interface AiCacheDao {
    @Query("SELECT value FROM ai_cache WHERE `key` = :key")
    suspend fun get(key: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(entity: app.kurenai.core.database.entity.AiCacheEntity)

    @Query("DELETE FROM ai_cache WHERE createdAt < :before")
    suspend fun prune(before: Long)
}
