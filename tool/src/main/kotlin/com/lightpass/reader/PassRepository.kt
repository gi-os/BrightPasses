package com.lightpass.reader

import android.content.Context
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

    private val passDir: File
        get() = File(lightContext.filesDir, "passes").apply { mkdirs() }

    private val importDir: File
        get() = File(lightContext.filesDir, "import").apply { mkdirs() }

    fun observeAll(): Flow<List<PassEntity>> = dao.observeAll()
    fun observeSearch(q: String): Flow<List<PassEntity>> = dao.observeSearch(q)
    fun observePass(id: String): Flow<PassEntity?> = dao.observePass(id)

    suspend fun getApiKey(): String = dao.getMetadata(KEY_API).orEmpty()
    suspend fun setApiKey(key: String) = dao.putMetadata(AppMetadataEntity(KEY_API, key.trim()))

    /** Legacy drop-inbox importer (kept as a fallback). */
    suspend fun importFromInbox(): Int {
        val key = getApiKey()
        val images = importDir.listFiles { f ->
            f.isFile && f.extension.lowercase() in setOf("jpg", "jpeg", "png")
        }.orEmpty()
        var count = 0
        for (src in images) { addPass(src.readBytes(), key); src.delete(); count++ }
        return count
    }

    /** Import from a content:// Uri (album pick or camera output that yielded a Uri). */
    suspend fun addPassFromUri(uri: android.net.Uri, context: Context) {
        val bytes = context.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
        addPass(bytes, getApiKey())
    }

    /** Import from an already-captured file on disk (camera capture path). */
    suspend fun addPassFromFile(file: File) {
        addPass(file.readBytes(), getApiKey())
        runCatching { file.delete() }
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
                movieTitle = if (meta == null || meta.notATicket) "Ticket ${id.take(4)}"
                             else meta.movieTitle.ifBlank { "Untitled" },
                theater = meta?.theater,
                date = meta?.date,
                time = meta?.time,
                seat = meta?.seat,
                price = meta?.price,
                code = meta?.code,
                confidence = meta?.confidence ?: 0.0,
                imagePath = dst.absolutePath,
            ),
        )
    }

    /** A fresh temp file for camera capture output. */
    fun newCaptureFile(): File = File(passDir, "cap_${UUID.randomUUID()}.jpg")

    suspend fun deletePass(pass: PassEntity) {
        runCatching { File(pass.imagePath).delete() }
        dao.delete(pass.id)
    }

    fun close() = parser.close()
}
