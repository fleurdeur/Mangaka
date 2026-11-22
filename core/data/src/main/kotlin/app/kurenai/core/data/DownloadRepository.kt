package app.kurenai.core.data

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import app.kurenai.core.database.dao.ChapterDao
import app.kurenai.core.database.dao.DownloadDao
import app.kurenai.core.database.dao.MangaDao
import app.kurenai.core.database.entity.DownloadEntity
import app.kurenai.core.datastore.SettingsStore
import app.kurenai.core.model.DownloadState
import app.kurenai.core.model.DownloadTask
import app.kurenai.core.model.Manga
import app.kurenai.core.model.MangaChapter
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val downloadDao: DownloadDao,
    private val mangaDao: MangaDao,
    private val chapterDao: ChapterDao,
    private val settingsStore: SettingsStore,
) {
    fun observeAll(): Flow<List<DownloadTask>> = downloadDao.observeAll().map { rows ->
        rows.mapNotNull { row ->
            val manga = mangaDao.get(row.mangaId)?.toModel() ?: return@mapNotNull null
            val chapter = chapterDao.get(row.chapterId)?.toModel() ?: return@mapNotNull null
            DownloadTask(
                id = row.id,
                manga = manga,
                chapter = chapter,
                state = runCatching { DownloadState.valueOf(row.state) }.getOrDefault(DownloadState.QUEUED),
                progress = row.progress,
                total = row.total,
                bytesDownloaded = row.bytesDownloaded,
                createdAt = row.createdAt,
                error = row.error,
            )
        }
    }

    suspend fun enqueue(manga: Manga, chapter: MangaChapter) {
        val settings = settingsStore.settings.first()
        downloadDao.upsert(
            DownloadEntity(
                id = chapter.id,
                mangaId = manga.id,
                chapterId = chapter.id,
                state = DownloadState.QUEUED.name,
                createdAt = System.currentTimeMillis(),
            ),
        )
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(if (settings.wifiOnlyDownloads) NetworkType.UNMETERED else NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build()
        val request = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setConstraints(constraints)
            .setInputData(workDataOf("mangaId" to manga.id, "chapterId" to chapter.id))
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 20, TimeUnit.SECONDS)
            .addTag("kurenai-download")
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            "download:${chapter.id}",
            ExistingWorkPolicy.KEEP,
            request,
        )
    }

    suspend fun enqueueNextChapters(manga: Manga, chapters: List<MangaChapter>, count: Int = 5) {
        val next = chapters.sortedBy { it.number }.filter { !it.isRead && !it.isDownloaded }.take(count)
        next.forEach { enqueue(manga, it) }
    }

    suspend fun cancel(id: String) {
        WorkManager.getInstance(context).cancelUniqueWork("download:$id")
        downloadDao.updateProgress(id, DownloadState.CANCELED.name, 0, 0, 0)
    }

    suspend fun remove(id: String) {
        downloadDao.delete(id)
        val chapter = chapterDao.get(id) ?: return
        chapterDao.setDownloaded(id, false, 0)
    }

    suspend fun retry(id: String) {
        val row = downloadDao.observeAll().first().firstOrNull { it.id == id } ?: return
        val manga = mangaDao.get(row.mangaId)?.toModel() ?: return
        val chapter = chapterDao.get(row.chapterId)?.toModel() ?: return
        enqueue(manga, chapter)
    }

    suspend fun clearFinished() {
        downloadDao.observeAll().first()
            .filter { it.state in setOf(DownloadState.COMPLETED.name, DownloadState.FAILED.name, DownloadState.CANCELED.name) }
            .forEach { downloadDao.delete(it.id) }
    }
}
