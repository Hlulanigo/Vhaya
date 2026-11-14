package com.wayfare.ride.ui.rider

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wayfare.ride.domain.LaunchPolicy
import com.wayfare.ride.domain.PaymentMethod
import com.wayfare.ride.domain.RideCategory
import com.wayfare.ride.domain.RideCoordinates
import com.wayfare.ride.domain.SavedDestination
import com.wayfare.ride.ui.BookingDraft
import com.wayfare.ride.ui.WayfareUiState
import com.wayfare.ride.ui.components.MonoBadge
import com.wayfare.ride.ui.components.WayfareEmptyState
import com.wayfare.ride.ui.components.tactilePressScale
import com.wayfare.ride.ui.theme.LocalWayfareColors
import com.wayfare.ride.ui.theme.WayfareMonoFamily
import kotlinx.coroutines.delay

data class QuickPlaceShortcut(
    val label: String,
    val coordinates: RideCoordinates
)

@Composable
fun CompactWhereToSheet(
    riderName: String,
    walletBalanceMinor: Long,
    savedDestinations: List<SavedDestination>,
    recentDestinations: List<QuickPlaceShortcut>,
    onWhereToClick: () -> Unit,
    onToggleScheduleLater: () -> Unit,
    onOpenWallet: () -> Unit,
    onSelectQuickPlace: (String, RideCoordinates) -> Unit,
    onPinOnMap: () -> Unit,
    onOpenSavedPlaces: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val firstName = remember(riderName) {
        riderName.trim().substringBefore(" ").takeIf { it.isNotBlank() }
    }

    Surface(
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        color = colorScheme.surface,
        shadowElevation = 8.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .width(38.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(colorScheme.outline)
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (firstName != null) "Ready to roll, $firstName" else "Ready for a ride?",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Choose a destination to see fare and ETA",
                        style = MaterialTheme.typography.labelSmall,
                        color = colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = colorScheme.surfaceVariant.copy(alpha = 0.7f),
                    border = BorderStroke(1.dp, colorScheme.outlineVariant),
                    modifier = Modifier
                        .height(48.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable(onClick = onOpenWallet)
                        .testTag("home_wallet_pill")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(horizontal = 10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.AccountBalanceWallet,
                            contentDescription = "Open wallet",
                            tint = colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = LaunchPolicy.formatZarMinor(walletBalanceMinor),
                            fontFamily = WayfareMonoFamily,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = colorScheme.onSurface
                        )
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = colorScheme.primary,
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp)
                        .tactilePressScale()
                        .clip(RoundedCornerShape(16.dp))
                        .clickable(onClick = onWhereToClick)
                        .testTag("where_to_button")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(horizontal = 16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = "Search destination",
                            tint = colorScheme.onPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                        Text(
                            text = "Where to?",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = colorScheme.onPrimary
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = colorScheme.surfaceVariant,
                    border = BorderStroke(1.dp, colorScheme.outlineVariant),
                    modifier = Modifier
                        .height(56.dp)
                        .tactilePressScale()
                        .clip(RoundedCornerShape(16.dp))
                        .clickable(onClick = onToggleScheduleLater)
                        .testTag("schedule_later_chip")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(horizontal = 16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CalendarToday,
                            contentDescription = "Schedule ride",
                            tint = colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Later",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = colorScheme.primary
                        )
                    }
                }
            }

            val popularHubs = remember(savedDestinations, recentDestinations) {
                val usedLabels = (savedDestinations.map { it.label } + recentDestinations.map { it.label })
                    .mapTo(mutableSetOf()) { it.lowercase() }
                LaunchPolicy.POLOKWANE_POPULAR_HUBS.filter { hub ->
                    usedLabels.none { it.contains(hub.name.lowercase()) }
                }
            }

            if (savedDestinations.isNotEmpty() || recentDestinations.isNotEmpty() || popularHubs.isNotEmpty()) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(savedDestinations, key = { "quick-saved-${it.id}" }) { saved ->
                        QuickDestinationPill(
                            icon = Icons.Filled.Bookmark,
                            label = saved.label.substringBefore(","),
                            subtitle = "Saved",
                            onClick = {
                                onSelectQuickPlace(saved.label, RideCoordinates(saved.latitude, saved.longitude))
                            }
                        )
                    }
                    items(recentDestinations, key = { "quick-recent-${it.label}" }) { recent ->
                        QuickDestinationPill(
                            icon = Icons.Filled.History,
                            label = recent.label.substringBefore(","),
                            subtitle = "Recent",
                            onClick = { onSelectQuickPlace(recent.label, recent.coordinates) }
                        )
                    }
                    items(popularHubs.take(5), key = { "quick-${it.id}" }) { hub ->
                        QuickDestinationPill(
                            icon = Icons.Filled.Place,
                            label = hub.name,
                            subtitle = hub.area,
                            onClick = {
                                onSelectQuickPlace("${hub.name}, ${hub.area}, Polokwane", hub.coordinates)
                            }
                        )
                    }
                }
            }
            if (savedDestinations.isEmpty() && recentDestinations.isEmpty()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    QuickActionTile(
                        icon = Icons.Filled.Map,
                        label = "Pin on map",
                        onClick = onPinOnMap,
                        modifier = Modifier.weight(1f).testTag("home_pin_shortcut")
                    )
                    QuickActionTile(
                        icon = Icons.Filled.BookmarkBorder,
                        label = "Saved places",
                        onClick = onOpenSavedPlaces,
                        modifier = Modifier.weight(1f).testTag("home_saved_shortcut")
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickDestinationPill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    subtitle: String,
    onClick: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = BorderStroke(1.dp, colorScheme.outlineVariant),
        modifier = Modifier
            .height(50.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(horizontal = 12.dp)
        ) {
            Icon(icon, contentDescription = null, tint = colorScheme.primary, modifier = Modifier.size(16.dp))
            Column {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun QuickActionTile(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = colorScheme.surfaceVariant.copy(alpha = 0.45f),
        border = BorderStroke(1.dp, colorScheme.outlineVariant),
        modifier = modifier
            .height(48.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 12.dp)
        ) {
            Icon(icon, contentDescription = null, tint = colorScheme.primary, modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(6.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
fun SearchDestinationSheet(
    draft: BookingDraft,
    uiState: WayfareUiState,
    recentDestinations: List<QuickPlaceShortcut>,
    onBack: () -> Unit,
    onUseGps: () -> Unit,
    onQueryChange: (String) -> Unit,
    onPickOnMap: (Boolean) -> Unit,
    onSelectPlace: (String, RideCoordinates, Boolean) -> Unit,
    onSavePlace: (String, RideCoordinates) -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val wayfareColors = LocalWayfareColors.current
    var editingPickup by remember(draft.pickingForPickup) { mutableStateOf(draft.pickingForPickup) }
    var query by remember { mutableStateOf("") }
    val matchingHubs = remember(query) {
        val q = query.trim().lowercase()
        if (q.length < 3) emptyList()
        else LaunchPolicy.POLOKWANE_POPULAR_HUBS.filter {
            it.name.lowercase().contains(q) || it.area.lowercase().contains(q)
        }
    }

    LaunchedEffect(query) {
        if (query.trim().length >= 3) {
            delay(280L)
            onQueryChange(query)
        } else {
            onQueryChange("")
        }
    }

    Surface(
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        color = colorScheme.surface,
        shadowElevation = 8.dp,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 580.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(onClick = onBack, modifier = Modifier.size(48.dp).testTag("search_back_button")) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Text(
                    text = if (editingPickup) "Set pickup point" else "Plan your route",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = { onPickOnMap(editingPickup) }, modifier = Modifier.testTag("pick_on_map_button")) {
                    Icon(Icons.Filled.Map, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Pin on map")
                }
            }

            Spacer(Modifier.height(6.dp))

            // Connected A -> B Route Spine (Pickup -> Destination)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(vertical = 8.dp)
                ) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(colorScheme.primary))
                    Box(Modifier.width(2.dp).height(34.dp).background(colorScheme.outline))
                    Box(Modifier.size(10.dp).clip(RoundedCornerShape(2.dp)).background(wayfareColors.destinationMarker))
                }

                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (editingPickup) colorScheme.primaryContainer.copy(alpha = 0.5f) else colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        border = BorderStroke(1.dp, if (editingPickup) colorScheme.primary else colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { editingPickup = true; query = "" }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(start = 12.dp, top = 4.dp, end = 4.dp, bottom = 4.dp)
                        ) {
                            Text(
                                text = draft.pickupLabel,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = onUseGps, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) {
                                Icon(Icons.Filled.GpsFixed, contentDescription = "GPS", modifier = Modifier.size(15.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("GPS", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }

                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text(if (editingPickup) "Search pickup address..." else "Where to? Search any address...") },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth().testTag("destination_search_input")
                    )
                }
            }

            if (uiState.searchingPlaces) {
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            if (
                uiState.placeSearchError != null &&
                !uiState.searchingPlaces &&
                query.trim().length >= 3
            ) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    border = BorderStroke(1.dp, colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = null,
                            tint = colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                        Column(
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = "Online search unavailable",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Retry or choose a pickup pin on the map.",
                                style = MaterialTheme.typography.bodySmall,
                                color = colorScheme.onSurfaceVariant
                            )
                        }
                        androidx.compose.material3.TextButton(
                            onClick = { onQueryChange(query) }
                        ) {
                            Text("Retry")
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f, fill = false)) {
                if (query.trim().length < 3 && !editingPickup && uiState.savedDestinations.isNotEmpty()) {
                    item {
                        Text(
                            text = "Saved places",
                            style = MaterialTheme.typography.labelMedium,
                            color = colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    items(uiState.savedDestinations, key = { "saved-${it.id}" }) { saved ->
                        PlaceRow(
                            title = saved.label,
                            subtitle = "Saved place",
                            isSaved = true,
                            onClick = { onSelectPlace(saved.label, RideCoordinates(saved.latitude, saved.longitude), editingPickup) },
                            onToggleSave = null
                        )
                    }
                }
                if (query.trim().length < 3 && !editingPickup && recentDestinations.isNotEmpty()) {
                    item {
                        Text(
                            text = "Recent rides",
                            style = MaterialTheme.typography.labelMedium,
                            color = colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    items(recentDestinations, key = { "recent-${it.label}" }) { recent ->
                        PlaceRow(
                            title = recent.label,
                            subtitle = "Recent destination",
                            isSaved = false,
                            onClick = { onSelectPlace(recent.label, recent.coordinates, editingPickup) },
                            onToggleSave = { onSavePlace(recent.label, recent.coordinates) }
                        )
                    }
                }
                if (query.trim().length >= 3) {
                    items(matchingHubs, key = { "hub-match-${it.id}" }) { hub ->
                        val fullLabel = "${hub.name}, ${hub.area}, Polokwane"
                        val alreadySaved = uiState.savedDestinations.any { it.label == fullLabel }
                        val distKm = LaunchPolicy.haversineKm(draft.pickupCoords, hub.coordinates)
                        PlaceRow(
                            title = fullLabel,
                            subtitle = distKm?.let { "Polokwane hub · ${"%.1f".format(it)} km away" } ?: "Polokwane hub",
                            isSaved = alreadySaved,
                            onClick = { onSelectPlace(fullLabel, hub.coordinates, editingPickup) },
                            onToggleSave = { onSavePlace(fullLabel, hub.coordinates) }
                        )
                    }
                    if (uiState.placeSearchError == null && uiState.placeSuggestions.isNotEmpty()) {
                        items(uiState.placeSuggestions, key = { "osm-${it.id}" }) { place ->
                            val alreadySaved = uiState.savedDestinations.any { it.label == place.label }
                            val subtitle = place.distanceMeters?.let { m ->
                                "Search result · ${"%.1f".format(m / 1000.0)} km away"
                            } ?: "Search result"
                            PlaceRow(
                                title = place.label,
                                subtitle = subtitle,
                                isSaved = alreadySaved,
                                onClick = { onSelectPlace(place.label, place.coordinates, editingPickup) },
                                onToggleSave = { onSavePlace(place.label, place.coordinates) }
                            )
                        }
                    } else if (
                        !uiState.searchingPlaces &&
                        uiState.placeSearchError == null &&
                        matchingHubs.isEmpty()
                    ) {
                        item {
                            WayfareEmptyState(
                                icon = Icons.Filled.Search,
                                title = "No matching places",
                                message = "Try a broader address or tap Pin on map.",
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }
                    }
                } else {
                    item {
                        Text(
                            text = "Popular hubs in Polokwane",
                            style = MaterialTheme.typography.labelMedium,
                            color = colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    items(LaunchPolicy.POLOKWANE_POPULAR_HUBS, key = { "hub-list-${it.id}" }) { hub ->
                        val fullLabel = "${hub.name}, ${hub.area}, Polokwane"
                        val alreadySaved = uiState.savedDestinations.any { it.label == fullLabel }
                        val distKm = LaunchPolicy.haversineKm(draft.pickupCoords, hub.coordinates)
                        PlaceRow(
                            title = hub.name,
                            subtitle = distKm?.let { "${hub.area} · ${"%.1f".format(it)} km away" } ?: hub.area,
                            isSaved = alreadySaved,
                            onClick = { onSelectPlace(fullLabel, hub.coordinates, editingPickup) },
                            onToggleSave = { onSavePlace(fullLabel, hub.coordinates) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaceRow(
    title: String,
    subtitle: String,
    isSaved: Boolean,
    onClick: () -> Unit,
    onToggleSave: (() -> Unit)?
) {
    val colorScheme = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 10.dp)
            .testTag("place_row_${title.take(12)}")
    ) {
        Surface(shape = CircleShape, color = colorScheme.surfaceVariant, modifier = Modifier.size(38.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = if (isSaved) Icons.Filled.Bookmark else Icons.Filled.LocationOn,
                    contentDescription = null,
                    tint = colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = colorScheme.onSurfaceVariant)
        }
        if (onToggleSave != null) {
            IconButton(onClick = onToggleSave, modifier = Modifier.size(48.dp)) {
                Icon(
                    imageVector = if (isSaved) Icons.Filled.Check else Icons.Filled.Bookmark,
                    contentDescription = "Save place",
                    tint = if (isSaved) colorScheme.primary else colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
fun MapPinPickerSheet(
    pickingForPickup: Boolean,
    centerCoords: RideCoordinates,
    pinPreviewAddress: String?,
    pinPreviewLoading: Boolean,
    onCancel: () -> Unit,
    onConfirmPin: (RideCoordinates) -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val fallbackLabel = remember(centerCoords) { LaunchPolicy.nearestLandmarkLabel(centerCoords) }
    val displayLabel = pinPreviewAddress?.takeIf { it.isNotBlank() } ?: fallbackLabel

    Surface(
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        color = colorScheme.surface,
        shadowElevation = 8.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(18.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = if (pickingForPickup) "Drag map to set pickup" else "Drag map to set destination",
                    style = MaterialTheme.typography.labelLarge,
                    color = colorScheme.onSurfaceVariant
                )
                if (pinPreviewLoading) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        CircularProgressIndicator(
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "Finding street...",
                            style = MaterialTheme.typography.labelSmall,
                            color = colorScheme.primary
                        )
                    }
                }
            }
            Text(
                text = displayLabel,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag("pin_picker_address_label")
            )
            Text(
                text = "${"%.5f".format(centerCoords.latitude)}, ${"%.5f".format(centerCoords.longitude)}",
                fontFamily = WayfareMonoFamily,
                style = MaterialTheme.typography.labelSmall,
                color = colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier.weight(1f).height(50.dp).testTag("cancel_map_pin")
                ) { Text("Back") }
                Button(
                    onClick = { onConfirmPin(centerCoords) },
                    modifier = Modifier.weight(2f).height(50.dp).tactilePressScale().testTag("confirm_map_pin")
                ) {
                    Text(if (pickingForPickup) "Use pickup" else "Use destination", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun ChooseRideAndFareSheet(
    draft: BookingDraft,
    walletBalanceMinor: Long,
    isDestinationSaved: Boolean,
    onToggleSaveDestination: () -> Unit,
    onBack: () -> Unit,
    onSelectCategory: (RideCategory) -> Unit,
    onToggleOffer: () -> Unit,
    onOfferTextChange: (String) -> Unit,
    onAdjustOffer: (Int) -> Unit,
    onToggleSchedule: () -> Unit,
    onSelectPaymentMethod: (PaymentMethod) -> Unit,
    onRetryRoute: () -> Unit,
    onRequestRide: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val wayfareColors = LocalWayfareColors.current
    val route = draft.previewRoute
    val kmText = when {
        draft.routeLoading -> "Calculating..."
        route != null -> "${"%.1f".format(route.distanceMeters / 1000.0)} km"
        else -> "Route unavailable"
    }
    val minText = when {
        draft.routeLoading -> "ETA pending"
        route != null -> "${(route.durationSeconds / 60).coerceAtLeast(1)} min"
        else -> "-- min"
    }
    val lowWallet = draft.paymentMethod == PaymentMethod.WALLET &&
        walletBalanceMinor < (draft.effectiveFare * 100).toLong()

    Surface(
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        color = colorScheme.surface,
        shadowElevation = 8.dp,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 580.dp)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                IconButton(onClick = onBack, modifier = Modifier.size(48.dp).testTag("ride_sheet_back")) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Change destination")
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = draft.destinationLabel.substringBefore(" ·"),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "From ${draft.pickupLabel.substringBefore(" ·")} · $kmText · $minText",
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(
                    onClick = onToggleSaveDestination,
                    modifier = Modifier.size(48.dp).testTag("save_destination_bookmark")
                ) {
                    Icon(
                        imageVector = if (isDestinationSaved) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                        contentDescription = if (isDestinationSaved) "Destination saved" else "Save destination",
                        tint = if (isDestinationSaved) colorScheme.primary else colorScheme.onSurfaceVariant
                    )
                }
                FilterChip(
                    selected = draft.scheduledOffsetMinutes != null,
                    onClick = onToggleSchedule,
                    leadingIcon = { Icon(Icons.Filled.Schedule, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    label = {
                        Text(
                            when (draft.scheduledOffsetMinutes) {
                                null -> "Now"
                                30 -> "+30m"
                                else -> "+1h"
                            }
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = wayfareColors.brandHighlight,
                        selectedLabelColor = colorScheme.onPrimaryContainer
                    ),
                    modifier = Modifier.testTag("ride_timing_chip")
                )
            }

            if (draft.routeLoading) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .testTag("route_preview_loading")
                )
            }

            draft.routeError?.let { errorMsg ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = colorScheme.errorContainer.copy(alpha = 0.7f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = errorMsg,
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.error,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(
                            onClick = onRetryRoute,
                            modifier = Modifier.testTag("retry_route_btn")
                        ) {
                            Text("Retry")
                        }
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                RideCategory.entries.forEach { category ->
                    val selected = draft.category == category
                    val fare = route?.let { it.fareEstimate ?: LaunchPolicy.calculateEstimate(it.distanceMeters, category) }
                    val bg by animateColorAsState(
                        if (selected) wayfareColors.brandHighlight else colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        label = "catBg"
                    )
                    val cardScale by animateFloatAsState(
                        targetValue = if (selected) 1.02f else 1f,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy),
                        label = "catScale"
                    )

                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = bg,
                        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) colorScheme.primary else colorScheme.outlineVariant),
                        modifier = Modifier
                            .weight(1f)
                            .scale(cardScale)
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { onSelectCategory(category) }
                            .testTag("category_card_${category.name.lowercase()}")
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(3.dp),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp)
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.DirectionsCar,
                                    contentDescription = category.label,
                                    tint = if (selected) colorScheme.primary else colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = category.caption,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Text(category.label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, maxLines = 1)
                            Text(
                                text = fare?.let { LaunchPolicy.formatZar(it) } ?: "--",
                                fontFamily = WayfareMonoFamily,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = colorScheme.primary
                            )
                        }
                    }
                }
            }

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = colorScheme.surfaceVariant.copy(alpha = 0.45f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable(onClick = onToggleOffer)
                            .padding(vertical = 4.dp)
                            .testTag("toggle_offer_fare")
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Filled.Tune, contentDescription = null, tint = colorScheme.primary, modifier = Modifier.size(18.dp))
                            Text("Offer fare", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                        }
                        MonoBadge(text = LaunchPolicy.formatZar(draft.effectiveFare))
                    }

                    AnimatedVisibility(visible = draft.isOfferExpanded) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
                            Text(
                                text = "Propose your fare — nearby drivers can accept or send a counter-offer.",
                                style = MaterialTheme.typography.labelSmall,
                                color = colorScheme.onSurfaceVariant
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                OutlinedButton(
                                    onClick = { onAdjustOffer(-10) },
                                    contentPadding = PaddingValues(horizontal = 10.dp),
                                    modifier = Modifier.height(48.dp).testTag("offer_minus_10")
                                ) {
                                    Icon(Icons.Filled.Remove, contentDescription = "Decrease R10", modifier = Modifier.size(16.dp))
                                    Text("R10")
                                }
                                OutlinedTextField(
                                    value = draft.customOfferText,
                                    onValueChange = onOfferTextChange,
                                    singleLine = true,
                                    prefix = { Text("R ") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1f).testTag("offer_amount_input")
                                )
                                OutlinedButton(
                                    onClick = { onAdjustOffer(10) },
                                    contentPadding = PaddingValues(horizontal = 10.dp),
                                    modifier = Modifier.height(48.dp).testTag("offer_plus_10")
                                ) {
                                    Icon(Icons.Filled.Add, contentDescription = "Increase R10", modifier = Modifier.size(16.dp))
                                    Text("R10")
                                }
                            }
                        }
                    }
                }
            }

            // Compact Cash / Wallet / Card selector directly above CTA
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                PaymentMethod.entries.forEach { method ->
                    val selected = draft.paymentMethod == method
                    val icon = when (method) {
                        PaymentMethod.CASH -> Icons.Filled.Payments
                        PaymentMethod.WALLET -> Icons.Filled.AccountBalanceWallet
                        PaymentMethod.CARD -> Icons.Filled.CreditCard
                    }
                    FilterChip(
                        selected = selected,
                        onClick = { onSelectPaymentMethod(method) },
                        leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        label = {
                            val text = if (method == PaymentMethod.WALLET && selected) {
                                "Wallet (${LaunchPolicy.formatZarMinor(walletBalanceMinor)})"
                            } else method.label
                            Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = wayfareColors.brandHighlight,
                            selectedLabelColor = colorScheme.onPrimaryContainer
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("payment_chip_${method.name.lowercase()}")
                    )
                }
            }

            AnimatedVisibility(visible = lowWallet) {
                Text(
                    text = "Wallet balance is below this fare. Top up in Wallet or switch to Card / Cash.",
                    style = MaterialTheme.typography.labelSmall,
                    color = colorScheme.error
                )
            }

            Button(
                onClick = onRequestRide,
                enabled = draft.isRouteValidForRequest,
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(containerColor = colorScheme.primary, contentColor = colorScheme.onPrimary),
                modifier = Modifier.fillMaxWidth().height(54.dp).tactilePressScale().testTag("request_ride_cta")
            ) {
                Text(
                    text = if (draft.routeLoading) {
                        "Calculating route..."
                    } else {
                        "Request Vhaya ${draft.category.label} · ${LaunchPolicy.formatZar(draft.effectiveFare)}"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
