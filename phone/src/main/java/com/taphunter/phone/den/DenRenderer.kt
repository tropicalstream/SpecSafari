package com.taphunter.phone.den

import android.opengl.GLES20.*
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import com.taphunter.shared.EthoModel
import com.taphunter.shared.Species
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.concurrent.ConcurrentLinkedQueue
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** One resident of the world. */
class DenC(val species: Int) {
    var x = 2f; var z = 0f
    var vx = 0f; var vz = 0f
    var phase = Random.nextFloat() * 6f
    var pauseT = 0f
    var happyT = 0f
    var headingDeg = Random.nextFloat() * 360f   // true facing, world yaw
    var bank = 0f                                 // lean into turns (fliers)
    var targetX = Float.NaN
    var targetZ = 0f
    var targetT = 0f           // patience: give up on unreachable places
    var lingerT = 0f
    var under = false          // burrowers: traveling as a molehill
    var underT = 0f
    var meetCd = 0f
    var inWater = false        // for the splash on the way in
    val scale = 0.88f + Random.nextFloat() * 0.24f
    // Perception of the human: arousal and the flight response.
    var fleeing = false
    var startleCd = 0f         // refractory period after a flush
    var alert = false          // oriented and monitoring, not yet fleeing
    var soliciting = false     // food-conditioned approach toward the human
    var solicitCd = 0f
    // The biological clock: waking hours, then home to the nest to sleep.
    var nestX = 2f; var nestZ = -1f
    var awakeT = 20f + Random.nextFloat() * 50f
    var sleeping = false
    var homing = false
    var chaseIdx = -1          // playmate being chased, -1 none
    var chaseT = 0f
    var releasing = false      // running free, off the edge of the world
}

private class Particle(var x: Float, var y: Float, var z: Float,
                       var vy: Float, var life: Float, var maxLife: Float,
                       var r: Float, var g: Float, var b: Float, var size: Float)

private class Ripple(val x: Float, val z: Float, var age: Float = 0f)

/** A standing body of water the world's physics knows about. */
private class WaterBody(val x: Float, val z: Float, val r: Float, val warm: Boolean)

class DenRenderer : GLSurfaceView.Renderer {

    companion object {
        const val EV_MEET = 0; const val EV_CHASE_END = 1
        const val EV_RELEASED = 2; const val EV_WAKE = 3
        // Behavioral events for the field-history recorder + audio.
        const val BEV_FLEE = 0; const val BEV_SOLICIT = 1; const val BEV_CLOSE = 2
    }

    @Volatile private var placedIds: List<String> = emptyList()
    @Volatile private var population: List<Int> = emptyList()
    @Volatile private var rebuild = true
    @Volatile var selected = -1
    @Volatile private var glideX = Float.NaN

    /** (event, species) pairs for the activity's audio layer. */
    val audioEvents = ConcurrentLinkedQueue<IntArray>()

    /** (BEV_*, species, arg) for the field-history recorder. */
    val behaviorEvents = ConcurrentLinkedQueue<IntArray>()

    // Learned per-species quantities, fed from the recorded field history.
    @Volatile private var fieldHabit = FloatArray(Species.ALL.size)
    @Volatile private var fieldFamiliar = FloatArray(Species.ALL.size)
    @Volatile private var fieldFood = FloatArray(Species.ALL.size)

    fun setFieldStats(habit: FloatArray, familiar: FloatArray, food: FloatArray) {
        fieldHabit = habit; fieldFamiliar = familiar; fieldFood = food
    }

    // The human's kinematics — position, velocity, gaze — for the FID model.
    private var prevCamX = 2.5f; private var prevCamZ = 4.2f
    @Volatile private var playerVX = 0f; @Volatile private var playerVZ = 0f
    @Volatile private var playerSpeed = 0f
    @Volatile private var fwdXv = 0f; @Volatile private var fwdZv = -1f
    private var closeCd = 0f

    // ------- the real sky: local clock + local weather over the den
    @Volatile var weatherCode = 0        // WMO code from Open-Meteo, 0 = clear
    @Volatile var windKmh = 0f
    private var flashT = 0f              // thunder
    private var bakedDay = -1f           // ground bake tracks the daylight

    fun setWeather(code: Int, wind: Float) { weatherCode = code; windKmh = wind }

    /** 0 = deep night, 1 = midday, smooth dawn/dusk ramps from the clock. */
    private fun dayLerp(): Float {
        val cal = java.util.Calendar.getInstance()
        val h = cal.get(java.util.Calendar.HOUR_OF_DAY) + cal.get(java.util.Calendar.MINUTE) / 60f
        return when {
            h < 5f || h >= 21f -> 0f
            h < 8f -> (h - 5f) / 3f          // dawn
            h < 17f -> 1f
            else -> 1f - (h - 17f) / 4f      // long dusk
        }
    }

    private val raining get() = weatherCode in 51..67 || weatherCode in 80..82 || weatherCode >= 95
    private val snowing get() = weatherCode in 71..77 || weatherCode == 85 || weatherCode == 86
    private val foggy get() = weatherCode == 45 || weatherCode == 48
    private val overcast get() = weatherCode == 3 || raining || snowing

    /** Nocturnal souls: dusk-stalkers and void-things keep opposite hours. */
    private val nocturnal = setOf(10, 11, 12, 13)

    private fun dayMix(c: Int, day: Float): Int {
        // Daylight lifts the palette toward a pale sky-wash; overcast mutes it.
        val k = day * (if (overcast) 0.45f else 0.7f)
        val dr = 150; val dg = 185; val db = 215
        return android.graphics.Color.rgb(
            (android.graphics.Color.red(c) * (1 - k) + dr * k).toInt(),
            (android.graphics.Color.green(c) * (1 - k) + dg * k).toInt(),
            (android.graphics.Color.blue(c) * (1 - k) + db * k).toInt()
        )
    }

    val creatures = ArrayList<DenC>()
    private val particles = ArrayList<Particle>()
    private var mainProg = 0; private var skyProg = 0; private var ptProg = 0
    private var waterProg = 0
    private var sceneMesh: Mesh? = null
    private var itemMeshes = listOf<Mesh>()
    private val forms = HashMap<Int, Mesh>()
    private var shadowMesh: Mesh? = null
    private var nestMesh: Mesh? = null
    private var flameMesh: Mesh? = null
    private var glowMesh: Mesh? = null
    private var ringMesh: Mesh? = null
    private var waterBuf: FloatBuffer? = null
    private var waterVerts = 0
    // The world's elemental furniture, gathered at scene build.
    private val flamePts = ArrayList<FloatArray>()     // x, y, z
    private val lanternPts = ArrayList<FloatArray>()
    private val sparklePts = ArrayList<FloatArray>()
    private val waterBodies = ArrayList<WaterBody>()
    private val ripples = ArrayList<Ripple>()
    /** Solid things: x, z, radius. Nobody walks through wood or fire. */
    private val obstacles = ArrayList<FloatArray>()

