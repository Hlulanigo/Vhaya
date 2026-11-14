package com.wayfare.ride.ui.admin

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wayfare.ride.domain.LaunchPolicy
import com.wayfare.ride.domain.RideRole
import com.wayfare.ride.ui.WayfareUiState
import com.wayfare.ride.ui.WayfareViewModel
import com.wayfare.ride.ui.components.MonoBadge
import com.wayfare.ride.ui.components.StatusChip
import com.wayfare.ride.ui.components.formatShortTime
import com.wayfare.ride.ui.rider.SupportThreadView
import com.wayfare.ride.ui.theme.LocalWayfareColors
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

private fun currentWeekIsoBounds(): Pair<String, String> {
    val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    val cal = Calendar.getInstance()
    cal.firstDayOfWeek = Calendar.MONDAY
    cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
    val start = fmt.format(cal.time)
    cal.add(Calendar.DAY_OF_YEAR, 6)
    val end = fmt.format(cal.time)
    return start to end
}

@Composable
fun AdminSupportTab(
    uiState: WayfareUiState,
    viewModel: WayfareViewModel,
    modifier: Modifier = Modifier
) {
    var selectedCaseId by remember { mutableStateOf<Long?>(null) }
    val selectedCase = uiState.supportCases.firstOrNull { it.id == selectedCaseId }
    val adminLabel = uiState.currentSession?.displayName?.ifBlank { "Vhaya Support" } ?: "Vhaya Support"

    if (selectedCase != null) {
        BackHandler { selectedCaseId = null }
        SupportThreadView(
            supportCase = selectedCase,
            senderLabel = adminLabel,
            onBack = { selectedCaseId = null },
            onSendReply = { msg -> viewModel.replyAdminSupportCase(selectedCase.id, msg) }
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
            Text("Support queue", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
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
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(14.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("#${ticket.id} · ${ticket.reporterName}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        StatusChip(
                            label = ticket.priority.uppercase(),
                            containerColor = if (ticket.priority == "urgent" || ticket.priority == "high") colorScheme.errorContainer else colorScheme.secondaryContainer,
                            contentColor = if (ticket.priority == "urgent" || ticket.priority == "high") colorScheme.error else colorScheme.onSecondaryContainer
                        )
                    }
                    Text(ticket.message, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("open", "in_progress", "resolved").forEach { status ->
                            FilterChip(
                                selected = ticket.status == status,
                                onClick = { viewModel.updateSupportCaseStatus(ticket.id, status) },
                                label = { Text(status.replace("_", " ")) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AdminPayoutsTab(
    uiState: WayfareUiState,
    viewModel: WayfareViewModel,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val wayfareColors = LocalWayfareColors.current
    val (defaultStartIso, defaultEndIso) = remember { currentWeekIsoBounds() }

    val eligibleDrivers = remember(uiState.userAccounts) {
        uiState.userAccounts.filter { it.role == RideRole.DRIVER && !it.isSuspended }
    }
    var selectedDriverId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(eligibleDrivers) {
        if (selectedDriverId == null || eligibleDrivers.none { it.userId == selectedDriverId }) {
            selectedDriverId = eligibleDrivers.firstOrNull()?.userId
        }
    }
    val selectedDriver = eligibleDrivers.firstOrNull { it.userId == selectedDriverId }

    var amountInput by remember { mutableStateOf("450") }
    var periodStartInput by remember { mutableStateOf(defaultStartIso) }
    var periodEndInput by remember { mutableStateOf(defaultEndIso) }
    var externalReferenceInput by remember { mutableStateOf("VH-${(System.currentTimeMillis() % 100000)}") }
    var batchStartInput by remember { mutableStateOf(defaultStartIso) }
    var batchEndInput by remember { mutableStateOf(defaultEndIso) }
    var provider by remember { mutableStateOf("paystack") }
    var transferConfirmed by remember { mutableStateOf(false) }

    val amountZar = amountInput.toDoubleOrNull() ?: 0.0
    val canRecordPayout = selectedDriver != null &&
        amountZar > 0.0 &&
        externalReferenceInput.isNotBlank() &&
        transferConfirmed

    LazyColumn(
        contentPadding = PaddingValues(top = 78.dp, bottom = 24.dp, start = 16.dp, end = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier.fillMaxSize()
    ) {
        item {
            Text("Driver payouts", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        }

        // Weekly Payout Batches section
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
                border = BorderStroke(1.dp, colorScheme.outlineVariant)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(16.dp)) {
                    Text("Weekly payout batches", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = batchStartInput,
                            onValueChange = { batchStartInput = it },
                            label = { Text("Period start") },
                            placeholder = { Text("YYYY-MM-DD") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = batchEndInput,
                            onValueChange = { batchEndInput = it },
                            label = { Text("Period end") },
                            placeholder = { Text("YYYY-MM-DD") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Button(
                        onClick = {
                            viewModel.runWeeklyPayoutBatch(
                                batchStartInput.trim().ifBlank { defaultStartIso },
                                batchEndInput.trim().ifBlank { defaultEndIso }
                            )
                        },
                        modifier = Modifier.fillMaxWidth().height(50.dp).testTag("admin_run_batch_btn")
                    ) {
                        Text("Run weekly batch", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        items(uiState.payoutBatches, key = { it.id }) { batch ->
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = colorScheme.surface,
                border = BorderStroke(1.dp, colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(14.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(batch.resolvedPeriodLabel, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text(
                                text = "${batch.driverCount} drivers · ${formatShortTime(batch.createdAt)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = colorScheme.onSurfaceVariant
                            )
                        }
                        MonoBadge(
                            text = LaunchPolicy.formatZarMinor(batch.totalAmountMinor),
                            containerColor = if (batch.status == "paid" || batch.status == "reconciled") wayfareColors.brandHighlight else colorScheme.secondaryContainer,
                            contentColor = colorScheme.onPrimaryContainer
                        )
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.horizontalScroll(rememberScrollState())
                    ) {
                        listOf("open", "submitted", "paid", "reconciled").forEach { st ->
                            FilterChip(
                                selected = batch.status == st,
                                onClick = { viewModel.updatePayoutBatchStatus(batch.id, st) },
                                label = { Text(st.replaceFirstChar { it.uppercase() }) }
                            )
                        }
                    }
                }
            }
        }

        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
                border = BorderStroke(1.dp, colorScheme.outlineVariant)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = selectedDriver?.let { "Record payout · ${it.displayName}" } ?: "Record individual payout",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    if (eligibleDrivers.isEmpty()) {
                        Text(
                            text = "No active non-suspended drivers found on backend.",
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.onSurfaceVariant
                        )
                    } else {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.horizontalScroll(rememberScrollState())
                        ) {
                            eligibleDrivers.forEach { driver ->
                                FilterChip(
                                    selected = selectedDriverId == driver.userId,
                                    onClick = { selectedDriverId = driver.userId },
                                    label = { Text(driver.displayName) }
                                )
                            }
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = amountInput,
                            onValueChange = { s -> amountInput = s.filter { it.isDigit() || it == '.' } },
                            label = { Text("Amount (R)") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = externalReferenceInput,
                            onValueChange = { externalReferenceInput = it },
                            label = { Text("Transfer ref") },
                            singleLine = true,
                            modifier = Modifier.weight(1.3f)
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = periodStartInput,
                            onValueChange = { periodStartInput = it },
                            label = { Text("Period start") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = periodEndInput,
                            onValueChange = { periodEndInput = it },
                            label = { Text("Period end") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("paystack" to "Paystack", "bank_transfer" to "Bank EFT", "cash" to "Cash").forEach { (key, label) ->
                            FilterChip(
                                selected = provider == key,
                                onClick = { provider = key },
                                label = { Text(label) }
                            )
                        }
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { transferConfirmed = !transferConfirmed }
                    ) {
                        Checkbox(checked = transferConfirmed, onCheckedChange = { transferConfirmed = it })
                        Text("I confirm this external transfer has been sent", style = MaterialTheme.typography.bodySmall)
                    }
                    Button(
                        onClick = {
                            val targetDriver = selectedDriver ?: return@Button
                            viewModel.recordPayout(
                                driverId = targetDriver.userId,
                                provider = provider,
                                externalReference = externalReferenceInput.trim(),
                                amountZar = amountZar,
                                periodStart = periodStartInput.trim().ifBlank { null },
                                periodEnd = periodEndInput.trim().ifBlank { null },
                                note = "Recorded via Vhaya Admin console"
                            )
                            transferConfirmed = false
                            externalReferenceInput = "VH-${(System.currentTimeMillis() % 100000)}"
                        },
                        enabled = canRecordPayout,
                        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("admin_record_payout_btn")
                    ) {
                        Text("Record payout", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        items(uiState.payoutRecords, key = { it.id }) { payout ->
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = colorScheme.surface,
                border = BorderStroke(1.dp, colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(14.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${payout.driverName.ifBlank { payout.driverId }} · ${payout.resolvedPeriodLabel}",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${payout.externalReference} · ${formatShortTime(payout.recordedAt)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = colorScheme.onSurfaceVariant
                            )
                        }
                        MonoBadge(text = LaunchPolicy.formatZarMinor(payout.amountMinor))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("unreconciled" to "Unreconciled", "matched" to "Matched", "exception" to "Exception").forEach { (st, lbl) ->
                            FilterChip(
                                selected = payout.reconciliationStatus == st,
                                onClick = { viewModel.reconcilePayout(payout.id, st) },
                                label = { Text(lbl) }
                            )
                        }
                    }
                }
            }
        }
    }
}
