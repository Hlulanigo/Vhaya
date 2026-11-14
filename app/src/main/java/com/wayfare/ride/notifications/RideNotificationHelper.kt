package com.wayfare.ride.notifications

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import com.wayfare.ride.MainActivity
import com.wayfare.ride.R
import com.wayfare.ride.domain.LaunchPolicy
import com.wayfare.ride.domain.PaymentMethod
import com.wayfare.ride.domain.PaymentStatus
import com.wayfare.ride.domain.RideCoordinates
import com.wayfare.ride.domain.RideStatus
import com.wayfare.ride.domain.RideTrip
import java.util.Locale

data class DriverOfferSpec(
    val tripId: String,
    val title: String,
    val shortText: String,
    val bigText: String,
    val fareLabel: String,
    val metaLabel: String,
    val distanceLabel: String,
    val pickupShort: String,
    val destShort: String,
    val countdownLabel: String
)

data class RiderNotificationSpec(
    val notificationId: Int,
    val title: String,
    val shortText: String,
    val bigText: String,
    val progressStep: Int,
    val progressMax: Int,
    val ongoing: Boolean,
    val alertUser: Boolean
)

object RideNotificationHelper {
    const val CHANNEL_DRIVER_OFFERS = "vhaya_driver_offers_v2"
    const val CHANNEL_RIDER_STATUS = "vhaya_rider_status_v2"
    const val CHANNEL_LIVE_DUTY = "vhaya_live_duty"

    const val DRIVER_DUTY_NOTIF_ID = 4201
    const val DRIVER_OFFER_NOTIF_ID = 4202
    const val RIDER_STATUS_NOTIF_ID = 4203

    private const val VHAYA_EMERALD_ARGB = 0xFF059669.toInt()

    private fun defaultNotificationSoundUri(): Uri? =
        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

    private fun notificationAudioAttributes(): AudioAttributes =
        AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

    fun notificationManager(context: Context): NotificationManager? {
        return context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
    }

    fun ensureChannels(context: Context) {
        SupportNotificationHelper.ensureChannels(context)
        val manager = notificationManager(context) ?: return
        val soundUri = defaultNotificationSoundUri()
        val audioAttributes = notificationAudioAttributes()

        val driverOffersChannel = NotificationChannel(
            CHANNEL_DRIVER_OFFERS,
            "Incoming Ride Requests",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Interactive ride offer cards when you are online in the background."
            enableVibration(true)
            setSound(soundUri, audioAttributes)
        }

        val riderStatusChannel = NotificationChannel(
            CHANNEL_RIDER_STATUS,
            "Live Ride Status",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Real-time ride progress updates while waiting for or riding in a Vhaya."
            enableVibration(true)
            setSound(soundUri, audioAttributes)
        }

        val liveDutyChannel = NotificationChannel(
            CHANNEL_LIVE_DUTY,
            "Background Online Sync",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Keeps Vhaya connected while you are online or on an active trip."
            setSound(null, null)
        }

        manager.createNotificationChannel(driverOffersChannel)
        manager.createNotificationChannel(riderStatusChannel)
        manager.createNotificationChannel(liveDutyChannel)
    }

    fun pickNextEligibleOffer(
        trips: List<RideTrip>,
        declinedIds: Set<String>,
        driverCoords: RideCoordinates? = null,
        nowMs: Long = System.currentTimeMillis()
    ): RideTrip? {
        return trips
            .filter { trip ->
                trip.status.isOpenForDrivers &&
                    trip.driverId == null &&
                    trip.id !in declinedIds &&
                    !LaunchPolicy.isTripExpired(trip, nowMs)
            }
            .maxByOrNull { LaunchPolicy.matchScoreForDriver(it, driverCoords) }
    }

