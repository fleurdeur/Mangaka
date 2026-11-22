package app.kurenai.core.network.challenge

import app.kurenai.core.network.NetworkLog
import okhttp3.HttpUrl
import okhttp3.Request
import okhttp3.Response

/** Heuristics that identify a Cloudflare / DDoS-Guard / Turnstile interstitial. */
object ChallengeDetector {

    private val challengeHeaders = setOf(
        "cf-mitigated",
        "cf-chl-bypass",
        "cf-challenge",
        "x-ddos-protection",
    )

    private val markers = listOf(
        "just a moment",
        "checking your browser",
        "cf-browser-verification",
        "cf-challenge",
        "turnstile",
        "challenge-platform",
        "ddos-guard",
        "enable javascript and cookies to continue",
        "attention required! | cloudflare",
        "verify you are human",
    )

    fun isChallenge(response: Response, body: String?): Boolean {
        if (response.code != 403 && response.code != 503 && response.code != 429) return false
        val headerHit = response.headers.names().any { it.lowercase() in challengeHeaders } ||
            response.header("Server")?.contains("cloudflare", ignoreCase = true) == true ||
            response.header("cf-ray") != null
        if (!headerHit) return false
        if (body.isNullOrBlank()) return true
        val lower = body.lowercase()
        return markers.any(lower::contains) || response.code == 403
    }

    fun needsInteraction(body: String?): Boolean {
        if (body.isNullOrBlank()) return false
        val lower = body.lowercase()
        return lower.contains("turnstile") ||
            lower.contains("captcha") ||
            lower.contains("verify you are human") ||
            lower.contains("challenge-form")
    }

    fun looksLikeChallenge(body: String?): Boolean {
        if (body.isNullOrBlank()) return false
        val lower = body.lowercase()
        return markers.any(lower::contains)
    }

    fun hostOf(request: Request): String = request.url.host

    fun originOf(url: HttpUrl): String = "${url.scheme}://${url.host}"
}
