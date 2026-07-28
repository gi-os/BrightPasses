package com.gios.lightpass.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gios.lightpass.data.PassEntity
import com.gios.lightpass.data.PassRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File

class PassViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = PassRepository(app)

    val passes: StateFlow<List<PassEntity>> =
        repo.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    fun observePass(id: String) = repo.observePass(id)

    fun apiKey(): String = repo.getApiKey()
    fun setApiKey(key: String) = repo.setApiKey(key)
    fun newCaptureFile(): File = repo.newCaptureFile()

    fun addFromFile(file: File) = ingest { repo.addFromFile(file) }
    fun addFromUri(uri: Uri) = ingest { repo.addFromUri(uri) }
    fun delete(pass: PassEntity) = viewModelScope.launch(Dispatchers.IO) { repo.delete(pass) }

    private fun ingest(block: suspend () -> Unit) {
        _busy.value = true
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { block() }
            _busy.value = false
        }
    }
}
