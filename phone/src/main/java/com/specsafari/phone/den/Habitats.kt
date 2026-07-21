package com.specsafari.phone.den

import android.graphics.Color
import kotlin.math.cos
import kotlin.math.sin

/**
 * ONE continuous world, walked end to end — now a transect of all nine of
 * Earth's terrestrial biomes (Whittaker's classification), cold pole to hot,
 * wet to dry: TUNDRA · TAIGA (boreal forest) · GLADE (temperate seasonal
 * forest) · MISTWOOD (temperate rain forest) · JUNGLE (tropical rain forest) ·
 * MONSOON (tropical seasonal forest) · DUNES (subtropical desert) · SAGE
 * (temperate desert) · SCRUB (woodland / shrubland). Sky, fog, and ground
 * blend at every seam; the creatures keep their nests and their microhabitats
 * follow the climate — water and bloom where it is wet, burrow and stone
 * where it is dry.
 */
class Zone(val kind: String, val x: Float, val z: Float, val r: Float)

/** A localized weather cell — a spot of falling snow, rain, or blizzard,
 *  not a whole biome. kind: 0 snow flurry · 1 rain · 2 blizzard. */
class Patch(val x: Float, val z: Float, val r: Float, val kind: Int)

/** A wild food source the creatures forage at, matched to its climate.
 *  kind: 0 berries · 1 fruit tree · 2 lichen · 3 mushrooms · 4 cactus fruit ·
 *  5 seed grass · 6 aquatic plants. */
class Food(val x: Float, val z: Float, val kind: Int)

class Biome(
    val name: String,
    val x0: Float, val x1: Float,
    val skyTop: Int, val skyBot: Int, val fog: Int, val ground: Int,
    val fireflyColor: Int,
    val loved: Set<String>,
    val decor: (MeshBuilder) -> Unit
) {
    val center get() = (x0 + x1) / 2f
}

class ItemDef(
    val id: String,
    val name: String,
    val price: Int,
    val treat: Boolean,
    val bondPts: Int,
    val loved: Set<String>,
    val cardColor: Int,
    val build: (MeshBuilder, Float, Float) -> Unit
)

object Habitats {
    const val WORLD_W = 108f       // nine biomes, ~12 units each; a proper expedition
    const val BIOME_W = 12f

    private fun tree(b: MeshBuilder, x: Float, z: Float, s: Float, leaf: Int, trunk: Int) {
        b.box(x, s * 0.35f, z, s * 0.08f, s * 0.35f, s * 0.08f, trunk)
        b.cone(x, s * 0.55f, z, s * 0.5f, 0f, 1f, 0f, s * 0.8f, leaf)
        b.cone(x, s * 1.05f, z, s * 0.34f, 0f, 1f, 0f, s * 0.6f, leaf, 0.12f)
    }

    /** A tall narrow spire conifer — the signature of taiga and mistwood. */
    private fun conifer(b: MeshBuilder, x: Float, z: Float, s: Float, leaf: Int, trunk: Int) {
        b.box(x, s * 0.3f, z, s * 0.07f, s * 0.3f, s * 0.07f, trunk)
        b.cone(x, s * 0.45f, z, s * 0.42f, 0f, 1f, 0f, s * 0.7f, leaf)
        b.cone(x, s * 0.95f, z, s * 0.32f, 0f, 1f, 0f, s * 0.65f, leaf)
        b.cone(x, s * 1.45f, z, s * 0.2f, 0f, 1f, 0f, s * 0.55f, leaf, 0.1f)
    }

    /** An emergent rain-forest giant: tall bare bole, buttress roots, and a
     *  wide FLAT crown spread above the canopy — nothing like a conifer. */
    private fun kapok(b: MeshBuilder, x: Float, z: Float, s: Float, leaf: Int, trunk: Int) {
        b.box(x, s * 0.75f, z, s * 0.055f, s * 0.75f, s * 0.055f, trunk)
        for (i in 0..2) {   // flaring buttress roots
            val a = i * 2.094f + x   // vary the flare per tree
            b.cone(x + kotlin.math.cos(a) * s * 0.1f, 0f, z + kotlin.math.sin(a) * s * 0.1f,
                s * 0.09f, kotlin.math.cos(a) * 0.9f, 0.9f, kotlin.math.sin(a) * 0.9f,
                s * 0.3f, trunk)
        }
        b.ellipsoid(x, s * 1.55f, z, s * 0.95f, s * 0.20f, s * 0.85f, leaf, 0f, 5, 9)
        b.ellipsoid(x + s * 0.3f, s * 1.68f, z - s * 0.15f, s * 0.5f, s * 0.14f, s * 0.45f, leaf, 0f, 4, 7)
        b.ellipsoid(x - s * 0.4f, s * 1.62f, z + s * 0.2f, s * 0.4f, s * 0.12f, s * 0.38f, leaf, 0f, 4, 7)
        // Lianas trailing off the crown's rim.
        b.box(x + s * 0.55f, s * 1.05f, z + s * 0.2f, s * 0.02f, s * 0.5f, s * 0.02f, Color.rgb(46, 104, 52))
        b.box(x - s * 0.6f, s * 1.1f, z - s * 0.15f, s * 0.02f, s * 0.45f, s * 0.02f, Color.rgb(40, 92, 48))
    }

    /** A leaning palm: curved stacked trunk and a burst of drooping fronds. */
    private fun palm(b: MeshBuilder, x: Float, z: Float, s: Float, frond: Int, trunk: Int) {
        var cx = x
        for (k in 0..3) {   // the lean
            b.box(cx, s * (0.18f + k * 0.3f), z, s * 0.05f, s * 0.17f, s * 0.05f, trunk)
            cx += s * 0.07f
        }
        val topX = cx; val topY = s * 1.32f
        for (k in 0 until 6) {   // fronds radiate and droop
            val a = k * 1.047f + 0.4f
            val dx = kotlin.math.cos(a); val dz = kotlin.math.sin(a)
            b.quad(topX, topY, z,
                topX + dx * s * 0.75f, topY + s * 0.22f, z + dz * s * 0.75f,
                topX + dx * s * 1.05f, topY - s * 0.28f, z + dz * s * 1.05f,
                topX + dx * s * 0.4f, topY - s * 0.04f, z + dz * s * 0.4f,
                frond, 0.06f, true)
        }
        b.ellipsoid(topX, topY - s * 0.06f, z, s * 0.10f, s * 0.08f, s * 0.10f,
            Color.rgb(120, 84, 40), 0f, 4, 6)   // coconut cluster
    }

