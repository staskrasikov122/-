package org.gsgit.admin.data

import android.content.Context
import org.json.JSONObject

/** Локальные черновики. Админ-ключ и другие секреты сюда никогда не записываются. */
class AdminDraftStore(context: Context) {
    private val preferences = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    fun readConfig(): Pair<AppConfig, String>? = read(KEY_CONFIG) { json ->
        AppConfig(
            maintenanceSoon = json.optString("maintenanceSoon"),
            maintenance = json.optString("maintenance"),
            latestVersion = json.optString("latestVersion"),
            minVersion = json.optString("minVersion"),
            changelog = json.optString("changelog"),
            downloadUrl = json.optString("downloadUrl"),
        ) to json.optString("reason")
    }

    fun saveConfig(config: AppConfig, reason: String) = write(
        KEY_CONFIG,
        JSONObject()
            .put("maintenanceSoon", config.maintenanceSoon)
            .put("maintenance", config.maintenance)
            .put("latestVersion", config.latestVersion)
            .put("minVersion", config.minVersion)
            .put("changelog", config.changelog)
            .put("downloadUrl", config.downloadUrl)
            .put("reason", reason),
    )

    fun clearConfig() = clear(KEY_CONFIG)

    fun readAnnouncement(): Announcement? = read(KEY_ANNOUNCEMENT) { json ->
        Announcement(json.optString("title"), json.optString("body"), json.optString("url"))
    }

    fun saveAnnouncement(value: Announcement) = write(
        KEY_ANNOUNCEMENT,
        JSONObject().put("title", value.title).put("body", value.body).put("url", value.url),
    )

    fun clearAnnouncement() = clear(KEY_ANNOUNCEMENT)

    fun readRelease(): ReleaseRecord? = read(KEY_RELEASE) { json ->
        ReleaseRecord(
            version = json.optString("version"),
            changelog = json.optString("changelog"),
            url = json.optString("url"),
            sha256 = json.optString("sha256"),
            mandatory = json.optBoolean("mandatory"),
            rollout = json.optInt("rollout", 100).coerceIn(0, 100),
        )
    }

    fun saveRelease(value: ReleaseRecord) = write(
        KEY_RELEASE,
        JSONObject()
            .put("version", value.version)
            .put("changelog", value.changelog)
            .put("url", value.url)
            .put("sha256", value.sha256)
            .put("mandatory", value.mandatory)
            .put("rollout", value.rollout),
    )

    fun clearRelease() = clear(KEY_RELEASE)

    private fun write(key: String, value: JSONObject) {
        preferences.edit().putString(key, value.toString()).apply()
    }

    private fun clear(key: String) {
        preferences.edit().remove(key).apply()
    }

    private inline fun <T> read(key: String, parser: (JSONObject) -> T): T? =
        preferences.getString(key, null)?.let { raw -> runCatching { parser(JSONObject(raw)) }.getOrNull() }

    private companion object {
        const val FILE_NAME = "gsgit_admin_drafts"
        const val KEY_CONFIG = "config"
        const val KEY_ANNOUNCEMENT = "announcement"
        const val KEY_RELEASE = "release"
    }
}
