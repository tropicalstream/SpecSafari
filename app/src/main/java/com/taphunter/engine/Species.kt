package com.taphunter.engine

import android.graphics.Color

/**
 * The twelve creatures of the realm. Each haunts a kind of real-world place:
 * the map decides what you meet by where you actually walk. All original
 * critters, tuned for the waveguide — white-hot cores, saturated glows.
 */
class Species(
    val name: String,
    val main: Int,
    val accent: Int,
    val habitat: String       // shown in the box: where this one is found
) {
    companion object {
        val ALL = arrayOf(
            Species("LEAFLING", Color.rgb(80, 255, 120), Color.rgb(220, 255, 190), "PARKS AND GLADES"),
            Species("THORNPUP", Color.rgb(170, 255, 60), Color.rgb(255, 250, 200), "THE WILD TRAILS"),
            Species("PUDDLIM", Color.rgb(60, 220, 255), Color.rgb(220, 250, 255), "WELLS AND WATERS"),
            Species("EMBERLING", Color.rgb(255, 140, 50), Color.rgb(255, 240, 160), "TAVERNS AND HEARTHS"),
            Species("BOOKWYRM", Color.rgb(190, 120, 255), Color.rgb(240, 220, 255), "ACADEMIES"),
            Species("LUXMOTH", Color.rgb(255, 235, 140), Color.rgb(255, 255, 230), "SANCTUMS"),
            Species("COINIX", Color.rgb(255, 210, 40), Color.rgb(255, 250, 210), "MARKETS AND POSTS"),
            Species("FERROKIT", Color.rgb(150, 190, 255), Color.rgb(235, 245, 255), "WAYGATES"),
            Species("VOLTLING", Color.rgb(255, 255, 80), Color.rgb(200, 240, 255), "EMBER DEPOTS"),
            Species("GUSTRIL", Color.rgb(120, 230, 255), Color.rgb(255, 255, 255), "OVERLOOKS AND SKIES"),
            Species("SHADEPAW", Color.rgb(255, 90, 220), Color.rgb(255, 220, 250), "STREETS AT LARGE"),
            Species("PRISMKIN", Color.rgb(255, 120, 255), Color.rgb(160, 255, 255), "ANYWHERE, RARELY"),
        )
        const val PRISMKIN = 11

        /** Which species haunts a POI category; PRISMKIN chance rises with the lure charm. */
        fun forCategory(category: String, seed: Int, charmTier: Int): Int {
            val roll = seed % 100
            if (roll < 2 + charmTier * 4) return PRISMKIN
            return when {
                category.startsWith("leisure=park") || category.startsWith("leisure=garden") ||
                    category.startsWith("leisure=playground") || category.startsWith("leisure=pitch") ||
                    category.startsWith("leisure=dog_park") || category.startsWith("leisure=golf") -> 0
                category.startsWith("leisure=nature_reserve") || category == "trailhead" ||
                    category.startsWith("natural=peak") -> 1
                category.startsWith("amenity=fountain") || category.startsWith("natural=spring") ||
                    category.startsWith("natural=beach") -> 2
                category.startsWith("amenity=cafe") || category.startsWith("amenity=restaurant") ||
                    category.startsWith("amenity=fast_food") || category.startsWith("amenity=bar") ||
                    category.startsWith("amenity=pub") -> 3
                category.startsWith("amenity=school") || category.startsWith("amenity=college") ||
                    category.startsWith("amenity=university") || category.startsWith("amenity=library") -> 4
                category.startsWith("amenity=place_of_worship") || category == "historic" -> 5
                category.startsWith("shop") || category.startsWith("amenity=marketplace") ||
                    category.startsWith("amenity=bank") || category.startsWith("amenity=post_office") -> 6
                category == "station" -> 7
                category.startsWith("amenity=fuel") || category.startsWith("amenity=pharmacy") -> 8
                category.startsWith("tourism") -> 9
                else -> 10
            }
        }
    }
}
