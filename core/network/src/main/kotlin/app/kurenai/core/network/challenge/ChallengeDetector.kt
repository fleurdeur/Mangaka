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
        "_cf_chl_opt",
        "ddos-guard",
        "enable javascript and cookies to continue",
        "attention required! | cloudflare",
        "verify you are human",
    )

    fun isChallenge(response: Response, body: String?): Boolean {
        if (response.code !in setOf(200, 403, 429, 503)) return false
        val headerHit = response.headers.names().any { it.lowercase() in challengeHeaders } ||
            response.header("Server")?.contains("cloudflare", ignoreCase = true) == true ||
            response.header("cf-ray") != null
        val lower = body.orEmpty().lowercase()
        // These strings only appear on an interstitial. Generic Cloudflare
        // snippets ("turnstile", "challenge-platform") are also embedded in
        // ordinary pages, so they are deliberately not treated as markers.
        val markerHit = markers.any(lower::contains)

        // Headers alone are enough for an error response, but some proxies strip
        // Cloudflare's headers and leave only the interstitial page. Markers are
        // also accepted on a 200 response so an HTML challenge never reaches a
        // parser as if it were a normal search page.
        return markerHit || (headerHit && response.code != 200)
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
