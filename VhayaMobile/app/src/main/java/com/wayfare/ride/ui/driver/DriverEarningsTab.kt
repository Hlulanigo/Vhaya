package com.wayfare.ride.ui.driver

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wayfare.ride.domain.LaunchPolicy
import com.wayfare.ride.domain.PaymentMethod
import com.wayfare.ride.ui.WayfareUiState
import com.wayfare.ride.ui.WayfareViewModel
import com.wayfare.ride.ui.components.MonoBadge
import com.wayfare.ride.ui.components.StatusChip
import com.wayfare.ride.ui.components.WayfareEmptyState
import com.wayfare.ride.ui.components.formatShortTime
import com.wayfare.ride.ui.components.tactilePressScale
import com.wayfare.ride.ui.theme.LocalWayfareColors
import com.wayfare.ride.ui.theme.WayfareMonoFamily
import kotlin.math.ceil

@Composable
fun DriverEarningsTab(
    uiState: WayfareUiState,
    viewModel: WayfareViewModel,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val wayfareColors = LocalWayfareColors.current
    val driverId = uiState.currentDriverApp?.userId ?: uiState.activeUserId

    val earnings = uiState.driverEarnings
    val completed = uiState.driverCompletedTrips
    val totalNetMinor = if (earnings.totalNetMinor > 0L || earnings.rideCount > 0) {
        earnings.totalNetMinor
    } else {
        (completed.sumOf { it.fare } * 100.0).toLong()
    }
    val tripCount = if (earnings.rideCount > 0) earnings.rideCount else completed.size
    val avgMinor = if (tripCount > 0) totalNetMinor / tripCount else 0L
    val cashZar = remember(completed) { completed.filter { it.paymentMethod == PaymentMethod.CASH }.sumOf { it.fare } }
    val digitalZar = remember(completed) { completed.filter { it.paymentMethod != PaymentMethod.CASH }.sumOf { it.fare } }

    val subscription = uiState.driverSubscription
    val passDaysLeft = remember(subscription) {
        val expiresAt = subscription?.expiresAt ?: return@remember 0
        val remainingMs = (expiresAt - System.currentTimeMillis()).coerceAtLeast(0L)
        ceil(remainingMs / 86_400_000.0).toInt()
    }

    val myPayouts = remember(uiState.payoutRecords, driverId) {
        uiState.payoutRecords.filter { driverId.isBlank() || it.driverId == driverId }
    }
    val latestBatch = uiState.payoutBatches.firstOrNull()

    LazyColumn(
        contentPadding = PaddingValues(top = 78.dp, bottom = 28.dp, start = 16.dp, end = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier.fillMaxSize()
    ) {
        item {
            Text("Earnings", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        }

        item {
            Card(
                shape = RoundedCornerShape(22.dp),
                border = BorderStroke(1.dp, wayfareColors.walletCardBorder.copy(alpha = 0.45f)),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            brush = Brush.linearGradient(
                                colors = listOf(wayfareColors.walletCardStart, wayfareColors.walletCardEnd)
                            )
                        )
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(18.dp)) {
                        Text(
                            text = "Net completed earnings",
                            style = MaterialTheme.typography.labelLarge,
                            color = wayfareColors.walletOnCard.copy(alpha = 0.82f)
                        )
                        Text(
                            text = LaunchPolicy.formatZarMinor(totalNetMinor),
                            fontFamily = WayfareMonoFamily,
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold,
                            color = wayfareColors.walletOnCard,
                            modifier = Modifier.testTag("driver_total_earnings")
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            Text(
                                text = "$tripCount completed trips",
                                style = MaterialTheme.typography.bodySmall,
                                color = wayfareColors.walletAccent
                            )
                            Text(
                                text = "Avg ${LaunchPolicy.formatZarMinor(avgMinor)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = wayfareColors.walletOnCard.copy(alpha = 0.9f)
                            )
                        }
                        HorizontalDivider(color = wayfareColors.walletOnCard.copy(alpha = 0.16f))
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Paid: ${LaunchPolicy.formatZarMinor(earnings.paidEarningsMinor)}",
                                style = MaterialTheme.typography.labelMedium,
                                color = wayfareColors.walletOnCard.copy(alpha = 0.92f)
                            )
                            Text(
                                text = "Outstanding: ${LaunchPolicy.formatZarMinor(earnings.outstandingEarningsMinor)}",
                                style = MaterialTheme.typography.labelMedium,
                                color = wayfareColors.walletOnCard.copy(alpha = 0.92f)
                            )
                        }
                        if (completed.isNotEmpty()) {
                            Row(
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "Card & Wallet: ${LaunchPolicy.formatZar(digitalZar)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = wayfareColors.walletOnCard.copy(alpha = 0.82f)
                                )
                                Text(
                                    text = "Cash: ${LaunchPolicy.formatZar(cashZar)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = wayfareColors.walletOnCard.copy(alpha = 0.82f)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Weekly Driver Pass Card
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
                border = BorderStroke(1.dp, colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.WorkspacePremium,
                                contentDescription = null,
                                tint = colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "Vhaya Weekly Pass",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        StatusChip(
                            label = if (uiState.driverWeeklyPlanActive) {
                                "Active · ${passDaysLeft}d left"
                            } else {
                                subscription?.status?.replaceFirstChar { it.uppercase() } ?: "Inactive"
                            },
                            containerColor = if (uiState.driverWeeklyPlanActive) {
                                wayfareColors.brandHighlight
                            } else {
                                colorScheme.surfaceVariant
                            },
                            contentColor = if (uiState.driverWeeklyPlanActive) {
                                colorScheme.onPrimaryContainer
                            } else {
                                colorScheme.onSurfaceVariant
                            }
                        )
                    }
                    Text(
                        text = if (uiState.driverWeeklyPlanActive) {
                            "Vhaya Weekly Driver Pass is active. Your trips earn 0% per-ride launch commission."
                        } else {
                            "Activate or renew your 7-day Weekly Driver Pass (R 199) via Paystack for zero per-ride commission."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = { viewModel.initializeDriverWeeklySubscription(19900L) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .tactilePressScale()
                            .testTag("driver_plan_renew")
                    ) {
                        Text(
                            text = if (uiState.driverWeeklyPlanActive) {
                                "Renew 7 days (R 199)"
                            } else {
                                "Activate 7-day pass (R 199)"
                            },
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Payouts & Weekly Settlement Batches
        if (myPayouts.isNotEmpty() || latestBatch != null) {
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Payouts & batches", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    latestBatch?.let { batch ->
                        StatusChip(
                            label = "${batch.resolvedPeriodLabel} · ${batch.status.replaceFirstChar { it.uppercase() }}",
                            containerColor = if (batch.status == "paid") wayfareColors.brandHighlight else colorScheme.secondaryContainer,
                            contentColor = colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            items(myPayouts, key = { it.id }) { payout ->
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = colorScheme.surface,
                    border = BorderStroke(1.dp, colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
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
                            Icon(
                                imageVector = Icons.Filled.AccountBalance,
                                contentDescription = null,
                                tint = colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = payout.resolvedPeriodLabel,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${payout.provider.uppercase()} · ${payout.externalReference}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        Spacer(Modifier.width(8.dp))
                        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            MonoBadge(text = LaunchPolicy.formatZarMinor(payout.amountMinor))
                            StatusChip(
                                label = payout.reconciliationStatus.replaceFirstChar { it.uppercase() },
                                containerColor = if (payout.reconciliationStatus == "matched") {
                                    wayfareColors.brandHighlight
                                } else {
                                    colorScheme.secondaryContainer
                                },
                                contentColor = colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }
        }

        item {
            Text("Trip ledger", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }

        if (earnings.records.isNotEmpty()) {
            items(earnings.records, key = { it.rideId }) { record ->
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = colorScheme.surface,
                    border = BorderStroke(1.dp, colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.padding(14.dp)
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = "${record.pickup.substringBefore(" ·")} → ${record.destination.substringBefore(" ·")}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${formatShortTime(record.completedAt)} · Fee ${LaunchPolicy.formatZarMinor(record.commissionMinor)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        MonoBadge(text = "+${LaunchPolicy.formatZarMinor(record.netMinor)}")
                    }
                }
            }
        } else if (completed.isEmpty()) {
            item {
                WayfareEmptyState(
                    icon = Icons.Filled.AccountBalance,
                    title = "No trip earnings yet",
                    message = "Completed rides and their fare breakdown will appear here."
                )
            }
        } else {
            items(completed, key = { it.id }) { trip ->
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = colorScheme.surface,
                    border = BorderStroke(1.dp, colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.padding(14.dp)
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = "${trip.pickup.substringBefore(" ·")} → ${trip.destination.substringBefore(" ·")}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = formatShortTime(trip.createdAt),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colorScheme.onSurfaceVariant
                                )
                                PaymentMethodPill(method = trip.paymentMethod)
                            }
                        }
                        Spacer(Modifier.width(8.dp))
                        MonoBadge(text = "+${LaunchPolicy.formatZar(trip.fare)}")
                    }
                }
            }
        }
    }
}
