package com.specsafari.phone.den

import android.graphics.Color
import com.specsafari.shared.Species

/**
 * Solid 3D forms for all twenty-nine species — round bodies, glossy eyes,
 * emissive accents — assembled from spheres, cones, and fins. Every creature
 * stands with feet at y=0 facing +Z, about one unit tall, so the den can
 * move them around as rigid charms that bob, hop, and squash.
 */
object CreatureForms {

    private fun eyes(b: MeshBuilder, y: Float, z: Float, spread: Float = 0.16f, r: Float = 0.085f) {
        val white = Color.WHITE; val dark = Color.rgb(18, 22, 30)
        for (sx in intArrayOf(-1, 1)) {
            b.ellipsoid(sx * spread, y, z, r, r * 1.15f, r * 0.7f, white, 0.25f, 6, 8)
            b.ellipsoid(sx * spread, y, z + r * 0.55f, r * 0.5f, r * 0.6f, r * 0.4f, dark, 0f, 5, 6)
            b.ellipsoid(sx * spread + r * 0.18f, y + r * 0.25f, z + r * 0.8f,
                r * 0.16f, r * 0.16f, r * 0.12f, white, 0.9f, 4, 5)
        }
    }

    private fun earCone(b: MeshBuilder, x: Float, y: Float, color: Int, tilt: Float = 0.25f,
                        r: Float = 0.11f, h: Float = 0.3f) {
        b.cone(x, y, 0f, r, x * tilt, 1f, 0f, h, color)
    }

    private fun tallEars(b: MeshBuilder, color: Int, inner: Int, y: Float) {
        for (sx in intArrayOf(-1, 1)) {
            b.ellipsoid(sx * 0.16f, y + 0.3f, 0f, 0.085f, 0.34f, 0.06f, color)
            b.ellipsoid(sx * 0.16f, y + 0.3f, 0.035f, 0.045f, 0.24f, 0.045f, inner, 0.35f)
        }
    }

