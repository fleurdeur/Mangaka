package app.kurenai.feature.more

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.kurenai.core.data.BackupRepository
import app.kurenai.core.data.DownloadRepository
import app.kurenai.core.data.LibraryRepository
import app.kurenai.core.data.RecommendationEngine
import app.kurenai.core.data.SourceRepository
import app.kurenai.core.data.TrackerRepository
import app.kurenai.core.datastore.SecureSecretStore
import app.kurenai.core.datastore.SettingsStore
import app.kurenai.core.model.AppSettings
import app.kurenai.core.model.DownloadTask
import app.kurenai.core.model.ReadingStats
import app.kurenai.core.model.SourceHealth
import app.kurenai.core.model.SourceInfo
import app.kurenai.core.model.TrackerService
import app.kurenai.core.model.YearInReview
import app.kurenai.core.network.KurenaiHttp
import app.kurenai.core.network.NetworkLog
import app.kurenai.core.network.ChallengeDiagnostic
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Year
import javax.inject.Inject

data class MoreUiState(
    val sources: List<SourceInfo> = emptyList(),
    val downloads: List<DownloadTask> = emptyList(),
    val stats: ReadingStats = ReadingStats(),
    val yearReview: YearInReview? = null,
    val challenge: ChallengeDiagnostic = ChallengeDiagnostic(),
    val taste: Map<String, Float> = emptyMap(),
    val settings: AppSettings = AppSettings(),
    val message: String? = null,
)

@HiltViewModel
class MoreViewModel @Inject constructor(
    private val sourceRepository: SourceRepository,
    private val downloadRepository: DownloadRepository,
    private val libraryRepository: LibraryRepository,
    private val recommendationEngine: RecommendationEngine,
    private val trackerRepository: TrackerRepository,
    private val backupRepository: BackupRepository,
    private val settingsStore: SettingsStore,
    private val secrets: SecureSecretStore,
    private val http: KurenaiHttp,
) : ViewModel() {
    private val message = MutableStateFlow<String?>(null)
    private val yearReview = MutableStateFlow<YearInReview?>(null)
    private val taste = MutableStateFlow<Map<String, Float>>(emptyMap())
    private val stats = MutableStateFlow(ReadingStats())

    val state: StateFlow<MoreUiState> = combine(
        sourceRepository.sources,
        downloadRepository.observeAll(),
        stats,
        taste,
        http.diagnosticsFlow,
    ) { sources, downloads, statsValue, tasteValue, challenge ->
        MoreUiState(
            sources = sources,
            downloads = downloads,
            stats = statsValue,
            taste = tasteValue,
            challenge = challenge,
            yearReview = yearReview.value,
            message = message.value,
        )
    }.combine(settingsStore.settings) { current, settings ->
        current.copy(settings = settings, yearReview = yearReview.value, message = message.value)
    }.combine(message) { current, msg ->
        current.copy(message = msg)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MoreUiState())

    init {
        refreshStats()
        refreshTaste()
    }

    fun refreshStats() = viewModelScope.launch {
        stats.value = libraryRepository.stats()
        yearReview.value = libraryRepository.yearInReview(Year.now().value)
    }

    fun refreshTaste() = viewModelScope.launch {
        taste.value = recommendationEngine.tasteProfile()
    }

    fun setSourceEnabled(id: String, enabled: Boolean) = viewModelScope.launch {
        sourceRepository.setEnabled(id, enabled)
    }

    fun setLanguage(language: String, enabled: Boolean) = viewModelScope.launch {
        sourceRepository.setLanguageEnabled(language, enabled)
    }

    fun setAdultUnlocked(unlocked: Boolean) = viewModelScope.launch {
        sourceRepository.setAdultUnlocked(unlocked)
    }

    fun resetSourceHealth(id: String) = viewModelScope.launch {
        sourceRepository.clearHealth(id)
        message.value = "Health reset for $id"
    }

    fun repairSources() = viewModelScope.launch {
        val count = sourceRepository.repairAutoDisabled()
        message.value = if (count == 0) "No automatically disabled sources to repair." else "Re-enabled $count sources. Try them again."
    }

    fun clearCookies() {
        http.cookieJar().clear()
        message.value = "Cleared encrypted cookies for all domains"
    }

    fun setChallengeHandling(enabled: Boolean) = viewModelScope.launch {
        settingsStore.update { it.copy(challengeHandlingEnabled = enabled) }
    }

    fun setDoh(enabled: Boolean) = viewModelScope.launch {
        settingsStore.update { it.copy(dohEnabled = enabled) }
    }

    fun setRecommendations(enabled: Boolean) = viewModelScope.launch {
        settingsStore.update { it.copy(recommendationsEnabled = enabled) }
    }

    fun setIncognito(enabled: Boolean) = viewModelScope.launch {
        settingsStore.update { it.copy(incognito = enabled) }
    }

    fun cancelDownload(id: String) = viewModelScope.launch { downloadRepository.cancel(id) }
    fun retryDownload(id: String) = viewModelScope.launch { downloadRepository.retry(id) }
    fun removeDownload(id: String) = viewModelScope.launch { downloadRepository.remove(id) }
    fun clearFinishedDownloads() = viewModelScope.launch { downloadRepository.clearFinished() }

    fun syncAllTrackers() = viewModelScope.launch {
        val results = trackerRepository.syncAll()
        message.value = if (results.isEmpty()) "No tracker links yet." else results.joinToString { "${it.service.title}: ${it.message}" }
    }

    fun saveTrackerToken(service: TrackerService, token: String) {
        secrets.put("tracker_${service.name.lowercase()}_token", token.ifBlank { null })
        message.value = "${service.title} token saved securely"
    }

    fun saveAiSettings(endpoint: String, model: String, key: String, enabled: Boolean) {
        secrets.put("ai_endpoint", endpoint.ifBlank { null })
        secrets.put("ai_model", model.ifBlank { null })
        secrets.put("ai_api_key", key.ifBlank { null })
        secrets.put("ai_enabled", enabled.toString())
        message.value = if (enabled) "Cloud librarian enabled" else "Cloud librarian disabled; local ranking stays on"
    }

    fun getSecret(key: String): String? = secrets.get(key)

    fun exportBackup(encrypt: Boolean, password: String?, onReady: (String) -> Unit) = viewModelScope.launch {
        val data = backupRepository.export(encrypt, password)
        onReady(data)
    }

    fun importBackup(data: String, password: String?, onDone: (String) -> Unit) = viewModelScope.launch {
        runCatching { backupRepository.import(data, password) }
            .onSuccess { onDone("Restored ${it.summary}") }
            .onFailure { onDone("Restore failed: ${it.message}") }
    }

    fun clearMessage() { message.value = null }

    fun networkLog(): List<NetworkLog.Entry> = NetworkLog.recent().takeLast(80)
}
