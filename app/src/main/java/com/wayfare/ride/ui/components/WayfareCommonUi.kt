package com.wayfare.ride.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalTaxi
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.wayfare.ride.domain.RideCoordinates
import com.wayfare.ride.domain.RideTrip
import com.wayfare.ride.domain.UserSession
import com.wayfare.ride.ui.theme.LocalWayfareColors
import com.wayfare.ride.ui.theme.WayfareMonoFamily
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun VhayaTopBar(
    session: UserSession?,
    isLiveApi: Boolean,
    onOpenAccount: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val wayfareColors = LocalWayfareColors.current

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = colorScheme.surface.copy(alpha = 0.96f),
        shadowElevation = 3.dp,
        border = BorderStroke(1.dp, colorScheme.outlineVariant),
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(colorScheme.primary)
                ) {
                    Icon(
                        imageVector = Icons.Filled.LocalTaxi,
                        contentDescription = "Vhaya",
                        tint = colorScheme.onPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Text(
                    text = "Vhaya",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = colorScheme.onSurface
                )
                if (isLiveApi) {
                    StatusChip(
                        label = "Live API",
                        containerColor = wayfareColors.brandHighlight,
                        contentColor = colorScheme.onPrimaryContainer
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(18.dp),
                color = colorScheme.surfaceVariant.copy(alpha = 0.72f),
                modifier = Modifier
                    .height(40.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .clickable(onClick = onOpenAccount)
                    .testTag("open_account_sheet")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(horizontal = 12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Person,
                        contentDescription = "Account",
                        tint = colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = session?.let { "${it.displayName.substringBefore(" ")} · ${it.role.label}" } ?: "Account",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

fun Modifier.tactilePressScale(): Modifier = composed {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = spring(),
        label = "pressScale"
    )
    this
        .scale(scale)
        .pointerInput(Unit) {
            detectTapGestures(
                onPress = {
                    pressed = true
                    tryAwaitRelease()
                    pressed = false
                }
            )
        }
}

@Composable
fun MonoBadge(
    text: String,
    containerColor: Color = MaterialTheme.colorScheme.secondaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onSecondaryContainer,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = containerColor,
        modifier = modifier
    ) {
        Text(
            text = text,
            fontFamily = WayfareMonoFamily,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = contentColor,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun StatusChip(
    label: String,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = containerColor,
        modifier = modifier
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
        )
    }
}

@Composable
fun WayfareConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    isDestructive: Boolean = false,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = MaterialTheme.typography.titleMedium) },
        text = { Text(message, style = MaterialTheme.typography.bodyMedium) },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isDestructive) colorScheme.error else colorScheme.primary,
                    contentColor = if (isDestructive) colorScheme.onError else colorScheme.onPrimary
                ),
                modifier = Modifier.testTag("dialog_confirm")
            ) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun Emergency112Chip(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = colorScheme.errorContainer,
        modifier = modifier
            .height(48.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .testTag("emergency_112_button")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 12.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Phone,
                contentDescription = "Emergency 112",
                tint = colorScheme.error,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "112",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = colorScheme.error
            )
        }
    }
}

fun dialEmergency112(context: Context) {
    context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:112")))
}

fun shareTripSnapshot(context: Context, trip: RideTrip) {
    val driverLine = trip.driverProfile?.let { "${it.name} · ${it.vehicle} (${it.registration})" } ?: "Matching driver"
    val body = buildString {
        appendLine("Vhaya Trip Share")
        appendLine("From: ${trip.pickup}")
        appendLine("To: ${trip.destination}")
        appendLine("Status: ${trip.status.label}")
        appendLine("Driver: $driverLine")
        trip.tripPin?.let { appendLine("Pickup PIN: $it") }
    }
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, body)
    }
    context.startActivity(Intent.createChooser(intent, "Share trip details"))
}

fun openExternalNavigation(context: Context, target: RideCoordinates, label: String) {
    val geoUri = Uri.parse("geo:${target.latitude},${target.longitude}?q=${target.latitude},${target.longitude}(${Uri.encode(label)})")
    val geoIntent = Intent(Intent.ACTION_VIEW, geoUri)
    if (geoIntent.resolveActivity(context.packageManager) != null) {
        context.startActivity(geoIntent)
    } else {
        val osmUri = Uri.parse("https://www.openstreetmap.org/?mlat=${target.latitude}&mlon=${target.longitude}#map=16/${target.latitude}/${target.longitude}")
        context.startActivity(Intent(Intent.ACTION_VIEW, osmUri))
    }
}

fun formatShortTime(epochMs: Long): String {
    return SimpleDateFormat("dd MMM · HH:mm", Locale.getDefault()).format(Date(epochMs))
}

@Composable
fun WayfareEmptyState(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val wayfareColors = LocalWayfareColors.current

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = colorScheme.surface,
        border = BorderStroke(1.dp, colorScheme.outlineVariant),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 22.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = wayfareColors.brandHighlight,
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}
