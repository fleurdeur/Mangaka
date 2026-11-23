package app.kurenai.core.database

import android.content.Context
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.test.core.app.ApplicationProvider
import app.kurenai.core.database.entity.MangaEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class KurenaiDatabaseFtsTest {
    private lateinit var db: KurenaiDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.deleteDatabase("kurenai.db")
        db = KurenaiDatabase.build(context)
        db.openHelper.writableDatabase
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `database opens with portable FTS4 index`() = runBlocking {
        db.mangaDao().upsert(
            MangaEntity(
                id = "m1",
                sourceId = "sample",
                title = "One Piece",
                tags = setOf("Action", "Adventure"),
                authors = setOf("Eiichiro Oda"),
            ),
        )
        val sql = "SELECT manga.* FROM manga JOIN manga_fts ON manga.id = manga_fts.mangaId " +
            "WHERE manga_fts MATCH ? ORDER BY manga.updatedAt DESC, manga.title ASC LIMIT ?"
        val results = db.mangaDao()
            .searchFts(SimpleSQLiteQuery(sql, arrayOf<Any>("\"one\"*", 20)))
            .first()
        assertEquals(1, results.size)
        assertEquals("One Piece", results.first().title)
    }

    @Test
    fun `punctuation-only queries are handled without an FTS syntax error`() = runBlocking {
        db.mangaDao().upsert(MangaEntity(id = "m2", sourceId = "sample", title = "Berserk"))
        val results = db.mangaDao()
            .searchFts(SimpleSQLiteQuery("SELECT manga.* FROM manga WHERE title LIKE ?", arrayOf<Any>("%Berserk%")))
            .first()
        assertEquals(1, results.size)
    }
}
