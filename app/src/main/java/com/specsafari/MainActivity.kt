package com.specsafari

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.FrameLayout
import com.specsafari.audio.Audio
import com.specsafari.gl.HologramView
import com.specsafari.engine.GameEngine
import com.specsafari.engine.Host
import com.specsafari.engine.State
import com.specsafari.geo.Compass
import com.specsafari.geo.GeoPoint
import com.specsafari.geo.GeoMath
import com.specsafari.geo.LocationSource
import com.specsafari.geo.OsmRepository
import com.specsafari.geo.PhoneLink
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt
import java.util.Locale
import java.util.concurrent.Executors

/**
 * Input (FABLE_X3_STARTER_GUIDE Part II + suite conventions):
 *  - Right temple pad swipe -> one discrete direction classified on
 *    finger-up (one gesture = one step; the TapChess standard).
 *  - Temple click arrives as a KEY (BUTTON_A / DPAD_CENTER): select.
 *    Double-tap = back. On the treasure choice only, triple-tap sends a
 *    creature while double-tap opens the cache yourself.
 *  - Left pad (cyttsp6) swallowed.
 *
 * Desk demo (no walking required):
 *   adb shell am start -n com.specsafari/.MainActivity \
 *     --ez demo true [--ef lat 37.7694 --ef lon -122.4862]
 * pins a fake hunter who strolls toward the current target by himself.
 */
class MainActivity : Activity(), Host {

    private lateinit var store: SettingsStore
    private lateinit var audio: Audio
    private lateinit var osm: OsmRepository
    private lateinit var engine: GameEngine
    private lateinit var renderer: Renderer
    private lateinit var gameView: GameView
    private lateinit var sbsRoot: BinocularSbsLayout
    private lateinit var holoView: HologramView
    private lateinit var location: LocationSource
    private lateinit var compass: Compass
    private lateinit var phoneLink: PhoneLink

    private val handler = Handler(Looper.getMainLooper())

    private var keyDownAt = 0L
    private var lastTapUpAt = 0L
    private var lastTapGuard = 0L
    private var pendingClick: Runnable? = null
    private var fetchTapCount = 0
    private var fetchLastTapAt = 0L

    private val geocodeWorker = Executors.newSingleThreadExecutor()
    private var lastRegionPoint: GeoPoint? = null
    private var lastRegionAt = 0L

