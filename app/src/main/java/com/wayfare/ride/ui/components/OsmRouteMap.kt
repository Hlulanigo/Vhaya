package com.wayfare.ride.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.ZoomOutMap
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.wayfare.ride.R
import com.wayfare.ride.domain.LaunchPolicy
import com.wayfare.ride.domain.PolylineCodec
import com.wayfare.ride.domain.RideCoordinates
import com.wayfare.ride.domain.RideRoute
import com.wayfare.ride.ui.theme.LocalWayfareColors
import com.wayfare.ride.ui.theme.WayfareMonoFamily
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.log2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sinh
import kotlin.math.tan

private const val TILE_PX = 256.0
private const val DEFAULT_STREET_ZOOM = 16.0f
private const val MIN_ZOOM = 5.0f
private const val MAX_ZOOM = 18.0f

private fun lonToWorldX(lon: Double, zoom: Double): Double {
    val n = 2.0.pow(zoom)
    return ((lon + 180.0) / 360.0) * n * TILE_PX
}

private fun latToWorldY(lat: Double, zoom: Double): Double {
    val clamped = lat.coerceIn(-85.0, 85.0)
    val rad = Math.toRadians(clamped)
    val n = 2.0.pow(zoom)
    return ((1.0 - ln(tan(rad) + 1.0 / cos(rad)) / PI) / 2.0) * n * TILE_PX
}

private fun worldXToLon(worldX: Double, zoom: Double): Double {
    val n = 2.0.pow(zoom)
    return (worldX / (n * TILE_PX)) * 360.0 - 180.0
}

private fun worldYToLat(worldY: Double, zoom: Double): Double {
    val n = 2.0.pow(zoom)
    val yNorm = PI * (1.0 - 2.0 * worldY / (n * TILE_PX))
    return Math.toDegrees(atan(sinh(yNorm)))
}

private fun computeBearingDegrees(from: RideCoordinates, to: RideCoordinates): Float {
    val lat1 = Math.toRadians(from.latitude)
    val lat2 = Math.toRadians(to.latitude)
    val dLon = Math.toRadians(to.longitude - from.longitude)
    val y = sin(dLon) * cos(lat2)
    val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)
    val deg = Math.toDegrees(atan2(y, x))
    return ((deg + 360.0) % 360.0).toFloat()
}

private fun fitCameraToPoints(
    points: List<RideCoordinates>,
    usableWidthPx: Float,
    usableHeightPx: Float
): Pair<RideCoordinates, Float> {
    if (points.isEmpty()) return LaunchPolicy.DEFAULT_MAP_CENTER to DEFAULT_STREET_ZOOM
    if (points.size == 1) return points.first() to DEFAULT_STREET_ZOOM

    var minLat = Double.POSITIVE_INFINITY
    var maxLat = Double.NEGATIVE_INFINITY
    var minLon = Double.POSITIVE_INFINITY
    var maxLon = Double.NEGATIVE_INFINITY
    for (p in points) {
        if (p.latitude < minLat) minLat = p.latitude
        if (p.latitude > maxLat) maxLat = p.latitude
        if (p.longitude < minLon) minLon = p.longitude
        if (p.longitude > maxLon) maxLon = p.longitude
    }

    val minWX = lonToWorldX(minLon, 0.0)
    val maxWX = lonToWorldX(maxLon, 0.0)
    val minWY = latToWorldY(maxLat, 0.0)
    val maxWY = latToWorldY(minLat, 0.0)

    val midWX = (minWX + maxWX) / 2.0
    val midWY = (minWY + maxWY) / 2.0
    val center = RideCoordinates(
        latitude = worldYToLat(midWY, 0.0).coerceIn(-85.0, 85.0),
        longitude = ((worldXToLon(midWX, 0.0) + 540.0) % 360.0) - 180.0
    )

    val spanX = max(1e-5, (maxWX - minWX) * 1.28)
    val spanY = max(1e-5, (maxWY - minWY) * 1.34)
    val zoomX = log2(usableWidthPx.coerceAtLeast(160f) / spanX)
    val zoomY = log2(usableHeightPx.coerceAtLeast(160f) / spanY)
    val fitZoom = min(zoomX, zoomY).toFloat().coerceIn(10.5f, 16.4f)
    return center to fitZoom
}

