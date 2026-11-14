package com.wayfare.ride.ui.driver

import android.Manifest
import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wayfare.ride.domain.LaunchPolicy
import com.wayfare.ride.domain.LocationPermissionState
import com.wayfare.ride.domain.PricingMode
import com.wayfare.ride.domain.RideCoordinates
import com.wayfare.ride.domain.RideStatus
import com.wayfare.ride.domain.RideTrip
import com.wayfare.ride.domain.UserAccountSummary
import com.wayfare.ride.service.RideTrackingService
import com.wayfare.ride.ui.DriverTab
import com.wayfare.ride.ui.WayfareUiState
import com.wayfare.ride.ui.WayfareViewModel
import com.wayfare.ride.ui.components.LocationDegradedBanner
import com.wayfare.ride.ui.components.LocationRationaleDialog
import com.wayfare.ride.ui.components.MonoBadge
import com.wayfare.ride.ui.components.OsmRouteMap
import com.wayfare.ride.ui.components.StatusChip
import com.wayfare.ride.ui.components.evaluateLocationPermissionState
import com.wayfare.ride.ui.components.openAppLocationSettings
import com.wayfare.ride.ui.components.tactilePressScale
import com.wayfare.ride.ui.theme.LocalWayfareColors
import com.wayfare.ride.ui.theme.WayfareMonoFamily
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

private const val REQUEST_COUNTDOWN_SECONDS = 15

private fun resolveRequesterName(accounts: List<UserAccountSummary>, riderId: String): String {
    return accounts.firstOrNull { it.userId == riderId }?.displayName
        ?: "Vhaya Requester"
}

