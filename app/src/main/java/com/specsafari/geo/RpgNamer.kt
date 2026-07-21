package com.specsafari.geo

/**
 * Deterministic fantasy renamer: the real map, wearing a costume. The same
 * street or shop always RPGifies to the same name (seeded by OSM id + name),
 * so the wearer's neighborhood becomes a stable questing realm.
 */
object RpgNamer {

    private val STREET_SUFFIXES = setOf(
        "street", "st", "road", "rd", "avenue", "ave", "boulevard", "blvd", "drive", "dr",
        "lane", "ln", "way", "court", "ct", "place", "pl", "trail", "path", "circle", "cir",
        "terrace", "ter", "highway", "hwy", "parkway", "pkwy", "alley", "row", "walk", "loop",
        "north", "south", "east", "west", "n", "s", "e", "w"
    )

    private val FANTASY_ROOTS = arrayOf(
        "Bramble", "Ember", "Willow", "Raven", "Fable", "Thistle", "Wander", "Moss",
        "Cinder", "Frost", "Meadow", "Hollow", "Star", "Fen", "Elder", "Dusk"
    )

    private val STREET_TEMPLATES = arrayOf(
        "THE %s WAY", "%s MARCH", "OLD %s ROAD", "%s CROSSING",
        "THE %s TRACK", "%s RUN", "%s PASSAGE", "THE %s MILE"
    )

    /** Some streets keep their full name under an archaic honorific. */
    private val FULLNAME_TEMPLATES = arrayOf("YE %s", "OLDE %s", "YE OLDE %s")

    private val PATH_NAMES = arrayOf(
        "WANDERER'S PATH", "FOX TRAIL", "MOSSY CUT", "PILGRIM'S WALK",
        "HIDDEN WAY", "DEER TRACK", "SHADOW PATH", "OLD CART TRACK"
    )

    private fun seed(id: Long, name: String?): Int {
        var h = (id xor (id ushr 32)).toInt()
        if (name != null) h = h * 31 + name.hashCode()
        return h and 0x7FFFFFFF
    }

    /** "Oak Street" -> "Oak"; empty roots get a seeded fantasy word. */
    private fun root(name: String?, s: Int): String {
        val cleaned = (name ?: "")
            .split(Regex("[\\s]+"))
            .filter { it.isNotBlank() && it.lowercase().trim('.') !in STREET_SUFFIXES }
            .joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }
            .replace(Regex("(?i)\\b(llc|inc|ltd|co)\\.?\\b"), "")
            .trim()
            .take(16)
            .trim()
        return cleaned.ifBlank { FANTASY_ROOTS[s % FANTASY_ROOTS.size] }.uppercase()
    }

    /** Streets keep a recognizable root so the map still navigates like a map. */
    fun road(name: String?, kind: String, id: Long): String? {
        val s = seed(id, name)
        if (name == null) {
            // Only footpaths earn a label when unnamed; streets stay quiet.
            return if (kind in OsmRoad.PATH_KINDS) PATH_NAMES[s % PATH_NAMES.size] else null
        }
        // Half the realm's streets wear the archaic full name ("YE DUBLIN AVE"),
        // the rest get a template built on the root ("THE DUBLIN WAY").
        return if (s % 2 == 0) {
            FULLNAME_TEMPLATES[s % FULLNAME_TEMPLATES.size]
                .format(name.uppercase().take(20).trim())
        } else {
            STREET_TEMPLATES[s % STREET_TEMPLATES.size].format(root(name, s))
        }
    }

    fun poi(name: String?, category: String, id: Long): String {
        val s = seed(id, name)
        val r = root(name, s)
        fun pick(vararg t: String) = t[s % t.size].format(r)
        return when {
            category.startsWith("leisure=park") || category.startsWith("leisure=garden") ->
                pick("%s GLADE", "THE VERDANT %s", "%s GREENWOLD")
            category.startsWith("leisure=nature_reserve") || category == "trailhead" ||
                category.startsWith("natural=peak") ->
                pick("GATE OF THE %s WILDS", "%s WILDMARK", "THE %s FELLS")
            category.startsWith("leisure=playground") || category.startsWith("leisure=pitch") ||
                category.startsWith("leisure=dog_park") || category.startsWith("leisure=golf") ->
                pick("%s TILTYARD", "THE %s COMMONS", "%s FAIRGROUND")
            category.startsWith("amenity=cafe") || category.startsWith("amenity=restaurant") ||
                category.startsWith("amenity=fast_food") || category.startsWith("amenity=bar") ||
                category.startsWith("amenity=pub") ->
                pick("THE %s TAVERN", "%s ALEHOUSE", "THE KETTLE OF %s", "%s HEARTH")
            category.startsWith("shop=supermarket") || category.startsWith("amenity=marketplace") ->
                pick("THE GRAND %s BAZAAR", "%s PROVISIONS", "%s MARKET SQUARE")
            category.startsWith("shop") ->
                pick("%s TRADING POST", "THE %s EMPORIUM", "%s CURIOS")
            category.startsWith("amenity=school") || category.startsWith("amenity=college") ||
                category.startsWith("amenity=university") || category.startsWith("amenity=kindergarten") ->
                pick("ACADEMY OF %s", "%s CONCLAVE", "THE %s SCRIPTORIUM")
            category.startsWith("amenity=library") ->
                pick("THE %s ATHENAEUM", "ARCHIVE OF %s")
            category.startsWith("amenity=place_of_worship") || category == "historic" ->
                pick("SANCTUM OF %s", "THE %s RELIQUARY", "%s SHRINE")
            category == "station" ->
                pick("WAYGATE %s", "THE %s PORTAL")
            category.startsWith("amenity=fountain") || category.startsWith("natural=spring") ||
                category.startsWith("natural=beach") ->
                pick("THE %s WELLSPRING", "%s WATERS")
            category.startsWith("tourism=viewpoint") ->
                pick("THE %s OVERLOOK", "%s WATCH")
            category.startsWith("tourism") ->
                pick("HALL OF %s", "THE %s WONDER")
            category.startsWith("amenity=pharmacy") ->
                pick("THE %s APOTHECARY")
            category.startsWith("amenity=bank") ->
                pick("%s COUNTING HOUSE")
            category.startsWith("amenity=fuel") ->
                pick("%s EMBER DEPOT")
            category.startsWith("amenity=cinema") || category.startsWith("amenity=theatre") ->
                pick("THE %s PLAYHOUSE")
            category.startsWith("amenity=post_office") ->
                pick("%s COURIER ROOST")
            category.startsWith("amenity=townhall") ->
                pick("%s HIGH KEEP")
            else -> pick("THE %s LANDMARK", "%s WAYPOINT")
        }
    }
}
