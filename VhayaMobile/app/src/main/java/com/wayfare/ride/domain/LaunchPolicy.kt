package com.wayfare.ride.domain

import java.text.NumberFormat
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.sin
import kotlin.math.sqrt

object LaunchPolicy {
    const val CURRENCY = "ZAR"
    const val COUNTRY = "South Africa"
    const val RATE_PER_KM = 20.0
    const val ARRIVAL_RADIUS_METERS = 150.0
    const val MIN_RIDE_DURATION_SECONDS = 5 * 60
    const val CANCELLATION_FEE_PERCENT = 0.0
    const val DRIVER_DOCS_EMAIL = "hlulanigo@gmail.com"
    const val OPEN_REQUEST_TTL_MS = 5 * 60_000L

    val DEFAULT_MAP_CENTER = RideCoordinates(latitude = -23.9045, longitude = 29.4689)

    fun isWithinSouthAfrica(coords: RideCoordinates): Boolean {
        return coords.latitude in -35.5..-22.0 && coords.longitude in 16.0..33.5
    }

    fun formatZar(amount: Double): String {
        return try {
            val format = NumberFormat.getCurrencyInstance(Locale("en", "ZA"))
            format.maximumFractionDigits = 2
            format.format(amount)
        } catch (_: Throwable) {
            "R %.2f".format(Locale.US, amount)
        }
    }

    fun formatZarMinor(amountMinor: Long): String = formatZar(amountMinor / 100.0)

    fun calculateEstimate(distanceMeters: Int, category: RideCategory): Double {
        val km = max(1.0, distanceMeters / 1000.0)
        val raw = km * RATE_PER_KM
        val multiplier = when (category) {
            RideCategory.STANDARD -> 1.0
            RideCategory.COMFORT -> 1.0 // Draft launch policy: R20/km across categories
            RideCategory.XL -> 1.0
        }
        return round(raw * multiplier)
    }

    fun canRiderCancel(status: RideStatus, scheduled: Boolean = false): Boolean {
        return status == RideStatus.REQUESTED ||
            status == RideStatus.COUNTERED ||
            status == RideStatus.ACCEPTED ||
            status == RideStatus.ARRIVED ||
            (scheduled && status == RideStatus.ACCEPTED)
    }

    fun riderCancellationFee(trip: RideTrip): Double {
        if (CANCELLATION_FEE_PERCENT <= 0.0) return 0.0
        val driverOnWay = trip.status == RideStatus.ARRIVED ||
            (trip.status == RideStatus.ACCEPTED && (trip.pickupAt == null || trip.pickupAt <= System.currentTimeMillis()))
        if (!driverOnWay) return 0.0
        return round(trip.fare * (CANCELLATION_FEE_PERCENT / 100.0) * 100.0) / 100.0
    }

    fun tripExpiresAtMs(trip: RideTrip): Long {
        trip.expiresAt?.takeIf { it > 0L }?.let { return it }
        val anchorMs = max(trip.createdAt, trip.pickupAt ?: trip.createdAt)
        return anchorMs + OPEN_REQUEST_TTL_MS
    }

    fun tripExpirationWindowMs(trip: RideTrip): Long {
        val anchorMs = max(trip.createdAt, trip.pickupAt ?: trip.createdAt)
        return (tripExpiresAtMs(trip) - anchorMs).coerceAtLeast(OPEN_REQUEST_TTL_MS)
    }

    fun remainingOpenMs(trip: RideTrip, nowMs: Long = System.currentTimeMillis()): Long {
        if (!trip.status.isOpenForDrivers || trip.driverId != null) return 0L
        return (tripExpiresAtMs(trip) - nowMs).coerceAtLeast(0L)
    }

    fun isTripExpired(trip: RideTrip, nowMs: Long = System.currentTimeMillis()): Boolean {
        return trip.status.isOpenForDrivers && trip.driverId == null && remainingOpenMs(trip, nowMs) <= 0L
    }

    fun formatCountdownMs(ms: Long): String {
        val totalSeconds = ((ms + 999L) / 1000L).coerceAtLeast(0L)
        val mins = totalSeconds / 60L
        val secs = totalSeconds % 60L
        return "%d:%02d".format(Locale.US, mins, secs)
    }

