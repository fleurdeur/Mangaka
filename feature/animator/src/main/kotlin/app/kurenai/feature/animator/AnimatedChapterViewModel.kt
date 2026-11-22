package app.kurenai.feature.animator

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
            val bitmap = downloadBitmap(pages.first().url)
            val rightToLeft = manga.kind == app.kurenai.core.model.ContentKind.MANGA ||
                manga.kind == app.kurenai.core.model.ContentKind.ONE_SHOT
            val panels = if (bitmap != null) {
                panelDetector.detect(bitmap, rightToLeft)
            } else {
                listOf(PanelRect(0f, 0f, 1f, 1f))
            }
            val bubbles = if (bitmap != null) {
                bubbleDetector.detect(bitmap, panels)
            } else {
                panels.map { Bubble(0, "", 0.1f, 0.1f, 0.9f, 0.2f, isNarration = true) }
            }
            val rigs = panels.flatMapIndexed { index, panel ->
                rigBuilder.build(index, panel, detectedCharacters = if (panel.width > 0.5f) 1 else 0)
            }
            val narrationTexts = buildNarration(manga, chapter, panels.size)
            val estimatedMs = narrationTexts.sumOf { it.length * 55L }.coerceAtLeast(3_000L)
            val plan = AnimationPlan(
                panels = panels,
                bubbles = bubbles,
                rigs = rigs,
                shots = director.compose(panels, estimatedMs, settings.intensity),
                rightToLeft = rightToLeft,
                durationMs = estimatedMs,
                intensity = settings.intensity,
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
                intensity = settings.intensity,
                casting = defaultCasting(voices, rigs.size),
                voices = voices,
                playback = PlaybackState(prepared = true, estimatedSeconds = (estimatedMs / 1000).toInt()),
                subtitle = narrationTexts.firstOrNull().orEmpty(),
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
            val texts = buildNarration(manga, chapter, plan.panelCount)
            texts.forEachIndexed { index, text ->
                if (!_state.value.playback.playing) return@launch
                _state.value = _state.value.copy(
                    playback = _state.value.playback.copy(
                        currentPanel = index,
                        progress = index.toFloat() / texts.size.coerceAtLeast(1),
                    ),
                    subtitle = text,
                    activeWord = null,
                )
                val voiceId = if (index == 0) {
                    current.casting.narrator.voiceName
                } else {
                    current.casting.characters.getOrNull(index - 1)?.voiceName
                }
                val voice = current.voices.firstOrNull { it.id == voiceId }
                runCatching {
                    provider.speak(text, voice, current.settings.speed, 1f) { timing ->
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
                shots = director.compose(plan.panels, plan.durationMs, intensity),
                intensity = intensity,
            ),
        )
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

    private fun buildNarration(manga: Manga, chapter: MangaChapter, panels: Int): List<String> {
        val base = chapter.title?.takeIf { it.isNotBlank() } ?: "Chapter ${chapter.number}"
        return (0 until panels).map { index ->
            when (index) {
                0 -> "$base. ${manga.title}."
                else -> "Panel ${index + 1}."
            }
        }
    }

    private suspend fun downloadBitmap(url: String): Bitmap? = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder().url(url).header("User-Agent", KurenaiHttp.CHROME_UA).build()
            http.client().newCall(request).execute().use { response ->
                response.body?.byteStream()?.let { BitmapFactory.decodeStream(it) }
            }
        }.getOrNull()
    }

    override fun onCleared() {
        narration.stopAll()
        super.onCleared()
    }
}