    /** One species, one charm. Roughly centered, ~1 unit tall. */
    fun build(species: Int): Mesh {
        val sp = Species.ALL[species]
        val m = sp.main; val a = sp.accent
        val b = MeshBuilder()
        when (species) {
            0 -> { // LEAFLING — sprout ball, one big leaf
                b.ellipsoid(0f, 0.4f, 0f, 0.4f, 0.38f, 0.38f, m)
                b.cone(0f, 0.75f, 0f, 0.05f, 0.2f, 1f, 0f, 0.22f, m)
                b.ellipsoid(0.12f, 1.05f, 0f, 0.2f, 0.09f, 0.12f, a, 0.3f)
                eyes(b, 0.48f, 0.33f)
            }
            1 -> { // THORNPUP — spiky pup
                b.ellipsoid(0f, 0.38f, 0f, 0.42f, 0.36f, 0.44f, m)
                for (i in 0..4) b.cone(-0.25f + i * 0.125f, 0.62f, -0.1f, 0.06f,
                    -0.2f + i * 0.1f, 1f, -0.3f, 0.22f, a, 0.3f)
                earCone(b, -0.2f, 0.66f, m); earCone(b, 0.2f, 0.66f, m)
                eyes(b, 0.45f, 0.38f)
            }
            2 -> { // PUDDLIM — droplet
                b.ellipsoid(0f, 0.42f, 0f, 0.38f, 0.42f, 0.38f, m, 0.15f)
                b.cone(0f, 0.78f, 0f, 0.12f, 0f, 1f, 0f, 0.28f, m, 0.2f)
                b.ellipsoid(-0.15f, 0.7f, 0.18f, 0.08f, 0.08f, 0.06f, a, 0.5f, 5, 6)
                eyes(b, 0.48f, 0.34f)
            }
            3 -> { // EMBERLING — coal with flame crown
                b.ellipsoid(0f, 0.36f, 0f, 0.4f, 0.36f, 0.4f, m)
                b.cone(0f, 0.66f, 0f, 0.14f, 0f, 1f, 0f, 0.4f, a, 0.85f)
                b.cone(-0.14f, 0.62f, 0f, 0.08f, -0.4f, 1f, 0f, 0.24f, m, 0.7f)
                b.cone(0.14f, 0.62f, 0f, 0.08f, 0.4f, 1f, 0f, 0.24f, m, 0.7f)
                eyes(b, 0.42f, 0.36f)
            }
            4 -> { // BOOKWYRM — dragonlet with specs
                b.ellipsoid(0f, 0.38f, 0f, 0.36f, 0.38f, 0.42f, m)
                for (sx in intArrayOf(-1, 1)) {
                    b.ellipsoid(sx * 0.42f, 0.42f, -0.05f, 0.16f, 0.22f, 0.05f, a, 0.2f)
                    b.ellipsoid(sx * 0.16f, 0.5f, 0.36f, 0.12f, 0.12f, 0.03f, Color.rgb(240, 240, 255), 0.4f, 5, 8)
                }
                b.cone(0f, 0.2f, -0.38f, 0.1f, 0f, 0.3f, -1f, 0.4f, m)
                eyes(b, 0.5f, 0.34f, 0.16f, 0.06f)
            }
            5 -> { // LUXMOTH — glowing moth
                b.ellipsoid(0f, 0.4f, 0f, 0.22f, 0.34f, 0.24f, m, 0.4f)
                for (sx in intArrayOf(-1, 1)) {
                    b.quad(sx * 0.1f, 0.62f, 0f, sx * 0.75f, 0.85f, -0.12f,
                        sx * 0.8f, 0.35f, -0.14f, sx * 0.12f, 0.28f, 0f, a, 0.5f, true)
                    b.cone(sx * 0.08f, 0.68f, 0.1f, 0.02f, sx * 0.3f, 1f, 0.3f, 0.22f, a, 0.6f)
                }
                eyes(b, 0.5f, 0.2f, 0.11f, 0.06f)
            }
            6 -> { // COINIX — winged coin
                b.ellipsoid(0f, 0.42f, 0f, 0.36f, 0.36f, 0.14f, m, 0.35f)
                b.ellipsoid(0f, 0.42f, 0.02f, 0.26f, 0.26f, 0.14f, a, 0.5f)
                for (sx in intArrayOf(-1, 1))
                    b.quad(sx * 0.3f, 0.5f, 0f, sx * 0.66f, 0.72f, -0.06f,
                        sx * 0.62f, 0.3f, -0.06f, sx * 0.32f, 0.3f, 0f, Color.WHITE, 0.25f, true)
                eyes(b, 0.48f, 0.15f, 0.13f, 0.07f)
            }
            7 -> { // FERROKIT — steel fox
                b.ellipsoid(0f, 0.36f, 0f, 0.34f, 0.34f, 0.42f, m)
                earCone(b, -0.18f, 0.62f, a, 0.35f, 0.12f, 0.3f)
                earCone(b, 0.18f, 0.62f, a, 0.35f, 0.12f, 0.3f)
                b.cone(0f, 0.3f, -0.4f, 0.09f, 0f, 0.5f, -1f, 0.42f, a, 0.25f)
                b.box(0f, 0.36f, 0.4f, 0.1f, 0.06f, 0.06f, a, 0.2f)
                eyes(b, 0.46f, 0.36f)
            }
            8 -> { // VOLTLING — spark imp
                b.ellipsoid(0f, 0.4f, 0f, 0.3f, 0.34f, 0.3f, m, 0.5f)
                b.cone(0f, 0.68f, 0f, 0.07f, 0.5f, 1f, 0f, 0.3f, a, 0.7f)
                b.cone(0.14f, 0.86f, 0f, 0.05f, -0.6f, 1f, 0f, 0.2f, a, 0.7f)
                for (sx in intArrayOf(-1, 1))
                    b.cone(sx * 0.28f, 0.4f, 0f, 0.05f, sx * 1f, 0.2f, 0f, 0.22f, a, 0.8f)
                eyes(b, 0.46f, 0.27f)
            }
            9 -> { // GUSTRIL — wind bird
                b.ellipsoid(0f, 0.42f, 0f, 0.3f, 0.32f, 0.38f, m)
                for (sx in intArrayOf(-1, 1))
                    b.quad(sx * 0.24f, 0.55f, 0f, sx * 0.85f, 0.75f, -0.15f,
                        sx * 0.75f, 0.4f, -0.18f, sx * 0.28f, 0.34f, 0f, a, 0.3f, true)
                b.cone(0f, 0.46f, 0.36f, 0.07f, 0f, -0.1f, 1f, 0.2f, Color.rgb(255, 210, 120), 0.3f)
                eyes(b, 0.54f, 0.3f)
            }
            10 -> { // SHADEPAW — sleek cat
                b.ellipsoid(0f, 0.36f, 0f, 0.32f, 0.34f, 0.4f, m)
                earCone(b, -0.17f, 0.62f, m, 0.3f, 0.11f, 0.26f)
                earCone(b, 0.17f, 0.62f, m, 0.3f, 0.11f, 0.26f)
                b.cone(0f, 0.3f, -0.36f, 0.06f, 0.3f, 0.9f, -0.8f, 0.5f, m)
                b.ellipsoid(0f, 0.32f, 0.38f, 0.08f, 0.05f, 0.05f, a, 0.5f, 5, 6)
                eyes(b, 0.46f, 0.33f, 0.15f, 0.075f)
            }
            11 -> { // PRISMKIN — crystal being
                b.cone(0f, 0.5f, 0f, 0.3f, 0f, 1f, 0f, 0.55f, m, 0.5f, 6)
                b.cone(0f, 0.5f, 0f, 0.3f, 0f, -1f, 0f, 0.5f, a, 0.45f, 6)
                for (i in 0..2) {
                    val ang = i * 2.094f
                    b.cone(kotlin.math.cos(ang) * 0.4f, 0.55f, kotlin.math.sin(ang) * 0.4f,
                        0.05f, kotlin.math.cos(ang), 0.4f, kotlin.math.sin(ang), 0.22f, a, 0.8f, 5)
                }
                eyes(b, 0.56f, 0.26f, 0.12f, 0.06f)
            }
            12 -> { // WISPLET — flame ghost
                b.ellipsoid(0f, 0.45f, 0f, 0.3f, 0.36f, 0.3f, m, 0.6f)
                b.cone(0f, 0.75f, 0f, 0.13f, 0.2f, 1f, 0f, 0.35f, a, 0.85f)
                b.ellipsoid(0f, 0.12f, -0.1f, 0.16f, 0.1f, 0.16f, m, 0.4f)
                eyes(b, 0.5f, 0.26f, 0.12f, 0.06f)
            }
            13 -> { // MOONHARE — moon rabbit
                b.ellipsoid(0f, 0.36f, 0f, 0.3f, 0.34f, 0.34f, m)
                tallEars(b, m, a, 0.62f)
                b.ellipsoid(0f, 0.28f, 0.3f, 0.1f, 0.06f, 0.05f, a, 0.5f, 5, 6)
                b.ellipsoid(0f, 0.2f, -0.32f, 0.09f, 0.09f, 0.09f, Color.WHITE, 0.3f, 5, 6)
                eyes(b, 0.45f, 0.28f)
            }
            14 -> { // KELPLING — water foal
                b.ellipsoid(0f, 0.4f, 0f, 0.28f, 0.34f, 0.44f, m)
                b.ellipsoid(0f, 0.6f, 0.3f, 0.16f, 0.16f, 0.24f, m)
                for (i in 0..3) b.cone(0f, 0.68f - i * 0.12f, 0.12f - i * 0.14f, 0.06f,
                    0f, 1f, -0.4f, 0.2f, a, 0.5f)
                eyes(b, 0.66f, 0.48f, 0.11f, 0.06f)
            }
            15 -> { // PUCKLE — hooded brownie
                b.ellipsoid(0f, 0.32f, 0f, 0.32f, 0.32f, 0.3f, m)
                b.cone(0f, 0.5f, 0f, 0.36f, 0f, 1f, 0f, 0.55f, a)
                b.ellipsoid(0f, 1.02f, 0f, 0.06f, 0.06f, 0.06f, m, 0.5f, 5, 6)
                eyes(b, 0.4f, 0.28f)
            }
            16 -> { // DREAMBAKU — trunked dozer
                b.ellipsoid(0f, 0.36f, 0f, 0.38f, 0.34f, 0.4f, m)
                b.cone(0f, 0.42f, 0.36f, 0.09f, 0.15f, -0.5f, 1f, 0.4f, a)
                for (sx in intArrayOf(-1, 1))
                    b.ellipsoid(sx * 0.34f, 0.58f, -0.02f, 0.13f, 0.16f, 0.05f, a)
                // Closed eyes: thin dark slivers.
                for (sx in intArrayOf(-1, 1))
                    b.ellipsoid(sx * 0.16f, 0.48f, 0.36f, 0.09f, 0.015f, 0.03f, Color.rgb(20, 24, 40), 0f, 4, 6)
            }
            17 -> { // GLIMMERFOX — two-tailed kitsune
                b.ellipsoid(0f, 0.36f, 0f, 0.3f, 0.32f, 0.4f, m)
                earCone(b, -0.16f, 0.6f, m, 0.4f, 0.12f, 0.32f)
                earCone(b, 0.16f, 0.6f, m, 0.4f, 0.12f, 0.32f)
                for (sx in intArrayOf(-1, 1)) {
                    b.cone(sx * 0.14f, 0.3f, -0.34f, 0.08f, sx * 0.6f, 0.7f, -0.8f, 0.5f, m)
                    b.ellipsoid(sx * 0.42f, 0.66f, -0.7f, 0.08f, 0.08f, 0.08f, a, 0.8f, 5, 6)
                }
                eyes(b, 0.46f, 0.34f)
            }
            18 -> { // GEMBACK — gem carrier
                b.ellipsoid(0f, 0.34f, 0f, 0.36f, 0.3f, 0.4f, m)
                b.cone(0f, 0.55f, -0.05f, 0.16f, 0f, 1f, 0f, 0.32f, a, 0.85f, 5)
                b.cone(0f, 0.55f, -0.05f, 0.16f, 0f, -1f, 0f, 0.1f, a, 0.6f, 5)
                earCone(b, -0.18f, 0.55f, m, 0.3f, 0.09f, 0.2f)
                earCone(b, 0.18f, 0.55f, m, 0.3f, 0.09f, 0.2f)
                eyes(b, 0.42f, 0.36f)
            }
            19 -> { // CLAYWARD — little golem
                b.box(0f, 0.4f, 0f, 0.3f, 0.36f, 0.24f, m)
                b.box(0f, 0.85f, 0f, 0.2f, 0.14f, 0.18f, m)
                for (sx in intArrayOf(-1, 1))
                    b.box(sx * 0.4f, 0.36f, 0f, 0.09f, 0.24f, 0.12f, a)
                b.box(0f, 0.98f, 0.1f, 0.14f, 0.02f, 0.1f, a, 0.4f)
                eyes(b, 0.85f, 0.18f, 0.1f, 0.05f)
            }
            20 -> { // SKYDRUM — storm fledgling
                b.ellipsoid(0f, 0.4f, 0f, 0.36f, 0.36f, 0.36f, m)
                for (sx in intArrayOf(-1, 1))
                    b.quad(sx * 0.28f, 0.6f, 0f, sx * 0.9f, 0.8f, -0.1f,
                        sx * 0.8f, 0.35f, -0.14f, sx * 0.3f, 0.32f, 0f, m, 0.15f, true)
                b.cone(0f, 0.44f, 0.34f, 0.07f, 0f, -0.15f, 1f, 0.2f, a, 0.4f)
                b.cone(-0.05f, 0.32f, 0.36f, 0.045f, 0.5f, -1f, 0.3f, 0.2f, a, 0.9f)
                eyes(b, 0.52f, 0.3f)
            }
            21 -> { // PYREBIRD — young phoenix
                b.ellipsoid(0f, 0.42f, 0f, 0.3f, 0.34f, 0.34f, m, 0.25f)
                b.cone(0f, 0.72f, 0f, 0.1f, 0f, 1f, 0f, 0.34f, a, 0.9f)
                b.cone(-0.1f, 0.7f, 0f, 0.06f, -0.5f, 1f, 0f, 0.22f, a, 0.8f)
                b.cone(0.1f, 0.7f, 0f, 0.06f, 0.5f, 1f, 0f, 0.22f, a, 0.8f)
                for (sx in intArrayOf(-1, 1))
                    b.quad(sx * 0.24f, 0.55f, 0f, sx * 0.7f, 0.7f, -0.1f,
                        sx * 0.66f, 0.35f, -0.12f, sx * 0.26f, 0.32f, 0f, a, 0.55f, true)
                b.cone(0f, 0.24f, -0.3f, 0.08f, 0f, -0.4f, -1f, 0.35f, a, 0.7f)
                b.cone(0f, 0.46f, 0.3f, 0.06f, 0f, -0.1f, 1f, 0.18f, Color.rgb(255, 230, 140), 0.5f)
                eyes(b, 0.52f, 0.26f)
            }
            22 -> { // HORNHOP — jackalope
                b.ellipsoid(0f, 0.34f, 0f, 0.3f, 0.32f, 0.36f, m)
                tallEars(b, m, a, 0.58f)
                for (sx in intArrayOf(-1, 1)) {
                    b.cone(sx * 0.1f, 0.6f, -0.08f, 0.035f, sx * 0.7f, 1f, -0.2f, 0.3f, a, 0.4f, 5)
                    b.cone(sx * 0.24f, 0.78f, -0.12f, 0.025f, sx * 0.5f, 1f, 0f, 0.16f, a, 0.4f, 5)
                }
                eyes(b, 0.42f, 0.3f)
            }
            23 -> { // SANDSHIFT — djinn on a whirl
                b.cone(0f, 0f, 0f, 0.28f, 0f, 1f, 0f, 0.45f, a, 0.35f)
                b.ellipsoid(0f, 0.62f, 0f, 0.3f, 0.3f, 0.28f, m, 0.2f)
                b.ellipsoid(0f, 0.95f, 0f, 0.12f, 0.05f, 0.12f, a, 0.6f)
                for (sx in intArrayOf(-1, 1))
                    b.ellipsoid(sx * 0.36f, 0.6f, 0.05f, 0.09f, 0.06f, 0.06f, m, 0.2f, 5, 6)
                eyes(b, 0.68f, 0.24f)
            }
            24 -> { // ZEPHYRET — the ground-skimmer (flies low; renderer lifts it)
                b.ellipsoid(0f, 0.3f, 0f, 0.26f, 0.22f, 0.4f, m)
                for (sx in intArrayOf(-1, 1)) {
                    b.quad(sx * 0.2f, 0.4f, 0f, sx * 0.95f, 0.55f, -0.25f,
                        sx * 0.85f, 0.3f, -0.4f, sx * 0.22f, 0.25f, -0.1f, m, 0.3f, true)
                    b.quad(sx * 0.08f, 0.28f, -0.35f, sx * 0.5f, 0.32f, -0.95f,
                        sx * 0.4f, 0.22f, -1.0f, sx * 0.05f, 0.2f, -0.4f, a, 0.45f, true)
                }
                b.cone(0f, 0.3f, 0.4f, 0.06f, 0f, 0f, 1f, 0.16f, a, 0.4f, 5)
                eyes(b, 0.38f, 0.36f, 0.12f, 0.06f)
            }
            25 -> { // NIXLET — the swimmer (renderer sinks it to the waterline)
                b.ellipsoid(0f, 0.32f, 0f, 0.3f, 0.28f, 0.32f, m, 0.15f)
                b.quad(0.05f, 0.3f, -0.28f, 0.15f, 0.75f, -0.75f,
                    0.05f, 0.35f, -0.95f, -0.05f, 0.25f, -0.4f, a, 0.5f, true)
                b.ellipsoid(0f, 0.62f, 0.1f, 0.09f, 0.12f, 0.09f, a, 0.5f, 5, 6)
                ring(b)
                eyes(b, 0.4f, 0.28f)
            }
            26 -> { // MOLDEWARP — the burrower (renderer swaps it for a mound underground)
                b.ellipsoid(0f, 0.3f, 0f, 0.32f, 0.3f, 0.38f, m)
                // Star nose: a ring of tiny feeler cones.
                for (i in 0 until 22) {
                    val ang = (i / 22f) * 6.283f
                    b.cone(0f, 0.28f, 0.36f, 0.012f,
                        kotlin.math.cos(ang) * 0.5f, kotlin.math.sin(ang) * 0.5f, 1f, 0.14f, a, 0.5f, 4)
                }
                b.ellipsoid(0f, 0.28f, 0.4f, 0.06f, 0.06f, 0.05f, a, 0.6f, 4, 6)
                for (sx in intArrayOf(-1, 1))
                    b.ellipsoid(sx * 0.34f, 0.16f, 0.15f, 0.14f, 0.08f, 0.18f, a, 0.2f, 5, 6)
                eyes(b, 0.44f, 0.3f, 0.13f, 0.055f)
            }
            27 -> { // SYLVARCH — glade archivist, crown and cache-working forepaws
                b.ellipsoid(0f, 0.42f, -0.08f, 0.34f, 0.30f, 0.48f, m, 0.15f)
                b.ellipsoid(0f, 0.67f, 0.35f, 0.27f, 0.25f, 0.28f, m, 0.22f)
                for (sx in intArrayOf(-1, 1)) {
                    // Four feet; the front pair is broad enough to manipulate caches.
                    b.ellipsoid(sx * 0.25f, 0.16f, 0.20f, 0.10f, 0.18f, 0.12f, a, 0.12f, 5, 6)
                    b.ellipsoid(sx * 0.24f, 0.16f, -0.30f, 0.10f, 0.18f, 0.12f, m, 0.08f, 5, 6)
                    // Branching polarized-light crown.
                    b.cone(sx * 0.14f, 0.83f, 0.30f, 0.025f, sx * 0.28f, 1f, -0.08f, 0.34f, a, 0.55f, 5)
                    b.cone(sx * 0.21f, 1.02f, 0.28f, 0.018f, sx * 0.55f, 0.55f, 0.05f, 0.18f, a, 0.60f, 5)
                    b.cone(sx * 0.20f, 1.01f, 0.28f, 0.016f, -sx * 0.15f, 0.7f, -0.05f, 0.15f, a, 0.45f, 5)
                    b.quad(sx * 0.18f, 0.50f, -0.42f, sx * 0.42f, 0.62f, -0.78f,
                        sx * 0.54f, 0.42f, -0.82f, sx * 0.20f, 0.34f, -0.45f, a, 0.28f, true)
                }
                b.quad(-0.18f, 0.50f, -0.42f, 0f, 0.70f, -0.90f,
                    0.18f, 0.50f, -0.42f, 0f, 0.30f, -0.88f, m, 0.20f, true)
                eyes(b, 0.73f, 0.46f, 0.14f, 0.055f)
            }
            28 -> { // MISTCROWN — arboreal hexapod with sensory crown and moss mantle
                b.ellipsoid(0f, 0.55f, -0.05f, 0.31f, 0.36f, 0.42f, m, 0.25f)
                b.ellipsoid(0f, 0.78f, 0.34f, 0.25f, 0.24f, 0.27f, m, 0.30f)
                for (pair in 0..2) for (sx in intArrayOf(-1, 1)) {
                    val zz = 0.24f - pair * 0.23f
                    b.cone(sx * 0.18f, 0.50f, zz, 0.045f,
                        sx * (0.78f + pair * 0.08f), -0.42f, (pair - 1) * 0.18f,
                        0.34f, a, 0.35f, 5)
                    b.ellipsoid(sx * (0.43f + pair * 0.03f), 0.34f, zz + (pair - 1) * 0.05f,
                        0.11f, 0.055f, 0.14f, a, 0.30f, 4, 6)
                }
                // Prehensile balancing tail and crown tendrils that sample fog chemistry.
                b.cone(0f, 0.55f, -0.38f, 0.075f, 0.35f, 0.15f, -1f, 0.62f, m, 0.35f, 7)
                for (k in 0 until 7) {
                    val ang = (k / 7f) * 6.283f
                    b.cone(0f, 0.96f, 0.32f, 0.018f,
                        kotlin.math.cos(ang) * 0.42f, 0.9f,
                        kotlin.math.sin(ang) * 0.35f, 0.24f + (k % 2) * 0.06f, a, 0.62f, 5)
                }
                for (k in 0..4) b.ellipsoid(-0.2f + k * 0.1f, 0.87f + (k % 2) * 0.06f, -0.02f,
                    0.09f, 0.045f, 0.10f, a, 0.34f, 4, 6)
                eyes(b, 0.84f, 0.45f, 0.14f, 0.052f)
            }
            else -> error("No 3D morphology for species index $species")
        }
        return b.bake()
    }