@Composable
fun DriverHomeScreen(
    uiState: WayfareUiState,
    viewModel: WayfareViewModel,
    onOpenAccount: () -> Unit,
    onOpenOnboarding: () -> Unit,
    modifier: Modifier = Modifier
) {
    val driverApp = uiState.currentDriverApp
    val isApproved = driverApp?.verificationStatus == "approved"
    val isOnline = driverApp?.isOnline == true
    val activeTrip = uiState.driverActiveTrip
    val openRequests = uiState.driverOpenRequests
    val currentRequest = if (openRequests.isNotEmpty()) {
        openRequests[uiState.driverRequestIndex % openRequests.size]
    } else null
    val haptics = LocalHapticFeedback.current
    val context = LocalContext.current
    val activity = context as? Activity

    fun currentLocationState(): LocationPermissionState {
        val rationale = activity?.let {
            ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.ACCESS_FINE_LOCATION)
        } ?: false
        return evaluateLocationPermissionState(
            context = context,
            shouldShowRationale = rationale,
            hasRequestedBefore = viewModel.hasRequestedLocationPermission()
        )
    }

    var driverLocationState by remember { mutableStateOf(currentLocationState()) }
    var showDriverRationale by remember { mutableStateOf(false) }

    val driverPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        viewModel.markLocationPermissionRequested()
        driverLocationState = currentLocationState()
    }

    LaunchedEffect(
        activeTrip?.id,
        activeTrip?.status,
        uiState.privacyPreferences.locationSharingConsent,
        uiState.privacyPreferences.tripOnlyPrivacyMode,
        driverLocationState
    ) {
        val trip = activeTrip
        val shouldShare = trip != null &&
            driverLocationState.isGranted &&
            uiState.privacyPreferences.locationSharingConsent &&
            (!uiState.privacyPreferences.tripOnlyPrivacyMode || trip.status == RideStatus.IN_PROGRESS)
        if (shouldShare && trip != null) {
            val summary = "${trip.pickup.substringBefore(" ·")} → ${trip.destination.substringBefore(" ·")}"
            RideTrackingService.startForTrip(context, trip.id, summary)
        } else {
            RideTrackingService.stopForTripEnd(context)
        }
    }

    LaunchedEffect(isApproved, isOnline, activeTrip == null, uiState.driverSoundEnabled, currentRequest?.id) {
        if (isApproved && isOnline && activeTrip == null && uiState.driverSoundEnabled && currentRequest != null) {
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            com.wayfare.ride.audio.SoundAlertPlayer.playRideOfferChime(context)
        }
    }

    val todayEarningsZar = remember(uiState.driverEarnings, uiState.driverCompletedTrips) {
        if (uiState.driverEarnings.totalNetMinor > 0L || uiState.driverEarnings.rideCount > 0) {
            uiState.driverEarnings.totalNetMinor / 100.0
        } else {
            uiState.driverCompletedTrips.sumOf { it.fare }
        }
    }

    val isDriveMode = activeTrip?.status == RideStatus.IN_PROGRESS && !uiState.driverDriveModeMinimized
    val demandZones = remember(uiState.trips) { LaunchPolicy.buildDemandHeatZones(uiState.trips) }
    var selectedDemandZone by remember { mutableStateOf<LaunchPolicy.DemandHeatZone?>(null) }
    var focusTargetCoords by remember { mutableStateOf<RideCoordinates?>(null) }
    var focusTrigger by remember { mutableIntStateOf(0) }

    LaunchedEffect(demandZones, uiState.driverHeatmapEnabled, isDriveMode) {
        if (!uiState.driverHeatmapEnabled || isDriveMode) {
            selectedDemandZone = null
        } else {
            selectedDemandZone = selectedDemandZone?.let { current ->
                demandZones.firstOrNull { it.id == current.id }
            }
        }
    }

    val mapPickup = when {
        activeTrip?.status == RideStatus.IN_PROGRESS ->
            activeTrip.liveDriverLocation ?: uiState.driverCurrentCoords
        activeTrip != null -> activeTrip.pickupCoordinates
        isOnline && currentRequest != null -> currentRequest.pickupCoordinates
        else -> uiState.driverCurrentCoords
    }
    val mapDest = when {
        activeTrip != null -> activeTrip.destinationCoordinates
        isOnline && currentRequest != null -> currentRequest.destinationCoordinates
        else -> null
    }
    val mapRoute = when {
        activeTrip != null -> activeTrip.route
        isOnline && currentRequest != null -> currentRequest.route
        else -> null
    }

    Box(modifier = modifier.fillMaxSize()) {
        OsmRouteMap(
            modifier = Modifier.fillMaxSize(),
            pickup = mapPickup,
            destination = mapDest,
            driverLocation = activeTrip?.liveDriverLocation ?: uiState.driverCurrentCoords,
            route = mapRoute,
            pickupLabel = activeTrip?.pickup ?: currentRequest?.takeIf { isOnline }?.pickup,
            destinationLabel = activeTrip?.destination ?: currentRequest?.takeIf { isOnline }?.destination,
            isMatching = isOnline && activeTrip == null && currentRequest != null,
            controlsTopOffsetDp = 114,
            bottomContentPaddingDp = when {
                isDriveMode -> 230
                activeTrip != null -> 310
                currentRequest != null && isOnline -> 315
                else -> 195
            },
            demandZones = demandZones,
            showDemandHeatmap = uiState.driverHeatmapEnabled && !isDriveMode,
            onToggleDemandHeatmap = { viewModel.toggleDriverHeatmap() },
            onDemandZoneSelected = { zone -> selectedDemandZone = zone },
            focusTarget = focusTargetCoords,
            focusTrigger = focusTrigger
        )

        if (!isDriveMode) {
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
                            .testTag("driver_account_btn")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Filled.Person,
                                contentDescription = "Account and settings",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    DriverTopOnlineBar(
                        isOnline = isOnline,
                        isApproved = isApproved,
                        soundEnabled = uiState.driverSoundEnabled,
                        todayEarningsZar = todayEarningsZar,
                        onToggleOnline = {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            viewModel.setDriverOnline(it)
                        },
                        onToggleSound = {
                            val enabling = !uiState.driverSoundEnabled
                            viewModel.toggleDriverSound()
                            if (enabling) {
                                com.wayfare.ride.audio.SoundAlertPlayer.playRideOfferChime(context)
                            }
                        },
                        modifier = Modifier.weight(1f)
                    )
                }

                AnimatedVisibility(
                    visible = !driverLocationState.isGranted && (isOnline || activeTrip != null),
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    LocationDegradedBanner(
                        permissionState = driverLocationState,
                        onRetryPermission = {
                            if (activity != null && ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.ACCESS_FINE_LOCATION)) {
                                showDriverRationale = true
                            } else {
                                viewModel.markLocationPermissionRequested()
                                driverPermissionLauncher.launch(
                                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                                )
                            }
                        },
                        onOpenSettings = { openAppLocationSettings(context) }
                    )
                }

                AnimatedVisibility(
                    visible = isApproved && isOnline && activeTrip == null && currentRequest != null,
                    enter = slideInVertically { -it / 2 } + fadeIn(),
                    exit = slideOutVertically { -it / 2 } + fadeOut()
                ) {
                    currentRequest?.let { req ->
                        val pickupKm = LaunchPolicy.haversineKm(uiState.driverCurrentCoords, req.pickupCoordinates)
                        IncomingRequestAlertPill(
                            pickupKm = pickupKm,
                            soundEnabled = uiState.driverSoundEnabled
                        )
                    }
                }

                AnimatedVisibility(
                    visible = uiState.driverHeatmapEnabled && selectedDemandZone != null,
                    enter = slideInVertically { -it / 2 } + fadeIn(),
                    exit = slideOutVertically { -it / 2 } + fadeOut()
                ) {
                    selectedDemandZone?.let { zone ->
                        val distKm = LaunchPolicy.haversineKm(uiState.driverCurrentCoords, zone.centroid)
                        DemandZoneInspectorPill(
                            zone = zone,
                            distanceKm = distKm,
                            onDismiss = { selectedDemandZone = null }
                        )
                    }
                }

                AnimatedVisibility(
                    visible = isApproved && uiState.driverHeatmapEnabled && selectedDemandZone == null &&
                        activeTrip == null && currentRequest == null,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    LiveDemandSummaryPill(
                        demandZones = demandZones,
                        onFocusHottest = {
                            demandZones.firstOrNull()?.let { topZone ->
                                selectedDemandZone = topZone
                                focusTargetCoords = topZone.centroid
                                focusTrigger++
                            }
                        }
                    )
                }
            }
        }

        if (activeTrip != null) {
            val requesterName = resolveRequesterName(uiState.userAccounts, activeTrip.riderId)
            if (isDriveMode) {
                BackHandler { viewModel.setDriverDriveModeMinimized(true) }
                DriverDriveModeOverlay(
                    trip = activeTrip,
                    requesterName = requesterName,
                    driverCoords = uiState.driverCurrentCoords,
                    privacyPreferences = uiState.privacyPreferences,
                    trustedContacts = uiState.trustedContacts,
                    viewModel = viewModel,
                    onMinimize = { viewModel.setDriverDriveModeMinimized(true) }
                )
            } else {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .widthIn(max = 600.dp)
                        .fillMaxWidth()
                ) {
                    DriverActiveTripSheet(
                        trip = activeTrip,
                        requesterName = requesterName,
                        driverCoords = uiState.driverCurrentCoords,
                        privacyPreferences = uiState.privacyPreferences,
                        trustedContacts = uiState.trustedContacts,
                        viewModel = viewModel,
                        onExpandDriveMode = { viewModel.setDriverDriveModeMinimized(false) }
                    )
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .widthIn(max = 600.dp)
                    .fillMaxWidth()
            ) {
                val sheetKey = when {
                    !isApproved -> "gate"
                    !isOnline -> "offline"
                    currentRequest != null -> "req_${currentRequest.id}"
                    else -> "waiting"
                }
                AnimatedContent(
                    targetState = sheetKey,
                    transitionSpec = {
                        (slideInVertically(
                            animationSpec = tween(durationMillis = 200)
                        ) { it / 3 } + fadeIn()) togetherWith
                            (slideOutVertically { it / 4 } + fadeOut())
                    },
                    label = "driverBottomSheet"
                ) { key ->
                    when {
                        key == "gate" -> DriverPendingGateSheet(
                            statusLabel = driverApp?.applicationStatus ?: "draft",
                            onOpenProfile = onOpenOnboarding
                        )
                        key == "offline" -> DriverOfflineSheet(
                            vehicleLabel = "${driverApp?.vehicleColor.orEmpty()} ${driverApp?.vehicle.orEmpty()}".trim(),
                            registration = driverApp?.registration.orEmpty(),
                            onGoOnline = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                viewModel.setDriverOnline(true)
                            }
                        )
                        key.startsWith("req_") && currentRequest != null -> DriverRequestSheet(
                            trip = currentRequest,
                            requesterName = resolveRequesterName(uiState.userAccounts, currentRequest.riderId),
                            requestIndex = (uiState.driverRequestIndex % openRequests.size) + 1,
                            totalCount = openRequests.size,
                            driverCoords = uiState.driverCurrentCoords,
                            onAccept = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                viewModel.driverSubmitOffer(currentRequest, currentRequest.fare)
                            },
                            onCounter = { amount ->
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                viewModel.driverSubmitOffer(currentRequest, amount)
                            },
                            onSkip = { viewModel.skipDriverRequest(openRequests.size) }
                        )
                        else -> DriverWaitingSheet(onGoOffline = { viewModel.setDriverOnline(false) })
                    }
                }
            }
        }

        if (showDriverRationale) {
            LocationRationaleDialog(
                isDriver = true,
                onAllow = {
                    showDriverRationale = false
                    viewModel.markLocationPermissionRequested()
                    driverPermissionLauncher.launch(
                        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                    )
                },
                onDismiss = { showDriverRationale = false }
            )
        }

        uiState.pendingDriverSummaryTrip?.let { summaryTrip ->
            DriverPostTripSummarySheet(
                trip = summaryTrip,
                requesterName = resolveRequesterName(uiState.userAccounts, summaryTrip.riderId),
                weeklyPlanActive = uiState.driverWeeklyPlanActive,
                onViewEarnings = {
                    viewModel.dismissDriverTripSummary()
                    viewModel.selectDriverTab(DriverTab.EARNINGS)
                },
                onDismiss = { viewModel.dismissDriverTripSummary() }
            )
        }
    }
}

