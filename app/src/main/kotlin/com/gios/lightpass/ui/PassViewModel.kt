package com.gios.lightpass.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gios.lightpass.ai.MovieCandidate
import com.gios.lightpass.data.EventType
import com.gios.lightpass.data.PassEntity
import com.gios.lightpass.data.PassRepository
import com.gios.lightpass.util.PassTimes
import kotlinx.coroutines.Dispatchers
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

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

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

    fun addFromFile(file: File, attachTo: String? = null) = ingest { repo.addFromFile(file, attachTo) }
    fun addFromUri(uri: Uri, attachTo: String? = null) = ingest { repo.addFromUri(uri, attachTo) }
    fun save(pass: PassEntity) = viewModelScope.launch(Dispatchers.IO) { repo.update(pass) }
    fun delete(pass: PassEntity) = viewModelScope.launch(Dispatchers.IO) { repo.delete(pass) }
    fun setEventType(passId: String, type: String) =
        viewModelScope.launch(Dispatchers.IO) { repo.setEventType(passId, type) }

    suspend fun searchMovies(title: String): List<MovieCandidate> =
        withContext(Dispatchers.IO) { repo.searchMovies(title) }
    fun applyMovie(passId: String, cand: MovieCandidate) =
        viewModelScope.launch(Dispatchers.IO) { repo.applyMovie(passId, cand) }

    private fun ingest(block: suspend () -> String) {
        _busy.value = true
        viewModelScope.launch(Dispatchers.IO) {
            val id = runCatching { block() }.getOrNull()
            _busy.value = false
            if (id != null) {
                // The picker exists to identify a film. A ticket that joined a group already
                // matched, and a sports or concert ticket has nothing on TMDb to match.
                val p = repo.getById(id)
                _justAdded.value =
                    if (p != null && p.eventType == EventType.MOVIE && p.posterUrl == null) id
                    else null
            }
        }
    }
}
