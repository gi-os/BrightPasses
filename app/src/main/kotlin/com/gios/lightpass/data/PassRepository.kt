package com.gios.lightpass.data

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.gios.lightpass.ai.MovieCandidate
import com.gios.lightpass.ai.PassParser
import com.gios.lightpass.ai.TmdbClient
import com.gios.lightpass.util.AutoCrop
import com.gios.lightpass.util.CodeReader
import com.gios.lightpass.util.EventArt
import com.gios.lightpass.util.ImageUtils
import com.gios.lightpass.util.ShowTime
import com.gios.lightpass.util.TextUtils
import com.gios.lightpass.util.TicketDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.io.File
import java.util.UUID

class PassRepository(private val context: Context) {
    private val dao = PassDatabase.get(context).passDao()
    private val prefs = context.getSharedPreferences("lightpass", Context.MODE_PRIVATE)
    private val passDir: File get() = File(context.filesDir, "passes").apply { mkdirs() }

    // Room re-runs a query and emits on EVERY write to the table, changed or not — so a
    // barcode backfill on one old ticket re-emitted every list on screen. Deduped here, once,
    // rather than at each collection site.
    fun observeAll(): Flow<List<PassEntity>> = dao.observeAll().distinctUntilChanged()
    fun observePass(id: String): Flow<PassEntity?> = dao.observePass(id).distinctUntilChanged()

    /**
     * The pass and everything grouped with it — a list of one for a lone ticket. Follows the
     * pass, so if a sibling arrives while the page is open, the page grows a pager.
     *
     * The upstream is keyed on the group id, not the whole pass: without that, any edit to
     * the pass (a barcode filling in, a seat typed) tore down and re-created the group query.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeTickets(id: String): Flow<List<PassEntity>> =
        dao.observePass(id)
            .map { it?.groupId ?: it?.id }
            .distinctUntilChanged()
            .flatMapLatest { key ->
                when (key) {
                    null -> flowOf(emptyList())
                    else -> dao.observeGroup(key).map { group ->
                        // A lone ticket has groupId null, so the group query finds nothing;
                        // fall back to the pass itself.
                        group.ifEmpty { listOfNotNull(dao.getById(id)) }
                    }
                }
            }
            .distinctUntilChanged()
    suspend fun getById(id: String): PassEntity? = dao.getById(id)
    suspend fun update(pass: PassEntity) = dao.update(pass)
    suspend fun delete(pass: PassEntity) {
        runCatching { File(pass.imagePath).delete() }
        pass.croppedPath?.let { runCatching { File(it).delete() } }
        // Safe to delete: every row owns its own art file, never a sibling's.
        pass.artPath?.let { runCatching { File(it).delete() } }
        dao.delete(pass.id)
    }

    /** Save an edit, then redraw generated art — a corrected matchup title means new crests. */
    suspend fun updateFromEdit(pass: PassEntity) {
        dao.update(pass)
        if (pass.eventType != EventType.MOVIE) runCatching { refreshArt(pass.id) }
    }

    fun getApiKey(): String = prefs.getString("api_key", "").orEmpty()
    fun setApiKey(key: String) { prefs.edit().putString("api_key", key.trim()).apply() }
    fun getTmdbKey(): String = prefs.getString("tmdb_key", "").orEmpty()
    fun setTmdbKey(key: String) { prefs.edit().putString("tmdb_key", key.trim()).apply() }

    fun newCaptureFile(): File = File(passDir, "cap_${UUID.randomUUID()}.jpg")

    suspend fun addFromFile(file: File, attachTo: String? = null, burstAnchor: String? = null): String =
        addBytes(file.readBytes(), attachTo, burstAnchor).also { runCatching { file.delete() } }
    suspend fun addFromUri(uri: Uri, attachTo: String? = null, burstAnchor: String? = null): String {
        val bytes = context.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
        return addBytes(bytes, attachTo, burstAnchor)
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
        // Regenerate after the type settles: SPORTS grows a versus card, CONCERT the note,
        // MOVIE drops generated art entirely. Per member, since each owns its file.
        for (member in groupOf(pass)) runCatching { refreshArt(member.id) }
    }

    /**
     * (Re)draw the generated poster for one pass. No art on failure, and no art simply means
     * the pass shows its photo — so every path out of here is safe to reach.
     */
    private suspend fun refreshArt(passId: String) {
        val pass = dao.getById(passId) ?: return
        val bmp: Bitmap? = when (pass.eventType) {
            EventType.SPORTS -> sportsCard(pass.movieTitle)
            EventType.CONCERT -> EventArt.musicCard()
            else -> null
        }
        if (bmp == null) {
            if (pass.artPath != null) {
                runCatching { File(pass.artPath).delete() }
                dao.getById(passId)?.let { dao.update(it.copy(artPath = null)) }
            }
            return
        }
        val f = EventArt.savePng(bmp, File(passDir, "${pass.id}_art.png"))
        dao.getById(passId)?.let { dao.update(it.copy(artPath = f.absolutePath)) }
    }