@Composable
private fun DriverTopOnlineBar(
    isOnline: Boolean,
    isApproved: Boolean,
    soundEnabled: Boolean,
    todayEarningsZar: Double,
    onToggleOnline: (Boolean) -> Unit,
    onToggleSound: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val wayfareColors = LocalWayfareColors.current

    val pulseTransition = rememberInfiniteTransition(label = "onlineDotPulse")
    val dotScale by pulseTransition.animateFloat(
        initialValue = 0.88f,
        targetValue = 1.32f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dotScale"
    )

    val borderColor by animateColorAsState(
        targetValue = if (isOnline) colorScheme.primary else colorScheme.outline,
        label = "onlineBorder"
    )

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = colorScheme.surface.copy(alpha = 0.96f),
        shadowElevation = 3.dp,
        border = BorderStroke(1.dp, borderColor),
        modifier = modifier.height(48.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 12.dp, end = 6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .scale(if (isOnline) dotScale else 1f)
                        .clip(CircleShape)
                        .background(if (isOnline) wayfareColors.positive else colorScheme.outline)
                )
                Text(
                    text = if (isOnline) "Online" else "Offline",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                MonoBadge(
                    text = LaunchPolicy.formatZar(todayEarningsZar),
                    containerColor = colorScheme.primaryContainer,
                    contentColor = colorScheme.onPrimaryContainer
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onToggleSound,
                    modifier = Modifier.size(48.dp).testTag("driver_sound_toggle")
                ) {
                    Icon(
                        imageVector = if (soundEnabled) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                        contentDescription = "Toggle alert sound",
                        tint = if (soundEnabled) colorScheme.primary else colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Switch(
                    checked = isOnline,
                    onCheckedChange = onToggleOnline,
                    enabled = isApproved,
                    colors = SwitchDefaults.colors(checkedTrackColor = colorScheme.primary),
                    modifier = Modifier.testTag("driver_online_switch")
                )
            }
        }
    }
}

