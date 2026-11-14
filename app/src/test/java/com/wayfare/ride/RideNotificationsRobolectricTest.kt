package com.wayfare.ride

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.FrameLayout
import android.widget.TextView
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.sun.net.httpserver.HttpServer
import com.wayfare.ride.data.SessionStore
import com.wayfare.ride.data.VhayaApiClient
import com.wayfare.ride.data.WayfareDatabase
import com.wayfare.ride.data.WayfareRepository
import com.wayfare.ride.domain.DriverProfileSnapshot
import com.wayfare.ride.domain.PaymentMethod
import com.wayfare.ride.domain.PaymentStatus
import com.wayfare.ride.domain.PricingMode
import com.wayfare.ride.domain.RideCategory
import com.wayfare.ride.domain.RideCoordinates
import com.wayfare.ride.domain.RideOffer
import com.wayfare.ride.domain.RideRole
import com.wayfare.ride.domain.RideRoute
import com.wayfare.ride.domain.RideStatus
import com.wayfare.ride.domain.RideTrip
import com.wayfare.ride.domain.UserSession
import com.wayfare.ride.notifications.RideNotificationActionReceiver
import com.wayfare.ride.notifications.RideNotificationHelper
import com.wayfare.ride.ui.WayfareViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.net.InetSocketAddress

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RideNotificationsRobolectricTest {

    private lateinit var app: Application
    private lateinit var db: WayfareDatabase
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        app.getSharedPreferences("vhaya_session_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        db = Room.inMemoryDatabaseBuilder(app, WayfareDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        Shadows.shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun sampleTrip(
        id: String = "trip-notif-1",
        status: RideStatus = RideStatus.REQUESTED,
        fare: Double = 95.0,
        offers: List<RideOffer> = emptyList(),
        driverId: String? = null
    ) = RideTrip(
        id = id,
        riderId = "rider-1",
        driverId = driverId,
        pickup = "Mall of the North · Bendor",
        destination = "Savannah Mall · Fauna Park",
        category = RideCategory.STANDARD,
        fare = fare,
        pricingMode = PricingMode.ESTIMATE,
        status = status,
        paymentMethod = PaymentMethod.CARD,
        paymentStatus = PaymentStatus.AUTHORIZED,
        createdAt = System.currentTimeMillis(),
        pickupCoordinates = RideCoordinates(-23.8768, 29.4979),
        destinationCoordinates = RideCoordinates(-23.9038, 29.4857),
        route = RideRoute(distanceMeters = 4200, durationSeconds = 540),
        offers = offers,
        tripPin = if (driverId != null) "482910" else null,
        driverProfile = if (driverId != null) {
            DriverProfileSnapshot(
                id = driverId,
                name = "Thabo Mokoena",
                vehicle = "Toyota Corolla Quest",
                vehicleColor = "White",
                registration = "ABC 123 GP"
            )
        } else null
    )

    @Test
    fun `driver offer notification card exposes accept and decline actions and channels`() {
        RideNotificationHelper.ensureChannels(app)
        val nm = RideNotificationHelper.notificationManager(app)
        assertNotNull(nm)
        assertNotNull(nm!!.getNotificationChannel(RideNotificationHelper.CHANNEL_DRIVER_OFFERS))
        assertNotNull(nm.getNotificationChannel(RideNotificationHelper.CHANNEL_RIDER_STATUS))
        assertNotNull(nm.getNotificationChannel(RideNotificationHelper.CHANNEL_LIVE_DUTY))

        val trip = sampleTrip(id = "trip-offer-77", fare = 120.0)
        val spec = RideNotificationHelper.buildDriverOfferSpec(trip)
        assertTrue(spec.title.contains("120"))
        assertTrue(spec.title.contains("4.2 km"))
        assertTrue(spec.bigText.contains("Mall of the North"))
        assertTrue(spec.bigText.contains("Savannah Mall"))

        val notif = RideNotificationHelper.buildDriverOfferNotification(app, trip)
        assertTrue(notif.actions == null || notif.actions.isEmpty())
        @Suppress("DEPRECATION")
        val contentView = notif.contentView
        @Suppress("DEPRECATION")
        val bigContentView = notif.bigContentView
        assertNotNull(contentView)
        assertNotNull(bigContentView)

        val collapsedRoot = contentView.apply(app, FrameLayout(app))
        val collapsedFare = collapsedRoot.findViewById<TextView>(R.id.notif_collapsed_fare)
        val collapsedRoute = collapsedRoot.findViewById<TextView>(R.id.notif_collapsed_route)
        assertTrue(collapsedFare.text.toString().contains("120"))
        assertEquals("Mall of the North → Savannah Mall", collapsedRoute.text.toString())

        val expandedRoot = bigContentView.apply(app, FrameLayout(app))
        val expandedFare = expandedRoot.findViewById<TextView>(R.id.notif_expanded_fare)
        val expandedMeta = expandedRoot.findViewById<TextView>(R.id.notif_expanded_meta)
        val expandedDist = expandedRoot.findViewById<TextView>(R.id.notif_expanded_distance)
        val expandedPickup = expandedRoot.findViewById<TextView>(R.id.notif_expanded_pickup)
        val expandedDest = expandedRoot.findViewById<TextView>(R.id.notif_expanded_destination)
        val acceptBtn = expandedRoot.findViewById<TextView>(R.id.notif_btn_accept)
        val declineBtn = expandedRoot.findViewById<TextView>(R.id.notif_btn_decline)

        assertTrue(expandedFare.text.toString().contains("120"))
        assertEquals("Standard · Card", expandedMeta.text.toString())
        assertEquals("4.2 km", expandedDist.text.toString())
        assertEquals("Mall of the North", expandedPickup.text.toString())
        assertEquals("Savannah Mall", expandedDest.text.toString())
        assertEquals("Accept", acceptBtn.text.toString())
        assertEquals("Decline", declineBtn.text.toString())

        val shadowApp = Shadows.shadowOf(app)
        acceptBtn.performClick()
        val launchedAcceptIntent = shadowApp.nextStartedActivity
        assertNotNull(launchedAcceptIntent)
        val acceptUri = launchedAcceptIntent.data
        assertEquals("vhaya", acceptUri?.scheme)
        assertEquals("ride", acceptUri?.host)
        assertEquals("accept", acceptUri?.getQueryParameter("action"))
        assertEquals("trip-offer-77", acceptUri?.getQueryParameter("tripId"))

        declineBtn.performClick()
        val declineBroadcast = shadowApp.broadcastIntents.lastOrNull()
        assertNotNull(declineBroadcast)
        assertEquals(RideNotificationActionReceiver.ACTION_DECLINE_OFFER, declineBroadcast?.action)
        assertEquals(
            "trip-offer-77",
            declineBroadcast?.getStringExtra(RideNotificationActionReceiver.EXTRA_TRIP_ID)
        )
    }

    @Test
    fun `driver offer notification posts only when app is in background and dismisses cleanly`() {
        val nm = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val shadowNm = Shadows.shadowOf(nm)
        val trip = sampleTrip(id = "trip-bg-55", fare = 135.0)

        val postedInForeground = RideNotificationHelper.showDriverOfferNotificationIfBackground(
            context = app,
            trip = trip,
            isAppInForeground = true
        )
        assertFalse(postedInForeground)
        assertNull(shadowNm.getNotification(RideNotificationHelper.DRIVER_OFFER_NOTIF_ID))

        val postedInBackground = RideNotificationHelper.showDriverOfferNotificationIfBackground(
            context = app,
            trip = trip,
            isAppInForeground = false
        )
        assertTrue(postedInBackground)
        val activeNotif = shadowNm.getNotification(RideNotificationHelper.RIDER_STATUS_NOTIF_ID.let { RideNotificationHelper.DRIVER_OFFER_NOTIF_ID })
        assertNotNull(activeNotif)
        @Suppress("DEPRECATION")
        assertNotNull(activeNotif.bigContentView)

        RideNotificationHelper.dismissDriverOfferNotification(app)
        assertNull(shadowNm.getNotification(RideNotificationHelper.DRIVER_OFFER_NOTIF_ID))
    }

    @Test
    fun `rider single status notification updates in place across all stages`() {
        val nm = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val shadowNm = Shadows.shadowOf(nm)

        val tRequested = sampleTrip(status = RideStatus.REQUESTED)
        val specReq = RideNotificationHelper.buildRiderStatusSpec(tRequested, isFirstNotification = true)
        assertEquals(1, specReq.progressStep)
        assertTrue(specReq.ongoing)
        assertTrue(specReq.alertUser)
        RideNotificationHelper.showOrUpdateRiderStatusNotification(app, tRequested, null, 0, isFirstNotification = true)
        assertEquals(1, shadowNm.allNotifications.size)

        // Repeating same REQUESTED poll should stay silent
        val specReqRepeat = RideNotificationHelper.buildRiderStatusSpec(
            tRequested,
            isFirstNotification = false,
            previousStatus = RideStatus.REQUESTED,
            previousOfferCount = 0
        )
        assertFalse(specReqRepeat.alertUser)

        // COUNTERED (2/5)
        val driverSnap = DriverProfileSnapshot(id = "d-1", name = "Thabo", vehicle = "Polo", registration = "XYZ 999 L")
        val tCountered = tRequested.copy(
            status = RideStatus.COUNTERED,
            offers = listOf(RideOffer("off-1", tRequested.id, "d-1", 90.0, "pending", 10L, driverSnap))
        )
        val specCountered = RideNotificationHelper.buildRiderStatusSpec(tCountered, false, RideStatus.REQUESTED, 0)
        assertEquals(2, specCountered.progressStep)
        assertTrue(specCountered.alertUser)
        RideNotificationHelper.showOrUpdateRiderStatusNotification(app, tCountered, RideStatus.REQUESTED, 0, false)
        assertEquals(1, shadowNm.allNotifications.size)

        // ARRIVED (4/5)
        val tArrived = sampleTrip(status = RideStatus.ARRIVED, driverId = "d-1")
        val specArrived = RideNotificationHelper.buildRiderStatusSpec(tArrived, false, RideStatus.ACCEPTED, 0)
        assertEquals(4, specArrived.progressStep)
        assertTrue(specArrived.title.contains("482910"))
        assertTrue(specArrived.alertUser)
        RideNotificationHelper.showOrUpdateRiderStatusNotification(app, tArrived, RideStatus.ACCEPTED, 0, false)
        assertEquals(1, shadowNm.allNotifications.size)

        // COMPLETED (terminal, non-ongoing)
        val tCompleted = tArrived.copy(status = RideStatus.COMPLETED, paymentStatus = PaymentStatus.PAID)
        val specDone = RideNotificationHelper.buildRiderStatusSpec(tCompleted, false, RideStatus.IN_PROGRESS, 0)
        assertFalse(specDone.ongoing)
        assertEquals(0, specDone.progressMax)
        RideNotificationHelper.showOrUpdateRiderStatusNotification(app, tCompleted, RideStatus.IN_PROGRESS, 0, false)
        assertEquals(1, shadowNm.allNotifications.size)
        val posted = shadowNm.getNotification(RideNotificationHelper.RIDER_STATUS_NOTIF_ID)
        assertEquals(0, posted.flags and Notification.FLAG_ONGOING_EVENT)

        // CANCELLED (hold release message)
        val tCancelled = tRequested.copy(status = RideStatus.CANCELLED, paymentMethod = PaymentMethod.CARD)
        val specCancelled = RideNotificationHelper.buildRiderStatusSpec(tCancelled, false, RideStatus.REQUESTED, 0)
        assertFalse(specCancelled.ongoing)
        assertTrue(specCancelled.shortText.contains("Card authorization hold released"))
    }

    @Test
    fun `declining a ride request persists locally and promotes the next eligible offer`() {
        val store = SessionStore(app)
        val repo = WayfareRepository(app, db, store, VhayaApiClient())
        val first = sampleTrip(id = "trip-a", fare = 140.0)
        val second = sampleTrip(id = "trip-b", fare = 90.0)

        val chosenFirst = RideNotificationHelper.pickNextEligibleOffer(listOf(first, second), repo.declinedDriverTripIds.value)
        assertEquals("trip-a", chosenFirst?.id)

        repo.declineDriverRequest("trip-a")
        assertTrue("trip-a" in repo.declinedDriverTripIds.value)
        assertTrue("trip-a" in store.loadDeclinedTripIds())

        val promoted = RideNotificationHelper.pickNextEligibleOffer(listOf(first, second), repo.declinedDriverTripIds.value)
        assertEquals("trip-b", promoted?.id)

        repo.declineDriverRequest("trip-b")
        assertNull(RideNotificationHelper.pickNextEligibleOffer(listOf(first, second), repo.declinedDriverTripIds.value))
    }

    @Test
    fun `accept deep link from driver notification accepts ride on backend`() = runBlocking {
        var acceptedActionReceived = false
        val openTrip = sampleTrip(id = "trip-accept-99", fare = 110.0)
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            val path = exchange.requestURI.path
            val body = exchange.requestBody.bufferedReader().use { it.readText() }
            val response = when {
                path == "/api/rides" -> """{"role":"driver","trips":[${json.encodeToString(openTrip)}]}"""
                path == "/api/rides/trip-accept-99/actions" -> {
                    val action = json.parseToJsonElement(body).jsonObject["action"]?.jsonPrimitive?.content
                    if (action == "accept") acceptedActionReceived = true
                    val assigned = openTrip.copy(driverId = "driver-1", status = RideStatus.ACCEPTED, tripPin = "112233")
                    """{"trip":${json.encodeToString(assigned)}}"""
                }
                path == "/api/driver/profile" -> """{"profile":{"userId":"driver-1","displayName":"Thabo","verificationStatus":"approved","isOnline":true}}"""
                path == "/api/driver/earnings" -> """{"driverId":"driver-1"}"""
                path == "/api/driver/subscription" -> """{"subscription":null}"""
                else -> "{}"
            }
            val bytes = response.toByteArray(Charsets.UTF_8)
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()

        try {
            val port = server.address.port
            val store = SessionStore(app, apiBaseUrlOverride = "http://127.0.0.1:$port")
            store.saveSession(
                UserSession(
                    userId = "driver-1",
                    displayName = "Thabo",
                    email = "thabo@vhaya.co.za",
                    role = RideRole.DRIVER,
                    emailVerified = true,
                    idToken = "jwt-driver",
                    expiresAtEpochMs = System.currentTimeMillis() + 3_600_000L
                )
            )
            val repo = WayfareRepository(app, db, store, VhayaApiClient())
            val vmStore = androidx.lifecycle.ViewModelStore()
            val vm = androidx.lifecycle.ViewModelProvider(
                vmStore,
                WayfareViewModel.Factory(repo)
            )[WayfareViewModel::class.java]
            try {
                vm.handleDeepLink(Uri.parse("vhaya://ride?action=accept&tripId=trip-accept-99"))
                var attempts = 0
                while (!acceptedActionReceived && attempts < 80) {
                    Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
                    Thread.sleep(25)
                    attempts++
                }
                Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
                assertTrue(acceptedActionReceived)
                val cached = repo.getCachedTripsSnapshot().firstOrNull { it.id == "trip-accept-99" }
                assertEquals(RideStatus.ACCEPTED, cached?.status)
                assertEquals("driver-1", cached?.driverId)
            } finally {
                vmStore.clear()
            }
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `viewmodel initializes uiState cleanly without startup NPE for authenticated and unauthenticated sessions`() {
        val uncaughtErrors = mutableListOf<Throwable>()
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { _, e -> uncaughtErrors += e }
        try {
            // Unauthenticated cold start
            val storeUnauth = SessionStore(app, apiBaseUrlOverride = "http://127.0.0.1:1")
            val repoUnauth = WayfareRepository(app, db, storeUnauth, VhayaApiClient())
            val vmStore1 = androidx.lifecycle.ViewModelStore()
            val vm1 = androidx.lifecycle.ViewModelProvider(
                vmStore1,
                WayfareViewModel.Factory(repoUnauth)
            )[WayfareViewModel::class.java]
            Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            assertNotNull(vm1.uiState.value)
            assertEquals(RideRole.RIDER, vm1.uiState.value.activeRole)
            vmStore1.clear()

            // Authenticated driver cold start (previously threw NPE on uiState.value inside init ticker)
            val storeAuth = SessionStore(app, apiBaseUrlOverride = "http://127.0.0.1:1")
            storeAuth.saveSession(
                UserSession(
                    userId = "driver-cold-1",
                    displayName = "Thabo",
                    email = "thabo@vhaya.co.za",
                    role = RideRole.DRIVER,
                    emailVerified = true,
                    idToken = "jwt-driver",
                    expiresAtEpochMs = System.currentTimeMillis() + 3_600_000L
                )
            )
            val repoAuth = WayfareRepository(app, db, storeAuth, VhayaApiClient())
            val vmStore2 = androidx.lifecycle.ViewModelStore()
            val vm2 = androidx.lifecycle.ViewModelProvider(
                vmStore2,
                WayfareViewModel.Factory(repoAuth)
            )[WayfareViewModel::class.java]
            Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            assertNotNull(vm2.uiState.value)
            assertEquals(RideRole.DRIVER, vm2.uiState.value.activeRole)
            assertEquals("driver-cold-1", vm2.uiState.value.activeUserId)
            vmStore2.clear()

            assertTrue("Unexpected uncaught exceptions: $uncaughtErrors", uncaughtErrors.isEmpty())
        } finally {
            Thread.setDefaultUncaughtExceptionHandler(previousHandler)
        }
    }

    @Test
    fun `sound alert player synthesizes non-silent two-tone chime and plays safely`() {
        val pcm = com.wayfare.ride.audio.SoundAlertPlayer.generateChimePcm()
        assertTrue(pcm.size > 8000)
        assertTrue(pcm.any { it.toInt() != 0 })
        com.wayfare.ride.audio.SoundAlertPlayer.playRideOfferChime(app)
        com.wayfare.ride.audio.SoundAlertPlayer.playStatusUpdateChime(app)
    }

    @Test
    fun `live sync service satisfies foreground start contract when unauthenticated and on timeout`() {
        val controller = org.robolectric.Robolectric.buildService(com.wayfare.ride.service.VhayaLiveSyncService::class.java)
        val service = controller.create().get()
        val startIntent = Intent(app, com.wayfare.ride.service.VhayaLiveSyncService::class.java).apply {
            action = com.wayfare.ride.service.VhayaLiveSyncService.ACTION_START
            putExtra(
                com.wayfare.ride.service.VhayaLiveSyncService.EXTRA_MODE,
                com.wayfare.ride.service.VhayaLiveSyncService.MODE_DRIVER_ONLINE
            )
        }
        val result = service.onStartCommand(startIntent, 0, 1)
        assertEquals(android.app.Service.START_NOT_STICKY, result)
        assertFalse(com.wayfare.ride.service.VhayaLiveSyncService.isRunning.value)
        service.onTimeout(1, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        controller.destroy()
    }

    @Test
    fun `accepted card notification prompts payment until authorized`() {
        val unpaidAccepted = sampleTrip(
            id = "trip-pay-notif-1",
            status = RideStatus.ACCEPTED,
            fare = 95.0,
            driverId = "driver-1"
        ).copy(paymentMethod = PaymentMethod.CARD, paymentStatus = PaymentStatus.NONE)

        val unpaidSpec = RideNotificationHelper.buildRiderStatusSpec(unpaidAccepted, isFirstNotification = true)
        assertTrue(unpaidSpec.title.contains("accepted"))
        assertTrue(unpaidSpec.title.contains("Pay"))
        assertTrue(unpaidSpec.title.contains("95"))
        assertTrue(unpaidSpec.shortText.contains("Tap to complete payment"))

        val paidAccepted = unpaidAccepted.copy(paymentStatus = PaymentStatus.AUTHORIZED)
        val paidSpec = RideNotificationHelper.buildRiderStatusSpec(paidAccepted, isFirstNotification = false)
        assertEquals("Thabo Mokoena is on the way", paidSpec.title)
    }

    @Test
    fun `rider accepted card trip auto-triggers payment checkout once in foreground`() = runBlocking {
        var initializeCalls = 0
        var lastInitTripId: String? = null
        val acceptedUnpaidTrip = sampleTrip(
            id = "trip-autopay-1",
            status = RideStatus.ACCEPTED,
            fare = 95.0,
            driverId = "driver-1"
        ).copy(paymentMethod = PaymentMethod.CARD, paymentStatus = PaymentStatus.NONE)

        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            val path = exchange.requestURI.path
            val body = exchange.requestBody.bufferedReader().use { it.readText() }
            val response = when {
                path == "/api/rides" -> """{"role":"rider","trips":[${json.encodeToString(acceptedUnpaidTrip)}]}"""
                path == "/api/payments/initialize" -> {
                    initializeCalls++
                    lastInitTripId = json.parseToJsonElement(body).jsonObject["tripId"]?.jsonPrimitive?.content
                    """{"provider":"paystack","mode":"paystack","authorizationUrl":"https://checkout.paystack.com/vhaya_pay_95","payment":{"reference":"vhaya_pay_95","amountMinor":9500,"status":"pending"}}"""
                }
                path == "/api/wallet" -> """{"balanceMinor":0,"transactions":[]}"""
                else -> "{}"
            }
            val bytes = response.toByteArray(Charsets.UTF_8)
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()

        try {
            val port = server.address.port
            val store = SessionStore(app, apiBaseUrlOverride = "http://127.0.0.1:$port")
            store.saveSession(
                UserSession(
                    userId = "rider-1",
                    displayName = "Naledi",
                    email = "naledi@vhaya.co.za",
                    role = RideRole.RIDER,
                    emailVerified = true,
                    idToken = "jwt-rider",
                    expiresAtEpochMs = System.currentTimeMillis() + 3_600_000L
                )
            )
            val repo = WayfareRepository(app, db, store, VhayaApiClient())
            repo.setAppInForeground(true)

            val vmStore = androidx.lifecycle.ViewModelStore()
            val vm = androidx.lifecycle.ViewModelProvider(
                vmStore,
                WayfareViewModel.Factory(repo)
            )[WayfareViewModel::class.java]

            val emittedUrls = mutableListOf<String>()
            val collectJob = launch(Dispatchers.Unconfined) {
                vm.openUrlEvents.collect { emittedUrls += it }
            }

            try {
                vm.refreshNow()
                var attempts = 0
                while (emittedUrls.isEmpty() && attempts < 80) {
                    Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
                    Thread.sleep(25)
                    attempts++
                }
                Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
                assertEquals(1, initializeCalls)
                assertEquals("trip-autopay-1", lastInitTripId)
                assertEquals(listOf("https://checkout.paystack.com/vhaya_pay_95"), emittedUrls)

                // Subsequent poll for same unpaid trip must not repeatedly re-open checkout
                vm.refreshNow()
                repeat(10) {
                    Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
                    Thread.sleep(20)
                }
                assertEquals(1, initializeCalls)
            } finally {
                collectJob.cancel()
                vmStore.clear()
            }
        } finally {
            server.stop(0)
        }
    }
}
