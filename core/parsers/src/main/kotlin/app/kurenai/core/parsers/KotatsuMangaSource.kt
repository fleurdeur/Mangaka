@file:OptIn(org.koitharu.kotatsu.parsers.InternalParsersApi::class)

package app.kurenai.core.parsers

import app.kurenai.core.model.ContentKind
import app.kurenai.core.model.ContentRating
import app.kurenai.core.model.FilterOptions
import app.kurenai.core.model.Manga
import app.kurenai.core.model.MangaChapter
import app.kurenai.core.model.MangaPage
import app.kurenai.core.model.MangaStatus
import app.kurenai.core.model.SearchFilters
import app.kurenai.core.model.SortOrder
import app.kurenai.core.model.SourceHealth
import app.kurenai.core.model.SourceInfo
import app.kurenai.core.model.SourceLicense
import app.kurenai.core.model.Tag
import app.kurenai.core.source.MangaSource
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.koitharu.kotatsu.parsers.MangaParser
import org.koitharu.kotatsu.parsers.model.ContentRating as KContentRating
import org.koitharu.kotatsu.parsers.model.ContentType as KContentType
import org.koitharu.kotatsu.parsers.model.Manga as KManga
import org.koitharu.kotatsu.parsers.model.MangaChapter as KChapter
import org.koitharu.kotatsu.parsers.model.MangaListFilter
import org.koitharu.kotatsu.parsers.model.MangaListFilterOptions
import org.koitharu.kotatsu.parsers.model.MangaPage as KPage
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.model.MangaState as KState
import org.koitharu.kotatsu.parsers.model.MangaTag as KTag
import org.koitharu.kotatsu.parsers.model.SortOrder as KSortOrder
import java.util.Locale

/**
 * Adapter that exposes one Kotatsu parser as a native Kurenai source. Every source
 * keeps its own lazy parser instance so cookies and config stay isolated.
 */