    /** A rounded broadleaf crown — temperate and monsoon forests. */
    private fun broadleaf(b: MeshBuilder, x: Float, z: Float, s: Float, leaf: Int, trunk: Int) {
        b.box(x, s * 0.4f, z, s * 0.1f, s * 0.4f, s * 0.1f, trunk)
        b.ellipsoid(x, s * 1.0f, z, s * 0.7f, s * 0.55f, s * 0.7f, leaf, 0f, 6, 8)
        b.ellipsoid(x - s * 0.35f, s * 0.85f, z + s * 0.1f, s * 0.4f, s * 0.35f, s * 0.4f, leaf, 0f, 5, 7)
    }

    /** A pillowy mound of snow — the tundra floor. */
    private fun snowMound(b: MeshBuilder, x: Float, z: Float, s: Float) {
        b.ellipsoid(x, s * 0.18f, z, s, s * 0.28f, s * 0.8f, Color.rgb(224, 232, 244), 0.1f, 5, 9)
    }

    /** A columnar cactus with an arm — the subtropical desert. */
    private fun cactus(b: MeshBuilder, x: Float, z: Float, s: Float) {
        val c = Color.rgb(70, 130, 80)
        b.box(x, s * 0.7f, z, s * 0.16f, s * 0.7f, s * 0.16f, c)
        b.ellipsoid(x, s * 1.42f, z, s * 0.17f, s * 0.2f, s * 0.17f, c, 0f, 5, 7)
        b.box(x + s * 0.28f, s * 0.85f, z, s * 0.09f, s * 0.09f, s * 0.09f, c)
        b.box(x + s * 0.35f, s * 1.05f, z, s * 0.08f, s * 0.22f, s * 0.08f, c)
    }

    /** A low grey-green sage bush — the cold desert's iconic shrub. */
    private fun sageBush(b: MeshBuilder, x: Float, z: Float, s: Float) {
        val c = Color.rgb(120, 130, 96)
        b.ellipsoid(x, s * 0.28f, z, s * 0.5f, s * 0.3f, s * 0.45f, c, 0f, 5, 7)
        b.ellipsoid(x - s * 0.28f, s * 0.22f, z + s * 0.15f, s * 0.3f, s * 0.22f, s * 0.28f, c, 0f, 4, 6)
    }

    /** A gnarled dry shrub — the Mediterranean chaparral. */
    private fun scrubBush(b: MeshBuilder, x: Float, z: Float, s: Float) {
        b.box(x, s * 0.22f, z, s * 0.06f, s * 0.22f, s * 0.06f, Color.rgb(70, 52, 36))
        b.ellipsoid(x, s * 0.55f, z, s * 0.44f, s * 0.34f, s * 0.4f, Color.rgb(96, 108, 60), 0f, 5, 7)
    }

    /** Tall dry grass tufts — savanna and monsoon dry-season. */
    private fun dryGrass(b: MeshBuilder, x: Float, z: Float, s: Float, c: Int) {
        for (k in 0..3) {
            val a = k * 1.4f
            b.cone(x + cos(a) * s * 0.2f, 0f, z + sin(a) * s * 0.2f, 0.03f,
                cos(a) * 0.2f, 1f, sin(a) * 0.2f, s * (0.7f + (k % 2) * 0.3f), c, 0.05f, 4)
        }
    }

    private fun rock(b: MeshBuilder, x: Float, z: Float, s: Float, c: Int) {
        b.ellipsoid(x, s * 0.3f, z, s * 0.55f, s * 0.34f, s * 0.45f, c, 0f, 5, 7)
    }

    private fun crystal(b: MeshBuilder, x: Float, z: Float, s: Float, c: Int) {
        b.cone(x, 0f, z, s * 0.22f, 0.15f, 1f, 0f, s, c, 0.7f, 5)
        b.cone(x + s * 0.25f, 0f, z + s * 0.15f, s * 0.13f, -0.2f, 1f, 0.1f, s * 0.55f, c, 0.6f, 5)
    }

    /** One microhabitat's scenery: generic per kind, at absolute coords. */
    fun zoneDecor(b: MeshBuilder, zone: Zone, @Suppress("UNUSED_PARAMETER") biome: Biome) {
        val x = zone.x; val z = zone.z; val r = zone.r
        when (zone.kind) {
            "WATER" -> {
                // The dark bed only; the living surface is rendered dynamically.
                b.ellipsoid(x, 0.04f, z, r, 0.035f, r * 0.75f, Color.rgb(30, 90, 120), 0.2f, 4, 14)
                for (k in 0..5) {
                    val a = k * 1.047f
                    rock(b, x + cos(a) * r * 1.05f, z + sin(a) * r * 0.8f, 0.34f, Color.rgb(66, 84, 96))
                }
                for (k in 0..2)
                    b.cone(x - r * 0.7f + k * r * 0.7f, 0f, z + r * 0.55f, 0.03f,
                        0.1f, 1f, 0f, 0.5f + (k % 2) * 0.2f, Color.rgb(60, 140, 90), 0.1f, 4)
            }
            "BLOOM" -> {
                for (k in 0..10) {
                    val a = k * 0.599f
                    val px = x + cos(a) * r * (0.25f + (k % 4) * 0.22f)
                    val pz = z + sin(a) * r * (0.2f + (k % 3) * 0.2f)
                    b.cone(px, 0f, pz, 0.025f, 0f, 1f, 0f, 0.2f, Color.rgb(50, 120, 70), 0f, 4)
                    b.ellipsoid(px, 0.24f, pz, 0.06f, 0.05f, 0.06f,
                        intArrayOf(Color.rgb(255, 170, 210), Color.rgb(255, 230, 140),
                            Color.rgb(190, 160, 255))[k % 3], 0.6f, 4, 6)
                }
            }
            "THICKET" -> {
                tree(b, x - r * 0.5f, z - 0.2f, 0.75f, Color.rgb(30, 96, 66), Color.rgb(60, 44, 40))
                tree(b, x + r * 0.45f, z + 0.15f, 0.6f, Color.rgb(36, 110, 74), Color.rgb(60, 44, 40))
                b.ellipsoid(x, 0.22f, z + r * 0.4f, 0.5f, 0.24f, 0.35f, Color.rgb(28, 84, 58))
                b.ellipsoid(x - r * 0.15f, 0.18f, z - r * 0.45f, 0.4f, 0.2f, 0.3f, Color.rgb(24, 76, 52))
            }
            "EMBER" -> {
                // Dark vents only; the fire itself burns dynamically.
                for (k in 0..3) {
                    val a = k * 1.57f + 0.4f
                    val px = x + cos(a) * r * 0.5f; val pz = z + sin(a) * r * 0.4f
                    b.cone(px, 0f, pz, 0.12f, 0f, 1f, 0f, 0.22f, Color.rgb(60, 36, 32))
                }
                rock(b, x, z - r * 0.3f, 0.5f, Color.rgb(74, 44, 36))
            }
            "STONE" -> {
                b.box(x, 0.12f, z, r * 0.55f, 0.12f, r * 0.4f, Color.rgb(84, 90, 104))
                b.box(x - r * 0.2f, 0.3f, z + 0.1f, r * 0.28f, 0.07f, r * 0.22f, Color.rgb(100, 106, 120))
                rock(b, x + r * 0.7f, z - 0.2f, 0.4f, Color.rgb(70, 76, 90))
            }
            "VOID" -> {
                b.ellipsoid(x, 0.03f, z, r * 0.8f, 0.025f, r * 0.6f, Color.rgb(16, 10, 30), 0.25f, 4, 12)
                for (k in 0..4) {
                    val a = k * 1.257f
                    b.cone(x + cos(a) * r * 0.85f, 0f, z + sin(a) * r * 0.65f, 0.05f,
                        0f, 1f, 0f, 0.4f + (k % 2) * 0.25f, Color.rgb(60, 40, 100), 0.45f, 4)
                }
            }
            "BURROW" -> {
                for (k in 0..2) {
                    val px = x - r * 0.5f + k * r * 0.5f
                    val pz = z + (k % 2) * 0.5f - 0.25f
                    b.cone(px, 0f, pz, 0.3f, 0f, 1f, 0f, 0.24f, Color.rgb(58, 42, 36), 0f, 8)
                    b.ellipsoid(px, 0.22f, pz, 0.08f, 0.05f, 0.08f, Color.rgb(40, 28, 26), 0f, 4, 6)
                }
            }
            "PERCH" -> {
                b.box(x, 0.5f, z, 0.14f, 0.5f, 0.14f, Color.rgb(66, 52, 46))
                b.box(x, 1.05f, z, r * 0.5f, 0.08f, 0.5f, Color.rgb(88, 70, 58))
                b.ellipsoid(x + r * 0.3f, 1.2f, z, 0.12f, 0.07f, 0.1f, Color.rgb(120, 160, 110), 0.2f, 4, 6)
            }
        }
    }

