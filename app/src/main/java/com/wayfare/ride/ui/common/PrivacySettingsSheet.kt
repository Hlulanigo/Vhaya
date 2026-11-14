package com.wayfare.ride.ui.common

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wayfare.ride.domain.PopiaDataExporter
import com.wayfare.ride.ui.WayfareUiState
import com.wayfare.ride.ui.WayfareViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacySettingsSheet(
    uiState: WayfareUiState,
    viewModel: WayfareViewModel,
    onDismiss: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val privacy = uiState.privacyPreferences

    var pendingExportJson by remember { mutableStateOf<String?>(null) }
    var showDeletionDialog by remember { mutableStateOf(false) }
    var deletionReason by remember { mutableStateOf("") }

    val saveFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        val json = pendingExportJson
        if (uri != null && json != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(json.toByteArray(Charsets.UTF_8))
                }
            }
        }
        pendingExportJson = null
    }

    BackHandler { onDismiss() }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 660.dp)
                .padding(horizontal = 18.dp)
                .padding(bottom = 26.dp)
        ) {
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Filled.PrivacyTip, contentDescription = null, tint = colorScheme.primary)
                        Column {
                            Text("Privacy & POPIA", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text("Protection of Personal Information Act (SA)", style = MaterialTheme.typography.labelSmall, color = colorScheme.onSurfaceVariant)
                        }
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Filled.Close, contentDescription = "Close privacy settings")
                    }
                }
            }

            item {
                PrivacyToggleRow(
                    icon = Icons.Filled.GpsFixed,
                    title = "Live location sharing",
                    subtitle = "Allow foreground GPS updates for pickups and active trips",
                    checked = privacy.locationSharingConsent,
                    onCheckedChange = { viewModel.setLocationSharingConsent(it) },
                    testTag = "privacy_location_consent_switch"
                )
            }

            item {
                PrivacyToggleRow(
                    icon = Icons.Filled.PrivacyTip,
                    title = "Trip-only privacy mode",
                    subtitle = "Share background driver GPS only while a ride is actively in progress",
                    checked = privacy.tripOnlyPrivacyMode,
                    onCheckedChange = { viewModel.setTripOnlyPrivacyMode(it) },
                    testTag = "privacy_trip_only_switch"
                )
            }

            item {
                PrivacyToggleRow(
                    icon = Icons.Filled.NotificationsActive,
                    title = "Support case notifications",
                    subtitle = "Notify when Vhaya Support replies or updates your ticket status",
                    checked = privacy.supportNotificationsEnabled,
                    onCheckedChange = { viewModel.setSupportNotificationsEnabled(it) },
                    testTag = "privacy_notifications_switch"
                )
            }

            item {
                HorizontalDivider()
                Spacer(Modifier.height(4.dp))
                Text("Data retention schedule", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            }

            items(PopiaDataExporter.RETENTION_SCHEDULE) { item ->
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    border = BorderStroke(1.dp, colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(item.category, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            Text(item.retentionPeriod, style = MaterialTheme.typography.labelMedium, color = colorScheme.primary, fontWeight = FontWeight.Bold)
                        }
                        Text(item.legalBasis, style = MaterialTheme.typography.bodySmall, color = colorScheme.onSurfaceVariant)
                    }
                }
            }

            item {
                HorizontalDivider()
                Spacer(Modifier.height(4.dp))
                Text("Your POPIA rights", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedButton(
                        onClick = {
                            viewModel.exportPopiaDataJson { json ->
                                pendingExportJson = json
                                saveFileLauncher.launch(PopiaDataExporter.suggestedFileName())
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .testTag("privacy_export_data_btn")
                    ) {
                        Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Save JSON")
                    }
                    OutlinedButton(
                        onClick = {
                            viewModel.exportPopiaDataJson { json ->
                                PopiaDataExporter.shareExportFile(context, json)
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .testTag("privacy_share_export_btn")
                    ) {
                        Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Share export")
                    }
                }
            }

            item {
                Button(
                    onClick = { showDeletionDialog = true },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colorScheme.errorContainer,
                        contentColor = colorScheme.error
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("privacy_delete_account_btn")
                ) {
                    Icon(Icons.Filled.DeleteForever, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Delete data or request erasure", fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    if (showDeletionDialog) {
        AlertDialog(
            onDismissRequest = { showDeletionDialog = false },
            title = { Text("POPIA data & account erasure") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "You can log an official POPIA erasure request with Vhaya Support and/or wipe all locally cached places, contacts, and session data from this device."
                    )
                    OutlinedTextField(
                        value = deletionReason,
                        onValueChange = { deletionReason = it },
                        label = { Text("Reason (optional)") },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.requestPopiaDataDeletion(deletionReason)
                        showDeletionDialog = false
                    },
                    modifier = Modifier.testTag("privacy_confirm_ticket_delete")
                ) {
                    Text("Submit request")
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TextButton(
                        onClick = {
                            showDeletionDialog = false
                            onDismiss()
                            viewModel.clearAllLocalUserDataAndSignOut()
                        },
                        modifier = Modifier.testTag("privacy_wipe_local_btn")
                    ) {
                        Text("Wipe device & sign out", color = colorScheme.error)
                    }
                    TextButton(onClick = { showDeletionDialog = false }) {
                        Text("Cancel")
                    }
                }
            }
        )
    }
}

@Composable
private fun PrivacyToggleRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String
) {
    val colorScheme = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = colorScheme.surfaceVariant.copy(alpha = 0.45f),
        border = BorderStroke(1.dp, colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(icon, contentDescription = null, tint = colorScheme.primary, modifier = Modifier.size(20.dp))
                Column {
                    Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.width(8.dp))
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                modifier = Modifier.testTag(testTag)
            )
        }
    }
}
