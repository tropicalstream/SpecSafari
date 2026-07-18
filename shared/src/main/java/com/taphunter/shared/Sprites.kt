package com.taphunter.shared

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import com.taphunter.shared.Species
import kotlin.math.cos
import kotlin.math.sin

/**
 * Vector creatures, drawn bright for daylight on the waveguide: saturated
 * body, near-white accents, thick strokes, no thin details. `s` is the body
 * radius; every critter fits in roughly a 3s x 3s box around (x, y).
 */
object Sprites {

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val path = Path()

    fun creature(c: Canvas, species: Int, x: Float, y0: Float, s: Float, t: Float, excited: Boolean) {
        val sp = Species.ALL[species.coerceIn(0, Species.ALL.size - 1)]
        val bob = sin(t * (if (excited) 9f else 3f)) * s * 0.08f
        val y = y0 + bob
        glow.color = sp.main; glow.alpha = 60
        c.drawCircle(x, y, s * 1.6f, glow)

        when (species) {
            0 -> { // LEAFLING — sprout-headed round body, leaf ears
                body(c, sp, x, y, s)
                leaf(c, sp.accent, x - s * 0.9f, y - s * 0.9f, s * 0.66f, -40f)
                leaf(c, sp.accent, x + s * 0.9f, y - s * 0.9f, s * 0.66f, 40f)
                stroke.color = sp.accent; stroke.strokeWidth = s * 0.16f
                c.drawLine(x, y - s, x, y - s * 1.5f, stroke)
                leaf(c, sp.main, x, y - s * 1.75f, s * 0.5f, sin(t * 2f) * 14f)
                eyes(c, x, y, s)
            }
            1 -> { // THORNPUP — spiky lime pup
                for (i in 0..4) {
                    val a = -150f + i * 30f
                    spike(c, sp.accent, x, y, s, a)
                }
                body(c, sp, x, y, s)
                ear(c, sp.accent, x - s * 0.62f, y - s * 0.85f, s * 0.5f)
                ear(c, sp.accent, x + s * 0.62f, y - s * 0.85f, s * 0.5f)
                snout(c, sp.accent, x, y + s * 0.32f, s * 0.34f)
                eyes(c, x, y - s * 0.12f, s)
            }
            2 -> { // PUDDLIM — droplet blob
                path.reset()
                path.moveTo(x, y - s * 1.45f)
                path.cubicTo(x + s * 1.15f, y - s * 0.4f, x + s * 1.05f, y + s * 0.95f, x, y + s * 0.95f)
                path.cubicTo(x - s * 1.05f, y + s * 0.95f, x - s * 1.15f, y - s * 0.4f, x, y - s * 1.45f)
                fill.color = sp.main; c.drawPath(path, fill)
                fill.color = sp.accent
                c.drawCircle(x - s * 0.35f, y - s * 0.45f, s * 0.22f, fill)
                eyes(c, x, y + s * 0.1f, s)
                wave(c, sp.accent, x, y + s * 0.6f, s * 0.7f, t)
            }
            3 -> { // EMBERLING — flame imp
                flame(c, sp.accent, x, y - s * 1.15f, s * 0.75f, t * 5f)
                flame(c, sp.main, x - s * 0.7f, y - s * 0.85f, s * 0.5f, t * 5f + 2f)
                flame(c, sp.main, x + s * 0.7f, y - s * 0.85f, s * 0.5f, t * 5f + 4f)
                body(c, sp, x, y, s)
                eyes(c, x, y - s * 0.05f, s)
                grin(c, sp.accent, x, y + s * 0.4f, s * 0.5f)
            }
            4 -> { // BOOKWYRM — spectacled mini dragon
                tail(c, sp.main, x + s * 1.05f, y + s * 0.5f, s, t)
                body(c, sp, x, y, s)
                horn(c, sp.accent, x - s * 0.45f, y - s * 0.95f, s * 0.42f)
                horn(c, sp.accent, x + s * 0.45f, y - s * 0.95f, s * 0.42f)
                stroke.color = sp.accent; stroke.strokeWidth = s * 0.1f
                c.drawCircle(x - s * 0.34f, y - s * 0.1f, s * 0.3f, stroke)
                c.drawCircle(x + s * 0.34f, y - s * 0.1f, s * 0.3f, stroke)
                c.drawLine(x - s * 0.05f, y - s * 0.1f, x + s * 0.05f, y - s * 0.1f, stroke)
                pupil(c, x - s * 0.34f, y - s * 0.1f, s * 0.13f)
                pupil(c, x + s * 0.34f, y - s * 0.1f, s * 0.13f)
            }
            5 -> { // LUXMOTH — glowing moth
                wing(c, sp.main, x - s * 1.15f, y - s * 0.25f, s * 1.05f, -20f + sin(t * 8f) * 12f)
                wing(c, sp.main, x + s * 1.15f, y - s * 0.25f, s * 1.05f, 200f - sin(t * 8f) * 12f)
                body(c, sp, x, y, s * 0.7f)
                stroke.color = sp.accent; stroke.strokeWidth = s * 0.12f
                c.drawLine(x - s * 0.2f, y - s * 0.7f, x - s * 0.55f, y - s * 1.3f, stroke)
                c.drawLine(x + s * 0.2f, y - s * 0.7f, x + s * 0.55f, y - s * 1.3f, stroke)
                fill.color = sp.accent
                c.drawCircle(x - s * 0.55f, y - s * 1.3f, s * 0.16f, fill)
                c.drawCircle(x + s * 0.55f, y - s * 1.3f, s * 0.16f, fill)
                eyes(c, x, y - s * 0.1f, s * 0.8f)
            }
            6 -> { // COINIX — winged coin
                wing(c, sp.accent, x - s * 1.1f, y, s * 0.7f, -30f + sin(t * 10f) * 18f)
                wing(c, sp.accent, x + s * 1.1f, y, s * 0.7f, 210f - sin(t * 10f) * 18f)
                fill.color = sp.main; c.drawCircle(x, y, s, fill)
                stroke.color = sp.accent; stroke.strokeWidth = s * 0.14f
                c.drawCircle(x, y, s * 0.72f, stroke)
                star(c, sp.accent, x, y, s * 0.42f)
                eyes(c, x, y - s * 0.28f, s * 0.7f)
            }
            7 -> { // FERROKIT — angular fox kit
                ear(c, sp.main, x - s * 0.6f, y - s * 1.0f, s * 0.62f)
                ear(c, sp.main, x + s * 0.6f, y - s * 1.0f, s * 0.62f)
                path.reset()
                path.moveTo(x, y - s)
                path.lineTo(x + s, y + s * 0.35f)
                path.lineTo(x + s * 0.5f, y + s)
                path.lineTo(x - s * 0.5f, y + s)
                path.lineTo(x - s, y + s * 0.35f)
                path.close()
                fill.color = sp.main; c.drawPath(path, fill)
                snout(c, sp.accent, x, y + s * 0.42f, s * 0.3f)
                eyes(c, x, y - s * 0.05f, s)
                stroke.color = sp.accent; stroke.strokeWidth = s * 0.1f
                c.drawLine(x, y - s, x, y - s * 1.45f, stroke)
                fill.color = sp.accent; c.drawCircle(x, y - s * 1.5f, s * 0.14f, fill)
            }
            8 -> { // VOLTLING — living spark
                path.reset()
                path.moveTo(x - s * 0.25f, y - s * 1.35f)
                path.lineTo(x + s * 0.55f, y - s * 0.3f)
                path.lineTo(x + s * 0.1f, y - s * 0.25f)
                path.lineTo(x + s * 0.45f, y + s * 1.05f)
                path.lineTo(x - s * 0.55f, y - s * 0.1f)
                path.lineTo(x - s * 0.1f, y - s * 0.15f)
                path.close()
                fill.color = sp.main; c.drawPath(path, fill)
                for (i in 0..2) {
                    val a = t * 6f + i * 2.1f
                    fill.color = sp.accent
                    c.drawCircle(x + cos(a) * s * 1.35f, y + sin(a) * s * 1.35f, s * 0.12f, fill)
                }
                eyes(c, x, y - s * 0.55f, s * 0.7f)
            }
            9 -> { // GUSTRIL — breeze bird
                wing(c, sp.accent, x - s * 0.95f, y - s * 0.15f, s * 0.95f, -35f + sin(t * 7f) * 20f)
                wing(c, sp.accent, x + s * 0.95f, y - s * 0.15f, s * 0.95f, 215f - sin(t * 7f) * 20f)
                body(c, sp, x, y, s * 0.9f)
                beak(c, sp.accent, x, y + s * 0.25f, s * 0.35f)
                eyes(c, x, y - s * 0.2f, s * 0.9f)
                stroke.color = sp.accent; stroke.strokeWidth = s * 0.09f
                c.drawLine(x - s * 1.9f, y + s * 0.7f, x - s * 1.1f, y + s * 0.7f, stroke)
                c.drawLine(x - s * 2.1f, y + s * 0.95f, x - s * 1.3f, y + s * 0.95f, stroke)
            }
            10 -> { // SHADEPAW — magenta cat
                ear(c, sp.main, x - s * 0.62f, y - s * 0.95f, s * 0.6f)
                ear(c, sp.main, x + s * 0.62f, y - s * 0.95f, s * 0.6f)
                body(c, sp, x, y, s)
                tail(c, sp.main, x - s * 1.05f, y + s * 0.4f, s, t + 1.5f)
                catEyes(c, sp.accent, x, y - s * 0.08f, s)
                whiskers(c, sp.accent, x, y + s * 0.3f, s)
            }
            11 -> { // PRISMKIN — living crystal
                path.reset()
                for (i in 0 until 6) {
                    val a = (i * 60f - 90f + t * 20f) * (Math.PI / 180f).toFloat()
                    val px = x + cos(a) * s * 1.15f
                    val py = y + sin(a) * s * 1.15f
                    if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
                }
                path.close()
                fill.color = sp.main; c.drawPath(path, fill)
                stroke.color = sp.accent; stroke.strokeWidth = s * 0.12f
                c.drawPath(path, stroke)
                for (i in 0 until 3) {
                    val a = (i * 120f + t * 40f) * (Math.PI / 180f).toFloat()
                    stroke.strokeWidth = s * 0.07f
                    c.drawLine(x, y, x + cos(a) * s * 0.95f, y + sin(a) * s * 0.95f, stroke)
                }
                eyes(c, x, y, s * 0.85f)
            }
            12 -> { // WISPLET — freelance will-o'-the-wisp
                val flick = 200 + (sin(t * 7f) * 55).toInt()
                fill.color = sp.main; fill.alpha = flick
                c.drawCircle(x, y, s * 0.85f, fill)
                fill.alpha = 255
                flame(c, sp.accent, x, y - s * 0.5f, s * 0.55f, t * 6f)
                for (i in 0..2) {
                    fill.color = sp.main; fill.alpha = 150 - i * 40
                    c.drawCircle(x - s * (0.5f + i * 0.45f) * sin(t * 2f + i),
                        y + s * (0.9f + i * 0.35f), s * (0.28f - i * 0.07f), fill)
                }
                fill.alpha = 255
                eyes(c, x, y, s * 0.8f)
            }
            13 -> { // MOONHARE — the moon rabbit's commuting cousin
                tallEar(c, sp.main, x - s * 0.34f, y - s * 0.85f, s, -8f)
                tallEar(c, sp.main, x + s * 0.34f, y - s * 0.85f, s, 8f)
                body(c, sp, x, y, s * 0.95f)
                stroke.color = sp.accent; stroke.strokeWidth = s * 0.14f
                path.reset()
                path.moveTo(x - s * 0.3f, y + s * 0.45f)
                path.quadTo(x, y + s * 0.72f, x + s * 0.3f, y + s * 0.45f)
                c.drawPath(path, stroke)
                eyes(c, x, y - s * 0.1f, s)
            }
            14 -> { // KELPLING — kelpie foal, mane still dripping
                wave(c, sp.accent, x - s * 0.1f, y - s * 0.95f, s * 0.8f, t)
                wave(c, sp.accent, x - s * 0.2f, y - s * 0.7f, s * 0.7f, t + 1f)
                body(c, sp, x, y, s * 0.95f)
                snout(c, sp.accent, x, y + s * 0.4f, s * 0.36f)
                eyes(c, x, y - s * 0.15f, s)
                fill.color = sp.accent
                c.drawCircle(x - s * 0.85f, y + s * 0.75f, s * 0.12f, fill)
                c.drawCircle(x + s * 0.9f, y + s * 0.6f, s * 0.09f, fill)
            }
            15 -> { // PUCKLE — hearth brownie in a pointed hood
                body(c, sp, x, y, s * 0.95f)
                path.reset()
                path.moveTo(x - s * 0.85f, y - s * 0.3f)
                path.lineTo(x, y - s * 1.6f)
                path.lineTo(x + s * 0.85f, y - s * 0.3f)
                path.close()
                fill.color = sp.accent; c.drawPath(path, fill)
                fill.color = sp.main
                c.drawCircle(x, y - s * 1.55f, s * 0.16f, fill)
                eyes(c, x, y + s * 0.05f, s * 0.9f)
                grin(c, sp.accent, x, y + s * 0.5f, s * 0.4f)
            }
            16 -> { // DREAMBAKU — the textbook nightmare-sipper
                ear(c, sp.accent, x - s * 0.6f, y - s * 0.75f, s * 0.42f)
                ear(c, sp.accent, x + s * 0.6f, y - s * 0.75f, s * 0.42f)
                body(c, sp, x, y, s)
                stroke.color = sp.accent; stroke.strokeWidth = s * 0.22f
                path.reset()
                path.moveTo(x, y + s * 0.15f)
                path.quadTo(x + s * 0.5f, y + s * 0.5f, x + s * 0.35f + sin(t * 2f) * s * 0.1f, y + s * 0.95f)
                c.drawPath(path, stroke)
                // Asleep on its feet: eyes are gentle closed arcs.
                stroke.strokeWidth = s * 0.1f
                path.reset(); path.moveTo(x - s * 0.45f, y - s * 0.1f)
                path.quadTo(x - s * 0.3f, y, x - s * 0.15f, y - s * 0.1f); c.drawPath(path, stroke)
                path.reset(); path.moveTo(x + s * 0.15f, y - s * 0.1f)
                path.quadTo(x + s * 0.3f, y, x + s * 0.45f, y - s * 0.1f); c.drawPath(path, stroke)
                star(c, sp.accent, x - s * 1.1f, y - s * 1.0f, s * 0.2f)
            }
            17 -> { // GLIMMERFOX — two-tailed apprentice kitsune
                tail(c, sp.main, x - s * 1.05f, y + s * 0.35f, s, t)
                tail(c, sp.main, x + s * 1.05f, y + s * 0.35f, s, t + 2f)
                fill.color = sp.accent
                c.drawCircle(x - s * 0.85f, y - s * 0.75f, s * 0.14f, fill)
                c.drawCircle(x + s * 0.85f, y - s * 0.75f, s * 0.14f, fill)
                ear(c, sp.main, x - s * 0.5f, y - s * 0.85f, s * 0.55f)
                ear(c, sp.main, x + s * 0.5f, y - s * 0.85f, s * 0.55f)
                body(c, sp, x, y, s * 0.95f)
                snout(c, sp.accent, x, y + s * 0.4f, s * 0.3f)
                eyes(c, x, y - s * 0.1f, s)
            }
            18 -> { // GEMBACK — carbuncle, gem bashfully leaf-covered
                body(c, sp, x, y, s)
                gem(c, x, y - s * 1.05f, s * 0.42f, sp.accent)
                leaf(c, sp.main, x + s * 0.3f, y - s * 1.15f, s * 0.4f, 60f)
                ear(c, sp.main, x - s * 0.55f, y - s * 0.7f, s * 0.35f)
                ear(c, sp.main, x + s * 0.55f, y - s * 0.7f, s * 0.35f)
                eyes(c, x, y, s * 0.9f)
            }
            19 -> { // CLAYWARD — honorably discharged pocket golem
                fill.color = sp.main
                c.drawRoundRect(RectF(x - s * 0.85f, y - s * 0.95f, x + s * 0.85f, y + s * 0.95f),
                    s * 0.25f, s * 0.25f, fill)
                fill.color = sp.accent
                c.drawRoundRect(RectF(x - s * 1.2f, y - s * 0.15f, x - s * 0.75f, y + s * 0.55f),
                    s * 0.15f, s * 0.15f, fill)
                c.drawRoundRect(RectF(x + s * 0.75f, y - s * 0.15f, x + s * 1.2f, y + s * 0.55f),
                    s * 0.15f, s * 0.15f, fill)
                stroke.color = sp.accent; stroke.strokeWidth = s * 0.08f
                c.drawLine(x - s * 0.5f, y + s * 0.45f, x - s * 0.15f, y + s * 0.65f, stroke)
                pupil(c, x - s * 0.3f, y - s * 0.25f, s * 0.14f)
                pupil(c, x + s * 0.3f, y - s * 0.25f, s * 0.14f)
                fill.color = sp.accent
                c.drawCircle(x - s * 0.3f, y - s * 0.25f, s * 0.06f, fill)
                c.drawCircle(x + s * 0.3f, y - s * 0.25f, s * 0.06f, fill)
            }
            20 -> { // SKYDRUM — fledgling thunderbird
                wing(c, sp.main, x - s * 0.95f, y - s * 0.15f, s * 0.95f, -35f + sin(t * 5f) * 15f)
                wing(c, sp.main, x + s * 0.95f, y - s * 0.15f, s * 0.95f, 215f - sin(t * 5f) * 15f)
                body(c, sp, x, y, s * 0.92f)
                spike(c, sp.accent, x, y - s * 0.4f, s * 0.5f, -90f)
                stroke.color = sp.accent; stroke.strokeWidth = s * 0.13f
                path.reset()
                path.moveTo(x - s * 0.2f, y + s * 0.15f)
                path.lineTo(x + s * 0.1f, y + s * 0.4f)
                path.lineTo(x - s * 0.1f, y + s * 0.45f)
                path.lineTo(x + s * 0.2f, y + s * 0.75f)
                c.drawPath(path, stroke)
                beak(c, sp.accent, x, y - s * 0.05f, s * 0.3f)
                eyes(c, x, y - s * 0.35f, s * 0.8f)
            }
            21 -> { // PYREBIRD — adolescent phoenix, professionally dramatic
                flame(c, sp.accent, x, y - s * 1.05f, s * 0.6f, t * 5f)
                flame(c, sp.main, x - s * 0.45f, y - s * 0.9f, s * 0.42f, t * 5f + 1.5f)
                flame(c, sp.main, x + s * 0.45f, y - s * 0.9f, s * 0.42f, t * 5f + 3f)
                wing(c, sp.accent, x - s * 0.9f, y, s * 0.85f, -30f + sin(t * 6f) * 12f)
                wing(c, sp.accent, x + s * 0.9f, y, s * 0.85f, 210f - sin(t * 6f) * 12f)
                body(c, sp, x, y, s * 0.9f)
                flame(c, sp.accent, x - s * 0.5f, y + s * 0.75f, s * 0.35f, t * 4f)
                flame(c, sp.accent, x + s * 0.5f, y + s * 0.75f, s * 0.35f, t * 4f + 2f)
                beak(c, sp.accent, x, y + s * 0.1f, s * 0.32f)
                eyes(c, x, y - s * 0.25f, s * 0.85f)
            }
            22 -> { // HORNHOP — the jackalope, antlers and attitude
                tallEar(c, sp.main, x - s * 0.3f, y - s * 0.8f, s * 0.9f, -10f)
                tallEar(c, sp.main, x + s * 0.3f, y - s * 0.8f, s * 0.9f, 10f)
                stroke.color = sp.accent; stroke.strokeWidth = s * 0.12f
                c.drawLine(x - s * 0.5f, y - s * 0.8f, x - s * 0.85f, y - s * 1.35f, stroke)
                c.drawLine(x - s * 0.85f, y - s * 1.35f, x - s * 1.15f, y - s * 1.2f, stroke)
                c.drawLine(x - s * 0.85f, y - s * 1.35f, x - s * 0.8f, y - s * 1.65f, stroke)
                c.drawLine(x + s * 0.5f, y - s * 0.8f, x + s * 0.85f, y - s * 1.35f, stroke)
                c.drawLine(x + s * 0.85f, y - s * 1.35f, x + s * 1.15f, y - s * 1.2f, stroke)
                c.drawLine(x + s * 0.85f, y - s * 1.35f, x + s * 0.8f, y - s * 1.65f, stroke)
                body(c, sp, x, y, s * 0.95f)
                snout(c, sp.accent, x, y + s * 0.35f, s * 0.28f)
                eyes(c, x, y - s * 0.12f, s)
            }
            else -> { // SANDSHIFT — the off-duty djinn
                stroke.color = sp.main; stroke.strokeWidth = s * 0.2f
                path.reset()
                path.moveTo(x - s * 0.15f, y + s * 1.3f)
                path.quadTo(x - s * 0.9f, y + s * 0.9f, x - s * 0.3f, y + s * 0.55f)
                path.quadTo(x + s * 0.7f, y + s * 0.25f, x + s * 0.15f, y + s * 0.05f)
                c.drawPath(path, stroke)
                body(c, sp, x, y - s * 0.35f, s * 0.85f)
                stroke.color = sp.accent; stroke.strokeWidth = s * 0.1f
                c.drawCircle(x, y - s * 0.35f, s * 1.05f, stroke)
                fill.color = sp.accent
                c.drawCircle(x + cos(t * 2f) * s * 1.05f, y - s * 0.35f + sin(t * 2f) * s * 1.05f,
                    s * 0.1f, fill)
                eyes(c, x, y - s * 0.45f, s * 0.85f)
                grin(c, sp.accent, x, y + s * 0.05f, s * 0.35f)
            }
        }
    }

