package com.taphunter.phone.den

import android.graphics.Color

/**
 * Four little dioramas, each about four phone-screens wide, plus the shop
 * catalog. Creatures prefer habitats and items that suit their temperament —
 * a GREEDY Coinix beelines for a gem pile; a PEDANTIC Bookwyrm settles at
 * the tome nook; DOZY things nap wherever it's soft.
 */
class HabitatDef(
    val name: String,
    val skyTop: Int, val skyBot: Int, val fog: Int, val ground: Int,
    val fireflyColor: Int,
    val loved: Set<String>,
    val decor: (MeshBuilder, Float) -> Unit    // (builder, worldWidth)
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
    const val WORLD_W = 13.6f      // ≈ four screens of side-scroll

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

    val ALL = arrayOf(
        HabitatDef(
            "MOONLIT MEADOW",
            Color.rgb(10, 16, 44), Color.rgb(24, 60, 74), Color.rgb(14, 30, 46),
            Color.rgb(22, 52, 40), Color.rgb(170, 255, 190),
            setOf("DOZY", "ZOOMY", "POLITE", "HELPFUL", "STEADFAST", "GIGGLY"),
            { b, w ->
                for (i in 0..6) tree(b, 0.6f + i * (w - 1.2f) / 6f + (i % 3) * 0.3f,
                    -2.6f - (i % 2) * 0.8f, 0.9f + (i % 3) * 0.25f,
                    Color.rgb(30, 96, 66), Color.rgb(60, 44, 40))
                for (i in 0..10) b.ellipsoid(0.4f + i * (w - 0.8f) / 10f, 0.05f,
                    -1.9f + (i % 3) * 0.5f, 0.05f, 0.05f, 0.05f,
                    Color.rgb(200, 255, 210), 0.8f, 4, 5)
                rock(b, w * 0.35f, -2f, 0.8f, Color.rgb(40, 58, 66))
            }
        ),
        HabitatDef(
            "EMBER HOLLOW",
            Color.rgb(26, 8, 20), Color.rgb(80, 34, 20), Color.rgb(40, 16, 16),
            Color.rgb(48, 26, 24), Color.rgb(255, 180, 90),
            setOf("COZY", "DRAMATIC", "GREEDY", "BRASH", "PUNCTUAL", "RUMBLY"),
            { b, w ->
                for (i in 0..4) {
                    val x = 1f + i * (w - 2f) / 4f
                    b.box(x, 0.55f, -2.4f, 0.05f, 0.55f, 0.05f, Color.rgb(50, 34, 34))
                    b.ellipsoid(x, 1.2f, -2.4f, 0.14f, 0.18f, 0.14f, Color.rgb(255, 170, 70), 0.95f, 6, 8)
                }
                for (i in 0..5) rock(b, 0.5f + i * (w - 1f) / 5f, -3.1f,
                    0.7f + (i % 3) * 0.3f, Color.rgb(70, 40, 34))
            }
        ),
        HabitatDef(
            "TIDE COVE",
            Color.rgb(6, 14, 40), Color.rgb(20, 70, 90), Color.rgb(10, 30, 44),
            Color.rgb(30, 52, 58), Color.rgb(140, 230, 255),
            setOf("SLY", "SERENE", "GIGGLY", "FLICKERY", "WRY", "BOISTEROUS"),
            { b, w ->
                b.quad(-3f, 0.03f, -4.6f, w + 3f, 0.03f, -4.6f,
                    w + 3f, 0.03f, -2f, -3f, 0.03f, -2f, Color.rgb(30, 120, 150), 0.45f)
                for (i in 0..3) {
                    val x = 1.4f + i * (w - 2.8f) / 3f
                    b.box(x - 0.5f, 0.5f, -3.2f, 0.18f, 0.5f, 0.2f, Color.rgb(46, 62, 76))
                    b.box(x + 0.5f, 0.5f, -3.2f, 0.18f, 0.5f, 0.2f, Color.rgb(46, 62, 76))
                    b.box(x, 1.05f, -3.2f, 0.75f, 0.14f, 0.2f, Color.rgb(52, 70, 84))
                }
                for (i in 0..7) b.ellipsoid(0.5f + i * (w - 1f) / 7f, 0.05f, -1.7f,
                    0.08f, 0.04f, 0.06f, Color.rgb(255, 220, 240), 0.35f, 4, 6)
            }
        ),
        HabitatDef(
            "STARFALL SHRINE",
            Color.rgb(8, 4, 24), Color.rgb(44, 22, 78), Color.rgb(20, 12, 40),
            Color.rgb(30, 24, 52), Color.rgb(210, 180, 255),
            setOf("OTHERWORLDLY", "ALOOF", "CLEVER", "PEDANTIC", "DROWSY", "BASHFUL", "JITTERY"),
            { b, w ->
                for (i in 0..5) crystal(b, 0.7f + i * (w - 1.4f) / 5f, -2.5f - (i % 2) * 0.7f,
                    0.8f + (i % 3) * 0.4f, Color.rgb(150, 110, 255))
                for (i in 0..4) {
                    val x = 1.2f + i * (w - 2.4f) / 4f
                    b.cone(x, 1.5f + (i % 2) * 0.4f, -2f, 0.09f, 0f, 1f, 0f, 0.18f,
                        Color.rgb(220, 200, 255), 0.85f, 4)
                    b.cone(x, 1.5f + (i % 2) * 0.4f, -2f, 0.09f, 0f, -1f, 0f, 0.18f,
                        Color.rgb(220, 200, 255), 0.85f, 4)
                }
            }
        ),
    )

    /** Six decor stands per habitat, spread along the stroll. */
    fun slotX(i: Int) = 1.8f + i * (WORLD_W - 3.6f) / 5f
    const val SLOT_Z = -1.55f
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
            setOf("GIGGLY", "SLY", "SERENE", "WRY"), Color.rgb(90, 210, 255),
            { b, x, z ->
                b.ellipsoid(x, 0.05f, z, 0.55f, 0.05f, 0.42f, Color.rgb(60, 190, 230), 0.6f, 4, 12)
                for (k in 0..5) {
                    val a = k * 1.047f
                    b.ellipsoid(x + kotlin.math.cos(a) * 0.6f, 0.09f, z + kotlin.math.sin(a) * 0.46f,
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
            setOf("BOISTEROUS", "ZOOMY", "CLEVER", "DRAMATIC"), Color.rgb(220, 240, 255),
            { b, x, z ->
                b.ellipsoid(x, 1.25f, z, 0.4f, 0.16f, 0.28f, Color.rgb(210, 225, 245), 0.3f, 5, 8)
                b.ellipsoid(x - 0.3f, 1.18f, z + 0.05f, 0.22f, 0.12f, 0.18f, Color.rgb(190, 210, 235), 0.25f, 5, 7)
                b.ellipsoid(x + 0.3f, 1.2f, z, 0.2f, 0.11f, 0.16f, Color.rgb(190, 210, 235), 0.25f, 5, 7)
            }),
    )

    fun item(id: String): ItemDef? = ITEMS.firstOrNull { it.id == id }
}
