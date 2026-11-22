package app.kurenai.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.kurenai.core.model.AppSettings
import app.kurenai.core.model.SourcePreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "kurenai_settings")

@Singleton
class DataStoreSettingsStore @Inject constructor(
    @ApplicationContext private val context: Context,
) : SettingsStore {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val settingsKey = stringPreferencesKey("app_settings_json")
    private val sourcesKey = stringPreferencesKey("source_preferences_json")

    override val settings: Flow<AppSettings> = context.settingsDataStore.data
        .catch { e -> if (e is IOException) emit(androidx.datastore.preferences.core.emptyPreferences()) else throw e }
        .map { prefs -> decode(prefs[settingsKey], AppSettings()) }

    override val sourcePreferences: Flow<SourcePreferences> = context.settingsDataStore.data
        .catch { e -> if (e is IOException) emit(androidx.datastore.preferences.core.emptyPreferences()) else throw e }
        .map { prefs -> decode(prefs[sourcesKey], SourcePreferences()) }

    override suspend fun update(transform: (AppSettings) -> AppSettings) {
        context.settingsDataStore.edit { prefs ->
            val current = decode(prefs[settingsKey], AppSettings())
            prefs[settingsKey] = json.encodeToString(AppSettings.serializer(), transform(current))
        }
    }

    override suspend fun updateSources(transform: (SourcePreferences) -> SourcePreferences) {
        context.settingsDataStore.edit { prefs ->
            val current = decode(prefs[sourcesKey], SourcePreferences())
            prefs[sourcesKey] = json.encodeToString(SourcePreferences.serializer(), transform(current))
        }
    }

    override suspend fun replaceAll(settings: AppSettings, sources: SourcePreferences) {
        context.settingsDataStore.edit { prefs ->
            prefs[settingsKey] = json.encodeToString(AppSettings.serializer(), settings)
            prefs[sourcesKey] = json.encodeToString(SourcePreferences.serializer(), sources)
        }
    }

    private fun <T> decode(raw: String?, fallback: T): T {
        if (raw.isNullOrBlank()) return fallback
        return runCatching {
            @Suppress("UNCHECKED_CAST")
            when (fallback) {
                is AppSettings -> json.decodeFromString(AppSettings.serializer(), raw)
                is SourcePreferences -> json.decodeFromString(SourcePreferences.serializer(), raw)
                else -> fallback
            } as T
        }.getOrDefault(fallback)
    }
}
