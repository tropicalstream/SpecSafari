package com.specsafari.engine

import com.specsafari.geo.GeoMath
import com.specsafari.shared.Species
import com.specsafari.geo.GeoPoint
import com.specsafari.geo.OsmPoi
import com.specsafari.geo.OsmRoad
import com.specsafari.geo.RpgNamer
import kotlin.math.abs
import kotlin.random.Random

/** A creature or treasure standing somewhere real. */
class Spawn(
    val p: GeoPoint,
    val isCreature: Boolean,
    val species: Int,          // creature only
    val level: Int,
    val placeName: String,     // RPGified anchor name
    val poiId: Long,           // 0 when anchored to a road/raw point
    val lost: Boolean = false  // a friend that wandered off, waiting to be found
)

/**
 * The session ladder. Level 1 always waits within 50 m of where the hunt
 * began; every next level doubles the distance from that origin and is
 * placed in the general direction the hunter is already walking, anchored
 * to a real place from the map wherever one exists.
 */
class Spawner(private var rng: Random) {

    var origin: GeoPoint? = null; private set
    var level = 1; private set
    var creature: Spawn? = null; private set
    val treasures = mutableListOf<Spawn>()
    private val usedPois = mutableSetOf<Long>()
    private var lastSpawnBearing = Float.NaN   // fan successive hunts apart

    /** Roads a hunter can actually walk (spec: "paved walkable directions"). */
    private val unwalkable = setOf("motorway", "motorway_link", "trunk", "trunk_link")

    /**
     * Everything spawns ON the street: a POI's map point is the building
     * centroid — indoors, ungrabbable from the sidewalk (the Monaghan's
     * chest lesson). Snap to the closest point of a walkable way.
     */
    private fun snapToStreet(p: GeoPoint, roads: List<OsmRoad>, maxM: Float = 80f): GeoPoint? {
        var best: GeoPoint? = null
        var bestD = maxM
        for (r in roads) {
            if (r.kind in unwalkable) continue
            val pts = r.pts
            for (i in 0 until pts.size - 1) {
                val q = GeoMath.nearestOnSegment(p, pts[i], pts[i + 1])
                val d = GeoMath.distanceM(p, q)
                if (d < bestD) { bestD = d; best = q }
            }
        }
        return best
    }

    /** Street spawns are not all cats: SHADEPAW keeps the plurality, the rest roam. */
    private fun wildSpecies(charmTier: Int): Int {
        val s = Species.forCategory("", rng.nextInt(1 shl 30), charmTier)
        return if (s == 10 && rng.nextFloat() < 0.6f) rng.nextInt(10) else s
    }

    /** Umbrella rule: a candidate too aligned with existing quarry is dull. */
    private fun spreadPenalty(player: GeoPoint, cand: GeoPoint, ignore: Spawn? = null): Float {
        val brg = GeoMath.bearingDeg(player, cand)
        var penalty = 0f
        creature?.takeIf { it !== ignore }?.let {
            if (abs(GeoMath.angleDiff(brg, GeoMath.bearingDeg(player, it.p))) < 60f) penalty += 110f
        }
        for (t in treasures) {
            if (t === ignore) continue
            if (abs(GeoMath.angleDiff(brg, GeoMath.bearingDeg(player, t.p))) < 60f) penalty += 110f
        }
        for (w in wildlife) {
            if (w === ignore) continue
            if (abs(GeoMath.angleDiff(brg, GeoMath.bearingDeg(player, w.p))) < 50f) penalty += 90f
        }
        lostOne?.takeIf { it !== ignore }?.let {
            if (abs(GeoMath.angleDiff(brg, GeoMath.bearingDeg(player, it.p))) < 50f) penalty += 120f
        }
        return penalty
    }

    val wildlife = mutableListOf<Spawn>()   // flanking spokes of the umbrella
    private var level1Realigned = false

    /** A creature away on a failed fetch, out there to be found and welcomed
     *  home. At most one is on the map at a time (the oldest lost). */
    var lostOne: Spawn? = null; private set

