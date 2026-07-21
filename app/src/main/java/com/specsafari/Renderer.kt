package com.specsafari

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.os.SystemClock
import com.specsafari.engine.GameEngine
import com.specsafari.shared.Species
import com.specsafari.engine.State
import com.specsafari.geo.GeoMath
import com.specsafari.render.MapRenderer
import com.specsafari.shared.Sprites
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
            State.FETCH -> drawFetch(c, w, h, t)
            State.RESCUE -> drawRescue(c, w, h, t)
            State.HUB -> drawMenu(c, w, h, "SPECSAFARI",
                (0 until g.hubRowCount()).map { g.hubRow(it) to "" })
            State.DEN -> drawDen(c, w, h, t)
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
        val wSpec = title.measureText("SPEC")
        val wAll = wSpec + title.measureText("SAFARI")
        title.color = Color.rgb(140, 255, 210)
        c.drawText("SPEC", cx - wAll / 2f, h * 0.22f, title)
        title.color = Color.WHITE
        c.drawText("SAFARI", cx - wAll / 2f + wSpec, h * 0.22f, title)
        title.textAlign = Paint.Align.CENTER
        small.textAlign = Paint.Align.CENTER
        c.drawText("THE REALM IS YOUR NEIGHBORHOOD", cx, h * 0.28f, small)

        // A parade of all twenty-four, marching in a slow circle.
        val n = Species.ALL.size
        for (i in 0 until n) {
            val a = t * 0.4f + i * (Math.PI.toFloat() * 2f / n)
            val px = cx + cos(a) * w * 0.34f
            val py = h * 0.52f + sin(a) * h * 0.17f
            Sprites.creature(c, i, px, py, 7f, t + i, excited = false)
        }
        Sprites.creature(c, (t / 3f).toInt() % n, cx, h * 0.52f, 22f, t, excited = true)

        val blink = (sin(t * 4f) + 1f) / 2f
        body.textAlign = Paint.Align.CENTER
        body.color = Color.argb((120 + 135 * blink).toInt(), 255, 255, 255)
        c.drawText("TAP TO BEGIN THE HUNT", cx, h * 0.82f, body)
        body.color = Color.rgb(235, 245, 255)
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
        val gps = if (g.player != null) "LOCATION LOCKED" else "SEARCHING FOR A FIX..."
        c.drawText(gps, cx, h * 0.58f, body)
        small.textAlign = Paint.Align.CENTER
        c.drawText(
            if (g.roads.isEmpty()) "CHARTING ROADS AND PLACES..." else "${g.roads.size} WAYS CHARTED",
            cx, h * 0.64f, small
        )
        if (g.locStatus.isNotEmpty()) c.drawText(g.locStatus, cx, h * 0.70f, small)
        if (g.player == null && g.stateT > 15f) {
            small.color = Color.rgb(255, 200, 120)
            c.drawText("THE GLASSES HAVE NO GPS OF THEIR OWN -", cx, h * 0.78f, small)
            c.drawText("START THE SPECSAFARI BEAM APP ON YOUR PHONE", cx, h * 0.83f, small)
            small.color = Color.rgb(180, 220, 245)
        }
    }

    private fun drawHunt(c: Canvas, w: Int, h: Int, t: Float) {
        map.draw(c, w, h, g, t)

        // Tight text rail down the map's left flank (Mars's layout: the disc
        // owns the upper right, words hug it, the world below stays clear).
        val railLeft = 8f
        val railRight = (map.lastCx - map.lastRadius - 8f).coerceAtLeast(96f)
        val railW = railRight - railLeft
        var y = 26f
        head.textAlign = Paint.Align.LEFT
        body.textAlign = Paint.Align.LEFT
        small.textAlign = Paint.Align.LEFT
        accent.textAlign = Paint.Align.LEFT

        val tg = g.target()
        val me = g.player
        if (tg != null && me != null) {
            val freed = g.isFreedFriend(tg)
            head.color = when {
                tg.lost -> Color.rgb(255, 150, 210)
                freed -> Color.rgb(120, 255, 150)
                tg.isCreature -> Species.ALL[tg.species].main
                else -> Color.rgb(255, 210, 40)
            }
            head.textSize = 15f
            val what = when {
                tg.lost -> "LOST - ${Species.ALL[tg.species].name}"
                freed -> "FREE - ${Species.ALL[tg.species].name}"
                tg.isCreature -> "L${tg.level} ${Species.ALL[tg.species].name}"
                else -> "TREASURE"
            }
            head.drawScaled(c, what, railLeft, y, railW)
            head.color = Color.rgb(140, 255, 210)
            head.textSize = 20f
            y += 6f
            body.textSize = 11f
            for (line in wrap(body, tg.placeName, railW, 3)) {
                y += 13f
                c.drawText(line, railLeft, y, body)
            }
            body.textSize = 15f
            y += 17f
            val d = GeoMath.distanceM(me, tg.p)
            accent.textSize = 14f
            accent.drawScaled(
                c,
                if (d <= g.rangeFor(tg)) "IN RANGE-TAP!" else GeoMath.prettyDistance(d),
                railLeft, y, railW
            )
            accent.textSize = 15f
        } else {
            head.textSize = 13f
            head.drawScaled(c, "SCOUTING...", railLeft, y, railW)
            head.textSize = 20f
        }

        // Walked + hunt stats, stacked snugly.
        y += 22f
        accent.textSize = 12f
        accent.drawScaled(c, "WALKED ${GeoMath.prettyDistance(g.sessionWalkedM)}", railLeft, y, railW)
        accent.textSize = 15f
        y += 16f
        Sprites.gem(c, railLeft + 5f, y - 4f, 5f)
        small.textSize = 12f
        c.drawText("${g.essence}", railLeft + 14f, y, small)
        y += 15f
        c.drawText("HUNT L${g.spawner.level}", railLeft, y, small)
        y += 15f
        c.drawText("${g.zoomRadius.toInt()} M ZOOM", railLeft, y, small)
        if (!g.compassLive || g.gpsAccuracy > 60f) {
            small.textSize = 10f
            small.color = Color.rgb(255, 150, 120)
            val warn = if (!g.compassLive) "COMPASS WARMING UP" else "WEAK SKY SIGNAL"
            for (line in wrap(small, warn, railW, 2)) {
                y += 12f
                c.drawText(line, railLeft, y, small)
            }
            small.color = Color.rgb(180, 220, 245)
        }
        small.textSize = 13f

        // A cache within 5-60 m throws a gold arrow across the disc pointing
        // exactly where to walk — screen-relative to where the hunter faces.
        g.treasureHint()?.let { (bearing, dist, _) ->
            val cx = map.lastCx; val cy = map.lastCy; val r = map.lastRadius
            val rel = ((bearing - g.heading) * Math.PI / 180.0).toFloat()
            val dx = kotlin.math.sin(rel); val dy = -kotlin.math.cos(rel)
            // Still, quiet guidance: the arrow points; the SPECTACLE lives in
            // the hologram layer, not the chrome (Mars: no distracting motion).
            val tip = r * 0.86f
            fill.color = Color.argb(215, 255, 210, 40)
            path.reset()
            path.moveTo(cx + dx * tip, cy + dy * tip)                        // arrowhead
            val bx = cx + dx * tip * 0.66f; val by = cy + dy * tip * 0.66f
            val px = -dy; val py = dx                                        // perpendicular
            path.lineTo(bx + px * r * 0.11f, by + py * r * 0.11f)
            path.lineTo(bx - px * r * 0.11f, by - py * r * 0.11f)
            path.close()
            c.drawPath(path, fill)
            strokeP.color = Color.argb(190, 255, 230, 120); strokeP.strokeWidth = r * 0.04f
            c.drawLine(cx, cy, bx, by, strokeP)
            accent.textAlign = Paint.Align.CENTER
            accent.color = Color.rgb(255, 220, 90); accent.textSize = 12f
            c.drawText("CACHE ${GeoMath.prettyDistance(dist)}", cx, cy + r + 14f, accent)
            accent.textSize = 15f; accent.textAlign = Paint.Align.LEFT
        }

        // A lost friend within reach throws a heart-lit trail across the disc,
        // pointing exactly where to walk to welcome it home.
        g.lostHint()?.let { (bearing, dist, _) ->
            val cx = map.lastCx; val cy = map.lastCy; val r = map.lastRadius
            val rel = ((bearing - g.heading) * Math.PI / 180.0).toFloat()
            val dx = kotlin.math.sin(rel); val dy = -kotlin.math.cos(rel)
            val tip = r * 0.86f
            fill.color = Color.argb(215, 255, 120, 190)
            path.reset()
            path.moveTo(cx + dx * tip, cy + dy * tip)
            val bx = cx + dx * tip * 0.66f; val by = cy + dy * tip * 0.66f
            val px = -dy; val py = dx
            path.lineTo(bx + px * r * 0.11f, by + py * r * 0.11f)
            path.lineTo(bx - px * r * 0.11f, by - py * r * 0.11f)
            path.close()
            c.drawPath(path, fill)
            strokeP.color = Color.argb(190, 255, 180, 220); strokeP.strokeWidth = r * 0.04f
            c.drawLine(cx, cy, bx, by, strokeP)
            accent.textAlign = Paint.Align.CENTER
            accent.color = Color.rgb(255, 160, 210); accent.textSize = 12f
            c.drawText("LOST FRIEND ${GeoMath.prettyDistance(dist)}", cx, cy + r + 28f, accent)
            accent.textSize = 15f; accent.textAlign = Paint.Align.LEFT
            accent.color = Color.rgb(255, 220, 90)
        }

        // Trail finds announce themselves under the disc.
        if (g.toastT > 0f) {
            accent.textAlign = Paint.Align.CENTER
            accent.color = Color.argb((255 * (g.toastT / 4f).coerceAtMost(1f)).toInt(), 255, 160, 220)
            accent.drawScaled(c, g.toastText, map.lastCx, map.lastCy + map.lastRadius + 22f,
                map.lastRadius * 2f)
            accent.color = Color.rgb(255, 220, 90)
            accent.textAlign = Paint.Align.LEFT
        }
    }

    private fun drawDen(c: Canvas, w: Int, h: Int, t: Float) {
        g.denW = w.toFloat(); g.denH = h.toFloat()
        head.textAlign = Paint.Align.CENTER
        c.drawText("CREATURE DEN", w / 2f, 30f, head)
        if (g.denPets.isEmpty()) {
            body.textAlign = Paint.Align.CENTER
            c.drawText("CATCH A CREATURE FIRST -", w / 2f, h * 0.45f, body)
            c.drawText("THE DEN AWAITS ITS RESIDENTS", w / 2f, h * 0.52f, body)
            return
        }
        // A soft meadow floor line so the den feels like a place.
        strokeP.color = Color.argb(60, 140, 255, 190); strokeP.strokeWidth = 2f
        c.drawLine(16f, h - 84f, w - 16f, h - 84f, strokeP)

        val sel = g.denPets.getOrNull(g.denSel)
        for (pet in g.denPets) {
            // Every resident keeps a nest; sleepers breathe there in peace.
            strokeP.color = Color.argb(70, 200, 170, 120); strokeP.strokeWidth = 2f
            c.drawOval(pet.homeX - 16f, pet.homeY + 8f, pet.homeX + 16f, pet.homeY + 16f, strokeP)
            val excited = pet.happyT > 0f
            if (pet === sel) {
                strokeP.color = Color.rgb(255, 255, 255); strokeP.strokeWidth = 2f
                c.drawCircle(pet.x, pet.y, 26f + sin(t * 4f) * 2f, strokeP)
            }
            Sprites.creature(c, pet.species, pet.x, pet.y, 13f, t + pet.phase, excited)
            if (pet.sleeping) {
                small.textAlign = Paint.Align.LEFT
                small.color = Color.rgb(150, 190, 255)
                c.drawText("z", pet.x + 12f, pet.y - 18f - sin(t * 2f) * 3f, small)
                c.drawText("Z", pet.x + 18f, pet.y - 28f - sin(t * 2f + 1f) * 3f, small)
                small.color = Color.rgb(180, 220, 245)
            }
            if (excited) {
                // Hearts float up while the joy lasts.
                body.textAlign = Paint.Align.CENTER
                body.color = Color.rgb(255, 120, 190)
                val rise = (2.2f - pet.happyT) * 14f
                c.drawText("♥", pet.x - 10f, pet.y - 24f - rise, body)
                if (pet.happyT > 1f) c.drawText("♥", pet.x + 12f, pet.y - 30f - rise * 0.7f, body)
                body.color = Color.rgb(235, 245, 255)
            }
        }

        // The selected resident introduces itself.
        sel?.let { pet ->
            val sp = Species.ALL[pet.species]
            head.textSize = 15f
            head.color = sp.main
            head.textAlign = Paint.Align.LEFT
            c.drawText(sp.name, 14f, 56f, head)
            head.color = Color.rgb(140, 255, 210)
            head.textSize = 20f
            small.textAlign = Paint.Align.LEFT
            small.color = Color.rgb(255, 220, 90)
            var hearts = ""
            for (i in 0 until 5) hearts += if (i < g.bondHearts(pet.species)) "♥" else "♡"
            c.drawText("THE ${sp.temperament} ONE  $hearts", 14f, 72f, small)
            small.color = Color.rgb(180, 220, 245)
            small.drawScaled(c, sp.nature, 14f, 88f, w - 28f)
        }

        // Pantry and hints.
        small.textAlign = Paint.Align.LEFT
        fill.color = Color.rgb(255, 120, 190)
        c.drawCircle(18f, h - 66f, 5f, fill)
        c.drawText("x${g.berries} BERRIES", 28f, h - 62f, small)
        Sprites.gem(c, 110f, h - 66f, 5f)
        c.drawText("${g.essence}", 120f, h - 62f, small)
        small.textAlign = Paint.Align.CENTER
        c.drawText("TAP PET · SWIPE UP BERRY · DOWN TREAT(5)", w / 2f, h - 40f, small)
        c.drawText("LEFT/RIGHT CHOOSE · DOUBLE-TAP BACK", w / 2f, h - 24f, small)
        if (g.toastT > 0f) {
            accent.textAlign = Paint.Align.CENTER
            c.drawText(g.toastText, w / 2f, h - 100f, accent)
            accent.textAlign = Paint.Align.LEFT
        }
    }

    /** Greedy word wrap into at most maxLines lines of maxW pixels. */
    private fun wrap(paint: Paint, text: String, maxW: Float, maxLines: Int): List<String> {
        val out = mutableListOf<String>()
        var line = ""
        for (word in text.split(' ')) {
            val candidate = if (line.isEmpty()) word else "$line $word"
            if (paint.measureText(candidate) <= maxW || line.isEmpty()) {
                line = candidate
            } else {
                out += line
                line = word
                if (out.size == maxLines - 1) break
            }
        }
        if (line.isNotEmpty() && out.size < maxLines) out += line
        return out
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
            c.drawText(if (g.reunion) "${sp.name} FOUND!" else "${sp.name} CAUGHT!", cx, h * 0.66f, head)
            head.color = Color.rgb(140, 255, 210)
            body.textAlign = Paint.Align.CENTER
            if (g.reunion) {
                body.color = Color.rgb(255, 170, 220)
                c.drawText("YOUR LOST FRIEND LEAPS HOME - ♥ +", cx, h * 0.72f, body)
                body.color = Color.rgb(140, 255, 210)
                accent.textAlign = Paint.Align.CENTER
                c.drawText("BACK IN YOUR BOX, LEVEL ${g.resultLevel}", cx, h * 0.79f, accent)
            } else {
                c.drawText("LEVEL ${g.resultLevel} JOINS YOUR BOX", cx, h * 0.72f, body)
                accent.textAlign = Paint.Align.CENTER
                c.drawText("NEXT HUNT: L${g.spawner.level} AT ${GeoMath.prettyDistance(g.spawner.ladderDistance())}", cx, h * 0.79f, accent)
            }
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

    /** Stable pseudo-random in [0,1) from a seed — for frame-consistent particles. */
    private fun hashUnit(s: Float): Float { val v = sin(s) * 43758.5453f; return v - kotlin.math.floor(v) }

    private fun drawLoot(c: Canvas, w: Int, h: Int, t: Float) {
        val cx = w / 2f
        val cyc = h * 0.4f
        val open = (g.stateT * 2f).coerceAtMost(1f)
        val gotLoot = g.lootEssence > 0
        val age = g.stateT - 0.30f          // the reward bursts as the lid lifts

        // A glow bloom behind the chest, brightest as it opens, then easing off.
        if (gotLoot) {
            val bloom = open * (1f - (age * 0.7f).coerceIn(0f, 1f))
            if (bloom > 0.01f) {
                fill.style = Paint.Style.FILL
                for (r in 3 downTo 1) {
                    fill.color = Color.argb((46f * bloom * r / 3f).toInt().coerceIn(0, 255), 255, 224, 120)
                    c.drawCircle(cx, cyc, 24f + r * 18f, fill)
                }
            }
            // A single shockwave ring snapping outward at the moment of opening.
            if (age in 0f..0.55f) {
                val k = age / 0.55f
                strokeP.style = Paint.Style.STROKE
                strokeP.color = Color.argb((200 * (1f - k)).toInt(), 255, 240, 180)
                strokeP.strokeWidth = 4f * (1f - k) + 1f
                c.drawCircle(cx, cyc, 22f + k * 300f, strokeP)
            }
        }

        Sprites.chest(c, cx, cyc, 30f, open)

        // A fountain of essence coins + sparkles arcing up out of the chest.
        if (gotLoot && age > 0f) {
            for (i in 0 until 22) {
                val r1 = hashUnit(i * 1.7f); val r2 = hashUnit(i * 3.3f + 1.1f); val r3 = hashUnit(i * 5.1f + 2.7f)
                val life = 1.05f + r3 * 0.55f
                if (age >= life) continue
                val ang = -1.5708f + (r1 - 0.5f) * 2.3f            // upward fan, ±~66°
                val spd = 120f + r2 * 170f
                val px = cx + cos(ang) * spd * age
                val py = cyc + sin(ang) * spd * age + 170f * age * age   // gravity pulls them back down
                val a = (255 * (1f - age / life)).toInt().coerceIn(0, 255)
                if (i % 5 == 0) {                                  // white sparkle: a twinkling plus
                    strokeP.style = Paint.Style.STROKE; strokeP.strokeWidth = 2f
                    strokeP.color = Color.argb(a, 255, 255, 255)
                    val s = 3f + r3 * 2.5f + sin(t * 12f + i) * 1.2f
                    c.drawLine(px - s, py, px + s, py, strokeP)
                    c.drawLine(px, py - s, px, py + s, strokeP)
                } else {                                           // gold coin with a bright top edge
                    val rad = 2.6f + r3 * 2.4f
                    fill.style = Paint.Style.FILL
                    fill.color = Color.argb(a, 255, 205, 70)
                    c.drawCircle(px, py, rad, fill)
                    fill.color = Color.argb((a * 0.8f).toInt(), 255, 240, 170)
                    c.drawCircle(px - rad * 0.3f, py - rad * 0.35f, rad * 0.45f, fill)
                }
            }
        }

        head.textAlign = Paint.Align.CENTER
        head.color = Color.rgb(140, 255, 210)
        c.drawText(if (g.lootEssence > 0) "TREASURE!" else "THE CACHE WAS EMPTY", cx, h * 0.6f, head)
        body.textAlign = Paint.Align.CENTER
        body.drawScaled(c, g.lootPlace, cx, h * 0.66f, w - 30f)
        if (g.lootEssence > 0) {
            val pop = 1f + 0.6f * (1f - (g.stateT / 0.5f).coerceIn(0f, 1f))   // essence line lands with a pop
            c.save(); c.scale(pop, pop, cx, h * 0.725f)
            Sprites.gem(c, cx - 34f, h * 0.725f, 7f)
            accent.textAlign = Paint.Align.LEFT
            c.drawText("+${g.lootEssence} ESSENCE", cx - 22f, h * 0.725f + 5f, accent)
            c.restore()
        }
        if (g.fetchWasQuest) {
            small.textAlign = Paint.Align.CENTER
            val sp = Species.ALL[g.fetchSpecies]
            if (g.fetchReturned) {
                // The hero returns, trotting into frame.
                Sprites.creature(c, g.fetchSpecies, cx, h * 0.83f, 16f, t, excited = true)
                small.color = Color.rgb(140, 255, 210)
                c.drawText("${sp.name} RETURNS, HEART FULL (+♥)", cx, h * 0.92f, small)
            } else {
                small.color = Color.rgb(255, 170, 120)
                c.drawText("${sp.name} WANDERED OFF —", cx, h * 0.85f, small)
                c.drawText("WALK THE WILDS TO FIND IT AGAIN", cx, h * 0.9f, small)
            }
            small.color = Color.rgb(180, 220, 245)
        } else if (g.lootShard) {
            small.textAlign = Paint.Align.CENTER
            small.color = Color.rgb(255, 160, 255)
            c.drawText("A CHARM SHARD GLIMMERS WITHIN", cx, h * 0.8f, small)
            small.color = Color.rgb(180, 220, 245)
        }
    }

    private fun drawFetch(c: Canvas, w: Int, h: Int, t: Float) {
        val cx = w / 2f
        // The cache, and the eager volunteer bouncing beside it.
        val open = 0.15f + 0.1f * kotlin.math.sin(t * 3f)
        Sprites.chest(c, cx + 40f, h * 0.4f, 26f, open)
        val bob = kotlin.math.abs(kotlin.math.sin(t * 5f)) * 8f
        Sprites.creature(c, g.fetchSpecies, cx - 42f, h * 0.4f - bob, 22f, t, excited = true)
        // The dotted path between them, the errand it wants to run.
        strokeP.color = Color.rgb(255, 220, 120); strokeP.strokeWidth = 3f
        var dx = cx - 20f
        while (dx < cx + 22f) { c.drawCircle(dx, h * 0.4f, 2.2f, fill.apply { color = Color.rgb(255, 220, 120) }); dx += 9f }

        head.textAlign = Paint.Align.CENTER
        head.color = Color.rgb(255, 210, 40)
        c.drawText("A CACHE!", cx, h * 0.6f, head)
        head.color = Color.rgb(140, 255, 210)
        body.textAlign = Paint.Align.CENTER
        val sp = Species.ALL[g.fetchSpecies]
        body.drawScaled(c, "Send L${g.fetchLevel} ${sp.name} to fetch it?", cx, h * 0.68f, w - 24f)
        small.textAlign = Paint.Align.CENTER
        small.color = Color.rgb(255, 220, 90)
        c.drawText("TAP: SEND  ${bondHeartStr(g.fetchSpecies)}", cx, h * 0.76f, small)
        small.color = Color.rgb(180, 220, 245)
        c.drawText("DOUBLE-TAP: OPEN IT YOURSELF", cx, h * 0.83f, small)
    }

    private fun drawRescue(c: Canvas, w: Int, h: Int, t: Float) {
        val cx = w / 2f; val cy = h * 0.44f
        val p = (g.fetchProgress()).coerceIn(0f, 1f)
        // Out-and-back: the volunteer runs to the chest, then home.
        val leg = if (p < 0.5f) p * 2f else (1f - p) * 2f
        val runX = cx - 70f + leg * 140f
        Sprites.chest(c, cx + 70f, cy, 24f, if (p > 0.45f) 1f else 0.1f)
        val bob = kotlin.math.abs(kotlin.math.sin(t * 12f)) * 7f
        Sprites.creature(c, g.fetchSpecies, runX, cy - bob, 20f, t, excited = true)
        // Dust puffs behind the runner.
        fill.color = Color.argb(90, 220, 230, 255)
        for (i in 0..2) c.drawCircle(runX - (if (p < 0.5f) 1f else -1f) * (10f + i * 7f),
            cy + 6f, 3f - i, fill)
        head.textAlign = Paint.Align.CENTER
        head.color = Color.rgb(140, 255, 210)
        c.drawText(if (p < 0.5f) "FETCHING..." else "HURRYING HOME...", cx, h * 0.66f, head)
    }

    /** Little heart string for the fetch prompt (return-odds hint). */
    private fun bondHeartStr(species: Int): String {
        val n = g.bondHearts(species)
        return "♥".repeat(n) + "♡".repeat((5 - n).coerceAtLeast(0))
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
            val hearts = g.bondHearts(species)
            c.drawText((if (hearts > 0) "♥".repeat(hearts) + " " else "") + "x$count  L$best",
                w - 16f, y + 4f, accent)
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
