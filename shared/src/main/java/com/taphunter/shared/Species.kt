package com.taphunter.shared

import android.graphics.Color

/**
 * The twelve creatures of the realm. Each haunts a kind of real-world place:
 * the map decides what you meet by where you actually walk. All original
 * critters, tuned for the waveguide — white-hot cores, saturated glows.
 * The lore feeds the HunterDex on the phone.
 */
class Species(
    val name: String,
    val main: Int,
    val accent: Int,
    val habitat: String,       // where this one is found
    val lore: String           // HunterDex entry
) {
    companion object {
        val ALL = arrayOf(
            Species(
                "LEAFLING", Color.rgb(80, 255, 120), Color.rgb(220, 255, 190),
                "PARKS AND GLADES",
                "A sprout that pulled itself out of the lawn one dawn and never looked back. " +
                    "Leaflings nap under park benches and sneeze pollen when startled. " +
                    "Gardeners swear the flowerbeds bloom brighter where one has slept."
            ),
            Species(
                "THORNPUP", Color.rgb(170, 255, 60), Color.rgb(255, 250, 200),
                "THE WILD TRAILS",
                "Half bramble, half puppy, all enthusiasm. Thornpups patrol trailheads " +
                    "and chase hikers' bootlaces for sport. Its spikes are soft until it " +
                    "gets excited — which is always."
            ),
            Species(
                "PUDDLIM", Color.rgb(60, 220, 255), Color.rgb(220, 250, 255),
                "WELLS AND WATERS",
                "A living droplet that escaped a fountain and refuses to be still water. " +
                    "Puddlims ripple with laughter and copy the shape of whatever swims by. " +
                    "On rainy days, dozens gather in gutters to race."
            ),
            Species(
                "EMBERLING", Color.rgb(255, 140, 50), Color.rgb(255, 240, 160),
                "TAVERNS AND HEARTHS",
                "Born from the last coal of a closing kitchen, it lives for warm corners " +
                    "and crumbs of conversation. An Emberling in your pocket keeps coffee " +
                    "hot but tends to singe receipts."
            ),
            Species(
                "BOOKWYRM", Color.rgb(190, 120, 255), Color.rgb(240, 220, 255),
                "ACADEMIES",
                "A pocket dragon that hoards words instead of gold. It reads over " +
                    "shoulders in libraries and hisses at dog-eared pages. Its spectacles " +
                    "are not prescription; it simply believes they help."
            ),
            Species(
                "LUXMOTH", Color.rgb(255, 235, 140), Color.rgb(255, 255, 230),
                "SANCTUMS",
                "It nests in belfries and stained-glass sills, drinking candlelight. " +
                    "A Luxmoth's wings hold the last hour of sunset in them; folk say " +
                    "seeing one at dusk means a kept promise."
            ),
            Species(
                "COINIX", Color.rgb(255, 210, 40), Color.rgb(255, 250, 210),
                "MARKETS AND POSTS",
                "A mimic that took the shape of the one thing everybody reaches for. " +
                    "Coinix loiter near tills and vending machines, giggling in small " +
                    "change. Merchants consider one on the counter very lucky — until it flies off."
            ),
            Species(
                "FERROKIT", Color.rgb(150, 190, 255), Color.rgb(235, 245, 255),
                "WAYGATES",
                "A fox kit of brushed steel that rides the rails between stations, " +
                    "whiskers tuned to timetables. It arrives precisely one minute before " +
                    "every train and has never once explained how."
            ),
            Species(
                "VOLTLING", Color.rgb(255, 255, 80), Color.rgb(200, 240, 255),
                "EMBER DEPOTS",
                "A spark that jumped the wire and liked the freedom. Voltlings doze in " +
                    "charging stations and fuel pumps, dreaming in kilowatts. Pet one and " +
                    "your hair will remember it all day."
            ),
            Species(
                "GUSTRIL", Color.rgb(120, 230, 255), Color.rgb(255, 255, 255),
                "OVERLOOKS AND SKIES",
                "It surfs updrafts above viewpoints and screams delightedly into the wind. " +
                    "A Gustril's feathers never ruffle; the breeze belongs to it. It " +
                    "collects hats it has liberated from tourists."
            ),
            Species(
                "SHADEPAW", Color.rgb(255, 90, 220), Color.rgb(255, 220, 250),
                "STREETS AT LARGE",
                "The cat you almost saw. Shadepaws slink along fences at the edge of " +
                    "streetlight, one street ahead of you, always. Catching one is less " +
                    "a hunt and more a negotiation it lets you win."
            ),
            Species(
                "PRISMKIN", Color.rgb(255, 120, 255), Color.rgb(160, 255, 255),
                "ANYWHERE, RARELY",
                "A shard of somewhere else entirely, walking on refracted light. " +
                    "Prismkin appear where they please, when they please, to whom they " +
                    "please. The lure charm merely makes you interesting to one."
            ),
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
