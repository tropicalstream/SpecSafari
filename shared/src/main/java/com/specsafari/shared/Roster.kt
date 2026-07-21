package com.specsafari.shared

/** One creature as an INDIVIDUAL: born the moment it is caught, named, and
 * marked for life. The seed is the genome — every phenotypic trait (coat
 * shade, birthmark, stature) derives from it deterministically, so the same
 * individual looks the same on the glasses, in the den, and on its biocard. */
data class Individual(
    val id: Long,          // birth timestamp millis — unique enough for one save
    val species: Int,
    val level: Int,
    val seed: Int,
    val day: String,       // "2026-07-21" — the day it was found
    val name: String,      // auto-named at birth; rename-able on the biocard
    val place: String,     // the RPG name of where it was found
)

object Roster {

    // ------------------------------------------------------------- codec
    // Line format: id|species|level|seed|day|name|place  (pipes sanitized)

    private fun safe(s: String) = s.replace('|', '/').replace('\n', ' ').trim()

    fun encode(list: List<Individual>): String = list.joinToString("\n") {
        listOf(
            it.id.toString(), it.species.toString(), it.level.toString(),
            it.seed.toString(), safe(it.day), safe(it.name), safe(it.place)
        ).joinToString("|")
    }

    fun decode(text: String): MutableList<Individual> =
        text.split('\n').mapNotNull { line ->
            val p = line.split('|')
            if (p.size != 7) return@mapNotNull null
            runCatching {
                Individual(
                    id = p[0].toLong(),
                    species = p[1].toInt().takeIf { it in Species.ALL.indices }
                        ?: return@mapNotNull null,
                    level = p[2].toInt().coerceIn(1, 99),
                    seed = p[3].toInt(),
                    day = p[4].take(16),
                    name = p[5].take(14),
                    place = p[6].take(40),
                )
            }.getOrNull()
        }.toMutableList()

    // ------------------------------------------------------- naming
    // Soft two-part names in the game's cozy register; seed-stable.

    private val HEADS = arrayOf(
        "Bram", "Cinder", "Moss", "Fen", "Wick", "Sorrel", "Pip", "Rook",
        "Tansy", "Ember", "Sable", "Briar", "Nim", "Cob", "Fern", "Gale",
        "Hazel", "Isla", "Juni", "Kestrel", "Lark", "Maple", "Nettle", "Otter",
        "Perri", "Quill", "Rowan", "Sedge", "Thistle", "Umber", "Vale", "Wren"
    )
    private val TAILS = arrayOf(
        "", "", "", "", "kin", "by", "wick", "let", "moss", "fell",
        "spry", "toe", "puff", "step", "song", "shade"
    )

    fun autoName(seed: Int): String {
        val h = HEADS[(seed ushr 3) and 31]
        val t = TAILS[(seed ushr 9) % TAILS.size]
        // A tail that just repeats the head's ending reads silly; drop it.
        return if (t.isNotEmpty() && !h.endsWith(t.first())) h + t else h
    }

    // ------------------------------------------------------- phenotype

    private val COATS = arrayOf(
        "ashen" to floatArrayOf(0.88f, 0.90f, 0.96f),
        "ruddy" to floatArrayOf(1.14f, 0.92f, 0.86f),
        "golden" to floatArrayOf(1.12f, 1.06f, 0.82f),
        "dusky" to floatArrayOf(0.82f, 0.82f, 0.88f),
        "pale" to floatArrayOf(1.12f, 1.12f, 1.10f),
        "mossy" to floatArrayOf(0.90f, 1.10f, 0.88f),
        "true" to floatArrayOf(1f, 1f, 1f),               // the fieldbook standard
        "smoky" to floatArrayOf(0.90f, 0.86f, 0.90f),
    )

    fun coatName(seed: Int): String = COATS[(seed ushr 13) and 7].first
    fun coatTint(seed: Int): FloatArray = COATS[(seed ushr 13) and 7].second

    /** 0 fleck, 1 star-blaze, 2 chest patch, 3 crown fleck. */
    fun markKind(seed: Int): Int = (seed ushr 17) and 3
    /** Where around the body the mark sits (radians, for flecks). */
    fun markAngle(seed: Int): Float = ((seed ushr 19) and 63) / 63f * 6.283f
    /** Marks read darker or lighter than the coat, by lineage. */
    fun markLight(seed: Int): Boolean = (seed ushr 25) and 1 == 1

    fun markDesc(seed: Int): String {
        val tone = if (markLight(seed)) "pale" else "dark"
        return when (markKind(seed)) {
            0 -> "a $tone fleck on its flank"
            1 -> "a $tone star-blaze over one eye"
            2 -> "a $tone patch across the chest"
            else -> "a $tone fleck at the crown"
        }
    }

    /** Stature multiplier, seed-stable (replaces the old per-launch jitter). */
    fun stature(seed: Int): Float = 0.88f + ((seed ushr 27) and 15) / 15f * 0.24f

    fun statureWord(seed: Int): String {
        val s = stature(seed)
        return when {
            s < 0.95f -> "small for its kind"
            s > 1.05f -> "large for its kind"
            else -> "typical of its kind"
        }
    }
}
