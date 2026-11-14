package com.wayfare.ride.ui.admin

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import com.wayfare.ride.domain.ComplianceStatus
import com.wayfare.ride.domain.DriverApplication
import com.wayfare.ride.domain.DriverDocumentType
import com.wayfare.ride.domain.LaunchPolicy
import com.wayfare.ride.domain.RideRole
import com.wayfare.ride.domain.RideTrip
import com.wayfare.ride.domain.UserAccountSummary
import com.wayfare.ride.ui.WayfareUiState
import com.wayfare.ride.ui.WayfareViewModel
import com.wayfare.ride.ui.components.MonoBadge
import com.wayfare.ride.ui.components.StatusChip
import com.wayfare.ride.ui.components.WayfareConfirmDialog
import com.wayfare.ride.ui.components.formatShortTime
import com.wayfare.ride.ui.theme.LocalWayfareColors
import com.wayfare.ride.ui.theme.WayfareMonoFamily

@Composable
fun AdminOverviewTab(
    uiState: WayfareUiState,
    viewModel: WayfareViewModel,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    var userSearch by remember { mutableStateOf("") }
    var pendingDecisionUser by remember { mutableStateOf<Pair<UserAccountSummary, Boolean>?>(null) }
    var selectedPersonId by remember { mutableStateOf<String?>(null) }

    val activeRides = maxOf(uiState.adminOverview.activeRides, uiState.trips.count { it.status.isActive })
    val onlineDrivers = uiState.driverApplications.count { it.isOnline && it.verificationStatus == "approved" }
    val pendingDocs = maxOf(
        uiState.adminOverview.pendingDriverReviews,
        uiState.driverApplications.count { it.verificationStatus != "approved" }
    )
    val openSupport = maxOf(
        uiState.adminOverview.activeSupportCases,
        uiState.supportCases.count { it.status != "resolved" }
    )

    val filteredUsers = remember(uiState.userAccounts, userSearch) {
        val q = userSearch.trim().lowercase()
        if (q.isEmpty()) uiState.userAccounts
        else uiState.userAccounts.filter {
            it.displayName.lowercase().contains(q) || it.email.lowercase().contains(q)
        }
    }

    val selectedPerson = uiState.userAccounts.firstOrNull { it.userId == selectedPersonId }

    LazyColumn(
        contentPadding = PaddingValues(top = 78.dp, bottom = 24.dp, start = 16.dp, end = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier.fillMaxSize()
    ) {
        item {
            Text("Operations", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    KpiCard("Active rides", activeRides.toString(), Modifier.weight(1f))
                    KpiCard("Online drivers", onlineDrivers.toString(), Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    KpiCard("Pending drivers", pendingDocs.toString(), Modifier.weight(1f))
                    KpiCard("Open support", openSupport.toString(), Modifier.weight(1f))
                }
            }
        }

        // Activity Feed
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(Icons.Filled.History, contentDescription = null, tint = colorScheme.primary, modifier = Modifier.size(18.dp))
                Text("Activity feed", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
        }

        if (uiState.activityLogs.isEmpty()) {
            item {
                Text("Recent platform events appear here.", style = MaterialTheme.typography.bodySmall, color = colorScheme.onSurfaceVariant)
            }
        } else {
            items(uiState.activityLogs.take(6), key = { it.id }) { log ->
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = colorScheme.surface,
                    border = BorderStroke(1.dp, colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(log.summary, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            Text("${log.actor} · ${formatShortTime(log.createdAt)}", style = MaterialTheme.typography.labelSmall, color = colorScheme.onSurfaceVariant)
                        }
                        StatusChip(
                            label = log.kind.replace("_", " "),
                            containerColor = colorScheme.secondaryContainer,
                            contentColor = colorScheme.onSecondaryContainer
                        )
                    }
                }
            }
        }

        item {
            Text("People & access", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = userSearch,
                onValueChange = { userSearch = it },
                placeholder = { Text("Search name or email...") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().testTag("admin_user_search")
            )
        }

        items(filteredUsers, key = { it.userId }) { user ->
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
                border = BorderStroke(1.dp, colorScheme.outlineVariant),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { selectedPersonId = user.userId }
                    .testTag("admin_user_card_${user.userId}")
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(14.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(user.displayName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text("${user.email} · ${user.rideCount} rides", style = MaterialTheme.typography.bodySmall, color = colorScheme.onSurfaceVariant)
                        }
                        StatusChip(
                            label = if (user.isSuspended) "Suspended" else user.role.label,
                            containerColor = if (user.isSuspended) colorScheme.errorContainer else colorScheme.secondaryContainer,
                            contentColor = if (user.isSuspended) colorScheme.error else colorScheme.onSecondaryContainer
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        RideRole.entries.forEach { role ->
                            FilterChip(
                                selected = user.role == role,
                                onClick = { viewModel.updateUserRoleOrSuspension(user.userId, role = role) },
                                label = { Text(role.label) }
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        OutlinedButton(
                            onClick = { pendingDecisionUser = user to !user.isSuspended },
                            contentPadding = PaddingValues(horizontal = 10.dp),
                            modifier = Modifier.height(48.dp)
                        ) {
                            Text(if (user.isSuspended) "Restore" else "Suspend")
                        }
                    }
                }
            }
        }
    }

    selectedPerson?.let { person ->
        val personTrips = remember(person.userId, uiState.trips) {
            uiState.trips.filter { it.riderId == person.userId || it.driverId == person.userId }
        }
        PersonHistoryDetailSheet(
            person = person,
            trips = personTrips,
            onDismiss = { selectedPersonId = null }
        )
    }

    pendingDecisionUser?.let { (user, suspend) ->
        WayfareConfirmDialog(
            title = if (suspend) "Suspend ${user.displayName}?" else "Restore ${user.displayName}?",
            message = if (suspend) "Suspending blocks ride requests and dispatch access." else "Restore normal Vhaya access.",
            confirmLabel = if (suspend) "Suspend" else "Restore",
            isDestructive = suspend,
            onConfirm = {
                viewModel.updateUserRoleOrSuspension(
                    user.userId,
                    suspend = suspend,
                    reason = if (suspend) "Admin review hold" else null
                )
                pendingDecisionUser = null
            },
            onDismiss = { pendingDecisionUser = null }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PersonHistoryDetailSheet(
    person: UserAccountSummary,
    trips: List<RideTrip>,
    onDismiss: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val wayfareColors = LocalWayfareColors.current

    BackHandler { onDismiss() }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 560.dp)
                .padding(horizontal = 18.dp)
                .padding(bottom = 24.dp)
        ) {
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(person.displayName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(person.email, style = MaterialTheme.typography.bodySmall, color = colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Filled.Close, contentDescription = "Close detail")
                    }
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatusChip(
                        label = person.role.label,
                        containerColor = wayfareColors.brandHighlight,
                        contentColor = colorScheme.onPrimaryContainer
                    )
                    StatusChip(
                        label = "${person.rideCount} rides · ${person.completedRideCount} completed",
                        containerColor = colorScheme.secondaryContainer,
                        contentColor = colorScheme.onSecondaryContainer
                    )
                }
                person.suspensionReason?.takeIf { person.isSuspended }?.let { reason ->
                    Spacer(Modifier.height(6.dp))
                    Text("Suspension reason: $reason", style = MaterialTheme.typography.bodySmall, color = colorScheme.error)
                }
            }

            item {
                Text("Recent trip history", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            }

            if (trips.isEmpty()) {
                item {
                    Text("No trips linked to this account yet.", style = MaterialTheme.typography.bodySmall, color = colorScheme.onSurfaceVariant)
                }
            } else {
                items(trips, key = { it.id }) { trip ->
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${trip.pickup.substringBefore(" ·")} → ${trip.destination.substringBefore(" ·")}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text("${trip.status.label} · ${formatShortTime(trip.createdAt)}", style = MaterialTheme.typography.labelSmall, color = colorScheme.onSurfaceVariant)
                            }
                            MonoBadge(text = LaunchPolicy.formatZar(trip.fare))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun KpiCard(title: String, value: String, modifier: Modifier = Modifier) {
    val colorScheme = MaterialTheme.colorScheme
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
        border = BorderStroke(1.dp, colorScheme.outlineVariant),
        modifier = modifier
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(14.dp)) {
            Text(
                text = value,
                fontFamily = WayfareMonoFamily,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = colorScheme.primary
            )
            Text(title, style = MaterialTheme.typography.labelMedium, color = colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun AdminDriversTab(
    uiState: WayfareUiState,
    viewModel: WayfareViewModel,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val wayfareColors = LocalWayfareColors.current
    var pendingDecision by remember { mutableStateOf<Pair<DriverApplication, String>?>(null) }
    var reviewNoteInput by remember { mutableStateOf("") }
    val reviewerName = uiState.currentSession?.displayName?.ifBlank { "Operations Admin" } ?: "Operations Admin"

    LazyColumn(
        contentPadding = PaddingValues(top = 78.dp, bottom = 24.dp, start = 16.dp, end = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier.fillMaxSize()
    ) {
        item {
            Text("Driver compliance", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        }

        items(uiState.driverApplications, key = { it.userId }) { driver ->
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
                border = BorderStroke(1.dp, colorScheme.outlineVariant)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(driver.displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text("${driver.vehicle} · ${driver.registration}", style = MaterialTheme.typography.bodySmall, color = colorScheme.onSurfaceVariant)
                        }
                        StatusChip(
                            label = driver.verificationStatus.replaceFirstChar { it.uppercase() },
                            containerColor = if (driver.verificationStatus == "approved") wayfareColors.brandHighlight else colorScheme.secondaryContainer,
                            contentColor = colorScheme.onPrimaryContainer
                        )
                    }

                    HorizontalDivider()

                    DriverDocumentType.entries.forEach { docType ->
                        val check = driver.complianceChecks.firstOrNull { it.documentType == docType }
                        val currentStatus = check?.status ?: ComplianceStatus.NOT_REVIEWED
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(docType.title, style = MaterialTheme.typography.labelLarge)
                                Text(currentStatus.label, style = MaterialTheme.typography.labelSmall, color = colorScheme.onSurfaceVariant)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf(
                                    ComplianceStatus.VERIFIED to "Verify",
                                    ComplianceStatus.NEEDS_FOLLOW_UP to "Follow-up",
                                    ComplianceStatus.NOT_REQUIRED to "Exempt"
                                ).forEach { (status, label) ->
                                    FilterChip(
                                        selected = currentStatus == status,
                                        onClick = { viewModel.updateComplianceCheck(driver.userId, docType, status, null) },
                                        label = { Text(label) }
                                    )
                                }
                            }
                        }
                    }

                    Text(
                        text = "Manual email check (${com.wayfare.ride.domain.LaunchPolicy.DRIVER_DOCS_EMAIL}): verify emailed documents before approving.",
                        style = MaterialTheme.typography.labelSmall,
                        color = colorScheme.onSurfaceVariant
                    )

                    HorizontalDivider()

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = {
                                reviewNoteInput = driver.staffNote.orEmpty()
                                pendingDecision = driver to "needs_follow_up"
                            },
                            modifier = Modifier.weight(1f).height(48.dp)
                        ) {
                            Text("Follow-up")
                        }
                        OutlinedButton(
                            onClick = {
                                reviewNoteInput = driver.staffNote.orEmpty()
                                pendingDecision = driver to "rejected"
                            },
                            modifier = Modifier.weight(1f).height(48.dp)
                        ) {
                            Text("Reject", color = colorScheme.error)
                        }
                        Button(
                            onClick = {
                                reviewNoteInput = "Approved by $reviewerName"
                                pendingDecision = driver to "approved"
                            },
                            modifier = Modifier.weight(1f).height(48.dp).testTag("admin_approve_${driver.userId}")
                        ) {
                            Text("Approve")
                        }
                    }
                }
            }
        }
    }

    pendingDecision?.let { (driver, decision) ->
        val noteRequired = decision == "rejected" || decision == "needs_follow_up"
        val canConfirm = !noteRequired || reviewNoteInput.trim().isNotEmpty()
        AlertDialog(
            onDismissRequest = { pendingDecision = null },
            title = { Text("Set ${driver.displayName} to $decision?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = if (noteRequired) {
                            "A review note is required so the driver sees what to fix."
                        } else {
                            "This updates live driver online eligibility across Vhaya."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = reviewNoteInput,
                        onValueChange = { reviewNoteInput = it },
                        label = { Text(if (noteRequired) "Review note (required)" else "Review note") },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth().testTag("admin_driver_review_note_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val finalNote = reviewNoteInput.trim().ifBlank { "Reviewed by $reviewerName" }
                        viewModel.decideDriverApplication(driver.userId, decision, finalNote)
                        pendingDecision = null
                    },
                    enabled = canConfirm,
                    colors = if (decision == "rejected") {
                        ButtonDefaults.buttonColors(containerColor = colorScheme.error, contentColor = colorScheme.onError)
                    } else {
                        ButtonDefaults.buttonColors()
                    },
                    modifier = Modifier.testTag("admin_driver_decision_confirm")
                ) {
                    Text("Confirm")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDecision = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}
