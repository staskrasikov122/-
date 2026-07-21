package org.gsgit.admin.data

import android.content.Context

class AdminSecurityStore(context: Context) {
    private val preferences = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    var biometricEnabled: Boolean
        get() = preferences.getBoolean(KEY_BIOMETRIC, true)
        set(value) { preferences.edit().putBoolean(KEY_BIOMETRIC, value).apply() }

    var lockTimeoutMinutes: Int
        get() = preferences.getInt(KEY_TIMEOUT, 5).takeIf { it in ALLOWED_TIMEOUTS } ?: 5
        set(value) {
            require(value in ALLOWED_TIMEOUTS)
            preferences.edit().putInt(KEY_TIMEOUT, value).apply()
        }

    var autoRefreshSeconds: Int
        get() = preferences.getInt(KEY_AUTO_REFRESH, 0).takeIf { it in ALLOWED_REFRESH_INTERVALS } ?: 0
        set(value) {
            require(value in ALLOWED_REFRESH_INTERVALS)
            preferences.edit().putInt(KEY_AUTO_REFRESH, value).apply()
        }

    companion object {
        val ALLOWED_TIMEOUTS = setOf(1, 5, 15)
        val ALLOWED_REFRESH_INTERVALS = setOf(0, 15, 30, 60)
        private const val FILE_NAME = "gsgit_admin_security"
        private const val KEY_BIOMETRIC = "biometric_enabled"
        private const val KEY_TIMEOUT = "lock_timeout_minutes"
        private const val KEY_AUTO_REFRESH = "dashboard_auto_refresh_seconds"
    }
}