@Composable
private fun IncomingRequestAlertPill(
    pickupKm: Double?,
    soundEnabled: Boolean
) {
    val colorScheme = MaterialTheme.colorScheme
    val wayfareColors = LocalWayfareColors.current

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = colorScheme.primary,
        shadowElevation = 6.dp
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.NotificationsActive,
                contentDescription = null,
                tint = wayfareColors.routeHighlight,
                modifier = Modifier.size(16.dp)
            )
            val distPart = pickupKm?.let { " · ${"%.1f".format(it)} km away" }.orEmpty()
            Text(
                text = "New ride$distPart · ${if (soundEnabled) "Alert chime ON" else "Muted"}",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = colorScheme.onPrimary
            )
        }
    }
}

@Composable
private fun LiveDemandSummaryPill(
    demandZones: List<LaunchPolicy.DemandHeatZone>,
    onFocusHottest: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val wayfareColors = LocalWayfareColors.current
    val openCount = demandZones.sumOf { it.liveOpenCount }
    val hottest = demandZones.firstOrNull()
    val summaryText = if (hottest == null) {
        "Demand Heatmap ON · No ride requests yet"
    } else if (openCount > 0) {
        "${demandZones.size} hot zones · $openCount live · Tap ${hottest.label}"
    } else {
        "${demandZones.size} active zones · Top: ${hottest.label}"
    }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = colorScheme.surface.copy(alpha = 0.94f),
        border = BorderStroke(1.dp, wayfareColors.heatMid.copy(alpha = 0.45f)),
        shadowElevation = 4.dp,
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .clickable(enabled = hottest != null, onClick = onFocusHottest)
            .testTag("driver_demand_pill")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.LocalFireDepartment,
                contentDescription = null,
                tint = if (openCount > 0) wayfareColors.heatHigh else wayfareColors.heatLow,
                modifier = Modifier.size(15.dp)
            )
            Text(
                text = summaryText,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun DemandZoneInspectorPill(
    zone: LaunchPolicy.DemandHeatZone,
    distanceKm: Double?,
    onDismiss: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val wayfareColors = LocalWayfareColors.current
    val tierColor = when (zone.tier) {
        LaunchPolicy.DemandTier.HIGH -> wayfareColors.heatHigh
        LaunchPolicy.DemandTier.MEDIUM -> wayfareColors.heatMid
        LaunchPolicy.DemandTier.LOW -> wayfareColors.heatLow
    }

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = colorScheme.surface.copy(alpha = 0.96f),
        border = BorderStroke(1.dp, tierColor.copy(alpha = 0.65f)),
        shadowElevation = 6.dp,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("driver_demand_inspector")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = tierColor.copy(alpha = 0.16f),
                modifier = Modifier.size(34.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.LocalFireDepartment,
                        contentDescription = null,
                        tint = tierColor,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                val distLabel = distanceKm?.let { " · ${"%.1f".format(it)} km" }.orEmpty()
                Text(
                    text = "${zone.label} (${zone.tier.label})$distLabel",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val farePart = if (zone.avgFareZar > 0.0) " · Avg ${LaunchPolicy.formatZar(zone.avgFareZar)}" else ""
                Text(
                    text = "${zone.liveOpenCount} live · ${zone.requestCount} recent requests$farePart",
                    style = MaterialTheme.typography.labelSmall,
                    color = colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.size(48.dp).testTag("driver_demand_inspector_close")
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Close hotspot details",
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun DriverOfflineSheet(
    vehicleLabel: String,
    registration: String,
    onGoOnline: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        color = colorScheme.surface,
        shadowElevation = 8.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(18.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    Text("Ready to drive", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(vehicleLabel.ifBlank { "Vehicle details saved" }, style = MaterialTheme.typography.bodySmall, color = colorScheme.onSurfaceVariant)
                }
                if (registration.isNotBlank()) {
                    MonoBadge(text = registration)
                }
            }
            Button(
                onClick = onGoOnline,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .tactilePressScale()
                    .testTag("driver_go_online_cta")
            ) {
                Text("Go online", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun DriverWaitingSheet(onGoOffline: () -> Unit) {
    val colorScheme = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        color = colorScheme.surface,
        shadowElevation = 8.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.padding(18.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Scanning nearby requests", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("Incoming requests appear immediately.", style = MaterialTheme.typography.bodySmall, color = colorScheme.onSurfaceVariant)
            }
            OutlinedButton(
                onClick = onGoOffline,
                modifier = Modifier.height(48.dp)
            ) {
                Text("Go offline")
            }
        }
    }
}

@Composable
private fun DriverPendingGateSheet(
    statusLabel: String,
    onOpenProfile: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        color = colorScheme.surface,
        shadowElevation = 8.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(18.dp)) {
            Text("Finish verification to go online", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                text = "Application status: ${statusLabel.replace("_", " ")} · Submit your application and email your documents to ${LaunchPolicy.DRIVER_DOCS_EMAIL} for manual review.",
                style = MaterialTheme.typography.bodySmall,
                color = colorScheme.onSurfaceVariant
            )
            Button(
                onClick = onOpenProfile,
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) {
                Text("Open driver profile")
            }
        }
    }
}

@Composable
private fun DriverRequestSheet(
    trip: RideTrip,
    requesterName: String,
    requestIndex: Int,
    totalCount: Int,
    driverCoords: RideCoordinates,
    onAccept: () -> Unit,
    onCounter: (Double) -> Unit,
    onSkip: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val wayfareColors = LocalWayfareColors.current
    val allowsCounter = trip.pricingMode == PricingMode.OFFER
    var showCounterRow by remember(trip.id) { mutableStateOf(false) }
    var counterAmountText by remember(trip.id, trip.fare) { mutableStateOf((trip.fare.roundToInt() + 10).toString()) }
    var secondsLeft by remember(trip.id) { mutableIntStateOf(REQUEST_COUNTDOWN_SECONDS) }

    LaunchedEffect(trip.id) {
        while (true) {
            delay(1_000L)
            secondsLeft = if (secondsLeft <= 1) REQUEST_COUNTDOWN_SECONDS else secondsLeft - 1
        }
    }

    val pickupKm = LaunchPolicy.haversineKm(driverCoords, trip.pickupCoordinates)
    val tripKm = trip.route?.distanceMeters?.let { it / 1000.0 }

    Surface(
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        color = colorScheme.surface,
        shadowElevation = 8.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "Ride request · $requestIndex of $totalCount",
                        style = MaterialTheme.typography.labelSmall,
                        color = colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = requesterName,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "${secondsLeft}s",
                        style = MaterialTheme.typography.labelLarge,
                        fontFamily = WayfareMonoFamily,
                        fontWeight = FontWeight.Bold,
                        color = if (secondsLeft <= 5) colorScheme.error else colorScheme.primary,
                        modifier = Modifier.testTag("driver_request_countdown")
                    )
                    Text(
                        text = trip.category.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = LaunchPolicy.formatZar(trip.fare),
                    fontFamily = WayfareMonoFamily,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = colorScheme.primary
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    PaymentMethodPill(method = trip.paymentMethod)
                    val chipText = listOfNotNull(
                        pickupKm?.let { "${"%.1f".format(it)} km pickup" },
                        tripKm?.let { "${"%.1f".format(it)} km" }
                    ).joinToString(" · ")
                    if (chipText.isNotEmpty()) {
                        StatusChip(
                            label = chipText,
                            containerColor = wayfareColors.brandHighlight,
                            contentColor = colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "A  ${trip.pickup.substringBefore(" ·")}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "B  ${trip.destination.substringBefore(" ·")}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (allowsCounter && showCounterRow) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = counterAmountText,
                        onValueChange = { text -> counterAmountText = text.filter { it.isDigit() }.take(5) },
                        prefix = { Text("R ") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f).testTag("driver_counter_input")
                    )
                    Button(
                        onClick = {
                            val amt = counterAmountText.toDoubleOrNull() ?: trip.fare
                            onCounter(amt)
                            showCounterRow = false
                        },
                        modifier = Modifier.height(52.dp).testTag("driver_send_counter_btn")
                    ) {
                        Text("Send")
                    }
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedButton(
                    onClick = onSkip,
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    modifier = Modifier.height(52.dp).testTag("driver_skip_request")
                ) {
                    Icon(Icons.Filled.SkipNext, contentDescription = "Skip request", modifier = Modifier.size(18.dp))
                    Text("Skip")
                }
                if (allowsCounter) {
                    OutlinedButton(
                        onClick = { showCounterRow = !showCounterRow },
                        contentPadding = PaddingValues(horizontal = 14.dp),
                        modifier = Modifier.height(52.dp).testTag("driver_toggle_counter")
                    ) {
                        Text("Offer")
                    }
                }
                Button(
                    onClick = onAccept,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .tactilePressScale()
                        .testTag("driver_accept_request")
                ) {
                    Text(
                        text = if (allowsCounter) {
                            "Accept ${LaunchPolicy.formatZar(trip.fare)}"
                        } else {
                            "Accept ${LaunchPolicy.formatZar(trip.fare)}"
                        },
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }
            }
        }
    }
}
