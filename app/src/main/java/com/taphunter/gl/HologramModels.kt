package com.taphunter.gl

import android.graphics.Color
import com.taphunter.shared.Species
import kotlin.math.cos
import kotlin.math.sin

/**
 * 3D wireframe holograms, one per species — line lists in unit space
 * (the creature fits inside radius ~1, +Y up), spun and scaled by the
 * renderer. Vertex layout: x y z r g b (GL_LINES pairs).
 */
object HologramModels {

    private val cache = arrayOfNulls<FloatArray>(Species.ALL.size)

    fun forSpecies(i: Int): FloatArray {
        cache[i]?.let { return it }
        val b = Builder()
        val sp = Species.ALL[i]
        when (i) {
            0 -> { // LEAFLING — sphere sprout with leaf ears and a stem
                b.sphere(0f, 0f, 0.72f, sp.main)
                b.leaf(-0.62f, 0.72f, 0.5f, -40f, sp.accent)
                b.leaf(0.62f, 0.72f, 0.5f, 40f, sp.accent)
                b.seg(0f, 0.72f, 0f, 0f, 1.12f, 0f, sp.accent)
                b.leaf(0.16f, 1.3f, 0.38f, 22f, sp.main)
                b.eyes(sp.accent)
            }
            1 -> { // THORNPUP — spiky pup
                b.sphere(0f, 0f, 0.7f, sp.main)
                for (k in 0..5) {
                    val a = Math.toRadians(120.0 + k * 24.0)
                    val x = cos(a).toFloat(); val y = sin(a).toFloat()
                    b.seg(x * 0.7f, y * 0.7f, 0f, x * 1.05f, y * 1.05f, 0f, sp.accent)
                }
                b.ear(-0.45f, 0.62f, 0.42f, sp.accent)
                b.ear(0.45f, 0.62f, 0.42f, sp.accent)
                b.eyes(sp.accent)
            }
            2 -> { // PUDDLIM — lathed teardrop
                b.lathe(floatArrayOf(-0.62f, 0.5f, -0.2f, 0.72f, 0.28f, 0.55f, 0.7f, 0.28f, 1.0f, 0.05f), sp.main)
                b.ring(0f, -0.62f, 0f, 0.5f, 0.16f, Builder.XZ, sp.accent)
                b.eyes(sp.accent)
            }
            3 -> { // EMBERLING — sphere with flames
                b.sphere(0f, -0.1f, 0.66f, sp.main)
                b.flame(0f, 0.6f, 0.62f, sp.accent)
                b.flame(-0.5f, 0.42f, 0.4f, sp.main)
                b.flame(0.5f, 0.42f, 0.4f, sp.main)
                b.eyes(sp.accent)
            }
            4 -> { // BOOKWYRM — horned wyrm with spectacles
                b.sphere(0f, 0f, 0.68f, sp.main)
                b.ear(-0.34f, 0.66f, 0.32f, sp.accent)
                b.ear(0.34f, 0.66f, 0.32f, sp.accent)
                b.ring(-0.26f, 0.08f, 0.62f, 0.2f, 0.2f, Builder.XY, sp.accent)
                b.ring(0.26f, 0.08f, 0.62f, 0.2f, 0.2f, Builder.XY, sp.accent)
                b.seg(-0.06f, 0.08f, 0.62f, 0.06f, 0.08f, 0.62f, sp.accent)
                b.tail(sp.main)
            }
            5 -> { // LUXMOTH — big glowing wings
                b.sphere(0f, 0f, 0.42f, sp.main)
                b.wing(-1f, sp.main, sp.accent)
                b.wing(1f, sp.main, sp.accent)
                b.seg(-0.12f, 0.36f, 0f, -0.4f, 0.95f, 0.1f, sp.accent)
                b.seg(0.12f, 0.36f, 0f, 0.4f, 0.95f, 0.1f, sp.accent)
                b.eyes(sp.accent)
            }
            6 -> { // COINIX — winged coin
                b.coin(sp.main, sp.accent)
                b.wingSmall(-1f, sp.accent)
                b.wingSmall(1f, sp.accent)
            }
            7 -> { // FERROKIT — angular steel kit
                b.prismBody(sp.main)
                b.ear(-0.42f, 0.72f, 0.46f, sp.main)
                b.ear(0.42f, 0.72f, 0.46f, sp.main)
                b.seg(0f, 0.72f, 0f, 0f, 1.15f, 0f, sp.accent)
                b.ring(0f, 1.2f, 0f, 0.07f, 0.07f, Builder.XY, sp.accent)
                b.eyes(sp.accent)
            }
            8 -> { // VOLTLING — living bolt
                b.bolt(sp.main)
                for (k in 0..2) {
                    val a = k * 2.1f
                    b.ring(cos(a) * 0.95f, sin(a) * 0.95f, 0f, 0.08f, 0.08f, Builder.XY, sp.accent)
                }
                b.eyes(sp.accent)
            }
            9 -> { // GUSTRIL — breeze bird
                b.sphere(0f, 0f, 0.58f, sp.main)
                b.wingSwept(-1f, sp.accent)
                b.wingSwept(1f, sp.accent)
                b.beak(sp.accent)
                b.eyes(sp.accent)
            }
            10 -> { // SHADEPAW — the cat
                b.sphere(0f, 0f, 0.66f, sp.main)
                b.ear(-0.42f, 0.6f, 0.44f, sp.main)
                b.ear(0.42f, 0.6f, 0.44f, sp.main)
                b.tail(sp.main)
                b.whiskers(sp.accent)
                b.eyes(sp.accent)
            }
            else -> { // PRISMKIN — rotating crystal
                b.crystal(sp.main, sp.accent)
            }
        }
        val arr = b.data.toFloatArray()
        cache[i] = arr
        return arr
    }

