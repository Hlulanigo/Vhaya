package com.wayfare.ride.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.wayfare.ride.BuildConfig
import com.wayfare.ride.data.WayfareRepository
import com.wayfare.ride.data.friendlyApiErrorMessage
import com.wayfare.ride.domain.ActivityLogEntry
import com.wayfare.ride.domain.AdminOverviewSummary
import com.wayfare.ride.domain.AuthState
import com.wayfare.ride.domain.ComplianceStatus
import com.wayfare.ride.domain.DriverApplication
import com.wayfare.ride.domain.DriverDocumentType
import com.wayfare.ride.domain.DriverEarningsSummary
import com.wayfare.ride.domain.DriverPayoutRecord
import com.wayfare.ride.domain.DriverSubscriptionSummary
import com.wayfare.ride.domain.LaunchPolicy
import com.wayfare.ride.domain.PaymentInitOutcome
import com.wayfare.ride.domain.PaymentMethod
import com.wayfare.ride.domain.PaymentStatus
import com.wayfare.ride.domain.PaymentVerificationResult
import com.wayfare.ride.domain.PaymentVerificationState
import com.wayfare.ride.domain.PayoutBatchRecord
import com.wayfare.ride.domain.PlaceSearchSuggestion
import com.wayfare.ride.domain.PrivacyPreferences
import com.wayfare.ride.domain.RideCategory
import com.wayfare.ride.domain.RideCoordinates
import com.wayfare.ride.domain.RideRole
import com.wayfare.ride.domain.RideRoute
import com.wayfare.ride.domain.RideStatus
import com.wayfare.ride.domain.RideTrip
import com.wayfare.ride.domain.SavedDestination
import com.wayfare.ride.domain.SupportCase
import com.wayfare.ride.domain.SupportUpdateAlert
import com.wayfare.ride.domain.TrustedContact
import com.wayfare.ride.domain.UserAccountSummary
import com.wayfare.ride.domain.UserSession
import com.wayfare.ride.domain.WalletTransaction
import com.wayfare.ride.notifications.SupportNotificationHelper
import com.wayfare.ride.ui.theme.AppThemeMode
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class RiderTab(val label: String) {
    RIDE("Ride"),
    TRIPS("Trips"),
    SUPPORT("Support"),
    WALLET("Wallet")
}

enum class DriverTab(val label: String) {
    DRIVE("Drive"),
    EARNINGS("Earnings"),
    PROFILE("Profile")
}

enum class AdminTab(val label: String) {
    OVERVIEW("Overview"),
    DRIVERS("Drivers"),
    SUPPORT("Support"),
    PAYOUTS("Payouts")
}

enum class RiderBookingStep {
    HOME_COMPACT,
    SEARCH_DESTINATION,
    MAP_PIN_PICKER,
    CHOOSE_RIDE_FARE
}

data class BookingDraft(
    val step: RiderBookingStep = RiderBookingStep.HOME_COMPACT,
    val pickingForPickup: Boolean = false,
    val pickupLabel: String = "Current pickup location",
    val pickupCoords: RideCoordinates = LaunchPolicy.DEFAULT_MAP_CENTER,
    val destinationLabel: String = "",
    val destinationCoords: RideCoordinates? = null,
    val category: RideCategory = RideCategory.STANDARD,
    val previewRoute: RideRoute? = null,
    val visualRoute: RideRoute? = null,
    val routeLoading: Boolean = false,
    val routeError: String? = null,
    val customOfferText: String = "",
    val isOfferExpanded: Boolean = false,
    val scheduledOffsetMinutes: Int? = null,
    val gpsUsingDeviceLocation: Boolean = false,
    val paymentMethod: PaymentMethod = PaymentMethod.CARD,
    val pinPreviewAddress: String? = null,
    val pinPreviewLoading: Boolean = false,
    val pinConfirming: Boolean = false
) {
    val estimatedFare: Double
        get() = previewRoute?.fareEstimate
            ?: previewRoute?.let { LaunchPolicy.calculateEstimate(it.distanceMeters, category) }
            ?: 20.0

    val effectiveFare: Double
        get() = customOfferText.toDoubleOrNull()?.takeIf { it >= 20.0 } ?: estimatedFare

    val isRouteValidForRequest: Boolean
        get() = previewRoute != null &&
            !previewRoute.quoteToken.isNullOrBlank() &&
            previewRoute.durationSeconds > LaunchPolicy.MIN_RIDE_DURATION_SECONDS &&
            !routeLoading
}

data class WayfareUiState(
    val authState: AuthState = AuthState.Unauthenticated,
    val apiBaseUrl: String = "",
    val themeMode: AppThemeMode = AppThemeMode.LIGHT,
    val privacyPreferences: PrivacyPreferences = PrivacyPreferences(),
    val pendingOfflineCount: Int = 0,
    val authBusy: Boolean = false,
    val authError: String? = null,
    val authNotice: String? = null,
    val isInitialSyncing: Boolean = false,
    val isRefreshing: Boolean = false,
    val syncError: String? = null,
    val placeSearchError: String? = null,
    val actionError: String? = null,
    val busyActions: Set<String> = emptySet(),
    val nowEpochMs: Long = System.currentTimeMillis(),
    val locallyExpiredTripIds: Set<String> = emptySet(),
    val expiredTripNotice: RideTrip? = null,
    val showAccountSheet: Boolean = false,
    val showPrivacySheet: Boolean = false,
    val activePaymentVerification: PaymentVerificationResult? = null,
    val pendingMockPayment: PaymentInitOutcome? = null,
    val pendingRatingTripId: String? = null,
    val dismissedOfferIds: Set<String> = emptySet(),
    val declinedDriverTripIds: Set<String> = emptySet(),
    val riderTab: RiderTab = RiderTab.RIDE,
    val driverTab: DriverTab = DriverTab.DRIVE,
    val adminTab: AdminTab = AdminTab.OVERVIEW,
    val booking: BookingDraft = BookingDraft(),
    val placeSuggestions: List<PlaceSearchSuggestion> = emptyList(),
    val searchingPlaces: Boolean = false,
    val driverSoundEnabled: Boolean = true,
    val driverHeatmapEnabled: Boolean = true,
    val driverRequestIndex: Int = 0,
    val driverCurrentCoords: RideCoordinates = LaunchPolicy.DEFAULT_MAP_CENTER,
    val driverDriveModeMinimized: Boolean = false,
    val pendingDriverSummaryTripId: String? = null,
    val uploadingDocType: DriverDocumentType? = null,
    val driverOnboardingBusy: Boolean = false,
    val driverOnboardingError: String? = null,
    val driverOnboardingNotice: String? = null,
    val walletBalanceMinor: Long = 0L,
    val driverEarnings: DriverEarningsSummary = DriverEarningsSummary(),
    val driverSubscription: DriverSubscriptionSummary? = null,
    val adminOverview: AdminOverviewSummary = AdminOverviewSummary(),
    val trips: List<RideTrip> = emptyList(),
    val savedDestinations: List<SavedDestination> = emptyList(),
    val trustedContacts: List<TrustedContact> = emptyList(),
    val walletTransactions: List<WalletTransaction> = emptyList(),
    val driverApplications: List<DriverApplication> = emptyList(),
    val supportCases: List<SupportCase> = emptyList(),
    val payoutRecords: List<DriverPayoutRecord> = emptyList(),
    val userAccounts: List<UserAccountSummary> = emptyList(),
    val payoutBatches: List<PayoutBatchRecord> = emptyList(),
    val activityLogs: List<ActivityLogEntry> = emptyList()
) {
    fun isBusy(key: String): Boolean = key in busyActions

    val isAnyActionBusy: Boolean
        get() = busyActions.isNotEmpty()

    val currentSession: UserSession?
        get() = when (authState) {
            is AuthState.Authenticated -> authState.session
            is AuthState.EmailVerificationPending -> authState.session
            AuthState.Unauthenticated -> null
        }

    val activeRole: RideRole
        get() = currentSession?.role ?: RideRole.RIDER

    val activeUserId: String
        get() = currentSession?.userId.orEmpty()

    val currentDriverApp: DriverApplication?
        get() = driverApplications.firstOrNull { it.userId == activeUserId } ?: driverApplications.firstOrNull()

    val driverWeeklyPlanActive: Boolean
        get() = driverSubscription?.let {
            it.status.equals("active", ignoreCase = true) &&
                (it.expiresAt == null || it.expiresAt > nowEpochMs)
        } ?: false

    val riderActiveTrip: RideTrip?
        get() = trips.firstOrNull { trip ->
            trip.riderId == activeUserId &&
                trip.status.isActive &&
                trip.id !in locallyExpiredTripIds &&
                !LaunchPolicy.isTripExpired(trip, nowEpochMs)
        }

    val riderHistoryTrips: List<RideTrip>
        get() = trips.filter { it.riderId == activeUserId && !it.status.isActive }

    val driverActiveTrip: RideTrip?
        get() = trips.firstOrNull { it.driverId == activeUserId && it.status.isActive }

    val driverOpenRequests: List<RideTrip>
        get() = trips
            .filter { trip ->
                trip.status.isOpenForDrivers &&
                    trip.driverId == null &&
                    trip.id !in locallyExpiredTripIds &&
                    trip.id !in declinedDriverTripIds &&
                    !LaunchPolicy.isTripExpired(trip, nowEpochMs)
            }
            .sortedByDescending { LaunchPolicy.matchScoreForDriver(it, driverCurrentCoords) }

    val driverCompletedTrips: List<RideTrip>
        get() = trips.filter { it.driverId == activeUserId && it.status == RideStatus.COMPLETED }

    val pendingRatingTrip: RideTrip?
        get() = pendingRatingTripId?.let { id -> trips.firstOrNull { it.id == id } }

    val pendingDriverSummaryTrip: RideTrip?
        get() = pendingDriverSummaryTripId?.let { id -> trips.firstOrNull { it.id == id } }
}

