package app.kurenai.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.kurenai.core.data.MangaRepository
import app.kurenai.core.data.SearchRepository
import app.kurenai.core.model.Manga
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val searchRepository: SearchRepository,
    private val mangaRepository: MangaRepository,
) : ViewModel() {
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()
    private val _results = MutableStateFlow<List<Manga>>(emptyList())
    val results: StateFlow<List<Manga>> = _results.asStateFlow()
    private val _recent = MutableStateFlow<List<String>>(emptyList())
    val recent: StateFlow<List<String>> = _recent.asStateFlow()
    private val _searching = MutableStateFlow(false)
    val searching: StateFlow<Boolean> = _searching.asStateFlow()
    private var searchJob: Job? = null

    init {
        viewModelScope.launch { _recent.value = searchRepository.recentQueries.first() }
    }

    fun setQuery(value: String) { _query.value = value }

    fun search(includeSources: Boolean = false) {
        val q = _query.value.trim()
        if (q.isBlank()) return
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _searching.value = true
            val local = searchRepository.search(q).first()
            _results.value = local
            searchRepository.remember(q)
            if (includeSources) {
                val remote = mutableListOf<Manga>()
                val filters = app.kurenai.core.model.SearchFilters(query = q)
                // Results arrive source-by-source, so the grid fills in while
                // slower parsers are still working instead of freezing for 15s.
                mangaRepository.searchAll(filters, limitPerSource = 16, maxSources = 24).collect { chunk ->
                    if (chunk.results.isEmpty()) return@collect
                    remote += chunk.results
                    _results.value = (local + remote).distinctBy { it.id }
                    chunk.results.forEach { mangaRepository.persist(it) }
                }
            }
            _searching.value = false
            _recent.value = searchRepository.recentQueries.first()
        }
    }

    fun useRecent(value: String) {
        _query.value = value
        search()
    }

    fun clearHistory() = viewModelScope.launch { searchRepository.clearHistory(); _recent.value = emptyList() }
}
