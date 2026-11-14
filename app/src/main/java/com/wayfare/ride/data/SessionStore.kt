package com.wayfare.ride.data

import android.content.Context
import com.wayfare.ride.BuildConfig
import com.wayfare.ride.domain.PendingOfflineAction
import com.wayfare.ride.domain.PrivacyPreferences
import com.wayfare.ride.domain.UserSession
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class SessionStore(
    context: Context,
    private val apiBaseUrlOverride: String? = null
) {
    private val prefs = context.getSharedPreferences("vhaya_session_prefs", Context.MODE_PRIVATE)
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        coerceInputValues = true
    }

    fun loadApiBaseUrl(): String = (apiBaseUrlOverride ?: BuildConfig.VHAYA_API_BASE_URL).trim().trimEnd('/')

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

    fun loadPrivacyPreferences(): PrivacyPreferences {
        val raw = prefs.getString(KEY_PRIVACY_PREFS, null) ?: return PrivacyPreferences()
        return runCatching { json.decodeFromString<PrivacyPreferences>(raw) }.getOrDefault(PrivacyPreferences())
    }

    fun savePrivacyPreferences(privacyPreferences: PrivacyPreferences) {
        prefs.edit().putString(KEY_PRIVACY_PREFS, json.encodeToString(privacyPreferences)).apply()
    }

    fun loadOfflineQueue(): List<PendingOfflineAction> {
        val raw = prefs.getString(KEY_OFFLINE_QUEUE, null) ?: return emptyList()
        return runCatching { json.decodeFromString<List<PendingOfflineAction>>(raw) }.getOrDefault(emptyList())
    }

    fun saveOfflineQueue(items: List<PendingOfflineAction>) {
        if (items.isEmpty()) {
            prefs.edit().remove(KEY_OFFLINE_QUEUE).apply()
        } else {
            prefs.edit().putString(KEY_OFFLINE_QUEUE, json.encodeToString(items)).apply()
        }
    }

    fun hasRequestedLocationPermission(): Boolean = prefs.getBoolean(KEY_LOCATION_REQUESTED, false)

    fun markLocationPermissionRequested() {
        prefs.edit().putBoolean(KEY_LOCATION_REQUESTED, true).apply()
    }

    fun loadDeclinedTripIds(): Set<String> {
        val raw = prefs.getString(KEY_DECLINED_TRIP_IDS, null) ?: return emptySet()
        return runCatching { json.decodeFromString<List<String>>(raw).toSet() }.getOrDefault(emptySet())
    }

    fun saveDeclinedTripIds(ids: Set<String>) {
        if (ids.isEmpty()) {
            prefs.edit().remove(KEY_DECLINED_TRIP_IDS).apply()
        } else {
            val trimmed = ids.toList().takeLast(50)
            prefs.edit().putString(KEY_DECLINED_TRIP_IDS, json.encodeToString(trimmed)).apply()
        }
    }

    fun clearAllUserPreferences() {
        prefs.edit()
            .remove(KEY_USER_SESSION)
            .remove(KEY_PRIVACY_PREFS)
            .remove(KEY_OFFLINE_QUEUE)
            .remove(KEY_DECLINED_TRIP_IDS)
            .apply()
    }

    companion object {
        private const val KEY_USER_SESSION = "user_session_json"
        private const val KEY_THEME_MODE = "app_theme_mode"
        private const val KEY_PRIVACY_PREFS = "privacy_preferences_json"
        private const val KEY_OFFLINE_QUEUE = "offline_action_queue_json"
        private const val KEY_LOCATION_REQUESTED = "location_permission_requested"
        private const val KEY_DECLINED_TRIP_IDS = "declined_driver_trip_ids"
    }
}