private data class LocalNavState(
    val riderTab: RiderTab = RiderTab.RIDE,
    val driverTab: DriverTab = DriverTab.DRIVE,
    val adminTab: AdminTab = AdminTab.OVERVIEW,
    val booking: BookingDraft = BookingDraft(),
    val driverSoundEnabled: Boolean = true,
    val driverHeatmapEnabled: Boolean = true,
    val driverRequestIndex: Int = 0,
    val driverCurrentCoords: RideCoordinates = LaunchPolicy.DEFAULT_MAP_CENTER,
    val driverDriveModeMinimized: Boolean = false,
    val pendingDriverSummaryTripId: String? = null,
    val uploadingDocType: DriverDocumentType? = null,
    val driverOnboardingBusy: Boolean = false,
    val driverOnboardingError: String? = null,
    val driverOnboardingNotice: String? = null,
    val showAccountSheet: Boolean = false,
    val showPrivacySheet: Boolean = false,
    val activePaymentVerification: PaymentVerificationResult? = null,
    val pendingMockPayment: PaymentInitOutcome? = null,
    val pendingRatingTripId: String? = null,
    val dismissedOfferIds: Set<String> = emptySet(),
    val authBusy: Boolean = false,
    val authError: String? = null,
    val authNotice: String? = null,
    val isInitialSyncing: Boolean = false,
    val isRefreshing: Boolean = false,
    val syncError: String? = null,
    val placeSearchError: String? = null,
    val actionError: String? = null,
    val busyActions: Set<String> = emptySet(),
    val nowEpochMs: Long = System.currentTimeMillis(),
    val locallyExpiredTripIds: Set<String> = emptySet(),
    val expiredTripNotice: RideTrip? = null
)

class WayfareViewModel(private val repository: WayfareRepository) : ViewModel() {

    private val localNav = MutableStateFlow(LocalNavState())
    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val events = _events.asSharedFlow()

    private val _openUrlEvents = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val openUrlEvents = _openUrlEvents.asSharedFlow()

    private val _supportAlerts = MutableSharedFlow<SupportUpdateAlert>(extraBufferCapacity = 8)
    val supportAlerts = _supportAlerts.asSharedFlow()

    private var observedActiveRiderTripId: String? = null
    private var pinPreviewJob: Job? = null
    private var placeSearchJob: Job? = null
    private var placeSearchRequestId = 0L
    private var hasCompletedInitialSyncForUserId: String? = null
    private var supportHydratedForUserId: String? = null
    private var lastObservedSupportCases: List<SupportCase> = emptyList()
    private val expiringTripIds = mutableSetOf<String>()
    private val autoPaymentTriggeredTripIds = mutableSetOf<String>()

    private val coreDataFlow = combine(
        repository.trips,
        repository.savedDestinations,
        repository.trustedContacts,
        repository.walletTransactions,
        repository.driverApplications
    ) { trips, saved, contacts, wallet, drivers ->
        CoreSlice(trips, saved, contacts, wallet, drivers)
    }

    private val opsDataFlow = combine(
        repository.supportCases,
        repository.payoutRecords,
        repository.userAccounts,
        repository.payoutBatches,
        repository.activityLogs
    ) { support, payouts, users, batches, logs ->
        OpsSlice(support, payouts, users, batches, logs)
    }

    private val liveSummaryFlow = combine(
        repository.walletBalanceMinor,
        repository.driverEarnings,
        repository.driverSubscription,
        repository.adminOverview,
        repository.placeSuggestions,
        repository.searchingPlaces
    ) { arr ->
        @Suppress("UNCHECKED_CAST")
        LiveSummarySlice(
            walletBalanceMinor = arr[0] as Long,
            driverEarnings = arr[1] as DriverEarningsSummary,
            driverSubscription = arr[2] as DriverSubscriptionSummary?,
            adminOverview = arr[3] as AdminOverviewSummary,
            placeSuggestions = arr[4] as List<PlaceSearchSuggestion>,
            searchingPlaces = arr[5] as Boolean
        )
    }

    private val sessionFlow = combine(
        repository.authState,
        repository.apiBaseUrl,
        repository.themeMode,
        repository.privacyPreferences,
        repository.pendingOfflineCount,
        repository.declinedDriverTripIds
    ) { arr ->
        @Suppress("UNCHECKED_CAST")
        SessionSlice(
            authState = arr[0] as AuthState,
            apiBaseUrl = arr[1] as String,
            themeMode = arr[2] as AppThemeMode,
            privacyPreferences = arr[3] as PrivacyPreferences,
            pendingOfflineCount = arr[4] as Int,
            declinedDriverTripIds = arr[5] as Set<String>
        )
    }

