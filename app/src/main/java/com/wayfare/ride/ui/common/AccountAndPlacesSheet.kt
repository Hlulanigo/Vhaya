package com.wayfare.ride.ui.common

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wayfare.ride.domain.RideRole
import com.wayfare.ride.ui.WayfareUiState
import com.wayfare.ride.ui.WayfareViewModel
import com.wayfare.ride.ui.components.StatusChip
import com.wayfare.ride.ui.driver.DriverProfileTab
import com.wayfare.ride.ui.theme.AppThemeMode
import com.wayfare.ride.ui.theme.LocalWayfareColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountAndPlacesSheet(
    uiState: WayfareUiState,
    viewModel: WayfareViewModel,
    onDismiss: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val wayfareColors = LocalWayfareColors.current
    val session = uiState.currentSession
    var showDriverApplicationSheet by rememberSaveable { mutableStateOf(false) }

    BackHandler {
        if (showDriverApplicationSheet) showDriverApplicationSheet = false else onDismiss()
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        if (showDriverApplicationSheet) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 660.dp)
                    .imePadding()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "Drive with Vhaya",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = { showDriverApplicationSheet = false }, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Filled.Close, contentDescription = "Back to account")
                    }
                }
                DriverProfileTab(
                    uiState = uiState,
                    viewModel = viewModel,
                    topContentPaddingDp = 8.dp
                )
            }
            return@ModalBottomSheet
        }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 640.dp)
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
                        Text(session?.displayName ?: "Vhaya Account", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(session?.email.orEmpty(), style = MaterialTheme.typography.bodySmall, color = colorScheme.onSurfaceVariant)
                    }
                    StatusChip(
                        label = session?.role?.label ?: "Guest",
                        containerColor = wayfareColors.brandHighlight,
                        contentColor = colorScheme.onPrimaryContainer
                    )
                    IconButton(onClick = { viewModel.refreshNow() }, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh account data")
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Filled.Close, contentDescription = "Close account")
                    }
                }
            }

            if (session?.role == RideRole.RIDER) {
                item {
                    HorizontalDivider()
                    Spacer(Modifier.height(6.dp))
                    OutlinedButton(
                        onClick = { showDriverApplicationSheet = true },
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth().height(50.dp).testTag("apply_to_drive_btn")
                    ) {
                        Icon(Icons.Filled.DirectionsCar, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        val statusLabel = uiState.currentDriverApp?.applicationStatus?.let {
                            if (it == "draft") "Apply to drive with Vhaya" else "Driver application (${it.replace("_", " ")})"
                        } ?: "Apply to drive with Vhaya"
                        Text(statusLabel, fontWeight = FontWeight.SemiBold)
                    }
                }

                item {
                    HorizontalDivider()
                    Spacer(Modifier.height(6.dp))
                    Text("Saved places", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                }
                if (uiState.savedDestinations.isEmpty()) {
                    item {
                        Text("Tap the bookmark icon when searching to save favourite places.", style = MaterialTheme.typography.bodySmall, color = colorScheme.onSurfaceVariant)
                    }
                } else {
                    items(uiState.savedDestinations, key = { it.id }) { place ->
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            border = BorderStroke(1.dp, colorScheme.outlineVariant),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Icon(Icons.Filled.Bookmark, contentDescription = null, tint = colorScheme.primary, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(10.dp))
                                Text(place.label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                IconButton(
                                    onClick = { viewModel.deleteSavedPlace(place.id) },
                                    modifier = Modifier.size(44.dp)
                                ) {
                                    Icon(Icons.Filled.DeleteOutline, contentDescription = "Delete saved place", tint = colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }

            item {
                HorizontalDivider()
                Spacer(Modifier.height(6.dp))
                Text("Appearance", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    AppThemeMode.entries.forEach { mode ->
                        val selected = uiState.themeMode == mode
                        val icon = when (mode) {
                            AppThemeMode.DARK -> Icons.Filled.DarkMode
                            AppThemeMode.SYSTEM -> Icons.Filled.BrightnessAuto
                            AppThemeMode.LIGHT -> Icons.Filled.LightMode
                        }
                        FilterChip(
                            selected = selected,
                            onClick = { viewModel.setThemeMode(mode) },
                            label = { Text(mode.label, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium) },
                            leadingIcon = {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = "${mode.label} theme",
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = wayfareColors.brandHighlight,
                                selectedLabelColor = colorScheme.onPrimaryContainer,
                                selectedLeadingIconColor = colorScheme.primary
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("theme_mode_${mode.key}")
                        )
                    }
                }
            }

            item {
                OutlinedButton(
                    onClick = {
                        onDismiss()
                        viewModel.setPrivacySheetVisible(true)
                    },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("account_privacy_btn")
                ) {
                    Icon(Icons.Filled.PrivacyTip, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Privacy & POPIA controls", fontWeight = FontWeight.SemiBold)
                }
            }

            item {
                Button(
                    onClick = { viewModel.signOut() },
                    colors = ButtonDefaults.buttonColors(containerColor = colorScheme.errorContainer, contentColor = colorScheme.error),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().height(50.dp).testTag("account_sign_out_btn")
                ) {
                    Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Sign out", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