    /** Nixlet's personal ripple: two flattened rings at the waterline. */
    private fun ring(b: MeshBuilder) {
        b.ellipsoid(0f, 0.08f, 0f, 0.5f, 0.02f, 0.5f, android.graphics.Color.rgb(180, 255, 220), 0.5f, 3, 12)
        b.ellipsoid(0f, 0.05f, 0f, 0.68f, 0.015f, 0.68f, android.graphics.Color.rgb(140, 230, 255), 0.35f, 3, 12)
    }

    /** The moving molehill a burrower travels as. */
    fun mound(): Mesh {
        val b = MeshBuilder()
        b.cone(0f, 0f, 0f, 0.42f, 0f, 1f, 0f, 0.34f, android.graphics.Color.rgb(52, 38, 34), 0f, 9)
        b.ellipsoid(0f, 0.3f, 0f, 0.1f, 0.06f, 0.1f, android.graphics.Color.rgb(80, 58, 48), 0.1f, 4, 6)
        return b.bake()
    }

    /** Soft dark oval under every creature so they sit ON the ground. */
    fun shadow(): Mesh {
        val b = MeshBuilder()
        b.ellipsoid(0f, 0.012f, 0f, 0.42f, 0.012f, 0.34f, Color.rgb(4, 6, 10), 0f, 3, 10)
        return b.bake()
    }
}
