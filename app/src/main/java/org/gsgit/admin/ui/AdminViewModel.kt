package org.gsgit.admin.ui

import android.app.Application
import android.net.Uri
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.gsgit.admin.data.*
import java.io.IOException

enum class Backend { GsGit, LMG, GlassFiles }
enum class Section { Dashboard, AppConfig, Announce, Devices, Operations }

sealed interface AuthState {
    data object Restoring : AuthState
    data class Locked(val error: String? = null) : AuthState
    data class BiometricRequired(val error: String? = null) : AuthState
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
    val operationsTab: String = "maintenance",
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
    val configRevisionDetails: LoadState<ConfigRevision> = LoadState.Idle,
    val releaseReadiness: LoadState<ReleaseReadiness> = LoadState.Idle,
    val lmgHealth: LoadState<LmgHealth> = LoadState.Idle,
    val lmgStatus: LoadState<LmgStatus> = LoadState.Idle,
    val lmgActivity: LoadState<LmgActivity> = LoadState.Idle,
    val lmgLatency: LoadState<Map<String, LmgLatencyStat>> = LoadState.Idle,
    val lmgMetrics: LoadState<LmgMetrics> = LoadState.Idle,
    val lmgMetricsPeriod: String = "24h",
    val lmgUsers: LoadState<LmgUsersResponse> = LoadState.Idle,
    val lmgSelectedUser: LoadState<LmgUser> = LoadState.Idle,
    val lmgDevices: LoadState<LmgDevicesResponse> = LoadState.Idle,
    val lmgConfig: LoadState<LmgConfig> = LoadState.Idle,
    val lmgErrors: LoadState<List<LmgError>> = LoadState.Idle,
    val lmgSessionTest: LoadState<LmgSessionTest> = LoadState.Idle,
    val lmgClientErrors: LoadState<List<LmgClientError>> = LoadState.Idle,
    val lmgRateLimits: LoadState<List<LmgRateLimit>> = LoadState.Idle,
    val lmgBackup: LoadState<LmgBackup> = LoadState.Idle,
    val lmgRotatedKey: LoadState<LmgRotatedKey> = LoadState.Idle,
    val savingConfig: Boolean = false,
    val sendingAnnouncement: Boolean = false,
    val togglingKillSwitch: Boolean = false,
    val busyAction: String? = null,
    val biometricEnabled: Boolean = true,
    val lockTimeoutMinutes: Int = 5,
    val autoRefreshSeconds: Int = 0,
    val configDraft: AppConfig? = null,
    val configReasonDraft: String = "",
    val announcementDraft: Announcement = Announcement(),
    val releaseDraft: ReleaseRecord = ReleaseRecord(""),
)

class AdminViewModel(application: Application) : AndroidViewModel(application) {
    private val api = AdminApi()
    private val lmgApi = LmgAdminApi()
    private val keyStore = AdminKeyStore(application)
    private val securityStore = AdminSecurityStore(application)
    private val draftStore = AdminDraftStore(application)
    private val apkVerifier = ApkVerifier()
    private var sessionKey: String? = null
    private var pendingSavedKey: String? = null
    private var backgroundAtMs: Long? = null
    private var autoRefreshJob: Job? = null

    private val _state = MutableStateFlow(AdminUiState())
    val state = _state.asStateFlow()

