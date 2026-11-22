package app.kurenai.feature.browse

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.kurenai.core.data.MangaRepository
import app.kurenai.core.data.SourceRepository
import app.kurenai.core.data.SourceSearchChunk
import app.kurenai.core.model.ContentKind
import app.kurenai.core.model.FilterOptions
import app.kurenai.core.model.Manga
import app.kurenai.core.model.MangaStatus
import app.kurenai.core.model.SearchFilters
import app.kurenai.core.model.SortOrder
import app.kurenai.core.model.SourceHealth
import app.kurenai.core.model.SourceInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

data class BrowseUiState(
    val sources: List<SourceInfo> = emptyList(),
    val selectedSourceId: String? = null,
    val query: String = "",
    val filters: SearchFilters = SearchFilters(),
    val options: FilterOptions = FilterOptions(),
    val results: List<Manga> = emptyList(),
    val sourceChunks: List<SourceSearchChunk> = emptyList(),
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val searchAllSources: Boolean = false,
    val error: String? = null,
    val page: Int = 0,
) {
    val selectedSource: SourceInfo? get() = sources.firstOrNull { it.id == selectedSourceId }
}

@HiltViewModel
class BrowseViewModel @Inject constructor(
    private val sourceRepository: SourceRepository,
    private val mangaRepository: MangaRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(BrowseUiState())
    val state: StateFlow<BrowseUiState> = _state.asStateFlow()
    private var searchJob: Job? = null

    init {
        viewModelScope.launch {
            val sources = sourceRepository.sources.first()
            val language = Locale.getDefault().language.lowercase()
            val default = sources.firstOrNull { it.language == language && it.health != SourceHealth.DISABLED && !it.nsfw }
                ?: sources.firstOrNull { it.language == "en" && it.health != SourceHealth.DISABLED && !it.nsfw }
                ?: sources.firstOrNull()
            _state.value = _state.value.copy(sources = sources, selectedSourceId = default?.id)
            default?.let { loadOptions(it.id) }
        }
    }

    fun selectSource(id: String) {
        _state.value = _state.value.copy(selectedSourceId = id, results = emptyList(), error = null, page = 0)
        loadOptions(id)
    }

    private fun loadOptions(id: String) {
        viewModelScope.launch {
            val options = runCatching { mangaRepository.filterOptions(id) }.getOrNull() ?: FilterOptions()
            _state.value = _state.value.copy(options = options)
        }
    }

    fun setQuery(value: String) {
        _state.value = _state.value.copy(query = value, filters = _state.value.filters.copy(query = value))
    }

    fun setSearchAllSources(value: Boolean) {
        _state.value = _state.value.copy(searchAllSources = value)
    }

    fun setSort(sort: SortOrder) {
        _state.value = _state.value.copy(filters = _state.value.filters.copy(sort = sort))
    }

    fun toggleTag(tag: String) {
        val filters = _state.value.filters
        val tags = if (tag in filters.tags) filters.tags - tag else filters.tags + tag
        _state.value = _state.value.copy(filters = filters.copy(tags = tags))
    }

    fun toggleStatus(status: MangaStatus) {
        val filters = _state.value.filters
        val statuses = if (status in filters.statuses) filters.statuses - status else filters.statuses + status
        _state.value = _state.value.copy(filters = filters.copy(statuses = statuses))
    }

    fun toggleKind(kind: ContentKind) {
        val filters = _state.value.filters
        val kinds = if (kind in filters.kinds) filters.kinds - kind else filters.kinds + kind
        _state.value = _state.value.copy(filters = filters.copy(kinds = kinds))
    }

    fun search() {
        searchJob?.cancel()
        val current = _state.value
        if (current.searchAllSources) {
            searchEverywhere(current)
        } else {
            val sourceId = current.selectedSourceId ?: return
            searchJob = viewModelScope.launch {
                _state.value = _state.value.copy(loading = true, error = null)
                runCatching { mangaRepository.search(sourceId, current.filters, 0, 40) }
                    .onSuccess { results ->
                        _state.value = _state.value.copy(results = results, loading = false, page = 0)
                    }
                    .onFailure { error ->
                        _state.value = _state.value.copy(loading = false, error = error.message ?: "Search failed")
                    }
            }
        }
    }

    private fun searchEverywhere(current: BrowseUiState) {
        searchJob = viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null, sourceChunks = emptyList(), results = emptyList())
            val chunks = mutableListOf<SourceSearchChunk>()
            mangaRepository.searchAll(current.filters, limitPerSource = 20, maxSources = 30).collect { chunk ->
                chunks += chunk
                _state.value = _state.value.copy(
                    sourceChunks = chunks.toList(),
                    results = chunks.flatMap { it.results }.distinctBy { it.id },
                    loading = true,
                )
            }
            _state.value = _state.value.copy(loading = false)
        }
    }

    fun openManga(manga: Manga, onReady: (String) -> Unit) {
        viewModelScope.launch {
            mangaRepository.persist(manga)
            onReady(manga.id)
        }
    }

    fun loadMore() {
        val current = _state.value
        if (current.searchAllSources || current.loading || current.loadingMore) return
        val sourceId = current.selectedSourceId ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(loadingMore = true)
            runCatching { mangaRepository.search(sourceId, current.filters, (current.page + 1) * 40, 40) }
                .onSuccess { results ->
                    _state.value = _state.value.copy(
                        results = (current.results + results).distinctBy { it.id },
                        loadingMore = false,
                        page = current.page + 1,
                    )
                }
                .onFailure {
                    _state.value = _state.value.copy(loadingMore = false)
                }
        }
    }
}
