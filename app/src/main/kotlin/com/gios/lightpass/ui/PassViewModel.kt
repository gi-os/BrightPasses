package com.gios.lightpass.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gios.lightpass.data.PassEntity
import com.gios.lightpass.data.PassRepository
import com.gios.lightpass.util.PassTimes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
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

    fun observePass(id: String) = repo.observePass(id)

    fun apiKey() = repo.getApiKey()
    fun setApiKey(k: String) = repo.setApiKey(k)
    fun tmdbKey() = repo.getTmdbKey()
    fun setTmdbKey(k: String) = repo.setTmdbKey(k)
    fun newCaptureFile(): File = repo.newCaptureFile()

    fun addFromFile(file: File) = ingest { repo.addFromFile(file) }
    fun addFromUri(uri: Uri) = ingest { repo.addFromUri(uri) }
    fun save(pass: PassEntity) = viewModelScope.launch(Dispatchers.IO) { repo.update(pass) }
    fun delete(pass: PassEntity) = viewModelScope.launch(Dispatchers.IO) { repo.delete(pass) }

    private fun ingest(block: suspend () -> Unit) {
        _busy.value = true
        viewModelScope.launch(Dispatchers.IO) { runCatching { block() }; _busy.value = false }
    }
}
