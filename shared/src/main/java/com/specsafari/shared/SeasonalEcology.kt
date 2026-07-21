package com.specsafari.shared

/** The nine climate regions in the Den, in the same order as Habitats.BIOMES. */
enum class WorldBiome(val label: String) {
    TUNDRA("tundra"),
    TAIGA("boreal forest"),
    GLADE("temperate seasonal forest"),
    MISTWOOD("temperate rain forest"),
    JUNGLE("tropical rain forest"),
    MONSOON("tropical seasonal forest"),
    DUNES("subtropical desert"),
    SAGE("temperate desert"),
    SCRUB("woodland / shrubland")
}

enum class Season(val label: String) {
    WINTER("winter"), SPRING("spring"), SUMMER("summer"), AUTUMN("autumn")
}

enum class MigrationMode(val label: String) {
    RESIDENT("resident range-shifter"),
    SHORT_DISTANCE("short-distance migrant"),
    CLIMATE_TRACKER("seasonal climate tracker"),
    RAIN_TRACKER("rain-front migrant"),
    ALTITUDINAL("cool-slope migrant"),
    NOMADIC("resource nomad")
}

/** IDs deliberately match Habitats.F_* so the shared behavior stays testable. */
enum class ForageKind(val id: Int, val label: String) {
    BERRY(0, "berries"), FRUIT(1, "canopy fruit"), LICHEN(2, "lichen"),
    MUSHROOM(3, "fungi"), CACTUS(4, "cactus fruit"), SEED(5, "seed heads"),
    AQUATIC(6, "aquatic plants")
}

data class SeasonalProfile(
    /** WINTER, SPRING, SUMMER, AUTUMN target climates. */
    val seasonalBiomes: IntArray,
    val migrationMode: MigrationMode,
    val climatePreference: String,
    val migrationNote: String,
    val forageKinds: Set<ForageKind>,
    val forageNote: String,
    /** Per simulated second; deliberately slow enough to show search, travel and feeding. */
    val hungerRate: Float,
    val migrationDrive: Float
) {
    init {
        require(seasonalBiomes.size == Season.values().size)
        require(seasonalBiomes.all { it in WorldBiome.values().indices })
        require(forageKinds.isNotEmpty())
    }

    fun targetBiome(season: Season): Int = seasonalBiomes[season.ordinal]
    fun accepts(foodKind: Int): Boolean = forageKinds.any { it.id == foodKind }
}

/**
 * Seasonal movement and feeding niches for every creature. These are fictional
 * organisms, but the rules follow real ecological constraints: energetic cost,
 * water dependence, mast/fruit seasons, rain fronts, thermal refuges and memory.
 */
object SeasonalEcology {
    private fun p(
        winter: WorldBiome, spring: WorldBiome, summer: WorldBiome, autumn: WorldBiome,
        mode: MigrationMode, climate: String, migration: String,
        vararg forage: ForageKind,
        forageNote: String,
        hunger: Float = .0032f,
        drive: Float = .65f
    ) = SeasonalProfile(
        intArrayOf(winter.ordinal, spring.ordinal, summer.ordinal, autumn.ordinal),
        mode, climate, migration, forage.toSet(), forageNote, hunger, drive
    )