    /** Two crests over a diagonal, or null if the title isn't a matchup or ESPN draws a blank. */
    private fun sportsCard(title: String): Bitmap? {
        val (home, away) = EventArt.splitMatchup(title) ?: return null
        val a = EventArt.teamLogoUrl(home)?.let(EventArt::downloadLogo) ?: return null
        val b = EventArt.teamLogoUrl(away)?.let(EventArt::downloadLogo) ?: return null
        return EventArt.versusCard(a, b)
    }

    /** A sibling's copy of the anchor's art — its own file, so deletes stay independent. */
    private fun copiedArt(anchor: PassEntity?, newId: String): String? =
        anchor?.artPath?.let { src ->
            runCatching {
                File(src).copyTo(File(passDir, "${newId}_art.png"), overwrite = true).absolutePath
            }.getOrNull()
        }

    /**
     * Merge two passes that were added separately into one event — the retroactive version
     * of the auto-match, for tickets that predate grouping or parsed too differently to be
     * recognised. Everything grouped with [otherId] comes along; the anchor's kind wins.
     */
    suspend fun mergeInto(anchorId: String, otherId: String) {
        if (anchorId == otherId) return
        val anchor = adoptInto(anchorId) ?: return
        val other = dao.getById(otherId) ?: return
        if (other.groupId == anchor.groupId) return
        for (member in groupOf(other)) {
            member.artPath?.let { runCatching { File(it).delete() } }
            dao.update(member.copy(
                groupId = anchor.groupId,
                eventType = anchor.eventType,
                // The event was matched once; its tickets should not disagree about it.
                posterUrl = anchor.posterUrl, overview = anchor.overview,
                runtimeMin = anchor.runtimeMin, year = anchor.year,
                artPath = copiedArt(anchor, member.id),
            ))
        }
    }

    /** Pull one ticket back out of a group — the undo for a merge or a bad auto-match. */
    suspend fun ungroup(passId: String) {
        val pass = dao.getById(passId) ?: return
        val gid = pass.groupId ?: return
        dao.update(pass.copy(groupId = null))
        // A group of one is a lone ticket again; don't leave it wearing a group id.
        dao.getGroup(gid).singleOrNull()?.let { dao.update(it.copy(groupId = null)) }
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

    /** Two tickets from one burst may join unless they name different days outright. */
    private fun agreesOnDate(anchor: PassEntity, date: String?): Boolean =
        date == null || anchor.date == null || anchor.date == date

    private fun normalizedTitle(t: String?): String? =
        t?.lowercase()?.replace(Regex("[^a-z0-9]"), "")?.takeIf { it.isNotEmpty() }

    private suspend fun addBytes(
        bytes: ByteArray,
        attachTo: String? = null,
        burstAnchor: String? = null,
    ): String {
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
        //
        // [burstAnchor] is the third route, and the weakest on purpose: shots taken back to back
        // in one camera session are almost always one stack, but "almost always" is not a licence
        // to fold two events together. So it only applies when the title match found nothing —
        // a shot that recognised a different showing keeps its own group — and only when the
        // dates do not actually disagree. What it rescues is the common case: a photograph the
        // model read badly, or not at all, which used to land on the shelf as a lone
        // "Ticket 4f2a" beside the three it was taken with.
        val matched = if (attachTo == null && title != null) findSameEvent(title, date, time) else null
        val anchor: PassEntity? = when {
            attachTo != null -> adoptInto(attachTo)
            matched != null -> adoptInto(matched.id)
            burstAnchor != null ->
                dao.getById(burstAnchor)?.takeIf { agreesOnDate(it, date) }?.let { adoptInto(it.id) }
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
                // A sibling copies the group's card instead of asking ESPN again.
                artPath = copiedArt(anchor, id),
            )
        )

        // Generated poster for a game or concert that didn't inherit one. Before the barcode
        // scan below, so the shelf shows the crests while the slow decode still runs.
        if (dao.getById(id)?.artPath == null) runCatching { refreshArt(id) }
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
    /**
     * Draw posters for non-movie passes that predate generated art (or whose generation
     * failed). A sports title that isn't a matchup fails at the regex — no network — so
     * retrying those every launch costs nothing.
     */
    suspend fun backfillArt() {
        val pending = runCatching { dao.getAll() }.getOrNull() ?: return
        for (pass in pending.filter { it.eventType != EventType.MOVIE && it.artPath == null }
            .take(MAX_BACKFILL)) {
            runCatching { refreshArt(pass.id) }
        }
    }

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
