package com.taphunter.phone.den

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaPlayer
import com.taphunter.phone.R
import com.taphunter.shared.Species
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/**
 * The den's ears. Habitat ambience is four real field recordings (all
 * public-domain, via archive.org: a night chorus of crickets and spring
 * peepers, a Turkish beach, a Dartington bonfire, and wind chimes in a
 * Dutch cemetery) looping at once, crossfaded by where you're standing.
 * Creature voices are synthesized per species — waveform from how it
 * moves, pitch from who it is, tempo from its energy.
 */
class DenAudio(context: Context) {

    private val loops = intArrayOf(R.raw.amb_meadow, R.raw.amb_tide, R.raw.amb_ember, R.raw.amb_starfall)
    private val players = arrayOfNulls<MediaPlayer>(4)
    private val vols = FloatArray(4)
    @Volatile private var released = false

    init {
        for (i in loops.indices) {
            runCatching {
                players[i] = MediaPlayer.create(context, loops[i])?.apply {
                    isLooping = true
                    setVolume(0f, 0f)
                    start()
                }
            }
        }
    }

    /** Crossfade the four biome beds around the listener's x. */
    fun setListener(x: Float) {
        if (released) return
        for ((i, biome) in Habitats.BIOMES.withIndex()) {
            val target = when {
                x in biome.x0..biome.x1 -> 1f
                else -> {
                    val d = minOf(abs(x - biome.x0), abs(x - biome.x1))
                    (1f - d / 5f).coerceIn(0f, 1f)
                }
            } * 0.55f
            vols[i] += (target - vols[i]) * 0.25f
            runCatching { players[i]?.setVolume(vols[i], vols[i]) }
        }
    }

    // ------------------------------------------------- creature voices

    /** kinds: 0 pet, 1 feed, 2 meet, 3 chase-end, 4 release, 5 wake */
    fun voice(species: Int, kind: Int) {
        if (released) return
        Thread {
            runCatching {
                val sp = Species.ALL[species.coerceIn(0, Species.ALL.size - 1)]
                // Who it is: a stable pitch from its number; how it moves: timbre.
                val base = 340f + ((species * 47) % 13) * 42f
                val rate = 0.09f - sp.energy * 0.035f
                val notes: FloatArray = when (kind) {
                    0 -> floatArrayOf(1f, 1.25f)                       // a pleased blip
                    1 -> floatArrayOf(1f, 1.25f, 1.5f)                 // fed and thrilled
                    2 -> floatArrayOf(1.2f, 1f)                        // hello there
                    3 -> floatArrayOf(1f, 1.33f, 1f, 1.33f)            // tag, you're it
                    4 -> floatArrayOf(1f, 1.25f, 1.5f, 2f)             // the farewell
                    else -> floatArrayOf(0.8f, 1f)                     // a yawn, ascending
                }
                val sr = 22050
                val noteLen = (sr * rate).toInt()
                val pcm = ShortArray(noteLen * notes.size)
                var idx = 0
                for (n in notes) {
                    val f = base * n
                    for (k in 0 until noteLen) {
                        val tt = k.toFloat() / sr
                        val env = (1f - k.toFloat() / noteLen) * minOf(1f, k / (sr * 0.004f))
                        val ph = 2.0 * PI * f * tt
                        val wave = when (sp.motion) {
                            Species.FLOAT, Species.DRIFT -> sin(ph)                  // pure and airy
                            Species.SWIM -> sin(ph + sin(2.0 * PI * 6.0 * tt) * 0.6) // watery vibrato
                            Species.DART, Species.SKIM ->                             // bright and buzzy
                                (0.7 * sin(ph) + 0.3 * sin(ph * 2.0))
                            Species.BURROW -> sin(ph * 0.5)                           // soft and low
                            else -> (0.8 * sin(ph) + 0.2 * sin(ph * 3.0))             // rounded chirp
                        }
                        pcm[idx++] = (wave * env * 9000).toInt().toShort()
                    }
                }
                val track = AudioTrack.Builder()
                    .setAudioAttributes(AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                    .setAudioFormat(AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sr)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                    .setBufferSizeInBytes(pcm.size * 2)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()
                track.write(pcm, 0, pcm.size)
                track.play()
                Thread.sleep((pcm.size * 1000L / sr) + 60)
                track.release()
            }
        }.start()
    }

    fun release() {
        released = true
        for (p in players) runCatching { p?.stop(); p?.release() }
    }
}