    /** Long upright rabbit ear, tiltable. */
    private fun tallEar(c: Canvas, color: Int, x: Float, y: Float, s: Float, tiltDeg: Float) {
        c.save(); c.rotate(tiltDeg, x, y)
        fill.color = color
        c.drawOval(RectF(x - s * 0.2f, y - s * 1.1f, x + s * 0.2f, y + s * 0.25f), fill)
        c.restore()
    }

    // ------------------------------------------------------------ props

    fun chest(c: Canvas, x: Float, y: Float, s: Float, open: Float) {
        val gold = Color.rgb(255, 210, 40)
        val trim = Color.rgb(255, 250, 210)
        glow.color = gold; glow.alpha = 70
        c.drawCircle(x, y, s * 1.7f, glow)
        fill.color = gold
        c.drawRoundRect(RectF(x - s, y - s * 0.35f, x + s, y + s * 0.75f), s * 0.15f, s * 0.15f, fill)
        val lidLift = open * s * 0.9f
        c.drawRoundRect(
            RectF(x - s * 1.06f, y - s * 0.85f - lidLift, x + s * 1.06f, y - s * 0.2f - lidLift),
            s * 0.2f, s * 0.2f, fill
        )
        stroke.color = trim; stroke.strokeWidth = s * 0.12f
        c.drawLine(x, y - s * 0.35f, x, y + s * 0.75f, stroke)
        fill.color = trim
        c.drawCircle(x, y + s * 0.06f, s * 0.16f, fill)
        if (open > 0.05f) {
            for (i in 0..4) {
                val a = -90f + (i - 2) * 28f
                val r = s * (0.9f + open * 1.1f)
                val rad = a * (Math.PI / 180f).toFloat()
                fill.color = if (i % 2 == 0) trim else gold
                c.drawCircle(x + cos(rad) * r, y - s * 0.5f + sin(rad) * r, s * 0.14f, fill)
            }
        }
    }

