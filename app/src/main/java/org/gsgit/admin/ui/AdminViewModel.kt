package org.gsgit.admin.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.gsgit.admin.data.AdminApi
import org.gsgit.admin.data.AdminKeyStore
import org.gsgit.admin.data.AdminStats
import org.gsgit.admin.data.Announcement
import org.gsgit.admin.data.ApiFailure
import org.gsgit.admin.data.AppConfig
import org.gsgit.admin.data.DevicesResponse
import org.gsgit.admin.data.ConfigValidator

enum class Backend { GsGit, GlassFiles }
enum class Section { Dashboard, AppConfig, Announce, Devices }

sealed interface AuthState {
    data object Restoring : AuthState
    data class Locked(val error: String? = null) : AuthState
    data object Checking : AuthState
    data object Unlocked : AuthState
}

sealed interface LoadState<out T> {
    data object Idle : LoadState<Nothing>
    data object Loading : LoadState<Nothing>
    data class Ready<T>(val value: T) : LoadState<T>
    data class Error(val message: String) : LoadState<Nothing>
}

data class AdminUiState(
    val auth: AuthState = AuthState.Restoring,
    val backend: Backend = Backend.GsGit,
    val section: Section = Section.Dashboard,
    val stats: LoadState<AdminStats> = LoadState.Idle,
    val devices: LoadState<DevicesResponse> = LoadState.Idle,
    val config: LoadState<AppConfig> = LoadState.Idle,
    val savingConfig: Boolean = false,
    val sendingAnnouncement: Boolean = false,
    val togglingKillSwitch: Boolean = false,
)

class AdminViewModel(application: Application) : AndroidViewModel(application) {
    private val api = AdminApi()
    private val keyStore = AdminKeyStore(application)
    private var sessionKey: String? = null

    private val _state = MutableStateFlow(AdminUiState())
    val state = _state.asStateFlow()