    /** Line-list builder in unit space. */
    class Builder {
        val data = ArrayList<Float>(1024)

        companion object { const val XY = 0; const val XZ = 1; const val YZ = 2 }

        private fun put(x: Float, y: Float, z: Float, c: Int) {
            data.add(x); data.add(y); data.add(z)
            data.add(Color.red(c) / 255f); data.add(Color.green(c) / 255f); data.add(Color.blue(c) / 255f)
        }

        fun seg(x1: Float, y1: Float, z1: Float, x2: Float, y2: Float, z2: Float, c: Int) {
            put(x1, y1, z1, c); put(x2, y2, z2, c)
        }

        fun ring(cx: Float, cy: Float, cz: Float, rx: Float, ry: Float, plane: Int, c: Int, segs: Int = 22) {
            var px = 0f; var py = 0f; var pz = 0f; var first = true
            for (k in 0..segs) {
                val a = (k.toFloat() / segs) * (Math.PI * 2).toFloat()
                val u = cos(a) * rx; val v = sin(a) * ry
                val (x, y, z) = when (plane) {
                    XY -> Triple(cx + u, cy + v, cz)
                    XZ -> Triple(cx + u, cy, cz + v)
                    else -> Triple(cx, cy + u, cz + v)
                }
                if (!first) seg(px, py, pz, x, y, z, c)
                px = x; py = y; pz = z; first = false
            }
        }

        /** Three orthogonal rings read as a sphere in rotation. */
        fun sphere(cx: Float, cy: Float, r: Float, c: Int) {
            ring(cx, cy, 0f, r, r, XY, c)
            ring(cx, cy, 0f, r, r, XZ, c)
            ring(cx, cy, 0f, r * 0.55f, r * 0.55f, XZ, c)   // equator accent
        }

        /** Stack of XZ rings tracing a profile: pairs of (y, radius). */
        fun lathe(profile: FloatArray, c: Int) {
            var k = 0
            var lastY = 0f; var lastR = 0f; var has = false
            while (k + 1 < profile.size) {
                val y = profile[k]; val r = profile[k + 1]
                ring(0f, y, 0f, r, r, XZ, c)
                if (has) {
                    for (q in 0 until 4) {
                        val a = (q / 4f) * (Math.PI * 2).toFloat()
                        seg(cos(a) * lastR, lastY, sin(a) * lastR, cos(a) * r, y, sin(a) * r, c)
                    }
                }
                lastY = y; lastR = r; has = true
                k += 2
            }
        }

