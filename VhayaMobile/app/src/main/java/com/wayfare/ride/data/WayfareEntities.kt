package com.wayfare.ride.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import com.wayfare.ride.domain.ActivityLogEntry
import com.wayfare.ride.domain.DriverApplication
import com.wayfare.ride.domain.DriverComplianceCheck
import com.wayfare.ride.domain.DriverPayoutRecord
import com.wayfare.ride.domain.DriverProfileSnapshot
import com.wayfare.ride.domain.PaymentMethod
import com.wayfare.ride.domain.PaymentStatus
import com.wayfare.ride.domain.PayoutBatchRecord
import com.wayfare.ride.domain.PricingMode
import com.wayfare.ride.domain.RideCategory
import com.wayfare.ride.domain.RideCoordinates
import com.wayfare.ride.domain.RideMessage
import com.wayfare.ride.domain.RideOffer
import com.wayfare.ride.domain.RideRole
import com.wayfare.ride.domain.RideRoute
import com.wayfare.ride.domain.RideStatus
import com.wayfare.ride.domain.RideTrip
import com.wayfare.ride.domain.SavedDestination
import com.wayfare.ride.domain.SupportCase
import com.wayfare.ride.domain.SupportReply
import com.wayfare.ride.domain.TrustedContact
import com.wayfare.ride.domain.UserAccountSummary
import com.wayfare.ride.domain.WalletTransaction
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val roomJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    coerceInputValues = true
}

private inline fun <reified T : Enum<T>> parseEnum(raw: String, fallback: T): T {
    val normalized = raw.trim().uppercase()
    return enumValues<T>().firstOrNull { it.name == normalized } ?: fallback
}

@Entity(tableName = "trips")
data class TripEntity(
    @PrimaryKey val id: String,
    val riderId: String,
    val driverId: String?,
    val pickup: String,
    val destination: String,
    val category: String,
    val currency: String,
    val fare: Double,
    val pricingMode: String,
    val status: String,
    val paymentStatus: String,
    val paymentMethod: String = "CARD",
    val paymentReference: String?,
    val createdAt: Long,
    val pickupAt: Long?,
    val expiresAt: Long? = null,
    val pickupCoordinatesJson: String?,
    val destinationCoordinatesJson: String?,
    val routeJson: String?,
    val offersJson: String,
    val messagesJson: String,
    val riderRating: Int?,
    val riderRatingTagsJson: String = "[]",
    val tripPin: String?,
    val arrivalConfirmationMethod: String?,
    val arrivalConfirmedAt: Long?,
    val driverProfileJson: String?,
    val liveDriverLocationJson: String?,
    val unfulfillmentReason: String?
)

@Entity(tableName = "saved_destinations")
data class SavedDestinationEntity(
    @PrimaryKey val id: String,
    val label: String,
    val latitude: Double,
    val longitude: Double,
    val savedAt: Long
)

@Entity(tableName = "trusted_contacts")
data class TrustedContactEntity(
    @PrimaryKey val id: String,
    val name: String,
    val phone: String
)

@Entity(tableName = "wallet_transactions")
data class WalletTransactionEntity(
    @PrimaryKey val id: String,
    val type: String,
    val amountMinor: Long,
    val currency: String,
    val reference: String,
    val description: String,
    val createdAt: Long
)

@Entity(tableName = "driver_applications")
data class DriverApplicationEntity(
    @PrimaryKey val userId: String,
    val displayName: String,
    val email: String?,
    val contactPhone: String,
    val city: String,
    val applicationStatus: String,
    val verificationStatus: String,
    val vehicle: String,
    val vehicleColor: String,
    val registration: String,
    val licenceExpiry: String,
    val operatingLicenceReference: String,
    val isOnline: Boolean,
    val documentsUploadedJson: String,
    val documentsConsent: Boolean,
    val staffNote: String?,
    val submittedAt: Long?,
    val reviewedAt: Long?,
    val complianceChecksJson: String
)

