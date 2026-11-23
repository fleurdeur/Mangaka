package app.kurenai.core.data

import app.kurenai.core.common.AppEvent
import app.kurenai.core.common.AppEventBus
import app.kurenai.core.database.dao.SourceHealthDao
import app.kurenai.core.database.entity.SourceHealthEntity
import app.kurenai.core.datastore.SettingsStore
import app.kurenai.core.model.ContentRating
import app.kurenai.core.model.SourceHealth
import app.kurenai.core.model.SourceInfo
import app.kurenai.core.model.SourcePreferences
import app.kurenai.core.model.SourceSort
import app.kurenai.core.source.MangaSource
import app.kurenai.core.source.SourceCatalog
import app.kurenai.core.source.SourceRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SourceRepository @Inject constructor(
    private val settingsStore: SettingsStore,
    private val registry: SourceRegistry,
    private val healthDao: SourceHealthDao,
    private val eventBus: AppEventBus,
) {
    val preferences: Flow<SourcePreferences> = settingsStore.sourcePreferences

    val health: Flow<Map<String, SourceHealthEntity>> = healthDao.observeAll()
        .map { list -> list.associateBy { it.sourceId } }

    val sources: Flow<List<SourceInfo>> = combine(
        preferences,
        health,
    ) { prefs, healthMap ->
        registry.sources.map { source ->
            val record = healthMap[source.info.id]
            val health = record?.health ?: source.info.health
            val enabled = source.info.id !in prefs.disabledSourceIds &&
                (prefs.enabledLanguages.isEmpty() || source.info.language in prefs.enabledLanguages || source.info.language.isBlank()) &&
                (prefs.enabledKinds.isEmpty() || source.info.kind in prefs.enabledKinds) &&
                (!source.info.nsfw || ContentRating.ADULT in prefs.enabledRatings) &&
                (!prefs.showOnlyHealthy || health == SourceHealth.HEALTHY || health == SourceHealth.UNKNOWN)
            source.info.copy(health = health, domain = source.info.domain)
                .let { if (enabled) it else it.copy(health = if (health == SourceHealth.DISABLED) health else SourceHealth.DISABLED) }
        }.sortedWith(
            when (prefs.sortBy) {
                SourceSort.NAME -> compareBy { it.name.lowercase() }
                SourceSort.LANGUAGE -> compareBy({ it.language }, { it.name })
                SourceSort.KIND -> compareBy({ it.kind.ordinal }, { it.name })
                SourceSort.HEALTH -> compareBy({ it.health.ordinal }, { it.name })
            },
        )
    }.flowOn(Dispatchers.Default)

    suspend fun visibleSources(): List<MangaSource> = withContext(Dispatchers.Default) {
        val prefs = preferences.first()
        val healthMap = health.first()
        registry.sources.filter { source ->
            val record = healthMap[source.info.id]
            val health = record?.health ?: source.info.health
            source.info.id !in prefs.disabledSourceIds &&
                (prefs.enabledLanguages.isEmpty() || source.info.language in prefs.enabledLanguages || source.info.language.isBlank()) &&
                (prefs.enabledKinds.isEmpty() || source.info.kind in prefs.enabledKinds) &&
                (!source.info.nsfw || ContentRating.ADULT in prefs.enabledRatings) &&
                (!prefs.showOnlyHealthy || health == SourceHealth.HEALTHY || health == SourceHealth.UNKNOWN) &&
                health != SourceHealth.DISABLED
        }
    }

    suspend fun allSources(): List<MangaSource> = withContext(Dispatchers.Default) { registry.sources }

    /**
     * Sources worth asking first for a global search. Healthy sources in the
     * device language come first, dead/disabled ones are skipped, and the list is
     * capped before any network work starts. This is the main reason a search
     * across dozens of parsers can finish in seconds instead of half a minute.
     */
    suspend fun searchCandidates(limit: Int): List<MangaSource> = withContext(Dispatchers.Default) {
        val prefs = preferences.first()
        val healthMap = health.first()
        val deviceLanguage = Locale.getDefault().language.lowercase()
        registry.sources.asSequence()
            .filter { source ->
                val record = healthMap[source.info.id]
                val health = record?.health ?: source.info.health
                source.info.id !in prefs.disabledSourceIds &&
                    health != SourceHealth.DISABLED &&
                    source.info.health != SourceHealth.BROKEN &&
                    (prefs.enabledLanguages.isEmpty() || source.info.language in prefs.enabledLanguages || source.info.language.isBlank()) &&
                    (prefs.enabledKinds.isEmpty() || source.info.kind in prefs.enabledKinds) &&
                    (!source.info.nsfw || ContentRating.ADULT in prefs.enabledRatings) &&
                    (!prefs.showOnlyHealthy || health == SourceHealth.HEALTHY || health == SourceHealth.UNKNOWN)
            }
            .sortedWith(
                compareBy<MangaSource> {
                    val record = healthMap[it.info.id]
                    when (record?.health ?: it.info.health) {
                        SourceHealth.HEALTHY -> 0
                        SourceHealth.UNKNOWN -> 1
                        SourceHealth.DEGRADED -> 2
                        SourceHealth.BROKEN, SourceHealth.DISABLED -> 3
                    }
                }.thenBy { source ->
                    // Prefer the user's language, then English as a useful fallback.
                    when {
                        source.info.language == deviceLanguage -> 0
                        source.info.language == "en" -> 1
                        else -> 2
                    }
                }.thenBy { it.info.name.lowercase() },
            )
            .take(limit)
            .toList()
    }

    suspend fun source(id: String): MangaSource? = withContext(Dispatchers.Default) { registry.source(id) }

    suspend fun setEnabled(sourceId: String, enabled: Boolean) {
        settingsStore.updateSources { prefs ->
            prefs.copy(disabledSourceIds = if (enabled) prefs.disabledSourceIds - sourceId else prefs.disabledSourceIds + sourceId)
        }
        eventBus.emit(AppEvent.SourcesChanged)
    }

    suspend fun setLanguageEnabled(language: String, enabled: Boolean) {
        settingsStore.updateSources { prefs ->
            prefs.copy(enabledLanguages = if (enabled) prefs.enabledLanguages + language else prefs.enabledLanguages - language)
        }
    }

    suspend fun setAdultUnlocked(unlocked: Boolean) {
        settingsStore.updateSources { prefs ->
            prefs.copy(
                enabledRatings = if (unlocked) {
                    prefs.enabledRatings + ContentRating.ADULT
                } else {
                    prefs.enabledRatings - ContentRating.ADULT
                },
            )
        }
    }

    suspend fun setSort(sort: SourceSort) {
        settingsStore.updateSources { it.copy(sortBy = sort) }
    }

    suspend fun setShowOnlyHealthy(value: Boolean) {
        settingsStore.updateSources { it.copy(showOnlyHealthy = value) }
    }

    suspend fun recordSuccess(sourceId: String, latencyMs: Long) {
        val previous = healthDao.get(sourceId) ?: SourceHealthEntity(sourceId)
        healthDao.upsert(
            previous.copy(
                consecutiveFailures = 0,
                totalSuccesses = previous.totalSuccesses + 1,
                lastCheckedAt = System.currentTimeMillis(),
                lastError = null,
                averageLatencyMs = if (previous.averageLatencyMs == 0L) latencyMs else (previous.averageLatencyMs * 4 + latencyMs) / 5,
            ),
        )
    }

    suspend fun recordFailure(sourceId: String, error: String) {
        val previous = healthDao.get(sourceId) ?: SourceHealthEntity(sourceId)
        val failures = previous.consecutiveFailures + 1
        val autoDisable = failures >= 5
        healthDao.upsert(
            previous.copy(
                consecutiveFailures = failures,
                totalFailures = previous.totalFailures + 1,
                lastCheckedAt = System.currentTimeMillis(),
                lastError = error.take(500),
                disabledAutomatically = autoDisable,
            ),
        )
        if (autoDisable) {
            settingsStore.updateSources { it.copy(disabledSourceIds = it.disabledSourceIds + sourceId) }
        }
    }

    suspend fun clearHealth(sourceId: String) {
        val previous = healthDao.get(sourceId) ?: SourceHealthEntity(sourceId)
        healthDao.upsert(SourceHealthEntity(sourceId))
        settingsStore.updateSources { it.copy(disabledSourceIds = it.disabledSourceIds - sourceId) }
    }

    /**
     * Re-enable every source that Kurenai disabled automatically. Sources the
     * user disabled by hand are left alone.
     */
    suspend fun repairAutoDisabled(): Int {
        val records = health.first().values.filter { it.disabledAutomatically }
        if (records.isEmpty()) return 0
        records.forEach { healthDao.upsert(SourceHealthEntity(it.sourceId)) }
        val ids = records.mapTo(mutableSetOf()) { it.sourceId }
        settingsStore.updateSources { prefs -> prefs.copy(disabledSourceIds = prefs.disabledSourceIds - ids) }
        eventBus.emit(AppEvent.SourcesChanged)
        return ids.size
    }

    fun catalog(): SourceCatalog = SourceCatalog(
        registry = registry,
        healthProvider = { id -> registry.source(id)?.info?.health ?: SourceHealth.UNKNOWN },
    )
}