    private val _messages = MutableSharedFlow<String>(
        extraBufferCapacity = 4,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val messages = _messages.asSharedFlow()

    init {
        val savedKey = keyStore.read()
        if (savedKey == null) {
            _state.update { it.copy(auth = AuthState.Locked()) }
        } else {
            validateKey(savedKey, persisted = true)
        }
    }

    fun unlock(key: String) {
        val normalized = key.trim()
        if (normalized.isEmpty()) {
            _state.update { it.copy(auth = AuthState.Locked("Enter X-Admin-Key")) }
            return
        }
        validateKey(normalized, persisted = false)
    }

    fun lock() {
        sessionKey = null
        keyStore.clear()
        _state.value = AdminUiState(auth = AuthState.Locked())
    }

    fun selectBackend(backend: Backend) {
        _state.update { it.copy(backend = backend) }
    }

    fun selectSection(section: Section) {
        _state.update { it.copy(section = section) }
        if (section == Section.AppConfig && _state.value.config is LoadState.Idle) {
            loadConfig()
        }
        if (section == Section.Devices && _state.value.devices is LoadState.Idle) {
            loadDevices()
        }
    }

    fun refreshAll() {
        loadStats()
        loadDevices()
        if (_state.value.section == Section.AppConfig) loadConfig()
    }

    fun loadStats() {
        val key = sessionKey ?: return
        _state.update { it.copy(stats = LoadState.Loading) }
        viewModelScope.launch {
            try {
                _state.update { it.copy(stats = LoadState.Ready(api.getStats(key))) }
            } catch (failure: ApiFailure) {
                onRequestFailure(failure) { message ->
                    _state.update { it.copy(stats = LoadState.Error(message)) }
                }
            }
        }
    }

    fun loadDevices() {
        val key = sessionKey ?: return
        _state.update { it.copy(devices = LoadState.Loading) }
        viewModelScope.launch {
            try {
                _state.update { it.copy(devices = LoadState.Ready(api.getDevices(key))) }
            } catch (failure: ApiFailure) {
                onRequestFailure(failure) { message ->
                    _state.update { it.copy(devices = LoadState.Error(message)) }
                }
            }
        }
    }

    fun loadConfig() {
        _state.update { it.copy(config = LoadState.Loading) }
        viewModelScope.launch {
            try {
                _state.update { it.copy(config = LoadState.Ready(api.getAppConfig())) }
            } catch (failure: ApiFailure) {
                onRequestFailure(failure) { message ->
                    _state.update { it.copy(config = LoadState.Error(message)) }
                }
            }
        }
    }

    fun saveConfig(config: AppConfig) {
        val key = sessionKey ?: return
        val validation = ConfigValidator.validate(config)
        if (validation != null) {
            _messages.tryEmit(validation)
            return
        }
        if (_state.value.savingConfig) return

        _state.update { it.copy(savingConfig = true) }
        viewModelScope.launch {
            try {
                val updated = api.saveAppConfig(key, config)
                _state.update {
                    it.copy(config = LoadState.Ready(updated), savingConfig = false)
                }
                _messages.emit("Saved, clients pick up within a minute")
                loadStats()
            } catch (failure: ApiFailure) {
                _state.update { it.copy(savingConfig = false) }
                onRequestFailure(failure) { message -> _messages.tryEmit(message) }
            }
        }
    }

    fun setMaintenance(message: String) {
        val key = sessionKey ?: return
        if (_state.value.togglingKillSwitch) return
        if (message.isBlank() && !currentMaintenance().isMaintenanceOn()) return

        _state.update { it.copy(togglingKillSwitch = true) }
        viewModelScope.launch {
            try {
                val updated = api.setMaintenance(key, message.trim())
                _state.update {
                    it.copy(
                        config = LoadState.Ready(updated),
                        togglingKillSwitch = false,
                    )
                }
                _messages.emit(if (updated.maintenance.isBlank()) "Kill-switch disabled" else "Kill-switch enabled")
                loadStats()
            } catch (failure: ApiFailure) {
                _state.update { it.copy(togglingKillSwitch = false) }
                onRequestFailure(failure) { text -> _messages.tryEmit(text) }
            }
        }
    }

    fun sendAnnouncement(title: String, body: String, url: String, onSuccess: () -> Unit) {
        val key = sessionKey ?: return
        if (_state.value.sendingAnnouncement) return
        if (title.isBlank() || body.isBlank()) {
            _messages.tryEmit("Title and text are required")
            return
        }

        _state.update { it.copy(sendingAnnouncement = true) }
        viewModelScope.launch {
            try {
                val result = api.announce(
                    key,
                    Announcement(title = title.trim(), body = body.trim(), url = url.trim()),
                )
                _state.update { it.copy(sendingAnnouncement = false) }
                _messages.emit("Delivered to ${result.delivered} devices")
                onSuccess()
                loadStats()
            } catch (failure: ApiFailure) {
                _state.update { it.copy(sendingAnnouncement = false) }
                onRequestFailure(failure) { message -> _messages.tryEmit(message) }
            }
        }
    }

    private fun validateKey(key: String, persisted: Boolean) {
        if (_state.value.auth == AuthState.Checking) return
        _state.update { it.copy(auth = AuthState.Checking) }
        viewModelScope.launch {
            try {
                val stats = api.getStats(key)
                sessionKey = key
                if (!persisted) keyStore.save(key)
                _state.update {
                    it.copy(
                        auth = AuthState.Unlocked,
                        stats = LoadState.Ready(stats),
                        devices = LoadState.Loading,
                    )
                }
                loadDevices()
            } catch (failure: ApiFailure) {
                sessionKey = null
                if (failure is ApiFailure.Unauthorized) keyStore.clear()
                _state.update { it.copy(auth = AuthState.Locked(failure.userMessage())) }
            }
        }
    }

    private fun onRequestFailure(failure: ApiFailure, keepScreen: (String) -> Unit) {
        if (failure is ApiFailure.Unauthorized) {
            sessionKey = null
            keyStore.clear()
            _state.update { it.copy(auth = AuthState.Locked("Wrong key")) }
        } else {
            keepScreen(failure.userMessage())
        }
    }

    private fun currentMaintenance(): String =
        (_state.value.stats as? LoadState.Ready)?.value?.maintenance.orEmpty()

    private fun String.isMaintenanceOn(): Boolean = isNotBlank() && !equals("off", ignoreCase = true)

    private fun ApiFailure.userMessage(): String = when (this) {
        is ApiFailure.Unauthorized -> "Wrong key"
        is ApiFailure.BadRequest -> message ?: "Invalid request"
        is ApiFailure.Unreachable -> "Server unreachable"
        is ApiFailure.Server -> "Server error ($status)"
        is ApiFailure.InvalidResponse -> "Invalid server response"
    }
}
