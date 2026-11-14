package com.wayfare.ride

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import com.wayfare.ride.data.SessionStore
import com.wayfare.ride.data.VhayaApiClient
import com.wayfare.ride.data.WayfareDatabase
import com.wayfare.ride.data.WayfareRepository
import com.wayfare.ride.domain.AuthState
import com.wayfare.ride.domain.DriverProfileSnapshot
import com.wayfare.ride.domain.IssueCategory
import com.wayfare.ride.domain.PaymentMethod
import com.wayfare.ride.domain.PaymentStatus
import com.wayfare.ride.domain.PaymentVerificationState
import com.wayfare.ride.domain.PricingMode
import com.wayfare.ride.domain.PrivacyPreferences
import com.wayfare.ride.domain.RideCategory
import com.wayfare.ride.domain.RideCoordinates
import com.wayfare.ride.domain.RideOffer
import com.wayfare.ride.domain.RideRole
import com.wayfare.ride.domain.RideRoute
import com.wayfare.ride.domain.RideStatus
import com.wayfare.ride.domain.RideTrip
import com.wayfare.ride.domain.SupportCase
import com.wayfare.ride.domain.UserSession
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.util.concurrent.CopyOnWriteArrayList

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class VhayaEndToEndRobolectricTest {

    private lateinit var context: Context
    private lateinit var db: WayfareDatabase
    private lateinit var server: HttpServer
    private var serverPort: Int = 0
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private var serverRole: String = "rider"
    private val serverTrips = linkedMapOf<String, RideTrip>()
    private val serverCases = mutableListOf<SupportCase>()
    private val recordedRequests = CopyOnWriteArrayList<Pair<String, String>>()
    private val recordedActionBodies = CopyOnWriteArrayList<String>()
    private val recordedLocationUpdates = CopyOnWriteArrayList<String>()
    private var nextTripSeq = 1
    private var nextCaseSeq = 100L

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("vhaya_session_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        db = Room.inMemoryDatabaseBuilder(context, WayfareDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        serverPort = server.address.port
        server.createContext("/") { exchange -> handleRequest(exchange) }
        server.start()
    }

    @After
    fun tearDown() {
        server.stop(0)
        db.close()
    }

    private fun seedSession(role: RideRole = RideRole.RIDER): SessionStore {
        val store = SessionStore(context, apiBaseUrlOverride = "http://127.0.0.1:$serverPort")
        store.saveSession(
            UserSession(
                userId = "user-1",
                displayName = "Kabelo Sithole",
                email = "kabelo@vhaya.co.za",
                role = role,
                emailVerified = true,
                idToken = "valid-jwt-token",
                refreshToken = "refresh-token",
                expiresAtEpochMs = System.currentTimeMillis() + 3_600_000L
            )
        )
        return store
    }

    private fun newRepository(baseUrl: String = "http://127.0.0.1:$serverPort"): WayfareRepository {
        val store = SessionStore(context, apiBaseUrlOverride = baseUrl)
        return WayfareRepository(context, db, store, VhayaApiClient())
    }

    private fun handleRequest(exchange: HttpExchange) {
        val method = exchange.requestMethod
        val path = exchange.requestURI.path
        val body = exchange.requestBody.bufferedReader().use { it.readText() }
        recordedRequests += method to path

        fun respond(status: Int, payload: String) {
            val bytes = payload.toByteArray(Charsets.UTF_8)
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(status, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }

        when {
            path == "/api/rides" && method == "GET" -> {
                val tripsJson = serverTrips.values.joinToString(",") { json.encodeToString(it) }
                respond(200, """{"role":"$serverRole","trips":[$tripsJson]}""")
            }

            path == "/api/routes/preview" && method == "POST" -> {
                val routeJson = """
                    {
                      "route": {
                        "distanceMeters": 4200,
                        "durationSeconds": 540,
                        "encodedPolyline": "_p~iF~ps|U_ulLnnqC_mqNvxq`@",
                        "provider": "osrm",
                        "originCoordinates": {"latitude": -23.8768, "longitude": 29.4979},
                        "destinationCoordinates": {"latitude": -23.9038, "longitude": 29.4857},
                        "fareEstimate": 84.0,
                        "quoteToken": "quote-token-123"
                      }
                    }
                """.trimIndent()
                respond(200, routeJson)
            }

            path == "/api/rides" && method == "POST" -> {
                val obj = json.parseToJsonElement(body).jsonObject
                val id = "trip-${nextTripSeq++}"
                val methodName = obj["paymentMethod"]?.jsonPrimitive?.contentOrNull ?: "card"
                val paymentMethod = PaymentMethod.entries.firstOrNull { it.name.equals(methodName, true) }
                    ?: PaymentMethod.CARD
                val fare = obj["fare"]?.jsonPrimitive?.doubleOrNull ?: 84.0
                val trip = RideTrip(
                    id = id,
                    riderId = "user-1",
                    pickup = obj["pickup"]?.jsonPrimitive?.contentOrNull ?: "Pickup",
                    destination = obj["destination"]?.jsonPrimitive?.contentOrNull ?: "Destination",
                    category = RideCategory.STANDARD,
                    fare = fare,
                    pricingMode = PricingMode.ESTIMATE,
                    status = RideStatus.REQUESTED,
                    paymentMethod = paymentMethod,
                    paymentStatus = if (paymentMethod == PaymentMethod.CARD) PaymentStatus.PENDING else PaymentStatus.NONE,
                    createdAt = System.currentTimeMillis(),
                    pickupCoordinates = RideCoordinates(-23.8768, 29.4979),
                    destinationCoordinates = RideCoordinates(-23.9038, 29.4857),
                    route = RideRoute(distanceMeters = 4200, durationSeconds = 540, quoteToken = "quote-token-123")
                )
                serverTrips[id] = trip
                respond(200, """{"trip":${json.encodeToString(trip)}}""")
            }

            path.startsWith("/api/rides/") && path.endsWith("/actions") && method == "POST" -> {
                recordedActionBodies += body
                val tripId = path.removePrefix("/api/rides/").removeSuffix("/actions")
                val current = serverTrips[tripId]
                if (current == null) {
                    respond(404, """{"error":"Trip not found"}""")
                    return
                }
                val obj = json.parseToJsonElement(body).jsonObject
                val action = obj["action"]?.jsonPrimitive?.contentOrNull.orEmpty()
                val driverSnap = DriverProfileSnapshot(
                    id = "driver-99",
                    name = "Thabo Mokoena",
                    vehicle = "Toyota Corolla Quest",
                    vehicleColor = "White",
                    registration = "ABC 123 GP"
                )
                val updated = when (action) {
                    "counter" -> {
                        val amount = obj["amount"]?.jsonPrimitive?.doubleOrNull ?: current.fare
                        val offer = RideOffer(
                            id = "offer-1",
                            tripId = tripId,
                            driverId = driverSnap.id,
                            amount = amount,
                            status = "pending",
                            createdAt = System.currentTimeMillis(),
                            driverProfile = driverSnap
                        )
                        current.copy(status = RideStatus.COUNTERED, offers = current.offers + offer)
                    }
                    "chooseOffer", "accept" -> {
                        val chosenAmount = current.offers.firstOrNull()?.amount ?: current.fare
                        current.copy(
                            driverId = driverSnap.id,
                            driverProfile = driverSnap,
                            fare = chosenAmount,
                            status = RideStatus.ACCEPTED,
                            tripPin = "654321"
                        )
                    }
                    "advance" -> {
                        when (current.status) {
                            RideStatus.ACCEPTED -> current.copy(status = RideStatus.ARRIVED, arrivalConfirmedAt = System.currentTimeMillis())
                            RideStatus.ARRIVED -> {
                                val pin = obj["pin"]?.jsonPrimitive?.contentOrNull
                                if (pin != (current.tripPin ?: "654321")) {
                                    respond(400, """{"error":"Incorrect 6-digit pickup PIN."}""")
                                    return
                                }
                                current.copy(status = RideStatus.IN_PROGRESS)
                            }
                            RideStatus.IN_PROGRESS -> current.copy(
                                status = RideStatus.COMPLETED,
                                paymentStatus = PaymentStatus.PAID
                            )
                            else -> current
                        }
                    }
                    "confirmArrival" -> current.copy(
                        arrivalConfirmationMethod = "rider",
                        arrivalConfirmedAt = System.currentTimeMillis()
                    )
                    "cancel" -> current.copy(
                        status = RideStatus.CANCELLED,
                        paymentStatus = if (current.paymentMethod == PaymentMethod.CARD) PaymentStatus.RELEASED else current.paymentStatus
                    )
                    "rate" -> {
                        val rating = obj["rating"]?.jsonPrimitive?.intOrNull ?: 5
                        current.copy(riderRating = rating)
                    }
                    "message" -> {
                        val channel = obj["channel"]?.jsonPrimitive?.contentOrNull ?: "driver"
                        val msgText = obj["message"]?.jsonPrimitive?.contentOrNull.orEmpty()
                        if (channel == "support") {
                            val caseId = nextCaseSeq++
                            serverCases += SupportCase(
                                id = caseId,
                                rideId = tripId,
                                reporterName = "Kabelo Sithole",
                                category = obj["category"]?.jsonPrimitive?.contentOrNull ?: "other",
                                priority = obj["priority"]?.jsonPrimitive?.contentOrNull ?: "normal",
                                status = "open",
                                message = msgText,
                                pickup = current.pickup,
                                destination = current.destination,
                                rideStatus = current.status.name.lowercase(),
                                createdAt = System.currentTimeMillis(),
                                updatedAt = System.currentTimeMillis()
                            )
                        }
                        current
                    }
                    else -> current
                }
                serverTrips[tripId] = updated
                respond(200, """{"trip":${json.encodeToString(updated)}}""")
            }

            path.startsWith("/api/rides/") && path.endsWith("/location") && method == "PATCH" -> {
                recordedLocationUpdates += body
                respond(200, "{}")
            }

            path == "/api/payments/initialize" && method == "POST" -> {
                val obj = json.parseToJsonElement(body).jsonObject
                val tripId = obj["tripId"]?.jsonPrimitive?.contentOrNull ?: "trip-1"
                val ref = "paystack-ref-$tripId"
                serverTrips[tripId]?.let { trip ->
                    serverTrips[tripId] = trip.copy(paymentReference = ref, paymentStatus = PaymentStatus.PENDING)
                }
                respond(
                    200,
                    """{"provider":"paystack","mode":"paystack","authorizationUrl":"https://checkout.paystack.com/$ref","payment":{"reference":"$ref","amountMinor":8400,"status":"pending"}}"""
                )
            }

            path == "/api/payments/mock-result" && method == "POST" -> {
                val obj = json.parseToJsonElement(body).jsonObject
                val ref = obj["reference"]?.jsonPrimitive?.contentOrNull.orEmpty()
                val ok = obj["success"]?.jsonPrimitive?.content == "true"
                val status = if (ok) "authorized" else "failed"
                respond(200, """{"payment":{"reference":"$ref","amountMinor":8400,"status":"$status"}}""")
            }

            path == "/api/payments/verify" && method == "POST" -> {
                val obj = json.parseToJsonElement(body).jsonObject
                val ref = obj["reference"]?.jsonPrimitive?.contentOrNull.orEmpty()
                val tripId = ref.removePrefix("paystack-ref-")
                serverTrips[tripId]?.let {
                    serverTrips[tripId] = it.copy(paymentStatus = PaymentStatus.AUTHORIZED)
                }
                respond(
                    200,
                    """{"verified":true,"authorized":true,"payment":{"tripId":"$tripId","amountMinor":8400,"status":"authorized"}}"""
                )
            }

            path == "/api/payments/release" && method == "POST" -> {
                val obj = json.parseToJsonElement(body).jsonObject
                val tripId = obj["tripId"]?.jsonPrimitive?.contentOrNull.orEmpty()
                serverTrips[tripId]?.let {
                    serverTrips[tripId] = it.copy(paymentStatus = PaymentStatus.RELEASED)
                }
                respond(200, "{}")
            }

            path == "/api/wallet" && method == "GET" -> {
                respond(200, """{"wallet":{"id":"w-1","userId":"user-1","balanceMinor":25000,"currency":"ZAR"},"transactions":[]}""")
            }

            path == "/api/support/cases" && method == "GET" -> {
                val casesJson = serverCases.joinToString(",") { json.encodeToString(it) }
                respond(200, """{"cases":[$casesJson]}""")
            }

            path == "/api/driver/profile" && method == "GET" -> {
                respond(200, """{"profile":{"userId":"user-1","displayName":"Thabo","applicationStatus":"approved","verificationStatus":"approved","isOnline":true}}""")
            }

            path == "/api/driver/profile/online" && method == "PATCH" -> {
                respond(200, "{}")
            }

            path == "/api/driver/earnings" && method == "GET" -> {
                respond(200, """{"driverId":"user-1","currency":"ZAR","estimatedEarningsMinor":48000,"rideCount":6}""")
            }

            path == "/api/driver/subscription" && method == "GET" -> {
                respond(200, """{"subscription":{"id":"sub-1","status":"active","amountMinor":19900}}""")
            }

            path == "/api/admin/overview" && method == "GET" -> {
                respond(200, """{"summary":{"users":12,"activeRides":2,"activeSupportCases":1}}""")
            }

            path.startsWith("/api/admin/activity") && method == "GET" -> respond(200, """{"activity":[]}""")
            path.startsWith("/api/admin/people") && method == "GET" -> respond(200, """{"people":[]}""")
            path == "/api/admin/driver-applications" && method == "GET" -> respond(200, """{"applications":[]}""")
            path == "/api/admin/support-cases" && method == "GET" -> respond(200, """{"cases":[]}""")
            path == "/api/admin/payouts" && method == "GET" -> respond(200, """{"payouts":[]}""")
            path == "/api/admin/payouts/batches" && method == "GET" -> respond(200, """{"batches":[]}""")

            else -> respond(404, """{"error":"Unhandled route $method $path"}""")
        }
    }

    @Test
    fun `full rider to driver trip lifecycle end to end`() = runBlocking {
        seedSession(RideRole.RIDER)
        val repo = newRepository()

        val pickup = RideCoordinates(-23.8768, 29.4979)
        val dest = RideCoordinates(-23.9038, 29.4857)
        val route = repo.previewRoute("Mall of the North", "Savannah Mall", pickup, dest).getOrThrow()
        assertEquals("quote-token-123", route.quoteToken)

        val created = repo.createRideRequest(
            pickup = "Mall of the North",
            destination = "Savannah Mall",
            pickupCoords = pickup,
            destCoords = dest,
            category = RideCategory.STANDARD,
            customOfferFare = null,
            pickupAt = null,
            paymentMethod = PaymentMethod.CARD,
            existingRoute = route
        ).getOrThrow()
        assertEquals(RideStatus.REQUESTED, created.status)

        val countered = repo.driverSubmitOffer(created, amount = 95.0, driverLocation = pickup).getOrThrow()
        assertEquals(RideStatus.COUNTERED, countered.status)
        assertEquals(1, countered.offers.size)

        val accepted = repo.acceptDriverOffer(created.id, countered.offers.first().id).getOrThrow()
        assertEquals(RideStatus.ACCEPTED, accepted.status)
        assertEquals(95.0, accepted.fare, 0.01)
        assertEquals("654321", accepted.tripPin)

        val arrived = repo.driverMarkArrived(created.id, pickup).getOrThrow()
        assertEquals(RideStatus.ARRIVED, arrived.status)

        val confirmed = repo.riderConfirmArrival(created.id).getOrThrow()
        assertEquals("rider", confirmed.arrivalConfirmationMethod)

        val wrongPin = repo.verifyPinAndStartTrip(created.id, "000000")
        assertTrue(wrongPin.isFailure)

        val started = repo.verifyPinAndStartTrip(created.id, "654321").getOrThrow()
        assertEquals(RideStatus.IN_PROGRESS, started.status)

        val completed = repo.completeTrip(created.id).getOrThrow()
        assertEquals(RideStatus.COMPLETED, completed.status)
        assertEquals(PaymentStatus.PAID, completed.paymentStatus)

        val rated = repo.rateTrip(created.id, stars = 5).getOrThrow()
        assertEquals(5, rated.riderRating)
    }

    @Test
    fun `role access syncs appropriate endpoints for rider driver and admin`() = runBlocking {
        seedSession(RideRole.RIDER)
        val repo = newRepository()

        serverRole = "rider"
        repo.refreshActiveSessionData().getOrThrow()
        assertEquals(RideRole.RIDER, (repo.authState.value as AuthState.Authenticated).session.role)
        assertTrue(recordedRequests.any { it.second == "/api/wallet" })
        assertTrue(recordedRequests.any { it.second == "/api/support/cases" })

        recordedRequests.clear()
        serverRole = "driver"
        repo.refreshActiveSessionData().getOrThrow()
        assertEquals(RideRole.DRIVER, (repo.authState.value as AuthState.Authenticated).session.role)
        assertTrue(recordedRequests.any { it.second == "/api/driver/earnings" })
        assertTrue(recordedRequests.any { it.second == "/api/driver/subscription" })

        recordedRequests.clear()
        serverRole = "admin"
        repo.refreshActiveSessionData().getOrThrow()
        assertEquals(RideRole.ADMIN, (repo.authState.value as AuthState.Authenticated).session.role)
        assertTrue(recordedRequests.any { it.second == "/api/admin/overview" })
        assertTrue(recordedRequests.any { it.second == "/api/admin/payouts" })
    }

    @Test
    fun `payment outcomes paystack card hold release mock results cash and wallet`() = runBlocking {
        seedSession(RideRole.RIDER)
        val repo = newRepository()
        val pickup = RideCoordinates(-23.8768, 29.4979)
        val dest = RideCoordinates(-23.9038, 29.4857)
        val route = repo.previewRoute("Mall of the North", "Savannah Mall", pickup, dest).getOrThrow()

        val cardTrip = repo.createRideRequest(
            "Mall of the North", "Savannah Mall", pickup, dest,
            RideCategory.STANDARD, null, null, PaymentMethod.CARD, route
        ).getOrThrow()

        val initOutcome = repo.initializeCardPayment(cardTrip.id).getOrThrow()
        assertEquals(PaymentStatus.PENDING, initOutcome.status)
        assertNotNull(initOutcome.authorizationUrl)

        val mockApproved = repo.submitMockPaymentResult(initOutcome.reference, success = true).getOrThrow()
        assertEquals(PaymentVerificationState.AUTHORIZED, mockApproved.state)

        val mockDeclined = repo.submitMockPaymentResult(initOutcome.reference, success = false).getOrThrow()
        assertEquals(PaymentVerificationState.FAILED, mockDeclined.state)

        val verified = repo.verifyPaymentReference(initOutcome.reference, cardTrip.id).getOrThrow()
        assertEquals(PaymentVerificationState.AUTHORIZED, verified.state)

        repo.releaseCardHold(cardTrip.id).getOrThrow()
        val cancelled = repo.cancelTrip(cardTrip.id, "Changed plans").getOrThrow()
        assertEquals(RideStatus.CANCELLED, cancelled.status)
        assertEquals(PaymentStatus.RELEASED, cancelled.paymentStatus)

        val cashTrip = repo.createRideRequest(
            "Mall of the North", "Savannah Mall", pickup, dest,
            RideCategory.STANDARD, null, null, PaymentMethod.CASH, route
        ).getOrThrow()
        assertEquals(PaymentMethod.CASH, cashTrip.paymentMethod)

        val walletTrip = repo.createRideRequest(
            "Mall of the North", "Savannah Mall", pickup, dest,
            RideCategory.STANDARD, null, null, PaymentMethod.WALLET, route
        ).getOrThrow()
        assertEquals(PaymentMethod.WALLET, walletTrip.paymentMethod)
    }

    @Test
    fun `cancel timeout and driver offline mid trip scenarios`() = runBlocking {
        seedSession(RideRole.RIDER)
        val repo = newRepository()
        val pickup = RideCoordinates(-23.8768, 29.4979)
        val dest = RideCoordinates(-23.9038, 29.4857)
        val route = repo.previewRoute("Mall of the North", "Savannah Mall", pickup, dest).getOrThrow()

        val openTrip = repo.createRideRequest(
            "Mall of the North", "Savannah Mall", pickup, dest,
            RideCategory.STANDARD, null, null, PaymentMethod.CASH, route
        ).getOrThrow()
        val expiredTrip = repo.expireOpenTrip(openTrip.id).getOrThrow()
        assertEquals(RideStatus.CANCELLED, expiredTrip.status)

        serverRole = "driver"
        repo.refreshActiveSessionData().getOrThrow()
        val onlineRes = repo.setDriverOnline(false).getOrThrow()
        assertEquals(false, onlineRes)

        serverTrips["trip-unfulfilled"] = openTrip.copy(
            id = "trip-unfulfilled",
            status = RideStatus.UNFULFILLED,
            unfulfillmentReason = "Driver went offline mid-trip"
        )
        repo.refreshActiveSessionData().getOrThrow()
        val cachedTrips = repo.trips.first()
        val synced = cachedTrips.firstOrNull { it.id == "trip-unfulfilled" }
        assertNotNull(synced)
        assertEquals(RideStatus.UNFULFILLED, synced?.status)
    }

    @Test
    fun `offline failure retains active trip and queues actions until connection returns`() = runBlocking {
        seedSession(RideRole.RIDER)
        val onlineRepo = newRepository()
        val pickup = RideCoordinates(-23.8768, 29.4979)
        val dest = RideCoordinates(-23.9038, 29.4857)
        val route = onlineRepo.previewRoute("Mall of the North", "Savannah Mall", pickup, dest).getOrThrow()
        val trip = onlineRepo.createRideRequest(
            "Mall of the North", "Savannah Mall", pickup, dest,
            RideCategory.STANDARD, null, null, PaymentMethod.CASH, route
        ).getOrThrow()

        val closedPort = ServerSocket(0).use { it.localPort }
        val offlineRepo = newRepository(baseUrl = "http://127.0.0.1:$closedPort")

        val msgRes = offlineRepo.sendTripMessage(trip.id, "Waiting at gate 2")
        assertTrue(msgRes.isFailure)
        val supRes = offlineRepo.createSupportCase(trip, "safety", "urgent", "Driver stopped responding")
        assertTrue(supRes.isFailure)
        val rateRes = offlineRepo.rateTrip(trip.id, 4)
        assertTrue(rateRes.isFailure)
        offlineRepo.updateLiveDriverLocation(trip.id, RideCoordinates(-23.88, 29.49))
        offlineRepo.updateLiveDriverLocation(trip.id, RideCoordinates(-23.89, 29.50))

        // Driver location updates coalesce to 1, plus 3 queued actions = 4 total queued
        assertEquals(4, offlineRepo.pendingOfflineCount.value)
        // Cached active trip remains visible locally despite network outage
        assertTrue(offlineRepo.trips.first().any { it.id == trip.id && it.status.isActive })

        recordedActionBodies.clear()
        recordedLocationUpdates.clear()
        val recoveryRepo = newRepository()
        val flushed = recoveryRepo.flushOfflineQueue()
        assertEquals(4, flushed)
        assertEquals(0, recoveryRepo.pendingOfflineCount.value)
        assertEquals(3, recordedActionBodies.size)
        assertEquals(1, recordedLocationUpdates.size)
        assertTrue(recordedLocationUpdates.first().contains("-23.89"))
    }

    @Test
    fun `post trip issue reporting covers all categories and refund review`() = runBlocking {
        seedSession(RideRole.RIDER)
        val repo = newRepository()
        val pickup = RideCoordinates(-23.8768, 29.4979)
        val dest = RideCoordinates(-23.9038, 29.4857)
        val route = repo.previewRoute("Mall of the North", "Savannah Mall", pickup, dest).getOrThrow()
        val trip = repo.createRideRequest(
            "Mall of the North", "Savannah Mall", pickup, dest,
            RideCategory.STANDARD, null, null, PaymentMethod.CARD, route
        ).getOrThrow()

        for (cat in IssueCategory.entries) {
            repo.createSupportCase(
                trip = trip,
                category = cat.key,
                priority = cat.defaultPriority,
                message = "Issue details for ${cat.key}",
                requestRefund = (cat == IssueCategory.PAYMENT)
            ).getOrThrow()
        }

        val cases = repo.supportCases.first()
        assertEquals(IssueCategory.entries.size, cases.size)
        val paymentCase = cases.first { it.category == "payment" }
        assertEquals("high", paymentCase.priority)
        assertTrue(paymentCase.message.contains("Refund / Fare Review Requested"))
    }

    @Test
    fun `popia privacy controls export deletion ticket and local data wipe`() = runBlocking {
        seedSession(RideRole.RIDER)
        val repo = newRepository()
        val pickup = RideCoordinates(-23.8768, 29.4979)
        val dest = RideCoordinates(-23.9038, 29.4857)
        val route = repo.previewRoute("Mall of the North", "Savannah Mall", pickup, dest).getOrThrow()
        repo.createRideRequest(
            "Mall of the North", "Savannah Mall", pickup, dest,
            RideCategory.STANDARD, null, null, PaymentMethod.CASH, route
        ).getOrThrow()

        repo.toggleSavedDestination("Home", pickup)
        repo.addTrustedContact("Lerato", "+27821234567")
        repo.updatePrivacyPreferences(
            PrivacyPreferences(
                locationSharingConsent = false,
                tripOnlyPrivacyMode = true,
                supportNotificationsEnabled = false,
                popiaAcceptedAtEpochMs = 1_700_000_000_000L
            )
        )
        assertEquals(false, repo.privacyPreferences.value.locationSharingConsent)
        assertTrue(repo.privacyPreferences.value.tripOnlyPrivacyMode)

        val exportJson = repo.buildPopiaExportJson()
        assertTrue(exportJson.contains("vhaya-popia-export-v1"))
        assertTrue(exportJson.contains("Lerato"))
        assertTrue(exportJson.contains("Mall of the North"))

        val ticketCreated = repo.requestPopiaDataDeletion("Please delete my data under POPIA").getOrThrow()
        assertTrue(ticketCreated)

        repo.clearAllLocalUserDataAndSignOut()
        assertEquals(AuthState.Unauthenticated, repo.authState.value)
        assertTrue(repo.trips.first().isEmpty())
        assertTrue(repo.savedDestinations.first().isEmpty())
        assertTrue(repo.trustedContacts.first().isEmpty())
        assertEquals(0, repo.pendingOfflineCount.value)
    }
}
