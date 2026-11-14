package com.wayfare.ride.ui.payments

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wayfare.ride.domain.LaunchPolicy
import com.wayfare.ride.domain.PaymentMethod
import com.wayfare.ride.domain.PaymentStatus
import com.wayfare.ride.domain.RideStatus
import com.wayfare.ride.domain.RideTrip
import com.wayfare.ride.ui.components.MonoBadge
import com.wayfare.ride.ui.components.StatusChip
import com.wayfare.ride.ui.components.tactilePressScale
import com.wayfare.ride.ui.theme.LocalWayfareColors

enum class PaymentConfirmationTone {
    CONFIRMED,
    ACTION_REQUIRED,
    PENDING,
    FAILED,
    CASH_DUE
}

data class PaymentConfirmationUiModel(
    val tone: PaymentConfirmationTone,
    val badgeLabel: String,
    val compactPillLabel: String,
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val isConfirmed: Boolean
)

fun resolvePaymentConfirmationUi(
    trip: RideTrip,
    isDriver: Boolean,
    counterpartyName: String? = null
): PaymentConfirmationUiModel {
    val fareLabel = LaunchPolicy.formatZar(trip.fare)
    val riderLabel = counterpartyName?.takeIf { it.isNotBlank() } ?: "the rider"
    val driverLabel = counterpartyName?.takeIf { it.isNotBlank() } ?: trip.driverProfile?.name?.takeIf { it.isNotBlank() } ?: "your driver"

    return when (trip.paymentMethod) {
        PaymentMethod.CASH -> PaymentConfirmationUiModel(
            tone = PaymentConfirmationTone.CASH_DUE,
            badgeLabel = "Cash ride",
            compactPillLabel = "Cash · $fareLabel",
            title = if (isDriver) "Cash ride · Collect $fareLabel" else "Cash payment · $fareLabel",
            subtitle = if (isDriver) {
                "Collect $fareLabel cash directly from $riderLabel at drop-off."
            } else {
                "Pay $fareLabel directly to $driverLabel at drop-off."
            },
            icon = Icons.Filled.Payments,
            isConfirmed = true
        )

        PaymentMethod.WALLET -> {
            val paid = trip.paymentStatus == PaymentStatus.PAID
            PaymentConfirmationUiModel(
                tone = PaymentConfirmationTone.CONFIRMED,
                badgeLabel = "Confirmed",
                compactPillLabel = "Paid · Wallet",
                title = if (paid) {
                    "Payment confirmed · Vhaya Wallet"
                } else {
                    "Payment confirmed · Wallet reserved"
                },
                subtitle = if (isDriver) {
                    "$fareLabel secured in $riderLabel's Vhaya Wallet — settles automatically on completion."
                } else if (paid) {
                    "$fareLabel paid from your Vhaya Wallet."
                } else {
                    "$fareLabel reserved from your Vhaya Wallet and settled automatically on trip completion."
                },
                icon = Icons.Filled.CheckCircle,
                isConfirmed = true
            )
        }

        PaymentMethod.CARD -> when (trip.paymentStatus) {
            PaymentStatus.AUTHORIZED -> PaymentConfirmationUiModel(
                tone = PaymentConfirmationTone.CONFIRMED,
                badgeLabel = "Confirmed",
                compactPillLabel = "Confirmed · Card",
                title = if (isDriver) {
                    "Payment confirmed · Card hold active"
                } else {
                    "Fare reserved on card · $fareLabel"
                },
                subtitle = if (isDriver) {
                    "$fareLabel authorized via Paystack and held for capture when the trip completes."
                } else {
                    "Paystack card hold active · auto-released if cancelled."
                },
                icon = Icons.Filled.CheckCircle,
                isConfirmed = true
            )

            PaymentStatus.PAID -> PaymentConfirmationUiModel(
                tone = PaymentConfirmationTone.CONFIRMED,
                badgeLabel = "Paid",
                compactPillLabel = "Paid · Card",
                title = if (isDriver) {
                    "Payment confirmed · Fare captured"
                } else {
                    "Card payment confirmed · $fareLabel"
                },
                subtitle = if (isDriver) {
                    "$fareLabel Paystack card payment verified and settled."
                } else {
                    "Paystack receipt verified."
                },
                icon = Icons.Filled.CheckCircle,
                isConfirmed = true
            )

            PaymentStatus.PENDING, PaymentStatus.CAPTURE_PENDING -> PaymentConfirmationUiModel(
                tone = PaymentConfirmationTone.PENDING,
                badgeLabel = "Pending",
                compactPillLabel = "Payment pending",
                title = if (isDriver) {
                    "Payment pending · $fareLabel"
                } else {
                    "Complete Paystack payment · $fareLabel"
                },
                subtitle = if (isDriver) {
                    "$riderLabel started Paystack checkout — confirming card authorization..."
                } else {
                    "Finish checkout in Paystack, then tap Verify payment below."
                },
                icon = Icons.Filled.Schedule,
                isConfirmed = false
            )

            PaymentStatus.FAILED, PaymentStatus.CAPTURE_FAILED, PaymentStatus.EXPIRED -> PaymentConfirmationUiModel(
                tone = PaymentConfirmationTone.FAILED,
                badgeLabel = "Failed",
                compactPillLabel = "Payment failed",
                title = if (isDriver) {
                    "Payment failed · Awaiting retry"
                } else {
                    "Card payment failed · Try again"
                },
                subtitle = if (isDriver) {
                    "$riderLabel's card authorization did not go through. Waiting for them to retry."
                } else {
                    "Your card hold did not go through. Tap Retry Pay Now to complete payment."
                },
                icon = Icons.Filled.ErrorOutline,
                isConfirmed = false
            )

            else -> PaymentConfirmationUiModel(
                tone = PaymentConfirmationTone.ACTION_REQUIRED,
                badgeLabel = if (isDriver) "Awaiting pay" else "Pay now",
                compactPillLabel = if (isDriver) "Awaiting pay" else "Pay $fareLabel",
                title = if (isDriver) {
                    "Awaiting rider payment · $fareLabel"
                } else {
                    "Pay $fareLabel to confirm ride"
                },
                subtitle = if (isDriver) {
                    "Waiting for $riderLabel to authorize $fareLabel via Paystack."
                } else {
                    "Driver accepted your fare. Complete Paystack checkout to confirm your ride."
                },
                icon = Icons.Filled.CreditCard,
                isConfirmed = false
            )
        }
    }
}

