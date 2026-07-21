package com.specsafari.phone.den

import android.content.Context
import android.graphics.Bitmap
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.GLES20.*
import android.opengl.Matrix
import android.os.Handler
import android.os.Looper
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sin

/**
 * A pocket photo studio: renders each species' den model as a slow turntable
 * of transparent frames, so the HunterDex shows live miniatures posing for
 * the player — the very creature the den raises, in the palm of a card.
 * Renders lazily on one offscreen EGL pbuffer thread; frames are cached.
 */
object MiniStudio {
    private const val FRAMES = 10
    private const val SIZE = 176
    const val FRAME_MS = 130L

    // Jobs are keyed (species, seed) so an INDIVIDUAL's portrait — phenotype
    // and all — renders through the same studio as the species turntables.
    private val cache = HashMap<Long, Array<Bitmap>>()
    private val pending = HashSet<Long>()
    private val waiters = HashMap<Long, MutableList<(Array<Bitmap>) -> Unit>>()
    private val lock = Any()
    private val queue = java.util.concurrent.LinkedBlockingQueue<Long>()
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var started = false

    private fun key(species: Int, seed: Int) =
        (species.toLong() shl 32) or (seed.toLong() and 0xFFFFFFFFL)

    /** Deliver the turntable for a species (cached, else rendered soon). */
    fun frames(species: Int, onReady: (Array<Bitmap>) -> Unit) =
        frames(species, 0, onReady)

    /** The same turntable wearing one individual's phenotype. */
    fun frames(species: Int, seed: Int, onReady: (Array<Bitmap>) -> Unit) {
        val k = key(species, seed)
        synchronized(lock) {
            cache[k]?.let { f -> main.post { onReady(f) }; return }
            waiters.getOrPut(k) { ArrayList() }.add(onReady)
            if (!pending.add(k)) return
            queue.add(k)
            if (!started) {
                started = true
                Thread({ studioLoop() }, "MiniStudio").apply { isDaemon = true }.start()
            }
        }
    }

    private fun studioLoop() {
        val display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        val ver = IntArray(2)
        if (!EGL14.eglInitialize(display, ver, 0, ver, 1)) return
        val cfgs = arrayOfNulls<EGLConfig>(1); val n = IntArray(1)
        EGL14.eglChooseConfig(display, intArrayOf(
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
            EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8, EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_DEPTH_SIZE, 16, EGL14.EGL_NONE), 0, cfgs, 0, 1, n, 0)
        if (n[0] == 0) return
        val ctx = EGL14.eglCreateContext(display, cfgs[0], EGL14.EGL_NO_CONTEXT,
            intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE), 0)
        val surf = EGL14.eglCreatePbufferSurface(display, cfgs[0],
            intArrayOf(EGL14.EGL_WIDTH, SIZE, EGL14.EGL_HEIGHT, SIZE, EGL14.EGL_NONE), 0)
        EGL14.eglMakeCurrent(display, surf, surf, ctx)

        val prog = glCreateProgram()
        glAttachShader(prog, shader(GL_VERTEX_SHADER, DenGL.MAIN_VS))
        glAttachShader(prog, shader(GL_FRAGMENT_SHADER, DenGL.MAIN_FS))
        glLinkProgram(prog)
        val aPos = glGetAttribLocation(prog, "aPos")
        val aNrm = glGetAttribLocation(prog, "aNrm")
        val aCol = glGetAttribLocation(prog, "aCol")
        glEnable(GL_DEPTH_TEST)

        // The card's stage: a gentle 3/4 studio angle, no fog, warm light.
        val proj = FloatArray(16); val view = FloatArray(16)
        val vp = FloatArray(16); val model = FloatArray(16)
        Matrix.perspectiveM(proj, 0, 36f, 1f, 0.1f, 20f)
        Matrix.setLookAtM(view, 0, 0f, 1.0f, 2.55f, 0f, 0.5f, 0f, 0f, 1f, 0f)
        Matrix.multiplyMM(vp, 0, proj, 0, view, 0)