@Composable
fun OsmRouteMap(
    modifier: Modifier = Modifier,
    pickup: RideCoordinates? = null,
    destination: RideCoordinates? = null,
    driverLocation: RideCoordinates? = null,
    route: RideRoute? = null,
    pickupLabel: String? = null,
    destinationLabel: String? = null,
    isMatching: Boolean = false,
    isPinPickerMode: Boolean = false,
    onCameraCenterChanged: ((RideCoordinates) -> Unit)? = null,
    showMapControls: Boolean = true,
    controlsTopOffsetDp: Int = 78,
    bottomContentPaddingDp: Int = 220,
    demandZones: List<LaunchPolicy.DemandHeatZone> = emptyList(),
    showDemandHeatmap: Boolean = false,
    onToggleDemandHeatmap: (() -> Unit)? = null,
    onDemandZoneSelected: ((LaunchPolicy.DemandHeatZone) -> Unit)? = null,
    focusTarget: RideCoordinates? = null,
    focusTrigger: Int = 0
) {
    val wayfareColors = LocalWayfareColors.current
    val colorScheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()

    val isDarkMap = colorScheme.background.luminance() < 0.35f
    val darkMapColorFilter = remember(isDarkMap) {
        if (!isDarkMap) null
        else ColorFilter.colorMatrix(
            ColorMatrix(
                floatArrayOf(
                    -0.85f, 0f, 0f, 0f, 230f,
                    0f, -0.83f, 0f, 0f, 235f,
                    0f, 0f, -0.75f, 0f, 245f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
        )
    }

    val routePoints = remember(route) {
        route?.encodedPolyline?.let { PolylineCodec.decode(it) }.orEmpty()
    }

    val boundsPoints = remember(routePoints, pickup, destination, driverLocation) {
        buildList {
            addAll(routePoints)
            if (pickup != null) add(pickup)
            if (destination != null) add(destination)
            if (driverLocation != null) add(driverLocation)
        }
    }

    val initialCenter = remember(pickup, destination, driverLocation) {
        when {
            pickup != null && destination != null -> RideCoordinates(
                (pickup.latitude + destination.latitude) / 2.0,
                (pickup.longitude + destination.longitude) / 2.0
            )
            pickup != null -> pickup
            driverLocation != null -> driverLocation
            else -> LaunchPolicy.DEFAULT_MAP_CENTER
        }
    }

    var centerLat by remember { mutableDoubleStateOf(initialCenter.latitude) }
    var centerLon by remember { mutableDoubleStateOf(initialCenter.longitude) }
    var cameraZoom by remember {
        mutableFloatStateOf(if (pickup != null && destination != null) 13.8f else DEFAULT_STREET_ZOOM)
    }
    var isDraggingPin by remember { mutableStateOf(false) }
    var is3DTiltPreferred by remember { mutableStateOf(true) }
    var cameraAnimJob by remember { mutableStateOf<Job?>(null) }
    var outerLayerCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var groundLayerCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }

    val targetTiltDeg = when {
        isPinPickerMode || !is3DTiltPreferred -> 0f
        driverLocation != null && routePoints.size >= 2 -> 38f
        else -> 33f
    }
    val animatedTiltDeg by animateFloatAsState(
        targetValue = targetTiltDeg,
        animationSpec = tween(durationMillis = 420, easing = FastOutSlowInEasing),
        label = "mapTilt3D"
    )
    val tiltFraction = (animatedTiltDeg / 38f).coerceIn(0f, 1f)

    fun animateCameraTo(targetLat: Double, targetLon: Double, zoomTarget: Float, durationMs: Int = 380) {
        cameraAnimJob?.cancel()
        val startLat = centerLat
        val startLon = centerLon
        val startZoom = cameraZoom
        val clampedZoom = zoomTarget.coerceIn(MIN_ZOOM, MAX_ZOOM)
        cameraAnimJob = coroutineScope.launch {
            animate(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = tween(durationMillis = durationMs, easing = FastOutSlowInEasing)
            ) { t, _ ->
                centerLat = (startLat + (targetLat - startLat) * t).coerceIn(-85.0, 85.0)
                val rawLon = startLon + (targetLon - startLon) * t
                centerLon = ((rawLon + 540.0) % 360.0) - 180.0
                cameraZoom = startZoom + (clampedZoom - startZoom) * t
            }
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "mapPulse")
    val pickupPulse by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2200, easing = FastOutSlowInEasing), RepeatMode.Restart),
        label = "pickupPulse"
    )
    val radarPulse by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1800, easing = LinearEasing), RepeatMode.Restart),
        label = "radarPulse"
    )
    val heatPulse by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "heatPulse"
    )

    // Progressive route reveal animation when route changes
    val routeReveal by animateFloatAsState(
        targetValue = if (route != null) 1f else 0f,
        animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing),
        label = "routeReveal"
    )

    val pinLiftDp by animateDpAsState(
        targetValue = if (isPinPickerMode && isDraggingPin) 14.dp else 0.dp,
        animationSpec = spring(),
        label = "pinLift"
    )

    // Double-precision driver interpolation + true heading bearing
    var driverFrom by remember { mutableStateOf(driverLocation) }
    var driverTo by remember { mutableStateOf(driverLocation) }
    val driverLerp = remember { Animatable(1f) }
    var targetBearingDeg by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(driverLocation, pickup, destination) {
        val next = driverLocation
        if (next == null) {
            driverFrom = null
            driverTo = null
            driverLerp.snapTo(1f)
        } else {
            val prev = driverTo
            if (prev == null) {
                driverFrom = next
                driverTo = next
                driverLerp.snapTo(1f)
                val headingTarget = pickup ?: destination
                if (headingTarget != null && LaunchPolicy.distanceMeters(next, headingTarget) > 10.0) {
                    targetBearingDeg = computeBearingDegrees(next, headingTarget)
                }
            } else if (prev != next) {
                val movedMeters = LaunchPolicy.distanceMeters(prev, next)
                if (movedMeters > 3.0) {
                    targetBearingDeg = computeBearingDegrees(prev, next)
                }
                val progress = driverLerp.value.toDouble()
                val currentInterpolated = RideCoordinates(
                    latitude = (driverFrom?.latitude ?: prev.latitude) + (prev.latitude - (driverFrom?.latitude ?: prev.latitude)) * progress,
                    longitude = (driverFrom?.longitude ?: prev.longitude) + (prev.longitude - (driverFrom?.longitude ?: prev.longitude)) * progress
                )
                driverFrom = currentInterpolated
                driverTo = next
                driverLerp.snapTo(0f)
                driverLerp.animateTo(1f, animationSpec = tween(durationMillis = 1000, easing = FastOutSlowInEasing))
            }
        }
    }

    val animatedDriverBearing by animateFloatAsState(
        targetValue = targetBearingDeg,
        animationSpec = tween(durationMillis = 450, easing = FastOutSlowInEasing),
        label = "driverBearing"
    )

    val smoothedDriverLocation = remember(driverFrom, driverTo, driverLerp.value) {
        val from = driverFrom
        val to = driverTo
        if (from == null || to == null) to
        else {
            val t = driverLerp.value.toDouble()
            RideCoordinates(
                latitude = from.latitude + (to.latitude - from.latitude) * t,
                longitude = from.longitude + (to.longitude - from.longitude) * t
            )
        }
    }

    LaunchedEffect(centerLat, centerLon) {
        onCameraCenterChanged?.invoke(RideCoordinates(centerLat, centerLon))
    }

    BoxWithConstraints(
        modifier = modifier
            .clipToBounds()
            .background(colorScheme.background)
            .onGloballyPositioned { outerLayerCoords = it }
    ) {
        val widthPx = with(density) { maxWidth.toPx() }
        val heightPx = with(density) { maxHeight.toPx() }
        val topClearancePx = with(density) { (controlsTopOffsetDp + 16).dp.toPx() }
        val bottomClearancePx = with(density) { (bottomContentPaddingDp + 12).dp.toPx() }
        val usableHeightPx = (heightPx - topClearancePx - bottomClearancePx).coerceAtLeast(heightPx * 0.25f)
        val usableWidthPx = (widthPx - with(density) { 48.dp.toPx() }).coerceAtLeast(widthPx * 0.6f)

        val visualCenterY = (topClearancePx + usableHeightPx / 2f).coerceIn(heightPx * 0.26f, heightPx * 0.56f)
        val visualCenterX = widthPx / 2f

        val overscanFactor = if (animatedTiltDeg > 0.5f) 1.85f else 1f
        val mapWidthPx = widthPx * overscanFactor
        val mapHeightPx = heightPx * overscanFactor
        val mapLocalCenterX = mapWidthPx / 2f
        val mapLocalCenterY = mapHeightPx / 2f

        val hasActiveBounds = boundsPoints.size >= 2

        LaunchedEffect(pickup, destination, routePoints, isPinPickerMode, widthPx, heightPx) {
            if (!isPinPickerMode && widthPx > 0f && heightPx > 0f) {
                if (boundsPoints.size >= 2) {
                    val (fitCenter, rawFitZoom) = fitCameraToPoints(boundsPoints, usableWidthPx, usableHeightPx)
                    val tiltedZoom = if (is3DTiltPreferred) (rawFitZoom - 0.18f).coerceAtLeast(10.5f) else rawFitZoom
                    animateCameraTo(fitCenter.latitude, fitCenter.longitude, tiltedZoom, durationMs = 420)
                } else if (pickup != null) {
                    animateCameraTo(pickup.latitude, pickup.longitude, DEFAULT_STREET_ZOOM, durationMs = 360)
                }
            }
        }

        LaunchedEffect(focusTrigger) {
            if (focusTrigger > 0 && focusTarget != null && !isPinPickerMode) {
                animateCameraTo(
                    focusTarget.latitude,
                    focusTarget.longitude,
                    max(cameraZoom, 14.9f),
                    durationMs = 420
                )
            }
        }

        val zoomDouble = cameraZoom.toDouble()
        val centerWorldX = lonToWorldX(centerLon, zoomDouble)
        val centerWorldY = latToWorldY(centerLat, zoomDouble)

        fun toMapLocal(coords: RideCoordinates): Offset {
            val wx = lonToWorldX(coords.longitude, zoomDouble)
            val wy = latToWorldY(coords.latitude, zoomDouble)
            return Offset(
                x = mapLocalCenterX + (wx - centerWorldX).toFloat(),
                y = mapLocalCenterY + (wy - centerWorldY).toFloat()
            )
        }

        fun toProjectedScreen(coords: RideCoordinates): Offset {
            val localPt = toMapLocal(coords)
            val outer = outerLayerCoords
            val ground = groundLayerCoords
            if (animatedTiltDeg > 0.1f && outer != null && ground != null && outer.isAttached && ground.isAttached) {
                val mapped = runCatching { outer.localPositionOf(ground, localPt) }.getOrNull()
                if (mapped != null && mapped.x.isFinite() && mapped.y.isFinite()) {
                    return mapped
                }
            }
            return Offset(
                x = visualCenterX + (localPt.x - mapLocalCenterX),
                y = visualCenterY + (localPt.y - mapLocalCenterY)
            )
        }

        // Gesture surface: double-tap zoom + multi-touch pan/pinch + decay fling inertia
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(visualCenterX, visualCenterY) {
                    detectTapGestures(
                        onDoubleTap = { tapOffset ->
                            val nextZoom = (cameraZoom + 1.25f).coerceIn(MIN_ZOOM, MAX_ZOOM)
                            val zNow = cameraZoom.toDouble()
                            val tappedWorldX = lonToWorldX(centerLon, zNow) + (tapOffset.x - visualCenterX)
                            val tappedWorldY = latToWorldY(centerLat, zNow) + (tapOffset.y - visualCenterY)
                            val tappedLon = worldXToLon(tappedWorldX, zNow)
                            val tappedLat = worldYToLat(tappedWorldY, zNow)
                            val scaleFactor = 2.0.pow((nextZoom - cameraZoom).toDouble())
                            val targetCenterWorldX = lonToWorldX(tappedLon, nextZoom.toDouble()) - (tapOffset.x - visualCenterX)
                            val targetCenterWorldY = latToWorldY(tappedLat, nextZoom.toDouble()) - (tapOffset.y - visualCenterY)
                            val targetLat = worldYToLat(targetCenterWorldY, nextZoom.toDouble()).coerceIn(-85.0, 85.0)
                            val targetLon = ((worldXToLon(targetCenterWorldX, nextZoom.toDouble()) + 540.0) % 360.0) - 180.0
                            if (scaleFactor > 0.0) {
                                animateCameraTo(targetLat, targetLon, nextZoom, durationMs = 280)
                            }
                        }
                    )
                }
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val velocityTracker = VelocityTracker()
                        var wasPinch = false
                        val down = awaitFirstDown(requireUnconsumed = false)
                        cameraAnimJob?.cancel()
                        velocityTracker.addPosition(down.uptimeMillis, down.position)
                        do {
                            val event = awaitPointerEvent()
                            val pressedCount = event.changes.count { it.pressed }
                            if (pressedCount > 1) wasPinch = true
                            val zoomChange = event.calculateZoom()
                            val panChange = event.calculatePan()
                            if (zoomChange != 1f || panChange != Offset.Zero) {
                                isDraggingPin = true
                                val nextZoom = (cameraZoom + ln(zoomChange.coerceIn(0.8f, 1.25f)) / ln(2f))
                                    .coerceIn(MIN_ZOOM, MAX_ZOOM)
                                cameraZoom = nextZoom
                                val yComp = 1f / cos(Math.toRadians(animatedTiltDeg.toDouble())).toFloat().coerceAtLeast(0.65f)
                                val cx = lonToWorldX(centerLon, nextZoom.toDouble()) - panChange.x
                                val cy = latToWorldY(centerLat, nextZoom.toDouble()) - (panChange.y * yComp)
                                val rawLon = worldXToLon(cx, nextZoom.toDouble())
                                centerLon = ((rawLon + 540.0) % 360.0) - 180.0
                                centerLat = worldYToLat(cy, nextZoom.toDouble()).coerceIn(-85.0, 85.0)
                                if (pressedCount == 1) {
                                    val activePointer = event.changes.firstOrNull { it.pressed }
                                    if (activePointer != null) {
                                        velocityTracker.addPosition(activePointer.uptimeMillis, activePointer.position)
                                    }
                                }
                                event.changes.forEach { if (it.positionChanged()) it.consume() }
                            }
                        } while (event.changes.any { it.pressed })
                        isDraggingPin = false
                        if (!wasPinch) {
                            val vel = velocityTracker.calculateVelocity()
                            val speed = hypot(vel.x, vel.y)
                            if (speed > 140f) {
                                cameraAnimJob = coroutineScope.launch {
                                    var prevOffset = Offset.Zero
                                    AnimationState(
                                        typeConverter = Offset.VectorConverter,
                                        initialValue = Offset.Zero,
                                        initialVelocity = Offset(vel.x, vel.y)
                                    ).animateDecay(exponentialDecay(frictionMultiplier = 2.2f)) {
                                        val delta = value - prevOffset
                                        prevOffset = value
                                        val z = cameraZoom.toDouble()
                                        val yComp = 1f / cos(Math.toRadians(animatedTiltDeg.toDouble())).toFloat().coerceAtLeast(0.65f)
                                        val cx = lonToWorldX(centerLon, z) - delta.x
                                        val cy = latToWorldY(centerLat, z) - (delta.y * yComp)
                                        val rawLon = worldXToLon(cx, z)
                                        centerLon = ((rawLon + 540.0) % 360.0) - 180.0
                                        centerLat = worldYToLat(cy, z).coerceIn(-85.0, 85.0)
                                    }
                                }
                            }
                        }
                    }
                }
        ) {
            // Tilted 3D Map Ground Plane (overscanned so corners never clip under rotationX)
            Box(
                modifier = Modifier
                    .layout { measurable, _ ->
                        val w = mapWidthPx.roundToInt().coerceAtLeast(1)
                        val h = mapHeightPx.roundToInt().coerceAtLeast(1)
                        val placeable = measurable.measure(Constraints.fixed(w, h))
                        layout(0, 0) {
                            placeable.place(
                                (visualCenterX - mapLocalCenterX).roundToInt(),
                                (visualCenterY - mapLocalCenterY).roundToInt()
                            )
                        }
                    }
                    .onGloballyPositioned { groundLayerCoords = it }
                    .graphicsLayer {
                        transformOrigin = TransformOrigin(0.5f, 0.5f)
                        rotationX = animatedTiltDeg
                        cameraDistance = 14f
                    }
            ) {
                // 1. Subtle vector street grid base layer beneath OpenStreetMap tiles
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawRect(color = wayfareColors.mapBlock)

                    val stepDeg = if (cameraZoom >= 15.0f) 0.002 else 0.004
                    val spanDeg = if (animatedTiltDeg > 1f) 0.09 else 0.055
                    val startLat = floor((centerLat - spanDeg) / stepDeg) * stepDeg
                    val endLat = centerLat + spanDeg
                    var lat = startLat
                    while (lat <= endLat) {
                        val p1 = toMapLocal(RideCoordinates(lat, centerLon - spanDeg - 0.02))
                        val p2 = toMapLocal(RideCoordinates(lat, centerLon + spanDeg + 0.02))
                        drawLine(
                            color = wayfareColors.mapRoad,
                            start = p1,
                            end = p2,
                            strokeWidth = if (((lat * 1000).roundToInt() % 6) == 0) 9f else 4f
                        )
                        lat += stepDeg
                    }

                    val startLon = floor((centerLon - spanDeg - 0.01) / stepDeg) * stepDeg
                    val endLon = centerLon + spanDeg + 0.01
                    var lon = startLon
                    while (lon <= endLon) {
                        val p1 = toMapLocal(RideCoordinates(centerLat - spanDeg - 0.01, lon))
                        val p2 = toMapLocal(RideCoordinates(centerLat + spanDeg + 0.01, lon))
                        drawLine(
                            color = wayfareColors.mapRoad,
                            start = p1,
                            end = p2,
                            strokeWidth = if (((lon * 1000).roundToInt() % 6) == 0) 9f else 4f
                        )
                        lon += stepDeg
                    }
                }

                // 2. OpenStreetMap raster tiles with adaptive dark/light palette filter
                val tileZoom = floor(cameraZoom).toInt().coerceIn(10, 17)
                val tileScale = 2.0.pow(zoomDouble - tileZoom)
                val scaledTilePx = TILE_PX * tileScale
                val scaledTileDp = with(density) { scaledTilePx.toFloat().toDp() }
                val tileCenterX = lonToWorldX(centerLon, tileZoom.toDouble()) * tileScale
                val tileCenterY = latToWorldY(centerLat, tileZoom.toDouble()) * tileScale
                val centerTileX = floor(tileCenterX / scaledTilePx).toInt()
                val centerTileY = floor(tileCenterY / scaledTilePx).toInt()
                val maxTile = (1 shl tileZoom) - 1
                val tileSpanW = if (animatedTiltDeg > 1f) mapWidthPx else widthPx
                val tileSpanH = if (animatedTiltDeg > 1f) mapHeightPx else heightPx
                val halfCols = (ceil(tileSpanW / 2f / scaledTilePx).toInt() + 1).coerceIn(2, 5)
                val halfRows = (ceil(tileSpanH / 2f / scaledTilePx).toInt() + 1).coerceIn(2, 5)
                val visMinX = if (animatedTiltDeg > 1f) 0f else (mapLocalCenterX - visualCenterX)
                val visMaxX = if (animatedTiltDeg > 1f) mapWidthPx else (mapLocalCenterX - visualCenterX + widthPx)
                val visMinY = if (animatedTiltDeg > 1f) 0f else (mapLocalCenterY - visualCenterY)
                val visMaxY = if (animatedTiltDeg > 1f) mapHeightPx else (mapLocalCenterY - visualCenterY + heightPx)

                for (dx in -halfCols..halfCols) {
                    for (dy in -halfRows..halfRows) {
                        val tx = centerTileX + dx
                        val ty = centerTileY + dy
                        if (tx in 0..maxTile && ty in 0..maxTile) {
                            val leftPx = (tx * scaledTilePx - tileCenterX).toFloat() + mapLocalCenterX
                            val topPx = (ty * scaledTilePx - tileCenterY).toFloat() + mapLocalCenterY
                            if (leftPx + scaledTilePx > visMinX && leftPx < visMaxX &&
                                topPx + scaledTilePx > visMinY && topPx < visMaxY
                            ) {
                                val url = "https://tile.openstreetmap.org/$tileZoom/$tx/$ty.png"
                                AsyncImage(
                                    model = ImageRequest.Builder(context)
                                        .data(url)
                                        .addHeader("User-Agent", "VhayaAndroid/1.0")
                                        .crossfade(180)
                                        .build(),
                                    contentDescription = null,
                                    contentScale = ContentScale.FillBounds,
                                    colorFilter = darkMapColorFilter,
                                    modifier = Modifier
                                        .offset { IntOffset(leftPx.roundToInt(), topPx.roundToInt()) }
                                        .size(scaledTileDp)
                                        .alpha(if (isDarkMap) 0.90f else 0.94f)
                                )
                            }
                        }
                    }
                }

                // 2b. Real-time Demand Heatmap layer (tilted 3D thermal gradients over city blocks)
                if (showDemandHeatmap && !isPinPickerMode && demandZones.isNotEmpty()) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val metersPerPixel = (
                            156543.03392 * cos(Math.toRadians(centerLat)) / 2.0.pow(zoomDouble)
                            ).coerceAtLeast(0.05)

                        demandZones.forEach { zone ->
                            val center = toMapLocal(zone.centroid)
                            val geoRadiusPx = (zone.radiusMeters / metersPerPixel).toFloat().coerceIn(56f, 320f)
                            val breatheScale = if (zone.liveOpenCount > 0) 0.93f + heatPulse * 0.14f else 1f
                            val radius = geoRadiusPx * breatheScale

                            val (coreColor, midColor, outerColor) = when (zone.tier) {
                                LaunchPolicy.DemandTier.HIGH -> Triple(
                                    wayfareColors.heatHigh.copy(alpha = 0.56f),
                                    wayfareColors.heatMid.copy(alpha = 0.34f),
                                    wayfareColors.heatLow.copy(alpha = 0.13f)
                                )
                                LaunchPolicy.DemandTier.MEDIUM -> Triple(
                                    wayfareColors.heatMid.copy(alpha = 0.45f),
                                    wayfareColors.heatLow.copy(alpha = 0.24f),
                                    wayfareColors.heatLow.copy(alpha = 0.08f)
                                )
                                LaunchPolicy.DemandTier.LOW -> Triple(
                                    wayfareColors.heatLow.copy(alpha = 0.34f),
                                    wayfareColors.heatLow.copy(alpha = 0.16f),
                                    wayfareColors.heatLow.copy(alpha = 0.04f)
                                )
                            }

                            drawCircle(
                                brush = Brush.radialGradient(
                                    0.0f to coreColor,
                                    0.40f to midColor,
                                    0.76f to outerColor,
                                    1.0f to Color.Transparent,
                                    center = center,
                                    radius = radius
                                ),
                                radius = radius,
                                center = center
                            )

                            if (zone.liveOpenCount > 0) {
                                drawCircle(
                                    color = coreColor.copy(alpha = 0.78f),
                                    radius = (radius * 0.28f).coerceAtLeast(14f),
                                    center = center,
                                    style = Stroke(
                                        width = 2.4f,
                                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))
                                    )
                                )
                            }
                        }
                    }
                }

                // 3. Route ribbon (soft casing + primary + inner highlight), ground halos, matching radar & pins
                Canvas(modifier = Modifier.fillMaxSize()) {
                    if (routePoints.size >= 2 && routeReveal > 0.02f) {
                        val screenPoints = routePoints.map { toMapLocal(it) }
                        val fullPath = Path().apply {
                            moveTo(screenPoints.first().x, screenPoints.first().y)
                            for (i in 1 until screenPoints.size) lineTo(screenPoints[i].x, screenPoints[i].y)
                        }
                        val pathToDraw = if (routeReveal >= 0.99f) {
                            fullPath
                        } else {
                            Path().apply {
                                val measure = PathMeasure()
                                measure.setPath(fullPath, false)
                                measure.getSegment(0f, measure.length * routeReveal, this, true)
                            }
                        }

                        drawPath(
                            path = pathToDraw,
                            color = colorScheme.primary.copy(alpha = 0.28f),
                            style = Stroke(width = 28f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                        )
                        drawPath(
                            path = pathToDraw,
                            color = colorScheme.primary,
                            style = Stroke(width = 15f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                        )
                        drawPath(
                            path = pathToDraw,
                            color = wayfareColors.routeHighlight,
                            style = Stroke(width = 6f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                        )
                    } else if (pickup != null && destination != null) {
                        drawLine(
                            color = colorScheme.primary,
                            start = toMapLocal(pickup),
                            end = toMapLocal(destination),
                            strokeWidth = 7f,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(18f, 12f)),
                            cap = StrokeCap.Round
                        )
                    }

                    // Breathing pickup halo & matching radar ring
                    if (pickup != null) {
                        val p = toMapLocal(pickup)
                        if (isMatching) {
                            val phases = floatArrayOf(0f, 0.33f, 0.66f)
                            for (offset in phases) {
                                val wave = (radarPulse + offset) % 1f
                                val radius = 26f + wave * 155f
                                drawCircle(
                                    color = colorScheme.primary.copy(alpha = (1f - wave) * 0.24f),
                                    radius = radius,
                                    center = p
                                )
                                drawCircle(
                                    color = wayfareColors.routeHighlight.copy(alpha = (1f - wave) * 0.52f),
                                    radius = radius,
                                    center = p,
                                    style = Stroke(width = 2.5f)
                                )
                            }
                            val sweepRadius = 56f
                            drawArc(
                                color = colorScheme.primary.copy(alpha = 0.72f),
                                startAngle = radarPulse * 360f,
                                sweepAngle = 95f,
                                useCenter = false,
                                topLeft = Offset(p.x - sweepRadius, p.y - sweepRadius),
                                size = Size(sweepRadius * 2f, sweepRadius * 2f),
                                style = Stroke(width = 4.5f, cap = StrokeCap.Round)
                            )
                        }
                        drawCircle(
                            color = colorScheme.primary.copy(alpha = (1f - pickupPulse) * 0.28f),
                            radius = 18f + pickupPulse * 34f,
                            center = p
                        )
                        drawCircle(color = colorScheme.surface, radius = 15f, center = p)
                        drawCircle(color = colorScheme.primary, radius = 8.5f, center = p)
                    }

                    if (destination != null) {
                        val d = toMapLocal(destination)
                        drawCircle(color = wayfareColors.destinationMarker.copy(alpha = 0.28f), radius = 26f, center = d)
                        drawCircle(color = colorScheme.surface, radius = 15f, center = d)
                        drawCircle(color = wayfareColors.destinationMarker, radius = 8.5f, center = d)
                    }

                    if (isPinPickerMode) {
                        val target = Offset(mapLocalCenterX, mapLocalCenterY)
                        drawCircle(
                            color = colorScheme.primary.copy(alpha = (1f - pickupPulse) * 0.35f),
                            radius = 16f + pickupPulse * 30f,
                            center = target
                        )
                        val shadowScale = if (isDraggingPin) 0.65f else 1f
                        drawOval(
                            color = colorScheme.onBackground.copy(alpha = 0.28f),
                            topLeft = Offset(target.x - 14f * shadowScale, target.y - 4f * shadowScale),
                            size = Size(28f * shadowScale, 9f * shadowScale)
                        )
                    }
                }

                // 3b. Ground-locked Driver Navigation Puck (tilts naturally with the 3D road plane)
                if (smoothedDriverLocation != null) {
                    val pos = toMapLocal(smoothedDriverLocation)
                    val haloHalf = with(density) { 27.dp.toPx() }
                    val badgeHalf = with(density) { 20.dp.toPx() }
                    Surface(
                        shape = CircleShape,
                        color = wayfareColors.routeHighlight.copy(alpha = 0.28f),
                        modifier = Modifier
                            .offset { IntOffset((pos.x - haloHalf).roundToInt(), (pos.y - haloHalf).roundToInt()) }
                            .size(54.dp)
                    ) {}
                    Surface(
                        shape = CircleShape,
                        color = colorScheme.primary,
                        border = BorderStroke(2.dp, colorScheme.surface),
                        shadowElevation = 6.dp,
                        modifier = Modifier
                            .offset { IntOffset((pos.x - badgeHalf).roundToInt(), (pos.y - badgeHalf).roundToInt()) }
                            .size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (routePoints.size >= 2) Icons.Filled.Navigation else Icons.Filled.DirectionsCar,
                                contentDescription = "Driver location",
                                tint = colorScheme.onPrimary,
                                modifier = Modifier
                                    .size(21.dp)
                                    .rotate(if (routePoints.size >= 2) animatedDriverBearing else 0f)
                            )
                        }
                    }
                }
            }

            // Atmospheric 3D horizon haze along the distant upper edge
            if (tiltFraction > 0.05f) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            brush = Brush.verticalGradient(
                                0.0f to colorScheme.background.copy(alpha = 0.68f * tiltFraction),
                                0.16f to colorScheme.background.copy(alpha = 0.18f * tiltFraction),
                                0.28f to Color.Transparent
                            )
                        )
                )
            }

            // 4. Upright 3D Floating Pin Stems + Midpoint ETA Badge + Address Callout Pills
            if (!isPinPickerMode && tiltFraction > 0.15f) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val stemPx = with(density) { (16.dp * tiltFraction).toPx() }
                    val headRadius = with(density) { (6.dp * tiltFraction).toPx() }
                    pickup?.let { pCoord ->
                        val p = toProjectedScreen(pCoord)
                        drawLine(
                            color = colorScheme.surface,
                            start = p,
                            end = Offset(p.x, p.y - stemPx),
                            strokeWidth = 5f,
                            cap = StrokeCap.Round
                        )
                        drawCircle(color = colorScheme.surface, radius = headRadius + 2.5f, center = Offset(p.x, p.y - stemPx))
                        drawCircle(color = colorScheme.primary, radius = headRadius, center = Offset(p.x, p.y - stemPx))
                    }
                    destination?.let { dCoord ->
                        val d = toProjectedScreen(dCoord)
                        drawLine(
                            color = colorScheme.surface,
                            start = d,
                            end = Offset(d.x, d.y - stemPx),
                            strokeWidth = 5f,
                            cap = StrokeCap.Round
                        )
                        drawCircle(color = colorScheme.surface, radius = headRadius + 2.5f, center = Offset(d.x, d.y - stemPx))
                        drawCircle(color = wayfareColors.destinationMarker, radius = headRadius, center = Offset(d.x, d.y - stemPx))
                    }
                }
            }

            if (!isPinPickerMode && route != null && route.distanceMeters > 0 && routePoints.size >= 2 && routeReveal > 0.85f) {
                val midCoord = routePoints[routePoints.size / 2]
                val midScreen = toProjectedScreen(midCoord)
                val etaMins = (route.durationSeconds / 60).coerceAtLeast(1)
                val distKm = "%.1f".format(route.distanceMeters / 1000.0)
                val pillWidthPx = with(density) { 126.dp.toPx() }
                val pillX = (midScreen.x - pillWidthPx / 2f)
                    .coerceIn(with(density) { 12.dp.toPx() }, (widthPx - pillWidthPx - with(density) { 12.dp.toPx() }).coerceAtLeast(12f))
                val pillY = (midScreen.y - with(density) { (40.dp + 12.dp * tiltFraction).toPx() })
                    .coerceIn(topClearancePx, (heightPx - bottomClearancePx - with(density) { 36.dp.toPx() }).coerceAtLeast(topClearancePx))

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = colorScheme.primary,
                    shadowElevation = 6.dp,
                    modifier = Modifier
                        .offset { IntOffset(pillX.roundToInt(), pillY.roundToInt()) }
                        .testTag("map_route_eta_callout")
                ) {
                    Text(
                        text = "$etaMins min · $distKm km",
                        fontFamily = WayfareMonoFamily,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = colorScheme.onPrimary,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }

            if (!isPinPickerMode && pickup != null && destination != null) {
                val pScreen = toProjectedScreen(pickup)
                val dScreen = toProjectedScreen(destination)
                val separated = hypot(pScreen.x - dScreen.x, pScreen.y - dScreen.y) > with(density) { 104.dp.toPx() }
                if (separated) {
                    val maxTagWidthPx = with(density) { 164.dp.toPx() }
                    val minMarginPx = with(density) { 10.dp.toPx() }
                    val liftPx = with(density) { (44.dp + 16.dp * tiltFraction).toPx() }

                    pickupLabel?.substringBefore(" ·")?.substringBefore(",")?.takeIf { it.isNotBlank() }?.let { shortPickup ->
                        val px = (pScreen.x - maxTagWidthPx / 2f)
                            .coerceIn(minMarginPx, (widthPx - maxTagWidthPx - minMarginPx).coerceAtLeast(minMarginPx))
                        val py = (pScreen.y - liftPx)
                            .coerceIn(topClearancePx, (heightPx - bottomClearancePx - with(density) { 34.dp.toPx() }).coerceAtLeast(topClearancePx))
                        MapPinCalloutPill(
                            dotColor = colorScheme.primary,
                            text = shortPickup,
                            modifier = Modifier
                                .offset { IntOffset(px.roundToInt(), py.roundToInt()) }
                                .testTag("map_pickup_callout")
                        )
                    }

                    destinationLabel?.substringBefore(" ·")?.substringBefore(",")?.takeIf { it.isNotBlank() }?.let { shortDest ->
                        val dx = (dScreen.x - maxTagWidthPx / 2f)
                            .coerceIn(minMarginPx, (widthPx - maxTagWidthPx - minMarginPx).coerceAtLeast(minMarginPx))
                        val dy = (dScreen.y - liftPx)
                            .coerceIn(topClearancePx, (heightPx - bottomClearancePx - with(density) { 34.dp.toPx() }).coerceAtLeast(topClearancePx))
                        MapPinCalloutPill(
                            dotColor = wayfareColors.destinationMarker,
                            text = shortDest,
                            modifier = Modifier
                                .offset { IntOffset(dx.roundToInt(), dy.roundToInt()) }
                                .testTag("map_destination_callout")
                        )
                    }
                }
            }

            // 4b. Upright Interactive Demand Hotspot Pills
            if (showDemandHeatmap && !isPinPickerMode && demandZones.isNotEmpty()) {
                val clearancePx = with(density) { 74.dp.toPx() }
                val pickupScreen = pickup?.let { toProjectedScreen(it) }
                val destScreen = destination?.let { toProjectedScreen(it) }
                val minMarginPx = with(density) { 10.dp.toPx() }
                val badgeHalfW = with(density) { 52.dp.toPx() }
                val badgeHalfH = with(density) { 16.dp.toPx() }

                demandZones.take(4).forEach { zone ->
                    val zScreen = toProjectedScreen(zone.centroid)
                    val tooCloseToPickup = pickupScreen != null &&
                        hypot(zScreen.x - pickupScreen.x, zScreen.y - pickupScreen.y) < clearancePx
                    val tooCloseToDest = destScreen != null &&
                        hypot(zScreen.x - destScreen.x, zScreen.y - destScreen.y) < clearancePx
                    val inViewport = zScreen.x in (minMarginPx + badgeHalfW)..(widthPx - minMarginPx - badgeHalfW) &&
                        zScreen.y in topClearancePx..(heightPx - bottomClearancePx)
                    if (!tooCloseToPickup && !tooCloseToDest && inViewport) {
                        val badgeAccent = when (zone.tier) {
                            LaunchPolicy.DemandTier.HIGH -> wayfareColors.heatHigh
                            LaunchPolicy.DemandTier.MEDIUM -> wayfareColors.heatMid
                            LaunchPolicy.DemandTier.LOW -> wayfareColors.heatLow
                        }
                        val badgeLabel = if (zone.liveOpenCount > 0) {
                            "${zone.liveOpenCount} live"
                        } else {
                            "${zone.tier.label} · ${zone.requestCount}"
                        }
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = colorScheme.surface.copy(alpha = 0.94f),
                            border = BorderStroke(1.dp, badgeAccent.copy(alpha = 0.65f)),
                            shadowElevation = 5.dp,
                            modifier = Modifier
                                .offset {
                                    IntOffset(
                                        (zScreen.x - badgeHalfW).roundToInt(),
                                        (zScreen.y - badgeHalfH).roundToInt()
                                    )
                                }
                                .tactilePressScale()
                                .clip(RoundedCornerShape(14.dp))
                                .clickable {
                                    animateCameraTo(
                                        zone.centroid.latitude,
                                        zone.centroid.longitude,
                                        max(cameraZoom, 14.9f),
                                        durationMs = 380
                                    )
                                    onDemandZoneSelected?.invoke(zone)
                                }
                                .testTag("map_demand_zone_${zone.id}")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.LocalFireDepartment,
                                    contentDescription = "${zone.label} demand hotspot",
                                    tint = badgeAccent,
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    text = badgeLabel,
                                    fontFamily = WayfareMonoFamily,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }

            // 5. Center Pin Picker Marker (always flat overhead 2D for pinpoint precision)
            if (isPinPickerMode) {
                Icon(
                    imageVector = Icons.Filled.LocationOn,
                    contentDescription = "Map pin",
                    tint = wayfareColors.crimsonAccent,
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                (visualCenterX - with(density) { 22.dp.toPx() }).roundToInt(),
                                (visualCenterY - with(density) { (42.dp + pinLiftDp).toPx() }).roundToInt()
                            )
                        }
                        .size(44.dp)
                )
            }
        }

        // 7. Map Controls (Heatmap Toggle, Zoom +/-, Fit Route when route exists, 3D/2D Tilt Toggle + Recenter)
        if (showMapControls) {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = controlsTopOffsetDp.dp, end = 12.dp)
            ) {
                AnimatedVisibility(
                    visible = onToggleDemandHeatmap != null && !isPinPickerMode,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    val heatContentDesc = if (showDemandHeatmap) "Hide demand heatmap" else "Show demand heatmap"
                    Surface(
                        shape = CircleShape,
                        color = if (showDemandHeatmap) wayfareColors.crimsonContainer else colorScheme.surface.copy(alpha = 0.94f),
                        border = BorderStroke(
                            1.dp,
                            if (showDemandHeatmap) wayfareColors.heatHigh.copy(alpha = 0.65f) else colorScheme.outlineVariant
                        ),
                        shadowElevation = 4.dp
                    ) {
                        IconButton(
                            onClick = { onToggleDemandHeatmap?.invoke() },
                            modifier = Modifier
                                .size(48.dp)
                                .semantics { contentDescription = heatContentDesc }
                                .testTag("map_toggle_heatmap")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.LocalFireDepartment,
                                contentDescription = null,
                                tint = if (showDemandHeatmap) wayfareColors.heatHigh else colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = colorScheme.surface.copy(alpha = 0.94f),
                    border = BorderStroke(1.dp, colorScheme.outlineVariant),
                    shadowElevation = 4.dp
                ) {
                    Column {
                        IconButton(
                            onClick = {
                                animateCameraTo(centerLat, centerLon, cameraZoom + 0.9f, durationMs = 220)
                            },
                            modifier = Modifier.size(48.dp).testTag("map_zoom_in")
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = "Zoom in")
                        }
                        HorizontalDivider(modifier = Modifier.size(width = 32.dp, height = 1.dp).align(Alignment.CenterHorizontally))
                        IconButton(
                            onClick = {
                                animateCameraTo(centerLat, centerLon, cameraZoom - 0.9f, durationMs = 220)
                            },
                            modifier = Modifier.size(48.dp).testTag("map_zoom_out")
                        ) {
                            Icon(Icons.Filled.Remove, contentDescription = "Zoom out")
                        }
                    }
                }

                AnimatedVisibility(
                    visible = hasActiveBounds && !isPinPickerMode,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Surface(
                        shape = CircleShape,
                        color = colorScheme.surface.copy(alpha = 0.94f),
                        border = BorderStroke(1.dp, colorScheme.outlineVariant),
                        shadowElevation = 4.dp
                    ) {
                        IconButton(
                            onClick = {
                                val (fitCenter, rawFitZoom) = fitCameraToPoints(boundsPoints, usableWidthPx, usableHeightPx)
                                val fitZoom = if (is3DTiltPreferred) (rawFitZoom - 0.18f).coerceAtLeast(10.5f) else rawFitZoom
                                animateCameraTo(fitCenter.latitude, fitCenter.longitude, fitZoom, durationMs = 380)
                            },
                            modifier = Modifier.size(48.dp).testTag("map_fit_route")
                        ) {
                            Icon(
                                Icons.Filled.ZoomOutMap,
                                contentDescription = "Fit route on map",
                                tint = colorScheme.primary
                            )
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AnimatedVisibility(
                        visible = !isPinPickerMode,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        val toggleLabel = if (is3DTiltPreferred) "Switch to 2D top-down map" else "Switch to 3D tilted map"
                        Surface(
                            shape = CircleShape,
                            color = if (is3DTiltPreferred) wayfareColors.brandHighlight else colorScheme.surface.copy(alpha = 0.94f),
                            border = BorderStroke(
                                1.dp,
                                if (is3DTiltPreferred) colorScheme.primary.copy(alpha = 0.55f) else colorScheme.outlineVariant
                            ),
                            shadowElevation = 4.dp
                        ) {
                            IconButton(
                                onClick = { is3DTiltPreferred = !is3DTiltPreferred },
                                modifier = Modifier
                                    .size(48.dp)
                                    .semantics { contentDescription = toggleLabel }
                                    .testTag("map_toggle_3d")
                            ) {
                                Text(
                                    text = if (is3DTiltPreferred) "3D" else "2D",
                                    fontFamily = WayfareMonoFamily,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (is3DTiltPreferred) colorScheme.primary else colorScheme.onSurface
                                )
                            }
                        }
                    }

                    Surface(
                        shape = CircleShape,
                        color = colorScheme.surface.copy(alpha = 0.94f),
                        border = BorderStroke(1.dp, colorScheme.outlineVariant),
                        shadowElevation = 4.dp
                    ) {
                        IconButton(
                            onClick = {
                                val target = pickup ?: driverLocation ?: LaunchPolicy.DEFAULT_MAP_CENTER
                                animateCameraTo(target.latitude, target.longitude, DEFAULT_STREET_ZOOM, durationMs = 360)
                            },
                            modifier = Modifier.size(48.dp).testTag("map_recenter")
                        ) {
                            Icon(
                                Icons.Filled.MyLocation,
                                contentDescription = "Recenter map",
                                tint = colorScheme.primary
                            )
                        }
                    }
                }
            }
        }

        // 8. Thermal Demand Legend + Attribution badge
        Column(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 10.dp, bottom = (bottomContentPaddingDp + 8).dp)
        ) {
            AnimatedVisibility(
                visible = showDemandHeatmap && !isPinPickerMode,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Surface(
                    color = colorScheme.surface.copy(alpha = 0.90f),
                    border = BorderStroke(1.dp, colorScheme.outlineVariant),
                    shape = RoundedCornerShape(10.dp),
                    shadowElevation = 3.dp,
                    modifier = Modifier.testTag("map_demand_legend")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.LocalFireDepartment,
                            contentDescription = "Demand scale",
                            tint = wayfareColors.heatHigh,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = "Low",
                            style = MaterialTheme.typography.labelSmall,
                            color = colorScheme.onSurfaceVariant
                        )
                        Box(
                            modifier = Modifier
                                .width(44.dp)
                                .height(6.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(
                                    brush = Brush.horizontalGradient(
                                        colors = listOf(wayfareColors.heatLow, wayfareColors.heatMid, wayfareColors.heatHigh)
                                    )
                                )
                        )
                        Text(
                            text = "High",
                            style = MaterialTheme.typography.labelSmall,
                            color = colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Surface(
                color = colorScheme.surface.copy(alpha = 0.80f),
                shape = RoundedCornerShape(6.dp)
            ) {
                Text(
                    text = stringResource(R.string.osm_attribution),
                    style = MaterialTheme.typography.labelSmall,
                    color = colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
    }
}

@Composable
private fun MapPinCalloutPill(
    dotColor: androidx.compose.ui.graphics.Color,
    text: String,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = colorScheme.surface.copy(alpha = 0.96f),
        border = BorderStroke(1.dp, colorScheme.outlineVariant),
        shadowElevation = 5.dp,
        modifier = modifier.widthIn(max = 168.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
