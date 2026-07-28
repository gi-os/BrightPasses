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

    val lists: StateFlow<PassLists> =
        repo.observeAll()
            .map { all ->
                val now = System.currentTimeMillis()
                PassLists(
                    active = all.filterNot { PassTimes.isArchived(it, now) },
                    archived = all.filter { PassTimes.isArchived(it, now) },
                )
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PassLists(emptyList(), emptyList()))

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    // one-shot: id of a freshly added pass, so the UI can open the movie picker
    private val _justAdded = MutableStateFlow<String?>(null)
    val justAdded: StateFlow<String?> = _justAdded.asStateFlow()
    fun clearJustAdded() { _justAdded.value = null }

    fun observePass(id: String) = repo.observePass(id)
    suspend fun getPass(id: String): PassEntity? = repo.getById(id)

    fun apiKey() = repo.getApiKey()
    fun setApiKey(k: String) = repo.setApiKey(k)
    fun tmdbKey() = repo.getTmdbKey()
    fun setTmdbKey(k: String) = repo.setTmdbKey(k)
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
