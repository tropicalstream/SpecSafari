package com.taphunter.gl

import android.content.Context
import android.graphics.PixelFormat
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.os.SystemClock
import com.taphunter.engine.GameEngine
import com.taphunter.engine.State
import com.taphunter.geo.GeoMath
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
 * SBS renders the scene once per eye viewport, same as the Canvas layer.
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

        private var program = 0
        private var uMvp = 0
        private var uAlpha = 0
        private val vbos = IntArray(12)
        private val vertCount = IntArray(12)
        private var surfaceW = 640
        private var surfaceH = 480
        private val mvp = FloatArray(16)
        private val proj = FloatArray(16)
        private val model = FloatArray(16)
        private val tmp = FloatArray(16)

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
            GLES30.glGenBuffers(12, vbos, 0)
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
            if (engine.state != State.HUNT) return
            val me = engine.player ?: return
            val creature = engine.spawner.creature ?: return
            val dM = GeoMath.distanceM(me, creature.p)
            if (dM > engine.appearRange()) return

            val split = sbs()
            val lw = if (split) surfaceW / 2 else surfaceW
            val lh = surfaceH

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
            val e = ((creature.p.lon - me.lon) * mPerLon).toFloat()
            val n = ((creature.p.lat - me.lat) * 111320.0).toFloat()
            val sx = cx + (e * cosH - n * sinH) * scale
            val sy = cy + (-(e * sinH + n * cosH)) * scale
            val ddx = sx - cx; val ddy = sy - cy
            if (sqrt(ddx * ddx + ddy * ddy) > radius - 6f) return  // off-disc: edge arrow's job

            // The zoom effect: distance drives size, arm's length towers.
            val sizePx = min(radius * 0.9f, 250f / dM.coerceAtLeast(2.6f)).coerceAtLeast(11f)
            val alpha = (0.4f + 0.6f * (1f - dM / engine.appearRange())).coerceIn(0.4f, 1f)
            val t = SystemClock.uptimeMillis() % 100000L / 1000f

            Matrix.orthoM(proj, 0, 0f, lw.toFloat(), lh.toFloat(), 0f, -400f, 400f)
            Matrix.setIdentityM(model, 0)
            Matrix.translateM(model, 0, sx, sy - sizePx * 0.15f, 0f)
            Matrix.scaleM(model, 0, sizePx, -sizePx, sizePx)   // flip Y: model +Y is up
            Matrix.rotateM(model, 0, 14f, 1f, 0f, 0f)
            Matrix.rotateM(model, 0, t * 65f, 0f, 1f, 0f)
            Matrix.multiplyMM(mvp, 0, proj, 0, model, 0)

            val species = creature.species
            bindModel(species)
            GLES30.glUseProgram(program)
            GLES30.glUniformMatrix4fv(uMvp, 1, false, mvp, 0)
            GLES30.glUniform1f(uAlpha, alpha)
            GLES30.glLineWidth(2.5f)

            if (split) {
                GLES30.glViewport(0, 0, surfaceW / 2, surfaceH)
                draw(species)
                GLES30.glViewport(surfaceW / 2, 0, surfaceW / 2, surfaceH)
                draw(species)
                GLES30.glViewport(0, 0, surfaceW, surfaceH)
            } else {
                draw(species)
            }
        }

        private fun bindModel(species: Int) {
            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbos[species])
            if (vertCount[species] == 0) {
                val data = HologramModels.forSpecies(species)
                val buf: FloatBuffer = ByteBuffer.allocateDirect(data.size * 4)
                    .order(ByteOrder.nativeOrder()).asFloatBuffer()
                buf.put(data).position(0)
                GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, data.size * 4, buf, GLES30.GL_STATIC_DRAW)
                vertCount[species] = data.size / 6
            }
            GLES30.glEnableVertexAttribArray(0)
            GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, 24, 0)
            GLES30.glEnableVertexAttribArray(1)
            GLES30.glVertexAttribPointer(1, 3, GLES30.GL_FLOAT, false, 24, 12)
        }

        private fun draw(species: Int) {
            GLES30.glDrawArrays(GLES30.GL_LINES, 0, vertCount[species])
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
