package app.kurenai.core.network

import java.util.concurrent.ConcurrentHashMap

/** Lightweight in-memory logger. The debug performance dashboard reads this. */
object NetworkLog {
    data class Entry(val at: Long, val tag: String, val message: String)

    private const val MAX = 400
    private val entries = ArrayDeque<Entry>()

    fun debug(tag: String, message: String) {
        synchronized(entries) {
            entries.addLast(Entry(System.currentTimeMillis(), tag, message))
            while (entries.size > MAX) entries.removeFirst()
        }
    }

    fun recent(): List<Entry> = synchronized(entries) { entries.toList() }
}

class CircuitBreakerRegistry(
    private val failureThreshold: Int = 5,
    private val cooldownMs: Long = 60_000L,
) {
    data class State(val failures: Int = 0, val openedAt: Long = 0L) {
        fun isOpen(now: Long, cooldown: Long) = failures >= 5 && now - openedAt < cooldown
    }

    private val states = ConcurrentHashMap<String, State>()

    fun isAvailable(key: String): Boolean {
        val state = states[key] ?: return true
        return !(state.failures >= failureThreshold && System.currentTimeMillis() - state.openedAt < cooldownMs)
    }

    fun recordSuccess(key: String) {
        states[key] = State(0, 0)
    }

    fun recordFailure(key: String) {
        states.compute(key) { _, old ->
            val failures = (old?.failures ?: 0) + 1
            State(failures, if (failures >= failureThreshold) System.currentTimeMillis() else old?.openedAt ?: 0)
        }
    }

    fun failures(key: String): Int = states[key]?.failures ?: 0
}
