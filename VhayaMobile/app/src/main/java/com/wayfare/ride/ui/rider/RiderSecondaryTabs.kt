package com.wayfare.ride.ui.rider

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wayfare.ride.domain.LaunchPolicy
import com.wayfare.ride.domain.RideStatus
import com.wayfare.ride.domain.RideTrip
import com.wayfare.ride.domain.SupportCase
import com.wayfare.ride.ui.WayfareUiState
import com.wayfare.ride.ui.WayfareViewModel
import com.wayfare.ride.ui.components.MonoBadge
import com.wayfare.ride.ui.components.OsmRouteMiniPreview
import com.wayfare.ride.ui.components.StatusChip
import com.wayfare.ride.ui.components.formatShortTime
import com.wayfare.ride.ui.theme.LocalWayfareColors
import com.wayfare.ride.ui.theme.WayfareMonoFamily

@Composable
fun RiderTripsTab(
    uiState: WayfareUiState,
    viewModel: WayfareViewModel,
    modifier: Modifier = Modifier
) {
    var selectedTripId by remember { mutableStateOf<String?>(null) }
    val selectedTrip = uiState.riderHistoryTrips.firstOrNull { it.id == selectedTripId }

    if (selectedTrip != null) {
        BackHandler { selectedTripId = null }
        PastRideDetailView(
            trip = selectedTrip,
            onBack = { selectedTripId = null },
            onRate = { stars -> viewModel.rateTrip(selectedTrip.id, stars) },
            onReportIssue = { cat, msg -> viewModel.createSupportCase(selectedTrip, cat, "normal", msg) }
        )
        return
    }

    val colorScheme = MaterialTheme.colorScheme
    LazyColumn(
        contentPadding = PaddingValues(top = 78.dp, bottom = 24.dp, start = 16.dp, end = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier.fillMaxSize()
    ) {
        item {
            Text(
                text = "Your trips",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
        }

        if (uiState.riderHistoryTrips.isEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Completed and cancelled rides appear here.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(18.dp)
                    )
                }
            }
        } else {
            items(uiState.riderHistoryTrips, key = { it.id }) { trip ->
                TripHistoryCard(trip = trip, onClick = { selectedTripId = trip.id })
            }
        }
    }
}

@Composable
private fun TripHistoryCard(trip: RideTrip, onClick: () -> Unit) {
    val colorScheme = MaterialTheme.colorScheme
    val wayfareColors = LocalWayfareColors.current

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
        border = BorderStroke(1.dp, colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .testTag("trip_card_${trip.id}")
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = formatShortTime(trip.createdAt),
                    style = MaterialTheme.typography.labelMedium,
                    color = colorScheme.onSurfaceVariant
                )
                StatusChip(
                    label = trip.status.label,
                    containerColor = if (trip.status == RideStatus.COMPLETED) wayfareColors.brandHighlight else colorScheme.surfaceVariant,
                    contentColor = colorScheme.onSurface
                )
            }
            Text(
                text = "${trip.pickup.substringBefore(" ·")} → ${trip.destination.substringBefore(" ·")}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = trip.driverProfile?.let { "${it.name} · ${it.vehicle}" } ?: trip.category.label,
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                MonoBadge(text = LaunchPolicy.formatZar(trip.fare))
            }
        }
    }
}

