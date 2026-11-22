package app.kurenai.feature.translation

import app.kurenai.core.datastore.SecureSecretStore
import app.kurenai.core.model.TranslationProviderKind
import app.kurenai.core.model.TranslationSettings
import app.kurenai.core.network.KurenaiHttp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Optional cloud translation through an OpenAI-compatible endpoint (also covers
 * local LLM servers exposing the same API). The user supplies endpoint, model and
 * key; nothing is sent unless they press Translate.
 */
@Singleton
class CloudTranslator @Inject constructor(
    private val secrets: SecureSecretStore,
    private val http: KurenaiHttp,
    private val onDevice: OnDeviceTranslator,
) {
    suspend fun translate(
        text: String,
        settings: TranslationSettings,
    ): TranslationOutcome = withContext(Dispatchers.IO) {
        if (settings.provider != TranslationProviderKind.CLOUD_OPENAI_COMPATIBLE &&
            settings.provider != TranslationProviderKind.LOCAL_LLM
        ) {
            return@withContext onDevice.translate(text, settings)
        }
        val endpoint = settings.cloudEndpoint?.takeIf { it.isNotBlank() }
            ?: secrets.get("translation_endpoint")
            ?: secrets.get("ai_endpoint")
        val key = secrets.get("translation_api_key") ?: secrets.get("ai_api_key")
        val model = settings.cloudModel?.takeIf { it.isNotBlank() }
            ?: secrets.get("translation_model")
            ?: secrets.get("ai_model")
            ?: "gpt-4o-mini"
        if (endpoint.isNullOrBlank() || (settings.provider == TranslationProviderKind.CLOUD_OPENAI_COMPATIBLE && key.isNullOrBlank())) {
            val fallback = onDevice.translate(text, settings.copy(provider = TranslationProviderKind.ON_DEVICE_ML_KIT))
            return@withContext fallback.copy(error = fallback.error ?: "No cloud endpoint configured; used on-device translation.")
        }
        val sourceName = if (settings.sourceLanguage == "auto") detectSource(text) else settings.sourceLanguage
        val system = "You are a manga letterer. Translate dialogue into ${settings.targetLanguage}. " +
            "Preserve speaker tone, honorifics and sound effects. Return only the translation, no notes."
        val payload = JSONObject().apply {
            put("model", model)
            put("temperature", 0.2)
            put("messages", JSONArray().apply {
                put(JSONObject().put("role", "system").put("content", system))
                put(JSONObject().put("role", "user").put("content", text))
            })
        }
        val request = Request.Builder()
            .url(endpoint.trimEnd('/') + "/chat/completions")
            .apply { if (!key.isNullOrBlank()) header("Authorization", "Bearer $key") }
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
            .build()
        runCatching {
            http.client().newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) error("HTTP ${response.code}")
                val content = JSONObject(body).getJSONArray("choices").getJSONObject(0)
                    .getJSONObject("message").getString("content").trim()
                TranslationOutcome(
                    text = applyGlossary(content, settings.glossary),
                    engine = if (settings.provider == TranslationProviderKind.LOCAL_LLM) "Local LLM" else "Cloud ($model)",
                    usedNetwork = settings.provider == TranslationProviderKind.CLOUD_OPENAI_COMPATIBLE,
                    sourceLanguage = sourceName,
                )
            }
        }.getOrElse { error ->
            val fallback = onDevice.translate(text, settings.copy(provider = TranslationProviderKind.ON_DEVICE_ML_KIT))
            fallback.copy(error = "Cloud unavailable (${error.message}); used on-device translation.")
        }
    }

    suspend fun detectSource(text: String): String = onDevice.identify(text)

    fun hasCloudKey(): Boolean = !secrets.get("translation_api_key").isNullOrBlank() || !secrets.get("ai_api_key").isNullOrBlank()

    private fun applyGlossary(text: String, glossary: Map<String, String>): String =
        glossary.entries.fold(text) { acc, (from, to) -> acc.replace(from, to) }
}
