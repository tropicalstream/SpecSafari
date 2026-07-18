package com.taphunter.render

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
import android.graphics.Typeface
import com.taphunter.engine.GameEngine
import com.taphunter.engine.Spawn
import com.taphunter.engine.Species
import com.taphunter.geo.GeoMath
import com.taphunter.geo.GeoPoint
import com.taphunter.geo.RpgNamer
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The realm map: heading-up, circular, half again the size of the Everyday
 * minimap it grew from (its whole reason to exist is the hunt, so it owns
 * the screen). Every real road and path is drawn; names arrive RPGified.
 * Palette is tuned for full sun: white-hot cores over saturated halos.
 */
class MapRenderer {

    private val roadPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(210, 240, 255); style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND
    }
    private val majorPaint = Paint(roadPaint).apply { color = Color.rgb(255, 205, 90) }
    private val pathPaint = Paint(roadPaint).apply { color = Color.rgb(150, 255, 130) }
    private val haloPaint = Paint(roadPaint).apply { color = Color.argb(70, 120, 210, 255) }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(90, 255, 255, 255); style = Paint.Style.STROKE; strokeWidth = 2f
    }
    private val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(90, 220, 255); style = Paint.Style.STROKE; strokeWidth = 3f
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val strokeP = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(255, 250, 200); textSize = 13f; typeface = Typeface.DEFAULT_BOLD
    }
    private val roadLabel = Paint(label).apply { color = Color.rgb(200, 235, 255); textSize = 12f }
    private val northPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(255, 110, 100); textSize = 15f; typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
    }
    private val clip = Path()
    private val work = Path()
    private val occupied = mutableListOf<RectF>()

    private var mPerLon = 111320.0

    fun draw(c: Canvas, w: Int, h: Int, g: GameEngine, t: Float) {
        val me = g.player ?: return
        val heading = g.heading
        // 50% larger than the Everyday widget's 210 px: a 315 px disc at 480 h.
        val radius = min(315f * (h / 480f), w - 10f) / 2f
        val cx = w / 2f
        val cy = h * 0.5f + 14f
        val scale = radius / g.zoomRadius
        mPerLon = 111320.0 * cos(Math.toRadians(me.lat))
        val hRad = Math.toRadians(heading.toDouble())
        val cosH = cos(hRad).toFloat(); val sinH = sin(hRad).toFloat()

        fun project(p: GeoPoint, out: PointF): Boolean {
            val e = ((p.lon - me.lon) * mPerLon).toFloat()
            val n = ((p.lat - me.lat) * 111320.0).toFloat()
            val sx = e * cosH - n * sinH
            val sy = -(e * sinH + n * cosH)
            out.set(cx + sx * scale, cy + sy * scale)
            return abs(sx) * scale < radius * 1.35f && abs(sy) * scale < radius * 1.35f
        }

        clip.reset(); clip.addCircle(cx, cy, radius, Path.Direction.CW)
        c.save(); c.clipPath(clip)

        // Faint range ring at half zoom for scale feel.
        c.drawCircle(cx, cy, radius * 0.5f, ringPaint)

        // Roads and paths — every one the data has.
        val pt = PointF()
        occupied.clear()
        // Creatures and chests own their pixels: labels route around them.
        for (s in g.interest()) {
            if (project(s.p, pt)) {
                occupied += RectF(pt.x - 26f, pt.y - 30f, pt.x + 46f, pt.y + 26f)
            }
        }
        // name, x, y, distance-from-wearer — the street underfoot labels first.
        val labelWanted = mutableListOf<Pair<Triple<String, Float, Float>, Float>>()
        for (road in g.roads) {
            work.reset()
            var started = false
            var anyIn = false
            var midX = 0f; var midY = 0f; var midN = 0
            for (p in road.pts) {
                val inside = project(p, pt)
                if (!started) { work.moveTo(pt.x, pt.y); started = true }
                else work.lineTo(pt.x, pt.y)
                if (inside) {
                    anyIn = true
                    val dx = pt.x - cx; val dy = pt.y - cy
                    if (dx * dx + dy * dy < radius * radius * 0.72f) {
                        midX += pt.x; midY += pt.y; midN++
                    }
                }
            }
            if (!started || !anyIn) continue
            val paint = when {
                road.isMajor -> majorPaint
                road.isPath -> pathPaint
                else -> roadPaint
            }
            haloPaint.strokeWidth = if (road.isMajor) 9f else 6f
            c.drawPath(work, haloPaint)
            paint.strokeWidth = if (road.isMajor) 4f else if (road.isPath) 2.4f else 3f
            c.drawPath(work, paint)
            if (midN > 0 && (road.name != null || road.isPath)) {
                RpgNamer.road(road.name, road.kind, road.id)?.let { fancy ->
                    val lx = midX / midN; val ly = midY / midN
                    val dx = lx - cx; val dy = ly - cy
                    // Named streets outrank unnamed path filler.
                    val rank = sqrt(dx * dx + dy * dy) + if (road.name == null) 90f else 0f
                    labelWanted += Triple(fancy, lx, ly) to rank
                }
            }
        }

        // POI markers + RPG names, nearest first, deconflicted.
        val poiLabels = g.pois
            .map { it to GeoMath.distanceM(me, it.p) }
            .filter { it.second < g.zoomRadius * 1.15f }
            .sortedBy { it.second }
            .take(9)
        for ((poi, _) in poiLabels) {
            if (!project(poi.p, pt)) continue
            fill.color = Color.rgb(255, 220, 90)
            c.drawCircle(pt.x, pt.y, 4f, fill)
            fill.color = Color.rgb(255, 250, 230)
            c.drawCircle(pt.x, pt.y, 1.8f, fill)
            place(c, RpgNamer.poi(poi.name, poi.category, poi.id), pt.x, pt.y, label)
        }
        var shown = 0
        for ((entry, _) in labelWanted.sortedBy { it.second }) {
            if (shown >= 5) break
            val (name, lx, ly) = entry
            if (place(c, name, lx, ly, roadLabel)) shown++
        }

        // Scanner ring: inside it a tap engages whatever is there.
        strokeP.color = Color.argb(110, 120, 255, 200); strokeP.strokeWidth = 2.5f
        c.drawCircle(cx, cy, g.engageRange() * scale, strokeP)

        // Sonar ping sweep.
        if (g.pingT > 0f) {
            val pr = (1.2f - g.pingT) / 1.2f * radius
            strokeP.color = Color.argb((160 * g.pingT / 1.2f).toInt(), 140, 255, 230)
            strokeP.strokeWidth = 4f
            c.drawCircle(cx, cy, pr, strokeP)
        }

        // Creatures and treasure.
        val target = g.target()
        for (s in g.interest()) {
            val onMap = project(s.p, pt)
            val dx = pt.x - cx; val dy = pt.y - cy
            val inDisc = sqrt(dx * dx + dy * dy) <= radius - 14f
            if (onMap && inDisc) drawSpawn(c, s, pt.x, pt.y, t, s === target)
        }
        c.restore()

        // Rim, north, offscreen target arrow with distance.
        c.drawCircle(cx, cy, radius, rimPaint)
        drawNorth(c, cx, cy, radius, heading)
        target?.let { tg ->
            project(tg.p, pt)
            val dx = pt.x - cx; val dy = pt.y - cy
            if (sqrt(dx * dx + dy * dy) > radius - 14f) {
                drawEdgeArrow(c, cx, cy, radius, dx, dy, tg, me, t)
            }
        }

        // The wearer: a bright arrow, always screen-up (heading-up map).
        work.reset()
        work.moveTo(cx, cy - 12f); work.lineTo(cx - 8f, cy + 9f)
        work.lineTo(cx, cy + 4f); work.lineTo(cx + 8f, cy + 9f)
        work.close()
        fill.color = Color.rgb(255, 255, 255)
        c.drawPath(work, fill)
        strokeP.color = Color.rgb(90, 220, 255); strokeP.strokeWidth = 2f
        c.drawPath(work, strokeP)
    }

    private fun drawSpawn(c: Canvas, s: Spawn, x: Float, y: Float, t: Float, isTarget: Boolean) {
        val pulse = 1f + sin(t * 4f) * 0.15f
        if (s.isCreature) {
            val sp = Species.ALL[s.species]
            fill.color = sp.main; fill.alpha = 100
            c.drawCircle(x, y, 21f * pulse, fill)
            fill.alpha = 255
            Sprites.creature(c, s.species, x, y, 10f, t, excited = false)
            fill.color = Color.rgb(20, 40, 70)
            val badge = RectF(x + 12f, y - 24f, x + 42f, y - 8f)
            c.drawRoundRect(badge, 4f, 4f, fill)
            label.color = Color.rgb(160, 255, 200)
            c.drawText("L${s.level}", badge.left + 5f, badge.bottom - 4f, label)
            label.color = Color.rgb(255, 250, 200)
        } else {
            Sprites.chest(c, x, y, 10f * pulse, 0f)
        }
        if (isTarget) {
            strokeP.color = Color.rgb(255, 255, 255); strokeP.strokeWidth = 2.5f
            c.drawCircle(x, y, 26f * pulse, strokeP)
        }
    }

    private fun drawEdgeArrow(
        c: Canvas, cx: Float, cy: Float, radius: Float,
        dx: Float, dy: Float, tg: Spawn, me: GeoPoint, t: Float
    ) {
        val a = atan2(dy, dx)
        val ax = cx + cos(a) * (radius - 6f)
        val ay = cy + sin(a) * (radius - 6f)
        val color = if (tg.isCreature) Species.ALL[tg.species].main else Color.rgb(255, 210, 40)
        val pulse = 1f + sin(t * 5f) * 0.2f
        work.reset()
        work.moveTo(ax + cos(a) * 13f * pulse, ay + sin(a) * 13f * pulse)
        work.lineTo(ax + cos(a + 2.6f) * 11f, ay + sin(a + 2.6f) * 11f)
        work.lineTo(ax + cos(a - 2.6f) * 11f, ay + sin(a - 2.6f) * 11f)
        work.close()
        fill.color = color
        c.drawPath(work, fill)
        val d = GeoMath.distanceM(me, tg.p)
        label.textAlign = Paint.Align.CENTER
        val tx = cx + cos(a) * (radius - 34f)
        val ty = cy + sin(a) * (radius - 34f) + 5f
        label.color = Color.WHITE
        c.drawText(GeoMath.prettyDistance(d), tx, ty, label)
        label.textAlign = Paint.Align.LEFT
        label.color = Color.rgb(255, 250, 200)
    }

    private fun drawNorth(c: Canvas, cx: Float, cy: Float, radius: Float, heading: Float) {
        val rel = Math.toRadians(GeoMath.relativeDeg(0f, heading).toDouble())
        val x = cx + sin(rel).toFloat() * (radius - 13f)
        val y = cy - cos(rel).toFloat() * (radius - 13f)
        c.drawText("N", x, y + 5f, northPaint)
    }

    /** Label with simple de-confliction; returns whether it was drawn. */
    private fun place(c: Canvas, text: String, x: Float, y: Float, paint: Paint): Boolean {
        val tw = paint.measureText(text)
        val rect = RectF(x + 6f, y - 13f, x + 6f + tw + 4f, y + 2f)
        if (occupied.any { RectF.intersects(it, rect) }) {
            val below = RectF(x - tw / 2f, y + 4f, x + tw / 2f + 4f, y + 19f)
            if (occupied.any { RectF.intersects(it, below) }) return false
            occupied += below
            c.drawText(text, below.left + 2f, below.bottom - 4f, paint)
            return true
        }
        occupied += rect
        c.drawText(text, rect.left + 2f, rect.bottom - 4f, paint)
        return true
    }
}