    val BIOMES = arrayOf(
        // 1 — TUNDRA: frozen, treeless, sparse. Snow floor, ice, bare rock.
        Biome(
            "TUNDRA", 0f, 12f,
            Color.rgb(14, 22, 44), Color.rgb(56, 84, 116), Color.rgb(40, 58, 78),
            Color.rgb(120, 140, 162), Color.rgb(190, 224, 255),
            setOf("STEADFAST", "ALOOF", "DROWSY", "SNUG", "POLITE", "OTHERWORLDLY"),
            { b ->
                for (i in 0..5) snowMound(b, 0.8f + i * 2f, -2.6f - (i % 2) * 1f, 0.8f + (i % 3) * 0.3f)
                for (i in 0..3) rock(b, 2f + i * 2.6f, -1.4f + (i % 2) * 0.6f, 0.5f, Color.rgb(96, 104, 120))
                for (i in 0..3)  // ice shards
                    b.cone(1.5f + i * 2.8f, 0f, -3.2f, 0.14f, 0.1f, 1f, 0f, 0.7f, Color.rgb(180, 214, 240), 0.4f, 4)
            }
        ),
        // 2 — TAIGA (boreal forest): dense spire conifers, cold, still.
        Biome(
            "TAIGA", 12f, 24f,
            Color.rgb(8, 18, 30), Color.rgb(22, 52, 56), Color.rgb(14, 34, 36),
            Color.rgb(26, 52, 46), Color.rgb(150, 255, 210),
            setOf("SERENE", "STEADFAST", "PEDANTIC", "SNUG", "PUNCTUAL", "DROWSY"),
            { b ->
                for (i in 0..7) conifer(b, 12.6f + i * 1.4f, -2.6f - (i % 3) * 0.8f,
                    0.9f + (i % 3) * 0.3f, Color.rgb(24, 76, 58), Color.rgb(54, 40, 34))
                for (i in 0..2) rock(b, 14f + i * 3f, -1f, 0.5f, Color.rgb(60, 70, 74))
            }
        ),
        // 3 — GLADE (temperate seasonal forest): rounded broadleaf, autumn tints.
        Biome(
            "GLADE", 24f, 36f,
            Color.rgb(16, 22, 44), Color.rgb(44, 66, 74), Color.rgb(22, 38, 44),
            Color.rgb(44, 72, 46), Color.rgb(255, 224, 150),
            setOf("DOZY", "ZOOMY", "POLITE", "HELPFUL", "GIGGLY", "BREEZY", "INQUISITIVE"),
            { b ->
                val fall = intArrayOf(Color.rgb(60, 130, 66), Color.rgb(210, 150, 60), Color.rgb(190, 90, 70))
                for (i in 0..5) broadleaf(b, 24.8f + i * 1.9f, -2.6f - (i % 2) * 0.9f,
                    0.95f + (i % 3) * 0.3f, fall[i % 3], Color.rgb(70, 50, 40))
                for (i in 0..6) b.ellipsoid(25f + i * 1.6f, 0.05f, 1.5f + (i % 2) * 0.5f,
                    0.05f, 0.05f, 0.05f, Color.rgb(255, 220, 150), 0.7f, 4, 5)
            }
        ),
        // 4 — MISTWOOD (temperate rain forest): towering mossy conifers, ferns, fog.
        Biome(
            "MISTWOOD", 36f, 48f,
            Color.rgb(12, 26, 30), Color.rgb(42, 68, 62), Color.rgb(34, 56, 52),
            Color.rgb(28, 62, 48), Color.rgb(180, 255, 220),
            setOf("SERENE", "SPLASHY", "GIGGLY", "SLY", "DROWSY", "FLICKERY", "SAGACIOUS"),
            { b ->
                for (i in 0..5) conifer(b, 36.8f + i * 1.9f, -2.8f - (i % 2) * 0.9f,
                    1.3f + (i % 3) * 0.35f, Color.rgb(26, 84, 60), Color.rgb(52, 44, 38))
                for (i in 0..6)  // fern clumps
                    b.ellipsoid(37f + i * 1.6f, 0.18f, 1.3f + (i % 3) * 0.4f,
                        0.4f, 0.2f, 0.3f, Color.rgb(40, 120, 72), 0f, 5, 7)
            }
        ),
        // 5 — JUNGLE (tropical rain forest): tall dense canopy, vines, deep shade.
        Biome(
            "JUNGLE", 48f, 60f,
            Color.rgb(6, 20, 16), Color.rgb(22, 62, 42), Color.rgb(12, 38, 28),
            Color.rgb(20, 66, 36), Color.rgb(150, 255, 170),
            setOf("BOISTEROUS", "ZOOMY", "CLEVER", "BRASH", "GREEDY", "SPLASHY"),
            { b ->
                // A TROPICAL skyline, unmistakably: emergent kapok giants with
                // flat spreading crowns, leaning palms between them — anchors
                // unchanged so the collision circles still hold true.
                for (i in 0..6) {
                    val x = 48.7f + i * 1.6f
                    val z = -2.7f - (i % 3) * 0.7f
                    if (i % 2 == 0)
                        kapok(b, x, z, 1.5f + (i % 3) * 0.35f,
                            Color.rgb(26, 118, 48), Color.rgb(96, 78, 58))
                    else
                        palm(b, x, z, 1.25f + (i % 3) * 0.3f,
                            Color.rgb(44, 152, 62), Color.rgb(110, 86, 52))
                }
                for (i in 0..4) b.ellipsoid(49f + i * 2.2f, 0.2f, 1.4f, 0.5f, 0.14f, 0.4f,
                    Color.rgb(30, 100, 50), 0f, 5, 8)  // broad forest-floor leaves
                for (i in 0..2)   // banana-like understory arcs on the near side
                    b.quad(50f + i * 3.1f, 0.1f, 1.9f, 50.4f + i * 3.1f, 0.9f, 2.1f,
                        50.9f + i * 3.1f, 0.5f, 2.3f, 50.5f + i * 3.1f, 0.05f, 2.0f,
                        Color.rgb(36, 128, 54), 0.05f, true)
            }
        ),
        // 6 — MONSOON (tropical seasonal forest): scattered broadleaf, gold dry grass.
        Biome(
            "MONSOON", 60f, 72f,
            Color.rgb(20, 20, 32), Color.rgb(74, 62, 42), Color.rgb(38, 34, 26),
            Color.rgb(60, 62, 34), Color.rgb(255, 230, 140),
            setOf("DRAMATIC", "RUMBLY", "BREEZY", "GIGGLY", "HELPFUL", "GREEDY"),
            { b ->
                for (i in 0..3) broadleaf(b, 61f + i * 2.8f, -2.6f - (i % 2) * 0.8f, 1.1f,
                    Color.rgb(90, 120, 50), Color.rgb(80, 58, 40))
                for (i in 0..8) dryGrass(b, 60.5f + i * 1.3f, 1.4f + (i % 3) * 0.4f,
                    0.9f, Color.rgb(190, 170, 80))
            }
        ),
        // 7 — DUNES (subtropical desert): sand ridges, cacti, heat.
        Biome(
            "DUNES", 72f, 84f,
            Color.rgb(28, 16, 10), Color.rgb(104, 70, 38), Color.rgb(56, 36, 22),
            Color.rgb(150, 122, 74), Color.rgb(255, 210, 130),
            setOf("COZY", "DRAMATIC", "GREEDY", "PUNCTUAL", "RUMBLY", "BRASH", "FLICKERY"),
            { b ->
                for (i in 0..4)  // dunes
                    b.ellipsoid(73f + i * 2.4f, 0.25f, -2.4f - (i % 2) * 0.8f,
                        1.6f, 0.4f, 1.1f, Color.rgb(168, 138, 84), 0.05f, 6, 10)
                for (i in 0..2) cactus(b, 74f + i * 3.2f, -0.8f + (i % 2) * 0.6f, 0.9f)
                for (i in 0..2) rock(b, 76f + i * 2.6f, 1f, 0.5f, Color.rgb(130, 96, 62))
            }
        ),
        // 8 — SAGE (temperate / cold desert): sagebrush flats, mesas, hardpan.
        Biome(
            "SAGE", 84f, 96f,
            Color.rgb(22, 18, 14), Color.rgb(72, 66, 50), Color.rgb(40, 36, 28),
            Color.rgb(116, 110, 84), Color.rgb(230, 216, 172),
            setOf("ALOOF", "WRY", "STEADFAST", "DROWSY", "SNUG", "BASHFUL"),
            { b ->
                for (i in 0..6) sageBush(b, 84.6f + i * 1.6f, -1.6f + (i % 3) * 0.9f, 0.8f + (i % 2) * 0.3f)
                b.box(93.5f, 0.7f, -2.8f, 1.2f, 0.7f, 0.8f, Color.rgb(120, 92, 66))  // a mesa
                for (i in 0..2) rock(b, 86f + i * 3f, -2.6f, 0.55f, Color.rgb(110, 100, 78))
            }
        ),
        // 9 — SCRUB (woodland / shrubland): fire-adapted chaparral, gnarled shrubs.
        Biome(
            "SCRUB", 96f, WORLD_W,
            Color.rgb(20, 16, 10), Color.rgb(82, 60, 34), Color.rgb(40, 30, 18),
            Color.rgb(84, 74, 42), Color.rgb(255, 206, 128),
            setOf("COZY", "BRASH", "CLEVER", "JITTERY", "WRY", "DRAMATIC", "PUNCTUAL"),
            { b ->
                for (i in 0..7) scrubBush(b, 96.6f + i * 1.4f, -2.2f - (i % 3) * 0.7f, 0.85f + (i % 3) * 0.3f)
                for (i in 0..2) broadleaf(b, 98f + i * 3.2f, -0.8f + (i % 2) * 0.7f, 0.8f,
                    Color.rgb(110, 110, 56), Color.rgb(74, 54, 36))
                for (i in 0..5) dryGrass(b, 97f + i * 1.8f, 1.4f, 0.7f, Color.rgb(200, 172, 90))
            }
        ),
    )

