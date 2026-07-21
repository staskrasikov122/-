package org.gsgit.admin.data

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.net.URLEncoder

class AdminApi(private val baseUrl: String = "https://api.gsgit.org") {
    suspend fun getStats(key: String): AdminStats = parseObject(request("GET", "/admin/stats", key)) { json ->
        AdminStats(
            logins = json.requireInt("logins"), devices = json.requireInt("devices"),
            quietEnabled = json.requireInt("quietEnabled"), heldPushes = json.requireInt("heldPushes"),
            maintenance = json.requireString("maintenance"), latestVersion = json.requireString("latestVersion"),
            minVersion = json.requireString("minVersion"),
        )
    }

    suspend fun getHealth(key: String): HealthStatus = parseObject(request("GET", "/admin/health", key)) { json ->
        HealthStatus(
            status = json.requireString("status"), uptimeSec = json.requireLong("uptimeSec"),
            serverVersion = json.requireString("serverVersion"), database = json.requireString("database"),
            firebase = json.requireString("firebase"), githubWebhooks = json.requireString("githubWebhooks"),
            pushQueue = json.requireInt("pushQueue"), serverTime = json.requireString("serverTime"),
        )
    }

    suspend fun getMetrics(key: String, period: String): AdminMetrics = parseObject(
        request("GET", "/admin/metrics?period=${query(period)}", key),
    ) { json ->
        AdminMetrics(
            period = json.requireString("period"), hours = json.requireInt("hours"),
            registrations = json.requireInt("registrations"), activeDevices = json.requireInt("activeDevices"),
            pushesSent = json.requireInt("pushesSent"), pushesFailed = json.requireInt("pushesFailed"),
            heldPushes = json.requireInt("heldPushes"), githubEvents = json.requireInt("githubEvents"),
            requests = json.requireInt("requests"), responses4xx = json.requireInt("responses4xx"),
            responses5xx = json.requireInt("responses5xx"),
        )
    }

    suspend fun getDevices(key: String, login: String = "", activeOnly: Boolean = false): DevicesResponse {
        val params = buildList {
            if (login.isNotBlank()) add("login=${query(login.trim())}")
            if (activeOnly) add("status=active")
        }
        val suffix = params.takeIf { it.isNotEmpty() }?.joinToString("&", prefix = "?").orEmpty()
        return parseObject(request("GET", "/admin/devices$suffix", key)) { root ->
            val groups = root.requireArray("devices")
            DevicesResponse(
                logins = root.requireInt("logins"),
                devices = buildList {
                    for (index in 0 until groups.length()) {
                        val group = groups.getJSONObject(index)
                        val rawDevices = group.requireArray("devices")
                        add(
                            DeviceGroup(
                                login = group.requireString("login"), count = group.requireInt("count"),
                                devices = buildList {
                                    for (deviceIndex in 0 until rawDevices.length()) {
                                        add(parseDevice(rawDevices.getJSONObject(deviceIndex)))
                                    }
                                },
                            ),
                        )
                    }
                },
            )
        }
    }

    suspend fun getDevice(key: String, deviceId: String): AdminDevice = parseObject(
        request("GET", "/admin/devices/${segment(deviceId)}", key),
    ) { parseDevice(it.optJSONObject("device") ?: it) }

    suspend fun deleteDevice(key: String, deviceId: String) {
        request("DELETE", "/admin/devices/${segment(deviceId)}", key)
    }

    suspend fun clearHeld(key: String, deviceId: String): Int = parseObject(
        request("POST", "/admin/devices/${segment(deviceId)}/clear-held", key, JSONObject()),
    ) { it.requireInt("cleared") }

    suspend fun setPushEnabled(key: String, deviceId: String, enabled: Boolean): Boolean = parseObject(
        request(
            "POST", "/admin/devices/${segment(deviceId)}/disable-push", key,
            JSONObject().put("enabled", enabled),
        ),
    ) { it.requireBoolean("pushEnabled") }