    val uiState = combine(
        localNav,
        sessionFlow,
        coreDataFlow,
        opsDataFlow,
        liveSummaryFlow
    ) { nav, session, core, ops, summary ->
        WayfareUiState(
            authState = session.authState,
            apiBaseUrl = session.apiBaseUrl,
            themeMode = session.themeMode,
            privacyPreferences = session.privacyPreferences,
            pendingOfflineCount = session.pendingOfflineCount,
            authBusy = nav.authBusy,
            authError = nav.authError,
            authNotice = nav.authNotice,
            isInitialSyncing = nav.isInitialSyncing,
            isRefreshing = nav.isRefreshing,
            syncError = nav.syncError,
            placeSearchError = nav.placeSearchError,
            actionError = nav.actionError,
            busyActions = nav.busyActions,
            nowEpochMs = nav.nowEpochMs,
            locallyExpiredTripIds = nav.locallyExpiredTripIds,
            expiredTripNotice = nav.expiredTripNotice,
            showAccountSheet = nav.showAccountSheet,
            showPrivacySheet = nav.showPrivacySheet,
            activePaymentVerification = nav.activePaymentVerification,
            pendingMockPayment = nav.pendingMockPayment,
            pendingRatingTripId = nav.pendingRatingTripId,
            dismissedOfferIds = nav.dismissedOfferIds,
            declinedDriverTripIds = session.declinedDriverTripIds,
            riderTab = nav.riderTab,
            driverTab = nav.driverTab,
            adminTab = nav.adminTab,
            booking = nav.booking,
            placeSuggestions = summary.placeSuggestions,
            searchingPlaces = summary.searchingPlaces,
            driverSoundEnabled = nav.driverSoundEnabled,
            driverHeatmapEnabled = nav.driverHeatmapEnabled,
            driverRequestIndex = nav.driverRequestIndex,
            driverCurrentCoords = nav.driverCurrentCoords,
            driverDriveModeMinimized = nav.driverDriveModeMinimized,
            pendingDriverSummaryTripId = nav.pendingDriverSummaryTripId,
            uploadingDocType = nav.uploadingDocType,
            driverOnboardingBusy = nav.driverOnboardingBusy,
            driverOnboardingError = nav.driverOnboardingError,
            driverOnboardingNotice = nav.driverOnboardingNotice,
            walletBalanceMinor = summary.walletBalanceMinor,
            driverEarnings = summary.driverEarnings,
            driverSubscription = summary.driverSubscription,
            adminOverview = summary.adminOverview,
            trips = core.trips,
            savedDestinations = core.saved,
            trustedContacts = core.contacts,
            walletTransactions = core.wallet,
            driverApplications = core.drivers,
            supportCases = ops.support,
            payoutRecords = ops.payouts,
            userAccounts = ops.users,
            payoutBatches = ops.batches,
            activityLogs = ops.logs
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        WayfareUiState(
            authState = repository.authState.value,
            apiBaseUrl = repository.apiBaseUrl.value,
            themeMode = repository.themeMode.value,
            privacyPreferences = repository.privacyPreferences.value,
            pendingOfflineCount = repository.pendingOfflineCount.value,
            declinedDriverTripIds = repository.declinedDriverTripIds.value
        )
    )

    init {
        viewModelScope.launch {
            repository.authState.collect { auth ->
                if (auth is AuthState.Unauthenticated) {
                    hasCompletedInitialSyncForUserId = null
                    supportHydratedForUserId = null
                    lastObservedSupportCases = emptyList()
                    expiringTripIds.clear()
                    autoPaymentTriggeredTripIds.clear()
                }
            }
        }

        viewModelScope.launch {
            repository.isAppInForeground.collect { inForeground ->
                if (inForeground) {
                    val active = uiState.value.riderActiveTrip
                    if (active != null) {
                        maybeTriggerCardPaymentCheckout(active)
                    }
                }
            }
        }

        viewModelScope.launch {
            repository.supportCases.collect { cases ->
                val userId = uiState.value.activeUserId
                if (userId.isBlank()) {
                    lastObservedSupportCases = emptyList()
                    supportHydratedForUserId = null
                    return@collect
                }
                if (supportHydratedForUserId != userId) {
                    lastObservedSupportCases = cases
                    supportHydratedForUserId = userId
                    return@collect
                }
                val alerts = SupportNotificationHelper.diffSupportUpdates(lastObservedSupportCases, cases)
                lastObservedSupportCases = cases
                if (repository.privacyPreferences.value.supportNotificationsEnabled) {
                    alerts.forEach { _supportAlerts.tryEmit(it) }
                }
            }
        }

        viewModelScope.launch {
            repository.trips.collect { allTrips ->
                val userId = uiState.value.activeUserId
                val active = allTrips.firstOrNull { it.riderId == userId && it.status.isActive }
                val prevId = observedActiveRiderTripId
                if (active != null) {
                    observedActiveRiderTripId = active.id
                    maybeTriggerCardPaymentCheckout(active)
                } else if (prevId != null) {
                    val finished = allTrips.firstOrNull { it.id == prevId }
                    if (finished?.status == RideStatus.COMPLETED && finished.riderRating == null) {
                        localNav.update { it.copy(pendingRatingTripId = finished.id) }
                    }
                    observedActiveRiderTripId = null
                }
                // Clear locally-expired markers once backend reflects terminal or assigned state
                val stillOpenIds = allTrips.filter { it.status.isOpenForDrivers && it.driverId == null }.map { it.id }.toSet()
                localNav.update { nav ->
                    val pruned = nav.locallyExpiredTripIds.intersect(stillOpenIds)
                    if (pruned == nav.locallyExpiredTripIds) nav else nav.copy(locallyExpiredTripIds = pruned)
                }
            }
        }

        // Initial hydration & periodic background polling while authenticated
        viewModelScope.launch {
            while (isActive) {
                val auth = repository.authState.value
                if (auth is AuthState.Authenticated) {
                    val userId = auth.session.userId
                    val isFirstForSession = hasCompletedInitialSyncForUserId != userId
                    if (isFirstForSession) {
                        localNav.update { it.copy(isInitialSyncing = true) }
                    }
                    val res = repository.refreshActiveSessionData()
                    hasCompletedInitialSyncForUserId = userId
                    res.onSuccess {
                        localNav.update {
                            it.copy(isInitialSyncing = false, syncError = null)
                        }
                    }.onFailure { err ->
                        val msg = friendlyApiErrorMessage(err, "Unable to sync with Vhaya. Retrying...")
                        localNav.update {
                            it.copy(isInitialSyncing = false, syncError = msg)
                        }
                    }
                }
                delay(5_000L)
            }
        }

        // Live 1-second ticker for open-request countdowns and auto-expiration
        viewModelScope.launch {
            while (isActive) {
                if (repository.authState.value is AuthState.Authenticated) {
                    val now = System.currentTimeMillis()
                    localNav.update { it.copy(nowEpochMs = now) }
                    checkAndExpireStaleTrips(now)
                }
                delay(1_000L)
            }
        }
    }

    private fun checkAndExpireStaleTrips(nowMs: Long) {
        val state = uiState.value
        val userId = state.activeUserId
        if (userId.isBlank()) return
        state.trips.forEach { trip ->
            if (trip.status.isOpenForDrivers && trip.driverId == null && LaunchPolicy.isTripExpired(trip, nowMs)) {
                if (trip.id !in localNav.value.locallyExpiredTripIds) {
                    localNav.update { it.copy(locallyExpiredTripIds = it.locallyExpiredTripIds + trip.id) }
                }
                if (trip.riderId == userId && trip.id !in expiringTripIds) {
                    expireTripInternal(trip)
                }
            }
        }
    }

    private fun expireTripInternal(trip: RideTrip) {
        expiringTripIds += trip.id
        localNav.update {
            it.copy(
                locallyExpiredTripIds = it.locallyExpiredTripIds + trip.id,
                expiredTripNotice = trip
            )
        }
        toast("Ride request expired — no driver accepted in time")
        viewModelScope.launch {
            repository.expireOpenTrip(trip.id)
                .onFailure {
                    // Refresh session so state stays aligned if another actor updated the trip simultaneously
                    repository.refreshActiveSessionData()
                }
            expiringTripIds -= trip.id
        }
    }

    private fun toast(message: String) {
        _events.tryEmit(message)
    }

    fun setThemeMode(mode: AppThemeMode) {
        repository.setThemeMode(mode)
    }

    // Deep links (vhaya://payment-return?reference=... & vhaya://ride/...)
    fun handleDeepLink(uri: Uri) {
        val scheme = uri.scheme?.lowercase() ?: return
        if (scheme != "vhaya") return
        val host = uri.host?.lowercase().orEmpty()
        when (host) {
            "payment-return" -> {
                val ref = uri.getQueryParameter("reference")
                    ?: uri.getQueryParameter("trxref")
                    ?: return
                verifyPaymentReference(ref)
            }
            "ride" -> {
                val action = uri.getQueryParameter("action")
                val tripId = uri.getQueryParameter("tripId")
                val role = uiState.value.activeRole
                when (role) {
                    RideRole.RIDER -> localNav.update { it.copy(riderTab = RiderTab.RIDE) }
                    RideRole.DRIVER -> localNav.update { it.copy(driverTab = DriverTab.DRIVE) }
                    RideRole.ADMIN -> localNav.update { it.copy(adminTab = AdminTab.OVERVIEW) }
                }
                if (action.equals("accept", ignoreCase = true) && !tripId.isNullOrBlank()) {
                    localNav.update { it.copy(driverTab = DriverTab.DRIVE) }
                    acceptRideRequestFromNotification(tripId)
                } else {
                    viewModelScope.launch { repository.refreshActiveSessionData() }
                }
            }
            "support" -> {
                when (uiState.value.activeRole) {
                    RideRole.RIDER -> localNav.update { it.copy(riderTab = RiderTab.SUPPORT) }
                    RideRole.ADMIN -> localNav.update { it.copy(adminTab = AdminTab.SUPPORT) }
                    RideRole.DRIVER -> localNav.update { it.copy(driverTab = DriverTab.PROFILE) }
                }
                viewModelScope.launch { repository.refreshActiveSessionData() }
            }
        }
    }

    fun acceptRideRequestFromNotification(tripId: String) {
        if (!beginBusy("driver_offer")) return
        viewModelScope.launch {
            try {
                var target = repository.getCachedTripsSnapshot().firstOrNull { it.id == tripId }
                if (target == null) {
                    repository.refreshActiveSessionData()
                    target = repository.getCachedTripsSnapshot().firstOrNull { it.id == tripId }
                }
                val nowMs = System.currentTimeMillis()
                if (target == null || !target.status.isOpenForDrivers || target.driverId != null || LaunchPolicy.isTripExpired(target, nowMs)) {
                    repository.refreshActiveSessionData()
                    toast("This ride request is no longer available")
                    return@launch
                }
                repository.driverSubmitOffer(target, target.fare, localNav.value.driverCurrentCoords)
                    .onSuccess { updated ->
                        val msg = if (updated.driverId != null) {
                            "Ride accepted · head to pickup"
                        } else {
                            "Offer sent at ${LaunchPolicy.formatZar(target.fare)} · awaiting rider"
                        }
                        toast(msg)
                    }
                    .onFailure { reportActionFailure(it, "Could not accept ride request") }
            } finally {
                endBusy("driver_offer")
            }
        }
    }

    // Authentication actions
    fun signIn(email: String, password: String) {
        localNav.update { it.copy(authBusy = true, authError = null, authNotice = null) }
        viewModelScope.launch {
            repository.signInWithEmailPassword(email, password)
                .onSuccess { session ->
                    localNav.update {
                        it.copy(
                            authBusy = false,
                            authError = null,
                            riderTab = RiderTab.RIDE,
                            driverTab = DriverTab.DRIVE,
                            adminTab = AdminTab.OVERVIEW
                        )
                    }
                    toast("Signed in as ${session.displayName} (${session.role.label})")
                }
                .onFailure { err ->
                    localNav.update { it.copy(authBusy = false, authError = err.message ?: "Sign-in failed") }
                }
        }
    }

    fun signUp(displayName: String, email: String, password: String) {
        localNav.update { it.copy(authBusy = true, authError = null, authNotice = null) }
        viewModelScope.launch {
            repository.signUpWithEmailPassword(displayName, email, password)
                .onSuccess {
                    localNav.update {
                        it.copy(
                            authBusy = false,
                            authNotice = "Account created. Check your inbox to verify your email."
                        )
                    }
                }
                .onFailure { err ->
                    localNav.update { it.copy(authBusy = false, authError = err.message ?: "Could not create account") }
                }
        }
    }

    fun checkEmailVerified() {
        localNav.update { it.copy(authBusy = true, authError = null, authNotice = null) }
        viewModelScope.launch {
            repository.checkEmailVerified()
                .onSuccess { verified ->
                    localNav.update {
                        it.copy(
                            authBusy = false,
                            authNotice = if (verified) "Email verified!" else "Still awaiting verification. Open the link in your inbox."
                        )
                    }
                }
                .onFailure { err ->
                    localNav.update { it.copy(authBusy = false, authError = err.message) }
                }
        }
    }

    fun resendVerificationEmail() {
        localNav.update { it.copy(authBusy = true, authError = null, authNotice = null) }
        viewModelScope.launch {
            repository.resendVerificationEmail()
                .onSuccess { localNav.update { it.copy(authBusy = false, authNotice = "Verification email sent.") } }
                .onFailure { err -> localNav.update { it.copy(authBusy = false, authError = err.message) } }
        }
    }

    fun sendPasswordReset(email: String) {
        if (email.isBlank()) {
            localNav.update { it.copy(authError = "Enter your email address first.") }
            return
        }
        localNav.update { it.copy(authBusy = true, authError = null, authNotice = null) }
        viewModelScope.launch {
            repository.sendPasswordReset(email)
                .onSuccess {
                    localNav.update {
                        it.copy(authBusy = false, authNotice = "Password reset instructions sent to $email.")
                    }
                }
                .onFailure { err ->
                    localNav.update { it.copy(authBusy = false, authError = err.message) }
                }
        }
    }

    private fun beginBusy(key: String): Boolean {
        if (key in localNav.value.busyActions) return false
        localNav.update { it.copy(busyActions = it.busyActions + key, actionError = null) }
        return true
    }

    private fun endBusy(key: String) {
        localNav.update { it.copy(busyActions = it.busyActions - key) }
    }

    private fun reportActionFailure(error: Throwable?, fallback: String) {
        val msg = friendlyApiErrorMessage(error, fallback)
        localNav.update { it.copy(actionError = msg) }
        toast(msg)
    }

    fun dismissSyncError() {
        localNav.update { it.copy(syncError = null, actionError = null) }
    }

    fun dismissExpiredTripNotice() {
        localNav.update { it.copy(expiredTripNotice = null) }
    }

    fun rebookExpiredTrip(trip: RideTrip) {
        val pickupCoords = trip.pickupCoordinates ?: localNav.value.booking.pickupCoords
        val destCoords = trip.destinationCoordinates
        localNav.update { state ->
            state.copy(
                expiredTripNotice = null,
                riderTab = RiderTab.RIDE,
                booking = state.booking.copy(
                    step = if (destCoords != null) RiderBookingStep.CHOOSE_RIDE_FARE else RiderBookingStep.SEARCH_DESTINATION,
                    pickupLabel = trip.pickup,
                    pickupCoords = pickupCoords,
                    destinationLabel = trip.destination,
                    destinationCoords = destCoords,
                    category = trip.category,
                    paymentMethod = trip.paymentMethod,
                    previewRoute = null,
                    visualRoute = null,
                    routeLoading = destCoords != null,
                    routeError = null,
                    customOfferText = "",
                    isOfferExpanded = false
                )
            )
        }
        if (destCoords != null) {
            fetchLiveRoutePreview(trip.pickup, trip.destination, pickupCoords, destCoords)
        }
    }

    fun signOut() {
        if (!beginBusy("sign_out")) return
        viewModelScope.launch {
            try {
                repository.signOut()
                observedActiveRiderTripId = null
                hasCompletedInitialSyncForUserId = null
                expiringTripIds.clear()
                localNav.update { LocalNavState() }
            } finally {
                endBusy("sign_out")
            }
        }
    }

    fun clearAuthMessages() {
        localNav.update { it.copy(authError = null, authNotice = null) }
    }

    fun setAccountSheetVisible(visible: Boolean) {
        localNav.update { it.copy(showAccountSheet = visible) }
    }

    fun setPrivacySheetVisible(visible: Boolean) {
        localNav.update { it.copy(showPrivacySheet = visible) }
    }

    fun updatePrivacyPreferences(prefs: PrivacyPreferences) {
        repository.updatePrivacyPreferences(prefs)
    }

    fun setLocationSharingConsent(enabled: Boolean) {
        val current = repository.privacyPreferences.value
        repository.updatePrivacyPreferences(current.copy(locationSharingConsent = enabled))
        toast(if (enabled) "Live location sharing enabled" else "Live location sharing paused")
    }

    fun setTripOnlyPrivacyMode(enabled: Boolean) {
        val current = repository.privacyPreferences.value
        repository.updatePrivacyPreferences(current.copy(tripOnlyPrivacyMode = enabled))
        toast(
            if (enabled) "Trip-only privacy enabled · GPS shares only while on trip"
            else "Standard pickup & trip GPS sharing enabled"
        )
    }

    fun setSupportNotificationsEnabled(enabled: Boolean) {
        val current = repository.privacyPreferences.value
        repository.updatePrivacyPreferences(current.copy(supportNotificationsEnabled = enabled))
    }

    fun hasRequestedLocationPermission(): Boolean = repository.hasRequestedLocationPermission()
    fun markLocationPermissionRequested() = repository.markLocationPermissionRequested()

    fun exportPopiaDataJson(onReady: (String) -> Unit) {
        if (!beginBusy("popia_export")) return
        viewModelScope.launch {
            try {
                val json = repository.buildPopiaExportJson()
                onReady(json)
            } catch (t: Throwable) {
                reportActionFailure(t, "Could not prepare personal data export")
            } finally {
                endBusy("popia_export")
            }
        }
    }

    fun requestPopiaDataDeletion(reason: String) {
        if (!beginBusy("popia_delete")) return
        viewModelScope.launch {
            try {
                repository.requestPopiaDataDeletion(reason)
                    .onSuccess { submittedTicket ->
                        if (submittedTicket) {
                            toast("POPIA deletion request logged with Vhaya Support")
                        } else {
                            toast("Local device cache cleared — email support@vhaya.co.za if no trips exist for ticket creation")
                        }
                    }
                    .onFailure { err ->
                        reportActionFailure(err, "Could not log deletion request")
                    }
            } finally {
                endBusy("popia_delete")
            }
        }
    }

    fun clearAllLocalUserDataAndSignOut() {
        if (!beginBusy("popia_wipe")) return
        viewModelScope.launch {
            try {
                repository.clearAllLocalUserDataAndSignOut()
                observedActiveRiderTripId = null
                hasCompletedInitialSyncForUserId = null
                supportHydratedForUserId = null
                lastObservedSupportCases = emptyList()
                expiringTripIds.clear()
                localNav.update { LocalNavState() }
                toast("Local device cache cleared and signed out")
            } finally {
                endBusy("popia_wipe")
            }
        }
    }

    fun flushOfflineQueueNow() {
        viewModelScope.launch {
            val flushed = repository.flushOfflineQueue()
            if (flushed > 0) {
                toast("Sent $flushed queued update${if (flushed == 1) "" else "s"}")
            }
            refreshNow(showToastOnSuccess = flushed == 0)
        }
    }

    fun retrySync() {
        refreshNow(showToastOnSuccess = true)
    }

    fun refreshNow(showToastOnSuccess: Boolean = false) {
        if (localNav.value.isRefreshing) return
        localNav.update { it.copy(isRefreshing = true) }
        viewModelScope.launch {
            repository.refreshActiveSessionData()
                .onSuccess {
                    localNav.update { it.copy(isRefreshing = false, syncError = null, actionError = null) }
                    if (showToastOnSuccess) toast("Synced with Vhaya")
                }
                .onFailure { err ->
                    val msg = friendlyApiErrorMessage(err, "Refresh failed")
                    localNav.update { it.copy(isRefreshing = false, syncError = msg) }
                }
        }
    }

    // Payments & Wallet
    fun initializeCardPayment(trip: RideTrip) {
        if (!beginBusy("card_hold")) return
        viewModelScope.launch {
            try {
                repository.initializeCardPayment(trip.id)
                    .onSuccess { outcome -> handlePaymentInitOutcome(outcome) }
                    .onFailure { reportActionFailure(it, "Could not start card hold") }
            } finally {
                endBusy("card_hold")
            }
        }
    }

    fun initializeWalletTopUp(amountZar: Double) {
        if (!beginBusy("wallet_topup")) return
        viewModelScope.launch {
            try {
                repository.initializeWalletTopUp(amountZar)
                    .onSuccess { outcome -> handlePaymentInitOutcome(outcome) }
                    .onFailure { reportActionFailure(it, "Could not start wallet top-up") }
            } finally {
                endBusy("wallet_topup")
            }
        }
    }

    fun initializeDriverWeeklySubscription(amountMinor: Long = 19900L) {
        if (!beginBusy("driver_pass")) return
        viewModelScope.launch {
            try {
                repository.initializeDriverWeeklySubscription(amountMinor)
                    .onSuccess { outcome -> handlePaymentInitOutcome(outcome) }
                    .onFailure { reportActionFailure(it, "Could not initialize Weekly Driver Pass") }
            } finally {
                endBusy("driver_pass")
            }
        }
    }

    private fun handlePaymentInitOutcome(outcome: PaymentInitOutcome) {
        if (outcome.mode == "mock") {
            if (!BuildConfig.ALLOW_MOCK_PAYMENTS) {
                reportActionFailure(
                    IllegalStateException("Mock payments are disabled in release builds."),
                    "Mock payments are unavailable in this build."
                )
                return
            }
            localNav.update { it.copy(pendingMockPayment = outcome) }
            return
        }

        val authorizationUrl = outcome.authorizationUrl?.takeIf { it.isNotBlank() }
        if (authorizationUrl == null) {
            reportActionFailure(
                IllegalStateException("Payment provider did not return a checkout URL."),
                "Could not open payment checkout."
            )
            return
        }
        _openUrlEvents.tryEmit(authorizationUrl)
    }

    fun submitMockPaymentDecision(success: Boolean) {
        if (!BuildConfig.ALLOW_MOCK_PAYMENTS) {
            localNav.update { it.copy(pendingMockPayment = null) }
            reportActionFailure(
                IllegalStateException("Mock payments are disabled in release builds."),
                "Mock payments are unavailable in this build."
            )
            return
        }
        val pending = localNav.value.pendingMockPayment ?: return
        if (!beginBusy("mock_payment")) return
        localNav.update { it.copy(pendingMockPayment = null) }
        viewModelScope.launch {
            try {
                repository.submitMockPaymentResult(pending.reference, success)
                    .onSuccess { result ->
                        localNav.update { it.copy(activePaymentVerification = result) }
                    }
                    .onFailure { err ->
                        reportActionFailure(err, "Payment confirmation failed")
                    }
            } finally {
                endBusy("mock_payment")
            }
        }
    }

    fun dismissMockPaymentDialog() {
        localNav.update { it.copy(pendingMockPayment = null) }
    }

    fun releaseCardHold(tripId: String) {
        if (!beginBusy("card_hold")) return
        viewModelScope.launch {
            try {
                repository.releaseCardHold(tripId)
                    .onSuccess { toast("Card hold released") }
                    .onFailure { reportActionFailure(it, "Could not release card hold") }
            } finally {
                endBusy("card_hold")
            }
        }
    }

    fun verifyPaymentReference(reference: String, tripIdHint: String? = null) {
        localNav.update {
            it.copy(
                activePaymentVerification = PaymentVerificationResult(
                    reference = reference,
                    tripId = tripIdHint,
                    state = PaymentVerificationState.CHECKING,
                    amountMinor = 0L,
                    message = "Verifying Paystack payment..."
                )
            )
        }
        viewModelScope.launch {
            repository.verifyPaymentReference(reference, tripIdHint)
                .onSuccess { res -> localNav.update { it.copy(activePaymentVerification = res) } }
                .onFailure { err ->
                    localNav.update {
                        it.copy(
                            activePaymentVerification = PaymentVerificationResult(
                                reference = reference,
                                tripId = tripIdHint,
                                state = PaymentVerificationState.FAILED,
                                amountMinor = 0L,
                                message = friendlyApiErrorMessage(err, "Payment verification failed.")
                            )
                        )
                    }
                }
        }
    }

    fun dismissPaymentVerification() {
        localNav.update { it.copy(activePaymentVerification = null) }
    }

    // Tabs
    fun selectRiderTab(tab: RiderTab) = localNav.update { it.copy(riderTab = tab) }
    fun selectDriverTab(tab: DriverTab) = localNav.update { it.copy(driverTab = tab) }
    fun selectAdminTab(tab: AdminTab) = localNav.update { it.copy(adminTab = tab) }

    // Requester Booking Actions
    fun searchPlaces(query: String) {
        placeSearchJob?.cancel()
        val requestId = ++placeSearchRequestId
        repository.clearPlaceSuggestions()
        localNav.update { it.copy(placeSearchError = null) }
        if (query.trim().length < 2) {
            placeSearchJob = null
            return
        }

        val proximity = localNav.value.booking.pickupCoords
        placeSearchJob = viewModelScope.launch {
            val result = repository.searchPlaces(query, proximity)
            if (requestId != placeSearchRequestId) return@launch
            result.onFailure { err ->
                val msg = friendlyApiErrorMessage(err, "Could not search places. Check your connection.")
                localNav.update { it.copy(placeSearchError = msg) }
            }
        }
    }

    private fun cancelPlaceSearch() {
        placeSearchRequestId += 1L
        placeSearchJob?.cancel()
        placeSearchJob = null
        repository.clearPlaceSuggestions()
        localNav.update { it.copy(placeSearchError = null) }
    }

    fun clearPlaceSearchError() {
        localNav.update { it.copy(placeSearchError = null) }
    }

    fun openSearchSheet(forPickup: Boolean = false) {
        cancelPlaceSearch()
        localNav.update {
            it.copy(
                placeSearchError = null,
                booking = it.booking.copy(step = RiderBookingStep.SEARCH_DESTINATION, pickingForPickup = forPickup)
            )
        }
    }

    fun openMapPinPicker(forPickup: Boolean) {
        cancelPlaceSearch()
        pinPreviewJob?.cancel()
        localNav.update {
            it.copy(
                booking = it.booking.copy(
                    step = RiderBookingStep.MAP_PIN_PICKER,
                    pickingForPickup = forPickup,
                    pinPreviewAddress = null,
                    pinPreviewLoading = false
                )
            )
        }
    }

    fun previewPinLocation(coords: RideCoordinates) {
        pinPreviewJob?.cancel()
        val hubMatch = LaunchPolicy.findNearestHubName(coords)
        if (hubMatch != null) {
            localNav.update { state ->
                state.copy(
                    booking = state.booking.copy(
                        pinPreviewAddress = hubMatch,
                        pinPreviewLoading = false
                    )
                )
            }
            return
        }
        localNav.update { state ->
            state.copy(booking = state.booking.copy(pinPreviewLoading = true))
        }
        pinPreviewJob = viewModelScope.launch {
            delay(420L)
            val resolved = repository.reverseGeocodeLabel(coords).getOrElse {
                LaunchPolicy.nearestLandmarkLabel(coords)
            }
            localNav.update { state ->
                if (state.booking.step == RiderBookingStep.MAP_PIN_PICKER) {
                    state.copy(
                        booking = state.booking.copy(
                            pinPreviewAddress = resolved,
                            pinPreviewLoading = false
                        )
                    )
                } else state
            }
        }
    }

    fun backToCompactHome() {
        pinPreviewJob?.cancel()
        cancelPlaceSearch()
        localNav.update {
            it.copy(
                booking = BookingDraft(
                    pickupLabel = it.booking.pickupLabel,
                    pickupCoords = it.booking.pickupCoords,
                    gpsUsingDeviceLocation = it.booking.gpsUsingDeviceLocation,
                    paymentMethod = it.booking.paymentMethod
                )
            )
        }
    }

    fun stepBackInBooking() {
        if (localNav.value.booking.step == RiderBookingStep.SEARCH_DESTINATION) {
            cancelPlaceSearch()
        }
        pinPreviewJob?.cancel()
        localNav.update { state ->
            val prev = when (state.booking.step) {
                RiderBookingStep.CHOOSE_RIDE_FARE -> RiderBookingStep.SEARCH_DESTINATION
                RiderBookingStep.MAP_PIN_PICKER -> RiderBookingStep.SEARCH_DESTINATION
                RiderBookingStep.SEARCH_DESTINATION -> RiderBookingStep.HOME_COMPACT
                RiderBookingStep.HOME_COMPACT -> RiderBookingStep.HOME_COMPACT
            }
            state.copy(
                booking = state.booking.copy(
                    step = prev,
                    pinPreviewAddress = null,
                    pinPreviewLoading = false
                )
            )
        }
    }

    fun resolvePinLocation(coords: RideCoordinates, forPickup: Boolean, fromGps: Boolean = false) {
        pinPreviewJob?.cancel()
        if (localNav.value.booking.pinConfirming) return
        localNav.update { it.copy(booking = it.booking.copy(pinConfirming = true)) }
        viewModelScope.launch {
            try {
                val res = repository.reverseGeocodeLabel(coords)
                val label = res.getOrElse {
                    if (fromGps) toast("Using nearest landmark for GPS location")
                    LaunchPolicy.nearestLandmarkLabel(coords)
                }
                if (forPickup) {
                    setPickupPoint(label, coords, fromGps)
                } else {
                    selectDestination(label, coords)
                }
            } finally {
                localNav.update { it.copy(booking = it.booking.copy(pinConfirming = false)) }
            }
        }
    }

    fun setPickupPoint(label: String, coords: RideCoordinates, fromGps: Boolean = false) {
        if (localNav.value.booking.step == RiderBookingStep.SEARCH_DESTINATION) {
            cancelPlaceSearch()
        }
        localNav.update { state ->
            val destCoords = state.booking.destinationCoords
            val nextStep = if (state.booking.step == RiderBookingStep.MAP_PIN_PICKER) {
                if (destCoords != null) RiderBookingStep.CHOOSE_RIDE_FARE else RiderBookingStep.SEARCH_DESTINATION
            } else state.booking.step
            state.copy(
                booking = state.booking.copy(
                    step = nextStep,
                    pickupLabel = label,
                    pickupCoords = coords,
                    gpsUsingDeviceLocation = fromGps,
                    routeError = null
                )
            )
        }
        val current = localNav.value.booking
        if (current.destinationCoords != null) {
            fetchLiveRoutePreview(
                pickup = current.pickupLabel,
                destination = current.destinationLabel,
                pickupCoords = coords,
                destCoords = current.destinationCoords
            )
        }
    }

    fun selectDestination(label: String, coords: RideCoordinates) {
        cancelPlaceSearch()
        val pickupLabel = localNav.value.booking.pickupLabel
        val pickupCoords = localNav.value.booking.pickupCoords
        localNav.update { state ->
            state.copy(
                booking = state.booking.copy(
                    step = RiderBookingStep.CHOOSE_RIDE_FARE,
                    destinationLabel = label,
                    destinationCoords = coords,
                    previewRoute = null,
                    visualRoute = null,
                    routeLoading = true,
                    routeError = null,
                    customOfferText = "",
                    isOfferExpanded = false
                )
            )
        }
        fetchLiveRoutePreview(pickupLabel, label, pickupCoords, coords)
    }

    fun refreshRoutePreview() {
        val current = localNav.value.booking
        val destCoords = current.destinationCoords ?: return
        fetchLiveRoutePreview(current.pickupLabel, current.destinationLabel, current.pickupCoords, destCoords)
    }

    private fun fetchLiveRoutePreview(
        pickup: String,
        destination: String,
        pickupCoords: RideCoordinates,
        destCoords: RideCoordinates
    ) {
        localNav.update { it.copy(booking = it.booking.copy(routeLoading = true, routeError = null)) }
        viewModelScope.launch {
            val visual = repository.fetchVisualRoadRoute(pickupCoords, destCoords)
            if (visual != null) {
                localNav.update { state ->
                    if (state.booking.destinationCoords == destCoords) {
                        state.copy(booking = state.booking.copy(visualRoute = visual))
                    } else state
                }
            }
        }
        viewModelScope.launch {
            repository.previewRoute(pickup, destination, pickupCoords, destCoords)
                .onSuccess { route ->
                    val shortMsg = if (route.durationSeconds <= LaunchPolicy.MIN_RIDE_DURATION_SECONDS) {
                        "Route must exceed 5 minutes. Choose a further pickup or destination."
                    } else null
                    localNav.update {
                        it.copy(
                            booking = it.booking.copy(
                                previewRoute = route,
                                routeLoading = false,
                                routeError = shortMsg
                            )
                        )
                    }
                }
                .onFailure { err ->
                    localNav.update {
                        it.copy(
                            booking = it.booking.copy(
                                previewRoute = null,
                                routeLoading = false,
                                routeError = err.message ?: "Could not preview route."
                            )
                        )
                    }
                }
        }
    }

    fun selectCategory(category: RideCategory) {
        localNav.update { state ->
            state.copy(booking = state.booking.copy(category = category))
        }
    }

    fun toggleOfferExpanded() {
        localNav.update { state ->
            val nextExpanded = !state.booking.isOfferExpanded
            val defaultText = if (nextExpanded && state.booking.customOfferText.isBlank()) {
                state.booking.estimatedFare.toInt().toString()
            } else state.booking.customOfferText
            state.copy(booking = state.booking.copy(isOfferExpanded = nextExpanded, customOfferText = defaultText))
        }
    }

    fun updateCustomOfferText(text: String) {
        val cleaned = text.filter { it.isDigit() || it == '.' }.take(6)
        localNav.update { it.copy(booking = it.booking.copy(customOfferText = cleaned)) }
    }

    fun adjustCustomOffer(delta: Int) {
        localNav.update { state ->
            val updated = (state.booking.effectiveFare.toInt() + delta).coerceAtLeast(20)
            state.copy(booking = state.booking.copy(isOfferExpanded = true, customOfferText = updated.toString()))
        }
    }

    fun setScheduledOffset(minutesFromNow: Int?) {
        localNav.update { it.copy(booking = it.booking.copy(scheduledOffsetMinutes = minutesFromNow)) }
    }

    fun selectPaymentMethod(method: PaymentMethod) {
        localNav.update { it.copy(booking = it.booking.copy(paymentMethod = method)) }
    }

    fun submitRideRequest() {
        val draft = localNav.value.booking
        val destCoords = draft.destinationCoords ?: return
        val destLabel = draft.destinationLabel.ifBlank { return }
        if (!beginBusy("request_ride")) return
        val customFare = draft.customOfferText.toDoubleOrNull()?.takeIf { it >= 20.0 }
        val pickupAt = draft.scheduledOffsetMinutes?.let { System.currentTimeMillis() + it * 60_000L }

        localNav.update { it.copy(booking = it.booking.copy(routeLoading = true, routeError = null)) }
        viewModelScope.launch {
            try {
                repository.createRideRequest(
                    pickup = draft.pickupLabel,
                    destination = destLabel,
                    pickupCoords = draft.pickupCoords,
                    destCoords = destCoords,
                    category = draft.category,
                    customOfferFare = customFare,
                    pickupAt = pickupAt,
                    paymentMethod = draft.paymentMethod,
                    existingRoute = draft.previewRoute
                )
                    .onSuccess {
                        localNav.update {
                            it.copy(
                                expiredTripNotice = null,
                                booking = BookingDraft(
                                    pickupLabel = draft.pickupLabel,
                                    pickupCoords = draft.pickupCoords,
                                    gpsUsingDeviceLocation = draft.gpsUsingDeviceLocation,
                                    paymentMethod = draft.paymentMethod
                                )
                            )
                        }
                        toast("Vhaya requested · finding nearby drivers")
                    }
                    .onFailure { err ->
                        val msg = friendlyApiErrorMessage(err, "Could not request ride")
                        localNav.update {
                            it.copy(
                                actionError = msg,
                                booking = it.booking.copy(
                                    routeLoading = false,
                                    routeError = msg
                                )
                            )
                        }
                        toast(msg)
                    }
            } finally {
                endBusy("request_ride")
            }
        }
    }

    private fun maybeTriggerCardPaymentCheckout(trip: RideTrip) {
        val session = (repository.authState.value as? AuthState.Authenticated)?.session ?: return
        if (session.role != RideRole.RIDER) return
        if (trip.riderId != session.userId) return
        if (trip.status != RideStatus.ACCEPTED) return
        if (trip.paymentMethod != PaymentMethod.CARD) return
        if (trip.paymentStatus != PaymentStatus.NONE) return
        if (!repository.isAppInForeground.value) return
        val nav = localNav.value
        if (nav.activePaymentVerification != null || nav.pendingMockPayment != null || "card_hold" in nav.busyActions) {
            return
        }
        if (!autoPaymentTriggeredTripIds.add(trip.id)) return
        toast("Driver accepted · taking you to payment...")
        initializeCardPayment(trip)
    }

    fun acceptDriverOffer(tripId: String, offerId: String) {
        if (!beginBusy("accept_offer")) return
        viewModelScope.launch {
            try {
                repository.acceptDriverOffer(tripId, offerId)
                    .onSuccess { updatedTrip ->
                        if (updatedTrip.paymentMethod == PaymentMethod.CARD &&
                            updatedTrip.paymentStatus == PaymentStatus.NONE &&
                            autoPaymentTriggeredTripIds.add(updatedTrip.id)
                        ) {
                            toast("Driver confirmed · taking you to payment...")
                            initializeCardPayment(updatedTrip)
                        } else if (updatedTrip.paymentStatus != PaymentStatus.NONE || updatedTrip.paymentMethod != PaymentMethod.CARD) {
                            toast("Driver confirmed · 6-digit PIN ready")
                        }
                    }
                    .onFailure { reportActionFailure(it, "Could not accept offer") }
            } finally {
                endBusy("accept_offer")
            }
        }
    }

    fun hideOfferLocally(offerId: String) {
        localNav.update { it.copy(dismissedOfferIds = it.dismissedOfferIds + offerId) }
    }

    fun flagDriverOffer(tripId: String, offerId: String, reason: String) {
        if (!beginBusy("flag_offer")) return
        viewModelScope.launch {
            try {
                repository.flagDriverOffer(tripId, offerId, reason)
                    .onSuccess { toast("Offer reported to Vhaya Support") }
                    .onFailure { reportActionFailure(it, "Could not report offer") }
            } finally {
                endBusy("flag_offer")
            }
        }
    }

    fun riderConfirmArrival(tripId: String) {
        if (!beginBusy("confirm_arrival")) return
        viewModelScope.launch {
            try {
                repository.riderConfirmArrival(tripId)
                    .onSuccess { toast("Driver arrival confirmed · share your 6-digit PIN") }
                    .onFailure { reportActionFailure(it, "Could not confirm arrival") }
            } finally {
                endBusy("confirm_arrival")
            }
        }
    }

    fun cancelTrip(tripId: String, reason: String? = null) {
        if (!beginBusy("cancel_trip")) return
        viewModelScope.launch {
            try {
                repository.cancelTrip(tripId, reason)
                    .onSuccess { toast("Ride cancelled") }
                    .onFailure { reportActionFailure(it, "Could not cancel ride") }
            } finally {
                endBusy("cancel_trip")
            }
        }
    }

    fun rateTrip(tripId: String, stars: Int) {
        if (!beginBusy("rate_trip")) return
        viewModelScope.launch {
            try {
                val beforeQueue = repository.pendingOfflineCount.value
                repository.rateTrip(tripId, stars)
                    .onSuccess {
                        localNav.update {
                            if (it.pendingRatingTripId == tripId) it.copy(pendingRatingTripId = null) else it
                        }
                        toast("Thanks for rating $stars ★")
                    }
                    .onFailure { err ->
                        if (repository.pendingOfflineCount.value > beforeQueue) {
                            localNav.update {
                                if (it.pendingRatingTripId == tripId) it.copy(pendingRatingTripId = null) else it
                            }
                            toast("Offline · rating queued and will sync automatically")
                        } else {
                            reportActionFailure(err, "Could not save rating")
                        }
                    }
            } finally {
                endBusy("rate_trip")
            }
        }
    }

    fun dismissPostTripRating() {
        localNav.update { it.copy(pendingRatingTripId = null) }
    }

    fun sendTripChat(tripId: String, text: String) {
        if (!beginBusy("chat_send")) return
        viewModelScope.launch {
            try {
                val beforeQueue = repository.pendingOfflineCount.value
                repository.sendTripMessage(tripId, text)
                    .onFailure { err ->
                        if (repository.pendingOfflineCount.value > beforeQueue) {
                            toast("Offline · message queued and will send when connected")
                        } else {
                            reportActionFailure(err, "Could not send message")
                        }
                    }
            } finally {
                endBusy("chat_send")
            }
        }
    }

    fun savePlace(label: String, coords: RideCoordinates) {
        viewModelScope.launch {
            repository.toggleSavedDestination(label, coords)
            toast("Saved place added")
        }
    }

    fun deleteSavedPlace(id: String) {
        viewModelScope.launch {
            repository.deleteSavedDestination(id)
            toast("Saved place removed")
        }
    }

    fun addTrustedContact(name: String, phone: String) {
        viewModelScope.launch {
            repository.addTrustedContact(name, phone)
            toast("Trusted contact saved")
        }
    }

    fun removeTrustedContact(id: String) {
        viewModelScope.launch { repository.removeTrustedContact(id) }
    }

    fun createSupportCase(
        trip: RideTrip,
        category: String,
        priority: String,
        message: String,
        requestRefund: Boolean = false,
        attachmentUri: Uri? = null
    ) {
        if (!beginBusy("support_submit")) return
        viewModelScope.launch {
            try {
                val beforeQueue = repository.pendingOfflineCount.value
                repository.createSupportCase(
                    trip = trip,
                    category = category,
                    priority = priority,
                    message = message,
                    requestRefund = requestRefund,
                    attachmentUri = attachmentUri
                )
                    .onSuccess { toast("Support ticket submitted") }
                    .onFailure { err ->
                        if (repository.pendingOfflineCount.value > beforeQueue) {
                            toast("Offline · issue queued and will submit automatically")
                        } else {
                            reportActionFailure(err, "Could not submit ticket")
                        }
                    }
            } finally {
                endBusy("support_submit")
            }
        }
    }

    fun sendRiderSupportFollowUp(supportCase: SupportCase, message: String) {
        if (!beginBusy("support_submit")) return
        viewModelScope.launch {
            try {
                val beforeQueue = repository.pendingOfflineCount.value
                repository.sendRiderSupportFollowUp(supportCase, message)
                    .onSuccess { toast("Message sent to Vhaya Support") }
                    .onFailure { err ->
                        if (repository.pendingOfflineCount.value > beforeQueue) {
                            toast("Offline · reply queued and will send automatically")
                        } else {
                            reportActionFailure(err, "Could not send message")
                        }
                    }
            } finally {
                endBusy("support_submit")
            }
        }
    }

    // Driver Actions
    fun setDriverOnline(online: Boolean) {
        if (!beginBusy("driver_online")) return
        viewModelScope.launch {
            try {
                repository.setDriverOnline(online)
                    .onSuccess { toast(if (online) "You are online" else "You are now offline") }
                    .onFailure { reportActionFailure(it, "Could not update driver availability") }
            } finally {
                endBusy("driver_online")
            }
        }
    }

    fun toggleDriverSound() = localNav.update { it.copy(driverSoundEnabled = !it.driverSoundEnabled) }
    fun toggleDriverHeatmap() = localNav.update { it.copy(driverHeatmapEnabled = !it.driverHeatmapEnabled) }

    fun skipDriverRequest(totalCount: Int) {
        if (totalCount <= 1) return
        localNav.update { it.copy(driverRequestIndex = (it.driverRequestIndex + 1) % totalCount) }
    }

    fun updateDriverCoords(coords: RideCoordinates, activeTripId: String? = null) {
        localNav.update { it.copy(driverCurrentCoords = coords) }
        if (activeTripId != null) {
            viewModelScope.launch {
                repository.updateLiveDriverLocation(activeTripId, coords)
                    .onFailure { toast(friendlyApiErrorMessage(it, "Could not update GPS location")) }
            }
        }
    }

    fun driverSubmitOffer(trip: RideTrip, amount: Double) {
        if (!beginBusy("driver_offer")) return
        viewModelScope.launch {
            try {
                repository.driverSubmitOffer(trip, amount, localNav.value.driverCurrentCoords)
                    .onSuccess {
                        toast(
                            if (amount == trip.fare) "Offer sent at ${LaunchPolicy.formatZar(amount)} · awaiting rider"
                            else "Counter-offer ${LaunchPolicy.formatZar(amount)} sent"
                        )
                    }
                    .onFailure { reportActionFailure(it, "Could not submit offer") }
            } finally {
                endBusy("driver_offer")
            }
        }
    }

    fun driverMarkArrived(tripId: String) {
        if (!beginBusy("driver_arrived")) return
        viewModelScope.launch {
            try {
                repository.driverMarkArrived(tripId, localNav.value.driverCurrentCoords)
                    .onSuccess { toast("Arrived · ask rider for 6-digit PIN") }
                    .onFailure { reportActionFailure(it, "Arrival check failed") }
            } finally {
                endBusy("driver_arrived")
            }
        }
    }

    fun driverVerifyPinAndStart(tripId: String, pin: String) {
        if (!beginBusy("driver_pin")) return
        viewModelScope.launch {
            try {
                repository.verifyPinAndStartTrip(tripId, pin)
                    .onSuccess {
                        localNav.update { it.copy(driverDriveModeMinimized = false) }
                        toast("PIN verified · Drive Mode active")
                    }
                    .onFailure { reportActionFailure(it, "Invalid PIN") }
            } finally {
                endBusy("driver_pin")
            }
        }
    }

    fun setDriverDriveModeMinimized(minimized: Boolean) = localNav.update { it.copy(driverDriveModeMinimized = minimized) }

    fun driverCompleteTrip(tripId: String) {
        if (!beginBusy("driver_complete")) return
        viewModelScope.launch {
            try {
                repository.completeTrip(tripId)
                    .onSuccess {
                        localNav.update {
                            it.copy(
                                driverDriveModeMinimized = false,
                                pendingDriverSummaryTripId = tripId
                            )
                        }
                        toast("Trip completed · fare recorded")
                    }
                    .onFailure { reportActionFailure(it, "Could not complete trip") }
            } finally {
                endBusy("driver_complete")
            }
        }
    }

    fun dismissDriverTripSummary() {
        localNav.update { it.copy(pendingDriverSummaryTripId = null) }
    }

    fun clearDriverOnboardingMessages() {
        localNav.update { it.copy(driverOnboardingError = null, driverOnboardingNotice = null) }
    }

    fun saveDriverOnboarding(app: DriverApplication, submit: Boolean) {
        localNav.update {
            it.copy(driverOnboardingBusy = true, driverOnboardingError = null, driverOnboardingNotice = null)
        }
        viewModelScope.launch {
            repository.saveDriverApplication(app, submit)
                .onSuccess {
                    val msg = if (submit) {
                        "Application submitted! Please email your documents to ${LaunchPolicy.DRIVER_DOCS_EMAIL} for manual verification."
                    } else {
                        "Driver draft saved"
                    }
                    localNav.update {
                        it.copy(driverOnboardingBusy = false, driverOnboardingError = null, driverOnboardingNotice = msg)
                    }
                    toast(msg)
                }
                .onFailure { err ->
                    val msg = friendlyApiErrorMessage(err, "Could not save driver application")
                    localNav.update {
                        it.copy(driverOnboardingBusy = false, driverOnboardingError = msg, driverOnboardingNotice = null)
                    }
                    toast(msg)
                }
        }
    }

    fun uploadDriverDocument(draft: DriverApplication?, docType: DriverDocumentType, uri: Uri) {
        localNav.update {
            it.copy(uploadingDocType = docType, driverOnboardingError = null, driverOnboardingNotice = null)
        }
        viewModelScope.launch {
            repository.uploadDriverDocument(draft, docType, uri)
                .onSuccess {
                    val msg = "${docType.title} uploaded"
                    localNav.update {
                        it.copy(uploadingDocType = null, driverOnboardingError = null, driverOnboardingNotice = msg)
                    }
                    toast(msg)
                }
                .onFailure { err ->
                    val msg = friendlyApiErrorMessage(err, "Document upload failed")
                    localNav.update {
                        it.copy(uploadingDocType = null, driverOnboardingError = msg, driverOnboardingNotice = null)
                    }
                    toast(msg)
                }
        }
    }

    // Admin Actions
    fun updateComplianceCheck(userId: String, docType: DriverDocumentType, status: ComplianceStatus, note: String?) {
        if (!beginBusy("admin_action")) return
        viewModelScope.launch {
            try {
                repository.updateComplianceCheck(userId, docType, status, note)
                    .onSuccess { toast("${docType.title} marked ${status.label}") }
                    .onFailure { reportActionFailure(it, "Could not update compliance check") }
            } finally {
                endBusy("admin_action")
            }
        }
    }

    fun decideDriverApplication(userId: String, decision: String, staffNote: String?) {
        if (!beginBusy("admin_action")) return
        viewModelScope.launch {
            try {
                repository.decideDriverApplication(userId, decision, staffNote)
                    .onSuccess { toast("Driver status updated to $decision") }
                    .onFailure { reportActionFailure(it, "Could not update driver review") }
            } finally {
                endBusy("admin_action")
            }
        }
    }

    fun updateUserRoleOrSuspension(userId: String, role: RideRole? = null, suspend: Boolean? = null, reason: String? = null) {
        if (!beginBusy("admin_action")) return
        viewModelScope.launch {
            try {
                repository.updateUserRoleOrSuspension(userId, role, suspend, reason)
                    .onSuccess { toast("Account updated") }
                    .onFailure { reportActionFailure(it, "Could not update account") }
            } finally {
                endBusy("admin_action")
            }
        }
    }

    fun updateSupportCaseStatus(caseId: Long, status: String, priority: String? = null) {
        if (!beginBusy("admin_action")) return
        viewModelScope.launch {
            try {
                repository.updateSupportCaseStatus(caseId, status, priority)
                    .onSuccess { toast("Ticket #$caseId updated to $status") }
                    .onFailure { reportActionFailure(it, "Could not update ticket") }
            } finally {
                endBusy("admin_action")
            }
        }
    }

    fun replyAdminSupportCase(caseId: Long, message: String) {
        if (!beginBusy("admin_action")) return
        viewModelScope.launch {
            try {
                repository.addAdminSupportReply(caseId, message)
                    .onFailure { reportActionFailure(it, "Could not send reply") }
            } finally {
                endBusy("admin_action")
            }
        }
    }

    fun recordPayout(
        driverId: String,
        provider: String,
        externalReference: String,
        amountZar: Double,
        periodStart: String?,
        periodEnd: String?,
        note: String?
    ) {
        if (!beginBusy("admin_payout")) return
        viewModelScope.launch {
            try {
                repository.recordPayout(driverId, provider, externalReference, amountZar, periodStart, periodEnd, note)
                    .onSuccess { toast("Payout ${LaunchPolicy.formatZar(amountZar)} recorded") }
                    .onFailure { reportActionFailure(it, "Could not record payout") }
            } finally {
                endBusy("admin_payout")
            }
        }
    }

    fun reconcilePayout(payoutId: Long, status: String, note: String? = null) {
        if (!beginBusy("admin_action")) return
        viewModelScope.launch {
            try {
                repository.reconcilePayout(payoutId, status, note)
                    .onSuccess { toast("Payout marked $status") }
                    .onFailure { reportActionFailure(it, "Could not update payout") }
            } finally {
                endBusy("admin_action")
            }
        }
    }

    fun runWeeklyPayoutBatch(periodStartIso: String, periodEndIso: String) {
        if (!beginBusy("admin_batch")) return
        viewModelScope.launch {
            try {
                repository.runWeeklyPayoutBatch(periodStartIso, periodEndIso)
                    .onSuccess { toast("Weekly payout batch generated") }
                    .onFailure { reportActionFailure(it, "Could not run weekly batch") }
            } finally {
                endBusy("admin_batch")
            }
        }
    }

    fun updatePayoutBatchStatus(batchId: String, status: String) {
        if (!beginBusy("admin_action")) return
        viewModelScope.launch {
            try {
                repository.updatePayoutBatchStatus(batchId, status)
                    .onSuccess { toast("Batch marked $status") }
                    .onFailure { reportActionFailure(it, "Could not update batch status") }
            } finally {
                endBusy("admin_action")
            }
        }
    }

    private data class SessionSlice(
        val authState: AuthState,
        val apiBaseUrl: String,
        val themeMode: AppThemeMode,
        val privacyPreferences: PrivacyPreferences,
        val pendingOfflineCount: Int,
        val declinedDriverTripIds: Set<String>
    )

    private data class CoreSlice(
        val trips: List<RideTrip>,
        val saved: List<SavedDestination>,
        val contacts: List<TrustedContact>,
        val wallet: List<WalletTransaction>,
        val drivers: List<DriverApplication>
    )

    private data class OpsSlice(
        val support: List<SupportCase>,
        val payouts: List<DriverPayoutRecord>,
        val users: List<UserAccountSummary>,
        val batches: List<PayoutBatchRecord>,
        val logs: List<ActivityLogEntry>
    )

    private data class LiveSummarySlice(
        val walletBalanceMinor: Long,
        val driverEarnings: DriverEarningsSummary,
        val driverSubscription: DriverSubscriptionSummary?,
        val adminOverview: AdminOverviewSummary,
        val placeSuggestions: List<PlaceSearchSuggestion>,
        val searchingPlaces: Boolean
    )

    class Factory(private val repository: WayfareRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = WayfareViewModel(repository) as T
    }
}
