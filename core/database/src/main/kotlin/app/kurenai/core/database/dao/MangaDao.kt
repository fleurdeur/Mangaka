package app.kurenai.core.database.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.Transaction
import androidx.sqlite.db.SupportSQLiteQuery
import app.kurenai.core.database.entity.MangaEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MangaDao {
    @Query("SELECT * FROM manga WHERE id = :id")
    suspend fun get(id: String): MangaEntity?

    @Query("SELECT * FROM manga WHERE sourceId = :sourceId AND url = :url LIMIT 1")
    suspend fun findBySourceUrl(sourceId: String, url: String): MangaEntity?

    @Query("SELECT * FROM manga WHERE isFavorite = 1 ORDER BY lastReadAt DESC, title ASC")
    fun observeFavorites(): Flow<List<MangaEntity>>

    @Query("SELECT * FROM manga WHERE isFavorite = 1 ORDER BY lastReadAt DESC, title ASC")
    fun pagingFavorites(): PagingSource<Int, MangaEntity>

    @Query("SELECT * FROM manga WHERE isFavorite = 1 AND (:categoryId IS NULL OR id IN (SELECT mangaId FROM manga_categories WHERE categoryId = :categoryId)) ORDER BY title ASC")
    fun observeCategory(categoryId: Long?): Flow<List<MangaEntity>>

    @Query("SELECT * FROM manga WHERE isFavorite = 1 AND title LIKE '%' || :query || '%' ORDER BY title ASC LIMIT :limit")
    fun observeFavoritesLike(query: String, limit: Int = 100): Flow<List<MangaEntity>>

    @RawQuery(observedEntities = [MangaEntity::class])
    fun searchFts(query: SupportSQLiteQuery): Flow<List<MangaEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: MangaEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<MangaEntity>)

    @Query("UPDATE manga SET isFavorite = :favorite, addedAt = CASE WHEN :favorite AND addedAt = 0 THEN :now ELSE addedAt END WHERE id = :id")
    suspend fun setFavorite(id: String, favorite: Boolean, now: Long)

    @Query("UPDATE manga SET lastReadAt = :at WHERE id = :id")
    suspend fun updateLastRead(id: String, at: Long)

    @Query("UPDATE manga SET updatedAt = :at, latestChapter = :latest, chapterCount = :count WHERE id = :id")
    suspend fun updateUpdateInfo(id: String, at: Long, latest: String?, count: Int)

    @Query("DELETE FROM manga WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT * FROM manga WHERE isFavorite = 0 ORDER BY lastReadAt ASC LIMIT :limit")
    suspend fun cacheCandidates(limit: Int): List<MangaEntity>

    @Query("SELECT COUNT(*) FROM manga")
    suspend fun count(): Int

    @Transaction
    @Query("SELECT * FROM manga WHERE isFavorite = 1 AND id IN (SELECT mangaId FROM manga_categories WHERE categoryId = :categoryId)")
    suspend fun favoritesInCategory(categoryId: Long): List<MangaEntity>
}
