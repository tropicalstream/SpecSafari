package com.taphunter

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.os.SystemClock
import com.taphunter.engine.GameEngine
import com.taphunter.engine.Species
import com.taphunter.engine.State
import com.taphunter.geo.GeoMath
import com.taphunter.render.MapRenderer
import com.taphunter.render.Sprites
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * All screens. Sunlight rules: white-hot text, saturated accents, nothing
 * thin, nothing dim. Black is transparent on the waveguide — the world
 * shows through everywhere we do not paint.
 */
class Renderer(private val g: GameEngine) {

    private val map = MapRenderer()

    private val title = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; textSize = 34f; typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER
    }
    private val head = Paint(title).apply { textSize = 20f; color = Color.rgb(140, 255, 210) }
    private val body = Paint(title).apply { textSize = 15f; color = Color.rgb(235, 245, 255) }
    private val small = Paint(body).apply { textSize = 13f; color = Color.rgb(180, 220, 245) }
    private val accent = Paint(body).apply { color = Color.rgb(255, 220, 90) }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val strokeP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND
    }
    private val path = Path()

    fun draw(c: Canvas, w: Int, h: Int) {
        val t = SystemClock.uptimeMillis() % 1000000L / 1000f
        when (g.state) {
            State.TITLE -> drawTitle(c, w, h, t)
            State.ACQUIRE -> drawAcquire(c, w, h, t)
            State.HUNT -> drawHunt(c, w, h, t)
            State.ENGAGE -> drawEngage(c, w, h, t)
            State.RESULT -> drawResult(c, w, h, t)
            State.LOOT -> drawLoot(c, w, h, t)
            State.HUB -> drawMenu(c, w, h, "TAPHUNTER",
                (0 until g.hubRowCount()).map { g.hubRow(it) to "" })
            State.BOX -> drawBox(c, w, h, t)
            State.UPGRADE -> drawUpgrade(c, w, h)
            State.SETTINGS -> drawMenu(c, w, h, "SETTINGS",
                (0 until g.settingsRowCount()).map { g.settingsRow(it) to g.settingValue(it) })
        }
    }

    // ------------------------------------------------------------ screens

    private fun drawTitle(c: Canvas, w: Int, h: Int, t: Float) {
        val cx = w / 2f
        title.textSize = 36f
        title.textAlign = Paint.Align.LEFT
        val wTap = title.measureText("TAP")
        val wAll = wTap + title.measureText("HUNTER")
        title.color = Color.rgb(140, 255, 210)
        c.drawText("TAP", cx - wAll / 2f, h * 0.22f, title)
        title.color = Color.WHITE
        c.drawText("HUNTER", cx - wAll / 2f + wTap, h * 0.22f, title)
        title.textAlign = Paint.Align.CENTER
        small.textAlign = Paint.Align.CENTER
        c.drawText("THE REALM IS YOUR NEIGHBORHOOD", cx, h * 0.28f, small)

        // A parade of the twelve, marching in a slow circle.
        for (i in 0 until 12) {
            val a = t * 0.4f + i * (Math.PI.toFloat() * 2f / 12f)
            val px = cx + cos(a) * w * 0.32f
            val py = h * 0.52f + sin(a) * h * 0.16f
            Sprites.creature(c, i, px, py, 9f, t + i, excited = false)
        }
        Sprites.creature(c, (t / 3f).toInt() % 12, cx, h * 0.52f, 22f, t, excited = true)

        val blink = (sin(t * 4f) + 1f) / 2f
        body.textAlign = Paint.Align.CENTER
        body.color = Color.argb((120 + 135 * blink).toInt(), 255, 255, 255)
        c.drawText("TAP TO BEGIN THE HUNT", cx, h * 0.82f, body)
        body.color = Color.rgb(235, 245, 255)
        small.color = Color.rgb(150, 200, 230)
        c.drawText("A CREATURE ALWAYS WAITS WITHIN 50 M", cx, h * 0.88f, small)
        small.color = Color.rgb(180, 220, 245)
    }

    private fun drawAcquire(c: Canvas, w: Int, h: Int, t: Float) {
        val cx = w / 2f
        head.textAlign = Paint.Align.CENTER
        c.drawText("READING THE REALM", cx, h * 0.3f, head)
        // Spinner: an orb circling a map pin.
        val a = t * 3f
        strokeP.color = Color.rgb(90, 220, 255); strokeP.strokeWidth = 3f
        c.drawCircle(cx, h * 0.45f, 26f, strokeP)
        fill.color = Color.rgb(255, 220, 90)
        c.drawCircle(cx + cos(a) * 26f, h * 0.45f + sin(a) * 26f, 6f, fill)
        body.textAlign = Paint.Align.CENTER
        val gps = if (g.player != null) "LOCATION LOCKED" else "SEARCHING FOR SKY..."
        c.drawText(gps, cx, h * 0.58f, body)
        small.textAlign = Paint.Align.CENTER
        c.drawText(
            if (g.roads.isEmpty()) "CHARTING ROADS AND PLACES..." else "${g.roads.size} WAYS CHARTED",
            cx, h * 0.64f, small
        )
    }

    private fun drawHunt(c: Canvas, w: Int, h: Int, t: Float) {
        map.draw(c, w, h, g, t)

        // Objective banner.
        val tg = g.target()
        val me = g.player
        head.textAlign = Paint.Align.CENTER
        body.textAlign = Paint.Align.CENTER
        if (tg != null && me != null) {
            val what = if (tg.isCreature)
                "L${tg.level} ${Species.ALL[tg.species].name}" else "TREASURE"
            head.color = if (tg.isCreature) Species.ALL[tg.species].main else Color.rgb(255, 210, 40)
            c.drawText(what, w / 2f, 24f, head)
            head.color = Color.rgb(140, 255, 210)
            body.drawScaled(c, tg.placeName, w / 2f, 44f, w - 24f)
            accent.textAlign = Paint.Align.CENTER
            val d = GeoMath.distanceM(me, tg.p)
            c.drawText(
                if (d <= g.engageRange()) "IN RANGE - TAP!" else GeoMath.prettyDistance(d),
                w / 2f, 63f, accent
            )
        } else {
            c.drawText("SCOUTING THE REALM...", w / 2f, 30f, head)
        }

        // Status strip.
        small.textAlign = Paint.Align.LEFT
        Sprites.gem(c, 12f, h - 14f, 6f)
        c.drawText("${g.essence}", 24f, h - 9f, small)
        small.textAlign = Paint.Align.CENTER
        c.drawText("HUNT L${g.spawner.level}", w / 2f, h - 9f, small)
        small.textAlign = Paint.Align.RIGHT
        c.drawText("${g.zoomRadius.toInt()} M", w - 10f, h - 9f, small)
        if (!g.compassLive || g.gpsAccuracy > 60f) {
            small.textAlign = Paint.Align.CENTER
            small.color = Color.rgb(255, 150, 120)
            c.drawText(
                if (!g.compassLive) "COMPASS WARMING UP" else "WEAK SKY SIGNAL",
                w / 2f, h - 26f, small
            )
            small.color = Color.rgb(180, 220, 245)
        }
    }

    private fun drawEngage(c: Canvas, w: Int, h: Int, t: Float) {
        val tg = g.engageTarget ?: return
        val cx = w / 2f
        val cy = h * 0.48f
        val ringR = min(w, h) * 0.33f

        Sprites.creature(c, tg.species, cx, cy, ringR * 0.36f, t, excited = true)

        // The capture ring: hit the sweet arc as the comet crosses it.
        strokeP.color = Color.argb(140, 255, 255, 255); strokeP.strokeWidth = 5f
        c.drawCircle(cx, cy, ringR, strokeP)
        val zc = g.zoneCenter; val zw = g.zoneWidth
        strokeP.color = Color.rgb(120, 255, 160); strokeP.strokeWidth = 11f
        val oval = RectF(cx - ringR, cy - ringR, cx + ringR, cy + ringR)
        c.drawArc(oval, zc - zw / 2f - 90f, zw, false, strokeP)
        val pa = Math.toRadians((g.pulseDeg - 90f).toDouble())
        val px = cx + cos(pa).toFloat() * ringR
        val py = cy + sin(pa).toFloat() * ringR
        fill.color = Color.WHITE
        c.drawCircle(px, py, 9f, fill)
        fill.color = Species.ALL[tg.species].main
        c.drawCircle(px, py, 5f, fill)

        head.textAlign = Paint.Align.CENTER
        head.color = Species.ALL[tg.species].main
        c.drawText("L${tg.level} ${Species.ALL[tg.species].name}", cx, 26f, head)
        head.color = Color.rgb(140, 255, 210)
        small.textAlign = Paint.Align.CENTER
        c.drawText("TAP WHEN THE ORB CROSSES GREEN", cx, 46f, small)

        // Hits and strikes.
        for (i in 0 until 3) {
            fill.color = if (i < g.hits) Color.rgb(120, 255, 160) else Color.argb(70, 255, 255, 255)
            c.drawCircle(cx - 30f + i * 30f, cy + ringR + 24f, 7f, fill)
            fill.color = if (i < g.misses) Color.rgb(255, 110, 100) else Color.argb(45, 255, 255, 255)
            c.drawCircle(cx - 30f + i * 30f, cy + ringR + 44f, 5f, fill)
        }

        // Time bar.
        val frac = (g.engageTimer / GameEngine.ENGAGE_SECONDS).coerceIn(0f, 1f)
        strokeP.strokeWidth = 6f
        strokeP.color = Color.argb(70, 255, 255, 255)
        c.drawLine(cx - 90f, h - 22f, cx + 90f, h - 22f, strokeP)
        strokeP.color = if (frac > 0.3f) Color.rgb(140, 255, 210) else Color.rgb(255, 110, 100)
        c.drawLine(cx - 90f, h - 22f, cx - 90f + 180f * frac, h - 22f, strokeP)
        small.textAlign = Paint.Align.CENTER
        c.drawText("DOUBLE-TAP TO RETREAT", cx, h - 8f, small)
    }

    private fun drawResult(c: Canvas, w: Int, h: Int, t: Float) {
        val cx = w / 2f
        val sp = Species.ALL[g.resultSpecies]
        if (g.resultCaught) {
            // Radiant burst.
            for (i in 0 until 10) {
                val a = i * 0.628f + t
                strokeP.color = Color.argb(120, 255, 250, 200); strokeP.strokeWidth = 3f
                c.drawLine(
                    cx + cos(a) * 44f, h * 0.42f + sin(a) * 44f,
                    cx + cos(a) * (60f + sin(t * 6f + i) * 8f), h * 0.42f + sin(a) * (60f + sin(t * 6f + i) * 8f),
                    strokeP
                )
            }
            Sprites.creature(c, g.resultSpecies, cx, h * 0.42f, 26f, t, excited = true)
            head.textAlign = Paint.Align.CENTER
            head.color = sp.main
            c.drawText("${sp.name} CAUGHT!", cx, h * 0.66f, head)
            head.color = Color.rgb(140, 255, 210)
            body.textAlign = Paint.Align.CENTER
            c.drawText("LEVEL ${g.resultLevel} JOINS YOUR BOX", cx, h * 0.72f, body)
            accent.textAlign = Paint.Align.CENTER
            c.drawText("NEXT HUNT: L${g.spawner.level} AT ${GeoMath.prettyDistance(g.spawner.ladderDistance())}", cx, h * 0.79f, accent)
        } else {
            Sprites.creature(c, g.resultSpecies, cx + sin(t * 3f) * 30f, h * 0.42f, 20f, t, excited = true)
            head.textAlign = Paint.Align.CENTER
            head.color = Color.rgb(255, 150, 120)
            c.drawText("IT SLIPPED AWAY!", cx, h * 0.66f, head)
            head.color = Color.rgb(140, 255, 210)
            body.textAlign = Paint.Align.CENTER
            c.drawText("IT LAIRS ELSEWHERE NOW - TRACK IT", cx, h * 0.72f, body)
        }
    }

    private fun drawLoot(c: Canvas, w: Int, h: Int, t: Float) {
        val cx = w / 2f
        val open = (g.stateT * 2f).coerceAtMost(1f)
        Sprites.chest(c, cx, h * 0.42f, 30f, open)
        head.textAlign = Paint.Align.CENTER
        c.drawText("TREASURE!", cx, h * 0.63f, head)
        body.textAlign = Paint.Align.CENTER
        body.drawScaled(c, g.lootPlace, cx, h * 0.69f, w - 30f)
        Sprites.gem(c, cx - 34f, h * 0.755f, 7f)
        accent.textAlign = Paint.Align.LEFT
        c.drawText("+${g.lootEssence} ESSENCE", cx - 22f, h * 0.76f + 5f, accent)
        if (g.lootShard) {
            small.textAlign = Paint.Align.CENTER
            small.color = Color.rgb(255, 160, 255)
            c.drawText("A CHARM SHARD GLIMMERS WITHIN", cx, h * 0.83f, small)
            small.color = Color.rgb(180, 220, 245)
        }
    }

    private fun drawMenu(c: Canvas, w: Int, h: Int, name: String, rows: List<Pair<String, String>>) {
        head.textAlign = Paint.Align.CENTER
        c.drawText(name, w / 2f, 40f, head)
        val rowH = 34f
        val top = 70f
        for ((i, row) in rows.withIndex()) {
            val y = top + i * rowH
            val selected = i == g.menuIdx
            if (selected) {
                fill.color = Color.argb(70, 90, 220, 255)
                c.drawRoundRect(RectF(14f, y - 22f, w - 14f, y + 8f), 8f, 8f, fill)
                strokeP.color = Color.rgb(90, 220, 255); strokeP.strokeWidth = 2f
                c.drawRoundRect(RectF(14f, y - 22f, w - 14f, y + 8f), 8f, 8f, strokeP)
            }
            body.textAlign = Paint.Align.LEFT
            body.color = if (selected) Color.WHITE else Color.rgb(200, 225, 245)
            c.drawText(row.first, 24f, y, body)
            if (row.second.isNotEmpty()) {
                accent.textAlign = Paint.Align.RIGHT
                c.drawText(row.second, w - 24f, y, accent)
                accent.textAlign = Paint.Align.CENTER
            }
        }
        body.color = Color.rgb(235, 245, 255)
        small.textAlign = Paint.Align.CENTER
        c.drawText("SWIPE - TAP SELECTS - DOUBLE-TAP BACK", w / 2f, h - 14f, small)
    }

    private fun drawBox(c: Canvas, w: Int, h: Int, t: Float) {
        head.textAlign = Paint.Align.CENTER
        c.drawText("CREATURE BOX", w / 2f, 40f, head)
        val lines = g.boxLines()
        small.textAlign = Paint.Align.CENTER
        c.drawText(
            "${g.box.size} CAUGHT - BEST HUNT L${maxOf(g.spawner.level, 1)}",
            w / 2f, 60f, small
        )
        if (lines.isEmpty()) {
            body.textAlign = Paint.Align.CENTER
            c.drawText("THE BOX AWAITS ITS FIRST CREATURE", w / 2f, h * 0.5f, body)
            return
        }
        val rowH = 52f
        val top = 92f
        val visible = ((h - 120f) / rowH).toInt()
        for (r in 0 until visible) {
            val idx = g.boxScroll + r
            if (idx >= lines.size) break
            val (species, count, best) = lines[idx]
            val y = top + r * rowH
            Sprites.creature(c, species, 34f, y, 13f, t + species, excited = false)
            body.textAlign = Paint.Align.LEFT
            c.drawText(Species.ALL[species].name, 62f, y - 2f, body)
            small.textAlign = Paint.Align.LEFT
            c.drawText(Species.ALL[species].habitat, 62f, y + 14f, small)
            accent.textAlign = Paint.Align.RIGHT
            c.drawText("x$count  L$best", w - 16f, y + 4f, accent)
            accent.textAlign = Paint.Align.CENTER
        }
        if (lines.size > visible) {
            small.textAlign = Paint.Align.CENTER
            c.drawText("SWIPE TO SCROLL", w / 2f, h - 14f, small)
        }
    }

    private fun drawUpgrade(c: Canvas, w: Int, h: Int) {
        head.textAlign = Paint.Align.CENTER
        c.drawText("UPGRADES", w / 2f, 36f, head)
        Sprites.gem(c, w / 2f - 34f, 56f, 7f)
        accent.textAlign = Paint.Align.LEFT
        c.drawText("${g.essence} ESSENCE", w / 2f - 22f, 61f, accent)
        val rowH = 62f
        val top = 100f
        for (i in 0 until 4) {
            val y = top + i * rowH
            val selected = i == g.menuIdx
            val tier = g.tier(i)
            if (selected) {
                fill.color = Color.argb(70, 90, 220, 255)
                c.drawRoundRect(RectF(10f, y - 26f, w - 10f, y + 26f), 8f, 8f, fill)
            }
            body.textAlign = Paint.Align.LEFT
            body.color = if (selected) Color.WHITE else Color.rgb(200, 225, 245)
            c.drawText(GameEngine.UPGRADE_NAMES[i], 20f, y - 6f, body)
            small.textAlign = Paint.Align.LEFT
            c.drawText(GameEngine.UPGRADE_BLURBS[i], 20f, y + 12f, small)
            // Tier pips.
            for (p in 0 until 5) {
                fill.color = if (p < tier) Color.rgb(120, 255, 160) else Color.argb(60, 255, 255, 255)
                c.drawCircle(w - 90f + p * 14f, y - 8f, 5f, fill)
            }
            accent.textAlign = Paint.Align.RIGHT
            c.drawText(
                if (tier >= 5) "MAX" else "${GameEngine.upgradeCost(tier)}",
                w - 20f, y + 14f, accent
            )
            accent.textAlign = Paint.Align.CENTER
        }
        body.color = Color.rgb(235, 245, 255)
        small.textAlign = Paint.Align.CENTER
        c.drawText("TAP BUYS - DOUBLE-TAP BACK", w / 2f, h - 14f, small)
    }

    /** Draw text shrunk to fit a width (RPG place names can run long). */
    private fun Paint.drawScaled(c: Canvas, text: String, x: Float, y: Float, maxW: Float) {
        val old = textSize
        while (measureText(text) > maxW && textSize > 9f) textSize -= 1f
        c.drawText(text, x, y, this)
        textSize = old
    }
}
