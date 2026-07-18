package com.taphunter.phone.den

import android.opengl.GLES20.*
import android.opengl.GLSurfaceView
import android.opengl.Matrix
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
    var face = 1f
    var targetX = Float.NaN
    var targetZ = 0f
    var lingerT = 0f
    var under = false          // burrowers: traveling as a molehill
    var underT = 0f
    var meetCd = 0f
    val scale = 0.88f + Random.nextFloat() * 0.24f
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

class DenRenderer : GLSurfaceView.Renderer {

    companion object {
        const val EV_MEET = 0; const val EV_CHASE_END = 1
        const val EV_RELEASED = 2; const val EV_WAKE = 3
    }

    @Volatile private var placedIds: List<String> = emptyList()
    @Volatile private var population: List<Int> = emptyList()
    @Volatile private var rebuild = true
    @Volatile var selected = -1
    @Volatile private var glideX = Float.NaN

    /** (event, species) pairs for the activity's audio layer. */
    val audioEvents = ConcurrentLinkedQueue<IntArray>()

    val creatures = ArrayList<DenC>()
    private val particles = ArrayList<Particle>()
    private var mainProg = 0; private var skyProg = 0; private var ptProg = 0
    private var sceneMesh: Mesh? = null
    private var itemMeshes = listOf<Mesh>()
    private val forms = HashMap<Int, Mesh>()
    private var shadowMesh: Mesh? = null
    private var nestMesh: Mesh? = null
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
        glEnable(GL_DEPTH_TEST)
        forms.clear()
        shadowMesh = CreatureForms.shadow()
        nestMesh = buildNest()
        rebuild = true
        lastNs = 0L
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

        if (rebuild) { rebuild = false; rebuildScene() }
        step(dt)

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
        val lookX = camPX + fwdX * cos(pitchR)
        val lookY = camPY + sin(pitchR)
        val lookZ = camPZ + fwdZ * cos(pitchR)
        Matrix.setLookAtM(view, 0, camPX, camPY, camPZ, lookX, lookY, lookZ, 0f, 1f, 0f)
        Matrix.multiplyMM(vp, 0, proj, 0, view, 0)
        lastVp = vp.clone()

        glClear(GL_COLOR_BUFFER_BIT or GL_DEPTH_BUFFER_BIT)

        // Sky, blended across the biome seams as you walk.
        val skyTop = Habitats.blendColor(camPX) { it.skyTop }
        val skyBot = Habitats.blendColor(camPX) { it.skyBot }
        val fog = Habitats.blendColor(camPX) { it.fog }
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
        val aPos = glGetAttribLocation(mainProg, "aPos")
        val aNrm = glGetAttribLocation(mainProg, "aNrm")
        val aCol = glGetAttribLocation(mainProg, "aCol")
        glEnableVertexAttribArray(aPos); glEnableVertexAttribArray(aNrm); glEnableVertexAttribArray(aCol)

        Matrix.setIdentityM(model, 0)
        glUniformMatrix4fv(uM, 1, false, model, 0)
        sceneMesh?.draw(aPos, aNrm, aCol)
        for (mesh in itemMeshes) mesh.draw(aPos, aNrm, aCol)

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
                Matrix.setIdentityM(model, 0)
                Matrix.translateM(model, 0, c.x, hop, c.z)
                Matrix.rotateM(model, 0, c.face * -22f, 0f, 1f, 0f)
                val sz = 0.62f * c.scale
                Matrix.scaleM(model, 0, sz, sz * squash, sz)
                glUniformMatrix4fv(uM, 1, false, model, 0)
                shadowDraw(c, aPos, aNrm, aCol, uM)
                Matrix.setIdentityM(model, 0)
                Matrix.translateM(model, 0, c.x, hop, c.z)
                Matrix.rotateM(model, 0, c.face * -22f, 0f, 1f, 0f)
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
        glDisableVertexAttribArray(aPos); glDisableVertexAttribArray(aNrm); glDisableVertexAttribArray(aCol)

        drawParticles()
    }

    private fun shadowDraw(c: DenC, aPos: Int, aNrm: Int, aCol: Int, uM: Int) {
        Matrix.setIdentityM(model, 0)
        Matrix.translateM(model, 0, c.x, 0f, c.z)
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
        for (s in 0 until strips) {
            val x0 = -9f + (w + 18f) * s / strips
            val x1 = -9f + (w + 18f) * (s + 1) / strips
            val col = Habitats.blendColor((x0 + x1) / 2f) { it.ground }
            b.quad(x0, 0f, 9f, x1, 0f, 9f, x1, 0f, -9f, x0, 0f, -9f, col)
        }
        for (zone in Habitats.ZONES) {
            Habitats.zoneDecor(b, zone, Habitats.biomeAt(zone.x))
        }
        for (biome in Habitats.BIOMES) biome.decor(b)
        sceneMesh = b.bake()
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
                    x = nestX + Random.nextFloat() * 2f - 1f
                    z = (nestZ + Random.nextFloat() - 0.5f).coerceIn(-2.4f, 1.6f)
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
                    c.face += ((if (c.vx > 0) 1f else -1f) - c.face) * dt * 8f
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
                c.awakeT -= dt
                if (c.awakeT <= 0f && !c.homing) { c.homing = true; c.targetX = c.nestX; c.targetZ = c.nestZ }
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
                    val dx = c.targetX - c.x; val dz = c.targetZ - c.z
                    val d = kotlin.math.sqrt(dx * dx + dz * dz)
                    if (d < 0.45f && !c.homing) {
                        c.targetX = Float.NaN; c.lingerT = 3f + Random.nextFloat() * 3f
                    } else if (d >= 0.4f) {
                        val v = (0.5f + sp.energy * 0.7f) * (if (c.under) 2f else 1f) *
                            (if (sp.motion == Species.SKIM) 1.8f else 1f) * (if (c.homing) 1.4f else 1f)
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
                            }
                        }
                    }
                }

                // Society.
                if (!c.under && !c.sleeping) {
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
                if (abs(c.vx) > 0.05f) c.face += ((if (c.vx > 0) 1f else -1f) - c.face) * dt * 6f
            }
            if (removed) {
                val gone = creatures.filter { it.releasing && abs(it.targetX - it.x) < 1f }
                for (g in gone) audioEvents.add(intArrayOf(EV_RELEASED, g.species))
                creatures.removeAll(gone.toSet())
                if (selected >= creatures.size) selected = -1
            }
        }
        // Fireflies tinted by where they hover.
        if (particles.count { it.vy < 0.05f } < 40 && Random.nextFloat() < dt * 10f) {
            val px = camPX - 6f + Random.nextFloat() * 12f
            val col = Habitats.blendColor(px) { it.fireflyColor }
            particles += Particle(
                px, 0.3f + Random.nextFloat() * 1.6f, -2.5f + Random.nextFloat() * 3f,
                0.02f, 0f, 6f + Random.nextFloat() * 6f,
                red(col), green(col), blue(col), 14f + Random.nextFloat() * 16f)
        }
        val it = particles.iterator()
        while (it.hasNext()) {
            val p = it.next()
            p.life += dt
            p.y += p.vy * dt * 30f * dt + p.vy * dt
            p.x += sin(p.life * 2f + p.z) * dt * 0.15f
            if (p.life >= p.maxLife) it.remove()
        }
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