    fun gem(c: Canvas, x: Float, y: Float, s: Float, color: Int = Color.rgb(120, 255, 230)) {
        path.reset()
        path.moveTo(x, y - s); path.lineTo(x + s * 0.8f, y)
        path.lineTo(x, y + s); path.lineTo(x - s * 0.8f, y)
        path.close()
        fill.color = color; c.drawPath(path, fill)
        stroke.color = Color.WHITE; stroke.strokeWidth = s * 0.16f
        c.drawPath(path, stroke)
    }

    // ---------------------------------------------------------- helpers

    private fun body(c: Canvas, sp: Species, x: Float, y: Float, s: Float) {
        fill.color = sp.main
        c.drawCircle(x, y, s, fill)
    }

    private fun eyes(c: Canvas, x: Float, y: Float, s: Float) {
        fill.color = Color.WHITE
        c.drawCircle(x - s * 0.34f, y - s * 0.1f, s * 0.24f, fill)
        c.drawCircle(x + s * 0.34f, y - s * 0.1f, s * 0.24f, fill)
        pupil(c, x - s * 0.3f, y - s * 0.08f, s * 0.11f)
        pupil(c, x + s * 0.38f, y - s * 0.08f, s * 0.11f)
    }

    private fun pupil(c: Canvas, x: Float, y: Float, r: Float) {
        fill.color = Color.rgb(20, 30, 60)
        c.drawCircle(x, y, r, fill)
    }

