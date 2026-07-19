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
import org.gsgit.admin.data.*

enum class Backend { GsGit, GlassFiles }
enum class Section { Dashboard, AppConfig, Announce, Devices, Operations }

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
    val health: LoadState<HealthStatus> = LoadState.Idle,
    val metrics: LoadState<AdminMetrics> = LoadState.Idle,
    val metricsPeriod: String = "24h",
    val devices: LoadState<DevicesResponse> = LoadState.Idle,
    val config: LoadState<AppConfig> = LoadState.Idle,
    val configHistory: LoadState<Page<ConfigRevision>> = LoadState.Idle,
    val announcements: LoadState<Page<AnnouncementRecord>> = LoadState.Idle,
    val announcementDetails: LoadState<AnnouncementRecord> = LoadState.Idle,
    val maintenance: LoadState<MaintenanceState> = LoadState.Idle,
    val releases: LoadState<Page<ReleaseRecord>> = LoadState.Idle,
    val audit: LoadState<Page<AuditRecord>> = LoadState.Idle,
    val errors: LoadState<List<ServerErrorRecord>> = LoadState.Idle,
    val savingConfig: Boolean = false,
    val sendingAnnouncement: Boolean = false,
    val togglingKillSwitch: Boolean = false,
    val busyAction: String? = null,
)

class AdminViewModel(application: Application) : AndroidViewModel(application) {
    private val api = AdminApi()
    private val keyStore = AdminKeyStore(application)
    private var sessionKey: String? = null

    private val _state = MutableStateFlow(AdminUiState())
    val state = _state.asStateFlow()

