package com.wayfare.ride.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wayfare.ride.domain.PolylineCodec
import com.wayfare.ride.domain.RideCoordinates
import com.wayfare.ride.domain.RideRoute
import com.wayfare.ride.ui.theme.LocalWayfareColors

@Composable
fun OsmRouteMiniPreview(
    pickup: RideCoordinates?,
    destination: RideCoordinates?,
    route: RideRoute?,
    modifier: Modifier = Modifier
) {
    val wayfareColors = LocalWayfareColors.current
    val colorScheme = MaterialTheme.colorScheme
    val points = remember(route) {
        route?.encodedPolyline?.let { PolylineCodec.decode(it) }.orEmpty()
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(wayfareColors.mapBlock)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val step = 36f
            var x = step
            while (x < size.width) {
                drawLine(wayfareColors.mapRoad, Offset(x, 0f), Offset(x, size.height), strokeWidth = 2.5f)
                x += step
            }
            var y = step
            while (y < size.height) {
                drawLine(wayfareColors.mapRoad, Offset(0f, y), Offset(size.width, y), strokeWidth = 2.5f)
                y += step
            }

            val all = if (points.size >= 2) points else listOfNotNull(pickup, destination)
            if (all.size >= 2) {
                val minLat = all.minOf { it.latitude }
                val maxLat = all.maxOf { it.latitude }
                val minLon = all.minOf { it.longitude }
                val maxLon = all.maxOf { it.longitude }
                val latSpan = (maxLat - minLat).coerceAtLeast(0.005)
                val lonSpan = (maxLon - minLon).coerceAtLeast(0.005)
                val pad = 28f
                val w = (size.width - pad * 2).coerceAtLeast(10f)
                val h = (size.height - pad * 2).coerceAtLeast(10f)

                fun mapPt(c: RideCoordinates): Offset {
                    val nx = ((c.longitude - minLon) / lonSpan).toFloat()
                    val ny = (1.0 - (c.latitude - minLat) / latSpan).toFloat()
                    return Offset(pad + nx * w, pad + ny * h)
                }

                val path = Path().apply {
                    val first = mapPt(all.first())
                    moveTo(first.x, first.y)
                    for (i in 1 until all.size) {
                        val pt = mapPt(all[i])
                        lineTo(pt.x, pt.y)
                    }
                }
                drawPath(path, color = colorScheme.primary, style = Stroke(width = 11f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                drawPath(path, color = wayfareColors.routeHighlight, style = Stroke(width = 5f, cap = StrokeCap.Round, join = StrokeJoin.Round))

                val start = mapPt(all.first())
                val end = mapPt(all.last())
                drawCircle(colorScheme.surface, 10f, start)
                drawCircle(colorScheme.primary, 6f, start)
                drawCircle(colorScheme.surface, 10f, end)
                drawCircle(wayfareColors.destinationMarker, 6f, end)
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(8.dp)
                .background(colorScheme.surface.copy(alpha = 0.86f), RoundedCornerShape(8.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Text(
                text = route?.let { "${"%.1f".format(it.distanceMeters / 1000.0)} km · ${(it.durationSeconds / 60).coerceAtLeast(1)} min" } ?: "Route preview",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = colorScheme.onSurface
            )
        }
    }
}
