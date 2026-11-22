package app.kurenai.core.network.cookies

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import app.kurenai.core.network.NetworkLog
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import java.util.concurrent.ConcurrentHashMap

/**
 * Per-domain cookie storage backed by EncryptedSharedPreferences. Cookies keep their
 * natural expiry and are dropped on read once expired. The jar also tracks when a
 * clearance cookie was first seen so the network layer can proactively refresh it.
 */
class EncryptedCookieJar(context: Context) : CookieJar {

    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "kurenai_cookies",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    private val cache = ConcurrentHashMap<String, MutableList<Cookie>>()
    private val clearanceSeen = ConcurrentHashMap<String, Long>()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        if (cookies.isEmpty()) return
        val host = url.host
        val now = System.currentTimeMillis()
        synchronized(this) {
            val current = load(host).toMutableList()
            for (cookie in cookies) {
                current.removeAll { it.name == cookie.name && it.path == cookie.path }
                if (cookie.expiresAt > now) {
                    current += cookie
                    if (cookie.name == "cf_clearance" || cookie.name == "__cf_bm" || cookie.name == "ddos-guard") {
                        clearanceSeen[cookie.name + host] = now
                    }
                }
            }
            cache[host] = current
            persist(host, current)
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val host = url.host
        val now = System.currentTimeMillis()
        val cookies = synchronized(this) {
            val current = cache[host] ?: load(host).also { cache[host] = it.toMutableList() }
            val valid = current.filter { it.expiresAt > now && it.matches(url) }
            if (valid.size != current.size) {
                cache[host] = valid.toMutableList()
                persist(host, valid)
            }
            valid
        }
        return cookies
    }

    fun clear(host: String? = null) {
        synchronized(this) {
            if (host == null) {
                prefs.edit().clear().apply()
                cache.clear()
                clearanceSeen.clear()
            } else {
                prefs.edit().remove(key(host)).apply()
                cache.remove(host)
                clearanceSeen.keys.removeAll { it.endsWith(host) }
            }
        }
    }

    fun hasClearance(host: String, maxAgeMs: Long = 25 * 60_000L): Boolean {
        val now = System.currentTimeMillis()
        return clearanceSeen.entries.any { (key, at) -> key.endsWith(host) && now - at < maxAgeMs }
    }

    fun exportAll(): Map<String, String> = prefs.all.mapNotNull { (k, v) -> (v as? String)?.let { k to it } }.toMap()

    private fun load(host: String): MutableList<Cookie> {
        val raw = prefs.getString(key(host), null) ?: return mutableListOf()
        val now = System.currentTimeMillis()
        return raw.lineSequence().mapNotNull { line ->
            line.takeIf { it.isNotBlank() }?.let { runCatching { Cookie.parse(HttpUrl.Builder().scheme("https").host(host).build(), it) }.getOrNull() }
        }.filter { it.expiresAt > now }.toMutableList()
    }

    private fun persist(host: String, cookies: List<Cookie>) {
        if (cookies.isEmpty()) {
            prefs.edit().remove(key(host)).apply()
        } else {
            prefs.edit().putString(key(host), cookies.joinToString("\n") { it.toString() }).apply()
        }
        NetworkLog.debug("cookie-jar", "saved ${cookies.size} cookies for $host")
    }

    private fun key(host: String) = "cookies_$host"
}
