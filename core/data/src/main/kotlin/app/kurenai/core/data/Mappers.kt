package app.kurenai.core.data

import app.kurenai.core.database.entity.ChapterEntity
import app.kurenai.core.database.entity.MangaEntity
import app.kurenai.core.model.ContentKind
import app.kurenai.core.model.ContentRating
import app.kurenai.core.model.Manga
import app.kurenai.core.model.MangaChapter
import app.kurenai.core.model.MangaStatus

fun MangaEntity.toModel(): Manga = Manga(
    id = id,
    sourceId = sourceId,
    title = title,
    altTitles = altTitles,
    url = url,
    publicUrl = publicUrl,
    coverUrl = coverUrl,
    largeCoverUrl = largeCoverUrl,
    description = description,
    authors = authors,
    tags = tags,
    status = runCatching { MangaStatus.valueOf(status) }.getOrDefault(MangaStatus.UNKNOWN),
    kind = runCatching { ContentKind.valueOf(kind) }.getOrDefault(ContentKind.MANGA),
    contentRating = runCatching { ContentRating.valueOf(contentRating) }.getOrDefault(ContentRating.SAFE),
    rating = rating,
    chapterCount = chapterCount,
    latestChapter = latestChapter,
    updatedAt = updatedAt,
    isFavorite = isFavorite,
)

fun Manga.toEntity(existingAddedAt: Long = 0L, favorite: Boolean = isFavorite): MangaEntity = MangaEntity(
    id = id,
    sourceId = sourceId,
    title = title,
    altTitles = altTitles,
    url = url,
    publicUrl = publicUrl,
    coverUrl = coverUrl,
    largeCoverUrl = largeCoverUrl,
    description = description,
    authors = authors,
    tags = tags,
    status = status.name,
    kind = kind.name,
    contentRating = contentRating.name,
    rating = rating,
    chapterCount = chapterCount,
    latestChapter = latestChapter,
    updatedAt = updatedAt,
    isFavorite = favorite,
    addedAt = existingAddedAt,
)

fun ChapterEntity.toModel(): MangaChapter = MangaChapter(
    id = id,
    mangaId = mangaId,
    sourceId = sourceId,
    title = title,
    number = number,
    volume = volume,
    url = url,
    scanlator = scanlator,
    uploadedAt = uploadedAt,
    branch = branch,
    isRead = isRead,
    isBookmarked = isBookmarked,
    isDownloaded = downloaded,
    pageCount = pageCount,
)

fun MangaChapter.toEntity(): ChapterEntity = ChapterEntity(
    id = id,
    mangaId = mangaId,
    sourceId = sourceId,
    title = title,
    number = number,
    volume = volume,
    url = url,
    scanlator = scanlator,
    uploadedAt = uploadedAt,
    branch = branch,
    isRead = isRead,
    isBookmarked = isBookmarked,
    downloaded = isDownloaded,
    pageCount = pageCount,
)
