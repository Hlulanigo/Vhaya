package com.wayfare.ride.ui.payments

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wayfare.ride.domain.LaunchPolicy
import com.wayfare.ride.domain.PaymentStatus
import com.wayfare.ride.domain.RideTrip
import com.wayfare.ride.ui.components.MonoBadge
import com.wayfare.ride.ui.components.StatusChip
import com.wayfare.ride.ui.components.tactilePressScale
import com.wayfare.ride.ui.theme.LocalWayfareColors
import com.wayfare.ride.ui.theme.WayfareMonoFamily

@Composable
fun PayNowScreen(
    trip: RideTrip,
    isProcessing: Boolean,
    errorMessage: String?,
    onPayNow: () -> Unit,
    onVerifyPayment: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val wayfareColors = LocalWayfareColors.current

    BackHandler { onDismiss() }

    val isConfirmed = trip.paymentStatus == PaymentStatus.AUTHORIZED || trip.paymentStatus == PaymentStatus.PAID
    val isPendingWithRef = trip.paymentStatus == PaymentStatus.PENDING && !trip.paymentReference.isNullOrBlank()
    val isFailed = trip.paymentStatus == PaymentStatus.FAILED || trip.paymentStatus == PaymentStatus.EXPIRED

    val backdropBrush = Brush.verticalGradient(
        colors = listOf(
            colorScheme.primaryContainer.copy(alpha = 0.55f),
            colorScheme.background,
            colorScheme.background
        )
    )

    Surface(
        color = colorScheme.background,
        modifier = modifier
            .fillMaxSize()
            .testTag("pay_now_screen")
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(backdropBrush)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
            ) {
                // Top Bar
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .widthIn(max = 580.dp)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isConfirmed) "Payment confirmed" else "Complete ride payment",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${trip.driverProfile?.name ?: "Your driver"} accepted your ${trip.category.label} ride",
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("pay_now_close_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Minimize payment screen"
                        )
                    }
                }

                // Scrollable Body
                Column(
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .widthIn(max = 580.dp)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    // Hero Fare Card
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = colorScheme.surface,
                        shadowElevation = 4.dp,
                        border = BorderStroke(
                            1.dp,
                            if (isConfirmed) wayfareColors.positive.copy(alpha = 0.55f) else colorScheme.outline
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.padding(20.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                StatusChip(
                                    label = when {
                                        isConfirmed -> "Authorized"
                                        isPendingWithRef -> "Checkout started"
                                        isFailed -> "Retry required"
                                        else -> "Action required"
                                    },
                                    containerColor = when {
                                        isConfirmed -> wayfareColors.positive.copy(alpha = 0.16f)
                                        isFailed -> colorScheme.errorContainer
                                        isPendingWithRef -> wayfareColors.warning.copy(alpha = 0.16f)
                                        else -> wayfareColors.brandHighlight
                                    },
                                    contentColor = when {
                                        isConfirmed -> wayfareColors.positive
                                        isFailed -> colorScheme.error
                                        isPendingWithRef -> wayfareColors.warning
                                        else -> colorScheme.onPrimaryContainer
                                    }
                                )
                                MonoBadge(text = "${trip.paymentMethod.label} · Paystack")
                            }

                            Text(
                                text = LaunchPolicy.formatZar(trip.fare),
                                fontFamily = WayfareMonoFamily,
                                style = MaterialTheme.typography.headlineLarge,
                                fontWeight = FontWeight.Bold,
                                color = if (isConfirmed) wayfareColors.positive else colorScheme.onSurface,
                                modifier = Modifier.testTag("pay_now_fare_amount")
                            )

                            Text(
                                text = "${trip.pickup.substringBefore(" ·")} → ${trip.destination.substringBefore(" ·")}",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Shield,
                                    contentDescription = null,
                                    tint = if (isConfirmed) wayfareColors.positive else colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = if (isConfirmed) {
                                        "Fare is safely reserved and confirmed for both you and your driver."
                                    } else {
                                        "Funds are held securely via Paystack and captured only when your trip completes."
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // 3-Step Payment Flow Indicator
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = colorScheme.surface,
                        border = BorderStroke(1.dp, colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Text(
                                text = "Trip payment progress",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                            PaymentStepRow(
                                stepNumber = 1,
                                title = "Driver accepted your fare",
                                subtitle = trip.driverProfile?.let { "${it.name} · ${it.vehicleColor} ${it.vehicle}".trim() }
                                    ?: "Driver matched and heading your way",
                                done = true,
                                active = false
                            )
                            PaymentStepRow(
                                stepNumber = 2,
                                title = if (isConfirmed) "Paystack authorization confirmed" else "Authorize card fare",
                                subtitle = when {
                                    isConfirmed -> "Your driver now sees your payment is confirmed"
                                    isPendingWithRef -> "Finish in Paystack and tap Verify payment"
                                    else -> "Tap Pay Now below to complete checkout"
                                },
                                done = isConfirmed,
                                active = !isConfirmed
                            )
                            PaymentStepRow(
                                stepNumber = 3,
                                title = "Board with 6-digit PIN & ride",
                                subtitle = trip.tripPin?.let { "Share PIN $it when your driver arrives" }
                                    ?: "Your 6-digit boarding PIN unlocks the trip",
                                done = false,
                                active = isConfirmed
                            )
                        }
                    }

                    // Driver + PIN Card
                    trip.driverProfile?.let { driver ->
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = colorScheme.surface,
                            border = BorderStroke(1.dp, colorScheme.outlineVariant),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.padding(16.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier
                                                .size(40.dp)
                                                .clip(CircleShape)
                                                .background(wayfareColors.brandHighlight)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.DirectionsCar,
                                                contentDescription = null,
                                                tint = colorScheme.primary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                        Column {
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
                                                text = "${driver.vehicleColor} ${driver.vehicle}".trim(),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    if (driver.registration.isNotBlank()) {
                                        MonoBadge(text = driver.registration)
                                    }
                                }

                                if (!trip.tripPin.isNullOrBlank()) {
                                    HorizontalDivider(color = colorScheme.outlineVariant)
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Lock,
                                                contentDescription = null,
                                                tint = colorScheme.primary,
                                                modifier = Modifier.size(16.dp)
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
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Status Banners (Confirmed / Pending / Error)
                    if (isConfirmed) {
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = wayfareColors.positive.copy(alpha = 0.14f),
                            border = BorderStroke(1.dp, wayfareColors.positive.copy(alpha = 0.5f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("pay_now_confirmed_state")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.padding(14.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.CheckCircle,
                                    contentDescription = "Payment confirmed",
                                    tint = wayfareColors.positive,
                                    modifier = Modifier.size(26.dp)
                                )
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        text = if (trip.paymentStatus == PaymentStatus.PAID) {
                                            "Payment confirmed"
                                        } else {
                                            "Fare reserved on card"
                                        },
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = wayfareColors.positive
                                    )
                                    Text(
                                        text = "Both you and ${trip.driverProfile?.name ?: "your driver"} can now see your payment is verified.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    } else if (isPendingWithRef) {
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = wayfareColors.warning.copy(alpha = 0.14f),
                            border = BorderStroke(1.dp, wayfareColors.warning.copy(alpha = 0.45f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.padding(14.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Schedule,
                                    contentDescription = null,
                                    tint = wayfareColors.warning,
                                    modifier = Modifier.size(24.dp)
                                )
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        text = "Paystack checkout open",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Once you finish in Paystack, tap \"I've Paid · Verify Payment\" below.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colorScheme.onSurfaceVariant
                                    )
                                    trip.paymentReference?.let { ref ->
                                        MonoBadge(text = "Ref · $ref")
                                    }
                                }
                            }
                        }
                    }

                    if (!errorMessage.isNullOrBlank() && !isConfirmed) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = colorScheme.errorContainer,
                            border = BorderStroke(1.dp, colorScheme.error.copy(alpha = 0.45f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.padding(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.ErrorOutline,
                                    contentDescription = null,
                                    tint = colorScheme.error,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = errorMessage,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colorScheme.onErrorContainer
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                }

                // Pinned Action Footer
                Surface(
                    color = colorScheme.surface,
                    shadowElevation = 8.dp,
                    border = BorderStroke(1.dp, colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .widthIn(max = 580.dp)
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp)
                    ) {
                        when {
                            isConfirmed -> {
                                Button(
                                    onClick = onDismiss,
                                    shape = RoundedCornerShape(16.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = wayfareColors.positive),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(54.dp)
                                        .tactilePressScale()
                                        .testTag("pay_now_continue_btn")
                                ) {
                                    Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Continue to live ride", fontWeight = FontWeight.Bold)
                                }
                            }

                            isPendingWithRef -> {
                                Button(
                                    onClick = { trip.paymentReference?.let(onVerifyPayment) },
                                    enabled = !isProcessing,
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(54.dp)
                                        .tactilePressScale()
                                        .testTag("pay_now_verify_btn")
                                ) {
                                    if (isProcessing) {
                                        CircularProgressIndicator(
                                            strokeWidth = 2.dp,
                                            color = colorScheme.onPrimary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(Modifier.width(8.dp))
                                    }
                                    Text("I've Paid · Verify Payment", fontWeight = FontWeight.Bold)
                                }

                                OutlinedButton(
                                    onClick = onPayNow,
                                    enabled = !isProcessing,
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .testTag("pay_now_reopen_btn")
                                ) {
                                    Text("Reopen Paystack Checkout", fontWeight = FontWeight.SemiBold)
                                }
                            }

                            else -> {
                                Button(
                                    onClick = onPayNow,
                                    enabled = !isProcessing,
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(54.dp)
                                        .tactilePressScale()
                                        .testTag("pay_now_submit_btn")
                                ) {
                                    if (isProcessing) {
                                        CircularProgressIndicator(
                                            strokeWidth = 2.dp,
                                            color = colorScheme.onPrimary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text("Starting Paystack checkout...", fontWeight = FontWeight.Bold)
                                    } else {
                                        Icon(Icons.Filled.CreditCard, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            text = if (isFailed) {
                                                "Retry Pay Now · ${LaunchPolicy.formatZar(trip.fare)}"
                                            } else {
                                                "Pay Now · ${LaunchPolicy.formatZar(trip.fare)}"
                                            },
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                TextButton(
                                    onClick = onDismiss,
                                    modifier = Modifier
                                        .height(48.dp)
                                        .testTag("pay_now_later_btn")
                                ) {
                                    Text("Pay later (keep ride active)")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PaymentStepRow(
    stepNumber: Int,
    title: String,
    subtitle: String,
    done: Boolean,
    active: Boolean
) {
    val colorScheme = MaterialTheme.colorScheme
    val wayfareColors = LocalWayfareColors.current

    val badgeBg = when {
        done -> wayfareColors.positive
        active -> colorScheme.primary
        else -> colorScheme.surfaceVariant
    }
    val badgeFg = when {
        done -> colorScheme.onPrimary
        active -> colorScheme.onPrimary
        else -> colorScheme.onSurfaceVariant
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(badgeBg)
        ) {
            if (done) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = "Done",
                    tint = badgeFg,
                    modifier = Modifier.size(18.dp)
                )
            } else {
                Text(
                    text = stepNumber.toString(),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = badgeFg
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = if (active || done) FontWeight.Bold else FontWeight.Medium,
                color = if (active || done) colorScheme.onSurface else colorScheme.onSurfaceVariant
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = colorScheme.onSurfaceVariant
            )
        }
    }
}
