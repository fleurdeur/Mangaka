package app.kurenai.core.image

import android.app.ActivityManager
import android.content.Context
import coil3.ImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import app.kurenai.core.network.KurenaiHttp
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import okio.Path.Companion.toPath
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class KurenaiImageLoader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val http: KurenaiHttp,
) {
    val memoryClassMb: Int by lazy {
        (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).memoryClass
    }

    fun build(cacheBudgetMb: Int = 512, lowMemory: Boolean = false): ImageLoader {
        val diskMb = if (lowMemory) minOf(cacheBudgetMb, 192) else cacheBudgetMb
        return ImageLoader.Builder(context)
            .components {
                add(OkHttpNetworkFetcherFactory(callFactory = { http.client() }))
            }
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(context, if (lowMemory) 0.10 else 0.22)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(File(context.cacheDir, "kurenai_image_cache").absolutePath.toPath())
                    .maxSizeBytes(diskMb.toLong() * 1024 * 1024)
                    .build()
            }
            .crossfade(true)
            .build()
    }
}
