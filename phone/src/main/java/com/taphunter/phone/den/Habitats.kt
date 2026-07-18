package com.taphunter.phone.den

import android.graphics.Color
import kotlin.math.cos
import kotlin.math.sin

/**
 * Four biospheres, each about eight phone-screens wide, stitched from
 * MICROHABITATS — ponds, thickets, ember vents, burrow banks, void pools,
 * perches — that match the creatures' ecological niches. A pond-dweller
 * makes for water; a dusk-stalker haunts the dark; a burrower travels the
 * soft ground between mounds. The shop catalog lives here too.
 */
class Zone(val kind: String, val x: Float, val z: Float, val r: Float)

class HabitatDef(
    val name: String,
    val skyTop: Int, val skyBot: Int, val fog: Int, val ground: Int,
    val fireflyColor: Int,
    val loved: Set<String>,
    val zones: List<Zone>,
    val decor: (MeshBuilder, Float) -> Unit    // bespoke scenery beyond the zones
)

class ItemDef(
    val id: String,
    val name: String,
    val price: Int,
    val treat: Boolean,
    val bondPts: Int,               // treats only
    val loved: Set<String>,         // decor: temperaments drawn to it
    val cardColor: Int,
    val build: (MeshBuilder, Float, Float) -> Unit   // at (x, z)
)

object Habitats {
    const val WORLD_W = 27.2f      // ≈ eight screens; a proper stroll

    private fun tree(b: MeshBuilder, x: Float, z: Float, s: Float, leaf: Int, trunk: Int) {
        b.box(x, s * 0.35f, z, s * 0.08f, s * 0.35f, s * 0.08f, trunk)
        b.cone(x, s * 0.55f, z, s * 0.5f, 0f, 1f, 0f, s * 0.8f, leaf)
        b.cone(x, s * 1.05f, z, s * 0.34f, 0f, 1f, 0f, s * 0.6f, leaf, 0.12f)
    }

    private fun rock(b: MeshBuilder, x: Float, z: Float, s: Float, c: Int) {
        b.ellipsoid(x, s * 0.3f, z, s * 0.55f, s * 0.34f, s * 0.45f, c, 0f, 5, 7)
    }

    private fun crystal(b: MeshBuilder, x: Float, z: Float, s: Float, c: Int) {
        b.cone(x, 0f, z, s * 0.22f, 0.15f, 1f, 0f, s, c, 0.7f, 5)
        b.cone(x + s * 0.25f, 0f, z + s * 0.15f, s * 0.13f, -0.2f, 1f, 0.1f, s * 0.55f, c, 0.6f, 5)
    }