@Composable
private fun PastRideDetailView(
    trip: RideTrip,
    onBack: () -> Unit,
    onRate: (Int) -> Unit,
    onReportIssue: (String, String) -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val wayfareColors = LocalWayfareColors.current
    var showReportDialog by remember { mutableStateOf(false) }

    LazyColumn(
        contentPadding = PaddingValues(top = 74.dp, bottom = 28.dp, start = 16.dp, end = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to trips")
                }
                Text("Ride receipt", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
        }

        item {
            OsmRouteMiniPreview(
                pickup = trip.pickupCoordinates,
                destination = trip.destinationCoordinates,
                route = trip.route,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(170.dp)
            )
        }

        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
                border = BorderStroke(1.dp, colorScheme.outlineVariant)
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(16.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(trip.category.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        MonoBadge(text = LaunchPolicy.formatZar(trip.fare))
                    }
                    HorizontalDivider()
                    Text("Pickup: ${trip.pickup}", style = MaterialTheme.typography.bodyMedium)
                    Text("Drop-off: ${trip.destination}", style = MaterialTheme.typography.bodyMedium)
                    Text("Payment: ${trip.paymentMethod.label} · ${trip.paymentStatus.label}", style = MaterialTheme.typography.bodySmall, color = colorScheme.onSurfaceVariant)
                    trip.driverProfile?.let { d ->
                        Text("Driver: ${d.name} · ${d.vehicle} (${d.registration})", style = MaterialTheme.typography.bodySmall, color = colorScheme.onSurfaceVariant)
                    }
                    trip.paymentReference?.let { ref ->
                        Text("Reference: $ref", style = MaterialTheme.typography.labelMedium, color = colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        if (trip.status == RideStatus.COMPLETED) {
            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
                    border = BorderStroke(1.dp, colorScheme.outlineVariant)
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text("Rate your driver", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            for (star in 1..5) {
                                val filled = (trip.riderRating ?: 0) >= star
                                IconButton(
                                    onClick = { onRate(star) },
                                    modifier = Modifier.size(48.dp).testTag("rate_star_$star")
                                ) {
                                    Icon(
                                        imageVector = if (filled) Icons.Filled.Star else Icons.Filled.StarBorder,
                                        contentDescription = "Rate $star stars",
                                        tint = if (filled) wayfareColors.warning else colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                            }
                        }
                        if (trip.riderRatingTags.isNotEmpty()) {
                            Text(
                                text = "Tags: ${trip.riderRatingTags.joinToString(" · ")}",
                                style = MaterialTheme.typography.bodySmall,
                                color = colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        item {
            OutlinedButton(
                onClick = { showReportDialog = true },
                modifier = Modifier.fillMaxWidth().height(50.dp).testTag("past_ride_report_btn")
            ) {
                Icon(Icons.Filled.Flag, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Report issue with this trip")
            }
        }
    }

    if (showReportDialog) {
        IncidentReportDialog(
            trip = trip,
            onSubmit = { cat, msg ->
                onReportIssue(cat, msg)
                showReportDialog = false
            },
            onDismiss = { showReportDialog = false }
        )
    }
}

@Composable
fun RiderSupportTab(
    uiState: WayfareUiState,
    viewModel: WayfareViewModel,
    modifier: Modifier = Modifier
) {
    var selectedCaseId by remember { mutableStateOf<Long?>(null) }
    val selectedCase = uiState.supportCases.firstOrNull { it.id == selectedCaseId }

    if (selectedCase != null) {
        val currentName = uiState.currentSession?.displayName?.takeIf { it.isNotBlank() } ?: "Requester"
        BackHandler { selectedCaseId = null }
        SupportThreadView(
            supportCase = selectedCase,
            senderLabel = currentName,
            onBack = { selectedCaseId = null },
            onSendReply = { msg -> viewModel.sendRiderSupportFollowUp(selectedCase, msg) }
        )
        return
    }

    val colorScheme = MaterialTheme.colorScheme
    LazyColumn(
        contentPadding = PaddingValues(top = 78.dp, bottom = 24.dp, start = 16.dp, end = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier.fillMaxSize()
    ) {
        item {
            Text("Support", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        }
        if (uiState.supportCases.isEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
                    border = BorderStroke(1.dp, colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(18.dp)
                    ) {
                        Text(
                            text = "No support tickets yet",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Need help with a trip or fare? Open any active ride or past trip receipt to contact Vhaya Support.",
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
        items(uiState.supportCases, key = { it.id }) { ticket ->
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
                border = BorderStroke(1.dp, colorScheme.outlineVariant),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .clickable { selectedCaseId = ticket.id }
                    .testTag("support_case_${ticket.id}")
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(14.dp)) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "#${ticket.id} · ${ticket.category.replaceFirstChar { it.uppercase() }}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        StatusChip(
                            label = ticket.status.replace("_", " "),
                            containerColor = colorScheme.secondaryContainer,
                            contentColor = colorScheme.onSecondaryContainer
                        )
                    }
                    Text(
                        text = ticket.message,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${ticket.pickup.substringBefore(" ·")} → ${ticket.destination.substringBefore(" ·")}",
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun SupportThreadView(
    supportCase: SupportCase,
    senderLabel: String,
    onBack: () -> Unit,
    onSendReply: (String) -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    var replyText by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 70.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp)
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("Ticket #${supportCase.id}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(supportCase.status.replace("_", " "), style = MaterialTheme.typography.labelMedium, color = colorScheme.onSurfaceVariant)
            }
        }

        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.weight(1f)
        ) {
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(14.dp)) {
                        Text(supportCase.reporterName, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        Text(supportCase.message, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            items(supportCase.replies, key = { it.id }) { rep ->
                val isStaff = rep.senderLabel.contains("Support") || rep.senderLabel.contains("Admin")
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (isStaff) colorScheme.primaryContainer else colorScheme.surface,
                    border = BorderStroke(1.dp, colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(14.dp)) {
                        Text(rep.senderLabel, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        Text(rep.message, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }

        Surface(
            color = colorScheme.surface,
            shadowElevation = 6.dp
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                OutlinedTextField(
                    value = replyText,
                    onValueChange = { replyText = it },
                    placeholder = { Text("Reply as $senderLabel...") },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.weight(1f).testTag("support_reply_input")
                )
                Button(
                    onClick = {
                        if (replyText.isNotBlank()) {
                            onSendReply(replyText)
                            replyText = ""
                        }
                    },
                    modifier = Modifier.height(52.dp).testTag("support_reply_send")
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send reply")
                }
            }
        }
    }
}
