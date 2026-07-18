package com.taphunter.geo

import android.content.Context
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * Absolute compass heading for a heading-up map.
 *
 * RayNeo X3 conventions (verified in the Everyday project): use
 * TYPE_GEOMAGNETIC_ROTATION_VECTOR — it keeps absolute magnetic orientation
 * across display sleep, where the standard rotation vector re-anchors yaw.
 * Worn normally, local +Y is vertical and optical forward is local -Z, so in
 * the Android row-major rotation matrix the forward direction's east/north
 * components are -m[2] and -m[5]; heading = atan2(east, north).
 */
class Compass(context: Context, private val onHeading: (Float) -> Unit) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR)
        ?: sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

    private val quat = FloatArray(4)
    private val matrix = FloatArray(9)
    private var declinationDeg = 0f
    private var smoothed = Float.NaN
    private var running = false

    var headingDeg = 0f; private set
    var valid = false; private set

    fun start() {
        if (running || sensor == null) return
        running = true
        sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI)
    }

    fun stop() {
        if (!running) return
        running = false
        valid = false
        smoothed = Float.NaN
        runCatching { sensorManager.unregisterListener(this) }
    }

    /** True-north correction; call whenever the location moves meaningfully. */
    fun updateDeclination(p: GeoPoint) {
        declinationDeg = GeomagneticField(
            p.lat.toFloat(), p.lon.toFloat(), 0f, System.currentTimeMillis()
        ).declination
    }

    override fun onSensorChanged(event: SensorEvent) {
        SensorManager.getQuaternionFromVector(quat, event.values)
        // getRotationMatrixFromVector wants the [x,y,z,w] vector, use values directly.
        SensorManager.getRotationMatrixFromVector(matrix, event.values)
        val east = -matrix[2]
        val north = -matrix[5]
        if (sqrt(east * east + north * north) < 0.35f) return  // looking straight down/up
        var h = Math.toDegrees(atan2(east.toDouble(), north.toDouble())).toFloat() + declinationDeg
        h %= 360f
        if (h < 0f) h += 360f
        smoothed = if (smoothed.isNaN()) h else GeoMath.smoothHeading(smoothed, h, 0.22f)
        headingDeg = smoothed
        valid = true
        onHeading(smoothed)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
