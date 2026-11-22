package app.kurenai.feature.translation

import android.graphics.Bitmap
import app.kurenai.core.model.TranslationSettings
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.max
import com.google.android.gms.tasks.Task

data class OcrLine(
    val text: String,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val confidence: Float = 0f,
)

data class TranslationOutcome(
    val text: String,
    val engine: String,
    val usedNetwork: Boolean = false,
    val sourceLanguage: String = "auto",
    val error: String? = null,
)

interface OcrEngine {
    suspend fun recognize(bitmap: Bitmap): List<OcrLine>
}

class MlKitOcrEngine @Inject constructor() : OcrEngine {
    private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    override suspend fun recognize(bitmap: Bitmap): List<OcrLine> = withContext(Dispatchers.Default) {
        val width = max(1, bitmap.width).toFloat()
        val height = max(1, bitmap.height).toFloat()
        val result = recognizer.process(InputImage.fromBitmap(bitmap, 0)).awaitTask()
        result.textBlocks.flatMap { block ->
            block.lines.mapNotNull { line ->
                val box = line.boundingBox ?: return@mapNotNull null
                OcrLine(
                    text = line.text,
                    left = box.left / width,
                    top = box.top / height,
                    right = box.right / width,
                    bottom = box.bottom / height,
                )
            }
        }.filter { it.text.isNotBlank() }
    }
}

/** On-device translation with ML Kit; the model downloads once and then works offline. */
class OnDeviceTranslator @Inject constructor() {
    suspend fun identify(text: String): String = withContext(Dispatchers.Default) {
        val id = LanguageIdentification.getClient()
        runCatching { id.identifyLanguage(text).awaitTask() }.getOrDefault("und")
    }

    suspend fun translate(
        text: String,
        settings: TranslationSettings,
        onProgress: (String) -> Unit = {},
    ): TranslationOutcome = withContext(Dispatchers.Default) {
        if (text.isBlank()) return@withContext TranslationOutcome("", "ML Kit", sourceLanguage = settings.sourceLanguage)
        val source = if (settings.sourceLanguage == "auto") identify(text) else settings.sourceLanguage
        val sourceCode = TranslateLanguage.fromLanguageTag(source)
            ?: return@withContext TranslationOutcome(text, "ML Kit", sourceLanguage = source, error = "Unsupported source language: $source")
        val targetCode = TranslateLanguage.fromLanguageTag(settings.targetLanguage)
            ?: return@withContext TranslationOutcome(text, "ML Kit", sourceLanguage = source, error = "Unsupported target language: ${settings.targetLanguage}")
        if (sourceCode == targetCode) return@withContext TranslationOutcome(text, "ML Kit", sourceLanguage = source)
        val translator = Translation.getClient(
            TranslatorOptions.Builder()
                .setSourceLanguage(sourceCode)
                .setTargetLanguage(targetCode)
                .build(),
        )
        try {
            onProgress("Preparing ${settings.targetLanguage.uppercase()} model…")
            translator.downloadModelIfNeeded().awaitTask()
            onProgress("Translating…")
            val translated = translator.translate(text).awaitTask()
            TranslationOutcome(
                text = applyGlossary(translated, settings.glossary),
                engine = "ML Kit (on-device)",
                sourceLanguage = source,
            )
        } catch (error: Exception) {
            TranslationOutcome(text, "ML Kit", sourceLanguage = source, error = error.message ?: "On-device translation failed")
        } finally {
            translator.close()
        }
    }

    private fun applyGlossary(text: String, glossary: Map<String, String>): String =
        glossary.entries.fold(text) { acc, (from, to) -> acc.replace(from, to) }
}

private suspend fun <T> Task<T>.awaitTask(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { value -> if (cont.isActive) cont.resume(value) }
    addOnFailureListener { error -> if (cont.isActive) cont.resumeWithException(error) }
    addOnCanceledListener { cont.cancel() }
}
