package com.wayfare.ride.ui.components

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.wayfare.ride.domain.LocationPermissionState
import com.wayfare.ride.domain.RideCoordinates
import com.wayfare.ride.domain.RideTrip
import com.wayfare.ride.domain.TrustedContact
import com.wayfare.ride.ui.theme.LocalWayfareColors

fun evaluateLocationPermissionState(
    context: Context,
    shouldShowRationale: Boolean,
    hasRequestedBefore: Boolean
): LocationPermissionState {
    val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED
    if (fine) return LocationPermissionState.GRANTED_FINE

    val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED
    if (coarse) return LocationPermissionState.GRANTED_COARSE

    if (shouldShowRationale) return LocationPermissionState.DENIED_CAN_RETRY
    if (hasRequestedBefore) return LocationPermissionState.DENIED_PERMANENTLY
    return LocationPermissionState.DENIED_CAN_RETRY
}

fun openAppLocationSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.fromParts("package", context.packageName, null)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { context.startActivity(intent) }
}

fun alertTrustedContacts(
    context: Context,
    trip: RideTrip,
    contacts: List<TrustedContact>,
    currentCoords: RideCoordinates? = trip.liveDriverLocation ?: trip.pickupCoordinates
): Boolean {
    if (contacts.isEmpty()) return false
    val driverInfo = trip.driverProfile?.let { "${it.name} · ${it.vehicleColor} ${it.vehicle} (${it.registration})" }
        ?: "Driver assignment in progress"
    val coordPart = currentCoords?.let {
        "Live map: https://www.openstreetmap.org/?mlat=${it.latitude}&mlon=${it.longitude}#map=16/${it.latitude}/${it.longitude}"
    }.orEmpty()

    val message = buildString {
        appendLine("VHAYA SAFETY ALERT")
        appendLine("Trip: ${trip.pickup.substringBefore(" ·")} -> ${trip.destination.substringBefore(" ·")}")
        appendLine("Status: ${trip.status.label}")
        appendLine("Driver: $driverInfo")
        trip.tripPin?.let { appendLine("Pickup PIN: $it") }
        if (coordPart.isNotBlank()) appendLine(coordPart)
    }.trim()

    val recipients = contacts.joinToString(",") { it.phone.trim() }
    val smsIntent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$recipients")).apply {
        putExtra("sms_body", message)
        if (context !is android.app.Activity) {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
    return if (smsIntent.resolveActivity(context.packageManager) != null) {
        runCatching { context.startActivity(smsIntent) }.isSuccess
    } else {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, message)
        }
        val chooser = Intent.createChooser(shareIntent, "Alert trusted contacts").apply {
            if (context !is android.app.Activity) {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
        runCatching {
            context.startActivity(chooser)
        }.isSuccess
    }
}

@Composable
fun EmergencyActionPanel(
    trip: RideTrip,
    trustedContactsCount: Int,
    onCall112: () -> Unit,
    onAlertContacts: () -> Unit,
    onReportSafety: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = colorScheme.errorContainer.copy(alpha = 0.55f),
        border = BorderStroke(1.dp, colorScheme.error.copy(alpha = 0.35f)),
        modifier = modifier
            .fillMaxWidth()
            .testTag("emergency_action_panel")
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Shield,
                    contentDescription = "Emergency escalation",
                    tint = colorScheme.error,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "Live trip safety · ${trip.status.label}",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = colorScheme.error
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Button(
                    onClick = onCall112,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colorScheme.error,
                        contentColor = colorScheme.onError
                    ),
                    contentPadding = PaddingValues(horizontal = 8.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("emergency_panel_call_112")
                ) {
                    Icon(Icons.Filled.Phone, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("112", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, maxLines = 1)
                }
                OutlinedButton(
                    onClick = onAlertContacts,
                    contentPadding = PaddingValues(horizontal = 8.dp),
                    modifier = Modifier
                        .weight(1.3f)
                        .height(48.dp)
                        .testTag("emergency_panel_alert_contacts")
                ) {
                    Icon(Icons.Filled.NotificationsActive, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = if (trustedContactsCount > 0) "Alert ($trustedContactsCount)" else "Contacts",
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                OutlinedButton(
                    onClick = onReportSafety,
                    contentPadding = PaddingValues(horizontal = 8.dp),
                    modifier = Modifier
                        .weight(1.1f)
                        .height(48.dp)
                        .testTag("emergency_panel_report_safety")
                ) {
                    Icon(Icons.Filled.Flag, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Issue", style = MaterialTheme.typography.labelLarge, maxLines = 1)
                }
            }
        }
    }
}

@Composable
fun LocationRationaleDialog(
    isDriver: Boolean,
    onAllow: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Filled.GpsFixed,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = { Text("Enable location access") },
        text = {
            Text(
                if (isDriver) {
                    "Vhaya uses your live location while you are online or on an active trip to show nearby ride requests, verify 150 m pickup arrival, and keep riders updated."
                } else {
                    "Vhaya uses your device location to set accurate GPS pickup pins and show nearby drivers. You can still search or drag the map pin if you keep GPS off."
                }
            )
        },
        confirmButton = {
            Button(
                onClick = onAllow,
                modifier = Modifier.testTag("location_rationale_allow")
            ) {
                Text("Continue")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("location_rationale_dismiss")
            ) {
                Text("Not now")
            }
        }
    )
}

@Composable
fun LocationDegradedBanner(
    permissionState: LocationPermissionState,
    onRetryPermission: () -> Unit,
    onOpenSettings: () -> Unit,
    onManualFallback: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val isPermanent = permissionState == LocationPermissionState.DENIED_PERMANENTLY
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = colorScheme.surface.copy(alpha = 0.97f),
        border = BorderStroke(1.dp, colorScheme.outlineVariant),
        shadowElevation = 3.dp,
        modifier = modifier
            .fillMaxWidth()
            .testTag("location_degraded_banner")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(start = 12.dp, top = 8.dp, end = 8.dp, bottom = 8.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.LocationOff,
                contentDescription = null,
                tint = colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
            Column(
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = if (isPermanent) "Location blocked in Settings" else "Location access is off",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = if (isPermanent) {
                        "Open app settings to restore GPS, or search pickup manually."
                    } else {
                        "Allow GPS or set your pickup manually on the map."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onSurfaceVariant
                )
            }
            if (onManualFallback != null) {
                TextButton(
                    onClick = onManualFallback,
                    modifier = Modifier.height(48.dp).testTag("location_manual_fallback_btn")
                ) {
                    Text("Set")
                }
            }
            TextButton(
                onClick = if (isPermanent) onOpenSettings else onRetryPermission,
                modifier = Modifier.height(48.dp).testTag("location_action_btn")
            ) {
                Text(if (isPermanent) "Settings" else "Allow")
            }
        }
    }
}

