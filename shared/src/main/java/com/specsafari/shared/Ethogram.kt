package com.specsafari.shared

import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

/** The escape-decision phenotype layered on top of a species' sensorium. */
class Ethogram(
    val baseFID: Float,
    val alertRatio: Float,
    val gazeSensitivity: Float,
    val speedSensitivity: Float,
    val neophobia: Float,
    val habituationRate: Float,
    val foodDrive: Float,
    val packBuffer: Float,
    val refugeDependence: Float,
    val curiosity: Float,
    val boldnessFloor: Float
)

/** Learned relationship with the player, normalized to 0..1. */
data class LearningState(
    val familiarity: Float = 0f,
    val habituation: Float = 0f,
    val foodExpectation: Float = 0f,
    val fear: Float = 0f
)

data class HumanPerceptionContext(
    val daylight: Float = 1f,
    val creatureFacingDot: Float = 1f,
    val humanSpeed: Float = 0f,
    val closingSpeed: Float = 0f,
    val humanGaze: Float = 0f,
    val homeHabitat: Boolean = false,
    val creatureInWater: Boolean = false,
    val humanInWater: Boolean = false,
    val creatureUnderground: Boolean = false,
    val onStone: Boolean = false,
    val fog: Boolean = false,
    val rain: Boolean = false,
    val wind: Float = 0f,
    val visualOccluded: Boolean = false,
    val nearbyConspecifics: Int = 0
)

data class InteractionThresholds(
    val detectionDistance: Float,
    val alertDistance: Float,
    val flightDistance: Float,
    val approachDistance: Float,
    val conditionedApproach: Float,
    val approachReady: Boolean,
    val petReach: Float,
    val feedRange: Float
)

enum class PlayerResponse { UNAWARE, AWARE, ALERT, FLEE, INVESTIGATE, APPROACH }

/**
 * Shared, deterministic behavioral model. The OpenGL renderer supplies positions,
 * clock and weather; this class owns the biological decision and is unit-testable.
 */
object EthoModel {

    private fun wariness(t: String): Float = when (t) {
        "ALOOF", "FLICKERY", "BASHFUL", "OTHERWORLDLY", "JITTERY", "WRY" -> 1.0f
        "POLITE", "SERENE", "DROWSY", "PEDANTIC", "SNUG", "SPLASHY" -> 0.55f
        "ZOOMY", "BRASH", "BOISTEROUS", "GIGGLY", "BREEZY", "RUMBLY",
        "DRAMATIC", "CLEVER", "PUNCTUAL" -> 0.35f
        "INQUISITIVE" -> 0.42f
        "SAGACIOUS" -> 0.58f
        "COZY", "HELPFUL", "STEADFAST", "DOZY", "GREEDY" -> 0.05f
        else -> 0.4f
    }

    private val cache = HashMap<Int, Ethogram>()

    fun of(i: Int): Ethogram {
        val key = i.coerceIn(0, Species.ALL.lastIndex)
        return cache.getOrPut(key) { derive(key) }
    }

    private fun derive(i: Int): Ethogram {
        val sp = Species.ALL[i]
        val eco = EcologyModel.of(i)
        val wary = wariness(sp.temperament)
        // Body size is now real input rather than a claim in a comment: larger,
        // high-energy, wary forms generally commit to escape farther out.
        var fid = 1.0f + sp.energy * 1.55f + wary * 1.55f + eco.bodyRadius * .85f
        var gaze = .24f + wary * .58f
        var speed = .34f + sp.energy * .58f
        var neo = .18f + wary * .62f
        var habit = .44f + sp.social * .58f - wary * .16f
        var food = .32f + (1f - wary) * .42f
        var pack = .16f + sp.social * .76f
        var refuge = .26f + if (eco.medium == PreferredMedium.UNDERGROUND ||
            sp.zone == "VOID" || sp.zone == "THICKET") .42f else 0f
        var curiosity = .28f + sp.energy * .42f - wary * .18f
        // A smaller minimum means greater learned tolerance. The previous model
        // accidentally gave tame creatures the largest irreducible FID.
        var floor = .42f + wary * .58f + eco.bodyRadius * .35f

        when (i) {
            0 -> { fid *= .58f; food += .30f; floor *= .72f }
            3 -> { fid *= .62f; food += .34f; floor *= .72f; habit += .18f }
            6 -> { food += .32f; curiosity += .18f }
            10 -> { gaze = .96f; curiosity = .74f; fid *= .82f; habit += .12f }
            11, 23 -> { neo += .28f; food -= .24f; habit -= .22f; fid *= 1.16f }
            13, 22 -> { fid *= 1.30f; gaze += .22f; speed += .18f }
            15 -> { fid *= .54f; food += .28f; floor *= .68f; habit += .22f }
            16 -> { speed -= .26f; fid *= .72f }
            19 -> { fid *= .38f; floor = .30f; neo -= .24f; speed -= .24f }
            24 -> { fid *= 1.20f; speed += .20f; curiosity += .14f }
            25 -> { fid *= 1.08f; food += .14f }
            26 -> { refuge += .25f; gaze -= .30f }
            27 -> { curiosity = 1f; habit += .28f; gaze = .72f; neo = .46f; food = .58f; fid *= .84f }
            28 -> { curiosity = .98f; habit += .22f; gaze = .58f; neo = .62f; food = .50f; fid *= .92f }
        }
        val alertRatio = (1.32f + eco.detectionRange / fid.coerceAtLeast(1f) * .12f)
            .coerceIn(1.35f, 2.15f)
        return Ethogram(
            fid.coerceIn(.65f, 5.8f), alertRatio,
            gaze.coerceIn(0f, 1f), speed.coerceIn(.08f, 1.15f), neo.coerceIn(0f, 1f),
            habit.coerceIn(.15f, 1.25f), food.coerceIn(.15f, 1f), pack.coerceIn(0f, 1f),
            refuge.coerceIn(0f, .92f), curiosity.coerceIn(0f, 1f), floor.coerceIn(.25f, 1.65f)
        )
    }

