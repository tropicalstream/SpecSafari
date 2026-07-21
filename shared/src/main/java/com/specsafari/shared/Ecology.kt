package com.specsafari.shared

import kotlin.math.abs
import kotlin.math.cos

/** The sensory channel that contributes most to long-range detection. */
enum class SenseKind(val label: String) {
    VISION("vision"), HEARING("hearing"), SCENT("scent"), VIBRATION("substrate vibration"),
    WATER_PRESSURE("water pressure"), THERMAL("thermal contrast"), ELECTRIC("electric fields"),
    LIGHT("light gradients"), AIRFLOW("airflow"), CHEMICAL("chemical cues")
}

enum class ActivityPattern(val label: String) {
    DIURNAL("day-active"), NOCTURNAL("night-active"),
    CREPUSCULAR("dawn/dusk-active"), CATHEMERAL("active in short bouts day and night")
}

enum class SocialSystem(val label: String) {
    SOLITARY("solitary"), PAIR("pair-living"), PACK("pack-social"), FLOCK("flocking"),
    COLONY("colonial"), SCHOOL("schooling"), COMMENSAL("human-commensal"),
    TERRITORIAL("territorial"), SENTINEL("sentinel-social")
}

enum class EcoGuild {
    BLOOM, THICKET, WATER, EMBER, SCHOLAR, AERIAL, MINERAL, ELECTRIC,
    STALKER, VOID, HEARTH, STONE, BURROW
}

enum class PreferredMedium { GROUND, AIR, WATER, AMPHIBIOUS, UNDERGROUND, PHASED }

/**
 * A gameplay-scale phenotype for one fictional species. Distances are Den metres:
 * internally consistent behavioral ranges, not measurements claimed for real animals.
 */
data class EcologyProfile(
    val morphology: String,
    val sensoryEcology: String,
    val socialEcology: String,
    val diet: String,
    val activity: ActivityPattern,
    val primarySense: SenseKind,
    val socialSystem: SocialSystem,
    val guild: EcoGuild,
    val medium: PreferredMedium,
    val bodyRadius: Float,
    val signalStrength: Float,
    val detectionRange: Float,
    val fieldOfViewDeg: Float,
    val vision: Float,
    val hearing: Float,
    val scent: Float,
    val vibration: Float,
    val lowLightAcuity: Float,
    val socialRange: Float,
    val personalSpace: Float,
    val groupAffinity: Float,
    val territoriality: Float,
    val alarmSensitivity: Float,
    val threatSignal: Float,
    val preySensitivity: Float,
    val playfulness: Float
)

data class SensingContext(
    val daylight: Float = 1f,
    val facingDot: Float = 1f,
    val targetSpeed: Float = 0f,
    val homeHabitat: Boolean = false,
    val observerInWater: Boolean = false,
    val targetInWater: Boolean = false,
    val observerUnderground: Boolean = false,
    val targetUnderground: Boolean = false,
    val onStone: Boolean = false,
    val fog: Boolean = false,
    val rain: Boolean = false,
    val wind: Float = 0f,
    val visualOccluded: Boolean = false
)

enum class SocialAction { UNAWARE, OBSERVE, APPROACH, CONTACT, AVOID, ALARM }

data class SocialResponse(
    val action: SocialAction,
    val strength: Float,
    val detectionDistance: Float,
    val preferredDistance: Float
)

/** Structured morphology and behavioral ecology for every Dex entry. */
object EcologyModel {

    private fun p(
        morphology: String, senses: String, society: String, diet: String,
        activity: ActivityPattern, primary: SenseKind, social: SocialSystem,
        guild: EcoGuild, medium: PreferredMedium,
        radius: Float, signal: Float, detection: Float, fov: Float,
        vision: Float, hearing: Float, scent: Float, vibration: Float, lowLight: Float,
        socialRange: Float, spacing: Float, group: Float, territory: Float,
        alarm: Float, threat: Float, prey: Float, play: Float
    ) = EcologyProfile(
        morphology, senses, society, diet, activity, primary, social, guild, medium,
        radius, signal, detection, fov, vision, hearing, scent, vibration, lowLight,
        socialRange, spacing, group, territory, alarm, threat, prey, play
    )

