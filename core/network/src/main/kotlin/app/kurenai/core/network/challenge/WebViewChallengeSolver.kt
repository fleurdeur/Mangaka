package app.kurenai.core.network.challenge

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import app.kurenai.core.network.NetworkLog
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.util.UUID

data class ChallengeSolution(
    val passed: Boolean,
    val cookies: String = "",
    val userAgent: String,
    val interactive: Boolean = false,
    val error: String? = null,
)

data class InteractiveChallenge(
    val id: String,
    val url: String,
    val host: String,
    val userAgent: String,
)

class InteractiveChallengeManager {
    private val _current = MutableStateFlow<InteractiveChallenge?>(null)
    val current: StateFlow<InteractiveChallenge?> = _current.asStateFlow()
    private var pending: CompletableDeferred<Boolean>? = null

    fun request(url: String, host: String, userAgent: String): CompletableDeferred<Boolean> {
        pending?.complete(false)
        val challenge = InteractiveChallenge(UUID.randomUUID().toString(), url, host, userAgent)
        val deferred = CompletableDeferred<Boolean>()
        pending = deferred
        _current.value = challenge
        return deferred
    }

    fun complete(id: String, passed: Boolean) {
        if (_current.value?.id != id) return
        _current.value = null
        pending?.complete(passed)
        pending = null
    }

    fun cancel() {
        _current.value = null
        pending?.complete(false)
        pending = null
    }
}

/**
 * Resolves a challenge with a real Chromium WebView. It loads the page with the
 * same user agent as the API request, waits for the clearance cookie, then hands
 * the cookie string back to the interceptor for replay. When the challenge needs
 * a human (Turnstile, CAPTCHA, login), a full-screen host is requested.
 */
class WebViewChallengeSolver(
    private val context: Context,
    private val interactiveManager: InteractiveChallengeManager,
) {

    suspend fun solve(url: String, userAgent: String, allowInteractive: Boolean = true): ChallengeSolution {
        val host = runCatching { java.net.URI(url).host ?: "" }.getOrDefault("")
        val hidden = attempt(url, userAgent, host)
        if (hidden.passed) return hidden
        if (!allowInteractive) return hidden
        NetworkLog.debug("challenge", "hidden solve failed for $host, requesting interaction")
        val accepted = interactiveManager.request(url, host, userAgent).awaitCompat()
        if (!accepted) return hidden.copy(error = "User cancelled challenge")
        return collect(url, userAgent, interactive = true)
    }

    private suspend fun attempt(url: String, userAgent: String, host: String): ChallengeSolution =
        withContext(Dispatchers.Main.immediate) {
            var webView: WebView? = null
            try {
                val wv = createWebView(userAgent)
                webView = wv
                wv.loadUrl(url)
                val deadline = System.currentTimeMillis() + 22_000L
                var lastBody = ""
                while (isActive && System.currentTimeMillis() < deadline) {
                    delay(450)
                    val cookies = CookieManager.getInstance().getCookie(url).orEmpty()
                    val title = wv.title.orEmpty()
                    lastBody = title
                    if (cookies.contains("cf_clearance") || cookies.contains("__cf_bm") || title.contains("Just a moment", true).not() && title.isNotBlank()) {
                        if (cookies.isNotBlank()) {
                            NetworkLog.debug("challenge", "hidden challenge passed for $host")
                            return@withContext ChallengeSolution(true, cookies, userAgent)
                        }
                    }
                    if (ChallengeDetector.needsInteraction(title)) {
                        return@withContext ChallengeSolution(false, cookies, userAgent, error = "needs_interaction")
                    }
                }
                ChallengeSolution(false, CookieManager.getInstance().getCookie(url).orEmpty(), userAgent, error = "timeout:$lastBody")
            } catch (t: Throwable) {
                NetworkLog.debug("challenge", "hidden solver error: ${t.message}")
                ChallengeSolution(false, "", userAgent, error = t.message)
            } finally {
                runCatching { webView?.stopLoading() }
                runCatching { webView?.destroy() }
            }
        }

    private suspend fun collect(url: String, userAgent: String, interactive: Boolean): ChallengeSolution =
        withContext(Dispatchers.Main.immediate) {
            val cookies = CookieManager.getInstance().getCookie(url).orEmpty()
            NetworkLog.debug("challenge", "collected ${cookies.length} cookie chars for ${runCatching { java.net.URI(url).host }.getOrNull()}")
            ChallengeSolution(cookies.contains("cf_clearance") || cookies.isNotBlank(), cookies, userAgent, interactive)
        }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebView(userAgent: String): WebView = WebView(context).apply {
        settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            cacheMode = WebSettings.LOAD_DEFAULT
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            this.userAgentString = userAgent
            loadsImagesAutomatically = false
            blockNetworkImage = true
        }
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
        webViewClient = WebViewClient()
        layout(0, 0, 1080, 1920)
    }
}

private suspend fun CompletableDeferred<Boolean>.awaitCompat(): Boolean =
    kotlinx.coroutines.withTimeoutOrNull(90_000L) { this@awaitCompat.await() } ?: false
