package com.wayfare.ride.ui.rider

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import com.wayfare.ride.domain.LaunchPolicy
import com.wayfare.ride.domain.PaymentMethod
import com.wayfare.ride.domain.PaymentStatus
import com.wayfare.ride.domain.RideOffer
import com.wayfare.ride.domain.RideStatus
import com.wayfare.ride.domain.RideTrip
import com.wayfare.ride.domain.TrustedContact
import com.wayfare.ride.ui.WayfareViewModel
import com.wayfare.ride.ui.components.Emergency112Chip
import com.wayfare.ride.ui.components.MonoBadge
import com.wayfare.ride.ui.components.StatusChip
import com.wayfare.ride.ui.components.WayfareConfirmDialog
import com.wayfare.ride.ui.components.dialEmergency112
import com.wayfare.ride.ui.components.shareTripSnapshot
import com.wayfare.ride.ui.components.tactilePressScale
import com.wayfare.ride.ui.theme.LocalWayfareColors
import com.wayfare.ride.ui.theme.WayfareMonoFamily
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RiderActiveTripSheet(
    trip: RideTrip,
    trustedContacts: List<TrustedContact>,
    dismissedOfferIds: Set<String>,
    viewModel: WayfareViewModel
) {
    val colorScheme = MaterialTheme.colorScheme
    val wayfareColors = LocalWayfareColors.current
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current

    var showChatSheet by remember { mutableStateOf(false) }
    var showSafetySheet by remember { mutableStateOf(false) }
    var showCancelConfirm by remember { mutableStateOf(false) }
    var showIncidentDialog by remember { mutableStateOf(false) }
    var flaggingOffer by remember { mutableStateOf<RideOffer?>(null) }

    val pendingOffers = remember(trip.offers, dismissedOfferIds) {
        trip.offers
            .filter { it.status == "pending" && !dismissedOfferIds.contains(it.id) }
            .sortedByDescending { LaunchPolicy.offerScore(it) }
    }

    Surface(
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        color = colorScheme.surface,
        shadowElevation = 8.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp)
        ) {
            // Header row: status + payment method + fare
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                StatusChip(
                    label = trip.status.label,
                    containerColor = wayfareColors.brandHighlight,
                    contentColor = colorScheme.onPrimaryContainer
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    StatusChip(
                        label = trip.paymentMethod.label,
                        containerColor = colorScheme.surfaceVariant,
                        contentColor = colorScheme.onSurfaceVariant
                    )
                    MonoBadge(text = LaunchPolicy.formatZar(trip.fare))
                }
            }

            TripStageProgressBar(status = trip.status)

            Text(
                text = "${trip.pickup.substringBefore(" ·")} → ${trip.destination.substringBefore(" ·")}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (trip.status.isOpenForDrivers) {
                if (pendingOffers.isEmpty()) {
                    SearchingDriversRadarCard(trip = trip)
                } else {
                    Text(
                        text = "Choose a driver (${pendingOffers.size})",
                        style = MaterialTheme.typography.labelLarge,
                        color = colorScheme.onSurfaceVariant
                    )
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.heightIn(max = 220.dp)
                    ) {
                        items(pendingOffers, key = { it.id }) { offer ->
                            DriverOfferCard(
                                offer = offer,
                                onAccept = {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    viewModel.acceptDriverOffer(trip.id, offer.id)
                                },
                                onFlag = { flaggingOffer = offer },
                                onHide = { viewModel.hideOfferLocally(offer.id) }
                            )
                        }
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedButton(
                        onClick = { showCancelConfirm = true },
                        modifier = Modifier.weight(1f).height(48.dp).testTag("rider_cancel_ride")
                    ) {
                        Text("Cancel ride")
                    }
                    Emergency112Chip(onClick = { dialEmergency112(context) })
                }
            } else {
                // Driver matched / Arrived / In progress
                trip.driverProfile?.let { driver ->
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = colorScheme.surfaceVariant.copy(alpha = 0.55f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = driver.name,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Icon(
                                        imageVector = Icons.Filled.Star,
                                        contentDescription = null,
                                        tint = wayfareColors.warning,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = "%.1f".format(driver.rating),
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                }
                                Text(
                                    text = "${driver.vehicleColor} ${driver.vehicle}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colorScheme.onSurfaceVariant
                                )
                            }
                            MonoBadge(
                                text = driver.registration,
                                containerColor = colorScheme.surface,
                                contentColor = colorScheme.onSurface
                            )
                        }
                    }
                }

                // 6-Digit Pickup PIN Pill
                if (!trip.tripPin.isNullOrBlank()) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = colorScheme.primaryContainer,
                        border = BorderStroke(1.dp, colorScheme.primary.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Lock,
                                    contentDescription = "Pickup PIN",
                                    tint = colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "Pickup PIN",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Text(
                                text = trip.tripPin,
                                fontFamily = WayfareMonoFamily,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = colorScheme.primary,
                                modifier = Modifier.testTag("rider_pickup_pin")
                            )
                        }
                    }
                }

                // Card hold (Paystack initialize / verify / release) + visual arrival confirm
                if (trip.paymentMethod == PaymentMethod.CARD || trip.status == RideStatus.ACCEPTED) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (trip.paymentMethod == PaymentMethod.CARD) {
                            val isHeld = trip.paymentStatus == PaymentStatus.AUTHORIZED
                            val isPending = trip.paymentStatus == PaymentStatus.PENDING && !trip.paymentReference.isNullOrBlank()
                            OutlinedButton(
                                onClick = {
                                    when {
                                        isHeld -> viewModel.releaseCardHold(trip.id)
                                        isPending -> trip.paymentReference?.let { ref ->
                                            viewModel.verifyPaymentReference(ref, trip.id)
                                        }
                                        else -> viewModel.initializeCardPayment(trip)
                                    }
                                },
                                contentPadding = PaddingValues(horizontal = 10.dp),
                                modifier = Modifier.weight(1f).height(48.dp).testTag("rider_card_hold_btn")
                            ) {
                                Icon(Icons.Filled.CreditCard, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = when {
                                        isHeld -> "Release hold"
                                        isPending -> "Verify hold"
                                        else -> "Authorize card"
                                    },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        if (trip.status == RideStatus.ACCEPTED) {
                            Button(
                                onClick = {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    viewModel.riderConfirmArrival(trip.id)
                                },
                                contentPadding = PaddingValues(horizontal = 10.dp),
                                modifier = Modifier.weight(1f).height(48.dp).tactilePressScale().testTag("rider_confirm_arrival")
                            ) {
                                Icon(Icons.Filled.VerifiedUser, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Driver here", maxLines = 1)
                            }
                        }
                    }
                }

                // Compact Quick Actions: Chat, Safety, Cancel
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedButton(
                        onClick = { showChatSheet = true },
                        modifier = Modifier.weight(1f).height(48.dp).testTag("rider_chat_button")
                    ) {
                        Icon(Icons.Filled.ChatBubbleOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Chat (${trip.messages.size})")
                    }
                    OutlinedButton(
                        onClick = { showSafetySheet = true },
                        modifier = Modifier.weight(1f).height(48.dp).testTag("rider_safety_button")
                    ) {
                        Icon(Icons.Filled.Shield, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Safety")
                    }
                    if (LaunchPolicy.canRiderCancel(trip.status)) {
                        TextButton(
                            onClick = { showCancelConfirm = true },
                            modifier = Modifier.height(48.dp).testTag("rider_cancel_active")
                        ) {
                            Text("Cancel", color = colorScheme.error)
                        }
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
                senderRole = "rider",
                onSend = { viewModel.sendTripChat(trip.id, it) },
                onClose = { showChatSheet = false }
            )
        }
    }

    if (showSafetySheet) {
        BackHandler { showSafetySheet = false }
        ModalBottomSheet(onDismissRequest = { showSafetySheet = false }) {
            SafetyShareSheetContent(
                trip = trip,
                trustedContacts = trustedContacts,
                onCall112 = { dialEmergency112(context) },
                onShareTrip = { shareTripSnapshot(context, trip) },
                onAddContact = { n, p -> viewModel.addTrustedContact(n, p) },
                onRemoveContact = { viewModel.removeTrustedContact(it) },
                onReportIncident = {
                    showSafetySheet = false
                    showIncidentDialog = true
                }
            )
        }
    }

    if (showCancelConfirm) {
        WayfareConfirmDialog(
            title = "Cancel this ride?",
            message = "Cancellation fee is R 0.00 during launch.",
            confirmLabel = "Cancel ride",
            isDestructive = true,
            onConfirm = {
                showCancelConfirm = false
                viewModel.cancelTrip(trip.id, "Cancelled by rider")
            },
            onDismiss = { showCancelConfirm = false }
        )
    }

    if (showIncidentDialog) {
        IncidentReportDialog(
            trip = trip,
            onSubmit = { cat, msg ->
                viewModel.createSupportCase(trip, cat, "high", msg)
                showIncidentDialog = false
            },
            onDismiss = { showIncidentDialog = false }
        )
    }

    flaggingOffer?.let { offer ->
        var reason by remember(offer.id) { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { flaggingOffer = null },
            title = { Text("Report offer from ${offer.driverProfile.name}?") },
            text = {
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    placeholder = { Text("Reason (e.g. unfair fare, safety concern)...") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.flagDriverOffer(trip.id, offer.id, reason)
                        flaggingOffer = null
                    },
                    enabled = reason.isNotBlank()
                ) {
                    Text("Report")
                }
            },
            dismissButton = {
                TextButton(onClick = { flaggingOffer = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun SearchingDriversRadarCard(trip: RideTrip) {
    val colorScheme = MaterialTheme.colorScheme
    val wayfareColors = LocalWayfareColors.current

    var elapsedSeconds by remember(trip.id, trip.createdAt) {
        mutableLongStateOf(((System.currentTimeMillis() - trip.createdAt) / 1000L).coerceAtLeast(0L))
    }
    LaunchedEffect(trip.id, trip.createdAt) {
        while (true) {
            elapsedSeconds = ((System.currentTimeMillis() - trip.createdAt) / 1000L).coerceAtLeast(0L)
            delay(1000L)
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "searchingDriverCircle")
    val sweepAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sweepAngle"
    )
    val ripplePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ripplePhase"
    )

    val shortPickup = remember(trip.pickup) {
        trip.pickup.substringBefore(" ·").substringBefore(",").ifBlank { "pickup" }
    }
    val mins = elapsedSeconds / 60L
    val secs = elapsedSeconds % 60L
    val timerText = "%d:%02d".format(mins, secs)

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = colorScheme.surfaceVariant.copy(alpha = 0.42f),
        border = BorderStroke(1.dp, colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("searching_drivers_radar_card")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.padding(14.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(74.dp)
                    .testTag("rider_searching_circle")
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val maxRadius = size.minDimension / 2f

                    // Expanding circular ripple
                    drawCircle(
                        color = colorScheme.primary.copy(alpha = (1f - ripplePhase) * 0.25f),
                        radius = (maxRadius * 0.56f) + ripplePhase * (maxRadius * 0.42f),
                        center = center
                    )

                    // Base ring track
                    val arcInset = 5.dp.toPx()
                    val arcDiameter = size.minDimension - arcInset * 2f
                    drawCircle(
                        color = colorScheme.outlineVariant.copy(alpha = 0.65f),
                        radius = arcDiameter / 2f,
                        center = center,
                        style = Stroke(width = 3.dp.toPx())
                    )

                    // Rotating circular orbital sweep
                    drawArc(
                        color = colorScheme.primary,
                        startAngle = sweepAngle,
                        sweepAngle = 115f,
                        useCenter = false,
                        topLeft = Offset(arcInset, arcInset),
                        size = Size(arcDiameter, arcDiameter),
                        style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round)
                    )
                    drawArc(
                        color = wayfareColors.routeHighlight,
                        startAngle = sweepAngle + 70f,
                        sweepAngle = 45f,
                        useCenter = false,
                        topLeft = Offset(arcInset, arcInset),
                        size = Size(arcDiameter, arcDiameter),
                        style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round)
                    )
                }

                Surface(
                    shape = CircleShape,
                    color = colorScheme.primary,
                    shadowElevation = 4.dp,
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Filled.DirectionsCar,
                            contentDescription = "Searching for drivers",
                            tint = colorScheme.onPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = "Searching for drivers",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (elapsedSeconds < 25) {
                        "Matching verified drivers near $shortPickup"
                    } else {
                        "Expanding search circle around $shortPickup"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(top = 2.dp)
                ) {
                    MonoBadge(
                        text = timerText,
                        containerColor = wayfareColors.brandHighlight,
                        contentColor = colorScheme.onPrimaryContainer
                    )
                    StatusChip(
                        label = trip.category.label,
                        containerColor = colorScheme.surface,
                        contentColor = colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun DriverOfferCard(
    offer: RideOffer,
    onAccept: () -> Unit,
    onFlag: () -> Unit,
    onHide: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val wayfareColors = LocalWayfareColors.current

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = colorScheme.surfaceVariant.copy(alpha = 0.55f),
        border = BorderStroke(1.dp, colorScheme.outline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(12.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = offer.driverProfile.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Icon(Icons.Filled.Star, contentDescription = null, tint = wayfareColors.warning, modifier = Modifier.size(14.dp))
                    Text("%.1f".format(offer.driverProfile.rating), style = MaterialTheme.typography.labelMedium)
                }
                Text(
                    text = "${offer.driverProfile.vehicle} · ${offer.driverProfile.registration}",
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Button(
                onClick = onAccept,
                contentPadding = PaddingValues(horizontal = 14.dp),
                modifier = Modifier.height(48.dp).tactilePressScale().testTag("accept_offer_${offer.id}")
            ) {
                Text(
                    text = LaunchPolicy.formatZar(offer.amount),
                    fontFamily = WayfareMonoFamily,
                    fontWeight = FontWeight.Bold
                )
            }
            IconButton(
                onClick = onFlag,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(Icons.Filled.Flag, contentDescription = "Report offer", tint = colorScheme.error, modifier = Modifier.size(18.dp))
            }
            IconButton(
                onClick = onHide,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(Icons.Filled.Close, contentDescription = "Hide offer", modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
fun DriverChatSheetContent(
    trip: RideTrip,
    senderRole: String,
    onSend: (String) -> Unit,
    onClose: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    var draftText by remember { mutableStateOf("") }
    val quickReplies = if (senderRole == "rider") {
        listOf("At the main entrance", "On my way", "Please wait 2 min")
    } else {
        listOf("I have arrived", "Outside in white Corolla", "2 minutes away")
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "Trip Chat",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            IconButton(onClick = onClose, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Filled.Close, contentDescription = "Close chat")
            }
        }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 120.dp, max = 260.dp)
        ) {
            if (trip.messages.isEmpty()) {
                item {
                    Text(
                        text = "Send a quick message about pickup.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.onSurfaceVariant
                    )
                }
            }
            items(trip.messages, key = { it.id }) { msg ->
                val isMine = msg.sender == senderRole
                Box(
                    contentAlignment = if (isMine) Alignment.CenterEnd else Alignment.CenterStart,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (isMine) colorScheme.primary else colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = msg.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isMine) colorScheme.onPrimary else colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                }
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            quickReplies.forEach { reply ->
                FilterChip(
                    selected = false,
                    onClick = { onSend(reply) },
                    label = { Text(reply, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
        ) {
            OutlinedTextField(
                value = draftText,
                onValueChange = { draftText = it },
                placeholder = { Text("Message...") },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.weight(1f).testTag("chat_message_input")
            )
            Button(
                onClick = {
                    if (draftText.isNotBlank()) {
                        onSend(draftText)
                        draftText = ""
                    }
                },
                modifier = Modifier.height(52.dp).testTag("chat_send_button")
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
            }
        }
    }
}

@Composable
private fun SafetyShareSheetContent(
    trip: RideTrip,
    trustedContacts: List<TrustedContact>,
    onCall112: () -> Unit,
    onShareTrip: () -> Unit,
    onAddContact: (String, String) -> Unit,
    onRemoveContact: (String) -> Unit,
    onReportIncident: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    var nameInput by remember { mutableStateOf("") }
    var phoneInput by remember { mutableStateOf("") }

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 8.dp)
            .padding(bottom = 20.dp)
    ) {
        Text("Safety & Trip Share", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = onCall112,
                colors = ButtonDefaults.buttonColors(containerColor = colorScheme.error, contentColor = colorScheme.onError),
                modifier = Modifier.weight(1f).height(50.dp).testTag("safety_call_112")
            ) {
                Text("Call 112", fontWeight = FontWeight.Bold)
            }
            OutlinedButton(
                onClick = onShareTrip,
                modifier = Modifier.weight(1f).height(50.dp).testTag("safety_share_trip")
            ) {
                Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Share trip")
            }
        }

        OutlinedButton(
            onClick = onReportIncident,
            modifier = Modifier.fillMaxWidth().height(48.dp).testTag("safety_report_incident")
        ) {
            Icon(Icons.Filled.Flag, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Report safety or ride issue")
        }

        HorizontalDivider()
        Text("Trusted contacts", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)

        trustedContacts.forEach { contact ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(contact.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    Text(contact.phone, style = MaterialTheme.typography.bodySmall, color = colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { onRemoveContact(contact.id) }, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Filled.DeleteOutline, contentDescription = "Remove contact", tint = colorScheme.error)
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = nameInput,
                onValueChange = { nameInput = it },
                placeholder = { Text("Name") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = phoneInput,
                onValueChange = { phoneInput = it },
                placeholder = { Text("+27 ...") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            Button(
                onClick = {
                    onAddContact(nameInput, phoneInput)
                    nameInput = ""
                    phoneInput = ""
                },
                modifier = Modifier.height(52.dp)
            ) {
                Text("Add")
            }
        }
    }
}

@Composable
fun IncidentReportDialog(
    trip: RideTrip,
    onSubmit: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var category by remember { mutableStateOf("safety") }
    var note by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Report ride issue") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("safety", "payment", "driver").forEach { cat ->
                        FilterChip(
                            selected = category == cat,
                            onClick = { category = cat },
                            label = { Text(cat.replaceFirstChar { it.uppercase() }) }
                        )
                    }
                }
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    placeholder = { Text("Describe what happened on ${trip.destination.substringBefore(" ·")}...") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { if (note.isNotBlank()) onSubmit(category, note) },
                enabled = note.isNotBlank()
            ) {
                Text("Submit")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun TripStageProgressBar(status: RideStatus) {
    val colorScheme = MaterialTheme.colorScheme
    val wayfareColors = LocalWayfareColors.current
    val activeStep = when (status) {
        RideStatus.REQUESTED, RideStatus.COUNTERED -> 0
        RideStatus.ACCEPTED -> 1
        RideStatus.ARRIVED -> 2
        RideStatus.IN_PROGRESS, RideStatus.COMPLETED -> 3
        RideStatus.CANCELLED, RideStatus.UNFULFILLED -> -1
    }
    if (activeStep < 0) return

    val stages = listOf("Matching", "Assigned", "Arrived", "On trip")

    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("trip_stage_progress_bar")
    ) {
        stages.forEachIndexed { index, label ->
            val isReached = index <= activeStep
            val isCurrent = index == activeStep
            val barColor = when {
                isCurrent -> colorScheme.primary
                isReached -> colorScheme.primary
                else -> colorScheme.surfaceVariant
            }
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .clip(CircleShape)
                        .background(barColor)
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                    color = if (isReached) colorScheme.onSurface else colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

