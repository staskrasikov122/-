package org.gsgit.admin.data

object ConfigValidator {
    private val versionPattern = Regex("^\\d+\\.\\d+\\.\\d+$")

    fun validate(config: AppConfig): String? {
        if (!versionPattern.matches(config.latestVersion)) return "Последняя версия должна быть в формате x.y.z"
        if (!versionPattern.matches(config.minVersion)) return "Минимальная версия должна быть в формате x.y.z"
        if (config.downloadUrl.isNotBlank() &&
            !config.downloadUrl.startsWith("https://") &&
            !config.downloadUrl.startsWith("http://")
        ) return "Ссылка на APK должна начинаться с https:// или http://"
        return null
    }
}
