package com.mobileagent.data

import android.content.Context
import android.content.SharedPreferences

object Prefs {
    private const val FILE = "mobileagent_prefs"
    private const val KEY_BASE_URL = "api_base_url"
    private const val KEY_API_KEY = "api_key"
    private const val KEY_MODEL = "model_name"
    private const val KEY_TELEGRAM_TOKEN = "telegram_bot_token"
    private const val KEY_TELEGRAM_ENABLED = "telegram_enabled"
    private const val KEY_MAX_STEPS = "max_steps"
    private const val KEY_STEP_DELAY = "step_delay_ms"
    private const val KEY_CONFIRM_DESTRUCTIVE = "confirm_destructive"
    private const val KEY_TELEGRAM_OFFSET = "telegram_offset"
    private const val KEY_OPENROUTER_KEY = "openrouter_key"
    private const val KEY_OPENROUTER_MODEL = "openrouter_model"
    private const val KEY_NIM_KEY = "nim_key"
    private const val KEY_NIM_MODEL = "nim_model"

    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    }

    var baseUrl: String
        get() = prefs.getString(KEY_BASE_URL, "https://openrouter.ai/api/v1") ?: "https://openrouter.ai/api/v1"
        set(value) = prefs.edit().putString(KEY_BASE_URL, value).apply()

    var apiKey: String
        get() = prefs.getString(KEY_API_KEY, "") ?: ""
        set(value) = prefs.edit().putString(KEY_API_KEY, value).apply()

    var model: String
        get() = prefs.getString(KEY_MODEL, "openai/gpt-oss-120b:free") ?: "openai/gpt-oss-120b:free"
        set(value) = prefs.edit().putString(KEY_MODEL, value).apply()

    var telegramBotToken: String
        get() = prefs.getString(KEY_TELEGRAM_TOKEN, "") ?: ""
        set(value) = prefs.edit().putString(KEY_TELEGRAM_TOKEN, value).apply()

    var telegramEnabled: Boolean
        get() = prefs.getBoolean(KEY_TELEGRAM_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_TELEGRAM_ENABLED, value).apply()

    var maxSteps: Int
        get() = prefs.getInt(KEY_MAX_STEPS, 30)
        set(value) = prefs.edit().putInt(KEY_MAX_STEPS, value).apply()

    var stepDelayMs: Long
        get() = prefs.getLong(KEY_STEP_DELAY, 1500L)
        set(value) = prefs.edit().putLong(KEY_STEP_DELAY, value).apply()

    var confirmDestructive: Boolean
        get() = prefs.getBoolean(KEY_CONFIRM_DESTRUCTIVE, true)
        set(value) = prefs.edit().putBoolean(KEY_CONFIRM_DESTRUCTIVE, value).apply()

    var telegramOffset: Long
        get() = prefs.getLong(KEY_TELEGRAM_OFFSET, 0L)
        set(value) = prefs.edit().putLong(KEY_TELEGRAM_OFFSET, value).apply()

    var openRouterKey: String
        get() = prefs.getString(KEY_OPENROUTER_KEY, "") ?: ""
        set(value) = prefs.edit().putString(KEY_OPENROUTER_KEY, value).apply()

    var openRouterModel: String
        get() = prefs.getString(KEY_OPENROUTER_MODEL, "openai/gpt-oss-120b:free") ?: "openai/gpt-oss-120b:free"
        set(value) = prefs.edit().putString(KEY_OPENROUTER_MODEL, value).apply()

    var nimKey: String
        get() = prefs.getString(KEY_NIM_KEY, "") ?: ""
        set(value) = prefs.edit().putString(KEY_NIM_KEY, value).apply()

    var nimModel: String
        get() = prefs.getString(KEY_NIM_MODEL, "meta/llama-3.1-405b-instruct") ?: "meta/llama-3.1-405b-instruct"
        set(value) = prefs.edit().putString(KEY_NIM_MODEL, value).apply()

    fun hasApiKey(): Boolean = apiKey.isNotBlank()
}