    val ALL: List<EcologyProfile> = listOf(
        p("Palm-high leafy quadruped; broad feet couple softly to soil.",
            "Reads light gradients and scent to 8 m; feels heavy footsteps at close range.",
            "Loose bloom colonies; mutualistic with Luxmoth and Moonhare, wary of flame and voltage.",
            "Tender shoots, fruit sugars, and mineral-rich berries.",
            ActivityPattern.DIURNAL, SenseKind.LIGHT, SocialSystem.COLONY, EcoGuild.BLOOM, PreferredMedium.GROUND,
            .20f, .55f, 8f, 310f, .85f, .45f, .75f, .35f, .55f, 4.5f, .55f, .85f, .08f, .62f, .05f, .72f, .45f),
        p("Small bramble-canid with flexible warning spines and scenting muzzle.",
            "Tracks scent to 14 m and footfall or voice to 12 m; wind changes the scent picture.",
            "Pack play and shared alarm; excited spines make strangers respect its personal space.",
            "Windfall fruit, honey, and protein-rich seed pods.",
            ActivityPattern.CREPUSCULAR, SenseKind.SCENT, SocialSystem.PACK, EcoGuild.THICKET, PreferredMedium.GROUND,
            .28f, .72f, 14f, 235f, .72f, .90f, 1f, .45f, .82f, 7f, .82f, .88f, .35f, .88f, .38f, .48f, .80f),
        p("Low, gelatinous amphibian whose whole skin acts as a pressure membrane.",
            "Detects shared-water ripples to 14 m but resolves shapes only about 4 m through air.",
            "Schools with Nixlet and follows Kelpling wakes; heat and electrical discharge trigger retreat.",
            "Algae films, pollen, and dissolved fruit sugars.",
            ActivityPattern.CATHEMERAL, SenseKind.WATER_PRESSURE, SocialSystem.SCHOOL, EcoGuild.WATER, PreferredMedium.WATER,
            .25f, .46f, 14f, 330f, .42f, .38f, .35f, .92f, .62f, 6f, .58f, .90f, .04f, .82f, .03f, .82f, .42f),
        p("Warm-bodied salamander analogue with insulated ember sac and heat-shedding crest.",
            "Thermal contrast carries to 10 m around a hearth; hearing provides a rain-proof fallback.",
            "Hearth commensal; settles near Puckle and Pyrebird but keeps away from aquatic bodies.",
            "Charred seeds, honey, and trace charcoal.",
            ActivityPattern.CREPUSCULAR, SenseKind.THERMAL, SocialSystem.COMMENSAL, EcoGuild.EMBER, PreferredMedium.GROUND,
            .23f, .68f, 10f, 250f, .62f, .58f, .48f, .25f, .82f, 5f, .62f, .72f, .18f, .52f, .20f, .26f, .55f),
        p("Long-bodied, fine-eyed roost lizard with page-thin stabilizing fins.",
            "Fine binocular vision to 9 m; quiet sounds carry about 5 m, loud motion overloads it.",
            "Defends a Tome Nook; tolerates Dreambaku and Clayward, withdraws from noisy racers.",
            "Paper mites, starch dust, and occasional pudding.",
            ActivityPattern.DIURNAL, SenseKind.VISION, SocialSystem.TERRITORIAL, EcoGuild.SCHOLAR, PreferredMedium.AIR,
            .22f, .48f, 9f, 205f, .98f, .52f, .28f, .22f, .55f, 4.5f, .66f, .32f, .78f, .45f, .12f, .40f, .18f),
        p("Feather-light moth form with broad antennae and reflective low-light eyes.",
            "Finds light sources to 15 m at dusk; rain and strong wind mask antenna and wing cues.",
            "Loose night swarms; pollinates Leafling blooms and gathers near Wisplet, never open flame.",
            "Nectar, luminous pollen, and fruit vapor.",
            ActivityPattern.NOCTURNAL, SenseKind.LIGHT, SocialSystem.FLOCK, EcoGuild.BLOOM, PreferredMedium.AIR,
            .16f, .50f, 15f, 300f, .90f, .25f, .62f, .18f, 1f, 7f, .48f, .82f, .04f, .76f, .02f, .82f, .52f),
        p("Compact corvid-like hopper with metallic display plates and grasping winglets.",
            "Long vision notices glints to 10 m; hearing checks whether a shiny object is moving.",
            "Resource flocking and opportunistic inspection; Gemback treats it as a gem thief.",
            "Seeds, sweet crumbs, and harmless metallic salts.",
            ActivityPattern.DIURNAL, SenseKind.VISION, SocialSystem.FLOCK, EcoGuild.MINERAL, PreferredMedium.AIR,
            .21f, .70f, 10f, 285f, .92f, .55f, .35f, .25f, .52f, 6f, .60f, .76f, .16f, .68f, .18f, .35f, .72f),
        p("Dense iron-furred quadruped with magnetite pads that couple to hard ground.",
            "Reads substrate vibration to 16 m on stone and scheduled machinery by hearing to 8 m.",
            "Patrols stone lanes; recognizes Clayward as another sentinel and avoids soft, noisy crowds.",
            "Iron-bearing grit, mineral berries, and static charge.",
            ActivityPattern.CATHEMERAL, SenseKind.VIBRATION, SocialSystem.SENTINEL, EcoGuild.STONE, PreferredMedium.GROUND,
            .27f, .78f, 16f, 220f, .52f, .72f, .38f, 1f, .72f, 6f, .82f, .52f, .62f, .78f, .42f, .18f, .24f),
        p("Tiny, light-footed electric rodent with insulated tail and discharge whiskers.",
            "Detects changing electric fields to 14 m; thunder boosts confidence while cover limits rain exposure.",
            "Associates with Skydrum and ember zones; water-bodied creatures avoid close discharges.",
            "Sugars, charged seeds, and stray current.",
            ActivityPattern.CATHEMERAL, SenseKind.ELECTRIC, SocialSystem.COLONY, EcoGuild.ELECTRIC, PreferredMedium.GROUND,
            .18f, .82f, 14f, 285f, .60f, .70f, .28f, .30f, .72f, 6f, .58f, .74f, .18f, .80f, .34f, .44f, .86f),
        p("Long-winged, hollow-boned air surfer with high-set panoramic eyes.",
            "Open-air sight reaches 18 m and calls carry 10 m; fog sharply shortens its visual horizon.",
            "Flocks and competes for high perches; aerial play is mainly with Zephyret.",
            "Airborne insects, high fruit, and condensed mist.",
            ActivityPattern.DIURNAL, SenseKind.VISION, SocialSystem.FLOCK, EcoGuild.AERIAL, PreferredMedium.AIR,
            .30f, .90f, 18f, 300f, 1f, .82f, .20f, .15f, .62f, 9f, .92f, .84f, .38f, .86f, .42f, .28f, .90f),
        p("Slender catlike stalker with light-absorbing coat, forward ears, and padded feet.",
            "Low-light vision to 12 m, hearing to 14 m, scent to 10 m; frontal gaze is conspicuous.",
            "Solitary ritual stalker; Moonhare, Hornhop, and small fliers read its outline as predatory.",
            "No longer hunts; samples fruit, scent marks, and attention on its own terms.",
            ActivityPattern.NOCTURNAL, SenseKind.HEARING, SocialSystem.SOLITARY, EcoGuild.STALKER, PreferredMedium.GROUND,
            .29f, .62f, 14f, 235f, .92f, 1f, .78f, .35f, 1f, 6f, 1.05f, .15f, .66f, .50f, .72f, .15f, .48f),
        p("Faceted, partly nonlocal body with no fixed front and light-dependent coherence.",
            "Reads gaze and photic disturbance to 16 m; dark stillness improves coherence, glare reduces it.",
            "Usually solitary; cautiously coexists with Sandshift and phases away from bright emitters.",
            "Trace photons and rare mineral sugars.",
            ActivityPattern.NOCTURNAL, SenseKind.LIGHT, SocialSystem.SOLITARY, EcoGuild.VOID, PreferredMedium.PHASED,
            .25f, .72f, 16f, 360f, .98f, .22f, .15f, .32f, 1f, 5f, .95f, .12f, .28f, .44f, .20f, .46f, .12f),
        p("Buoyant cold-plasma lantern with a small gas-bladder core and no load-bearing limbs.",
            "Reads motion and light to 10 m plus air chemistry to 8 m; fog helps, hard wind disperses cues.",
            "Loose associations with Luxmoth; curious followers trail its safe path through dim ground.",
            "Shadow, marsh gas, and diffuse night light.",
            ActivityPattern.NOCTURNAL, SenseKind.LIGHT, SocialSystem.FLOCK, EcoGuild.VOID, PreferredMedium.AIR,
            .17f, .58f, 10f, 360f, .82f, .28f, .70f, .12f, .95f, 5.5f, .58f, .68f, .06f, .50f, .06f, .36f, .64f),
        p("Long-eared lagomorph with panoramic eyes, oversized pinnae, and explosive hindlimbs.",
            "Hearing reaches 18 m and near-panoramic vision 14 m; strongest around dusk and night.",
            "Forages in loose groups and alarm-thumps; cat/fox silhouettes amplify group vigilance.",
            "Night blooms, tender shoots, berries, and ceremonial treats.",
            ActivityPattern.CREPUSCULAR, SenseKind.HEARING, SocialSystem.COLONY, EcoGuild.BLOOM, PreferredMedium.GROUND,
            .25f, .66f, 18f, 335f, .90f, 1f, .62f, .72f, .95f, 8f, .78f, .86f, .10f, 1f, .03f, 1f, .62f),
        p("Foal-sized amphibious grazer with lateral-line mane, gills, lungs, and broad hooves.",
            "Water pressure reaches 14 m, hearing 10 m, scent 8 m; rain joins its sensory worlds.",
            "Small amphibious herd; shares wakes with Puddlim and Nixlet, avoids flame and voltage.",
            "Freshwater algae, fruit, reeds, and mineral salts.",
            ActivityPattern.CATHEMERAL, SenseKind.WATER_PRESSURE, SocialSystem.PACK, EcoGuild.WATER, PreferredMedium.AMPHIBIOUS,
            .38f, .90f, 14f, 300f, .65f, .82f, .72f, .92f, .72f, 8f, 1.05f, .86f, .28f, .88f, .32f, .42f, .72f),
        p("Palm-sized bipedal hearth commensal with grasping paws and glare-sensitive eyes.",
            "Low-light vision and hearing resolve household movement to 8 m; direct attention feels intrusive.",
            "Human commensal that tidies around Emberling; gentle proximity can settle timid neighbors.",
            "Crumbs, berries left as gifts, and waste heat.",
            ActivityPattern.NOCTURNAL, SenseKind.HEARING, SocialSystem.COMMENSAL, EcoGuild.HEARTH, PreferredMedium.GROUND,
            .19f, .42f, 8f, 250f, .70f, .72f, .55f, .28f, .90f, 5f, .52f, .78f, .18f, .48f, .02f, .22f, .40f),
        p("Tapir-like, slow-bodied browser with flexible trunk and broad chemical epithelium.",
            "Chemosensory anxiety cues carry to 12 m; hearing substitutes for weak fine vision.",
            "Quiet, affiliative sleeper; reduces arousal near Bookwyrm and Puckle rather than initiating chases.",
            "Stress metabolites, fragrant fruit, and dream-rich pollen.",
            ActivityPattern.NOCTURNAL, SenseKind.CHEMICAL, SocialSystem.SOLITARY, EcoGuild.SCHOLAR, PreferredMedium.GROUND,
            .36f, .75f, 12f, 220f, .38f, .62f, .92f, .40f, .68f, 5f, 1.08f, .46f, .08f, .42f, .08f, .18f, .12f),
        p("Lean two-tailed canid with mobile ears, scenting muzzle, and heat-sink tails.",
            "Hearing reaches 15 m, scent 14 m, and low-light vision 11 m; excels at edge detection.",
            "Solitary or paired trickster; playful stalking is reciprocal only with confident neighbors.",
            "Fruit, insects, foxfire byproducts, and rare sweets.",
            ActivityPattern.CREPUSCULAR, SenseKind.HEARING, SocialSystem.PAIR, EcoGuild.STALKER, PreferredMedium.GROUND,
            .31f, .74f, 15f, 245f, .88f, 1f, .94f, .35f, .94f, 7f, 1f, .38f, .58f, .62f, .68f, .22f, .76f),
        p("Low armored herbivore with mineral crystal organ and leaf-carrying forelimbs.",
            "Hearing to 12 m, scent 8 m, peripheral sight 7 m; freezes and covers the gem when watched.",
            "Bashful loose groups; seeks mineral beds and avoids Coinix kleptoparasitic inspection.",
            "Lichens, trace minerals, leaves, and berries.",
            ActivityPattern.CREPUSCULAR, SenseKind.HEARING, SocialSystem.COLONY, EcoGuild.MINERAL, PreferredMedium.GROUND,
            .30f, .82f, 12f, 315f, .62f, .90f, .70f, .52f, .76f, 5.5f, .84f, .66f, .12f, .82f, .06f, .82f, .28f),
        p("Dense stone sentinel with tiny stride, high inertia, and vibration-conducting feet.",
            "Stone vibration reaches 18 m while vision resolves only about 5 m; never truly off duty.",
            "Fearless sentinel that stands between timid residents and disturbance; recognizes Ferrokit patrols.",
            "One grain of mineral substrate per day.",
            ActivityPattern.CATHEMERAL, SenseKind.VIBRATION, SocialSystem.SENTINEL, EcoGuild.STONE, PreferredMedium.GROUND,
            .34f, 1f, 18f, 210f, .38f, .58f, .18f, 1f, .62f, 7f, 1.10f, .58f, .72f, .72f, .25f, .02f, .05f),
        p("Large hollow-boned storm bird with resonant skeleton and charge-holding down.",
            "Open sight to 18 m; infrasound and changing electric fields carry roughly 16 m.",
            "Perch flock associated with Voltling; competes with Gustril and can alarm distant listeners.",
            "Airborne prey analogues, condensed charge, and fruit.",
            ActivityPattern.CATHEMERAL, SenseKind.ELECTRIC, SocialSystem.FLOCK, EcoGuild.AERIAL, PreferredMedium.AIR,
            .40f, 1f, 18f, 295f, .95f, .92f, .18f, .36f, .72f, 10f, 1.15f, .82f, .48f, .94f, .62f, .30f, .66f),
        p("Medium firebird with heat-shedding plumage and broad display wings.",
            "Vision reaches 14 m and thermal contrast 10 m; strongest near sunset, rain suppresses display.",
            "Territorial display bird; tolerates Emberling while water-bodied residents maintain distance.",
            "Seeds, fruit, controlled flame byproducts, and ceremony.",
            ActivityPattern.CREPUSCULAR, SenseKind.VISION, SocialSystem.TERRITORIAL, EcoGuild.EMBER, PreferredMedium.AIR,
            .32f, .98f, 14f, 285f, .96f, .70f, .20f, .22f, .78f, 7f, 1f, .42f, .78f, .76f, .42f, .35f, .62f),
        p("Antlered jackrabbit with panoramic eyes, long ears, and short refuge-digging claws.",
            "Hearing reaches 18 m and peripheral sight 15 m; alarm is strongest around dawn and dusk.",
            "Loose racing groups use alarm stomps and antler displays; cat/fox shapes prompt early escape.",
            "Grasses, bark, berries, and boastfully accepted treats.",
            ActivityPattern.CREPUSCULAR, SenseKind.HEARING, SocialSystem.COLONY, EcoGuild.BURROW, PreferredMedium.GROUND,
            .28f, .78f, 18f, 340f, .92f, 1f, .68f, .86f, .92f, 9f, .92f, .84f, .42f, 1f, .08f, 1f, .92f),
        p("Humanoid standing dune held by thermal lift; body outline and mass continually shift.",
            "Ground vibration and airflow reach 16 m; noon thermals help, rain collapses useful structure.",
            "Mostly solitary, loosely tolerant of Prismkin; avoids water and circles unfamiliar beings.",
            "Mineral dust, heat, and very occasional crystallized sugar.",
            ActivityPattern.DIURNAL, SenseKind.AIRFLOW, SocialSystem.SOLITARY, EcoGuild.BURROW, PreferredMedium.PHASED,
            .33f, .70f, 16f, 360f, .55f, .42f, .22f, .88f, .38f, 6f, 1.05f, .16f, .22f, .52f, .30f, .50f, .18f),
        p("Swallow-sized ground-effect flier with long wings and airflow-sensitive feather margins.",
            "Airflow reaches 16 m and open sight 14 m; thicket clutter, fog, and rain shorten both.",
            "Loose flocks and strictly proximity-limited races; stalking cat/fox forms trigger avoidance.",
            "Aerial insects, pollen, and fruit droplets skimmed from leaves.",
            ActivityPattern.DIURNAL, SenseKind.AIRFLOW, SocialSystem.FLOCK, EcoGuild.AERIAL, PreferredMedium.AIR,
            .17f, .58f, 16f, 305f, .92f, .62f, .25f, .22f, .58f, 8f, .52f, .82f, .10f, .90f, .04f, .82f, 1f),
        p("Small aquatic juvenile with a folded tail-fin and pressure-sensitive lateral line.",
            "Shared-water pressure reaches 16 m; air vision falls to about 5 m away from the surface.",
            "Schools with Puddlim and follows Kelpling wakes; will not chase a terrestrial target ashore.",
            "Algae, plankton analogues, fruit sugars, and attention.",
            ActivityPattern.CATHEMERAL, SenseKind.WATER_PRESSURE, SocialSystem.SCHOOL, EcoGuild.WATER, PreferredMedium.WATER,
            .22f, .48f, 16f, 330f, .48f, .45f, .32f, 1f, .62f, 7f, .56f, .94f, .05f, .88f, .04f, .76f, .68f),
        p("Velvet subterranean digger with bidirectional fur and a 22-ray tactile-sensory star nose.",
            "Ground vibration reaches 14 m underground, scent 10 m, while useful sight is under 1 m.",
            "Solitary tunnel territoriality; reads footsteps from below and escapes through connected soil.",
            "Soil invertebrate analogues, roots, and picnic crumbs.",
            ActivityPattern.CATHEMERAL, SenseKind.VIBRATION, SocialSystem.TERRITORIAL, EcoGuild.BURROW, PreferredMedium.UNDERGROUND,
            .23f, .40f, 14f, 110f, .08f, .62f, .86f, 1f, .18f, 4.5f, .72f, .14f, .72f, .68f, .03f, .48f, .20f),
        p("Fox-sized glade quadruped with dexterous forepaws, fan tail and a polarized-light sensory crown.",
            "Integrates panoramic color vision, crown polarization and memory-guided hearing to about 19 m.",
            "Rare pair-living archivist; teaches cache routes, investigates novelty and mediates alarm rather than copying it blindly.",
            "Seasonal mast, berries, fungi and fruit, harvested selectively and cached for future scarcity.",
            ActivityPattern.CATHEMERAL, SenseKind.VISION, SocialSystem.PAIR, EcoGuild.THICKET, PreferredMedium.GROUND,
            .36f, .78f, 19f, 330f, 1f, .90f, .76f, .52f, .82f, 8.5f, .94f, .48f, .44f, .68f, .18f, .40f, .96f),
        p("Arboreal hexapod with prehensile feet, balancing tail, chromatophore moss and a chemical-sensory crown.",
            "Fuses canopy vision, bark vibration, scent and fog chemistry to about 20 m; rain preserves rather than masks its cues.",
            "Exceptionally rare mistwood reasoner; maps other creatures' routes, offers calm warnings and avoids exhausting shared food patches.",
            "Canopy fruit, rain-forest fungi and berries, taken in rotating harvests remembered across years.",
            ActivityPattern.CREPUSCULAR, SenseKind.CHEMICAL, SocialSystem.PAIR, EcoGuild.THICKET, PreferredMedium.AIR,
            .34f, .66f, 20f, 320f, .94f, .86f, 1f, .82f, .98f, 8f, .98f, .40f, .52f, .66f, .14f, .34f, .94f)
    )

