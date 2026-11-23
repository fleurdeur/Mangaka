package app.kurenai.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.kurenai.core.data.ContinueReadingItem
import app.kurenai.core.data.LibraryRepository
import app.kurenai.core.data.MangaRepository
import app.kurenai.core.data.SearchRepository
import app.kurenai.core.model.Category
import app.kurenai.core.model.LibraryEntry
import app.kurenai.core.model.Manga
import app.kurenai.core.model.ReadingStats
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LibraryUiState(
    val favorites: List<LibraryEntry> = emptyList(),
    val continueReading: List<ContinueReadingItem> = emptyList(),
    val categories: List<Category> = emptyList(),
    val selectedCategoryId: Long? = null,
    val searchQuery: String = "",
    val searchResults: List<Manga> = emptyList(),
    val stats: ReadingStats = ReadingStats(),
    val loading: Boolean = true,
)

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val libraryRepository: LibraryRepository,
    private val mangaRepository: MangaRepository,
    private val searchRepository: SearchRepository,
) : ViewModel() {
    private val query = MutableStateFlow("")
    private val selectedCategory = MutableStateFlow<Long?>(null)
    private val searchResults = MutableStateFlow<List<Manga>>(emptyList())
    private var searchJob: Job? = null

    val uiState: StateFlow<LibraryUiState> = combine(
        libraryRepository.favorites,
        libraryRepository.history,
        libraryRepository.categories,
        query,
    ) { favorites, history, categories, queryValue ->
        LibraryUiState(
            favorites = favorites,
            continueReading = history,
            categories = categories,
            searchQuery = queryValue,
            loading = false,
        )
    }.combine(selectedCategory) { state, categoryId ->
        state.copy(selectedCategoryId = categoryId)
    }.combine(searchResults) { state, results ->
        state.copy(searchResults = results)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryUiState())

    init {
        viewModelScope.launch { libraryRepository.ensureDefaultCategories() }
    }

    fun setQuery(value: String) {
        query.value = value
        searchJob?.cancel()
        if (value.isBlank()) {
            searchResults.value = emptyList()
            return
        }
        searchJob = viewModelScope.launch {
            // Debounce keystrokes, then run the FTS query off the main thread.
            delay(140)
            searchResults.value = searchRepository.search(value).first()
        }
    }

    fun selectCategory(id: Long?) { selectedCategory.value = id }

    fun toggleFavorite(manga: Manga) = viewModelScope.launch { mangaRepository.toggleFavorite(manga) }

    fun createCategory(name: String) = viewModelScope.launch { libraryRepository.createCategory(name) }

    fun addToCategory(mangaId: String, categoryId: Long) = viewModelScope.launch {
        libraryRepository.addToCategory(mangaId, categoryId)
    }

    fun deleteCategory(id: Long) = viewModelScope.launch { libraryRepository.deleteCategory(id) }
}
