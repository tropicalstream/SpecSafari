package com.taphunter.geo

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.os.Build
import android.util.Log
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Bluetooth link to the TapHunter Beam phone app — the real location source
 * on this hardware: the X3's own location stack stays empty indoors, so the
 * phone's GPS rides over RFCOMM (the topology the Everyday project proved:
 * phone listens, glasses connect to the service UUID on a bonded device).
 *
 * Lines in:  TH1 lat lon accuracyM bearingDeg speedMps   (and PING keepalives)
 */
class PhoneLink(
    context: Context,
    private val onFix: (Double, Double, Float) -> Unit
) {
    companion object {
        val BEAM_UUID: UUID = UUID.fromString("8f6a2b58-3c0d-4e2a-9a2e-7c5b9d1f4e21")
        private const val TAG = "PhoneLink"
    }

    private val adapter: BluetoothAdapter? =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
    private val appContext = context.applicationContext
    private val running = AtomicBoolean(false)
    private var socket: BluetoothSocket? = null

    @Volatile var status = "PHONE LINK OFF"; private set
    @Volatile var connected = false; private set

    fun start() {
        if (!running.compareAndSet(false, true)) return
        Thread { connectLoop() }.start()
    }

    fun stop() {
        running.set(false)
        runCatching { socket?.close() }
        socket = null
        connected = false
        status = "PHONE LINK OFF"
    }

    private fun hasPermission(): Boolean =
        Build.VERSION.SDK_INT < 31 ||
            appContext.checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    private fun connectLoop() {
        while (running.get()) {
            val bt = adapter
            if (bt == null || !bt.isEnabled) {
                status = "BLUETOOTH OFF"; sleep(4000); continue
            }
            if (!hasPermission()) { status = "NEEDS BT PERMISSION"; sleep(4000); continue }
            val bonded = runCatching { bt.bondedDevices }.getOrNull().orEmpty()
            // Phone-shaped devices first (never the glasses' own accessories).
            val candidates = bonded.sortedBy { d ->
                val n = d.name?.lowercase() ?: ""
                if ("rayneo" in n || "glasses" in n || "watch" in n ||
                    "buds" in n || "headphone" in n) 1 else 0
            }
            if (candidates.isEmpty()) { status = "PAIR THE PHONE"; sleep(4000); continue }
            for (device in candidates) {
                if (!running.get()) return
                val name = device.name ?: device.address
                status = "TRYING $name"
                try {
                    val s = device.createRfcommSocketToServiceRecord(BEAM_UUID)
                    runCatching { bt.cancelDiscovery() }
                    s.connect()
                    socket = s
                    connected = true
                    status = "PHONE LINK: $name"
                    Log.d(TAG, "connected to $name")
                    readLoop(s)
                } catch (t: Throwable) {
                    // Not this device / beam not running there — try the next.
                    runCatching { socket?.close() }
                } finally {
                    socket = null
                    connected = false
                }
            }
            status = "BEAM NOT FOUND - START THE PHONE APP"
            sleep(3000)
        }
    }

    private fun readLoop(s: BluetoothSocket) {
        val reader = s.inputStream.bufferedReader()
        while (running.get()) {
            val line = reader.readLine() ?: break
            if (!line.startsWith("TH1 ")) continue
            val parts = line.split(' ')
            if (parts.size < 4) continue
            val lat = parts[1].toDoubleOrNull() ?: continue
            val lon = parts[2].toDoubleOrNull() ?: continue
            val acc = parts[3].toFloatOrNull()?.takeIf { it >= 0f } ?: 30f
            onFix(lat, lon, acc)
        }
    }

    private fun sleep(ms: Long) = runCatching { Thread.sleep(ms) }
}
