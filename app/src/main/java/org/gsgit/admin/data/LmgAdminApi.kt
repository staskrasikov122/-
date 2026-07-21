package org.gsgit.admin.data

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.net.URLEncoder

class LmgAdminApi(private val baseUrl: String = "https://api.gsgit.org") {
    suspend fun getHealth(key: String): LmgHealth = parseObject(get("/admin/lmg/health", key)) { json ->
        LmgHealth(
            serverVersion = json.optString("serverVersion"),
            uptimeMs = json.optLong("uptimeMs"),
            users = json.optInt("users"),
            partnerKeySet = json.optBoolean("partnerKeySet"),
            icmOrigin = json.optString("icmOrigin"),
            telegramHmac = json.optBoolean("telegramHmac"),
            icmUpstream = json.optString("icmUpstream", "down"),
            serverTime = json.optString("serverTime"),
        )
    }

    suspend fun getMetrics(key: String, period: String): LmgMetrics = parseObject(
        get("/admin/lmg/metrics?period=${query(period)}", key),
    ) { json ->
        LmgMetrics(
            req = json.optLong("req"),
            sessionIssued = json.optLong("sessionIssued"),
            authFail = json.optLong("authFail"),
            rateLimited = json.optLong("rateLimited"),
            icm = json.optLong("icm"),
            icm4xx = json.optLong("icm4xx"),
            icm5xx = json.optLong("icm5xx"),
            icmFail = json.optLong("icmFail"),
        )
    }

    suspend fun getUsers(key: String): LmgUsersResponse = parseObject(get("/admin/lmg/users", key)) { root ->
        val items = root.optJSONArray("items") ?: JSONArray()
        val users = buildList {
            for (index in 0 until items.length()) add(parseUser(items.getJSONObject(index)))
        }
        LmgUsersResponse(root.optInt("count", users.size), users)
    }

    suspend fun getUser(key: String, partnerUserId: String): LmgUser = parseObject(
        get("/admin/lmg/users/${segment(partnerUserId)}", key),
    ) { root -> parseUser(root.optJSONObject("user") ?: root) }

    suspend fun setPremium(
        key: String,
        partnerUserId: String,
        premium: Boolean,
        until: Long = 0,
    ): LmgUser {
        val body = JSONObject().put("premium", premium)
        if (premium) body.put("plan", "premium").put("until", until)
        return parseObject(
            post("/admin/lmg/users/${segment(partnerUserId)}/premium", key, body),
        ) { root -> parseUser(root.optJSONObject("user") ?: root) }
    }

    suspend fun deleteUser(key: String, partnerUserId: String) {
        parseObject(delete("/admin/lmg/users/${segment(partnerUserId)}", key)) { root ->
            if (!root.optBoolean("ok", false)) throw ApiFailure.InvalidResponse()
        }
    }

    suspend fun getDevices(key: String): LmgDevicesResponse = parseObject(get("/admin/lmg/devices", key)) { root ->
        val items = root.optJSONArray("items") ?: JSONArray()
        val devices = buildList {
            for (index in 0 until items.length()) {
                val item = items.getJSONObject(index)
                add(
                    LmgDevice(
                        deviceId = item.optString("deviceId"),
                        partnerUserId = item.optString("partner_user_id"),
                        name = item.optString("name"),
                        platform = item.optString("platform"),
                        appVersion = item.optString("appVersion"),
                        firstSeen = item.optLong("firstSeen"),
                        lastSeen = item.optLong("lastSeen"),
                    ),
                )
            }
        }
        LmgDevicesResponse(root.optInt("count", devices.size), devices)
    }

    suspend fun getConfig(key: String): LmgConfig = parseObject(get("/admin/lmg/config", key), ::parseConfig)

    suspend fun updateConfig(key: String, previous: LmgConfig, updated: LmgConfig): LmgConfig {
        val body = JSONObject()
        if (previous.maintenance != updated.maintenance) body.put("maintenance", updated.maintenance)
        if (previous.minVersion != updated.minVersion) body.put("minVersion", updated.minVersion)
        if (previous.latestVersion != updated.latestVersion) body.put("latestVersion", updated.latestVersion)
        if (previous.changelog != updated.changelog) body.put("changelog", updated.changelog)
        if (previous.downloadUrl != updated.downloadUrl) body.put("downloadUrl", updated.downloadUrl)
        if (previous.waveEnabled != updated.waveEnabled) body.put("waveEnabled", updated.waveEnabled)
        if (previous.importEnabled != updated.importEnabled) body.put("importEnabled", updated.importEnabled)
        if (body.length() == 0) return previous
        return parseObject(post("/admin/lmg/config", key, body), ::parseConfig)
    }

