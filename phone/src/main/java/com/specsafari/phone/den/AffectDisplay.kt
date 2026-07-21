package com.specsafari.phone.den

import android.graphics.Color
import android.opengl.GLES20.glUniformMatrix4fv
import android.opengl.Matrix
import kotlin.math.sin

/**
 * The affect layer: purely aesthetic behavioral expression. Each creature's
 * internal state (hunger, learned trust and fear, alertness, curiosity) and
 * its dex profile (energy, eye morphology) drive posture — a terrified crouch
 * and tremble, a trusting perked stance, a hungry sag, a curious head-cock —
 * and FACIAL expression drawn right on the model: worry-brows and a sweat
 * drop, happy squints, eager eye-sparkles, and idle blinks. Anchored to every
 * species' true eye positions from CreatureForms, so a Clayward frets from
 * the top of its head-block and a Kelpling from its raised periscope face.
 * Touches nothing mechanical: no stats, no events, no behavior.
 */
object AffectDisplay {

    // Eye anchors mirrored from CreatureForms' eyes() calls:
    // eyeY, eyeZ, spread, eyeR per species index.
    private val FACE = floatArrayOf(
        0.48f, 0.33f, 0.16f, 0.085f,   // 0 LEAFLING
        0.45f, 0.38f, 0.16f, 0.085f,   // 1 THORNPUP
        0.48f, 0.34f, 0.16f, 0.085f,   // 2 PUDDLIM
        0.42f, 0.36f, 0.16f, 0.085f,   // 3 EMBERLING
        0.50f, 0.34f, 0.16f, 0.060f,   // 4 BOOKWYRM
        0.50f, 0.20f, 0.11f, 0.060f,   // 5 LUXMOTH
        0.48f, 0.15f, 0.13f, 0.070f,   // 6 COINIX
        0.46f, 0.36f, 0.16f, 0.085f,   // 7 FERROKIT
        0.46f, 0.27f, 0.16f, 0.085f,   // 8 VOLTLING
        0.54f, 0.30f, 0.16f, 0.085f,   // 9 GUSTRIL
        0.46f, 0.33f, 0.15f, 0.075f,   // 10 SHADEPAW
        0.56f, 0.26f, 0.12f, 0.060f,   // 11 PRISMKIN
        0.50f, 0.26f, 0.12f, 0.060f,   // 12 WISPLET
        0.45f, 0.28f, 0.16f, 0.085f,   // 13 MOONHARE
        0.66f, 0.48f, 0.11f, 0.060f,   // 14 KELPLING
        0.40f, 0.28f, 0.16f, 0.085f,   // 15 PUCKLE
        0.48f, 0.36f, 0.16f, 0.090f,   // 16 DREAMBAKU (sliver-eyed)
        0.46f, 0.34f, 0.16f, 0.085f,   // 17 GLIMMERFOX
        0.42f, 0.36f, 0.16f, 0.085f,   // 18 GEMBACK
        0.85f, 0.18f, 0.10f, 0.050f,   // 19 CLAYWARD
        0.52f, 0.30f, 0.16f, 0.085f,   // 20 SKYDRUM
        0.52f, 0.26f, 0.16f, 0.085f,   // 21 PYREBIRD
        0.42f, 0.30f, 0.16f, 0.085f,   // 22 HORNHOP
        0.68f, 0.24f, 0.16f, 0.085f,   // 23 SANDSHIFT
        0.38f, 0.36f, 0.12f, 0.060f,   // 24 ZEPHYRET
        0.40f, 0.28f, 0.16f, 0.085f,   // 25 NIXLET
        0.44f, 0.30f, 0.13f, 0.055f,   // 26 MOLDEWARP
        0.73f, 0.46f, 0.14f, 0.055f,   // 27 SYLVARCH
        0.84f, 0.45f, 0.14f, 0.052f    // 28 MISTCROWN
    )

