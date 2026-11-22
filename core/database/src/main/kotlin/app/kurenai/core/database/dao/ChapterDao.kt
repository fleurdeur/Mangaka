package app.kurenai.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import app.kurenai.core.database.entity.ChapterEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChapterDao {
    @Query("SELECT * FROM chapters WHERE mangaId = :mangaId ORDER BY number DESC, uploadedAt DESC")
    fun observe(mangaId: String): Flow<List<ChapterEntity>>

    @Query("SELECT * FROM chapters WHERE mangaId = :mangaId ORDER BY number DESC, uploadedAt DESC")
    suspend fun getForManga(mangaId: String): List<ChapterEntity>

    @Query("SELECT * FROM chapters WHERE id = :id")
    suspend fun get(id: String): ChapterEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<ChapterEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ChapterEntity)

    @Query("UPDATE chapters SET isRead = :read WHERE id = :id")
    suspend fun setRead(id: String, read: Boolean)

    @Query("UPDATE chapters SET isRead = 1 WHERE mangaId = :mangaId")
    suspend fun markAllRead(mangaId: String)

    @Query("UPDATE chapters SET downloaded = :downloaded, pageCount = :pageCount WHERE id = :id")
    suspend fun setDownloaded(id: String, downloaded: Boolean, pageCount: Int)

    @Query("UPDATE chapters SET isBookmarked = :bookmarked WHERE id = :id")
    suspend fun setBookmarked(id: String, bookmarked: Boolean)

    @Query("SELECT COUNT(*) FROM chapters WHERE mangaId = :mangaId AND isRead = 0")
    suspend fun unreadCount(mangaId: String): Int

    @Query("SELECT * FROM chapters WHERE mangaId = :mangaId AND isRead = 0 ORDER BY number ASC LIMIT 1")
    suspend fun firstUnread(mangaId: String): ChapterEntity?

    @Query("SELECT * FROM chapters WHERE mangaId = :mangaId ORDER BY number ASC LIMIT 1")
    suspend fun first(mangaId: String): ChapterEntity?

    @Query("SELECT * FROM chapters WHERE downloaded = 1 ORDER BY uploadedAt DESC")
    fun observeDownloaded(): Flow<List<ChapterEntity>>

    @Query("DELETE FROM chapters WHERE mangaId = :mangaId")
    suspend fun deleteForManga(mangaId: String)
}
