package com.gios.lightpass.ui

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gios.lightpass.ai.MovieCandidate
import com.gios.lightpass.data.EventType
import com.gios.lightpass.data.PassEntity
import com.gios.lightpass.data.PassRepository
import com.gios.lightpass.util.PassTimes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * One entry on the shelf: an event and every ticket to it. A lone ticket is a group of one,
 * so the list code never has two cases.
 */
data class PassGroup(val tickets: List<PassEntity>) {
    /** The oldest ticket speaks for the group — it is the one whose id names the group. */
    val primary: PassEntity get() = tickets.first()
    val count: Int get() = tickets.size
}

data class PassLists(val active: List<PassGroup>, val archived: List<PassGroup>)

class PassViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = PassRepository(app)

    init {
        // Tickets added before the app could read a barcode still have the code sitting in their
        // photograph. Catch a few up in the background on each launch — nothing waits on it, and a
        // ticket whose code arrives while you're looking at the shelf is the point.
        viewModelScope.launch(Dispatchers.IO) { runCatching { repo.backfillScannedCodes() } }
        // And posters for games/concerts added before generated art existed.
        viewModelScope.launch(Dispatchers.IO) { runCatching { repo.backfillArt() } }
    }

    val lists: StateFlow<PassLists> =
        repo.observeAll()
            .map { all ->
                val now = System.currentTimeMillis()
                // Tickets to the same showing collapse to one shelf entry; whether that entry
                // is upcoming or archived is the primary's call, since siblings share a clock.
                val groups = all.groupBy { it.groupId ?: it.id }.values
                    .map { PassGroup(it.sortedBy { t -> t.addedAt }) }
                PassLists(
                    active = groups.filterNot { PassTimes.isArchived(it.primary, now) }
                        .sortedWith(compareBy(nullsLast()) { PassTimes.startMillis(it.primary) }),
                    archived = groups.filter { PassTimes.isArchived(it.primary, now) }
                        .sortedByDescending { PassTimes.startMillis(it.primary) ?: it.primary.addedAt },
                )
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PassLists(emptyList(), emptyList()))

    /**
     * One photograph waiting to be read: the file, and what it should attach to.
     *
     * [burst] marks a shot taken in a camera session rather than a lone add — those defer the
     * movie picker to the end so it cannot open over a live viewfinder.
     */
    private class IngestJob(
        val file: File? = null,
        val uri: Uri? = null,
        val attachTo: String?,
        val burst: Boolean,
    )

    /**
     * Every photograph is read on this one thread, in the order it was taken.
     *
     * Before, each add launched its own coroutine on Dispatchers.IO, so a burst of four meant
     * four model calls, four ZXing decodes and four full-size bitmaps live at once — on a phone
     * that has none of those to spare. That is what made the preview stutter after about the
     * third shot. Serialising costs nothing in wall-clock time (the work was contending, not
     * parallelising) and it gives the buffer indicator something honest to count.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val ingestDispatcher = Dispatchers.IO.limitedParallelism(1)
    private val jobs = Channel<IngestJob>(Channel.UNLIMITED)

    /** Shots taken and not yet read. Counts down; the camera draws it as the buffer bar. */
    private val _pending = MutableStateFlow(0)
    val pending: StateFlow<Int> = _pending.asStateFlow()

    /** Shots taken in this camera session, so the bar can fill against a fixed denominator. */
    private val _burstTotal = MutableStateFlow(0)

    /** 0 when the buffer is full, 1 when it has drained — the fill of the bar. */
    val burstProgress: StateFlow<Float> =
        combine(_pending, _burstTotal) { left, total ->
            if (total <= 0) 1f else ((total - left).toFloat() / total).coerceIn(0f, 1f)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 1f)

    val busy: StateFlow<Boolean> =
        _pending.map { it > 0 }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    /**
     * The first pass of the current burst, which later shots in the same burst fall back to.
     *
     * Written from the ingest thread only, which is the single consumer, so it needs no lock.
     */
    private var burstAnchor: String? = null
    private var inBurst = false

    init {
        viewModelScope.launch {
            for (job in jobs) {
                val id = withContext(ingestDispatcher) {
                    runCatching {
                        when {
                            job.file != null -> repo.addFromFile(job.file, job.attachTo, burstAnchor)
                            job.uri != null -> repo.addFromUri(job.uri, job.attachTo, burstAnchor)
                            else -> null
                        }
                    }.getOrNull()
                }
                if (id != null && job.burst && burstAnchor == null) burstAnchor = id
                _pending.update { (it - 1).coerceAtLeast(0) }
                // A lone add still opens the picker the moment it is ready. A burst waits:
                // see endBurst().
                if (id != null && !job.burst) _justAdded.value = pickerCandidate(id)
            }
        }
    }

    private val _apiKey = MutableStateFlow(repo.getApiKey())
    val apiKeyState: StateFlow<String> = _apiKey.asStateFlow()
    private val _tmdbKey = MutableStateFlow(repo.getTmdbKey())
    val tmdbKeyState: StateFlow<String> = _tmdbKey.asStateFlow()

    // one-shot: id of a freshly added pass, so the UI can open the movie picker
    private val _justAdded = MutableStateFlow<String?>(null)
    val justAdded: StateFlow<String?> = _justAdded.asStateFlow()
    fun clearJustAdded() { _justAdded.value = null }

    fun observePass(id: String) = repo.observePass(id)
    fun observeTickets(id: String) = repo.observeTickets(id)
    suspend fun getPass(id: String): PassEntity? = repo.getById(id)

    fun apiKey() = repo.getApiKey()
    fun setApiKey(k: String) { repo.setApiKey(k); _apiKey.value = repo.getApiKey() }
    fun tmdbKey() = repo.getTmdbKey()
    fun setTmdbKey(k: String) { repo.setTmdbKey(k); _tmdbKey.value = repo.getTmdbKey() }
    fun hasTmdbKey() = repo.getTmdbKey().isNotBlank()
    fun newCaptureFile(): File = repo.newCaptureFile()

    fun addFromFile(file: File, attachTo: String? = null) = enqueue(IngestJob(file = file, attachTo = attachTo, burst = false))
    fun addFromUri(uri: Uri, attachTo: String? = null) = enqueue(IngestJob(uri = uri, attachTo = attachTo, burst = false))

    /** A camera session starts here: shots from now until [endBurst] land on one stack. */
    fun startBurst() {
        inBurst = true
        burstAnchor = null
        // Denominator, not a reset: an album add still being read has to keep its place in the
        // count, or the bar fills to a number that is not what is left.
        _burstTotal.value = _pending.value
    }

    /**
     * Hand a captured frame over and return immediately.
     *
     * The encode runs off the main thread — it used to run inside the button's onClick, where a
     * full-resolution JPEG sits directly between the finger and the next preview frame. It is not
     * on the serial ingest thread either, because compressing is quick and there is no reason for
     * shot four to wait behind shot one's model call just to reach the disk.
     */
    fun captureShot(bitmap: Bitmap, attachTo: String? = null) {
        _pending.update { it + 1 }
        _burstTotal.update { it + 1 }
        viewModelScope.launch(Dispatchers.IO) {
            val out = runCatching {
                repo.newCaptureFile().also { f ->
                    f.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 92, it) }
                }
            }.getOrNull()
            runCatching { bitmap.recycle() }
            if (out == null) _pending.update { (it - 1).coerceAtLeast(0) }
            else jobs.send(IngestJob(file = out, attachTo = attachTo, burst = true))
        }
    }

    /**
     * Leave the camera. The picker, if the stack wants one, opens when the last shot is read —
     * not over the viewfinder, and not before the anchor exists to open it for.
     */
    fun endBurst() {
        if (!inBurst) return
        inBurst = false
        viewModelScope.launch {
            _pending.first { it == 0 }
            _justAdded.value = burstAnchor?.let { pickerCandidate(it) }
            burstAnchor = null
        }
    }

    private fun enqueue(job: IngestJob) {
        _pending.update { it + 1 }
        _burstTotal.update { it + 1 }
        viewModelScope.launch { jobs.send(job) }
    }

    /** The picker exists to identify a film, so only an unmatched movie is worth opening it for. */
    private suspend fun pickerCandidate(id: String): String? {
        val p = repo.getById(id) ?: return null
        return if (p.eventType == EventType.MOVIE && p.posterUrl == null) id else null
    }
    fun save(pass: PassEntity) = viewModelScope.launch(Dispatchers.IO) { repo.updateFromEdit(pass) }
    fun delete(pass: PassEntity) = viewModelScope.launch(Dispatchers.IO) { repo.delete(pass) }
    fun setEventType(passId: String, type: String) =
        viewModelScope.launch(Dispatchers.IO) { repo.setEventType(passId, type) }
    fun mergeInto(anchorId: String, otherId: String) =
        viewModelScope.launch(Dispatchers.IO) { repo.mergeInto(anchorId, otherId) }
    fun ungroup(passId: String) =
        viewModelScope.launch(Dispatchers.IO) { repo.ungroup(passId) }

    suspend fun searchMovies(title: String): List<MovieCandidate> =
        withContext(Dispatchers.IO) { repo.searchMovies(title) }
    fun applyMovie(passId: String, cand: MovieCandidate) =
        viewModelScope.launch(Dispatchers.IO) { repo.applyMovie(passId, cand) }

}