    fun haversineKm(first: RideCoordinates?, second: RideCoordinates?): Double? {
        if (first == null || second == null) return null
        val dLat = Math.toRadians(second.latitude - first.latitude)
        val dLon = Math.toRadians(second.longitude - first.longitude)
        val lat1 = Math.toRadians(first.latitude)
        val lat2 = Math.toRadians(second.latitude)
        val a = sin(dLat / 2).pow(2) + cos(lat1) * cos(lat2) * sin(dLon / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return 6371.0 * c
    }

    fun distanceMeters(first: RideCoordinates, second: RideCoordinates): Double {
        return (haversineKm(first, second) ?: 0.0) * 1000.0
    }

    fun matchScoreForDriver(trip: RideTrip, driverLocation: RideCoordinates?): Double {
        val km = trip.route?.distanceMeters?.takeIf { it > 0 }?.div(1000.0)
            ?: haversineKm(trip.pickupCoordinates, trip.destinationCoordinates)
        val farePerKm = if (km != null && km > 0) trip.fare / km else trip.fare
        val ageMinutes = max(0.0, (System.currentTimeMillis() - trip.createdAt) / 60_000.0)
        val recencyBonus = max(0.0, 1.0 - ageMinutes / 30.0) * 5.0
        val offerBonus = if (trip.pricingMode == PricingMode.OFFER) 2.0 else 0.0
        var score = farePerKm + recencyBonus + offerBonus
        val pickup = trip.pickupCoordinates ?: trip.route?.originCoordinates
        if (driverLocation != null && pickup != null) {
            val distance = haversineKm(driverLocation, pickup)
            if (distance != null) {
                score += max(0.0, 25.0 - distance) * 0.4
            }
        }
        return score
    }

    fun offerScore(offer: RideOffer): Double {
        val rating = offer.driverProfile.rating
        val completed = offer.driverProfile.completedRides
        val acceptance = offer.driverProfile.acceptanceRate
        return rating * 12.0 + completed * 0.5 + acceptance * 8.0 - offer.amount / 20.0
    }

    fun normalizeSouthAfricanRegistration(value: String): String {
        return value.uppercase(Locale.ROOT).replace(Regex("\\s+"), " ").take(30)
    }

    fun formatSouthAfricanRegistration(value: String): String {
        val normalized = normalizeSouthAfricanRegistration(value).trim()
        val compact = normalized.replace(Regex("[\\s-]"), "")
        val gautengMatch = Regex("^([A-Z]{3})(\\d{3})([A-Z]{2})$").matchEntire(compact)
        if (gautengMatch != null) {
            return "${gautengMatch.groupValues[1]} ${gautengMatch.groupValues[2]} ${gautengMatch.groupValues[3]}"
        }
        return normalized
    }

    data class PopularHub(
        val id: String,
        val name: String,
        val area: String,
        val coordinates: RideCoordinates
    )

    val POLOKWANE_POPULAR_HUBS: List<PopularHub> = listOf(
        PopularHub(
            id = "hub_mall_of_the_north",
            name = "Mall of the North",
            area = "Bendor",
            coordinates = RideCoordinates(-23.8768, 29.4979)
        ),
        PopularHub(
            id = "hub_savannah_mall",
            name = "Savannah Mall",
            area = "Fauna Park",
            coordinates = RideCoordinates(-23.9038, 29.4857)
        ),
        PopularHub(
            id = "hub_peter_mokaba",
            name = "Peter Mokaba Stadium",
            area = "Polokwane Central",
            coordinates = RideCoordinates(-23.9242, 29.4685)
        ),
        PopularHub(
            id = "hub_polokwane_cbd",
            name = "Polokwane CBD",
            area = "Library Gardens",
            coordinates = RideCoordinates(-23.9045, 29.4689)
        ),
        PopularHub(
            id = "hub_cycad",
            name = "Cycad Shopping Centre",
            area = "Bendor",
            coordinates = RideCoordinates(-23.9082, 29.4765)
        ),
        PopularHub(
            id = "hub_provincial_hospital",
            name = "Polokwane Hospital",
            area = "Hospital Park",
            coordinates = RideCoordinates(-23.9117, 29.4573)
        ),
        PopularHub(
            id = "hub_seshego_plaza",
            name = "Seshego Circle Centre",
            area = "Seshego",
            coordinates = RideCoordinates(-23.8548, 29.3924)
        ),
        PopularHub(
            id = "hub_ul_turfloop",
            name = "University of Limpopo",
            area = "Turfloop",
            coordinates = RideCoordinates(-23.8888, 29.7386)
        ),
        PopularHub(
            id = "hub_polokwane_airport",
            name = "Polokwane Airport",
            area = "Gateway",
            coordinates = RideCoordinates(-23.8453, 29.4586)
        )
    )

    fun findNearestHubName(point: RideCoordinates, maxRadiusMeters: Double = 350.0): String? {
        val closest = POLOKWANE_POPULAR_HUBS
            .map { hub -> hub to distanceMeters(point, hub.coordinates) }
            .minByOrNull { it.second }
        return if (closest != null && closest.second <= maxRadiusMeters) {
            "${closest.first.name} (${closest.first.area})"
        } else null
    }

    fun nearestLandmarkLabel(point: RideCoordinates): String {
        return findNearestHubName(point)
            ?: "Pin (${"%.4f".format(Locale.US, point.latitude)}, ${"%.4f".format(Locale.US, point.longitude)})"
    }

    val SOUTH_AFRICAN_VEHICLE_CATALOG: List<String> = listOf(
        "Toyota Corolla",
        "Toyota Corolla Cross",
        "Toyota Corolla Quest",
        "Toyota Fortuner",
        "Toyota Hilux",
        "Toyota Starlet",
        "Toyota Urban Cruiser",
        "Toyota Yaris",
        "Volkswagen Polo",
        "Volkswagen Polo Vivo",
        "Volkswagen T-Cross",
        "Volkswagen Taigo",
        "Volkswagen Tiguan",
        "Suzuki Swift",
        "Suzuki Baleno",
        "Suzuki Fronx",
        "Suzuki Ertiga",
        "Hyundai Grand i10",
        "Hyundai i20",
        "Hyundai Venue",
        "Hyundai Creta",
        "Kia Picanto",
        "Kia Sonet",
        "Ford Ranger",
        "Ford Territory",
        "Nissan Magnite",
        "Renault Kwid",
        "Renault Triber",
        "Haval Jolion",
        "Chery Tiggo 4 Pro"
    )

    fun searchVehicles(query: String, limit: Int = 8): List<String> {
        val q = query.trim().lowercase(Locale.ROOT)
        return if (q.isEmpty()) {
            listOf(
                "Toyota Corolla Quest",
                "Toyota Corolla",
                "Volkswagen Polo Vivo",
                "Volkswagen Polo",
                "Suzuki Swift",
                "Toyota Starlet"
            )
        } else {
            SOUTH_AFRICAN_VEHICLE_CATALOG.filter { it.lowercase(Locale.ROOT).contains(q) }.take(limit)
        }
    }

    enum class DemandTier(val label: String) {
        LOW("Warm"),
        MEDIUM("Busy"),
        HIGH("Hot")
    }

    data class DemandHeatZone(
        val id: String,
        val label: String,
        val centroid: RideCoordinates,
        val intensity: Float,
        val tier: DemandTier,
        val requestCount: Int,
        val liveOpenCount: Int,
        val avgFareZar: Double,
        val radiusMeters: Double
    )

    fun buildDemandHeatZones(
        trips: List<RideTrip>,
        nowMs: Long = System.currentTimeMillis()
    ): List<DemandHeatZone> {
        data class WeightedPoint(
            val trip: RideTrip,
            val coords: RideCoordinates,
            val weight: Double,
            val isLiveOpen: Boolean
        )

        val candidates = trips.mapNotNull { trip ->
            val coords = trip.pickupCoordinates ?: trip.route?.originCoordinates ?: return@mapNotNull null
            val ageMinutes = max(0.0, (nowMs - trip.createdAt) / 60_000.0)
            val expired = isTripExpired(trip, nowMs)
            val baseWeight = when (trip.status) {
                RideStatus.REQUESTED, RideStatus.COUNTERED -> if (expired) 0.80 else 1.0
                RideStatus.UNFULFILLED -> 0.80
                RideStatus.ACCEPTED, RideStatus.ARRIVED, RideStatus.IN_PROGRESS -> 0.65
                RideStatus.COMPLETED -> 0.45
                RideStatus.CANCELLED -> if (ageMinutes <= 120.0) 0.15 else 0.0
            }
            if (baseWeight <= 0.0) return@mapNotNull null
            val recencyFactor = 1.0 / (1.0 + ageMinutes / 90.0)
            val weight = baseWeight * recencyFactor
            if (weight < 0.04) return@mapNotNull null
            WeightedPoint(
                trip = trip,
                coords = coords,
                weight = weight,
                isLiveOpen = trip.status.isOpenForDrivers && !expired
            )
        }.sortedByDescending { it.weight }

        if (candidates.isEmpty()) return emptyList()

        val clusterRadiusMeters = 900.0
        val assigned = BooleanArray(candidates.size)
        data class RawCluster(
            val centroid: RideCoordinates,
            val score: Double,
            val points: List<WeightedPoint>
        )

        val rawClusters = mutableListOf<RawCluster>()
        for (i in candidates.indices) {
            if (assigned[i]) continue
            assigned[i] = true
            val seed = candidates[i]
            val members = mutableListOf(seed)
            var sumLat = seed.coords.latitude * seed.weight
            var sumLon = seed.coords.longitude * seed.weight
            var sumWeight = seed.weight

            for (j in candidates.indices) {
                if (assigned[j]) continue
                val other = candidates[j]
                val currentCentroid = RideCoordinates(sumLat / sumWeight, sumLon / sumWeight)
                if (distanceMeters(currentCentroid, other.coords) <= clusterRadiusMeters) {
                    assigned[j] = true
                    members += other
                    sumLat += other.coords.latitude * other.weight
                    sumLon += other.coords.longitude * other.weight
                    sumWeight += other.weight
                }
            }

            rawClusters += RawCluster(
                centroid = RideCoordinates(sumLat / sumWeight, sumLon / sumWeight),
                score = sumWeight,
                points = members
            )
        }

        val maxScore = max(rawClusters.maxOf { it.score }, 2.4)
        return rawClusters
            .sortedWith(compareByDescending<RawCluster> { it.points.count { p -> p.isLiveOpen } }.thenByDescending { it.score })
            .take(12)
            .mapIndexed { idx, cluster ->
                val intensity = (cluster.score / maxScore).toFloat().coerceIn(0.22f, 1.0f)
                val liveCount = cluster.points.count { it.isLiveOpen }
                val tier = when {
                    intensity >= 0.68f || liveCount >= 2 -> DemandTier.HIGH
                    intensity >= 0.38f || liveCount >= 1 -> DemandTier.MEDIUM
                    else -> DemandTier.LOW
                }
                val fallbackLabel = cluster.points
                    .maxByOrNull { it.trip.createdAt }
                    ?.trip
                    ?.pickup
                    ?.substringBefore(" ·")
                    ?.substringBefore(",")
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }
                    ?: "Active Zone"
                val label = findNearestHubName(cluster.centroid, maxRadiusMeters = 850.0)
                    ?.substringBefore(" (")
                    ?: fallbackLabel
                val avgFare = cluster.points.map { it.trip.fare }.average().takeIf { !it.isNaN() } ?: 0.0
                val radiusMeters = 520.0 + intensity * 420.0
                DemandHeatZone(
                    id = "zone_${idx}_${(cluster.centroid.latitude * 1000).toInt()}_${(cluster.centroid.longitude * 1000).toInt()}",
                    label = label,
                    centroid = cluster.centroid,
                    intensity = intensity,
                    tier = tier,
                    requestCount = cluster.points.size,
                    liveOpenCount = liveCount,
                    avgFareZar = avgFare,
                    radiusMeters = radiusMeters
                )
            }
    }
}
