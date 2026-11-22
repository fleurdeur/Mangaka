package app.kurenai.core.datastore

import app.kurenai.core.model.AppSettings
import app.kurenai.core.model.SourcePreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface SettingsStore {
    val settings: Flow<AppSettings>
    val sourcePreferences: Flow<SourcePreferences>
    suspend fun update(transform: (AppSettings) -> AppSettings)
    suspend fun updateSources(transform: (SourcePreferences) -> SourcePreferences)
    suspend fun replaceAll(settings: AppSettings, sources: SourcePreferences)
}
