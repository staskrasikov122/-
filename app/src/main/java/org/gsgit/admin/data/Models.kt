package org.gsgit.admin.data

data class AdminStats(
    val logins: Int,
    val devices: Int,
    val quietEnabled: Int,
    val heldPushes: Int,
    val maintenance: String,
    val latestVersion: String,
    val minVersion: String,
)

data class HealthStatus(
    val status: String,
    val uptimeSec: Long,
    val serverVersion: String,
    val database: String,
    val firebase: String,
    val githubWebhooks: String,
    val pushQueue: Int,
    val serverTime: String,
)

data class AdminMetrics(
    val period: String,
    val hours: Int,
    val registrations: Int,
    val activeDevices: Int,
    val pushesSent: Int,
    val pushesFailed: Int,
    val heldPushes: Int,
    val githubEvents: Int,
    val requests: Int,
    val responses4xx: Int,
    val responses5xx: Int,
)

data class QuietHours(val start: Int, val end: Int)

data class AdminDevice(
    val deviceId: String,
    val name: String,
    val appVersion: String,
    val tzOffsetMin: Int,
    val quietHours: QuietHours?,
    val heldCount: Int,
    val pushEnabled: Boolean,
    val registeredAt: String,
    val lastSeenAt: String,
    val lastPushAt: String,
    val lastPushStatus: String,
    val tokenTail: String,
)

data class DeviceGroup(
    val login: String,
    val count: Int,
    val devices: List<AdminDevice>,
)

data class DevicesResponse(
    val logins: Int,
    val devices: List<DeviceGroup>,
) {
    val totalDevices: Int get() = devices.sumOf { it.devices.size }
}

data class AppConfig(
    val maintenanceSoon: String = "",
    val maintenance: String = "",
    val latestVersion: String = "",
    val minVersion: String = "",
    val changelog: String = "",
    val downloadUrl: String = "",
)

data class ConfigValidation(val ok: Boolean, val errors: List<String>)

data class ConfigRevision(
    val revision: Int,
    val changedAt: String,
    val changedFields: List<String>,
    val reason: String,
    val snapshot: AppConfig? = null,
)

data class Announcement(val title: String = "", val body: String = "", val url: String = "")
data class AnnouncementResult(val delivered: Int)

data class AnnouncementRecord(
    val id: String,
    val title: String,
    val body: String,
    val url: String,
    val createdAt: String,
    val targeted: Int,
    val delivered: Int,
    val failed: Int,
    val status: String,
)

data class AnnouncementRetryResult(val delivered: Int, val stillFailed: Int)

data class MaintenanceSchedule(
    val startsAt: String,
    val endsAt: String,
    val message: String,
)

data class MaintenanceState(
    val maintenanceNow: String,
    val schedule: MaintenanceSchedule?,
)

data class ReleaseRecord(
    val version: String,
    val changelog: String = "",
    val url: String = "",
    val sha256: String = "",
    val mandatory: Boolean = false,
    val rollout: Int = 100,
    val createdAt: String = "",
    val publishedAt: String = "",
)

data class ReleaseReadiness(
    val release: ReleaseRecord,
    val serverAvailable: Boolean,
    val firebaseAvailable: Boolean,
    val versionValid: Boolean,
    val apkSpecified: Boolean,
    val shaSpecified: Boolean,
    val apkVerification: ApkVerification?,
    val blockedClients: Int,
) {
    val ready: Boolean
        get() = serverAvailable && firebaseAvailable && versionValid && apkSpecified && shaSpecified &&
            apkVerification?.matches == true
}

data class AuditRecord(
    val id: String,
    val at: String,
    val ip: String,
    val action: String,
    val result: String,
    val meta: String,
)

data class ServerErrorRecord(
    val id: String,
    val service: String,
    val code: String,
    val message: String,
    val count: Int,
    val createdAt: String,
    val lastAt: String,
)

data class Page<T>(val items: List<T>, val nextCursor: String?)

sealed class ApiFailure(message: String) : Exception(message) {
    class Unauthorized : ApiFailure("Неверный ключ")
    class BadRequest(message: String) : ApiFailure(message)
    class NotFound(message: String = "Объект не найден") : ApiFailure(message)
    class Conflict(message: String) : ApiFailure(message)
    class Upstream(message: String = "Внешний сервис отклонил запрос") : ApiFailure(message)
    class Unreachable : ApiFailure("Сервер недоступен")
    class Server(val status: Int) : ApiFailure("Ошибка сервера ($status)")
    class InvalidResponse : ApiFailure("Сервер вернул некорректный ответ")
}
