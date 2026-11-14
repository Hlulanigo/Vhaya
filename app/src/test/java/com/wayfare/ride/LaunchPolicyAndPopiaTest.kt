package com.wayfare.ride

import android.Manifest
import android.app.Application
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.wayfare.ride.domain.DriverProfileSnapshot
import com.wayfare.ride.domain.IssueCategory
import com.wayfare.ride.domain.LaunchPolicy
import com.wayfare.ride.domain.LocationPermissionState
import com.wayfare.ride.domain.PaymentMethod
import com.wayfare.ride.domain.PopiaDataExporter
import com.wayfare.ride.domain.PricingMode
import com.wayfare.ride.domain.PrivacyPreferences
import com.wayfare.ride.domain.RideCategory
import com.wayfare.ride.domain.RideCoordinates
import com.wayfare.ride.domain.RideRole
import com.wayfare.ride.domain.RideStatus
import com.wayfare.ride.domain.RideTrip
import com.wayfare.ride.domain.SavedDestination
import com.wayfare.ride.domain.SupportCase
import com.wayfare.ride.domain.SupportReply
import com.wayfare.ride.domain.TrustedContact
import com.wayfare.ride.domain.UserSession
import com.wayfare.ride.domain.WalletTransaction
import com.wayfare.ride.notifications.SupportNotificationHelper
import com.wayfare.ride.ui.components.alertTrustedContacts
import com.wayfare.ride.ui.components.evaluateLocationPermissionState
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LaunchPolicyAndPopiaTest {

    private fun sampleTrip(
        status: RideStatus = RideStatus.REQUESTED,
        createdAt: Long = 1_000_000L,
        expiresAt: Long? = null,
        driverId: String? = null
    ) = RideTrip(
        id = "trip-101",
        riderId = "rider-1",
        driverId = driverId,
        pickup = "Mall of the North · Bendor",
        destination = "Savannah Mall · Fauna Park",
        category = RideCategory.STANDARD,
        fare = 80.0,
        pricingMode = PricingMode.ESTIMATE,
        status = status,
        paymentMethod = PaymentMethod.CARD,
        createdAt = createdAt,
        expiresAt = expiresAt,
        pickupCoordinates = RideCoordinates(-23.8768, 29.4979),
        destinationCoordinates = RideCoordinates(-23.9038, 29.4857),
        tripPin = "482910",
        driverProfile = DriverProfileSnapshot(
            id = "driver-1",
            name = "Thabo Mokoena",
            vehicle = "Toyota Corolla Quest",
            vehicleColor = "White",
            registration = "ABC 123 GP"
        )
    )

    @Test
    fun `launch policy fare calculation arrival radius ttl and registration formatting`() {
        assertEquals(20.0, LaunchPolicy.calculateEstimate(500, RideCategory.STANDARD), 0.01)
        assertEquals(100.0, LaunchPolicy.calculateEstimate(5_000, RideCategory.COMFORT), 0.01)
        assertEquals("ABC 123 GP", LaunchPolicy.formatSouthAfricanRegistration("abc123gp"))

        val openTrip = sampleTrip(status = RideStatus.REQUESTED, createdAt = 10_000L)
        assertFalse(LaunchPolicy.isTripExpired(openTrip, nowMs = 10_000L + 60_000L))
        assertTrue(LaunchPolicy.isTripExpired(openTrip, nowMs = 10_000L + LaunchPolicy.OPEN_REQUEST_TTL_MS))

        val assignedTrip = sampleTrip(status = RideStatus.ACCEPTED, createdAt = 10_000L, driverId = "driver-1")
        assertFalse(LaunchPolicy.isTripExpired(assignedTrip, nowMs = 10_000L + LaunchPolicy.OPEN_REQUEST_TTL_MS + 1000L))
        assertTrue(LaunchPolicy.canRiderCancel(RideStatus.REQUESTED))
        assertTrue(LaunchPolicy.canRiderCancel(RideStatus.ARRIVED))
        assertFalse(LaunchPolicy.canRiderCancel(RideStatus.IN_PROGRESS))

        val p1 = RideCoordinates(-23.9045, 29.4689)
        val p2 = RideCoordinates(-23.9052, 29.4689)
        assertTrue(LaunchPolicy.distanceMeters(p1, p2) <= LaunchPolicy.ARRIVAL_RADIUS_METERS)
    }

    @Test
    fun `issue categories cover all five backend support types`() {
        val keys = IssueCategory.entries.map { it.key }.toSet()
        assertEquals(setOf("safety", "payment", "driver", "lost_item", "other"), keys)
        assertEquals(IssueCategory.SAFETY, IssueCategory.fromKey("safety"))
        assertEquals(IssueCategory.LOST_ITEM, IssueCategory.fromKey("LOST_ITEM"))
        assertEquals(IssueCategory.OTHER, IssueCategory.fromKey("unknown_cat"))
    }

    @Test
    fun `popia data exporter serializes complete user bundle and retention schedule`() {
        val session = UserSession(
            userId = "u-1",
            displayName = "Nomsa Dlamini",
            email = "nomsa@example.co.za",
            role = RideRole.RIDER
        )
        val prefs = PrivacyPreferences(
            locationSharingConsent = false,
            tripOnlyPrivacyMode = true,
            supportNotificationsEnabled = true,
            popiaAcceptedAtEpochMs = 1_700_000_000_000L
        )
        val jsonText = PopiaDataExporter.buildExportJson(
            session = session,
            privacyPreferences = prefs,
            trips = listOf(sampleTrip(status = RideStatus.COMPLETED)),
            savedDestinations = listOf(SavedDestination("s1", "Home", -23.9, 29.47, 123L)),
            trustedContacts = listOf(TrustedContact("tc1", "Lerato", "0821234567")),
            walletTransactions = listOf(WalletTransaction("w1", "topup", 15_000L, "ZAR", "ref-1", "Top-up", 123L)),
            supportCases = listOf(
                SupportCase(
                    id = 42L,
                    rideId = "trip-101",
                    category = "lost_item",
                    priority = "normal",
                    status = "open",
                    message = "Left keys",
                    createdAt = 123L,
                    updatedAt = 123L
                )
            ),
            nowEpochMs = 1_700_000_000_000L
        )
        val root = Json.parseToJsonElement(jsonText).jsonObject
        assertEquals("vhaya-popia-export-v1", root["schemaVersion"]?.jsonPrimitive?.content)
        assertEquals(false, root["privacyPreferences"]?.jsonObject?.get("locationSharingConsent")?.jsonPrimitive?.content?.toBoolean())
        assertEquals(1, root["trips"]?.jsonArray?.size)
        assertEquals(1, root["savedDestinations"]?.jsonArray?.size)
        assertEquals(1, root["trustedContacts"]?.jsonArray?.size)
        assertEquals(1, root["walletTransactions"]?.jsonArray?.size)
        assertEquals(1, root["supportCases"]?.jsonArray?.size)
        assertTrue(PopiaDataExporter.RETENTION_SCHEDULE.size >= 4)
    }

    @Test
    fun `support notification diffing detects staff replies status transitions and staff notes`() {
        val baseCase = SupportCase(
            id = 77L,
            rideId = "trip-101",
            category = "payment",
            priority = "high",
            status = "open",
            message = "Fare review",
            createdAt = 100L,
            updatedAt = 100L,
            replies = emptyList()
        )
        assertTrue(SupportNotificationHelper.diffSupportUpdates(emptyList(), listOf(baseCase)).isEmpty())

        val withRiderReply = baseCase.copy(
            replies = listOf(SupportReply("r1", 77L, "Requester", "Checking in", 200L))
        )
        assertTrue(SupportNotificationHelper.diffSupportUpdates(listOf(baseCase), listOf(withRiderReply)).isEmpty())

        val withStaffReply = withRiderReply.copy(
            status = "in_progress",
            replies = withRiderReply.replies + SupportReply("r2", 77L, "Vhaya Support", "Refund approved", 300L)
        )
        val replyAlerts = SupportNotificationHelper.diffSupportUpdates(listOf(withRiderReply), listOf(withStaffReply))
        assertEquals(1, replyAlerts.size)
        assertTrue(replyAlerts.first().isStaffReply)
        assertEquals("Refund approved", replyAlerts.first().summary)

        val resolvedCase = withStaffReply.copy(status = "resolved")
        val statusAlerts = SupportNotificationHelper.diffSupportUpdates(listOf(withStaffReply), listOf(resolvedCase))
        assertEquals(1, statusAlerts.size)
        assertFalse(statusAlerts.first().isStaffReply)
        assertTrue(statusAlerts.first().title.contains("resolved"))

        val noteUpdatedCase = resolvedCase.copy(staffNote = "Payout batch #9 settled")
        val noteAlerts = SupportNotificationHelper.diffSupportUpdates(listOf(resolvedCase), listOf(noteUpdatedCase))
        assertEquals(1, noteAlerts.size)
        assertEquals("Payout batch #9 settled", noteAlerts.first().summary)
    }

    @Test
    fun `location permission evaluator handles all four Android states`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val shadowApp = Shadows.shadowOf(app)

        shadowApp.denyPermissions(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        assertEquals(
            LocationPermissionState.DENIED_CAN_RETRY,
            evaluateLocationPermissionState(app, shouldShowRationale = false, hasRequestedBefore = false)
        )
        assertEquals(
            LocationPermissionState.DENIED_CAN_RETRY,
            evaluateLocationPermissionState(app, shouldShowRationale = true, hasRequestedBefore = true)
        )
        assertEquals(
            LocationPermissionState.DENIED_PERMANENTLY,
            evaluateLocationPermissionState(app, shouldShowRationale = false, hasRequestedBefore = true)
        )

        shadowApp.grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION)
        assertEquals(
            LocationPermissionState.GRANTED_COARSE,
            evaluateLocationPermissionState(app, shouldShowRationale = false, hasRequestedBefore = true)
        )

        shadowApp.grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
        assertEquals(
            LocationPermissionState.GRANTED_FINE,
            evaluateLocationPermissionState(app, shouldShowRationale = false, hasRequestedBefore = true)
        )
    }

    @Test
    fun `alert trusted contacts dispatches sms or share intent with safety summary`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val shadowApp = Shadows.shadowOf(app)
        val trip = sampleTrip(status = RideStatus.IN_PROGRESS, driverId = "driver-1")

        assertFalse(alertTrustedContacts(app, trip, emptyList()))

        val contacts = listOf(TrustedContact("tc1", "Lerato", "+27821234567"))
        val started = alertTrustedContacts(app, trip, contacts)
        assertTrue(started)

        val launchedIntent: Intent? = shadowApp.nextStartedActivity
        assertNotNull(launchedIntent)
        val nested = launchedIntent?.let {
            androidx.core.content.IntentCompat.getParcelableExtra(it, Intent.EXTRA_INTENT, Intent::class.java)
        }
        val body = launchedIntent?.getStringExtra("sms_body")
            ?: nested?.getStringExtra(Intent.EXTRA_TEXT)
            ?: launchedIntent?.getStringExtra(Intent.EXTRA_TEXT)
        assertNotNull(body)
        assertTrue(body!!.contains("VHAYA SAFETY ALERT"))
        assertTrue(body.contains("ABC 123 GP"))
    }
}
