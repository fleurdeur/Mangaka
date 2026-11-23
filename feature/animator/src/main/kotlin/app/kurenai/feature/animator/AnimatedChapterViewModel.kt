package app.kurenai.feature.animator

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.kurenai.core.data.MangaRepository
import app.kurenai.core.data.ReaderRepository
import app.kurenai.core.datastore.SettingsStore
import app.kurenai.core.model.AnimatedChapterSettings
import app.kurenai.core.model.Manga
import app.kurenai.core.model.MangaChapter
import app.kurenai.core.model.MotionIntensity
import app.kurenai.core.model.TtsProviderKind
import app.kurenai.core.network.KurenaiHttp
import app.kurenai.feature.animator.model.AnimationPlan
import app.kurenai.feature.animator.model.Bubble
import app.kurenai.feature.animator.model.CastMember
import app.kurenai.feature.animator.model.PanelRect
import app.kurenai.feature.animator.model.PlaybackState
import app.kurenai.feature.animator.model.VoiceCasting
import app.kurenai.feature.animator.pipeline.BubbleDetector
import app.kurenai.feature.animator.pipeline.Director
import app.kurenai.feature.animator.pipeline.PanelDetector
import app.kurenai.feature.animator.pipeline.RigBuilder
import app.kurenai.feature.animator.tts.NarrationEngine
import app.kurenai.feature.animator.tts.VoiceOption
import app.kurenai.feature.animator.tts.WordTiming
import app.kurenai.feature.translation.MlKitOcrEngine
import kotlinx.coroutines.delay
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request
import javax.inject.Inject

data class AnimatedUiState(
    val manga: Manga? = null,
    val chapter: MangaChapter? = null,
    val bitmap: Bitmap? = null,
    val plan: AnimationPlan? = null,
    val playback: PlaybackState = PlaybackState(),
    val casting: VoiceCasting = VoiceCasting(),
    val settings: AnimatedChapterSettings = AnimatedChapterSettings(),
    val intensity: MotionIntensity = MotionIntensity.CINEMATIC,
    val subtitle: String = "",
    val narration: List<String> = emptyList(),
    val activeWord: WordTiming? = null,
    val voices: List<VoiceOption> = emptyList(),
    val editorOpen: Boolean = false,
    val castingOpen: Boolean = false,
    val message: String? = null,
)