    init {
        require(ALL.size == Species.ALL.size) { "Ecology roster must cover every species" }
    }

    fun of(species: Int): EcologyProfile = ALL[species.coerceIn(0, ALL.lastIndex)]

    /** Circadian gain from the Den's 0=night, 1=day daylight value. */
    fun activityGain(profile: EcologyProfile, daylight: Float): Float {
        val day = daylight.coerceIn(0f, 1f)
        return when (profile.activity) {
            ActivityPattern.DIURNAL -> .58f + .52f * day
            ActivityPattern.NOCTURNAL -> 1.15f - .58f * day
            ActivityPattern.CREPUSCULAR ->
                .66f + .55f * (1f - abs(day - .45f) / .55f).coerceIn(0f, 1f)
            ActivityPattern.CATHEMERAL -> .96f
        }
    }

    /**
     * Channel-based detection. Vision can be occluded; sound, scent, substrate
     * vibration, pressure, thermal and electric channels remain independently useful.
     */
    fun detectionDistance(species: Int, c: SensingContext): Float {
        val p = of(species)
        val halfFovCos = cos(Math.toRadians((p.fieldOfViewDeg / 2f).coerceAtMost(179.5f).toDouble())).toFloat()
        val inVisualArc = p.fieldOfViewDeg >= 359f || c.facingDot >= halfFovCos
        val rearGain = if (inVisualArc) 1f else if (p.fieldOfViewDeg >= 300f) .48f else .10f
        val light = c.daylight.coerceIn(0f, 1f) + (1f - c.daylight.coerceIn(0f, 1f)) * p.lowLightAcuity
        var visual = p.vision * light * rearGain
        if (c.fog) visual *= .38f
        if (c.rain) visual *= .78f
        if (c.visualOccluded || c.observerUnderground) visual = 0f

        val motion = (c.targetSpeed / 1.8f).coerceIn(0f, 1f)
        var hearing = p.hearing * (.32f + .68f * motion)
        if (c.rain) hearing *= .78f
        hearing *= (1f - c.wind.coerceIn(0f, 1.5f) * .12f).coerceAtLeast(.72f)

        var scent = p.scent * (.72f + c.wind.coerceIn(0f, 1f) * .12f)
        if (c.rain) scent *= .72f

        var vibration = p.vibration * (.28f + .72f * motion)
        if (c.observerUnderground) vibration *= 1.35f
        if (c.onStone && p.primarySense == SenseKind.VIBRATION) vibration *= 1.30f
        if (c.targetUnderground && !c.observerUnderground) vibration *= .65f

        var special = when (p.primarySense) {
            SenseKind.WATER_PRESSURE -> when {
                c.observerInWater && c.targetInWater -> .98f + motion * .30f
                c.observerInWater -> .42f
                else -> .24f
            }
            SenseKind.ELECTRIC -> .72f + if (c.rain || c.targetInWater) .22f else 0f
            SenseKind.THERMAL -> if (c.rain) .62f else .88f
            SenseKind.LIGHT -> visual
            SenseKind.AIRFLOW -> (.58f + c.wind.coerceIn(0f, 1f) * .45f) * if (c.rain) .65f else 1f
            SenseKind.CHEMICAL -> scent.coerceAtLeast(.82f)
            SenseKind.VIBRATION -> vibration
            SenseKind.HEARING -> hearing
            SenseKind.SCENT -> scent
            SenseKind.VISION -> visual
        }
        if (p.medium == PreferredMedium.WATER && !c.observerInWater) special *= .55f

        val channels = floatArrayOf(visual, hearing, scent, vibration, special)
        val dominant = channels.maxOrNull() ?: 0f
        val supporting = (channels.sum() - dominant) / 4f
        var gain = (.34f + dominant * .68f + supporting * .18f).coerceIn(.22f, 1.35f)
        gain *= activityGain(p, c.daylight)
        if (c.homeHabitat) gain *= 1.08f
        gain *= when (p.medium) {
            PreferredMedium.WATER -> if (c.observerInWater && c.targetInWater) 1.18f else .62f
            PreferredMedium.AMPHIBIOUS -> if (c.observerInWater == c.targetInWater) 1.08f else .86f
            PreferredMedium.UNDERGROUND -> if (c.observerUnderground) 1.18f else .72f
            else -> 1f
        }
        return (p.detectionRange * gain).coerceIn(.75f, p.detectionRange * 1.55f)
    }

