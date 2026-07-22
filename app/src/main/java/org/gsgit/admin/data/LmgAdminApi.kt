package org.gsgit.admin.data

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.net.URLEncoder

class LmgAdminApi(private val baseUrl: String = "https://api.gsgit.org") {
    suspend fun getStatus(key: String): LmgStatus = parseObject(get("/admin/lmg/status", key)) { root ->
        val broker = root.optJSONObject("broker") ?: JSONObject()
        val checks = root.optJSONArray("checks") ?: JSONArray()
        LmgStatus(
            broker = LmgBrokerStatus(
                ok = broker.optBoolean("ok"),
                version = broker.optString("version"),
                uptimeMs = broker.optLong("uptimeMs"),
                users = broker.optInt("users"),
                partnerKeySet = broker.optBoolean("partnerKeySet"),
            ),
            checks = buildList {
                for (index in 0 until checks.length()) {
                    val check = checks.getJSONObject(index)
                    add(LmgServiceCheck(check.optString("name"), check.optBoolean("ok"), check.optLong("ms")))
                }
            },
            latency = parseLatency(root.optJSONObject("latency") ?: JSONObject()),
            serverTime = root.optString("serverTime"),
        )
    }

    suspend fun getActivity(key: String): LmgActivity = parseObject(get("/admin/lmg/activity", key)) { root ->
        LmgActivity(
            total = root.optInt("total"),
            dau = root.optInt("dau"),
            wau = root.optInt("wau"),
            mau = root.optInt("mau"),
            new24h = root.optInt("new24h"),
            new7d = root.optInt("new7d"),
            premium = root.optInt("premium"),
            banned = root.optInt("banned"),
            versions = root.optJSONObject("versions").intMap(),
            countries = root.optJSONObject("countries").intMap(),
        )
    }

    suspend fun getLatency(key: String): Map<String, LmgLatencyStat> = parseObject(
        get("/admin/lmg/latency", key),
    ) { root -> parseLatency(root.optJSONObject("latency") ?: root) }

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
            clientLog = json.optLong("clientLog"),
            bannedHit = json.optLong("bannedHit"),
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

    suspend fun setBanned(key: String, partnerUserId: String, banned: Boolean, reason: String = ""): LmgUser {
        val body = JSONObject().put("banned", banned)
        if (banned && reason.isNotBlank()) body.put("reason", reason.trim())
        post("/admin/lmg/users/${segment(partnerUserId)}/ban", key, body)
        return getUser(key, partnerUserId)
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
                        ip = item.optString("ip"),
                        cc = item.optString("cc"),
                        country = item.optString("country"),
                        city = item.optString("city"),
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
        if (previous.notice != updated.notice) body.put("notice", updated.notice)
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

    suspend fun getClientErrors(key: String): List<LmgClientError> = parseObject(
        get("/admin/lmg/client-errors", key),
    ) { root ->
        val items = root.optJSONArray("items") ?: JSONArray()
        buildList {
            for (index in 0 until items.length()) {
                val item = items.getJSONObject(index)
                add(
                    LmgClientError(
                        key = item.optString("key"),
                        count = item.optLong("count"),
                        firstAt = item.optLong("firstAt"),
                        lastAt = item.optLong("lastAt"),
                        level = item.optString("level"),
                        tag = item.optString("tag"),
                        message = item.optString("message"),
                        stack = item.optString("stack"),
                        version = item.optString("version"),
                        partnerUserId = item.optString("pid"),
                        deviceId = item.optString("deviceId"),
                    ),
                )
            }
        }
    }

    suspend fun clearClientErrors(key: String) {
        delete("/admin/lmg/client-errors", key)
    }

    suspend fun downloadBackup(key: String): LmgBackup = AdminHttpClient.download(baseUrl, "/admin/lmg/backup", key)

    suspend fun rotateKey(key: String): String = parseObject(post("/admin/lmg/rotate-key", key, JSONObject())) { root ->
        root.optString("adminKey").takeIf { it.isNotBlank() } ?: throw ApiFailure.InvalidResponse()
    }

    suspend fun getRateLimits(key: String): List<LmgRateLimit> = parseObject(get("/admin/lmg/ratelimits", key)) { root ->
        val items = root.optJSONArray("items") ?: JSONArray()
        buildList {
            for (index in 0 until items.length()) {
                val item = items.getJSONObject(index)
                add(LmgRateLimit(item.optString("ip"), item.optInt("hitsLastMin")))
            }
        }
    }

    suspend fun clearRateLimits(key: String, ip: String? = null) {
        val body = JSONObject()
        ip?.takeIf { it.isNotBlank() }?.let { body.put("ip", it) }
        post("/admin/lmg/ratelimits/clear", key, body)
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
            cc = json.optString("cc"),
            country = json.optString("country"),
            city = json.optString("city"),
            banned = json.optBoolean("banned"),
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
                    ip = device.optString("ip"),
                    cc = device.optString("cc"),
                    country = device.optString("country"),
                    city = device.optString("city"),
                ),
            )
        }
    }

    private fun parseConfig(json: JSONObject) = LmgConfig(
        maintenance = json.optString("maintenance"),
        notice = json.optString("notice"),
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

    private fun JSONObject?.intMap(): Map<String, Int> = if (this == null) emptyMap() else buildMap {
        val names = keys()
        while (names.hasNext()) {
            val name = names.next()
            put(name, optInt(name))
        }
    }

    private fun parseLatency(json: JSONObject): Map<String, LmgLatencyStat> = buildMap {
        val names = json.keys()
        while (names.hasNext()) {
            val name = names.next()
            val item = json.optJSONObject(name) ?: continue
            put(
                name,
                LmgLatencyStat(
                    count = item.optLong("count"),
                    p50 = item.optLong("p50"),
                    p95 = item.optLong("p95"),
                    max = item.optLong("max"),
                ),
            )
        }
    }


    private fun segment(value: String) = URLEncoder.encode(value, Charsets.UTF_8.name()).replace("+", "%20")
    private fun query(value: String) = segment(value)
}
