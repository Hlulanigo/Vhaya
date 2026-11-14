package com.wayfare.ride.ui.rider

import android.Manifest
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wayfare.ride.domain.IssueCategory
import com.wayfare.ride.domain.LaunchPolicy
import com.wayfare.ride.domain.PaymentStatus
import com.wayfare.ride.domain.RideStatus
import com.wayfare.ride.domain.RideTrip
import com.wayfare.ride.domain.SupportCase
import com.wayfare.ride.notifications.SupportNotificationHelper
import com.wayfare.ride.ui.WayfareUiState
import com.wayfare.ride.ui.WayfareViewModel
import com.wayfare.ride.ui.components.Emergency112Chip
import com.wayfare.ride.ui.components.MonoBadge
import com.wayfare.ride.ui.components.OsmRouteMiniPreview
import com.wayfare.ride.ui.components.StatusChip
import com.wayfare.ride.ui.components.WayfareEmptyState
import com.wayfare.ride.ui.components.dialEmergency112
import com.wayfare.ride.ui.components.formatShortTime
import com.wayfare.ride.ui.theme.LocalWayfareColors

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
            onReportIssue = { cat, msg, requestRefund, attachmentUri ->
                viewModel.createSupportCase(
                    trip = selectedTrip,
                    category = cat,
                    priority = if (cat == "safety" || requestRefund) "high" else "normal",
                    message = msg,
                    requestRefund = requestRefund,
                    attachmentUri = attachmentUri
                )
            }
        )
        return
    }

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
                WayfareEmptyState(
                    icon = Icons.Filled.History,
                    title = "No rides yet",
                    message = "Completed and cancelled rides will appear here."
                )
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
    onReportIssue: (String, String, Boolean, Uri?) -> Unit
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
                Text("Report issue or request refund")
            }
        }
    }

    if (showReportDialog) {
        IssueReportingSheet(
            trip = trip,
            initialCategory = if (trip.paymentStatus == PaymentStatus.FAILED) IssueCategory.PAYMENT else IssueCategory.DRIVER,
            onSubmit = { cat, msg, requestRefund, attachmentUri ->
                onReportIssue(cat, msg, requestRefund, attachmentUri)
                showReportDialog = false
            },
            onDismiss = { showReportDialog = false }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RiderSupportTab(
    uiState: WayfareUiState,
    viewModel: WayfareViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val wayfareColors = LocalWayfareColors.current
    var selectedCaseId by remember { mutableStateOf<Long?>(null) }
    var statusFilter by remember { mutableStateOf("all") }
    val selectedCase = uiState.supportCases.firstOrNull { it.id == selectedCaseId }

    var hasNotifPermission by remember {
        mutableStateOf(SupportNotificationHelper.hasNotificationPermission(context))
    }
    val notifLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasNotifPermission = granted
    }

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

    val filteredCases = remember(uiState.supportCases, statusFilter) {
        if (statusFilter == "all") uiState.supportCases
        else uiState.supportCases.filter { it.status.equals(statusFilter, ignoreCase = true) }
    }

    val colorScheme = MaterialTheme.colorScheme
    LazyColumn(
        contentPadding = PaddingValues(top = 78.dp, bottom = 24.dp, start = 16.dp, end = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier.fillMaxSize()
    ) {
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Support", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Emergency112Chip(onClick = { dialEmergency112(context) })
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotifPermission) {
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = wayfareColors.brandHighlight,
                    border = BorderStroke(1.dp, colorScheme.primary.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Icon(Icons.Filled.NotificationsActive, contentDescription = null, tint = colorScheme.primary)
                        Text(
                            text = "Enable notifications for instant Vhaya Support staff replies.",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(
                            onClick = { notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                            modifier = Modifier.height(48.dp).testTag("support_enable_notif_btn")
                        ) {
                            Text("Enable")
                        }
                    }
                }
            }
        }

        item {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf("all" to "All", "open" to "Open", "in_progress" to "In progress", "resolved" to "Resolved").forEach { (key, label) ->
                    FilterChip(
                        selected = statusFilter == key,
                        onClick = { statusFilter = key },
                        label = { Text(label) },
                        modifier = Modifier.testTag("support_filter_$key")
                    )
                }
            }
        }

        if (filteredCases.isEmpty()) {
            item {
                WayfareEmptyState(
                    icon = Icons.Filled.SupportAgent,
                    title = if (uiState.supportCases.isEmpty()) "No support cases yet" else "No $statusFilter tickets",
                    message = "Report an issue from any active ride or past receipt in Trips."
                )
            }
        }
        items(filteredCases, key = { it.id }) { ticket ->
            val statusNormalized = ticket.status.lowercase()
            val chipBg = when (statusNormalized) {
                "resolved" -> wayfareColors.brandHighlight
                "in_progress" -> colorScheme.primaryContainer
                else -> colorScheme.secondaryContainer
            }
            val chipFg = when (statusNormalized) {
                "resolved" -> colorScheme.onPrimaryContainer
                "in_progress" -> colorScheme.onPrimaryContainer
                else -> colorScheme.onSecondaryContainer
            }
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
                            text = "#${ticket.id} · ${IssueCategory.fromKey(ticket.category).label}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (ticket.priority.equals("urgent", ignoreCase = true) || ticket.priority.equals("high", ignoreCase = true)) {
                                StatusChip(
                                    label = ticket.priority.replaceFirstChar { it.uppercase() },
                                    containerColor = colorScheme.errorContainer,
                                    contentColor = colorScheme.error
                                )
                            }
                            StatusChip(
                                label = ticket.status.replace("_", " ").replaceFirstChar { it.uppercase() },
                                containerColor = chipBg,
                                contentColor = chipFg
                            )
                        }
                    }
                    Text(
                        text = ticket.message,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (!ticket.staffNote.isNullOrBlank()) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = colorScheme.primaryContainer.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Staff note: ${ticket.staffNote}",
                                style = MaterialTheme.typography.labelSmall,
                                color = colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "${ticket.pickup.substringBefore(" ·")} → ${ticket.destination.substringBefore(" ·")}",
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        val assigneePart = ticket.assignedTo?.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty()
                        Text(
                            text = "${ticket.replies.size} repl${if (ticket.replies.size == 1) "y" else "ies"}$assigneePart",
                            style = MaterialTheme.typography.labelSmall,
                            color = colorScheme.onSurfaceVariant
                        )
                    }
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
    val context = LocalContext.current
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
                Text(
                    text = "Ticket #${supportCase.id} · ${IssueCategory.fromKey(supportCase.category).label}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                val assignee = supportCase.assignedTo?.takeIf { it.isNotBlank() }?.let { " · Assigned to $it" }.orEmpty()
                Text(
                    text = "${supportCase.status.replace("_", " ").replaceFirstChar { it.uppercase() }}$assignee",
                    style = MaterialTheme.typography.labelMedium,
                    color = colorScheme.onSurfaceVariant
                )
            }
            if (supportCase.category.equals("safety", ignoreCase = true)) {
                Emergency112Chip(onClick = { dialEmergency112(context) })
            }
        }

        SupportCaseProgressStrip(
            status = supportCase.status,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
        )

        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.weight(1f)
        ) {
            if (!supportCase.staffNote.isNullOrBlank()) {
                item {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = colorScheme.primaryContainer.copy(alpha = 0.6f),
                        border = BorderStroke(1.dp, colorScheme.primary.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.padding(12.dp)) {
                            Text("Latest staff assessment", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = colorScheme.primary)
                            Text(supportCase.staffNote, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
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
                val isStaff = SupportNotificationHelper.isStaffSender(rep.senderLabel)
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

@Composable
private fun SupportCaseProgressStrip(
    status: String,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val stepIndex = when (status.lowercase()) {
        "resolved" -> 2
        "in_progress" -> 1
        else -> 0
    }
    val labels = listOf("Open", "In progress", "Resolved")
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        labels.forEachIndexed { idx, label ->
            val reached = idx <= stepIndex
            Column(
                verticalArrangement = Arrangement.spacedBy(3.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(if (reached) colorScheme.primary else colorScheme.surfaceVariant)
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (idx == stepIndex) FontWeight.Bold else FontWeight.Normal,
                    color = if (reached) colorScheme.onSurface else colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
