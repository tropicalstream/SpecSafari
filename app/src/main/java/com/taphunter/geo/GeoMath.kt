package com.taphunter.geo

import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class GeoPoint(val lat: Double, val lon: Double)

/** Pure spherical helpers — no android.location dependency, testable anywhere. */
object GeoMath {
    private const val R = 6371000.0

    fun distanceM(a: GeoPoint, b: GeoPoint): Float = distanceM(a.lat, a.lon, b.lat, b.lon)

    fun distanceM(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
        val p1 = Math.toRadians(lat1); val p2 = Math.toRadians(lat2)
        val dp = Math.toRadians(lat2 - lat1); val dl = Math.toRadians(lon2 - lon1)
        val h = sin(dp / 2) * sin(dp / 2) + cos(p1) * cos(p2) * sin(dl / 2) * sin(dl / 2)
        return (2 * R * asin(sqrt(h))).toFloat()
    }

    /** Initial great-circle bearing a→b, degrees clockwise from north in [0, 360). */
    fun bearingDeg(a: GeoPoint, b: GeoPoint): Float {
        val p1 = Math.toRadians(a.lat); val p2 = Math.toRadians(b.lat)
        val dl = Math.toRadians(b.lon - a.lon)
        val y = sin(dl) * cos(p2)
        val x = cos(p1) * sin(p2) - sin(p1) * cos(p2) * cos(dl)
        var deg = Math.toDegrees(atan2(y, x)).toFloat() % 360f
        if (deg < 0f) deg += 360f
        return deg
    }

    /** Destination point from start along a bearing for a distance. */
    fun destination(start: GeoPoint, bearingDeg: Float, distanceM: Float): GeoPoint {
        val d = distanceM / R
        val brg = Math.toRadians(bearingDeg.toDouble())
        val p1 = Math.toRadians(start.lat)
        val l1 = Math.toRadians(start.lon)
        val p2 = asin(sin(p1) * cos(d) + cos(p1) * sin(d) * cos(brg))
        val l2 = l1 + atan2(sin(brg) * sin(d) * cos(p1), cos(d) - sin(p1) * sin(p2))
        return GeoPoint(Math.toDegrees(p2), Math.toDegrees(l2))
    }

    /** Smallest signed difference a-b in degrees, in [-180, 180). */
    fun angleDiff(a: Float, b: Float): Float {
        var d = (a - b) % 360f
        if (d < -180f) d += 360f
        if (d >= 180f) d -= 360f
        return d
    }

    /** Wrap-aware exponential smoothing for headings. */
    fun smoothHeading(current: Float, target: Float, alpha: Float): Float {
        var h = current + angleDiff(target, current) * alpha
        h %= 360f
        if (h < 0f) h += 360f
        return h
    }

    /** Bearing relative to the wearer's heading: 0 = dead ahead, clockwise. */
    fun relativeDeg(absoluteBearing: Float, heading: Float): Float {
        var rel = (absoluteBearing - heading) % 360f
        if (rel < 0f) rel += 360f
        return rel
    }

    fun prettyDistance(m: Float): String = when {
        m < 1000f -> "${m.toInt()} M"
        else -> String.format("%.1f KM", m / 1000f)
    }

    const val DEG2RAD = PI / 180.0
}
