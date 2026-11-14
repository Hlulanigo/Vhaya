package com.wayfare.ride.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.wayfare.ride.WayfareApplication
import com.wayfare.ride.service.VhayaLiveSyncService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class RideNotificationActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        val app = context.applicationContext as? WayfareApplication ?: return
        val repo = app.repository
        val pendingResult = goAsync()

        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                when (action) {
                    ACTION_DECLINE_OFFER -> {
                        val tripId = intent.getStringExtra(EXTRA_TRIP_ID).orEmpty()
                        if (tripId.isNotBlank()) {
                            repo.declineDriverRequest(tripId)
                        }
                        RideNotificationHelper.dismissDriverOfferNotification(context)

                        val driverApp = repo.getCachedDriverProfileSnapshot()
                        val trips = repo.getCachedTripsSnapshot()
                        val hasActiveTrip = trips.any {
                            it.driverId == driverApp?.userId && it.status.isActive
                        }
                        if (driverApp?.isOnline == true && !hasActiveTrip && !repo.isAppInForeground.value) {
                            val next = RideNotificationHelper.pickNextEligibleOffer(
                                trips = trips,
                                declinedIds = repo.declinedDriverTripIds.value
                            )
                            if (next != null) {
                                RideNotificationHelper.showDriverOfferNotification(context, next)
                            }
                        }
                    }

                    ACTION_GO_OFFLINE -> {
                        RideNotificationHelper.dismissDriverOfferNotification(context)
                        repo.setDriverOnline(false)
                        VhayaLiveSyncService.stop(context)
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_DECLINE_OFFER = "com.wayfare.ride.action.NOTIF_DECLINE_OFFER"
        const val ACTION_GO_OFFLINE = "com.wayfare.ride.action.NOTIF_GO_OFFLINE"
        const val EXTRA_TRIP_ID = "extra_trip_id"
    }
}