    /** Every microhabitat of the world, placed by climate: water and bloom
     *  where it is wet, burrow and stone where it is dry. All eight kinds are
     *  represented so no species is left without a home. */
    // Zones now spread across the wide depth so the broad world stays full.
    val ZONES = listOf(
        // TUNDRA — a meltpool and frozen ground
        Zone("WATER", 3.4f, -3.5f, 1.6f),
        Zone("STONE", 8.6f, 3.2f, 1.4f),
        // TAIGA — conifer thicket, a bog, a high roost
        Zone("THICKET", 14.6f, -4.5f, 1.6f),
        Zone("WATER", 18.6f, 1.8f, 1.5f),
        Zone("PERCH", 22f, -1.0f, 1.3f),
        // GLADE — bloom, thicket, pond
        Zone("BLOOM", 26.6f, 3.6f, 1.6f),
        Zone("THICKET", 30.4f, -4.0f, 1.6f),
        Zone("WATER", 34f, 0.0f, 1.5f),
        // MISTWOOD — creek, fern thicket, moss bloom, old-growth canopy court
        Zone("WATER", 38.6f, -2.6f, 1.6f),
        Zone("THICKET", 42.4f, 4.2f, 1.6f),
        Zone("BLOOM", 46f, -4.6f, 1.5f),
        Zone("PERCH", 44.2f, 0.8f, 1.35f),
        // JUNGLE — river, dense thicket, deep shade, canopy roost
        Zone("WATER", 50.4f, 2.6f, 1.7f),
        Zone("THICKET", 53.6f, -4.2f, 1.6f),
        Zone("VOID", 56.4f, 5.2f, 1.3f),       // the deep-shade hollow
        Zone("PERCH", 58.6f, -1.6f, 1.3f),     // the canopy
        // MONSOON — bloom, thicket, seasonal pool
        Zone("BLOOM", 62.6f, 4.0f, 1.6f),
        Zone("THICKET", 66.4f, -3.6f, 1.5f),
        Zone("WATER", 70.4f, 0.6f, 1.3f),
        // DUNES — heat vents, sun-warmed stone, a burrow
        Zone("EMBER", 75f, -3.0f, 1.5f),
        Zone("STONE", 78.6f, 3.6f, 1.5f),
        Zone("BURROW", 82.4f, -1.0f, 1.4f),
        // SAGE — burrow warren, hardpan stone, a night hollow
        Zone("BURROW", 87f, 2.6f, 1.4f),
        Zone("STONE", 90.6f, -4.2f, 1.5f),
        Zone("VOID", 94f, 4.6f, 1.3f),         // the moon-shadow
        // SCRUB — thicket, a chaparral burn, bloom
        Zone("THICKET", 99f, -3.2f, 1.5f),
        Zone("EMBER", 102.6f, 2.6f, 1.5f),     // the chaparral fire
        Zone("BLOOM", 106f, -1.0f, 1.4f),
    )