    private val mutualisms = setOf(
        5,          // Leafling + Luxmoth
        13,         // Leafling + Moonhare
        214, 225, 1425, // aquatic network
        315, 321,   // Emberling + Puckle/Pyrebird
        416,        // Bookwyrm + Dreambaku
        512,        // Luxmoth + Wisplet
        718, 719,   // Ferrokit + Gemback/Clayward
        820,        // Voltling + Skydrum
        924,        // Gustril + Zephyret
        1017,       // Shadepaw + Glimmerfox (competitive familiarity)
        1123,       // Prismkin + Sandshift
        27, 1327, 2627,       // Sylvarch's glade mutualists
        528, 1228, 1628, 2728 // Mistcrown's pollinator, fog and thinker network
    )

    private fun pairKey(a: Int, b: Int): Int = minOf(a, b) * 100 + maxOf(a, b)

    private fun affinity(a: Int, b: Int): Float {
        if (a == b) return 1f
        val pa = of(a); val pb = of(b)
        var value = if (pa.guild == pb.guild) .32f else 0f
        if (pairKey(a, b) in mutualisms) value += .42f
        return value.coerceIn(0f, 1f)
    }

    private fun elementalConflict(a: Int, b: Int): Boolean {
        val water = setOf(2, 14, 25)
        val hotOrElectric = setOf(3, 8, 20, 21)
        return (a in water && b in hotOrElectric) || (b in water && a in hotOrElectric)
    }

