package org.gsgit.admin.data

data class LmgHealth(
    val serverVersion: String,
    val uptimeMs: Long,
    val users: Int,
    val partnerKeySet: Boolean,
    val icmOrigin: String,
    val telegramHmac: Boolean,
    val icmUpstream: String,
    val serverTime: String,
)

data class LmgMetrics(
    val req: Long = 0,
    val sessionIssued: Long = 0,
    val authFail: Long = 0,
    val rateLimited: Long = 0,
    val icm: Long = 0,
    val icm4xx: Long = 0,
    val icm5xx: Long = 0,
    val icmFail: Long = 0,
)

data class LmgUserDevice(
    val deviceId: String,
    val platform: String = "",
    val appVersion: String = "",
    val firstSeen: Long = 0,
    val lastSeen: Long = 0,
)

data class LmgUser(
    val partnerUserId: String,
    val name: String,
    val email: String? = null,
    val tgId: String? = null,
    val isPremium: Boolean = false,
    val plan: String = "",
    val premiumExpiresAt: Long = 0,
    val regions: List<String> = emptyList(),
    val localGrant: Boolean = false,
    val devices: Int = 0,
    val lastSeenAt: Long = 0,
    val icmRegions: List<String> = emptyList(),
    val deviceMap: Map<String, LmgUserDevice> = emptyMap(),
)

data class LmgUsersResponse(val count: Int, val items: List<LmgUser>)

data class LmgDevice(
    val deviceId: String,
    val partnerUserId: String,
    val name: String,
    val platform: String,
    val appVersion: String,
    val firstSeen: Long,
    val lastSeen: Long,
)

data class LmgDevicesResponse(val count: Int, val items: List<LmgDevice>)

data class LmgConfig(
    val maintenance: String = "",
    val minVersion: String = "",
    val latestVersion: String = "",
    val changelog: String = "",
    val downloadUrl: String = "",
    val waveEnabled: Boolean = true,
    val importEnabled: Boolean = true,
)

data class LmgError(
    val code: String,
    val message: String,
    val count: Long,
    val firstAt: Long,
    val lastAt: Long,
)

data class LmgSessionTest(val upstreamStatus: Int, val gotToken: Boolean)
