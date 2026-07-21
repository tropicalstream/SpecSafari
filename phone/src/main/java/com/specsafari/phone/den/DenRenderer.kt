package com.specsafari.phone.den

import android.opengl.GLES20.*
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import com.specsafari.shared.EcologyModel
import com.specsafari.shared.EthoModel
import com.specsafari.shared.HumanPerceptionContext
import com.specsafari.shared.LearningState
import com.specsafari.shared.PlayerResponse
import com.specsafari.shared.PreferredMedium
import com.specsafari.shared.SensingContext
import com.specsafari.shared.Season
import com.specsafari.shared.SeasonalEcology
import com.specsafari.shared.SocialAction
import com.specsafari.shared.Species
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.concurrent.ConcurrentLinkedQueue
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** One resident of the world. */
class DenC(val species: Int) {
    var x = 2f; var z = 0f
    var vx = 0f; var vz = 0f
    var phase = Random.nextFloat() * 6f
    var pauseT = 0f
    var happyT = 0f
    var headingDeg = Random.nextFloat() * 360f   // true facing, world yaw
    var bank = 0f                                 // lean into turns (fliers)
    var targetX = Float.NaN
    var targetZ = 0f
    var targetT = 0f           // patience: give up on unreachable places
    var lingerT = 0f
    var under = false          // burrowers: traveling as a molehill
    var underT = 0f
    // The third dimension: fliers cruise, perchers roost, climbers scale.
    var alt = 0f               // height above the ground right now
    var perchT = 0f            // time left sitting on a roost/summit
    var perchTop = 0f          // the roost's top height
    var climbing = false       // ascending claw-over-claw, not on the wing
    var meetCd = 0f
    var questT = 0f            // fetch-quest dash: sprint out, dig, sprint home
    var questDir = 1f
    var bubbleT = 0f           // thought-bubble hearts over the head after a feed
    var bubbleBroken = false   // too frightened to enjoy it: the hearts crack
    var inWater = false        // for the splash on the way in
    val scale = 0.88f + Random.nextFloat() * 0.24f
    // Perception of the human: arousal and the flight response.
    var fleeing = false
    var startleCd = 0f         // refractory period after a flush
    var alert = false          // oriented and monitoring, not yet fleeing
    var soliciting = false     // food-conditioned approach toward the human
    var solicitCd = 0f
    var investigating = false   // curious inspection without food solicitation
    var aware = false           // detected the player outside alert distance
    var closeCd = 0f            // closest-approach recorder, per individual
    var encounterLogged = false
    var playerDistance = Float.MAX_VALUE
    var detectionDistance = 0f
    var flightDistance = 0f
    var approachDistance = 0f
    // Seasonal climate tracking and actual food search/consumption.
    var seasonalBiome = -1
    var migrating = false
    var migrationCheckT = Random.nextFloat() * 5f
    var migrationTransitT = 0f  // swimmers use unseen connected-water passages
    var hunger = .18f + Random.nextFloat() * .52f
    var forageCheckT = Random.nextFloat() * 3f
    var forageFood = -1
    var foraging = false
    var forageWaypoint = false
    var feedingT = 0f
    // The biological clock: waking hours, then home to the nest to sleep.
    var nestX = 2f; var nestZ = -1f
    var awakeT = 20f + Random.nextFloat() * 50f
    var sleeping = false
    var homing = false
    var chaseIdx = -1          // playmate being chased, -1 none
    var chaseT = 0f
    var releasing = false      // running free, off the edge of the world
}

private class Particle(var x: Float, var y: Float, var z: Float,
                       var vy: Float, var life: Float, var maxLife: Float,
                       var r: Float, var g: Float, var b: Float, var size: Float)

private class Ripple(val x: Float, val z: Float, var age: Float = 0f)

/** A standing body of water the world's physics knows about. */
private class WaterBody(val x: Float, val z: Float, val r: Float, val frozen: Boolean)

class DenRenderer : GLSurfaceView.Renderer {

    companion object {
        const val EV_MEET = 0; const val EV_CHASE_END = 1
        const val EV_RELEASED = 2; const val EV_WAKE = 3
        const val EV_ALARM = 4; const val EV_FORAGE = 5
        // Behavioral events for the field-history recorder + audio.
        const val BEV_FLEE = 0; const val BEV_SOLICIT = 1; const val BEV_CLOSE = 2
        const val BEV_NOTICE = 3
        // The satchel: carryable trail food and its lifecycle events.
        const val KIND_BERRY = 0; const val KIND_HONEY = 1; const val KIND_PUDDING = 2
        const val IEV_PICKUP = 0; const val IEV_FED = 1
    }

    /** A satchel item out of the bag: arcing through the air after a toss,
     *  resting on the ground, or flying to a chosen creature's mouth. */
    class LooseItem(var x: Float, var y: Float, var z: Float,
                    var vx: Float, var vy: Float, var vz: Float,
                    val kind: Int, var homeIndex: Int = -1)

    @Volatile private var placedIds: List<String> = emptyList()
    @Volatile private var population: List<Int> = emptyList()
    @Volatile private var rebuild = true
    @Volatile var selected = -1
    @Volatile private var glideX = Float.NaN

    /** Seconds of golden celebration left — the hunter struck treasure in the
     *  field. Consumed on the GL thread so no cross-thread list is touched. */
    @Volatile private var cheerT = 0f

    /** The item in the walker's hand (KIND_*, -1 empty) and items loose in
     *  the world. Only the GL thread mutates the list; the activity reads. */
    @Volatile var carriedKind = -1; private set
    val looseItems = java.util.concurrent.CopyOnWriteArrayList<LooseItem>()

    /** (IEV_*, kind, species) — feeding and pickup outcomes for the activity. */
    val itemEvents = ConcurrentLinkedQueue<IntArray>()

    /** How loud a voice from this species should be right now — full up close,
     *  fading to silence a little past a biome's width so you only hear the
     *  creatures near you, never the whole world's chorus at once. */
    fun voiceGain(species: Int): Float {
        var best = Float.MAX_VALUE
        synchronized(creatures) {
            for (c in creatures) {
                if (c.species != species || c.releasing) continue
                val dx = c.x - camPX; val dz = c.z - camPZ
                val d2 = dx * dx + dz * dz
                if (d2 < best) best = d2
            }
        }
        if (best == Float.MAX_VALUE) return 0f
        val d = kotlin.math.sqrt(best)
        return (1f - (d - 4f) / 9f).coerceIn(0f, 1f)   // full ≤4u, silent ≥13u
    }

    fun equipItem(kind: Int) { carriedKind = kind }

    /** Back in the bag; returns what was carried (-1 if empty-handed). */
    fun stowCarried(): Int { val k = carriedKind; carriedKind = -1; return k }

    /** Underhand toss ahead of the walker; it lands and waits to be reclaimed. */
    fun throwCarried() {
        val k = carriedKind
        if (k < 0) return
        carriedKind = -1
        looseItems += LooseItem(camPX + fwdXv * 0.7f, 1.05f, camPZ + fwdZv * 0.7f,
            fwdXv * 3.0f, 2.1f, fwdZv * 3.0f, k)
    }

    /** Offer the carried item to a chosen creature: it flies to the mouth. */
    fun sendCarriedTo(index: Int) {
        val k = carriedKind
        if (k < 0) return
        carriedKind = -1
        looseItems += LooseItem(camPX + fwdXv * 0.5f, 1.0f, camPZ + fwdZv * 0.5f,
            0f, 0f, 0f, k, homeIndex = index)
    }

    /** (event, species) pairs for the activity's audio layer. */
    val audioEvents = ConcurrentLinkedQueue<IntArray>()

    /** (BEV_*, species, arg) for the field-history recorder. */
    val behaviorEvents = ConcurrentLinkedQueue<IntArray>()
    private val sessionEncounteredSpecies = HashSet<Int>()

    // Learned per-species quantities, fed from the recorded field history.
    @Volatile private var fieldHabit = FloatArray(Species.ALL.size)
    @Volatile private var fieldFamiliar = FloatArray(Species.ALL.size)
    @Volatile private var fieldFood = FloatArray(Species.ALL.size)
    @Volatile private var fieldFear = FloatArray(Species.ALL.size)

    fun setFieldStats(habit: FloatArray, familiar: FloatArray, food: FloatArray, fear: FloatArray) {
        fieldHabit = habit; fieldFamiliar = familiar; fieldFood = food; fieldFear = fear
    }

    // The human's kinematics — position, velocity, gaze — for the FID model.
    private var prevCamX = 2.5f; private var prevCamZ = 4.2f
    @Volatile private var playerVX = 0f; @Volatile private var playerVZ = 0f
    @Volatile private var playerSpeed = 0f
    @Volatile private var fwdXv = 0f; @Volatile private var fwdZv = -1f
    // ------- the real sky: local clock + local weather over the den
    @Volatile var weatherCode = 0        // WMO code from Open-Meteo, 0 = clear
    // Localized precipitation the audio layer reads (0..1 by proximity).
    @Volatile var precipRain = 0f; private set
    @Volatile var precipSnow = 0f; private set
    private var gust = 0f                 // blizzard cross-wind on the flakes
    @Volatile var windKmh = 0f
    @Volatile private var southernHemisphere = false
    private var flashT = 0f              // thunder
    private var bakedDay = -1f           // ground bake tracks the daylight
    private var frameDaylight = 1f       // computed once per frame; behavior reuses it

    fun setWeather(code: Int, wind: Float) { weatherCode = code; windKmh = wind }
    fun setSouthernHemisphere(southern: Boolean) {
        if (southernHemisphere != southern) {
            southernHemisphere = southern
            cachedSeasonAt = 0L
        }
    }

    /** 0 = deep night, 1 = midday, smooth dawn/dusk ramps from the clock. */
    private fun dayLerp(): Float {
        val cal = java.util.Calendar.getInstance()
        val h = cal.get(java.util.Calendar.HOUR_OF_DAY) + cal.get(java.util.Calendar.MINUTE) / 60f
        return when {
            h < 5f || h >= 21f -> 0f
            h < 8f -> (h - 5f) / 3f          // dawn
            h < 17f -> 1f
            else -> 1f - (h - 17f) / 4f      // long dusk
        }
    }

    private val raining get() = weatherCode in 51..67 || weatherCode in 80..82 || weatherCode >= 95
    private val snowing get() = weatherCode in 71..77 || weatherCode == 85 || weatherCode == 86
    private val foggy get() = weatherCode == 45 || weatherCode == 48
    private val overcast get() = weatherCode == 3 || raining || snowing

    private fun dayMix(c: Int, day: Float): Int {
        // Daylight lifts the palette toward a pale sky-wash; overcast mutes it.
        val k = day * (if (overcast) 0.45f else 0.7f)
        val dr = 150; val dg = 185; val db = 215
        return android.graphics.Color.rgb(
            (android.graphics.Color.red(c) * (1 - k) + dr * k).toInt(),
            (android.graphics.Color.green(c) * (1 - k) + dg * k).toInt(),
            (android.graphics.Color.blue(c) * (1 - k) + db * k).toInt()
        )
    }

    val creatures = ArrayList<DenC>()
    private val particles = ArrayList<Particle>()
    private var mainProg = 0; private var skyProg = 0; private var ptProg = 0
    private var waterProg = 0
    private var sceneMesh: Mesh? = null
    private var itemMeshes = listOf<Mesh>()
    private val forms = HashMap<Int, Mesh>()
    private var shadowMesh: Mesh? = null
    private var nestMesh: Mesh? = null
    private var flameMesh: Mesh? = null
    private var glowMesh: Mesh? = null
    private var ringMesh: Mesh? = null
    private var waterBuf: FloatBuffer? = null
    private var waterVerts = 0
    // The world's elemental furniture, gathered at scene build.
    private val flamePts = ArrayList<FloatArray>()     // x, y, z
    private val lanternPts = ArrayList<FloatArray>()
    private val sparklePts = ArrayList<FloatArray>()
    private val waterBodies = ArrayList<WaterBody>()
    private var foodCooldown = FloatArray(Habitats.FOODS.size)
    // The bordering ocean: (x, z, radius) discs, tinted per biome, no wading.
    @Volatile private var borderWater: List<FloatArray> = emptyList()
    private val ripples = ArrayList<Ripple>()
    /** Solid things: x, z, radius. Nobody walks through wood or fire.
     *  Foliage (bushes, nooks) is deliberately NOT here — a creature may
     *  hide inside leaves; it may never stand inside a physically solid
     *  object. */
    private val obstacles = ArrayList<FloatArray>()
    @Volatile private var obstacleSnapshot: List<FloatArray> = emptyList()
    /** Roosts and summits a flier can land on / a climber can scale: x, z, topY. */
    private val perchPts = ArrayList<FloatArray>()
    // The claw-and-paw club: metal kit, cat, brownie, roost-dreamer, carbuncle.
    private val climberSpecies = intArrayOf(7, 10, 15, 16, 18)

    /** Push a point out of every solid it overlaps; returns corrected x,z. */
    private fun resolve(px: Float, pz: Float, rad: Float): FloatArray {
        var x = px; var z = pz
        for (pass in 0 until 4) {
            var moved = false
            for (o in obstacles) {
                val dx = x - o[0]; val dz = z - o[1]
                val min = o[2] + rad
                val d2 = dx * dx + dz * dz
                if (d2 < min * min) {
                    val d = kotlin.math.sqrt(d2).coerceAtLeast(0.001f)
                    x = o[0] + dx / d * min; z = o[1] + dz / d * min
                    moved = true
                }
            }
            if (!moved) break
        }
        return floatArrayOf(x, z)
    }

    private fun overlaps(x: Float, z: Float, rad: Float): Boolean {
        for (o in obstacles) {
            val dx = x - o[0]; val dz = z - o[1]; val min = o[2] + rad
            if (dx * dx + dz * dz < min * min) return true
        }
        return false
    }

