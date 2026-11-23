package app.kurenai.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
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
    version = 2,
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
        /**
         * Version 1 used an FTS5 virtual table. Not every Android SQLite build ships
         * FTS5, which made the database fail to open on some devices. Version 2 uses
         * the universally available FTS4 module and rebuilds the index from Room data.
         */
        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS manga_fts")
                createFtsIndex(db)
                rebuildFtsIndex(db)
            }
        }

        fun build(context: Context): KurenaiDatabase =
            Room.databaseBuilder(context, KurenaiDatabase::class.java, "kurenai.db")
                .addCallback(FtsCallback)
                .addMigrations(MIGRATION_1_2)
                .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                .build()
    }
}

/**
 * Room only ships an FTS4 annotation, so Kurenai creates the virtual table and its
 * sync triggers directly. FTS4 is available on every supported Android version,
 * unlike FTS5. Search queries use `manga_fts MATCH` through @RawQuery.
 */
private object FtsCallback : RoomDatabase.Callback() {
    override fun onCreate(db: SupportSQLiteDatabase) {
        super.onCreate(db)
        createFtsIndex(db)
        rebuildFtsIndex(db)
    }

    override fun onOpen(db: SupportSQLiteDatabase) {
        super.onOpen(db)
        createFtsIndex(db)
    }
}

private fun createFtsIndex(db: SupportSQLiteDatabase) {
    db.execSQL(
        """
        CREATE VIRTUAL TABLE IF NOT EXISTS manga_fts USING fts4(
            mangaId,
            title,
            altTitles,
            authors,
            tags,
            tokenize=unicode61
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

private fun rebuildFtsIndex(db: SupportSQLiteDatabase) {
    db.execSQL("DELETE FROM manga_fts")
    db.execSQL(
        "INSERT INTO manga_fts(mangaId, title, altTitles, authors, tags) " +
            "SELECT id, title, altTitles, authors, tags FROM manga",
    )
}
