package app.kurenai.core.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import app.kurenai.core.database.dao.ChapterDao
import app.kurenai.core.database.dao.MangaDao
import app.kurenai.core.database.dao.UpdateDao
import app.kurenai.core.database.entity.UpdateEntity
import app.kurenai.core.datastore.SettingsStore
import app.kurenai.core.model.Manga
import app.kurenai.core.model.UpdateEntry
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UpdateRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val mangaDao: MangaDao,
    private val chapterDao: ChapterDao,
    private val updateDao: UpdateDao,
    private val mangaRepository: MangaRepository,
    private val settingsStore: SettingsStore,
) {
    val updates: Flow<List<UpdateEntry>> = updateDao.observe(100).map { rows ->
        rows.mapNotNull { row ->
            val manga = mangaDao.get(row.mangaId)?.toModel() ?: return@mapNotNull null
            val chapter = chapterDao.get(row.chapterId)?.toModel()
            UpdateEntry(manga, listOfNotNull(chapter), row.discoveredAt, row.score, row.reason)
        }
    }

    val unreadCount: Flow<Int> = updateDao.observeUnreadCount()

    suspend fun checkForUpdates(onProgress: (Int, Int) -> Unit = { _, _ -> }): Int = withContext(Dispatchers.IO) {
        val favorites = mangaDao.observeFavorites().first().map { it.toModel() }
        var found = 0
        favorites.take(60).forEachIndexed { index, manga ->
            onProgress(index + 1, favorites.size)
            val chapters = runCatching { mangaRepository.chapters(manga) }.getOrElse { emptyList() }
            val newOnes = chapters.filter { it.uploadedAt > manga.updatedAt && !it.isRead }
            if (newOnes.isNotEmpty()) {
                found += newOnes.size
                updateDao.upsertAll(
                    newOnes.map { chapter ->
                        UpdateEntity(
                            chapterId = chapter.id,
                            mangaId = manga.id,
                            discoveredAt = System.currentTimeMillis(),
                            score = smartScore(manga, chapter),
                            reason = buildReason(manga, chapter),
                        )
                    },
                )
                mangaDao.updateUpdateInfo(manga.id, newOnes.maxOf { it.uploadedAt }, newOnes.first().displayTitle, chapters.size)
            }
        }
        if (found > 0) notify(found)
        found
    }

    suspend fun markSeen(chapterId: String) = updateDao.markSeen(chapterId)

    suspend fun prune() = updateDao.prune(System.currentTimeMillis() - 60L * 24 * 60 * 60 * 1000)

    private fun smartScore(manga: Manga, chapter: app.kurenai.core.model.MangaChapter): Float {
        var score = 0.4f
        if (manga.isFavorite) score += 0.2f
        if (chapter.number > manga.chapterCount) score += 0.1f
        if (System.currentTimeMillis() - chapter.uploadedAt < 24 * 60 * 60 * 1000L) score += 0.2f
        return score.coerceAtMost(1f)
    }

    private fun buildReason(manga: Manga, chapter: app.kurenai.core.model.MangaChapter): String =
        "New ${chapter.displayTitle} from ${manga.sourceId.substringAfter(':')}"

    private suspend fun notify(count: Int) {
        val settings = settingsStore.settings.first()
        if (!settings.notificationsEnabled) return
        val manager = context.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel("updates", "New chapters", NotificationManager.IMPORTANCE_DEFAULT),
            )
        }
        manager.notify(
            42,
            NotificationCompat.Builder(context, "updates")
                .setSmallIcon(android.R.drawable.stat_notify_sync)
                .setContentTitle("$count new chapter${if (count == 1) "" else "s"}")
                .setContentText("Your library has fresh updates.")
                .setAutoCancel(true)
                .build(),
        )
    }
}
