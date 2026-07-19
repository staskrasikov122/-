package org.gsgit.admin.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.URL
import javax.net.ssl.HttpsURLConnection

class AdminApi(
    private val baseUrl: String = "https://api.gsgit.org",
) {
    suspend fun getStats(key: String): AdminStats = parseObject(
        request("GET", "/admin/stats", key),
    ) { json ->
        AdminStats(
            logins = json.requireInt("logins"),
            devices = json.requireInt("devices"),
            quietEnabled = json.requireInt("quietEnabled"),
            heldPushes = json.requireInt("heldPushes"),
            maintenance = json.requireString("maintenance"),
            latestVersion = json.requireString("latestVersion"),
            minVersion = json.requireString("minVersion"),
        )
    }

    suspend fun getDevices(key: String): DevicesResponse = parseObject(
        request("GET", "/admin/devices", key),
    ) { root ->
        val groups = root.requireArray("devices")
        val result = buildList {
            for (groupIndex in 0 until groups.length()) {
                val group = groups.getJSONObject(groupIndex)
                val rawDevices = group.requireArray("devices")
                val devices = buildList {
                    for (deviceIndex in 0 until rawDevices.length()) {
                        val device = rawDevices.getJSONObject(deviceIndex)
                        val quiet = if (device.isNull("quietHours")) {
                            null
                        } else {
                            device.getJSONObject("quietHours").let {
                                QuietHours(
                                    start = it.requireInt("start"),
                                    end = it.requireInt("end"),
                                )
                            }
                        }
                        add(
                            AdminDevice(
                                name = device.requireString("name"),
                                tzOffsetMin = device.requireInt("tzOffsetMin"),
                                quietHours = quiet,
                                heldCount = device.requireInt("heldCount"),
                                tokenTail = device.requireString("tokenTail"),
                            ),
                        )
                    }
                }
                add(
                    DeviceGroup(
                        login = group.requireString("login"),
                        count = group.requireInt("count"),
                        devices = devices,
                    ),
                )
            }
        }
        DevicesResponse(logins = root.requireInt("logins"), devices = result)
    }

    suspend fun getAppConfig(): AppConfig = parseObject(
        request("GET", "/appconfig", key = null),
        ::parseConfig,
    )

    suspend fun saveAppConfig(key: String, config: AppConfig): AppConfig {
        val body = JSONObject()
            .put("maintenanceSoon", config.maintenanceSoon)
            .put("maintenance", config.maintenance)
            .put("latestVersion", config.latestVersion)
            .put("minVersion", config.minVersion)
            .put("changelog", config.changelog)
            .put("downloadUrl", config.downloadUrl)
        return parseObject(request("POST", "/admin/appconfig", key, body), ::parseConfig)
    }

    suspend fun setMaintenance(key: String, message: String): AppConfig {
        val body = JSONObject().put("maintenance", message)
        return parseObject(request("POST", "/admin/appconfig", key, body), ::parseConfig)
    }

    suspend fun announce(key: String, announcement: Announcement): AnnouncementResult {
        val body = JSONObject()
            .put("title", announcement.title)
            .put("body", announcement.body)
        if (announcement.url.isNotBlank()) body.put("url", announcement.url)

        return parseObject(request("POST", "/announce", key, body)) { json ->
            if (!json.optBoolean("ok", false)) throw ApiFailure.InvalidResponse()
            AnnouncementResult(delivered = json.requireInt("delivered"))
        }
    }

    private fun parseConfig(json: JSONObject): AppConfig = AppConfig(
        maintenanceSoon = json.requireString("maintenanceSoon"),
        maintenance = json.requireString("maintenance"),
        latestVersion = json.requireString("latestVersion"),
        minVersion = json.requireString("minVersion"),
        changelog = json.requireString("changelog"),
        downloadUrl = json.requireString("downloadUrl"),
    )

    private suspend fun request(
        method: String,
        path: String,
        key: String?,
        body: JSONObject? = null,
    ): String = withContext(Dispatchers.IO) {
        val connection = (URL(baseUrl + path).openConnection() as HttpsURLConnection).apply {
            requestMethod = method
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            setRequestProperty("Accept", "application/json")
            if (key != null) setRequestProperty("X-Admin-Key", key)
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
        }

        try {
            if (body != null) {
                connection.outputStream.bufferedWriter(Charsets.UTF_8).use { writer ->
                    writer.write(body.toString())
                }
            }

            val status = connection.responseCode
            val response = (if (status in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader(Charsets.UTF_8)
                ?.use { it.readText() }
                .orEmpty()

            when (status) {
                in 200..299 -> response
                400 -> throw ApiFailure.BadRequest(readError(response) ?: "Некорректный запрос")
                401 -> throw ApiFailure.Unauthorized()
                else -> throw ApiFailure.Server(status)
            }
        } catch (failure: ApiFailure) {
            throw failure
        } catch (_: SocketTimeoutException) {
            throw ApiFailure.Unreachable()
        } catch (_: IOException) {
            throw ApiFailure.Unreachable()
        } finally {
            connection.disconnect()
        }
    }

    private fun readError(raw: String): String? = try {
        when (val error = JSONObject(raw).optString("error").takeIf { it.isNotBlank() }) {
            "bad json" -> "Некорректные данные запроса"
            "title and body required" -> "Заголовок и текст обязательны"
            "bad admin key" -> "Неверный ключ"
            else -> error
        }
    } catch (_: JSONException) {
        null
    }

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

    private fun JSONObject.requireArray(name: String) = try {
        getJSONArray(name)
    } catch (_: JSONException) {
        throw ApiFailure.InvalidResponse()
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 10_000
        const val READ_TIMEOUT_MS = 15_000
    }
}