    fun biomeAt(x: Float): Biome {
        for (b in BIOMES) if (x < b.x1) return b
        return BIOMES.last()
    }

    const val SNOW = 0; const val RAIN = 1; const val BLIZZARD = 2

    const val F_BERRY = 0; const val F_FRUIT = 1; const val F_LICHEN = 2
    const val F_MUSHROOM = 3; const val F_CACTUS = 4; const val F_SEED = 5
    const val F_AQUATIC = 6

    /** Wild forage, each matched to its climate: lichen and hardy berries on
     *  the tundra, cones and mushrooms in the forests, fruit in the tropics,
     *  cactus fruit and seed-heads in the drylands. Creatures graze at the
     *  nearest one. */
    val FOODS = listOf(
        Food(3.5f, 2.5f, F_LICHEN), Food(9f, -2f, F_LICHEN),          // TUNDRA
        Food(15f, -1.5f, F_MUSHROOM), Food(21f, 3f, F_BERRY),         // TAIGA
        Food(26f, -2f, F_BERRY), Food(32f, 2.5f, F_MUSHROOM),         // GLADE
        Food(38f, 2f, F_MUSHROOM), Food(45f, -2.5f, F_MUSHROOM),      // MISTWOOD
        Food(43f, 1.8f, F_FRUIT),                                    // mistwood canopy fruit
        Food(51f, -1.5f, F_FRUIT), Food(57f, 3f, F_FRUIT),            // JUNGLE
        Food(63f, -2f, F_FRUIT), Food(69f, 2f, F_SEED),              // MONSOON
        Food(75f, 2f, F_CACTUS), Food(81f, -2f, F_SEED),             // DUNES
        Food(79f, 5f, F_LICHEN),                                     // desert biological crust
        Food(87f, 2.5f, F_SEED), Food(93f, -2.5f, F_BERRY),          // SAGE
        Food(90f, 4.5f, F_LICHEN),                                   // sage hardpan lichen
        Food(99f, -2f, F_BERRY), Food(105f, 2f, F_SEED),             // SCRUB
        // Submerged browse: water specialists never need to pursue land food.
        Food(3.4f, -3.35f, F_AQUATIC), Food(18.6f, 1.9f, F_AQUATIC),
        Food(34f, 0.1f, F_AQUATIC), Food(38.6f, -2.5f, F_AQUATIC),
        Food(50.4f, 2.7f, F_AQUATIC), Food(70.4f, 0.7f, F_AQUATIC),
    )

    fun berryFood(b: MeshBuilder, x: Float, z: Float) {
        b.ellipsoid(x, 0.28f, z, 0.4f, 0.3f, 0.36f, Color.rgb(30, 108, 60))
        b.ellipsoid(x - 0.22f, 0.38f, z + 0.1f, 0.24f, 0.2f, 0.22f, Color.rgb(38, 128, 70))
        for (k in 0..6) b.ellipsoid(x - 0.34f + k * 0.11f, 0.28f + (k % 3) * 0.14f, z + 0.28f,
            0.055f, 0.055f, 0.055f, intArrayOf(Color.rgb(220, 60, 70), Color.rgb(255, 110, 170))[k % 2], 0.5f, 4, 5)
    }

    fun fruitFood(b: MeshBuilder, x: Float, z: Float) {
        b.box(x, 0.35f, z, 0.08f, 0.35f, 0.08f, Color.rgb(70, 50, 38))
        b.ellipsoid(x, 0.9f, z, 0.5f, 0.42f, 0.5f, Color.rgb(38, 118, 54), 0f, 6, 8)
        for (k in 0..4) b.ellipsoid(x - 0.3f + k * 0.15f, 0.62f + (k % 2) * 0.12f, z + 0.36f,
            0.08f, 0.1f, 0.08f, intArrayOf(Color.rgb(255, 160, 60), Color.rgb(255, 210, 80))[k % 2], 0.4f, 5, 6)
    }

    fun lichenFood(b: MeshBuilder, x: Float, z: Float) {
        rock(b, x, z, 0.5f, Color.rgb(96, 104, 118))
        for (k in 0..5) {
            val a = k * 1.05f
            b.ellipsoid(x + cos(a) * 0.35f, 0.28f + (k % 2) * 0.1f, z + sin(a) * 0.32f,
                0.14f, 0.03f, 0.12f, intArrayOf(Color.rgb(150, 190, 140), Color.rgb(190, 200, 150))[k % 2], 0.15f, 3, 6)
        }
    }

