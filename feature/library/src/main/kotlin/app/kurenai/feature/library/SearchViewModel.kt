package app.kurenai.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.kurenai.core.data.MangaRepository
import app.kurenai.core.data.SearchRepository
import app.kurenai.core.data.SourceRepository
import app.kurenai.core.model.Manga
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val searchRepository: SearchRepository,
    private val mangaRepository: MangaRepository,
    private val sourceRepository: SourceRepository,
) : ViewModel() {
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()
    private val _results = MutableStateFlow<List<Manga>>(emptyList())
    val results: StateFlow<List<Manga>> = _results.asStateFlow()
    private val _recent = MutableStateFlow<List<String>>(emptyList())
    val recent: StateFlow<List<String>> = _recent.asStateFlow()
    private val _searching = MutableStateFlow(false)
    val searching: StateFlow<Boolean> = _searching.asStateFlow()

    init {
        viewModelScope.launch { _recent.value = searchRepository.recentQueries.first() }
    }

    fun setQuery(value: String) { _query.value = value }

    fun search(includeSources: Boolean = false) {
        val q = _query.value.trim()
        if (q.isBlank()) return
        viewModelScope.launch {
            _searching.value = true
            _results.value = searchRepository.search(q).first()
            searchRepository.remember(q)
            if (includeSources) {
                val visible = sourceRepository.visibleSources().take(12)
                val remote = visible.flatMap { source ->
                    runCatching { source.search(app.kurenai.core.model.SearchFilters(query = q), 0, 12) }.getOrDefault(emptyList())
                }
                _results.value = (_results.value + remote).distinctBy { it.id }
                remote.forEach { mangaRepository.persist(it) }
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
