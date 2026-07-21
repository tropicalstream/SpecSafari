package com.specsafari.engine

import com.specsafari.geo.GeoMath
import com.specsafari.geo.GeoPoint
import com.specsafari.geo.OsmPoi
import com.specsafari.geo.RpgNamer
import com.specsafari.shared.Species

/** A real-world place made kinder by a freed creature. Friendly creatures,
 *  set loose, venture to the habitats their biology fits and leave the land
 *  more alive — the vitality here softens other creatures, drops more
 *  berries and caches, and greens the map. Vitality accrues with every
 *  fond release nearby; a wary parting barely stirs the ground. */
class Haven(var lat: Double, var lon: Double, var vitality: Float, var species: Int) {
    val p get() = GeoPoint(lat, lon)
}

/**
 * The ecology of release. Owns the havens, persists them as CSV through the
 * store, matches each species to the real-place family that fits it, and
 * reports the flourishing felt at any point so the hunt loop and the map can
 * respond. Glasses-side only; nothing here touches the phone den.
 */
class EcoWorld(private val load: () -> String, private val save: (String) -> Unit) {

    val havens = mutableListOf<Haven>()
    private var loaded = false

    private fun ensureLoaded() {
        if (loaded) return
        loaded = true
        for (ln in load().split('\n')) {
            if (ln.isBlank()) continue
            val p = ln.split(',')
            val lat = p.getOrNull(0)?.toDoubleOrNull() ?: continue
            val lon = p.getOrNull(1)?.toDoubleOrNull() ?: continue
            val vit = p.getOrNull(2)?.toFloatOrNull() ?: continue
            havens += Haven(lat, lon, vit, p.getOrNull(3)?.toIntOrNull() ?: 10)
        }
    }

    private fun persist() {
        save(havens.joinToString("\n") {
            "%.6f,%.6f,%.2f,%d".format(it.lat, it.lon, it.vitality, it.species)
        })
    }

    /** How far a haven's flourishing reaches — it widens as the place thrives. */
    fun havenRadius(h: Haven): Float = (60f + h.vitality * 24f).coerceAtMost(260f)

    /** The desk demo plants a flourishing haven so the grove + its effects can
     *  be seen without a two-device release. Not persisted. */
    fun seedDemo(p: GeoPoint, vitality: Float, species: Int) {
        ensureLoaded()
        if (havens.none { GeoMath.distanceM(p, it.p) < 60f })
            havens += Haven(p.lat, p.lon, vitality, species)
    }

    /** A creature set free ventures to the nearest habitat of its kind (≤220 m)
     *  and, if it left fondly (friendliness = its bond hearts), makes that
     *  place flourish. Returns the RPG name of where it settled, or null if
     *  nowhere fitting was charted. */
    fun release(player: GeoPoint, species: Int, pois: List<OsmPoi>, friendliness: Int): String? {
        ensureLoaded()
        val wantIdx = preferredCategoryIndex(species)
        val destPoi = pois
            .filter { GeoMath.distanceM(player, it.p) <= 220f }
            .filter { wantIdx < 0 || poiCategoryIndex(it.category) == wantIdx }
            .minByOrNull { GeoMath.distanceM(player, it.p) }
        val dest = destPoi?.p ?: player   // no fitting habitat charted: it settles where freed
        // A fond parting (more hearts) does more good; a wary one barely stirs.
        val gain = 0.4f + friendliness.coerceIn(0, 5) * 0.55f
        val near = havens.minByOrNull { GeoMath.distanceM(dest, it.p) }
            ?.takeIf { GeoMath.distanceM(dest, it.p) <= 45f }
        if (near != null) { near.vitality += gain; near.species = species }
        else havens += Haven(dest.lat, dest.lon, gain, species)
        if (havens.size > 40) {
            havens.sortByDescending { it.vitality }
            while (havens.size > 40) havens.removeAt(havens.lastIndex)
        }
        persist()
        return destPoi?.let { RpgNamer.poi(it.name, it.category, it.id) }
            ?: HABITAT_NAME.getOrElse(wantIdx.coerceIn(0, HABITAT_NAME.size - 1)) { "THE WILDS" }
    }

    /** Total flourishing felt at a point: every haven radiates over its
     *  radius, tapering to nothing at the edge; overlapping havens stack. */
    fun vitalityAt(p: GeoPoint): Float {
        ensureLoaded()
        var v = 0f
        for (h in havens) {
            val r = havenRadius(h)
            val d = GeoMath.distanceM(p, h.p)
            if (d < r) v += h.vitality * (1f - d / r)
        }
        return v
    }

    // Reverse of Species.MYTHIC_ALT (base->mythic): mythic index -> base family.
    private val MYTHIC_BASE = mapOf(
        13 to 0, 12 to 1, 14 to 2, 15 to 3, 16 to 4,
        17 to 5, 18 to 6, 19 to 7, 20 to 8, 21 to 9, 22 to 10
    )

    /** The POI-category family a species belongs to (native index == family
     *  index 0..10); the mythic, ecology, and rare wings map onto the same
     *  real-place families. -1 means "anywhere" (open-air / rare wanderers). */
    private fun preferredCategoryIndex(species: Int): Int = when (species) {
        in 0..10 -> species
        Species.PRISMKIN -> -1
        Species.SANDSHIFT -> -1
        Species.ZEPHYRET -> -1
        Species.NIXLET -> 2       // water
        Species.MOLDEWARP -> 0    // green ground
        Species.SYLVARCH -> 0     // glade
        Species.MISTCROWN -> 1    // mistwood
        else -> MYTHIC_BASE[species] ?: -1
    }

    /** Mirror of Species.forCategory's category→family switch, kept here so
     *  the ecology stays clear of the shared module. */
    private fun poiCategoryIndex(cat: String): Int = when {
        cat.startsWith("leisure=park") || cat.startsWith("leisure=garden") ||
            cat.startsWith("leisure=playground") || cat.startsWith("leisure=pitch") ||
            cat.startsWith("leisure=dog_park") || cat.startsWith("leisure=golf") -> 0
        cat.startsWith("leisure=nature_reserve") || cat == "trailhead" ||
            cat.startsWith("natural=peak") -> 1
        cat.startsWith("amenity=fountain") || cat.startsWith("natural=spring") ||
            cat.startsWith("natural=beach") -> 2
        cat.startsWith("amenity=cafe") || cat.startsWith("amenity=restaurant") ||
            cat.startsWith("amenity=fast_food") || cat.startsWith("amenity=bar") ||
            cat.startsWith("amenity=pub") -> 3
        cat.startsWith("amenity=school") || cat.startsWith("amenity=college") ||
            cat.startsWith("amenity=university") || cat.startsWith("amenity=library") -> 4
        cat.startsWith("amenity=place_of_worship") || cat == "historic" -> 5
        cat.startsWith("shop") || cat.startsWith("amenity=marketplace") ||
            cat.startsWith("amenity=bank") || cat.startsWith("amenity=post_office") -> 6
        cat == "station" -> 7
        cat.startsWith("amenity=fuel") || cat.startsWith("amenity=pharmacy") -> 8
        cat.startsWith("tourism") -> 9
        else -> 10
    }

    private val HABITAT_NAME = arrayOf(
        "A GLADE", "THE WILD TRAILS", "A WATERSIDE", "A HEARTH", "A QUIET COURT",
        "A SANCTUM", "A MARKET ROW", "A WAYGATE", "AN EMBER DEPOT", "AN OVERLOOK", "THE OPEN STREETS"
    )
}