    fun mushroomFood(b: MeshBuilder, x: Float, z: Float) {
        for (k in 0..3) {
            val px = x - 0.3f + k * 0.2f; val pz = z + (k % 2) * 0.25f - 0.1f
            val h = 0.16f + (k % 3) * 0.06f
            b.box(px, h * 0.5f, pz, 0.045f, h * 0.5f, 0.045f, Color.rgb(232, 230, 214))
            b.cone(px, h, pz, 0.13f, 0f, -1f, 0f, 0.14f, Color.rgb(200, 60, 60), 0.2f, 6)
            b.cone(px, h, pz, 0.13f, 0f, 1f, 0f, 0.05f, Color.rgb(200, 60, 60), 0.2f, 6)
        }
    }

    fun cactusFood(b: MeshBuilder, x: Float, z: Float) {
        val c = Color.rgb(70, 130, 80)
        b.box(x, 0.55f, z, 0.14f, 0.55f, 0.14f, c)
        b.ellipsoid(x, 1.12f, z, 0.15f, 0.16f, 0.15f, c, 0f, 5, 7)
        for (k in 0..2) b.ellipsoid(x - 0.12f + k * 0.12f, 1.24f, z, 0.06f, 0.07f, 0.06f,
            Color.rgb(230, 80, 140), 0.35f, 4, 6)
    }

    fun seedFood(b: MeshBuilder, x: Float, z: Float) {
        for (k in 0..4) {
            val a = k * 1.25f; val px = x + cos(a) * 0.2f; val pz = z + sin(a) * 0.2f
            val h = 0.7f + (k % 3) * 0.2f
            b.cone(px, 0f, pz, 0.025f, 0.05f, 1f, 0.05f, h, Color.rgb(180, 168, 96), 0.05f, 4)
            b.ellipsoid(px, h, pz, 0.05f, 0.09f, 0.05f, Color.rgb(232, 202, 104), 0.2f, 4, 5)
        }
    }

    fun aquaticFood(b: MeshBuilder, x: Float, z: Float) {
        for (k in 0..4) {
            val px = x - 0.34f + k * 0.17f
            val h = 0.25f + (k % 3) * 0.10f
            b.cone(px, 0.03f, z + (k % 2) * 0.16f - 0.08f, 0.025f,
                0.08f, 1f, 0.02f, h, Color.rgb(38, 142, 108), 0.18f, 4)
            b.ellipsoid(px + 0.02f, h * 0.78f, z + 0.12f,
                0.11f, 0.025f, 0.07f, Color.rgb(74, 184, 126), 0.25f, 3, 6)
        }
    }

    /** Render every wild food source in the world. */
    fun drawFood(b: MeshBuilder) {
        for (f in FOODS) when (f.kind) {
            F_BERRY -> berryFood(b, f.x, f.z)
            F_FRUIT -> fruitFood(b, f.x, f.z)
            F_LICHEN -> lichenFood(b, f.x, f.z)
            F_MUSHROOM -> mushroomFood(b, f.x, f.z)
            F_CACTUS -> cactusFood(b, f.x, f.z)
            F_AQUATIC -> aquaticFood(b, f.x, f.z)
            else -> seedFood(b, f.x, f.z)
        }
    }

    /** Weather cells that hang over particular corners of the world — a
     *  blizzard raking one end of the tundra, flurries in the taiga, downpours
     *  deep in the rain forests — never a whole biome, just a place. */
    val WEATHER_PATCHES = listOf(
        Patch(5f, -3.5f, 5f, BLIZZARD),   // the far tundra, storm-scoured
        Patch(20f, 2.5f, 4f, SNOW),       // a taiga flurry
        Patch(40f, -3f, 5f, RAIN),        // the mistwood, forever dripping
        Patch(54f, 3f, 5.5f, RAIN),       // a jungle downpour
        Patch(67f, -2.5f, 4f, RAIN),      // monsoon rains
    )

    // The world floor reaches this far in z; beyond it, beach slopes into sea.
    // Four times the old depth — the world is a broad country now, not a lane.
    const val GROUND_Z = 14f

    private fun mixCol(a: Int, b: Int, k: Float): Int {
        val kk = k.coerceIn(0f, 1f)
        return Color.rgb(
            (Color.red(a) * (1 - kk) + Color.red(b) * kk).toInt(),
            (Color.green(a) * (1 - kk) + Color.green(b) * kk).toInt(),
            (Color.blue(a) * (1 - kk) + Color.blue(b) * kk).toInt()
        )
    }

    /** The shore's sand, reflecting the biome it fringes (icy tundra pebble,
     *  bright desert sand, dark jungle loam) — the ground color pulled warm. */
    fun beachColor(x: Float): Int =
        mixCol(blendColor(x) { it.ground }, Color.rgb(206, 184, 132), 0.55f)

    /** A pond, spring, or pool reflecting the biome it sits in: a pale sheet
     *  of ice in the tundra, cold blue in the taiga, temperate blue-green in
     *  the seasonal forests, turquoise in the tropics, a clear oasis in the
     *  drylands. */
    fun pondColor(x: Float): Int = when {
        x < 12f -> Color.rgb(210, 230, 244)   // TUNDRA — a frozen sheet
        x < 24f -> Color.rgb(120, 178, 214)    // TAIGA — cold blue
        x < 48f -> Color.rgb(96, 200, 226)     // GLADE / MISTWOOD
        x < 72f -> Color.rgb(74, 214, 200)     // JUNGLE / MONSOON — turquoise
        else -> Color.rgb(96, 196, 214)        // DUNES / SAGE / SCRUB — oasis
    }

    /** The tundra's ponds freeze solid; they render as flat ice, not glowing water. */
    fun isFrozen(x: Float): Boolean = x < 12f

    /** The bordering sea, tinted by the biome's own sky and ground: a jungle
     *  runs turquoise, the tundra icy-pale, the desert a hot shallow blue. */
    fun seaColor(x: Float): Int {
        val sky = blendColor(x) { it.skyBot }
        val ground = blendColor(x) { it.ground }
        // Lean on the biome's own palette, then cool it toward water — so the
        // jungle runs green, the desert warm-blue, the tundra icy-pale.
        val biomeTone = mixCol(sky, ground, 0.5f)
        return mixCol(biomeTone, Color.rgb(46, 150, 176), 0.42f)
    }

