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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import java.io.File
import java.util.UUID

class PassRepository(private val context: Context) {
    private val dao = PassDatabase.get(context).passDao()
    private val prefs = context.getSharedPreferences("lightpass", Context.MODE_PRIVATE)
    private val passDir: File get() = File(context.filesDir, "passes").apply { mkdirs() }

    fun observeAll(): Flow<List<PassEntity>> = dao.observeAll()
    fun observePass(id: String): Flow<PassEntity?> = dao.observePass(id)

    /**
     * The pass and everything grouped with it — a list of one for a lone ticket. Follows the
     * pass, so if a sibling arrives while the page is open, the page grows a pager.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeTickets(id: String): Flow<List<PassEntity>> =
        dao.observePass(id).flatMapLatest { p ->
            when {
                p == null -> flowOf(emptyList())
                p.groupId == null -> flowOf(listOf(p))
                else -> dao.observeGroup(p.groupId)
            }
        }
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

    suspend fun addFromFile(file: File, attachTo: String? = null): String =
        addBytes(file.readBytes(), attachTo).also { runCatching { file.delete() } }
    suspend fun addFromUri(uri: Uri, attachTo: String? = null): String {
        val bytes = context.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
        return addBytes(bytes, attachTo)
    }

    /** Search TMDb for the movie so the user can pick (we no longer auto-guess). */
    fun searchMovies(title: String): List<MovieCandidate> = TmdbClient.search(title, getTmdbKey())

    /**
     * Apply the user's chosen movie: poster + synopsis + runtime + year — to every ticket in
     * the group, because they are all for the same showing and each row carries its own copy.
     */
    suspend fun applyMovie(passId: String, cand: MovieCandidate) {
        val pass = dao.getById(passId) ?: return
        val runtime = TmdbClient.runtime(cand.id, getTmdbKey())
        for (member in groupOf(pass)) {
            dao.update(member.copy(
                posterUrl = cand.posterUrl, overview = cand.overview,
                runtimeMin = runtime, year = cand.year,
            ))
        }
    }

    /**
     * Reclassify a ticket — and its whole group, since one showing has one kind. Retroactive
     * by design: this is how a pass that predates event types stops being a "movie".
     */
    suspend fun setEventType(passId: String, type: String) {
        val pass = dao.getById(passId) ?: return
        for (member in groupOf(pass)) dao.update(member.copy(eventType = type))
    }

    /**
     * Photograph another ticket straight into an existing event. The anchor gains a groupId
     * if it never had one — a lone ticket becomes a group of one the moment it gets company.
     */
    private suspend fun adoptInto(anchorId: String): PassEntity? {
        val anchor = dao.getById(anchorId) ?: return null
        return if (anchor.groupId == null) {
            val adopted = anchor.copy(groupId = anchor.id)
            dao.update(adopted)
            adopted
        } else anchor
    }

    private suspend fun groupOf(pass: PassEntity): List<PassEntity> =
        pass.groupId?.let { dao.getGroup(it).ifEmpty { listOf(pass) } } ?: listOf(pass)

    /**
     * The same event, already on the shelf: same title (ignoring case and punctuation), same
     * date, and no disagreement on time. This is what lets three QR codes photographed one
     * after another land as one entry — the second and third recognise the first.
     */
    private suspend fun findSameEvent(title: String?, date: String?, time: String?): PassEntity? {
        val wantTitle = normalizedTitle(title) ?: return null
        val wantDate = date ?: return null
        return dao.getAll()
            .filter { normalizedTitle(it.movieTitle) == wantTitle && it.date == wantDate }
            .firstOrNull { time == null || it.time == null || it.time == time }
    }

    private fun normalizedTitle(t: String?): String? =
        t?.lowercase()?.replace(Regex("[^a-z0-9]"), "")?.takeIf { it.isNotEmpty() }

    private suspend fun addBytes(bytes: ByteArray, attachTo: String? = null): String {
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
        val date = TicketDate.resolveFromModel(meta?.date)
        val time = ShowTime.normalize(meta?.time)

        // Whose event this ticket joins, if anyone's. ADD TICKET says so outright; a plain
        // add still recognises a showing already on the shelf by title + date + time, which
        // is what makes three codes for the same film land as one entry.
        val anchor: PassEntity? = when {
            attachTo != null -> adoptInto(attachTo)
            title != null -> findSameEvent(title, date, time)?.let { adoptInto(it.id) }
            else -> null
        }

        dao.insert(
            PassEntity(
                id = id,
                // Attached tickets inherit what their photo failed to say — the event is
                // already known, so a parse coming back empty should not blank the row.
                movieTitle = if (title.isNullOrBlank()) anchor?.movieTitle ?: "Ticket ${id.take(4)}" else title,
                theater = TextUtils.titleCaseVenue(meta?.theater) ?: anchor?.theater,
                // The model is asked for MM-DD when the paper has no year, and is not always
                // asked politely enough; whatever comes back, the year is settled here.
                date = date ?: anchor?.date,
                time = time ?: anchor?.time,
                seat = meta?.seat, price = meta?.price, code = meta?.code,
                confidence = meta?.confidence ?: 0.0,
                imagePath = original.absolutePath,
                croppedPath = croppedPath,
                // Siblings share the movie match too, so the picker never asks twice.
                posterUrl = anchor?.posterUrl, overview = anchor?.overview,
                runtimeMin = anchor?.runtimeMin, year = anchor?.year,
                eventType = anchor?.eventType ?: meta?.kind ?: EventType.MOVIE,
                groupId = anchor?.groupId,
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