@Entity(tableName = "support_cases")
data class SupportCaseEntity(
    @PrimaryKey val id: Long,
    val rideId: String,
    val reporterName: String,
    val category: String,
    val priority: String,
    val status: String,
    val assignedTo: String?,
    val staffNote: String?,
    val message: String,
    val pickup: String,
    val destination: String,
    val rideStatus: String,
    val createdAt: Long,
    val updatedAt: Long,
    val repliesJson: String
)

@Entity(tableName = "payout_records")
data class PayoutRecordEntity(
    @PrimaryKey val id: Long,
    val driverId: String,
    val driverName: String,
    val provider: String,
    val externalReference: String,
    val amountMinor: Long,
    val currency: String,
    val periodLabel: String,
    val periodStart: String?,
    val periodEnd: String?,
    val reconciliationStatus: String,
    val note: String?,
    val recordedAt: Long,
    val reconciledAt: Long?
)

@Entity(tableName = "user_accounts")
data class UserAccountEntity(
    @PrimaryKey val userId: String,
    val displayName: String,
    val email: String,
    val role: String,
    val isSuspended: Boolean,
    val suspensionReason: String?,
    val rideCount: Int,
    val completedRideCount: Int
)

@Entity(tableName = "payout_batches")
data class PayoutBatchEntity(
    @PrimaryKey val id: String,
    val periodLabel: String,
    val periodStart: Long?,
    val periodEnd: Long?,
    val status: String,
    val totalAmountMinor: Long,
    val driverCount: Int,
    val createdAt: Long,
    val paidAt: Long?
)

@Entity(tableName = "activity_logs")
data class ActivityLogEntity(
    @PrimaryKey val id: String,
    val kind: String,
    val actor: String,
    val subject: String,
    val summary: String,
    val createdAt: Long
)

class WayfareTypeConverters {
    @TypeConverter
    fun fromStringList(list: List<String>): String = roomJson.encodeToString(list)

    @TypeConverter
    fun toStringList(value: String): List<String> =
        runCatching { roomJson.decodeFromString<List<String>>(value) }.getOrDefault(emptyList())
}

fun TripEntity.toDomain(): RideTrip = RideTrip(
    id = id,
    riderId = riderId,
    driverId = driverId,
    pickup = pickup,
    destination = destination,
    category = parseEnum(category, RideCategory.STANDARD),
    currency = currency,
    fare = fare,
    pricingMode = parseEnum(pricingMode, PricingMode.ESTIMATE),
    status = parseEnum(status, RideStatus.REQUESTED),
    paymentStatus = parseEnum(paymentStatus, PaymentStatus.NONE),
    paymentMethod = parseEnum(paymentMethod, PaymentMethod.CARD),
    paymentReference = paymentReference,
    createdAt = createdAt,
    pickupAt = pickupAt,
    expiresAt = expiresAt,
    pickupCoordinates = pickupCoordinatesJson?.let { runCatching { roomJson.decodeFromString<RideCoordinates>(it) }.getOrNull() },
    destinationCoordinates = destinationCoordinatesJson?.let { runCatching { roomJson.decodeFromString<RideCoordinates>(it) }.getOrNull() },
    route = routeJson?.let { runCatching { roomJson.decodeFromString<RideRoute>(it) }.getOrNull() },
    offers = runCatching { roomJson.decodeFromString<List<RideOffer>>(offersJson) }.getOrDefault(emptyList()),
    messages = runCatching { roomJson.decodeFromString<List<RideMessage>>(messagesJson) }.getOrDefault(emptyList()),
    riderRating = riderRating,
    riderRatingTags = runCatching { roomJson.decodeFromString<List<String>>(riderRatingTagsJson) }.getOrDefault(emptyList()),
    tripPin = tripPin,
    arrivalConfirmationMethod = arrivalConfirmationMethod,
    arrivalConfirmedAt = arrivalConfirmedAt,
    driverProfile = driverProfileJson?.let { runCatching { roomJson.decodeFromString<DriverProfileSnapshot>(it) }.getOrNull() },
    liveDriverLocation = liveDriverLocationJson?.let { runCatching { roomJson.decodeFromString<RideCoordinates>(it) }.getOrNull() },
    unfulfillmentReason = unfulfillmentReason
)