    fun buildDriverOfferSpec(
        trip: RideTrip,
        nowMs: Long = System.currentTimeMillis()
    ): DriverOfferSpec {
        val pickupShort = trip.pickup.substringBefore(" ·").trim().ifBlank { "Pickup" }
        val destShort = trip.destination.substringBefore(" ·").trim().ifBlank { "Destination" }
        val fareLabel = LaunchPolicy.formatZar(trip.fare)
        val distanceMeters = trip.route?.distanceMeters ?: 0
        val distanceLabel = if (distanceMeters > 0) {
            "%.1f km".format(Locale.US, distanceMeters / 1000.0)
        } else {
            "Nearby"
        }
        val distanceSuffix = if (distanceMeters > 0) " · $distanceLabel" else ""
        val remaining = LaunchPolicy.formatCountdownMs(LaunchPolicy.remainingOpenMs(trip, nowMs))
        val metaLabel = "${trip.category.label} · ${trip.paymentMethod.label}"
        val title = "New Ride · $fareLabel$distanceSuffix"
        val shortText = "$pickupShort → $destShort"
        val bigText = buildString {
            appendLine("Pickup: $pickupShort")
            appendLine("Drop-off: $destShort")
            append("Pay: ${trip.paymentMethod.label} · ${trip.category.label} · Expires in $remaining")
        }
        return DriverOfferSpec(
            tripId = trip.id,
            title = title,
            shortText = shortText,
            bigText = bigText,
            fareLabel = fareLabel,
            metaLabel = metaLabel,
            distanceLabel = distanceLabel,
            pickupShort = pickupShort,
            destShort = destShort,
            countdownLabel = remaining
        )
    }

