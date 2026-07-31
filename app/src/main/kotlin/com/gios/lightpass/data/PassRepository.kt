package com.gios.lightpass.data

import android.content.Context
import android.net.Uri
import com.gios.lightpass.ai.MovieCandidate
import com.gios.lightpass.ai.PassParser
import com.gios.lightpass.ai.TmdbClient
import com.gios.lightpass.util.AutoCrop
import com.gios.lightpass.util.CodeReader
import com.gios.lightpass.util.ImageUtils
import com.gios.lightpass.util.ShowTime
import com.gios.lightpass.util.TextUtils
import com.gios.lightpass.util.TicketDate
import kotlinx.coroutines.flow.Flow
import java.io.File
import java.util.UUID

class PassRepository(private val context: Context) {
    private val dao = PassDatabase.get(context).passDao()
    private val prefs = context.getSharedPreferences("lightpass", Context.MODE_PRIVATE)
    private val passDir: File get() = File(context.filesDir, "passes").apply { mkdirs() }

    fun observeAll(): Flow<List<PassEntity>> = dao.observeAll()
    fun observePass(id: String): Flow<PassEntity?> = dao.observePass(id)
    suspend fun getById(id: String): PassEntity? = dao.getById(id)
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

    suspend fun addFromFile(file: File): String = addBytes(file.readBytes()).also { runCatching { file.delete() } }
    suspend fun addFromUri(uri: Uri): String {
        val bytes = context.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
        return addBytes(bytes)
    }

    /** Search TMDb for the movie so the user can pick (we no longer auto-guess). */
    fun searchMovies(title: String): List<MovieCandidate> = TmdbClient.search(title, getTmdbKey())

    /** Apply the user's chosen movie: poster + synopsis + runtime + year. */
    suspend fun applyMovie(passId: String, cand: MovieCandidate) {
        val pass = dao.getById(passId) ?: return
        val runtime = TmdbClient.runtime(cand.id, getTmdbKey())
        dao.update(pass.copy(
            posterUrl = cand.posterUrl, overview = cand.overview,
            runtimeMin = runtime, year = cand.year,
        ))
    }

    private suspend fun addBytes(bytes: ByteArray): String {
        val id = UUID.randomUUID().toString()
        val upright = ImageUtils.normalizeUpright(bytes)
        val original = ImageUtils.saveJpeg(upright, File(passDir, "$id.jpg"))

        val key = getApiKey()
        val meta = if (key.isNotBlank()) runCatching { PassParser.parse(original, key) }.getOrNull() else null

        var croppedPath: String? = null
        if (meta != null && !meta.notATicket) {
            var cropped = ImageUtils.cropToBox(upright, meta.boxX, meta.boxY, meta.boxW, meta.boxH)
            if (cropped === upright) cropped = AutoCrop.trimBorders(upright) // fallback if model box was full-frame
            if (cropped !== upright) {
                croppedPath = ImageUtils.saveJpeg(cropped, File(passDir, "${id}_crop.jpg")).absolutePath
                cropped.recycle()
            }
        }

        val title = if (meta == null || meta.notATicket) null else meta.movieTitle
        dao.insert(
            PassEntity(
                id = id,
                movieTitle = if (title.isNullOrBlank()) "Ticket ${id.take(4)}" else title,
                theater = TextUtils.titleCaseVenue(meta?.theater),
                // The model is asked for MM-DD when the paper has no year, and is not always
                // asked politely enough; whatever comes back, the year is settled here.
                date = TicketDate.resolveFromModel(meta?.date),
                time = ShowTime.normalize(meta?.time),
                seat = meta?.seat, price = meta?.price, code = meta?.code,
                confidence = meta?.confidence ?: 0.0,
                imagePath = original.absolutePath,
                croppedPath = croppedPath,
            )
        )
        upright.recycle()

        // Read the ticket's own code off the photograph now, while the ticket is the thing on
        // screen. It is the slow part of adding a pass — seconds, not milliseconds — so it happens
        // after the row exists rather than before: the ticket appears, then its barcode fills in.
        runCatching {
            CodeReader.scan(original.absolutePath, croppedPath)?.let { scanned ->
                dao.getById(id)?.let { row ->
                    dao.update(row.copy(scannedCode = scanned.text, scannedFormat = scanned.format))
                }
            }
        }
        return id
    }

    /**
     * Scan the photos of tickets that were added before any of this existed.
     *
     * Bounded by [MAX_BACKFILL] a launch, because each one is seconds of decoding and there is no
     * hurry — the queue only shrinks, since every ticket that can be read gets written.
     *
     * A ticket that genuinely has no code is re-scanned on every launch, and that is deliberate: the
     * alternative is a third column whose only job is to remember "asked, found nothing", and a few
     * seconds of background work on an old pass is the cheaper of the two mistakes.
     */
    suspend fun backfillScannedCodes() {
        val pending = runCatching { dao.neverScanned() }.getOrNull() ?: return
        for (pass in pending.take(MAX_BACKFILL)) {
            val scanned = runCatching {
                CodeReader.scan(pass.imagePath, pass.croppedPath)
            }.getOrNull() ?: continue
            runCatching {
                dao.getById(pass.id)?.let {
                    dao.update(it.copy(scannedCode = scanned.text, scannedFormat = scanned.format))
                }
            }
        }
    }

    private companion object {
        /** Tickets read per launch. Enough to catch up a shelf in a couple of openings. */
        const val MAX_BACKFILL = 8
    }
}