    private fun catEyes(c: Canvas, accent: Int, x: Float, y: Float, s: Float) {
        fill.color = accent
        c.drawOval(RectF(x - s * 0.55f, y - s * 0.28f, x - s * 0.12f, y + s * 0.05f), fill)
        c.drawOval(RectF(x + s * 0.12f, y - s * 0.28f, x + s * 0.55f, y + s * 0.05f), fill)
        fill.color = Color.rgb(30, 10, 40)
        c.drawOval(RectF(x - s * 0.38f, y - s * 0.26f, x - s * 0.28f, y + s * 0.03f), fill)
        c.drawOval(RectF(x + s * 0.28f, y - s * 0.26f, x + s * 0.38f, y + s * 0.03f), fill)
    }

    private fun ear(c: Canvas, color: Int, x: Float, y: Float, s: Float) {
        path.reset()
        path.moveTo(x - s * 0.5f, y + s * 0.5f)
        path.lineTo(x, y - s * 0.7f)
        path.lineTo(x + s * 0.5f, y + s * 0.5f)
        path.close()
        fill.color = color; c.drawPath(path, fill)
    }

    private fun horn(c: Canvas, color: Int, x: Float, y: Float, s: Float) = ear(c, color, x, y, s)

    private fun spike(c: Canvas, color: Int, x: Float, y: Float, s: Float, angleDeg: Float) {
        val a = angleDeg * (Math.PI / 180f).toFloat()
        val bx = x + cos(a) * s; val by = y + sin(a) * s
        val tx = x + cos(a) * s * 1.55f; val ty = y + sin(a) * s * 1.55f
        stroke.color = color; stroke.strokeWidth = s * 0.18f
        c.drawLine(bx, by, tx, ty, stroke)
    }