class KotatsuMangaSource(
    val parserSource: MangaParserSource,
    private val loaderContext: KotatsuLoaderContext,
) : MangaSource {

    private val parser: MangaParser by lazy { loaderContext.newParserInstance(parserSource) }
    private val optionsMutex = Mutex()
    private var cachedOptions: MangaListFilterOptions? = null

    override val info: SourceInfo by lazy {
        SourceInfo(
            id = "kotatsu:${parserSource.name}",
            name = parserSource.title,
            language = parserSource.locale.ifBlank { "multi" },
            // Resolved lazily when the parser is first used. Instantiating every
            // parser here would make the first source list slow and memory-heavy.
            domain = "",
            kind = parserSource.contentType.toKind(),
            nsfw = parserSource.contentType == KContentType.HENTAI,
            license = SourceLicense.UNKNOWN,
            health = if (parserSource.isBroken) SourceHealth.BROKEN else SourceHealth.UNKNOWN,
            description = "Kotatsu parser · ${parserSource.contentType.name.lowercase().replace('_', ' ')}",
            version = "kotatsu-parsers",
        )
    }

    override suspend fun search(filters: SearchFilters, offset: Int, limit: Int): List<Manga> {
        val options = optionsMutex.withLock { cachedOptions ?: parser.getFilterOptions().also { cachedOptions = it } }
        val tags = options.availableTags.filter { tag ->
            filters.tags.isEmpty() || filters.tags.any { it.equals(tag.title, true) || it == tag.key }
        }.toSet()
        val excluded = options.availableTags.filter { tag ->
            filters.excludedTags.any { it.equals(tag.title, true) || it == tag.key }
        }.toSet()
        val states = filters.statuses.mapNotNull { it.toKotatsu() }.toSet()
        val ratings = filters.contentRatings.map { it.toKotatsu() }.toSet()
        val kinds = filters.kinds.map { it.toKotatsuType() }.toSet()
        val locale = filters.languages.firstOrNull()?.let { Locale.forLanguageTag(it) }
        val filter = MangaListFilter(
            query = filters.query.takeIf { it.isNotBlank() },
            tags = tags,
            tagsExclude = excluded,
            locale = locale,
            states = states,
            contentRating = ratings,
            types = kinds,
            year = filters.year ?: org.koitharu.kotatsu.parsers.model.YEAR_UNKNOWN,
        )
        val sort = filters.sort.toKotatsu()
        val list = parser.getList(offset, sort, filter)
        return list.map { it.toModel() }
    }

    override suspend fun details(manga: Manga): Manga {
        val details = parser.getDetails(manga.toKotatsu())
        return details.toModel()
    }

    override suspend fun chapters(manga: Manga): List<MangaChapter> {
        val details = parser.getDetails(manga.toKotatsu())
        return details.chapters.orEmpty().map { it.toModel(manga.id) }
    }

    override suspend fun pages(chapter: MangaChapter): List<MangaPage> {
        val kChapter = chapter.toKotatsu()
        val pages = parser.getPages(kChapter)
        return pages.mapIndexed { index, page ->
            val url = runCatching { parser.getPageUrl(page) }.getOrElse { page.url }
            MangaPage(
                index = index,
                url = url,
                preview = page.preview,
                sourceId = info.id,
                chapterId = chapter.id,
            )
        }
    }

    override suspend fun resolveImageUrl(page: MangaPage): String = page.url

    override suspend fun filterOptions(): FilterOptions {
        val options = optionsMutex.withLock { cachedOptions ?: parser.getFilterOptions().also { cachedOptions = it } }
        return FilterOptions(
            tags = options.availableTags.map { Tag(it.key, it.title, info.id) },
            statuses = options.availableStates.mapNotNull { it.toModel() }.toSet(),
            kinds = options.availableContentTypes.map { it.toKind() }.toSet(),
            contentRatings = options.availableContentRating.map { it.toModel() }.toSet(),
            languages = options.availableLocales.map { it.language }.toSet(),
            supportsSearch = parser.filterCapabilities.isSearchSupported,
            supportsMultipleTags = parser.filterCapabilities.isMultipleTagsSupported,
            supportsYear = parser.filterCapabilities.isYearSupported || parser.filterCapabilities.isYearRangeSupported,
        )
    }

    override suspend fun resolveLink(url: String): Manga? = runCatching {
        val httpUrl = okhttp3.HttpUrl.Companion.run { url.toHttpUrl() }
        parser.resolveLink(loaderContext.newLinkResolver(httpUrl), httpUrl)?.toModel()
    }.getOrNull()

    override fun requestHeaders(): Map<String, String> =
        runCatching { parser.getRequestHeaders().toMultimap().mapValues { it.value.firstOrNull().orEmpty() } }
            .getOrDefault(emptyMap())

    private fun KManga.toModel(): Manga = Manga(
        id = "kotatsu:${parserSource.name}:${id}",
        sourceId = "kotatsu:${parserSource.name}",
        title = title,
        altTitles = altTitles,
        url = url,
        publicUrl = publicUrl,
        coverUrl = coverUrl,
        largeCoverUrl = largeCoverUrl,
        description = description,
        authors = authors,
        tags = tags.mapTo(mutableSetOf()) { it.title },
        status = state?.toModel() ?: MangaStatus.UNKNOWN,
        kind = parserSource.contentType.toKind(),
        contentRating = contentRating?.toModel() ?: if (parserSource.contentType == KContentType.HENTAI) ContentRating.ADULT else ContentRating.SAFE,
        rating = rating,
        chapterCount = chapters?.size ?: 0,
        latestChapter = chapters?.maxByOrNull { it.uploadDate }?.numberString(),
        updatedAt = chapters?.maxOfOrNull { it.uploadDate } ?: 0L,
    )

    private fun KChapter.toModel(parentId: String): MangaChapter = MangaChapter(
        id = "kotatsu:${parserSource.name}:$id",
        mangaId = parentId,
        sourceId = "kotatsu:${parserSource.name}",
        title = title,
        number = number,
        volume = volume,
        url = url,
        scanlator = scanlator,
        uploadedAt = uploadDate,
        branch = branch,
        pageCount = 0,
    )

    private fun Manga.toKotatsu(): KManga {
        val remoteId = id.substringAfterLast(':').toLongOrNull() ?: 0L
        return KManga(
            id = remoteId,
            title = title,
            altTitles = altTitles,
            url = url,
            publicUrl = publicUrl,
            rating = rating,
            contentRating = contentRating.toKotatsu(),
            coverUrl = coverUrl,
            tags = emptySet(),
            state = status.toKotatsu(),
            authors = authors,
            largeCoverUrl = largeCoverUrl,
            description = description,
            chapters = null,
            source = parserSource,
        )
    }

    private fun MangaChapter.toKotatsu(): KChapter {
        val remoteId = id.substringAfterLast(':').toLongOrNull() ?: 0L
        return KChapter(
            id = remoteId,
            title = title,
            number = number,
            volume = volume,
            url = url,
            scanlator = scanlator,
            uploadDate = uploadedAt,
            branch = branch,
            source = parserSource,
        )
    }
}

