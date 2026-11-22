package app.kurenai.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.kurenai.core.datastore.SettingsStore
import app.kurenai.core.model.AppSettings
import app.kurenai.core.model.Palette
import app.kurenai.core.model.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
    val loading: Boolean = true,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsStore: SettingsStore,
) : ViewModel() {
    val state: StateFlow<SettingsUiState> = settingsStore.settings
        .map { SettingsUiState(it, loading = false) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun update(transform: (AppSettings) -> AppSettings) = viewModelScope.launch {
        settingsStore.update(transform)
    }

    fun setThemeMode(mode: ThemeMode) = update { it.copy(theme = it.theme.copy(mode = mode)) }
    fun setPalette(palette: Palette) = update { it.copy(theme = it.theme.copy(palette = palette)) }
}
