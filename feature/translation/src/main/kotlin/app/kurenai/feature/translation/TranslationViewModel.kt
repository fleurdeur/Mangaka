package app.kurenai.feature.translation

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.provider.MediaStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.kurenai.core.datastore.SecureSecretStore
import app.kurenai.core.datastore.SettingsStore
import app.kurenai.core.model.TranslationProviderKind
import app.kurenai.core.model.TranslationSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import javax.inject.Inject

data class TranslationUiState(
    val settings: TranslationSettings = TranslationSettings(),
    val input: String = "",
    val output: String = "",
    val engine: String = "",
    val busy: Boolean = false,
    val status: String? = null,
    val error: String? = null,
    val ocrLines: List<OcrLine> = emptyList(),
    val detectedSource: String? = null,
    val cloudKeyPresent: Boolean = false,
)

@HiltViewModel
class TranslationViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsStore: SettingsStore,
    private val secrets: SecureSecretStore,
    private val ocr: MlKitOcrEngine,
    private val translator: CloudTranslator,
) : ViewModel() {
    private val _state = MutableStateFlow(TranslationUiState())
    val state: StateFlow<TranslationUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val settings = settingsStore.settings.first().translation
            _state.value = _state.value.copy(
                settings = settings,
                cloudKeyPresent = translator.hasCloudKey(),
            )
        }
    }

    fun setInput(value: String) { _state.value = _state.value.copy(input = value, error = null) }
    fun setProvider(kind: TranslationProviderKind) = updateSettings { it.copy(provider = kind) }
    fun setSource(language: String) = updateSettings { it.copy(sourceLanguage = language) }
    fun setTarget(language: String) = updateSettings { it.copy(targetLanguage = language) }
    fun setLearning(enabled: Boolean) = updateSettings { it.copy(languageLearning = enabled) }
    fun setFurigana(enabled: Boolean) = updateSettings { it.copy(showFurigana = enabled) }

    fun setCloudConfig(endpoint: String, model: String, key: String) {
        if (endpoint.isNotBlank()) secrets.put("translation_endpoint", endpoint.trim())
        if (model.isNotBlank()) secrets.put("translation_model", model.trim())
        if (key.isNotBlank()) secrets.put("translation_api_key", key.trim())
        _state.value = _state.value.copy(cloudKeyPresent = translator.hasCloudKey(), status = "Cloud translation settings saved privately.")
    }

    fun translate() {
        val current = _state.value
        if (current.input.isBlank()) {
            _state.value = current.copy(error = "Add some text or scan a page first.")
            return
        }
        _state.value = current.copy(busy = true, error = null, status = null)
        viewModelScope.launch {
            val outcome = translator.translate(current.input, current.settings)
            _state.value = _state.value.copy(
                busy = false,
                output = outcome.text,
                engine = outcome.engine,
                detectedSource = outcome.sourceLanguage,
                error = outcome.error,
            )
        }
    }

    fun scan(uri: Uri) {
        _state.value = _state.value.copy(busy = true, status = "Reading the page…", error = null)
        viewModelScope.launch {
            val bitmap = withContext(Dispatchers.IO) {
                runCatching { MediaStore.Images.Media.getBitmap(context.contentResolver, uri) }.getOrNull()
            }
            if (bitmap == null) {
                _state.value = _state.value.copy(busy = false, error = "Could not open that image.")
                return@launch
            }
            val lines = runCatching { ocr.recognize(bitmap) }.getOrDefault(emptyList())
            if (lines.isEmpty()) {
                _state.value = _state.value.copy(busy = false, error = "No text found. Try a sharper crop of the bubbles.")
                return@launch
            }
            val text = lines.joinToString("\n") { it.text }
            _state.value = _state.value.copy(
                input = text,
                ocrLines = lines,
                busy = false,
                status = "${lines.size} bubble${if (lines.size == 1) "" else "s"} recognized on-device.",
            )
            translate()
        }
    }

    fun saveGlossary(raw: String) {
        val map = raw.lines().mapNotNull { line ->
            val parts = line.split('=', '→', '|').map { it.trim() }
            if (parts.size == 2 && parts[0].isNotBlank() && parts[1].isNotBlank()) parts[0] to parts[1] else null
        }.toMap()
        updateSettings { it.copy(glossary = map) }
        _state.value = _state.value.copy(status = "Glossary saved (${map.size} terms).")
    }

    fun clearOutput() { _state.value = _state.value.copy(output = "", engine = "", error = null) }

    private fun updateSettings(transform: (TranslationSettings) -> TranslationSettings) {
        val updated = transform(_state.value.settings)
        _state.value = _state.value.copy(settings = updated)
        viewModelScope.launch { settingsStore.update { it.copy(translation = updated) } }
    }

    companion object {
        val languages = listOf("auto", "en", "ja", "ko", "zh", "es", "fr", "pt", "id", "th", "vi", "ru", "de", "it", "ar", "hi", "tr", "pl", "uk")
        fun languageName(tag: String): String = if (tag == "auto") "Detect" else Locale.forLanguageTag(tag).getDisplayLanguage(Locale.getDefault()).replaceFirstChar { it.uppercase() }
    }
}