private fun KContentType.toKind(): ContentKind = when (this) {
    KContentType.MANGA -> ContentKind.MANGA
    KContentType.MANHWA -> ContentKind.MANHWA
    KContentType.MANHUA -> ContentKind.MANHUA
    KContentType.HENTAI -> ContentKind.MANGA
    KContentType.COMICS -> ContentKind.COMIC
    KContentType.NOVEL -> ContentKind.LIGHT_NOVEL
    KContentType.ONE_SHOT -> ContentKind.ONE_SHOT
    KContentType.DOUJINSHI -> ContentKind.DOUJINSHI
    else -> ContentKind.OTHER
}

private fun ContentKind.toKotatsuType(): KContentType = when (this) {
    ContentKind.MANGA -> KContentType.MANGA
    ContentKind.MANHWA -> KContentType.MANHWA
    ContentKind.MANHUA -> KContentType.MANHUA
    ContentKind.WEBTOON -> KContentType.MANHWA
    ContentKind.COMIC -> KContentType.COMICS
    ContentKind.LIGHT_NOVEL -> KContentType.NOVEL
    ContentKind.ONE_SHOT -> KContentType.ONE_SHOT
    ContentKind.DOUJINSHI -> KContentType.DOUJINSHI
    ContentKind.OTHER -> KContentType.OTHER
}

private fun KContentRating.toModel(): ContentRating = when (this) {
    KContentRating.SAFE -> ContentRating.SAFE
    KContentRating.SUGGESTIVE -> ContentRating.SUGGESTIVE
    KContentRating.ADULT -> ContentRating.ADULT
}

private fun ContentRating.toKotatsu(): KContentRating = when (this) {
    ContentRating.SAFE -> KContentRating.SAFE
    ContentRating.SUGGESTIVE -> KContentRating.SUGGESTIVE
    ContentRating.ADULT -> KContentRating.ADULT
}

private fun MangaStatus.toKotatsu(): KState? = when (this) {
    MangaStatus.ONGOING -> KState.ONGOING
    MangaStatus.FINISHED -> KState.FINISHED
    MangaStatus.PAUSED -> KState.PAUSED
    MangaStatus.ABANDONED -> KState.ABANDONED
    MangaStatus.UPCOMING -> KState.UPCOMING
    MangaStatus.UNKNOWN -> null
}

private fun KState.toModel(): MangaStatus = when (this) {
    KState.ONGOING -> MangaStatus.ONGOING
    KState.FINISHED -> MangaStatus.FINISHED
    KState.PAUSED -> MangaStatus.PAUSED
    KState.ABANDONED -> MangaStatus.ABANDONED
    KState.UPCOMING -> MangaStatus.UPCOMING
    KState.RESTRICTED -> MangaStatus.UNKNOWN
}

private fun SortOrder.toKotatsu(): KSortOrder = when (this) {
    SortOrder.UPDATED -> KSortOrder.UPDATED
    SortOrder.POPULARITY -> KSortOrder.POPULARITY
    SortOrder.RATING -> KSortOrder.RATING
    SortOrder.NEWEST -> KSortOrder.NEWEST
    SortOrder.ALPHABETICAL -> KSortOrder.ALPHABETICAL
    SortOrder.RELEVANCE -> KSortOrder.RELEVANCE
}

