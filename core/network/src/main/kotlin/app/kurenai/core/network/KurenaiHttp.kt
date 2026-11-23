package app.kurenai.core.network

import app.kurenai.core.model.AppSettings
import app.kurenai.core.model.ProxySettings
import app.kurenai.core.model.ProxyType
import app.kurenai.core.network.challenge.CloudflareInterceptor
import app.kurenai.core.network.challenge.InteractiveChallengeManager
import app.kurenai.core.network.challenge.WebViewChallengeSolver
import app.kurenai.core.network.cookies.EncryptedCookieJar
import app.kurenai.core.datastore.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.Authenticator
import okhttp3.Cache
import okhttp3.Credentials
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.dnsoverhttps.DnsOverHttps
import java.io.File
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext

data class ChallengeDiagnostic(
    val at: Long = 0L,
    val host: String = "",
    val status: String = "idle",
    val message: String = "No challenges resolved yet",
    val interactive: Boolean = false,
)

@Singleton
class NetworkDiagnostics @Inject constructor() {
    private val _lastChallenge = MutableStateFlow(ChallengeDiagnostic())
    val lastChallenge: StateFlow<ChallengeDiagnostic> = _lastChallenge.asStateFlow()

    fun record(host: String, status: String, message: String, interactive: Boolean = false) {
        _lastChallenge.value = ChallengeDiagnostic(System.currentTimeMillis(), host, status, message, interactive)
    }
}

/**
 * Creates and owns the shared OkHttp client. A real Chromium user agent is kept
 * consistent across challenge solving and replay. DNS-over-HTTPS, proxy and
 * challenge toggles are applied by rebuilding the client when settings change.
 */
@Singleton
class KurenaiHttp @Inject constructor(
    @ApplicationContext context: Context,
    private val settingsStore: SettingsStore,
    private val cookieJar: EncryptedCookieJar,
    private val interactiveChallengeManager: InteractiveChallengeManager,
    private val diagnostics: NetworkDiagnostics,
) {
    companion object {
        const val CHROME_UA =
            "Mozilla/5.0 (Linux; Android 15; Pixel 9) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/141.0.0.0 Mobile Safari/537.36"
    }

    private val solver = WebViewChallengeSolver(context, interactiveChallengeManager)
    private val challengeCache = ChallengeCache()
    private val domainGate = DomainGate()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var settings: AppSettings = AppSettings()

    private val baseBuilder: OkHttpClient.Builder = OkHttpClient.Builder()
        .protocols(listOf(Protocol.HTTP_2, Protocol.HTTP_1_1))
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(45, TimeUnit.SECONDS)
        .callTimeout(90, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .cache(Cache(File(context.cacheDir, "http_cache"), 96L * 1024L * 1024L))
        .cookieJar(cookieJar)
        .followRedirects(true)
        .followSslRedirects(true)
        .addInterceptor { chain ->
            val ua = chain.request().header("User-Agent") ?: CHROME_UA
            val builder = chain.request().newBuilder()
                .header("User-Agent", ua)
                .header("Accept-Language", "en-US,en;q=0.9,ja;q=0.8,ko;q=0.7,zh;q=0.6")
                .header("Accept", chain.request().header("Accept") ?: "*/*")
            if (ua.contains("Chrome/")) {
                // Keep the client hints consistent with the Chromium user agent.
                // Missing/contradictory hints are a common bot signal.
                builder.header("sec-ch-ua", "\"Chromium\";v=\"141\", \"Not?A_Brand\";v=\"24\"")
                builder.header("sec-ch-ua-mobile", "?1")
                builder.header("sec-ch-ua-platform", "\"Android\"")
            }
            chain.proceed(builder.build())
        }
        .addInterceptor(PoliteRateLimitInterceptor())
        .addInterceptor(
            CloudflareInterceptor(
                cookieJar = cookieJar,
                solver = solver,
                challengeCache = challengeCache,
                domainGate = domainGate,
                enabled = { settings.challengeHandlingEnabled },
                defaultUserAgent = CHROME_UA,
            ),
        )
        .addInterceptor(BackoffInterceptor())

    @Volatile
    private var client: OkHttpClient = baseBuilder.build()

    private val dohProviders: List<Dns> by lazy {
        listOf(
            dnsOverHttps("https://cloudflare-dns.com/dns-query", "1.1.1.1", "1.0.0.1"),
            dnsOverHttps("https://dns.google/dns-query", "8.8.8.8", "8.8.4.4"),
        )
    }

    init {
        // Build once with the current settings. Without this first build the
        // DataStore flow only rebuilt on a *change*, so the saved DoH/proxy
        // choice was silently ignored until the user toggled it.
        client = buildClient(settings)
        scope.launch {
            settingsStore.settings.collect { updated ->
                val rebuild = settings.proxy != updated.proxy ||
                    settings.dohEnabled != updated.dohEnabled ||
                    settings.challengeHandlingEnabled != updated.challengeHandlingEnabled
                settings = updated
                if (rebuild) rebuildClient(updated)
            }
        }
    }

    fun client(): OkHttpClient = client
    fun cookieJar(): EncryptedCookieJar = cookieJar
    val challenges: InteractiveChallengeManager get() = interactiveChallengeManager
    val diagnosticsFlow: StateFlow<ChallengeDiagnostic> get() = diagnostics.lastChallenge

    fun markChallengeResult(host: String, passed: Boolean, message: String, interactive: Boolean) {
        diagnostics.record(host, if (passed) "passed" else "failed", message, interactive)
        if (passed) challengeCache.markPassed(host)
    }

    private fun buildClient(settings: AppSettings): OkHttpClient {
        val builder = baseBuilder
        builder.dns(ResilientDns(preferEncrypted = settings.dohEnabled, encrypted = dohProviders))
        if (settings.proxy.enabled) {
            builder.proxy(settings.proxy.asProxy())
            builder.proxyAuthenticator(settings.proxy.authenticator())
        } else {
            builder.proxy(null)
            builder.proxyAuthenticator(Authenticator.NONE)
        }
        NetworkLog.debug("http", "client built (doh=${settings.dohEnabled}, proxy=${settings.proxy.enabled})")
        return builder.build()
    }

    private fun rebuildClient(settings: AppSettings) {
        client = buildClient(settings)
    }

    private fun dnsOverHttps(endpoint: String, vararg bootstrapIps: String): Dns {
        val bootstrap = OkHttpClient.Builder()
            .connectTimeout(3, TimeUnit.SECONDS)
            .readTimeout(3, TimeUnit.SECONDS)
            .callTimeout(4, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
        return DnsOverHttps.Builder()
            .client(bootstrap)
            .url(endpoint.toHttpUrl())
            .includeIPv6(true)
            .bootstrapDnsHosts(bootstrapIps.map { InetAddress.getByName(it) })
            .build()
    }

    private fun ProxySettings.asProxy(): Proxy {
        val type = when (type) {
            ProxyType.SOCKS5 -> Proxy.Type.SOCKS
            else -> Proxy.Type.HTTP
        }
        return Proxy(type, InetSocketAddress(host, port))
    }

    private fun ProxySettings.authenticator(): Authenticator = Authenticator { _, response ->
        if (response.request.header("Proxy-Authorization") != null) return@Authenticator null
        if (username.isBlank()) return@Authenticator null
        response.request.newBuilder()
            .header("Proxy-Authorization", Credentials.basic(username, password))
            .build()
    }
}