    private fun snout(c: Canvas, color: Int, x: Float, y: Float, s: Float) {
        fill.color = color
        c.drawCircle(x, y, s, fill)
        pupil(c, x, y - s * 0.15f, s * 0.35f)
    }

    private fun beak(c: Canvas, color: Int, x: Float, y: Float, s: Float) {
        path.reset()
        path.moveTo(x - s * 0.6f, y - s * 0.3f)
        path.lineTo(x + s, y)
        path.lineTo(x - s * 0.6f, y + s * 0.3f)
        path.close()
        fill.color = color; c.drawPath(path, fill)
    }

    private fun grin(c: Canvas, color: Int, x: Float, y: Float, s: Float) {
        stroke.color = color; stroke.strokeWidth = s * 0.22f
        path.reset()
        path.moveTo(x - s, y)
        path.quadTo(x, y + s * 0.8f, x + s, y)
        c.drawPath(path, stroke)
    }

    private fun leaf(c: Canvas, color: Int, x: Float, y: Float, s: Float, angleDeg: Float) {
        c.save(); c.rotate(angleDeg, x, y)
        path.reset()
        path.moveTo(x, y + s)
        path.quadTo(x - s * 0.8f, y, x, y - s)
        path.quadTo(x + s * 0.8f, y, x, y + s)
        fill.color = color; c.drawPath(path, fill)
        c.restore()
    }

