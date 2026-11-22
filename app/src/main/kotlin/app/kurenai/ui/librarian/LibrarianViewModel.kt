package app.kurenai.ui.librarian

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.kurenai.core.data.AiAnswer
import app.kurenai.core.data.AiLibrarian
import app.kurenai.core.data.MangaRepository
import app.kurenai.core.data.SourceRepository
import app.kurenai.core.datastore.SettingsStore
import app.kurenai.core.model.Manga
import app.kurenai.core.model.SearchFilters
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LibrarianViewModel @Inject constructor(
    private val aiLibrarian: AiLibrarian,
    private val mangaRepository: MangaRepository,
    private val sourceRepository: SourceRepository,
    private val settingsStore: SettingsStore,
) : ViewModel() {
    private val _answer = MutableStateFlow<AiAnswer?>(null)
    val answer: StateFlow<AiAnswer?> = _answer.asStateFlow()
    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun ask(question: String) {
        if (question.isBlank()) return
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            val enabled = settingsStore.settings.first().recommendationsEnabled
            if (!enabled) {
                _error.value = "Recommendations are turned off in Settings → Privacy."
                _loading.value = false
                return@launch
            }
            val library = mangaRepository.observeFavorites().first()
            val chunks = mutableListOf<Manga>()
            sourceRepository.visibleSources().take(8).forEach { source ->
                runCatching { source.search(SearchFilters(query = question), 0, 10) }.getOrDefault(emptyList()).forEach { chunks += it }
            }
            runCatching { aiLibrarian.ask(question, (library + chunks).distinctBy { it.id }) }
                .onSuccess { _answer.value = it }
                .onFailure { _error.value = it.message ?: "The librarian is offline." }
            _loading.value = false
        }
    }
}