    enum class Face { NONE, WORRY, WORRY_SWEAT, SQUINT, SPARKLE, BROW_COCK, BLINK }

    /** One frame's worth of expressive intent; reused, GL thread only. */
    class Mood {
        var face = Face.NONE
        var crouch = 1f      // fear flattens the stance, joy perks it
        var stature = 1f     // lifetime carriage: trust stands tall, fear small
        var pitch = 0f       // hungry forward sag, degrees
        var roll = 0f        // curious head-cock, degrees
        var jitterX = 0f     // terror tremble, world units
        var jitterZ = 0f
    }

    private val mood = Mood()
    private val face = FloatArray(16)

    private const val K_BROW = 0
    private const val K_LID = 1
    private const val K_SPARK = 2
    private const val K_DROP = 3
    private val meshes = HashMap<Int, Mesh>()

    /**
     * Read the creature's moment and choose its display. Fear only shows
     * once the creature is actually AWARE of you — a wary species grazing
     * alone carries only its general stature, not an audience-less terror.
     */
    fun assess(c: DenC, fear: Float, trust: Float, foodHope: Float,
               energy: Float, t: Float): Mood {
        val m = mood
        m.face = Face.NONE
        m.crouch = 1f; m.pitch = 0f; m.roll = 0f
        m.jitterX = 0f; m.jitterZ = 0f
        m.stature = 1f + trust * 0.03f - fear * 0.05f
        if (c.sleeping || c.under || c.releasing || c.questT > 0f) return m
        if (c.feedingT > 0f) return m   // table manners + heart bubbles own this
        when {
            c.fleeing || (fear > 0.75f && c.aware) -> {
                m.face = if (c.fleeing || fear > 0.85f) Face.WORRY_SWEAT else Face.WORRY
                m.crouch = 0.86f
                val rate = 26f + energy * 18f
                m.jitterX = sin(t * rate + c.phase) * 0.012f
                m.jitterZ = sin(t * rate * 1.13f + c.phase * 2f) * 0.010f
            }
            c.alert || (fear > 0.45f && c.aware) -> {
                m.face = Face.WORRY; m.crouch = 0.94f
            }
            c.happyT > 0.3f -> { m.face = Face.SQUINT; m.crouch = 1.05f }
            c.soliciting || (foodHope > 0.5f && c.aware) -> {
                m.face = Face.SPARKLE; m.crouch = 1.03f
            }
            c.investigating -> {
                m.face = Face.BROW_COCK
                m.roll = sin(t * 1.7f + c.phase) * 11f
            }
            c.hunger > 0.72f -> {
                m.pitch = 5f; m.crouch = 0.97f
                val rumble = sin(t * 2.3f + c.phase)   // the belly speaks up
                if (rumble > 0.6f) m.crouch *= 1f + (rumble - 0.6f) * 0.06f
            }
            else -> {
                if (((t * 0.85f + c.phase * 1.7f) % 4.3f) < 0.13f) m.face = Face.BLINK
                if (trust > 0.5f) m.crouch = 1.02f
            }
        }
        return m
    }

