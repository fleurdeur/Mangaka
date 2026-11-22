package app.kurenai.core.data

import app.kurenai.core.database.dao.TrackerDao
import app.kurenai.core.database.entity.TrackerLinkEntity
import app.kurenai.core.datastore.SecureSecretStore
import app.kurenai.core.model.Manga
import app.kurenai.core.model.TrackerLink
import app.kurenai.core.model.TrackerService
import app.kurenai.core.model.TrackerStatus
import app.kurenai.core.network.KurenaiHttp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

data class TrackerSyncResult(val service: TrackerService, val success: Boolean, val message: String)

@Singleton
class TrackerRepository @Inject constructor(
    private val trackerDao: TrackerDao,
    private val secrets: SecureSecretStore,
    private val http: KurenaiHttp,
) {
    fun links(mangaId: String): Flow<List<TrackerLink>> = trackerDao.observeForManga(mangaId).map { rows ->
        rows.map { row ->
            TrackerLink(
                mangaId = row.mangaId,
                tracker = runCatching { TrackerService.valueOf(row.tracker) }.getOrDefault(TrackerService.ANILIST),
                remoteId = row.remoteId,
                remoteUrl = row.remoteUrl,
                status = runCatching { TrackerStatus.valueOf(row.status) }.getOrDefault(TrackerStatus.READING),
                progress = row.progress,
                score = row.score,
                syncedAt = row.syncedAt,
            )
        }
    }

    suspend fun link(manga: Manga, service: TrackerService, remoteId: String, url: String? = null, progress: Int = 0) {
        trackerDao.upsert(
            TrackerLinkEntity(
                mangaId = manga.id,
                tracker = service.name,
                remoteId = remoteId,
                remoteUrl = url,
                progress = progress,
                syncedAt = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun unlink(mangaId: String, service: TrackerService) = trackerDao.delete(mangaId, service.name)

    suspend fun syncAll(): List<TrackerSyncResult> = withContext(Dispatchers.IO) {
        trackerDao.all().groupBy { it.mangaId }.flatMap { (_, links) ->
            links.map { row ->
                val service = runCatching { TrackerService.valueOf(row.tracker) }.getOrDefault(TrackerService.ANILIST)
                syncOne(service, row)
            }
        }
    }

    suspend fun syncManga(mangaId: String): List<TrackerSyncResult> = withContext(Dispatchers.IO) {
        trackerDao.all().filter { it.mangaId == mangaId }.map { row ->
            syncOne(runCatching { TrackerService.valueOf(row.tracker) }.getOrDefault(TrackerService.ANILIST), row)
        }
    }

    private fun syncOne(service: TrackerService, row: TrackerLinkEntity): TrackerSyncResult {
        val token = secrets.get(tokenKey(service))
            ?: return TrackerSyncResult(service, false, "Add a ${service.title} token in Settings → Tracking")
        return runCatching {
            when (service) {
                TrackerService.ANILIST -> syncAniList(row, token)
                TrackerService.MYANIMELIST -> syncMal(row, token)
                TrackerService.SHIKIMORI -> syncShikimori(row, token)
                TrackerService.KITSU -> syncKitsu(row, token)
            }
        }.fold(
            onSuccess = { TrackerSyncResult(service, true, "Synced to ${service.title}") },
            onFailure = { TrackerSyncResult(service, false, it.message ?: "Sync failed") },
        )
    }

    private fun syncAniList(row: TrackerLinkEntity, token: String) {
        val status = when (runCatching { TrackerStatus.valueOf(row.status) }.getOrDefault(TrackerStatus.READING)) {
            TrackerStatus.READING -> "CURRENT"
            TrackerStatus.COMPLETED -> "COMPLETED"
            TrackerStatus.ON_HOLD -> "PAUSED"
            TrackerStatus.DROPPED -> "DROPPED"
            TrackerStatus.PLAN_TO_READ -> "PLANNING"
        }
        val payload = JSONObject()
            .put("query", "mutation(\$mediaId:Int,\$progress:Int,\$status:MediaListStatus){SaveMediaListEntry(mediaId:\$mediaId,progress:\$progress,status:\$status){id progress status}}")
            .put("variables", JSONObject().put("mediaId", row.remoteId.toInt()).put("progress", row.progress).put("status", status))
            .toString()
        request(
            Request.Builder()
                .url("https://graphql.anilist.co")
                .header("Authorization", "Bearer $token")
                .post(payload.toRequestBody("application/json".toMediaType())),
        )
    }

    private fun syncMal(row: TrackerLinkEntity, token: String) {
        val body = FormBody.Builder()
            .add("status", malStatus(row.status))
            .add("num_chapters_read", row.progress.toString())
            .build()
        request(
            Request.Builder()
                .url("https://api.myanimelist.net/v2/manga/${row.remoteId}/my_list_status")
                .header("Authorization", "Bearer $token")
                .put(body),
        )
    }

    private fun syncShikimori(row: TrackerLinkEntity, token: String) {
        val payload = JSONObject()
            .put("user_rate", JSONObject().put("target_id", row.remoteId).put("target_type", "Manga").put("status", shikiStatus(row.status)).put("chapters", row.progress))
            .toString()
        request(
            Request.Builder()
                .url("https://shikimori.one/api/v2/user_rates")
                .header("Authorization", "Bearer $token")
                .post(payload.toRequestBody("application/json".toMediaType())),
        )
    }

    private fun syncKitsu(row: TrackerLinkEntity, token: String) {
        val payload = JSONObject()
            .put("data", JSONObject().put("type", "libraryEntries").put("id", row.remoteId).put("attributes", JSONObject().put("progress", row.progress).put("status", kitsuStatus(row.status))))
            .toString()
        request(
            Request.Builder()
                .url("https://kitsu.io/api/edge/library-entries/${row.remoteId}")
                .header("Authorization", "Bearer $token")
                .patch(payload.toRequestBody("application/vnd.api+json".toMediaType())),
        )
    }

    private fun request(builder: Request.Builder) {
        http.client().newCall(builder.build()).execute().use { response ->
            if (!response.isSuccessful) error("${response.code}: ${response.body?.string().orEmpty().take(180)}")
        }
    }

    private fun tokenKey(service: TrackerService) = "tracker_${service.name.lowercase()}_token"
    private fun malStatus(status: String) = when (runCatching { TrackerStatus.valueOf(status) }.getOrDefault(TrackerStatus.READING)) {
        TrackerStatus.READING -> "reading"
        TrackerStatus.COMPLETED -> "completed"
        TrackerStatus.ON_HOLD -> "on_hold"
        TrackerStatus.DROPPED -> "dropped"
        TrackerStatus.PLAN_TO_READ -> "plan_to_read"
    }
    private fun shikiStatus(status: String) = when (runCatching { TrackerStatus.valueOf(status) }.getOrDefault(TrackerStatus.READING)) {
        TrackerStatus.READING -> "watching"
        TrackerStatus.COMPLETED -> "completed"
        TrackerStatus.ON_HOLD -> "on_hold"
        TrackerStatus.DROPPED -> "dropped"
        TrackerStatus.PLAN_TO_READ -> "planned"
    }
    private fun kitsuStatus(status: String) = when (runCatching { TrackerStatus.valueOf(status) }.getOrDefault(TrackerStatus.READING)) {
        TrackerStatus.READING -> "current"
        TrackerStatus.COMPLETED -> "completed"
        TrackerStatus.ON_HOLD -> "on_hold"
        TrackerStatus.DROPPED -> "dropped"
        TrackerStatus.PLAN_TO_READ -> "planned"
    }
}
