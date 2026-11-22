package app.kurenai.core.network

import okhttp3.Interceptor
import okhttp3.Response
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

/**
 * Per-host politeness. Only one request starts per [minGapMs] window and a random
 * jitter of up to [jitterMs] keeps bursts from looking like a bot. Requests are
 * serialized per host with a small semaphore-like lock, never globally.
 */
class PoliteRateLimitInterceptor(
    private val minGapMs: Long = 180L,
    private val jitterMs: Long = 240L,
) : Interceptor {
    private val nextSlot = ConcurrentHashMap<String, Long>()
    private val locks = ConcurrentHashMap<String, Any>()

    override fun intercept(chain: Interceptor.Chain): Response {
        val host = chain.request().url.host.lowercase()
        val lock = locks.computeIfAbsent(host) { Any() }
        synchronized(lock) {
            val now = System.currentTimeMillis()
            val slot = nextSlot[host] ?: now
            val wait = (slot - now).coerceAtLeast(0L) + Random.nextLong(0, jitterMs + 1)
            if (wait > 0) Thread.sleep(wait)
            nextSlot[host] = System.currentTimeMillis() + minGapMs
        }
        return chain.proceed(chain.request())
    }
}

/** Cache of domains whose Cloudflare challenge has already been solved. */
class ChallengeCache(private val ttlMs: Long = 20 * 60_000L) {
    private val passed = ConcurrentHashMap<String, Long>()

    fun markPassed(host: String) { passed[host] = System.currentTimeMillis() }

    fun isPassed(host: String): Boolean {
        val at = passed[host] ?: return false
        return System.currentTimeMillis() - at < ttlMs
    }

    fun clear() = passed.clear()
}