fun RideTrip.toEntity(): TripEntity = TripEntity(
    id = id,
    riderId = riderId,
    driverId = driverId,
    pickup = pickup,
    destination = destination,
    category = category.name,
    currency = currency,
    fare = fare,
    pricingMode = pricingMode.name,
    status = status.name,
    paymentStatus = paymentStatus.name,
    paymentMethod = paymentMethod.name,
    paymentReference = paymentReference,
    createdAt = createdAt,
    pickupAt = pickupAt,
    expiresAt = expiresAt,
    pickupCoordinatesJson = pickupCoordinates?.let { roomJson.encodeToString(it) },
    destinationCoordinatesJson = destinationCoordinates?.let { roomJson.encodeToString(it) },
    routeJson = route?.let { roomJson.encodeToString(it) },
    offersJson = roomJson.encodeToString(offers),
    messagesJson = roomJson.encodeToString(messages),
    riderRating = riderRating,
    riderRatingTagsJson = roomJson.encodeToString(riderRatingTags),
    tripPin = tripPin,
    arrivalConfirmationMethod = arrivalConfirmationMethod,
    arrivalConfirmedAt = arrivalConfirmedAt,
    driverProfileJson = driverProfile?.let { roomJson.encodeToString(it) },
    liveDriverLocationJson = liveDriverLocation?.let { roomJson.encodeToString(it) },
    unfulfillmentReason = unfulfillmentReason
)

fun SavedDestinationEntity.toDomain() = SavedDestination(id, label, latitude, longitude, savedAt)
fun SavedDestination.toEntity() = SavedDestinationEntity(id, label, latitude, longitude, savedAt)

fun TrustedContactEntity.toDomain() = TrustedContact(id, name, phone)
fun TrustedContact.toEntity() = TrustedContactEntity(id, name, phone)

fun WalletTransactionEntity.toDomain() = WalletTransaction(id, type, amountMinor, currency, reference, description, createdAt)
fun WalletTransaction.toEntity() = WalletTransactionEntity(id, type, amountMinor, currency, reference, description, createdAt)

fun DriverApplicationEntity.toDomain() = DriverApplication(
    userId = userId,
    displayName = displayName,
    email = email,
    contactPhone = contactPhone,
    city = city,
    applicationStatus = applicationStatus,
    verificationStatus = verificationStatus,
    vehicle = vehicle,
    vehicleColor = vehicleColor,
    registration = registration,
    licenceExpiry = licenceExpiry,
    operatingLicenceReference = operatingLicenceReference,
    isOnline = isOnline,
    documentsUploaded = runCatching { roomJson.decodeFromString<List<String>>(documentsUploadedJson) }.getOrDefault(emptyList()),
    documentsConsent = documentsConsent,
    staffNote = staffNote,
    submittedAt = submittedAt,
    reviewedAt = reviewedAt,
    complianceChecks = runCatching { roomJson.decodeFromString<List<DriverComplianceCheck>>(complianceChecksJson) }.getOrDefault(emptyList())
)

fun DriverApplication.toEntity() = DriverApplicationEntity(
    userId = userId,
    displayName = displayName,
    email = email,
    contactPhone = contactPhone,
    city = city,
    applicationStatus = applicationStatus,
    verificationStatus = verificationStatus,
    vehicle = vehicle,
    vehicleColor = vehicleColor,
    registration = registration,
    licenceExpiry = licenceExpiry,
    operatingLicenceReference = operatingLicenceReference,
    isOnline = isOnline,
    documentsUploadedJson = roomJson.encodeToString(documentsUploaded),
    documentsConsent = documentsConsent,
    staffNote = staffNote,
    submittedAt = submittedAt,
    reviewedAt = reviewedAt,
    complianceChecksJson = roomJson.encodeToString(complianceChecks)
)