    private var touchActive = false
    private var touchStartT = 0L
    private var sumX = 0f
    private var sumY = 0f
    private var lastX = 0f
    private var lastY = 0f
    private var dropFirst = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = SettingsStore(this)
        audio = Audio(this).also { it.loadAsync() }
        osm = OsmRepository(this) { r, p -> engine.onOsm(r, p) }
        engine = GameEngine(store, this, osm)
        renderer = Renderer(engine)
        location = LocationSource(this) { p, _ ->
            engine.onLocation(p, locationAccuracy(), locationTravel(), location.sessionDistanceM)
            compass.updateDeclination(p)
            maybeResolveJourneyRegion(p)
        }
        compass = Compass(this) { deg -> engine.onHeading(deg) }
        phoneLink = PhoneLink(
            this,
            onFix = { lat, lon, acc, speed ->
                runOnUiThread { location.acceptExternal(lat, lon, acc, speed) }
            },
            onSetting = { k, v -> runOnUiThread { engine.applyRemoteSetting(k, v) } },
            onConnected = {
                // Do not wait on the rendering looper: the X3 may expose only
                // a brief bidirectional RFCOMM window.
                phoneLink.send("JNY " + engine.journeyCode())
            },
        )
        engine.demoDriver = { p -> location.setFake(p) }
        handler.post(object : Runnable {
            override fun run() {
                engine.locStatus =
                    if (phoneLink.connected) location.statusText
                    else "${location.statusText} · ${phoneLink.status}"
                handler.postDelayed(this, 2000)
            }
        })
        // The HunterDex on the phone stays current over the same link. Poll
        // connection state every second so a newly opened X3 RFCOMM window
        // gets its snapshot immediately; steady links still send only every
        // 15 seconds.
        handler.post(object : Runnable {
            private var lastSentAt = 0L
            override fun run() {
                val now = SystemClock.uptimeMillis()
                if (!phoneLink.connected) lastSentAt = 0L
                else if (lastSentAt == 0L || now - lastSentAt >= 15_000L) {
                    // Compact Journey first: it fits through even the X3's
                    // shortest-lived RFCOMM window; the larger Dex follows.
                    phoneLink.send("JNY " + engine.journeyCode())
                    handler.postDelayed({
                        if (phoneLink.connected) phoneLink.send("DEX " + engine.dexJson())
                    }, 250)
                    lastSentAt = now
                }
                handler.postDelayed(this, 1000)
            }
        })
        gameView = GameView(this, engine, renderer)
        sbsRoot = BinocularSbsLayout(this).apply { addView(gameView) }
        holoView = HologramView(this, engine) { store.sbs }
        setContentView(FrameLayout(this).apply {
            addView(sbsRoot)
            addView(holoView)   // translucent, z-on-top: apparitions over the map
        })
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        hideSystemBars()
        applySettings()
        engine.boot()
        val wanted = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= 31) wanted += Manifest.permission.BLUETOOTH_CONNECT
        val missing = wanted.filter {
            checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) requestPermissions(missing.toTypedArray(), 4001)
        applyDebugExtras(intent)
    }

    private fun locationAccuracy() = location.accuracyM
    private fun locationTravel() = location.travelBearing()

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        intent?.let { applyDebugExtras(it) }
    }

    private fun applyDebugExtras(i: Intent) {
        // Demo is per-launch: a plain launcher start ALWAYS returns to real
        // GPS, even when an old demo process is still alive (learned the
        // hard way — a leftover ghost hunter once kept hunting Dublin Ave).
        if (i.getBooleanExtra("demo", false)) {
            engine.demo = true
            val lat = i.getFloatExtra("lat", 37.7694f).toDouble()
            val lon = i.getFloatExtra("lon", -122.4862f).toDouble()
            location.setFake(GeoPoint(lat, lon))
        } else if (engine.demo) {
            engine.demo = false
            location.clearFake()
            engine.abandonSession()   // the ghost's hunt is not your hunt
        }
    }

    override fun onRequestPermissionsResult(code: Int, perms: Array<String>, results: IntArray) {
        super.onRequestPermissionsResult(code, perms, results)
        location.start()
    }

    // ------------------------------------------------------------ Host

    override fun applySettings() {
        audio.volume = store.soundVolume / 10f
        gameView.frameCap30 = store.frameCap30
        sbsRoot.sbsEnabled = store.sbs
    }

    override fun sound(id: Int, pitch: Float, vol: Float) = audio.play(id, pitch, vol)

    override fun beam(line: String) { if (phoneLink.connected) phoneLink.send(line) }

    // --------------------------------------------------------------- input

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val isTapKey = event.keyCode == KeyEvent.KEYCODE_BUTTON_A ||
            event.keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
            event.keyCode == KeyEvent.KEYCODE_ENTER
        if (isTapKey) {
            when (event.action) {
                KeyEvent.ACTION_DOWN -> if (event.repeatCount == 0) keyDownAt = SystemClock.uptimeMillis()
                KeyEvent.ACTION_UP -> {
                    val now = SystemClock.uptimeMillis()
                    if (now - keyDownAt <= 400) handleClick(now)
                }
            }
            return true
        }
        if (event.action == KeyEvent.ACTION_DOWN) {
            val dir = when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_UP -> 0
                KeyEvent.KEYCODE_DPAD_DOWN -> 1
                KeyEvent.KEYCODE_DPAD_LEFT -> 2
                KeyEvent.KEYCODE_DPAD_RIGHT -> 3
                else -> -1
            }
            if (dir >= 0) { engine.swipeDir(dir); return true }
        }
        if (event.keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
            if (engine.onBack()) return true
        }
        return super.dispatchKeyEvent(event)
    }

    private fun handleClick(now: Long) {
        if (now - lastTapGuard < 35) return
        lastTapGuard = now
        if (engine.state == State.FETCH) {
            handleFetchClick(now)
            return
        }
        val gap = now - lastTapUpAt
        if (gap in 40..320) {
            pendingClick?.let { handler.removeCallbacks(it) }
            pendingClick = null
            lastTapUpAt = 0
            engine.doubleTap()
            return
        }
        lastTapUpAt = now
        if (store.safeTap) {
            val r = Runnable { pendingClick = null; engine.click() }
            pendingClick = r
            handler.postDelayed(r, 300)
        } else engine.click()
    }

    /** The treasure volunteer is consequential: one tap merely acknowledges,
     * double-tap opens the cache yourself, and only a deliberate triple sends
     * (and temporarily releases) the creature. */
    private fun handleFetchClick(now: Long) {
        val gap = now - fetchLastTapAt
        if (fetchTapCount > 0 && gap !in 40..320) fetchTapCount = 0
        fetchLastTapAt = now
        fetchTapCount++
        pendingClick?.let { handler.removeCallbacks(it) }
        pendingClick = null
        if (fetchTapCount >= 3) {
            fetchTapCount = 0
            engine.tripleTap()
            return
        }
        val r = Runnable {
            val count = fetchTapCount
            fetchTapCount = 0
            pendingClick = null
            if (count == 2) engine.doubleTap() else engine.click()
        }
        pendingClick = r
        handler.postDelayed(r, 330)
    }

    /** Resolve only a postal/admin-sized region for the shareable journey.
     * Exact coordinates, addresses, and street names are never persisted. */
    @Suppress("DEPRECATION")
    private fun maybeResolveJourneyRegion(p: GeoPoint) {
        val now = System.currentTimeMillis()
        val previous = lastRegionPoint
        if (previous != null && now - lastRegionAt < 30 * 60_000L &&
            GeoMath.distanceM(previous, p) < 2_000f
        ) return
        lastRegionPoint = p
        lastRegionAt = now
        geocodeWorker.execute {
            val region = runCatching {
                val a = Geocoder(this, Locale.getDefault())
                    .getFromLocation(p.lat, p.lon, 1)?.firstOrNull()
                a?.postalCode?.takeIf { it.isNotBlank() }
                    ?: a?.subAdminArea?.takeIf { it.isNotBlank() }
                    ?: a?.adminArea?.takeIf { it.isNotBlank() }
                    ?: a?.countryName
            }.getOrNull()
            if (!region.isNullOrBlank()) runOnUiThread { engine.onJourneyRegion(region) }
        }
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        val name = ev.device?.name ?: ""
        if (name.contains("cyttsp6", ignoreCase = true)) return true

        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                touchActive = true; touchStartT = SystemClock.uptimeMillis()
                sumX = 0f; sumY = 0f; lastX = ev.x; lastY = ev.y; dropFirst = true
            }
            MotionEvent.ACTION_MOVE -> {
                if (!touchActive) {
                    touchActive = true; touchStartT = SystemClock.uptimeMillis()
                    sumX = 0f; sumY = 0f; lastX = ev.x; lastY = ev.y; dropFirst = true
                } else {
                    var dx = ev.x - lastX; var dy = ev.y - lastY
                    lastX = ev.x; lastY = ev.y
                    if (dropFirst) dropFirst = false else {
                        if (store.flipHorizontal) dx = -dx
                        if (store.flipVertical) dy = -dy
                        sumX += dx; sumY += dy
                    }
                }
            }
            MotionEvent.ACTION_UP -> {
                if (touchActive) resolveGesture(SystemClock.uptimeMillis())
                touchActive = false
            }
            MotionEvent.ACTION_CANCEL -> touchActive = false
        }
        return true
    }

    /** One swipe = one discrete step, classified on finger-up (suite standard). */
    private fun resolveGesture(now: Long) {
        val dist = sqrt(sumX * sumX + sumY * sumY)
        val threshold = max(48f, 0.09f * resources.displayMetrics.widthPixels) / store.swipeSens
        if (dist >= threshold) {
            val dir = if (abs(sumX) >= abs(sumY)) { if (sumX > 0) 3 else 2 } else { if (sumY < 0) 0 else 1 }
            engine.swipeDir(dir)
        } else if (now - touchStartT <= 320) {
            handleClick(now)
        }
    }

    // ------------------------------------------------------------ lifecycle

    override fun onResume() {
        super.onResume()
        hideSystemBars()
        applySettings()
        location.start()
        compass.start()
        phoneLink.start()
        gameView.start()
        holoView.onResume()
        engine.boot()
    }

    override fun onPause() {
        engine.onAppPause()
        location.stop()
        compass.stop()
        phoneLink.stop()
        gameView.stop()
        holoView.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        audio.release()
        geocodeWorker.shutdownNow()
        super.onDestroy()
    }

    private fun hideSystemBars() {
        if (Build.VERSION.SDK_INT >= 30) {
            window.setDecorFitsSystemWindows(false)
            window.insetsController?.let {
                it.hide(WindowInsets.Type.systemBars())
                it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility =
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        }
    }
}
