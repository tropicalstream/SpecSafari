package com.specsafari.geo

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
        // Seed from whatever the OS remembers so the map appears instantly —
        // but not a fix from another city: a seed older than 5 minutes is
        // worse than a brief blank (drive somewhere new and yesterday's
        // last-known would flash the old town before the first real fix).
        if (current == null) {
            runCatching {
                all.mapNotNull { manager.getLastKnownLocation(it) }
                    .maxByOrNull { it.time }
                    ?.takeIf { System.currentTimeMillis() - it.time < 5 * 60_000L }
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

    /** Fixes beamed from the SpecSafari phone app — the primary real source. */
    fun acceptExternal(lat: Double, lon: Double, acc: Float, speedMps: Float = -1f) {
        if (fake) return
        accept(GeoPoint(lat, lon), acc, null, speedMps)
        statusText = "PHONE GPS ±${acc.toInt()} M"
    }

    /** Session meters walked, jitter-filtered by the 8 m crumb spacing. */
    var sessionDistanceM = 0f; private set

    private fun accept(
        pRaw: GeoPoint,
        acc: Float,
        loc: Location?,
        reportedSpeedMps: Float = if (loc?.hasSpeed() == true) loc.speed else -1f,
    ) {
        val nowElapsed = SystemClock.elapsedRealtime()
        // Jitter discipline: an indoor ±100 m estimate teleports every second;
        // a wearer walks at ~1.5 m/s. Fixes farther than plausible movement
        // NUDGE the smoothed position (weighted by their quality) instead of
        // snapping it — a real relocation still converges within seconds,
        // but the map stops leaping around the standing hunter.
        val prev = current
        val p: GeoPoint
        if (fake || prev == null) {
            p = pRaw
        } else {
            val dt = ((nowElapsed - lastFixElapsed) / 1000f).coerceIn(0.2f, 30f)
            val d = GeoMath.distanceM(prev, pRaw)
            val plausible = 4f * dt + 10f
            p = if (d <= plausible) pRaw else {
                val w = (25f / acc.coerceAtLeast(1f)).coerceIn(0.10f, 1f)
                GeoMath.destination(prev, GeoMath.bearingDeg(prev, pRaw), d * w)
            }
        }
        current = p
        accuracyM = acc
        lastFixElapsed = nowElapsed
        if (!fake) statusText = "FIX ${loc?.provider?.uppercase() ?: "?"} ±${acc.toInt()} M"
        // Odometer input is deliberately independent of the smoothed map
        // position. Counting map corrections was the source of large phantom
        // walks. Only accurate, pedestrian-speed raw fixes can add distance.
        if (fake) {
            val now = System.currentTimeMillis()
            val last = trail.lastOrNull()
            if (last != null) sessionDistanceM += GeoMath.distanceM(last.p, pRaw)
            trail.addLast(Crumb(pRaw, now))
        } else if (acc <= 25f) {
            val now = System.currentTimeMillis()
            val last = trail.lastOrNull()
            if (last == null) {
                trail.addLast(Crumb(pRaw, now))
            } else {
                val step = GeoMath.distanceM(last.p, pRaw)
                val dt = ((now - last.at) / 1000f).coerceAtLeast(0.2f)
                val observedSpeed = step / dt
                val pedestrian = if (reportedSpeedMps >= 0f) {
                    reportedSpeedMps in 0.35f..3.4f
                } else observedSpeed in 0.35f..3.4f
                val plausible = step <= 3.4f * dt + 5f
                if (dt > 120f || step > 200f) {
                    // Never bridge a long outage: the wearer may have driven
                    // while GPS or the phone beam was unavailable.
                    trail.clear()
                    trail.addLast(Crumb(pRaw, now))
                } else if (step >= 8f && pedestrian && plausible) {
                    sessionDistanceM += step
                    trail.addLast(Crumb(pRaw, now))
                } else if (reportedSpeedMps > 3.4f) {
                    // Driving/riding does not count, but move the baseline so
                    // a later walk begins cleanly at the destination.
                    trail.clear()
                    trail.addLast(Crumb(pRaw, now))
                }
            }
        }
        while (trail.size > 64) trail.removeFirst()
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
