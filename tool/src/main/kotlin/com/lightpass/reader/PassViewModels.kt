package com.lightpass.reader

import androidx.lifecycle.viewModelScope
import com.thelightphone.sdk.LightViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HomeViewModel(
    val repository: PassRepository,
    private val database: PassDatabase,
) : LightViewModel<Unit>() {

    val passes: StateFlow<List<PassEntity>> = repository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _status = MutableStateFlow<String?>(null)
    val status: StateFlow<String?> = _status.asStateFlow()

    fun importFromInbox() {
        _status.value = "Importing…"
        viewModelScope.launch(Dispatchers.IO) {
            val n = runCatching { repository.importFromInbox() }.getOrDefault(0)
            _status.value = if (n > 0) "Added $n" else "Nothing to import"
        }
    }

    fun delete(pass: PassEntity) {
        viewModelScope.launch(Dispatchers.IO) { repository.deletePass(pass) }
    }

    override fun onCleared() {
        repository.close()
        database.close()
        super.onCleared()
    }
}

data class SettingsUiState(
    val draft: String = "",
    val inputSession: Int = 0,
)

class SettingsViewModel(private val repository: PassRepository) : LightViewModel<Unit>() {
    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val key = repository.getApiKey()
            _state.value = SettingsUiState(draft = key, inputSession = 1)
        }
    }

    fun save(key: String, onDone: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.setApiKey(key)
            onDone()
        }
    }
}

class ViewerViewModel(
    repository: PassRepository,
    passId: String,
) : LightViewModel<Unit>() {
    val pass: StateFlow<PassEntity?> = repository.observePass(passId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

/** No-state viewmodel for simple screens (camera/album/QR/chooser). */
class EmptyViewModel : com.thelightphone.sdk.LightViewModel<Unit>()
