@file:OptIn(org.koitharu.kotatsu.parsers.InternalParsersApi::class)

package app.kurenai.core.parsers

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.BitmapFactory
import android.webkit.WebView
import app.kurenai.core.network.KurenaiHttp
import app.kurenai.core.network.cookies.EncryptedCookieJar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.ResponseBody.Companion.asResponseBody
import okio.Buffer
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.bitmap.Bitmap
import org.koitharu.kotatsu.parsers.bitmap.Rect
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.config.MangaSourceConfig
import org.koitharu.kotatsu.parsers.model.MangaSource
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume
import android.graphics.Canvas
import android.graphics.Bitmap as AndroidBitmap
import android.graphics.Rect as AndroidRect

/**
 * Bridges the Kotatsu parser library to Kurenai's shared OkHttp stack, encrypted
 * cookie jar and WebView JavaScript host. All parsers therefore benefit from the
 * Cloudflare interceptor, rate limiting and per-domain cookie isolation.
 */
class KotatsuLoaderContext(
    private val context: Context,
    private val http: KurenaiHttp,
    private val cookieJarImpl: EncryptedCookieJar,
) : MangaLoaderContext() {

    override val httpClient: OkHttpClient get() = http.client()
    override val cookieJar: okhttp3.CookieJar get() = cookieJarImpl

    private val configs = ConcurrentHashMap<String, MangaSourceConfig>()
    private var webView: WebView? = null

    override suspend fun evaluateJs(script: String): String? = evaluateJs("about:blank", script)

    override suspend fun evaluateJs(baseUrl: String, script: String): String? = withContext(Dispatchers.Main.immediate) {
        val view = obtainWebView()
        suspendCancellableCoroutine { continuation ->
            view.evaluateJavascript(script) { result ->
                if (continuation.isActive) {
                    continuation.resume(result?.takeUnless { it == "null" })
                }
            }
        }
    }

    override fun getConfig(source: MangaSource): MangaSourceConfig =
        configs.computeIfAbsent(source.name) { SourceConfig(context, source) }

    override fun getDefaultUserAgent(): String = KurenaiHttp.CHROME_UA

    override fun getPreferredLocales(): List<Locale> = listOf(Locale.getDefault())

    override fun redrawImageResponse(response: Response, redraw: (Bitmap) -> Bitmap): Response {
        return response.newBuilder().body(
            response.body?.let { body ->
                val bitmap = BitmapFactory.decodeStream(body.byteStream())
                    ?: return@let body
                val wrapper = AndroidBitmapWrapper.create(bitmap)
                val result = redraw(wrapper) as AndroidBitmapWrapper
                Buffer().also { result.compressTo(it.outputStream()) }
                    .asResponseBody("image/png".toMediaType())
            },
        ).build()
    }

    override fun createBitmap(width: Int, height: Int): Bitmap = AndroidBitmapWrapper.create(width, height)

    @SuppressLint("SetJavaScriptEnabled")
    private fun obtainWebView(): WebView = webView ?: WebView(context).apply {
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.userAgentString = KurenaiHttp.CHROME_UA
        webView = this
    }
}

private class AndroidBitmapWrapper private constructor(
    private val bitmap: AndroidBitmap,
) : Bitmap, AutoCloseable {
    private val canvas by lazy { Canvas(bitmap) }
    override val width: Int get() = bitmap.width
    override val height: Int get() = bitmap.height

    override fun drawBitmap(sourceBitmap: Bitmap, src: Rect, dst: Rect) {
        val source = (sourceBitmap as AndroidBitmapWrapper).bitmap
        canvas.drawBitmap(source, AndroidRect(src.left, src.top, src.right, src.bottom), AndroidRect(dst.left, dst.top, dst.right, dst.bottom), null)
    }

    override fun close() = bitmap.recycle()

    fun compressTo(output: java.io.OutputStream) {
        bitmap.compress(AndroidBitmap.CompressFormat.PNG, 100, output)
    }

    companion object {
        fun create(width: Int, height: Int) = AndroidBitmapWrapper(
            AndroidBitmap.createBitmap(width, height, AndroidBitmap.Config.ARGB_8888),
        )

        fun create(bitmap: AndroidBitmap) = AndroidBitmapWrapper(
            if (bitmap.isMutable) bitmap else bitmap.copy(AndroidBitmap.Config.ARGB_8888, true),
        )
    }
}

private class SourceConfig(context: Context, source: MangaSource) : MangaSourceConfig {
    private val prefs = context.getSharedPreferences("source_${source.name}", Context.MODE_PRIVATE)

    @Suppress("UNCHECKED_CAST")
    override fun <T> get(key: ConfigKey<T>): T = when (key) {
        is ConfigKey.UserAgent -> (prefs.getString(key.key, null)?.takeIf { it.isNotBlank() } ?: key.defaultValue) as T
        is ConfigKey.Domain -> (prefs.getString(key.key, null)?.takeIf { it.isNotBlank() } ?: key.defaultValue) as T
        is ConfigKey.ShowSuspiciousContent -> prefs.getBoolean(key.key, key.defaultValue) as T
        is ConfigKey.SplitByTranslations -> prefs.getBoolean(key.key, key.defaultValue) as T
        is ConfigKey.PreferredImageServer -> (prefs.getString(key.key, null) ?: key.defaultValue) as T
    }

    @Suppress("UNCHECKED_CAST")
    fun <T> set(key: ConfigKey<T>, value: T) {
        when (key) {
            is ConfigKey.UserAgent -> prefs.edit().putString(key.key, value as String).apply()
            is ConfigKey.Domain -> prefs.edit().putString(key.key, value as String).apply()
            is ConfigKey.ShowSuspiciousContent -> prefs.edit().putBoolean(key.key, value as Boolean).apply()
            is ConfigKey.SplitByTranslations -> prefs.edit().putBoolean(key.key, value as Boolean).apply()
            is ConfigKey.PreferredImageServer -> prefs.edit().putString(key.key, value as String?).apply()
        }
    }
}
