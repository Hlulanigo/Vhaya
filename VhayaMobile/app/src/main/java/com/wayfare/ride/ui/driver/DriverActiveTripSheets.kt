package com.wayfare.ride.ui.driver

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.wayfare.ride.domain.LaunchPolicy
import com.wayfare.ride.domain.PaymentMethod
import com.wayfare.ride.domain.RideCoordinates
import com.wayfare.ride.domain.RideStatus
import com.wayfare.ride.domain.RideTrip
import com.wayfare.ride.ui.WayfareViewModel
import com.wayfare.ride.ui.components.Emergency112Chip
import com.wayfare.ride.ui.components.MonoBadge
import com.wayfare.ride.ui.components.StatusChip
import com.wayfare.ride.ui.components.WayfareConfirmDialog
import com.wayfare.ride.ui.components.dialEmergency112
import com.wayfare.ride.ui.components.openExternalNavigation
import com.wayfare.ride.ui.components.tactilePressScale
import com.wayfare.ride.ui.rider.DriverChatSheetContent
import com.wayfare.ride.ui.theme.LocalWayfareColors
import com.wayfare.ride.ui.theme.WayfareMonoFamily
import kotlin.math.roundToInt

@Composable
internal fun PaymentMethodPill(
    method: PaymentMethod,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val icon = when (method) {
        PaymentMethod.CASH -> Icons.Filled.Payments
        PaymentMethod.WALLET -> Icons.Filled.AccountBalanceWallet
        PaymentMethod.CARD -> Icons.Filled.CreditCard
    }
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = colorScheme.secondaryContainer,
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = method.label,
                tint = colorScheme.onSecondaryContainer,
                modifier = Modifier.size(13.dp)
            )
            Text(
                text = method.label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = colorScheme.onSecondaryContainer
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriverActiveTripSheet(
    trip: RideTrip,
    requesterName: String,
    driverCoords: RideCoordinates,
    viewModel: WayfareViewModel,
    onExpandDriveMode: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val wayfareColors = LocalWayfareColors.current
    val context = LocalContext.current

    var showStageModal by remember { mutableStateOf(false) }
    var showChatSheet by remember { mutableStateOf(false) }
    var pinInput by remember(trip.id) { mutableStateOf("") }
    var gpsStatusNote by remember(trip.id) { mutableStateOf<String?>(null) }

    val gpsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val granted = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            val resolved = resolveDriverDeviceLocation(context)
            if (resolved != null) {
                viewModel.updateDriverCoords(resolved, trip.id)
                gpsStatusNote = "Live GPS updated."
            } else {
                gpsStatusNote = "Device GPS fix unavailable yet. Move outdoors and retry."
            }
        } else {
            gpsStatusNote = "Location permission is required to confirm GPS arrival."
        }
    }

    fun syncRealDriverGps() {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (fine || coarse) {
            val resolved = resolveDriverDeviceLocation(context)
            if (resolved != null) {
                viewModel.updateDriverCoords(resolved, trip.id)
                gpsStatusNote = "Live GPS updated."
            } else {
                gpsStatusNote = "Waiting for device GPS fix..."
            }
        } else {
            gpsPermissionLauncher.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            )
        }
    }

    LaunchedEffect(trip.id, trip.status) {
        if (trip.status == RideStatus.ACCEPTED || trip.status == RideStatus.IN_PROGRESS) {
            val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
            if (fine || coarse) {
                resolveDriverDeviceLocation(context)?.let { resolved ->
                    viewModel.updateDriverCoords(resolved, trip.id)
                }
            }
        }
    }

    val stageLabel = when (trip.status) {
        RideStatus.ACCEPTED -> "1. To pickup"
        RideStatus.ARRIVED -> "2. At pickup · Enter PIN"
        RideStatus.IN_PROGRESS -> "3. On trip"
        else -> trip.status.label
    }

    val targetCoords = if (trip.status == RideStatus.IN_PROGRESS) {
        trip.destinationCoordinates
    } else {
        trip.pickupCoordinates
    }
    val remainingKm = remember(trip.liveDriverLocation, driverCoords, targetCoords, trip.route) {
        val current = trip.liveDriverLocation ?: driverCoords
        LaunchPolicy.haversineKm(current, targetCoords)
            ?: ((trip.route?.distanceMeters ?: 4500) / 1000.0)
    }
    val remainingMin = remember(remainingKm) { (remainingKm * 2.2).roundToInt().coerceAtLeast(1) }

    Surface(
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        color = colorScheme.surface,
        shadowElevation = 8.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(18.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    StatusChip(
                        label = stageLabel,
                        containerColor = wayfareColors.brandHighlight,
                        contentColor = colorScheme.onPrimaryContainer
                    )
                    PaymentMethodPill(method = trip.paymentMethod)
                }
                MonoBadge(text = LaunchPolicy.formatZar(trip.fare))
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Person,
                    contentDescription = null,
                    tint = colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "$requesterName · ~${remainingMin} min (${"%.1f".format(remainingKm)} km)",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Text(
                text = "${trip.pickup.substringBefore(" ·")} → ${trip.destination.substringBefore(" ·")}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = { showChatSheet = true },
                    modifier = Modifier.weight(1f).height(48.dp).testTag("driver_chat_btn")
                ) {
                    Icon(Icons.Filled.ChatBubbleOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Chat")
                }
                OutlinedButton(
                    onClick = {
                        if (targetCoords != null) {
                            openExternalNavigation(context, targetCoords, trip.destination)
                        }
                    },
                    modifier = Modifier.weight(1f).height(48.dp)
                ) {
                    Icon(Icons.Filled.Directions, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Navigate")
                }
                Emergency112Chip(onClick = { dialEmergency112(context) })
            }

            when (trip.status) {
                RideStatus.ACCEPTED -> {
                    val distMeters = trip.pickupCoordinates?.let {
                        LaunchPolicy.distanceMeters(trip.liveDriverLocation ?: driverCoords, it).toInt()
                    } ?: 0
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { syncRealDriverGps() },
                            modifier = Modifier.weight(1f).height(52.dp).testTag("driver_sync_gps_btn")
                        ) {
                            Icon(Icons.Filled.MyLocation, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("GPS ($distMeters m)", maxLines = 1)
                        }
                        Button(
                            onClick = { showStageModal = true },
                            modifier = Modifier.weight(1f).height(52.dp).tactilePressScale().testTag("driver_open_stage_modal")
                        ) {
                            Text("Arrived at pickup", fontWeight = FontWeight.Bold)
                        }
                    }
                }
                RideStatus.ARRIVED -> {
                    Button(
                        onClick = { showStageModal = true },
                        modifier = Modifier.fillMaxWidth().height(52.dp).tactilePressScale().testTag("driver_enter_pin_cta")
                    ) {
                        Icon(Icons.Filled.LockOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Enter 6-digit PIN to start", fontWeight = FontWeight.Bold)
                    }
                }
                RideStatus.IN_PROGRESS -> {
                    Button(
                        onClick = onExpandDriveMode,
                        modifier = Modifier.fillMaxWidth().height(52.dp).testTag("driver_expand_drive_mode")
                    ) {
                        Text("Open Drive Mode", fontWeight = FontWeight.Bold)
                    }
                }
                else -> {}
            }
        }
    }

    if (showStageModal) {
        BackHandler { showStageModal = false }
        ModalBottomSheet(onDismissRequest = { showStageModal = false }) {
            Column(
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp)
                    .padding(bottom = 24.dp)
            ) {
                Text("Trip stage", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    StagePill("1. To pickup", active = trip.status == RideStatus.ACCEPTED, done = trip.status != RideStatus.ACCEPTED, modifier = Modifier.weight(1f))
                    StagePill("2. At pickup", active = trip.status == RideStatus.ARRIVED, done = trip.status == RideStatus.IN_PROGRESS, modifier = Modifier.weight(1f))
                    StagePill("3. On trip", active = trip.status == RideStatus.IN_PROGRESS, done = false, modifier = Modifier.weight(1f))
                }

                if (trip.status == RideStatus.ACCEPTED) {
                    val dist = trip.pickupCoordinates?.let {
                        LaunchPolicy.distanceMeters(trip.liveDriverLocation ?: driverCoords, it).toInt()
                    } ?: 0
                    Text(
                        text = "GPS distance to pickup: $dist m (150 m & fresh GPS check required, or $requesterName can tap \"Driver here\" in their app).",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colorScheme.onSurfaceVariant
                    )
                    gpsStatusNote?.let { note ->
                        Text(
                            text = note,
                            style = MaterialTheme.typography.labelMedium,
                            color = colorScheme.primary
                        )
                    }
                    OutlinedButton(
                        onClick = { syncRealDriverGps() },
                        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("stage_sync_gps_btn")
                    ) {
                        Icon(Icons.Filled.MyLocation, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Refresh GPS coordinates")
                    }
                    Button(
                        onClick = {
                            syncRealDriverGps()
                            viewModel.driverMarkArrived(trip.id)
                        },
                        modifier = Modifier.fillMaxWidth().height(50.dp).testTag("stage_confirm_gps_arrived")
                    ) {
                        Text("Confirm GPS arrival", fontWeight = FontWeight.Bold)
                    }
                } else if (trip.status == RideStatus.ARRIVED) {
                    Text(
                        text = "Ask $requesterName for their 6-digit pickup PIN.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = pinInput,
                        onValueChange = { s -> pinInput = s.filter { it.isDigit() }.take(6) },
                        label = { Text("6-digit Pickup PIN") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth().testTag("driver_pin_input")
                    )
                    Button(
                        onClick = {
                            viewModel.driverVerifyPinAndStart(trip.id, pinInput)
                            showStageModal = false
                        },
                        enabled = pinInput.length == 6,
                        modifier = Modifier.fillMaxWidth().height(52.dp).testTag("driver_verify_pin_btn")
                    ) {
                        Text("Verify PIN & start trip", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    if (showChatSheet) {
        BackHandler { showChatSheet = false }
        ModalBottomSheet(onDismissRequest = { showChatSheet = false }) {
            DriverChatSheetContent(
                trip = trip,
                senderRole = "driver",
                onSend = { viewModel.sendTripChat(trip.id, it) },
                onClose = { showChatSheet = false }
            )
        }
    }
}

@Composable
private fun StagePill(label: String, active: Boolean, done: Boolean, modifier: Modifier = Modifier) {
    val colorScheme = MaterialTheme.colorScheme
    val wayfareColors = LocalWayfareColors.current
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = when {
            done -> wayfareColors.brandHighlight
            active -> colorScheme.primary
            else -> colorScheme.surfaceVariant
        },
        modifier = modifier
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = when {
                done -> colorScheme.onPrimaryContainer
                active -> colorScheme.onPrimary
                else -> colorScheme.onSurfaceVariant
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriverDriveModeOverlay(
    trip: RideTrip,
    requesterName: String,
    driverCoords: RideCoordinates,
    viewModel: WayfareViewModel,
    onMinimize: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val wayfareColors = LocalWayfareColors.current
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    var showCompleteConfirm by remember { mutableStateOf(false) }
    var showChat by remember { mutableStateOf(false) }

    val pulse = rememberInfiniteTransition(label = "driveModePulse")
    val pulseScale by pulse.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.32f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Reverse),
        label = "gpsPulse"
    )

    val liveKm = remember(trip.liveDriverLocation, driverCoords, trip.destinationCoordinates, trip.route) {
        val current = trip.liveDriverLocation ?: driverCoords
        LaunchPolicy.haversineKm(current, trip.destinationCoordinates)
            ?: ((trip.route?.distanceMeters ?: 4500) / 1000.0)
    }
    val liveMinutes = remember(liveKm) { (liveKm * 2.1).roundToInt().coerceAtLeast(1) }

    Box(modifier = Modifier.fillMaxSize()) {
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = colorScheme.primary,
            shadowElevation = 8.dp,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 12.dp, start = 14.dp, end = 14.dp)
                .widthIn(max = 580.dp)
                .fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.padding(14.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.Navigation, contentDescription = null, tint = wayfareColors.routeHighlight, modifier = Modifier.size(26.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "Heading to ${trip.destination.substringBefore(" ·")}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = colorScheme.onPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .scale(pulseScale)
                                    .clip(CircleShape)
                                    .background(wayfareColors.routeHighlight)
                            )
                            Text(
                                text = "~$liveMinutes min · ${"%.1f".format(liveKm)} km remaining",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = colorScheme.onPrimary.copy(alpha = 0.92f),
                                modifier = Modifier.testTag("driver_live_eta_text")
                            )
                        }
                    }
                }
                TextButton(
                    onClick = onMinimize,
                    modifier = Modifier.testTag("driver_minimize_drive_mode")
                ) {
                    Text("Minimize", color = wayfareColors.routeHighlight)
                }
            }
        }

        Surface(
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            color = colorScheme.surface,
            shadowElevation = 8.dp,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .widthIn(max = 600.dp)
                .fillMaxWidth()
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(18.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Filled.DirectionsCar, contentDescription = null, tint = colorScheme.primary)
                        Column {
                            Text("On trip · $requesterName", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("Drop-off ${trip.destination.substringBefore(" ·")}", style = MaterialTheme.typography.bodySmall, color = colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        PaymentMethodPill(method = trip.paymentMethod)
                        MonoBadge(text = LaunchPolicy.formatZar(trip.fare))
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = {
                            trip.destinationCoordinates?.let { openExternalNavigation(context, it, trip.destination) }
                        },
                        modifier = Modifier.weight(1f).height(48.dp)
                    ) {
                        Icon(Icons.Filled.Directions, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Directions")
                    }
                    OutlinedButton(
                        onClick = { showChat = true },
                        modifier = Modifier.weight(1f).height(48.dp)
                    ) {
                        Icon(Icons.Filled.ChatBubbleOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Chat")
                    }
                    Emergency112Chip(onClick = { dialEmergency112(context) })
                }

                Button(
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        showCompleteConfirm = true
                    },
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth().height(54.dp).tactilePressScale().testTag("driver_complete_trip_btn")
                ) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Complete trip · ${LaunchPolicy.formatZar(trip.fare)}", fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    if (showCompleteConfirm) {
        WayfareConfirmDialog(
            title = "Complete this trip?",
            message = "Confirm drop-off at ${trip.destination.substringBefore(" ·")} and capture ${LaunchPolicy.formatZar(trip.fare)} (${trip.paymentMethod.label}).",
            confirmLabel = "Complete trip",
            onConfirm = {
                showCompleteConfirm = false
                viewModel.driverCompleteTrip(trip.id)
            },
            onDismiss = { showCompleteConfirm = false }
        )
    }

    if (showChat) {
        BackHandler { showChat = false }
        ModalBottomSheet(onDismissRequest = { showChat = false }) {
            DriverChatSheetContent(
                trip = trip,
                senderRole = "driver",
                onSend = { viewModel.sendTripChat(trip.id, it) },
                onClose = { showChat = false }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriverPostTripSummarySheet(
    trip: RideTrip,
    requesterName: String,
    weeklyPlanActive: Boolean,
    onViewEarnings: () -> Unit,
    onDismiss: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val wayfareColors = LocalWayfareColors.current

    BackHandler { onDismiss() }

    val settlementNote = when (trip.paymentMethod) {
        PaymentMethod.CASH -> "Collect ${LaunchPolicy.formatZar(trip.fare)} cash from $requesterName."
        PaymentMethod.WALLET -> "Settled from requester Vhaya wallet."
        PaymentMethod.CARD -> "Captured via card hold."
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp)
                .padding(bottom = 24.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = wayfareColors.brandHighlight,
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = "Trip completed",
                            tint = colorScheme.onPrimaryContainer
                        )
                    }
                }
                Column {
                    Text("Trip completed", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(
                        text = "${trip.pickup.substringBefore(" ·")} → ${trip.destination.substringBefore(" ·")}",
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(18.dp),
                color = colorScheme.primaryContainer.copy(alpha = 0.55f),
                border = BorderStroke(1.dp, colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(14.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Captured fare", style = MaterialTheme.typography.labelLarge, color = colorScheme.onSurfaceVariant)
                        PaymentMethodPill(method = trip.paymentMethod)
                    }
                    Text(
                        text = LaunchPolicy.formatZar(trip.fare),
                        fontFamily = WayfareMonoFamily,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = colorScheme.primary,
                        modifier = Modifier.testTag("driver_summary_fare")
                    )
                    HorizontalDivider()
                    Text(
                        text = settlementNote,
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.onSurface
                    )
                    Text(
                        text = if (weeklyPlanActive) {
                            "Vhaya Weekly Driver Pass active · 0% per-ride launch fee."
                        } else {
                            "Standard payout cycle settles weekly via Paystack / EFT."
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedButton(
                    onClick = onViewEarnings,
                    modifier = Modifier.weight(1f).height(50.dp).testTag("driver_summary_view_earnings")
                ) {
                    Text("View earnings")
                }
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f).height(50.dp).tactilePressScale().testTag("driver_summary_done")
                ) {
                    Text("Ready next ride", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@SuppressLint("MissingPermission")
private fun resolveDriverDeviceLocation(context: Context): RideCoordinates? {
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
