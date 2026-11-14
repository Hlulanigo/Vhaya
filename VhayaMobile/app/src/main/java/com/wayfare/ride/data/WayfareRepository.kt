package com.wayfare.ride.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import com.wayfare.ride.BuildConfig
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
import com.wayfare.ride.domain.PaymentVerificationResult
import com.wayfare.ride.domain.PaymentVerificationState
import com.wayfare.ride.domain.PayoutBatchRecord
import com.wayfare.ride.domain.PlaceSearchSuggestion
import com.wayfare.ride.domain.PolylineCodec
import com.wayfare.ride.domain.PricingMode
import com.wayfare.ride.domain.RideCategory
import com.wayfare.ride.domain.RideCoordinates
import com.wayfare.ride.domain.RideRole
import com.wayfare.ride.domain.RideRoute
import com.wayfare.ride.domain.RideTrip
import com.wayfare.ride.domain.SavedDestination
import com.wayfare.ride.domain.SupportCase
import com.wayfare.ride.domain.TrustedContact
import com.wayfare.ride.domain.UserAccountSummary
import com.wayfare.ride.domain.UserSession
import com.wayfare.ride.domain.WalletTransaction
import com.wayfare.ride.ui.theme.AppThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.ByteArrayOutputStream
import java.util.UUID

