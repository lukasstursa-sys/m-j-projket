package cz.stursa.speechnotes.ai

import android.content.Context
import android.content.SharedPreferences

/**
 * Manages AI API settings stored in SharedPreferences.
 */
class AiSettingsManager(context: Context) {

    companion object {
        private const val PREFS_NAME = "ai_settings"
        private const val KEY_API_KEY = "api_key"
        private const val KEY_API_URL = "api_url"
        private const val KEY_MODEL = "model"
        private const val DEFAULT_URL = "https://api.openai.com/v1/chat/completions"
        private const val DEFAULT_MODEL = "gpt-3.5-turbo"
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var apiKey: String
        get() = prefs.getString(KEY_API_KEY, "") ?: ""
        set(value) = prefs.edit().putString(KEY_API_KEY, value).apply()

    var apiUrl: String
        get() = prefs.getString(KEY_API_URL, DEFAULT_URL) ?: DEFAULT_URL
        set(value) = prefs.edit().putString(KEY_API_URL, value).apply()

    var model: String
        get() = prefs.getString(KEY_MODEL, DEFAULT_MODEL) ?: DEFAULT_MODEL
        set(value) = prefs.edit().putString(KEY_MODEL, value).apply()

    fun isConfigured(): Boolean = apiKey.isNotEmpty()

    fun createProcessor(): AiTextProcessor {
        return AiTextProcessor(apiKey, apiUrl, model)
    }
}
