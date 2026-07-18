package com.taphunter.geo

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock

/**
 * Location on the RayNeo X3 is odd: the OS exposes only `passive` and a
 * `fused` provider (no gps/network), and fixes tend to arrive only when
 * something else — the phone companion link — injects them. So we do what
 * the Everyday project proved works on this hardware: register on EVERY
 * enabled provider (passive included), seed from last-known, and keep
 * prodding with getCurrentLocation while no fresh fix exists.
 */
class LocationSource(context: Context, private val onFix: (GeoPoint, Location?) -> Unit) {

    private val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val handler = Handler(Looper.getMainLooper())
    private var running = false

    var current: GeoPoint? = null; private set
    var accuracyM = 999f; private set
    var lastFixElapsed = 0L; private set
    var fake = false; private set
    var statusText = "STARTING"; private set

    /** Breadcrumbs for the travel bearing (spec: spawn "in the general direction of travel"). */
    private data class Crumb(val p: GeoPoint, val at: Long)
    private val trail = ArrayDeque<Crumb>()

    private val listener = object : LocationListener {
        override fun onLocationChanged(loc: Location) { deliver(loc) }
        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
    }

    private fun deliver(loc: Location) {
        if (fake) return
        val acc = if (loc.hasAccuracy()) loc.accuracy else 50f
        accept(GeoPoint(loc.latitude, loc.longitude), acc, loc)
    }

    private val prodder = object : Runnable {
        override fun run() {
            if (!running || fake) return
            val fresh = current != null &&
                SystemClock.elapsedRealtime() - lastFixElapsed < 5000
            if (!fresh) prodProviders()
            handler.postDelayed(this, 3000)
        }
    }

    @SuppressLint("MissingPermission")
    fun start() {
        if (running || fake) return
        running = true
        // Register on every provider the device admits to having. On the X3
        // that is [passive, fused]; on anything else this still does the
        // right thing (gps/network get included automatically).
        val enabled = runCatching { manager.getProviders(true) }.getOrDefault(emptyList())
        val all = runCatching { manager.allProviders }.getOrDefault(emptyList())
        val wanted = (enabled + all).distinct()
        var registered = 0
        for (p in wanted) {
            runCatching {
                manager.requestLocationUpdates(p, 1000L, 0f, listener)
                registered++
            }
        }
        statusText = "LISTENING ON ${wanted.joinToString(",").uppercase()}"
        // Seed from whatever the OS remembers so the map appears instantly.
        if (current == null) {
            runCatching {
                all.mapNotNull { manager.getLastKnownLocation(it) }
                    .maxByOrNull { it.time }
                    ?.let { deliver(it) }
            }
        }
        handler.postDelayed(prodder, 1500)
    }

    /** Everyday's trick: an explicit current-location request per provider. */
    @SuppressLint("MissingPermission")
    private fun prodProviders() {
        val all = runCatching { manager.allProviders }.getOrDefault(emptyList())
        for (p in all) {
            runCatching {
                if (Build.VERSION.SDK_INT >= 30) {
                    manager.getCurrentLocation(p, null, { r -> handler.post(r) }) { loc ->
                        if (loc != null) deliver(loc)
                    }
                } else {
                    @Suppress("DEPRECATION")
                    manager.requestSingleUpdate(p, listener, Looper.getMainLooper())
                }
            }
        }
        // Last-known may have been refreshed by another app meanwhile.
        runCatching {
            all.mapNotNull { manager.getLastKnownLocation(it) }
                .maxByOrNull { it.time }
                ?.takeIf { System.currentTimeMillis() - it.time < 120_000 }
                ?.let { deliver(it) }
        }
    }

    fun stop() {
        if (!running) return
        running = false
        handler.removeCallbacks(prodder)
        runCatching { manager.removeUpdates(listener) }
    }

    /** Desk-demo hook: adb extras pin the wearer anywhere on Earth. */
    fun setFake(p: GeoPoint) {
        fake = true
        statusText = "DEMO LOCATION"
        accept(p, 5f, null)
    }

    /** Back to reality: real sources resume, the ghost's trail is erased. */
    fun clearFake() {
        if (!fake) return
        fake = false
        current = null
        trail.clear()
        sessionDistanceM = 0f
        statusText = "STARTING"
        start()
    }

    /** Fixes beamed from the TapHunter phone app — the primary real source. */
    fun acceptExternal(lat: Double, lon: Double, acc: Float) {
        if (fake) return
        accept(GeoPoint(lat, lon), acc, null)
        statusText = "PHONE GPS ±${acc.toInt()} M"
    }

    /** Session meters walked, jitter-filtered by the 8 m crumb spacing. */
    var sessionDistanceM = 0f; private set

    private fun accept(p: GeoPoint, acc: Float, loc: Location?) {
        current = p
        accuracyM = acc
        lastFixElapsed = SystemClock.elapsedRealtime()
        if (!fake) statusText = "FIX ${loc?.provider?.uppercase() ?: "?"} ±${acc.toInt()} M"
        val now = System.currentTimeMillis()
        val last = trail.lastOrNull()
        if (last == null || GeoMath.distanceM(last.p, p) >= 8f) {
            if (last != null) {
                val step = GeoMath.distanceM(last.p, p)
                if (step < 120f) sessionDistanceM += step   // teleports don't count
            }
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
