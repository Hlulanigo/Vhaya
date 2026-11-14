package com.wayfare.ride.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.wayfare.ride.WayfareApplication
import com.wayfare.ride.domain.AuthState
import com.wayfare.ride.domain.LaunchPolicy
import com.wayfare.ride.domain.RideRole
import com.wayfare.ride.domain.RideStatus
import com.wayfare.ride.domain.RideTrip
import com.wayfare.ride.notifications.RideNotificationHelper
import com.wayfare.ride.notifications.RiderNotificationSpec
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

class VhayaLiveSyncService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var monitorJob: Job? = null
    private var currentMode: String = MODE_DRIVER_ONLINE

    private var lastNotifiedDriverTripId: String? = null
    private var trackedRiderTripId: String? = null
    private var lastRiderStatus: RideStatus? = null
    private var lastRiderOfferCount: Int = 0
    private var riderCardPostedOnce: Boolean = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopMonitorService()
                return START_NOT_STICKY
            }
            ACTION_START -> {
                val requestedMode = intent.getStringExtra(EXTRA_MODE) ?: MODE_DRIVER_ONLINE
                currentMode = requestedMode
                // Always satisfy Android's startForegroundService() contract first to prevent ForegroundServiceDidNotStartInTimeException
                if (!startInitialForeground(requestedMode)) {
                    stopMonitorService()
                    return START_NOT_STICKY
                }
                val auth = (applicationContext as? WayfareApplication)?.repository?.authState?.value
                if (auth !is AuthState.Authenticated) {
                    stopMonitorService()
                    return START_NOT_STICKY
                }
                startMonitoringLoop()
            }
            else -> {
                stopMonitorService()
            }
        }
        return START_NOT_STICKY
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        stopMonitorService()
    }

    private fun startInitialForeground(mode: String): Boolean {
        val notification = if (mode == MODE_RIDER_ACTIVE) {
            RideNotificationHelper.buildRiderStatusNotification(
                context = this,
                spec = RiderNotificationSpec(
                    notificationId = RideNotificationHelper.RIDER_STATUS_NOTIF_ID,
                    title = "Tracking your Vhaya ride…",
                    shortText = "Live status updates active",
                    bigText = "Your ride progress card will update automatically as drivers respond.",
                    progressStep = 1,
                    progressMax = 5,
                    ongoing = true,
                    alertUser = false
                )
            )
        } else {
            RideNotificationHelper.buildDriverDutyNotification(this, hasActiveTrip = false)
        }
        val notifId = if (mode == MODE_RIDER_ACTIVE) {
            RideNotificationHelper.RIDER_STATUS_NOTIF_ID
        } else {
            RideNotificationHelper.DRIVER_DUTY_NOTIF_ID
        }
        return promoteForeground(notifId, notification)
    }

    private fun promoteForeground(notificationId: Int, notification: android.app.Notification): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(notificationId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            } else {
                startForeground(notificationId, notification)
            }
            true
        } catch (_: Throwable) {
            false
        }
    }

    private fun startMonitoringLoop() {
        if (monitorJob?.isActive == true) return
        _isRunning.value = true
        val repo = (applicationContext as? WayfareApplication)?.repository ?: run {
            stopMonitorService()
            return
        }

        monitorJob = serviceScope.launch {
            while (isActive) {
                val auth = repo.authState.value as? AuthState.Authenticated
                if (auth == null) {
                    stopMonitorService()
                    break
                }

                // Avoid duplicate network calls while UI ViewModel polling is already running in foreground
                if (!repo.isAppInForeground.value) {
                    repo.refreshActiveSessionData()
                }

                val nowMs = System.currentTimeMillis()
                val trips = repo.getCachedTripsSnapshot()
                val role = auth.session.role
                val userId = auth.session.userId

                when (role) {
                    RideRole.DRIVER -> {
                        val driverApp = repo.getCachedDriverProfileSnapshot()
                        val activeTrip = trips.firstOrNull { it.driverId == userId && it.status.isActive }
                        val isOnline = driverApp?.isOnline == true

                        if (!isOnline && activeTrip == null) {
                            RideNotificationHelper.dismissDriverOfferNotification(this@VhayaLiveSyncService)
                            stopMonitorService()
                            break
                        }

                        if (currentMode != MODE_DRIVER_ONLINE) {
                            currentMode = MODE_DRIVER_ONLINE
                        }
                        val dutyNotification = RideNotificationHelper.buildDriverDutyNotification(
                            context = this@VhayaLiveSyncService,
                            hasActiveTrip = activeTrip != null
                        )
                        promoteForeground(RideNotificationHelper.DRIVER_DUTY_NOTIF_ID, dutyNotification)

                        if (activeTrip != null || repo.isAppInForeground.value) {
                            if (lastNotifiedDriverTripId != null) {
                                RideNotificationHelper.dismissDriverOfferNotification(this@VhayaLiveSyncService)
                                lastNotifiedDriverTripId = null
                            }
                        } else {
                            val nextOffer = RideNotificationHelper.pickNextEligibleOffer(
                                trips = trips,
                                declinedIds = repo.declinedDriverTripIds.value,
                                nowMs = nowMs
                            )
                            if (nextOffer != null) {
                                if (lastNotifiedDriverTripId != nextOffer.id) {
                                    val posted = RideNotificationHelper.showDriverOfferNotificationIfBackground(
                                        context = this@VhayaLiveSyncService,
                                        trip = nextOffer,
                                        isAppInForeground = repo.isAppInForeground.value
                                    )
                                    if (posted) {
                                        lastNotifiedDriverTripId = nextOffer.id
                                    }
                                }
                            } else if (lastNotifiedDriverTripId != null) {
                                RideNotificationHelper.dismissDriverOfferNotification(this@VhayaLiveSyncService)
                                lastNotifiedDriverTripId = null
                            }
                        }
                    }

                    RideRole.RIDER -> {
                        val activeTrip = findActiveRiderTrip(trips, userId)
                        if (activeTrip != null) {
                            if (LaunchPolicy.isTripExpired(activeTrip, nowMs)) {
                                if (!repo.isAppInForeground.value) {
                                    repo.expireOpenTrip(activeTrip.id)
                                }
                            } else {
                                trackedRiderTripId = activeTrip.id
                                val isFirst = !riderCardPostedOnce
                                val notif = RideNotificationHelper.showOrUpdateRiderStatusNotification(
                                    context = this@VhayaLiveSyncService,
                                    trip = activeTrip,
                                    previousStatus = lastRiderStatus,
                                    previousOfferCount = lastRiderOfferCount,
                                    isFirstNotification = isFirst
                                )
                                promoteForeground(RideNotificationHelper.RIDER_STATUS_NOTIF_ID, notif)
                                riderCardPostedOnce = true
                                lastRiderStatus = activeTrip.status
                                lastRiderOfferCount = activeTrip.offers.count { it.status.equals("pending", true) }
                            }
                        } else {
                            val finished = trackedRiderTripId?.let { id -> trips.firstOrNull { it.id == id } }
                            stopForeground(STOP_FOREGROUND_REMOVE)
                            if (finished != null && !finished.status.isActive &&
                                SupportNotificationHelper.hasNotificationPermission(this@VhayaLiveSyncService)
                            ) {
                                val terminalSpec = RideNotificationHelper.buildRiderStatusSpec(
                                    trip = finished,
                                    isFirstNotification = false,
                                    previousStatus = lastRiderStatus,
                                    previousOfferCount = lastRiderOfferCount
                                )
                                val terminalNotif = RideNotificationHelper.buildRiderStatusNotification(
                                    this@VhayaLiveSyncService,
                                    terminalSpec
                                )
                                runCatching {
                                    NotificationManagerCompat.from(this@VhayaLiveSyncService)
                                        .notify(RideNotificationHelper.RIDER_STATUS_NOTIF_ID, terminalNotif)
                                }
                            }
                            trackedRiderTripId = null
                            riderCardPostedOnce = false
                            lastRiderStatus = null
                            lastRiderOfferCount = 0
                            _isRunning.value = false
                            stopSelf()
                            break
                        }
                    }

                    RideRole.ADMIN -> {
                        stopMonitorService()
                        break
                    }
                }

                delay(POLL_INTERVAL_MS)
            }
        }
    }

    private fun findActiveRiderTrip(trips: List<RideTrip>, userId: String): RideTrip? {
        return trips.firstOrNull { trip ->
            trip.riderId == userId && trip.status.isActive
        }
    }

    private fun stopMonitorService() {
        monitorJob?.cancel()
        monitorJob = null
        _isRunning.value = false
        lastNotifiedDriverTripId = null
        trackedRiderTripId = null
        riderCardPostedOnce = false
        lastRiderStatus = null
        lastRiderOfferCount = 0
        runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
        stopSelf()
    }

    override fun onDestroy() {
        monitorJob?.cancel()
        _isRunning.value = false
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val POLL_INTERVAL_MS = 5_000L
        const val ACTION_START = "com.wayfare.ride.action.START_LIVE_SYNC"
        const val ACTION_STOP = "com.wayfare.ride.action.STOP_LIVE_SYNC"
        const val EXTRA_MODE = "extra_live_sync_mode"
        const val MODE_DRIVER_ONLINE = "driver_online"
        const val MODE_RIDER_ACTIVE = "rider_active"

        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

        fun startForDriverOnline(context: Context) {
            startWithMode(context, MODE_DRIVER_ONLINE)
        }

        fun startForRiderActiveTrip(context: Context) {
            startWithMode(context, MODE_RIDER_ACTIVE)
        }

        private fun startWithMode(context: Context, mode: String) {
            val intent = Intent(context, VhayaLiveSyncService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_MODE, mode)
            }
            runCatching {
                ContextCompat.startForegroundService(context, intent)
            }
        }

        fun stop(context: Context) {
            if (!_isRunning.value) return
            _isRunning.value = false
            runCatching {
                context.stopService(Intent(context, VhayaLiveSyncService::class.java))
            }
        }
    }
}
