package app.kurenai.feature.animator

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.kurenai.core.datastore.SettingsStore
import app.kurenai.core.model.AnimatedChapterSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AnimatedSettingsViewModel @Inject constructor(
    private val settingsStore: SettingsStore,
) : ViewModel() {
    private val _state = MutableStateFlow(AnimatedChapterSettings())
    val state: StateFlow<AnimatedChapterSettings> = _state.asStateFlow()

    init {
        viewModelScope.launch { _state.value = settingsStore.settings.first().animatedChapters }
    }

    fun update(transform: (AnimatedChapterSettings) -> AnimatedChapterSettings) {
        val updated = transform(_state.value)
        _state.value = updated
        viewModelScope.launch { settingsStore.update { it.copy(animatedChapters = updated) } }
    }
}
