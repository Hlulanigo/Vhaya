package com.wayfare.ride.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.wayfare.ride.MainActivity
import com.wayfare.ride.R
import com.wayfare.ride.domain.SupportCase
import com.wayfare.ride.domain.SupportUpdateAlert

object SupportNotificationHelper {
    const val CHANNEL_SUPPORT = "vhaya_support_updates_v2"
    const val CHANNEL_TRACKING = "vhaya_ride_tracking"

    fun ensureChannels(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val supportChannel = NotificationChannel(
            CHANNEL_SUPPORT,
            "Support & Safety Updates",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Notifications when Vhaya Support replies or updates your ticket status."
            enableVibration(true)
            setSound(soundUri, audioAttributes)
        }
        val trackingChannel = NotificationChannel(
            CHANNEL_TRACKING,
            "Active Ride Location Sharing",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Persistent indicator while sharing live location for an active Vhaya ride."
            setSound(null, null)
        }
        manager.createNotificationChannel(supportChannel)
        manager.createNotificationChannel(trackingChannel)
    }

    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun diffSupportUpdates(
        previous: List<SupportCase>,
        current: List<SupportCase>
    ): List<SupportUpdateAlert> {
        if (previous.isEmpty()) return emptyList()
        val prevById = previous.associateBy { it.id }
        val alerts = mutableListOf<SupportUpdateAlert>()

        for (cur in current) {
            val prev = prevById[cur.id] ?: continue
            if (cur.replies.size > prev.replies.size) {
                val latest = cur.replies.lastOrNull()
                if (latest != null && isStaffSender(latest.senderLabel)) {
                    alerts += SupportUpdateAlert(
                        caseId = cur.id,
                        title = "Vhaya Support replied to #${cur.id}",
                        summary = latest.message,
                        status = cur.status,
                        isStaffReply = true
                    )
                    continue
                }
            }
            if (!cur.status.equals(prev.status, ignoreCase = true)) {
                alerts += SupportUpdateAlert(
                    caseId = cur.id,
                    title = "Ticket #${cur.id} marked ${cur.status.replace("_", " ")}",
                    summary = cur.staffNote?.takeIf { it.isNotBlank() } ?: cur.message,
                    status = cur.status,
                    isStaffReply = false
                )
            } else if (!cur.staffNote.isNullOrBlank() && cur.staffNote != prev.staffNote) {
                alerts += SupportUpdateAlert(
                    caseId = cur.id,
                    title = "Staff update on ticket #${cur.id}",
                    summary = cur.staffNote,
                    status = cur.status,
                    isStaffReply = false
                )
            }
        }
        return alerts
    }

    fun isStaffSender(senderLabel: String): Boolean {
        val lower = senderLabel.lowercase()
        return lower.contains("support") || lower.contains("admin") || lower.contains("staff")
    }

    fun showSupportUpdateNotification(context: Context, alert: SupportUpdateAlert) {
        if (!hasNotificationPermission(context)) return
        ensureChannels(context)

        val deepLinkIntent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("vhaya://support?caseId=${alert.caseId}"),
            context,
            MainActivity::class.java
        ).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            alert.caseId.toInt(),
            deepLinkIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_SUPPORT)
            .setSmallIcon(R.drawable.ic_vhaya_notification)
            .setContentTitle(alert.title)
            .setContentText(alert.summary)
            .setStyle(NotificationCompat.BigTextStyle().bigText(alert.summary))
            .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
            .setDefaults(NotificationCompat.DEFAULT_VIBRATE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(
                (10_000L + (alert.caseId % 1000L)).toInt(),
                notification
            )
        }
    }
}
