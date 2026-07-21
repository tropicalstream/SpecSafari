package com.specsafari.phone

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.Intent
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import java.io.OutputStream
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The beam: streams this phone's GPS to the SpecSafari glasses app over
 * Bluetooth RFCOMM. The phone is the server (the proven Everyday topology
 * for this hardware pair); the glasses connect to our service UUID, which
 * is SpecSafari's own — Everyday's channel is untouched and both can run.
 *
 * Wire format, one line per fix:  TH1 lat lon accuracyM bearingDeg speedMps
 */
class LocationBeamService : Service() {

    companion object {
        val BEAM_UUID: UUID = UUID.fromString("8f6a2b58-3c0d-4e2a-9a2e-7c5b9d1f4e21")
        private const val SERVICE_NAME = "SpecSafariBeam"
        private const val TAG = "SpecSafariBeam"
        private const val CHANNEL = "beam"

        // Simple status surface the activity polls.
        @Volatile var running = false
        @Volatile var linkStatus = "OFF"
        @Volatile var lastFixText = "no fix yet"
        @Volatile var fixesSent = 0
        @Volatile var connected = false
        @Volatile var instance: LocationBeamService? = null

        /** Latest progress snapshot from the glasses, JSON (HunterDex). */
        @Volatile var dexJson: String? = null
        @Volatile var lastLat = Double.NaN
        @Volatile var lastLon = Double.NaN

        /** Live moments beamed from the glasses (treasure, fetch) for the den to
         *  celebrate. Timestamped so a den opened long afterward ignores stale ones. */
        class BeamEvent(val line: String, val at: Long)
        val events = java.util.concurrent.ConcurrentLinkedQueue<BeamEvent>()

        /** Push a settings line to the glasses; false when unlinked. */
        fun sendLine(line: String): Boolean = instance?.sendRaw(line) ?: false
    }

    private val alive = AtomicBoolean(false)
    private var serverSocket: BluetoothServerSocket? = null
    private var client: BluetoothSocket? = null
    private var out: OutputStream? = null
    private var lastFix: Location? = null

    private val listener = object : LocationListener {
        override fun onLocationChanged(loc: Location) {
            lastFix = loc
            lastLat = loc.latitude; lastLon = loc.longitude
            lastFixText = "%.5f, %.5f  ±%.0fm".format(loc.latitude, loc.longitude, loc.accuracy)
            send(loc)
        }
        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(p: String?, s: Int, e: android.os.Bundle?) {}
        override fun onProviderEnabled(p: String) {}
        override fun onProviderDisabled(p: String) {}
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "stop") {
            stopSelf()
            return START_NOT_STICKY
        }
        if (alive.compareAndSet(false, true)) {
            running = true
            instance = this
            dexJson = loadDex()
            startForeground(1, notification())
            startLocation()
            acceptLoop()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        alive.set(false)
        running = false
        connected = false
        if (instance === this) instance = null
        linkStatus = "OFF"
        runCatching { (getSystemService(Context.LOCATION_SERVICE) as LocationManager).removeUpdates(listener) }
        runCatching { client?.close() }
        runCatching { serverSocket?.close() }
        super.onDestroy()
    }

    // ---------------------------------------------------------- location

    @SuppressLint("MissingPermission")
    private fun startLocation() {
        val lm = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        for (p in listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, "fused")) {
            runCatching { lm.requestLocationUpdates(p, 1000L, 0f, listener) }
        }
        runCatching {
            lm.allProviders.mapNotNull { lm.getLastKnownLocation(it) }
                .maxByOrNull { it.time }?.let { listener.onLocationChanged(it) }
        }
    }

    // ---------------------------------------------------------- bluetooth

    @SuppressLint("MissingPermission")
    private fun acceptLoop() {
        Thread {
            val adapter: BluetoothAdapter? =
                (getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
            if (adapter == null) { linkStatus = "NO BLUETOOTH"; return@Thread }
            while (alive.get()) {
                try {
                    linkStatus = "WAITING FOR GLASSES"
                    serverSocket = adapter.listenUsingRfcommWithServiceRecord(SERVICE_NAME, BEAM_UUID)
                    val socket = serverSocket?.accept() ?: continue
                    runCatching { serverSocket?.close() }
                    client = socket
                    out = socket.outputStream
                    connected = true
                    linkStatus = "BEAMING TO GLASSES"
                    lastFix?.let { send(it) }
                    readGlasses(socket)   // spawns the DEX reader
                    // Heartbeat keeps the link honest; a dead pipe throws here.
                    while (alive.get() && socket.isConnected) {
                        Thread.sleep(5000)
                        synchronized(this) { out?.write("PING\n".toByteArray()); out?.flush() }
                    }
                } catch (t: Throwable) {
                    Log.w(TAG, "link dropped: ${t.message}")
                } finally {
                    runCatching { client?.close() }
                    client = null; out = null
                    connected = false
                    if (alive.get()) linkStatus = "RECONNECTING"
                }
            }
        }.start()
    }

    /** The glasses talk back: DEX progress snapshots for the HunterDex. */
    private fun readGlasses(socket: BluetoothSocket) {
        Thread {
            runCatching {
                val reader = socket.inputStream.bufferedReader()
                while (alive.get()) {
                    val line = reader.readLine() ?: break
                    if (line.startsWith("DEX ")) {
                        val json = line.removePrefix("DEX ").trim()
                        dexJson = json
                        getSharedPreferences("hunterdex", Context.MODE_PRIVATE)
                            .edit().putString("dex", json)
                            .putLong("dexAt", System.currentTimeMillis()).apply()
                    } else if (line.startsWith("EVT ")) {
                        events.add(BeamEvent(line.removePrefix("EVT ").trim(),
                            System.currentTimeMillis()))
                        while (events.size > 16) events.poll()   // never hoard
                    }
                }
            }
        }.start()
    }

    private fun loadDex(): String? =
        getSharedPreferences("hunterdex", Context.MODE_PRIVATE).getString("dex", null)

    fun sendRaw(line: String): Boolean {
        val o = out ?: return false
        Thread {
            runCatching { synchronized(this) { o.write((line + "\n").toByteArray()); o.flush() } }
        }.start()
        return true
    }

    private fun send(loc: Location) {
        val o = out ?: return
        val line = "TH1 %.7f %.7f %.1f %.1f %.2f\n".format(
            loc.latitude, loc.longitude,
            if (loc.hasAccuracy()) loc.accuracy else -1f,
            if (loc.hasBearing()) loc.bearing else -1f,
            if (loc.hasSpeed()) loc.speed else -1f
        )
        Thread {
            runCatching {
                synchronized(this) { o.write(line.toByteArray()); o.flush() }
                fixesSent++
            }
        }.start()
    }

    // ------------------------------------------------------- notification

    private fun notification(): Notification {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "GPS beam", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val builder = if (Build.VERSION.SDK_INT >= 26) {
            Notification.Builder(this, CHANNEL)
        } else @Suppress("DEPRECATION") Notification.Builder(this)
        return builder
            .setContentTitle("SpecSafari beam")
            .setContentText("Streaming GPS to the glasses")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .build()
    }
}