@Composable
fun LiveTrackingStatusPill(
    isActive: Boolean,
    consentEnabled: Boolean,
    tripOnlyMode: Boolean,
    onStopSharing: () -> Unit,
    onEnableSharing: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val wayfareColors = LocalWayfareColors.current
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (isActive) wayfareColors.brandHighlight else colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, colorScheme.outlineVariant),
        modifier = modifier
            .fillMaxWidth()
            .testTag("live_tracking_status_pill")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Filled.GpsFixed,
                    contentDescription = null,
                    tint = if (isActive) colorScheme.primary else colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = when {
                        isActive -> "Background GPS sharing active"
                        !consentEnabled -> "Location sharing paused by you"
                        tripOnlyMode -> "Trip-only privacy · resumes when trip starts"
                        else -> "Foreground location standby"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            TextButton(
                onClick = if (isActive || consentEnabled) onStopSharing else onEnableSharing,
                modifier = Modifier.height(48.dp).testTag("live_tracking_toggle_btn")
            ) {
                Text(if (isActive || consentEnabled) "Pause" else "Resume")
            }
        }
    }
}

@Composable
fun OfflineReconnectingBanner(
    pendingOfflineCount: Int,
    syncError: String?,
    onSyncNow: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val wayfareColors = LocalWayfareColors.current
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = colorScheme.surface,
        border = BorderStroke(1.dp, wayfareColors.warning.copy(alpha = 0.65f)),
        shadowElevation = 4.dp,
        modifier = modifier
            .fillMaxWidth()
            .testTag("offline_reconnecting_banner")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(start = 12.dp, top = 6.dp, bottom = 6.dp, end = 6.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.CloudOff,
                contentDescription = "Offline status",
                tint = wayfareColors.warning,
                modifier = Modifier.size(18.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                val title = if (pendingOfflineCount > 0) {
                    "Connecting to Vhaya · $pendingOfflineCount queued"
                } else {
                    "Connecting to Vhaya..."
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = syncError ?: "Active trips remain visible and queued actions auto-send.",
                    style = MaterialTheme.typography.labelSmall,
                    color = colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            TextButton(
                onClick = onSyncNow,
                modifier = Modifier.height(48.dp).testTag("offline_sync_now_btn")
            ) {
                Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(4.dp))
                Text("Sync")
            }
        }
    }
}