@Composable
fun CompactPaymentStatusPill(
    trip: RideTrip,
    isDriver: Boolean,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val wayfareColors = LocalWayfareColors.current
    val model = resolvePaymentConfirmationUi(trip, isDriver = isDriver)

    val containerColor = when (model.tone) {
        PaymentConfirmationTone.CONFIRMED -> wayfareColors.positive.copy(alpha = 0.16f)
        PaymentConfirmationTone.PENDING, PaymentConfirmationTone.ACTION_REQUIRED -> wayfareColors.warning.copy(alpha = 0.16f)
        PaymentConfirmationTone.FAILED -> colorScheme.errorContainer
        PaymentConfirmationTone.CASH_DUE -> colorScheme.secondaryContainer
    }
    val contentColor = when (model.tone) {
        PaymentConfirmationTone.CONFIRMED -> wayfareColors.positive
        PaymentConfirmationTone.PENDING, PaymentConfirmationTone.ACTION_REQUIRED -> wayfareColors.warning
        PaymentConfirmationTone.FAILED -> colorScheme.error
        PaymentConfirmationTone.CASH_DUE -> colorScheme.onSecondaryContainer
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = containerColor,
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Icon(
                imageVector = when {
                    model.tone == PaymentConfirmationTone.CONFIRMED -> Icons.Filled.CheckCircle
                    trip.paymentMethod == PaymentMethod.WALLET -> Icons.Filled.AccountBalanceWallet
                    trip.paymentMethod == PaymentMethod.CASH -> Icons.Filled.Payments
                    else -> model.icon
                },
                contentDescription = model.compactPillLabel,
                tint = contentColor,
                modifier = Modifier.size(13.dp)
            )
            Text(
                text = model.compactPillLabel,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun PaymentStatusCard(
    trip: RideTrip,
    isDriver: Boolean,
    counterpartyName: String? = null,
    onOpenPayNow: () -> Unit = {},
    onVerifyPayment: (String) -> Unit = {},
    onReleaseHold: () -> Unit = {},
    onRefreshPaymentStatus: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val wayfareColors = LocalWayfareColors.current
    val model = resolvePaymentConfirmationUi(
        trip = trip,
        isDriver = isDriver,
        counterpartyName = counterpartyName
    )

    val containerColor = when (model.tone) {
        PaymentConfirmationTone.CONFIRMED -> wayfareColors.positive.copy(alpha = 0.14f)
        PaymentConfirmationTone.FAILED -> colorScheme.errorContainer.copy(alpha = 0.72f)
        PaymentConfirmationTone.PENDING -> wayfareColors.warning.copy(alpha = 0.12f)
        PaymentConfirmationTone.ACTION_REQUIRED -> if (isDriver) {
            wayfareColors.warning.copy(alpha = 0.12f)
        } else {
            colorScheme.primaryContainer.copy(alpha = 0.75f)
        }
        PaymentConfirmationTone.CASH_DUE -> colorScheme.surfaceVariant.copy(alpha = 0.55f)
    }

    val borderColor = when (model.tone) {
        PaymentConfirmationTone.CONFIRMED -> wayfareColors.positive.copy(alpha = 0.45f)
        PaymentConfirmationTone.FAILED -> colorScheme.error.copy(alpha = 0.45f)
        PaymentConfirmationTone.PENDING -> wayfareColors.warning.copy(alpha = 0.45f)
        PaymentConfirmationTone.ACTION_REQUIRED -> if (isDriver) {
            wayfareColors.warning.copy(alpha = 0.45f)
        } else {
            colorScheme.primary.copy(alpha = 0.45f)
        }
        PaymentConfirmationTone.CASH_DUE -> colorScheme.outlineVariant
    }

    val accentColor = when (model.tone) {
        PaymentConfirmationTone.CONFIRMED -> wayfareColors.positive
        PaymentConfirmationTone.FAILED -> colorScheme.error
        PaymentConfirmationTone.PENDING -> wayfareColors.warning
        PaymentConfirmationTone.ACTION_REQUIRED -> if (isDriver) wayfareColors.warning else colorScheme.primary
        PaymentConfirmationTone.CASH_DUE -> colorScheme.primary
    }

    val cardTag = if (isDriver) "driver_payment_status_card" else "rider_payment_status_card"

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = containerColor,
        border = BorderStroke(1.dp, borderColor),
        modifier = modifier
            .fillMaxWidth()
            .testTag(cardTag)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = model.icon,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = model.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = if (model.isConfirmed) {
                            Modifier.testTag(
                                if (isDriver) "driver_payment_confirmed_banner"
                                else "rider_payment_confirmed_banner"
                            )
                        } else {
                            Modifier
                        }
                    )
                }

                Spacer(Modifier.width(8.dp))

                if (!isDriver &&
                    trip.paymentMethod == PaymentMethod.CARD &&
                    trip.paymentStatus == PaymentStatus.AUTHORIZED &&
                    trip.status == RideStatus.ACCEPTED
                ) {
                    TextButton(
                        onClick = onReleaseHold,
                        modifier = Modifier.testTag("rider_card_hold_btn")
                    ) {
                        Text("Release")
                    }
                } else {
                    StatusChip(
                        label = model.badgeLabel,
                        containerColor = accentColor.copy(alpha = 0.16f),
                        contentColor = accentColor
                    )
                }
            }

            Text(
                text = model.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = colorScheme.onSurfaceVariant
            )

            if (trip.paymentMethod == PaymentMethod.CARD && !trip.paymentReference.isNullOrBlank()) {
                MonoBadge(
                    text = "Ref · ${trip.paymentReference}",
                    containerColor = colorScheme.surface.copy(alpha = 0.85f),
                    contentColor = colorScheme.onSurfaceVariant
                )
            }

            if (!isDriver &&
                trip.paymentMethod == PaymentMethod.CARD &&
                trip.paymentStatus != PaymentStatus.AUTHORIZED &&
                trip.paymentStatus != PaymentStatus.PAID
            ) {
                val isPendingWithRef = trip.paymentStatus == PaymentStatus.PENDING &&
                    !trip.paymentReference.isNullOrBlank()
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Button(
                        onClick = {
                            if (isPendingWithRef) {
                                trip.paymentReference?.let(onVerifyPayment)
                            } else {
                                onOpenPayNow()
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .tactilePressScale()
                            .testTag("rider_card_hold_btn")
                    ) {
                        Icon(Icons.Filled.CreditCard, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = when {
                                isPendingWithRef -> "Verify payment"
                                trip.paymentStatus == PaymentStatus.FAILED ||
                                    trip.paymentStatus == PaymentStatus.EXPIRED -> "Retry Pay Now"
                                else -> "Pay Now · ${LaunchPolicy.formatZar(trip.fare)}"
                            },
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    if (isPendingWithRef) {
                        OutlinedButton(
                            onClick = onOpenPayNow,
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("rider_reopen_checkout_btn")
                        ) {
                            Text("Pay Now", maxLines = 1)
                        }
                    }
                }
            } else if (isDriver &&
                trip.paymentMethod == PaymentMethod.CARD &&
                !model.isConfirmed
            ) {
                OutlinedButton(
                    onClick = onRefreshPaymentStatus,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("driver_refresh_payment_btn")
                ) {
                    Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Check payment status", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
