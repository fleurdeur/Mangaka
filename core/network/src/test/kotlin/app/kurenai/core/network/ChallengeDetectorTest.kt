package app.kurenai.core.network

import app.kurenai.core.network.challenge.ChallengeDetector
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChallengeDetectorTest {
    private fun response(code: Int, server: String? = null, body: String = "") = Response.Builder()
        .request(Request.Builder().url("https://example.com/chapter/1").build())
        .protocol(Protocol.HTTP_1_1)
        .code(code)
        .message("test")
        .apply { server?.let { header("Server", it) } }
        .body(body.toResponseBody())
        .build()

    @Test
    fun `cloudflare 403 with interstitial is a challenge`() {
        val result = ChallengeDetector.isChallenge(
            response(403, "cloudflare"),
            "<html><title>Just a moment...</title></html>",
        )
        assertTrue(result)
    }

    @Test
    fun `ordinary 404 is not a challenge`() {
        assertFalse(ChallengeDetector.isChallenge(response(404), "not found"))
    }

    @Test
    fun `plain 403 without cloudflare headers is not a challenge`() {
        assertFalse(ChallengeDetector.isChallenge(response(403), "forbidden"))
    }

    @Test
    fun `turnstile needs interaction`() {
        assertTrue(ChallengeDetector.needsInteraction("<div class=cf-turnstile></div>"))
        assertFalse(ChallengeDetector.needsInteraction("chapter page"))
    }
}
