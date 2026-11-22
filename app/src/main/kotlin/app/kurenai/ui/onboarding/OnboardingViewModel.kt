package app.kurenai.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.kurenai.core.data.SourceRepository
import app.kurenai.core.datastore.SettingsStore
import app.kurenai.core.model.ContentRating
import app.kurenai.core.model.Palette
import app.kurenai.core.model.SourceInfo
import app.kurenai.core.model.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

data class OnboardingUiState(
    val step: Int = 0,
    val sources: List<SourceInfo> = emptyList(),
    val selectedLanguages: Set<String> = emptySet(),
    val accepted: Boolean = false,
    val systemLanguage: String = Locale.getDefault().language,
    val saving: Boolean = false,
) {
    val enabledCount: Int get() = sources.count { it.language in selectedLanguages || it.language.isBlank() }
}

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val sourceRepository: SourceRepository,
    private val settingsStore: SettingsStore,
) : ViewModel() {
    private val _state = MutableStateFlow(OnboardingUiState())
    val state: StateFlow<OnboardingUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val sources = sourceRepository.sources.first()
            val language = Locale.getDefault().language.lowercase()
            val preferred = sources.filter { it.language == language }.map { it.language }.toSet().ifEmpty { setOf("en") }
            _state.value = _state.value.copy(sources = sources, selectedLanguages = preferred, systemLanguage = language)
        }
    }

    fun next() { _state.value = _state.value.copy(step = (_state.value.step + 1).coerceAtMost(3)) }
    fun back() { _state.value = _state.value.copy(step = (_state.value.step - 1).coerceAtLeast(0)) }
    fun setAccepted(value: Boolean) { _state.value = _state.value.copy(accepted = value) }

    fun toggleLanguage(language: String) {
        val current = _state.value.selectedLanguages
        _state.value = _state.value.copy(
            selectedLanguages = if (language in current) current - language else current + language,
        )
    }

    fun selectAllLanguages() { _state.value = _state.value.copy(selectedLanguages = _state.value.sources.map { it.language }.toSet()) }

    fun finish(themeMode: ThemeMode, palette: Palette, dynamicColor: Boolean, amoled: Boolean, onDone: () -> Unit) {
        viewModelScope.launch {
            _state.value = _state.value.copy(saving = true)
            val sources = _state.value.sources
            val enabledLanguages = _state.value.selectedLanguages
            settingsStore.update {
                it.copy(
                    onboardingComplete = true,
                    disclaimerAccepted = true,
                    adultContentUnlocked = false,
                    theme = it.theme.copy(mode = themeMode, palette = palette, dynamicColor = dynamicColor, amoled = amoled),
                )
            }
            settingsStore.updateSources {
                it.copy(
                    enabledLanguages = enabledLanguages,
                    disabledSourceIds = sources.filter { source -> source.language.isNotBlank() && source.language !in enabledLanguages }.map { source -> source.id }.toSet(),
                    enabledRatings = setOf(ContentRating.SAFE, ContentRating.SUGGESTIVE),
                )
            }
            _state.value = _state.value.copy(saving = false)
            onDone()
        }
    }
}