    /** The beach: a biome-tinted band that slopes off the world's edge into
     *  the water on all four sides, so the ground never ends in a flat line. */
    fun buildBorders(b: MeshBuilder) {
        val gz = GROUND_Z; val oz = gz + 2f          // outer beach edge
        val x0 = -3f; val x1 = WORLD_W + 3f
        val segs = 44
        // Front (+z) and back (-z) shores, tinted along the whole transect.
        for (i in 0 until segs) {
            val xa = x0 + (x1 - x0) * i / segs
            val xb = x0 + (x1 - x0) * (i + 1) / segs
            val col = beachColor((xa + xb) / 2f)
            b.quad(xa, 0f, gz, xb, 0f, gz, xb, -0.2f, oz, xa, -0.2f, oz, col)          // front
            b.quad(xb, 0f, -gz, xa, 0f, -gz, xa, -0.2f, -oz, xb, -0.2f, -oz, col)      // back
        }
        // Left and right shores (the world's ends), fewer segments in z.
        val zsegs = 10
        for (i in 0 until zsegs) {
            val za = -gz + 2 * gz * i / zsegs
            val zb = -gz + 2 * gz * (i + 1) / zsegs
            val cl = beachColor(x0); val cr = beachColor(x1)
            b.quad(x0, 0f, zb, x0, 0f, za, x0 - 2f, -0.2f, za, x0 - 2f, -0.2f, zb, cl)  // left
            b.quad(x1, 0f, za, x1, 0f, zb, x1 + 2f, -0.2f, zb, x1 + 2f, -0.2f, za, cr)  // right
        }
    }

    /** The ring of ocean discs beyond the beach, out to the horizon. Returns
     *  (x, z, radius); the renderer tints each by the biome at that x. */
    fun oceanDiscs(): List<FloatArray> {
        val o = ArrayList<FloatArray>()
        val r = 12f
        val fz = GROUND_Z + 5f
        var x = -8f
        while (x <= WORLD_W + 8f) {
            o += floatArrayOf(x, fz, r)        // front sea
            o += floatArrayOf(x, -fz, r)       // back sea
            x += 7f
        }
        var z = -(GROUND_Z + 2f)
        while (z <= GROUND_Z + 2f) {
            o += floatArrayOf(-13f, z, r)          // left sea (the western end)
            o += floatArrayOf(WORLD_W + 13f, z, r) // right sea (the eastern end)
            z += 7f
        }
        return o
    }

    /** Scatter biome-appropriate background props across the widened depth so
     *  the broad world reads full, not a treeline over an empty field. The
     *  central lane is left clearer for the creatures; these are visual only. */
    fun scatterDepth(b: MeshBuilder) {
        for ((bi, biome) in BIOMES.withIndex()) {
            for (i in 0 until 14) {
                val h = bi * 131 + i * 71
                val fx = biome.x0 + 1f + (biome.x1 - biome.x0 - 2f) * ((h % 100) / 100f)
                val fz = -8f + 16f * (((h / 100) % 100) / 100f)
                if (fz > -3f && fz < 3.4f) continue      // keep the play lane open
                val s = 0.7f + ((h / 7) % 3) * 0.22f
                when (bi) {
                    0 -> if (i % 2 == 0) snowMound(b, fx, fz, s)
                    else rock(b, fx, fz, s * 0.6f, Color.rgb(96, 104, 120))
                    1 -> conifer(b, fx, fz, s, Color.rgb(24, 76, 58), Color.rgb(54, 40, 34))
                    2 -> broadleaf(b, fx, fz, s,
                        intArrayOf(Color.rgb(60, 130, 66), Color.rgb(210, 150, 60))[i % 2], Color.rgb(70, 50, 40))
                    3 -> conifer(b, fx, fz, s * 1.15f, Color.rgb(26, 84, 60), Color.rgb(52, 44, 38))
                    4 -> broadleaf(b, fx, fz, s * 1.1f, Color.rgb(24, 96, 44), Color.rgb(66, 48, 36))
                    5 -> if (i % 2 == 0) broadleaf(b, fx, fz, s, Color.rgb(90, 120, 50), Color.rgb(80, 58, 40))
                    else dryGrass(b, fx, fz, s, Color.rgb(190, 170, 80))
                    6 -> if (i % 3 == 0) cactus(b, fx, fz, s) else rock(b, fx, fz, s * 0.6f, Color.rgb(130, 96, 62))
                    7 -> if (i % 2 == 0) sageBush(b, fx, fz, s) else rock(b, fx, fz, s * 0.6f, Color.rgb(110, 100, 78))
                    else -> scrubBush(b, fx, fz, s)
                }
            }
        }
    }

    /**
     * Collision circles (x, z, radius) for the solid biome scenery — the
     * trunks, cacti, and the mesa — so the walker and creatures don't ghost
     * through them. Kept in step with the biome decor above; the soft things
     * (snow, ferns, grass, sage) stay walkable. Microhabitat zones add their
     * own obstacles separately in the renderer.
     */
    fun decorObstacles(): List<FloatArray> {
        val o = ArrayList<FloatArray>()
        for (i in 0..3) o += floatArrayOf(2f + i * 2.6f, -1.4f + (i % 2) * 0.6f, 0.4f)   // TUNDRA rocks
        for (i in 0..7) o += floatArrayOf(12.6f + i * 1.4f, -2.6f - (i % 3) * 0.8f, 0.3f) // TAIGA conifers
        for (i in 0..5) o += floatArrayOf(24.8f + i * 1.9f, -2.6f - (i % 2) * 0.9f, 0.42f) // GLADE broadleaf
        for (i in 0..5) o += floatArrayOf(36.8f + i * 1.9f, -2.8f - (i % 2) * 0.9f, 0.42f) // MISTWOOD conifers
        for (i in 0..6) o += floatArrayOf(48.7f + i * 1.6f, -2.7f - (i % 3) * 0.7f, 0.42f) // JUNGLE broadleaf
        for (i in 0..3) o += floatArrayOf(61f + i * 2.8f, -2.6f - (i % 2) * 0.8f, 0.4f)   // MONSOON broadleaf
        for (i in 0..2) o += floatArrayOf(74f + i * 3.2f, -0.8f + (i % 2) * 0.6f, 0.26f)  // DUNES cacti
        o += floatArrayOf(93.5f, -2.8f, 1.0f)                                             // SAGE mesa
        for (i in 0..2) o += floatArrayOf(86f + i * 3f, -2.6f, 0.42f)                     // SAGE rocks
        for (i in 0..2) o += floatArrayOf(98f + i * 3.2f, -0.8f + (i % 2) * 0.7f, 0.34f)  // SCRUB trees
        return o
    }

    /** Color blended across biome seams (±2.5 units of gentle transition). */
    fun blendColor(x: Float, pick: (Biome) -> Int): Int {
        val b = biomeAt(x)
        val i = BIOMES.indexOf(b)
        val half = 2.5f
        val (other, t) = when {
            i > 0 && x - b.x0 < half -> BIOMES[i - 1] to (0.5f - (x - b.x0) / (half * 2f))
            i < BIOMES.size - 1 && b.x1 - x < half -> BIOMES[i + 1] to (0.5f - (b.x1 - x) / (half * 2f))
            else -> return pick(b)
        }
        val ca = pick(b); val cb = pick(other)
        val k = t.coerceIn(0f, 0.5f)
        return Color.rgb(
            (Color.red(ca) * (1 - k) + Color.red(cb) * k).toInt(),
            (Color.green(ca) * (1 - k) + Color.green(cb) * k).toInt(),
            (Color.blue(ca) * (1 - k) + Color.blue(cb) * k).toInt()
        )
    }