    fun beginSession(start: GeoPoint, seed: Long, recentlyUsed: Set<Long> = emptySet()) {
        // Reseed deterministically: given the same origin+seed, this session
        // lays out the identical ladder, so restarting the app in the same spot
        // reproduces the same spawn rather than re-rolling for a nicer one.
        rng = Random(seed)
        origin = start
        level = 1
        creature = null
        treasures.clear()
        wildlife.clear()
        lostOne = null
        usedPois.clear()
        // Yesterday's lairs stay cold: the same place must not serve the same
        // hunt again while the memory lasts.
        usedPois.addAll(recentlyUsed)
        lastSpawnBearing = Float.NaN
        level1Realigned = false
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
        // Level 1 realign: the opening quarry is placed before the hunter has
        // a travel bearing. Once they've shown a direction, a still-distant
        // level-1 lair sitting BEHIND them relocates ahead — once.
        creature?.let { c ->
            if (!level1Realigned && level == 1 && travelBearing != null &&
                GeoMath.distanceM(player, c.p) > 30f &&
                abs(GeoMath.angleDiff(GeoMath.bearingDeg(player, c.p), travelBearing)) > 100f
            ) {
                level1Realigned = true
                usedPois += c.poiId
                creature = null
            }
        }
        if (creature == null) {
            creature = placeCreature(org, player, travelBearing, roads, pois, charmTier)
            placed = creature != null
        }
        // Interest guarantee: a chest is always waiting in SOME direction —
        // the umbrella's other rib when the creature pulls one way.
        val d = ladderDistance().coerceAtMost(400f)
        val treasureNear = treasures.any { GeoMath.distanceM(player, it.p) <= d }
        if (!treasureNear && treasures.size < 3) {
            placeTreasure(player, roads, pois)?.let { treasures += it; placed = true }
        }
        // The spoke wheel: the ladder quarry holds one rib, ONE wild flanker
        // holds another — two creatures total, on distinct walking directions.
        // A flanker the hunter has passed re-spokes once it falls 300 m
        // behind, so the road ahead is never empty and never crowded.
        wildlife.removeAll { GeoMath.distanceM(player, it.p) > 300f }
        if (wildlife.isEmpty()) {
            placeWildlife(player, roads, pois, charmTier)?.let { wildlife += it; placed = true }
        }
        return placed
    }

    /** A lesser creature on a flank spoke: apart from the quarry, the chests,
     *  and its fellow wildlife, 60-220 m out, on a walkable street. */
    private fun placeWildlife(
        player: GeoPoint, roads: List<OsmRoad>, pois: List<OsmPoi>, charmTier: Int
    ): Spawn? {
        val lv = (level - 1).coerceAtLeast(1)
        val poi = pois.asSequence()
            .filter { it.id !in usedPois }
            .filter { GeoMath.distanceM(player, it.p) in 60f..220f }
            .filter { cand -> wildlife.none {
                abs(GeoMath.angleDiff(GeoMath.bearingDeg(player, cand.p),
                    GeoMath.bearingDeg(player, it.p))) < 50f } }
            .minByOrNull { spreadPenalty(player, it.p) + rng.nextFloat() * 120f }
        if (poi != null) {
            usedPois += poi.id
            val seed = (((poi.id xor poi.id.ushr(21)).toInt() xor timeSalt()) and 0x7FFFFFFF)
            val anchor = snapToStreet(poi.p, roads) ?: poi.p
            return Spawn(anchor, true, Species.forCategory(poi.category, seed, charmTier),
                lv, RpgNamer.poi(poi.name, poi.category, poi.id), poi.id)
        }
        // No fitting place: a street point on a fresh bearing.
        val brg = rng.nextFloat() * 360f
        val raw = GeoMath.destination(player, brg, 80f + rng.nextFloat() * 120f)
        val anchor = snapToStreet(raw, roads, 120f) ?: return null
        return Spawn(anchor, true, wildSpecies(charmTier), lv, "A QUIET SIDE STREET", 0L)
    }

    /** Species rotate with the clock: the same lair hosts a different guest
     *  every six hours, so no place is ever "always that one level-1 cat". */
    private fun timeSalt(): Int = (System.currentTimeMillis() / (6 * 3600_000L)).toInt() * 0x9E3779B9.toInt()

