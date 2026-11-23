package app.kurenai.core.source

import app.kurenai.core.model.ContentKind
import app.kurenai.core.model.FilterOptions
import app.kurenai.core.model.Manga
import app.kurenai.core.model.MangaChapter
import app.kurenai.core.model.MangaPage
import app.kurenai.core.model.SearchFilters
import app.kurenai.core.model.SourceHealth
import app.kurenai.core.model.SourceInfo
import app.kurenai.core.model.SourcePreferences
import app.kurenai.core.model.Tag

/**
 * The stable contract every source implements. Kotatsu parsers are adapted to this
 * interface by [app.kurenai.core.parsers.KotatsuMangaSource], while first-party
 * parsers can implement it directly.
 */
interface MangaSource {
    val info: SourceInfo

    suspend fun search(filters: SearchFilters, offset: Int, limit: Int): List<Manga>

    suspend fun details(manga: Manga): Manga

    suspend fun chapters(manga: Manga): List<MangaChapter>

    suspend fun pages(chapter: MangaChapter): List<MangaPage>

    suspend fun resolveImageUrl(page: MangaPage): String

    suspend fun filterOptions(): FilterOptions = FilterOptions()

    suspend fun resolveLink(url: String): Manga? = null

    fun requestHeaders(): Map<String, String> = emptyMap()

    /**
     * Rotate to another mirror declared by the parser when the current host
     * cannot be resolved. Returns true when a new host was selected.
     */
    suspend fun tryAlternateDomain(): Boolean = false
}

interface SourceRegistry {
    val sources: List<MangaSource>
    fun source(id: String): MangaSource?
    fun refresh()
}

/** Applies user preferences and health state to the full source catalogue. */
class SourceCatalog(
    private val registry: SourceRegistry,
    private val healthProvider: (String) -> SourceHealth = { SourceHealth.UNKNOWN },
) {
    fun all(): List<MangaSource> = registry.sources

    fun visible(preferences: SourcePreferences): List<MangaSource> = registry.sources.filter { source ->
        val health = if (source.info.health == SourceHealth.DISABLED) SourceHealth.DISABLED else healthProvider(source.info.id)
        source.info.id !in preferences.disabledSourceIds &&
            (preferences.enabledLanguages.isEmpty() || source.info.language in preferences.enabledLanguages || source.info.language.isBlank()) &&
            (preferences.enabledKinds.isEmpty() || source.info.kind in preferences.enabledKinds) &&
            (source.info.nsfw.not() || preferences.enabledRatings.contains(app.kurenai.core.model.ContentRating.ADULT)) &&
            (!preferences.showOnlyHealthy || health == SourceHealth.HEALTHY || health == SourceHealth.UNKNOWN)
    }.sortedWith(
        when (preferences.sortBy) {
            app.kurenai.core.model.SourceSort.NAME -> compareBy { it.info.name.lowercase() }
            app.kurenai.core.model.SourceSort.LANGUAGE -> compareBy({ it.info.language }, { it.info.name })
            app.kurenai.core.model.SourceSort.KIND -> compareBy({ it.info.kind.ordinal }, { it.info.name })
            app.kurenai.core.model.SourceSort.HEALTH -> compareBy({ healthProvider(it.info.id).ordinal }, { it.info.name })
        },
    )

    fun groupedByLanguage(preferences: SourcePreferences): Map<String, List<MangaSource>> =
        visible(preferences).groupBy { it.info.language.ifBlank { "multi" } }

    fun defaultsForLanguage(languageTag: String, limit: Int = 40): List<MangaSource> {
        val language = languageTag.substringBefore('-').lowercase()
        return registry.sources
            .filter { it.info.kind != ContentKind.LIGHT_NOVEL }
            .sortedWith(
                compareByDescending<MangaSource> { it.info.language == language }
                    .thenByDescending { it.info.license == app.kurenai.core.model.SourceLicense.OFFICIAL }
                    .thenBy { it.info.name },
            )
            .take(limit)
    }

    companion object {
        val PopularLanguages = listOf("en", "ja", "ko", "zh", "es", "fr", "pt", "ru", "id", "it", "de", "ar", "tr", "th", "vi")
    }
}

data class SourceSearchResult(
    val sources: List<MangaSource>,
    val filterOptions: Map<String, FilterOptions>,
    val tags: Map<String, List<Tag>>,
)
