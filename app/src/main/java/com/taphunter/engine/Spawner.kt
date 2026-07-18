package com.taphunter.engine

import com.taphunter.geo.GeoMath
import com.taphunter.geo.GeoPoint
import com.taphunter.geo.OsmPoi
import com.taphunter.geo.OsmRoad
import com.taphunter.geo.RpgNamer
import kotlin.math.abs
import kotlin.random.Random

/** A creature or treasure standing somewhere real. */
class Spawn(
    val p: GeoPoint,
    val isCreature: Boolean,
    val species: Int,          // creature only
    val level: Int,
    val placeName: String,     // RPGified anchor name
    val poiId: Long            // 0 when anchored to a road/raw point
)

/**
 * The session ladder. Level 1 always waits within 50 m of where the hunt
 * began; every next level doubles the distance from that origin and is
 * placed in the general direction the hunter is already walking, anchored
 * to a real place from the map wherever one exists.
 */
class Spawner(private val rng: Random) {

    var origin: GeoPoint? = null; private set
    var level = 1; private set
    var creature: Spawn? = null; private set
    val treasures = mutableListOf<Spawn>()
    private val usedPois = mutableSetOf<Long>()
    private var lastSpawnBearing = Float.NaN   // fan successive hunts apart

    /** Roads a hunter can actually walk (spec: "paved walkable directions"). */
    private val unwalkable = setOf("motorway", "motorway_link", "trunk", "trunk_link")

    fun beginSession(start: GeoPoint) {
        origin = start
        level = 1
        creature = null
        treasures.clear()
        usedPois.clear()
        lastSpawnBearing = Float.NaN
    }

    /** Ladder distance for a level: 50, 100, 200, 400 ... meters from origin. */
    fun ladderDistance(l: Int = level): Float = 50f * (1 shl (l - 1).coerceIn(0, 9))

    fun onCaught() {
        creature?.let { usedPois += it.poiId }
        creature = null
        level++
    }

    fun onFled() {
        // Same level, different lair — the hunt is never left empty-handed.
        creature?.let { usedPois += it.poiId }
        creature = null
    }

    fun openTreasure(t: Spawn) {
        treasures.remove(t)
        usedPois += t.poiId
    }

    /**
     * Keep the invariant: a creature for the current level always exists, and
     * something of interest is always inside the current ladder distance.
     */
    fun ensure(
        player: GeoPoint,
        travelBearing: Float?,
        roads: List<OsmRoad>,
        pois: List<OsmPoi>,
        charmTier: Int
    ): Boolean {
        val org = origin ?: return false
        var placed = false
        // A creature dropped blind (before map data arrived) re-anchors to a
        // real place as soon as the charts come in.
        creature?.let { c ->
            if (c.poiId == 0L && c.placeName == "THE OPEN WILDS" &&
                (pois.isNotEmpty() || roads.isNotEmpty())
            ) creature = null
        }
        if (creature == null) {
            creature = placeCreature(org, player, travelBearing, roads, pois, charmTier)
            placed = creature != null
        }
        // Interest guarantee: if the quarry is beyond the ladder ring and no
        // treasure is close, plant a chest at a nearby real place.
        val d = ladderDistance()
        val creatureNear = creature?.let { GeoMath.distanceM(player, it.p) <= d } ?: false
        val treasureNear = treasures.any { GeoMath.distanceM(player, it.p) <= d }
        if (!creatureNear && !treasureNear && treasures.size < 3) {
            placeTreasure(player, pois)?.let { treasures += it; placed = true }
        }
        return placed
    }

    private fun placeCreature(
        org: GeoPoint,
        player: GeoPoint,
        travelBearing: Float?,
        roads: List<OsmRoad>,
        pois: List<OsmPoi>,
        charmTier: Int
    ): Spawn? {
        val d = ladderDistance()
        val minD = if (level == 1) 12f else d * 0.70f
        val maxD = if (level == 1) 50f else d * 1.15f

        fun inCone(p: GeoPoint): Boolean {
            if (level == 1 || travelBearing == null) return true
            val brg = GeoMath.bearingDeg(player, p)
            return abs(GeoMath.angleDiff(brg, travelBearing)) <= 55f
        }
        fun inRing(p: GeoPoint): Boolean {
            val dist = GeoMath.distanceM(org, p)
            return dist in minD..maxD
        }
        // When the hunter is standing still, successive hunts FAN OUT: each
        // new lair prefers a direction well apart from the previous one
        // (e.g. north up the boulevard, then south down it).
        fun fanPenalty(p: GeoPoint): Float {
            if (travelBearing != null || lastSpawnBearing.isNaN() || level <= 1) return 0f
            val brg = GeoMath.bearingDeg(org, p)
            val apart = abs(GeoMath.angleDiff(brg, lastSpawnBearing))
            return if (apart < 70f) 120f else 0f
        }
        fun remember(p: GeoPoint) { lastSpawnBearing = GeoMath.bearingDeg(org, p) }

        // 1st choice: a real named place in the ring, ahead of the hunter.
        val poi = pois.asSequence()
            .filter { it.id !in usedPois && inRing(it.p) && inCone(it.p) }
            .minByOrNull {
                abs(GeoMath.distanceM(org, it.p) - d) + fanPenalty(it.p) + rng.nextFloat() * 30f
            }
        if (poi != null) {
            remember(poi.p)
            val seed = ((poi.id xor poi.id.ushr(21)).toInt() and 0x7FFFFFFF)
            return Spawn(
                poi.p, true,
                Species.forCategory(poi.category, seed, charmTier),
                level, RpgNamer.poi(poi.name, poi.category, poi.id), poi.id
            )
        }

        // 2nd: a point along a real WALKABLE road or path in the ring; the
        // lair borrows the street's RPG name so the banner reads like a map.
        var best: GeoPoint? = null; var bestScore = Float.MAX_VALUE; var bestRoad: OsmRoad? = null
        for (r in roads) {
            if (r.kind in unwalkable) continue
            for (p in r.pts) {
                if (!inRing(p) || !inCone(p)) continue
                val walkBonus = if (r.isPath) -18f else 0f
                val score = abs(GeoMath.distanceM(org, p) - d) + fanPenalty(p) + walkBonus
                if (score < bestScore) { bestScore = score; best = p; bestRoad = r }
            }
        }
        if (best != null) {
            remember(best)
            val where = bestRoad?.let { RpgNamer.road(it.name, it.kind, it.id) }
                ?: "AN UNMARKED CROSSING"
            return Spawn(best, true, Species.forCategory("", rng.nextInt(1 shl 30), charmTier),
                level, where, 0L)
        }

        // Last resort (no map data yet): drop it dead ahead at the ladder ring.
        val brg = travelBearing ?: rng.nextFloat() * 360f
        val raw = GeoMath.destination(if (level == 1) org else player, brg,
            if (level == 1) 35f else d * 0.9f)
        remember(raw)
        return Spawn(raw, true, Species.forCategory("", rng.nextInt(1 shl 30), charmTier),
            level, "THE OPEN WILDS", 0L)
    }

    private fun placeTreasure(player: GeoPoint, pois: List<OsmPoi>): Spawn? {
        val poi = pois.asSequence()
            .filter { it.id !in usedPois }
            .filter { GeoMath.distanceM(player, it.p) in 40f..ladderDistance().coerceAtMost(400f) }
            .minByOrNull { GeoMath.distanceM(player, it.p) + rng.nextFloat() * 60f }
            ?: return null
        return Spawn(poi.p, false, 0, level,
            RpgNamer.poi(poi.name, poi.category, poi.id), poi.id)
    }
}