    private fun flame(c: Canvas, color: Int, x: Float, y: Float, s: Float, t: Float) {
        val flick = sin(t) * s * 0.15f
        path.reset()
        path.moveTo(x, y - s * 1.4f - flick)
        path.quadTo(x + s, y - s * 0.2f, x, y + s * 0.6f)
        path.quadTo(x - s, y - s * 0.2f, x, y - s * 1.4f - flick)
        fill.color = color; c.drawPath(path, fill)
    }

    private fun wave(c: Canvas, color: Int, x: Float, y: Float, s: Float, t: Float) {
        stroke.color = color; stroke.strokeWidth = s * 0.14f
        path.reset()
        path.moveTo(x - s, y)
        val ph = sin(t * 4f) * s * 0.12f
        path.quadTo(x - s * 0.5f, y + s * 0.25f + ph, x, y)
        path.quadTo(x + s * 0.5f, y - s * 0.25f - ph, x + s, y)
        c.drawPath(path, stroke)
    }

    private fun wing(c: Canvas, color: Int, x: Float, y: Float, s: Float, angleDeg: Float) {
        c.save(); c.rotate(angleDeg, x, y)
        path.reset()
        path.moveTo(x, y)
        path.quadTo(x - s * 1.4f, y - s * 1.2f, x - s * 0.3f, y - s * 1.6f)
        path.quadTo(x + s * 0.35f, y - s * 0.9f, x, y)
        fill.color = color; fill.alpha = 235
        c.drawPath(path, fill)
        fill.alpha = 255
        c.restore()
    }