fun SupportCaseEntity.toDomain() = SupportCase(
    id = id,
    rideId = rideId,
    reporterName = reporterName,
    category = category,
    priority = priority,
    status = status,
    assignedTo = assignedTo,
    staffNote = staffNote,
    message = message,
    pickup = pickup,
    destination = destination,
    rideStatus = rideStatus,
    createdAt = createdAt,
    updatedAt = updatedAt,
    replies = runCatching { roomJson.decodeFromString<List<SupportReply>>(repliesJson) }.getOrDefault(emptyList())
)

fun SupportCase.toEntity() = SupportCaseEntity(
    id = id,
    rideId = rideId,
    reporterName = reporterName,
    category = category,
    priority = priority,
    status = status,
    assignedTo = assignedTo,
    staffNote = staffNote,
    message = message,
    pickup = pickup,
    destination = destination,
    rideStatus = rideStatus,
    createdAt = createdAt,
    updatedAt = updatedAt,
    repliesJson = roomJson.encodeToString(replies)
)

fun PayoutRecordEntity.toDomain() = DriverPayoutRecord(
    id = id,
    driverId = driverId,
    driverName = driverName,
    provider = provider,
    externalReference = externalReference,
    amountMinor = amountMinor,
    currency = currency,
    periodLabel = periodLabel,
    periodStart = periodStart,
    periodEnd = periodEnd,
    reconciliationStatus = reconciliationStatus,
    note = note,
    recordedAt = recordedAt,
    reconciledAt = reconciledAt
)

fun DriverPayoutRecord.toEntity() = PayoutRecordEntity(
    id = id,
    driverId = driverId,
    driverName = driverName,
    provider = provider,
    externalReference = externalReference,
    amountMinor = amountMinor,
    currency = currency,
    periodLabel = resolvedPeriodLabel,
    periodStart = periodStart,
    periodEnd = periodEnd,
    reconciliationStatus = reconciliationStatus,
    note = note,
    recordedAt = recordedAt,
    reconciledAt = reconciledAt
)

fun UserAccountEntity.toDomain() = UserAccountSummary(
    userId = userId,
    displayName = displayName,
    email = email,
    role = parseEnum(role, RideRole.RIDER),
    isSuspended = isSuspended,
    suspensionReason = suspensionReason,
    rideCount = rideCount,
    completedRideCount = completedRideCount
)

fun UserAccountSummary.toEntity() = UserAccountEntity(
    userId = userId,
    displayName = displayName,
    email = email,
    role = role.name,
    isSuspended = isSuspended,
    suspensionReason = suspensionReason,
    rideCount = rideCount,
    completedRideCount = completedRideCount
)

fun PayoutBatchEntity.toDomain() = PayoutBatchRecord(
    id = id,
    periodLabel = periodLabel,
    periodStart = periodStart,
    periodEnd = periodEnd,
    status = status,
    totalAmountMinor = totalAmountMinor,
    driverCount = driverCount,
    createdAt = createdAt,
    paidAt = paidAt
)

fun PayoutBatchRecord.toEntity() = PayoutBatchEntity(
    id = id,
    periodLabel = resolvedPeriodLabel,
    periodStart = periodStart,
    periodEnd = periodEnd,
    status = status,
    totalAmountMinor = totalAmountMinor,
    driverCount = driverCount,
    createdAt = createdAt,
    paidAt = paidAt
)

fun ActivityLogEntity.toDomain() = ActivityLogEntry(
    id = id,
    kind = kind,
    actor = actor,
    subject = subject,
    summary = summary,
    createdAt = createdAt
)

fun ActivityLogEntry.toEntity() = ActivityLogEntity(
    id = id,
    kind = kind,
    actor = actor,
    subject = subject,
    summary = summary,
    createdAt = createdAt
)