    private val _messages = MutableSharedFlow<String>(
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val messages = _messages.asSharedFlow()

    init {
        keyStore.read()?.let { validateKey(it, persisted = true) }
            ?: _state.update { it.copy(auth = AuthState.Locked()) }
    }

    fun unlock(key: String) {
        val normalized = key.trim()
        if (normalized.isEmpty()) {
            _state.update { it.copy(auth = AuthState.Locked("Введите X-Admin-Key")) }
        } else {
            validateKey(normalized, persisted = false)
        }
    }

    fun lock() {
        sessionKey = null
        keyStore.clear()
        _state.value = AdminUiState(auth = AuthState.Locked())
    }

    fun selectBackend(backend: Backend) = _state.update { it.copy(backend = backend) }

    fun selectSection(section: Section) {
        _state.update { it.copy(section = section) }
        when (section) {
            Section.Dashboard -> loadDashboard()
            Section.AppConfig -> { loadConfig(); loadConfigHistory() }
            Section.Announce -> loadAnnouncements()
            Section.Devices -> loadDevices()
            Section.Operations -> loadOperations()
        }
    }

    fun refreshAll() {
        when (_state.value.section) {
            Section.Dashboard -> loadDashboard()
            Section.AppConfig -> { loadConfig(); loadConfigHistory() }
            Section.Announce -> { loadStats(); loadAnnouncements() }
            Section.Devices -> loadDevices()
            Section.Operations -> loadOperations()
        }
    }

    fun loadDashboard() {
        loadStats()
        loadHealth()
        loadMetrics(_state.value.metricsPeriod)
    }

    fun loadStats() = loadInto({ copy(stats = it) }) { api.getStats(requireKey()) }
    fun loadHealth() = loadInto({ copy(health = it) }) { api.getHealth(requireKey()) }

    fun loadMetrics(period: String) {
        if (period !in setOf("1h", "24h", "7d", "30d")) return
        _state.update { it.copy(metricsPeriod = period) }
        loadInto({ copy(metrics = it) }) { api.getMetrics(requireKey(), period) }
    }

    fun loadDevices(login: String = "", activeOnly: Boolean = false) =
        loadInto({ copy(devices = it) }) { api.getDevices(requireKey(), login, activeOnly) }

    fun loadConfig() = loadInto({ copy(config = it) }) { api.getAppConfig() }
    fun loadConfigHistory() = loadInto({ copy(configHistory = it) }) { api.getConfigHistory(requireKey()) }
    fun loadAnnouncements() = loadInto({ copy(announcements = it) }) { api.getAnnouncements(requireKey()) }

    fun loadAnnouncementDetails(id: String) =
        loadInto({ copy(announcementDetails = it) }) { api.getAnnouncement(requireKey(), id) }

    fun loadOperations() {
        loadMaintenance()
        loadReleases()
        loadAudit()
        loadErrors()
    }

    fun loadMaintenance() = loadInto({ copy(maintenance = it) }) { api.getMaintenance(requireKey()) }
    fun loadReleases() = loadInto({ copy(releases = it) }) { api.getReleases(requireKey()) }
    fun loadAudit() = loadInto({ copy(audit = it) }) { api.getAudit(requireKey()) }
    fun loadErrors(service: String = "") = loadInto({ copy(errors = it) }) { api.getErrors(requireKey(), service) }

    fun saveConfig(config: AppConfig, reason: String) {
        val localError = ConfigValidator.validate(config)
        if (localError != null) {
            _messages.tryEmit(localError)
            return
        }
        if (_state.value.savingConfig) return
        _state.update { it.copy(savingConfig = true) }
        viewModelScope.launch {
            try {
                val validation = api.validateAppConfig(requireKey(), config)
                if (!validation.ok) {
                    _messages.emit(validation.errors.joinToString("\n").ifBlank { "Конфигурация не прошла проверку" })
                    return@launch
                }
                val updated = api.saveAppConfig(requireKey(), config, reason)
                _state.update { it.copy(config = LoadState.Ready(updated)) }
                _messages.emit("Настройки проверены и сохранены")
                loadStats()
                loadConfigHistory()
            } catch (failure: ApiFailure) {
                onRequestFailure(failure) { _messages.tryEmit(it) }
            } finally {
                _state.update { it.copy(savingConfig = false) }
            }
        }
    }

    fun saveConfig(config: AppConfig) = saveConfig(config, "")

    fun rollbackConfig(revision: Int) = action("config.rollback.$revision") {
        val updated = api.rollbackConfig(requireKey(), revision)
        _state.update { it.copy(config = LoadState.Ready(updated)) }
        loadStats()
        loadConfigHistory()
        "Конфигурация откачена к ревизии $revision"
    }

    fun setMaintenance(message: String) {
        if (_state.value.togglingKillSwitch) return
        _state.update { it.copy(togglingKillSwitch = true) }
        viewModelScope.launch {
            try {
                val updated = api.setMaintenance(requireKey(), message.trim())
                _state.update { it.copy(config = LoadState.Ready(updated)) }
                _messages.emit(if (updated.maintenance.isBlank()) "Блокировка отключена" else "Блокировка включена")
                loadStats()
                loadMaintenance()
            } catch (failure: ApiFailure) {
                onRequestFailure(failure) { _messages.tryEmit(it) }
            } finally {
                _state.update { it.copy(togglingKillSwitch = false) }
            }
        }
    }

    fun sendAnnouncement(title: String, body: String, url: String, onSuccess: () -> Unit) {
        if (_state.value.sendingAnnouncement || title.isBlank() || body.isBlank()) {
            if (title.isBlank() || body.isBlank()) _messages.tryEmit("Заголовок и текст обязательны")
            return
        }
        _state.update { it.copy(sendingAnnouncement = true) }
        viewModelScope.launch {
            try {
                val result = api.announce(requireKey(), Announcement(title.trim(), body.trim(), url.trim()))
                _messages.emit("Доставлено на устройства: ${result.delivered}")
                onSuccess()
                loadStats()
                loadAnnouncements()
            } catch (failure: ApiFailure) {
                onRequestFailure(failure) { _messages.tryEmit(it) }
            } finally {
                _state.update { it.copy(sendingAnnouncement = false) }
            }
        }
    }

    fun retryAnnouncement(id: String) = action("announcement.retry.$id") {
        val result = api.retryAnnouncement(requireKey(), id)
        loadAnnouncements()
        "Повторно доставлено: ${result.delivered}; ошибок осталось: ${result.stillFailed}"
    }

    fun cancelAnnouncement(id: String) = action("announcement.cancel.$id") {
        api.cancelAnnouncement(requireKey(), id)
        "Рассылка отменена"
    }

    fun deleteDevice(deviceId: String) = action("device.delete.$deviceId") {
        api.deleteDevice(requireKey(), deviceId)
        loadDevices()
        loadStats()
        "Регистрация устройства удалена"
    }

    fun clearHeld(deviceId: String) = action("device.clear.$deviceId") {
        val cleared = api.clearHeld(requireKey(), deviceId)
        loadDevices()
        "Удалено отложенных уведомлений: $cleared"
    }

    fun setDevicePush(deviceId: String, enabled: Boolean) = action("device.push.$deviceId") {
        val actual = api.setPushEnabled(requireKey(), deviceId, enabled)
        loadDevices()
        if (actual) "Пуши для устройства включены" else "Пуши для устройства выключены"
    }

    fun testDevicePush(deviceId: String, title: String, body: String, url: String) =
        action("device.test.$deviceId") {
            val delivered = api.testPush(requireKey(), deviceId, Announcement(title, body, url))
            loadDevices()
            "Тестовый пуш доставлен: $delivered"
        }

    fun scheduleMaintenance(startsAt: String, endsAt: String, message: String) = action("maintenance.schedule") {
        if (startsAt.isBlank() || endsAt.isBlank() || message.isBlank()) throw ApiFailure.BadRequest("Заполните начало, окончание и сообщение")
        val updated = api.scheduleMaintenance(requireKey(), MaintenanceSchedule(startsAt.trim(), endsAt.trim(), message.trim()))
        _state.update { it.copy(maintenance = LoadState.Ready(updated)) }
        loadAudit()
        "Техработы запланированы"
    }

    fun deleteMaintenanceSchedule() = action("maintenance.delete") {
        api.deleteMaintenanceSchedule(requireKey())
        loadMaintenance(); loadStats(); loadAudit()
        "Расписание техработ удалено"
    }

    fun stopMaintenance() = action("maintenance.stop") {
        api.stopMaintenance(requireKey())
        loadMaintenance(); loadStats(); loadAudit()
        "Техработы немедленно остановлены"
    }

    fun saveRelease(release: ReleaseRecord) = action("release.save") {
        if (release.version.isBlank()) throw ApiFailure.BadRequest("Введите версию релиза")
        api.saveRelease(requireKey(), release)
        loadReleases(); loadAudit()
        "Релиз ${release.version} сохранён"
    }

    fun publishRelease(version: String) = action("release.publish.$version") {
        api.publishRelease(requireKey(), version)
        loadReleases(); loadConfig(); loadStats(); loadAudit()
        "Релиз $version опубликован"
    }

    private fun validateKey(key: String, persisted: Boolean) {
        if (_state.value.auth == AuthState.Checking) return
        _state.update { it.copy(auth = AuthState.Checking) }
        viewModelScope.launch {
            try {
                val stats = api.getStats(key)
                sessionKey = key
                if (!persisted) keyStore.save(key)
                _state.update { it.copy(auth = AuthState.Unlocked, stats = LoadState.Ready(stats)) }
                loadHealth()
                loadMetrics("24h")
            } catch (failure: ApiFailure) {
                sessionKey = null
                if (failure is ApiFailure.Unauthorized) keyStore.clear()
                _state.update { it.copy(auth = AuthState.Locked(failure.userMessage())) }
            }
        }
    }

    private fun action(name: String, block: suspend () -> String) {
        if (_state.value.busyAction != null) return
        _state.update { it.copy(busyAction = name) }
        viewModelScope.launch {
            try {
                _messages.emit(block())
            } catch (failure: ApiFailure) {
                onRequestFailure(failure) { _messages.tryEmit(it) }
            } finally {
                _state.update { it.copy(busyAction = null) }
            }
        }
    }

    private fun <T> loadInto(
        reducer: AdminUiState.(LoadState<T>) -> AdminUiState,
        loader: suspend () -> T,
    ) {
        if (sessionKey == null && _state.value.auth != AuthState.Unlocked) return
        _state.update { it.reducer(LoadState.Loading) }
        viewModelScope.launch {
            try {
                val value = loader()
                _state.update { it.reducer(LoadState.Ready(value)) }
            } catch (failure: ApiFailure) {
                onRequestFailure(failure) { message -> _state.update { it.reducer(LoadState.Error(message)) } }
            }
        }
    }

    private fun requireKey(): String = sessionKey ?: throw ApiFailure.Unauthorized()

    private fun onRequestFailure(failure: ApiFailure, keepScreen: (String) -> Unit) {
        if (failure is ApiFailure.Unauthorized) {
            sessionKey = null
            keyStore.clear()
            _state.update { it.copy(auth = AuthState.Locked("Неверный ключ")) }
        } else {
            keepScreen(failure.userMessage())
        }
    }

    private fun ApiFailure.userMessage(): String = when (this) {
        is ApiFailure.Unauthorized -> "Неверный ключ"
        is ApiFailure.BadRequest -> message ?: "Некорректный запрос"
        is ApiFailure.NotFound -> message ?: "Объект не найден"
        is ApiFailure.Conflict -> message ?: "Операция недоступна"
        is ApiFailure.Upstream -> message ?: "Внешний сервис отклонил запрос"
        is ApiFailure.Unreachable -> "Сервер недоступен"
        is ApiFailure.Server -> "Ошибка сервера ($status)"
        is ApiFailure.InvalidResponse -> "Сервер вернул некорректный ответ"
    }
}
