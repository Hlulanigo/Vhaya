package com.wayfare.ride.data

import com.wayfare.ride.domain.ActivityLogEntry
import com.wayfare.ride.domain.AdminOverviewSummary
import com.wayfare.ride.domain.ComplianceStatus
import com.wayfare.ride.domain.DriverApplication
import com.wayfare.ride.domain.DriverComplianceCheck
import com.wayfare.ride.domain.DriverDocumentType
import com.wayfare.ride.domain.DriverEarningsSummary
import com.wayfare.ride.domain.DriverPayoutRecord
import com.wayfare.ride.domain.DriverSubscriptionSummary
import com.wayfare.ride.domain.PaymentInitOutcome
import com.wayfare.ride.domain.PaymentMethod
import com.wayfare.ride.domain.PaymentStatus
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
import com.wayfare.ride.domain.SupportCase
import com.wayfare.ride.domain.UserAccountSummary
import com.wayfare.ride.domain.UserSession
import com.wayfare.ride.domain.WalletSummary
import com.wayfare.ride.domain.WalletTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.SerializationException
import java.io.BufferedReader
import java.io.IOException
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.URLEncoder
import java.net.UnknownHostException
import javax.net.ssl.SSLException
import kotlin.math.roundToInt

class ApiHttpException(val statusCode: Int, message: String) : Exception(message)

fun friendlyApiErrorMessage(error: Throwable?, fallback: String = "Something went wrong. Please try again."): String {
    if (error == null) return fallback
    return when (error) {
        is UnknownHostException -> "No internet connection. Check your network and try again."
        is SocketTimeoutException -> "The request timed out. Please try again."
        is ConnectException -> "Unable to reach Vhaya right now. Please try again shortly."
        is SSLException -> "Secure connection failed. Check your device date/time and network."
        is SerializationException -> "Received an unexpected response from Vhaya. Please try again."
        is ApiHttpException -> {
            val raw = error.message.orEmpty().trim()
            when {
                error.statusCode == 401 -> "Your session expired. Sign in again."
                error.statusCode == 403 && raw.startsWith("Request failed") -> "You don't have permission for this action."
                error.statusCode == 404 && raw.startsWith("Request failed") -> "This item is no longer available."
                error.statusCode == 429 && raw.startsWith("Request failed") -> "Too many requests. Please wait a moment and try again."
                error.statusCode >= 500 && (raw.isBlank() || raw.startsWith("Request failed")) ->
                    "Vhaya servers are temporarily unavailable. Please try again shortly."
                raw.isNotBlank() -> raw
                else -> fallback
            }
        }
        is IOException -> {
            val msg = error.message.orEmpty()
            if (msg.contains("Unable to resolve host", ignoreCase = true) ||
                msg.contains("No address associated", ignoreCase = true)
            ) {
                "No internet connection. Check your network and try again."
            } else {
                msg.ifBlank { "Network error. Please check your connection and try again." }
            }
        }
        else -> error.message?.takeIf { it.isNotBlank() } ?: fallback
    }
}

fun Throwable.toFriendlyApiException(): Throwable = when (this) {
    is ApiHttpException -> ApiHttpException(statusCode, friendlyApiErrorMessage(this))
    is UnknownHostException,
    is SocketTimeoutException,
    is ConnectException,
    is SSLException,
    is SerializationException,
    is IOException -> IOException(friendlyApiErrorMessage(this), this)
    else -> this
}