    /** When resolve can't escape a tight cluster of trunks, spiral outward to
     *  the nearest genuinely-clear spot — so nothing can ever be trapped. */
    private fun findClear(x: Float, z: Float, rad: Float): FloatArray {
        if (!overlaps(x, z, rad)) return floatArrayOf(x, z)
        var r = 0.5f
        while (r < 14f) {
            var a = 0f
            while (a < 6.283f) {
                val cx = (x + cos(a) * r).coerceIn(0.4f, Habitats.WORLD_W - 0.4f)
                val cz = (z + sin(a) * r).coerceIn(-12f, 12f)
                if (!overlaps(cx, cz, rad)) return floatArrayOf(cx, cz)
                a += 0.45f
            }
            r += 0.6f
        }
        return floatArrayOf(x, z)
    }

    private fun inHomeHabitat(c: DenC): Boolean {
        val kind = Species.ALL[c.species].zone
        return Habitats.ZONES.any {
            if (it.kind != kind) false else {
                val dx = it.x - c.x; val dz = it.z - c.z
                dx * dx + dz * dz < (it.r + .6f) * (it.r + .6f)
            }
        }
    }

    private fun onStone(c: DenC): Boolean = Habitats.ZONES.any {
        if (it.kind != "STONE") false else {
            val dx = it.x - c.x; val dz = it.z - c.z
            dx * dx + dz * dz < (it.r + .4f) * (it.r + .4f)
        }
    }

    /** Segment/circle occlusion: blocks sight, never the nonvisual channels. */
    private fun sightOccluded(ax: Float, az: Float, bx: Float, bz: Float): Boolean {
        val dx = bx - ax; val dz = bz - az
        val len2 = dx * dx + dz * dz
        if (len2 < .04f) return false
        for (o in obstacleSnapshot) {
            val u = (((o[0] - ax) * dx + (o[1] - az) * dz) / len2).coerceIn(0f, 1f)
            if (u < .06f || u > .94f) continue
            val px = ax + dx * u; val pz = az + dz * u
            val ox = px - o[0]; val oz = pz - o[1]
            val r = o[2] * .82f
            if (ox * ox + oz * oz < r * r) return true
        }
        return false
    }

    private fun facingDot(c: DenC, tx: Float, tz: Float): Float {
        val dx = tx - c.x; val dz = tz - c.z
        val d = kotlin.math.sqrt(dx * dx + dz * dz).coerceAtLeast(.001f)
        val h = Math.toRadians(c.headingDeg.toDouble()).toFloat()
        return (sin(h) * dx / d + cos(h) * dz / d).coerceIn(-1f, 1f)
    }

    private fun sensingContext(
        c: DenC,
        tx: Float,
        tz: Float,
        targetSpeed: Float,
        targetInWater: Boolean,
        targetUnderground: Boolean = false
    ) = SensingContext(
        daylight = frameDaylight,
        facingDot = facingDot(c, tx, tz),
        targetSpeed = targetSpeed,
        homeHabitat = inHomeHabitat(c),
        observerInWater = waterAt(c.x, c.z) != null,
        targetInWater = targetInWater,
        observerUnderground = c.under,
        targetUnderground = targetUnderground,
        onStone = onStone(c),
        fog = foggy,
        rain = raining || snowing,
        wind = (windKmh / 30f).coerceIn(0f, 1.5f),
        visualOccluded = sightOccluded(c.x, c.z, tx, tz)
    )

    private var cachedSeason = Season.SUMMER
    private var cachedSeasonAt = 0L
    private fun seasonNow(): Season {
        val now = android.os.SystemClock.elapsedRealtime()
        if (now - cachedSeasonAt < 60_000L) return cachedSeason
        val month = java.util.Calendar.getInstance().get(java.util.Calendar.MONTH) + 1
        cachedSeason = SeasonalEcology.currentSeason(month, southernHemisphere)
        cachedSeasonAt = now
        return cachedSeason
    }

    private fun biomeIndexAt(x: Float): Int =
        Habitats.BIOMES.indexOf(Habitats.biomeAt(x)).coerceAtLeast(0)

    /** A seasonal home honors both climate first and microhabitat where available. */
    private fun seasonalHome(species: Int, targetBiome: Int, salt: Int): FloatArray {
        val sp = Species.ALL[species]
        val exact = Habitats.ZONES.filter {
            it.kind == sp.zone && biomeIndexAt(it.x) == targetBiome
        }
        val zone = if (exact.isNotEmpty()) exact[Math.floorMod(salt, exact.size)] else null
        val rawX: Float
        val rawZ: Float
        if (zone != null) {
            val a = Math.floorMod(salt * 37, 360) * (6.283f / 360f)
            rawX = zone.x + cos(a) * zone.r * .42f
            rawZ = zone.z + sin(a) * zone.r * .34f
        } else {
            val b = Habitats.BIOMES[targetBiome.coerceIn(Habitats.BIOMES.indices)]
            rawX = b.center + (Math.floorMod(salt * 13, 7) - 3) * .32f
            rawZ = (Math.floorMod(salt * 29, 9) - 4) * .72f
        }
        val clear = findClear(rawX, rawZ, EcologyModel.of(species).bodyRadius + .08f)
        return floatArrayOf(clear[0], clear[1].coerceIn(-8f, 8f))
    }

    /** Choose food by species diet, current seasonal crop, climate and morphology. */
    private fun bestForage(c: DenC, season: Season): Int {
        val profile = SeasonalEcology.of(c.species)
        val eco = EcologyModel.of(c.species)
        val desiredBiome = profile.targetBiome(season)
        var best = -1
        var bestScore = Float.MAX_VALUE
        for (i in Habitats.FOODS.indices) {
            if (foodCooldown.getOrElse(i) { 0f } > 0f) continue
            val f = Habitats.FOODS[i]
            if (!profile.accepts(f.kind)) continue
            if (eco.medium == PreferredMedium.WATER && f.kind != Habitats.F_AQUATIC) continue
            if (f.kind == Habitats.F_AQUATIC && waterAt(f.x, f.z) == null) continue
            val foodBiome = biomeIndexAt(f.x)
            // Foraging is local. A climate migration must finish before feeding starts.
            if (foodBiome != desiredBiome) continue
            val availability = SeasonalEcology.foodAvailability(f.kind, season, foodBiome)
            if (availability < .12f) continue
            val dx = f.x - c.x; val dz = f.z - c.z
            val distance = kotlin.math.sqrt(dx * dx + dz * dz)
            val score = distance / (.30f + availability) + Math.floorMod(i * 17 + c.species, 11) * .02f
            if (score < bestScore) { bestScore = score; best = i }
        }
        return best
    }