    suspend fun getErrors(key: String): List<LmgError> = parseObject(get("/admin/lmg/errors", key)) { root ->
        val items = root.optJSONArray("items") ?: JSONArray()
        buildList {
            for (index in 0 until items.length()) {
                val item = items.getJSONObject(index)
                add(
                    LmgError(
                        code = item.optString("code"),
                        message = item.optString("message"),
                        count = item.optLong("count"),
                        firstAt = item.optLong("firstAt"),
                        lastAt = item.optLong("lastAt"),
                    ),
                )
            }
        }
    }

    suspend fun testSession(key: String): LmgSessionTest = parseObject(
        post("/admin/lmg/session/test", key, JSONObject()),
    ) { json ->
        LmgSessionTest(
            upstreamStatus = json.optInt("upstreamStatus"),
            gotToken = json.optBoolean("gotToken"),
        )
    }

    private fun parseUser(json: JSONObject): LmgUser {
        val rawDevices = json.opt("devices")
        val deviceMap = if (rawDevices is JSONObject) parseDeviceMap(rawDevices) else emptyMap()
        return LmgUser(
            partnerUserId = json.optString("partner_user_id"),
            name = json.optString("name"),
            email = json.optionalString("email"),
            tgId = json.optionalString("tgId"),
            isPremium = json.optBoolean("is_premium"),
            plan = json.optString("plan"),
            premiumExpiresAt = json.optLong("premium_expires_at"),
            regions = json.optJSONArray("regions").stringList(),
            localGrant = json.optBoolean("localGrant"),
            devices = when (rawDevices) {
                is Number -> rawDevices.toInt()
                is JSONObject -> rawDevices.length()
                else -> 0
            },
            lastSeenAt = json.optLong("lastSeenAt"),
            icmRegions = json.optJSONArray("icmRegions").stringList(),
            deviceMap = deviceMap,
        )
    }

    private fun parseDeviceMap(json: JSONObject): Map<String, LmgUserDevice> = buildMap {
        val keys = json.keys()
        while (keys.hasNext()) {
            val id = keys.next()
            val device = json.optJSONObject(id) ?: continue
            put(
                id,
                LmgUserDevice(
                    deviceId = device.optString("deviceId", id),
                    platform = device.optString("platform"),
                    appVersion = device.optString("appVersion"),
                    firstSeen = device.optLong("firstSeen"),
                    lastSeen = device.optLong("lastSeen"),
                ),
            )
        }
    }

    private fun parseConfig(json: JSONObject) = LmgConfig(
        maintenance = json.optString("maintenance"),
        minVersion = json.optString("minVersion"),
        latestVersion = json.optString("latestVersion"),
        changelog = json.optString("changelog"),
        downloadUrl = json.optString("downloadUrl"),
        waveEnabled = json.optBoolean("waveEnabled", true),
        importEnabled = json.optBoolean("importEnabled", true),
    )

    private suspend fun get(path: String, key: String) = AdminHttpClient.request(baseUrl, "GET", path, key)
    private suspend fun post(path: String, key: String, body: JSONObject) =
        AdminHttpClient.request(baseUrl, "POST", path, key, body)
    private suspend fun delete(path: String, key: String) = AdminHttpClient.request(baseUrl, "DELETE", path, key)

    private inline fun <T> parseObject(raw: String, parser: (JSONObject) -> T): T = try {
        parser(JSONObject(raw))
    } catch (failure: ApiFailure) {
        throw failure
    } catch (_: JSONException) {
        throw ApiFailure.InvalidResponse()
    }

    private fun JSONObject.optionalString(name: String): String? =
        if (!has(name) || isNull(name)) null else opt(name)?.toString()?.takeIf { it.isNotBlank() }

    private fun JSONArray?.stringList(): List<String> = if (this == null) emptyList() else buildList {
        for (index in 0 until length()) optString(index).takeIf { it.isNotBlank() }?.let(::add)
    }

    private fun segment(value: String) = URLEncoder.encode(value, Charsets.UTF_8.name()).replace("+", "%20")
    private fun query(value: String) = segment(value)
}
