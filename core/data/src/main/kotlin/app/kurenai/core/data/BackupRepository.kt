package app.kurenai.core.data

import android.util.Base64
import app.kurenai.core.database.dao.CategoryDao
import app.kurenai.core.database.dao.ChapterDao
import app.kurenai.core.database.dao.HistoryDao
import app.kurenai.core.database.dao.MangaDao
import app.kurenai.core.datastore.SettingsStore
import app.kurenai.core.model.AppSettings
import app.kurenai.core.model.Category
import app.kurenai.core.model.Manga
import app.kurenai.core.model.MangaChapter
import app.kurenai.core.model.SourcePreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class BackupBundle(
    val version: Int = 1,
    val createdAt: Long = System.currentTimeMillis(),
    val settings: AppSettings,
    val sourcePreferences: SourcePreferences,
    val favorites: List<Manga>,
    val chapters: List<MangaChapter>,
    val categories: List<Category>,
    val progress: Map<String, Int> = emptyMap(),
) {
    val summary: String
        get() = "${favorites.size} favorites · ${chapters.size} chapters · ${categories.size} categories"
}

@Singleton
class BackupRepository @Inject constructor(
    private val settingsStore: SettingsStore,
    private val mangaDao: MangaDao,
    private val chapterDao: ChapterDao,
    private val categoryDao: CategoryDao,
    private val historyDao: HistoryDao,
) {
    private val json = Json { prettyPrint = true; encodeDefaults = true; ignoreUnknownKeys = true }

    suspend fun export(encrypt: Boolean, password: String? = null): String = withContext(Dispatchers.IO) {
        val favorites = mangaDao.observeFavorites().first().map { it.toModel() }
        val chapters = favorites.flatMap { chapterDao.getForManga(it.id) }.map { it.toModel() }
        val categories = categoryDao.observe().first().map { Category(it.id, it.name, it.order, it.isSystem, it.colorSeed) }
        val progress = historyDao.observe(500).first().associate { it.mangaId to it.page }
        val bundle = BackupBundle(
            settings = settingsStore.settings.first(),
            sourcePreferences = settingsStore.sourcePreferences.first(),
            favorites = favorites,
            chapters = chapters,
            categories = categories,
            progress = progress,
        )
        val raw = json.encodeToString(BackupBundle.serializer(), bundle)
        if (encrypt) encrypt(raw, password.orEmpty().ifBlank { error("Password required") }) else raw
    }

    suspend fun import(payload: String, password: String? = null): BackupBundle = withContext(Dispatchers.IO) {
        val raw = if (payload.startsWith(PREFIX)) decrypt(payload, password.orEmpty()) else payload
        val bundle = json.decodeFromString(BackupBundle.serializer(), raw)
        settingsStore.replaceAll(bundle.settings, bundle.sourcePreferences)
        bundle.categories.forEach { category ->
            categoryDao.upsert(app.kurenai.core.database.entity.CategoryEntity(category.id, category.name, category.order, category.isSystem, category.colorSeed))
        }
        bundle.favorites.forEach { manga -> mangaDao.upsert(manga.toEntity(favorite = true)) }
        bundle.chapters.forEach { chapter -> chapterDao.upsert(chapter.toEntity()) }
        bundle.progress.forEach { (mangaId, page) ->
            val chapter = chapterDao.getForManga(mangaId).firstOrNull() ?: return@forEach
            historyDao.upsert(
                app.kurenai.core.database.entity.HistoryEntity(
                    mangaId = mangaId,
                    chapterId = chapter.id,
                    page = page,
                    totalPages = chapter.pageCount,
                    readAt = System.currentTimeMillis(),
                ),
            )
        }
        bundle
    }

    private fun encrypt(raw: String, password: String): String {
        val salt = ByteArray(16).also(SecureRandom()::nextBytes)
        val iv = ByteArray(12).also(SecureRandom()::nextBytes)
        val key = derive(password, salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
        val encrypted = cipher.doFinal(raw.toByteArray(Charsets.UTF_8))
        return PREFIX + Base64.encodeToString(salt + iv + encrypted, Base64.NO_WRAP)
    }

    private fun decrypt(payload: String, password: String): String {
        val bytes = Base64.decode(payload.removePrefix(PREFIX), Base64.NO_WRAP)
        val salt = bytes.copyOfRange(0, 16)
        val iv = bytes.copyOfRange(16, 28)
        val encrypted = bytes.copyOfRange(28, bytes.size)
        val key = derive(password, salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
        return cipher.doFinal(encrypted).toString(Charsets.UTF_8)
    }

    private fun derive(password: String, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(password.toCharArray(), salt, 120_000, 256)
        val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        return SecretKeySpec(bytes, "AES")
    }

    companion object {
        private const val PREFIX = "KURENAI1:"
    }
}
