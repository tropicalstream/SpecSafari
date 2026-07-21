package com.specsafari.phone.den

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaPlayer
import com.specsafari.phone.R
import com.specsafari.shared.Species
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/**
 * The den's ears. Habitat ambience is seven real field recordings (all
 * public-domain, via archive.org: a night chorus of crickets and spring
 * peepers, a Turkish beach, a Dartington bonfire, and wind chimes in a
 * Dutch cemetery) looping at once, crossfaded by where you're standing.
 * Creature voices are synthesized per species — waveform from how it
 * moves, pitch from who it is, tempo from its energy.
 */
class DenAudio(context: Context) {

    private val res = context.resources
    private val pkg = context.packageName

    // Each creature borrows the voice of the real animal its biology echoes.
    // Index = species; value = the res/raw clip family (voice_<fam>_<disp>).
    //         0 LEAFLING  1 THORNPUP 2 PUDDLIM  3 EMBERLING 4 BOOKWYRM
    //         5 LUXMOTH   6 COINIX   7 FERROKIT 8 VOLTLING  9 GUSTRIL
    //        10 SHADEPAW 11 PRISMKIN 12 WISPLET 13 MOONHARE 14 KELPLING
    //        15 PUCKLE   16 DREAMBAKU 17 GLIMMERFOX 18 GEMBACK 19 CLAYWARD
    //        20 SKYDRUM  21 PYREBIRD 22 HORNHOP 23 SANDSHIFT 24 ZEPHYRET
    //        25 NIXLET   26 MOLDEWARP 27 SYLVARCH 28 MISTCROWN
    private val voiceFamily = arrayOf(
        "cricket", "fox", "frog", "gecko", "gecko",
        "cricket", "chime", "fox", "cicada", "swift",
        "cat", "chime", "owl", "rabbit", "frog",
        "jay", "owl", "fox", "gecko", "corvid",
        "raptor", "raptor", "rabbit", "swift", "swift",
        "frog", "rabbit", "jay", "songbird"
    )

    private val pcmCache = HashMap<Int, ShortArray>()
    private val idCache = HashMap<String, Int>()

    private fun rawId(family: String, disp: String): Int =
        idCache.getOrPut("${family}_$disp") {
            res.getIdentifier("voice_${family}_$disp", "raw", pkg)
        }

    /** Decode a small PCM-16 mono WAV from res/raw once, then cache it. */
    private fun loadPcm(resId: Int): ShortArray? = pcmCache.getOrPut(resId) {
        runCatching {
            val bytes = res.openRawResource(resId).use { it.readBytes() }
            // Walk RIFF chunks to the 'data' payload (skip any fmt/LIST).
            var p = 12
            var dataOff = 44; var dataLen = bytes.size - 44
            while (p + 8 <= bytes.size) {
                val id = String(bytes, p, 4, Charsets.US_ASCII)
                val sz = (bytes[p + 4].toInt() and 0xFF) or ((bytes[p + 5].toInt() and 0xFF) shl 8) or
                    ((bytes[p + 6].toInt() and 0xFF) shl 16) or ((bytes[p + 7].toInt() and 0xFF) shl 24)
                if (id == "data") { dataOff = p + 8; dataLen = sz; break }
                p += 8 + sz + (sz and 1)
            }
            val n = (dataLen / 2).coerceAtMost((bytes.size - dataOff) / 2)
            ShortArray(n) { i ->
                val b = dataOff + i * 2
                ((bytes[b].toInt() and 0xFF) or (bytes[b + 1].toInt() shl 8)).toShort()
            }
        }.getOrNull() ?: ShortArray(0)
    }.takeIf { it.isNotEmpty() }


