package app.kurenai.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class ContentKind {
    MANGA,
    MANHWA,
    MANHUA,
    WEBTOON,
    COMIC,
    LIGHT_NOVEL,
    ONE_SHOT,
    DOUJINSHI,
    OTHER,
    ;

    val label: String
        get() = when (this) {
            MANGA -> "Manga"
            MANHWA -> "Manhwa"
            MANHUA -> "Manhua"
            WEBTOON -> "Webtoon"
            COMIC -> "Comic"
            LIGHT_NOVEL -> "Light novel"
            ONE_SHOT -> "One-shot"
            DOUJINSHI -> "Doujinshi"
            OTHER -> "Other"
        }
}

@Serializable
enum class ContentRating {
    SAFE,
    SUGGESTIVE,
    ADULT,
}

@Serializable
enum class MangaStatus {
    ONGOING,
    FINISHED,
    PAUSED,
    ABANDONED,
    UPCOMING,
    UNKNOWN,
}

@Serializable
enum class SortOrder {
    UPDATED,
    POPULARITY,
    RATING,
    NEWEST,
    ALPHABETICAL,
    RELEVANCE,
}

@Serializable
enum class SourceLicense {
    OFFICIAL,
    LICENSED_API,
    FAN_TRANSLATION,
    UNKNOWN,
    ;

    val badge: String
        get() = when (this) {
            OFFICIAL -> "Official"
            LICENSED_API -> "Licensed API"
            FAN_TRANSLATION -> "Fan translation"
            UNKNOWN -> "Unknown"
        }
}

@Serializable
enum class SourceHealth {
    HEALTHY,
    DEGRADED,
    BROKEN,
    DISABLED,
    UNKNOWN,
}

@Serializable
enum class ReadingMode {
    PAGED_LTR,
    PAGED_RTL,
    PAGED_VERTICAL,
    WEBTOON,
    CONTINUOUS_VERTICAL,
    PANEL_BY_PANEL,
}

@Serializable
enum class ReaderBackground {
    BLACK,
    WHITE,
    GRAY,
    AUTOMATIC,
}

@Serializable
enum class TranslationProviderKind {
    ON_DEVICE_ML_KIT,
    LOCAL_LLM,
    CLOUD_OPENAI_COMPATIBLE,
    NONE,
}

@Serializable
enum class TtsProviderKind {
    SYSTEM,
    NEURAL_OFFLINE,
    VOICE_STUDIO,
}

@Serializable
enum class MotionIntensity {
    STILL,
    SUBTLE,
    CINEMATIC,
    FULL,
}

@Serializable
data class SourceInfo(
    val id: String,
    val name: String,
    val language: String,
    val domain: String,
    val kind: ContentKind,
    val nsfw: Boolean,
    val license: SourceLicense,
    val health: SourceHealth = SourceHealth.UNKNOWN,
    val description: String? = null,
    val isBuiltIn: Boolean = true,
    val version: String = "1.0",
    val iconUrl: String? = null,
) {
    val languageLabel: String
        get() = java.util.Locale.forLanguageTag(language).displayLanguage
            .ifBlank { language.ifBlank { "Multi-language" } }
}
