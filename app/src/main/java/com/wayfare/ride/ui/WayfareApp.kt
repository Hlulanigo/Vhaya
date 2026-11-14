package com.wayfare.ride.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LocalTaxi
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wayfare.ride.domain.AuthState
import com.wayfare.ride.domain.LaunchPolicy
import com.wayfare.ride.domain.RideRole
import com.wayfare.ride.notifications.SupportNotificationHelper
import com.wayfare.ride.ui.admin.AdminDriversTab
import com.wayfare.ride.ui.admin.AdminOverviewTab
import com.wayfare.ride.ui.admin.AdminPayoutsTab
import com.wayfare.ride.ui.admin.AdminSupportTab
import com.wayfare.ride.ui.auth.VhayaAuthScreen
import com.wayfare.ride.ui.common.AccountAndPlacesSheet
import com.wayfare.ride.ui.common.PrivacySettingsSheet
import com.wayfare.ride.ui.components.OfflineReconnectingBanner
import com.wayfare.ride.ui.components.VhayaTopBar
import com.wayfare.ride.ui.driver.DriverEarningsTab
import com.wayfare.ride.ui.driver.DriverHomeScreen
import com.wayfare.ride.ui.driver.DriverProfileTab
import com.wayfare.ride.ui.payments.PaymentReturnScreen
import com.wayfare.ride.ui.rider.RiderHomeScreen
import com.wayfare.ride.ui.rider.RiderSupportTab
import com.wayfare.ride.ui.rider.RiderTripsTab
import com.wayfare.ride.ui.rider.RiderWalletTab
import com.wayfare.ride.ui.theme.LocalWayfareColors
import kotlinx.coroutines.launch