    /** Push a point out of every solid it overlaps; returns corrected x,z. */
    private fun resolve(px: Float, pz: Float, rad: Float): FloatArray {
        var x = px; var z = pz
        for (pass in 0 until 2) {
            var moved = false
            for (o in obstacles) {
                val dx = x - o[0]; val dz = z - o[1]
                val min = o[2] + rad
                val d2 = dx * dx + dz * dz
                if (d2 < min * min) {
                    val d = kotlin.math.sqrt(d2).coerceAtLeast(0.001f)
                    x = o[0] + dx / d * min; z = o[1] + dz / d * min
                    moved = true
                }
            }
            if (!moved) break
        }
        return floatArrayOf(x, z)
    }
    private val proj = FloatArray(16); private val view = FloatArray(16)
    private val vp = FloatArray(16); private val model = FloatArray(16)
    @Volatile private var lastVp = FloatArray(16)
    private var viewW = 1; private var viewH = 1
    private var lastNs = 0L
    private var t = 0f
    private val skyBuf: FloatBuffer = ByteBuffer.allocateDirect(8 * 4)
        .order(ByteOrder.nativeOrder()).asFloatBuffer()
        .put(floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f)).apply { position(0) }

    // First-person stroll.
    @Volatile var camPX = 2.5f; private set
    @Volatile private var camPZ = 4.2f
    private val camPY = 1.35f
    @Volatile var yawDeg = 0f
    @Volatile var pitchDeg = -6f
    @Volatile private var moveX = 0f
    @Volatile private var moveY = 0f

    fun setMove(x: Float, y: Float) { moveX = x.coerceIn(-1f, 1f); moveY = y.coerceIn(-1f, 1f) }
    fun look(dxDeg: Float, dyDeg: Float) {
        yawDeg = (yawDeg + dxDeg) % 360f
        pitchDeg = (pitchDeg + dyDeg).coerceIn(-55f, 40f)
    }
    fun resetCamera() { camPX = 2.5f; camPZ = 4.2f; yawDeg = 0f; pitchDeg = -6f; setMove(0f, 0f) }

    /** Fast travel: the camera glides to a biome; the world stays one world. */
    fun travelTo(biome: Int) { glideX = Habitats.BIOMES[biome.coerceIn(0, 3)].center }

    fun configure(pop: List<Int>, items: List<String>) {
        population = pop; placedIds = items; rebuild = true
    }

    fun placeItems(items: List<String>) { placedIds = items; rebuild = true }

    fun celebrate(index: Int, big: Boolean) {
        val c = creatures.getOrNull(index) ?: return
        c.happyT = if (big) 3.2f else 2f
        c.pauseT = 1f
        if (c.under) { c.under = false; c.underT = 6f }
        if (c.sleeping) { c.sleeping = false; c.awakeT = 30f; audioEvents.add(intArrayOf(EV_WAKE, c.species)) }
    }

    /** Set a resident free: it runs for the world's edge and is gone. */
    fun release(index: Int) {
        val c = creatures.getOrNull(index) ?: return
        c.releasing = true
        c.sleeping = false; c.under = false
        c.targetX = if (c.x < Habitats.WORLD_W / 2f) -2.5f else Habitats.WORLD_W + 2.5f
        c.targetZ = c.z
        selected = -1
    }

    fun pick(px: Float, py: Float): Int {
        val vpm = lastVp
        var bestI = -1; var bestD = (viewW * 0.13f) * (viewW * 0.13f)
        val inV = FloatArray(4); val outV = FloatArray(4)
        synchronized(creatures) {
            for ((i, c) in creatures.withIndex()) {
                if (c.releasing) continue
                inV[0] = c.x; inV[1] = 0.5f; inV[2] = c.z; inV[3] = 1f
                Matrix.multiplyMV(outV, 0, vpm, 0, inV, 0)
                if (outV[3] <= 0f) continue
                val sx = (outV[0] / outV[3] * 0.5f + 0.5f) * viewW
                val sy = (1f - (outV[1] / outV[3] * 0.5f + 0.5f)) * viewH
                val d = (sx - px) * (sx - px) + (sy - py) * (sy - py)
                if (d < bestD) { bestD = d; bestI = i }
            }
        }
        return bestI
    }

    // ------------------------------------------------------------ GL life

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        mainProg = DenGL.program(DenGL.MAIN_VS, DenGL.MAIN_FS)
        skyProg = DenGL.program(DenGL.SKY_VS, DenGL.SKY_FS)
        ptProg = DenGL.program(DenGL.PT_VS, DenGL.PT_FS)
        waterProg = DenGL.program(DenGL.WATER_VS, DenGL.WATER_FS)
        glEnable(GL_DEPTH_TEST)
        forms.clear()
        shadowMesh = CreatureForms.shadow()
        nestMesh = buildNest()
        flameMesh = MeshBuilder().apply {
            cone(0f, 0f, 0f, 0.07f, 0f, 1f, 0f, 0.34f, android.graphics.Color.rgb(255, 150, 60), 0.9f, 6)
            cone(0f, 0.03f, 0f, 0.04f, 0f, 1f, 0f, 0.24f, android.graphics.Color.rgb(255, 230, 130), 0.95f, 5)
        }.bake()
        glowMesh = MeshBuilder().apply {
            ellipsoid(0f, 0f, 0f, 0.12f, 0.14f, 0.12f, android.graphics.Color.rgb(255, 190, 90), 0.95f, 6, 8)
        }.bake()
        ringMesh = MeshBuilder().apply {
            val segs = 22
            for (k in 0 until segs) {
                val a0 = k * (Math.PI * 2 / segs).toFloat(); val a1 = (k + 1) * (Math.PI * 2 / segs).toFloat()
                quad(cos(a0) * 0.9f, 0f, sin(a0) * 0.9f, cos(a1) * 0.9f, 0f, sin(a1) * 0.9f,
                    cos(a1), 0f, sin(a1), cos(a0), 0f, sin(a0),
                    android.graphics.Color.rgb(190, 245, 255), 0.85f, true)
            }
        }.bake()
        buildWaterDisc()
        rebuild = true
        lastNs = 0L
    }

    /** A tessellated unit disc whose vertices the water shader can wave. */
    private fun buildWaterDisc() {
        val rings = 6; val sectors = 18
        val v = ArrayList<Float>()
        fun pt(ri: Int, si: Int): FloatArray {
            val r = ri.toFloat() / rings
            val a = si * (Math.PI * 2 / sectors)
            return floatArrayOf((cos(a) * r).toFloat(), 0f, (sin(a) * r).toFloat())
        }
        for (ri in 0 until rings) for (si in 0 until sectors) {
            val a = pt(ri, si); val b = pt(ri + 1, si)
            val c = pt(ri + 1, si + 1); val d = pt(ri, si + 1)
            v.addAll(listOf(a[0], a[1], a[2], b[0], b[1], b[2], c[0], c[1], c[2]))
            v.addAll(listOf(a[0], a[1], a[2], c[0], c[1], c[2], d[0], d[1], d[2]))
        }
        waterVerts = v.size / 3
        waterBuf = ByteBuffer.allocateDirect(v.size * 4).order(ByteOrder.nativeOrder())
            .asFloatBuffer().put(v.toFloatArray()).apply { position(0) }
    }

    private fun waterAt(x: Float, z: Float): WaterBody? =
        waterBodies.firstOrNull {
            val dx = x - it.x; val dz = z - it.z
            dx * dx + dz * dz < it.r * it.r * 0.72f
        }

    override fun onSurfaceChanged(gl: GL10?, w: Int, h: Int) {
        viewW = w; viewH = h
        glViewport(0, 0, w, h)
        Matrix.perspectiveM(proj, 0, 58f, w.toFloat() / h, 0.3f, 60f)
    }

    override fun onDrawFrame(gl: GL10?) {
        val now = System.nanoTime()
        val dt = if (lastNs == 0L) 0.016f else ((now - lastNs) / 1e9f).coerceIn(0.001f, 0.05f)
        lastNs = now; t += dt

        val day = dayLerp()
        if (abs(day - bakedDay) > 0.15f) rebuild = true   // the earth relights slowly
        if (rebuild) { rebuild = false; bakedDay = day; rebuildScene() }
        step(dt)
        flashT = (flashT - dt).coerceAtLeast(0f)
        if (weatherCode >= 95 && Random.nextFloat() < dt * 0.12f) flashT = 0.18f

        // Fast-travel glide, then free walking.
        if (!glideX.isNaN()) {
            camPX += (glideX - camPX) * dt * 2.5f
            if (abs(glideX - camPX) < 0.4f) glideX = Float.NaN
        }
        val yawR = Math.toRadians(yawDeg.toDouble()).toFloat()
        val pitchR = Math.toRadians(pitchDeg.toDouble()).toFloat()
        val fwdX = -sin(yawR); val fwdZ = -cos(yawR)
        val rightX = cos(yawR); val rightZ = -sin(yawR)
        val speed = 3.1f
        camPX = (camPX + (fwdX * moveY + rightX * moveX) * speed * dt)
            .coerceIn(0.4f, Habitats.WORLD_W - 0.4f)
        camPZ = (camPZ + (fwdZ * moveY + rightZ * moveX) * speed * dt)
            .coerceIn(-4.4f, 3.0f)
        // The walker doesn't ghost through trees either.
        if (glideX.isNaN()) {
            val cf = resolve(camPX, camPZ, 0.32f)
            camPX = cf[0]; camPZ = cf[1].coerceIn(-4.4f, 3.0f)
        }
        val lookX = camPX + fwdX * cos(pitchR)
        val lookY = camPY + sin(pitchR)
        val lookZ = camPZ + fwdZ * cos(pitchR)
        // Record the human's motion and gaze for the creatures to read.
        playerVX = (camPX - prevCamX) / dt.coerceAtLeast(0.001f)
        playerVZ = (camPZ - prevCamZ) / dt.coerceAtLeast(0.001f)
        playerSpeed = kotlin.math.sqrt(playerVX * playerVX + playerVZ * playerVZ)
        prevCamX = camPX; prevCamZ = camPZ
        fwdXv = fwdX; fwdZv = fwdZ
        Matrix.setLookAtM(view, 0, camPX, camPY, camPZ, lookX, lookY, lookZ, 0f, 1f, 0f)
        Matrix.multiplyMM(vp, 0, proj, 0, view, 0)
        lastVp = vp.clone()

        glClear(GL_COLOR_BUFFER_BIT or GL_DEPTH_BUFFER_BIT)

        // Sky: biome blend by position, then the real hour and weather over it.
        var skyTop = dayMix(Habitats.blendColor(camPX) { it.skyTop }, day)
        var skyBot = dayMix(Habitats.blendColor(camPX) { it.skyBot }, day)
        val fog = dayMix(Habitats.blendColor(camPX) { it.fog }, day)
        if (flashT > 0f) {   // lightning washes the whole sky white
            skyTop = android.graphics.Color.rgb(230, 235, 255)
            skyBot = android.graphics.Color.rgb(200, 215, 245)
        }
        glDisable(GL_DEPTH_TEST)
        glUseProgram(skyProg)
        glUniform3f(glGetUniformLocation(skyProg, "uTop"), red(skyTop), green(skyTop), blue(skyTop))
        glUniform3f(glGetUniformLocation(skyProg, "uBot"), red(skyBot), green(skyBot), blue(skyBot))
        val aSky = glGetAttribLocation(skyProg, "aPos")
        glEnableVertexAttribArray(aSky)
        skyBuf.position(0)
        glVertexAttribPointer(aSky, 2, GL_FLOAT, false, 0, skyBuf)
        glDrawArrays(GL_TRIANGLE_STRIP, 0, 4)
        glDisableVertexAttribArray(aSky)
        glEnable(GL_DEPTH_TEST)

        glUseProgram(mainProg)
        val uVP = glGetUniformLocation(mainProg, "uVP")
        val uM = glGetUniformLocation(mainProg, "uM")
        glUniformMatrix4fv(uVP, 1, false, vp, 0)
        glUniform3f(glGetUniformLocation(mainProg, "uCam"), camPX, camPY, camPZ)
        glUniform3f(glGetUniformLocation(mainProg, "uLight"), 0.35f, 0.8f, 0.5f)
        glUniform3f(glGetUniformLocation(mainProg, "uFog"), red(fog), green(fog), blue(fog))
        glUniform3f(glGetUniformLocation(mainProg, "uRim"), 0.55f, 0.85f, 1f)
        glUniform1f(glGetUniformLocation(mainProg, "uDay"),
            if (flashT > 0f) 1f else day)
        glUniform1f(glGetUniformLocation(mainProg, "uFogNear"), if (foggy) 3f else 9f)
        val aPos = glGetAttribLocation(mainProg, "aPos")
        val aNrm = glGetAttribLocation(mainProg, "aNrm")
        val aCol = glGetAttribLocation(mainProg, "aCol")
        glEnableVertexAttribArray(aPos); glEnableVertexAttribArray(aNrm); glEnableVertexAttribArray(aCol)

        glUniform1f(glGetUniformLocation(mainProg, "uAlpha"), 1f)
        Matrix.setIdentityM(model, 0)
        glUniformMatrix4fv(uM, 1, false, model, 0)
        sceneMesh?.draw(aPos, aNrm, aCol)
        for (mesh in itemMeshes) mesh.draw(aPos, aNrm, aCol)

        // The fires burn: flames flicker and sway with the actual wind.
        val windLean = (windKmh / 10f).coerceAtMost(3f)
        for ((i, f) in flamePts.withIndex()) {
            Matrix.setIdentityM(model, 0)
            Matrix.translateM(model, 0, f[0], f[1], f[2])
            Matrix.rotateM(model, 0, sin(t * 5.5f + i * 1.7f) * 7f + windLean * 4f, 0f, 0f, 1f)
            Matrix.scaleM(model, 0, 1f, 0.82f + 0.3f * sin(t * 7f + i * 2.3f) + 0.1f * sin(t * 13f + i), 1f)
            glUniformMatrix4fv(uM, 1, false, model, 0)
            flameMesh?.draw(aPos, aNrm, aCol)
        }
        for ((i, l) in lanternPts.withIndex()) {
            Matrix.setIdentityM(model, 0)
            Matrix.translateM(model, 0, l[0], l[1], l[2])
            val pulse = 1f + 0.1f * sin(t * 3f + i * 2f) + 0.04f * sin(t * 11f + i)
            Matrix.scaleM(model, 0, pulse, pulse, pulse)
            glUniformMatrix4fv(uM, 1, false, model, 0)
            glowMesh?.draw(aPos, aNrm, aCol)
        }

        synchronized(creatures) {
            // Nests first, so their residents sit on top.
            for (c in creatures) {
                if (c.releasing) continue
                Matrix.setIdentityM(model, 0)
                Matrix.translateM(model, 0, c.nestX, 0f, c.nestZ)
                glUniformMatrix4fv(uM, 1, false, model, 0)
                nestMesh?.draw(aPos, aNrm, aCol)
            }
            for ((i, c) in creatures.withIndex()) {
                val sp = Species.ALL[c.species]
                if (c.under) {
                    Matrix.setIdentityM(model, 0)
                    Matrix.translateM(model, 0, c.x, 0f, c.z)
                    Matrix.scaleM(model, 0, 0.62f, 0.62f + sin(c.phase * 6f) * 0.06f, 0.62f)
                    glUniformMatrix4fv(uM, 1, false, model, 0)
                    forms.getOrPut(-2) { CreatureForms.mound() }.draw(aPos, aNrm, aCol)
                    continue
                }
                val hop = when {
                    c.sleeping -> 0f
                    sp.motion == Species.HOP -> abs(sin(c.phase * 4f)) * 0.22f
                    sp.motion == Species.FLOAT -> 0.25f + sin(c.phase * 1.6f) * 0.1f
                    sp.motion == Species.DRIFT -> 0.12f + sin(c.phase * 2.2f) * 0.07f
                    sp.motion == Species.SKIM -> 0.5f + sin(c.phase * 2.6f) * 0.18f
                    sp.motion == Species.SWIM -> -0.14f + sin(c.phase * 2f) * 0.05f
                    else -> abs(sin(c.phase * 5f)) * 0.035f
                }
                val squash = when {
                    c.sleeping -> 0.82f + sin(t * 1.2f + c.phase) * 0.03f   // slow breathing
                    else -> 1f + sin(c.phase * 5f) * 0.04f +
                        (if (c.happyT > 0f) sin(c.happyT * 14f) * 0.08f else 0f)
                }
                shadowDraw(c, aPos, aNrm, aCol, uM)
                Matrix.setIdentityM(model, 0)
                Matrix.translateM(model, 0, c.x, hop, c.z)
                Matrix.rotateM(model, 0, c.headingDeg, 0f, 1f, 0f)
                if (c.bank != 0f) Matrix.rotateM(model, 0, c.bank, 0f, 0f, 1f)
                val sz = 0.62f * c.scale
                Matrix.scaleM(model, 0, sz, sz * squash, sz)
                glUniformMatrix4fv(uM, 1, false, model, 0)
                forms.getOrPut(c.species) { CreatureForms.build(c.species) }.draw(aPos, aNrm, aCol)
                if (i == selected) {
                    Matrix.setIdentityM(model, 0)
                    Matrix.translateM(model, 0, c.x, hop + 1.05f + sin(t * 3f) * 0.05f, c.z)
                    Matrix.rotateM(model, 0, t * 90f, 0f, 1f, 0f)
                    Matrix.scaleM(model, 0, 0.16f, 0.16f, 0.16f)
                    glUniformMatrix4fv(uM, 1, false, model, 0)
                    forms.getOrPut(-1) {
                        val b = MeshBuilder()
                        b.cone(0f, 0f, 0f, 0.8f, 0f, 1f, 0f, 1f, android.graphics.Color.WHITE, 0.9f, 4)
                        b.cone(0f, 0f, 0f, 0.8f, 0f, -1f, 0f, 1f, android.graphics.Color.WHITE, 0.9f, 4)
                        b.bake()
                    }.draw(aPos, aNrm, aCol)
                }
            }
        }
        // Ripple rings expand and fade on the ponds.
        if (ripples.isNotEmpty()) {
            glEnable(GL_BLEND); glBlendFunc(GL_SRC_ALPHA, GL_ONE)
            glDepthMask(false)
            val uAlpha = glGetUniformLocation(mainProg, "uAlpha")
            for (rp in ripples) {
                val s = 0.22f + rp.age * 1.3f
                glUniform1f(uAlpha, (1f - rp.age / 0.9f).coerceIn(0f, 1f) * 0.75f)
                Matrix.setIdentityM(model, 0)
                Matrix.translateM(model, 0, rp.x, 0.06f, rp.z)
                Matrix.scaleM(model, 0, s, 1f, s * 0.8f)
                glUniformMatrix4fv(uM, 1, false, model, 0)
                ringMesh?.draw(aPos, aNrm, aCol)
            }
            glUniform1f(uAlpha, 1f)
            glDepthMask(true)
            glDisable(GL_BLEND)
        }
        glDisableVertexAttribArray(aPos); glDisableVertexAttribArray(aNrm); glDisableVertexAttribArray(aCol)

        // The living water: waved surfaces over every pond and spring.
        waterBuf?.let { wb ->
            glUseProgram(waterProg)
            glUniformMatrix4fv(glGetUniformLocation(waterProg, "uVP"), 1, false, vp, 0)
            glUniform1f(glGetUniformLocation(waterProg, "uT"), t)
            glEnable(GL_BLEND); glBlendFunc(GL_SRC_ALPHA, GL_ONE)
            glDepthMask(false)
            val aW = glGetAttribLocation(waterProg, "aPos")
            glEnableVertexAttribArray(aW)
            wb.position(0)
            glVertexAttribPointer(aW, 3, GL_FLOAT, false, 12, wb)
            val uMW = glGetUniformLocation(waterProg, "uM")
            val uCol = glGetUniformLocation(waterProg, "uCol")
            for (w in waterBodies) {
                Matrix.setIdentityM(model, 0)
                Matrix.translateM(model, 0, w.x, 0.07f, w.z)
                Matrix.scaleM(model, 0, w.r * 0.95f, 1f, w.r * 0.72f)
                glUniformMatrix4fv(uMW, 1, false, model, 0)
                if (w.warm) glUniform3f(uCol, 0.85f, 0.65f, 0.45f)   // the hot spring steams gold
                else glUniform3f(uCol, 0.35f, 0.8f, 0.95f)
                glDrawArrays(GL_TRIANGLES, 0, waterVerts)
            }
            glDisableVertexAttribArray(aW)
            glDepthMask(true)
            glDisable(GL_BLEND)
        }

        drawParticles()
    }

    private fun shadowDraw(c: DenC, aPos: Int, aNrm: Int, aCol: Int, uM: Int) {
        Matrix.setIdentityM(model, 0)
        Matrix.translateM(model, 0, c.x, 0f, c.z)
        Matrix.rotateM(model, 0, c.headingDeg, 0f, 1f, 0f)   // oblong shadows follow the body
        Matrix.scaleM(model, 0, c.scale, 1f, c.scale)
        glUniformMatrix4fv(uM, 1, false, model, 0)
        shadowMesh?.draw(aPos, aNrm, aCol)
    }

    // ------------------------------------------------------------- scene

    private fun buildNest(): Mesh {
        val b = MeshBuilder()
        for (k in 0 until 8) {
            val a = k * 0.785f
            b.ellipsoid(cos(a) * 0.34f, 0.045f, sin(a) * 0.27f,
                0.09f, 0.045f, 0.05f, android.graphics.Color.rgb(96, 74, 52), 0f, 3, 5)
        }
        b.ellipsoid(0f, 0.015f, 0f, 0.3f, 0.015f, 0.24f, android.graphics.Color.rgb(52, 40, 30), 0f, 3, 8)
        return b.bake()
    }

    private fun rebuildScene() {
        val b = MeshBuilder()
        val w = Habitats.WORLD_W
        // Ground as strips so the earth itself changes with the biome.
        val strips = 32
        val day = bakedDay.coerceAtLeast(0f)
        for (s in 0 until strips) {
            val x0 = -9f + (w + 18f) * s / strips
            val x1 = -9f + (w + 18f) * (s + 1) / strips
            val col = dayMix(Habitats.blendColor((x0 + x1) / 2f) { it.ground }, day * 0.7f)
            b.quad(x0, 0f, 9f, x1, 0f, 9f, x1, 0f, -9f, x0, 0f, -9f, col)
        }
        for (zone in Habitats.ZONES) {
            Habitats.zoneDecor(b, zone, Habitats.biomeAt(zone.x))
        }
        for (biome in Habitats.BIOMES) biome.decor(b)
        sceneMesh = b.bake()
        // Gather the elemental furniture: fires, lamps, waters, crystals —
        // and the collision map, so wood and fire are finally solid.
        flamePts.clear(); lanternPts.clear(); sparklePts.clear(); waterBodies.clear()
        obstacles.clear()
        for (zone in Habitats.ZONES) when (zone.kind) {
            "WATER" -> waterBodies += WaterBody(zone.x, zone.z, zone.r,
                warm = zone.x in 28f..41f)
            "EMBER" -> for (k in 0..3) {
                val a = k * 1.57f + 0.4f
                val px = zone.x + cos(a) * zone.r * 0.5f
                val pz = zone.z + sin(a) * zone.r * 0.4f
                flamePts += floatArrayOf(px, 0.16f, pz)
                obstacles += floatArrayOf(px, pz, 0.3f)          // fire is not a path
            }
            "THICKET" -> {
                obstacles += floatArrayOf(zone.x - zone.r * 0.5f, zone.z - 0.2f, 0.45f)
                obstacles += floatArrayOf(zone.x + zone.r * 0.45f, zone.z + 0.15f, 0.4f)
                obstacles += floatArrayOf(zone.x, zone.z + zone.r * 0.4f, 0.5f)
                obstacles += floatArrayOf(zone.x - zone.r * 0.15f, zone.z - zone.r * 0.45f, 0.42f)
            }
            "STONE" -> {
                obstacles += floatArrayOf(zone.x, zone.z, zone.r * 0.6f)
                obstacles += floatArrayOf(zone.x + zone.r * 0.7f, zone.z - 0.2f, 0.4f)
            }
            "PERCH" -> obstacles += floatArrayOf(zone.x, zone.z, 0.28f)
            "VOID" -> for (k in 0..4) {
                val a = k * 1.257f
                obstacles += floatArrayOf(zone.x + cos(a) * zone.r * 0.85f,
                    zone.z + sin(a) * zone.r * 0.65f, 0.16f)
            }
        }
        // Meadow pines, cove arches, hollow lantern row, shrine crystals.
        for (i in 0..6) obstacles += floatArrayOf(1f + i * 1.9f + (i % 3) * 0.3f,
            -2.9f - (i % 2) * 0.9f, 0.5f)
        for (i in 0..2) {
            val x = 16.5f + i * 4.4f
            obstacles += floatArrayOf(x - 0.5f, -3.4f, 0.3f)
            obstacles += floatArrayOf(x + 0.5f, -3.4f, 0.3f)
        }
        for (i in 0..4) {
            lanternPts += floatArrayOf(29f + i * 2.8f, 1.2f, -2.6f)
            obstacles += floatArrayOf(29f + i * 2.8f, -2.6f, 0.2f)
        }
        for (i in 0..5) {
            sparklePts += floatArrayOf(42f + i * 2.2f,
                0.7f + (i % 3) * 0.3f, -2.8f - (i % 2) * 0.8f)
            obstacles += floatArrayOf(42f + i * 2.2f, -2.8f - (i % 2) * 0.8f, 0.42f)
        }
        for ((i, id) in placedIds.withIndex()) {
            val sx = Habitats.slotX(i); val sz = Habitats.SLOT_Z
            when (id) {
                "lantern" -> {
                    lanternPts += floatArrayOf(sx, 1.06f, sz)
                    obstacles += floatArrayOf(sx, sz, 0.22f)
                }
                "pool" -> waterBodies += WaterBody(sx, sz, 0.6f, warm = false)
                "gems" -> {
                    sparklePts += floatArrayOf(sx, 0.35f, sz)
                    obstacles += floatArrayOf(sx, sz, 0.35f)
                }
                "bush" -> obstacles += floatArrayOf(sx, sz, 0.5f)
                "nook" -> obstacles += floatArrayOf(sx, sz, 0.5f)
                "drum" -> obstacles += floatArrayOf(sx, sz, 0.4f)
                "plinth" -> obstacles += floatArrayOf(sx, sz, 0.3f)
            }
        }
        itemMeshes = placedIds.mapIndexedNotNull { i, id ->
            Habitats.item(id)?.let { item ->
                val ib = MeshBuilder()
                item.build(ib, Habitats.slotX(i), Habitats.SLOT_Z)
                ib.bake()
            }
        }
        synchronized(creatures) {
            val want = population
            if (creatures.map { it.species } != want) {
                creatures.clear()
                for ((idx, s) in want.withIndex()) creatures += DenC(s).apply {
                    val sp = Species.ALL[s]
                    // Home is a nest at a microhabitat of your niche; siblings
                    // spread across the world's matching zones.
                    val homes = Habitats.ZONES.filter { it.kind == sp.zone }
                    if (homes.isNotEmpty()) {
                        val zn = homes[idx % homes.size]
                        val a = idx * 2.4f
                        nestX = zn.x + cos(a) * zn.r * 0.45f
                        nestZ = (zn.z + sin(a) * zn.r * 0.35f).coerceIn(-2.4f, 1.6f)
                    } else {
                        nestX = 2f + Random.nextFloat() * (w - 4f); nestZ = 0f
                    }
                    // Nobody nests inside a tree; nudge the nest into the clear.
                    val nf = resolve(nestX, nestZ, 0.35f)
                    nestX = nf[0]; nestZ = nf[1].coerceIn(-2.4f, 1.6f)
                    x = nestX + Random.nextFloat() * 2f - 1f
                    z = (nestZ + Random.nextFloat() - 0.5f).coerceIn(-2.4f, 1.6f)
                    val sf = resolve(x, z, 0.25f)
                    x = sf[0]; z = sf[1].coerceIn(-2.4f, 1.6f)
                }
            }
        }
    }

    // --------------------------------------------------------------- sim

    private fun step(dt: Float) {
        val w = Habitats.WORLD_W
        var removed = false
        synchronized(creatures) {
            for (c in creatures) {
                val sp = Species.ALL[c.species]
                val biome = Habitats.biomeAt(c.x)
                val perky = if (sp.temperament in biome.loved) 1.25f else 1f
                c.phase += dt * (if (c.sleeping) 0.2f else (0.8f + sp.energy * 1.6f) * perky)
                c.happyT = (c.happyT - dt).coerceAtLeast(0f)
                c.meetCd = (c.meetCd - dt).coerceAtLeast(0f)
                if (c.happyT > 0.4f && Random.nextFloat() < dt * 6f)
                    heart(c.x, 0.9f, c.z, big = c.happyT > 2f)

                // Freedom run: straight off the edge of the world.
                if (c.releasing) {
                    val dx = c.targetX - c.x
                    c.vx = (if (dx > 0) 1f else -1f) * 4.5f
                    c.vz = 0f
                    c.x += c.vx * dt
                    turnToward(c, sp, dt)
                    if (Random.nextFloat() < dt * 8f) heart(c.x, 0.8f, c.z, false)
                    if (abs(dx) < 1f) { removed = true }
                    continue
                }

                // The clock: wander while awake, then home to the nest.
                if (c.sleeping) {
                    c.awakeT -= dt   // reused as remaining sleep
                    c.vx = 0f; c.vz = 0f
                    if (Random.nextFloat() < dt * 1.4f)
                        zzz(c.x, 0.8f + Random.nextFloat() * 0.3f, c.z)
                    if (c.awakeT <= 0f) {
                        c.sleeping = false
                        c.awakeT = (30f + Random.nextFloat() * 50f) * (0.6f + sp.energy)
                        audioEvents.add(intArrayOf(EV_WAKE, c.species))
                    }
                    continue
                }
                // Sleep pressure follows the real clock: diurnal creatures tire
                // at night, the nocturnal tire in daylight.
                val offHours = if (c.species in nocturnal) dayLerp() else 1f - dayLerp()
                c.awakeT -= dt * (1f + offHours * 1.2f)
                if (c.awakeT <= 0f && !c.homing) {
                    c.homing = true; c.targetX = c.nestX; c.targetZ = c.nestZ; c.targetT = 14f
                }
                if (c.homing && abs(c.x - c.nestX) < 0.4f && abs(c.z - c.nestZ) < 0.4f) {
                    c.homing = false
                    c.sleeping = true
                    // Sleep length by temperament: the dozy sleep long.
                    c.awakeT = (10f + Random.nextFloat() * 10f) * (1.6f - sp.energy)
                    c.targetX = Float.NaN
                    continue
                }

                if (sp.motion == Species.BURROW) {
                    c.underT -= dt
                    if (c.underT <= 0f) {
                        c.under = !c.under
                        c.underT = if (c.under) 2.5f + Random.nextFloat() * 3f else 4f + Random.nextFloat() * 4f
                        if (!c.under) c.happyT = maxOf(c.happyT, 0.8f)
                    }
                }

                // ---- Perception of the human: the flight-initiation model ----
                c.startleCd = (c.startleCd - dt).coerceAtLeast(0f)
                c.solicitCd = (c.solicitCd - dt).coerceAtLeast(0f)
                c.alert = false
                if (!c.under) {
                    val eth = EthoModel.of(c.species)
                    val dxp = c.x - camPX; val dzp = c.z - camPZ
                    val distP = kotlin.math.sqrt(dxp * dxp + dzp * dzp).coerceAtLeast(0.001f)
                    val toX = dxp / distP; val toZ = dzp / distP
                    val approach = playerVX * toX + playerVZ * toZ           // >0 = human closing in
                    val gaze = (fwdXv * toX + fwdZv * toZ).coerceIn(0f, 1f)   // 1 = looked straight at
                    val hab = fieldHabit.getOrElse(c.species) { 0f }
                    val fam = fieldFamiliar.getOrElse(c.species) { 0f }
                    val food = fieldFood.getOrElse(c.species) { 0f }
                    // Social buffering: bolder near conspecifics (risk dilution).
                    var kin = 0
                    for (o in creatures) if (o !== c && o.species == c.species && !o.under && !o.releasing) {
                        val d2 = (o.x - c.x) * (o.x - c.x) + (o.z - c.z) * (o.z - c.z)
                        if (d2 < 16f) kin++
                    }
                    val packBuf = eth.packBuffer * kotlin.math.min(kin, 3)
                    val homeCover = Habitats.ZONES.any {
                        it.kind == sp.zone && abs(it.x - c.x) + abs(it.z - c.z) < it.r + 0.6f
                    }
                    var effFID = eth.baseFID
                    effFID *= (1f + eth.gazeSensitivity * gaze)               // gaze lengthens flight distance
                    effFID *= (1f + (playerSpeed * eth.speedSensitivity).coerceAtMost(1.5f)) // fast approach too
                    effFID *= (1f + eth.neophobia * (1f - fam))               // strangers scarier
                    effFID -= hab * eth.habituationRate * eth.baseFID         // learned tolerance shrinks it
                    effFID -= packBuf * 0.35f
                    if (homeCover) effFID *= (1f - eth.refugeDependence * 0.3f)
                    effFID = effFID.coerceAtLeast(eth.boldnessFloor)
                    val alertD = effFID * eth.alertRatio

                    if (c.fleeing) {
                        if (distP > alertD * 1.3f) {
                            c.fleeing = false; c.targetX = Float.NaN
                            if (c.homing) { c.targetX = c.nestX; c.targetZ = c.nestZ; c.targetT = 14f }
                        } else if (c.targetX.isNaN()) {   // keep running for cover
                            c.targetX = (c.x + toX * 3.5f).coerceIn(0.8f, w - 0.8f)
                            c.targetZ = (c.z + toZ * 2f).coerceIn(-2.4f, 1.6f); c.targetT = 6f
                        }
                    } else if (distP < effFID && approach > 0.03f && c.startleCd <= 0f) {
                        // FLUSH — the human crossed the flight distance while closing.
                        c.fleeing = true; c.startleCd = 5f + Random.nextFloat() * 3f
                        c.chaseT = 0f; c.chaseIdx = -1; c.soliciting = false
                        val refuge = Habitats.ZONES.filter { it.kind == sp.zone }
                            .minByOrNull { (it.x - c.x) * (it.x - c.x) + (it.z - c.z) * (it.z - c.z) }
                        val safe = refuge != null &&
                            (refuge.x - c.x) * toX + (refuge.z - c.z) * toZ > 0f   // cover lies away from the human
                        if (safe && refuge != null) { c.targetX = refuge.x; c.targetZ = refuge.z }
                        else { c.targetX = (c.x + toX * 4f).coerceIn(0.8f, w - 0.8f); c.targetZ = (c.z + toZ * 2.5f).coerceIn(-2.4f, 1.6f) }
                        c.targetT = 6f
                        behaviorEvents.add(intArrayOf(BEV_FLEE, c.species, 0))
                        audioEvents.add(intArrayOf(6, c.species))
                        // Contagious flight (allelomimetic): kin scatter too.
                        for (o in creatures) if (o !== c && !o.under && !o.releasing && !o.fleeing && !o.sleeping) {
                            val d2 = (o.x - c.x) * (o.x - c.x) + (o.z - c.z) * (o.z - c.z)
                            if (d2 < 6.25f && o.startleCd <= 0f) {
                                o.fleeing = true; o.startleCd = 5f; o.chaseT = 0f
                                val odx = o.x - camPX; val odz = o.z - camPZ
                                val od = kotlin.math.sqrt(odx * odx + odz * odz).coerceAtLeast(0.001f)
                                o.targetX = (o.x + odx / od * 3.5f).coerceIn(0.8f, w - 0.8f)
                                o.targetZ = (o.z + odz / od * 2f).coerceIn(-2.4f, 1.6f); o.targetT = 6f
                            }
                        }
                    } else if (distP < alertD && approach > -0.02f) {
                        // ALERT — orient to and monitor the human; hold position.
                        c.alert = true
                        c.pauseT = maxOf(c.pauseT, 0.25f)
                        val desired = Math.toDegrees(
                            kotlin.math.atan2((-dxp).toDouble(), (-dzp).toDouble())).toFloat()
                        val d = ((desired - c.headingDeg + 540f) % 360f) - 180f
                        c.headingDeg = (c.headingDeg + d.coerceIn(-160f * dt, 160f * dt) + 360f) % 360f
                        // Food conditioning: a calm, non-staring, familiar human is
                        // approached and solicited by a food-associated creature.
                        if (food > 0.45f && playerSpeed < 0.6f && gaze < 0.4f &&
                            c.solicitCd <= 0f && distP < alertD * 0.95f) {
                            c.soliciting = true; c.alert = false; c.pauseT = 0f
                        }
                    }
                    if (c.soliciting) {
                        if (playerSpeed > 1.2f || gaze > 0.75f) {   // the human moved or stared — abort
                            c.soliciting = false; c.targetX = Float.NaN; c.solicitCd = 4f
                        } else if (distP < 1.15f) {
                            c.soliciting = false; c.targetX = Float.NaN; c.solicitCd = 8f
                            c.happyT = maxOf(c.happyT, 1.4f)
                            behaviorEvents.add(intArrayOf(BEV_SOLICIT, c.species, 0))
                        } else {
                            c.targetX = camPX.coerceIn(0.8f, w - 0.8f)
                            c.targetZ = camPZ.coerceIn(-2.4f, 1.6f); c.targetT = 5f
                        }
                    }
                    // The closest a member of this species tolerates, recorded.
                    if (!c.fleeing && distP < 2f && closeCd <= 0f) {
                        behaviorEvents.add(intArrayOf(BEV_CLOSE, c.species, (distP * 100f).toInt()))
                        closeCd = 0.5f
                    }
                }
                closeCd = (closeCd - dt).coerceAtLeast(0f)

                // Playful chase: the zoomiest invite a friend to a race.
                if (c.chaseT > 0f) {
                    c.chaseT -= dt
                    val o = creatures.getOrNull(c.chaseIdx)
                    if (o == null || o.releasing || o.sleeping || c.chaseT <= 0f) {
                        c.chaseIdx = -1; c.chaseT = 0f
                    } else {
                        val dx = o.x - c.x; val dz = o.z - c.z
                        val d = kotlin.math.sqrt(dx * dx + dz * dz).coerceAtLeast(0.001f)
                        c.vx = dx / d * (0.9f + sp.energy); c.vz = dz / d * (0.9f + sp.energy)
                        if (d < 0.9f) {
                            c.chaseIdx = -1; c.chaseT = 0f
                            c.happyT = 2f; o.happyT = 2f
                            heart(c.x, 1f, c.z, true); heart(o.x, 1f, o.z, false)
                            audioEvents.add(intArrayOf(EV_CHASE_END, c.species))
                        }
                    }
                } else if (sp.energy > 0.65f && sp.social > 0.4f && !c.homing &&
                    !c.fleeing && !c.soliciting && !c.alert &&
                    Random.nextFloat() < dt * 0.02f && creatures.size > 1) {
                    var pick = Random.nextInt(creatures.size)
                    if (creatures[pick] === c) pick = (pick + 1) % creatures.size
                    if (!creatures[pick].sleeping && !creatures[pick].releasing) {
                        c.chaseIdx = pick; c.chaseT = 5f
                    }
                }

                if (c.lingerT > 0f) {
                    c.lingerT -= dt
                    c.vx *= 0.8f; c.vz *= 0.8f
                    if (Random.nextFloat() < dt * 1.2f) heart(c.x, 0.85f, c.z, false)
                } else if (c.pauseT > 0f && c.chaseT <= 0f) {
                    c.pauseT -= dt
                    c.vx *= 0.85f; c.vz *= 0.85f
                } else if (!c.targetX.isNaN()) {
                    // Patience runs out at blocked doorways: shrug, settle here.
                    c.targetT -= dt
                    if (c.targetT <= 0f) {
                        c.targetX = Float.NaN
                        if (c.homing) { c.homing = false; c.sleeping = true
                            c.awakeT = (10f + Random.nextFloat() * 10f) * (1.6f - sp.energy) }
                        else c.lingerT = 2f
                    }
                    val dx = c.targetX - c.x; val dz = c.targetZ - c.z
                    val d = kotlin.math.sqrt(dx * dx + dz * dz)
                    if (c.targetX.isNaN()) { /* patience spent this frame */ }
                    else if (d < 0.45f && !c.homing) {
                        c.targetX = Float.NaN; c.lingerT = 3f + Random.nextFloat() * 3f
                    } else if (d >= 0.4f) {
                        val v = (0.5f + sp.energy * 0.7f) * (if (c.under) 2f else 1f) *
                            (if (sp.motion == Species.SKIM) 1.8f else 1f) *
                            (if (c.homing) 1.4f else 1f) * (if (c.fleeing) 2.4f else 1f)
                        c.vx = dx / d * v; c.vz = dz / d * v
                    }
                } else if (c.chaseT <= 0f) {
                    val speed = (0.25f + sp.energy * 0.85f) * perky
                    when (sp.motion) {
                        Species.DART -> if (Random.nextFloat() < dt * (0.5f + sp.energy)) {
                            val a = Random.nextFloat() * 6.283f
                            c.vx = cos(a) * speed * 2.2f; c.vz = sin(a) * speed * 1.2f
                            c.pauseT = 0.5f + Random.nextFloat() * 0.8f
                        }
                        Species.HOP -> if (Random.nextFloat() < dt * 1.6f) {
                            val a = Random.nextFloat() * 6.283f
                            c.vx = cos(a) * speed * 1.6f; c.vz = sin(a) * speed * 0.9f
                            c.pauseT = 0.4f
                        }
                        Species.SKIM -> {
                            c.vx += (cos(c.phase * 0.9f) * speed * 2.4f - c.vx) * dt * 2f
                            c.vz += (sin(c.phase * 1.3f) * speed * 1.1f - c.vz) * dt * 2f
                        }
                        Species.SWIM -> {
                            c.vx += (cos(c.phase * 0.7f) * speed * 1.2f - c.vx) * dt * 2f
                            c.vz += (sin(c.phase * 1.4f) * speed * 0.8f - c.vz) * dt * 2f
                        }
                        else -> {
                            c.vx += (cos(c.phase * 0.5f) * speed - c.vx) * dt * 1.5f
                            c.vz += (sin(c.phase * 0.33f) * speed * 0.5f - c.vz) * dt * 1.5f
                            if (Random.nextFloat() < dt * (0.8f - sp.energy * 0.5f))
                                c.pauseT = 0.8f + Random.nextFloat() * 1.6f
                        }
                    }
                    if (Random.nextFloat() < dt * 0.12f) {
                        var found = false
                        for ((i, id) in placedIds.withIndex()) {
                            val item = Habitats.item(id) ?: continue
                            if (sp.temperament in item.loved) {
                                c.targetX = Habitats.slotX(i); c.targetZ = Habitats.SLOT_Z + 0.5f
                                c.targetT = 10f
                                found = true; break
                            }
                        }
                        if (!found) {
                            val homes = Habitats.ZONES.filter { it.kind == sp.zone }
                            if (homes.isNotEmpty()) {
                                // Prefer nearby matching zones; the world is wide.
                                val zn = homes.minByOrNull { abs(it.x - c.x) + Random.nextFloat() * 8f }!!
                                val a = Random.nextFloat() * 6.283f
                                c.targetX = zn.x + cos(a) * zn.r * 0.5f
                                c.targetZ = (zn.z + sin(a) * zn.r * 0.4f).coerceIn(-2.4f, 1.6f)
                                c.targetT = 10f
                            }
                        }
                    }
                }

                // Society — suspended while fleeing or soliciting the human.
                if (!c.under && !c.sleeping && !c.fleeing && !c.soliciting) {
                    var nearest: DenC? = null; var nd = Float.MAX_VALUE
                    for (o in creatures) {
                        if (o === c || o.under || o.sleeping || o.releasing) continue
                        val dx = o.x - c.x; val dz = o.z - c.z
                        val d2 = dx * dx + dz * dz
                        if (d2 < nd) { nd = d2; nearest = o }
                    }
                    nearest?.let { o ->
                        val d = kotlin.math.sqrt(nd).coerceAtLeast(0.001f)
                        var pull = (sp.social - 0.35f) * 0.5f
                        if (o.species == c.species) pull += 0.5f
                        if (d > 0.9f && d < 6f && c.targetX.isNaN() && c.lingerT <= 0f && c.chaseT <= 0f) {
                            c.vx += (o.x - c.x) / d * pull * dt * 2.5f
                            c.vz += (o.z - c.z) / d * pull * dt * 2.5f
                        }
                        if (d < 0.8f && c.meetCd <= 0f && o.meetCd <= 0f) {
                            c.meetCd = 7f; o.meetCd = 7f
                            c.happyT = maxOf(c.happyT, 1.5f)
                            o.happyT = maxOf(o.happyT, 1.5f)
                            heart(c.x, 1f, c.z, big = o.species == c.species)
                            heart(o.x, 1f, o.z, false)
                            audioEvents.add(intArrayOf(EV_MEET, c.species))
                            c.vx -= (o.x - c.x) * 0.3f; c.vz -= (o.z - c.z) * 0.3f
                        }
                    }
                }

                c.x = (c.x + c.vx * dt).coerceIn(0.8f, w - 0.8f)
                c.z = (c.z + c.vz * dt).coerceIn(-2.4f, 1.6f)
                // Solids are solid — unless you're a mole in the underworld.
                if (!c.under) {
                    val fixed = resolve(c.x, c.z, 0.24f * c.scale)
                    if (fixed[0] != c.x || fixed[1] != c.z) {
                        c.x = fixed[0]; c.z = fixed[1]
                        c.vx *= 0.4f; c.vz *= 0.4f   // bumping into things is humbling
                    }
                }
                // Water physics: a splash going in, bow ripples while moving.
                if (!c.under) {
                    val wading = waterAt(c.x, c.z) != null
                    if (wading && !c.inWater) {
                        ripples += Ripple(c.x, c.z)
                        for (s in 0..5) particles += Particle(
                            c.x + Random.nextFloat() * 0.3f - 0.15f, 0.15f, c.z + 0.1f,
                            0.8f + Random.nextFloat() * 0.7f, 0f, 0.5f,
                            0.55f, 0.85f, 0.95f, 16f)
                    }
                    c.inWater = wading
                    if (wading && (c.vx * c.vx + c.vz * c.vz) > 0.02f &&
                        Random.nextFloat() < dt * 1.6f) ripples += Ripple(c.x, c.z)
                }
                if (sp.motion == Species.SWIM) {
                    val pools = Habitats.ZONES.filter { it.kind == "WATER" }
                    val zn = pools.minByOrNull {
                        (it.x - c.x) * (it.x - c.x) + (it.z - c.z) * (it.z - c.z)
                    }
                    if (zn != null) {
                        val dx = c.x - zn.x; val dz = c.z - zn.z
                        val d = kotlin.math.sqrt(dx * dx + dz * dz)
                        val rim = zn.r * 0.7f
                        if (d > rim && c.targetX.isNaN() && !c.homing) {
                            c.x = zn.x + dx / d * rim; c.z = zn.z + dz / d * rim
                            c.vx -= dx / d * 0.6f; c.vz -= dz / d * 0.6f
                        }
                    }
                }
                turnToward(c, sp, dt)
            }
            if (removed) {
                val gone = creatures.filter { it.releasing && abs(it.targetX - it.x) < 1f }
                for (g in gone) audioEvents.add(intArrayOf(EV_RELEASED, g.species))
                creatures.removeAll(gone.toSet())
                if (selected >= creatures.size) selected = -1
            }
        }
        // Fireflies belong to clear nights; rain and daylight send them home.
        val fireflyRate = (1f - dayLerp()) * (if (raining || snowing) 0f else 1f)
        if (particles.count { it.vy in 0f..0.05f } < 40 && Random.nextFloat() < dt * 10f * fireflyRate) {
            val px = camPX - 6f + Random.nextFloat() * 12f
            val col = Habitats.blendColor(px) { it.fireflyColor }
            particles += Particle(
                px, 0.3f + Random.nextFloat() * 1.6f, -2.5f + Random.nextFloat() * 3f,
                0.02f, 0f, 6f + Random.nextFloat() * 6f,
                red(col), green(col), blue(col), 14f + Random.nextFloat() * 16f)
        }
        // The weather itself: rain streaks or drifting snow around the walker.
        if (raining && Random.nextFloat() < dt * 90f) {
            particles += Particle(
                camPX - 5f + Random.nextFloat() * 10f, 3.2f, -3f + Random.nextFloat() * 5f,
                -6.5f - Random.nextFloat() * 2f, 0f, 0.55f,
                0.55f, 0.75f, 0.95f, 9f)
        }
        if (snowing && Random.nextFloat() < dt * 30f) {
            particles += Particle(
                camPX - 5f + Random.nextFloat() * 10f, 3f, -3f + Random.nextFloat() * 5f,
                -0.55f, 0f, 5f,
                0.95f, 0.97f, 1f, 13f)
        }
        // The fires breathe out sparks; springs bubble; crystals glint.
        for (f in flamePts) if (Random.nextFloat() < dt * 2.2f) {
            particles += Particle(
                f[0] + Random.nextFloat() * 0.14f - 0.07f, f[1] + 0.2f, f[2],
                0.45f + Random.nextFloat() * 0.35f, 0f, 1f + Random.nextFloat() * 0.5f,
                1f, 0.55f + Random.nextFloat() * 0.3f, 0.2f, 10f + Random.nextFloat() * 8f)
        }
        for (l in lanternPts) if (Random.nextFloat() < dt * 0.7f) {
            particles += Particle(l[0], l[1] + 0.12f, l[2],
                0.25f, 0f, 1.2f, 1f, 0.8f, 0.45f, 8f)
        }
        for (w2 in waterBodies) if (w2.warm && Random.nextFloat() < dt * 3f) {
            val a = Random.nextFloat() * 6.283f; val rr = Random.nextFloat() * w2.r * 0.6f
            particles += Particle(w2.x + cos(a) * rr, 0.1f, w2.z + sin(a) * rr * 0.75f,
                0.3f + Random.nextFloat() * 0.2f, 0f, 0.7f,
                0.9f, 0.95f, 1f, 9f)
        }
        for (s in sparklePts) if (Random.nextFloat() < dt * 1.1f) {
            particles += Particle(
                s[0] + Random.nextFloat() * 0.3f - 0.15f, s[1] + Random.nextFloat() * 0.4f,
                s[2] + Random.nextFloat() * 0.2f - 0.1f,
                0.06f, 0f, 0.8f, 0.85f, 0.75f, 1f, 11f)
        }
        // The walker makes waves too.
        if (waterAt(camPX, camPZ) != null && (abs(moveX) > 0.1f || abs(moveY) > 0.1f) &&
            Random.nextFloat() < dt * 2.5f) ripples += Ripple(camPX, camPZ)

        for (rp in ripples) rp.age += dt
        ripples.removeAll { it.age > 0.9f }
        if (ripples.size > 24) ripples.subList(0, ripples.size - 24).clear()

        val wind = (windKmh / 30f).coerceIn(0f, 1.5f)
        val splashes = ArrayList<Particle>(4)
        val it = particles.iterator()
        while (it.hasNext()) {
            val p = it.next()
            p.life += dt
            p.y += p.vy * dt * 30f * dt + p.vy * dt
            p.x += sin(p.life * 2f + p.z) * dt * 0.15f +
                (if (p.vy < -0.1f) wind * dt * 1.2f else 0f)   // weather rides the wind
            if (p.life >= p.maxLife || p.y < 0.02f && p.vy < -1f) {
                // Raindrops land somewhere real: a splash, or a ring on water.
                if (p.vy < -1f && p.y < 0.02f) {
                    splashes += Particle(p.x, 0.06f, p.z, 0.5f, 0f, 0.22f,
                        0.6f, 0.8f, 0.95f, 7f)
                    if (waterAt(p.x, p.z) != null && Random.nextFloat() < 0.2f)
                        ripples += Ripple(p.x, p.z)
                }
                it.remove()
            }
        }
        particles.addAll(splashes)
    }

    /**
     * Turn to face where you're going, at a rate set by your biology:
     * darters and skimmers whip around and lean into it, floaters pivot
     * dreamily, walkers turn like the creatures they are — a Thornpup
     * spins on a leaf-tip, a Clayward comes about like a barge.
     */
    private fun turnToward(c: DenC, sp: Species, dt: Float) {
        val speed2 = c.vx * c.vx + c.vz * c.vz
        var delta = 0f
        if (speed2 > 0.006f) {
            val desired = Math.toDegrees(kotlin.math.atan2(c.vx.toDouble(), c.vz.toDouble())).toFloat()
            delta = ((desired - c.headingDeg + 540f) % 360f) - 180f
            val turnRate = when (sp.motion) {
                Species.DART, Species.SKIM -> 220f + sp.energy * 320f
                Species.FLOAT, Species.DRIFT -> 55f + sp.energy * 90f
                Species.SWIM -> 100f + sp.energy * 130f
                else -> 70f + sp.energy * 240f
            }
            c.headingDeg += delta.coerceIn(-turnRate * dt, turnRate * dt)
            c.headingDeg = (c.headingDeg + 360f) % 360f
        }
        // Fliers bank into their turns; everyone else stays level.
        val wantBank = if (sp.motion == Species.SKIM || sp.motion == Species.DART)
            (-delta).coerceIn(-38f, 38f) * 0.55f else 0f
        c.bank += (wantBank - c.bank) * (dt * 5f).coerceAtMost(1f)
    }

    private fun heart(x: Float, y: Float, z: Float, big: Boolean) {
        particles += Particle(
            x + Random.nextFloat() * 0.4f - 0.2f, y, z + 0.2f,
            0.35f + Random.nextFloat() * 0.2f, 0f, if (big) 1.6f else 1.1f,
            1f, 0.45f + Random.nextFloat() * 0.2f, 0.75f,
            if (big) 46f else 30f)
    }

    private fun zzz(x: Float, y: Float, z: Float) {
        particles += Particle(
            x + 0.15f, y, z + 0.1f, 0.22f, 0f, 1.8f,
            0.75f, 0.85f, 1f, 20f)
    }

    private fun drawParticles() {
        if (particles.isEmpty()) return
        val n = particles.size
        val fb = ByteBuffer.allocateDirect(n * 8 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        for (p in particles) {
            val fade = (1f - p.life / p.maxLife).coerceIn(0f, 1f)
            val tw = if (p.vy < 0.05f) (0.4f + 0.6f * abs(sin(p.life * 3f + p.x))) else 1f
            fb.put(p.x); fb.put(p.y); fb.put(p.z)
            fb.put(p.r); fb.put(p.g); fb.put(p.b); fb.put(fade * tw)
            fb.put(p.size)
        }
        fb.position(0)
        glUseProgram(ptProg)
        glUniformMatrix4fv(glGetUniformLocation(ptProg, "uVP"), 1, false, vp, 0)
        glEnable(GL_BLEND); glBlendFunc(GL_SRC_ALPHA, GL_ONE)
        glDepthMask(false)
        val aPos = glGetAttribLocation(ptProg, "aPos")
        val aCol = glGetAttribLocation(ptProg, "aCol")
        val aSize = glGetAttribLocation(ptProg, "aSize")
        glEnableVertexAttribArray(aPos); glEnableVertexAttribArray(aCol); glEnableVertexAttribArray(aSize)
        fb.position(0); glVertexAttribPointer(aPos, 3, GL_FLOAT, false, 32, fb)
        fb.position(3); glVertexAttribPointer(aCol, 4, GL_FLOAT, false, 32, fb)
        fb.position(7); glVertexAttribPointer(aSize, 1, GL_FLOAT, false, 32, fb)
        glDrawArrays(GL_POINTS, 0, n)
        glDisableVertexAttribArray(aPos); glDisableVertexAttribArray(aCol); glDisableVertexAttribArray(aSize)
        glDepthMask(true)
        glDisable(GL_BLEND)
    }

    private fun red(c: Int) = android.graphics.Color.red(c) / 255f
    private fun green(c: Int) = android.graphics.Color.green(c) / 255f
    private fun blue(c: Int) = android.graphics.Color.blue(c) / 255f
}
