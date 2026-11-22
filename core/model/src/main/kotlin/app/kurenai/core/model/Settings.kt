package app.kurenai.core.model

import kotlinx.serialization.Serializable

@Serializable
data class ReaderSettings(
    val mode: ReadingMode = ReadingMode.WEBTOON,
    val background: ReaderBackground = ReaderBackground.AUTOMATIC,
    val keepScreenOn: Boolean = true,
    val showPageNumber: Boolean = true,
    val tapZones: TapZones = TapZones(),
    val volumeKeysEnabled: Boolean = true,
    val volumeKeysInvert: Boolean = false,
    val autoCrop: Boolean = false,
    val colorFilter: ColorFilterMode = ColorFilterMode.NONE,
    val brightness: Float = 0f,
    val readingLightSchedule: ReadingLightSchedule? = null,
    val doublePage: Boolean = true,
    val autoDetectDoublePage: Boolean = true,
    val pageTurnSpeed: Float = 1f,
    val autoScrollSpeed: Float = 1f,
    val autoScrollAdaptive: Boolean = true,
    val einkMode: Boolean = false,
    val reducedMotion: Boolean = false,
    val keepUiHidden: Boolean = false,
) {
    companion object {
        val Default = ReaderSettings()
    }
}

@Serializable
data class TapZones(
    val leftAction: ReaderAction = ReaderAction.PREV_PAGE,
    val rightAction: ReaderAction = ReaderAction.NEXT_PAGE,
    val centerAction: ReaderAction = ReaderAction.TOGGLE_UI,
    val topAction: ReaderAction = ReaderAction.NONE,
    val bottomAction: ReaderAction = ReaderAction.NONE,
    val leftWidthFraction: Float = 0.28f,
    val rightWidthFraction: Float = 0.28f,
)

@Serializable
enum class ReaderAction {
    NONE,
    NEXT_PAGE,
    PREV_PAGE,
    TOGGLE_UI,
    NEXT_CHAPTER,
    PREV_CHAPTER,
    TOGGLE_AUTO_SCROLL,
    OPEN_SETTINGS,
}

@Serializable
enum class ColorFilterMode {
    NONE,
    SEPIA,
    GRAYSCALE,
    NIGHT,
    WARM,
    INVERTED,
}

@Serializable
data class ReadingLightSchedule(
    val enabled: Boolean = false,
    val startHour: Int = 21,
    val endHour: Int = 7,
    val warmth: Float = 0.6f,
    val dim: Float = 0.3f,
)

@Serializable
data class AnimatedChapterSettings(
    val enabled: Boolean = false,
    val autoPrecompute: Boolean = true,
    val quality: AnimationQuality = AnimationQuality.BALANCED,
    val intensity: MotionIntensity = MotionIntensity.CINEMATIC,
    val narrationEnabled: Boolean = true,
    val ttsProvider: TtsProviderKind = TtsProviderKind.SYSTEM,
    val narratorVoice: String? = null,
    val speed: Float = 1f,
    val volume: Float = 1f,
    val subtitles: Boolean = true,
    val karaoke: Boolean = true,
    val ambientAudio: Boolean = false,
    val reducedMotion: Boolean = false,
    val photosensitivitySafe: Boolean = true,
    val cacheBudgetMb: Int = 1024,
    val backgroundWhileChargingOnly: Boolean = true,
)

@Serializable
enum class AnimationQuality {
    LOW,
    BALANCED,
    HIGH,
    ULTRA,
}

@Serializable
data class TranslationSettings(
    val enabled: Boolean = false,
    val provider: TranslationProviderKind = TranslationProviderKind.NONE,
    val sourceLanguage: String = "auto",
    val targetLanguage: String = "en",
    val cloudEndpoint: String? = null,
    val cloudModel: String? = null,
    val glossary: Map<String, String> = emptyMap(),
    val showFurigana: Boolean = false,
    val languageLearning: Boolean = false,
    val learningLanguage: String = "ja",
    val dailyNewWords: Int = 10,
)

@Serializable
data class AppSettings(
    val onboardingComplete: Boolean = false,
    val disclaimerAccepted: Boolean = false,
    val theme: ThemeSettings = ThemeSettings(),
    val reader: ReaderSettings = ReaderSettings(),
    val animatedChapters: AnimatedChapterSettings = AnimatedChapterSettings(),
    val translation: TranslationSettings = TranslationSettings(),
    val adultContentUnlocked: Boolean = false,
    val incognito: Boolean = false,
    val appLockEnabled: Boolean = false,
    val biometricLockEnabled: Boolean = false,
    val lowMemoryMode: Boolean = false,
    val batteryAwareWork: Boolean = true,
    val wifiOnlyDownloads: Boolean = true,
    val autoDeleteReadAfterDays: Int = 0,
    val recommendationsEnabled: Boolean = true,
    val crashReportsEnabled: Boolean = false,
    val analyticsEnabled: Boolean = false,
    val challengeHandlingEnabled: Boolean = true,
    val dohEnabled: Boolean = true,
    val proxy: ProxySettings = ProxySettings(),
    val downloadConcurrency: Int = 3,
    val imageCacheMb: Int = 512,
    val language: String = "system",
    val notificationsEnabled: Boolean = true,
    val syncEndpoint: String? = null,
)

@Serializable
data class ThemeSettings(
    val mode: ThemeMode = ThemeMode.SYSTEM,
    val amoled: Boolean = false,
    val dynamicColor: Boolean = true,
    val coverAdaptive: Boolean = true,
    val palette: Palette = Palette.KURENAI,
    val glassSurfaces: Boolean = true,
    val fontScale: Float = 1f,
    val useDyslexiaFont: Boolean = false,
)

@Serializable
enum class ThemeMode { SYSTEM, LIGHT, DARK }

@Serializable
enum class Palette { KURENAI, COFFEE, MONET, AMOLED, COVER }

@Serializable
data class ProxySettings(
    val enabled: Boolean = false,
    val type: ProxyType = ProxyType.HTTP,
    val host: String = "",
    val port: Int = 8080,
    val username: String = "",
    val password: String = "",
    val perSource: Set<String> = emptySet(),
)

@Serializable
enum class ProxyType { HTTP, HTTPS, SOCKS5 }

@Serializable
data class SourcePreferences(
    val disabledSourceIds: Set<String> = emptySet(),
    val enabledLanguages: Set<String> = emptySet(),
    val enabledKinds: Set<ContentKind> = emptySet(),
    val enabledRatings: Set<ContentRating> = setOf(ContentRating.SAFE, ContentRating.SUGGESTIVE),
    val showOnlyHealthy: Boolean = false,
    val sortBy: SourceSort = SourceSort.LANGUAGE,
    val healthRecords: Map<String, SourceHealthRecord> = emptyMap(),
)

@Serializable
enum class SourceSort { NAME, LANGUAGE, KIND, HEALTH }

@Serializable
data class SourceHealthRecord(
    val sourceId: String,
    val consecutiveFailures: Int = 0,
    val totalSuccesses: Long = 0,
    val totalFailures: Long = 0,
    val lastCheckedAt: Long = 0,
    val lastError: String? = null,
    val averageLatencyMs: Long = 0,
    val disabledAutomatically: Boolean = false,
) {
    val health: SourceHealth
        get() = when {
            disabledAutomatically -> SourceHealth.DISABLED
            consecutiveFailures >= 5 -> SourceHealth.BROKEN
            consecutiveFailures >= 2 -> SourceHealth.DEGRADED
            totalSuccesses > 0 -> SourceHealth.HEALTHY
            else -> SourceHealth.UNKNOWN
        }
}
