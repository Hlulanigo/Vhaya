package com.wayfare.ride.data

import android.content.Context
import com.wayfare.ride.BuildConfig
import com.wayfare.ride.domain.UserSession
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class SessionStore(context: Context) {
    private val prefs = context.getSharedPreferences("vhaya_session_prefs", Context.MODE_PRIVATE)
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        coerceInputValues = true
    }

    fun loadApiBaseUrl(): String = BuildConfig.VHAYA_API_BASE_URL.trim().trimEnd('/')

    fun loadFirebaseApiKey(): String = BuildConfig.FIREBASE_WEB_API_KEY.trim()

    fun loadSession(): UserSession? {
        val raw = prefs.getString(KEY_USER_SESSION, null) ?: return null
        return runCatching { json.decodeFromString<UserSession>(raw) }.getOrNull()
    }

    fun saveSession(session: UserSession?) {
        if (session == null) {
            prefs.edit().remove(KEY_USER_SESSION).apply()
        } else {
            prefs.edit().putString(KEY_USER_SESSION, json.encodeToString(session)).apply()
        }
    }

    fun loadThemeMode(): com.wayfare.ride.ui.theme.AppThemeMode =
        com.wayfare.ride.ui.theme.AppThemeMode.fromKey(prefs.getString(KEY_THEME_MODE, null))

    fun saveThemeMode(mode: com.wayfare.ride.ui.theme.AppThemeMode) {
        prefs.edit().putString(KEY_THEME_MODE, mode.key).apply()
    }

    companion object {
        private const val KEY_USER_SESSION = "user_session_json"
        private const val KEY_THEME_MODE = "app_theme_mode"
    }
}
