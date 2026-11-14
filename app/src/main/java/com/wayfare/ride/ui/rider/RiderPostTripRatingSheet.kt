package com.wayfare.ride.ui.rider

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Button
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wayfare.ride.domain.LaunchPolicy
import com.wayfare.ride.domain.RideTrip
import com.wayfare.ride.ui.components.MonoBadge
import com.wayfare.ride.ui.components.StatusChip
import com.wayfare.ride.ui.components.tactilePressScale
import com.wayfare.ride.ui.theme.LocalWayfareColors

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RiderPostTripRatingSheet(
    trip: RideTrip,
    onSubmitRating: (stars: Int, tags: List<String>) -> Unit,
    onReportIssue: (category: String, message: String, requestRefund: Boolean, attachmentUri: Uri?) -> Unit,
    onDismiss: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val wayfareColors = LocalWayfareColors.current
    val haptics = LocalHapticFeedback.current

    var selectedStars by remember(trip.id) { mutableIntStateOf(5) }
    var selectedTags by remember(trip.id) { mutableStateOf(emptySet<String>()) }
    var showReportDialog by remember { mutableStateOf(false) }

    val availableTags = if (selectedStars == 5) {
        listOf("Smooth drive", "Clean car", "Great pickup", "Polite driver")
    } else {
        listOf("Long pickup wait", "Route choice", "Vehicle condition", "Navigation")
    }

    BackHandler { onDismiss() }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
        ) {
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
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = wayfareColors.positive,
                        modifier = Modifier.size(22.dp)
                    )
                    Text("Trip completed", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
                MonoBadge(
                    text = LaunchPolicy.formatZar(trip.fare),
                    containerColor = wayfareColors.brandHighlight,
                    contentColor = colorScheme.onPrimaryContainer
                )
            }

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = colorScheme.surfaceVariant.copy(alpha = 0.5f),
                border = BorderStroke(1.dp, colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(12.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = colorScheme.primaryContainer,
                        modifier = Modifier.size(42.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.Person, contentDescription = null, tint = colorScheme.primary)
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = trip.driverProfile?.name ?: "Vhaya Driver",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${trip.pickup.substringBefore(" ·")} → ${trip.destination.substringBefore(" ·")}",
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    StatusChip(
                        label = trip.paymentMethod.label,
                        containerColor = colorScheme.surface,
                        contentColor = colorScheme.onSurface
                    )
                }
            }

            HorizontalDivider()

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "How was your Vhaya ride?",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (star in 1..5) {
                        val filled = selectedStars >= star
                        val scale by animateFloatAsState(
                            targetValue = if (selectedStars == star) 1.16f else 1f,
                            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                            label = "postTripStarScale"
                        )
                        IconButton(
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                selectedStars = star
                                selectedTags = emptySet()
                            },
                            modifier = Modifier
                                .size(50.dp)
                                .scale(scale)
                                .testTag("post_trip_star_$star")
                        ) {
                            Icon(
                                imageVector = if (filled) Icons.Filled.Star else Icons.Filled.StarBorder,
                                contentDescription = "Rate $star stars",
                                tint = if (filled) wayfareColors.warning else colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(34.dp)
                            )
                        }
                    }
                }
            }

            AnimatedVisibility(visible = true) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    availableTags.forEach { tag ->
                        val active = selectedTags.contains(tag)
                        FilterChip(
                            selected = active,
                            onClick = {
                                selectedTags = if (active) selectedTags - tag else selectedTags + tag
                            },
                            label = { Text(tag) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = wayfareColors.brandHighlight,
                                selectedLabelColor = colorScheme.onPrimaryContainer
                            )
                        )
                    }
                }
            }

            Spacer(Modifier.height(2.dp))

            Button(
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onSubmitRating(selectedStars, selectedTags.toList())
                },
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .tactilePressScale()
                    .testTag("post_trip_submit_rating")
            ) {
                Text("Submit $selectedStars ★ rating", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }

            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedButton(
                    onClick = { showReportDialog = true },
                    modifier = Modifier.height(48.dp).testTag("post_trip_report_issue")
                ) {
                    Icon(Icons.Filled.Flag, contentDescription = null, modifier = Modifier.size(16.dp))
                    Text(" Report issue")
                }
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.height(48.dp).testTag("post_trip_skip")
                ) {
                    Text("Not now")
                }
            }
        }
    }

    if (showReportDialog) {
        IssueReportingSheet(
            trip = trip,
            onSubmit = { cat, msg, requestRefund, attachmentUri ->
                showReportDialog = false
                onReportIssue(cat, msg, requestRefund, attachmentUri)
                onDismiss()
            },
            onDismiss = { showReportDialog = false }
        )
    }
}