    private fun tail(c: Canvas, color: Int, x: Float, y: Float, s: Float, t: Float) {
        stroke.color = color; stroke.strokeWidth = s * 0.24f
        path.reset()
        path.moveTo(x, y)
        val sway = sin(t * 3f) * s * 0.3f
        path.quadTo(x + s * 0.5f + sway, y - s * 0.4f, x + s * 0.3f + sway, y - s * 1.1f)
        c.drawPath(path, stroke)
    }

    private fun whiskers(c: Canvas, color: Int, x: Float, y: Float, s: Float) {
        stroke.color = color; stroke.strokeWidth = s * 0.07f
        c.drawLine(x - s * 0.3f, y, x - s * 1.1f, y - s * 0.12f, stroke)
        c.drawLine(x - s * 0.3f, y + s * 0.14f, x - s * 1.05f, y + s * 0.22f, stroke)
        c.drawLine(x + s * 0.3f, y, x + s * 1.1f, y - s * 0.12f, stroke)
        c.drawLine(x + s * 0.3f, y + s * 0.14f, x + s * 1.05f, y + s * 0.22f, stroke)
    }

    private fun star(c: Canvas, color: Int, x: Float, y: Float, s: Float) {
        path.reset()
        for (i in 0 until 10) {
            val r = if (i % 2 == 0) s else s * 0.45f
            val a = (i * 36f - 90f) * (Math.PI / 180f).toFloat()
            val px = x + cos(a) * r; val py = y + sin(a) * r
            if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        path.close()
        fill.color = color; c.drawPath(path, fill)
    }
}