    /** Morphology-, medium-, guild-, alarm- and distance-aware neighbor response. */
    fun socialResponse(
        observer: Int,
        target: Int,
        distance: Float,
        context: SensingContext,
        targetAlarmed: Boolean = false
    ): SocialResponse {
        val a = of(observer); val b = of(target)
        val detection = detectionDistance(observer, context.copy(targetSpeed = context.targetSpeed + b.signalStrength))
        val preferred = maxOf(a.personalSpace, a.bodyRadius + b.bodyRadius + .18f)
        if (distance > detection) return SocialResponse(SocialAction.UNAWARE, 0f, detection, preferred)

        val affinity = affinity(observer, target)
        if (targetAlarmed) {
            val transfer = a.alarmSensitivity * (if (observer == target) 1f else .24f + affinity * .58f)
            if (transfer > .43f && distance < minOf(detection, a.socialRange * 1.2f))
                return SocialResponse(SocialAction.ALARM, transfer, detection, preferred)
        }

        val stalker = target == 10 || target == 17
        val preyShape = observer in setOf(0, 5, 13, 18, 22, 24, 26)
        var danger = b.threatSignal * a.preySensitivity
        if (stalker && preyShape) danger += .55f
        if (elementalConflict(observer, target)) danger += .72f
        if ((observer == 18 && target == 6) || (observer == 4 && target in setOf(9, 20, 22, 24))) danger += .48f
        if (a.territoriality > .60f && context.homeHabitat && observer != target) danger += a.territoriality * .45f
        if (danger > .48f && distance < minOf(detection, a.socialRange * 1.15f))
            return SocialResponse(SocialAction.AVOID, danger.coerceAtMost(1.2f), detection, preferred)

        val mediumCompatible = when (a.medium) {
            PreferredMedium.WATER -> context.observerInWater && context.targetInWater
            PreferredMedium.UNDERGROUND -> context.observerUnderground && context.targetUnderground
            else -> true
        }
        if (distance < preferred)
            return SocialResponse(SocialAction.AVOID, .45f + (preferred - distance), detection, preferred)
        if (!mediumCompatible) return SocialResponse(SocialAction.OBSERVE, .18f, detection, preferred)

        val socialStrength = (affinity * .65f + a.groupAffinity * .35f).coerceIn(0f, 1f)
        if (socialStrength > .46f && distance <= a.socialRange) {
            val action = if (distance <= preferred * 1.35f) SocialAction.CONTACT else SocialAction.APPROACH
            return SocialResponse(action, socialStrength, detection, preferred)
        }
        return SocialResponse(SocialAction.OBSERVE, .16f + affinity * .20f, detection, preferred)
    }
}
