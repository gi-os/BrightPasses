package com.lightpass.reader

import com.thelightphone.sdk.SealedLightContext
import kotlinx.coroutines.flow.Flow
import java.io.File
import java.util.UUID

private const val KEY_API = "anthropic_api_key"

class PassRepository(
    private val dao: PassDao,
    private val lightContext: SealedLightContext,
) {
    private val parser = PassParser()

    // Permanent home for ORIGINAL images (shown full-screen). filesDir is public on SealedLightContext.
    private val passDir: File
        get() = File(lightContext.filesDir, "passes").apply { mkdirs() }

    // Import inbox: images land here (via LightOS file share / adb run-as), then get pulled in.
    private val importDir: File
        get() = File(lightContext.filesDir, "import").apply { mkdirs() }

    fun observeAll(): Flow<List<PassEntity>> = dao.observeAll()
    fun observeSearch(q: String): Flow<List<PassEntity>> = dao.observeSearch(q)
    fun observePass(id: String): Flow<PassEntity?> = dao.observePass(id)

    suspend fun getApiKey(): String = dao.getMetadata(KEY_API).orEmpty()
    suspend fun setApiKey(key: String) = dao.putMetadata(AppMetadataEntity(KEY_API, key.trim()))

    /** Import every image currently in the inbox; returns how many were added. */
    suspend fun importFromInbox(): Int {
        val key = getApiKey()
        val images = importDir.listFiles { f ->
            f.isFile && f.extension.lowercase() in setOf("jpg", "jpeg", "png")
        }.orEmpty()
        var count = 0
        for (src in images) {
            addPass(src.readBytes(), key)
            src.delete()
            count++
        }
        return count
    }

    /** Copy original bytes into permanent storage, parse best-effort, persist. */
    suspend fun addPass(bytes: ByteArray, apiKey: String) {
        val id = UUID.randomUUID().toString()
        val dst = File(passDir, "$id.jpg")
        dst.writeBytes(bytes)

        val meta = if (apiKey.isNotBlank()) {
            runCatching { parser.parse(dst, apiKey) }.getOrNull()
        } else null

        dao.insert(
            PassEntity(
                id = id,
                title = meta?.title ?: "Pass ${id.take(4)}",
                type = meta?.type ?: "other",
                date = meta?.date,
                code = meta?.code,
                issuer = meta?.issuer,
                imagePath = dst.absolutePath,
            ),
        )
    }

    suspend fun deletePass(pass: PassEntity) {
        runCatching { File(pass.imagePath).delete() }
        dao.delete(pass.id)
    }

    fun close() = parser.close()
}
