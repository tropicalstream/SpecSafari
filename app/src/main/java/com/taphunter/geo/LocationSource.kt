package com.taphunter.geo

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.SystemClock

/**
 * Best-of-all-providers location. The X3's OS exposes fused/gps/network
 * through the framework LocationManager (the Everyday speedometer proved
 * which ones answer); we listen to every enabled provider and keep the
 * freshest accurate fix. A fake mode drives desk demos and screenshots.
 */
class LocationSource(context: Context, private val onFix: (GeoPoint, Location?) -> Unit) {

    private val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private var running = false

    var current: GeoPoint? = null; private set
    var accuracyM = 999f; private set
    var lastFixElapsed = 0L; private set
    var fake = false; private set

    /** Breadcrumbs for the travel bearing (spec: spawn "in the general direction of travel"). */
    private data class Crumb(val p: GeoPoint, val at: Long)
    private val trail = ArrayDeque<Crumb>()

    private val listener = object : LocationListener {
        override fun onLocationChanged(loc: Location) {
            if (fake) return
            // Prefer the new fix unless it is both older-grade and much less accurate.
            if (loc.accuracy > 80f && accuracyM < 40f &&
                SystemClock.elapsedRealtime() - lastFixElapsed < 8000) return
            accept(GeoPoint(loc.latitude, loc.longitude), loc.accuracy, loc)
        }
        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
    }

    @SuppressLint("MissingPermission")
    fun start() {
        if (running || fake) return
        running = true
        val wanted = listOf("fused", LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
        for (p in wanted) {
            runCatching {
                if (manager.allProviders.contains(p) && manager.isProviderEnabled(p)) {
                    manager.requestLocationUpdates(p, 1000L, 0f, listener)
                }
            }
        }
        // Seed from whatever the OS remembers so the map appears instantly.
        if (current == null) {
            runCatching {
                manager.allProviders
                    .mapNotNull { manager.getLastKnownLocation(it) }
                    .minByOrNull { it.accuracy }
                    ?.let { accept(GeoPoint(it.latitude, it.longitude), it.accuracy + 30f, it) }
            }
        }
    }

    fun stop() {
        if (!running) return
        running = false
        runCatching { manager.removeUpdates(listener) }
    }

    /** Desk-demo hook: adb extras pin the wearer anywhere on Earth. */
    fun setFake(p: GeoPoint) {
        fake = true
        accept(p, 5f, null)
    }

    private fun accept(p: GeoPoint, acc: Float, loc: Location?) {
        current = p
        accuracyM = acc
        lastFixElapsed = SystemClock.elapsedRealtime()
        val now = System.currentTimeMillis()
        val last = trail.lastOrNull()
        if (last == null || GeoMath.distanceM(last.p, p) >= 8f) {
            trail.addLast(Crumb(p, now))
            while (trail.size > 64) trail.removeFirst()
        }
        onFix(p, loc)
    }

    /**
     * Direction the wearer is walking: bearing from the crumb ~50 m back to
     * here. Null until they have actually moved (~20 m) this session.
     */
    fun travelBearing(): Float? {
        val here = current ?: return null
        var anchor: Crumb? = null
        for (c in trail.reversed()) {
            anchor = c
            if (GeoMath.distanceM(c.p, here) >= 50f) break
        }
        val a = anchor ?: return null
        if (GeoMath.distanceM(a.p, here) < 20f) return null
        return GeoMath.bearingDeg(a.p, here)
    }

    fun hasRecentFix(maxAgeMs: Long = 30_000L): Boolean =
        current != null && (fake || SystemClock.elapsedRealtime() - lastFixElapsed <= maxAgeMs)
}
