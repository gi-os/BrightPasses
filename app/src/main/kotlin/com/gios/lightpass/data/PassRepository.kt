package com.gios.lightpass.data

import android.content.Context
import android.net.Uri
import com.gios.lightpass.ai.PassParser
import kotlinx.coroutines.flow.Flow
import java.io.File
import java.util.UUID

class PassRepository(private val context: Context) {
    private val dao = PassDatabase.get(context).passDao()
    private val prefs = context.getSharedPreferences("lightpass", Context.MODE_PRIVATE)

    private val passDir: File get() = File(context.filesDir, "passes").apply { mkdirs() }

    fun observeAll(): Flow<List<PassEntity>> = dao.observeAll()
    fun observePass(id: String): Flow<PassEntity?> = dao.observePass(id)
    suspend fun delete(pass: PassEntity) { runCatching { File(pass.imagePath).delete() }; dao.delete(pass.id) }

    fun getApiKey(): String = prefs.getString("api_key", "").orEmpty()
    fun setApiKey(key: String) { prefs.edit().putString("api_key", key.trim()).apply() }

    fun newCaptureFile(): File = File(passDir, "cap_${UUID.randomUUID()}.jpg")

    /** From a captured file already on disk. */
    suspend fun addFromFile(file: File) = addBytes(file.readBytes()).also { runCatching { file.delete() } }

    /** From an album/photo-picker Uri. */
    suspend fun addFromUri(uri: Uri) {
        val bytes = context.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
        addBytes(bytes)
    }

    private suspend fun addBytes(bytes: ByteArray) {
        val id = UUID.randomUUID().toString()
        val dst = File(passDir, "$id.jpg").apply { writeBytes(bytes) }
        val key = getApiKey()
        val meta = if (key.isNotBlank()) runCatching { PassParser.parse(dst, key) }.getOrNull() else null
        dao.insert(
            PassEntity(
                id = id,
                movieTitle = if (meta == null || meta.notATicket) "Ticket ${id.take(4)}"
                             else meta.movieTitle.ifBlank { "Untitled" },
                theater = meta?.theater, date = meta?.date, time = meta?.time,
                seat = meta?.seat, price = meta?.price, code = meta?.code,
                confidence = meta?.confidence ?: 0.0,
                imagePath = dst.absolutePath,
            )
        )
    }
}