        while (true) {
            val jobKey = queue.take()
            val species = (jobKey ushr 32).toInt()
            val seed = jobKey.toInt()
            val mesh = runCatching { CreatureForms.build(species, seed) }.getOrNull() ?: continue
            val frames = Array(FRAMES) { k ->
                glViewport(0, 0, SIZE, SIZE)
                glClearColor(0f, 0f, 0f, 0f)
                glClear(GL_COLOR_BUFFER_BIT or GL_DEPTH_BUFFER_BIT)
                glUseProgram(prog)
                glUniformMatrix4fv(glGetUniformLocation(prog, "uVP"), 1, false, vp, 0)
                glUniform3f(glGetUniformLocation(prog, "uCam"), 0f, 1.0f, 2.55f)
                glUniform3f(glGetUniformLocation(prog, "uLight"), 0.4f, 0.8f, 0.45f)
                glUniform3f(glGetUniformLocation(prog, "uFog"), 0f, 0f, 0f)
                glUniform3f(glGetUniformLocation(prog, "uRim"), 0.55f, 0.85f, 1f)
                glUniform1f(glGetUniformLocation(prog, "uDay"), 0.85f)
                glUniform1f(glGetUniformLocation(prog, "uFogNear"), 100f)
                glUniform1f(glGetUniformLocation(prog, "uAlpha"), 1f)
                // The pose: a slow turntable with the den's idle bounce baked in.
                val squash = 1f + 0.045f * sin(k * 2f * Math.PI.toFloat() * 2f / FRAMES)
                Matrix.setIdentityM(model, 0)
                Matrix.rotateM(model, 0, 24f + k * 360f / FRAMES, 0f, 1f, 0f)
                Matrix.scaleM(model, 0, 0.92f, 0.92f * squash, 0.92f)
                glUniformMatrix4fv(glGetUniformLocation(prog, "uM"), 1, false, model, 0)
                glEnableVertexAttribArray(aPos)
                glEnableVertexAttribArray(aNrm)
                glEnableVertexAttribArray(aCol)
                mesh.draw(aPos, aNrm, aCol)
                glDisableVertexAttribArray(aPos)
                glDisableVertexAttribArray(aNrm)
                glDisableVertexAttribArray(aCol)
                glFinish()
                grab()
            }
            synchronized(lock) {
                cache[jobKey] = frames
                pending.remove(jobKey)
                val ready = waiters.remove(jobKey)
                if (ready != null) main.post { for (cb in ready) cb(frames) }
            }
        }
    }

    private fun grab(): Bitmap {
        val buf = ByteBuffer.allocateDirect(SIZE * SIZE * 4).order(ByteOrder.nativeOrder())
        glReadPixels(0, 0, SIZE, SIZE, GL_RGBA, GL_UNSIGNED_BYTE, buf)
        val raw = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        buf.rewind()
        raw.copyPixelsFromBuffer(buf)
        // GL reads rows bottom-up; the card wants them the right way round.
        val flip = android.graphics.Matrix().apply { preScale(1f, -1f) }
        val out = Bitmap.createBitmap(raw, 0, 0, SIZE, SIZE, flip, false)
        raw.recycle()
        return out
    }

    private fun shader(type: Int, src: String): Int {
        val s = glCreateShader(type)
        glShaderSource(s, src)
        glCompileShader(s)
        return s
    }
}

/** A dex card's window into the den: the species' live model, turning. */
class MiniModelView(context: Context, species: Int) :
    android.widget.ImageView(context) {

    private var frames: Array<Bitmap>? = null
    private var idx = 0
    private val tick = object : Runnable {
        override fun run() {
            frames?.let { f ->
                idx = (idx + 1) % f.size
                setImageBitmap(f[idx])
            }
            postDelayed(this, MiniStudio.FRAME_MS)
        }
    }

    init {
        scaleType = ScaleType.FIT_CENTER
        MiniStudio.frames(species) { f ->
            frames = f
            setImageBitmap(f[0])
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        postDelayed(tick, MiniStudio.FRAME_MS)
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(tick)
        super.onDetachedFromWindow()
    }
}
