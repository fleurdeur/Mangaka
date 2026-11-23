package app.kurenai.core.network

import okhttp3.Dns
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.concurrent.ConcurrentHashMap

/**
 * DNS that never trusts a single resolver.
 *
 * Android devices, carrier DNS and public DoH endpoints fail independently. A
 * source that looks like "unable to resolve host" is often a resolver problem,
 * not a dead website. This implementation tries the preferred resolver first and
 * falls back to the other one, caches positive answers for a few minutes and
 * remembers a failed DoH endpoint so a blocked resolver does not add latency to
 * every request.
 */
class ResilientDns(
    private val preferEncrypted: Boolean,
    private val system: Dns = Dns.SYSTEM,
    private val encrypted: List<Dns> = emptyList(),
    private val positiveTtlMs: Long = 5 * 60_000L,
) : Dns {

    private data class Entry(
        val addresses: List<InetAddress>,
        val expiresAt: Long,
    )

    private val cache = ConcurrentHashMap<String, Entry>()

    @Volatile
    private var encryptedUnavailableUntil: Long = 0L

    override fun lookup(hostname: String): List<InetAddress> {
        val host = hostname.trim().lowercase().removeSuffix(".")
        if (host.isEmpty()) throw UnknownHostException("Empty host")

        val now = System.currentTimeMillis()
        cache[host]?.let { entry ->
            if (entry.expiresAt > now) return entry.addresses
            cache.remove(host, entry)
        }

        val encryptedUsable = encrypted.isNotEmpty() && now >= encryptedUnavailableUntil
        val providers = if (preferEncrypted && encryptedUsable) {
            encrypted + system
        } else {
            listOf(system) + if (encryptedUsable) encrypted else emptyList()
        }

        var lastFailure: Throwable? = null
        var encryptedFailures = 0
        var encryptedAttempts = 0
        for (provider in providers) {
            val isEncrypted = encrypted.any { it === provider }
            if (isEncrypted) encryptedAttempts++
            try {
                val addresses = provider.lookup(host).filter { !it.isAnyLocalAddress }
                if (addresses.isNotEmpty()) {
                    cache[host] = Entry(addresses, now + positiveTtlMs)
                    if (isEncrypted) encryptedUnavailableUntil = 0L
                    return addresses
                }
            } catch (t: Throwable) {
                lastFailure = t
                if (isEncrypted) encryptedFailures++
                NetworkLog.debug("dns", "resolver failed for $host: ${t.message ?: t::class.simpleName}")
            }
        }

        if (encryptedAttempts > 0 && encryptedFailures == encryptedAttempts) {
            // DoH is blocked or unhealthy. Skip it for two minutes instead of
            // paying a timeout on every source the user opens.
            encryptedUnavailableUntil = now + 2 * 60_000L
        }
        throw UnknownHostException(host).apply {
            initCause(lastFailure)
        }
    }

    fun clear() = cache.clear()
}
