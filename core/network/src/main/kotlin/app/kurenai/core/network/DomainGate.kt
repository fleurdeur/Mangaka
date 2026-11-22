package app.kurenai.core.network

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

/** Per-host mutexes so only one challenge/solve attempt runs for a domain. */
class DomainGate {
    private val mutexes = ConcurrentHashMap<String, Mutex>()

    suspend fun <T> withHostLock(host: String, block: suspend () -> T): T =
        mutexes.computeIfAbsent(host) { Mutex() }.withLock { block() }
}

/** Exponential backoff with jitter for 429/503 responses. */
class BackoffInterceptor(
    private val maxRetries: Int = 3,
    private val baseDelayMs: Long = 800L,
) : okhttp3.Interceptor {
    override fun intercept(chain: okhttp3.Interceptor.Chain): okhttp3.Response {
        var attempt = 0
        var response = chain.proceed(chain.request())
        while (attempt < maxRetries && (response.code == 429 || response.code == 503)) {
            val retryAfter = response.header("Retry-After")?.toLongOrNull()?.times(1000L)
            val wait = retryAfter ?: ((baseDelayMs shl attempt) + kotlin.random.Random.nextLong(0, 400))
            NetworkLog.debug("backoff", "HTTP ${response.code}, retrying in ${wait}ms (${attempt + 1}/$maxRetries)")
            response.close()
            Thread.sleep(wait)
            attempt++
            response = chain.proceed(chain.request())
        }
        return response
    }
}
