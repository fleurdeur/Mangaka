package app.kurenai.core.network.challenge

import app.kurenai.core.network.ChallengeCache
import app.kurenai.core.network.DomainGate
import app.kurenai.core.network.NetworkLog
import app.kurenai.core.network.cookies.EncryptedCookieJar
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.io.IOException

/**
 * Detects challenge responses, serializes solving per host, replays the original
 * request with clearance cookies and the exact same user agent, and caches the
 * "passed" state. Non-challenge traffic passes through untouched.
 */
class CloudflareInterceptor(
    private val cookieJar: EncryptedCookieJar,
    private val solver: WebViewChallengeSolver,
    private val challengeCache: ChallengeCache,
    private val domainGate: DomainGate,
    private val enabled: () -> Boolean,
    private val defaultUserAgent: String,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        if (!enabled()) return chain.proceed(original)

        val request = withConsistentUserAgent(original)
        val firstResponse = try {
            chain.proceed(request)
        } catch (io: IOException) {
            throw io
        }

        val bodyPreview = runCatching { firstResponse.peekBody(96 * 1024).string() }.getOrNull()
        if (!ChallengeDetector.isChallenge(firstResponse, bodyPreview)) {
            return firstResponse
        }

        val host = request.url.host
        NetworkLog.debug("challenge", "detected on $host (${firstResponse.code})")
        firstResponse.close()

        return runBlocking {
            domainGate.withHostLock(host) {
                // Another request may have solved it while we waited.
                if (challengeCache.isPassed(host) || cookieJar.hasClearance(host)) {
                    return@withHostLock replay(chain, request)
                }
                val solution = solver.solve(request.url.toString(), request.header("User-Agent") ?: defaultUserAgent)
                if (!solution.passed) {
                    NetworkLog.debug("challenge", "not solved for $host: ${solution.error}")
                    return@withHostLock challengeFailedResponse(request, solution.error ?: "Challenge not solved")
                }
                challengeCache.markPassed(host)
                importCookies(request, solution.cookies)
                replay(chain, request)
            }
        }
    }

    private fun replay(chain: Interceptor.Chain, request: Request): Response {
        val url = request.url
        val cookieHeader = cookieJar.loadForRequest(url).joinToString("; ") { "${it.name}=${it.value}" }
        val replay = request.newBuilder()
            .header("User-Agent", request.header("User-Agent") ?: defaultUserAgent)
            .apply { if (cookieHeader.isNotBlank()) header("Cookie", cookieHeader) }
            .build()
        return chain.proceed(replay)
    }

    private fun importCookies(request: Request, cookieString: String) {
        if (cookieString.isBlank()) return
        val host = request.url.host
        val cookies = cookieString.split(';').mapNotNull { part ->
            val name = part.substringBefore('=').trim()
            val value = part.substringAfter('=', "").trim()
            if (name.isBlank()) null else runCatching {
                okhttp3.Cookie.Builder()
                    .name(name)
                    .value(value)
                    .domain(host)
                    .path("/")
                    .expiresAt(System.currentTimeMillis() + 60 * 60_000L)
                    .build()
            }.getOrNull()
        }
        cookieJar.saveFromResponse(request.url, cookies)
    }

    private fun withConsistentUserAgent(request: Request): Request {
        val ua = request.header("User-Agent") ?: defaultUserAgent
        return request.newBuilder().header("User-Agent", ua).build()
    }

    private fun challengeFailedResponse(request: Request, message: String): Response =
        Response.Builder()
            .request(request)
            .protocol(okhttp3.Protocol.HTTP_1_1)
            .code(403)
            .message("Cloudflare challenge unresolved: $message")
            .body("".toResponseBody(null))
            .build()
}
