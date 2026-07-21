package com.specsafari.shared

import android.graphics.Color

/**
 * The twenty-nine creatures of the realm. Twelve natives, twelve walked in
 * from the world's mythologies, an ecology wing of three, and two exceptionally
 * rare forest intelligences. Every species is a full organism: its
 * temperament shapes how it moves and mingles; its `niche` names the
 * microhabitat it seeks in the den's biospheres; its `biology` is the
 * evolutionary story of why it is the way it is.
 */
class Species(
    val name: String,
    val main: Int,
    val accent: Int,
    val habitat: String,       // where this one is found in the real world
    val lore: String,          // HunterDex entry
    val temperament: String,   // one word, worn like a title
    val nature: String,        // one line of character, shown in den + dex
    val niche: String,         // ecological role, shown in the dex
    val zone: String,          // microhabitat tag it seeks: WATER BLOOM THICKET EMBER STONE VOID BURROW PERCH
    val biology: String,       // evolutionary biology, one field note
    val energy: Float,         // 0 sluggish .. 1 frantic (den movement)
    val social: Float,         // 0 loner .. 1 gregarious (den mingling)
    val motion: Int            // den gait
) {
    companion object {
        const val WALK = 0; const val HOP = 1; const val DART = 2
        const val DRIFT = 3; const val FLOAT = 4
        const val SKIM = 5; const val SWIM = 6; const val BURROW = 7

        val ALL = arrayOf(
            Species(
                "LEAFLING", Color.rgb(80, 255, 120), Color.rgb(220, 255, 190),
                "PARKS AND GLADES",
                "A sprout that pulled itself out of the lawn one dawn and never looked back. " +
                    "Leaflings nap under park benches and sneeze pollen when startled. " +
                    "Gardeners swear the flowerbeds bloom brighter where one has slept.",
                "DOZY", "Naps mid-step and dreams in chlorophyll.",
                "BLOOM-GRAZER", "BLOOM",
                "Photosynthesizes through its pelt; the head-leaf is a solar sail it angles " +
                    "all day without waking. Roots shallowly through its feet during naps, " +
                    "which is most of the time.",
                0.25f, 0.6f, WALK
            ),
            Species(
                "THORNPUP", Color.rgb(170, 255, 60), Color.rgb(255, 250, 200),
                "THE WILD TRAILS",
                "Half bramble, half puppy, all enthusiasm. Thornpups patrol trailheads " +
                    "and chase hikers' bootlaces for sport. Its spikes are soft until it " +
                    "gets excited — which is always.",
                "ZOOMY", "Runs three laps to greet you once.",
                "THICKET-RUNNER", "THICKET",
                "Descended from hedges that learned to chase. Its spikes are modified " +
                    "leaves that stiffen with adrenaline; it reseeds the bramble line of " +
                    "every trail it patrols, which is why the wild stays wild.",
                0.95f, 0.8f, DART
            ),
            Species(
                "PUDDLIM", Color.rgb(60, 220, 255), Color.rgb(220, 250, 255),
                "WELLS AND WATERS",
                "A living droplet that escaped a fountain and refuses to be still water. " +
                    "Puddlims ripple with laughter and copy the shape of whatever swims by. " +
                    "On rainy days, dozens gather in gutters to race.",
                "GIGGLY", "Wobbles when amused, which is constantly.",
                "POND-DWELLER", "WATER",
                "Surface tension is its skeleton. It maintains shape by giggling — the " +
                    "vibration keeps its membrane taut — and reproduces by laughing so " +
                    "hard it splits in two.",
                0.6f, 0.7f, SWIM
            ),
            Species(
                "EMBERLING", Color.rgb(255, 140, 50), Color.rgb(255, 240, 160),
                "TAVERNS AND HEARTHS",
                "Born from the last coal of a closing kitchen, it lives for warm corners " +
                    "and crumbs of conversation. An Emberling in your pocket keeps coffee " +
                    "hot but tends to singe receipts.",
                "COZY", "Claims the warmest spot, shares it grudgingly.",
                "HEARTH-KEEPER", "EMBER",
                "An obligate warmth-feeder: it metabolizes ambient heat and stray " +
                    "gossip in equal measure. Its flame crown is a radiator fin — it " +
                    "dims when the creature is lonely, which is diagnostic.",
                0.4f, 0.5f, WALK
            ),
            Species(
                "BOOKWYRM", Color.rgb(190, 120, 255), Color.rgb(240, 220, 255),
                "ACADEMIES",
                "A pocket dragon that hoards words instead of gold. It reads over " +
                    "shoulders in libraries and hisses at dog-eared pages. Its spectacles " +
                    "are not prescription; it simply believes they help.",
                "PEDANTIC", "Corrects the other creatures' grammar. They growl.",
                "ROOST-SCHOLAR", "PERCH",
                "Evolved from cave dragons whose hoards fossilized into strata — it " +
                    "still stacks everything it values and sleeps on top. Digests " +
                    "information literally; a good paragraph sustains it for a day.",
                0.35f, 0.3f, DRIFT
            ),
            Species(
                "LUXMOTH", Color.rgb(255, 235, 140), Color.rgb(255, 255, 230),
                "SANCTUMS",
                "It nests in belfries and stained-glass sills, drinking candlelight. " +
                    "A Luxmoth's wings hold the last hour of sunset in them; folk say " +
                    "seeing one at dusk means a kept promise.",
                "SERENE", "Drifts above squabbles, glowing softly.",
                "BLOOM-SIPPER", "BLOOM",
                "Feeds on light the way its cousins feed on nectar, preferring the " +
                    "aged vintages: candle flame, dusk, stained glass. Its wing scales " +
                    "are prisms that store an hour of glow for the night flights.",
                0.3f, 0.4f, FLOAT
            ),
            Species(
                "COINIX", Color.rgb(255, 210, 40), Color.rgb(255, 250, 210),
                "MARKETS AND POSTS",
                "A mimic that took the shape of the one thing everybody reaches for. " +
                    "Coinix loiter near tills and vending machines, giggling in small " +
                    "change. Merchants consider one on the counter very lucky — until it flies off.",
                "GREEDY", "First in line for treats. Every time.",
                "STONE-HOARDER", "STONE",
                "A mimic whose camouflage outlived its predator. It nests on sun-warmed " +
                    "stone because metal remembers heat, and it hoards shiny pebbles in " +
                    "the sincere belief that they are eggs.",
                0.7f, 0.5f, DART
            ),
            Species(
                "FERROKIT", Color.rgb(150, 190, 255), Color.rgb(235, 245, 255),
                "WAYGATES",
                "A fox kit of brushed steel that rides the rails between stations, " +
                    "whiskers tuned to timetables. It arrives precisely one minute before " +
                    "every train and has never once explained how.",
                "PUNCTUAL", "Patrols the den on a schedule only it knows.",
                "STONE-SENTRY", "STONE",
                "Grew its alloy coat from generations of licking rail dust; the " +
                    "whiskers are antennae that read vibration through solid rock. It " +
                    "marks territory in perfect intervals, like a timetable.",
                0.55f, 0.35f, WALK
            ),
            Species(
                "VOLTLING", Color.rgb(255, 255, 80), Color.rgb(200, 240, 255),
                "EMBER DEPOTS",
                "A spark that jumped the wire and liked the freedom. Voltlings doze in " +
                    "charging stations and fuel pumps, dreaming in kilowatts. Pet one and " +
                    "your hair will remember it all day.",
                "JITTERY", "Cannot hold still; apologizes with static.",
                "VENT-SPARK", "EMBER",
                "Its body is a standing charge looking for a capacitor. It grounds " +
                    "itself at warm vents to keep from arcing, and its jitters are " +
                    "literal: sixty tiny discharges a minute, each one an apology.",
                0.9f, 0.3f, DART
            ),
            Species(
                "GUSTRIL", Color.rgb(120, 230, 255), Color.rgb(255, 255, 255),
                "OVERLOOKS AND SKIES",
                "It surfs updrafts above viewpoints and screams delightedly into the wind. " +
                    "A Gustril's feathers never ruffle; the breeze belongs to it. It " +
                    "collects hats it has liberated from tourists.",
                "BOISTEROUS", "Announces its landings. Loudly.",
                "HIGH-PERCHER", "PERCH",
                "Hollow-boned and gale-built: its wings generate lift at a standstill " +
                    "by convincing the local air to move. Lands only to brag about " +
                    "flying, on the highest point available.",
                0.8f, 0.75f, DRIFT
            ),
            Species(
                "SHADEPAW", Color.rgb(255, 90, 220), Color.rgb(255, 220, 250),
                "STREETS AT LARGE",
                "The cat you almost saw. Shadepaws slink along fences at the edge of " +
                    "streetlight, one street ahead of you, always. Catching one is less " +
                    "a hunt and more a negotiation it lets you win.",
                "ALOOF", "Pretends not to want petting. Stays in reach.",
                "DUSK-STALKER", "VOID",
                "Evolved in the gap between streetlights — its coat absorbs the exact " +
                    "wavelengths of a glance. Hunts nothing anymore; the stalk itself " +
                    "became the meal, which biologists call ritualized and cats call art.",
                0.5f, 0.15f, WALK
            ),
            Species(
                "PRISMKIN", Color.rgb(255, 120, 255), Color.rgb(160, 255, 255),
                "ANYWHERE, RARELY",
                "A shard of somewhere else entirely, walking on refracted light. " +
                    "Prismkin appear where they please, when they please, to whom they " +
                    "please. The lure charm merely makes you interesting to one.",
                "OTHERWORLDLY", "Watches the den like a play it has seen before.",
                "VOID-WALKER", "VOID",
                "Not strictly local to this universe; it refracts in and out of " +
                    "adjacent ones, which is why it prefers dark still places — less " +
                    "light to keep coherent. Eats one photon a day, as a formality.",
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
                "VOID-LANTERN", "VOID",
                "A marsh-gas symbiosis that achieved opinions. It burns cold — the " +
                    "flame is bioluminescent plasma — and photosynthesizes in reverse, " +
                    "exhaling the dark it drinks from shadowed hollows.",
                0.65f, 0.5f, FLOAT
            ),
            Species(
                "MOONHARE", Color.rgb(230, 235, 255), Color.rgb(190, 200, 255),
                "PARKS AND GLADES",
                "The rabbit in the moon has cousins, and they commute. A Moonhare pounds " +
                    "invisible mochi under park lamps at dusk, ears tuned to the tide. " +
                    "Its footprints glow faintly for exactly one heartbeat.",
                "POLITE", "Bows before eating. Expects the same of you.",
                "BLOOM-TENDER", "BLOOM",
                "Its circadian clock runs on the lunar day, not the solar one — 50 " +
                    "extra minutes it spends gardening. Night-blooming flowers track " +
                    "Moonhares the way sunflowers track the sun.",
                0.6f, 0.6f, HOP
            ),
            Species(
                "KELPLING", Color.rgb(80, 200, 190), Color.rgb(210, 250, 240),
                "WELLS AND WATERS",
                "A kelpie foal, mane still dripping loch water. Its ancestors offered " +
                    "rides nobody should have taken; it offers splashes, which everyone " +
                    "should. Keeps one hoof in every fountain it has ever met.",
                "SLY", "Nudges other creatures toward puddles, innocently.",
                "SHALLOWS-HORSE", "WATER",
                "Amphibious by treaty: gills behind the jaw for the deep hours, lungs " +
                    "for the mischief hours. Its mane is a freshwater algae it farms " +
                    "and grooms; a glossy mane is a well-fed Kelpling.",
                0.55f, 0.45f, DRIFT
            ),
            Species(
                "PUCKLE", Color.rgb(210, 160, 90), Color.rgb(255, 235, 190),
                "TAVERNS AND HEARTHS",
                "A hearth-brownie of the old contract: tidy the crumbs, mend the mugs, " +
                    "vanish before thanks. Thank one directly and it sulks behind the " +
                    "kettle for a week. Leave it a berry instead — that's proper manners.",
                "HELPFUL", "Straightens everything, including other creatures.",
                "HEARTH-WARDEN", "EMBER",
                "Coevolved with human kitchens so tightly it now imprints on any warm " +
                    "hearth as 'home'. Burns crumbs for energy at near-perfect " +
                    "efficiency; the tidying is digestion.",
                0.5f, 0.7f, WALK
            ),
            Species(
                "DREAMBAKU", Color.rgb(140, 150, 230), Color.rgb(220, 225, 255),
                "ACADEMIES",
                "A baku on a diet: it sips nightmares out of textbooks and exam halls, " +
                    "leaving only the good kind of butterflies. Its trunk twitches when " +
                    "someone nearby is dreading a test it has already eaten.",
                "DROWSY", "Sleepwalks gracefully; snores in lullabies.",
                "ROOST-DREAMER", "PERCH",
                "Feeds exclusively on anxiety, a renewable resource near exams. It " +
                    "sleeps eighteen hours a day because digestion happens in REM; its " +
                    "snore is a byproduct lullaby that biologists classify as symbiotic.",
                0.2f, 0.5f, WALK
            ),
            Species(
                "GLIMMERFOX", Color.rgb(255, 180, 90), Color.rgb(160, 255, 230),
                "SANCTUMS",
                "A young kitsune with two tails and towering ambition — the elders wear " +
                    "nine. Each tail tip burns with cold foxfire. It practices small " +
                    "harmless illusions: your keys, briefly, become a leaf.",
                "CLEVER", "Wins every den game; nobody saw how.",
                "THICKET-TRICKSTER", "THICKET",
                "Grows one tail per century, each a separate heat-sink for its " +
                    "foxfire metabolism. The illusions are hunting behavior with the " +
                    "hunger removed — pure craft, kept sharp on keys and leaves.",
                0.7f, 0.4f, DART
            ),
            Species(
                "GEMBACK", Color.rgb(120, 220, 160), Color.rgb(255, 130, 170),
                "MARKETS AND POSTS",
                "The carbuncle of the southern tales, whose back-gem grants fortune to " +
                    "the pure-hearted. It is deeply embarrassed by the gem and covers it " +
                    "with leaves. The gem shows your reflection — flattered slightly.",
                "BASHFUL", "Hides its treasure; shares its snacks.",
                "STONE-BURROWER", "STONE",
                "The gem is a mineral organ — it grows a new facet each year like tree " +
                    "rings, crystallizing trace minerals the creature licks from stones. " +
                    "The leaf camouflage is learned, not instinct, and slightly vain.",
                0.5f, 0.3f, HOP
            ),
            Species(
                "CLAYWARD", Color.rgb(190, 160, 130), Color.rgb(255, 220, 170),
                "WAYGATES",
                "A palm-sized golem discharged from guard duty with honors. Somewhere " +
                    "under its brow a kind word is written, which is why it is gentle. " +
                    "It stands very still in stations, being mistaken for lost luggage.",
                "STEADFAST", "Moves little; misses nothing.",
                "STONE-SENTINEL", "STONE",
                "Technically a lithotroph: it eats one grain of sand a day and will " +
                    "outlast the station it guards. Grows a millimeter a decade. The " +
                    "kind word under its brow is load-bearing.",
                0.15f, 0.4f, WALK
            ),
            Species(
                "SKYDRUM", Color.rgb(90, 160, 255), Color.rgb(255, 255, 160),
                "EMBER DEPOTS",
                "A fledgling of the great storm-birds; each wingbeat is a kettledrum " +
                    "heard from three valleys away. It roosts near charging stations to " +
                    "sip stray current, politely, like tea.",
                "RUMBLY", "Purrs in low thunder when content.",
                "STORM-PERCHER", "PERCH",
                "Its hollow bones are resonance chambers; the thunder is skeletal. " +
                    "Static charge builds in its down and must be sipped off at " +
                    "roosts — the purr is a controlled discharge, felt in your teeth.",
                0.75f, 0.5f, DRIFT
            ),
            Species(
                "PYREBIRD", Color.rgb(255, 110, 60), Color.rgb(255, 230, 120),
                "OVERLOOKS AND SKIES",
                "A phoenix in its adolescent molts: every shed feather flares into a " +
                    "tiny, harmless firework, and it insists each one is a full rebirth " +
                    "deserving of ceremony. Attends every sunset professionally.",
                "DRAMATIC", "Faints beautifully when denied a treat.",
                "EMBER-ROOST", "EMBER",
                "Rebirth is metabolically real but scaled down: it composts and " +
                    "regrows a few cells per flare instead of the whole bird, a " +
                    "juvenile adaptation that trades glory for survival. It resents this.",
                0.65f, 0.35f, FLOAT
            ),
            Species(
                "HORNHOP", Color.rgb(220, 180, 140), Color.rgb(160, 255, 190),
                "STREETS AT LARGE",
                "The jackalope of the tall tales, antlers and all. It sings back at car " +
                    "alarms in perfect key and wins. Cowboys claimed you could catch one " +
                    "with whiskey; a berry works better and is legal everywhere.",
                "BRASH", "Challenges everything to a race, including walls.",
                "WARREN-DIGGER", "BURROW",
                "The antlers are honest jackrabbit keratin, regrown each year and shed " +
                    "as street treasure. Digs shallow brag-warrens it never sleeps in — " +
                    "each one is a challenge flag to the neighborhood.",
                0.85f, 0.65f, HOP
            ),
            Species(
                "SANDSHIFT", Color.rgb(240, 200, 120), Color.rgb(140, 220, 255),
                "ANYWHERE, RARELY",
                "A djinn of the noon wind, retired from the wish business. It grants " +
                    "exactly zero wishes but excellent directions, delivered in a voice " +
                    "like warm sand. Appears where it pleases; the charm merely amuses it.",
                "WRY", "Smiles like it knows the ending. It does.",
                "DUNE-DRIFTER", "BURROW",
                "Its body is a standing dune held upright by will and thermals. It " +
                    "rebuilds itself grain by grain from whatever ground it crosses, " +
                    "which is why no two portraits of a Sandshift agree.",
                0.6f, 0.3f, DRIFT
            ),
            // ---------------- the ecology wing ----------------
            Species(
                "ZEPHYRET", Color.rgb(200, 240, 255), Color.rgb(255, 200, 230),
                "PARKS AND SKIES",
                "A sylph the size of a swallow that flies exactly one hand above the " +
                    "ground, always. Zephyrets slalom fence posts and skim pond surfaces " +
                    "for the joy of the near-miss. One has never, ever crashed. Allegedly.",
                "BREEZY", "Skims your shoelaces at full speed, on purpose.",
                "GROUND-SKIMMER", "BLOOM",
                "Ground-effect flight: its wing vortices push against the earth, so " +
                    "flying low costs half the calories of flying high. Reads grass-bend " +
                    "and flower-sway as a map of the wind it surfs.",
                0.9f, 0.55f, SKIM
            ),
            Species(
                "NIXLET", Color.rgb(80, 190, 230), Color.rgb(180, 255, 220),
                "WELLS AND WATERS",
                "A nixie hatchling with an inner tube of its own ripples. It swims " +
                    "figure-eights in fountains and surfaces only to check whether anyone " +
                    "clapped. River-spirits consider it a promising student of loitering.",
                "SPLASHY", "Surfaces dramatically; expects applause.",
                "DEEP-SWIMMER", "WATER",
                "Its tail fin is a folded second body it grows into over decades. " +
                    "Breathes water and air with equal disdain, and carries a personal " +
                    "ripple — a standing wave it excites for lift, like a liquid wing.",
                0.7f, 0.6f, SWIM
            ),
            Species(
                "MOLDEWARP", Color.rgb(120, 90, 140), Color.rgb(255, 170, 190),
                "PARKS AND TRAILS",
                "The velvet digger of old field-lore, star-nosed and satin-pawed. It " +
                    "surfaces beside picnics with impeccable timing, accepts one crumb, " +
                    "and vanishes. Molehills in perfect circles are its idea of a joke.",
                "SNUG", "Pops up exactly where you weren't looking.",
                "TUNNEL-DIGGER", "BURROW",
                "The star nose is twenty-two touch-tentacles that taste soil in " +
                    "stereo — it navigates the underworld faster than most creatures " +
                    "walk the overworld. Its velvet pile lies flat in both directions, " +
                    "an old digger's trick for reversing in a tight tunnel.",
                0.55f, 0.45f, BURROW
            ),
            // -------- two exceptionally rare, habitat-derived intelligences --------
            Species(
                "SYLVARCH", Color.rgb(72, 184, 92), Color.rgb(244, 204, 92),
                "GLADE — EXCEPTIONALLY RARE",
                "A fox-sized glade thinker with a fan tail, dexterous forepaws, and a " +
                    "branching sensory crown. Sylvarchs arrange acorns into questions, " +
                    "remember every answer, and quietly revise the question when a person surprises them.",
                "INQUISITIVE", "Studies you as carefully as you study it.",
                "GLADE-ARCHIVIST", "THICKET",
                "Temperate forests reward minds that can predict leaf-out, insect pulses, and " +
                    "mast years. Its antler-like crown reads polarized skylight beneath foliage; " +
                    "its corvid-style cache memory became planning, teaching, and symbolic play.",
                0.62f, 0.48f, WALK
            ),
            Species(
                "MISTCROWN", Color.rgb(58, 152, 126), Color.rgb(176, 238, 224),
                "MISTWOOD — EXCEPTIONALLY RARE",
                "An arboreal six-limbed sage draped in moss-soft chromatophores. A Mistcrown " +
                    "listens through bark, samples the fog with crown tendrils, and approaches " +
                    "new things only after considering several possible futures.",
                "SAGACIOUS", "Curiosity wins, but never before a thoughtful pause.",
                "MISTWOOD-CARTOGRAPHER", "PERCH",
                "Old temperate rain forest is three-dimensional and seasonally unpredictable. " +
                    "Prehensile feet, a balancing tail, fungal chemical literacy, and long spatial " +
                    "memory coevolved into cooperative inference and sustainable harvest routes.",
                0.50f, 0.38f, DRIFT
            ),
        )

        const val PRISMKIN = 11
        const val SANDSHIFT = 23
        const val ZEPHYRET = 24
        const val NIXLET = 25
        const val MOLDEWARP = 26
        const val SYLVARCH = 27
        const val MISTCROWN = 28

        /** Each native has a mythic counterpart haunting the same places. */
        private val MYTHIC_ALT = intArrayOf(13, 12, 14, 15, 16, 17, 18, 19, 20, 21, 22)

        /** Which species haunts a POI category; rare wanderers ride the charm. */
        fun forCategory(category: String, seed: Int, charmTier: Int): Int {
            // The forest intelligences are rarer than the mythic wanderers and
            // occur only in the real-place habitats that shaped them.
            val rareRoll = Math.floorMod(seed, 10_000)
            val rareGate = 8 + charmTier.coerceAtLeast(0) * 10  // 0.08% .. 0.58%
            val gladePlace = category.startsWith("leisure=park") ||
                category.startsWith("leisure=garden") || category.startsWith("natural=grassland")
            val mistwoodPlace = category.startsWith("leisure=nature_reserve") ||
                category == "trailhead" || category.startsWith("natural=wood") ||
                category.startsWith("natural=wetland") || category.startsWith("natural=spring")
            if (mistwoodPlace && rareRoll < rareGate) return MISTCROWN
            if (gladePlace && Math.floorMod(seed / 17, 10_000) < rareGate) return SYLVARCH

            val roll = Math.floorMod(seed, 100)
            if (roll < 2 + charmTier * 4) return PRISMKIN
            if (Math.floorMod(seed / 100, 100) < 1 + charmTier * 2) return SANDSHIFT
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
            // The ecology wing haunts by terrain: swimmers to water, diggers to
            // green ground, skimmers to open air anywhere else.
            if (Math.floorMod(seed / 1_000_000, 100) < 12) {
                return when (base) {
                    2 -> NIXLET
                    0, 1 -> MOLDEWARP
                    else -> ZEPHYRET
                }
            }
            // A third of the rest are the mythic counterpart of the place.
            return if (Math.floorMod(seed / 10000, 100) < 33) MYTHIC_ALT[base] else base
        }
    }
}
