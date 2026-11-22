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
import okhttp3.Credentials
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.dnsoverhttps.DnsOverHttps
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
        .cookieJar(cookieJar)
        .followRedirects(true)
        .followSslRedirects(true)
        .addInterceptor { chain ->
            val ua = chain.request().header("User-Agent") ?: CHROME_UA
            chain.proceed(
                chain.request().newBuilder()
                    .header("User-Agent", ua)
                    .header("Accept-Language", "en-US,en;q=0.9,ja;q=0.8,ko;q=0.7,zh;q=0.6")
                    .header("Accept", chain.request().header("Accept") ?: "*/*")
                    .build(),
            )
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

    init {
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

    private fun rebuildClient(settings: AppSettings) {
        val builder = baseBuilder
        if (settings.dohEnabled) {
            builder.dns(dohDns())
        } else {
            builder.dns(Dns.SYSTEM)
        }
        if (settings.proxy.enabled) {
            builder.proxy(settings.proxy.asProxy())
            builder.proxyAuthenticator(settings.proxy.authenticator())
        } else {
            builder.proxy(null)
            builder.proxyAuthenticator(Authenticator.NONE)
        }
        client = builder.build()
        NetworkLog.debug("http", "client rebuilt (doh=${settings.dohEnabled}, proxy=${settings.proxy.enabled})")
    }

    private fun dohDns(): Dns {
        val bootstrap = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .build()
        return DnsOverHttps.Builder()
            .client(bootstrap)
            .url("https://cloudflare-dns.com/dns-query".toHttpUrl())
            .includeIPv6(true)
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
