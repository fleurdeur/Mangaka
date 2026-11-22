package app.kurenai.feature.details

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.kurenai.core.data.DownloadRepository
import app.kurenai.core.data.LibraryRepository
import app.kurenai.core.data.MangaRepository
import app.kurenai.core.data.RecommendationEngine
import app.kurenai.core.data.TrackerRepository
import app.kurenai.core.model.Category
import app.kurenai.core.model.Manga
import app.kurenai.core.model.MangaChapter
import app.kurenai.core.model.Recommendation
import app.kurenai.core.model.TrackerLink
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DetailsUiState(
    val manga: Manga? = null,
    val chapters: List<MangaChapter> = emptyList(),
    val categories: List<Category> = emptyList(),
    val selectedCategoryIds: Set<Long> = emptySet(),
    val source: app.kurenai.core.model.SourceInfo? = null,
    val trackers: List<TrackerLink> = emptyList(),
    val recommendations: List<Recommendation> = emptyList(),
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val error: String? = null,
    val message: String? = null,
) {
    val readCount: Int get() = chapters.count { it.isRead }
    val unreadCount: Int get() = chapters.count { !it.isRead }
    val nextChapter: MangaChapter? get() = chapters.sortedBy { it.number }.firstOrNull { !it.isRead }
}

@HiltViewModel
class DetailsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val mangaRepository: MangaRepository,
    private val libraryRepository: LibraryRepository,
    private val downloadRepository: DownloadRepository,
    private val trackerRepository: TrackerRepository,
    private val recommendationEngine: RecommendationEngine,
) : ViewModel() {
    private val mangaId: String = savedStateHandle["mangaId"] ?: ""
    private val _state = MutableStateFlow(DetailsUiState())
    val state: StateFlow<DetailsUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            val stored = mangaRepository.manga(mangaId)
            if (stored == null) {
                _state.value = _state.value.copy(loading = false, error = "This series is not cached yet.")
                return@launch
            }
            val sourceInfo = mangaRepository.sourceInfo(stored.sourceId)
            _state.value = _state.value.copy(manga = stored, source = sourceInfo)
            val detailed = mangaRepository.details(stored, refresh = true)
            val chapters = mangaRepository.chapters(detailed)
            val categories = libraryRepository.categories.first()
            _state.value = _state.value.copy(
                manga = detailed,
                chapters = chapters,
                categories = categories,
                loading = false,
                refreshing = false,
            )
            val trackers = runCatching { trackerRepository.links(mangaId).first() }.getOrDefault(emptyList())
            _state.value = _state.value.copy(trackers = trackers)
            val recs = runCatching {
                val seed = detailed.tags.take(2).joinToString(" ")
                val candidates = mangaRepository.search(detailed.sourceId, app.kurenai.core.model.SearchFilters(query = seed), 0, 20)
                recommendationEngine.recommend(candidates, 6)
            }.getOrDefault(emptyList())
            _state.value = _state.value.copy(recommendations = recs)
        }
    }

    fun refresh() {
        _state.value = _state.value.copy(refreshing = true)
        load()
    }

    fun toggleFavorite() {
        val manga = _state.value.manga ?: return
        viewModelScope.launch {
            mangaRepository.toggleFavorite(manga)
            _state.value = _state.value.copy(manga = manga.copy(isFavorite = !manga.isFavorite))
        }
    }

    fun read(chapter: MangaChapter, onOpen: (String, String) -> Unit) {
        val manga = _state.value.manga ?: return
        viewModelScope.launch {
            mangaRepository.persist(manga)
            onOpen(manga.id, chapter.id)
        }
    }

    fun startReading(onOpen: (String, String) -> Unit) {
        val next = _state.value.nextChapter ?: _state.value.chapters.minByOrNull { it.number } ?: return
        read(next, onOpen)
    }

    fun download(chapter: MangaChapter) = viewModelScope.launch {
        val manga = _state.value.manga ?: return@launch
        downloadRepository.enqueue(manga, chapter)
        _state.value = _state.value.copy(message = "Queued ${chapter.displayTitle}")
    }

    fun downloadNext() = viewModelScope.launch {
        val manga = _state.value.manga ?: return@launch
        val chapters = _state.value.chapters.sortedBy { it.number }
        downloadRepository.enqueueNextChapters(manga, chapters.filter { it.number >= (state.value.nextChapter?.number ?: 0f) }, 5)
        _state.value = _state.value.copy(message = "Queued the next five chapters")
    }

    fun markRead(chapter: MangaChapter, read: Boolean = true) = viewModelScope.launch {
        mangaRepository.markRead(chapter, read)
        _state.value = _state.value.copy(chapters = _state.value.chapters.map { if (it.id == chapter.id) it.copy(isRead = read) else it })
    }

    fun addToCategory(mangaId: String, categoryId: Long) = viewModelScope.launch {
        libraryRepository.addToCategory(mangaId, categoryId)
    }

    fun createCategory(name: String) = viewModelScope.launch {
        val id = libraryRepository.createCategory(name)
        val manga = _state.value.manga ?: return@launch
        libraryRepository.addToCategory(manga.id, id)
    }

    fun syncTrackers() = viewModelScope.launch {
        val results = trackerRepository.syncManga(mangaId)
        _state.value = _state.value.copy(message = results.joinToString { "${it.service.title}: ${it.message}" })
    }

    fun clearMessage() {
        _state.value = _state.value.copy(message = null)
    }
}
