package com.specsafari.gl

import android.content.Context
import android.graphics.PixelFormat
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.os.SystemClock
import com.specsafari.engine.GameEngine
import com.specsafari.engine.State
import com.specsafari.geo.GeoMath
import com.specsafari.shared.Species
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The apparition layer: a translucent GLES3 surface over the Canvas UI.
 * When the hunter closes on a creature, its 3D wireframe hologram
 * materializes AT ITS SPOT ON THE MINIMAP and zooms up as the distance
 * falls — tiny ghost at the scanner's edge, towering at capture range.
 * The big MOMENTS are 3D too: a struck cache cracks open in gold right
 * before your eyes, and a volunteer sent to fetch runs the errand in
 * wireframe. SBS renders the scene once per eye viewport, same as the
 * Canvas layer.
 */
class HologramView(
    context: Context,
    private val engine: GameEngine,
    private val sbs: () -> Boolean
) : GLSurfaceView(context) {

    init {
        setEGLContextClientVersion(3)
        setEGLConfigChooser(8, 8, 8, 8, 0, 0)
        holder.setFormat(PixelFormat.TRANSLUCENT)
        setZOrderOnTop(true)
        preserveEGLContextOnPause = true
        setRenderer(HoloRenderer(engine, sbs))
        renderMode = RENDERMODE_CONTINUOUSLY
    }

    private class HoloRenderer(
        private val engine: GameEngine,
        private val sbs: () -> Boolean
    ) : Renderer {

        // One VBO per species, then three scene props (a 12-slot array once
        // crashed here the day the dex outgrew it — size from the source).
        private val slotChest = Species.ALL.size
        private val slotLid = Species.ALL.size + 1
        private val slotBurst = Species.ALL.size + 2
        private val slotCount = Species.ALL.size + 3

        private var program = 0
        private var uMvp = 0
        private var uAlpha = 0
        private val vbos = IntArray(slotCount)
        private val vertCount = IntArray(slotCount)
        private var surfaceW = 640
        private var surfaceH = 480
        private val mvp = FloatArray(16)
        private val proj = FloatArray(16)
        private val model = FloatArray(16)
        private val parent = FloatArray(16)

        override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
            val vs = """
                #version 300 es
                layout(location=0) in vec3 aPos;
                layout(location=1) in vec3 aColor;
                uniform mat4 uMvp;
                out vec3 vColor;
                void main() { gl_Position = uMvp * vec4(aPos, 1.0); vColor = aColor; }
            """.trimIndent()
            val fs = """
                #version 300 es
                precision mediump float;
                in vec3 vColor;
                uniform float uAlpha;
                out vec4 frag;
                void main() { frag = vec4(vColor * uAlpha, uAlpha); }
            """.trimIndent()
            program = link(compile(GLES30.GL_VERTEX_SHADER, vs), compile(GLES30.GL_FRAGMENT_SHADER, fs))
            uMvp = GLES30.glGetUniformLocation(program, "uMvp")
            uAlpha = GLES30.glGetUniformLocation(program, "uAlpha")
            GLES30.glGenBuffers(slotCount, vbos, 0)
            vertCount.fill(0)   // context loss leaves stale counts for dead VBOs
            GLES30.glClearColor(0f, 0f, 0f, 0f)
            GLES30.glDisable(GLES30.GL_DEPTH_TEST)
            GLES30.glEnable(GLES30.GL_BLEND)
            GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE)
        }

        override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
            surfaceW = width; surfaceH = height
        }

        override fun onDrawFrame(gl: GL10?) {
            GLES30.glViewport(0, 0, surfaceW, surfaceH)
            GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
            val split = sbs()
            val lw = if (split) surfaceW / 2 else surfaceW
            val lh = surfaceH
            Matrix.orthoM(proj, 0, 0f, lw.toFloat(), lh.toFloat(), 0f, -400f, 400f)
            val t = SystemClock.uptimeMillis() % 100000L / 1000f
            when (engine.state) {
                State.HUNT -> drawApparition(lw, lh, split, t)
                State.RESCUE -> drawRescueScene(lw, lh, split, t)
                State.LOOT -> drawLootScene(lw, lh, split, t)
                else -> {}
            }
        }

        // ------------------------------------------- the hunt apparition

        private fun drawApparition(lw: Int, lh: Int, split: Boolean, t: Float) {
            val me = engine.player ?: return

            // The Canvas map's disc geometry, recomputed identically
            // (upper-right corner, Everyday size).
            val radius = min(210f * (lh / 480f), lw - 10f) / 2f
            val cx = lw - radius - 6f
            val cy = radius + 10f
            val scale = radius / engine.zoomRadius

            // Equirect projection rotated to heading-up (same as MapRenderer).
            val mPerLon = 111320.0 * cos(Math.toRadians(me.lat))
            val hRad = Math.toRadians(engine.heading.toDouble())
            val cosH = cos(hRad).toFloat(); val sinH = sin(hRad).toFloat()

            // EVERY nearby creature materializes — the ladder quarry, the
            // wild flanker, a lost friend — never a silent handoff to nothing.
            for (creature in engine.interest()) {
                if (!creature.isCreature) continue
                val dM = GeoMath.distanceM(me, creature.p)
                if (dM > engine.appearRange()) continue
                val e = ((creature.p.lon - me.lon) * mPerLon).toFloat()
                val n = ((creature.p.lat - me.lat) * 111320.0).toFloat()
                val sx = cx + (e * cosH - n * sinH) * scale
                val sy = cy + (-(e * sinH + n * cosH)) * scale
                val ddx = sx - cx; val ddy = sy - cy
                if (sqrt(ddx * ddx + ddy * ddy) > radius - 6f) continue  // off-disc: edge arrow's job

                // The zoom effect: distance drives size, arm's length towers.
                val sizePx = min(radius * 0.9f, 250f / dM.coerceAtLeast(2.6f)).coerceAtLeast(11f)
                val alpha = (0.4f + 0.6f * (1f - dM / engine.appearRange())).coerceIn(0.4f, 1f)

                Matrix.setIdentityM(model, 0)
                Matrix.translateM(model, 0, sx, sy - sizePx * 0.15f, 0f)
                Matrix.scaleM(model, 0, sizePx, -sizePx, sizePx)   // flip Y: model +Y is up
                Matrix.rotateM(model, 0, 14f, 1f, 0f, 0f)
                Matrix.rotateM(model, 0, t * 65f, 0f, 1f, 0f)
                Matrix.multiplyMM(mvp, 0, proj, 0, model, 0)
                GLES30.glLineWidth(2.5f)
                render(creature.species, alpha, split)
            }
        }

        // ------------------------- the volunteer's errand, run in wireframe

        private fun drawRescueScene(lw: Int, lh: Int, split: Boolean, t: Float) {
            val sp = engine.fetchSpecies
            if (sp < 0) return
            val p = engine.fetchProgress().coerceIn(0f, 1f)
            val startX = lw * 0.30f; val startY = lh * 0.60f
            val chestX = lw * 0.72f; val chestY = lh * 0.30f

            // The cache waits at the far point, small with distance.
            chestMvp(chestX, chestY, lh * 0.11f, openDeg = 0f, spinDeg = t * 30f)
            GLES30.glLineWidth(2.2f)
            render(slotChest, 0.8f, split)
            lidMvp(0f)
            render(slotLid, 0.8f, split)

            // Out, a beat at the chest, and home: position and size follow.
            val leg = when {
                p < 0.46f -> p / 0.46f                    // outbound 0..1
                p < 0.58f -> 1f                           // digging at the cache
                else -> 1f - (p - 0.58f) / 0.42f          // homebound 1..0
            }
            val cxr = startX + (chestX - startX) * leg
            val cyr = startY + (chestY - startY) * leg
            val size = lh * (0.20f - 0.11f * leg)         // away = smaller
            val hop = kotlin.math.abs(sin(t * 9f)) * lh * 0.018f

            Matrix.setIdentityM(model, 0)
            Matrix.translateM(model, 0, cxr, cyr - hop, 0f)
            Matrix.scaleM(model, 0, size, -size, size)
            Matrix.rotateM(model, 0, 14f, 1f, 0f, 0f)
            Matrix.rotateM(model, 0, t * 85f, 0f, 1f, 0f)
            Matrix.multiplyMM(mvp, 0, proj, 0, model, 0)
            GLES30.glLineWidth(2.5f)
            render(sp, 0.95f, split)

            // Gold dust while it digs.
            if (p in 0.46f..0.58f) {
                burstMvp(chestX, chestY, lh * 0.10f, t)
                GLES30.glLineWidth(1.8f)
                render(slotBurst, 0.7f, split)
            }
        }

        // ------------------------------- the cache cracks open, in gold

        private fun drawLootScene(lw: Int, lh: Int, split: Boolean, t: Float) {
            val ts = engine.stateT
            // The spectacle plays once and bows out; the text stays behind.
            val sceneA = when {
                ts < 0.2f -> ts / 0.2f
                ts > 2.6f -> (1f - (ts - 2.6f) / 0.5f).coerceAtLeast(0f)
                else -> 1f
            }
            if (sceneA <= 0f) return
            val cx = lw * 0.5f; val cy = lh * 0.38f
            val size = lh * 0.20f
            val open = 105f * easeOut((ts / 0.7f).coerceIn(0f, 1f))

            chestMvp(cx, cy, size, open, spinDeg = 20f + t * 35f)
            GLES30.glLineWidth(2.6f)
            render(slotChest, sceneA, split)
            lidMvp(open)
            render(slotLid, sceneA, split)

            // The burst rides out of the lid as it opens.
            if (ts > 0.30f) {
                val bs = easeOut(((ts - 0.30f) / 0.8f).coerceIn(0f, 1f))
                burstMvp(cx, cy - size * 0.35f, size * (0.4f + 1.1f * bs), t)
                GLES30.glLineWidth(2f)
                render(slotBurst, sceneA * (1f - 0.55f * bs), split)
            }

            // A hero that came home takes its bow beside the gold.
            if (engine.fetchWasQuest && engine.fetchReturned && engine.fetchSpecies >= 0) {
                val hop = kotlin.math.abs(sin(t * 6f)) * lh * 0.02f
                Matrix.setIdentityM(model, 0)
                Matrix.translateM(model, 0, lw * 0.24f, cy + size * 0.25f - hop, 0f)
                Matrix.scaleM(model, 0, lh * 0.13f, -lh * 0.13f, lh * 0.13f)
                Matrix.rotateM(model, 0, 14f, 1f, 0f, 0f)
                Matrix.rotateM(model, 0, t * 70f, 0f, 1f, 0f)
                Matrix.multiplyMM(mvp, 0, proj, 0, model, 0)
                GLES30.glLineWidth(2.5f)
                render(engine.fetchSpecies, sceneA * 0.95f, split)
            }
        }

        // -------------------------------------------------- scene helpers

        /** Chest base transform; also parks the parent matrix for the lid. */
        private fun chestMvp(x: Float, y: Float, size: Float, openDeg: Float, spinDeg: Float) {
            Matrix.setIdentityM(parent, 0)
            Matrix.translateM(parent, 0, x, y, 0f)
            Matrix.scaleM(parent, 0, size, -size, size)
            Matrix.rotateM(parent, 0, 16f, 1f, 0f, 0f)
            Matrix.rotateM(parent, 0, spinDeg, 0f, 1f, 0f)
            Matrix.multiplyMM(mvp, 0, proj, 0, parent, 0)
        }

        /** Lid rides the chest's parent transform, hinged at the back rim. */
        private fun lidMvp(openDeg: Float) {
            System.arraycopy(parent, 0, model, 0, 16)
            Matrix.translateM(model, 0, 0f, 0.55f, -0.4f)
            Matrix.rotateM(model, 0, -openDeg, 1f, 0f, 0f)
            Matrix.multiplyMM(mvp, 0, proj, 0, model, 0)
        }

        private fun burstMvp(x: Float, y: Float, size: Float, t: Float) {
            Matrix.setIdentityM(model, 0)
            Matrix.translateM(model, 0, x, y, 0f)
            Matrix.scaleM(model, 0, size, -size, size)
            Matrix.rotateM(model, 0, t * 50f, 0f, 1f, 0f)
            Matrix.multiplyMM(mvp, 0, proj, 0, model, 0)
        }

        private fun easeOut(x: Float): Float = 1f - (1f - x) * (1f - x)

        // ---------------------------------------------------- gl plumbing

        private fun render(slot: Int, alpha: Float, split: Boolean) {
            GLES30.glUseProgram(program)
            GLES30.glUniformMatrix4fv(uMvp, 1, false, mvp, 0)
            GLES30.glUniform1f(uAlpha, alpha)
            bind(slot)
            if (split) {
                GLES30.glViewport(0, 0, surfaceW / 2, surfaceH)
                GLES30.glDrawArrays(GLES30.GL_LINES, 0, vertCount[slot])
                GLES30.glViewport(surfaceW / 2, 0, surfaceW / 2, surfaceH)
                GLES30.glDrawArrays(GLES30.GL_LINES, 0, vertCount[slot])
                GLES30.glViewport(0, 0, surfaceW, surfaceH)
            } else {
                GLES30.glDrawArrays(GLES30.GL_LINES, 0, vertCount[slot])
            }
        }

        private fun bind(slot: Int) {
            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbos[slot])
            if (vertCount[slot] == 0) {
                val data = when (slot) {
                    slotChest -> chestModel()
                    slotLid -> lidModel()
                    slotBurst -> burstModel()
                    else -> HologramModels.forSpecies(slot)
                }
                val buf: FloatBuffer = ByteBuffer.allocateDirect(data.size * 4)
                    .order(ByteOrder.nativeOrder()).asFloatBuffer()
                buf.put(data).position(0)
                GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, data.size * 4, buf, GLES30.GL_STATIC_DRAW)
                vertCount[slot] = data.size / 6
            }
            GLES30.glEnableVertexAttribArray(0)
            GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, 24, 0)
            GLES30.glEnableVertexAttribArray(1)
            GLES30.glVertexAttribPointer(1, 3, GLES30.GL_FLOAT, false, 24, 12)
        }

        // -------------------------------------------------- prop geometry

        private fun edge(
            out: MutableList<Float>,
            x1: Float, y1: Float, z1: Float, x2: Float, y2: Float, z2: Float,
            r: Float, g: Float, b: Float
        ) {
            out.add(x1); out.add(y1); out.add(z1); out.add(r); out.add(g); out.add(b)
            out.add(x2); out.add(y2); out.add(z2); out.add(r); out.add(g); out.add(b)
        }

        /** Wireframe treasure chest body: gold box, straps, a bright hasp. */
        private fun chestModel(): FloatArray {
            val v = ArrayList<Float>(400)
            val gr = 1f; val gg = 0.78f; val gb = 0.28f
            val xs = floatArrayOf(-0.6f, 0.6f); val zs = floatArrayOf(-0.4f, 0.4f)
            for (x in xs) for (z in zs) edge(v, x, 0f, z, x, 0.55f, z, gr, gg, gb)
            for (y in floatArrayOf(0f, 0.55f)) {
                edge(v, -0.6f, y, -0.4f, 0.6f, y, -0.4f, gr, gg, gb)
                edge(v, -0.6f, y, 0.4f, 0.6f, y, 0.4f, gr, gg, gb)
                edge(v, -0.6f, y, -0.4f, -0.6f, y, 0.4f, gr, gg, gb)
                edge(v, 0.6f, y, -0.4f, 0.6f, y, 0.4f, gr, gg, gb)
            }
            // Iron straps front and back.
            for (x in floatArrayOf(-0.3f, 0.3f)) for (z in zs)
                edge(v, x, 0f, z, x, 0.55f, z, 0.82f, 0.58f, 0.2f)
            // The hasp glints brightest.
            edge(v, -0.07f, 0.30f, 0.401f, 0.07f, 0.30f, 0.401f, 1f, 0.96f, 0.62f)
            edge(v, -0.07f, 0.44f, 0.401f, 0.07f, 0.44f, 0.401f, 1f, 0.96f, 0.62f)
            edge(v, -0.07f, 0.30f, 0.401f, -0.07f, 0.44f, 0.401f, 1f, 0.96f, 0.62f)
            edge(v, 0.07f, 0.30f, 0.401f, 0.07f, 0.44f, 0.401f, 1f, 0.96f, 0.62f)
            return v.toFloatArray()
        }

        /** The lid, hinged at its own origin so a rotation swings it open. */
        private fun lidModel(): FloatArray {
            val v = ArrayList<Float>(200)
            val gr = 1f; val gg = 0.82f; val gb = 0.34f
            // Base rectangle at the rim, crown inset above: a low vaulted lid.
            edge(v, -0.62f, 0f, 0f, 0.62f, 0f, 0f, gr, gg, gb)
            edge(v, -0.62f, 0f, 0.82f, 0.62f, 0f, 0.82f, gr, gg, gb)
            edge(v, -0.62f, 0f, 0f, -0.62f, 0f, 0.82f, gr, gg, gb)
            edge(v, 0.62f, 0f, 0f, 0.62f, 0f, 0.82f, gr, gg, gb)
            edge(v, -0.62f, 0.24f, 0.12f, 0.62f, 0.24f, 0.12f, gr, gg, gb)
            edge(v, -0.62f, 0.24f, 0.70f, 0.62f, 0.24f, 0.70f, gr, gg, gb)
            edge(v, -0.62f, 0.24f, 0.12f, -0.62f, 0.24f, 0.70f, gr, gg, gb)
            edge(v, 0.62f, 0.24f, 0.12f, 0.62f, 0.24f, 0.70f, gr, gg, gb)
            for (x in floatArrayOf(-0.62f, 0.62f)) {
                edge(v, x, 0f, 0f, x, 0.24f, 0.12f, gr, gg, gb)
                edge(v, x, 0f, 0.82f, x, 0.24f, 0.70f, gr, gg, gb)
            }
            return v.toFloatArray()
        }

        /** A starburst of gold rays, white-hot at the core. */
        private fun burstModel(): FloatArray {
            val v = ArrayList<Float>(30 * 12)
            for (i in 0 until 30) {
                val y = 1f - 2f * (i + 0.5f) / 30f
                val rr = sqrt(1f - y * y)
                val phi = i * 2.399963f
                val dx = rr * cos(phi); val dz = rr * sin(phi)
                val outer = 0.85f + (i % 5) * 0.06f
                v.add(dx * 0.18f); v.add(y * 0.18f); v.add(dz * 0.18f)
                v.add(1f); v.add(1f); v.add(0.88f)
                v.add(dx * outer); v.add(y * outer); v.add(dz * outer)
                v.add(1f); v.add(0.72f); v.add(0.18f)
            }
            return v.toFloatArray()
        }

        private fun compile(type: Int, src: String): Int {
            val s = GLES30.glCreateShader(type)
            GLES30.glShaderSource(s, src)
            GLES30.glCompileShader(s)
            return s
        }

        private fun link(vs: Int, fs: Int): Int {
            val p = GLES30.glCreateProgram()
            GLES30.glAttachShader(p, vs)
            GLES30.glAttachShader(p, fs)
            GLES30.glLinkProgram(p)
            return p
        }
    }
}
