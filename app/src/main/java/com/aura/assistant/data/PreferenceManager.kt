package com.aura.assistant.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class PreferenceManager(context: Context) {

    private val sharedPreferences: SharedPreferences = try {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        EncryptedSharedPreferences.create(
            context,
            PREFS_FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        // Fallback to standard private mode if Keystore unavailable
        context.getSharedPreferences(PREFS_FILE_NAME, Context.MODE_PRIVATE)
    }

    var geminiApiKey: String
        get() = sharedPreferences.getString(KEY_GEMINI_API_KEY, "") ?: ""
        set(value) {
            sharedPreferences.edit().putString(KEY_GEMINI_API_KEY, value.trim()).apply()
        }

    val hasApiKey: Boolean
        get() = geminiApiKey.isNotBlank()

    var isHapticFeedbackEnabled: Boolean
        get() = sharedPreferences.getBoolean(KEY_HAPTIC_ENABLED, true)
        set(value) = sharedPreferences.edit().putBoolean(KEY_HAPTIC_ENABLED, value).apply()

    var autoDismissDelayMs: Long
        get() = sharedPreferences.getLong(KEY_AUTO_DISMISS_DELAY, 3500L)
        set(value) = sharedPreferences.edit().putLong(KEY_AUTO_DISMISS_DELAY, value).apply()

    var geminiModel: String
        get() = sharedPreferences.getString(KEY_GEMINI_MODEL, "gemini-2.0-flash") ?: "gemini-2.0-flash"
        set(value) = sharedPreferences.edit().putString(KEY_GEMINI_MODEL, value).apply()

    companion object {
        private const val PREFS_FILE_NAME = "aura_assistant_secure_prefs"
        private const val KEY_GEMINI_API_KEY = "gemini_api_key"
        private const val KEY_HAPTIC_ENABLED = "haptic_enabled"
        private const val KEY_AUTO_DISMISS_DELAY = "auto_dismiss_delay"
        private const val KEY_GEMINI_MODEL = "gemini_model"

        @Volatile
        private var instance: PreferenceManager? = null

        fun getInstance(context: Context): PreferenceManager {
            return instance ?: synchronized(this) {
                instance ?: PreferenceManager(context.applicationContext).also { instance = it }
            }
        }
    }
}
