package app.kurenai.core.data

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import app.kurenai.core.database.dao.ChapterDao
import app.kurenai.core.database.dao.DownloadDao
import app.kurenai.core.database.dao.MangaDao
import app.kurenai.core.model.DownloadState
import app.kurenai.core.network.KurenaiHttp
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Semaphore

/** Limits concurrent downloads per host to stay polite. */
object HostDownloadLimiter {
    private val limits = ConcurrentHashMap<String, Semaphore>()
    fun forHost(host: String): Semaphore = limits.computeIfAbsent(host) { Semaphore(2) }
}

@HiltWorker
class DownloadWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val mangaDao: MangaDao,
    private val chapterDao: ChapterDao,
    private val downloadDao: DownloadDao,
    private val mangaRepository: MangaRepository,
    private val http: KurenaiHttp,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val mangaId = inputData.getString("mangaId") ?: return@withContext Result.failure()
        val chapterId = inputData.getString("chapterId") ?: return@withContext Result.failure()
        val manga = mangaDao.get(mangaId)?.toModel() ?: return@withContext Result.failure()
        val chapter = chapterDao.get(chapterId)?.toModel() ?: return@withContext Result.failure()

        downloadDao.updateProgress(chapterId, DownloadState.RUNNING.name, 0, 0, 0)
        val pages = mangaRepository.pages(chapter)
        if (pages.isEmpty()) {
            downloadDao.updateProgress(chapterId, DownloadState.FAILED.name, 0, 0, 0, "No pages returned")
            return@withContext Result.failure()
        }
        val root = File(applicationContext.filesDir, "downloads/${mangaId.safeName()}/$chapterId")
        root.mkdirs()
        var bytes = 0L
        pages.forEachIndexed { index, page ->
            if (isStopped) return@withContext Result.failure()
            val target = File(root, "%04d.img".format(index))
            if (target.exists() && target.length() > 1024) {
                bytes += target.length()
                return@forEachIndexed
            }
            val host = runCatching { java.net.URI(page.url).host }.getOrDefault("unknown")
            val permit = HostDownloadLimiter.forHost(host)
            permit.acquire()
            try {
                val request = Request.Builder()
                    .url(page.url)
                    .header("User-Agent", KurenaiHttp.CHROME_UA)
                    .header("Referer", manga.publicUrl)
                    .build()
                http.client().newCall(request).execute().use { response ->
                    if (!response.isSuccessful) error("HTTP ${response.code}")
                    val body = response.body ?: error("Empty body")
                    target.outputStream().use { out -> body.byteStream().copyTo(out) }
                    bytes += target.length()
                }
            } catch (t: Throwable) {
                if (runAttemptCount >= 3) {
                    downloadDao.updateProgress(chapterId, DownloadState.FAILED.name, index, pages.size, bytes, t.message)
                    return@withContext Result.failure()
                }
                downloadDao.updateProgress(chapterId, DownloadState.QUEUED.name, index, pages.size, bytes, t.message)
                return@withContext Result.retry()
            } finally {
                permit.release()
            }
            setProgress(androidx.work.workDataOf("progress" to index + 1, "total" to pages.size))
            downloadDao.updateProgress(chapterId, DownloadState.RUNNING.name, index + 1, pages.size, bytes)
            chapterDao.setDownloaded(chapterId, false, pages.size)
        }
        chapterDao.setDownloaded(chapterId, true, pages.size)
        downloadDao.updateProgress(chapterId, DownloadState.COMPLETED.name, pages.size, pages.size, bytes)
        Result.success()
    }

    private fun String.safeName(): String = replace(Regex("[^A-Za-z0-9._-]"), "_")
}