@HiltViewModel
class AnimatedChapterViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    savedStateHandle: SavedStateHandle,
    private val mangaRepository: MangaRepository,
    private val readerRepository: ReaderRepository,
    private val settingsStore: SettingsStore,
    private val http: KurenaiHttp,
) : ViewModel() {
    private val mangaId: String = savedStateHandle["mangaId"] ?: ""
    private val chapterId: String = savedStateHandle["chapterId"] ?: ""
    private val _state = MutableStateFlow(AnimatedUiState())
    val state: StateFlow<AnimatedUiState> = _state.asStateFlow()
    private val panelDetector = PanelDetector()
    private val bubbleDetector = BubbleDetector()
    private val director = Director()
    private val rigBuilder = RigBuilder()
    private val narration = NarrationEngine(context)
    private val ocr = MlKitOcrEngine()
    private var speechJob: Job? = null

    init {
        process()
    }

    fun process() {
        viewModelScope.launch {
            _state.value = _state.value.copy(playback = PlaybackState(processing = true))
            val manga = mangaRepository.manga(mangaId)
            val chapter = mangaRepository.chapter(chapterId)
            if (manga == null || chapter == null) {
                _state.value = _state.value.copy(playback = PlaybackState(error = "Open a chapter first."))
                return@launch
            }
            val settings = settingsStore.settings.first().animatedChapters
            val pages = readerRepository.pages(chapter)
            if (pages.isEmpty()) {
                _state.value = _state.value.copy(playback = PlaybackState(error = "No pages to animate."))
                return@launch
            }
            val bitmap = downloadBitmap(pages.first().url, chapter.url, settings.quality)
            val effectiveIntensity = if (settings.reducedMotion) {
                app.kurenai.core.model.MotionIntensity.STILL
            } else if (settings.photosensitivitySafe && settings.intensity == app.kurenai.core.model.MotionIntensity.FULL) {
                app.kurenai.core.model.MotionIntensity.CINEMATIC
            } else {
                settings.intensity
            }
            val rightToLeft = manga.kind == app.kurenai.core.model.ContentKind.MANGA ||
                manga.kind == app.kurenai.core.model.ContentKind.ONE_SHOT
            val panels = if (bitmap != null) {
                panelDetector.detect(bitmap, rightToLeft)
            } else {
                listOf(PanelRect(0f, 0f, 1f, 1f))
            }
            val ocrLines = if (bitmap != null) {
                runCatching { ocr.recognize(bitmap) }.getOrDefault(emptyList())
            } else {
                emptyList()
            }
            val bubbles = if (ocrLines.isNotEmpty()) {
                ocrLines.mapIndexedNotNull { index, line ->
                    val cx = (line.left + line.right) / 2f
                    val cy = (line.top + line.bottom) / 2f
                    val panelIndex = panels.indexOfFirst { panel ->
                        cx in panel.left..panel.right && cy in panel.top..panel.bottom
                    }.coerceAtLeast(0)
                    line.text.trim().takeIf { it.isNotBlank() }?.let { text ->
                        Bubble(
                            panelIndex = panelIndex,
                            text = text,
                            left = line.left,
                            top = line.top,
                            right = line.right,
                            bottom = line.bottom,
                            isNarration = false,
                        )
                    }
                }.ifEmpty {
                    bubbleDetector.detect(bitmap ?: return@launch, panels)
                }
            } else if (bitmap != null) {
                bubbleDetector.detect(bitmap, panels)
            } else {
                emptyList()
            }
            val panelTexts = panels.map { panel ->
                ocrLines.filter { line ->
                    val cx = (line.left + line.right) / 2f
                    val cy = (line.top + line.bottom) / 2f
                    cx in panel.left..panel.right && cy in panel.top..panel.bottom
                }.sortedWith(compareBy({ it.top }, { it.left }))
                    .joinToString(" ") { it.text.trim() }
            }
            val rigs = panels.flatMapIndexed { index, panel ->
                rigBuilder.build(index, panel, detectedCharacters = if (panel.width > 0.5f) 1 else 0)
            }
            val narrationTexts = buildNarration(manga, chapter, panelTexts)
            val speechRate = settings.speed.coerceIn(0.5f, 2f)
            val panelDurations = narrationTexts.map { text ->
                val base = if (text.isBlank()) 1_400L else (text.length * 55L).coerceAtLeast(1_600L)
                (base / speechRate).toLong().coerceAtLeast(700L)
            }
            val estimatedMs = panelDurations.sum().coerceAtLeast(3_000L)
            val plan = AnimationPlan(
                panels = panels,
                bubbles = bubbles,
                rigs = rigs,
                shots = director.compose(panels, estimatedMs, effectiveIntensity, panelDurations = panelDurations),
                rightToLeft = rightToLeft,
                durationMs = estimatedMs,
                intensity = effectiveIntensity,
                warnings = if (panels.size <= 1) listOf("This page has no clear gutters; using a single cinematic panel.") else emptyList(),
            )
            val provider = narration.provider(settings.ttsProvider)
            val voices = runCatching { provider.voices() }.getOrDefault(emptyList())
            _state.value = _state.value.copy(
                manga = manga,
                chapter = chapter,
                bitmap = bitmap,
                plan = plan,
                settings = settings,
                intensity = effectiveIntensity,
                casting = defaultCasting(voices, rigs.size),
                voices = voices,
                playback = PlaybackState(prepared = true, estimatedSeconds = (estimatedMs / 1000).toInt()),
                subtitle = narrationTexts.firstOrNull { it.isNotBlank() }.orEmpty(),
                narration = narrationTexts,
            )
        }
    }

    private fun defaultCasting(voices: List<VoiceOption>, characters: Int): VoiceCasting {
        val narrator = CastMember(
            id = "narrator",
            name = "Narrator",
            voiceName = voices.firstOrNull()?.id,
            personality = "warm",
        )
        val cast = (0 until characters).map { index ->
            CastMember(
                id = "char-$index",
                name = "Character ${index + 1}",
                voiceName = voices.getOrNull(index + 1)?.id,
                speed = 1f,
                pitch = 1f + index * 0.03f,
                emotion = "neutral",
            )
        }
        return VoiceCasting(narrator = narrator, characters = cast)
    }

    fun play() {
        val current = _state.value
        val plan = current.plan ?: return
        if (current.playback.playing) {
            pause()
            return
        }
        _state.value = _state.value.copy(playback = current.playback.copy(playing = true, error = null))
        speechJob = viewModelScope.launch {
            val provider = narration.provider(current.settings.ttsProvider)
            val manga = current.manga ?: return@launch
            val chapter = current.chapter ?: return@launch
            val texts = current.narration.ifEmpty { buildNarration(manga, chapter, List(plan.panelCount) { "" }) }
            if (!current.settings.narrationEnabled) {
                texts.forEachIndexed { index, text ->
                    if (!_state.value.playback.playing) return@launch
                    _state.value = _state.value.copy(
                        playback = _state.value.playback.copy(
                            currentPanel = index,
                            progress = (index + 1).toFloat() / texts.size.coerceAtLeast(1),
                        ),
                        subtitle = text,
                        activeWord = null,
                    )
                    delay(plan.shots.getOrNull(index)?.durationMs ?: 1_600L)
                }
                _state.value = _state.value.copy(
                    playback = _state.value.playback.copy(
                        playing = false,
                        progress = 1f,
                        currentPanel = plan.panelCount - 1,
                    ),
                )
                return@launch
            }
            texts.forEachIndexed { index, text ->
                if (!_state.value.playback.playing) return@launch
                if (text.isBlank()) {
                    _state.value = _state.value.copy(
                        playback = _state.value.playback.copy(
                            currentPanel = index,
                            progress = index.toFloat() / texts.size.coerceAtLeast(1),
                        ),
                        subtitle = "",
                    )
                    delay(1_400L)
                    return@forEachIndexed
                }
                _state.value = _state.value.copy(
                    playback = _state.value.playback.copy(
                        currentPanel = index,
                        progress = index.toFloat() / texts.size.coerceAtLeast(1),
                    ),
                    subtitle = text,
                    activeWord = null,
                )
                val castMember = if (index == 0) {
                    current.casting.narrator
                } else {
                    current.casting.characters.getOrNull(index - 1) ?: current.casting.narrator
                }
                val voice = current.voices.firstOrNull { it.id == castMember.voiceName }
                runCatching {
                    provider.speak(
                        text,
                        voice,
                        current.settings.speed * castMember.speed.coerceIn(0.7f, 1.4f),
                        castMember.pitch.coerceIn(0.6f, 1.6f),
                    ) { timing ->
                        _state.value = _state.value.copy(activeWord = timing)
                    }
                }.onFailure { error ->
                    _state.value = _state.value.copy(
                        playback = _state.value.playback.copy(playing = false, error = error.message),
                    )
                }
            }
            _state.value = _state.value.copy(
                playback = _state.value.playback.copy(
                    playing = false,
                    progress = 1f,
                    currentPanel = plan.panelCount - 1,
                ),
            )
        }
    }

    fun pause() {
        narration.stopAll()
        speechJob?.cancel()
        _state.value = _state.value.copy(playback = _state.value.playback.copy(playing = false))
    }

    fun setIntensity(intensity: MotionIntensity) {
        val plan = _state.value.plan ?: return
        _state.value = _state.value.copy(
            intensity = intensity,
            plan = plan.copy(
                shots = director.compose(
                    panels = plan.panels,
                    narrationMs = plan.durationMs,
                    intensity = intensity,
                    panelDurations = plan.shots.map { it.durationMs },
                ),
                intensity = intensity,
            ),
        )
    }

    fun setSpeed(speed: Float) {
        val settings = _state.value.settings.copy(speed = speed.coerceIn(0.5f, 2f))
        _state.value = _state.value.copy(settings = settings)
        viewModelScope.launch { settingsStore.update { it.copy(animatedChapters = settings) } }
    }

    fun setProvider(kind: TtsProviderKind) {
        viewModelScope.launch {
            val settings = _state.value.settings.copy(ttsProvider = kind)
            _state.value = _state.value.copy(settings = settings)
            val voices = runCatching { narration.provider(kind).voices() }.getOrDefault(emptyList())
            _state.value = _state.value.copy(voices = voices)
            settingsStore.update { it.copy(animatedChapters = settings) }
        }
    }

    fun assignVoice(characterId: String, voiceId: String) {
        val casting = _state.value.casting
        val updated = if (characterId == "narrator") {
            casting.copy(narrator = casting.narrator.copy(voiceName = voiceId))
        } else {
            casting.copy(characters = casting.characters.map { if (it.id == characterId) it.copy(voiceName = voiceId) else it })
        }
        _state.value = _state.value.copy(casting = updated)
    }

    fun autoCast() {
        val voices = _state.value.voices
        val casting = _state.value.casting
        val newCharacters = casting.characters.mapIndexed { index, member ->
            member.copy(
                voiceName = voices.getOrNull(index + 1)?.id ?: member.voiceName,
                pitch = 0.9f + (index % 3) * 0.08f,
                emotion = listOf("calm", "bright", "wry", "gentle")[index % 4],
            )
        }
        _state.value = _state.value.copy(casting = casting.copy(characters = newCharacters, autoCast = true))
    }

    fun movePanel(from: Int, to: Int) {
        val plan = _state.value.plan ?: return
        val panels = plan.panels.toMutableList()
        if (from !in panels.indices || to !in panels.indices) return
        val item = panels.removeAt(from)
        panels.add(to, item)
        val reordered = panels.mapIndexed { index, rect -> rect.copy(order = index) }
        _state.value = _state.value.copy(
            plan = plan.copy(panels = reordered, shots = director.compose(reordered, plan.durationMs, plan.intensity)),
        )
    }

    fun setBubbleSpeaker(bubbleIndex: Int, characterId: String) {
        val plan = _state.value.plan ?: return
        val bubbles = plan.bubbles.mapIndexed { index, bubble ->
            if (index == bubbleIndex) bubble.copy(speakerId = characterId) else bubble
        }
        _state.value = _state.value.copy(plan = plan.copy(bubbles = bubbles))
    }

    fun toggleEditor() { _state.value = _state.value.copy(editorOpen = !_state.value.editorOpen) }
    fun toggleCasting() { _state.value = _state.value.copy(castingOpen = !_state.value.castingOpen) }
    fun clearMessage() { _state.value = _state.value.copy(message = null) }
    fun setMessage(value: String) { _state.value = _state.value.copy(message = value) }

    fun exportVideo() {
        _state.value = _state.value.copy(
            message = "Personal-use export renders locally. Pick a destination in the editor; processing can take a few minutes.",
        )
    }

    private fun buildNarration(manga: Manga, chapter: MangaChapter, panelTexts: List<String>): List<String> {
        val base = chapter.title?.takeIf { it.isNotBlank() } ?: "Chapter ${chapter.number}"
        val synopsis = manga.description
            ?.replace(Regex("<[^>]+>"), " ")
            ?.replace(Regex("""\s+"""), " ")
            ?.trim()
            ?.takeIf { it.isNotBlank() }
        return panelTexts.mapIndexed { index, raw ->
            val text = raw.replace(Regex("""\s+"""), " ").trim()
                .replace(Regex("""^[\p{Punct}\s]+|[\p{Punct}\s]+$"""), "")
            when {
                text.isNotBlank() -> text.take(320)
                index == 0 -> if (synopsis != null) "$base. ${manga.title}. ${synopsis.take(180)}" else "$base. ${manga.title}."
                else -> ""
            }
        }
    }

    private suspend fun downloadBitmap(
        url: String,
        referer: String?,
        quality: app.kurenai.core.model.AnimationQuality,
    ): Bitmap? = withContext(Dispatchers.IO) {
        runCatching {
            val bytes = if (url.startsWith("data:", ignoreCase = true)) {
                decodeDataUri(url)
            } else {
                fetchImage(url, referer) ?: fetchImage(url, null)
            } ?: return@runCatching null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            val maxDimension = when (quality) {
                app.kurenai.core.model.AnimationQuality.LOW -> 2048
                app.kurenai.core.model.AnimationQuality.BALANCED -> 3072
                app.kurenai.core.model.AnimationQuality.HIGH -> 4096
                app.kurenai.core.model.AnimationQuality.ULTRA -> 4096
            }
            var sample = 1
            while (bounds.outWidth / sample > maxDimension || bounds.outHeight / sample > maxDimension) sample *= 2
            BitmapFactory.decodeByteArray(
                bytes,
                0,
                bytes.size,
                BitmapFactory.Options().apply {
                    inSampleSize = sample
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                    inScaled = false
                },
            )
        }.getOrNull()
    }


    private fun fetchImage(url: String, referer: String?): ByteArray? = runCatching {
        val request = Request.Builder()
            .url(if (url.startsWith("//")) "https:$url" else url)
            .header("User-Agent", KurenaiHttp.CHROME_UA)
            .header("Accept", "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8")
            .apply { referer?.takeIf { it.isNotBlank() }?.let { header("Referer", it) } }
            .build()
        http.client().newCall(request).execute().use { response ->
            if (!response.isSuccessful) null else response.body?.bytes()
        }
    }.getOrNull()

    private fun decodeDataUri(uri: String): ByteArray? {
        val comma = uri.indexOf(',')
        if (comma < 0) return null
        val meta = uri.substring(0, comma)
        val payload = uri.substring(comma + 1)
        return runCatching {
            if (meta.contains(";base64", ignoreCase = true)) {
                Base64.decode(payload, Base64.DEFAULT)
            } else {
                java.net.URLDecoder.decode(payload, "UTF-8").toByteArray()
            }
        }.getOrNull()
    }

    override fun onCleared() {
        narration.stopAll()
        super.onCleared()
    }
}
