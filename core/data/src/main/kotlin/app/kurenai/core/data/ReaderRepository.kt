package app.kurenai.core.data

import android.content.Context
import app.kurenai.core.datastore.SettingsStore
import app.kurenai.core.model.Manga
import app.kurenai.core.model.MangaChapter
import app.kurenai.core.model.MangaPage
import app.kurenai.core.model.ReaderSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReaderRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val mangaRepository: MangaRepository,
    private val libraryRepository: LibraryRepository,
    private val settingsStore: SettingsStore,
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val perSeries = MutableStateFlow<Map<String, ReaderSettings>>(emptyMap())

    fun settingsFor(mangaId: String): ReaderSettings =
        perSeries.value[mangaId] ?: ReaderSettings.Default

    suspend fun loadSeriesOverrides() {
        val prefs = context.getSharedPreferences("reader_per_series", Context.MODE_PRIVATE)
        val map = prefs.all.mapNotNull { (key, value) ->
            (value as? String)?.let { raw ->
                runCatching { key to json.decodeFromString(ReaderSettings.serializer(), raw) }.getOrNull()
            }
        }.toMap()
        perSeries.value = map
    }

    suspend fun saveSeriesSettings(mangaId: String, settings: ReaderSettings) {
        val updated = perSeries.value + (mangaId to settings)
        perSeries.value = updated
        context.getSharedPreferences("reader_per_series", Context.MODE_PRIVATE)
            .edit()
            .putString(mangaId, json.encodeToString(ReaderSettings.serializer(), settings))
            .apply()
    }

    suspend fun pages(chapter: MangaChapter): List<MangaPage> {
        val downloaded = downloadedPages(chapter)
        return if (downloaded.isNotEmpty()) downloaded else mangaRepository.pages(chapter)
    }

    private suspend fun downloadedPages(chapter: MangaChapter): List<MangaPage> = withContext(Dispatchers.IO) {
        val root = File(context.filesDir, "downloads/${chapter.mangaId.safe()}/${chapter.id}")
        if (!root.isDirectory) return@withContext emptyList()
        root.listFiles().orEmpty().sortedBy { it.name }.mapIndexed { index, file ->
            MangaPage(index = index, url = file.toURI().toString(), sourceId = chapter.sourceId, chapterId = chapter.id)
        }
    }

    suspend fun save(manga: Manga, chapter: MangaChapter, page: Int, totalPages: Int) {
        mangaRepository.saveProgress(manga, chapter, page, totalPages)
    }

    suspend fun markChapterRead(chapter: MangaChapter) = mangaRepository.markRead(chapter, true)

    suspend fun neighbours(manga: Manga, chapter: MangaChapter): Pair<MangaChapter?, MangaChapter?> {
        val chapters = mangaRepository.chapters(manga).sortedBy { it.number }
        val index = chapters.indexOfFirst { it.id == chapter.id }
        return Pair(chapters.getOrNull(index - 1), chapters.getOrNull(index + 1))
    }

    suspend fun completeSession(manga: Manga, chapter: MangaChapter, pages: Int, startedAt: Long) {
        libraryRepository.recordSession(manga, chapter, pages, startedAt, System.currentTimeMillis())
        if (pages > 0) markChapterRead(chapter)
    }

    private fun String.safe() = replace(Regex("[^A-Za-z0-9._-]"), "_")
}