    /** Draw the chosen expression onto the face, riding the body transform. */
    fun drawFace(species: Int, m: Mood, bodyModel: FloatArray, uM: Int,
                 aPos: Int, aNrm: Int, aCol: Int, t: Float, phase: Float) {
        if (m.face == Face.NONE) return
        val i = species * 4
        val ey: Float; val ez: Float; val spread: Float; val er: Float
        if (i + 3 < FACE.size) {
            ey = FACE[i]; ez = FACE[i + 1]; spread = FACE[i + 2]; er = FACE[i + 3]
        } else { ey = 0.5f; ez = 0.3f; spread = 0.14f; er = 0.07f }
        val s = er / 0.085f   // features sized to the species' own eyes

        fun place(dx: Float, dy: Float, dz: Float, rotZ: Float, scale: Float, kind: Int) {
            System.arraycopy(bodyModel, 0, face, 0, 16)
            Matrix.translateM(face, 0, dx, dy, dz)
            if (rotZ != 0f) Matrix.rotateM(face, 0, rotZ, 0f, 0f, 1f)
            Matrix.scaleM(face, 0, scale, scale, scale)
            glUniformMatrix4fv(uM, 1, false, face, 0)
            mesh(kind).draw(aPos, aNrm, aCol)
        }

        when (m.face) {
            Face.WORRY, Face.WORRY_SWEAT -> {
                // Inner ends lift: the universal fret.
                place(-spread, ey + er * 1.7f, ez + er * 0.8f, -22f, s, K_BROW)
                place(spread, ey + er * 1.7f, ez + er * 0.8f, 22f, s, K_BROW)
                if (m.face == Face.WORRY_SWEAT) {
                    val fall = (t * 1.3f + phase) % 1.2f
                    place(spread + er * 2.6f, ey + er * 2.4f - fall * er * 1.6f,
                        ez * 0.5f, 0f, s * (1f - fall * 0.25f), K_DROP)
                }
            }
            Face.SQUINT -> {   // eyes closed in a smile: ^ ^
                place(-spread, ey + er * 0.4f, ez + er * 0.75f, 9f, s, K_LID)
                place(spread, ey + er * 0.4f, ez + er * 0.75f, -9f, s, K_LID)
            }
            Face.BLINK -> {
                place(-spread, ey, ez + er * 0.75f, 0f, s, K_LID)
                place(spread, ey, ez + er * 0.75f, 0f, s, K_LID)
            }
            Face.SPARKLE -> {   // stars beside the eyes, twinkling
                val tw = s * (0.85f + 0.3f * sin(t * 9f + phase))
                place(-(spread + er * 1.6f), ey + er * 0.9f, ez, t * 120f % 360f, tw, K_SPARK)
                place(spread + er * 1.6f, ey + er * 1.2f, ez, -(t * 140f % 360f), tw * 0.8f, K_SPARK)
            }
            Face.BROW_COCK -> {   // one brow up: hmm?
                place(-spread, ey + er * 1.6f, ez + er * 0.8f, 0f, s, K_BROW)
                place(spread, ey + er * 2.3f, ez + er * 0.8f, 12f, s, K_BROW)
            }
            Face.NONE -> {}
        }
    }

    private fun mesh(kind: Int): Mesh = meshes.getOrPut(kind) {
        val b = MeshBuilder()
        val dark = Color.rgb(18, 22, 30)
        when (kind) {
            K_BROW -> b.box(0f, 0f, 0f, 0.11f, 0.022f, 0.02f, dark)
            K_LID -> b.ellipsoid(0f, 0f, 0f, 0.105f, 0.02f, 0.05f, dark, 0f, 4, 6)
            K_SPARK -> {
                val white = Color.WHITE
                b.cone(0f, 0.02f, 0f, 0.018f, 0f, 1f, 0f, 0.11f, white, 0.9f, 4)
                b.cone(0f, -0.02f, 0f, 0.018f, 0f, -1f, 0f, 0.11f, white, 0.9f, 4)
                b.cone(0.02f, 0f, 0f, 0.018f, 1f, 0f, 0f, 0.11f, white, 0.9f, 4)
                b.cone(-0.02f, 0f, 0f, 0.018f, -1f, 0f, 0f, 0.11f, white, 0.9f, 4)
            }
            else -> {   // K_DROP — the classic bead of cold sweat
                val cyan = Color.rgb(150, 220, 255)
                b.ellipsoid(0f, 0f, 0f, 0.045f, 0.055f, 0.04f, cyan, 0.35f, 5, 6)
                b.cone(0f, 0.04f, 0f, 0.03f, 0f, 1f, 0f, 0.07f, cyan, 0.35f, 5)
            }
        }
        b.bake()
    }
}