@Composable
fun WayfareApp(viewModel: WayfareViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    var notificationAskedThisSession by remember { mutableStateOf(false) }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ -> }

    LaunchedEffect(viewModel) {
        launch {
            viewModel.events.collect { msg ->
                snackbarHostState.showSnackbar(msg)
            }
        }
        launch {
            viewModel.supportAlerts.collect { alert ->
                SupportNotificationHelper.showSupportUpdateNotification(context, alert)
                val action = snackbarHostState.showSnackbar(
                    message = "${alert.title}: ${alert.summary}",
                    actionLabel = "View",
                    duration = SnackbarDuration.Short
                )
                if (action == SnackbarResult.ActionPerformed) {
                    when (uiState.activeRole) {
                        RideRole.RIDER -> viewModel.selectRiderTab(RiderTab.SUPPORT)
                        RideRole.ADMIN -> viewModel.selectAdminTab(AdminTab.SUPPORT)
                        RideRole.DRIVER -> viewModel.selectDriverTab(DriverTab.PROFILE)
                    }
                }
            }
        }
        launch {
            viewModel.openUrlEvents.collect { url ->
                runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                }.onFailure {
                    snackbarHostState.showSnackbar("Open checkout link in browser: $url")
                }
            }
        }
    }

    LaunchedEffect(
        uiState.riderActiveTrip?.id,
        uiState.riderTab,
        uiState.activeRole,
        uiState.currentDriverApp?.isOnline,
        uiState.privacyPreferences.supportNotificationsEnabled
    ) {
        val driverGoingOnline = uiState.activeRole == RideRole.DRIVER && uiState.currentDriverApp?.isOnline == true
        val riderActive = uiState.riderActiveTrip != null || uiState.riderTab == RiderTab.SUPPORT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !notificationAskedThisSession &&
            !SupportNotificationHelper.hasNotificationPermission(context) &&
            (driverGoingOnline || (uiState.privacyPreferences.supportNotificationsEnabled && riderActive))
        ) {
            notificationAskedThisSession = true
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    LaunchedEffect(
        uiState.authState is AuthState.Authenticated,
        uiState.activeRole,
        uiState.currentDriverApp?.isOnline,
        uiState.driverActiveTrip?.id,
        uiState.riderActiveTrip?.id
    ) {
        val isAuthed = uiState.authState is AuthState.Authenticated
        if (!isAuthed) {
            com.wayfare.ride.notifications.RideNotificationHelper.dismissDriverOfferNotification(context)
            com.wayfare.ride.notifications.RideNotificationHelper.dismissRiderStatusNotification(context)
            com.wayfare.ride.service.VhayaLiveSyncService.stop(context)
            return@LaunchedEffect
        }
        when (uiState.activeRole) {
            RideRole.DRIVER -> {
                val isOnline = uiState.currentDriverApp?.isOnline == true
                val hasActive = uiState.driverActiveTrip != null
                if (hasActive) {
                    com.wayfare.ride.notifications.RideNotificationHelper.dismissDriverOfferNotification(context)
                }
                if (isOnline || hasActive) {
                    com.wayfare.ride.service.VhayaLiveSyncService.startForDriverOnline(context)
                } else {
                    com.wayfare.ride.notifications.RideNotificationHelper.dismissDriverOfferNotification(context)
                    com.wayfare.ride.service.VhayaLiveSyncService.stop(context)
                }
            }
            RideRole.RIDER -> {
                if (uiState.riderActiveTrip != null) {
                    com.wayfare.ride.service.VhayaLiveSyncService.startForRiderActiveTrip(context)
                }
            }
            RideRole.ADMIN -> {
                com.wayfare.ride.service.VhayaLiveSyncService.stop(context)
            }
        }
    }

    var lastRiderAlertStatusKey by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(
        uiState.activeRole,
        uiState.riderActiveTrip?.id,
        uiState.riderActiveTrip?.status,
        uiState.riderActiveTrip?.offers?.size
    ) {
        val activeRiderTrip = uiState.riderActiveTrip
        if (uiState.activeRole != RideRole.RIDER || activeRiderTrip == null) {
            lastRiderAlertStatusKey = null
            return@LaunchedEffect
        }
        val key = "${activeRiderTrip.id}:${activeRiderTrip.status}:${activeRiderTrip.offers.size}"
        val prev = lastRiderAlertStatusKey
        lastRiderAlertStatusKey = key
        if (prev != null && prev != key &&
            (activeRiderTrip.status == com.wayfare.ride.domain.RideStatus.COUNTERED ||
                activeRiderTrip.status == com.wayfare.ride.domain.RideStatus.ACCEPTED ||
                activeRiderTrip.status == com.wayfare.ride.domain.RideStatus.ARRIVED)
        ) {
            com.wayfare.ride.audio.SoundAlertPlayer.playStatusUpdateChime(context)
        }
    }

    if (uiState.authState !is AuthState.Authenticated) {
        Scaffold(
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            containerColor = MaterialTheme.colorScheme.background,
            snackbarHost = {
                SnackbarHost(
                    hostState = snackbarHostState,
                    modifier = Modifier.navigationBarsPadding()
                )
            }
        ) { innerPadding ->
            VhayaAuthScreen(
                uiState = uiState,
                viewModel = viewModel,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            )
        }
        return
    }

    val navItems: List<NavEntry> = when (uiState.activeRole) {
        RideRole.RIDER -> listOf(
            NavEntry("ride", RiderTab.RIDE.label, Icons.Filled.LocalTaxi, uiState.riderTab == RiderTab.RIDE) {
                viewModel.selectRiderTab(RiderTab.RIDE)
            },
            NavEntry("trips", RiderTab.TRIPS.label, Icons.Filled.History, uiState.riderTab == RiderTab.TRIPS) {
                viewModel.selectRiderTab(RiderTab.TRIPS)
            },
            NavEntry("support", RiderTab.SUPPORT.label, Icons.Filled.SupportAgent, uiState.riderTab == RiderTab.SUPPORT) {
                viewModel.selectRiderTab(RiderTab.SUPPORT)
            },
            NavEntry("wallet", RiderTab.WALLET.label, Icons.Filled.AccountBalanceWallet, uiState.riderTab == RiderTab.WALLET) {
                viewModel.selectRiderTab(RiderTab.WALLET)
            }
        )
        RideRole.DRIVER -> listOf(
            NavEntry("drive", DriverTab.DRIVE.label, Icons.Filled.DirectionsCar, uiState.driverTab == DriverTab.DRIVE) {
                viewModel.selectDriverTab(DriverTab.DRIVE)
            },
            NavEntry("earnings", DriverTab.EARNINGS.label, Icons.Filled.AttachMoney, uiState.driverTab == DriverTab.EARNINGS) {
                viewModel.selectDriverTab(DriverTab.EARNINGS)
            },
            NavEntry("profile", DriverTab.PROFILE.label, Icons.Filled.Person, uiState.driverTab == DriverTab.PROFILE) {
                viewModel.selectDriverTab(DriverTab.PROFILE)
            }
        )
        RideRole.ADMIN -> listOf(
            NavEntry("overview", AdminTab.OVERVIEW.label, Icons.Filled.Dashboard, uiState.adminTab == AdminTab.OVERVIEW) {
                viewModel.selectAdminTab(AdminTab.OVERVIEW)
            },
            NavEntry("drivers", AdminTab.DRIVERS.label, Icons.Filled.VerifiedUser, uiState.adminTab == AdminTab.DRIVERS) {
                viewModel.selectAdminTab(AdminTab.DRIVERS)
            },
            NavEntry("admin_support", AdminTab.SUPPORT.label, Icons.Filled.AdminPanelSettings, uiState.adminTab == AdminTab.SUPPORT) {
                viewModel.selectAdminTab(AdminTab.SUPPORT)
            },
            NavEntry("payouts", AdminTab.PAYOUTS.label, Icons.Filled.Payments, uiState.adminTab == AdminTab.PAYOUTS) {
                viewModel.selectAdminTab(AdminTab.PAYOUTS)
            }
        )
    }

    val isFullBleedMapTab = (uiState.activeRole == RideRole.RIDER && uiState.riderTab == RiderTab.RIDE) ||
        (uiState.activeRole == RideRole.DRIVER && uiState.driverTab == DriverTab.DRIVE)

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 700.dp

        Scaffold(
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            containerColor = MaterialTheme.colorScheme.background,
            snackbarHost = {
                SnackbarHost(
                    hostState = snackbarHostState,
                    modifier = Modifier.navigationBarsPadding()
                )
            },
            bottomBar = {
                if (!useRail) {
                    val wayfareColors = LocalWayfareColors.current
                    Column {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surface,
                            tonalElevation = 0.dp
                        ) {
                            navItems.forEach { entry ->
                                NavigationBarItem(
                                    selected = entry.selected,
                                    onClick = entry.onClick,
                                    icon = { Icon(entry.icon, contentDescription = entry.label) },
                                    label = { Text(entry.label) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = MaterialTheme.colorScheme.primary,
                                        selectedTextColor = MaterialTheme.colorScheme.primary,
                                        indicatorColor = wayfareColors.brandHighlight,
                                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                    ),
                                    modifier = Modifier.testTag("nav_${entry.id}")
                                )
                            }
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .consumeWindowInsets(innerPadding)
            ) {
                if (useRail) {
                    val wayfareColors = LocalWayfareColors.current
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxHeight()
                    ) {
                        navItems.forEach { entry ->
                            NavigationRailItem(
                                selected = entry.selected,
                                onClick = entry.onClick,
                                icon = { Icon(entry.icon, contentDescription = entry.label) },
                                label = { Text(entry.label) },
                                colors = NavigationRailItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.primary,
                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                    indicatorColor = wayfareColors.brandHighlight,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                modifier = Modifier.testTag("nav_${entry.id}")
                            )
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                    when (uiState.activeRole) {
                        RideRole.RIDER -> when (uiState.riderTab) {
                            RiderTab.RIDE -> RiderHomeScreen(
                                uiState = uiState,
                                viewModel = viewModel,
                                onOpenAccount = { viewModel.setAccountSheetVisible(true) },
                                onShowMessage = { msg ->
                                    coroutineScope.launch { snackbarHostState.showSnackbar(msg) }
                                }
                            )
                            RiderTab.TRIPS -> RiderTripsTab(
                                uiState = uiState,
                                viewModel = viewModel,
                                modifier = Modifier.widthIn(max = 640.dp).align(Alignment.TopCenter)
                            )
                            RiderTab.SUPPORT -> RiderSupportTab(
                                uiState = uiState,
                                viewModel = viewModel,
                                modifier = Modifier.widthIn(max = 640.dp).align(Alignment.TopCenter)
                            )
                            RiderTab.WALLET -> RiderWalletTab(
                                uiState = uiState,
                                viewModel = viewModel,
                                modifier = Modifier.widthIn(max = 640.dp).align(Alignment.TopCenter)
                            )
                        }
                        RideRole.DRIVER -> when (uiState.driverTab) {
                            DriverTab.DRIVE -> DriverHomeScreen(
                                uiState = uiState,
                                viewModel = viewModel,
                                onOpenAccount = { viewModel.setAccountSheetVisible(true) },
                                onOpenOnboarding = { viewModel.selectDriverTab(DriverTab.PROFILE) }
                            )
                            DriverTab.EARNINGS -> DriverEarningsTab(
                                uiState = uiState,
                                viewModel = viewModel,
                                modifier = Modifier.widthIn(max = 640.dp).align(Alignment.TopCenter)
                            )
                            DriverTab.PROFILE -> DriverProfileTab(
                                uiState = uiState,
                                viewModel = viewModel,
                                modifier = Modifier.widthIn(max = 640.dp).align(Alignment.TopCenter)
                            )
                        }
                        RideRole.ADMIN -> when (uiState.adminTab) {
                            AdminTab.OVERVIEW -> AdminOverviewTab(
                                uiState = uiState,
                                viewModel = viewModel,
                                modifier = Modifier.widthIn(max = 680.dp).align(Alignment.TopCenter)
                            )
                            AdminTab.DRIVERS -> AdminDriversTab(
                                uiState = uiState,
                                viewModel = viewModel,
                                modifier = Modifier.widthIn(max = 680.dp).align(Alignment.TopCenter)
                            )
                            AdminTab.SUPPORT -> AdminSupportTab(
                                uiState = uiState,
                                viewModel = viewModel,
                                modifier = Modifier.widthIn(max = 680.dp).align(Alignment.TopCenter)
                            )
                            AdminTab.PAYOUTS -> AdminPayoutsTab(
                                uiState = uiState,
                                viewModel = viewModel,
                                modifier = Modifier.widthIn(max = 680.dp).align(Alignment.TopCenter)
                            )
                        }
                    }

                    if (!isFullBleedMapTab) {
                        Column(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .statusBarsPadding()
                                .padding(horizontal = 14.dp, vertical = 10.dp)
                                .widthIn(max = 640.dp)
                                .fillMaxWidth()
                        ) {
                            VhayaTopBar(
                                session = uiState.currentSession,
                                isLiveApi = uiState.apiBaseUrl.isNotBlank(),
                                onOpenAccount = { viewModel.setAccountSheetVisible(true) },
                                modifier = Modifier.fillMaxWidth()
                            )
                            if (uiState.syncError != null || uiState.pendingOfflineCount > 0) {
                                OfflineReconnectingBanner(
                                    pendingOfflineCount = uiState.pendingOfflineCount,
                                    syncError = uiState.syncError,
                                    onSyncNow = { viewModel.flushOfflineQueueNow() },
                                    modifier = Modifier.padding(top = 8.dp)
                                )
                            }
                        }
                    } else if (uiState.syncError != null || uiState.pendingOfflineCount > 0) {
                        OfflineReconnectingBanner(
                            pendingOfflineCount = uiState.pendingOfflineCount,
                            syncError = uiState.syncError,
                            onSyncNow = { viewModel.flushOfflineQueueNow() },
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .statusBarsPadding()
                                .padding(horizontal = 14.dp, vertical = 68.dp)
                                .widthIn(max = 580.dp)
                        )
                    }
                }
            }
        }

        if (uiState.showAccountSheet) {
            AccountAndPlacesSheet(
                uiState = uiState,
                viewModel = viewModel,
                onDismiss = { viewModel.setAccountSheetVisible(false) }
            )
        }

        if (uiState.showPrivacySheet) {
            PrivacySettingsSheet(
                uiState = uiState,
                viewModel = viewModel,
                onDismiss = { viewModel.setPrivacySheetVisible(false) }
            )
        }

        uiState.activePaymentVerification?.let { verification ->
            PaymentReturnScreen(
                result = verification,
                onDone = { viewModel.dismissPaymentVerification() }
            )
        }

        uiState.pendingMockPayment?.let { outcome ->
            AlertDialog(
                onDismissRequest = { viewModel.dismissMockPaymentDialog() },
                title = { Text("Confirm sandbox payment") },
                text = {
                    Text(
                        "Paystack is running in sandbox mode (${outcome.reference}). " +
                            "Authorize ${LaunchPolicy.formatZarMinor(outcome.amountMinor)} or cancel?"
                    )
                },
                confirmButton = {
                    Button(
                        onClick = { viewModel.submitMockPaymentDecision(true) },
                        modifier = Modifier.testTag("mock_payment_authorize_btn")
                    ) {
                        Text("Authorize ${LaunchPolicy.formatZarMinor(outcome.amountMinor)}")
                    }
                },
                dismissButton = {
                    OutlinedButton(
                        onClick = { viewModel.submitMockPaymentDecision(false) },
                        modifier = Modifier.testTag("mock_payment_cancel_btn")
                    ) {
                        Text("Decline")
                    }
                }
            )
        }
    }
}

private data class NavEntry(
    val id: String,
    val label: String,
    val icon: ImageVector,
    val selected: Boolean,
    val onClick: () -> Unit
)