    fun buildRiderStatusSpec(
        trip: RideTrip,
        isFirstNotification: Boolean = false,
        previousStatus: RideStatus? = null,
        previousOfferCount: Int = 0
    ): RiderNotificationSpec {
        val pickupShort = trip.pickup.substringBefore(" ·").trim().ifBlank { "Pickup" }
        val destShort = trip.destination.substringBefore(" ·").trim().ifBlank { "Destination" }
        val driver = trip.driverProfile
        val driverLabel = driver?.name?.takeIf { it.isNotBlank() } ?: "Your driver"
        val vehicleSummary = driver?.let {
            listOf(it.vehicleColor, it.vehicle, it.registration)
                .filter { part -> part.isNotBlank() }
                .joinToString(" · ")
        }.orEmpty()
        val pinSuffix = trip.tripPin?.takeIf { it.isNotBlank() }?.let { "PIN $it" }.orEmpty()
        val pendingOffers = trip.offers.filter { it.status.equals("pending", ignoreCase = true) }
        val lowestOffer = pendingOffers.minByOrNull { it.amount }?.amount

        return when (trip.status) {
            RideStatus.REQUESTED -> RiderNotificationSpec(
                notificationId = RIDER_STATUS_NOTIF_ID,
                title = "Finding a Vhaya driver…",
                shortText = "$pickupShort → $destShort · ${LaunchPolicy.formatZar(trip.fare)}",
                bigText = "Pickup: $pickupShort\nDrop-off: $destShort\nWe'll update this card as soon as a driver responds.",
                progressStep = 1,
                progressMax = 5,
                ongoing = true,
                alertUser = isFirstNotification
            )
            RideStatus.COUNTERED -> {
                val count = pendingOffers.size.coerceAtLeast(1)
                val fareHint = lowestOffer?.let { "From ${LaunchPolicy.formatZar(it)}" } ?: LaunchPolicy.formatZar(trip.fare)
                RiderNotificationSpec(
                    notificationId = RIDER_STATUS_NOTIF_ID,
                    title = "$count driver offer${if (count == 1) "" else "s"} ready",
                    shortText = "$fareHint · Tap to review and confirm",
                    bigText = "$count driver${if (count == 1) " has" else "s have"} offered for $pickupShort → $destShort ($fareHint). Tap to accept.",
                    progressStep = 2,
                    progressMax = 5,
                    ongoing = true,
                    alertUser = isFirstNotification || previousStatus != RideStatus.COUNTERED || pendingOffers.size > previousOfferCount
                )
            }
            RideStatus.ACCEPTED -> {
                val needsCardPayment = trip.paymentMethod == PaymentMethod.CARD &&
                    (trip.paymentStatus == PaymentStatus.NONE ||
                        trip.paymentStatus == PaymentStatus.FAILED)
                RiderNotificationSpec(
                    notificationId = RIDER_STATUS_NOTIF_ID,
                    title = if (needsCardPayment) {
                        "$driverLabel accepted · Pay ${LaunchPolicy.formatZar(trip.fare)}"
                    } else {
                        "$driverLabel is on the way"
                    },
                    shortText = if (needsCardPayment) {
                        listOf("Tap to complete payment", vehicleSummary).filter { it.isNotBlank() }.joinToString(" · ")
                    } else {
                        listOf(vehicleSummary, pinSuffix).filter { it.isNotBlank() }.joinToString(" · ")
                            .ifBlank { "Heading to $pickupShort" }
                    },
                    bigText = buildString {
                        append("$driverLabel accepted your ride to $destShort.")
                        if (needsCardPayment) {
                            append("\nTap to complete ${LaunchPolicy.formatZar(trip.fare)} Paystack payment.")
                        }
                        if (vehicleSummary.isNotBlank()) append("\nVehicle: $vehicleSummary")
                        if (pinSuffix.isNotBlank()) append("\nPickup $pinSuffix")
                    },
                    progressStep = 3,
                    progressMax = 5,
                    ongoing = true,
                    alertUser = isFirstNotification || previousStatus != RideStatus.ACCEPTED
                )
            }
            RideStatus.ARRIVED -> RiderNotificationSpec(
                notificationId = RIDER_STATUS_NOTIF_ID,
                title = if (pinSuffix.isNotBlank()) "$driverLabel arrived · $pinSuffix" else "$driverLabel arrived at pickup",
                shortText = vehicleSummary.ifBlank { "Meet your driver at $pickupShort" },
                bigText = buildString {
                    append("$driverLabel is waiting at $pickupShort.")
                    if (vehicleSummary.isNotBlank()) append("\nVehicle: $vehicleSummary")
                    if (pinSuffix.isNotBlank()) append("\nShare $pinSuffix when boarding.")
                },
                progressStep = 4,
                progressMax = 5,
                ongoing = true,
                alertUser = isFirstNotification || previousStatus != RideStatus.ARRIVED
            )
            RideStatus.IN_PROGRESS -> RiderNotificationSpec(
                notificationId = RIDER_STATUS_NOTIF_ID,
                title = "On the way to $destShort",
                shortText = listOf(driverLabel, LaunchPolicy.formatZar(trip.fare)).joinToString(" · "),
                bigText = "Riding with $driverLabel to $destShort (${LaunchPolicy.formatZar(trip.fare)} via ${trip.paymentMethod.label}).",
                progressStep = 5,
                progressMax = 5,
                ongoing = true,
                alertUser = isFirstNotification || previousStatus != RideStatus.IN_PROGRESS
            )
            RideStatus.COMPLETED -> RiderNotificationSpec(
                notificationId = RIDER_STATUS_NOTIF_ID,
                title = "Trip completed · ${LaunchPolicy.formatZar(trip.fare)}",
                shortText = "Tap to view receipt and rate $driverLabel",
                bigText = "You arrived at $destShort. Fare: ${LaunchPolicy.formatZar(trip.fare)} (${trip.paymentStatus.label}). Tap to rate your driver.",
                progressStep = 0,
                progressMax = 0,
                ongoing = false,
                alertUser = previousStatus != RideStatus.COMPLETED
            )
            RideStatus.CANCELLED -> {
                val holdNote = if (trip.paymentMethod == PaymentMethod.CARD) {
                    "Card authorization hold released."
                } else {
                    "No charge was taken."
                }
                RiderNotificationSpec(
                    notificationId = RIDER_STATUS_NOTIF_ID,
                    title = "Ride cancelled",
                    shortText = "$pickupShort → $destShort · $holdNote",
                    bigText = "Your ride from $pickupShort to $destShort was cancelled. $holdNote",
                    progressStep = 0,
                    progressMax = 0,
                    ongoing = false,
                    alertUser = previousStatus != RideStatus.CANCELLED
                )
            }
            RideStatus.UNFULFILLED -> RiderNotificationSpec(
                notificationId = RIDER_STATUS_NOTIF_ID,
                title = "No driver matched in time",
                shortText = trip.unfulfillmentReason?.takeIf { it.isNotBlank() } ?: "Tap to request another Vhaya",
                bigText = trip.unfulfillmentReason?.takeIf { it.isNotBlank() }
                    ?: "No nearby driver was available for $pickupShort → $destShort. Tap to re-request.",
                progressStep = 0,
                progressMax = 0,
                ongoing = false,
                alertUser = previousStatus != RideStatus.UNFULFILLED
            )
        }
    }