    /**
     * Draw one microhabitat's scenery. Generic per kind, tinted per habitat,
     * so every biosphere is fully furnished without a thousand hand-placed
     * props — the bespoke decor adds the local character on top.
     */
    fun zoneDecor(b: MeshBuilder, zone: Zone, hab: HabitatDef) {
        val x = zone.x; val z = zone.z; val r = zone.r
        when (zone.kind) {
            "WATER" -> {
                b.ellipsoid(x, 0.04f, z, r, 0.035f, r * 0.75f, Color.rgb(50, 170, 210), 0.55f, 4, 14)
                b.ellipsoid(x, 0.055f, z, r * 0.55f, 0.03f, r * 0.4f, Color.rgb(90, 220, 245), 0.4f, 3, 10)
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
                for (k in 0..3) {
                    val a = k * 1.57f + 0.4f
                    val px = x + cos(a) * r * 0.5f; val pz = z + sin(a) * r * 0.4f
                    b.cone(px, 0f, pz, 0.12f, 0f, 1f, 0f, 0.22f, Color.rgb(60, 36, 32))
                    b.cone(px, 0.1f, pz, 0.05f, 0f, 1f, 0f, 0.3f, Color.rgb(255, 160, 60), 0.9f, 5)
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

    val ALL = arrayOf(
        HabitatDef(
            "MOONLIT MEADOW",
            Color.rgb(10, 16, 44), Color.rgb(24, 60, 74), Color.rgb(14, 30, 46),
            Color.rgb(22, 52, 40), Color.rgb(170, 255, 190),
            setOf("DOZY", "ZOOMY", "POLITE", "HELPFUL", "STEADFAST", "GIGGLY", "BREEZY", "SNUG"),
            listOf(
                Zone("WATER", 4.2f, -0.6f, 1.5f),
                Zone("BLOOM", 8.6f, 0.2f, 1.6f),
                Zone("THICKET", 12.8f, -1.2f, 1.6f),
                Zone("BURROW", 16.8f, 0.4f, 1.3f),
                Zone("PERCH", 20.6f, -1.0f, 1.2f),
                Zone("STONE", 23.8f, 0.1f, 1.2f),
                Zone("VOID", 25.8f, -1.4f, 1.1f),
            ),
            { b, w ->
                for (i in 0..12) tree(b, 0.6f + i * (w - 1.2f) / 12f + (i % 3) * 0.3f,
                    -2.9f - (i % 2) * 0.9f, 0.85f + (i % 3) * 0.28f,
                    Color.rgb(30, 96, 66), Color.rgb(60, 44, 40))
                for (i in 0..8)
                    b.ellipsoid(1f + i * (w - 2f) / 8f, 0.05f, 1.6f + (i % 2) * 0.5f,
                        0.05f, 0.05f, 0.05f, Color.rgb(200, 255, 210), 0.8f, 4, 5)
            }
        ),
        HabitatDef(
            "EMBER HOLLOW",
            Color.rgb(26, 8, 20), Color.rgb(80, 34, 20), Color.rgb(40, 16, 16),
            Color.rgb(48, 26, 24), Color.rgb(255, 180, 90),
            setOf("COZY", "DRAMATIC", "GREEDY", "BRASH", "PUNCTUAL", "RUMBLY", "SPLASHY"),
            listOf(
                Zone("EMBER", 3.6f, -0.4f, 1.5f),
                Zone("STONE", 7.8f, 0.3f, 1.4f),
                Zone("BURROW", 11.6f, -1.1f, 1.3f),
                Zone("WATER", 15.2f, 0.2f, 1.2f),      // a small hot spring
                Zone("VOID", 19.0f, -1.2f, 1.3f),      // the ash drift
                Zone("PERCH", 22.6f, -0.4f, 1.2f),
                Zone("EMBER", 25.6f, 0.3f, 1.2f),
            ),
            { b, w ->
                for (i in 0..8) {
                    val x = 1f + i * (w - 2f) / 8f
                    b.box(x, 0.55f, -2.6f, 0.05f, 0.55f, 0.05f, Color.rgb(50, 34, 34))
                    b.ellipsoid(x, 1.2f, -2.6f, 0.14f, 0.18f, 0.14f, Color.rgb(255, 170, 70), 0.95f, 6, 8)
                }
                for (i in 0..9) rock(b, 0.5f + i * (w - 1f) / 9f, -3.4f,
                    0.7f + (i % 3) * 0.3f, Color.rgb(70, 40, 34))
            }
        ),
        HabitatDef(
            "TIDE COVE",
            Color.rgb(6, 14, 40), Color.rgb(20, 70, 90), Color.rgb(10, 30, 44),
            Color.rgb(30, 52, 58), Color.rgb(140, 230, 255),
            setOf("SLY", "SERENE", "GIGGLY", "FLICKERY", "WRY", "BOISTEROUS", "SPLASHY", "BREEZY"),
            listOf(
                Zone("WATER", 4.6f, -0.5f, 2.2f),      // the lagoon
                Zone("THICKET", 9.4f, -1.2f, 1.5f),    // the kelp stand
                Zone("BURROW", 13.2f, 0.4f, 1.3f),     // the dune
                Zone("STONE", 17.0f, -0.8f, 1.4f),     // the tidepools
                Zone("BLOOM", 20.8f, 0.2f, 1.4f),      // the shell drift
                Zone("WATER", 24.4f, -0.9f, 1.6f),     // the far pool
            ),
            { b, w ->
                b.quad(-3f, 0.03f, -5.2f, w + 3f, 0.03f, -5.2f,
                    w + 3f, 0.03f, -2.6f, -3f, 0.03f, -2.6f, Color.rgb(30, 120, 150), 0.45f)
                for (i in 0..6) {
                    val x = 1.4f + i * (w - 2.8f) / 6f
                    b.box(x - 0.5f, 0.5f, -3.4f, 0.18f, 0.5f, 0.2f, Color.rgb(46, 62, 76))
                    b.box(x + 0.5f, 0.5f, -3.4f, 0.18f, 0.5f, 0.2f, Color.rgb(46, 62, 76))
                    b.box(x, 1.05f, -3.4f, 0.75f, 0.14f, 0.2f, Color.rgb(52, 70, 84))
                }
                for (i in 0..8)
                    b.cone(0.8f + i * (w - 1.6f) / 8f, 0f, -2.2f, 0.04f,
                        0.15f, 1f, 0f, 0.8f + (i % 3) * 0.3f, Color.rgb(40, 130, 90), 0.15f, 4)
            }
        ),
        HabitatDef(
            "STARFALL SHRINE",
            Color.rgb(8, 4, 24), Color.rgb(44, 22, 78), Color.rgb(20, 12, 40),
            Color.rgb(30, 24, 52), Color.rgb(210, 180, 255),
            setOf("OTHERWORLDLY", "ALOOF", "CLEVER", "PEDANTIC", "DROWSY", "BASHFUL", "JITTERY", "SNUG"),
            listOf(
                Zone("VOID", 4.0f, -0.6f, 1.7f),       // the still pool of night
                Zone("THICKET", 8.4f, -1.1f, 1.5f),    // the crystal grove
                Zone("BLOOM", 12.6f, 0.3f, 1.4f),      // the moss ring
                Zone("BURROW", 16.4f, -0.9f, 1.3f),    // the meteor crater
                Zone("PERCH", 20.2f, -0.5f, 1.3f),     // the floating step
                Zone("STONE", 24.0f, 0.2f, 1.3f),
                Zone("VOID", 26.2f, -1.3f, 1.0f),
            ),
            { b, w ->
                for (i in 0..10) crystal(b, 0.7f + i * (w - 1.4f) / 10f, -2.8f - (i % 2) * 0.8f,
                    0.8f + (i % 3) * 0.4f, Color.rgb(150, 110, 255))
                for (i in 0..8) {
                    val x = 1.2f + i * (w - 2.4f) / 8f
                    b.cone(x, 1.5f + (i % 2) * 0.4f, -2.2f, 0.09f, 0f, 1f, 0f, 0.18f,
                        Color.rgb(220, 200, 255), 0.85f, 4)
                    b.cone(x, 1.5f + (i % 2) * 0.4f, -2.2f, 0.09f, 0f, -1f, 0f, 0.18f,
                        Color.rgb(220, 200, 255), 0.85f, 4)
                }
            }
        ),
    )

    /** Six decor stands per habitat, spread along the stroll. */
    fun slotX(i: Int) = 2.2f + i * (WORLD_W - 4.4f) / 5f
    const val SLOT_Z = -1.85f
    const val SLOTS = 6

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
                b.ellipsoid(x, 1.06f, z, 0.1f, 0.12f, 0.1f, Color.rgb(255, 190, 90), 0.95f, 5, 7)
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
