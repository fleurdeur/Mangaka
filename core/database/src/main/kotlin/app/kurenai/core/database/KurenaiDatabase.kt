package app.kurenai.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import app.kurenai.core.database.dao.AiCacheDao
import app.kurenai.core.database.dao.CategoryDao
import app.kurenai.core.database.dao.ChapterDao
import app.kurenai.core.database.dao.DownloadDao
import app.kurenai.core.database.dao.HistoryDao
import app.kurenai.core.database.dao.MangaDao
import app.kurenai.core.database.dao.SearchHistoryDao
import app.kurenai.core.database.dao.SourceHealthDao
import app.kurenai.core.database.dao.StatsDao
import app.kurenai.core.database.dao.TrackerDao
import app.kurenai.core.database.dao.UpdateDao
import app.kurenai.core.database.entity.AiCacheEntity
import app.kurenai.core.database.entity.CategoryEntity
import app.kurenai.core.database.entity.ChapterEntity
import app.kurenai.core.database.entity.DownloadEntity
import app.kurenai.core.database.entity.HistoryEntity
import app.kurenai.core.database.entity.MangaCategoryEntity
import app.kurenai.core.database.entity.MangaEntity
import app.kurenai.core.database.entity.ReadingSessionEntity
import app.kurenai.core.database.entity.SearchHistoryEntity
import app.kurenai.core.database.entity.SourceHealthEntity
import app.kurenai.core.database.entity.TrackerLinkEntity
import app.kurenai.core.database.entity.UpdateEntity

@Database(
    entities = [
        MangaEntity::class,
        ChapterEntity::class,
        HistoryEntity::class,
        CategoryEntity::class,
        MangaCategoryEntity::class,
        DownloadEntity::class,
        UpdateEntity::class,
        TrackerLinkEntity::class,
        ReadingSessionEntity::class,
        SearchHistoryEntity::class,
        SourceHealthEntity::class,
        AiCacheEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class KurenaiDatabase : RoomDatabase() {
    abstract fun mangaDao(): MangaDao
    abstract fun chapterDao(): ChapterDao
    abstract fun historyDao(): HistoryDao
    abstract fun categoryDao(): CategoryDao
    abstract fun downloadDao(): DownloadDao
    abstract fun updateDao(): UpdateDao
    abstract fun trackerDao(): TrackerDao
    abstract fun statsDao(): StatsDao
    abstract fun sourceHealthDao(): SourceHealthDao
    abstract fun searchHistoryDao(): SearchHistoryDao
    abstract fun aiCacheDao(): AiCacheDao

    companion object {
        fun build(context: Context): KurenaiDatabase =
            Room.databaseBuilder(context, KurenaiDatabase::class.java, "kurenai.db")
                .addCallback(FtsCallback)
                .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                .build()
    }
}

/**
 * Room only ships an FTS4 annotation, so Kurenai creates a true FTS5 index and its
 * sync triggers directly. Search queries use `manga_fts MATCH` through @RawQuery.
 */
private object FtsCallback : RoomDatabase.Callback() {
    override fun onCreate(db: SupportSQLiteDatabase) {
        super.onCreate(db)
        createFts(db)
    }

    override fun onOpen(db: SupportSQLiteDatabase) {
        super.onOpen(db)
        createFts(db)
    }

    private fun createFts(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE VIRTUAL TABLE IF NOT EXISTS manga_fts USING fts5(
                mangaId UNINDEXED,
                title,
                altTitles,
                authors,
                tags,
                tokenize = 'unicode61 remove_diacritics 2'
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TRIGGER IF NOT EXISTS manga_fts_ai AFTER INSERT ON manga BEGIN
                INSERT INTO manga_fts(mangaId, title, altTitles, authors, tags)
                VALUES (new.id, new.title, new.altTitles, new.authors, new.tags);
            END
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TRIGGER IF NOT EXISTS manga_fts_ad AFTER DELETE ON manga BEGIN
                DELETE FROM manga_fts WHERE mangaId = old.id;
            END
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TRIGGER IF NOT EXISTS manga_fts_au AFTER UPDATE ON manga BEGIN
                DELETE FROM manga_fts WHERE mangaId = old.id;
                INSERT INTO manga_fts(mangaId, title, altTitles, authors, tags)
                VALUES (new.id, new.title, new.altTitles, new.authors, new.tags);
            END
            """.trimIndent(),
        )
    }
}