        fun eyes(c: Int) {
            ring(-0.24f, 0.1f, 0.62f, 0.09f, 0.11f, XY, c, 10)
            ring(0.24f, 0.1f, 0.62f, 0.09f, 0.11f, XY, c, 10)
        }

        fun ear(x: Float, y: Float, s: Float, c: Int) {
            seg(x - s * 0.5f, y, 0f, x, y + s, 0f, c)
            seg(x, y + s, 0f, x + s * 0.5f, y, 0f, c)
            seg(x - s * 0.5f, y, 0f, x + s * 0.5f, y, 0f, c)
        }

        fun leaf(x: Float, y: Float, s: Float, angleDeg: Float, c: Int) {
            val a = Math.toRadians(angleDeg.toDouble()).toFloat()
            val ux = sin(a); val uy = cos(a)
            val tipX = x + ux * s; val tipY = y + uy * s
            val sideX = -uy * s * 0.4f; val sideY = ux * s * 0.4f
            seg(x, y, 0f, x + sideX + ux * s * 0.5f, y + sideY + uy * s * 0.5f, 0f, c)
            seg(x + sideX + ux * s * 0.5f, y + sideY + uy * s * 0.5f, 0f, tipX, tipY, 0f, c)
            seg(tipX, tipY, 0f, x - sideX + ux * s * 0.5f, y - sideY + uy * s * 0.5f, 0f, c)
            seg(x - sideX + ux * s * 0.5f, y - sideY + uy * s * 0.5f, 0f, x, y, 0f, c)
        }

        fun flame(x: Float, y: Float, s: Float, c: Int) {
            seg(x, y, 0f, x + s * 0.4f, y + s * 0.55f, 0f, c)
            seg(x + s * 0.4f, y + s * 0.55f, 0f, x, y + s * 1.35f, 0f, c)
            seg(x, y + s * 1.35f, 0f, x - s * 0.4f, y + s * 0.55f, 0f, c)
            seg(x - s * 0.4f, y + s * 0.55f, 0f, x, y, 0f, c)
        }

        fun tail(c: Int) {
            seg(0.6f, -0.35f, -0.2f, 0.95f, -0.05f, -0.35f, c)
            seg(0.95f, -0.05f, -0.35f, 0.85f, 0.45f, -0.4f, c)
        }

        fun whiskers(c: Int) {
            seg(-0.2f, -0.12f, 0.62f, -0.75f, -0.02f, 0.45f, c)
            seg(-0.2f, -0.22f, 0.62f, -0.72f, -0.28f, 0.45f, c)
            seg(0.2f, -0.12f, 0.62f, 0.75f, -0.02f, 0.45f, c)
            seg(0.2f, -0.22f, 0.62f, 0.72f, -0.28f, 0.45f, c)
        }

        fun beak(c: Int) {
            seg(-0.14f, -0.08f, 0.56f, 0f, -0.16f, 0.85f, c)
            seg(0.14f, -0.08f, 0.56f, 0f, -0.16f, 0.85f, c)
        }

        fun wing(side: Float, main: Int, accent: Int) {
            val pts = floatArrayOf(
                0.3f, 0.15f, 0f, 1.15f, 0.75f, -0.15f, 1.35f, 0.15f, -0.2f,
                1.05f, -0.35f, -0.15f, 0.3f, -0.2f, 0f
            )
            outline(pts, side, main)
            seg(side * 0.3f, 0f, 0f, side * 1.05f, 0.25f, -0.15f, accent)
        }

        fun wingSmall(side: Float, c: Int) {
            outline(floatArrayOf(0.6f, 0.1f, 0f, 1.1f, 0.45f, -0.1f, 1.2f, 0f, -0.12f, 0.6f, -0.15f, 0f), side, c)
        }

        fun wingSwept(side: Float, c: Int) {
            outline(floatArrayOf(0.35f, 0.1f, 0f, 1.2f, 0.5f, -0.25f, 1.45f, 0.15f, -0.3f, 0.5f, -0.2f, 0f), side, c)
        }