    val ALL: List<SeasonalProfile> = listOf(
        p(WorldBiome.GLADE, WorldBiome.GLADE, WorldBiome.MISTWOOD, WorldBiome.GLADE,
            MigrationMode.SHORT_DISTANCE, "mild, moist understory with seasonal sunflecks",
            "Tracks spring shoots and autumn mast; retreats into moist shade during summer heat.",
            ForageKind.BERRY, ForageKind.SEED, ForageKind.FRUIT, ForageKind.MUSHROOM,
            forageNote = "Angles its solar leaf while cropping shoots and windfall fruit.", hunger = .0025f),
        p(WorldBiome.TAIGA, WorldBiome.GLADE, WorldBiome.MISTWOOD, WorldBiome.GLADE,
            MigrationMode.CLIMATE_TRACKER, "cool forest edge with dense escape cover",
            "Follows the cool-green edge northward in summer and the berry crop back in autumn.",
            ForageKind.BERRY, ForageKind.SEED, ForageKind.MUSHROOM,
            forageNote = "Scent-maps berry patches, then digs for fungi with flexible bramble paws.", hunger = .0045f),
        p(WorldBiome.MISTWOOD, WorldBiome.MISTWOOD, WorldBiome.JUNGLE, WorldBiome.MONSOON,
            MigrationMode.RAIN_TRACKER, "ice-free shallow water and frequent rain",
            "Moves between connected pools behind rain fronts rather than crossing dry ground.",
            ForageKind.AQUATIC, ForageKind.MUSHROOM, ForageKind.FRUIT,
            forageNote = "Grazes algal films and strains fruit sugars from shared water.", hunger = .0034f),
        p(WorldBiome.SCRUB, WorldBiome.MONSOON, WorldBiome.DUNES, WorldBiome.SCRUB,
            MigrationMode.CLIMATE_TRACKER, "warm, dry refuges with safe radiant heat",
            "Shifts with warm fronts and returns to fire-adapted scrub before winter rain.",
            ForageKind.SEED, ForageKind.CACTUS, ForageKind.FRUIT,
            forageNote = "Roasts hard seeds against its ember sac before swallowing them.", hunger = .0038f),
        p(WorldBiome.GLADE, WorldBiome.GLADE, WorldBiome.GLADE, WorldBiome.GLADE,
            MigrationMode.RESIDENT, "quiet temperate canopy close to a defensible roost",
            "Keeps a tiny year-round territory; only its reading and insect routes move seasonally.",
            ForageKind.MUSHROOM, ForageKind.SEED,
            forageNote = "Gleans paper mites, starch dust and small fungi beneath its roost.", hunger = .0022f, drive = .25f),
        p(WorldBiome.MISTWOOD, WorldBiome.GLADE, WorldBiome.JUNGLE, WorldBiome.MISTWOOD,
            MigrationMode.CLIMATE_TRACKER, "humid, flower-rich air with calm twilight",
            "Follows successive bloom waves while avoiding exposed storm crossings.",
            ForageKind.FRUIT, ForageKind.BERRY,
            forageNote = "Hovers at fruit vapor and luminous flowers without landing.", hunger = .0030f),
        p(WorldBiome.SAGE, WorldBiome.GLADE, WorldBiome.MONSOON, WorldBiome.GLADE,
            MigrationMode.NOMADIC, "open ground with visible seed and mineral glints",
            "Remembers boom crops and revisits caches when the same season returns.",
            ForageKind.SEED, ForageKind.BERRY, ForageKind.FRUIT,
            forageNote = "Tests, caches and recovers seeds by landmark memory.", hunger = .0036f),
        p(WorldBiome.SAGE, WorldBiome.SAGE, WorldBiome.DUNES, WorldBiome.SAGE,
            MigrationMode.RESIDENT, "dry mineral ground that carries vibration",
            "Patrol loops contract in winter and expand onto sun-warmed stone in summer.",
            ForageKind.LICHEN, ForageKind.SEED, ForageKind.MUSHROOM,
            forageNote = "Scrapes iron-rich lichen and tests substrate grains magnetically.", hunger = .0024f, drive = .30f),
        p(WorldBiome.SCRUB, WorldBiome.MONSOON, WorldBiome.MONSOON, WorldBiome.SCRUB,
            MigrationMode.RAIN_TRACKER, "warm storm margins without standing deep water",
            "Runs along storm fronts, stopping before flooded ground and following electrical gradients.",
            ForageKind.SEED, ForageKind.FRUIT,
            forageNote = "Selects charged seeds and rejects waterlogged ones after a whisker test.", hunger = .0048f),
        p(WorldBiome.SCRUB, WorldBiome.GLADE, WorldBiome.TAIGA, WorldBiome.GLADE,
            MigrationMode.ALTITUDINAL, "open, windy air near a cool thermal boundary",
            "Climbs toward cool summer air and descends with autumn's weakening thermals.",
            ForageKind.FRUIT, ForageKind.SEED, ForageKind.BERRY,
            forageNote = "Takes canopy fruit and aerial prey analogues on the wing.", hunger = .0045f),
        p(WorldBiome.MISTWOOD, WorldBiome.GLADE, WorldBiome.MISTWOOD, WorldBiome.GLADE,
            MigrationMode.SHORT_DISTANCE, "dim, structurally complex forest edge",
            "Moves between leaf-on ambush cover and rain-dark summer forest.",
            ForageKind.BERRY, ForageKind.MUSHROOM,
            forageNote = "Samples fallen fruit and scent-marks productive fungal logs.", hunger = .0028f),
        p(WorldBiome.SAGE, WorldBiome.SAGE, WorldBiome.DUNES, WorldBiome.SAGE,
            MigrationMode.NOMADIC, "clear, dark air over mineral ground",
            "Appears where refraction is stable; its route is episodic rather than calendar-regular.",
            ForageKind.LICHEN, ForageKind.CACTUS, ForageKind.SEED,
            forageNote = "Absorbs trace photons from pale lichen and crystalline fruit.", hunger = .0016f, drive = .35f),
        p(WorldBiome.MISTWOOD, WorldBiome.MISTWOOD, WorldBiome.JUNGLE, WorldBiome.MISTWOOD,
            MigrationMode.RAIN_TRACKER, "foggy, still air rich in volatile organic cues",
            "Drifts with fog banks and shelters before hard wind disperses its body.",
            ForageKind.MUSHROOM, ForageKind.FRUIT,
            forageNote = "Draws marsh gas and fruit vapor through its lantern membrane.", hunger = .0022f),
        p(WorldBiome.SAGE, WorldBiome.GLADE, WorldBiome.TAIGA, WorldBiome.GLADE,
            MigrationMode.CLIMATE_TRACKER, "cool open cover with abundant low browse",
            "Follows new shoots upslope, then returns for autumn seed and berry mast.",
            ForageKind.BERRY, ForageKind.SEED, ForageKind.LICHEN,
            forageNote = "Clips shoots quickly, pausing often to scan and alarm-thump.", hunger = .0040f),
        p(WorldBiome.MISTWOOD, WorldBiome.MONSOON, WorldBiome.JUNGLE, WorldBiome.MISTWOOD,
            MigrationMode.RAIN_TRACKER, "warm connected wetlands and soft banks",
            "Travels through water corridors with seasonal rain, never across a dry desert gap.",
            ForageKind.AQUATIC, ForageKind.FRUIT,
            forageNote = "Browses reeds and submerged growth, then noses down windfall fruit.", hunger = .0044f),
        p(WorldBiome.GLADE, WorldBiome.GLADE, WorldBiome.MISTWOOD, WorldBiome.GLADE,
            MigrationMode.RESIDENT, "sheltered temperate edge near warmth and cover",
            "Stays close to a hearth-like refuge while shifting nightly gleaning paths.",
            ForageKind.BERRY, ForageKind.MUSHROOM, ForageKind.SEED,
            forageNote = "Gleans crumbs, berries and fungi after larger residents leave.", hunger = .0030f, drive = .25f),
        p(WorldBiome.MISTWOOD, WorldBiome.MISTWOOD, WorldBiome.JUNGLE, WorldBiome.MISTWOOD,
            MigrationMode.SHORT_DISTANCE, "quiet humid forest with fragrant fruit",
            "Follows night-blooming pollen and returns to the cool rain forest to rest.",
            ForageKind.FRUIT, ForageKind.MUSHROOM,
            forageNote = "Browses aromatic fruit and fungal caps with its flexible trunk.", hunger = .0020f),
        p(WorldBiome.SCRUB, WorldBiome.GLADE, WorldBiome.MISTWOOD, WorldBiome.GLADE,
            MigrationMode.CLIMATE_TRACKER, "productive edge habitat with many scent lanes",
            "Tracks rodents-of-myth no longer; now follows fruit and insect pulses along forest edges.",
            ForageKind.BERRY, ForageKind.FRUIT, ForageKind.MUSHROOM,
            forageNote = "Triangulates ripe fruit by scent, then checks every nearby novelty.", hunger = .0038f),
        p(WorldBiome.TAIGA, WorldBiome.GLADE, WorldBiome.SAGE, WorldBiome.TAIGA,
            MigrationMode.CLIMATE_TRACKER, "cool mineral barrens with sparse browse",
            "Moves between lichen ground and seasonal berry mast while guarding its mineral organ.",
            ForageKind.LICHEN, ForageKind.BERRY,
            forageNote = "Scrapes lichen and selects mineral-rich berries one at a time.", hunger = .0028f),
        p(WorldBiome.SAGE, WorldBiome.SAGE, WorldBiome.DUNES, WorldBiome.SAGE,
            MigrationMode.RESIDENT, "bare, vibration-conducting mineral substrate",
            "Its sentinel circuit expands toward hot stone in summer but never becomes a true migration.",
            ForageKind.LICHEN, ForageKind.MUSHROOM,
            forageNote = "Consumes a few mineral and fungal grains, then resumes watch.", hunger = .0012f, drive = .15f),
        p(WorldBiome.SCRUB, WorldBiome.MONSOON, WorldBiome.MONSOON, WorldBiome.GLADE,
            MigrationMode.RAIN_TRACKER, "windy storm fronts with a safe high roost",
            "Rides thunder systems poleward, then follows quieter autumn fronts back.",
            ForageKind.FRUIT, ForageKind.SEED, ForageKind.BERRY,
            forageNote = "Catches airborne food and plucks high fruit between storm cells.", hunger = .0048f),
        p(WorldBiome.SCRUB, WorldBiome.MONSOON, WorldBiome.DUNES, WorldBiome.SCRUB,
            MigrationMode.CLIMATE_TRACKER, "hot, open country with dry roosts",
            "Tracks the warmest safe sunset belt and withdraws from sustained rain.",
            ForageKind.SEED, ForageKind.CACTUS, ForageKind.FRUIT,
            forageNote = "Toasts seed heads and opens cactus fruit with a controlled flare.", hunger = .0042f),
        p(WorldBiome.SAGE, WorldBiome.GLADE, WorldBiome.TAIGA, WorldBiome.GLADE,
            MigrationMode.CLIMATE_TRACKER, "cool semi-open browse beside shallow refuge soil",
            "Follows grass flushes uphill and returns for bark and berries after leaf fall.",
            ForageKind.SEED, ForageKind.BERRY, ForageKind.LICHEN,
            forageNote = "Browses quickly in a group while one animal scans.", hunger = .0046f),
        p(WorldBiome.DUNES, WorldBiome.DUNES, WorldBiome.DUNES, WorldBiome.DUNES,
            MigrationMode.NOMADIC, "hot dry ground with strong noon thermals",
            "Wanders among dune patches after wind exposes seed and cactus fruit; rain halts movement.",
            ForageKind.CACTUS, ForageKind.SEED,
            forageNote = "Sifts edible seed from sand and draws moisture from cactus fruit.", hunger = .0018f, drive = .45f),
        p(WorldBiome.SCRUB, WorldBiome.GLADE, WorldBiome.TAIGA, WorldBiome.GLADE,
            MigrationMode.ALTITUDINAL, "open low vegetation under smooth wind",
            "Follows gentle summer air upslope and autumn insects back to the glades.",
            ForageKind.FRUIT, ForageKind.BERRY,
            forageNote = "Skims pollen and fruit droplets without breaking ground effect.", hunger = .0044f),
        p(WorldBiome.MISTWOOD, WorldBiome.MONSOON, WorldBiome.JUNGLE, WorldBiome.MISTWOOD,
            MigrationMode.RAIN_TRACKER, "connected warm water with productive shallows",
            "Swims behind seasonal rains through linked pools; refuses overland shortcuts.",
            ForageKind.AQUATIC, ForageKind.FRUIT,
            forageNote = "Grazes algae and plankton analogues by pressure-sensing each patch.", hunger = .0038f),
        p(WorldBiome.GLADE, WorldBiome.MISTWOOD, WorldBiome.MISTWOOD, WorldBiome.GLADE,
            MigrationMode.SHORT_DISTANCE, "damp friable soil below forest litter",
            "Tracks soil moisture between seasonal forest and rain forest entirely underground.",
            ForageKind.MUSHROOM, ForageKind.LICHEN,
            forageNote = "Locates roots and fungal bodies by touch before surfacing to feed.", hunger = .0035f),
        p(WorldBiome.GLADE, WorldBiome.GLADE, WorldBiome.GLADE, WorldBiome.GLADE,
            MigrationMode.RESIDENT, "temperate seasonal forest with a predictable mosaic of mast and cover",
            "Remains faithful to one glade, but moves caches and sleeping courts as leaf-out and mast shift.",
            ForageKind.BERRY, ForageKind.MUSHROOM, ForageKind.SEED, ForageKind.FRUIT,
            forageNote = "Plans multi-stage forage routes, tests ripeness, and caches surplus by future need.", hunger = .0026f, drive = .20f),
        p(WorldBiome.MISTWOOD, WorldBiome.MISTWOOD, WorldBiome.MISTWOOD, WorldBiome.MISTWOOD,
            MigrationMode.RESIDENT, "cool saturated old-growth rain forest with persistent canopy fog",
            "Defends a vast mistwood memory-range; seasonal movement is vertical, from creek fungi to canopy fruit.",
            ForageKind.MUSHROOM, ForageKind.BERRY, ForageKind.FRUIT,
            forageNote = "Reads fungal networks, remembers fruiting trees, and harvests without exhausting a patch.", hunger = .0024f, drive = .18f)
    )

