package com.taphunter.shared

import android.graphics.Color

/**
 * The twenty-four creatures of the realm. The first twelve are natives;
 * the second twelve walked in out of the world's mythologies — wisp and
 * moon hare, kelpie and brownie, baku and kitsune, carbuncle and golem,
 * thunderbird and phoenix, jackalope and djinn — each wearing an original
 * name. Every species has a distinct personality: temperament drives how
 * it moves and mingles in the den, `nature` is its one-line character.
 */
class Species(
    val name: String,
    val main: Int,
    val accent: Int,
    val habitat: String,       // where this one is found
    val lore: String,          // HunterDex entry
    val temperament: String,   // one word, worn like a title
    val nature: String,        // one line of character, shown in den + dex
    val energy: Float,         // 0 sluggish .. 1 frantic (den movement)
    val social: Float,         // 0 loner .. 1 gregarious (den mingling)
    val motion: Int            // den gait: WALK / HOP / DART / DRIFT / FLOAT
) {
    companion object {
        const val WALK = 0; const val HOP = 1; const val DART = 2
        const val DRIFT = 3; const val FLOAT = 4

        val ALL = arrayOf(
            Species(
                "LEAFLING", Color.rgb(80, 255, 120), Color.rgb(220, 255, 190),
                "PARKS AND GLADES",
                "A sprout that pulled itself out of the lawn one dawn and never looked back. " +
                    "Leaflings nap under park benches and sneeze pollen when startled. " +
                    "Gardeners swear the flowerbeds bloom brighter where one has slept.",
                "DOZY", "Naps mid-step and dreams in chlorophyll.",
                0.25f, 0.6f, WALK
            ),
            Species(
                "THORNPUP", Color.rgb(170, 255, 60), Color.rgb(255, 250, 200),
                "THE WILD TRAILS",
                "Half bramble, half puppy, all enthusiasm. Thornpups patrol trailheads " +
                    "and chase hikers' bootlaces for sport. Its spikes are soft until it " +
                    "gets excited — which is always.",
                "ZOOMY", "Runs three laps to greet you once.",
                0.95f, 0.8f, DART
            ),
            Species(
                "PUDDLIM", Color.rgb(60, 220, 255), Color.rgb(220, 250, 255),
                "WELLS AND WATERS",
                "A living droplet that escaped a fountain and refuses to be still water. " +
                    "Puddlims ripple with laughter and copy the shape of whatever swims by. " +
                    "On rainy days, dozens gather in gutters to race.",
                "GIGGLY", "Wobbles when amused, which is constantly.",
                0.6f, 0.7f, HOP
            ),
            Species(
                "EMBERLING", Color.rgb(255, 140, 50), Color.rgb(255, 240, 160),
                "TAVERNS AND HEARTHS",
                "Born from the last coal of a closing kitchen, it lives for warm corners " +
                    "and crumbs of conversation. An Emberling in your pocket keeps coffee " +
                    "hot but tends to singe receipts.",
                "COZY", "Claims the warmest spot, shares it grudgingly.",
                0.4f, 0.5f, WALK
            ),
            Species(
                "BOOKWYRM", Color.rgb(190, 120, 255), Color.rgb(240, 220, 255),
                "ACADEMIES",
                "A pocket dragon that hoards words instead of gold. It reads over " +
                    "shoulders in libraries and hisses at dog-eared pages. Its spectacles " +
                    "are not prescription; it simply believes they help.",
                "PEDANTIC", "Corrects the other creatures' grammar. They growl.",
                0.35f, 0.3f, WALK
            ),
            Species(
                "LUXMOTH", Color.rgb(255, 235, 140), Color.rgb(255, 255, 230),
                "SANCTUMS",
                "It nests in belfries and stained-glass sills, drinking candlelight. " +
                    "A Luxmoth's wings hold the last hour of sunset in them; folk say " +
                    "seeing one at dusk means a kept promise.",
                "SERENE", "Drifts above squabbles, glowing softly.",
                0.3f, 0.4f, FLOAT
            ),
            Species(
                "COINIX", Color.rgb(255, 210, 40), Color.rgb(255, 250, 210),
                "MARKETS AND POSTS",
                "A mimic that took the shape of the one thing everybody reaches for. " +
                    "Coinix loiter near tills and vending machines, giggling in small " +
                    "change. Merchants consider one on the counter very lucky — until it flies off.",
                "GREEDY", "First in line for treats. Every time.",
                0.7f, 0.5f, HOP
            ),
            Species(
                "FERROKIT", Color.rgb(150, 190, 255), Color.rgb(235, 245, 255),
                "WAYGATES",
                "A fox kit of brushed steel that rides the rails between stations, " +
                    "whiskers tuned to timetables. It arrives precisely one minute before " +
                    "every train and has never once explained how.",
                "PUNCTUAL", "Patrols the den on a schedule only it knows.",
                0.55f, 0.35f, WALK
            ),
            Species(
                "VOLTLING", Color.rgb(255, 255, 80), Color.rgb(200, 240, 255),
                "EMBER DEPOTS",
                "A spark that jumped the wire and liked the freedom. Voltlings doze in " +
                    "charging stations and fuel pumps, dreaming in kilowatts. Pet one and " +
                    "your hair will remember it all day.",
                "JITTERY", "Cannot hold still; apologizes with static.",
                0.9f, 0.3f, DART
            ),
            Species(
                "GUSTRIL", Color.rgb(120, 230, 255), Color.rgb(255, 255, 255),
                "OVERLOOKS AND SKIES",
                "It surfs updrafts above viewpoints and screams delightedly into the wind. " +
                    "A Gustril's feathers never ruffle; the breeze belongs to it. It " +
                    "collects hats it has liberated from tourists.",
                "BOISTEROUS", "Announces its landings. Loudly.",
                0.8f, 0.75f, DRIFT
            ),
            Species(
                "SHADEPAW", Color.rgb(255, 90, 220), Color.rgb(255, 220, 250),
                "STREETS AT LARGE",
                "The cat you almost saw. Shadepaws slink along fences at the edge of " +
                    "streetlight, one street ahead of you, always. Catching one is less " +
                    "a hunt and more a negotiation it lets you win.",
                "ALOOF", "Pretends not to want petting. Stays in reach.",
                0.5f, 0.15f, WALK
            ),
            Species(
                "PRISMKIN", Color.rgb(255, 120, 255), Color.rgb(160, 255, 255),
                "ANYWHERE, RARELY",
                "A shard of somewhere else entirely, walking on refracted light. " +
                    "Prismkin appear where they please, when they please, to whom they " +
                    "please. The lure charm merely makes you interesting to one.",
                "OTHERWORLDLY", "Watches the den like a play it has seen before.",
                0.45f, 0.2f, FLOAT
            ),
            // ---------------- the mythology wing ----------------
            Species(
                "WISPLET", Color.rgb(150, 255, 210), Color.rgb(230, 255, 245),
                "THE WILD TRAILS",
                "A will-o'-the-wisp gone freelance. For centuries its kind led travelers " +
                    "one fateful step off the path; this one leads them a single harmless " +
                    "step, then giggles and shows the way back. Marsh lights miss it.",
                "FLICKERY", "Dims shyly when watched, blazes when ignored.",
                0.65f, 0.5f, FLOAT
            ),
            Species(
                "MOONHARE", Color.rgb(230, 235, 255), Color.rgb(190, 200, 255),
                "PARKS AND GLADES",
                "The rabbit in the moon has cousins, and they commute. A Moonhare pounds " +
                    "invisible mochi under park lamps at dusk, ears tuned to the tide. " +
                    "Its footprints glow faintly for exactly one heartbeat.",
                "POLITE", "Bows before eating. Expects the same of you.",
                0.6f, 0.6f, HOP
            ),
            Species(
                "KELPLING", Color.rgb(80, 200, 190), Color.rgb(210, 250, 240),
                "WELLS AND WATERS",
                "A kelpie foal, mane still dripping loch water. Its ancestors offered " +
                    "rides nobody should have taken; it offers splashes, which everyone " +
                    "should. Keeps one hoof in every fountain it has ever met.",
                "SLY", "Nudges other creatures toward puddles, innocently.",
                0.55f, 0.45f, DRIFT
            ),
            Species(
                "PUCKLE", Color.rgb(210, 160, 90), Color.rgb(255, 235, 190),
                "TAVERNS AND HEARTHS",
                "A hearth-brownie of the old contract: tidy the crumbs, mend the mugs, " +
                    "vanish before thanks. Thank one directly and it sulks behind the " +
                    "kettle for a week. Leave it a berry instead — that's proper manners.",
                "HELPFUL", "Straightens everything, including other creatures.",
                0.5f, 0.7f, WALK
            ),
            Species(
                "DREAMBAKU", Color.rgb(140, 150, 230), Color.rgb(220, 225, 255),
                "ACADEMIES",
                "A baku on a diet: it sips nightmares out of textbooks and exam halls, " +
                    "leaving only the good kind of butterflies. Its trunk twitches when " +
                    "someone nearby is dreading a test it has already eaten.",
                "DROWSY", "Sleepwalks gracefully; snores in lullabies.",
                0.2f, 0.5f, WALK
            ),
            Species(
                "GLIMMERFOX", Color.rgb(255, 180, 90), Color.rgb(160, 255, 230),
                "SANCTUMS",
                "A young kitsune with two tails and towering ambition — the elders wear " +
                    "nine. Each tail tip burns with cold foxfire. It practices small " +
                    "harmless illusions: your keys, briefly, become a leaf.",
                "CLEVER", "Wins every den game; nobody saw how.",
                0.7f, 0.4f, DART
            ),
            Species(
                "GEMBACK", Color.rgb(120, 220, 160), Color.rgb(255, 130, 170),
                "MARKETS AND POSTS",
                "The carbuncle of the southern tales, whose back-gem grants fortune to " +
                    "the pure-hearted. It is deeply embarrassed by the gem and covers it " +
                    "with leaves. The gem shows your reflection — flattered slightly.",
                "BASHFUL", "Hides its treasure; shares its snacks.",
                0.5f, 0.3f, HOP
            ),
            Species(
                "CLAYWARD", Color.rgb(190, 160, 130), Color.rgb(255, 220, 170),
                "WAYGATES",
                "A palm-sized golem discharged from guard duty with honors. Somewhere " +
                    "under its brow a kind word is written, which is why it is gentle. " +
                    "It stands very still in stations, being mistaken for lost luggage.",
                "STEADFAST", "Moves little; misses nothing.",
                0.15f, 0.4f, WALK
            ),
            Species(
                "SKYDRUM", Color.rgb(90, 160, 255), Color.rgb(255, 255, 160),
                "EMBER DEPOTS",
                "A fledgling of the great storm-birds; each wingbeat is a kettledrum " +
                    "heard from three valleys away. It roosts near charging stations to " +
                    "sip stray current, politely, like tea.",
                "RUMBLY", "Purrs in low thunder when content.",
                0.75f, 0.5f, DRIFT
            ),
            Species(
                "PYREBIRD", Color.rgb(255, 110, 60), Color.rgb(255, 230, 120),
                "OVERLOOKS AND SKIES",
                "A phoenix in its adolescent molts: every shed feather flares into a " +
                    "tiny, harmless firework, and it insists each one is a full rebirth " +
                    "deserving of ceremony. Attends every sunset professionally.",
                "DRAMATIC", "Faints beautifully when denied a treat.",
                0.65f, 0.35f, FLOAT
            ),
            Species(
                "HORNHOP", Color.rgb(220, 180, 140), Color.rgb(160, 255, 190),
                "STREETS AT LARGE",
                "The jackalope of the tall tales, antlers and all. It sings back at car " +
                    "alarms in perfect key and wins. Cowboys claimed you could catch one " +
                    "with whiskey; a berry works better and is legal everywhere.",
                "BRASH", "Challenges everything to a race, including walls.",
                0.85f, 0.65f, HOP
            ),
            Species(
                "SANDSHIFT", Color.rgb(240, 200, 120), Color.rgb(140, 220, 255),
                "ANYWHERE, RARELY",
                "A djinn of the noon wind, retired from the wish business. It grants " +
                    "exactly zero wishes but excellent directions, delivered in a voice " +
                    "like warm sand. Appears where it pleases; the charm merely amuses it.",
                "WRY", "Smiles like it knows the ending. It does.",
                0.6f, 0.3f, DRIFT
            ),
        )

        const val PRISMKIN = 11
        const val SANDSHIFT = 23

        /** Each native has a mythic counterpart haunting the same places. */
        private val MYTHIC_ALT = intArrayOf(13, 12, 14, 15, 16, 17, 18, 19, 20, 21, 22)

        /** Which species haunts a POI category; rare wanderers ride the charm. */
        fun forCategory(category: String, seed: Int, charmTier: Int): Int {
            val roll = seed % 100
            if (roll < 2 + charmTier * 4) return PRISMKIN
            if ((seed / 100) % 100 < 1 + charmTier * 2) return SANDSHIFT
            val base = when {
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
            // A third of encounters are the mythic counterpart of the place.
            return if ((seed / 10000) % 100 < 33) MYTHIC_ALT[base] else base
        }
    }
}
