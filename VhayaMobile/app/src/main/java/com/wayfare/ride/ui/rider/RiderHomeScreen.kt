package com.wayfare.ride.ui.rider

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.wayfare.ride.domain.LaunchPolicy
import com.wayfare.ride.domain.RideCoordinates
import com.wayfare.ride.domain.RideStatus
import com.wayfare.ride.domain.RideTrip
import com.wayfare.ride.ui.RiderBookingStep
import com.wayfare.ride.ui.RiderTab
import com.wayfare.ride.ui.WayfareUiState
import com.wayfare.ride.ui.WayfareViewModel
import com.wayfare.ride.ui.components.OsmRouteMap
import com.wayfare.ride.ui.components.dialEmergency112
import com.wayfare.ride.ui.components.tactilePressScale
import com.wayfare.ride.ui.theme.LocalWayfareColors
import kotlin.math.ceil

@Composable
fun RiderHomeScreen(
    uiState: WayfareUiState,
    viewModel: WayfareViewModel,
    onOpenAccount: () -> Unit,
    onShowMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val activeTrip = uiState.riderActiveTrip
    val draft = uiState.booking
    var mapCenterCoords by remember { mutableStateOf(draft.pickupCoords) }
    var locationPermissionDenied by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val granted = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            locationPermissionDenied = false
            val coords = resolveDeviceOrFallbackLocation(context)
            viewModel.resolvePinLocation(coords, forPickup = true, fromGps = true)
            onShowMessage("Resolving GPS pickup address...")
        } else {
            locationPermissionDenied = true
            viewModel.setPickupPoint("Current map area", LaunchPolicy.DEFAULT_MAP_CENTER, fromGps = false)
        }
    }

    fun requestGpsPickup() {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (fine || coarse) {
            locationPermissionDenied = false
            val coords = resolveDeviceOrFallbackLocation(context)
            viewModel.resolvePinLocation(coords, forPickup = true, fromGps = true)
            onShowMessage("Updating pickup from GPS...")
        } else {
            locationPermissionLauncher.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            )
        }
    }

    LaunchedEffect(uiState.apiBaseUrl) {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if ((fine || coarse) && !draft.gpsUsingDeviceLocation && draft.destinationCoords == null && uiState.apiBaseUrl.isNotBlank()) {
            val coords = resolveDeviceOrFallbackLocation(context)
            viewModel.resolvePinLocation(coords, forPickup = true, fromGps = true)
        }
    }

    LaunchedEffect(draft.step) {
        if (activeTrip == null && draft.step == RiderBookingStep.MAP_PIN_PICKER) {
            viewModel.previewPinLocation(mapCenterCoords)
        }
    }

    val recentDestinations = remember(uiState.riderHistoryTrips, uiState.savedDestinations) {
        val savedLabels = uiState.savedDestinations.mapTo(mutableSetOf()) { it.label }
        uiState.riderHistoryTrips
            .asSequence()
            .filter { it.destination.isNotBlank() && it.destinationCoordinates != null && !savedLabels.contains(it.destination) }
            .distinctBy { it.destination }
            .take(3)
            .map { QuickPlaceShortcut(label = it.destination, coordinates = it.destinationCoordinates!!) }
            .toList()
    }

    if (activeTrip == null && draft.step != RiderBookingStep.HOME_COMPACT) {
        BackHandler { viewModel.stepBackInBooking() }
    }

    Box(modifier = modifier.fillMaxSize()) {
        OsmRouteMap(
            modifier = Modifier.fillMaxSize(),
            pickup = activeTrip?.pickupCoordinates ?: draft.pickupCoords,
            destination = activeTrip?.destinationCoordinates ?: draft.destinationCoords,
            driverLocation = activeTrip?.liveDriverLocation,
            route = activeTrip?.route ?: draft.previewRoute ?: draft.visualRoute,
            pickupLabel = activeTrip?.pickup ?: draft.pickupLabel,
            destinationLabel = activeTrip?.destination ?: draft.destinationLabel.takeIf { it.isNotBlank() },
            isMatching = activeTrip?.status?.isOpenForDrivers == true,
            isPinPickerMode = activeTrip == null && draft.step == RiderBookingStep.MAP_PIN_PICKER,
            onCameraCenterChanged = { coords ->
                mapCenterCoords = coords
                if (activeTrip == null && draft.step == RiderBookingStep.MAP_PIN_PICKER) {
                    viewModel.previewPinLocation(coords)
                }
            },
            controlsTopOffsetDp = if (locationPermissionDenied && activeTrip == null) 202 else 114,
            bottomContentPaddingDp = when {
                activeTrip != null -> 300
                draft.step == RiderBookingStep.HOME_COMPACT -> 225
                else -> 285
            }
        )

        // Minimalist top controls + live active-trip ETA banner
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(horizontal = 14.dp, vertical = 10.dp)
                .widthIn(max = 580.dp)
                .fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                    shadowElevation = 3.dp,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier
                        .size(48.dp)
                        .tactilePressScale()
                        .clip(CircleShape)
                        .clickable(onClick = onOpenAccount)
                        .testTag("rider_account_btn")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Filled.Person,
                            contentDescription = "Account and settings",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                if (activeTrip == null && draft.step == RiderBookingStep.HOME_COMPACT) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                        shadowElevation = 3.dp,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { viewModel.openSearchSheet(forPickup = true) }
                            .testTag("pickup_pill")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(start = 12.dp, end = 6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (draft.gpsUsingDeviceLocation) LocalWayfareColors.current.positive
                                        else MaterialTheme.colorScheme.primary
                                    )
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (draft.gpsUsingDeviceLocation) "GPS pickup" else "Pickup",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                                Text(
                                    text = draft.pickupLabel.substringBefore(" ·"),
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            IconButton(
                                onClick = { requestGpsPickup() },
                                modifier = Modifier.size(40.dp).testTag("pickup_gps_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.GpsFixed,
                                    contentDescription = "Use current GPS location",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                } else {
                    Spacer(Modifier.weight(1f))
                }

                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                    shadowElevation = 3.dp,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier
                        .size(48.dp)
                        .tactilePressScale()
                        .clip(CircleShape)
                        .clickable { dialEmergency112(context) }
                        .testTag("rider_safety_shield_top")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Filled.Shield,
                            contentDescription = "Emergency 112",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = locationPermissionDenied && activeTrip == null,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    shadowElevation = 3.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(start = 12.dp, top = 8.dp, end = 8.dp, bottom = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.LocationOff,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                        Column(
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = "Location access is off",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Search or pin your pickup on the map.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        TextButton(
                            onClick = {
                                locationPermissionDenied = false
                                viewModel.openSearchSheet(forPickup = true)
                            }
                        ) {
                            Text("Set pickup")
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = activeTrip != null &&
                    (activeTrip.status == RideStatus.ACCEPTED ||
                        activeTrip.status == RideStatus.ARRIVED ||
                        activeTrip.status == RideStatus.IN_PROGRESS),
                enter = slideInVertically { -it / 2 } + fadeIn(),
                exit = slideOutVertically { -it / 2 } + fadeOut()
            ) {
                activeTrip?.let { trip ->
                    RiderLiveEtaPill(trip = trip)
                }
            }
        }

        // Briefly animated bottom sheet area
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .statusBarsPadding()
                .imePadding()
                .widthIn(max = 600.dp)
                .fillMaxWidth()
                .animateContentSize(animationSpec = tween(durationMillis = 200))
        ) {
            if (activeTrip != null) {
                RiderActiveTripSheet(
                    trip = activeTrip,
                    trustedContacts = uiState.trustedContacts,
                    dismissedOfferIds = uiState.dismissedOfferIds,
                    viewModel = viewModel
                )
            } else {
                AnimatedContent(
                    targetState = draft.step,
                    transitionSpec = {
                        (slideInVertically(
                            animationSpec = tween(durationMillis = 220),
                            initialOffsetY = { it / 3 }
                        ) + fadeIn()) togetherWith (slideOutVertically { it / 4 } + fadeOut())
                    },
                    label = "riderBookingStep"
                ) { step ->
                    when (step) {
                        RiderBookingStep.HOME_COMPACT -> CompactWhereToSheet(
                            riderName = uiState.currentSession?.displayName.orEmpty(),
                            walletBalanceMinor = uiState.walletBalanceMinor,
                            savedDestinations = uiState.savedDestinations,
                            recentDestinations = recentDestinations,
                            onWhereToClick = { viewModel.openSearchSheet(forPickup = false) },
                            onToggleScheduleLater = {
                                viewModel.setScheduledOffset(if (draft.scheduledOffsetMinutes == null) 30 else null)
                                viewModel.openSearchSheet(forPickup = false)
                            },
                            onOpenWallet = { viewModel.selectRiderTab(RiderTab.WALLET) },
                            onSelectQuickPlace = { label, coords ->
                                viewModel.selectDestination(label, coords)
                            },
                            onPinOnMap = { viewModel.openMapPinPicker(forPickup = false) },
                            onOpenSavedPlaces = onOpenAccount
                        )
                        RiderBookingStep.SEARCH_DESTINATION -> SearchDestinationSheet(
                            draft = draft,
                            uiState = uiState,
                            recentDestinations = recentDestinations,
                            onBack = { viewModel.backToCompactHome() },
                            onUseGps = { requestGpsPickup() },
                            onQueryChange = { q -> viewModel.searchPlaces(q) },
                            onPickOnMap = { forPickup -> viewModel.openMapPinPicker(forPickup) },
                            onSelectPlace = { label, coords, forPickup ->
                                if (forPickup) viewModel.setPickupPoint(label, coords)
                                else viewModel.selectDestination(label, coords)
                            },
                            onSavePlace = { label, coords -> viewModel.savePlace(label, coords) }
                        )
                        RiderBookingStep.MAP_PIN_PICKER -> MapPinPickerSheet(
                            pickingForPickup = draft.pickingForPickup,
                            centerCoords = mapCenterCoords,
                            pinPreviewAddress = draft.pinPreviewAddress,
                            pinPreviewLoading = draft.pinPreviewLoading,
                            onCancel = { viewModel.stepBackInBooking() },
                            onConfirmPin = { coords ->
                                viewModel.resolvePinLocation(coords, forPickup = draft.pickingForPickup)
                            }
                        )
                        RiderBookingStep.CHOOSE_RIDE_FARE -> {
                            val isSaved = uiState.savedDestinations.any { it.label == draft.destinationLabel }
                            ChooseRideAndFareSheet(
                                draft = draft,
                                walletBalanceMinor = uiState.walletBalanceMinor,
                                isDestinationSaved = isSaved,
                                onToggleSaveDestination = {
                                    draft.destinationCoords?.let { coords ->
                                        if (!isSaved) viewModel.savePlace(draft.destinationLabel, coords)
                                    }
                                },
                                onBack = { viewModel.stepBackInBooking() },
                                onSelectCategory = { viewModel.selectCategory(it) },
                                onToggleOffer = { viewModel.toggleOfferExpanded() },
                                onOfferTextChange = { viewModel.updateCustomOfferText(it) },
                                onAdjustOffer = { viewModel.adjustCustomOffer(it) },
                                onToggleSchedule = {
                                    val next = when (draft.scheduledOffsetMinutes) {
                                        null -> 30
                                        30 -> 60
                                        else -> null
                                    }
                                    viewModel.setScheduledOffset(next)
                                },
                                onSelectPaymentMethod = { viewModel.selectPaymentMethod(it) },
                                onRetryRoute = { viewModel.refreshRoutePreview() },
                                onRequestRide = {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    viewModel.submitRideRequest()
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    uiState.pendingRatingTrip?.let { finishedTrip ->
        RiderPostTripRatingSheet(
            trip = finishedTrip,
            onSubmitRating = { stars, _ -> viewModel.rateTrip(finishedTrip.id, stars) },
            onReportIssue = { cat, msg -> viewModel.createSupportCase(finishedTrip, cat, "high", msg) },
            onDismiss = { viewModel.dismissPostTripRating() }
        )
    }
}

@Composable
private fun RiderLiveEtaPill(trip: RideTrip) {
    val colorScheme = MaterialTheme.colorScheme
    val wayfareColors = LocalWayfareColors.current

    val pulseTransition = rememberInfiniteTransition(label = "etaPulse")
    val dotScale by pulseTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "etaDotScale"
    )

    val label = remember(trip.status, trip.liveDriverLocation, trip.pickupCoordinates, trip.destinationCoordinates, trip.tripPin) {
        val target = when (trip.status) {
            RideStatus.ACCEPTED -> trip.pickupCoordinates
            RideStatus.IN_PROGRESS -> trip.destinationCoordinates
            else -> null
        }
        val driver = trip.liveDriverLocation
        val meters = if (driver != null && target != null) LaunchPolicy.distanceMeters(driver, target) else null
        val mins = meters?.let { ceil(it / 500.0).toInt().coerceAtLeast(1) }
        when (trip.status) {
            RideStatus.ACCEPTED -> when {
                meters == null -> "Driver assigned · en route"
                meters <= 150.0 -> "Driver nearby (< 1 min)"
                else -> "Driver arriving in ~$mins min · ${"%.1f".format(meters / 1000.0)} km"
            }
            RideStatus.ARRIVED -> "Driver at pickup · PIN ${trip.tripPin.orEmpty()}"
            RideStatus.IN_PROGRESS -> if (mins != null) "On trip · ~$mins min to destination" else "On trip"
            else -> trip.status.label
        }
    }

    Surface(
        shape = RoundedCornerShape(22.dp),
        color = colorScheme.primary,
        shadowElevation = 6.dp,
        modifier = Modifier.testTag("rider_live_eta_pill")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(9.dp)
                    .scale(dotScale)
                    .clip(CircleShape)
                    .background(wayfareColors.routeHighlight)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = colorScheme.onPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@SuppressLint("MissingPermission")
private fun resolveDeviceOrFallbackLocation(context: Context): RideCoordinates {
    return try {
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
        val loc = providers.firstNotNullOfOrNull { provider ->
            runCatching { manager?.getLastKnownLocation(provider) }.getOrNull()
        }
        if (loc != null) {
            val coords = RideCoordinates(loc.latitude, loc.longitude)
            if (LaunchPolicy.isWithinSouthAfrica(coords)) coords else LaunchPolicy.DEFAULT_MAP_CENTER
        } else {
            LaunchPolicy.DEFAULT_MAP_CENTER
        }
    } catch (_: Throwable) {
        LaunchPolicy.DEFAULT_MAP_CENTER
    }
}