    /**
     * Break a blocked forage route into short visible legs. This is deliberately
     * small and deterministic: enough navigation for trees/rocks without a heavy
     * per-frame pathfinder on the phone.
     */
    private fun setForageLeg(c: DenC, foodIndex: Int) {
        val f = Habitats.FOODS.getOrNull(foodIndex) ?: run {
            c.foraging = false; c.forageFood = -1; c.forageWaypoint = false
            return
        }
        val eco = EcologyModel.of(c.species)
        val side = if (f.kind == Habitats.F_AQUATIC) 0f
            else if ((c.species + foodIndex) % 2 == 0) .52f else -.52f
        val final = findClear((f.x + side).coerceIn(.8f, Habitats.WORLD_W - .8f),
            f.z.coerceIn(-8f, 8f), eco.bodyRadius + .08f)
        var legX = final[0]; var legZ = final[1]
        if (sightOccluded(c.x, c.z, legX, legZ)) {
            var bestScore = Float.MAX_VALUE
            val currentToFinal = kotlin.math.sqrt(
                (final[0] - c.x) * (final[0] - c.x) + (final[1] - c.z) * (final[1] - c.z))
            for (ring in 1..3) {
                val r = .9f + ring * .8f
                for (k in 0 until 16) {
                    val a = k * (6.283f / 16f)
                    val px = (c.x + cos(a) * r).coerceIn(.8f, Habitats.WORLD_W - .8f)
                    val pz = (c.z + sin(a) * r).coerceIn(-8f, 8f)
                    if (overlaps(px, pz, eco.bodyRadius + .08f) ||
                        sightOccluded(c.x, c.z, px, pz)) continue
                    val toFinal = kotlin.math.sqrt(
                        (final[0] - px) * (final[0] - px) + (final[1] - pz) * (final[1] - pz))
                    val onwardClear = !sightOccluded(px, pz, final[0], final[1])
                    val progressPenalty = if (toFinal <= currentToFinal + .25f) 0f else 2.5f
                    val score = toFinal + r * .12f + progressPenalty + if (onwardClear) -3f else 0f
                    if (score < bestScore) { bestScore = score; legX = px; legZ = pz }
                }
            }
        }
        val remaining = kotlin.math.sqrt((legX - c.x) * (legX - c.x) + (legZ - c.z) * (legZ - c.z))
        c.forageWaypoint = abs(legX - final[0]) + abs(legZ - final[1]) > .18f
        c.targetX = legX; c.targetZ = legZ
        c.targetT = maxOf(8f, remaining / (.45f + Species.ALL[c.species].energy * .65f) + 5f)
    }
    private val proj = FloatArray(16); private val view = FloatArray(16)
    private val vp = FloatArray(16); private val model = FloatArray(16)
    @Volatile private var lastVp = FloatArray(16)
    private var viewW = 1; private var viewH = 1
    private var lastNs = 0L
    private var t = 0f
    private val skyBuf: FloatBuffer = ByteBuffer.allocateDirect(8 * 4)
        .order(ByteOrder.nativeOrder()).asFloatBuffer()
        .put(floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f)).apply { position(0) }

    // First-person stroll.
    @Volatile var camPX = 2.5f; private set
    @Volatile private var camPZ = 4.2f
    private val camPY = 1.35f
    @Volatile var yawDeg = 0f
    @Volatile var pitchDeg = -6f
    @Volatile private var moveX = 0f
    @Volatile private var moveY = 0f

    fun setMove(x: Float, y: Float) { moveX = x.coerceIn(-1f, 1f); moveY = y.coerceIn(-1f, 1f) }
    fun look(dxDeg: Float, dyDeg: Float) {
        yawDeg = (yawDeg + dxDeg) % 360f
        pitchDeg = (pitchDeg + dyDeg).coerceIn(-55f, 40f)
    }
    fun resetCamera() { camPX = 2.5f; camPZ = 4.2f; yawDeg = 0f; pitchDeg = -6f; setMove(0f, 0f) }

    /** Fast travel: the camera glides to a biome; the world stays one world. */
    fun travelTo(biome: Int) { glideX = Habitats.BIOMES[biome.coerceIn(0, Habitats.BIOMES.size - 1)].center }

    fun configure(pop: List<Int>, items: List<String>) {
        population = pop; placedIds = items; rebuild = true
    }

    fun placeItems(items: List<String>) { placedIds = items; rebuild = true }

    private fun learningFor(species: Int) = LearningState(
        familiarity = fieldFamiliar.getOrElse(species) { 0f },
        habituation = fieldHabit.getOrElse(species) { 0f },
        foodExpectation = fieldFood.getOrElse(species) { 0f },
        fear = fieldFear.getOrElse(species) { 0f }
    )

    /** Surface distance rather than center distance: morphology now affects reach. */
    fun distanceToPlayer(index: Int): Float {
        val c = creatures.getOrNull(index) ?: return Float.MAX_VALUE
        val dx = c.x - camPX; val dz = c.z - camPZ
        return (kotlin.math.sqrt(dx * dx + dz * dz) -
            EcologyModel.of(c.species).bodyRadius - .24f).coerceAtLeast(.05f)
    }

    // A creature flees to hold its flight distance, then settles there — so
    // the reach has to REACH that far, or a wary animal you have chased as
    // close as it will ever allow stays permanently "too far to pet". Reach
    // always clears the flight distance, so wherever it lets you stand, you
    // can touch it. (Creatures also loom large in view; a couple units already
    // reads as "right in front", so the floor is generous.)
    fun petReach(index: Int): Float = creatures.getOrNull(index)?.let {
        maxOf(EthoModel.thresholds(it.species).petReach, it.flightDistance + 0.8f, 3f)
    } ?: 0f

    fun feedRange(index: Int): Float = creatures.getOrNull(index)?.let {
        maxOf(EthoModel.thresholds(it.species).feedRange, it.flightDistance + 1.4f, 4.5f)
    } ?: 0f

    fun clearInteractionPath(index: Int): Boolean = creatures.getOrNull(index)?.let {
        !sightOccluded(camPX, camPZ, it.x, it.z)
    } ?: false

    /** True only while the creature's body is actually inside the current view.
     *  Line of sight alone is insufficient: a previously selected resident can
     *  remain unobstructed while standing behind the player. */
    fun isVisible(index: Int): Boolean {
        val p = synchronized(creatures) {
            creatures.getOrNull(index)?.let { floatArrayOf(it.x, .55f, it.z, 1f) }
        } ?: return false
        val clip = FloatArray(4)
        Matrix.multiplyMV(clip, 0, lastVp, 0, p, 0)
        if (clip[3] <= .001f) return false
        val nx = clip[0] / clip[3]
        val ny = clip[1] / clip[3]
        return nx in -1.05f..1.05f && ny in -1.08f..1.08f
    }

    fun celebrate(index: Int, big: Boolean) {
        val c = creatures.getOrNull(index) ?: return
        c.happyT = if (big) 3.2f else 2f
        c.pauseT = 1f
        if (c.under) { c.under = false; c.underT = 6f }
        if (c.sleeping) { c.sleeping = false; c.awakeT = 30f; audioEvents.add(intArrayOf(EV_WAKE, c.species)) }
    }

    /** Thought-bubble hearts: 0 = whole, -1/+1 = the cracked halves. */
    private fun heartForm(half: Int) = forms.getOrPut(-30 - half) {
        val b = MeshBuilder()
        if (half == 0) {
            val rose = android.graphics.Color.rgb(255, 92, 148)
            b.ellipsoid(-0.26f, 0.34f, 0f, 0.33f, 0.31f, 0.25f, rose, 0.3f)
            b.ellipsoid(0.26f, 0.34f, 0f, 0.33f, 0.31f, 0.25f, rose, 0.3f)
            b.cone(0f, 0.14f, 0f, 0.50f, 0f, -1f, 0f, 0.80f, rose, 0.3f, 8)
        } else {
            // One lobe and a splintered point, leaning away from the break.
            val ash = android.graphics.Color.rgb(214, 84, 122)
            b.ellipsoid(half * 0.24f, 0.36f, 0f, 0.30f, 0.29f, 0.23f, ash, 0.12f)
            b.cone(half * 0.12f, 0.16f, 0f, 0.32f, half * 0.3f, -1f, 0f, 0.62f, ash, 0.12f, 6)
        }
        b.bake()
    }

    /** Hand-sized food models, cached beside the creature forms. */
    private fun itemForm(kind: Int) = forms.getOrPut(-20 - kind) {
        val b = MeshBuilder()
        when (kind) {
            KIND_HONEY -> {
                b.box(0f, 0.10f, 0f, 0.085f, 0.10f, 0.085f, android.graphics.Color.rgb(232, 160, 60))
                b.box(0f, 0.215f, 0f, 0.095f, 0.025f, 0.095f, android.graphics.Color.rgb(120, 82, 50))
            }
            KIND_PUDDING -> {
                b.box(0f, 0.05f, 0f, 0.11f, 0.05f, 0.11f, android.graphics.Color.rgb(214, 178, 128))
                b.cone(0f, 0.10f, 0f, 0.09f, 0f, 1f, 0f, 0.16f, android.graphics.Color.rgb(255, 214, 235), 0.55f, 8)
            }
            else -> {   // a plump trail-berry cluster with a leaf-tip stem
                b.ellipsoid(-0.05f, 0.07f, 0f, 0.055f, 0.055f, 0.055f, android.graphics.Color.rgb(255, 110, 170))
                b.ellipsoid(0.05f, 0.07f, 0.02f, 0.05f, 0.05f, 0.05f, android.graphics.Color.rgb(255, 90, 150))
                b.ellipsoid(0f, 0.13f, -0.02f, 0.05f, 0.05f, 0.05f, android.graphics.Color.rgb(255, 130, 185))
                b.cone(0f, 0.185f, 0f, 0.03f, 0f, 1f, 0f, 0.06f, android.graphics.Color.rgb(70, 170, 90), 0.5f, 5)
            }
        }
        b.bake()
    }

    /** Tossed food arcs and lands; offered food homes to its mouth; anything
     *  on the ground goes back to the satchel when walked over. GL thread. */
    private fun updateLooseItems(dt: Float) {
        if (looseItems.isEmpty()) return
        for (li in looseItems) {
            if (li.homeIndex >= 0) {
                val c = synchronized(creatures) { creatures.getOrNull(li.homeIndex) }
                if (c == null || c.releasing) { looseItems.remove(li); continue }
                val dx = c.x - li.x; val dy = 0.45f - li.y; val dz = c.z - li.z
                val d = kotlin.math.sqrt(dx * dx + dy * dy + dz * dz)
                if (d < 0.35f) {
                    val enjoyed = acceptFeed(li.homeIndex)
                    itemEvents.add(intArrayOf(IEV_FED, li.kind, c.species, if (enjoyed) 1 else 0))
                    looseItems.remove(li)
                } else {
                    val s = 7f * dt / d
                    li.x += dx * s; li.y += dy * s; li.z += dz * s
                }
            } else if (li.vy != 0f || li.y > 0.13f) {
                li.vy -= 6.5f * dt
                li.x += li.vx * dt; li.y += li.vy * dt; li.z += li.vz * dt
                if (li.y <= 0.12f) { li.y = 0.12f; li.vx = 0f; li.vy = 0f; li.vz = 0f }
            } else {
                // Arm's reach, not boot-sole reach: a berry that rolled against
                // a rock must still be reclaimable from beside it.
                val dx = li.x - camPX; val dz = li.z - camPZ
                if (dx * dx + dz * dz < 1.1f * 1.1f) {
                    itemEvents.add(intArrayOf(IEV_PICKUP, li.kind, 0))
                    looseItems.remove(li)
                }
            }
        }
    }

    /** The hunter sent this resident's kin to fetch treasure in the real
     *  world: mirror the errand — a sprint for the horizon and back. */
    fun questDash(index: Int) {
        val c = creatures.getOrNull(index) ?: return
        c.sleeping = false; c.under = false
        c.fleeing = false; c.migrating = false
        c.questT = 3.2f
        c.questDir = if (c.x < Habitats.WORLD_W / 2f) 1f else -1f
        c.happyT = 1f
    }

    /** A treasure was struck out on the real walk: rain gold over the den and
     *  set every resident hopping. The particle shower is spawned by update()
     *  from cheerT, so nothing but a volatile is touched off the GL thread. */
    fun treasureCheer() {
        cheerT = 1.8f
        synchronized(creatures) {
            for (c in creatures) {
                if (c.releasing) continue
                c.happyT = 2.6f; c.pauseT = 0.8f
                if (c.under) { c.under = false; c.underT = 6f }
                if (c.sleeping) { c.sleeping = false; c.awakeT = 30f }
            }
        }
    }

    /** A physically accepted food toss interrupts vigilance, not an active
     *  escape. Returns whether the creature actually ENJOYED it: a calm one
     *  eats happily (whole hearts bubble from its head), a terrified one eats
     *  because it must (the hearts crack). Either way it settles in to eat. */
    fun acceptFeed(index: Int): Boolean {
        val c = creatures.getOrNull(index) ?: return true
        val wary = c.fleeing || learningFor(c.species).fear > 0.55f
        c.alert = false; c.aware = true; c.investigating = false
        c.soliciting = false; c.solicitCd = 1.2f
        c.foraging = false; c.forageFood = -1; c.forageWaypoint = false; c.hunger = .08f
        c.pauseT = 1.4f; c.targetX = Float.NaN
        c.fleeing = false
        if (c.sleeping) {   // food under the nose wakes anyone
            c.sleeping = false; c.awakeT = 30f
            audioEvents.add(intArrayOf(EV_WAKE, c.species))
        }
        c.feedingT = 2.4f              // the species' own munching animation
        c.bubbleT = 2.8f
        c.bubbleBroken = wary
        if (wary) c.happyT = 0f else celebrate(index, big = true)
        return !wary
    }

    /** Set a resident free: it runs for the world's edge and is gone. */
    fun release(index: Int) {
        val c = creatures.getOrNull(index) ?: return
        c.releasing = true
        c.sleeping = false; c.under = false
        c.targetX = if (c.x < Habitats.WORLD_W / 2f) -2.5f else Habitats.WORLD_W + 2.5f
        c.targetZ = c.z
        selected = -1
    }

    fun pick(px: Float, py: Float): Int {
        val vpm = lastVp
        var bestI = -1; var bestD = (viewW * 0.13f) * (viewW * 0.13f)
        // A creature you're standing on top of: its body center may project
        // off-screen or to an edge, so screen-space picking would miss it and
        // grab a farther one mid-view. Prefer the creature you're beside.
        var nearI = -1; var nearD = 2.2f * 2.2f
        val inV = FloatArray(4); val outV = FloatArray(4)
        synchronized(creatures) {
            for ((i, c) in creatures.withIndex()) {
                if (c.releasing) continue
                val gx = c.x - camPX; val gz = c.z - camPZ
                val g2 = gx * gx + gz * gz
                if (g2 < nearD) { nearD = g2; nearI = i }
                inV[0] = c.x; inV[1] = 0.5f; inV[2] = c.z; inV[3] = 1f
                Matrix.multiplyMV(outV, 0, vpm, 0, inV, 0)
                if (outV[3] <= 0f) continue
                val sx = (outV[0] / outV[3] * 0.5f + 0.5f) * viewW
                val sy = (1f - (outV[1] / outV[3] * 0.5f + 0.5f)) * viewH
                val d = (sx - px) * (sx - px) + (sy - py) * (sy - py)
                if (d < bestD) { bestD = d; bestI = i }
            }
        }
        // Being right beside a creature wins over a mid-screen tap on a distant one.
        return if (nearI >= 0) nearI else bestI
    }

    // ------------------------------------------------------------ GL life

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        mainProg = DenGL.program(DenGL.MAIN_VS, DenGL.MAIN_FS)
        skyProg = DenGL.program(DenGL.SKY_VS, DenGL.SKY_FS)
        ptProg = DenGL.program(DenGL.PT_VS, DenGL.PT_FS)
        waterProg = DenGL.program(DenGL.WATER_VS, DenGL.WATER_FS)
        glEnable(GL_DEPTH_TEST)
        forms.clear()
        shadowMesh = CreatureForms.shadow()
        nestMesh = buildNest()
        flameMesh = MeshBuilder().apply {
            cone(0f, 0f, 0f, 0.07f, 0f, 1f, 0f, 0.34f, android.graphics.Color.rgb(255, 150, 60), 0.9f, 6)
            cone(0f, 0.03f, 0f, 0.04f, 0f, 1f, 0f, 0.24f, android.graphics.Color.rgb(255, 230, 130), 0.95f, 5)
        }.bake()
        glowMesh = MeshBuilder().apply {
            ellipsoid(0f, 0f, 0f, 0.12f, 0.14f, 0.12f, android.graphics.Color.rgb(255, 190, 90), 0.95f, 6, 8)
        }.bake()
        ringMesh = MeshBuilder().apply {
            val segs = 22
            for (k in 0 until segs) {
                val a0 = k * (Math.PI * 2 / segs).toFloat(); val a1 = (k + 1) * (Math.PI * 2 / segs).toFloat()
                quad(cos(a0) * 0.9f, 0f, sin(a0) * 0.9f, cos(a1) * 0.9f, 0f, sin(a1) * 0.9f,
                    cos(a1), 0f, sin(a1), cos(a0), 0f, sin(a0),
                    android.graphics.Color.rgb(190, 245, 255), 0.85f, true)
            }
        }.bake()
        buildWaterDisc()
        rebuild = true
        lastNs = 0L
    }

    /** A tessellated unit disc whose vertices the water shader can wave. */
    private fun buildWaterDisc() {
        val rings = 6; val sectors = 18
        val v = ArrayList<Float>()
        fun pt(ri: Int, si: Int): FloatArray {
            val r = ri.toFloat() / rings
            val a = si * (Math.PI * 2 / sectors)
            return floatArrayOf((cos(a) * r).toFloat(), 0f, (sin(a) * r).toFloat())
        }
        for (ri in 0 until rings) for (si in 0 until sectors) {
            val a = pt(ri, si); val b = pt(ri + 1, si)
            val c = pt(ri + 1, si + 1); val d = pt(ri, si + 1)
            v.addAll(listOf(a[0], a[1], a[2], b[0], b[1], b[2], c[0], c[1], c[2]))
            v.addAll(listOf(a[0], a[1], a[2], c[0], c[1], c[2], d[0], d[1], d[2]))
        }
        waterVerts = v.size / 3
        waterBuf = ByteBuffer.allocateDirect(v.size * 4).order(ByteOrder.nativeOrder())
            .asFloatBuffer().put(v.toFloatArray()).apply { position(0) }
    }

    private fun waterAt(x: Float, z: Float): WaterBody? =
        waterBodies.firstOrNull {
            val dx = x - it.x; val dz = z - it.z
            dx * dx + dz * dz < it.r * it.r * 0.72f
        }

    override fun onSurfaceChanged(gl: GL10?, w: Int, h: Int) {
        viewW = w; viewH = h
        glViewport(0, 0, w, h)
        Matrix.perspectiveM(proj, 0, 58f, w.toFloat() / h, 0.3f, 60f)
    }

    override fun onDrawFrame(gl: GL10?) {
        val now = System.nanoTime()
        val dt = if (lastNs == 0L) 0.016f else ((now - lastNs) / 1e9f).coerceIn(0.001f, 0.05f)
        lastNs = now; t += dt

        val day = dayLerp()
        frameDaylight = day
        if (abs(day - bakedDay) > 0.15f) rebuild = true   // the earth relights slowly
        if (rebuild) { rebuild = false; bakedDay = day; rebuildScene() }
        flashT = (flashT - dt).coerceAtLeast(0f)
        if (weatherCode >= 95 && Random.nextFloat() < dt * 0.12f) flashT = 0.18f

        // Fast-travel glide, then free walking.
        if (!glideX.isNaN()) {
            camPX += (glideX - camPX) * dt * 2.5f
            if (abs(glideX - camPX) < 0.4f) glideX = Float.NaN
        }
        val yawR = Math.toRadians(yawDeg.toDouble()).toFloat()
        val pitchR = Math.toRadians(pitchDeg.toDouble()).toFloat()
        val fwdX = -sin(yawR); val fwdZ = -cos(yawR)
        val rightX = cos(yawR); val rightZ = -sin(yawR)
        val speed = 3.1f
        camPX = (camPX + (fwdX * moveY + rightX * moveX) * speed * dt)
            .coerceIn(0.4f, Habitats.WORLD_W - 0.4f)
        camPZ = (camPZ + (fwdZ * moveY + rightZ * moveX) * speed * dt)
            .coerceIn(-12f, 12f)
        // The walker doesn't ghost through trees either — but can never be
        // trapped: if resolve can't free it from a cluster, it steps to clear.
        if (glideX.isNaN()) {
            val cf = resolve(camPX, camPZ, 0.32f)
            camPX = cf[0]; camPZ = cf[1].coerceIn(-12f, 12f)
            if (overlaps(camPX, camPZ, 0.30f)) {
                val fc = findClear(camPX, camPZ, 0.34f)
                camPX = fc[0]; camPZ = fc[1].coerceIn(-12f, 12f)
            }
        }
        val lookX = camPX + fwdX * cos(pitchR)
        val lookY = camPY + sin(pitchR)
        val lookZ = camPZ + fwdZ * cos(pitchR)
        // Record the human's motion and gaze for the creatures to read.
        playerVX = (camPX - prevCamX) / dt.coerceAtLeast(0.001f)
        playerVZ = (camPZ - prevCamZ) / dt.coerceAtLeast(0.001f)
        playerSpeed = kotlin.math.sqrt(playerVX * playerVX + playerVZ * playerVZ)
        prevCamX = camPX; prevCamZ = camPZ
        fwdXv = fwdX; fwdZv = fwdZ
        // Perception uses this frame's pose and kinematics, not the previous one.
        step(dt)
        updateLooseItems(dt)
        Matrix.setLookAtM(view, 0, camPX, camPY, camPZ, lookX, lookY, lookZ, 0f, 1f, 0f)
        Matrix.multiplyMM(vp, 0, proj, 0, view, 0)
        lastVp = vp.clone()

        glClear(GL_COLOR_BUFFER_BIT or GL_DEPTH_BUFFER_BIT)

        // Sky: biome blend by position, then the real hour and weather over it.
        var skyTop = dayMix(Habitats.blendColor(camPX) { it.skyTop }, day)
        var skyBot = dayMix(Habitats.blendColor(camPX) { it.skyBot }, day)
        val fog = dayMix(Habitats.blendColor(camPX) { it.fog }, day)
        if (flashT > 0f) {   // lightning washes the whole sky white
            skyTop = android.graphics.Color.rgb(230, 235, 255)
            skyBot = android.graphics.Color.rgb(200, 215, 245)
        }
        glDisable(GL_DEPTH_TEST)
        glUseProgram(skyProg)
        glUniform3f(glGetUniformLocation(skyProg, "uTop"), red(skyTop), green(skyTop), blue(skyTop))
        glUniform3f(glGetUniformLocation(skyProg, "uBot"), red(skyBot), green(skyBot), blue(skyBot))
        val aSky = glGetAttribLocation(skyProg, "aPos")
        glEnableVertexAttribArray(aSky)
        skyBuf.position(0)
        glVertexAttribPointer(aSky, 2, GL_FLOAT, false, 0, skyBuf)
        glDrawArrays(GL_TRIANGLE_STRIP, 0, 4)
        glDisableVertexAttribArray(aSky)
        glEnable(GL_DEPTH_TEST)

        glUseProgram(mainProg)
        val uVP = glGetUniformLocation(mainProg, "uVP")
        val uM = glGetUniformLocation(mainProg, "uM")
        glUniformMatrix4fv(uVP, 1, false, vp, 0)
        glUniform3f(glGetUniformLocation(mainProg, "uCam"), camPX, camPY, camPZ)
        glUniform3f(glGetUniformLocation(mainProg, "uLight"), 0.35f, 0.8f, 0.5f)
        glUniform3f(glGetUniformLocation(mainProg, "uFog"), red(fog), green(fog), blue(fog))
        glUniform3f(glGetUniformLocation(mainProg, "uRim"), 0.55f, 0.85f, 1f)
        glUniform1f(glGetUniformLocation(mainProg, "uDay"),
            if (flashT > 0f) 1f else day)
        glUniform1f(glGetUniformLocation(mainProg, "uFogNear"), if (foggy) 3f else 9f)
        val aPos = glGetAttribLocation(mainProg, "aPos")
        val aNrm = glGetAttribLocation(mainProg, "aNrm")
        val aCol = glGetAttribLocation(mainProg, "aCol")
        glEnableVertexAttribArray(aPos); glEnableVertexAttribArray(aNrm); glEnableVertexAttribArray(aCol)

        glUniform1f(glGetUniformLocation(mainProg, "uAlpha"), 1f)
        Matrix.setIdentityM(model, 0)
        glUniformMatrix4fv(uM, 1, false, model, 0)
        sceneMesh?.draw(aPos, aNrm, aCol)
        for (mesh in itemMeshes) mesh.draw(aPos, aNrm, aCol)

        // The fires burn: flames flicker and sway with the actual wind.
        val windLean = (windKmh / 10f).coerceAtMost(3f)
        for ((i, f) in flamePts.withIndex()) {
            Matrix.setIdentityM(model, 0)
            Matrix.translateM(model, 0, f[0], f[1], f[2])
            Matrix.rotateM(model, 0, sin(t * 5.5f + i * 1.7f) * 7f + windLean * 4f, 0f, 0f, 1f)
            Matrix.scaleM(model, 0, 1f, 0.82f + 0.3f * sin(t * 7f + i * 2.3f) + 0.1f * sin(t * 13f + i), 1f)
            glUniformMatrix4fv(uM, 1, false, model, 0)
            flameMesh?.draw(aPos, aNrm, aCol)
        }
        for ((i, l) in lanternPts.withIndex()) {
            Matrix.setIdentityM(model, 0)
            Matrix.translateM(model, 0, l[0], l[1], l[2])
            val pulse = 1f + 0.1f * sin(t * 3f + i * 2f) + 0.04f * sin(t * 11f + i)
            Matrix.scaleM(model, 0, pulse, pulse, pulse)
            glUniformMatrix4fv(uM, 1, false, model, 0)
            glowMesh?.draw(aPos, aNrm, aCol)
        }

        synchronized(creatures) {
            // Nests first, so their residents sit on top.
            for (c in creatures) {
                if (c.releasing) continue
                Matrix.setIdentityM(model, 0)
                Matrix.translateM(model, 0, c.nestX, 0f, c.nestZ)
                glUniformMatrix4fv(uM, 1, false, model, 0)
                nestMesh?.draw(aPos, aNrm, aCol)
            }
            for ((i, c) in creatures.withIndex()) {
                val sp = Species.ALL[c.species]
                if (c.migrationTransitT > 0f) continue
                if (c.under) {
                    Matrix.setIdentityM(model, 0)
                    Matrix.translateM(model, 0, c.x, 0f, c.z)
                    Matrix.scaleM(model, 0, 0.62f, 0.62f + sin(c.phase * 6f) * 0.06f, 0.62f)
                    glUniformMatrix4fv(uM, 1, false, model, 0)
                    forms.getOrPut(-2) { CreatureForms.mound() }.draw(aPos, aNrm, aCol)
                    continue
                }
                val hop = when {
                    c.sleeping -> 0f
                    sp.motion == Species.HOP -> abs(sin(c.phase * 4f)) * 0.22f
                    sp.motion == Species.FLOAT -> 0.25f + sin(c.phase * 1.6f) * 0.1f
                    sp.motion == Species.DRIFT -> 0.12f + sin(c.phase * 2.2f) * 0.07f
                    sp.motion == Species.SKIM -> 0.5f + sin(c.phase * 2.6f) * 0.18f
                    sp.motion == Species.SWIM -> -0.14f + sin(c.phase * 2f) * 0.05f
                    else -> abs(sin(c.phase * 5f)) * 0.035f
                }
                val squash = when {
                    c.sleeping -> 0.82f + sin(t * 1.2f + c.phase) * 0.03f   // slow breathing
                    else -> 1f + sin(c.phase * 5f) * 0.04f +
                        (if (c.happyT > 0f) sin(c.happyT * 14f) * 0.08f else 0f)
                }
                shadowDraw(c, aPos, aNrm, aCol, uM)
                // Affective display: state and disposition read straight off
                // the body — posture, carriage, tremble — and off the face.
                val affect = AffectDisplay.assess(c,
                    fieldFear.getOrElse(c.species) { 0f },
                    fieldFamiliar.getOrElse(c.species) { 0f },
                    fieldFood.getOrElse(c.species) { 0f },
                    sp.energy, t)
                Matrix.setIdentityM(model, 0)
                Matrix.translateM(model, 0, c.x + affect.jitterX, hop + c.alt, c.z + affect.jitterZ)
                Matrix.rotateM(model, 0, c.headingDeg, 0f, 1f, 0f)
                if (c.bank != 0f) Matrix.rotateM(model, 0, c.bank, 0f, 0f, 1f)
                if (affect.pitch != 0f) Matrix.rotateM(model, 0, affect.pitch, 1f, 0f, 0f)
                if (affect.roll != 0f) Matrix.rotateM(model, 0, affect.roll, 0f, 0f, 1f)
                // Mealtime, each to its own table manners: grazers put their
                // head down and munch, darters peck, floaters and swimmers
                // dip their whole body toward the morsel.
                var chew = 1f
                if (c.feedingT > 0f && !c.sleeping) {
                    val bite = sin(t * 10f + c.phase * 3f)
                    val nod = when (sp.motion) {
                        Species.FLOAT, Species.DRIFT -> 6f + bite * 6f
                        Species.SKIM, Species.SWIM -> 10f + bite * 5f
                        Species.DART -> 4f + bite * 10f
                        else -> 9f + bite * 9f
                    }
                    Matrix.rotateM(model, 0, nod, 1f, 0f, 0f)
                    chew = 1f + sin(t * 11f + c.phase) * 0.06f
                }
                val sz = 0.62f * c.scale * affect.stature
                Matrix.scaleM(model, 0, sz, sz * squash * chew * affect.crouch, sz)
                glUniformMatrix4fv(uM, 1, false, model, 0)
                forms.getOrPut(c.species) { CreatureForms.build(c.species) }.draw(aPos, aNrm, aCol)
                AffectDisplay.drawFace(c.species, affect, model, uM, aPos, aNrm, aCol, t, c.phase)
                if (i == selected) {
                    Matrix.setIdentityM(model, 0)
                    Matrix.translateM(model, 0, c.x, hop + c.alt + 1.05f + sin(t * 3f) * 0.05f, c.z)
                    Matrix.rotateM(model, 0, t * 90f, 0f, 1f, 0f)
                    Matrix.scaleM(model, 0, 0.16f, 0.16f, 0.16f)
                    glUniformMatrix4fv(uM, 1, false, model, 0)
                    forms.getOrPut(-1) {
                        val b = MeshBuilder()
                        b.cone(0f, 0f, 0f, 0.8f, 0f, 1f, 0f, 1f, android.graphics.Color.WHITE, 0.9f, 4)
                        b.cone(0f, 0f, 0f, 0.8f, 0f, -1f, 0f, 1f, android.graphics.Color.WHITE, 0.9f, 4)
                        b.bake()
                    }.draw(aPos, aNrm, aCol)
                }
                // The verdict, thought aloud: hearts bubble up from the head
                // one after another — whole and glowing when the meal was a
                // joy; cracked in two, drifting apart and drooping, when fear
                // spoiled the taste.
                if (c.bubbleT > 0f) {
                    val uAlphaB = glGetUniformLocation(mainProg, "uAlpha")
                    glEnable(GL_BLEND); glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)
                    glDepthMask(false)
                    val age = 2.8f - c.bubbleT
                    val headY = hop + 1.02f * c.scale
                    for (k in 0 until 3) {
                        val life = age - k * 0.42f
                        if (life < 0f || life > 1.6f) continue
                        val fade = when {
                            life < 0.22f -> life / 0.22f
                            life > 1.25f -> ((1.6f - life) / 0.35f).coerceIn(0f, 1f)
                            else -> 1f
                        }
                        glUniform1f(uAlphaB, fade)
                        if (!c.bubbleBroken) {
                            val s = (0.105f + (k % 2) * 0.025f) * c.scale * (0.55f + 0.45f * fade)
                            Matrix.setIdentityM(model, 0)
                            Matrix.translateM(model, 0,
                                c.x + sin(t * 3.4f + k * 2.1f) * 0.07f,
                                headY + 0.24f + life * 0.62f, c.z)
                            Matrix.rotateM(model, 0, t * 70f + k * 45f, 0f, 1f, 0f)
                            Matrix.scaleM(model, 0, s, s, s)
                            glUniformMatrix4fv(uM, 1, false, model, 0)
                            heartForm(0).draw(aPos, aNrm, aCol)
                        } else {
                            val droop = life * 0.30f - life * life * 0.42f
                            for (h in intArrayOf(-1, 1)) {
                                val s = 0.10f * c.scale * (0.6f + 0.4f * fade)
                                Matrix.setIdentityM(model, 0)
                                Matrix.translateM(model, 0,
                                    c.x + h * life * 0.17f,
                                    headY + 0.20f + droop, c.z)
                                Matrix.rotateM(model, 0, sin(t * 2f + k) * 20f, 0f, 1f, 0f)
                                Matrix.rotateM(model, 0, h * (16f + life * 34f), 0f, 0f, 1f)
                                Matrix.scaleM(model, 0, s, s, s)
                                glUniformMatrix4fv(uM, 1, false, model, 0)
                                heartForm(h).draw(aPos, aNrm, aCol)
                            }
                        }
                    }
                    glUniform1f(uAlphaB, 1f)
                    glDepthMask(true)
                    glDisable(GL_BLEND)
                }
            }
        }
        // Satchel food: carried at hand height ahead of the walker, arcing
        // through the air after a toss, or resting where it fell.
        if (carriedKind >= 0) {
            val bobY = camPY - 0.42f + sin(t * 6f) * 0.02f +
                (playerSpeed * 0.02f) * sin(t * 11f)
            Matrix.setIdentityM(model, 0)
            Matrix.translateM(model, 0, camPX + fwdXv * 0.8f, bobY, camPZ + fwdZv * 0.8f)
            Matrix.rotateM(model, 0, yawDeg, 0f, 1f, 0f)
            glUniformMatrix4fv(uM, 1, false, model, 0)
            itemForm(carriedKind).draw(aPos, aNrm, aCol)
        }
        for (li in looseItems) {
            Matrix.setIdentityM(model, 0)
            Matrix.translateM(model, 0, li.x, li.y, li.z)
            Matrix.rotateM(model, 0, t * 140f, 0f, 1f, 0f)
            glUniformMatrix4fv(uM, 1, false, model, 0)
            itemForm(li.kind).draw(aPos, aNrm, aCol)
        }
        // Ripple rings expand and fade on the ponds.
        if (ripples.isNotEmpty()) {
            glEnable(GL_BLEND); glBlendFunc(GL_SRC_ALPHA, GL_ONE)
            glDepthMask(false)
            val uAlpha = glGetUniformLocation(mainProg, "uAlpha")
            for (rp in ripples) {
                val s = 0.22f + rp.age * 1.3f
                glUniform1f(uAlpha, (1f - rp.age / 0.9f).coerceIn(0f, 1f) * 0.75f)
                Matrix.setIdentityM(model, 0)
                Matrix.translateM(model, 0, rp.x, 0.06f, rp.z)
                Matrix.scaleM(model, 0, s, 1f, s * 0.8f)
                glUniformMatrix4fv(uM, 1, false, model, 0)
                ringMesh?.draw(aPos, aNrm, aCol)
            }
            glUniform1f(uAlpha, 1f)
            glDepthMask(true)
            glDisable(GL_BLEND)
        }
        glDisableVertexAttribArray(aPos); glDisableVertexAttribArray(aNrm); glDisableVertexAttribArray(aCol)

        // The living water: the bordering biome sea, then the ponds and springs.
        waterBuf?.let { wb ->
            glUseProgram(waterProg)
            glUniformMatrix4fv(glGetUniformLocation(waterProg, "uVP"), 1, false, vp, 0)
            glUniform1f(glGetUniformLocation(waterProg, "uT"), t)
            glDepthMask(false)
            glEnable(GL_BLEND)
            val aW = glGetAttribLocation(waterProg, "aPos")
            glEnableVertexAttribArray(aW)
            wb.position(0)
            glVertexAttribPointer(aW, 3, GL_FLOAT, false, 12, wb)
            val uMW = glGetUniformLocation(waterProg, "uM")
            val uCol = glGetUniformLocation(waterProg, "uCol")
            val uFlat = glGetUniformLocation(waterProg, "uFlat")

            // The ocean ring and frozen ponds: opaque, normal blend so they
            // read as solid water and ice rather than glowing.
            glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)
            glUniform1f(uFlat, 1f)
            for (o in borderWater) {
                val c = Habitats.seaColor(o[0])
                Matrix.setIdentityM(model, 0)
                Matrix.translateM(model, 0, o[0], -0.16f, o[1])
                Matrix.scaleM(model, 0, o[2], 1f, o[2])
                glUniformMatrix4fv(uMW, 1, false, model, 0)
                glUniform3f(uCol, red(c), green(c), blue(c))
                glDrawArrays(GL_TRIANGLES, 0, waterVerts)
            }
            for (wtr in waterBodies) if (wtr.frozen) {
                val c = Habitats.pondColor(wtr.x)
                Matrix.setIdentityM(model, 0)
                Matrix.translateM(model, 0, wtr.x, 0.05f, wtr.z)
                Matrix.scaleM(model, 0, wtr.r * 0.98f, 1f, wtr.r * 0.74f)
                glUniformMatrix4fv(uMW, 1, false, model, 0)
                glUniform3f(uCol, red(c), green(c), blue(c))
                glDrawArrays(GL_TRIANGLES, 0, waterVerts)
            }

            // Liquid ponds and springs: glowy, additive, biome-tinted.
            glBlendFunc(GL_SRC_ALPHA, GL_ONE)
            glUniform1f(uFlat, 0f)
            for (wtr in waterBodies) if (!wtr.frozen) {
                val c = Habitats.pondColor(wtr.x)
                Matrix.setIdentityM(model, 0)
                Matrix.translateM(model, 0, wtr.x, 0.07f, wtr.z)
                Matrix.scaleM(model, 0, wtr.r * 0.95f, 1f, wtr.r * 0.72f)
                glUniformMatrix4fv(uMW, 1, false, model, 0)
                glUniform3f(uCol, red(c), green(c), blue(c))
                glDrawArrays(GL_TRIANGLES, 0, waterVerts)
            }
            glDisableVertexAttribArray(aW)
            glDepthMask(true)
            glDisable(GL_BLEND)
        }

        drawParticles()
    }

    private fun shadowDraw(c: DenC, aPos: Int, aNrm: Int, aCol: Int, uM: Int) {
        Matrix.setIdentityM(model, 0)
        Matrix.translateM(model, 0, c.x, 0f, c.z)
        Matrix.rotateM(model, 0, c.headingDeg, 0f, 1f, 0f)   // oblong shadows follow the body
        // The higher the body, the smaller its pool of shade on the ground.
        val s = c.scale / (1f + c.alt * 0.7f)
        Matrix.scaleM(model, 0, s, 1f, s)
        glUniformMatrix4fv(uM, 1, false, model, 0)
        shadowMesh?.draw(aPos, aNrm, aCol)
    }

    // ------------------------------------------------------------- scene

    private fun buildNest(): Mesh {
        val b = MeshBuilder()
        for (k in 0 until 8) {
            val a = k * 0.785f
            b.ellipsoid(cos(a) * 0.34f, 0.045f, sin(a) * 0.27f,
                0.09f, 0.045f, 0.05f, android.graphics.Color.rgb(96, 74, 52), 0f, 3, 5)
        }
        b.ellipsoid(0f, 0.015f, 0f, 0.3f, 0.015f, 0.24f, android.graphics.Color.rgb(52, 40, 30), 0f, 3, 8)
        return b.bake()
    }

    private fun rebuildScene() {
        val b = MeshBuilder()
        val w = Habitats.WORLD_W
        // Ground as strips so the earth itself changes with the biome; it
        // reaches only to the beach line, where the shore takes over.
        val strips = 32
        val gz = Habitats.GROUND_Z
        val day = bakedDay.coerceAtLeast(0f)
        for (s in 0 until strips) {
            val x0 = -3f + (w + 6f) * s / strips
            val x1 = -3f + (w + 6f) * (s + 1) / strips
            val col = dayMix(Habitats.blendColor((x0 + x1) / 2f) { it.ground }, day * 0.7f)
            b.quad(x0, 0f, gz, x1, 0f, gz, x1, 0f, -gz, x0, 0f, -gz, col)
        }
        Habitats.buildBorders(b)   // biome-tinted beach fringe on every side
        Habitats.scatterDepth(b)   // background props filling the widened depth
        Habitats.drawFood(b)       // wild forage, matched to each climate
        for (zone in Habitats.ZONES) {
            Habitats.zoneDecor(b, zone, Habitats.biomeAt(zone.x))
        }
        for (biome in Habitats.BIOMES) biome.decor(b)
        sceneMesh = b.bake()
        borderWater = Habitats.oceanDiscs()
        // Gather the elemental furniture: fires, lamps, waters, crystals —
        // and the collision map, so wood and fire are finally solid.
        flamePts.clear(); lanternPts.clear(); sparklePts.clear(); waterBodies.clear()
        obstacles.clear(); perchPts.clear()
        for (zone in Habitats.ZONES) when (zone.kind) {
            "WATER" -> waterBodies += WaterBody(zone.x, zone.z, zone.r, Habitats.isFrozen(zone.x))
            "EMBER" -> for (k in 0..3) {
                val a = k * 1.57f + 0.4f
                val px = zone.x + cos(a) * zone.r * 0.5f
                val pz = zone.z + sin(a) * zone.r * 0.4f
                flamePts += floatArrayOf(px, 0.16f, pz)
                obstacles += floatArrayOf(px, pz, 0.3f)          // fire is not a path
            }
            "THICKET" -> {
                // Only the trunk core is solid; the foliage around it is
                // concealment a creature may slip into and hide.
                obstacles += floatArrayOf(zone.x - zone.r * 0.5f, zone.z - 0.2f, 0.2f)
                obstacles += floatArrayOf(zone.x + zone.r * 0.45f, zone.z + 0.15f, 0.18f)
                obstacles += floatArrayOf(zone.x, zone.z + zone.r * 0.4f, 0.22f)
                obstacles += floatArrayOf(zone.x - zone.r * 0.15f, zone.z - zone.r * 0.45f, 0.19f)
                perchPts += floatArrayOf(zone.x, zone.z + zone.r * 0.4f, 1.6f)
                perchPts += floatArrayOf(zone.x - zone.r * 0.5f, zone.z - 0.2f, 1.5f)
            }
            "STONE" -> {
                obstacles += floatArrayOf(zone.x, zone.z, zone.r * 0.6f)
                obstacles += floatArrayOf(zone.x + zone.r * 0.7f, zone.z - 0.2f, 0.4f)
                perchPts += floatArrayOf(zone.x, zone.z, 0.75f)
            }
            "PERCH" -> {
                obstacles += floatArrayOf(zone.x, zone.z, 0.28f)
                perchPts += floatArrayOf(zone.x, zone.z, 1.5f)
            }
            "VOID" -> for (k in 0..4) {
                val a = k * 1.257f
                obstacles += floatArrayOf(zone.x + cos(a) * zone.r * 0.85f,
                    zone.z + sin(a) * zone.r * 0.65f, 0.16f)
                if (k == 0) perchPts += floatArrayOf(zone.x + zone.r * 0.85f,
                    zone.z, 1.35f)
            }
        }
        // Solid biome scenery (trunks, cacti, the mesa), data-driven per biome.
        for (ob in Habitats.decorObstacles()) obstacles += ob
        for ((i, id) in placedIds.withIndex()) {
            val sx = Habitats.slotX(i); val sz = Habitats.SLOT_Z
            when (id) {
                "lantern" -> {
                    lanternPts += floatArrayOf(sx, 1.06f, sz)
                    obstacles += floatArrayOf(sx, sz, 0.22f)
                    perchPts += floatArrayOf(sx, sz, 1.25f)
                }
                "pool" -> waterBodies += WaterBody(sx, sz, 0.6f, Habitats.isFrozen(sx))
                "gems" -> {
                    sparklePts += floatArrayOf(sx, 0.35f, sz)
                    obstacles += floatArrayOf(sx, sz, 0.35f)
                }
                // bush + nook: leafy concealment, not solids — hideable.
                "drum" -> {
                    obstacles += floatArrayOf(sx, sz, 0.4f)
                    perchPts += floatArrayOf(sx, sz, 0.85f)
                }
                "plinth" -> {
                    obstacles += floatArrayOf(sx, sz, 0.3f)
                    perchPts += floatArrayOf(sx, sz, 0.7f)
                }
            }
        }
        itemMeshes = placedIds.mapIndexedNotNull { i, id ->
            Habitats.item(id)?.let { item ->
                val ib = MeshBuilder()
                item.build(ib, Habitats.slotX(i), Habitats.SLOT_Z)
                ib.bake()
            }
        }
        // Immutable copy lets UI-thread range checks share line-of-sight safely.
        obstacleSnapshot = obstacles.map { it.clone() }
        synchronized(creatures) {
            val want = population
            if (creatures.map { it.species } != want) {
                creatures.clear()
                for ((idx, s) in want.withIndex()) creatures += DenC(s).apply {
                    seasonalBiome = SeasonalEcology.targetBiome(s, seasonNow())
                    val home = seasonalHome(s, seasonalBiome, idx + s * 31)
                    nestX = home[0]; nestZ = home[1]
                    x = nestX + Random.nextFloat() * 2f - 1f
                    z = (nestZ + Random.nextFloat() - 0.5f).coerceIn(-8f, 8f)
                    val sf = resolve(x, z, 0.25f)
                    x = sf[0]; z = sf[1].coerceIn(-8f, 8f)
                }
            }
        }
    }

    // --------------------------------------------------------------- sim

    private fun step(dt: Float) {
        val w = Habitats.WORLD_W
        val season = seasonNow()
        var removed = false
        for (i in foodCooldown.indices)
            foodCooldown[i] = (foodCooldown[i] - dt).coerceAtLeast(0f)
        synchronized(creatures) {
            for (c in creatures) {
                val sp = Species.ALL[c.species]
                val biome = Habitats.biomeAt(c.x)
                val perky = if (sp.temperament in biome.loved) 1.25f else 1f
                c.phase += dt * (if (c.sleeping) 0.2f else (0.8f + sp.energy * 1.6f) * perky)
                c.happyT = (c.happyT - dt).coerceAtLeast(0f)
                c.bubbleT = (c.bubbleT - dt).coerceAtLeast(0f)
                c.meetCd = (c.meetCd - dt).coerceAtLeast(0f)
                // The thought-bubble hearts carry the feeling while they play;
                // the generic sparkle-hearts would muddle the message.
                if (c.happyT > 0.4f && c.bubbleT <= 0f && Random.nextFloat() < dt * 6f)
                    heart(c.x, 0.9f, c.z, big = c.happyT > 2f)

                // Freedom run: straight off the edge of the world.
                if (c.releasing) {
                    val dx = c.targetX - c.x
                    c.vx = (if (dx > 0) 1f else -1f) * 4.5f
                    c.vz = 0f
                    c.x += c.vx * dt
                    turnToward(c, sp, dt)
                    if (Random.nextFloat() < dt * 8f) heart(c.x, 0.8f, c.z, false)
                    if (abs(dx) < 1f) { removed = true }
                    continue
                }

                // Fetch-quest dash: the hunter sent this one for real-world
                // treasure — it sprints for the horizon, digs, races home.
                if (c.questT > 0f) {
                    c.questT -= dt
                    val out = c.questT > 1.6f
                    c.vx = (if (out) c.questDir else -c.questDir) * 5f
                    c.vz = 0f
                    c.x = (c.x + c.vx * dt).coerceIn(0.8f, Habitats.WORLD_W - 0.8f)
                    turnToward(c, sp, dt)
                    if (Random.nextFloat() < dt * 6f) heart(c.x, 0.7f, c.z, false)
                    if (c.questT <= 0f) { c.targetX = Float.NaN; c.happyT = 1.5f }
                    continue
                }

                // Water specialists use connected subsurface channels between
                // distant seasonal pools; they never slide unrealistically over land.
                if (c.migrationTransitT > 0f) {
                    c.migrationTransitT -= dt
                    c.vx = 0f; c.vz = 0f
                    if (c.migrationTransitT <= 0f) {
                        c.x = c.nestX; c.z = c.nestZ
                        c.migrating = false
                        c.targetX = Float.NaN
                        ripples += Ripple(c.x, c.z)
                    }
                    continue
                }

                // The clock: wander while awake, then home to the nest.
                if (c.sleeping) {
                    c.awakeT -= dt   // reused as remaining sleep
                    c.vx = 0f; c.vz = 0f
                    if (Random.nextFloat() < dt * 1.4f)
                        zzz(c.x, 0.8f + Random.nextFloat() * 0.3f, c.z)
                    if (c.awakeT <= 0f) {
                        c.sleeping = false
                        c.awakeT = (30f + Random.nextFloat() * 50f) * (0.6f + sp.energy)
                        audioEvents.add(intArrayOf(EV_WAKE, c.species))
                    }
                    continue
                }

                val seasonal = SeasonalEcology.of(c.species)
                c.hunger = (c.hunger + seasonal.hungerRate * dt).coerceIn(0f, 1f)
                c.forageCheckT = (c.forageCheckT - dt).coerceAtLeast(0f)
                c.feedingT = (c.feedingT - dt).coerceAtLeast(0f)

                // Real-calendar seasonal range shifts. Intermediate biome centers
                // are waypoints, so a terrestrial animal traverses the world instead
                // of teleporting across the climate transect.
                c.migrationCheckT -= dt
                if (c.migrationCheckT <= 0f) {
                    c.migrationCheckT = 1.5f + Random.nextFloat() * 2.5f
                    val desiredBiome = seasonal.targetBiome(season)
                    if (desiredBiome != c.seasonalBiome) {
                        c.seasonalBiome = desiredBiome
                        val home = seasonalHome(c.species, desiredBiome, c.species * 31 + desiredBiome)
                        c.nestX = home[0]; c.nestZ = home[1]
                        c.homing = false; c.sleeping = false
                        c.foraging = false; c.forageFood = -1; c.forageWaypoint = false
                        c.targetX = Float.NaN
                        if (EcologyModel.of(c.species).medium == PreferredMedium.WATER) {
                            c.migrating = true; c.migrationTransitT = 2.4f
                            ripples += Ripple(c.x, c.z)
                            continue
                        }
                    }
                    val currentBiome = biomeIndexAt(c.x)
                    if (currentBiome != c.seasonalBiome &&
                        EcologyModel.of(c.species).medium == PreferredMedium.WATER &&
                        !c.fleeing && !c.soliciting) {
                        c.migrating = true; c.migrationTransitT = 2.4f
                        c.foraging = false; c.forageFood = -1; c.forageWaypoint = false
                        c.targetX = Float.NaN
                        ripples += Ripple(c.x, c.z)
                        continue
                    }
                    if (currentBiome != c.seasonalBiome && !c.fleeing && !c.soliciting &&
                        !c.alert && !c.homing && !c.foraging) {
                        val next = currentBiome + if (c.seasonalBiome > currentBiome) 1 else -1
                        val finalLeg = next == c.seasonalBiome
                        c.migrating = true
                        c.targetX = if (finalLeg) c.nestX else Habitats.BIOMES[next].center
                        c.targetZ = if (finalLeg) c.nestZ else ((c.species % 5) - 2) * .55f
                        val distance = abs(c.targetX - c.x)
                        c.targetT = maxOf(18f, distance / (.55f + sp.energy * .7f) + 8f)
                    } else if (currentBiome == c.seasonalBiome && c.migrating) {
                        val dx = c.nestX - c.x; val dz = c.nestZ - c.z
                        val homeDistance = kotlin.math.sqrt(dx * dx + dz * dz)
                        if (homeDistance > .65f) {
                            c.targetX = c.nestX; c.targetZ = c.nestZ
                            c.targetT = maxOf(12f, homeDistance / (.55f + sp.energy * .7f) + 6f)
                        } else {
                            c.migrating = false
                            c.targetX = Float.NaN
                        }
                    }
                }
                // Sleep pressure follows each species' full circadian profile,
                // including dusk specialists and cathemeral sentinels/swimmers.
                val activityGain = EcologyModel.activityGain(EcologyModel.of(c.species), frameDaylight)
                val offHours = (1.10f - activityGain).coerceIn(0f, .8f)
                c.awakeT -= dt * (1f + offHours * 1.2f)
                if (c.awakeT <= 0f && !c.homing && !c.migrating) {
                    c.homing = true; c.targetX = c.nestX; c.targetZ = c.nestZ; c.targetT = 14f
                }
                if (c.homing && abs(c.x - c.nestX) < 0.4f && abs(c.z - c.nestZ) < 0.4f) {
                    c.homing = false
                    c.sleeping = true
                    // Sleep length by temperament: the dozy sleep long.
                    c.awakeT = (10f + Random.nextFloat() * 10f) * (1.6f - sp.energy)
                    c.targetX = Float.NaN
                    continue
                }

                if (sp.motion == Species.BURROW) {
                    c.underT -= dt
                    if (c.underT <= 0f) {
                        c.under = !c.under
                        c.underT = if (c.under) 2.5f + Random.nextFloat() * 3f else 4f + Random.nextFloat() * 4f
                        if (!c.under) c.happyT = maxOf(c.happyT, 0.8f)
                    }
                }

                // ---- Multichannel perception of the human ----
                c.startleCd = (c.startleCd - dt).coerceAtLeast(0f)
                c.solicitCd = (c.solicitCd - dt).coerceAtLeast(0f)
                c.closeCd = (c.closeCd - dt).coerceAtLeast(0f)
                c.alert = false; c.aware = false; c.investigating = false

                val eco = EcologyModel.of(c.species)
                val dxp = c.x - camPX; val dzp = c.z - camPZ
                val centerP = kotlin.math.sqrt(dxp * dxp + dzp * dzp).coerceAtLeast(.001f)
                val distP = (centerP - eco.bodyRadius - .24f).coerceAtLeast(.05f)
                val toX = dxp / centerP; val toZ = dzp / centerP
                val approach = playerVX * toX + playerVZ * toZ
                val gaze = (fwdXv * toX + fwdZv * toZ).coerceIn(0f, 1f)
                var kin = 0
                for (o in creatures) if (o !== c && o.species == c.species && !o.releasing) {
                    val odx = o.x - c.x; val odz = o.z - c.z
                    if (odx * odx + odz * odz < eco.socialRange * eco.socialRange) kin++
                }
                val humanInWater = waterAt(camPX, camPZ) != null
                val context = HumanPerceptionContext(
                    daylight = frameDaylight,
                    creatureFacingDot = facingDot(c, camPX, camPZ),
                    humanSpeed = playerSpeed,
                    closingSpeed = approach,
                    humanGaze = gaze,
                    homeHabitat = inHomeHabitat(c),
                    creatureInWater = waterAt(c.x, c.z) != null,
                    humanInWater = humanInWater,
                    creatureUnderground = c.under,
                    onStone = onStone(c),
                    fog = foggy,
                    rain = raining || snowing,
                    wind = (windKmh / 30f).coerceIn(0f, 1.5f),
                    visualOccluded = sightOccluded(c.x, c.z, camPX, camPZ),
                    nearbyConspecifics = kin
                )
                val learned = learningFor(c.species)
                val limits = EthoModel.thresholds(c.species, learned, context)
                val response = EthoModel.response(c.species, distP, learned, context)
                c.playerDistance = distP
                c.detectionDistance = limits.detectionDistance
                c.flightDistance = limits.flightDistance
                c.approachDistance = limits.approachDistance
                c.aware = response != PlayerResponse.UNAWARE
                if (c.aware && !c.encounterLogged) {
                    c.encounterLogged = true
                    if (sessionEncounteredSpecies.add(c.species))
                        behaviorEvents.add(intArrayOf(BEV_NOTICE, c.species,
                            Habitats.BIOMES.indexOf(biome).coerceAtLeast(0)))
                }

                // Ongoing flight has hysteresis: don't oscillate at one boundary.
                if (c.fleeing) {
                    val recovery = maxOf(limits.alertDistance * 1.28f, limits.detectionDistance * 1.06f)
                    if (distP > recovery) {
                        c.fleeing = false; c.targetX = Float.NaN
                        if (c.homing) { c.targetX = c.nestX; c.targetZ = c.nestZ; c.targetT = 14f }
                    } else if (c.targetX.isNaN()) {
                        c.targetX = (c.x + toX * 3.5f).coerceIn(.8f, w - .8f)
                        c.targetZ = (c.z + toZ * 2f).coerceIn(-8f, 8f); c.targetT = 6f
                    }
                } else if (response == PlayerResponse.FLEE && c.startleCd <= 0f) {
                    c.fleeing = true; c.startleCd = 5f + Random.nextFloat() * 3f
                    c.chaseT = 0f; c.chaseIdx = -1; c.soliciting = false
                    c.foraging = false; c.forageFood = -1; c.forageWaypoint = false
                    c.migrating = false; c.migrationCheckT = .6f
                    val refuge = Habitats.ZONES.filter { it.kind == sp.zone }
                        .minByOrNull { (it.x - c.x) * (it.x - c.x) + (it.z - c.z) * (it.z - c.z) }
                    val safe = refuge != null &&
                        (refuge.x - c.x) * toX + (refuge.z - c.z) * toZ > 0f
                    if (safe) { c.targetX = refuge!!.x; c.targetZ = refuge.z }
                    else {
                        c.targetX = (c.x + toX * 4f).coerceIn(.8f, w - .8f)
                        c.targetZ = (c.z + toZ * 2.5f).coerceIn(-8f, 8f)
                    }
                    c.targetT = 6f
                    behaviorEvents.add(intArrayOf(BEV_FLEE, c.species, 0))
                    audioEvents.add(intArrayOf(EV_ALARM, c.species))
                } else {
                    val mediumAllowsApproach = eco.medium != PreferredMedium.WATER ||
                        (waterAt(c.x, c.z) != null && humanInWater)
                    when (response) {
                        PlayerResponse.APPROACH -> if (mediumAllowsApproach && c.solicitCd <= 0f) {
                            if (c.under) { c.under = false; c.underT = 6f }
                            c.soliciting = true; c.alert = false; c.pauseT = 0f
                        }
                        PlayerResponse.INVESTIGATE -> if (mediumAllowsApproach && c.targetX.isNaN()) {
                            c.investigating = true
                            c.targetX = (camPX + toX * 1.65f).coerceIn(.8f, w - .8f)
                            c.targetZ = (camPZ + toZ * 1.65f).coerceIn(-8f, 8f); c.targetT = 4f
                        }
                        PlayerResponse.ALERT -> {
                            c.alert = true; c.pauseT = maxOf(c.pauseT, .25f)
                            val desired = Math.toDegrees(
                                kotlin.math.atan2((-dxp).toDouble(), (-dzp).toDouble())).toFloat()
                            val d = ((desired - c.headingDeg + 540f) % 360f) - 180f
                            c.headingDeg = (c.headingDeg + d.coerceIn(-160f * dt, 160f * dt) + 360f) % 360f
                        }
                        else -> Unit
                    }
                }

                if (c.soliciting) {
                    if (response != PlayerResponse.APPROACH) {
                        c.soliciting = false; c.targetX = Float.NaN; c.solicitCd = 3f
                    } else if (distP < limits.petReach + .18f) {
                        c.soliciting = false; c.targetX = Float.NaN; c.solicitCd = 8f
                        c.happyT = maxOf(c.happyT, 1.4f)
                        behaviorEvents.add(intArrayOf(BEV_SOLICIT, c.species, 0))
                    } else {
                        c.targetX = camPX.coerceIn(.8f, w - .8f)
                        c.targetZ = camPZ.coerceIn(-8f, 8f); c.targetT = 5f
                    }
                }
                if (!c.fleeing && distP < 2f && c.closeCd <= 0f) {
                    behaviorEvents.add(intArrayOf(BEV_CLOSE, c.species, (distP * 100f).toInt()))
                    c.closeCd = .55f
                }

                // Hunger is a state, not a random sightseeing trip. Search only
                // for foods the morphology can use, in the current seasonal range.
                if (c.foraging && (c.fleeing || c.soliciting || c.migrating || c.homing)) {
                    c.foraging = false; c.forageFood = -1; c.forageWaypoint = false
                }
                if (!c.foraging && c.forageCheckT <= 0f && c.hunger >= .58f &&
                    !c.fleeing && !c.soliciting && !c.migrating && !c.homing && !c.alert) {
                    c.forageCheckT = 2f + Random.nextFloat() * 2f
                    val food = bestForage(c, season)
                    if (food >= 0) {
                        c.foraging = true; c.forageFood = food
                        setForageLeg(c, food)
                        c.pauseT = 0f; c.lingerT = 0f
                    }
                }

                // Playful chase: the zoomiest invite a friend to a race.
                if ((c.foraging || c.migrating || c.feedingT > 0f) && c.chaseT > 0f) {
                    c.chaseT = 0f; c.chaseIdx = -1
                }
                if (c.chaseT > 0f) {
                    c.chaseT -= dt
                    val o = creatures.getOrNull(c.chaseIdx)
                    if (o == null || o.releasing || o.sleeping || c.chaseT <= 0f) {
                        c.chaseIdx = -1; c.chaseT = 0f
                    } else {
                        val dx = o.x - c.x; val dz = o.z - c.z
                        val d = kotlin.math.sqrt(dx * dx + dz * dz).coerceAtLeast(0.001f)
                        val targetSpeed = kotlin.math.sqrt(o.vx * o.vx + o.vz * o.vz)
                        val relation = EcologyModel.socialResponse(c.species, o.species, d,
                            sensingContext(c, o.x, o.z, targetSpeed,
                                waterAt(o.x, o.z) != null, o.under), targetAlarmed = o.fleeing)
                        if (relation.action !in setOf(SocialAction.APPROACH, SocialAction.CONTACT) ||
                            d > eco.socialRange || o.fleeing) {
                            c.chaseIdx = -1; c.chaseT = 0f
                        } else {
                            c.vx = dx / d * (.9f + sp.energy); c.vz = dz / d * (.9f + sp.energy)
                        }
                        if (c.chaseT > 0f && d < relation.preferredDistance * 1.20f) {
                            c.chaseIdx = -1; c.chaseT = 0f
                            c.happyT = 2f; o.happyT = 2f
                            heart(c.x, 1f, c.z, true); heart(o.x, 1f, o.z, false)
                            audioEvents.add(intArrayOf(EV_CHASE_END, c.species))
                        }
                    }
                } else if (sp.energy > .65f && eco.playfulness > .55f && !c.homing &&
                    !c.fleeing && !c.soliciting && !c.alert && !c.foraging && !c.migrating &&
                    Random.nextFloat() < dt * 0.02f && creatures.size > 1) {
                    val candidates = creatures.withIndex().filter { (_, o) ->
                        if (o === c || o.sleeping || o.releasing || o.fleeing ||
                            EcologyModel.of(o.species).playfulness <= .45f) false
                        else {
                            val dx = o.x - c.x; val dz = o.z - c.z
                            val d = kotlin.math.sqrt(dx * dx + dz * dz)
                            if (d > eco.socialRange) false else {
                                val os = kotlin.math.sqrt(o.vx * o.vx + o.vz * o.vz)
                                EcologyModel.socialResponse(c.species, o.species, d,
                                    sensingContext(c, o.x, o.z, os,
                                        waterAt(o.x, o.z) != null, o.under)).action in
                                    setOf(SocialAction.APPROACH, SocialAction.CONTACT)
                            }
                        }
                    }
                    if (candidates.isNotEmpty()) {
                        c.chaseIdx = candidates[Random.nextInt(candidates.size)].index
                        c.chaseT = 5f
                    }
                }

                val urgentTarget = c.fleeing || c.soliciting || c.homing || c.investigating ||
                    c.foraging || c.migrating
                if (c.feedingT > 0f && !c.fleeing) {
                    c.vx *= .68f; c.vz *= .68f
                    if (Random.nextFloat() < dt * 2.4f) {
                        val green = if (c.species == Species.MISTCROWN) .95f else .72f
                        particles += Particle(c.x + Random.nextFloat() * .35f - .17f,
                            .45f, c.z, .35f, 0f, .8f, .42f, green, .42f, 16f)
                    }
                } else if (c.lingerT > 0f && !urgentTarget) {
                    c.lingerT -= dt
                    c.vx *= 0.8f; c.vz *= 0.8f
                    if (Random.nextFloat() < dt * 1.2f) heart(c.x, 0.85f, c.z, false)
                } else if (c.pauseT > 0f && c.chaseT <= 0f && !urgentTarget) {
                    c.pauseT -= dt
                    c.vx *= 0.85f; c.vz *= 0.85f
                } else if (!c.targetX.isNaN()) {
                    // Patience runs out at blocked doorways: shrug, settle here.
                    c.targetT -= dt
                    if (c.targetT <= 0f) {
                        c.targetX = Float.NaN
                        if (c.foraging) {
                            c.foraging = false; c.forageFood = -1; c.forageWaypoint = false
                            c.forageCheckT = 1f
                        }
                        if (c.migrating) c.migrationCheckT = .2f
                        if (c.homing) { c.homing = false; c.sleeping = true
                            c.awakeT = (10f + Random.nextFloat() * 10f) * (1.6f - sp.energy) }
                        else if (!urgentTarget) c.lingerT = 2f
                    }
                    val dx = c.targetX - c.x; val dz = c.targetZ - c.z
                    val d = kotlin.math.sqrt(dx * dx + dz * dz)
                    if (c.targetX.isNaN()) { /* patience spent this frame */ }
                    else if (d < 0.45f && !c.homing) {
                        if (c.foraging && c.forageWaypoint && c.forageFood in Habitats.FOODS.indices) {
                            val food = c.forageFood
                            c.targetX = Float.NaN; c.forageWaypoint = false
                            setForageLeg(c, food)
                        } else if (c.foraging && c.forageFood in Habitats.FOODS.indices) {
                            val food = c.forageFood
                            foodCooldown[food] = 22f + (food % 5) * 4f
                            c.foraging = false; c.forageFood = -1; c.forageWaypoint = false
                            c.hunger = (c.hunger - .70f).coerceAtLeast(.05f)
                            c.feedingT = 2.2f; c.happyT = maxOf(c.happyT, 1.1f)
                            if (c.under) { c.under = false; c.underT = 5f }
                            c.vx = 0f; c.vz = 0f
                            audioEvents.add(intArrayOf(EV_FORAGE, c.species))
                            c.targetX = Float.NaN
                        } else {
                            // Arrived beside a roost or summit? Fliers alight,
                            // climbers scale it claw over claw.
                            val flier = sp.motion == Species.FLOAT || sp.motion == Species.DRIFT
                            val roost = if (flier || c.species in climberSpecies)
                                perchPts.firstOrNull {
                                    abs(it[0] - c.x) < 0.7f && abs(it[1] - c.z) < 0.7f
                                } else null
                            if (roost != null && !c.fleeing && !c.migrating) {
                                c.perchT = 6f + Random.nextFloat() * 8f
                                c.perchTop = roost[2]
                                c.climbing = !flier
                                c.x = roost[0]; c.z = roost[1]
                                c.vx = 0f; c.vz = 0f
                                c.targetX = Float.NaN
                            } else {
                                if (c.migrating) c.migrationCheckT = .15f
                                c.targetX = Float.NaN
                                if (!urgentTarget) c.lingerT = 3f + Random.nextFloat() * 3f
                            }
                        }
                    } else if (d >= 0.4f) {
                        val v = (0.5f + sp.energy * 0.7f) * (if (c.under) 2f else 1f) *
                            (if (sp.motion == Species.SKIM) 1.8f else 1f) *
                            (if (c.homing) 1.4f else 1f) * (if (c.fleeing) 2.4f else 1f) *
                            (if (c.soliciting || c.investigating) 1.12f else 1f) *
                            (if (c.migrating) 1f + seasonal.migrationDrive * .75f else 1f)
                        c.vx = dx / d * v; c.vz = dz / d * v
                    }
                } else if (c.chaseT <= 0f) {
                    val speed = (0.25f + sp.energy * 0.85f) * perky
                    when (sp.motion) {
                        Species.DART -> if (Random.nextFloat() < dt * (0.5f + sp.energy)) {
                            val a = Random.nextFloat() * 6.283f
                            c.vx = cos(a) * speed * 2.2f; c.vz = sin(a) * speed * 1.2f
                            c.pauseT = 0.5f + Random.nextFloat() * 0.8f
                        }
                        Species.HOP -> if (Random.nextFloat() < dt * 1.6f) {
                            val a = Random.nextFloat() * 6.283f
                            c.vx = cos(a) * speed * 1.6f; c.vz = sin(a) * speed * 0.9f
                            c.pauseT = 0.4f
                        }
                        Species.SKIM -> {
                            c.vx += (cos(c.phase * 0.9f) * speed * 2.4f - c.vx) * dt * 2f
                            c.vz += (sin(c.phase * 1.3f) * speed * 1.1f - c.vz) * dt * 2f
                        }
                        Species.SWIM -> {
                            c.vx += (cos(c.phase * 0.7f) * speed * 1.2f - c.vx) * dt * 2f
                            c.vz += (sin(c.phase * 1.4f) * speed * 0.8f - c.vz) * dt * 2f
                        }
                        else -> {
                            c.vx += (cos(c.phase * 0.5f) * speed - c.vx) * dt * 1.5f
                            c.vz += (sin(c.phase * 0.33f) * speed * 0.5f - c.vz) * dt * 1.5f
                            if (Random.nextFloat() < dt * (0.8f - sp.energy * 0.5f))
                                c.pauseT = 0.8f + Random.nextFloat() * 1.6f
                        }
                    }
                    // Wings and claws seek the high places: a flier picks a
                    // roost, a climber something worth scaling.
                    val highSeeker = sp.motion == Species.FLOAT ||
                        sp.motion == Species.DRIFT || c.species in climberSpecies
                    if (highSeeker && c.perchT <= 0f && Random.nextFloat() < dt * 0.05f) {
                        val p = perchPts.minByOrNull {
                            val ddx = it[0] - c.x; val ddz = it[1] - c.z; ddx * ddx + ddz * ddz
                        }
                        if (p != null) {
                            val ddx = p[0] - c.x; val ddz = p[1] - c.z
                            if (ddx * ddx + ddz * ddz < 49f) {
                                c.targetX = p[0]; c.targetZ = p[1]; c.targetT = 12f
                            }
                        }
                    }
                    if (Random.nextFloat() < dt * 0.12f) {
                        var found = false
                        for ((i, id) in placedIds.withIndex()) {
                            val item = Habitats.item(id) ?: continue
                            if (sp.temperament in item.loved) {
                                c.targetX = Habitats.slotX(i); c.targetZ = Habitats.SLOT_Z + 0.5f
                                c.targetT = 10f
                                found = true; break
                            }
                        }
                        if (!found) {
                            val homes = Habitats.ZONES.filter { it.kind == sp.zone }
                            if (homes.isNotEmpty()) {
                                // Prefer nearby matching zones; the world is wide.
                                val zn = homes.minByOrNull { abs(it.x - c.x) + Random.nextFloat() * 8f }!!
                                val a = Random.nextFloat() * 6.283f
                                c.targetX = zn.x + cos(a) * zn.r * 0.5f
                                c.targetZ = (zn.z + sin(a) * zn.r * 0.4f).coerceIn(-8f, 8f)
                                c.targetT = 10f
                            }
                        }
                    }
                }

                // Society: evaluate every perceived neighbor, then act on the
                // strongest alarm, threat, compatible partner, or space conflict.
                if (!c.sleeping && !c.fleeing && !c.soliciting && c.perchT <= 0f &&
                    !c.foraging && !c.migrating && c.feedingT <= 0f) {
                    var chosen: DenC? = null
                    var chosenResponse: com.specsafari.shared.SocialResponse? = null
                    var chosenDistance = Float.MAX_VALUE
                    var chosenScore = -1f
                    for (o in creatures) {
                        if (o === c || o.sleeping || o.releasing) continue
                        val dx = o.x - c.x; val dz = o.z - c.z
                        val d = kotlin.math.sqrt(dx * dx + dz * dz).coerceAtLeast(.001f)
                        val os = kotlin.math.sqrt(o.vx * o.vx + o.vz * o.vz)
                        val relation = EcologyModel.socialResponse(c.species, o.species, d,
                            sensingContext(c, o.x, o.z, os,
                                waterAt(o.x, o.z) != null, o.under), targetAlarmed = o.fleeing)
                        val priority = when (relation.action) {
                            SocialAction.ALARM -> 4f
                            SocialAction.AVOID -> 3f
                            SocialAction.CONTACT -> 2f
                            SocialAction.APPROACH -> 1f
                            SocialAction.OBSERVE -> .25f
                            else -> 0f
                        }
                        val score = priority + relation.strength - d * .008f
                        if (score > chosenScore) {
                            chosen = o; chosenResponse = relation; chosenDistance = d; chosenScore = score
                        }
                    }
                    val o = chosen
                    val relation = chosenResponse
                    if (o != null && relation != null) {
                        val dx = o.x - c.x; val dz = o.z - c.z
                        val d = chosenDistance.coerceAtLeast(.001f)
                        when (relation.action) {
                            SocialAction.ALARM -> if (c.startleCd <= 0f) {
                                c.fleeing = true; c.startleCd = 5f; c.chaseT = 0f; c.chaseIdx = -1
                                c.foraging = false; c.forageFood = -1; c.forageWaypoint = false
                                c.migrating = false; c.migrationCheckT = .6f
                                c.targetX = (c.x - dx / d * 3.5f).coerceIn(.8f, w - .8f)
                                c.targetZ = (c.z - dz / d * 2.2f).coerceIn(-8f, 8f); c.targetT = 6f
                                c.vx = -dx / d * (1f + sp.energy); c.vz = -dz / d * (1f + sp.energy)
                                behaviorEvents.add(intArrayOf(BEV_FLEE, c.species, 1))
                            }
                            SocialAction.AVOID -> {
                                val push = (.25f + relation.strength * .45f) * dt * 3f
                                c.vx -= dx / d * push; c.vz -= dz / d * push
                            }
                            SocialAction.APPROACH -> if (d > relation.preferredDistance &&
                                c.targetX.isNaN() && c.lingerT <= 0f && c.chaseT <= 0f) {
                                val pull = relation.strength * dt * 2.2f
                                c.vx += dx / d * pull; c.vz += dz / d * pull
                            }
                            SocialAction.CONTACT -> if (c.meetCd <= 0f && o.meetCd <= 0f && !c.under && !o.under) {
                                c.meetCd = 7f; o.meetCd = 7f
                                c.happyT = maxOf(c.happyT, 1.2f); o.happyT = maxOf(o.happyT, 1.2f)
                                heart(c.x, 1f, c.z, big = o.species == c.species)
                                if (o.species == c.species || relation.strength > .72f)
                                    heart(o.x, 1f, o.z, false)
                                audioEvents.add(intArrayOf(EV_MEET, c.species))
                                c.vx -= dx / d * .18f; c.vz -= dz / d * .18f
                            }
                            else -> Unit
                        }
                    }
                }

                // The third dimension: fliers lift off when traveling (and to
                // escape), roosters sit their perch, climbers inch up and down.
                run {
                    val flier = sp.motion == Species.FLOAT || sp.motion == Species.DRIFT
                    if (c.perchT > 0f) {
                        c.perchT -= dt
                        c.vx = 0f; c.vz = 0f
                        c.pauseT = maxOf(c.pauseT, 0.3f)      // no wandering off a roost
                        if (c.happyT > 0.9f) c.perchT = 0f    // petted: come on down
                    }
                    val wantAlt = when {
                        c.under || c.sleeping || c.feedingT > 0f || c.soliciting -> 0f
                        c.perchT > 0f -> c.perchTop
                        flier && c.fleeing -> 1.5f            // escape on the wing
                        flier && (c.vx * c.vx + c.vz * c.vz) > 0.12f ->
                            0.85f + sin(c.phase * 1.1f) * 0.25f
                        else -> 0f
                    }
                    // Claws move slower than wings (both up and back down).
                    val rate = if (c.climbing) 0.7f else 2.2f
                    c.alt += (wantAlt - c.alt).coerceIn(-rate * dt, rate * dt)
                    if (c.alt < 0.02f && c.perchT <= 0f) {
                        c.alt = 0f; c.perchTop = 0f; c.climbing = false
                    }
                }
                c.x = (c.x + c.vx * dt).coerceIn(0.8f, w - 0.8f)
                c.z = (c.z + c.vz * dt).coerceIn(-8f, 8f)
                // Solids are solid — unless you're a mole in the underworld,
                // on the wing above them, or sitting on top of one.
                if (!c.under && c.alt < 0.3f && c.perchT <= 0f) {
                    val bodyR = eco.bodyRadius * c.scale
                    val fixed = resolve(c.x, c.z, bodyR)
                    if (fixed[0] != c.x || fixed[1] != c.z) {
                        c.x = fixed[0]; c.z = fixed[1]
                        c.vx *= 0.4f; c.vz *= 0.4f   // bumping into things is humbling
                    }
                    // Basic logic: a creature may hide in foliage, never inside
                    // a physically solid object. If the pushout couldn't escape
                    // (overlapping trunks, a world-edge pin), step to the
                    // nearest genuinely clear ground.
                    if (overlaps(c.x, c.z, bodyR * 0.7f)) {
                        val fc = findClear(c.x, c.z, bodyR)
                        c.x = fc[0]; c.z = fc[1]
                        c.vx = 0f; c.vz = 0f
                    }
                }
                // Water physics: a splash going in, bow ripples while moving.
                if (!c.under) {
                    val wading = waterAt(c.x, c.z) != null
                    if (wading && !c.inWater) {
                        ripples += Ripple(c.x, c.z)
                        for (s in 0..5) particles += Particle(
                            c.x + Random.nextFloat() * 0.3f - 0.15f, 0.15f, c.z + 0.1f,
                            0.8f + Random.nextFloat() * 0.7f, 0f, 0.5f,
                            0.55f, 0.85f, 0.95f, 16f)
                    }
                    c.inWater = wading
                    if (wading && (c.vx * c.vx + c.vz * c.vz) > 0.02f &&
                        Random.nextFloat() < dt * 1.6f) ripples += Ripple(c.x, c.z)
                }
                if (sp.motion == Species.SWIM) {
                    val pools = Habitats.ZONES.filter { it.kind == "WATER" }
                    val zn = pools.minByOrNull {
                        (it.x - c.x) * (it.x - c.x) + (it.z - c.z) * (it.z - c.z)
                    }
                    if (zn != null) {
                        val dx = c.x - zn.x; val dz = c.z - zn.z
                        val d = kotlin.math.sqrt(dx * dx + dz * dz)
                        val rim = zn.r * 0.7f
                        if (d > rim && c.targetX.isNaN() && !c.homing) {
                            c.x = zn.x + dx / d * rim; c.z = zn.z + dz / d * rim
                            c.vx -= dx / d * 0.6f; c.vz -= dz / d * 0.6f
                        }
                    }
                }
                turnToward(c, sp, dt)
            }
            if (removed) {
                val gone = creatures.filter { it.releasing && abs(it.targetX - it.x) < 1f }
                for (g in gone) audioEvents.add(intArrayOf(EV_RELEASED, g.species))
                creatures.removeAll(gone.toSet())
                if (selected >= creatures.size) selected = -1
            }
        }
        // Fireflies belong to clear nights; rain and daylight send them home.
        val fireflyRate = (1f - frameDaylight) * (if (raining || snowing) 0f else 1f)
        if (particles.count { it.vy in 0f..0.05f } < 40 && Random.nextFloat() < dt * 10f * fireflyRate) {
            val px = camPX - 6f + Random.nextFloat() * 12f
            val col = Habitats.blendColor(px) { it.fireflyColor }
            particles += Particle(
                px, 0.3f + Random.nextFloat() * 1.6f, -2.5f + Random.nextFloat() * 3f,
                0.02f, 0f, 6f + Random.nextFloat() * 6f,
                red(col), green(col), blue(col), 14f + Random.nextFloat() * 16f)
        }
        // The weather itself: rain streaks or drifting snow around the walker.
        if (raining) repeat(((dt * 420f).toInt() + if (Random.nextFloat() < 0.5f) 1 else 0)) {
            particles += Particle(
                camPX - 5f + Random.nextFloat() * 10f, 3.2f, camPZ - 4f + Random.nextFloat() * 8f,
                -6.5f - Random.nextFloat() * 2f, 0f, 0.55f,
                0.55f, 0.75f, 0.95f, 58f)
        }
        if (snowing) repeat(((dt * 160f).toInt() + if (Random.nextFloat() < 0.5f) 1 else 0)) {
            particles += Particle(
                camPX - 5f + Random.nextFloat() * 10f, 3f, camPZ - 4f + Random.nextFloat() * 8f,
                -0.55f, 0f, 5f,
                0.95f, 0.97f, 1f, 80f)
        }
        // Localized weather cells: a blizzard, a flurry, a downpour over a spot.
        var nearSnow = 0f; var nearRain = 0f
        gust = 0f
        for (p in Habitats.WEATHER_PATCHES) {
            val dx = camPX - p.x; val dz = camPZ - p.z
            val d = kotlin.math.sqrt(dx * dx + dz * dz)
            val reach = p.r + 14f
            if (d > reach) continue
            val inten = ((reach - d) / reach).coerceIn(0f, 1f)
            when (p.kind) {
                Habitats.RAIN -> {
                    nearRain = maxOf(nearRain, inten)
                    repeat(((dt * 520f * inten).toInt() + if (Random.nextFloat() < inten) 1 else 0)) {
                        particles += Particle(camPX - 5f + Random.nextFloat() * 10f, 3.2f,
                            camPZ - 4f + Random.nextFloat() * 8f,
                            -7f - Random.nextFloat() * 2f, 0f, 0.5f, 0.55f, 0.72f, 0.98f, 60f)
                    }
                }
                Habitats.SNOW -> {
                    nearSnow = maxOf(nearSnow, inten * 0.75f)
                    repeat(((dt * 200f * inten).toInt() + if (Random.nextFloat() < inten) 1 else 0)) {
                        particles += Particle(camPX - 5f + Random.nextFloat() * 10f, 3.2f,
                            camPZ - 4f + Random.nextFloat() * 8f,
                            -0.5f, 0f, 5.5f, 0.97f, 0.99f, 1f, 85f)
                    }
                }
                else -> {   // BLIZZARD — dense, fast, wind-driven whiteout
                    nearSnow = maxOf(nearSnow, inten)
                    gust = maxOf(gust, inten)
                    repeat(((dt * 620f * inten).toInt() + if (Random.nextFloat() < inten) 1 else 0)) {
                        particles += Particle(camPX - 6f + Random.nextFloat() * 12f, 3.2f,
                            camPZ - 5f + Random.nextFloat() * 10f,
                            -1.8f - Random.nextFloat(), 0f, 2.6f, 0.98f, 1f, 1f, 75f)
                    }
                }
            }
        }
        precipRain = nearRain; precipSnow = nearSnow
        // The fires breathe out sparks; springs bubble; crystals glint.
        for (f in flamePts) if (Random.nextFloat() < dt * 2.2f) {
            particles += Particle(
                f[0] + Random.nextFloat() * 0.14f - 0.07f, f[1] + 0.2f, f[2],
                0.45f + Random.nextFloat() * 0.35f, 0f, 1f + Random.nextFloat() * 0.5f,
                1f, 0.55f + Random.nextFloat() * 0.3f, 0.2f, 10f + Random.nextFloat() * 8f)
        }
        for (l in lanternPts) if (Random.nextFloat() < dt * 0.7f) {
            particles += Particle(l[0], l[1] + 0.12f, l[2],
                0.25f, 0f, 1.2f, 1f, 0.8f, 0.45f, 8f)
        }
        // Frost glints drift off the frozen tundra ponds.
        for (w2 in waterBodies) if (w2.frozen && Random.nextFloat() < dt * 2f) {
            val a = Random.nextFloat() * 6.283f; val rr = Random.nextFloat() * w2.r * 0.6f
            particles += Particle(w2.x + cos(a) * rr, 0.12f, w2.z + sin(a) * rr * 0.75f,
                0.12f + Random.nextFloat() * 0.12f, 0f, 1.1f,
                0.86f, 0.94f, 1f, 8f)
        }
        for (s in sparklePts) if (Random.nextFloat() < dt * 1.1f) {
            particles += Particle(
                s[0] + Random.nextFloat() * 0.3f - 0.15f, s[1] + Random.nextFloat() * 0.4f,
                s[2] + Random.nextFloat() * 0.2f - 0.1f,
                0.06f, 0f, 0.8f, 0.85f, 0.75f, 1f, 11f)
        }
        // Treasure struck in the field: a shower of gold motes rises around you.
        if (cheerT > 0f) {
            cheerT -= dt
            repeat(4) {
                val a = Random.nextFloat() * 6.283f
                val rr = 0.6f + Random.nextFloat() * 3.6f
                particles += Particle(
                    camPX + cos(a) * rr, 0.25f + Random.nextFloat() * 0.6f, camPZ + sin(a) * rr,
                    1.3f + Random.nextFloat() * 0.9f, 0f, 1.0f + Random.nextFloat() * 0.6f,
                    1f, 0.82f + Random.nextFloat() * 0.16f, 0.30f, 13f + Random.nextFloat() * 9f)
            }
        }
        // The walker makes waves too.
        if (waterAt(camPX, camPZ) != null && (abs(moveX) > 0.1f || abs(moveY) > 0.1f) &&
            Random.nextFloat() < dt * 2.5f) ripples += Ripple(camPX, camPZ)

        for (rp in ripples) rp.age += dt
        ripples.removeAll { it.age > 0.9f }
        if (ripples.size > 24) ripples.subList(0, ripples.size - 24).clear()

        val wind = (windKmh / 30f).coerceIn(0f, 1.5f) + gust * 2.4f   // blizzards drive hard
        val splashes = ArrayList<Particle>(4)
        val it = particles.iterator()
        while (it.hasNext()) {
            val p = it.next()
            p.life += dt
            p.y += p.vy * dt * 30f * dt + p.vy * dt
            p.x += sin(p.life * 2f + p.z) * dt * 0.15f +
                (if (p.vy < -0.1f) wind * dt * 1.2f else 0f)   // weather rides the wind
            if (p.life >= p.maxLife || p.y < 0.02f && p.vy < -1f) {
                // Raindrops land somewhere real: a splash, or a ring on water.
                if (p.vy < -1f && p.y < 0.02f) {
                    splashes += Particle(p.x, 0.06f, p.z, 0.5f, 0f, 0.22f,
                        0.6f, 0.8f, 0.95f, 7f)
                    if (waterAt(p.x, p.z) != null && Random.nextFloat() < 0.2f)
                        ripples += Ripple(p.x, p.z)
                }
                it.remove()
            }
        }
        particles.addAll(splashes)
    }

    /**
     * Turn to face where you're going, at a rate set by your biology:
     * darters and skimmers whip around and lean into it, floaters pivot
     * dreamily, walkers turn like the creatures they are — a Thornpup
     * spins on a leaf-tip, a Clayward comes about like a barge.
     */
    private fun turnToward(c: DenC, sp: Species, dt: Float) {
        val speed2 = c.vx * c.vx + c.vz * c.vz
        var delta = 0f
        if (speed2 > 0.006f) {
            val desired = Math.toDegrees(kotlin.math.atan2(c.vx.toDouble(), c.vz.toDouble())).toFloat()
            delta = ((desired - c.headingDeg + 540f) % 360f) - 180f
            val turnRate = when (sp.motion) {
                Species.DART, Species.SKIM -> 220f + sp.energy * 320f
                Species.FLOAT, Species.DRIFT -> 55f + sp.energy * 90f
                Species.SWIM -> 100f + sp.energy * 130f
                else -> 70f + sp.energy * 240f
            }
            c.headingDeg += delta.coerceIn(-turnRate * dt, turnRate * dt)
            c.headingDeg = (c.headingDeg + 360f) % 360f
        }
        // Fliers bank into their turns; everyone else stays level.
        val wantBank = if (sp.motion == Species.SKIM || sp.motion == Species.DART)
            (-delta).coerceIn(-38f, 38f) * 0.55f else 0f
        c.bank += (wantBank - c.bank) * (dt * 5f).coerceAtMost(1f)
    }

    private fun heart(x: Float, y: Float, z: Float, big: Boolean) {
        particles += Particle(
            x + Random.nextFloat() * 0.4f - 0.2f, y, z + 0.2f,
            0.35f + Random.nextFloat() * 0.2f, 0f, if (big) 1.6f else 1.1f,
            1f, 0.45f + Random.nextFloat() * 0.2f, 0.75f,
            if (big) 46f else 30f)
    }

    private fun zzz(x: Float, y: Float, z: Float) {
        particles += Particle(
            x + 0.15f, y, z + 0.1f, 0.22f, 0f, 1.8f,
            0.75f, 0.85f, 1f, 20f)
    }

    private fun drawParticles() {
        if (particles.isEmpty()) return
        val n = particles.size
        val fb = ByteBuffer.allocateDirect(n * 8 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        for (p in particles) {
            val fade = (1f - p.life / p.maxLife).coerceIn(0f, 1f)
            val tw = if (p.vy < 0.05f) (0.4f + 0.6f * abs(sin(p.life * 3f + p.x))) else 1f
            fb.put(p.x); fb.put(p.y); fb.put(p.z)
            fb.put(p.r); fb.put(p.g); fb.put(p.b); fb.put(fade * tw)
            fb.put(p.size)
        }
        fb.position(0)
        glUseProgram(ptProg)
        glUniformMatrix4fv(glGetUniformLocation(ptProg, "uVP"), 1, false, vp, 0)
        glEnable(GL_BLEND); glBlendFunc(GL_SRC_ALPHA, GL_ONE)
        glDepthMask(false)
        val aPos = glGetAttribLocation(ptProg, "aPos")
        val aCol = glGetAttribLocation(ptProg, "aCol")
        val aSize = glGetAttribLocation(ptProg, "aSize")
        glEnableVertexAttribArray(aPos); glEnableVertexAttribArray(aCol); glEnableVertexAttribArray(aSize)
        fb.position(0); glVertexAttribPointer(aPos, 3, GL_FLOAT, false, 32, fb)
        fb.position(3); glVertexAttribPointer(aCol, 4, GL_FLOAT, false, 32, fb)
        fb.position(7); glVertexAttribPointer(aSize, 1, GL_FLOAT, false, 32, fb)
        glDrawArrays(GL_POINTS, 0, n)
        glDisableVertexAttribArray(aPos); glDisableVertexAttribArray(aCol); glDisableVertexAttribArray(aSize)
        glDepthMask(true)
        glDisable(GL_BLEND)
    }

    private fun red(c: Int) = android.graphics.Color.red(c) / 255f
    private fun green(c: Int) = android.graphics.Color.green(c) / 255f
    private fun blue(c: Int) = android.graphics.Color.blue(c) / 255f
}
