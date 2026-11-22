package app.kurenai.core.database

import androidx.room.TypeConverter
import app.kurenai.core.model.ContentKind
import app.kurenai.core.model.ContentRating
import app.kurenai.core.model.DownloadState
import app.kurenai.core.model.MangaStatus
import app.kurenai.core.model.TrackerService
import app.kurenai.core.model.TrackerStatus
import kotlinx.serialization.json.Json

class Converters {
    private val json = Json

    @TypeConverter
    fun setToString(value: Set<String>?): String = json.encodeToString(value ?: emptySet())

    @TypeConverter
    fun stringToSet(value: String?): Set<String> = runCatching {
        json.decodeFromString<Set<String>>(value.orEmpty())
    }.getOrDefault(emptySet())

    @TypeConverter fun statusToString(value: MangaStatus): String = value.name
    @TypeConverter fun stringToStatus(value: String): MangaStatus = runCatching { MangaStatus.valueOf(value) }.getOrDefault(MangaStatus.UNKNOWN)
    @TypeConverter fun kindToString(value: ContentKind): String = value.name
    @TypeConverter fun stringToKind(value: String): ContentKind = runCatching { ContentKind.valueOf(value) }.getOrDefault(ContentKind.MANGA)
    @TypeConverter fun ratingToString(value: ContentRating): String = value.name
    @TypeConverter fun stringToRating(value: String): ContentRating = runCatching { ContentRating.valueOf(value) }.getOrDefault(ContentRating.SAFE)
    @TypeConverter fun downloadToString(value: DownloadState): String = value.name
    @TypeConverter fun stringToDownload(value: String): DownloadState = runCatching { DownloadState.valueOf(value) }.getOrDefault(DownloadState.QUEUED)
    @TypeConverter fun trackerToString(value: TrackerService): String = value.name
    @TypeConverter fun stringToTracker(value: String): TrackerService = runCatching { TrackerService.valueOf(value) }.getOrDefault(TrackerService.ANILIST)
    @TypeConverter fun trackerStatusToString(value: TrackerStatus): String = value.name
    @TypeConverter fun stringToTrackerStatus(value: String): TrackerStatus = runCatching { TrackerStatus.valueOf(value) }.getOrDefault(TrackerStatus.READING)
}
