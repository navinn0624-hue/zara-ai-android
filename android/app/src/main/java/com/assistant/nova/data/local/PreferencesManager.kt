package com.assistant.nova.data.local

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.assistant.nova.domain.model.LanguagePreference

class PreferencesManager(context: Context) {

    private val prefs: SharedPreferences = try {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        EncryptedSharedPreferences.create(
            context,
            SECURE_PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        Log.w(TAG, "EncryptedSharedPreferences initialization failed, falling back to private prefs", e)
        context.getSharedPreferences(FALLBACK_PREFS_NAME, Context.MODE_PRIVATE)
    }

    var geminiApiKey: String
        get() = prefs.getString(KEY_GEMINI_API_KEY, "") ?: ""
        set(value) = prefs.edit().putString(KEY_GEMINI_API_KEY, value.trim()).apply()

    var languagePreference: LanguagePreference
        get() {
            val code = prefs.getString(KEY_LANGUAGE_PREFERENCE, LanguagePreference.AUTO.code) ?: LanguagePreference.AUTO.code
            return LanguagePreference.fromCode(code)
        }
        set(value) = prefs.edit().putString(KEY_LANGUAGE_PREFERENCE, value.code).apply()

    var wakeWordEnabled: Boolean
        get() = prefs.getBoolean(KEY_WAKE_WORD_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_WAKE_WORD_ENABLED, value).apply()

    var ttsSpeechRate: Float
        get() = prefs.getFloat(KEY_TTS_SPEECH_RATE, 1.0f)
        set(value) = prefs.edit().putFloat(KEY_TTS_SPEECH_RATE, value).apply()

    var ttsPitch: Float
        get() = prefs.getFloat(KEY_TTS_PITCH, 1.0f)
        set(value) = prefs.edit().putFloat(KEY_TTS_PITCH, value).apply()

    var autoSpeakResponses: Boolean
        get() = prefs.getBoolean(KEY_AUTO_SPEAK, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_SPEAK, value).apply()

    val isApiKeyConfigured: Boolean
        get() = geminiApiKey.isNotBlank()

    companion object {
        private const val TAG = "PreferencesManager"
        private const val SECURE_PREFS_NAME = "nova_secure_prefs"
        private const val FALLBACK_PREFS_NAME = "nova_settings_prefs"

        private const val KEY_GEMINI_API_KEY = "gemini_api_key"
        private const val KEY_LANGUAGE_PREFERENCE = "language_preference"
        private const val KEY_WAKE_WORD_ENABLED = "wake_word_enabled"
        private const val KEY_TTS_SPEECH_RATE = "tts_speech_rate"
        private const val KEY_TTS_PITCH = "tts_pitch"
        private const val KEY_AUTO_SPEAK = "auto_speak"
    }
}
