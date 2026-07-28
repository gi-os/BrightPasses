package com.gios.lightpass.data

import android.content.Context
import android.net.Uri
import com.gios.lightpass.ai.PassParser
import com.gios.lightpass.ai.TmdbClient
import com.gios.lightpass.util.ImageUtils
import kotlinx.coroutines.flow.Flow
import java.io.File
import java.util.UUID

class PassRepository(private val context: Context) {
    private val dao = PassDatabase.get(context).passDao()
    private val prefs = context.getSharedPreferences("lightpass", Context.MODE_PRIVATE)
    private val passDir: File get() = File(context.filesDir, "passes").apply { mkdirs() }

    fun observeAll(): Flow<List<PassEntity>> = dao.observeAll()
    fun observePass(id: String): Flow<PassEntity?> = dao.observePass(id)
    suspend fun update(pass: PassEntity) = dao.update(pass)
    suspend fun delete(pass: PassEntity) {
        runCatching { File(pass.imagePath).delete() }
        pass.croppedPath?.let { runCatching { File(it).delete() } }
        dao.delete(pass.id)
    }

    fun getApiKey(): String = prefs.getString("api_key", "").orEmpty()
    fun setApiKey(key: String) { prefs.edit().putString("api_key", key.trim()).apply() }
    fun getTmdbKey(): String = prefs.getString("tmdb_key", "").orEmpty()
    fun setTmdbKey(key: String) { prefs.edit().putString("tmdb_key", key.trim()).apply() }

    fun newCaptureFile(): File = File(passDir, "cap_${UUID.randomUUID()}.jpg")

    suspend fun addFromFile(file: File) { addBytes(file.readBytes()); runCatching { file.delete() } }
    suspend fun addFromUri(uri: Uri) {
        val bytes = context.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
        addBytes(bytes)
    }

    private suspend fun addBytes(bytes: ByteArray) {
        val id = UUID.randomUUID().toString()
        // 1) normalize upright so the model's box matches stored pixels
        val upright = ImageUtils.normalizeUpright(bytes)
        val original = ImageUtils.saveJpeg(upright, File(passDir, "$id.jpg"))

        // 2) parse (title/fields + bounding box)
        val key = getApiKey()
        val meta = if (key.isNotBlank()) runCatching { PassParser.parse(original, key) }.getOrNull() else null

        // 3) crop the ticket out using the box
        var croppedPath: String? = null
        if (meta != null && !meta.notATicket) {
            val cropped = ImageUtils.cropToBox(upright, meta.boxX, meta.boxY, meta.boxW, meta.boxH)
            if (cropped != upright) {
                croppedPath = ImageUtils.saveJpeg(cropped, File(passDir, "${id}_crop.jpg")).absolutePath
                cropped.recycle()
            }
        }

        // 4) optional TMDb enrichment
        val title = if (meta == null || meta.notATicket) null else meta.movieTitle
        val movie = title?.let { TmdbClient.lookup(it, getTmdbKey(), meta?.date?.take(4)) }

        dao.insert(
            PassEntity(
                id = id,
                movieTitle = if (title.isNullOrBlank()) "Ticket ${id.take(4)}" else title,
                theater = meta?.theater, date = meta?.date, time = meta?.time,
                seat = meta?.seat, price = meta?.price, code = meta?.code,
                confidence = meta?.confidence ?: 0.0,
                imagePath = original.absolutePath,
                croppedPath = croppedPath,
                posterUrl = movie?.posterUrl,
                overview = movie?.overview,
                runtimeMin = movie?.runtimeMin,
                year = movie?.year,
            )
        )
        upright.recycle()
    }
}