    // Seven public-domain field-recording beds, each its own climate:
    //   0 meadow crickets/peepers · 1 tide/water · 2 fire crackle · 3 wind chimes
    //   4 winter wind · 5 Amazon rainforest · 6 desert wind
    private val loops = intArrayOf(
        R.raw.amb_meadow, R.raw.amb_tide, R.raw.amb_ember, R.raw.amb_starfall,
        R.raw.amb_frost, R.raw.amb_jungle, R.raw.amb_wind)
    private val players = arrayOfNulls<MediaPlayer>(loops.size)
    private val vols = FloatArray(loops.size)
    // Two extra layers for the localized weather cells: rain and snow-storm.
    private var rainPlayer: MediaPlayer? = null
    private var snowPlayer: MediaPlayer? = null
    private var rainVol = 0f; private var snowVol = 0f
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
        runCatching {
            rainPlayer = MediaPlayer.create(context, R.raw.amb_rain)?.apply {
                isLooping = true; setVolume(0f, 0f); start()
            }
            snowPlayer = MediaPlayer.create(context, R.raw.amb_frost)?.apply {
                isLooping = true; setVolume(0f, 0f); start()
            }
        }
    }

    /** Layer the precipitation cells over the biome bed, by proximity (0..1). */
    fun setPrecip(rain: Float, snow: Float) {
        if (released) return
        rainVol += (rain * 0.6f - rainVol) * 0.2f
        snowVol += (snow * 0.55f - snowVol) * 0.2f
        runCatching { rainPlayer?.setVolume(rainVol, rainVol) }
        runCatching { snowPlayer?.setVolume(snowVol, snowVol) }
    }

    // Each biome draws its own climate's bed: tundra howls with winter wind,
    // the taiga rings with cold chimes, temperate forest and monsoon chirp
    // crickets, the rain forest drips, the jungle roars with Amazon storm,
    // the deserts hiss with sand-wind, and the chaparral crackles with fire.
    //           TUNDRA TAIGA GLADE MIST JUNGLE MONSOON DUNES SAGE SCRUB
    private val bedForBiome = intArrayOf(4,   3,    0,    1,   5,    0,     6,    6,   2)

    /** Crossfade the ambience beds by which biomes the listener is near. */
    fun setListener(x: Float) {
        if (released) return
        val bedTarget = FloatArray(players.size)
        for ((i, biome) in Habitats.BIOMES.withIndex()) {
            val prox = when {
                x in biome.x0..biome.x1 -> 1f
                else -> {
                    val d = minOf(abs(x - biome.x0), abs(x - biome.x1))
                    (1f - d / 5f).coerceIn(0f, 1f)
                }
            }
            val bed = bedForBiome.getOrElse(i) { 0 }.coerceIn(0, players.size - 1)
            if (prox > bedTarget[bed]) bedTarget[bed] = prox
        }
        for (bed in players.indices) {
            val target = bedTarget[bed] * 0.55f
            vols[bed] += (target - vols[bed]) * 0.25f
            runCatching { players[bed]?.setVolume(vols[bed], vols[bed]) }
        }
    }

    // ------------------------------------------------- creature voices

    /** kinds: 0 pet, 1 feed, 2 meet, 3 chase-end, 4 release, 5 wake, 6 alarm, 7 solicit.
     *  gain scales volume for proximity (1 = right beside you). */
    fun voice(species: Int, kind: Int, gain: Float = 1f) {
        if (released) return
        val s = species.coerceIn(0, Species.ALL.size - 1)
        // The behavioral disposition picks WHICH processed take of the voice:
        // an alarmed cry, an excited chirp, or the settled everyday call.
        val disp = when (kind) {
            6 -> "alarm"
            1, 3 -> "excited"
            else -> "calm"
        }
        val fam = voiceFamily.getOrElse(s) { "songbird" }
        val pcm = rawId(fam, disp).takeIf { it != 0 }?.let { loadPcm(it) }
        if (pcm != null && pcm.isNotEmpty()) {
            playSample(pcm, s, gain); return
        }
        // No clip for this one: fall back to the synthesized chirp.
        Thread {
            runCatching {
                val sp = Species.ALL[s]
                // Who it is: a stable pitch from its number; how it moves: timbre.
                val base = 340f + ((s * 47) % 13) * 42f
                val rate = 0.09f - sp.energy * 0.035f
                val notes: FloatArray = when (kind) {
                    0 -> floatArrayOf(1f, 1.25f)                       // a pleased blip
                    1 -> floatArrayOf(1f, 1.25f, 1.5f)                 // fed and thrilled
                    2 -> floatArrayOf(1.2f, 1f)                        // hello there
                    3 -> floatArrayOf(1f, 1.33f, 1f, 1.33f)            // tag, you're it
                    4 -> floatArrayOf(1f, 1.25f, 1.5f, 2f)             // the farewell
                    6 -> floatArrayOf(1.6f, 1.2f, 0.9f)               // alarm — a startled descent
                    7 -> floatArrayOf(1.1f, 1.4f)                     // solicitation — hopeful
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
                track.setVolume(gain.coerceIn(0f, 1f))
                track.play()
                Thread.sleep((pcm.size * 1000L / sr) + 60)
                track.release()
            }
        }.start()
    }

    /** Play a real-animal take, shifted to this species' own pitch so twenty-
     *  nine creatures keep distinct voices from a shared pool of recordings.
     *  Pitch rides the AudioTrack sample rate — light, and no re-encoding. */
    private fun playSample(pcm: ShortArray, species: Int, gain: Float) {
        Thread {
            runCatching {
                // A stable identity note per species: small ones squeak high,
                // large and drowsy ones speak low.
                val pitch = 0.82f + ((species * 7) % 11) / 11f * 0.46f   // ~0.82..1.28
                val sr = (22050f * pitch).toInt().coerceIn(8000, 48000)
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
                track.setVolume(gain.coerceIn(0f, 1f))
                track.play()
                Thread.sleep((pcm.size * 1000L / sr) + 80)
                track.release()
            }
        }.start()
    }

    fun release() {
        released = true
        for (p in players) runCatching { p?.stop(); p?.release() }
        runCatching { rainPlayer?.stop(); rainPlayer?.release() }
        runCatching { snowPlayer?.stop(); snowPlayer?.release() }
    }
}
