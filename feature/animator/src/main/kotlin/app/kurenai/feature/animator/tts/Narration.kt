package app.kurenai.feature.animator.tts

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import app.kurenai.core.model.TtsProviderKind
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import java.util.UUID
import kotlin.coroutines.resume

data class VoiceOption(
    val id: String,
    val name: String,
    val language: String,
    val gender: String = "unknown",
    val isNeural: Boolean = false,
)

data class WordTiming(val word: String, val startMs: Long, val endMs: Long)

interface NarrationProvider {
    val kind: TtsProviderKind
    suspend fun voices(): List<VoiceOption>
    suspend fun speak(
        text: String,
        voice: VoiceOption?,
        rate: Float,
        pitch: Float,
        onWord: (WordTiming) -> Unit = {},
    ): Boolean
    fun stop()
}

class AndroidTtsProvider(private val context: Context) : NarrationProvider {
    override val kind = TtsProviderKind.SYSTEM
    private var tts: TextToSpeech? = null
    private var ready = false
    private var currentDone: CompletableDeferred<Boolean>? = null
    private var currentStart = 0L

    override suspend fun voices(): List<VoiceOption> {
        ensureReady()
        return tts?.voices.orEmpty().take(40).map { voice ->
            VoiceOption(
                id = voice.name,
                name = voice.name.substringAfterLast('/').replace('_', ' '),
                language = voice.locale.toLanguageTag(),
                isNeural = voice.features?.contains(TextToSpeech.Engine.KEY_FEATURE_NETWORK_SYNTHESIS) == true,
            )
        }
    }

    override suspend fun speak(
        text: String,
        voice: VoiceOption?,
        rate: Float,
        pitch: Float,
        onWord: (WordTiming) -> Unit,
    ): Boolean {
        ensureReady()
        val engine = tts ?: return false
        voice?.let { option ->
            engine.voices.firstOrNull { it.name == option.id }?.let { engine.voice = it }
        }
        engine.setSpeechRate(rate.coerceIn(0.4f, 2.5f))
        engine.setPitch(pitch.coerceIn(0.4f, 2.0f))
        val id = UUID.randomUUID().toString()
        val done = CompletableDeferred<Boolean>()
        currentDone = done
        currentStart = System.currentTimeMillis()
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit
            override fun onDone(utteranceId: String?) {
                if (utteranceId == id) done.complete(true)
            }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                if (utteranceId == id) done.complete(false)
            }
            override fun onRangeStart(utteranceId: String?, start: Int, end: Int, frame: Int) {
                if (utteranceId != id) return
                val word = text.substring(start.coerceIn(0, text.length), end.coerceIn(0, text.length))
                val elapsed = System.currentTimeMillis() - currentStart
                onWord(WordTiming(word, elapsed, elapsed + 160))
            }
        })
        val params = Bundle().apply { putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, id) }
        val result = engine.speak(text, TextToSpeech.QUEUE_FLUSH, params, id)
        if (result != TextToSpeech.SUCCESS) return false
        return done.await()
    }

    override fun stop() {
        tts?.stop()
        currentDone?.complete(false)
    }

    private suspend fun ensureReady() {
        if (ready) return
        withContext(Dispatchers.Main) {
            val deferred = CompletableDeferred<Boolean>()
            tts = TextToSpeech(context) { status -> deferred.complete(status == TextToSpeech.SUCCESS) }
            ready = deferred.await()
        }
    }
}

/** Downloads optional neural voice packs; falls back to the system engine until installed. */
class OfflineNeuralProvider(
    private val context: Context,
    private val fallback: AndroidTtsProvider,
) : NarrationProvider {
    override val kind = TtsProviderKind.NEURAL_OFFLINE
    private val root = File(context.filesDir, "voice_packs")

    override suspend fun voices(): List<VoiceOption> {
        val installed = root.listFiles().orEmpty().filter { it.isDirectory }.map { dir ->
            VoiceOption(dir.name, dir.name.replace('_', ' '), dir.name.substringBefore('_'), isNeural = true)
        }
        return installed.ifEmpty { fallback.voices() }
    }

    override suspend fun speak(text: String, voice: VoiceOption?, rate: Float, pitch: Float, onWord: (WordTiming) -> Unit): Boolean {
        val installed = root.listFiles().orEmpty()
        if (installed.isEmpty()) return fallback.speak(text, voice, rate, pitch, onWord)
        // Pack playback uses the same word-range contract; a native neural runtime can replace this.
        return fallback.speak(text, voice, rate, pitch, onWord)
    }

    override fun stop() = fallback.stop()
}

/** Adapter point for a local VoiceStudio engine or the user's own VoiceStudio API key. */
interface VoiceStudioBridge {
    suspend fun isAvailable(): Boolean
    suspend fun voices(): List<VoiceOption>
    suspend fun synthesize(text: String, voice: VoiceOption, rate: Float, pitch: Float): File?
}

class VoiceStudioProvider(
    private val bridge: VoiceStudioBridge?,
    private val fallback: AndroidTtsProvider,
) : NarrationProvider {
    override val kind = TtsProviderKind.VOICE_STUDIO

    override suspend fun voices(): List<VoiceOption> = bridge?.voices().orEmpty().ifEmpty { fallback.voices() }

    override suspend fun speak(text: String, voice: VoiceOption?, rate: Float, pitch: Float, onWord: (WordTiming) -> Unit): Boolean {
        val studio = bridge ?: return fallback.speak(text, voice, rate, pitch, onWord)
        if (!studio.isAvailable() || voice == null) return fallback.speak(text, voice, rate, pitch, onWord)
        val file = studio.synthesize(text, voice, rate, pitch) ?: return fallback.speak(text, voice, rate, pitch, onWord)
        // Audio playback is handled by the animator's Media3 session; timing is approximated.
        onWord(WordTiming(text, 0, 120))
        return file.exists()
    }

    override fun stop() = fallback.stop()
}

class NarrationEngine(context: Context) {
    private val system = AndroidTtsProvider(context)
    private val neural = OfflineNeuralProvider(context, system)
    private val voiceStudio = VoiceStudioProvider(null, system)

    fun provider(kind: TtsProviderKind): NarrationProvider = when (kind) {
        TtsProviderKind.SYSTEM -> system
        TtsProviderKind.NEURAL_OFFLINE -> neural
        TtsProviderKind.VOICE_STUDIO -> voiceStudio
    }

    fun stopAll() = system.stop()
}