    init {
        require(ALL.size == Species.ALL.size) { "Seasonal roster must cover every species" }
    }

    fun of(species: Int): SeasonalProfile = ALL[species.coerceIn(0, ALL.lastIndex)]

    fun currentSeason(monthOneBased: Int, southernHemisphere: Boolean = false): Season {
        val north = when (monthOneBased.coerceIn(1, 12)) {
            12, 1, 2 -> Season.WINTER
            3, 4, 5 -> Season.SPRING
            6, 7, 8 -> Season.SUMMER
            else -> Season.AUTUMN
        }
        if (!southernHemisphere) return north
        return when (north) {
            Season.WINTER -> Season.SUMMER
            Season.SPRING -> Season.AUTUMN
            Season.SUMMER -> Season.WINTER
            Season.AUTUMN -> Season.SPRING
        }
    }

    fun targetBiome(species: Int, season: Season): Int = of(species).targetBiome(season)

    /** Relative crop value (0..1), used when choosing among reachable food patches. */
    fun foodAvailability(foodKind: Int, season: Season, biome: Int): Float {
        val b = biome.coerceIn(0, WorldBiome.values().lastIndex)
        val seasonal = when (foodKind) {
            ForageKind.BERRY.id -> when (season) {
                Season.SUMMER -> 1f; Season.AUTUMN -> .9f; Season.SPRING -> .55f; Season.WINTER -> .25f
            }
            ForageKind.FRUIT.id -> when (season) {
                Season.SUMMER -> 1f; Season.AUTUMN -> .8f; Season.SPRING -> .45f; Season.WINTER -> .18f
            }
            ForageKind.LICHEN.id -> when (season) {
                Season.WINTER -> 1f; Season.AUTUMN -> .85f; else -> .65f
            }
            ForageKind.MUSHROOM.id -> when (season) {
                Season.AUTUMN -> 1f; Season.SPRING -> .85f; Season.SUMMER -> .55f; Season.WINTER -> .35f
            }
            ForageKind.CACTUS.id -> if (season == Season.SUMMER) 1f else .65f
            ForageKind.SEED.id -> when (season) {
                Season.AUTUMN -> 1f; Season.SUMMER -> .85f; Season.WINTER -> .55f; Season.SPRING -> .45f
            }
            ForageKind.AQUATIC.id -> if (season == Season.WINTER) .45f else .9f
            else -> 0f
        }
        val climate = when (foodKind) {
            ForageKind.AQUATIC.id -> if (b in intArrayOf(WorldBiome.TUNDRA.ordinal, WorldBiome.TAIGA.ordinal,
                WorldBiome.GLADE.ordinal, WorldBiome.MISTWOOD.ordinal, WorldBiome.JUNGLE.ordinal,
                WorldBiome.MONSOON.ordinal)) 1f else .08f
            ForageKind.CACTUS.id -> if (b == WorldBiome.DUNES.ordinal) 1f else .12f
            ForageKind.LICHEN.id -> if (b <= WorldBiome.TAIGA.ordinal || b == WorldBiome.SAGE.ordinal) 1f else .55f
            ForageKind.MUSHROOM.id -> if (b in WorldBiome.TAIGA.ordinal..WorldBiome.JUNGLE.ordinal) 1f else .25f
            ForageKind.FRUIT.id -> if (b in WorldBiome.GLADE.ordinal..WorldBiome.MONSOON.ordinal) 1f else .35f
            else -> 1f
        }
        return (seasonal * climate).coerceIn(0f, 1f)
    }
}