    /**
     * Benign experience, food conditioning and adverse sensitization are separate
     * memories. Quiet time slowly fades all three, fear fastest and familiarity slowest.
     */
    fun learningState(
        pets: Int,
        treats: Int,
        berries: Int,
        startles: Int,
        encounters: Int,
        daysSinceContact: Float = 0f
    ): LearningState {
        val benign = pets * .85f + treats * 1.55f + berries * 1.05f + encounters * .16f
        val familiarityBase = 1f - exp(-(benign / 10f))
        val fearBase = if (startles <= 0) 0f else
            (startles * 2.4f / (startles * 2.4f + benign * .42f + 3.5f)).coerceIn(0f, 1f)
        val habitBase = ((1f - exp(-(benign / 8.5f))) - fearBase * .68f).coerceIn(0f, 1f)
        val foodBase = (1f - exp(-((treats * 1.10f + berries * .55f) / 3.5f))) *
            (1f - fearBase * .60f)
        val days = daysSinceContact.coerceAtLeast(0f)
        return LearningState(
            (familiarityBase * exp(-(days / 260f))).coerceIn(0f, 1f),
            (habitBase * exp(-(days / 150f))).coerceIn(0f, 1f),
            (foodBase * exp(-(days / 75f))).coerceIn(0f, 1f),
            (fearBase * exp(-(days / 18f))).coerceIn(0f, 1f)
        )
    }

    fun familiarity(pets: Int, treats: Int, berries: Int, encounters: Int): Float =
        learningState(pets, treats, berries, 0, encounters).familiarity

    fun habituation(pets: Int, treats: Int, berries: Int, startles: Int): Float =
        learningState(pets, treats, berries, startles, 0).habituation

    fun foodAssociation(treats: Int, berries: Int): Float =
        learningState(0, treats, berries, 0, 0).foodExpectation

    fun sensitization(pets: Int, treats: Int, berries: Int, startles: Int): Float =
        learningState(pets, treats, berries, startles, 0).fear

