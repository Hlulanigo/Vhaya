package com.wayfare.ride.service

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.wayfare.ride.MainActivity
import com.wayfare.ride.R
import com.wayfare.ride.WayfareApplication
import com.wayfare.ride.domain.RideCoordinates
import com.wayfare.ride.notifications.SupportNotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class RideTrackingService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var trackingJob: Job? = null
    private var currentTripId: String? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP_SHARING -> {
                stopTracking(disableConsent = true)
                return START_NOT_STICKY
            }
            ACTION_STOP_TRIP_ENDED -> {
                stopTracking(disableConsent = false)
                return START_NOT_STICKY
            }
            ACTION_START -> {
                val tripId = intent.getStringExtra(EXTRA_TRIP_ID).orEmpty()
                val routeSummary = intent.getStringExtra(EXTRA_ROUTE_SUMMARY).orEmpty().ifBlank { "Active Vhaya ride" }
                if (tripId.isBlank() || !hasLocationPermission(this)) {
                    stopTracking(disableConsent = false)
                    return START_NOT_STICKY
                }
                startForegroundCompat(routeSummary)
                startLocationLoop(tripId)
            }
            else -> {
                stopTracking(disableConsent = false)
            }
        }
        return START_NOT_STICKY
    }

    private fun startForegroundCompat(routeSummary: String) {
        SupportNotificationHelper.ensureChannels(this)
        val openIntent = PendingIntent.getActivity(
            this,
            201,
            Intent(Intent.ACTION_VIEW, Uri.parse("vhaya://ride"), this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            this,
            202,
            Intent(this, RideTrackingService::class.java).apply { action = ACTION_STOP_SHARING },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, SupportNotificationHelper.CHANNEL_TRACKING)
            .setSmallIcon(R.drawable.ic_vhaya_notification)
            .setContentTitle(getString(R.string.live_tracking_active))
            .setContentText(routeSummary)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openIntent)
            .addAction(0, getString(R.string.live_tracking_stop), stopIntent)
            .build()

        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        }.onFailure {
            stopTracking(disableConsent = false)
        }
    }

    private fun startLocationLoop(tripId: String) {
        if (currentTripId == tripId && trackingJob?.isActive == true) return
        trackingJob?.cancel()
        currentTripId = tripId
        _isTrackingActive.value = true
        _activeTrackedTripId.value = tripId

        val repo = (applicationContext as? WayfareApplication)?.repository
        trackingJob = serviceScope.launch {
            while (isActive) {
                val consentOn = repo?.privacyPreferences?.value?.locationSharingConsent ?: true
                if (!consentOn || !hasLocationPermission(this@RideTrackingService)) {
                    stopTracking(disableConsent = false)
                    break
                }
                val coords = resolveLastLocation(this@RideTrackingService)
                if (coords != null && repo != null) {
                    repo.updateLiveDriverLocation(tripId, coords)
                }
                delay(UPDATE_INTERVAL_MS)
            }
        }
    }

    private fun stopTracking(disableConsent: Boolean) {
        trackingJob?.cancel()
        trackingJob = null
        currentTripId = null
        _isTrackingActive.value = false
        _activeTrackedTripId.value = null
        if (disableConsent) {
            val repo = (applicationContext as? WayfareApplication)?.repository
            repo?.let {
                val current = it.privacyPreferences.value
                it.updatePrivacyPreferences(current.copy(locationSharingConsent = false))
            }
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        trackingJob?.cancel()
        _isTrackingActive.value = false
        _activeTrackedTripId.value = null
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val NOTIFICATION_ID = 4102
        private const val UPDATE_INTERVAL_MS = 12_000L
        const val ACTION_START = "com.wayfare.ride.action.START_TRACKING"
        const val ACTION_STOP_SHARING = "com.wayfare.ride.action.STOP_SHARING"
        const val ACTION_STOP_TRIP_ENDED = "com.wayfare.ride.action.STOP_TRIP_ENDED"
        const val EXTRA_TRIP_ID = "extra_trip_id"
        const val EXTRA_ROUTE_SUMMARY = "extra_route_summary"

        private val _isTrackingActive = MutableStateFlow(false)
        val isTrackingActive: StateFlow<Boolean> = _isTrackingActive.asStateFlow()

        private val _activeTrackedTripId = MutableStateFlow<String?>(null)
        val activeTrackedTripId: StateFlow<String?> = _activeTrackedTripId.asStateFlow()

        fun hasLocationPermission(context: Context): Boolean {
            val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
            val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
            return fine || coarse
        }

        fun startForTrip(context: Context, tripId: String, routeSummary: String) {
            if (!hasLocationPermission(context)) return
            val intent = Intent(context, RideTrackingService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_TRIP_ID, tripId)
                putExtra(EXTRA_ROUTE_SUMMARY, routeSummary)
            }
            runCatching {
                ContextCompat.startForegroundService(context, intent)
            }
        }

        fun stopForTripEnd(context: Context) {
            if (!_isTrackingActive.value) return
            _isTrackingActive.value = false
            _activeTrackedTripId.value = null
            runCatching { context.stopService(Intent(context, RideTrackingService::class.java)) }
        }

        fun stopByUserOptOut(context: Context) {
            val repo = (context.applicationContext as? WayfareApplication)?.repository
            repo?.let {
                val current = it.privacyPreferences.value
                it.updatePrivacyPreferences(current.copy(locationSharingConsent = false))
            }
            _isTrackingActive.value = false
            _activeTrackedTripId.value = null
            runCatching { context.stopService(Intent(context, RideTrackingService::class.java)) }
        }

        @SuppressLint("MissingPermission")
        internal fun resolveLastLocation(context: Context): RideCoordinates? {
            if (!hasLocationPermission(context)) return null
            return try {
                val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
                val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
                val loc = providers.firstNotNullOfOrNull { provider ->
                    runCatching { manager.getLastKnownLocation(provider) }.getOrNull()
                } ?: return null
                RideCoordinates(loc.latitude, loc.longitude)
            } catch (_: Throwable) {
                null
            }
        }
    }
}