    private val _messages = MutableSharedFlow<String>(
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val messages = _messages.asSharedFlow()

    init {
        _state.update {
            val configDraft = draftStore.readConfig()
            it.copy(
                biometricEnabled = securityStore.biometricEnabled,
                lockTimeoutMinutes = securityStore.lockTimeoutMinutes,
                autoRefreshSeconds = securityStore.autoRefreshSeconds,
                configDraft = configDraft?.first,
                configReasonDraft = configDraft?.second.orEmpty(),
                announcementDraft = draftStore.readAnnouncement() ?: Announcement(),
                releaseDraft = draftStore.readRelease() ?: ReleaseRecord(""),
            )
        }
        keyStore.read()?.let { savedKey ->
            pendingSavedKey = savedKey
            if (securityStore.biometricEnabled) {
                _state.update { it.copy(auth = AuthState.BiometricRequired()) }
            } else {
                validateKey(savedKey, persisted = true)
            }
        } ?: _state.update { it.copy(auth = AuthState.Locked()) }
        restartAutoRefresh()
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
        val saved = sessionKey ?: keyStore.read()
        pendingSavedKey = saved
        sessionKey = null
        _state.update {
            it.copy(
                auth = if (securityStore.biometricEnabled && saved != null) {
                    AuthState.BiometricRequired()
                } else {
                    AuthState.Locked("Сессия заблокирована")
                },
            )
        }
    }

    fun logout() {
        sessionKey = null
        pendingSavedKey = null
        keyStore.clear()
        _state.value = AdminUiState(
            auth = AuthState.Locked(),
            biometricEnabled = securityStore.biometricEnabled,
            lockTimeoutMinutes = securityStore.lockTimeoutMinutes,
            autoRefreshSeconds = securityStore.autoRefreshSeconds,
            configDraft = draftStore.readConfig()?.first,
            configReasonDraft = draftStore.readConfig()?.second.orEmpty(),
            announcementDraft = draftStore.readAnnouncement() ?: Announcement(),
            releaseDraft = draftStore.readRelease() ?: ReleaseRecord(""),
        )
    }

    fun completeBiometricAuthentication() {
        val saved = pendingSavedKey ?: keyStore.read()
        if (saved == null) {
            _state.update { it.copy(auth = AuthState.Locked("Сохранённый ключ не найден")) }
            return
        }
        validateKey(saved, persisted = true)
    }

    fun biometricFailed(message: String) {
        _state.update { it.copy(auth = AuthState.BiometricRequired(message)) }
    }

    fun useAdminKeyInstead() {
        sessionKey = null
        _state.update { it.copy(auth = AuthState.Locked()) }
    }

    fun onBackground() {
        if (_state.value.auth == AuthState.Unlocked) backgroundAtMs = SystemClock.elapsedRealtime()
    }

    fun onForeground() {
        val backgroundAt = backgroundAtMs ?: return
        backgroundAtMs = null
        val elapsed = SystemClock.elapsedRealtime() - backgroundAt
        if (elapsed >= securityStore.lockTimeoutMinutes * 60_000L) lock()
    }

    fun setBiometricEnabled(enabled: Boolean) {
        securityStore.biometricEnabled = enabled
        _state.update { it.copy(biometricEnabled = enabled) }
    }

    fun setLockTimeout(minutes: Int) {
        if (minutes !in AdminSecurityStore.ALLOWED_TIMEOUTS) return
        securityStore.lockTimeoutMinutes = minutes
        _state.update { it.copy(lockTimeoutMinutes = minutes) }
    }

    fun setAutoRefresh(seconds: Int) {
        if (seconds !in AdminSecurityStore.ALLOWED_REFRESH_INTERVALS) return
        securityStore.autoRefreshSeconds = seconds
        _state.update { it.copy(autoRefreshSeconds = seconds) }
        restartAutoRefresh()
    }

    fun saveConfigDraft(config: AppConfig, reason: String) {
        draftStore.saveConfig(config, reason)
        _state.update { it.copy(configDraft = config, configReasonDraft = reason) }
    }

    fun clearConfigDraft() {
        draftStore.clearConfig()
        _state.update { it.copy(configDraft = null, configReasonDraft = "") }
    }

    fun saveAnnouncementDraft(value: Announcement) {
        draftStore.saveAnnouncement(value)
        _state.update { it.copy(announcementDraft = value) }
    }

    fun clearAnnouncementDraft() {
        draftStore.clearAnnouncement()
        _state.update { it.copy(announcementDraft = Announcement()) }
    }

    fun saveReleaseDraft(value: ReleaseRecord) {
        draftStore.saveRelease(value)
        _state.update { it.copy(releaseDraft = value) }
    }

    fun clearReleaseDraft() {
        draftStore.clearRelease()
        _state.update { it.copy(releaseDraft = ReleaseRecord("")) }
    }

    fun selectBackend(backend: Backend) {
        _state.update { it.copy(backend = backend) }
        if (backend == Backend.LMG) loadLmgDashboard()
        restartAutoRefresh()
    }

    fun selectSection(section: Section) {
        _state.update { it.copy(section = section) }
        when (section) {
            Section.Dashboard -> loadDashboard()
            Section.AppConfig -> { loadConfig(); loadConfigHistory() }
            Section.Announce -> loadAnnouncements()
            Section.Devices -> loadDevices()
            Section.Operations -> loadOperations()
        }
        restartAutoRefresh()
    }

    fun openOperations(tab: String) {
        _state.update { it.copy(section = Section.Operations, operationsTab = tab) }
        loadOperations()
        restartAutoRefresh()
    }

    fun showMessage(message: String) {
        _messages.tryEmit(message)
    }

    fun refreshAll() {
        if (_state.value.backend == Backend.LMG) {
            loadLmgDashboard()
            return
        }
        if (_state.value.backend == Backend.GlassFiles) return
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

    fun loadLmgDashboard() {
        loadLmgStatus()
        loadLmgHealth()
        loadLmgActivity()
        loadLmgLatency()
        loadLmgMetrics(_state.value.lmgMetricsPeriod)
        loadLmgUsers()
        loadLmgDevices()
        loadLmgConfig()
        loadLmgErrors()
        loadLmgClientErrors()
        loadLmgRateLimits()
    }

    fun loadLmgStatus() = loadInto({ copy(lmgStatus = it) }) { lmgApi.getStatus(requireKey()) }
    fun loadLmgHealth() = loadInto({ copy(lmgHealth = it) }) { lmgApi.getHealth(requireKey()) }
    fun loadLmgActivity() = loadInto({ copy(lmgActivity = it) }) { lmgApi.getActivity(requireKey()) }
    fun loadLmgLatency() = loadInto({ copy(lmgLatency = it) }) { lmgApi.getLatency(requireKey()) }

    fun loadLmgMetrics(period: String) {
        if (period !in setOf("1h", "24h", "7d", "30d")) return
        _state.update { it.copy(lmgMetricsPeriod = period) }
        loadInto({ copy(lmgMetrics = it) }) { lmgApi.getMetrics(requireKey(), period) }
    }

    fun loadLmgUsers() = loadInto({ copy(lmgUsers = it) }) { lmgApi.getUsers(requireKey()) }
    fun loadLmgDevices() = loadInto({ copy(lmgDevices = it) }) { lmgApi.getDevices(requireKey()) }
    fun loadLmgConfig() = loadInto({ copy(lmgConfig = it) }) { lmgApi.getConfig(requireKey()) }
    fun loadLmgErrors() = loadInto({ copy(lmgErrors = it) }) { lmgApi.getErrors(requireKey()) }
    fun loadLmgClientErrors() = loadInto({ copy(lmgClientErrors = it) }) { lmgApi.getClientErrors(requireKey()) }
    fun loadLmgRateLimits() = loadInto({ copy(lmgRateLimits = it) }) { lmgApi.getRateLimits(requireKey()) }

    fun loadLmgUser(partnerUserId: String) =
        loadInto({ copy(lmgSelectedUser = it) }) { lmgApi.getUser(requireKey(), partnerUserId) }

    fun closeLmgUser() {
        _state.update { it.copy(lmgSelectedUser = LoadState.Idle) }
    }

    fun setLmgPremium(partnerUserId: String, premium: Boolean, until: Long = 0) =
        action("lmg.premium.$partnerUserId") {
            val updated = lmgApi.setPremium(requireKey(), partnerUserId, premium, until)
            _state.update { it.copy(lmgSelectedUser = LoadState.Ready(updated)) }
            loadLmgUsers()
            if (premium) "Ручной премиум выдан" else "Ручной премиум снят"
        }

    fun deleteLmgUser(partnerUserId: String) = action("lmg.user.delete.$partnerUserId") {
        lmgApi.deleteUser(requireKey(), partnerUserId)
        closeLmgUser()
        loadLmgUsers()
        loadLmgDevices()
        loadLmgHealth()
        "Пользователь удалён"
    }

    fun setLmgBanned(partnerUserId: String, banned: Boolean, reason: String = "") =
        action("lmg.user.ban.$partnerUserId") {
            val updated = lmgApi.setBanned(requireKey(), partnerUserId, banned, reason)
            _state.update { it.copy(lmgSelectedUser = LoadState.Ready(updated)) }
            loadLmgUsers()
            loadLmgActivity()
            if (banned) "Пользователь заблокирован" else "Пользователь разблокирован"
        }

    fun saveLmgConfig(previous: LmgConfig, updated: LmgConfig) = action("lmg.config.save") {
        val saved = lmgApi.updateConfig(requireKey(), previous, updated)
        _state.update { it.copy(lmgConfig = LoadState.Ready(saved)) }
        if (saved == previous) "В конфигурации нет изменений" else "Конфигурация LMG сохранена"
    }

    fun testLmgSession() {
        if (_state.value.busyAction != null) return
        _state.update { it.copy(busyAction = "lmg.session.test", lmgSessionTest = LoadState.Loading) }
        viewModelScope.launch {
            try {
                val result = lmgApi.testSession(requireKey())
                _state.update { it.copy(lmgSessionTest = LoadState.Ready(result)) }
                _messages.emit(if (result.gotToken) "Связь с ICM работает" else "ICM не вернул токен")
            } catch (failure: ApiFailure) {
                onRequestFailure(failure) { message ->
                    _state.update { it.copy(lmgSessionTest = LoadState.Error(message)) }
                    _messages.tryEmit(message)
                }
            } finally {
                _state.update { it.copy(busyAction = null) }
            }
        }
    }

    fun clearLmgClientErrors() = action("lmg.client-errors.clear") {
        lmgApi.clearClientErrors(requireKey())
        loadLmgClientErrors()
        "Клиентские ошибки очищены"
    }

    fun clearLmgRateLimits(ip: String? = null) = action("lmg.ratelimits.clear") {
        lmgApi.clearRateLimits(requireKey(), ip)
        loadLmgRateLimits()
        if (ip.isNullOrBlank()) "Все оперативные лимиты очищены" else "Лимит для $ip очищен"
    }

    fun loadLmgBackup() {
        if (_state.value.busyAction != null) return
        _state.update { it.copy(busyAction = "lmg.backup", lmgBackup = LoadState.Loading) }
        viewModelScope.launch {
            try {
                val backup = lmgApi.downloadBackup(requireKey())
                _state.update { it.copy(lmgBackup = LoadState.Ready(backup)) }
            } catch (failure: ApiFailure) {
                onRequestFailure(failure) { message ->
                    _state.update { it.copy(lmgBackup = LoadState.Error(message)) }
                    _messages.tryEmit(message)
                }
            } finally {
                _state.update { it.copy(busyAction = null) }
            }
        }
    }

    fun cancelLmgBackup() {
        _state.update { it.copy(lmgBackup = LoadState.Idle) }
    }

    fun saveLmgBackup(uri: Uri) {
        val backup = (_state.value.lmgBackup as? LoadState.Ready)?.value ?: return
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val output = getApplication<Application>().contentResolver.openOutputStream(uri)
                        ?: throw ApiFailure.BadRequest("Не удалось открыть выбранный файл")
                    output.use { it.write(backup.bytes) }
                }
                _state.update { it.copy(lmgBackup = LoadState.Idle) }
                _messages.emit("Резервная копия сохранена")
            } catch (failure: ApiFailure) {
                _state.update { it.copy(lmgBackup = LoadState.Error(failure.userMessage())) }
                _messages.emit(failure.userMessage())
            } catch (_: IOException) {
                _state.update { it.copy(lmgBackup = LoadState.Error("Не удалось сохранить резервную копию")) }
                _messages.emit("Не удалось сохранить резервную копию")
            } catch (_: SecurityException) {
                _state.update { it.copy(lmgBackup = LoadState.Error("Не удалось сохранить резервную копию")) }
                _messages.emit("Не удалось сохранить резервную копию")
            }
        }
    }

    fun rotateLmgKey() {
        if (_state.value.busyAction != null) return
        _state.update { it.copy(busyAction = "lmg.rotate-key", lmgRotatedKey = LoadState.Loading) }
        viewModelScope.launch {
            try {
                val newKey = lmgApi.rotateKey(requireKey())
                val saved = withContext(Dispatchers.IO) { keyStore.save(newKey) }
                sessionKey = newKey
                pendingSavedKey = newKey
                _state.update { it.copy(lmgRotatedKey = LoadState.Ready(LmgRotatedKey(newKey, saved))) }
                if (!saved) _messages.emit("Новый ключ не удалось сохранить. Скопируйте его сейчас")
            } catch (failure: ApiFailure) {
                onRequestFailure(failure) { message ->
                    _state.update { it.copy(lmgRotatedKey = LoadState.Error(message)) }
                    _messages.tryEmit(message)
                }
            } finally {
                _state.update { it.copy(busyAction = null) }
            }
        }
    }

    fun clearRotatedLmgKey() {
        _state.update { it.copy(lmgRotatedKey = LoadState.Idle) }
    }

    fun loadDevices(login: String = "", activeOnly: Boolean = false) =
        loadInto({ copy(devices = it) }) { api.getDevices(requireKey(), login, activeOnly) }

    fun loadConfig() = loadInto({ copy(config = it) }) { api.getAppConfig() }
    fun loadConfigHistory() = loadInto({ copy(configHistory = it) }) { api.getConfigHistory(requireKey()) }
    fun loadAnnouncements() = loadInto({ copy(announcements = it) }) { api.getAnnouncements(requireKey()) }

    fun loadMoreConfigHistory() = loadMorePage(
        name = "config.history.more",
        current = { _state.value.configHistory },
        loader = { cursor -> api.getConfigHistory(requireKey(), cursor = cursor) },
        reducer = { copy(configHistory = it) },
    )

    fun loadMoreAnnouncements() = loadMorePage(
        name = "announcements.more",
        current = { _state.value.announcements },
        loader = { cursor -> api.getAnnouncements(requireKey(), cursor = cursor) },
        reducer = { copy(announcements = it) },
    )

    fun loadAnnouncementDetails(id: String) =
        loadInto({ copy(announcementDetails = it) }) { api.getAnnouncement(requireKey(), id) }

    fun loadConfigRevision(revision: Int) =
        loadInto({ copy(configRevisionDetails = it) }) { api.getConfigRevision(requireKey(), revision) }

    fun loadOperations() {
        loadStats()
        loadHealth()
        loadMaintenance()
        loadReleases()
        loadAudit()
        loadErrors()
    }

    fun loadMaintenance() = loadInto({ copy(maintenance = it) }) { api.getMaintenance(requireKey()) }
    fun loadReleases() = loadInto({ copy(releases = it) }) { api.getReleases(requireKey()) }
    fun loadAudit() = loadInto({ copy(audit = it) }) { api.getAudit(requireKey()) }
    fun loadErrors(service: String = "") = loadInto({ copy(errors = it) }) { api.getErrors(requireKey(), service) }

    fun loadMoreReleases() = loadMorePage(
        name = "releases.more",
        current = { _state.value.releases },
        loader = { cursor -> api.getReleases(requireKey(), cursor = cursor) },
        reducer = { copy(releases = it) },
    )

    fun loadMoreAudit() = loadMorePage(
        name = "audit.more",
        current = { _state.value.audit },
        loader = { cursor -> api.getAudit(requireKey(), cursor = cursor) },
        reducer = { copy(audit = it) },
    )

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
                clearConfigDraft()
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
                clearAnnouncementDraft()
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
        if (!ConfigValidator.isValidVersion(release.version)) throw ApiFailure.BadRequest("Версия должна быть в формате x.y.z")
        if (release.sha256.isNotBlank() && !Regex("^[0-9a-fA-F]{64}$").matches(release.sha256)) {
            throw ApiFailure.BadRequest("SHA-256 должен содержать 64 шестнадцатеричных символа")
        }
        api.saveRelease(requireKey(), release)
        clearReleaseDraft()
        loadReleases(); loadAudit()
        "Релиз ${release.version} сохранён"
    }

    fun checkReleaseReadiness(release: ReleaseRecord) {
        if (_state.value.busyAction != null) return
        _state.update { it.copy(busyAction = "release.readiness.${release.version}", releaseReadiness = LoadState.Loading) }
        viewModelScope.launch {
            try {
                if (!ConfigValidator.isValidVersion(release.version)) {
                    throw ApiFailure.BadRequest("Версия должна быть в формате x.y.z")
                }
                val health = api.getHealth(requireKey())
                val devices = api.getDevices(requireKey())
                val verification = apkVerifier.verify(release.url.trim(), release.sha256.trim())
                val blockedClients = if (release.mandatory) {
                    devices.devices.sumOf { group ->
                        group.devices.count { device -> compareVersions(device.appVersion, release.version) < 0 }
                    }
                } else {
                    0
                }
                val readiness = ReleaseReadiness(
                    release = release,
                    serverAvailable = health.status.equals("ok", true) && health.database.equals("ok", true),
                    firebaseAvailable = health.firebase.equals("ok", true),
                    versionValid = true,
                    apkSpecified = release.url.isNotBlank(),
                    shaSpecified = release.sha256.isNotBlank(),
                    apkVerification = verification,
                    blockedClients = blockedClients,
                )
                _state.update { it.copy(releaseReadiness = LoadState.Ready(readiness)) }
                if (!readiness.ready) _messages.emit("Релиз не прошёл проверку готовности")
            } catch (failure: ApiFailure) {
                onRequestFailure(failure) { message ->
                    _state.update { it.copy(releaseReadiness = LoadState.Error(message)) }
                    _messages.tryEmit(message)
                }
            } finally {
                _state.update { it.copy(busyAction = null) }
            }
        }
    }

    fun clearReleaseReadiness() {
        _state.update { it.copy(releaseReadiness = LoadState.Idle) }
    }

    fun publishRelease(version: String) = action("release.publish.$version") {
        val readiness = (_state.value.releaseReadiness as? LoadState.Ready)?.value
        if (readiness == null || readiness.release.version != version || !readiness.ready) {
            throw ApiFailure.BadRequest("Сначала выполните успешную проверку готовности релиза")
        }
        api.publishRelease(requireKey(), version)
        clearReleaseReadiness()
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
                pendingSavedKey = key
                if (!persisted) withContext(Dispatchers.IO) { keyStore.save(key) }
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

    private fun <T> loadMorePage(
        name: String,
        current: () -> LoadState<Page<T>>,
        loader: suspend (String) -> Page<T>,
        reducer: AdminUiState.(LoadState<Page<T>>) -> AdminUiState,
    ) {
        if (_state.value.busyAction != null) return
        val ready = current() as? LoadState.Ready ?: return
        val cursor = ready.value.nextCursor ?: return
        _state.update { it.copy(busyAction = name) }
        viewModelScope.launch {
            try {
                val next = loader(cursor)
                val merged = Page(
                    items = (ready.value.items + next.items).distinct(),
                    nextCursor = next.nextCursor,
                )
                _state.update { it.reducer(LoadState.Ready(merged)) }
            } catch (failure: ApiFailure) {
                onRequestFailure(failure) { _messages.tryEmit(it) }
            } finally {
                _state.update { it.copy(busyAction = null) }
            }
        }
    }

    private fun restartAutoRefresh() {
        autoRefreshJob?.cancel()
        val seconds = _state.value.autoRefreshSeconds
        if (seconds <= 0) return
        autoRefreshJob = viewModelScope.launch {
            while (true) {
                delay(seconds * 1_000L)
                val current = _state.value
                if (current.auth == AuthState.Unlocked && current.backend == Backend.GsGit && current.section == Section.Dashboard) {
                    loadDashboardSilently()
                }
            }
        }
    }

    private fun loadDashboardSilently() {
        val key = sessionKey ?: return
        val period = _state.value.metricsPeriod
        viewModelScope.launch {
            try {
                val stats = api.getStats(key)
                val health = api.getHealth(key)
                val metrics = api.getMetrics(key, period)
                _state.update {
                    it.copy(
                        stats = LoadState.Ready(stats),
                        health = LoadState.Ready(health),
                        metrics = LoadState.Ready(metrics),
                    )
                }
            } catch (failure: ApiFailure) {
                onRequestFailure(failure) { _messages.tryEmit("Автообновление: $it") }
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
            _state.update { it.copy(auth = AuthState.Locked("Неверный admin-key")) }
        } else {
            keepScreen(failure.userMessage())
        }
    }

    private fun ApiFailure.userMessage(): String = when (this) {
        is ApiFailure.Unauthorized -> "Неверный admin-key"
        is ApiFailure.BadRequest -> message ?: "Некорректный запрос"
        is ApiFailure.NotFound -> message ?: "Объект не найден"
        is ApiFailure.Conflict -> message ?: "Операция недоступна"
        is ApiFailure.Upstream -> message ?: "Внешний сервис отклонил запрос"
        is ApiFailure.Unreachable -> "Сервер недоступен"
        is ApiFailure.Server -> "Ошибка сервера ($status)"
        is ApiFailure.InvalidResponse -> "Сервер вернул некорректный ответ"
    }
}

private fun compareVersions(left: String, right: String): Int {
    fun parts(value: String) = value.split('.').map { it.toIntOrNull() ?: 0 }
    val a = parts(left)
    val b = parts(right)
    val size = maxOf(a.size, b.size, 3)
    for (index in 0 until size) {
        val comparison = (a.getOrElse(index) { 0 }).compareTo(b.getOrElse(index) { 0 })
        if (comparison != 0) return comparison
    }
    return 0
}
