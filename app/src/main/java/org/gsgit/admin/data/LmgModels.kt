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
    val clientLog: Long = 0,
    val bannedHit: Long = 0,
)

data class LmgBrokerStatus(
    val ok: Boolean,
    val version: String,
    val uptimeMs: Long,
    val users: Int,
    val partnerKeySet: Boolean,
)

data class LmgServiceCheck(val name: String, val ok: Boolean, val ms: Long)

data class LmgLatencyStat(val count: Long, val p50: Long, val p95: Long, val max: Long)

data class LmgStatus(
    val broker: LmgBrokerStatus,
    val checks: List<LmgServiceCheck>,
    val latency: Map<String, LmgLatencyStat>,
    val serverTime: String,
)

data class LmgActivity(
    val total: Int,
    val dau: Int,
    val wau: Int,
    val mau: Int,
    val new24h: Int,
    val new7d: Int,
    val premium: Int,
    val banned: Int,
    val versions: Map<String, Int>,
    val countries: Map<String, Int>,
)

data class LmgUserDevice(
    val deviceId: String,
    val platform: String = "",
    val appVersion: String = "",
    val firstSeen: Long = 0,
    val lastSeen: Long = 0,
    val ip: String = "",
    val cc: String = "",
    val country: String = "",
    val city: String = "",
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
    val cc: String = "",
    val country: String = "",
    val city: String = "",
    val banned: Boolean = false,
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
    val ip: String = "",
    val cc: String = "",
    val country: String = "",
    val city: String = "",
)

data class LmgDevicesResponse(val count: Int, val items: List<LmgDevice>)

data class LmgConfig(
    val maintenance: String = "",
    val notice: String = "",
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

data class LmgClientError(
    val key: String,
    val count: Long,
    val firstAt: Long,
    val lastAt: Long,
    val level: String,
    val tag: String,
    val message: String,
    val stack: String,
    val version: String,
    val partnerUserId: String,
    val deviceId: String,
)

data class LmgRateLimit(val ip: String, val hitsLastMin: Int)

data class LmgRotatedKey(val value: String, val securelySaved: Boolean)

data class LmgBackup(val fileName: String, val bytes: ByteArray) {
    override fun equals(other: Any?): Boolean =
        other is LmgBackup && fileName == other.fileName && bytes.contentEquals(other.bytes)

    override fun hashCode(): Int = 31 * fileName.hashCode() + bytes.contentHashCode()
}
