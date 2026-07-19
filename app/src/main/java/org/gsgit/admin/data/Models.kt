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

data class QuietHours(
    val start: Int,
    val end: Int,
)

data class AdminDevice(
    val name: String,
    val tzOffsetMin: Int,
    val quietHours: QuietHours?,
    val heldCount: Int,
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

data class Announcement(
    val title: String,
    val body: String,
    val url: String,
)

data class AnnouncementResult(val delivered: Int)

sealed class ApiFailure(message: String) : Exception(message) {
    class Unauthorized : ApiFailure("Неверный ключ")
    class BadRequest(message: String) : ApiFailure(message)
    class Unreachable : ApiFailure("Сервер недоступен")
    class Server(val status: Int) : ApiFailure("Ошибка сервера ($status)")
    class InvalidResponse : ApiFailure("Сервер вернул некорректный ответ")
}
