package com.taphunter.phone

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/**
 * One screen, one job: start the beam, watch it work. Pair the phone with
 * the glasses in Bluetooth settings first (already true if Everyday runs).
 */
class MainActivity : Activity() {

    private lateinit var status: TextView
    private lateinit var fix: TextView
    private lateinit var sent: TextView
    private lateinit var toggle: Button
    private val handler = Handler(Looper.getMainLooper())

    private val poll = object : Runnable {
        override fun run() {
            status.text = "Link: ${LocationBeamService.linkStatus}"
            fix.text = "Fix: ${LocationBeamService.lastFixText}"
            sent.text = "Sent: ${LocationBeamService.fixesSent}"
            toggle.text = if (LocationBeamService.running) "STOP BEAM" else "START BEAM"
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setBackgroundColor(Color.rgb(10, 24, 32))
            setPadding(48, 160, 48, 48)
        }
        fun text(sizeSp: Float, bold: Boolean = false) = TextView(this).apply {
            textSize = sizeSp
            setTextColor(Color.rgb(220, 245, 255))
            if (bold) typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(0, 12, 0, 12)
        }
        root.addView(text(28f, bold = true).apply { text = "TapHunter Beam" })
        root.addView(text(15f).apply {
            text = "Streams this phone's GPS to the TapHunter glasses game over Bluetooth. " +
                "Pair the phone with the glasses first, then start the beam and go hunt."
        })
        status = text(18f, bold = true); root.addView(status)
        fix = text(15f); root.addView(fix)
        sent = text(15f); root.addView(sent)
        toggle = Button(this).apply {
            textSize = 18f
            setOnClickListener { toggleBeam() }
        }
        root.addView(toggle)
        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        handler.post(poll)
    }

    override fun onPause() {
        handler.removeCallbacks(poll)
        super.onPause()
    }

    private fun neededPermissions(): Array<String> {
        val wanted = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= 31) wanted += Manifest.permission.BLUETOOTH_CONNECT
        if (Build.VERSION.SDK_INT >= 33) wanted += Manifest.permission.POST_NOTIFICATIONS
        return wanted.filter {
            checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()
    }

    private fun toggleBeam() {
        if (LocationBeamService.running) {
            startService(Intent(this, LocationBeamService::class.java).setAction("stop"))
            return
        }
        val missing = neededPermissions()
        if (missing.isNotEmpty()) {
            requestPermissions(missing, 1)
            return
        }
        startBeam()
    }

    override fun onRequestPermissionsResult(code: Int, perms: Array<String>, results: IntArray) {
        super.onRequestPermissionsResult(code, perms, results)
        if (neededPermissions().isEmpty()) startBeam()
    }

    private fun startBeam() {
        val i = Intent(this, LocationBeamService::class.java)
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(i) else startService(i)
    }
}