    suspend fun testPush(key: String, deviceId: String, announcement: Announcement): Int {
        val body = JSONObject()
        if (announcement.title.isNotBlank()) body.put("title", announcement.title)
        if (announcement.body.isNotBlank()) body.put("body", announcement.body)
        if (announcement.url.isNotBlank()) body.put("url", announcement.url)
        return parseObject(
            request("POST", "/admin/devices/${segment(deviceId)}/test-push", key, body),
        ) { it.requireInt("delivered") }
    }

    suspend fun getAppConfig(): AppConfig = parseObject(request("GET", "/appconfig", null), ::parseConfig)

    suspend fun validateAppConfig(key: String, config: AppConfig): ConfigValidation = parseObject(
        request("POST", "/admin/appconfig/validate", key, config.toJson()),
    ) { root ->
        ConfigValidation(root.optBoolean("ok", false), root.optJSONArray("errors").stringList())
    }

    suspend fun saveAppConfig(key: String, config: AppConfig, reason: String): AppConfig {
        val body = config.toJson()
        if (reason.isNotBlank()) body.put("reason", reason.trim())
        return parseObject(request("POST", "/admin/appconfig", key, body), ::parseConfig)
    }

    suspend fun setMaintenance(key: String, message: String): AppConfig = parseObject(
        request("POST", "/admin/appconfig", key, JSONObject().put("maintenance", message)), ::parseConfig,
    )

    suspend fun getConfigHistory(key: String, limit: Int = 30, cursor: String? = null): Page<ConfigRevision> = parseObject(
        request("GET", "/admin/appconfig/history?limit=$limit${cursorParam(cursor)}", key),
    ) { root -> parsePage(root) { parseRevision(it) } }

    suspend fun getConfigRevision(key: String, revision: Int): ConfigRevision = parseObject(
        request("GET", "/admin/appconfig/history/$revision", key),
    ) { parseRevision(it) }

    suspend fun rollbackConfig(key: String, revision: Int): AppConfig = parseObject(
        request("POST", "/admin/appconfig/rollback", key, JSONObject().put("revision", revision)), ::parseConfig,
    )

    suspend fun announce(key: String, announcement: Announcement): AnnouncementResult {
        val body = JSONObject().put("title", announcement.title).put("body", announcement.body)
        if (announcement.url.isNotBlank()) body.put("url", announcement.url)
        return parseObject(request("POST", "/announce", key, body)) { json ->
            if (!json.optBoolean("ok", false)) throw ApiFailure.InvalidResponse()
            AnnouncementResult(json.requireInt("delivered"))
        }
    }

    suspend fun getAnnouncements(key: String, limit: Int = 30, cursor: String? = null): Page<AnnouncementRecord> = parseObject(
        request("GET", "/admin/announcements?limit=$limit${cursorParam(cursor)}", key),
    ) { root -> parsePage(root) { parseAnnouncement(it) } }

    suspend fun getAnnouncement(key: String, id: String): AnnouncementRecord = parseObject(
        request("GET", "/admin/announcements/${segment(id)}", key),
    ) { parseAnnouncement(it.optJSONObject("announcement") ?: it) }

    suspend fun retryAnnouncement(key: String, id: String): AnnouncementRetryResult = parseObject(
        request("POST", "/admin/announcements/${segment(id)}/retry", key, JSONObject()),
    ) { AnnouncementRetryResult(it.requireInt("delivered"), it.requireInt("stillFailed")) }

    suspend fun cancelAnnouncement(key: String, id: String) {
        request("POST", "/admin/announcements/${segment(id)}/cancel", key, JSONObject())
    }

    suspend fun getMaintenance(key: String): MaintenanceState = parseObject(
        request("GET", "/admin/maintenance", key),
    ) { root ->
        MaintenanceState(
            maintenanceNow = root.optString("maintenanceNow"),
            schedule = root.optJSONObject("schedule")?.let(::parseSchedule),
        )
    }

