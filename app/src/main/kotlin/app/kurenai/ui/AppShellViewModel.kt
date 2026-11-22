package app.kurenai.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.kurenai.core.datastore.SettingsStore
import app.kurenai.core.model.AppSettings
import app.kurenai.core.model.SourcePreferences
import app.kurenai.core.network.KurenaiHttp
import app.kurenai.core.network.challenge.InteractiveChallenge
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AppShellViewModel @Inject constructor(
    private val settingsStore: SettingsStore,
    private val http: KurenaiHttp,
) : ViewModel() {
    val challenge = http.challenges.current

    fun cancelChallenge() = http.challenges.cancel()

    val settings: StateFlow<AppSettings> = settingsStore.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    val sourcePreferences: StateFlow<SourcePreferences> = settingsStore.sourcePreferences
        .stateIn(viewModelScope, SharingStarted.Eagerly, SourcePreferences())

    fun update(transform: (AppSettings) -> AppSettings) = viewModelScope.launch {
        settingsStore.update(transform)
    }

    fun completeOnboarding() = update {
        it.copy(onboardingComplete = true, disclaimerAccepted = true)
    }

    fun setAdultUnlocked(unlocked: Boolean) = update {
        it.copy(adultContentUnlocked = unlocked)
    }
}