        private fun outline(xyz: FloatArray, sideX: Float, c: Int) {
            var k = 0
            while (k + 5 < xyz.size) {
                seg(xyz[k] * sideX, xyz[k + 1], xyz[k + 2], xyz[k + 3] * sideX, xyz[k + 4], xyz[k + 5], c)
                k += 3
            }
            // close
            seg(xyz[xyz.size - 3] * sideX, xyz[xyz.size - 2], xyz[xyz.size - 1], xyz[0] * sideX, xyz[1], xyz[2], c)
        }

        fun coin(main: Int, accent: Int) {
            ring(0f, 0f, 0.1f, 0.85f, 0.85f, XY, main)
            ring(0f, 0f, -0.1f, 0.85f, 0.85f, XY, main)
            for (q in 0 until 8) {
                val a = (q / 8f) * (Math.PI * 2).toFloat()
                seg(cos(a) * 0.85f, sin(a) * 0.85f, 0.1f, cos(a) * 0.85f, sin(a) * 0.85f, -0.1f, main)
            }
            // star
            var px = 0f; var py = 0f; var first = true
            for (q in 0..10) {
                val r = if (q % 2 == 0) 0.5f else 0.22f
                val a = (q / 10f) * (Math.PI * 2).toFloat() - (Math.PI / 2).toFloat()
                val x = cos(a) * r; val y = sin(a) * r
                if (!first) seg(px, py, 0.1f, x, y, 0.1f, accent)
                px = x; py = y; first = false
            }
        }

        fun prismBody(c: Int) {
            val n = 5
            for (dz in intArrayOf(-1, 1)) {
                var px = 0f; var py = 0f; var first = true
                for (q in 0..n) {
                    val a = (q.toFloat() / n) * (Math.PI * 2).toFloat() - (Math.PI / 2).toFloat()
                    val x = cos(a) * 0.72f; val y = sin(a) * 0.72f
                    if (!first) seg(px, py, dz * 0.3f, x, y, dz * 0.3f, c)
                    px = x; py = y; first = false
                }
            }
            for (q in 0 until n) {
                val a = (q.toFloat() / n) * (Math.PI * 2).toFloat() - (Math.PI / 2).toFloat()
                seg(cos(a) * 0.72f, sin(a) * 0.72f, -0.3f, cos(a) * 0.72f, sin(a) * 0.72f, 0.3f, c)
            }
        }

        fun bolt(c: Int) {
            val p = floatArrayOf(
                -0.2f, 1.05f, 0.45f, 0.25f, 0.08f, 0.2f, 0.38f, -0.85f,
                -0.45f, -0.1f, -0.08f, -0.15f
            )
            for (dz in intArrayOf(-1, 1)) {
                var k = 0
                while (k + 3 < p.size) {
                    seg(p[k], p[k + 1], dz * 0.12f, p[k + 2], p[k + 3], dz * 0.12f, c)
                    k += 2
                }
                seg(p[p.size - 2], p[p.size - 1], dz * 0.12f, p[0], p[1], dz * 0.12f, c)
            }
        }

        fun crystal(main: Int, accent: Int) {
            val n = 6
            for (dz in intArrayOf(-1, 1)) {
                var px = 0f; var py = 0f; var first = true
                for (q in 0..n) {
                    val a = (q.toFloat() / n) * (Math.PI * 2).toFloat()
                    val x = cos(a) * 0.8f; val y = sin(a) * 0.8f
                    if (!first) seg(px, py, dz * 0.3f, x, y, dz * 0.3f, if (q % 2 == 0) main else accent)
                    px = x; py = y; first = false
                }
            }
            for (q in 0 until n) {
                val a = (q.toFloat() / n) * (Math.PI * 2).toFloat()
                val c = if (q % 2 == 0) accent else main
                seg(cos(a) * 0.8f, sin(a) * 0.8f, -0.3f, cos(a) * 0.8f, sin(a) * 0.8f, 0.3f, c)
                seg(cos(a) * 0.8f, sin(a) * 0.8f, 0.3f, 0f, 1.15f, 0f, c)
                seg(cos(a) * 0.8f, sin(a) * 0.8f, -0.3f, 0f, -1.15f, 0f, c)
            }
            eyes(accent)
        }
    }
}