    fun buildDriverDutyNotification(context: Context, hasActiveTrip: Boolean): Notification {
        ensureChannels(context)
        val openAppIntent = PendingIntent.getActivity(
            context,
            4210,
            Intent(Intent.ACTION_VIEW, Uri.parse("vhaya://ride"), context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_LIVE_DUTY)
            .setSmallIcon(R.drawable.ic_vhaya_notification)
            .setColor(VHAYA_EMERALD_ARGB)
            .setContentTitle(context.getString(R.string.notif_driver_online_title))
            .setContentText(
                if (hasActiveTrip) context.getString(R.string.notif_driver_active_body)
                else context.getString(R.string.notif_driver_online_body)
            )
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(openAppIntent)

        if (!hasActiveTrip) {
            val goOfflineIntent = PendingIntent.getBroadcast(
                context,
                4211,
                Intent(context, RideNotificationActionReceiver::class.java).apply {
                    action = RideNotificationActionReceiver.ACTION_GO_OFFLINE
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(0, context.getString(R.string.notif_action_go_offline), goOfflineIntent)
        }

        return builder.build()
    }

    fun buildDriverOfferNotification(context: Context, trip: RideTrip): Notification {
        ensureChannels(context)
        val spec = buildDriverOfferSpec(trip)

        // Android 12+ requires Activity PendingIntent directly to avoid notification trampoline bans
        val acceptActivityIntent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("vhaya://ride?action=accept&tripId=${Uri.encode(trip.id)}"),
            context,
            MainActivity::class.java
        ).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val acceptPendingIntent = PendingIntent.getActivity(
            context,
            ("accept-${trip.id}").hashCode(),
            acceptActivityIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val declineBroadcastIntent = Intent(context, RideNotificationActionReceiver::class.java).apply {
            action = RideNotificationActionReceiver.ACTION_DECLINE_OFFER
            putExtra(RideNotificationActionReceiver.EXTRA_TRIP_ID, trip.id)
        }
        val declinePendingIntent = PendingIntent.getBroadcast(
            context,
            ("decline-${trip.id}").hashCode(),
            declineBroadcastIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val contentIntent = PendingIntent.getActivity(
            context,
            ("open-${trip.id}").hashCode(),
            Intent(Intent.ACTION_VIEW, Uri.parse("vhaya://ride"), context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val collapsedViews = RemoteViews(
            context.packageName,
            R.layout.notification_driver_offer_collapsed
        ).apply {
            setTextViewText(R.id.notif_collapsed_fare, spec.fareLabel)
            setTextViewText(R.id.notif_collapsed_meta, spec.metaLabel)
            setTextViewText(R.id.notif_collapsed_timer, spec.countdownLabel)
            setTextViewText(R.id.notif_collapsed_route, spec.shortText)
        }

        val expandedViews = RemoteViews(
            context.packageName,
            R.layout.notification_driver_offer_expanded
        ).apply {
            setTextViewText(R.id.notif_expanded_meta, spec.metaLabel)
            setTextViewText(R.id.notif_expanded_timer, spec.countdownLabel)
            setTextViewText(R.id.notif_expanded_fare, spec.fareLabel)
            setTextViewText(R.id.notif_expanded_distance, spec.distanceLabel)
            setTextViewText(R.id.notif_expanded_pickup, spec.pickupShort)
            setTextViewText(R.id.notif_expanded_destination, spec.destShort)
            setOnClickPendingIntent(R.id.notif_btn_accept, acceptPendingIntent)
            setOnClickPendingIntent(R.id.notif_btn_decline, declinePendingIntent)
        }

        return NotificationCompat.Builder(context, CHANNEL_DRIVER_OFFERS)
            .setSmallIcon(R.drawable.ic_vhaya_notification)
            .setColor(VHAYA_EMERALD_ARGB)
            .setContentTitle(spec.title)
            .setContentText(spec.shortText)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setCustomContentView(collapsedViews)
            .setCustomBigContentView(expandedViews)
            .setSound(defaultNotificationSoundUri())
            .setDefaults(NotificationCompat.DEFAULT_VIBRATE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .build()
    }

    fun buildRiderStatusNotification(context: Context, spec: RiderNotificationSpec): Notification {
        ensureChannels(context)
        val openRideIntent = PendingIntent.getActivity(
            context,
            4220,
            Intent(Intent.ACTION_VIEW, Uri.parse("vhaya://ride"), context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_RIDER_STATUS)
            .setSmallIcon(R.drawable.ic_vhaya_notification)
            .setColor(VHAYA_EMERALD_ARGB)
            .setContentTitle(spec.title)
            .setContentText(spec.shortText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(spec.bigText))
            .setOngoing(spec.ongoing)
            .setAutoCancel(!spec.ongoing)
            .setOnlyAlertOnce(!spec.alertUser)
            .setSilent(!spec.alertUser)
            .setPriority(if (spec.alertUser) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setContentIntent(openRideIntent)

        if (spec.alertUser) {
            builder.setSound(defaultNotificationSoundUri())
            builder.setDefaults(NotificationCompat.DEFAULT_VIBRATE)
        }

        if (spec.progressMax > 0) {
            builder.setProgress(spec.progressMax, spec.progressStep.coerceIn(0, spec.progressMax), false)
        } else {
            builder.setProgress(0, 0, false)
        }

        return builder.build()
    }

    fun showDriverOfferNotification(context: Context, trip: RideTrip) {
        if (!SupportNotificationHelper.hasNotificationPermission(context)) return
        val manager = notificationManager(context) ?: return
        val notification = buildDriverOfferNotification(context, trip)
        runCatching {
            manager.notify(DRIVER_OFFER_NOTIF_ID, notification)
        }
    }

    fun showDriverOfferNotificationIfBackground(
        context: Context,
        trip: RideTrip,
        isAppInForeground: Boolean
    ): Boolean {
        if (isAppInForeground || !SupportNotificationHelper.hasNotificationPermission(context)) {
            return false
        }
        val manager = notificationManager(context) ?: return false
        val notification = buildDriverOfferNotification(context, trip)
        return runCatching {
            manager.notify(DRIVER_OFFER_NOTIF_ID, notification)
            true
        }.getOrDefault(false)
    }

    fun dismissDriverOfferNotification(context: Context) {
        runCatching {
            notificationManager(context)?.cancel(DRIVER_OFFER_NOTIF_ID)
        }
    }

    fun showOrUpdateRiderStatusNotification(
        context: Context,
        trip: RideTrip,
        previousStatus: RideStatus?,
        previousOfferCount: Int,
        isFirstNotification: Boolean
    ): Notification {
        val spec = buildRiderStatusSpec(
            trip = trip,
            isFirstNotification = isFirstNotification,
            previousStatus = previousStatus,
            previousOfferCount = previousOfferCount
        )
        val notification = buildRiderStatusNotification(context, spec)
        if (SupportNotificationHelper.hasNotificationPermission(context)) {
            runCatching {
                notificationManager(context)?.notify(spec.notificationId, notification)
            }
        }
        return notification
    }

    fun dismissRiderStatusNotification(context: Context) {
        runCatching {
            notificationManager(context)?.cancel(RIDER_STATUS_NOTIF_ID)
        }
    }
}
