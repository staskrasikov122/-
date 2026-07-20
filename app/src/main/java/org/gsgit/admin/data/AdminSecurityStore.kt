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

    companion object {
        val ALLOWED_TIMEOUTS = setOf(1, 5, 15)
        private const val FILE_NAME = "gsgit_admin_security"
        private const val KEY_BIOMETRIC = "biometric_enabled"
        private const val KEY_TIMEOUT = "lock_timeout_minutes"
    }
}
