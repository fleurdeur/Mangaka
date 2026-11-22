package app.kurenai.core.data

import app.kurenai.core.datastore.SecureSecretStore
import app.kurenai.core.model.Manga
import app.kurenai.core.model.Recommendation
import app.kurenai.core.network.KurenaiHttp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

@Serializable
data class AiAnswer(
    val text: String,
    val recommendations: List<Recommendation> = emptyList(),
    val usedCloud: Boolean = false,
)

/**
 * "Ask AI" librarian. Runs a local retrieval/ranking pass first, so it works
 * offline and without an account. Cloud completion is opt-in and uses the user's
 * own OpenAI-compatible endpoint and key; no analytics are attached.
 */
@Singleton
class AiLibrarian @Inject constructor(
    private val recommendationEngine: RecommendationEngine,
    private val searchRepository: SearchRepository,
    private val secrets: SecureSecretStore,
    private val http: KurenaiHttp,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun ask(question: String, candidates: List<Manga>): AiAnswer = withContext(Dispatchers.IO) {
        val keywords = question.lowercase().split(Regex("\\s+")).filter { it.length > 2 }
        val taste = recommendationEngine.tasteProfile()
        val scored = candidates.map { manga ->
            var score = 0f
            val haystack = (manga.title + " " + manga.tags.joinToString(" ") + " " + manga.description.orEmpty()).lowercase()
            keywords.forEach { keyword -> if (haystack.contains(keyword)) score += 0.3f }
            manga.tags.forEach { tag -> score += (taste[tag] ?: 0f) * 0.05f }
            if (question.contains("short") && manga.chapterCount in 1..30) score += 0.3f
            if (question.contains("romance") && manga.tags.any { it.contains("romance", true) }) score += 0.5f
            if (question.contains("unfinished") && manga.status == app.kurenai.core.model.MangaStatus.ONGOING) score += 0.2f
            manga to (score + manga.rating * 0.2f)
        }.sortedByDescending { it.second }
        val top = recommendationEngine.recommend(scored.take(max(20, keywords.size * 5)).map { it.first }, 5)
        val localText = buildLocalAnswer(question, top)
        val endpoint = secrets.get("ai_endpoint")
        val key = secrets.get("ai_api_key")
        val model = secrets.get("ai_model") ?: "gpt-4o-mini"
        if (endpoint.isNullOrBlank() || key.isNullOrBlank() || !secrets.get("ai_enabled").toBoolean()) {
            return@withContext AiAnswer(localText, top, usedCloud = false)
        }
        val cloud = runCatching { callCloud(endpoint, key, model, question, top) }.getOrNull()
        AiAnswer(cloud ?: localText, top, usedCloud = cloud != null)
    }

    private fun buildLocalAnswer(question: String, top: List<Recommendation>): String {
        if (top.isEmpty()) return "I could not find a strong match in your sources yet. Try broadening the question, or enable more sources in Settings → Sources."
        return buildString {
            append("Here is what I would pick for \"").append(question.trim()).append("\":\n\n")
            top.forEachIndexed { index, rec ->
                append(index + 1).append(". ").append(rec.manga.title)
                append(" — ").append(rec.reasons.firstOrNull().orEmpty()).append('\n')
            }
            append("\nI ranked these locally from your taste profile, ratings and the sources you have enabled.")
        }
    }

    private fun callCloud(endpoint: String, key: String, model: String, question: String, recs: List<Recommendation>): String {
        val catalogue = recs.joinToString("\n") { "- ${it.manga.title} [${it.manga.tags.joinToString()}]: ${it.manga.description.orEmpty().take(220)}" }
        val payload = buildString {
            append("""{"model":""").append(org.json.JSONObject.quote(model))
            append(""","messages":[{"role":"system","content":"You are Kurenai's librarian. Recommend and compare series using only the supplied catalogue. Be concise, warm and concrete. Never invent chapters."},{"role":"user","content":""")
            append(org.json.JSONObject.quote("Question: $question\n\nCatalogue:\n$catalogue"))
            append(""""}],"temperature":0.4}""")
        }
        val request = Request.Builder()
            .url(endpoint.trimEnd('/') + "/chat/completions")
            .header("Authorization", "Bearer $key")
            .post(payload.toRequestBody("application/json".toMediaType()))
            .build()
        http.client().newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) error("Cloud AI HTTP ${response.code}")
            val root = org.json.JSONObject(body)
            return root.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")
        }
    }
}