class WayfareRepository(
    private val context: Context,
    private val db: WayfareDatabase,
    private val sessionStore: SessionStore,
    private val apiClient: VhayaApiClient
) {
    private val tripDao = db.tripDao()
    private val savedDao = db.savedDestinationDao()
    private val contactDao = db.trustedContactDao()
    private val walletDao = db.walletDao()
    private val driverDao = db.driverApplicationDao()
    private val supportDao = db.supportCaseDao()
    private val payoutDao = db.payoutDao()
    private val userDao = db.userAccountDao()
    private val payoutBatchDao = db.payoutBatchDao()
    private val activityLogDao = db.activityLogDao()

    private val tokenRefreshMutex = Mutex()

    private val _apiBaseUrl = MutableStateFlow(sessionStore.loadApiBaseUrl())
    val apiBaseUrl: StateFlow<String> = _apiBaseUrl.asStateFlow()

    private val _themeMode = MutableStateFlow(sessionStore.loadThemeMode())
    val themeMode: StateFlow<AppThemeMode> = _themeMode.asStateFlow()

    private val _firebaseApiKey = MutableStateFlow(sessionStore.loadFirebaseApiKey())

    private val _authState = MutableStateFlow(resolveInitialAuthState())
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val _walletBalanceMinor = MutableStateFlow(0L)
    val walletBalanceMinor: StateFlow<Long> = _walletBalanceMinor.asStateFlow()

    private val _driverEarnings = MutableStateFlow(DriverEarningsSummary())
    val driverEarnings: StateFlow<DriverEarningsSummary> = _driverEarnings.asStateFlow()

    private val _driverSubscription = MutableStateFlow<DriverSubscriptionSummary?>(null)
    val driverSubscription: StateFlow<DriverSubscriptionSummary?> = _driverSubscription.asStateFlow()

    private val _adminOverview = MutableStateFlow(AdminOverviewSummary())
    val adminOverview: StateFlow<AdminOverviewSummary> = _adminOverview.asStateFlow()

    private val _placeSuggestions = MutableStateFlow<List<PlaceSearchSuggestion>>(emptyList())
    val placeSuggestions: StateFlow<List<PlaceSearchSuggestion>> = _placeSuggestions.asStateFlow()

    private val _searchingPlaces = MutableStateFlow(false)
    val searchingPlaces: StateFlow<Boolean> = _searchingPlaces.asStateFlow()

    val trips: Flow<List<RideTrip>> = tripDao.observeAll().map { list -> list.map { it.toDomain() } }
    val savedDestinations: Flow<List<SavedDestination>> = savedDao.observeAll().map { list -> list.map { it.toDomain() } }
    val trustedContacts: Flow<List<TrustedContact>> = contactDao.observeAll().map { list -> list.map { it.toDomain() } }
    val walletTransactions: Flow<List<WalletTransaction>> = walletDao.observeAll().map { list -> list.map { it.toDomain() } }
    val driverApplications: Flow<List<DriverApplication>> = driverDao.observeAll().map { list -> list.map { it.toDomain() } }
    val supportCases: Flow<List<SupportCase>> = supportDao.observeAll().map { list -> list.map { it.toDomain() } }
    val payoutRecords: Flow<List<DriverPayoutRecord>> = payoutDao.observeAll().map { list -> list.map { it.toDomain() } }
    val userAccounts: Flow<List<UserAccountSummary>> = userDao.observeAll().map { list -> list.map { it.toDomain() } }
    val payoutBatches: Flow<List<PayoutBatchRecord>> = payoutBatchDao.observeAll().map { list -> list.map { it.toDomain() } }
    val activityLogs: Flow<List<ActivityLogEntry>> = activityLogDao.observeAll().map { list -> list.map { it.toDomain() } }

    fun setThemeMode(mode: AppThemeMode) {
        sessionStore.saveThemeMode(mode)
        _themeMode.value = mode
    }

    private fun resolveInitialAuthState(): AuthState {
        val saved = sessionStore.loadSession() ?: return AuthState.Unauthenticated
        if (saved.idToken.isNullOrBlank()) {
            sessionStore.saveSession(null)
            return AuthState.Unauthenticated
        }
        return if (!saved.emailVerified) {
            AuthState.EmailVerificationPending(saved)
        } else {
            AuthState.Authenticated(saved)
        }
    }

    private fun currentSessionOrNull(): UserSession? = when (val st = _authState.value) {
        is AuthState.Authenticated -> st.session
        is AuthState.EmailVerificationPending -> st.session
        AuthState.Unauthenticated -> null
    }

    private suspend fun ensureFreshToken(forceRefresh: Boolean = false): Result<Pair<String, UserSession>> {
        val apiUrl = _apiBaseUrl.value.trim().trimEnd('/')
        if (apiUrl.isBlank()) {
            return Result.failure(IllegalStateException("We're having trouble connecting to Vhaya. Please try again in a moment."))
        }
        val session = (authState.value as? AuthState.Authenticated)?.session
            ?: return Result.failure(IllegalStateException("Sign in to Vhaya to continue."))
        val token = session.idToken
        if (token.isNullOrBlank()) {
            return Result.failure(IllegalStateException("Session token missing. Please sign in again."))
        }

        val now = System.currentTimeMillis()
        val needsRefresh = forceRefresh ||
            (session.expiresAtEpochMs > 0L && now >= session.expiresAtEpochMs - 60_000L)
        if (!needsRefresh) {
            return Result.success(apiUrl to session)
        }

        val fbKey = _firebaseApiKey.value.trim()
        val refreshToken = session.refreshToken.orEmpty()
        if (fbKey.isBlank() || refreshToken.isBlank()) {
            return if (forceRefresh) {
                Result.failure(IllegalStateException("Your session expired. Sign in again."))
            } else {
                Result.success(apiUrl to session)
            }
        }

        return tokenRefreshMutex.withLock {
            val latest = (authState.value as? AuthState.Authenticated)?.session ?: session
            val latestNeeds = forceRefresh ||
                (latest.expiresAtEpochMs > 0L && System.currentTimeMillis() >= latest.expiresAtEpochMs - 60_000L)
            if (!latestNeeds && !latest.idToken.isNullOrBlank()) {
                return@withLock Result.success(apiUrl to latest)
            }
            val refreshed = apiClient.refreshIdToken(fbKey, latest.refreshToken.orEmpty())
            if (refreshed.isSuccess) {
                val r = refreshed.getOrThrow()
                val updatedSession = latest.copy(
                    idToken = r.idToken,
                    refreshToken = r.refreshToken,
                    expiresAtEpochMs = r.expiresAtEpochMs
                )
                sessionStore.saveSession(updatedSession)
                _authState.value = AuthState.Authenticated(updatedSession)
                Result.success(apiUrl to updatedSession)
            } else {
                val err = refreshed.exceptionOrNull()
                if (err is ApiHttpException && err.statusCode == 401) {
                    signOut()
                }
                Result.failure(err ?: IllegalStateException("Session expired. Sign in again."))
            }
        }
    }

    private suspend fun <T> withValidToken(
        block: suspend (apiUrl: String, token: String, session: UserSession) -> Result<T>
    ): Result<T> {
        val auth = ensureFreshToken(forceRefresh = false)
        if (auth.isFailure) return Result.failure(auth.exceptionOrNull()!!)
        val (apiUrl, session) = auth.getOrThrow()
        val first = block(apiUrl, session.idToken.orEmpty(), session)
        val err = first.exceptionOrNull()
        if (err is ApiHttpException && err.statusCode == 401) {
            val retryAuth = ensureFreshToken(forceRefresh = true)
            if (retryAuth.isFailure) return Result.failure(retryAuth.exceptionOrNull()!!)
            val (retryUrl, retrySession) = retryAuth.getOrThrow()
            return block(retryUrl, retrySession.idToken.orEmpty(), retrySession)
        }
        return first
    }

    suspend fun signInWithEmailPassword(email: String, password: String): Result<UserSession> {
        val cleanEmail = email.trim().lowercase()
        val apiUrl = _apiBaseUrl.value.trim().trimEnd('/')
        val fbKey = _firebaseApiKey.value.trim()

        if (apiUrl.isBlank()) {
            return Result.failure(IllegalStateException("We're having trouble connecting to Vhaya. Please try again in a moment."))
        }
        if (fbKey.isBlank()) {
            return Result.failure(IllegalStateException("Vhaya sign-in is temporarily unavailable. Please try again shortly."))
        }
        if (cleanEmail.isBlank() || password.isBlank()) {
            return Result.failure(IllegalArgumentException("Enter your email address and password."))
        }

        val authRes = apiClient.firebaseSignIn(fbKey, cleanEmail, password)
        if (authRes.isFailure) return authRes

        var session = authRes.getOrThrow()
        if (!session.emailVerified) {
            sessionStore.saveSession(session)
            _authState.value = AuthState.EmailVerificationPending(session)
            return Result.success(session)
        }

        val token = session.idToken.orEmpty()
        val ridesRes = apiClient.fetchRidesAndRole(apiUrl, token)
        if (ridesRes.isFailure) {
            return Result.failure(ridesRes.exceptionOrNull() ?: IllegalStateException("Could not reach Vhaya backend."))
        }
        val (backendRole, backendTrips) = ridesRes.getOrThrow()
        session = session.copy(role = backendRole)
        tripDao.replaceAll(backendTrips.map { it.toEntity() })
        sessionStore.saveSession(session)
        _authState.value = AuthState.Authenticated(session)
        refreshRoleScopedData(apiUrl, token, session)
        return Result.success(session)
    }

    suspend fun signUpWithEmailPassword(displayName: String, email: String, password: String): Result<UserSession> {
        val cleanName = displayName.trim()
        val cleanEmail = email.trim().lowercase()
        val apiUrl = _apiBaseUrl.value.trim().trimEnd('/')
        val fbKey = _firebaseApiKey.value.trim()

        if (apiUrl.isBlank()) {
            return Result.failure(IllegalStateException("We're having trouble connecting to Vhaya. Please try again in a moment."))
        }
        if (fbKey.isBlank()) {
            return Result.failure(IllegalStateException("Vhaya sign-in is temporarily unavailable. Please try again shortly."))
        }
        if (cleanName.isBlank()) {
            return Result.failure(IllegalArgumentException("Enter your full name."))
        }
        if (password.length < 6) {
            return Result.failure(IllegalArgumentException("Use a password with at least 6 characters."))
        }

        val res = apiClient.firebaseSignUp(fbKey, cleanName, cleanEmail, password)
        res.onSuccess { session ->
            sessionStore.saveSession(session)
            _authState.value = AuthState.EmailVerificationPending(session)
        }
        return res
    }

    suspend fun checkEmailVerified(): Result<Boolean> {
        val pending = (_authState.value as? AuthState.EmailVerificationPending)?.session
            ?: return Result.failure(IllegalStateException("No pending email verification session."))
        val fbKey = _firebaseApiKey.value.trim()
        val apiUrl = _apiBaseUrl.value.trim().trimEnd('/')
        val token = pending.idToken.orEmpty()
        if (fbKey.isBlank() || token.isBlank()) {
            return Result.failure(IllegalStateException("Your session has expired. Please sign in again."))
        }

        val verified = apiClient.lookupEmailVerified(fbKey, token)
        if (verified) {
            var verifiedSession = pending.copy(emailVerified = true)
            if (apiUrl.isNotBlank()) {
                apiClient.fetchRidesAndRole(apiUrl, token).onSuccess { (role, backendTrips) ->
                    verifiedSession = verifiedSession.copy(role = role)
                    tripDao.replaceAll(backendTrips.map { it.toEntity() })
                }
            }
            sessionStore.saveSession(verifiedSession)
            _authState.value = AuthState.Authenticated(verifiedSession)
            if (apiUrl.isNotBlank()) {
                refreshRoleScopedData(apiUrl, token, verifiedSession)
            }
        }
        return Result.success(verified)
    }

    suspend fun resendVerificationEmail(): Result<Unit> {
        val pending = (_authState.value as? AuthState.EmailVerificationPending)?.session
            ?: return Result.failure(IllegalStateException("No pending verification session."))
        val fbKey = _firebaseApiKey.value.trim()
        val token = pending.idToken.orEmpty()
        if (fbKey.isBlank() || token.isBlank()) {
            return Result.failure(IllegalStateException("Vhaya sign-in is temporarily unavailable. Please try again shortly."))
        }
        return apiClient.sendEmailVerification(fbKey, token)
    }

    suspend fun sendPasswordReset(email: String): Result<Unit> {
        val fbKey = _firebaseApiKey.value.trim()
        if (fbKey.isBlank()) {
            return Result.failure(IllegalStateException("Vhaya sign-in is temporarily unavailable. Please try again shortly."))
        }
        if (email.isBlank()) {
            return Result.failure(IllegalArgumentException("Enter your email address first."))
        }
        return apiClient.sendPasswordReset(fbKey, email)
    }

    suspend fun signOut() {
        sessionStore.saveSession(null)
        _authState.value = AuthState.Unauthenticated
        _walletBalanceMinor.value = 0L
        _driverEarnings.value = DriverEarningsSummary()
        _driverSubscription.value = null
        _adminOverview.value = AdminOverviewSummary()
        _placeSuggestions.value = emptyList()
        _searchingPlaces.value = false
        tripDao.clearAll()
        walletDao.clearAll()
        driverDao.clearAll()
        supportDao.clearAll()
        payoutDao.clearAll()
        userDao.clearAll()
        payoutBatchDao.clearAll()
        activityLogDao.clearAll()
    }

    suspend fun refreshActiveSessionData(): Result<Unit> {
        if (_authState.value !is AuthState.Authenticated) return Result.success(Unit)
        return withValidToken { apiUrl, token, session ->
            val ridesRes = apiClient.fetchRidesAndRole(apiUrl, token)
            if (ridesRes.isFailure) {
                val err = ridesRes.exceptionOrNull()
                return@withValidToken Result.failure(IllegalStateException(friendlyApiErrorMessage(err)))
            }
            val (backendRole, backendTrips) = ridesRes.getOrThrow()
            var updatedSession = session
            if (backendRole != session.role) {
                updatedSession = session.copy(role = backendRole)
                sessionStore.saveSession(updatedSession)
                _authState.value = AuthState.Authenticated(updatedSession)
            }
            tripDao.replaceAll(backendTrips.map { it.toEntity() })
            val partialErr = refreshRoleScopedData(apiUrl, token, updatedSession)
            if (partialErr != null) {
                Result.failure(IllegalStateException(partialErr))
            } else {
                Result.success(Unit)
            }
        }
    }

    private suspend fun refreshRoleScopedData(
        apiUrl: String,
        token: String,
        session: UserSession
    ): String? = coroutineScope {
        val errors = mutableListOf<String>()
        when (session.role) {
            RideRole.RIDER -> {
                val walletJob = async { apiClient.fetchWallet(apiUrl, token) }
                val supportJob = async { apiClient.fetchSupportCases(apiUrl, token) }
                val driverProfileJob = async {
                    apiClient.fetchDriverProfile(apiUrl, token, session.displayName, session.email)
                }

                walletJob.await()
                    .onSuccess { (summary, txs) ->
                        _walletBalanceMinor.value = summary.balanceMinor
                        walletDao.replaceAll(txs.map { it.toEntity() })
                    }
                    .onFailure { errors += friendlyApiErrorMessage(it, "Could not load wallet") }
                supportJob.await()
                    .onSuccess { cases ->
                        supportDao.replaceAll(cases.map { it.toEntity() })
                    }
                    .onFailure { errors += friendlyApiErrorMessage(it, "Could not load support cases") }
                driverProfileJob.await()
                    .onSuccess { profile ->
                        if (profile != null) {
                            driverDao.replaceAll(listOf(profile.toEntity()))
                        } else {
                            driverDao.clearAll()
                        }
                    }
                    .onFailure { errors += friendlyApiErrorMessage(it, "Could not load driver status") }
            }
            RideRole.DRIVER -> {
                val profileJob = async {
                    apiClient.fetchDriverProfile(apiUrl, token, session.displayName, session.email)
                }
                val earningsJob = async { apiClient.fetchDriverEarnings(apiUrl, token) }
                val subscriptionJob = async { apiClient.fetchDriverSubscription(apiUrl, token) }

                profileJob.await()
                    .onSuccess { profile ->
                        if (profile != null) {
                            driverDao.replaceAll(listOf(profile.toEntity()))
                        } else {
                            driverDao.clearAll()
                        }
                    }
                    .onFailure { errors += friendlyApiErrorMessage(it, "Could not load driver profile") }
                earningsJob.await()
                    .onSuccess { earnings ->
                        _driverEarnings.value = earnings
                    }
                    .onFailure { errors += friendlyApiErrorMessage(it, "Could not load earnings") }
                subscriptionJob.await()
                    .onSuccess { sub ->
                        _driverSubscription.value = sub
                    }
                    .onFailure { errors += friendlyApiErrorMessage(it, "Could not load driver pass status") }
            }
            RideRole.ADMIN -> {
                val overviewJob = async { apiClient.fetchAdminOverview(apiUrl, token) }
                val activityJob = async { apiClient.fetchAdminActivity(apiUrl, token) }
                val peopleJob = async { apiClient.fetchAdminPeople(apiUrl, token) }
                val appsJob = async { apiClient.fetchAdminDriverApplications(apiUrl, token) }
                val casesJob = async { apiClient.fetchAdminSupportCases(apiUrl, token) }
                val payoutsJob = async { apiClient.fetchAdminPayouts(apiUrl, token) }
                val batchesJob = async { apiClient.fetchAdminPayoutBatches(apiUrl, token) }

                overviewJob.await()
                    .onSuccess { _adminOverview.value = it }
                    .onFailure { errors += friendlyApiErrorMessage(it, "Could not load admin overview") }
                activityJob.await()
                    .onSuccess { activityLogDao.replaceAll(it.map { item -> item.toEntity() }) }
                    .onFailure { errors += friendlyApiErrorMessage(it, "Could not load activity log") }
                peopleJob.await()
                    .onSuccess { userDao.replaceAll(it.map { item -> item.toEntity() }) }
                    .onFailure { errors += friendlyApiErrorMessage(it, "Could not load user directory") }
                appsJob.await()
                    .onSuccess { driverDao.replaceAll(it.map { item -> item.toEntity() }) }
                    .onFailure { errors += friendlyApiErrorMessage(it, "Could not load driver applications") }
                casesJob.await()
                    .onSuccess { supportDao.replaceAll(it.map { item -> item.toEntity() }) }
                    .onFailure { errors += friendlyApiErrorMessage(it, "Could not load support queue") }
                payoutsJob.await()
                    .onSuccess { payoutDao.replaceAll(it.map { item -> item.toEntity() }) }
                    .onFailure { errors += friendlyApiErrorMessage(it, "Could not load payouts") }
                batchesJob.await()
                    .onSuccess { payoutBatchDao.replaceAll(it.map { item -> item.toEntity() }) }
                    .onFailure { errors += friendlyApiErrorMessage(it, "Could not load payout batches") }
            }
        }
        errors.firstOrNull()
    }

    // Places & Routing
    suspend fun searchPlaces(query: String, proximity: RideCoordinates?): Result<List<PlaceSearchSuggestion>> {
        val clean = query.trim()
        if (clean.length < 2) {
            _placeSuggestions.value = emptyList()
            _searchingPlaces.value = false
            return Result.success(emptyList())
        }
        val apiUrl = _apiBaseUrl.value.trim().trimEnd('/')
        if (apiUrl.isBlank()) {
            return Result.failure(IllegalStateException("Place search is temporarily unavailable. Please try again shortly."))
        }
        _searchingPlaces.value = true
        return try {
            val token = currentSessionOrNull()?.idToken
            val res = apiClient.searchPlaces(apiUrl, token, clean, proximity)
            res.onSuccess { list -> _placeSuggestions.value = list }
            res
        } finally {
            _searchingPlaces.value = false
        }
    }

    fun clearPlaceSuggestions() {
        _placeSuggestions.value = emptyList()
        _searchingPlaces.value = false
    }

    suspend fun reverseGeocodeLabel(coords: RideCoordinates): Result<String> {
        val apiUrl = _apiBaseUrl.value.trim().trimEnd('/')
        if (apiUrl.isBlank()) {
            return Result.failure(IllegalStateException("Location details are temporarily unavailable. Please try again shortly."))
        }
        val token = currentSessionOrNull()?.idToken
        return apiClient.reverseGeocode(apiUrl, token, coords).map { suggestion ->
            suggestion?.label?.takeIf { it.isNotBlank() }
                ?: LaunchPolicy.nearestLandmarkLabel(coords)
        }
    }

    suspend fun previewRoute(
        pickup: String,
        destination: String,
        pickupCoords: RideCoordinates?,
        destCoords: RideCoordinates?
    ): Result<RideRoute> {
        return withValidToken { apiUrl, token, _ ->
            apiClient.previewRoute(apiUrl, token, pickup, destination, pickupCoords, destCoords)
                .map { route ->
                    val decodedSize = PolylineCodec.decode(route.encodedPolyline).size
                    val origin = route.originCoordinates ?: pickupCoords
                    val dest = route.destinationCoordinates ?: destCoords
                    if (decodedSize <= 2 && origin != null && dest != null) {
                        val visual = apiClient.fetchOsrmVisualRoute(origin, dest)
                        if (visual != null && PolylineCodec.decode(visual.encodedPolyline).size > decodedSize) {
                            route.copy(encodedPolyline = visual.encodedPolyline)
                        } else {
                            route
                        }
                    } else {
                        route
                    }
                }
        }
    }

    suspend fun fetchVisualRoadRoute(
        pickupCoords: RideCoordinates,
        destCoords: RideCoordinates
    ): RideRoute? = apiClient.fetchOsrmVisualRoute(pickupCoords, destCoords)

    // Rider Actions
    suspend fun createRideRequest(
        pickup: String,
        destination: String,
        pickupCoords: RideCoordinates,
        destCoords: RideCoordinates,
        category: RideCategory,
        customOfferFare: Double?,
        pickupAt: Long?,
        paymentMethod: PaymentMethod,
        existingRoute: RideRoute?
    ): Result<RideTrip> {
        return withValidToken { apiUrl, token, _ ->
            val routeResult = if (existingRoute != null && !existingRoute.quoteToken.isNullOrBlank()) {
                Result.success(existingRoute)
            } else {
                apiClient.previewRoute(apiUrl, token, pickup, destination, pickupCoords, destCoords)
            }
            if (routeResult.isFailure) {
                return@withValidToken Result.failure(routeResult.exceptionOrNull()!!)
            }
            val route = routeResult.getOrThrow()
            val quoteToken = route.quoteToken
            if (quoteToken.isNullOrBlank()) {
                return@withValidToken Result.failure(IllegalStateException("Route quote expired. Please preview the route again."))
            }

            val baseEstimate = route.fareEstimate ?: LaunchPolicy.calculateEstimate(route.distanceMeters, category)
            val isOffer = customOfferFare != null && customOfferFare != baseEstimate
            val pricingMode = if (isOffer) PricingMode.OFFER else PricingMode.ESTIMATE
            val fare = (customOfferFare ?: baseEstimate).coerceAtLeast(20.0)

            val created = apiClient.createRide(
                baseUrl = apiUrl,
                token = token,
                pickup = pickup,
                destination = destination,
                category = category,
                pricingMode = pricingMode,
                fare = fare,
                paymentMethod = paymentMethod,
                pickupAt = pickupAt,
                quoteToken = quoteToken
            )
            created.onSuccess { trip ->
                tripDao.upsert(trip.toEntity())
            }
            created
        }
    }

    suspend fun acceptDriverOffer(tripId: String, offerId: String): Result<RideTrip> =
        performRideActionAndCache(tripId, buildJsonObject {
            put("action", "chooseOffer")
            put("offerId", offerId)
        })

    suspend fun flagDriverOffer(tripId: String, offerId: String, reason: String): Result<RideTrip> =
        performRideActionAndCache(tripId, buildJsonObject {
            put("action", "flagOffer")
            put("offerId", offerId)
            put("reason", reason.trim().ifBlank { "Concern flagged by requester" })
        })

    suspend fun riderConfirmArrival(tripId: String): Result<RideTrip> =
        performRideActionAndCache(tripId, buildJsonObject {
            put("action", "confirmArrival")
        })

    suspend fun cancelTrip(tripId: String, reason: String? = null): Result<RideTrip> =
        performRideActionAndCache(tripId, buildJsonObject {
            put("action", "cancel")
            if (!reason.isNullOrBlank()) put("reason", reason.trim())
        }).onSuccess { refreshActiveSessionData() }

    suspend fun expireOpenTrip(tripId: String): Result<RideTrip> =
        performRideActionAndCache(tripId, buildJsonObject {
            put("action", "cancel")
            put("reason", "Request expired — no driver matched")
        }).onSuccess { refreshActiveSessionData() }

    suspend fun rateTrip(tripId: String, stars: Int, feedbackTags: List<String> = emptyList()): Result<RideTrip> =
        performRideActionAndCache(tripId, buildJsonObject {
            put("action", "rate")
            put("rating", stars.coerceIn(1, 5))
        })

    suspend fun sendTripMessage(tripId: String, text: String): Result<RideTrip> {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return Result.failure(IllegalArgumentException("Enter a message."))
        return performRideActionAndCache(tripId, buildJsonObject {
            put("action", "message")
            put("channel", "driver")
            put("message", trimmed)
        })
    }

    suspend fun createSupportCase(trip: RideTrip, category: String, priority: String, message: String): Result<RideTrip> {
        val trimmed = message.trim()
        if (trimmed.isEmpty()) return Result.failure(IllegalArgumentException("Describe the issue before submitting."))
        val safePriority = if (category == "safety") priority else if (priority == "urgent") "high" else priority
        return performRideActionAndCache(trip.id, buildJsonObject {
            put("action", "message")
            put("channel", "support")
            put("category", category)
            put("priority", safePriority)
            put("message", trimmed)
        }).onSuccess {
            refreshActiveSessionData()
        }
    }

    suspend fun sendRiderSupportFollowUp(supportCase: SupportCase, message: String): Result<RideTrip> {
        val trimmed = message.trim()
        if (trimmed.isEmpty()) return Result.failure(IllegalArgumentException("Enter a message."))
        val safePriority = if (supportCase.category == "safety") supportCase.priority else "normal"
        return performRideActionAndCache(supportCase.rideId, buildJsonObject {
            put("action", "message")
            put("channel", "support")
            put("category", supportCase.category)
            put("priority", safePriority)
            put("message", trimmed)
        }).onSuccess {
            refreshActiveSessionData()
        }
    }

    private suspend fun performRideActionAndCache(tripId: String, payload: kotlinx.serialization.json.JsonObject): Result<RideTrip> {
        return withValidToken { apiUrl, token, _ ->
            apiClient.performRideAction(apiUrl, token, tripId, payload).onSuccess { trip ->
                tripDao.upsert(trip.toEntity())
            }
        }
    }

    // Payments & Wallet
    suspend fun initializeCardPayment(tripId: String): Result<PaymentInitOutcome> {
        return withValidToken { apiUrl, token, _ ->
            apiClient.initializePayment(apiUrl, token, tripId).onSuccess {
                refreshActiveSessionData()
            }
        }
    }

    suspend fun submitMockPaymentResult(reference: String, success: Boolean): Result<PaymentVerificationResult> {
        if (!BuildConfig.ALLOW_MOCK_PAYMENTS) {
            return Result.failure(IllegalStateException("Mock payments are disabled in this build."))
        }
        return withValidToken { apiUrl, token, _ ->
            apiClient.submitMockPaymentResult(apiUrl, token, reference, success).also {
                refreshActiveSessionData()
            }
        }
    }

    suspend fun releaseCardHold(tripId: String): Result<Unit> {
        return withValidToken { apiUrl, token, _ ->
            apiClient.releasePaymentHold(apiUrl, token, tripId).onSuccess {
                refreshActiveSessionData()
            }
        }
    }

    suspend fun initializeWalletTopUp(amountZar: Double): Result<PaymentInitOutcome> {
        val amountMinor = (amountZar * 100).toLong()
        if (amountMinor < 500L) {
            return Result.failure(IllegalArgumentException("Minimum top-up is R 5.00."))
        }
        return withValidToken { apiUrl, token, _ ->
            apiClient.initializeWalletTopUp(apiUrl, token, amountMinor)
        }
    }

    suspend fun verifyPaymentReference(reference: String, tripIdHint: String? = null): Result<PaymentVerificationResult> {
        return withValidToken { apiUrl, token, session ->
            when {
                reference.startsWith("wallet-topup-") -> {
                    apiClient.verifyWalletTopUp(apiUrl, token, reference).map { (result, walletSummary) ->
                        _walletBalanceMinor.value = walletSummary.balanceMinor
                        refreshActiveSessionData()
                        result
                    }
                }
                reference.startsWith("driver-subscription-") -> {
                    val sub = apiClient.fetchDriverSubscription(apiUrl, token).getOrNull()
                    _driverSubscription.value = sub
                    refreshActiveSessionData()
                    val state = when (sub?.status?.lowercase()) {
                        "active" -> PaymentVerificationState.PAID
                        "failed", "cancelled", "expired" -> PaymentVerificationState.FAILED
                        else -> PaymentVerificationState.PENDING
                    }
                    Result.success(
                        PaymentVerificationResult(
                            reference = reference,
                            state = state,
                            amountMinor = sub?.amountMinor ?: 0L,
                            message = when (state) {
                                PaymentVerificationState.PAID -> "Your Vhaya Weekly Driver Pass is now active for 7 days."
                                PaymentVerificationState.FAILED -> "Weekly Driver Pass payment was not completed."
                                else -> "Paystack is still confirming your Weekly Driver Pass payment."
                            }
                        )
                    )
                }
                else -> {
                    apiClient.verifyPayment(apiUrl, token, reference).map { res ->
                        refreshRoleScopedData(apiUrl, token, session)
                        res.copy(tripId = res.tripId ?: tripIdHint)
                    }
                }
            }
        }
    }

    // Driver Operations
    suspend fun setDriverOnline(online: Boolean): Result<Boolean> {
        return withValidToken { apiUrl, token, _ ->
            apiClient.setDriverOnline(apiUrl, token, online).onSuccess {
                refreshActiveSessionData()
            }
        }
    }

    suspend fun saveDriverApplication(updated: DriverApplication, submitForReview: Boolean): Result<DriverApplication> {
        val formattedReg = LaunchPolicy.formatSouthAfricanRegistration(updated.registration)
        val normalized = updated.copy(registration = formattedReg)
        return withValidToken { apiUrl, token, session ->
            apiClient.saveDriverApplication(
                baseUrl = apiUrl,
                token = token,
                app = normalized,
                submit = submitForReview,
                fallbackName = session.displayName,
                fallbackEmail = session.email
            ).onSuccess { saved ->
                driverDao.upsert(saved.toEntity())
            }
        }
    }

    suspend fun uploadDriverDocument(
        draft: DriverApplication?,
        docType: DriverDocumentType,
        uri: Uri
    ): Result<DriverApplication> {
        val payloadResult = withContext(Dispatchers.IO) {
            runCatching {
                val rawBytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: error("Unable to read selected file.")
                if (rawBytes.isEmpty()) error("Selected file is empty.")

                val reportedMime = context.contentResolver.getType(uri)
                val (finalBytes, finalMime) = prepareUploadBytesAndMime(rawBytes, reportedMime)
                val fileName = resolveFileName(uri, finalMime, docType)
                Triple(fileName, finalMime, finalBytes)
            }
        }
        if (payloadResult.isFailure) return Result.failure(payloadResult.exceptionOrNull()!!)
        val (fileName, mimeType, bytes) = payloadResult.getOrThrow()

        return withValidToken { apiUrl, token, session ->
            // Best-effort draft sync before upload without blocking on partial form validation
            val localExisting = driverDao.getById(session.userId)?.toDomain()
            val remoteExisting = if (localExisting == null) {
                apiClient.fetchDriverProfile(apiUrl, token, session.displayName, session.email).getOrNull()
                    ?.also { driverDao.upsert(it.toEntity()) }
            } else {
                localExisting
            }

            var draftFailure: Throwable? = null
            val hasDraftDetails = draft != null && (
                draft.contactPhone.isNotBlank() ||
                    draft.city.isNotBlank() ||
                    draft.vehicle.isNotBlank() ||
                    draft.registration.isNotBlank()
                )
            if (remoteExisting == null || (remoteExisting.applicationStatus == "draft" && hasDraftDetails)) {
                val draftApp = (draft ?: DriverApplication(
                    userId = session.userId,
                    displayName = session.displayName,
                    email = session.email
                )).let {
                    it.copy(registration = LaunchPolicy.formatSouthAfricanRegistration(it.registration))
                }
                val saveDraft = apiClient.saveDriverApplication(
                    baseUrl = apiUrl,
                    token = token,
                    app = draftApp,
                    submit = false,
                    fallbackName = session.displayName,
                    fallbackEmail = session.email
                )
                saveDraft.onSuccess { driverDao.upsert(it.toEntity()) }
                saveDraft.onFailure { draftFailure = it }
            }

            val uploadResult = apiClient.uploadDriverDocument(
                baseUrl = apiUrl,
                token = token,
                docType = docType,
                fileName = fileName,
                contentType = mimeType,
                bytes = bytes,
                fallbackName = session.displayName,
                fallbackEmail = session.email
            )
            uploadResult.onSuccess { updatedProfile ->
                driverDao.upsert(updatedProfile.toEntity())
            }
            if (uploadResult.isFailure && remoteExisting == null && draftFailure != null) {
                val uploadErr = uploadResult.exceptionOrNull()
                if (uploadErr is ApiHttpException && uploadErr.statusCode == 401) {
                    uploadResult
                } else {
                    Result.failure(uploadErr ?: draftFailure!!)
                }
            } else {
                uploadResult
            }
        }
    }

    suspend fun driverSubmitOffer(trip: RideTrip, amount: Double, driverLocation: RideCoordinates?): Result<RideTrip> {
        val payload = if (amount == trip.fare) {
            buildJsonObject {
                put("action", "accept")
                if (driverLocation != null) {
                    put("location", buildJsonObject {
                        put("latitude", driverLocation.latitude)
                        put("longitude", driverLocation.longitude)
                    })
                }
            }
        } else {
            buildJsonObject {
                put("action", "counter")
                put("amount", amount.coerceAtLeast(20.0))
            }
        }
        return performRideActionAndCache(trip.id, payload)
    }

    suspend fun updateLiveDriverLocation(tripId: String, coords: RideCoordinates): Result<Unit> {
        return withValidToken { apiUrl, token, _ ->
            apiClient.updateDriverLocation(apiUrl, token, tripId, coords).onSuccess {
                refreshActiveSessionData()
            }
        }
    }

    suspend fun driverMarkArrived(tripId: String, driverLocation: RideCoordinates?): Result<RideTrip> {
        return withValidToken { apiUrl, token, _ ->
            if (driverLocation != null) {
                val locRes = apiClient.updateDriverLocation(apiUrl, token, tripId, driverLocation)
                if (locRes.isFailure) return@withValidToken Result.failure(locRes.exceptionOrNull()!!)
            }
            apiClient.performRideAction(
                apiUrl,
                token,
                tripId,
                buildJsonObject { put("action", "advance") }
            ).onSuccess { trip ->
                tripDao.upsert(trip.toEntity())
            }
        }
    }

    suspend fun verifyPinAndStartTrip(tripId: String, enteredPin: String): Result<RideTrip> {
        val cleanPin = enteredPin.trim()
        if (cleanPin.length != 6) {
            return Result.failure(IllegalArgumentException("Enter the rider's 6-digit pickup PIN."))
        }
        return performRideActionAndCache(tripId, buildJsonObject {
            put("action", "advance")
            put("pin", cleanPin)
        })
    }

    suspend fun completeTrip(tripId: String): Result<RideTrip> =
        performRideActionAndCache(tripId, buildJsonObject {
            put("action", "advance")
        }).onSuccess { refreshActiveSessionData() }

    suspend fun initializeDriverWeeklySubscription(amountMinor: Long = 19900L): Result<PaymentInitOutcome> {
        return withValidToken { apiUrl, token, _ ->
            apiClient.initializeDriverSubscription(apiUrl, token, amountMinor).onSuccess {
                refreshActiveSessionData()
            }
        }
    }

    // Admin Actions
    suspend fun updateComplianceCheck(userId: String, docType: DriverDocumentType, status: ComplianceStatus, note: String?): Result<Unit> {
        return withValidToken { apiUrl, token, _ ->
            apiClient.updateComplianceCheck(apiUrl, token, userId, docType, status, note).onSuccess {
                refreshActiveSessionData()
            }
        }
    }

    suspend fun decideDriverApplication(userId: String, decision: String, staffNote: String?): Result<Unit> {
        return withValidToken { apiUrl, token, _ ->
            apiClient.decideDriverApplication(apiUrl, token, userId, decision, staffNote).onSuccess {
                refreshActiveSessionData()
            }
        }
    }

    suspend fun updateUserRoleOrSuspension(userId: String, role: RideRole? = null, suspend: Boolean? = null, reason: String? = null): Result<Unit> {
        return withValidToken { apiUrl, token, _ ->
            if (role != null) {
                val res = apiClient.updateUserRole(apiUrl, token, userId, role)
                if (res.isFailure) return@withValidToken res
            }
            if (suspend != null) {
                val res = apiClient.updateUserSuspension(apiUrl, token, userId, suspend, reason)
                if (res.isFailure) return@withValidToken res
            }
            refreshActiveSessionData()
            Result.success(Unit)
        }
    }

    suspend fun updateSupportCaseStatus(caseId: Long, status: String, priority: String? = null): Result<Unit> {
        return withValidToken { apiUrl, token, _ ->
            apiClient.updateAdminSupportCase(apiUrl, token, caseId, status, priority).onSuccess {
                refreshActiveSessionData()
            }
        }
    }

    suspend fun addAdminSupportReply(caseId: Long, message: String): Result<Unit> {
        val trimmed = message.trim()
        if (trimmed.isEmpty()) return Result.failure(IllegalArgumentException("Enter a reply."))
        return withValidToken { apiUrl, token, _ ->
            apiClient.replyAdminSupportCase(apiUrl, token, caseId, trimmed).onSuccess {
                refreshActiveSessionData()
            }
        }
    }

    suspend fun recordPayout(
        driverId: String,
        provider: String,
        externalReference: String,
        amountZar: Double,
        periodStart: String?,
        periodEnd: String?,
        note: String?
    ): Result<Unit> {
        val amountMinor = (amountZar * 100).toLong()
        if (amountMinor <= 0L) return Result.failure(IllegalArgumentException("Enter a valid payout amount."))
        val cleanRef = externalReference.trim()
        if (!Regex("^[A-Za-z0-9._:-]{3,128}$").matches(cleanRef)) {
            return Result.failure(IllegalArgumentException("Reference must be 3–128 characters (letters, digits, ., _, :, -)."))
        }
        return withValidToken { apiUrl, token, _ ->
            apiClient.recordAdminPayout(
                baseUrl = apiUrl,
                token = token,
                driverId = driverId,
                provider = provider,
                externalReference = cleanRef,
                amountMinor = amountMinor,
                periodStart = periodStart?.trim()?.takeIf { it.isNotEmpty() },
                periodEnd = periodEnd?.trim()?.takeIf { it.isNotEmpty() },
                note = note
            ).onSuccess {
                refreshActiveSessionData()
            }
        }
    }

    suspend fun reconcilePayout(payoutId: Long, status: String, note: String? = null): Result<Unit> {
        return withValidToken { apiUrl, token, _ ->
            apiClient.reconcileAdminPayout(apiUrl, token, payoutId, status, note).onSuccess {
                refreshActiveSessionData()
            }
        }
    }

    suspend fun runWeeklyPayoutBatch(periodStartIso: String, periodEndIso: String): Result<Unit> {
        return withValidToken { apiUrl, token, _ ->
            apiClient.runWeeklyPayoutBatch(apiUrl, token, periodStartIso.trim(), periodEndIso.trim()).onSuccess {
                refreshActiveSessionData()
            }
        }
    }

    suspend fun updatePayoutBatchStatus(batchId: String, nextStatus: String): Result<Unit> {
        return withValidToken { apiUrl, token, _ ->
            apiClient.updateAdminPayoutBatchStatus(apiUrl, token, batchId, nextStatus).onSuccess {
                refreshActiveSessionData()
            }
        }
    }

    // Personal Local Helpers (Saved Places & Trusted Contacts)
    suspend fun toggleSavedDestination(label: String, coordinates: RideCoordinates) {
        val id = "saved-${label.lowercase().replace(Regex("[^a-z0-9]+"), "-").take(24)}"
        savedDao.upsert(
            SavedDestination(
                id = id,
                label = label,
                latitude = coordinates.latitude,
                longitude = coordinates.longitude,
                savedAt = System.currentTimeMillis()
            ).toEntity()
        )
    }

    suspend fun deleteSavedDestination(id: String) = savedDao.deleteById(id)

    suspend fun addTrustedContact(name: String, phone: String) {
        if (name.isBlank() || phone.isBlank()) return
        contactDao.upsert(TrustedContact("tc-${UUID.randomUUID().toString().take(6)}", name.trim(), phone.trim()).toEntity())
    }

    suspend fun removeTrustedContact(id: String) = contactDao.deleteById(id)

    private fun prepareUploadBytesAndMime(rawBytes: ByteArray, reportedMime: String?): Pair<ByteArray, String> {
        val directMime = detectSupportedMime(rawBytes, reportedMime)
        if (directMime == "application/pdf") {
            if (rawBytes.size > 8 * 1024 * 1024) {
                error("PDF exceeds the 8 MB upload limit.")
            }
            return rawBytes to "application/pdf"
        }
        if ((directMime == "image/jpeg" || directMime == "image/png") && rawBytes.size <= 4 * 1024 * 1024) {
            return rawBytes to directMime
        }

        // Convert HEIC/WebP or oversized photos into a clean JPEG under 4 MB
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, bounds)
        if (bounds.outWidth > 0 && bounds.outHeight > 0) {
            var sampleSize = 1
            val maxDim = 2048
            while ((bounds.outWidth / sampleSize) > maxDim || (bounds.outHeight / sampleSize) > maxDim) {
                sampleSize *= 2
            }
            val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            val bitmap = BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, decodeOpts)
            if (bitmap != null) {
                try {
                    val out = ByteArrayOutputStream()
                    var quality = 88
                    bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
                    while (out.size() > 4 * 1024 * 1024 && quality > 50) {
                        out.reset()
                        quality -= 12
                        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
                    }
                    val jpegBytes = out.toByteArray()
                    if (jpegBytes.isNotEmpty() && jpegBytes.size <= 8 * 1024 * 1024) {
                        return jpegBytes to "image/jpeg"
                    }
                } finally {
                    bitmap.recycle()
                }
            }
        }

        if (directMime != null) {
            if (rawBytes.size > 8 * 1024 * 1024) {
                error("Document exceeds the 8 MB upload limit.")
            }
            return rawBytes to directMime
        }
        error("Unsupported file type. Upload a JPG, PNG, or PDF file.")
    }

    private fun detectSupportedMime(bytes: ByteArray, reportedMime: String?): String? {
        if (bytes.size >= 4 &&
            bytes[0] == 0x25.toByte() && bytes[1] == 0x50.toByte() &&
            bytes[2] == 0x44.toByte() && bytes[3] == 0x46.toByte()
        ) {
            return "application/pdf"
        }
        if (bytes.size >= 8 &&
            bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() &&
            bytes[2] == 0x4E.toByte() && bytes[3] == 0x47.toByte()
        ) {
            return "image/png"
        }
        if (bytes.size >= 3 &&
            bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() && bytes[2] == 0xFF.toByte()
        ) {
            return "image/jpeg"
        }
        return when (reportedMime?.lowercase()) {
            "application/pdf" -> "application/pdf"
            "image/png" -> "image/png"
            "image/jpeg", "image/jpg" -> "image/jpeg"
            else -> null
        }
    }

    private fun resolveFileName(uri: Uri, mimeType: String, docType: DriverDocumentType): String {
        val queried = runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        }.getOrNull()
        val ext = when (mimeType) {
            "application/pdf" -> "pdf"
            "image/png" -> "png"
            else -> "jpg"
        }
        if (!queried.isNullOrBlank()) {
            val sanitized = queried.replace(Regex("[^A-Za-z0-9._-]"), "_")
            if (mimeType == "image/jpeg" && !sanitized.endsWith(".jpg", ignoreCase = true) && !sanitized.endsWith(".jpeg", ignoreCase = true)) {
                return "${sanitized.substringBeforeLast('.')}.$ext"
            }
            return sanitized
        }
        return "${docType.key}.$ext"
    }
}
