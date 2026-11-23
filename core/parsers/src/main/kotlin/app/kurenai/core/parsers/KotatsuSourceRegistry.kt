@file:OptIn(org.koitharu.kotatsu.parsers.InternalParsersApi::class)

package app.kurenai.core.parsers

import app.kurenai.core.network.KurenaiHttp
import app.kurenai.core.network.cookies.EncryptedCookieJar
import app.kurenai.core.source.MangaSource
import app.kurenai.core.source.SourceRegistry
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Builds the complete built-in catalogue. Kotatsu's 1,200+ parsers are generated
 * as an enum by the upstream KSP processor; every entry becomes a Kurenai source.
 */
@Singleton
class KotatsuSourceRegistry @Inject constructor(
    @ApplicationContext private val context: Context,
    private val http: KurenaiHttp,
    private val cookieJar: EncryptedCookieJar,
) : SourceRegistry {

    private val loaderContext by lazy { KotatsuLoaderContext(context, http, cookieJar) }
    @Volatile
    private var cached: List<MangaSource> = emptyList()

    override val sources: List<MangaSource>
        get() {
            if (cached.isEmpty()) refresh()
            return cached
        }

    override fun source(id: String): MangaSource? = sources.firstOrNull { it.info.id == id }

    @Synchronized
    override fun refresh() {
        if (cached.isNotEmpty()) return
        val kotatsu = MangaParserSource.entries.mapNotNull { parserSource ->
            runCatching { KotatsuMangaSource(parserSource, loaderContext) }.getOrNull()
        }
        cached = kotatsu + SampleSource()
    }
}
