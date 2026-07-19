package org.gsgit.admin.data

object ConfigValidator {
    private val versionPattern = Regex("^\\d+\\.\\d+\\.\\d+$")

    fun validate(config: AppConfig): String? {
        if (!versionPattern.matches(config.latestVersion)) return "Latest version must use x.y.z"
        if (!versionPattern.matches(config.minVersion)) return "Minimum version must use x.y.z"
        if (config.downloadUrl.isNotBlank() &&
            !config.downloadUrl.startsWith("https://") &&
            !config.downloadUrl.startsWith("http://")
        ) return "Download URL must start with https:// or http://"
        return null
    }
}
