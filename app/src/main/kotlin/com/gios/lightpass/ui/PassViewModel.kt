package com.gios.lightpass.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gios.lightpass.ai.MovieCandidate
import com.gios.lightpass.data.PassEntity
import com.gios.lightpass.data.PassRepository
import com.gios.lightpass.util.PassTimes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class PassLists(val active: List<PassEntity>, val archived: List<PassEntity>)

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
                PassLists(
                    active = all.filterNot { PassTimes.isArchived(it, now) }
                        .sortedWith(compareBy(nullsLast()) { PassTimes.startMillis(it) }),
                    archived = all.filter { PassTimes.isArchived(it, now) }
                        .sortedByDescending { PassTimes.startMillis(it) ?: it.addedAt },
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
    suspend fun getPass(id: String): PassEntity? = repo.getById(id)

    fun apiKey() = repo.getApiKey()
    fun setApiKey(k: String) { repo.setApiKey(k); _apiKey.value = repo.getApiKey() }
    fun tmdbKey() = repo.getTmdbKey()
    fun setTmdbKey(k: String) { repo.setTmdbKey(k); _tmdbKey.value = repo.getTmdbKey() }
    fun hasTmdbKey() = repo.getTmdbKey().isNotBlank()
    fun newCaptureFile(): File = repo.newCaptureFile()

    fun addFromFile(file: File) = ingest { repo.addFromFile(file) }
    fun addFromUri(uri: Uri) = ingest { repo.addFromUri(uri) }
    fun save(pass: PassEntity) = viewModelScope.launch(Dispatchers.IO) { repo.update(pass) }
    fun delete(pass: PassEntity) = viewModelScope.launch(Dispatchers.IO) { repo.delete(pass) }

    suspend fun searchMovies(title: String): List<MovieCandidate> =
        withContext(Dispatchers.IO) { repo.searchMovies(title) }
    fun applyMovie(passId: String, cand: MovieCandidate) =
        viewModelScope.launch(Dispatchers.IO) { repo.applyMovie(passId, cand) }

    private fun ingest(block: suspend () -> String) {
        _busy.value = true
        viewModelScope.launch(Dispatchers.IO) {
            val id = runCatching { block() }.getOrNull()
            _busy.value = false
            if (id != null) _justAdded.value = id
        }
    }
}