    suspend fun scheduleMaintenance(key: String, schedule: MaintenanceSchedule): MaintenanceState = parseObject(
        request(
            "POST", "/admin/maintenance/schedule", key,
            JSONObject().put("startsAt", schedule.startsAt).put("endsAt", schedule.endsAt).put("message", schedule.message),
        ),
    ) { root -> MaintenanceState(root.optString("maintenanceNow"), parseSchedule(root.requireObject("schedule"))) }

    suspend fun deleteMaintenanceSchedule(key: String) {
        request("DELETE", "/admin/maintenance/schedule", key)
    }

    suspend fun stopMaintenance(key: String) {
        request("POST", "/admin/maintenance/stop", key, JSONObject())
    }

    suspend fun getReleases(key: String, limit: Int = 30, cursor: String? = null): Page<ReleaseRecord> = parseObject(
        request("GET", "/admin/releases?limit=$limit${cursorParam(cursor)}", key),
    ) { root -> parsePage(root) { parseRelease(it) } }

    suspend fun saveRelease(key: String, release: ReleaseRecord): ReleaseRecord = parseObject(
        request("POST", "/admin/releases", key, release.toJson()),
    ) { parseRelease(it.optJSONObject("release") ?: it) }

    suspend fun publishRelease(key: String, version: String) {
        request("POST", "/admin/releases/${segment(version)}/publish", key, JSONObject())
    }

    suspend fun getAudit(key: String, limit: Int = 100, cursor: String? = null): Page<AuditRecord> = parseObject(
        request("GET", "/admin/audit?limit=$limit${cursorParam(cursor)}", key),
    ) { root ->
        parsePage(root) {
            AuditRecord(
                id = it.optString("id"), at = it.optString("at"), ip = it.optString("ip"),
                action = it.optString("action"), result = it.optString("result"),
                meta = it.optJSONObject("meta")?.toString(2) ?: it.optString("meta"),
            )
        }
    }

    suspend fun getErrors(key: String, service: String = "", limit: Int = 50): List<ServerErrorRecord> {
        val serviceParam = if (service.isBlank()) "" else "&service=${query(service)}"
        return parseObject(request("GET", "/admin/errors?limit=$limit$serviceParam", key)) { root ->
            val items = root.requireArray("items")
            buildList {
                for (index in 0 until items.length()) {
                    val item = items.getJSONObject(index)
                    add(
                        ServerErrorRecord(
                            id = item.optString("id"), service = item.optString("service"),
                            code = item.optString("code"), message = item.optString("message"),
                            count = item.optInt("count"), createdAt = item.optString("createdAt"),
                            lastAt = item.optString("lastAt"),
                        ),
                    )
                }
            }
        }
    }

    private fun parseDevice(device: JSONObject): AdminDevice {
        val quiet = device.optJSONObject("quietHours")?.let {
            QuietHours(it.requireInt("start"), it.requireInt("end"))
        }
        return AdminDevice(
            deviceId = device.requireString("deviceId"), name = device.requireString("name"),
            appVersion = device.optString("appVersion"), tzOffsetMin = device.requireInt("tzOffsetMin"),
            quietHours = quiet, heldCount = device.requireInt("heldCount"),
            pushEnabled = device.optBoolean("pushEnabled", true), registeredAt = device.optString("registeredAt"),
            lastSeenAt = device.optString("lastSeenAt"), lastPushAt = device.optString("lastPushAt"),
            lastPushStatus = device.optString("lastPushStatus"), tokenTail = device.requireString("tokenTail"),
        )
    }

    private fun parseConfig(json: JSONObject) = AppConfig(
        maintenanceSoon = json.requireString("maintenanceSoon"), maintenance = json.requireString("maintenance"),
        latestVersion = json.requireString("latestVersion"), minVersion = json.requireString("minVersion"),
        changelog = json.requireString("changelog"), downloadUrl = json.requireString("downloadUrl"),
    )

    private fun AppConfig.toJson() = JSONObject()
        .put("maintenanceSoon", maintenanceSoon).put("maintenance", maintenance)
        .put("latestVersion", latestVersion).put("minVersion", minVersion)
        .put("changelog", changelog).put("downloadUrl", downloadUrl)

