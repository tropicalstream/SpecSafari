package com.taphunter.phone.den

import android.opengl.GLES20.*
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import com.taphunter.shared.Species
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** One resident of the 3D den. */
class DenC(val species: Int) {
    var x = 2f; var z = 0f
    var vx = 0f; var vz = 0f
    var phase = Random.nextFloat() * 6f
    var pauseT = 0f
    var happyT = 0f
    var face = 1f              // +1 facing right of world, smoothed
    var targetX = Float.NaN    // pilgrimage toward a loved item or home zone
    var targetZ = 0f
    var lingerT = 0f
    var under = false          // burrowers: traveling as a molehill
    var underT = 0f
}

private class Particle(var x: Float, var y: Float, var z: Float,
                       var vy: Float, var life: Float, var maxLife: Float,
                       var r: Float, var g: Float, var b: Float, var size: Float)

class DenRenderer : GLSurfaceView.Renderer {

    // ------- state shared with the UI thread (kept simple and volatile)
    @Volatile var habitatIdx = 0
    @Volatile private var placedIds: List<String> = emptyList()
    @Volatile private var population: List<Int> = emptyList()
    @Volatile private var rebuild = true
    @Volatile var selected = -1

    // First-person stroll: joystick walks, drag looks — Minecraft manners.
    @Volatile private var camPX = 2.5f
    @Volatile private var camPZ = 4.2f
    private val camPY = 1.35f                 // eye height; feet on the meadow
    @Volatile var yawDeg = 0f                 // 0 = looking into the scene (-Z)
    @Volatile var pitchDeg = -6f
    @Volatile private var moveX = 0f          // joystick, -1..1 (right+)
    @Volatile private var moveY = 0f          // joystick, -1..1 (forward+)

    /** Joystick vector from the UI thread; camera-relative walk. */
    fun setMove(x: Float, y: Float) { moveX = x.coerceIn(-1f, 1f); moveY = y.coerceIn(-1f, 1f) }

    fun look(dxDeg: Float, dyDeg: Float) {
        yawDeg = (yawDeg + dxDeg) % 360f
        pitchDeg = (pitchDeg + dyDeg).coerceIn(-55f, 40f)
    }

    fun resetCamera() {
        camPX = 2.5f; camPZ = 4.2f; yawDeg = 0f; pitchDeg = -6f; setMove(0f, 0f)
    }