class VhayaApiClient {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        coerceInputValues = true
    }

    @Serializable
    private data class FirebaseAuthResponse(
        val localId: String = "",
        val email: String = "",
        val displayName: String = "",
        val idToken: String = "",
        val refreshToken: String = "",
        val expiresIn: String = "3600",
        val error: FirebaseErrorDetail? = null
    )

    @Serializable
    private data class FirebaseRefreshResponse(
        val id_token: String = "",
        val refresh_token: String = "",
        val expires_in: String = "3600",
        val user_id: String = "",
        val error: FirebaseErrorDetail? = null
    )

    @Serializable
    private data class FirebaseErrorDetail(
        val message: String = ""
    )

    @Serializable
    private data class RidesEnvelope(
        val role: String = "rider",
        val trips: List<RideTrip> = emptyList()
    )

    @Serializable
    private data class SingleTripEnvelope(
        val trip: RideTrip
    )

    @Serializable
    private data class PlacesSearchEnvelope(
        val places: List<PlaceSearchSuggestion> = emptyList()
    )

    @Serializable
    private data class ReversePlaceDto(
        val label: String,
        val latitude: Double,
        val longitude: Double
    )

    @Serializable
    private data class ReversePlaceEnvelope(
        val place: ReversePlaceDto? = null
    )

    @Serializable
    private data class WalletEnvelope(
        val wallet: WalletSummary = WalletSummary(),
        val transactions: List<WalletTransaction> = emptyList()
    )

    @Serializable
    private data class WalletVerifyEnvelope(
        val wallet: WalletSummary = WalletSummary()
    )

    @Serializable
    private data class UploadedDocumentMeta(
        val documentType: String = "",
        val type: String = ""
    ) {
        fun resolvedType(): String = documentType.ifBlank { type }
    }

    @Serializable
    private data class DriverProfileDto(
        val userId: String,
        val displayName: String? = null,
        val email: String? = null,
        val contactPhone: String? = null,
        val city: String? = null,
        val applicationStatus: String = "draft",
        val verificationStatus: String = "unverified",
        val vehicle: String? = null,
        val vehicleColor: String? = null,
        val registration: String? = null,
        val licenceExpiry: String? = null,
        val operatingLicenceReference: String? = null,
        val isOnline: Boolean = false,
        val staffNote: String? = null,
        val submittedAt: Long? = null,
        val reviewedAt: Long? = null,
        val documents: List<UploadedDocumentMeta> = emptyList(),
        val complianceChecks: List<DriverComplianceCheck> = emptyList()
    ) {
        fun toDomain(fallbackName: String, fallbackEmail: String?): DriverApplication = DriverApplication(
            userId = userId,
            displayName = displayName?.takeIf { it.isNotBlank() } ?: fallbackName,
            email = email ?: fallbackEmail,
            contactPhone = contactPhone.orEmpty(),
            city = city.orEmpty(),
            applicationStatus = applicationStatus,
            verificationStatus = verificationStatus,
            vehicle = vehicle.orEmpty(),
            vehicleColor = vehicleColor.orEmpty(),
            registration = registration.orEmpty(),
            licenceExpiry = licenceExpiry.orEmpty(),
            operatingLicenceReference = operatingLicenceReference.orEmpty(),
            isOnline = isOnline,
            documentsUploaded = documents.map { it.resolvedType() }.filter { it.isNotBlank() }.distinct(),
            documentsConsent = submittedAt != null || applicationStatus != "draft",
            staffNote = staffNote,
            submittedAt = submittedAt,
            reviewedAt = reviewedAt,
            complianceChecks = complianceChecks.map { check ->
                if (check.userId.isBlank()) check.copy(userId = userId) else check
            }
        )
    }

    @Serializable
    private data class DriverProfileEnvelope(
        val profile: DriverProfileDto? = null,
        val application: DriverProfileDto? = null
    ) {
        fun resolvedProfile(): DriverProfileDto? = profile ?: application
    }

    @Serializable
    private data class AdminDriverApplicationsEnvelope(
        val applications: List<DriverProfileDto> = emptyList()
    )

    @Serializable
    private data class DriverSubscriptionEnvelope(
        val subscription: DriverSubscriptionSummary? = null
    )

    @Serializable
    private data class SupportCasesEnvelope(
        val cases: List<SupportCase> = emptyList()
    )

    @Serializable
    private data class AdminOverviewEnvelope(
        val summary: AdminOverviewSummary = AdminOverviewSummary()
    )

    @Serializable
    private data class AdminActivityEnvelope(
        val activity: List<ActivityLogEntry> = emptyList()
    )

    @Serializable
    private data class AdminPeopleEnvelope(
        val people: List<UserAccountSummary> = emptyList()
    )

    @Serializable
    private data class AdminPayoutsEnvelope(
        val payouts: List<DriverPayoutRecord> = emptyList()
    )

    @Serializable
    private data class AdminBatchesEnvelope(
        val batches: List<PayoutBatchRecord> = emptyList()
    )

    private fun cleanUrl(baseUrl: String): String {
        val trimmed = baseUrl.trim().trimEnd('/')
        return if (trimmed.endsWith("/api", ignoreCase = true)) {
            trimmed.dropLast(4).trimEnd('/')
        } else {
            trimmed
        }
    }

    private fun enc(segment: String): String = URLEncoder.encode(segment, "UTF-8")

    suspend fun firebaseSignIn(
        firebaseApiKey: String,
        email: String,
        password: String
    ): Result<UserSession> = withContext(Dispatchers.IO) {
        if (firebaseApiKey.isBlank()) {
            return@withContext Result.failure(IllegalStateException("Vhaya sign-in is temporarily unavailable. Please try again shortly."))
        }
        runCatching {
            val payload = buildJsonObject {
                put("email", email.trim())
                put("password", password)
                put("returnSecureToken", true)
            }.toString()
            val (code, raw) = executeRequest(
                "https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=$firebaseApiKey",
                method = "POST",
                body = payload
            )
            val parsed = json.decodeFromString<FirebaseAuthResponse>(raw)
            if (code !in 200..299 || parsed.idToken.isBlank()) {
                error(friendlyFirebaseError(parsed.error?.message ?: "Sign-in failed ($code)"))
            }
            val verified = lookupEmailVerified(firebaseApiKey, parsed.idToken)
            val ttlMs = (parsed.expiresIn.toLongOrNull() ?: 3600L) * 1000L
            UserSession(
                userId = parsed.localId,
                displayName = parsed.displayName.ifBlank { parsed.email.substringBefore("@") },
                email = parsed.email,
                role = RideRole.RIDER,
                emailVerified = verified,
                idToken = parsed.idToken,
                refreshToken = parsed.refreshToken,
                expiresAtEpochMs = System.currentTimeMillis() + ttlMs
            )
        }
    }

    suspend fun firebaseSignUp(
        firebaseApiKey: String,
        displayName: String,
        email: String,
        password: String
    ): Result<UserSession> = withContext(Dispatchers.IO) {
        if (firebaseApiKey.isBlank()) {
            return@withContext Result.failure(IllegalStateException("Vhaya sign-in is temporarily unavailable. Please try again shortly."))
        }
        runCatching {
            val payload = buildJsonObject {
                put("email", email.trim())
                put("password", password)
                put("returnSecureToken", true)
            }.toString()
            val (code, raw) = executeRequest(
                "https://identitytoolkit.googleapis.com/v1/accounts:signUp?key=$firebaseApiKey",
                method = "POST",
                body = payload
            )
            val parsed = json.decodeFromString<FirebaseAuthResponse>(raw)
            if (code !in 200..299 || parsed.idToken.isBlank()) {
                error(friendlyFirebaseError(parsed.error?.message ?: "Account creation failed"))
            }
            val updatePayload = buildJsonObject {
                put("idToken", parsed.idToken)
                put("displayName", displayName.trim())
                put("returnSecureToken", true)
            }.toString()
            executeRequest(
                "https://identitytoolkit.googleapis.com/v1/accounts:update?key=$firebaseApiKey",
                method = "POST",
                body = updatePayload
            )
            sendEmailVerification(firebaseApiKey, parsed.idToken)
            val ttlMs = (parsed.expiresIn.toLongOrNull() ?: 3600L) * 1000L
            UserSession(
                userId = parsed.localId,
                displayName = displayName.trim().ifBlank { parsed.email.substringBefore("@") },
                email = parsed.email,
                role = RideRole.RIDER,
                emailVerified = false,
                idToken = parsed.idToken,
                refreshToken = parsed.refreshToken,
                expiresAtEpochMs = System.currentTimeMillis() + ttlMs
            )
        }
    }

    suspend fun refreshIdToken(firebaseApiKey: String, refreshToken: String): Result<UserSessionRefresh> =
        withContext(Dispatchers.IO) {
            if (firebaseApiKey.isBlank() || refreshToken.isBlank()) {
                return@withContext Result.failure(IllegalStateException("Session expired. Sign in again."))
            }
            runCatching {
                val formBody = "grant_type=refresh_token&refresh_token=${enc(refreshToken)}"
                val (code, raw) = executeRequest(
                    urlString = "https://securetoken.googleapis.com/v1/token?key=$firebaseApiKey",
                    method = "POST",
                    body = formBody,
                    contentType = "application/x-www-form-urlencoded"
                )
                val parsed = json.decodeFromString<FirebaseRefreshResponse>(raw)
                if (code !in 200..299 || parsed.id_token.isBlank()) {
                    throw ApiHttpException(401, friendlyFirebaseError(parsed.error?.message ?: "Session expired. Sign in again."))
                }
                val ttlMs = (parsed.expires_in.toLongOrNull() ?: 3600L) * 1000L
                UserSessionRefresh(
                    idToken = parsed.id_token,
                    refreshToken = parsed.refresh_token.ifBlank { refreshToken },
                    expiresAtEpochMs = System.currentTimeMillis() + ttlMs
                )
            }
        }

    data class UserSessionRefresh(
        val idToken: String,
        val refreshToken: String,
        val expiresAtEpochMs: Long
    )

    suspend fun sendPasswordReset(firebaseApiKey: String, email: String): Result<Unit> = withContext(Dispatchers.IO) {
        if (firebaseApiKey.isBlank()) {
            return@withContext Result.failure(IllegalStateException("Vhaya sign-in is temporarily unavailable. Please try again shortly."))
        }
        runCatching {
            val payload = buildJsonObject {
                put("requestType", "PASSWORD_RESET")
                put("email", email.trim())
            }.toString()
            val (code, raw) = executeRequest(
                "https://identitytoolkit.googleapis.com/v1/accounts:sendOobCode?key=$firebaseApiKey",
                method = "POST",
                body = payload
            )
            if (code !in 200..299) {
                error("Could not send password reset email (${extractApiError(code, raw)})")
            }
        }
    }

    suspend fun sendEmailVerification(firebaseApiKey: String, idToken: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val payload = buildJsonObject {
                put("requestType", "VERIFY_EMAIL")
                put("idToken", idToken)
            }.toString()
            val (code, raw) = executeRequest(
                "https://identitytoolkit.googleapis.com/v1/accounts:sendOobCode?key=$firebaseApiKey",
                method = "POST",
                body = payload
            )
            if (code !in 200..299) error(extractApiError(code, raw))
        }
    }

    suspend fun lookupEmailVerified(firebaseApiKey: String, idToken: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val payload = buildJsonObject { put("idToken", idToken) }.toString()
            val (code, raw) = executeRequest(
                "https://identitytoolkit.googleapis.com/v1/accounts:lookup?key=$firebaseApiKey",
                method = "POST",
                body = payload
            )
            if (code !in 200..299) return@runCatching false
            val root = json.parseToJsonElement(raw).jsonObject
            val users = root["users"]?.jsonArray
            val first = users?.firstOrNull()?.jsonObject
            first?.get("emailVerified")?.jsonPrimitive?.content == "true"
        }.getOrDefault(false)
    }

    suspend fun searchPlaces(
        baseUrl: String,
        token: String?,
        query: String,
        proximity: RideCoordinates? = null
    ): Result<List<PlaceSearchSuggestion>> = withContext(Dispatchers.IO) {
        runCatching {
            val payload = buildJsonObject {
                put("query", query.trim())
                if (proximity != null) {
                    put("proximity", buildJsonObject {
                        put("latitude", proximity.latitude)
                        put("longitude", proximity.longitude)
                    })
                }
            }.toString()
            val (code, raw) = executeRequest(
                "${cleanUrl(baseUrl)}/api/places/search",
                method = "POST",
                body = payload,
                bearerToken = token
            )
            ensureSuccess(code, raw)
            json.decodeFromString<PlacesSearchEnvelope>(raw).places
        }
    }

    suspend fun reverseGeocode(
        baseUrl: String,
        token: String?,
        coords: RideCoordinates
    ): Result<PlaceSearchSuggestion?> = withContext(Dispatchers.IO) {
        runCatching {
            val payload = buildJsonObject {
                put("coordinates", buildJsonObject {
                    put("latitude", coords.latitude)
                    put("longitude", coords.longitude)
                })
            }.toString()
            val (code, raw) = executeRequest(
                "${cleanUrl(baseUrl)}/api/places/reverse",
                method = "POST",
                body = payload,
                bearerToken = token
            )
            ensureSuccess(code, raw)
            val place = json.decodeFromString<ReversePlaceEnvelope>(raw).place
            place?.let {
                PlaceSearchSuggestion(
                    id = "rev-${it.latitude}-${it.longitude}",
                    label = it.label,
                    latitude = it.latitude,
                    longitude = it.longitude
                )
            }
        }
    }

    suspend fun previewRoute(
        baseUrl: String,
        token: String,
        pickup: String,
        destination: String,
        pickupCoords: RideCoordinates?,
        destCoords: RideCoordinates?
    ): Result<RideRoute> = withContext(Dispatchers.IO) {
        runCatching {
            val payload = buildJsonObject {
                put("pickup", pickup.trim())
                put("destination", destination.trim())
                if (pickupCoords != null) {
                    put("pickupCoordinates", buildJsonObject {
                        put("latitude", pickupCoords.latitude)
                        put("longitude", pickupCoords.longitude)
                    })
                }
                if (destCoords != null) {
                    put("destinationCoordinates", buildJsonObject {
                        put("latitude", destCoords.latitude)
                        put("longitude", destCoords.longitude)
                    })
                }
            }.toString()
            val (code, raw) = executeRequest(
                "${cleanUrl(baseUrl)}/api/routes/preview",
                method = "POST",
                body = payload,
                bearerToken = token
            )
            ensureSuccess(code, raw)
            parseRoutePreviewResponse(raw, pickupCoords, destCoords)
        }
    }

    suspend fun fetchOsrmVisualRoute(
        pickupCoords: RideCoordinates,
        destCoords: RideCoordinates
    ): RideRoute? = withContext(Dispatchers.IO) {
        runCatching {
            val osrmUrl = "https://router.project-osrm.org/route/v1/driving/" +
                "${pickupCoords.longitude},${pickupCoords.latitude};" +
                "${destCoords.longitude},${destCoords.latitude}" +
                "?overview=full&geometries=polyline"
            val (code, raw) = executeRequest(
                urlString = osrmUrl,
                method = "GET"
            )
            if (code !in 200..299 || raw.isBlank()) return@runCatching null
            val root = json.parseToJsonElement(raw).jsonObject
            val firstRoute = root["routes"]?.jsonArray?.firstOrNull()?.jsonObject ?: return@runCatching null
            val geometry = (firstRoute["geometry"] as? JsonPrimitive)?.contentOrNull.orEmpty()
            if (geometry.isBlank()) return@runCatching null
            val distanceMeters = (firstRoute["distance"] as? JsonPrimitive)?.doubleOrNull?.roundToInt() ?: 0
            val durationSeconds = (firstRoute["duration"] as? JsonPrimitive)?.doubleOrNull?.roundToInt() ?: 0
            RideRoute(
                distanceMeters = distanceMeters,
                durationSeconds = durationSeconds,
                encodedPolyline = geometry,
                provider = "osrm-visual",
                originCoordinates = pickupCoords,
                destinationCoordinates = destCoords,
                fareEstimate = null,
                quoteToken = null
            )
        }.getOrNull()
    }

    private fun parseRoutePreviewResponse(
        raw: String,
        fallbackPickup: RideCoordinates?,
        fallbackDest: RideCoordinates?
    ): RideRoute {
        val root = json.parseToJsonElement(raw).jsonObject
        val routeObj = (root["route"] as? JsonObject) ?: root

        val distanceMeters = ((routeObj["distanceMeters"] ?: root["distanceMeters"]) as? JsonPrimitive)
            ?.doubleOrNull?.roundToInt() ?: 0
        val durationSeconds = ((routeObj["durationSeconds"] ?: root["durationSeconds"]) as? JsonPrimitive)
            ?.doubleOrNull?.roundToInt() ?: 0

        val encodedPolyline = extractEncodedPolyline(routeObj)
            ?: extractEncodedPolyline(root)
            ?: ""

        val provider = ((routeObj["provider"] ?: root["provider"]) as? JsonPrimitive)
            ?.contentOrNull?.takeIf { it.isNotBlank() } ?: "osrm"

        val fareEstimate = ((routeObj["fareEstimate"] ?: root["fareEstimate"]
            ?: routeObj["estimate"] ?: root["estimate"]
            ?: routeObj["fare"] ?: root["fare"]) as? JsonPrimitive)?.doubleOrNull

        val quoteToken = ((routeObj["quoteToken"] ?: root["quoteToken"]) as? JsonPrimitive)
            ?.contentOrNull?.takeIf { it.isNotBlank() }

        val originCoords = parseCoordinates(
            routeObj["originCoordinates"] ?: routeObj["pickupCoordinates"]
                ?: root["originCoordinates"] ?: root["pickupCoordinates"]
        ) ?: fallbackPickup

        val destCoords = parseCoordinates(
            routeObj["destinationCoordinates"] ?: root["destinationCoordinates"]
        ) ?: fallbackDest

        return RideRoute(
            distanceMeters = distanceMeters,
            durationSeconds = durationSeconds,
            encodedPolyline = encodedPolyline,
            provider = provider,
            originCoordinates = originCoords,
            destinationCoordinates = destCoords,
            fareEstimate = fareEstimate,
            quoteToken = quoteToken
        )
    }

    private fun extractEncodedPolyline(obj: JsonObject): String? {
        val direct = (obj["encodedPolyline"] as? JsonPrimitive)?.contentOrNull
            ?: (obj["polyline"] as? JsonPrimitive)?.contentOrNull
        if (!direct.isNullOrBlank()) return direct

        val geomElem = obj["geometry"]
        if (geomElem is JsonPrimitive) {
            return geomElem.contentOrNull?.takeIf { it.isNotBlank() }
        }
        val coordsElem = (geomElem as? JsonObject)?.get("coordinates") ?: obj["coordinates"]
        val coordsArray = coordsElem as? JsonArray ?: return null
        val points = coordsArray.mapNotNull { item ->
            val pair = item as? JsonArray ?: return@mapNotNull null
            if (pair.size < 2) return@mapNotNull null
            val lon = (pair[0] as? JsonPrimitive)?.doubleOrNull ?: return@mapNotNull null
            val lat = (pair[1] as? JsonPrimitive)?.doubleOrNull ?: return@mapNotNull null
            RideCoordinates(latitude = lat, longitude = lon)
        }
        return if (points.size >= 2) PolylineCodec.encode(points) else null
    }

    private fun parseCoordinates(element: kotlinx.serialization.json.JsonElement?): RideCoordinates? {
        val obj = element as? JsonObject ?: return null
        val lat = (obj["latitude"] ?: obj["lat"])?.let { (it as? JsonPrimitive)?.doubleOrNull } ?: return null
        val lon = (obj["longitude"] ?: obj["lng"] ?: obj["lon"])?.let { (it as? JsonPrimitive)?.doubleOrNull } ?: return null
        return RideCoordinates(latitude = lat, longitude = lon)
    }

    suspend fun fetchRidesAndRole(baseUrl: String, token: String): Result<Pair<RideRole, List<RideTrip>>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val (code, raw) = executeRequest("${cleanUrl(baseUrl)}/api/rides", method = "GET", bearerToken = token)
                ensureSuccess(code, raw)
                val parsed = json.decodeFromString<RidesEnvelope>(raw)
                val role = when (parsed.role.lowercase()) {
                    "driver" -> RideRole.DRIVER
                    "admin" -> RideRole.ADMIN
                    else -> RideRole.RIDER
                }
                role to parsed.trips
            }
        }

    suspend fun createRide(
        baseUrl: String,
        token: String,
        pickup: String,
        destination: String,
        category: RideCategory,
        pricingMode: PricingMode,
        fare: Double,
        paymentMethod: PaymentMethod,
        pickupAt: Long?,
        quoteToken: String
    ): Result<RideTrip> = withContext(Dispatchers.IO) {
        runCatching {
            val payload = buildJsonObject {
                put("pickup", pickup.trim())
                put("destination", destination.trim())
                put("category", category.name.lowercase())
                put("pricingMode", pricingMode.name.lowercase())
                put("fare", fare)
                put("paymentMethod", paymentMethod.name.lowercase())
                if (pickupAt != null) put("pickupAt", pickupAt)
                put("route", buildJsonObject {
                    put("quoteToken", quoteToken)
                })
            }.toString()
            val (code, raw) = executeRequest(
                "${cleanUrl(baseUrl)}/api/rides",
                method = "POST",
                body = payload,
                bearerToken = token
            )
            ensureSuccess(code, raw)
            json.decodeFromString<SingleTripEnvelope>(raw).trip
        }
    }

    suspend fun performRideAction(
        baseUrl: String,
        token: String,
        tripId: String,
        payload: JsonObject
    ): Result<RideTrip> = withContext(Dispatchers.IO) {
        runCatching {
            val (code, raw) = executeRequest(
                "${cleanUrl(baseUrl)}/api/rides/${enc(tripId)}/actions",
                method = "POST",
                body = payload.toString(),
                bearerToken = token
            )
            ensureSuccess(code, raw)
            json.decodeFromString<SingleTripEnvelope>(raw).trip
        }
    }

    suspend fun updateDriverLocation(
        baseUrl: String,
        token: String,
        tripId: String,
        coords: RideCoordinates
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val payload = buildJsonObject {
                put("latitude", coords.latitude)
                put("longitude", coords.longitude)
            }.toString()
            val (code, raw) = executeRequest(
                "${cleanUrl(baseUrl)}/api/rides/${enc(tripId)}/location",
                method = "PATCH",
                body = payload,
                bearerToken = token
            )
            ensureSuccess(code, raw)
        }
    }

    suspend fun initializePayment(
        baseUrl: String,
        token: String,
        tripId: String
    ): Result<PaymentInitOutcome> = withContext(Dispatchers.IO) {
        runCatching {
            val payload = buildJsonObject { put("tripId", tripId) }.toString()
            val (code, raw) = executeRequest(
                "${cleanUrl(baseUrl)}/api/payments/initialize",
                method = "POST",
                body = payload,
                bearerToken = token
            )
            ensureSuccess(code, raw)
            val obj = json.parseToJsonElement(raw).jsonObject
            val payment = obj["payment"]?.jsonObject ?: error("Payment payload missing")
            val reference = payment["reference"]?.jsonPrimitive?.content.orEmpty()
            val amountMinor = payment["amountMinor"]?.jsonPrimitive?.longOrNull ?: 0L
            val statusRaw = payment["status"]?.jsonPrimitive?.content ?: "pending"
            PaymentInitOutcome(
                tripId = tripId,
                provider = obj["provider"]?.jsonPrimitive?.content ?: "paystack",
                mode = obj["mode"]?.jsonPrimitive?.content ?: "paystack",
                reference = reference,
                amountMinor = amountMinor,
                status = parsePaymentStatus(statusRaw),
                authorizationUrl = obj["authorizationUrl"]?.let { elem ->
                    if (elem.toString() == "null") null else elem.jsonPrimitive.content
                }
            )
        }
    }

    suspend fun submitMockPaymentResult(
        baseUrl: String,
        token: String,
        reference: String,
        success: Boolean
    ): Result<PaymentVerificationResult> = withContext(Dispatchers.IO) {
        runCatching {
            val payload = buildJsonObject {
                put("reference", reference)
                put("success", success)
            }.toString()
            val (code, raw) = executeRequest(
                "${cleanUrl(baseUrl)}/api/payments/mock-result",
                method = "POST",
                body = payload,
                bearerToken = token
            )
            ensureSuccess(code, raw)
            val obj = json.parseToJsonElement(raw).jsonObject
            val payment = obj["payment"]?.jsonObject
            val statusRaw = payment?.get("status")?.jsonPrimitive?.content ?: "failed"
            val amountMinor = payment?.get("amountMinor")?.jsonPrimitive?.longOrNull ?: 0L
            val state = when (statusRaw) {
                "authorized" -> PaymentVerificationState.AUTHORIZED
                "paid" -> PaymentVerificationState.PAID
                "pending" -> PaymentVerificationState.PENDING
                else -> PaymentVerificationState.FAILED
            }
            PaymentVerificationResult(
                reference = reference,
                state = state,
                amountMinor = amountMinor,
                message = if (state == PaymentVerificationState.AUTHORIZED) {
                    "Card authorization held in development mode."
                } else {
                    "Mock payment declined."
                }
            )
        }
    }

    suspend fun verifyPayment(baseUrl: String, token: String, reference: String): Result<PaymentVerificationResult> =
        withContext(Dispatchers.IO) {
            runCatching {
                val payload = buildJsonObject { put("reference", reference) }.toString()
                val (code, raw) = executeRequest(
                    "${cleanUrl(baseUrl)}/api/payments/verify",
                    method = "POST",
                    body = payload,
                    bearerToken = token
                )
                if (code !in 200..299 && code != 202) {
                    throw ApiHttpException(code, extractApiError(code, raw))
                }
                val obj = json.parseToJsonElement(raw).jsonObject
                val verified = obj["verified"]?.jsonPrimitive?.content == "true"
                val authorized = obj["authorized"]?.jsonPrimitive?.content == "true"
                val payment = obj["payment"]?.jsonObject
                val amountMinor = payment?.get("amountMinor")?.jsonPrimitive?.longOrNull ?: 0L
                val tripId = payment?.get("tripId")?.jsonPrimitive?.content
                val statusStr = payment?.get("status")?.jsonPrimitive?.content
                val state = when {
                    statusStr == "failed" || statusStr == "expired" -> PaymentVerificationState.FAILED
                    verified && authorized -> PaymentVerificationState.AUTHORIZED
                    verified -> PaymentVerificationState.PAID
                    else -> PaymentVerificationState.PENDING
                }
                PaymentVerificationResult(
                    reference = reference,
                    tripId = tripId,
                    state = state,
                    amountMinor = amountMinor,
                    message = when (state) {
                        PaymentVerificationState.AUTHORIZED -> "Your card hold is confirmed. You will only be charged once the ride finishes."
                        PaymentVerificationState.PAID -> "Ride fare captured and receipt saved."
                        PaymentVerificationState.FAILED -> "Authorization did not succeed. Try again with a different card or payment method."
                        else -> "Paystack is confirming this authorization."
                    }
                )
            }
        }

    suspend fun releasePaymentHold(baseUrl: String, token: String, tripId: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val payload = buildJsonObject { put("tripId", tripId) }.toString()
                val (code, raw) = executeRequest(
                    "${cleanUrl(baseUrl)}/api/payments/release",
                    method = "POST",
                    body = payload,
                    bearerToken = token
                )
                ensureSuccess(code, raw)
            }
        }

    suspend fun fetchWallet(baseUrl: String, token: String): Result<Pair<WalletSummary, List<WalletTransaction>>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val (code, raw) = executeRequest("${cleanUrl(baseUrl)}/api/wallet", method = "GET", bearerToken = token)
                ensureSuccess(code, raw)
                val parsed = json.decodeFromString<WalletEnvelope>(raw)
                parsed.wallet to parsed.transactions
            }
        }

    suspend fun initializeWalletTopUp(baseUrl: String, token: String, amountMinor: Long): Result<PaymentInitOutcome> =
        withContext(Dispatchers.IO) {
            runCatching {
                val payload = buildJsonObject { put("amountMinor", amountMinor) }.toString()
                val (code, raw) = executeRequest(
                    "${cleanUrl(baseUrl)}/api/wallet/top-up",
                    method = "POST",
                    body = payload,
                    bearerToken = token
                )
                ensureSuccess(code, raw)
                val obj = json.parseToJsonElement(raw).jsonObject
                val topup = obj["topup"]?.jsonObject ?: error("Top-up payload missing")
                PaymentInitOutcome(
                    tripId = null,
                    provider = "paystack",
                    mode = "paystack",
                    reference = topup["reference"]?.jsonPrimitive?.content.orEmpty(),
                    amountMinor = topup["amountMinor"]?.jsonPrimitive?.longOrNull ?: amountMinor,
                    status = PaymentStatus.PENDING,
                    authorizationUrl = topup["authorizationUrl"]?.jsonPrimitive?.content
                )
            }
        }

    suspend fun verifyWalletTopUp(
        baseUrl: String,
        token: String,
        reference: String
    ): Result<Pair<PaymentVerificationResult, WalletSummary>> = withContext(Dispatchers.IO) {
        runCatching {
            val (code, raw) = executeRequest(
                "${cleanUrl(baseUrl)}/api/wallet/top-ups/${enc(reference)}/verify",
                method = "GET",
                bearerToken = token
            )
            ensureSuccess(code, raw)
            val parsed = json.decodeFromString<WalletVerifyEnvelope>(raw)
            val result = PaymentVerificationResult(
                reference = reference,
                state = PaymentVerificationState.PAID,
                amountMinor = 0L,
                message = "Wallet top-up confirmed. Updated balance: R %.2f".format(parsed.wallet.balanceMinor / 100.0)
            )
            result to parsed.wallet
        }
    }

    suspend fun fetchDriverProfile(
        baseUrl: String,
        token: String,
        fallbackName: String,
        fallbackEmail: String?
    ): Result<DriverApplication?> = withContext(Dispatchers.IO) {
        runCatching {
            val (code, raw) = executeRequest(
                "${cleanUrl(baseUrl)}/api/driver/profile",
                method = "GET",
                bearerToken = token
            )
            ensureSuccess(code, raw)
            json.decodeFromString<DriverProfileEnvelope>(raw).resolvedProfile()?.toDomain(fallbackName, fallbackEmail)
        }
    }

    suspend fun setDriverOnline(baseUrl: String, token: String, online: Boolean): Result<Boolean> =
        withContext(Dispatchers.IO) {
            runCatching {
                val payload = buildJsonObject { put("online", online) }.toString()
                val (code, raw) = executeRequest(
                    "${cleanUrl(baseUrl)}/api/driver/profile/online",
                    method = "PATCH",
                    body = payload,
                    bearerToken = token
                )
                ensureSuccess(code, raw)
                online
            }
        }

    suspend fun saveDriverApplication(
        baseUrl: String,
        token: String,
        app: DriverApplication,
        submit: Boolean,
        fallbackName: String,
        fallbackEmail: String?
    ): Result<DriverApplication> = withContext(Dispatchers.IO) {
        runCatching {
            val payload = buildJsonObject {
                put("contactPhone", app.contactPhone.trim())
                put("city", app.city.trim())
                put("vehicle", app.vehicle.trim())
                put("vehicleColor", app.vehicleColor.trim())
                put("registration", app.registration.trim())
                if (app.licenceExpiry.isNotBlank()) put("licenceExpiry", app.licenceExpiry.trim())
                if (app.operatingLicenceReference.isNotBlank()) {
                    put("operatingLicenceReference", app.operatingLicenceReference.trim())
                }
                if (submit) put("documentsConsent", app.documentsConsent)
            }.toString()
            val (code, raw) = executeRequest(
                "${cleanUrl(baseUrl)}/api/driver/application",
                method = if (submit) "POST" else "PATCH",
                body = payload,
                bearerToken = token
            )
            ensureSuccess(code, raw)
            val dto = runCatching { json.decodeFromString<DriverProfileEnvelope>(raw).resolvedProfile() }.getOrNull()
            if (dto != null) {
                dto.toDomain(fallbackName, fallbackEmail)
            } else {
                fetchDriverProfile(baseUrl, token, fallbackName, fallbackEmail).getOrNull()
                    ?: error("Driver application profile missing")
            }
        }
    }

    suspend fun uploadDriverDocument(
        baseUrl: String,
        token: String,
        docType: DriverDocumentType,
        fileName: String,
        contentType: String,
        bytes: ByteArray,
        fallbackName: String,
        fallbackEmail: String?
    ): Result<DriverApplication> = withContext(Dispatchers.IO) {
        runCatching {
            val (code, raw) = executeRawUpload(
                urlString = "${cleanUrl(baseUrl)}/api/driver/application/documents/${enc(docType.key)}",
                contentType = contentType,
                fileName = fileName,
                bytes = bytes,
                bearerToken = token
            )
            ensureSuccess(code, raw)
            val dto = runCatching { json.decodeFromString<DriverProfileEnvelope>(raw).resolvedProfile() }.getOrNull()
            if (dto != null) {
                dto.toDomain(fallbackName, fallbackEmail)
            } else {
                fetchDriverProfile(baseUrl, token, fallbackName, fallbackEmail).getOrNull()
                    ?: error("Updated driver profile missing")
            }
        }
    }

    suspend fun fetchDriverEarnings(baseUrl: String, token: String): Result<DriverEarningsSummary> =
        withContext(Dispatchers.IO) {
            runCatching {
                val (code, raw) = executeRequest(
                    "${cleanUrl(baseUrl)}/api/driver/earnings",
                    method = "GET",
                    bearerToken = token
                )
                ensureSuccess(code, raw)
                json.decodeFromString<DriverEarningsSummary>(raw)
            }
        }

    suspend fun fetchDriverSubscription(baseUrl: String, token: String): Result<DriverSubscriptionSummary?> =
        withContext(Dispatchers.IO) {
            runCatching {
                val (code, raw) = executeRequest(
                    "${cleanUrl(baseUrl)}/api/driver/subscription",
                    method = "GET",
                    bearerToken = token
                )
                ensureSuccess(code, raw)
                json.decodeFromString<DriverSubscriptionEnvelope>(raw).subscription
            }
        }

    suspend fun initializeDriverSubscription(
        baseUrl: String,
        token: String,
        amountMinor: Long
    ): Result<PaymentInitOutcome> = withContext(Dispatchers.IO) {
        runCatching {
            val payload = buildJsonObject { put("amountMinor", amountMinor) }.toString()
            val (code, raw) = executeRequest(
                "${cleanUrl(baseUrl)}/api/driver/subscription/initialize",
                method = "POST",
                body = payload,
                bearerToken = token
            )
            ensureSuccess(code, raw)
            val obj = json.parseToJsonElement(raw).jsonObject
            val payment = obj["payment"]?.jsonObject ?: error("Subscription payment payload missing")
            PaymentInitOutcome(
                tripId = null,
                provider = obj["provider"]?.jsonPrimitive?.content ?: "paystack",
                mode = obj["mode"]?.jsonPrimitive?.content ?: "paystack",
                reference = payment["reference"]?.jsonPrimitive?.content.orEmpty(),
                amountMinor = payment["amountMinor"]?.jsonPrimitive?.longOrNull ?: amountMinor,
                status = parsePaymentStatus(payment["status"]?.jsonPrimitive?.content ?: "pending"),
                authorizationUrl = obj["authorizationUrl"]?.let { elem ->
                    if (elem.toString() == "null") null else elem.jsonPrimitive.content
                }
            )
        }
    }

    suspend fun fetchSupportCases(baseUrl: String, token: String): Result<List<SupportCase>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val (code, raw) = executeRequest(
                    "${cleanUrl(baseUrl)}/api/support/cases",
                    method = "GET",
                    bearerToken = token
                )
                ensureSuccess(code, raw)
                json.decodeFromString<SupportCasesEnvelope>(raw).cases
            }
        }

    // Admin endpoints
    suspend fun fetchAdminOverview(baseUrl: String, token: String): Result<AdminOverviewSummary> =
        withContext(Dispatchers.IO) {
            runCatching {
                val (code, raw) = executeRequest("${cleanUrl(baseUrl)}/api/admin/overview", method = "GET", bearerToken = token)
                ensureSuccess(code, raw)
                json.decodeFromString<AdminOverviewEnvelope>(raw).summary
            }
        }

    suspend fun fetchAdminActivity(baseUrl: String, token: String): Result<List<ActivityLogEntry>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val (code, raw) = executeRequest("${cleanUrl(baseUrl)}/api/admin/activity?limit=50", method = "GET", bearerToken = token)
                ensureSuccess(code, raw)
                json.decodeFromString<AdminActivityEnvelope>(raw).activity
            }
        }

    suspend fun fetchAdminPeople(baseUrl: String, token: String): Result<List<UserAccountSummary>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val (code, raw) = executeRequest("${cleanUrl(baseUrl)}/api/admin/people?q=&type=all&page=0&limit=50", method = "GET", bearerToken = token)
                ensureSuccess(code, raw)
                json.decodeFromString<AdminPeopleEnvelope>(raw).people
            }
        }

    suspend fun fetchAdminDriverApplications(baseUrl: String, token: String): Result<List<DriverApplication>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val (code, raw) = executeRequest("${cleanUrl(baseUrl)}/api/admin/driver-applications", method = "GET", bearerToken = token)
                ensureSuccess(code, raw)
                json.decodeFromString<AdminDriverApplicationsEnvelope>(raw).applications.map {
                    it.toDomain(it.displayName ?: "Driver", it.email)
                }
            }
        }

    suspend fun updateComplianceCheck(
        baseUrl: String,
        token: String,
        userId: String,
        docType: DriverDocumentType,
        status: ComplianceStatus,
        note: String?
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val payload = buildJsonObject {
                put("documentType", docType.key)
                put("status", status.key)
                if (!note.isNullOrBlank()) {
                    put("note", note.trim())
                } else if (status == ComplianceStatus.NOT_REQUIRED) {
                    put("note", "Exempted by operations admin")
                }
            }.toString()
            val (code, raw) = executeRequest(
                "${cleanUrl(baseUrl)}/api/admin/people/${enc(userId)}/compliance",
                method = "PATCH",
                body = payload,
                bearerToken = token
            )
            ensureSuccess(code, raw)
        }
    }

    suspend fun decideDriverApplication(
        baseUrl: String,
        token: String,
        userId: String,
        outcome: String,
        note: String?
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val payload = buildJsonObject {
                put("subjectType", "driver")
                put("outcome", outcome)
                put("checksConfirmed", outcome == "approved")
                if (!note.isNullOrBlank()) put("note", note.trim())
            }.toString()
            val (code, raw) = executeRequest(
                "${cleanUrl(baseUrl)}/api/admin/people/${enc(userId)}/reviews",
                method = "POST",
                body = payload,
                bearerToken = token
            )
            ensureSuccess(code, raw)
        }
    }

    suspend fun updateUserRole(
        baseUrl: String,
        token: String,
        userId: String,
        role: RideRole
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val payload = buildJsonObject { put("role", role.name.lowercase()) }.toString()
            val (code, raw) = executeRequest(
                "${cleanUrl(baseUrl)}/api/admin/people/${enc(userId)}/role",
                method = "PATCH",
                body = payload,
                bearerToken = token
            )
            ensureSuccess(code, raw)
        }
    }

    suspend fun updateUserSuspension(
        baseUrl: String,
        token: String,
        userId: String,
        suspend: Boolean,
        reason: String?
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val payload = buildJsonObject {
                put("suspend", suspend)
                if (!reason.isNullOrBlank()) put("reason", reason.trim())
            }.toString()
            val (code, raw) = executeRequest(
                "${cleanUrl(baseUrl)}/api/admin/people/${enc(userId)}/suspend",
                method = "PATCH",
                body = payload,
                bearerToken = token
            )
            ensureSuccess(code, raw)
        }
    }

    suspend fun fetchAdminSupportCases(baseUrl: String, token: String): Result<List<SupportCase>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val (code, raw) = executeRequest("${cleanUrl(baseUrl)}/api/admin/support-cases", method = "GET", bearerToken = token)
                ensureSuccess(code, raw)
                json.decodeFromString<SupportCasesEnvelope>(raw).cases
            }
        }

    suspend fun updateAdminSupportCase(
        baseUrl: String,
        token: String,
        caseId: Long,
        status: String,
        priority: String? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val payload = buildJsonObject {
                put("status", status)
                if (!priority.isNullOrBlank()) put("priority", priority)
            }.toString()
            val (code, raw) = executeRequest(
                "${cleanUrl(baseUrl)}/api/admin/support-cases/$caseId",
                method = "PATCH",
                body = payload,
                bearerToken = token
            )
            ensureSuccess(code, raw)
        }
    }

    suspend fun replyAdminSupportCase(
        baseUrl: String,
        token: String,
        caseId: Long,
        message: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val payload = buildJsonObject { put("message", message.trim()) }.toString()
            val (code, raw) = executeRequest(
                "${cleanUrl(baseUrl)}/api/admin/support-cases/$caseId/replies",
                method = "POST",
                body = payload,
                bearerToken = token
            )
            ensureSuccess(code, raw)
        }
    }

    suspend fun fetchAdminPayouts(baseUrl: String, token: String): Result<List<DriverPayoutRecord>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val (code, raw) = executeRequest("${cleanUrl(baseUrl)}/api/admin/payouts", method = "GET", bearerToken = token)
                ensureSuccess(code, raw)
                json.decodeFromString<AdminPayoutsEnvelope>(raw).payouts
            }
        }

    suspend fun recordAdminPayout(
        baseUrl: String,
        token: String,
        driverId: String,
        provider: String,
        externalReference: String,
        amountMinor: Long,
        periodStart: String?,
        periodEnd: String?,
        note: String?
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val payload = buildJsonObject {
                put("driverId", driverId)
                put("provider", provider)
                put("externalReference", externalReference)
                put("amountMinor", amountMinor)
                put("currency", "ZAR")
                if (!periodStart.isNullOrBlank()) put("periodStart", periodStart)
                if (!periodEnd.isNullOrBlank()) put("periodEnd", periodEnd)
                if (!note.isNullOrBlank()) put("note", note.trim())
                put("confirmedExternalTransfer", true)
            }.toString()
            val (code, raw) = executeRequest(
                "${cleanUrl(baseUrl)}/api/admin/payouts",
                method = "POST",
                body = payload,
                bearerToken = token
            )
            ensureSuccess(code, raw)
        }
    }

    suspend fun reconcileAdminPayout(
        baseUrl: String,
        token: String,
        payoutId: Long,
        reconciliationStatus: String,
        note: String?
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val payload = buildJsonObject {
                put("reconciliationStatus", reconciliationStatus)
                if (!note.isNullOrBlank()) {
                    put("note", note.trim())
                } else if (reconciliationStatus == "exception") {
                    put("note", "Flagged in operations console")
                }
            }.toString()
            val (code, raw) = executeRequest(
                "${cleanUrl(baseUrl)}/api/admin/payouts/$payoutId",
                method = "PATCH",
                body = payload,
                bearerToken = token
            )
            ensureSuccess(code, raw)
        }
    }

    suspend fun fetchAdminPayoutBatches(baseUrl: String, token: String): Result<List<PayoutBatchRecord>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val (code, raw) = executeRequest("${cleanUrl(baseUrl)}/api/admin/payouts/batches", method = "GET", bearerToken = token)
                ensureSuccess(code, raw)
                json.decodeFromString<AdminBatchesEnvelope>(raw).batches
            }
        }

    suspend fun runWeeklyPayoutBatch(
        baseUrl: String,
        token: String,
        periodStartIso: String,
        periodEndIso: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val payload = buildJsonObject {
                put("periodStart", periodStartIso)
                put("periodEnd", periodEndIso)
            }.toString()
            val (code, raw) = executeRequest(
                "${cleanUrl(baseUrl)}/api/admin/payouts/run-weekly",
                method = "POST",
                body = payload,
                bearerToken = token
            )
            ensureSuccess(code, raw)
        }
    }

    suspend fun updateAdminPayoutBatchStatus(
        baseUrl: String,
        token: String,
        batchId: String,
        status: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val payload = buildJsonObject { put("status", status) }.toString()
            val (code, raw) = executeRequest(
                "${cleanUrl(baseUrl)}/api/admin/payouts/batches/${enc(batchId)}",
                method = "PATCH",
                body = payload,
                bearerToken = token
            )
            ensureSuccess(code, raw)
        }
    }

    private fun parsePaymentStatus(raw: String): PaymentStatus =
        PaymentStatus.entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: PaymentStatus.PENDING

    private fun ensureSuccess(code: Int, raw: String) {
        if (code !in 200..299) {
            throw ApiHttpException(code, extractApiError(code, raw)).toFriendlyApiException()
        }
    }

    private fun extractApiError(code: Int, raw: String): String {
        val parsedMsg = runCatching {
            val obj = json.parseToJsonElement(raw).jsonObject
            val errElem = obj["error"]
            when (errElem) {
                is JsonPrimitive -> errElem.contentOrNull
                is JsonObject -> (errElem["message"] as? JsonPrimitive)?.contentOrNull
                    ?: (errElem["error"] as? JsonPrimitive)?.contentOrNull
                else -> null
            }
                ?: (obj["message"] as? JsonPrimitive)?.contentOrNull
                ?: (obj["detail"] as? JsonPrimitive)?.contentOrNull
        }.getOrNull()
        if (!parsedMsg.isNullOrBlank()) return parsedMsg
        val trimmed = raw.trim()
        if (trimmed.isNotEmpty() && !trimmed.startsWith("<")) {
            return trimmed.take(180)
        }
        return "Request failed ($code)"
    }

    private fun executeRequest(
        urlString: String,
        method: String,
        body: String? = null,
        bearerToken: String? = null,
        contentType: String = "application/json"
    ): Pair<Int, String> {
        var conn: HttpURLConnection? = null
        return try {
            val url = URL(urlString)
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = 15_000
                readTimeout = 25_000
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "VhayaAndroid/1.0")
                if (!bearerToken.isNullOrBlank()) {
                    setRequestProperty("Authorization", "Bearer $bearerToken")
                }
                if (body != null) {
                    doOutput = true
                    setRequestProperty("Content-Type", contentType)
                }
            }
            if (body != null) {
                conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use(BufferedReader::readText).orEmpty()
            code to text
        } catch (t: Throwable) {
            throw t.toFriendlyApiException()
        } finally {
            conn?.disconnect()
        }
    }

    private fun executeRawUpload(
        urlString: String,
        contentType: String,
        fileName: String,
        bytes: ByteArray,
        bearerToken: String
    ): Pair<Int, String> {
        var conn: HttpURLConnection? = null
        return try {
            val safeName = fileName.replace(Regex("[^A-Za-z0-9._-]"), "_").ifBlank { "document" }
            val url = URL(urlString)
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 20_000
                readTimeout = 45_000
                doOutput = true
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "VhayaAndroid/1.0")
                setRequestProperty("Authorization", "Bearer $bearerToken")
                setRequestProperty("Content-Type", contentType)
                setRequestProperty("x-document-name", safeName)
                setFixedLengthStreamingMode(bytes.size)
            }
            conn.outputStream.use { it.write(bytes) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use(BufferedReader::readText).orEmpty()
            code to text
        } catch (t: Throwable) {
            throw t.toFriendlyApiException()
        } finally {
            conn?.disconnect()
        }
    }

    private fun friendlyFirebaseError(code: String): String = when {
        code.contains("INVALID_EMAIL") -> "Enter a valid email address."
        code.contains("EMAIL_EXISTS") -> "An account already uses this email."
        code.contains("WEAK_PASSWORD") -> "Use a password with at least 6 characters."
        code.contains("INVALID_LOGIN_CREDENTIALS") || code.contains("INVALID_PASSWORD") || code.contains("EMAIL_NOT_FOUND") ->
            "Incorrect email or password."
        code.contains("USER_DISABLED") -> "This Vhaya account has been disabled."
        code.contains("TOO_MANY_ATTEMPTS") -> "Too many attempts. Try again shortly."
        else -> code
    }
}