    private fun parseRevision(json: JSONObject) = ConfigRevision(
        revision = json.requireInt("revision"), changedAt = json.optString("changedAt"),
        changedFields = json.optJSONArray("changedFields").stringList(), reason = json.optString("reason"),
        snapshot = json.optJSONObject("snapshot")?.let(::parseConfig),
    )

    private fun parseAnnouncement(json: JSONObject) = AnnouncementRecord(
        id = json.requireString("id"), title = json.optString("title"), body = json.optString("body"),
        url = json.optString("url"), createdAt = json.optString("createdAt"),
        targeted = json.optInt("targeted"), delivered = json.optInt("delivered"),
        failed = json.optInt("failed"), status = json.optString("status"),
    )

    private fun parseSchedule(json: JSONObject) = MaintenanceSchedule(
        startsAt = json.requireString("startsAt"), endsAt = json.requireString("endsAt"),
        message = json.requireString("message"),
    )

    private fun parseRelease(json: JSONObject) = ReleaseRecord(
        version = json.requireString("version"), changelog = json.optString("changelog"),
        url = json.optString("url"), sha256 = json.optString("sha256"),
        mandatory = json.optBoolean("mandatory"), rollout = json.optInt("rollout", 100),
        createdAt = json.optString("createdAt"), publishedAt = json.optString("publishedAt"),
    )

    private fun ReleaseRecord.toJson() = JSONObject()
        .put("version", version).put("changelog", changelog).put("url", url)
        .put("sha256", sha256).put("mandatory", mandatory).put("rollout", rollout)

    private inline fun <T> parsePage(root: JSONObject, parser: (JSONObject) -> T): Page<T> {
        val array = root.requireArray("items")
        return Page(
            items = buildList { for (index in 0 until array.length()) add(parser(array.getJSONObject(index))) },
            nextCursor = root.optString("nextCursor").takeIf { it.isNotBlank() && it != "null" },
        )
    }

    private suspend fun request(method: String, path: String, key: String?, body: JSONObject? = null): String =
        AdminHttpClient.request(baseUrl, method, path, key, body)

    private inline fun <T> parseObject(raw: String, parser: (JSONObject) -> T): T = try {
        parser(JSONObject(raw))
    } catch (failure: ApiFailure) {
        throw failure
    } catch (_: JSONException) {
        throw ApiFailure.InvalidResponse()
    }

    private fun JSONObject.requireString(name: String): String {
        if (!has(name) || isNull(name)) throw ApiFailure.InvalidResponse()
        return getString(name)
    }

    private fun JSONObject.requireInt(name: String): Int {
        if (!has(name) || isNull(name)) throw ApiFailure.InvalidResponse()
        return getInt(name)
    }

    private fun JSONObject.requireLong(name: String): Long {
        if (!has(name) || isNull(name)) throw ApiFailure.InvalidResponse()
        return getLong(name)
    }

    private fun JSONObject.requireBoolean(name: String): Boolean {
        if (!has(name) || isNull(name)) throw ApiFailure.InvalidResponse()
        return getBoolean(name)
    }

    private fun JSONObject.requireArray(name: String): JSONArray = try {
        getJSONArray(name)
    } catch (_: JSONException) {
        throw ApiFailure.InvalidResponse()
    }

    private fun JSONObject.requireObject(name: String): JSONObject = try {
        getJSONObject(name)
    } catch (_: JSONException) {
        throw ApiFailure.InvalidResponse()
    }

    private fun JSONArray?.stringList(): List<String> = if (this == null) emptyList() else buildList {
        for (index in 0 until length()) add(optString(index))
    }

    private fun segment(value: String) = URLEncoder.encode(value, Charsets.UTF_8.name()).replace("+", "%20")
    private fun query(value: String) = segment(value)
    private fun cursorParam(cursor: String?) = cursor?.takeIf { it.isNotBlank() }?.let { "&cursor=${query(it)}" }.orEmpty()

}
