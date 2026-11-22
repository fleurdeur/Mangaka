package app.kurenai.feature.reader

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.kurenai.core.data.MangaRepository
import app.kurenai.core.data.ReaderRepository
import app.kurenai.core.model.Manga
import app.kurenai.core.model.MangaChapter
import app.kurenai.core.model.MangaPage
import app.kurenai.core.model.ReaderAction
import app.kurenai.core.model.ReaderSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ReaderUiState(
    val manga: Manga? = null,
    val chapter: MangaChapter? = null,
    val chapters: List<MangaChapter> = emptyList(),
    val pages: List<MangaPage> = emptyList(),
    val loading: Boolean = true,
    val error: String? = null,
    val currentPage: Int = 0,
    val initialPage: Int = 0,
    val settings: ReaderSettings = ReaderSettings.Default,
    val uiVisible: Boolean = true,
    val autoScrolling: Boolean = false,
    val prefetchPages: Int = 2,
    val translationVisible: Boolean = false,
) {
    val nextChapter: MangaChapter? get() = chapter?.let { current -> chapters.sortedBy { it.number }.firstOrNull { it.number > current.number } }
    val previousChapter: MangaChapter? get() = chapter?.let { current -> chapters.sortedByDescending { it.number }.firstOrNull { it.number < current.number } }
    val progress: Float get() = if (pages.isEmpty()) 0f else (currentPage + 1f) / pages.size
}

@HiltViewModel
class ReaderViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val mangaRepository: MangaRepository,
    private val readerRepository: ReaderRepository,
) : ViewModel() {
    private val mangaId: String = savedStateHandle["mangaId"] ?: ""
    private val chapterId: String = savedStateHandle["chapterId"] ?: ""
    private val _state = MutableStateFlow(ReaderUiState())
    val state: StateFlow<ReaderUiState> = _state.asStateFlow()
    private var saveJob: Job? = null
    private val pageTimes = mutableListOf<Long>()
    private var lastPageAt = System.currentTimeMillis()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true)
            val manga = mangaRepository.manga(mangaId)
            val chapter = mangaRepository.chapter(chapterId)
            if (manga == null || chapter == null) {
                _state.value = _state.value.copy(loading = false, error = "Chapter is not cached yet.")
                return@launch
            }
            val settings = readerRepository.settingsFor(mangaId)
            val chapters = mangaRepository.chapters(manga)
            val pages = readerRepository.pages(chapter)
            val history = mangaRepository.historyFor(mangaId)
            val initial = if (history?.chapterId == chapter.id) history.page.coerceIn(0, (pages.size - 1).coerceAtLeast(0)) else 0
            _state.value = _state.value.copy(
                manga = manga,
                chapter = chapter,
                chapters = chapters,
                pages = pages,
                loading = false,
                settings = settings,
                initialPage = initial,
                currentPage = initial,
                error = if (pages.isEmpty()) "No pages returned for this chapter." else null,
            )
            lastPageAt = System.currentTimeMillis()
        }
    }

    fun onPageChanged(page: Int) {
        val current = _state.value
        if (page == current.currentPage && current.pages.isNotEmpty()) {
            // Still record time for adaptive prefetch.
        }
        val now = System.currentTimeMillis()
        val delta = (now - lastPageAt).coerceIn(200L, 30_000L)
        pageTimes += delta
        if (pageTimes.size > 12) pageTimes.removeAt(0)
        lastPageAt = now
        val average = pageTimes.average().toLong()
        val prefetch = when {
            average < 2_500 -> 4
            average < 5_000 -> 3
            average < 9_000 -> 2
            else -> 1
        }
        _state.value = _state.value.copy(currentPage = page, prefetchPages = prefetch)
        val manga = current.manga ?: return
        val chapter = current.chapter ?: return
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(700)
            mangaRepository.saveProgress(manga, chapter, page, current.pages.size)
        }
    }

    fun toggleUi() { _state.value = _state.value.copy(uiVisible = !_state.value.uiVisible) }

    fun setUiVisible(visible: Boolean) { _state.value = _state.value.copy(uiVisible = visible) }

    fun setAutoScrolling(enabled: Boolean) { _state.value = _state.value.copy(autoScrolling = enabled) }

    fun setTranslationVisible(visible: Boolean) { _state.value = _state.value.copy(translationVisible = visible) }

    fun onAction(action: ReaderAction) {
        when (action) {
            ReaderAction.NONE -> Unit
            ReaderAction.TOGGLE_UI -> toggleUi()
            ReaderAction.TOGGLE_AUTO_SCROLL -> setAutoScrolling(!state.value.autoScrolling)
            ReaderAction.OPEN_SETTINGS -> setUiVisible(true)
            ReaderAction.NEXT_PAGE -> setUiVisible(state.value.uiVisible)
            ReaderAction.PREV_PAGE -> setUiVisible(state.value.uiVisible)
            ReaderAction.NEXT_CHAPTER -> nextChapter()
            ReaderAction.PREV_CHAPTER -> previousChapter()
        }
    }

    fun updateSettings(transform: (ReaderSettings) -> ReaderSettings) {
        val updated = transform(_state.value.settings)
        _state.value = _state.value.copy(settings = updated)
        viewModelScope.launch { readerRepository.saveSeriesSettings(mangaId, updated) }
    }

    fun nextChapter() {
        val next = _state.value.nextChapter ?: return
        goToChapter(next)
    }

    fun previousChapter() {
        val previous = _state.value.previousChapter ?: return
        goToChapter(previous)
    }

    fun goToChapter(chapter: MangaChapter) {
        viewModelScope.launch {
            val manga = _state.value.manga ?: return@launch
            val current = _state.value.chapter
            if (current != null) {
                readerRepository.completeSession(manga, current, _state.value.pages.size, lastPageAt)
            }
            val pages = readerRepository.pages(chapter)
            _state.value = _state.value.copy(
                chapter = chapter,
                pages = pages,
                currentPage = 0,
                initialPage = 0,
                loading = false,
                autoScrolling = false,
            )
            mangaRepository.saveProgress(manga, chapter, 0, pages.size)
        }
    }

    fun finishSession() {
        val manga = _state.value.manga ?: return
        val chapter = _state.value.chapter ?: return
        viewModelScope.launch {
            readerRepository.completeSession(manga, chapter, _state.value.pages.size, lastPageAt)
        }
    }
}
