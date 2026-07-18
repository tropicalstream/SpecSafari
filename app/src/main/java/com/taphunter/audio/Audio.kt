package com.taphunter.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import java.io.BufferedOutputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/**
 * Synthesized SFX (no audio binaries ship). Audio-first is the X3 idiom:
 * the hunt is mostly heard — the sonar ping sharpens as a creature nears,
 * so a wearer can keep their eyes on the trail.
 */
class Audio(private val context: Context) {

    companion object {
        const val SELECT = 0        // menu step
        const val BACK = 1
        const val PING = 2          // sonar sweep on the hunt
        const val NEAR = 3          // creature inside engage range
        const val TARGET = 4        // new quarry placed on the map
        const val ENGAGE = 5        // capture ring opens
        const val HIT = 6           // pulse tapped inside the sweet arc
        const val MISS = 7
        const val CATCH = 8         // fanfare
        const val FLEE = 9
        const val CHEST = 10        // treasure creaks open
        const val ESSENCE = 11      // reward tick
        const val UPGRADE = 12
        const val DENY = 13
        const val LEVEL = 14        // hunt ladder climbs
        const val LOCK = 15         // first GPS fix
        private const val COUNT = 16
        private const val RATE = 22050
    }

    private val pool = SoundPool.Builder()
        .setMaxStreams(6)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        ).build()

    private val ids = IntArray(COUNT)
    @Volatile private var loaded = false
    @Volatile var volume = 0.8f
    private val rng = Random(5)

    fun loadAsync() {
        Thread {
            runCatching {
                val dir = File(context.cacheDir, "snd").apply { mkdirs() }
                ids[SELECT] = load(dir, "sel", buf(70) { t -> sine(760f + t * 400f, t) * exp(-t * 18f) * 0.5f })
                ids[BACK] = load(dir, "bak", buf(80) { t -> sine(420f - t * 300f, t) * exp(-t * 16f) * 0.45f })
                ids[PING] = load(dir, "png", buf(240) { t -> sine(1180f - t * 500f, t) * exp(-t * 11f) * 0.5f })
                ids[NEAR] = load(dir, "nea", doubleBeep(988f, 1318f))
                ids[TARGET] = load(dir, "tgt", arpeggio(intArrayOf(659, 880), 90, 0.55f))
                ids[ENGAGE] = load(dir, "eng", arpeggio(intArrayOf(392, 523, 659, 784), 60, 0.6f))
                ids[HIT] = load(dir, "hit", buf(90) { t -> sine(1046f + t * 800f, t) * exp(-t * 14f) * 0.6f })
                ids[MISS] = load(dir, "mis", buf(160) { t -> (sine(150f, t) * 0.7f + noise() * 0.3f) * exp(-t * 12f) * 0.55f })
                ids[CATCH] = load(dir, "cat", arpeggio(intArrayOf(523, 659, 784, 1046, 1318), 85, 0.65f))
                ids[FLEE] = load(dir, "fle", buf(450) { t -> sine(880f - t * 950f, t) * exp(-t * 4.5f) * 0.5f })
                ids[CHEST] = load(dir, "chs", buf(500) { t ->
                    (noise() * exp(-t * 20f) * 0.45f) +
                        (if (t > 0.16f) sine(784f + (t - 0.16f) * 600f, t - 0.16f) * exp(-(t - 0.16f) * 7f) * 0.5f else 0f)
                })
                ids[ESSENCE] = load(dir, "ess", buf(60) { t -> sine(1568f, t) * exp(-t * 28f) * 0.5f })
                ids[UPGRADE] = load(dir, "upg", arpeggio(intArrayOf(440, 554, 659, 880), 80, 0.6f))
                ids[DENY] = load(dir, "dny", doubleBeep(220f, 175f))
                ids[LEVEL] = load(dir, "lvl", arpeggio(intArrayOf(392, 494, 587, 784), 95, 0.65f))
                ids[LOCK] = load(dir, "lok", arpeggio(intArrayOf(523, 784), 110, 0.5f))
                loaded = true
            }
        }.start()
    }

    fun play(id: Int, pitch: Float = 1f, vol: Float = 1f) {
        if (!loaded || id < 0 || id >= COUNT) return
        val s = ids[id]
        if (s == 0) return
        val v = (volume * vol).coerceIn(0f, 1f)
        if (v <= 0f) return
        pool.play(s, v, v, 1, 0, pitch.coerceIn(0.5f, 2f))
    }

    fun release() { runCatching { pool.release() } }

    // ------------------------------------------------------------ synth

    private fun buf(ms: Int, gen: (Float) -> Float): ShortArray {
        val n = RATE * ms / 1000
        return ShortArray(n) { i -> (gen(i.toFloat() / RATE).coerceIn(-1f, 1f) * 30000f).toInt().toShort() }
    }
    private fun sine(f: Float, t: Float) = sin(2.0 * PI * f * t).toFloat()
    private fun noise() = rng.nextFloat() * 2f - 1f

    private fun doubleBeep(f1: Float, f2: Float): ShortArray = buf(360) { t ->
        when {
            t < 0.13f -> sine(f1, t) * exp(-t * 9f) * 0.55f
            t in 0.18f..0.34f -> sine(f2, t - 0.18f) * exp(-(t - 0.18f) * 9f) * 0.55f
            else -> 0f
        }
    }

    private fun arpeggio(freqs: IntArray, noteMs: Int, amp: Float): ShortArray {
        val total = noteMs * freqs.size + 240
        return buf(total) { t ->
            var v = 0f
            for ((i, f) in freqs.withIndex()) {
                val start = i * noteMs / 1000f
                if (t >= start) {
                    val lt = t - start
                    v += (sine(f.toFloat(), lt) + 0.3f * sine(f * 2f, lt)) * exp(-lt * 5.5f) * amp * 0.45f
                }
            }
            v
        }
    }

    // ------------------------------------------------------------- wav

    private fun DataOutputStream.wInt(v: Int) { write(v and 0xFF); write((v shr 8) and 0xFF); write((v shr 16) and 0xFF); write((v shr 24) and 0xFF) }
    private fun DataOutputStream.wShort(v: Int) { write(v and 0xFF); write((v shr 8) and 0xFF) }

    private fun load(dir: File, name: String, pcm: ShortArray): Int {
        val f = File(dir, "$name.wav")
        val dataLen = pcm.size * 2
        DataOutputStream(BufferedOutputStream(FileOutputStream(f))).use { o ->
            o.writeBytes("RIFF"); o.wInt(36 + dataLen); o.writeBytes("WAVE")
            o.writeBytes("fmt "); o.wInt(16); o.wShort(1); o.wShort(1)
            o.wInt(RATE); o.wInt(RATE * 2); o.wShort(2); o.wShort(16)
            o.writeBytes("data"); o.wInt(dataLen)
            for (s in pcm) o.wShort(s.toInt())
        }
        return pool.load(f.absolutePath, 1)
    }
}
