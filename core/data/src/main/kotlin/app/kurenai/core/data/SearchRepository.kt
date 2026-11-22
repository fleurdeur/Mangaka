package app.kurenai.core.data

import app.kurenai.core.database.dao.ChapterDao
import app.kurenai.core.database.dao.SearchHistoryDao
import app.kurenai.core.database.dao.MangaDao
import app.kurenai.core.database.entity.SearchHistoryEntity
import app.kurenai.core.model.Manga
import androidx.sqlite.db.SimpleSQLiteQuery
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SearchRepository @Inject constructor(
    private val mangaDao: MangaDao,
    private val chapterDao: ChapterDao,
    private val searchHistoryDao: SearchHistoryDao,
) {
    val recentQueries: Flow<List<String>> = searchHistoryDao.observe().map { list -> list.map { it.query } }

    /** Natural-language-ish FTS5 search over the local library. */
    fun search(query: String, limit: Int = 60): Flow<List<Manga>> {
        val tokens = query.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        if (tokens.isEmpty()) return mangaDao.observeFavorites().map { it.map { entity -> entity.toModel() } }
        val fts = tokens.joinToString(" AND ") { token ->
            val clean = token.replace("\"", "").replace("'", "").replace(Regex("[^\\p{L}\\p{N}_-]"), "")
            if (clean.isBlank()) "" else "\"$clean\"*"
        }.ifBlank { "\"\"" }
        val sql = "SELECT manga.* FROM manga JOIN manga_fts ON manga.id = manga_fts.mangaId " +
            "WHERE manga_fts MATCH ? ORDER BY rank LIMIT ?"
        return mangaDao.searchFts(SimpleSQLiteQuery(sql, arrayOf<Any>(fts, limit)))
            .map { list -> list.map { it.toModel() } }
    }

    suspend fun remember(query: String) {
        if (query.isBlank()) return
        searchHistoryDao.add(SearchHistoryEntity(query.trim(), System.currentTimeMillis()))
    }

    suspend fun clearHistory() = searchHistoryDao.clear()
}
