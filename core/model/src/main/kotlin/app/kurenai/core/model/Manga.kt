package app.kurenai.core.model

import kotlinx.serialization.Serializable

@Serializable
data class Manga(
    val id: String,
    val sourceId: String,
    val title: String,
    val altTitles: Set<String> = emptySet(),
    val url: String = "",
    val publicUrl: String = "",
    val coverUrl: String? = null,
    val largeCoverUrl: String? = null,
    val description: String? = null,
    val authors: Set<String> = emptySet(),
    val tags: Set<String> = emptySet(),
    val status: MangaStatus = MangaStatus.UNKNOWN,
    val kind: ContentKind = ContentKind.MANGA,
    val contentRating: ContentRating = ContentRating.SAFE,
    val rating: Float = 0f,
    val chapterCount: Int = 0,
    val latestChapter: String? = null,
    val updatedAt: Long = 0L,
    val isFavorite: Boolean = false,
    val isNsfw: Boolean = contentRating == ContentRating.ADULT,
) {
    val displayAuthor: String get() = authors.joinToString()
    val hasRating: Boolean get() = rating in 0.01f..1f
}

@Serializable
data class MangaChapter(
    val id: String,
    val mangaId: String,
    val sourceId: String,
    val title: String? = null,
    val number: Float = 0f,
    val volume: Int = 0,
    val url: String = "",
    val scanlator: String? = null,
    val uploadedAt: Long = 0L,
    val branch: String? = null,
    val isRead: Boolean = false,
    val isBookmarked: Boolean = false,
    val isDownloaded: Boolean = false,
    val pageCount: Int = 0,
) {
    val displayTitle: String
        get() = title?.takeIf { it.isNotBlank() } ?: buildString {
            if (volume > 0) append("Vol. ").append(volume).append(' ')
            if (number > 0f) append("Chapter ").append(number.toString().removeSuffix(".0"))
            else append("Oneshot")
        }

    val branchLabel: String get() = scanlator?.takeIf { it.isNotBlank() } ?: branch ?: "Default"
}

@Serializable
data class MangaPage(
    val index: Int,
    val url: String,
    val preview: String? = null,
    val width: Int = 0,
    val height: Int = 0,
    val sourceId: String = "",
    val chapterId: String = "",
)

@Serializable
data class Tag(
    val key: String,
    val title: String,
    val sourceId: String = "",
)

@Serializable
data class SearchFilters(
    val query: String = "",
    val tags: Set<String> = emptySet(),
    val excludedTags: Set<String> = emptySet(),
    val statuses: Set<MangaStatus> = emptySet(),
    val contentRatings: Set<ContentRating> = emptySet(),
    val kinds: Set<ContentKind> = emptySet(),
    val languages: Set<String> = emptySet(),
    val year: Int? = null,
    val minRating: Float = 0f,
    val sort: SortOrder = SortOrder.POPULARITY,
) {
    val isEmpty: Boolean
        get() = query.isBlank() && tags.isEmpty() && excludedTags.isEmpty() && statuses.isEmpty() &&
            contentRatings.isEmpty() && kinds.isEmpty() && languages.isEmpty() && year == null && minRating <= 0f
}

@Serializable
data class FilterOptions(
    val tags: List<Tag> = emptyList(),
    val statuses: Set<MangaStatus> = emptySet(),
    val kinds: Set<ContentKind> = emptySet(),
    val contentRatings: Set<ContentRating> = emptySet(),
    val languages: Set<String> = emptySet(),
    val supportsSearch: Boolean = true,
    val supportsMultipleTags: Boolean = true,
    val supportsYear: Boolean = false,
)
