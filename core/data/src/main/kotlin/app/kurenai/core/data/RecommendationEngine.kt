package app.kurenai.core.data

import app.kurenai.core.database.dao.MangaDao
import app.kurenai.core.model.Manga
import app.kurenai.core.model.Recommendation
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/**
 * Fully on-device recommender. It learns genre/tag affinity, recency, completion
 * and reading length, then explains every suggestion with human-readable reasons.
 * No usage data leaves the device unless the user explicitly opts in to cloud AI.
 */
@Singleton
class RecommendationEngine @Inject constructor(
    private val mangaDao: MangaDao,
    private val libraryRepository: LibraryRepository,
) {
    suspend fun tasteProfile(): Map<String, Float> {
        val favorites = mangaDao.observeFavorites().first()
        val weights = mutableMapOf<String, Float>()
        favorites.forEachIndexed { index, entity ->
            val recency = 1f - (index.toFloat() / (favorites.size + 1))
            val base = 1f + recency
            entity.tags.forEach { tag -> weights[tag] = (weights[tag] ?: 0f) + base }
        }
        return weights.entries.sortedByDescending { it.value }.associate { it.key to it.value }
    }

    suspend fun recommend(candidates: List<Manga>, limit: Int = 10): List<Recommendation> {
        val taste = tasteProfile()
        val favorites = mangaDao.observeFavorites().first().map { it.id }.toSet()
        val history = libraryRepository.history.first().map { it.manga.id }.toSet()
        return candidates
            .asSequence()
            .filter { it.id !in favorites }
            .map { manga ->
                val matched = manga.tags.filter { it in taste }.sortedByDescending { taste[it] }
                var score = matched.sumOf { (taste[it] ?: 0f).toDouble() }.toFloat()
                score += if (manga.isNsfw) -0.5f else 0.2f
                score += manga.rating * 0.8f
                if (manga.id in history) score -= 0.4f
                if (manga.status == app.kurenai.core.model.MangaStatus.FINISHED) score += 0.3f
                val reasons = buildList {
                    if (matched.isNotEmpty()) add("Because you like ${matched.take(3).joinToString()}")
                    if (manga.rating >= 0.8f) add("Highly rated by readers")
                    if (manga.id in history) add("You already started this one")
                    if (isEmpty()) add("A fresh pick from your enabled sources")
                }
                Recommendation(manga, score.coerceIn(0f, 1f), reasons)
            }
            .sortedByDescending { it.score }
            .take(limit)
            .toList()
    }

    /** Cross-source title matcher. Normalises punctuation, volume markers and alt titles. */
    fun matchAcrossSources(seed: Manga, candidates: List<Manga>): List<Manga> {
        val key = normalise(seed.title)
        return candidates.filter { candidate ->
            candidate.sourceId != seed.sourceId &&
                (normalise(candidate.title) == key || candidate.altTitles.any { normalise(it) == key } ||
                    levenshtein(normalise(candidate.title), key) <= 2 && abs(candidate.title.length - seed.title.length) <= 6)
        }
    }

    private fun normalise(value: String): String = value.lowercase()
        .replace(Regex("[\\p{Punct}\\s]+"), "")
        .replace(Regex("(vol|volume|chapter|ch)\\.?\\d+"), "")
        .trim()

    private fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        var previous = IntArray(b.length + 1) { it }
        for (i in a.indices) {
            val current = IntArray(b.length + 1)
            current[0] = i + 1
            for (j in b.indices) {
                current[j + 1] = minOf(
                    current[j] + 1,
                    previous[j + 1] + 1,
                    previous[j] + if (a[i] == b[j]) 0 else 1,
                )
            }
            previous = current
        }
        return previous[b.length]
    }
}