    /** Compute all near/far boundaries from sensory ecology, conditions and history. */
    fun thresholds(
        species: Int,
        learning: LearningState = LearningState(),
        c: HumanPerceptionContext = HumanPerceptionContext()
    ): InteractionThresholds {
        val e = of(species)
        val eco = EcologyModel.of(species)
        val detection = EcologyModel.detectionDistance(species, SensingContext(
            daylight = c.daylight,
            facingDot = c.creatureFacingDot,
            targetSpeed = c.humanSpeed,
            homeHabitat = c.homeHabitat,
            observerInWater = c.creatureInWater,
            targetInWater = c.humanInWater,
            observerUnderground = c.creatureUnderground,
            onStone = c.onStone,
            fog = c.fog,
            rain = c.rain,
            wind = c.wind,
            visualOccluded = c.visualOccluded
        ))

        // Normalize closing speed around a brisk 1.8 m/s reference. Total speed
        // alone no longer makes sideways movement look like a predatory charge.
        val closingCue = (c.closingSpeed.coerceAtLeast(0f) / 1.8f).coerceIn(0f, 1f)
        var fid = e.baseFID
        fid *= 1f + e.speedSensitivity * closingCue * .62f
        fid *= 1f + e.gazeSensitivity * c.humanGaze.coerceIn(0f, 1f) * .34f
        fid *= 1f + e.neophobia * (1f - learning.familiarity) * .38f
        fid *= 1f + learning.fear * .55f
        fid *= 1f - (learning.habituation * e.habituationRate * .50f).coerceAtMost(.60f)

        val conditioned = (learning.foodExpectation *
            (.42f + learning.familiarity * .58f) *
            (.54f + e.foodDrive * .46f) *
            (1f - learning.fear * .78f)).coerceIn(0f, 1f)
        fid *= 1f - conditioned * .24f
        fid *= 1f - min(c.nearbyConspecifics, 3) * e.packBuffer * .055f
        if (c.homeHabitat) fid *= 1f - e.refugeDependence * .22f
        fid = max(fid, e.boldnessFloor * (1f + learning.fear * .25f))

        // A creature cannot make an escape decision before one of its senses
        // detects the player; fog, cover, medium and circadian state therefore
        // cap effective FID without erasing the underlying wariness phenotype.
        val flight = min(fid, detection * .90f).coerceAtLeast(.30f)
        val alert = max(flight * e.alertRatio, detection * .56f).coerceAtMost(detection)
        val needed = (.28f + e.neophobia * .11f - e.foodDrive * .035f).coerceIn(.24f, .40f)
        val ready = conditioned >= needed
        val approachDistance = if (ready)
            min(detection * .86f, 1.55f + conditioned * 3.2f + e.curiosity * .55f)
        else 0f
        return InteractionThresholds(
            detection, alert, flight, approachDistance, conditioned, ready,
            petReach = .78f + eco.bodyRadius,
            feedRange = (3.0f + eco.detectionRange * .20f).coerceIn(3.4f, 6.0f)
        )
    }

    /** Stateless proximity classification; renderer adds hysteresis to ongoing flight. */
    fun response(
        species: Int,
        distance: Float,
        learning: LearningState = LearningState(),
        c: HumanPerceptionContext = HumanPerceptionContext()
    ): PlayerResponse {
        val t = thresholds(species, learning, c)
        if (distance > t.detectionDistance) return PlayerResponse.UNAWARE
        val calm = c.humanSpeed < .68f && c.closingSpeed < .24f && c.humanGaze < .52f
        if (calm && t.approachReady && distance <= t.approachDistance)
            return PlayerResponse.APPROACH
        if (distance < t.flightDistance &&
            (c.closingSpeed > .035f || c.humanSpeed > 1.05f || c.humanGaze > .84f))
            return PlayerResponse.FLEE
        val innateCuriosity = of(species).curiosity
        val curiosityGate = if (innateCuriosity > .92f) 0f else .18f
        if (calm && innateCuriosity > .62f && learning.familiarity >= curiosityGate &&
            distance < t.detectionDistance * .72f) return PlayerResponse.INVESTIGATE
        if (distance < t.alertDistance) return PlayerResponse.ALERT
        return PlayerResponse.AWARE
    }

    fun referenceThresholds(species: Int): InteractionThresholds = thresholds(
        species,
        LearningState(),
        HumanPerceptionContext(daylight = .8f, creatureFacingDot = 1f,
            humanSpeed = 1.0f, closingSpeed = .75f, humanGaze = .55f)
    )

    /** Number of calm treats normally needed before food-conditioned approach. */
    fun treatsToApproach(species: Int): Int {
        for (treats in 1..20) {
            val learned = learningState(0, treats, 0, 0, maxOf(1, treats / 2))
            if (thresholds(species, learned).approachReady) return treats
        }
        return 20
    }

    fun warinessLabel(e: Ethogram): String = when {
        e.baseFID < 1.7f -> "Tame"
        e.baseFID < 2.7f -> "Tolerant"
        e.baseFID < 3.8f -> "Wary"
        else -> "Skittish"
    }

    fun approachTip(e: Ethogram): String {
        val parts = ArrayList<String>()
        parts += if (e.speedSensitivity > .78f) "approach slowly" else "a steady walk is fine"
        if (e.gazeSensitivity > .70f) parts += "don't stare it down"
        if (e.foodDrive > .62f) parts += "food wins it over quickly"
        if (e.packBuffer > .70f) parts += "reads nearby kin before reacting"
        if (e.curiosity > .62f) parts += "stand still and it may investigate"
        if (e.refugeDependence > .62f) parts += "keeps a line to cover"
        return parts.joinToString(", ").replaceFirstChar { it.uppercase() } + "."
    }
}
