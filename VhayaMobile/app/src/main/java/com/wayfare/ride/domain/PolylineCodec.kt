package com.wayfare.ride.domain

import kotlin.math.roundToInt

object PolylineCodec {
    fun decode(encoded: String): List<RideCoordinates> {
        if (encoded.isBlank()) return emptyList()
        val points = mutableListOf<RideCoordinates>()
        var index = 0
        var lat = 0
        var lng = 0

        while (index < encoded.length) {
            var result = 0
            var shift = 0
            var byte: Int
            do {
                if (index >= encoded.length) return points
                byte = encoded[index++].code - 63
                result = result or ((byte and 0x1f) shl shift)
                shift += 5
            } while (byte >= 0x20)
            lat += if ((result and 1) != 0) (result shr 1).inv() else result shr 1

            result = 0
            shift = 0
            do {
                if (index >= encoded.length) return points
                byte = encoded[index++].code - 63
                result = result or ((byte and 0x1f) shl shift)
                shift += 5
            } while (byte >= 0x20)
            lng += if ((result and 1) != 0) (result shr 1).inv() else result shr 1

            points.add(RideCoordinates(lat / 100_000.0, lng / 100_000.0))
        }
        return points
    }

    fun encode(points: List<RideCoordinates>): String {
        val sb = StringBuilder()
        var lastLat = 0
        var lastLng = 0
        for (point in points) {
            val lat = (point.latitude * 100_000.0).roundToInt()
            val lng = (point.longitude * 100_000.0).roundToInt()
            encodeValue(lat - lastLat, sb)
            encodeValue(lng - lastLng, sb)
            lastLat = lat
            lastLng = lng
        }
        return sb.toString()
    }

    private fun encodeValue(value: Int, sb: StringBuilder) {
        var v = if (value < 0) (value shl 1).inv() else value shl 1
        while (v >= 0x20) {
            sb.append(((0x20 or (v and 0x1f)) + 63).toChar())
            v = v shr 5
        }
        sb.append((v + 63).toChar())
    }
}
