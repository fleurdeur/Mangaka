package app.kurenai.core.parsers

import app.kurenai.core.model.ContentKind
import app.kurenai.core.model.ContentRating
import app.kurenai.core.model.FilterOptions
import app.kurenai.core.model.Manga
import app.kurenai.core.model.MangaChapter
import app.kurenai.core.model.MangaPage
import app.kurenai.core.model.MangaStatus
import app.kurenai.core.model.SearchFilters
import app.kurenai.core.model.SourceHealth
import app.kurenai.core.model.SourceInfo
import app.kurenai.core.model.SourceLicense
import app.kurenai.core.model.Tag
import app.kurenai.core.source.MangaSource

/** Offline sample source used by onboarding, screenshots and UI tests. */
class SampleSource : MangaSource {
    override val info = SourceInfo(
        id = "kurenai:sample",
        name = "Kurenai Samples",
        language = "en",
        domain = "sample.kurenai.app",
        kind = ContentKind.MANGA,
        nsfw = false,
        license = SourceLicense.OFFICIAL,
        health = SourceHealth.HEALTHY,
        description = "Bundled demo chapters. No network required.",
        version = "1.0",
    )

    private val samples = listOf(
        Manga(
            id = "kurenai:sample:1",
            sourceId = info.id,
            title = "The Lantern Between Worlds",
            altTitles = setOf("世界の間の灯籠"),
            coverUrl = "https://picsum.photos/seed/kurenai-lantern/600/900",
            description = "A quiet keeper of lanterns learns that every light is a door. A cozy fantasy about grief, tea, and the courage to walk through.",
            authors = setOf("Aoi Sumire"),
            tags = setOf("Fantasy", "Slice of Life", "Drama", "Supernatural"),
            status = MangaStatus.ONGOING,
            kind = ContentKind.MANGA,
            rating = 0.92f,
            chapterCount = 3,
            latestChapter = "3",
        ),
        Manga(
            id = "kurenai:sample:2",
            sourceId = info.id,
            title = "Crimson Hour",
            altTitles = setOf("紅の刻"),
            coverUrl = "https://picsum.photos/seed/kurenai-hour/600/900",
            description = "Two rivals share one last night before the city burns. A cinematic one-shot about loyalty and the stories we leave behind.",
            authors = setOf("Ren Kisaragi"),
            tags = setOf("Action", "Tragedy", "Romance"),
            status = MangaStatus.FINISHED,
            rating = 0.88f,
            chapterCount = 1,
            latestChapter = "1",
        ),
        Manga(
            id = "kurenai:sample:3",
            sourceId = info.id,
            title = "Quietly, the Sea Remembers",
            altTitles = setOf("海は静かに覚えている"),
            coverUrl = "https://picsum.photos/seed/kurenai-sea/600/900",
            description = "A slow-burn seaside romance told in small gestures, warm light and the space between words.",
            authors = setOf("Mio Tachibana"),
            tags = setOf("Romance", "Slice of Life", "Josei"),
            status = MangaStatus.ONGOING,
            contentRating = ContentRating.SUGGESTIVE,
            rating = 0.95f,
            chapterCount = 2,
            latestChapter = "2",
        ),
    )

    override suspend fun search(filters: SearchFilters, offset: Int, limit: Int): List<Manga> {
        val filtered = samples.filter { manga ->
            (filters.query.isBlank() || manga.title.contains(filters.query, true) ||
                manga.tags.any { it.contains(filters.query, true) }) &&
                (filters.tags.isEmpty() || manga.tags.any { it in filters.tags }) &&
                (filters.statuses.isEmpty() || manga.status in filters.statuses) &&
                (filters.kinds.isEmpty() || manga.kind in filters.kinds)
        }
        return filtered.drop(offset).take(limit)
    }

    override suspend fun details(manga: Manga): Manga = samples.firstOrNull { it.id == manga.id } ?: manga

    override suspend fun chapters(manga: Manga): List<MangaChapter> {
        val model = samples.firstOrNull { it.id == manga.id } ?: return emptyList()
        return (1..model.chapterCount).map { index ->
            MangaChapter(
                id = "${model.id}:chapter:$index",
                mangaId = model.id,
                sourceId = info.id,
                title = "The light we keep",
                number = index.toFloat(),
                url = "sample/$index",
                uploadedAt = System.currentTimeMillis() - index * 86_400_000L,
                branch = "Sample",
                pageCount = 3,
            )
        }
    }

    override suspend fun pages(chapter: MangaChapter): List<MangaPage> = (0 until 3).map { index ->
        MangaPage(
            index = index,
            url = "https://picsum.photos/seed/${chapter.id.replace(':', '-')}-$index/900/1400",
            sourceId = info.id,
            chapterId = chapter.id,
        )
    }

    override suspend fun resolveImageUrl(page: MangaPage): String = page.url

    override suspend fun filterOptions(): FilterOptions = FilterOptions(
        tags 
= listOf("Fantasy", "Romance", "Action", "Drama", "Slice of Life", "Tragedy", "Josei")
            .map { Tag(it, it, info.id) },
        statuses = setOf(MangaStatus.ONGOING, MangaStatus.FINISHED),
        kinds = setOf(ContentKind.MANGA, ContentKind.LIGHT_NOVEL),
        contentRatings = setOf(ContentRating.SAFE, ContentRating.SUGGESTIVE),
        supportsYear = false,
    )
}