    val creatures = ArrayList<DenC>()
    private val particles = ArrayList<Particle>()
    private var mainProg = 0; private var skyProg = 0; private var ptProg = 0
    private var sceneMesh: Mesh? = null
    private var itemMeshes = listOf<Mesh>()
    private val forms = HashMap<Int, Mesh>()
    private var shadowMesh: Mesh? = null
    private val proj = FloatArray(16); private val view = FloatArray(16)
    private val vp = FloatArray(16); private val model = FloatArray(16); private val tmp = FloatArray(16)
    @Volatile private var lastVp = FloatArray(16)
    private var viewW = 1; private var viewH = 1
    private var lastNs = 0L
    private var t = 0f
    private val skyBuf: FloatBuffer = ByteBuffer.allocateDirect(8 * 4)
        .order(ByteOrder.nativeOrder()).asFloatBuffer()
        .put(floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f)).apply { position(0) }

    fun configure(pop: List<Int>, habitat: Int, items: List<String>) {
        population = pop; habitatIdx = habitat; placedIds = items; rebuild = true
    }

    fun placeItems(items: List<String>) { placedIds = items; rebuild = true }

    fun celebrate(index: Int, big: Boolean) {
        val c = creatures.getOrNull(index) ?: return
        c.happyT = if (big) 3.2f else 2f
        c.pauseT = 1f
        // Petting the molehill summons the mole, delighted.
        if (c.under) { c.under = false; c.underT = 6f }
    }

    /** Nearest creature to a screen tap, or -1. Safe from the UI thread. */
    fun pick(px: Float, py: Float): Int {
        val vpm = lastVp
        var bestI = -1; var bestD = (viewW * 0.13f) * (viewW * 0.13f)
        val inV = FloatArray(4); val outV = FloatArray(4)
        synchronized(creatures) {
            for ((i, c) in creatures.withIndex()) {
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
        val hab = Habitats.ALL[habitatIdx]

        if (rebuild) { rebuild = false; rebuildScene(hab) }
        step(dt, hab)

        // Walk the world: joystick is camera-relative, bounds keep you in the glade.
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

        // Sky.
        glDisable(GL_DEPTH_TEST)
        glUseProgram(skyProg)
        glUniform3f(glGetUniformLocation(skyProg, "uTop"), red(hab.skyTop), green(hab.skyTop), blue(hab.skyTop))
        glUniform3f(glGetUniformLocation(skyProg, "uBot"), red(hab.skyBot), green(hab.skyBot), blue(hab.skyBot))
        val aSky = glGetAttribLocation(skyProg, "aPos")
        glEnableVertexAttribArray(aSky)
        skyBuf.position(0)
        glVertexAttribPointer(aSky, 2, GL_FLOAT, false, 0, skyBuf)
        glDrawArrays(GL_TRIANGLE_STRIP, 0, 4)
        glDisableVertexAttribArray(aSky)
        glEnable(GL_DEPTH_TEST)

        // World.
        glUseProgram(mainProg)
        val uVP = glGetUniformLocation(mainProg, "uVP")
        val uM = glGetUniformLocation(mainProg, "uM")
        glUniformMatrix4fv(uVP, 1, false, vp, 0)
        glUniform3f(glGetUniformLocation(mainProg, "uCam"), camPX, camPY, camPZ)
        glUniform3f(glGetUniformLocation(mainProg, "uLight"), 0.35f, 0.8f, 0.5f)
        glUniform3f(glGetUniformLocation(mainProg, "uFog"), red(hab.fog), green(hab.fog), blue(hab.fog))
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
            for ((i, c) in creatures.withIndex()) {
                val sp = Species.ALL[c.species]
                // A burrower underground is just a traveling molehill.
                if (c.under) {
                    Matrix.setIdentityM(model, 0)
                    Matrix.translateM(model, 0, c.x, 0f, c.z)
                    Matrix.scaleM(model, 0, 0.62f, 0.62f + sin(c.phase * 6f) * 0.06f, 0.62f)
                    glUniformMatrix4fv(uM, 1, false, model, 0)
                    forms.getOrPut(-2) { CreatureForms.mound() }.draw(aPos, aNrm, aCol)
                    continue
                }
                val hop = when (sp.motion) {
                    Species.HOP -> abs(sin(c.phase * 4f)) * 0.22f
                    Species.FLOAT -> 0.25f + sin(c.phase * 1.6f) * 0.1f
                    Species.DRIFT -> 0.12f + sin(c.phase * 2.2f) * 0.07f
                    Species.SKIM -> 0.5f + sin(c.phase * 2.6f) * 0.18f   // one hand above ground
                    Species.SWIM -> -0.14f + sin(c.phase * 2f) * 0.05f   // hull below the waterline
                    else -> abs(sin(c.phase * 5f)) * 0.035f
                }
                val squash = 1f + sin(c.phase * 5f) * 0.04f +
                    (if (c.happyT > 0f) sin(c.happyT * 14f) * 0.08f else 0f)
                // Shadow first, pinned to the ground.
                Matrix.setIdentityM(model, 0)
                Matrix.translateM(model, 0, c.x, 0f, c.z)
                glUniformMatrix4fv(uM, 1, false, model, 0)
                shadowMesh?.draw(aPos, aNrm, aCol)
                // The creature.
                Matrix.setIdentityM(model, 0)
                Matrix.translateM(model, 0, c.x, hop, c.z)
                Matrix.rotateM(model, 0, c.face * -22f, 0f, 1f, 0f)
                Matrix.scaleM(model, 0, 0.62f, 0.62f * squash, 0.62f)
                glUniformMatrix4fv(uM, 1, false, model, 0)
                forms.getOrPut(c.species) { CreatureForms.build(c.species) }.draw(aPos, aNrm, aCol)
                // Selection halo: a slim spinning diamond overhead.
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

        drawParticles(hab)
    }

    // ------------------------------------------------------------- scene

    private fun rebuildScene(hab: HabitatDef) {
        val b = MeshBuilder()
        val w = Habitats.WORLD_W
        // Big enough that a wanderer looking any direction never sees the edge.
        b.quad(-9f, 0f, 9f, w + 9f, 0f, 9f, w + 9f, 0f, -9f, -9f, 0f, -9f, hab.ground)
        // The microhabitats first — they ARE the biosphere — then local color.
        for (zone in hab.zones) Habitats.zoneDecor(b, zone, hab)
        hab.decor(b, w)
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
                for (s in want) creatures += DenC(s).apply {
                    x = 1f + Random.nextFloat() * (w - 2f)
                    z = -2f + Random.nextFloat() * 3.4f
                    // Everyone wakes up at home: swimmers in water, and so on.
                    Habitats.ALL[habitatIdx].zones.firstOrNull { it.kind == Species.ALL[s].zone }?.let {
                        x = it.x; z = it.z.coerceIn(-2.4f, 1.6f)
                    }
                }
            }
        }
    }

    // --------------------------------------------------------------- sim

    private fun step(dt: Float, hab: HabitatDef) {
        val w = Habitats.WORLD_W
        synchronized(creatures) {
            for (c in creatures) {
                val sp = Species.ALL[c.species]
                val perky = if (sp.temperament in hab.loved) 1.25f else 1f
                c.phase += dt * (0.8f + sp.energy * 1.6f) * perky
                c.happyT = (c.happyT - dt).coerceAtLeast(0f)
                if (c.happyT > 0.4f && Random.nextFloat() < dt * 6f)
                    heart(c.x, 0.9f, c.z, big = c.happyT > 2f)
                // Ambient contentment in a loved habitat.
                if (sp.temperament in hab.loved && Random.nextFloat() < dt * 0.06f)
                    heart(c.x, 0.9f, c.z, big = false)

                // Burrowers travel the underworld between surface visits.
                if (sp.motion == Species.BURROW) {
                    c.underT -= dt
                    if (c.underT <= 0f) {
                        c.under = !c.under
                        c.underT = if (c.under) 2.5f + Random.nextFloat() * 3f
                        else 4f + Random.nextFloat() * 4f
                        if (!c.under) c.happyT = maxOf(c.happyT, 0.8f)  // the triumphant pop-up
                    }
                }
                if (c.lingerT > 0f) {   // parked at a beloved spot, soaking it in
                    c.lingerT -= dt
                    c.vx *= 0.8f; c.vz *= 0.8f
                    if (Random.nextFloat() < dt * 1.2f) heart(c.x, 0.85f, c.z, false)
                } else if (c.pauseT > 0f) {
                    c.pauseT -= dt
                    c.vx *= 0.85f; c.vz *= 0.85f
                } else if (!c.targetX.isNaN()) {
                    // Pilgrimage: make for the beloved place, then linger.
                    val dx = c.targetX - c.x; val dz = c.targetZ - c.z
                    val d = kotlin.math.sqrt(dx * dx + dz * dz)
                    if (d < 0.45f) { c.targetX = Float.NaN; c.lingerT = 3f + Random.nextFloat() * 3f }
                    else {
                        val v = (0.5f + sp.energy * 0.7f) *
                            (if (c.under) 2f else 1f) * (if (sp.motion == Species.SKIM) 1.8f else 1f)
                        c.vx = dx / d * v; c.vz = dz / d * v
                    }
                } else {
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
                        Species.SKIM -> {  // long banking slaloms, never a pause
                            c.vx += (cos(c.phase * 0.9f) * speed * 2.4f - c.vx) * dt * 2f
                            c.vz += (sin(c.phase * 1.3f) * speed * 1.1f - c.vz) * dt * 2f
                        }
                        Species.SWIM -> {  // lazy figure-eights in home water
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
                    // Every so often, remember there's a favorite place here:
                    // a loved item first, else the microhabitat of its niche.
                    if (Random.nextFloat() < dt * 0.14f) {
                        var found = false
                        for ((i, id) in placedIds.withIndex()) {
                            val item = Habitats.item(id) ?: continue
                            if (sp.temperament in item.loved) {
                                c.targetX = Habitats.slotX(i); c.targetZ = Habitats.SLOT_Z + 0.5f
                                found = true; break
                            }
                        }
                        if (!found) {
                            val homes = hab.zones.filter { it.kind == sp.zone }
                            if (homes.isNotEmpty()) {
                                val zn = homes[Random.nextInt(homes.size)]
                                val a = Random.nextFloat() * 6.283f
                                c.targetX = zn.x + cos(a) * zn.r * 0.5f
                                c.targetZ = (zn.z + sin(a) * zn.r * 0.4f).coerceIn(-2.4f, 1.6f)
                            }
                        }
                    }
                }
                c.x = (c.x + c.vx * dt).coerceIn(0.8f, w - 0.8f)
                c.z = (c.z + c.vz * dt).coerceIn(-2.4f, 1.6f)
                // Swimmers stay in their water; the nearest pool claims them.
                if (sp.motion == Species.SWIM) {
                    val pools = hab.zones.filter { it.kind == "WATER" }
                    if (pools.isNotEmpty()) {
                        val zn = pools.minByOrNull {
                            (it.x - c.x) * (it.x - c.x) + (it.z - c.z) * (it.z - c.z)
                        }!!
                        val dx = c.x - zn.x; val dz = c.z - zn.z
                        val d = kotlin.math.sqrt(dx * dx + dz * dz)
                        val rim = zn.r * 0.7f
                        if (d > rim && c.targetX.isNaN()) {
                            c.x = zn.x + dx / d * rim; c.z = zn.z + dz / d * rim
                            c.vx -= dx / d * 0.6f; c.vz -= dz / d * 0.6f
                        }
                    }
                }
                if (abs(c.vx) > 0.05f) c.face += ((if (c.vx > 0) 1f else -1f) - c.face) * dt * 6f
            }
        }
        // Fireflies drift; hearts rise and fade.
        if (particles.count { it.vy < 0.05f } < 26 && Random.nextFloat() < dt * 8f) {
            particles += Particle(
                Random.nextFloat() * w, 0.3f + Random.nextFloat() * 1.6f,
                -2.5f + Random.nextFloat() * 3f, 0.02f, 0f,
                6f + Random.nextFloat() * 6f,
                red(hab.fireflyColor), green(hab.fireflyColor), blue(hab.fireflyColor),
                14f + Random.nextFloat() * 16f)
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

    private fun drawParticles(hab: HabitatDef) {
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
