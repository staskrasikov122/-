package org.gsgit.admin.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

@Suppress("DEPRECATION")
class AdminKeyStore(private val context: Context) {
    private val preferences: SharedPreferences by lazy {
        runCatching(::createEncryptedPreferences).getOrElse {
            context.deleteSharedPreferences(FILE_NAME)
            createEncryptedPreferences()
        }
    }

    fun read(): String? = preferences.getString(KEY_ADMIN, null)?.takeIf { it.isNotBlank() }

    fun save(key: String): Boolean = preferences.edit().putString(KEY_ADMIN, key).commit()

    fun clear() {
        preferences.edit().remove(KEY_ADMIN).apply()
    }

    private fun createEncryptedPreferences(): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            context,
            FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    private companion object {
        const val FILE_NAME = "gsgit_admin_secure"
        const val KEY_ADMIN = "admin_key"
    }
}