    /** Twelve decor stands along the whole stroll. */
    fun slotX(i: Int) = 2.4f + i * (WORLD_W - 4.8f) / 11f
    const val SLOT_Z = -1.85f
    const val SLOTS = 12

    val ITEMS = arrayOf(
        ItemDef("honey", "HONEY TREAT", 15, true, 2, emptySet(), Color.rgb(255, 190, 80),
            { _, _, _ -> }),
        ItemDef("pudding", "ROYAL PUDDING", 40, true, 5, emptySet(), Color.rgb(255, 140, 190),
            { _, _, _ -> }),
        ItemDef("bush", "BERRY BUSH", 25, false, 0,
            setOf("DOZY", "ZOOMY", "POLITE", "HELPFUL"), Color.rgb(90, 220, 120),
            { b, x, z ->
                b.ellipsoid(x, 0.3f, z, 0.42f, 0.32f, 0.36f, Color.rgb(30, 110, 60))
                b.ellipsoid(x - 0.25f, 0.42f, z + 0.1f, 0.24f, 0.2f, 0.22f, Color.rgb(36, 130, 70))
                for (k in 0..4) b.ellipsoid(x - 0.3f + k * 0.15f, 0.35f + (k % 2) * 0.18f, z + 0.28f,
                    0.05f, 0.05f, 0.05f, Color.rgb(255, 110, 170), 0.7f, 4, 5)
            }),
        ItemDef("pool", "SPRING POOL", 30, false, 0,
            setOf("GIGGLY", "SLY", "SERENE", "WRY", "SPLASHY"), Color.rgb(90, 210, 255),
            { b, x, z ->
                b.ellipsoid(x, 0.05f, z, 0.55f, 0.05f, 0.42f, Color.rgb(60, 190, 230), 0.6f, 4, 12)
                for (k in 0..5) {
                    val a = k * 1.047f
                    b.ellipsoid(x + cos(a) * 0.6f, 0.09f, z + sin(a) * 0.46f,
                        0.09f, 0.07f, 0.08f, Color.rgb(70, 90, 104), 0f, 4, 6)
                }
            }),
        ItemDef("lantern", "EMBER LANTERN", 30, false, 0,
            setOf("COZY", "DRAMATIC", "PUNCTUAL", "FLICKERY"), Color.rgb(255, 170, 70),
            { b, x, z ->
                b.box(x, 0.5f, z, 0.05f, 0.5f, 0.05f, Color.rgb(56, 40, 40))
                b.box(x, 1.06f, z, 0.14f, 0.16f, 0.14f, Color.rgb(70, 52, 46))
                // Its flame is lit dynamically by the renderer.
            }),
        ItemDef("nook", "TOME NOOK", 25, false, 0,
            setOf("PEDANTIC", "DROWSY", "CLEVER", "STEADFAST"), Color.rgb(190, 140, 255),
            { b, x, z ->
                b.box(x, 0.12f, z, 0.4f, 0.12f, 0.3f, Color.rgb(80, 58, 44))
                for (k in 0..3) b.box(x - 0.24f + k * 0.16f, 0.36f, z,
                    0.06f, 0.14f - (k % 2) * 0.03f, 0.2f,
                    intArrayOf(Color.rgb(150, 90, 200), Color.rgb(90, 140, 220),
                        Color.rgb(200, 120, 120), Color.rgb(110, 190, 140))[k], 0.15f)
            }),
        ItemDef("gems", "GEM PILE", 35, false, 0,
            setOf("GREEDY", "OTHERWORLDLY", "BASHFUL", "ALOOF"), Color.rgb(120, 255, 200),
            { b, x, z ->
                b.cone(x, 0f, z, 0.18f, 0.1f, 1f, 0f, 0.4f, Color.rgb(110, 255, 190), 0.8f, 5)
                b.cone(x - 0.25f, 0f, z + 0.12f, 0.12f, -0.2f, 1f, 0f, 0.26f, Color.rgb(255, 130, 180), 0.75f, 5)
                b.cone(x + 0.24f, 0f, z + 0.08f, 0.11f, 0.3f, 1f, 0f, 0.24f, Color.rgb(140, 170, 255), 0.75f, 5)
            }),
        ItemDef("drum", "STORM DRUM", 35, false, 0,
            setOf("RUMBLY", "JITTERY", "BOISTEROUS", "BRASH"), Color.rgb(150, 190, 255),
            { b, x, z ->
                b.ellipsoid(x, 0.22f, z, 0.34f, 0.22f, 0.34f, Color.rgb(60, 74, 110), 0.1f, 6, 10)
                b.ellipsoid(x, 0.42f, z, 0.3f, 0.04f, 0.3f, Color.rgb(180, 210, 255), 0.4f, 3, 10)
                b.cone(x + 0.05f, 0.46f, z, 0.05f, 0.4f, 1f, 0f, 0.3f, Color.rgb(255, 255, 140), 0.9f, 4)
            }),
        ItemDef("plinth", "MOON PLINTH", 40, false, 0,
            setOf("POLITE", "ALOOF", "SERENE", "OTHERWORLDLY"), Color.rgb(230, 235, 255),
            { b, x, z ->
                b.box(x, 0.3f, z, 0.2f, 0.3f, 0.2f, Color.rgb(70, 76, 100))
                b.ellipsoid(x, 0.85f, z, 0.22f, 0.22f, 0.22f, Color.rgb(235, 240, 255), 0.75f, 7, 10)
            }),
        ItemDef("perch", "CLOUD PERCH", 45, false, 0,
            setOf("BOISTEROUS", "ZOOMY", "CLEVER", "DRAMATIC", "BREEZY"), Color.rgb(220, 240, 255),
            { b, x, z ->
                b.ellipsoid(x, 1.25f, z, 0.4f, 0.16f, 0.28f, Color.rgb(210, 225, 245), 0.3f, 5, 8)
                b.ellipsoid(x - 0.3f, 1.18f, z + 0.05f, 0.22f, 0.12f, 0.18f, Color.rgb(190, 210, 235), 0.25f, 5, 7)
                b.ellipsoid(x + 0.3f, 1.2f, z, 0.2f, 0.11f, 0.16f, Color.rgb(190, 210, 235), 0.25f, 5, 7)
            }),
    )

    fun item(id: String): ItemDef? = ITEMS.firstOrNull { it.id == id }
}