    /** Put the pending lost friend somewhere real to be found: a named place or
     *  a street point 70-260 m out. It stays put once placed (and near) so the
     *  hunter can walk to it; it re-anchors only if they wander far off. */
    fun ensureLostOne(
        player: GeoPoint, roads: List<OsmRoad>, pois: List<OsmPoi>,
        species: Int, level: Int, place: String
    ): Boolean {
        if (species < 0) { lostOne = null; return false }
        lostOne?.let {
            if (it.species == species && GeoMath.distanceM(player, it.p) <= 320f) return false
        }
        val poi = pois.asSequence()
            .filter { it.id !in usedPois }
            .filter { GeoMath.distanceM(player, it.p) in 70f..260f }
            .minByOrNull { spreadPenalty(player, it.p) + rng.nextFloat() * 100f }
        val anchor: GeoPoint = if (poi != null) {
            usedPois += poi.id
            snapToStreet(poi.p, roads) ?: poi.p
        } else {
            val brg = rng.nextFloat() * 360f
            val raw = GeoMath.destination(player, brg, 90f + rng.nextFloat() * 120f)
            snapToStreet(raw, roads, 130f) ?: raw
        }
        lostOne = Spawn(anchor, true, species, level,
            if (poi != null) RpgNamer.poi(poi.name, poi.category, poi.id) else place,
            poi?.id ?: 0L, lost = true)
        return true
    }

    /** The desk demo plants a lost friend a few strides away to walk the flow. */
    fun seedLostOne(s: Spawn) { lostOne = s }

    fun clearLostOne() { lostOne = null }

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

        // Wide cone: walking a side street onto a boulevard, BOTH turns of
        // the T are "the direction you are going" — the hunter chooses. It
        // now binds level 1 too: once you've shown a direction, the opening
        // quarry must be ahead of you, never nestled at your back.
        fun inCone(p: GeoPoint): Boolean {
            if (travelBearing == null) return true
            val brg = GeoMath.bearingDeg(player, p)
            return abs(GeoMath.angleDiff(brg, travelBearing)) <= 85f
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

        // 1st choice: a real named place in the ring, ahead of the hunter —
        // but standing OUTSIDE on the street that serves it.
        val poi = pois.asSequence()
            .filter { it.id !in usedPois && inRing(it.p) && inCone(it.p) }
            .minByOrNull {
                abs(GeoMath.distanceM(org, it.p) - d) + fanPenalty(it.p) +
                    spreadPenalty(player, it.p) + rng.nextFloat() * 90f
            }
        if (poi != null) {
            remember(poi.p)
            val seed = (((poi.id xor poi.id.ushr(21)).toInt() xor timeSalt()) and 0x7FFFFFFF)
            val anchor = snapToStreet(poi.p, roads) ?: poi.p
            return Spawn(
                anchor, true,
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
                val score = abs(GeoMath.distanceM(org, p) - d) + fanPenalty(p) +
                    spreadPenalty(player, p) + walkBonus
                if (score < bestScore) { bestScore = score; best = p; bestRoad = r }
            }
        }
        if (best != null) {
            remember(best)
            val where = bestRoad?.let { RpgNamer.road(it.name, it.kind, it.id) }
                ?: "AN UNMARKED CROSSING"
            return Spawn(best, true, wildSpecies(charmTier), level, where, 0L)
        }

        // Last resort (no map data yet): drop it dead ahead at the ladder ring.
        val brg = travelBearing ?: rng.nextFloat() * 360f
        val raw = GeoMath.destination(if (level == 1) org else player, brg,
            if (level == 1) 35f else d * 0.9f)
        remember(raw)
        return Spawn(raw, true, wildSpecies(charmTier), level, "THE OPEN WILDS", 0L)
    }

    private fun placeTreasure(player: GeoPoint, roads: List<OsmRoad>, pois: List<OsmPoi>): Spawn? {
        // Only anchor a cache where it snaps onto a walkable street. A raw POI
        // centroid is often the building interior or the middle of a parking
        // lot — somewhere you can never stand within reach — so a POI with no
        // walkable road nearby is skipped, not dropped there. If none qualify,
        // no cache this pass (better than one you can't collect).
        val pick = pois.asSequence()
            .filter { it.id !in usedPois }
            .filter { GeoMath.distanceM(player, it.p) in 30f..ladderDistance().coerceAtMost(400f) }
            .mapNotNull { poi -> snapToStreet(poi.p, roads)?.let { poi to it } }
            .minByOrNull { (poi, _) ->
                GeoMath.distanceM(player, poi.p) + spreadPenalty(player, poi.p) + rng.nextFloat() * 120f
            }
            ?: return null
        val (poi, anchor) = pick
        return Spawn(anchor, false, 0, level,
            RpgNamer.poi(poi.name, poi.category, poi.id), poi.id)
    }
}
