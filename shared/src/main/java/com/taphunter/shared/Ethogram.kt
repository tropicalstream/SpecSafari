package com.taphunter.shared

import kotlin.math.exp

/**
 * The behavioral ecology of a species — how it perceives and responds to the
 * human observer (the player). This is a working ethological model, grounded
 * in the real literature of animal–human interaction:
 *
 *  • FLIGHT INITIATION DISTANCE (FID) — the Ydenberg & Dill (1986) economic
 *    model of escape: an animal flees when an approaching threat crosses the
 *    distance at which the cost of staying exceeds the cost of fleeing.
 *  • ALERT DISTANCE — Fernández-Juricic et al.: the farther range at which the
 *    animal first orients to and monitors the human, before any flight.
 *  • APPROACH KINEMATICS — directness and speed of approach raise perceived
 *    predation risk; a fast, straight-on approach flushes at longer range than
 *    a slow, tangential one.
 *  • GAZE AS A PREDATORY CUE — many prey species treat a direct frontal gaze
 *    (eyes on them) as imminent-attack signalling and flee sooner.
 *  • HABITUATION vs. SENSITIZATION — repeated benign exposure lowers wariness
 *    (learned tolerance); aversive events (startles) raise it.
 *  • FOOD CONDITIONING — classical/operant association of the human with food
 *    (treats, berries) builds approach and food-solicitation behavior.
 *  • NEOPHOBIA / NEOPHILIA — wariness of, or attraction to, an unfamiliar human.
 *  • SOCIAL BUFFERING / RISK DILUTION — the "many eyes" and safety-in-numbers
 *    effects: an animal is bolder near conspecifics, and fleeing is contagious
 *    (allelomimetic) through a group.
 *  • REFUGE / COVER EFFECT — proximity to cover or the home range shortens FID.
 *  • BODY-SIZE ALLOMETRY & the BOLDNESS–SHYNESS personality axis round it out.
 *
 * Params are derived from each species' existing traits (energy, sociality,
 * gait, niche, temperament) with lore-driven overrides, so the whole roster
 * carries a coherent, individual profile without hand-tuning 27 rows.
 */
class Ethogram(
    val baseFID: Float,          // world units; flight distance for a calm animal under direct approach
    val alertRatio: Float,       // alert distance = baseFID * this
    val gazeSensitivity: Float,  // 0..1 how much a direct human gaze lengthens FID
    val speedSensitivity: Float, // 0..1+ how much fast approach lengthens FID
    val neophobia: Float,        // 0..1 wariness of an unfamiliar human
    val habituationRate: Float,  // how strongly learned tolerance shrinks FID
    val foodDrive: Float,        // 0..1 motivation to approach a food-associated human
    val packBuffer: Float,       // 0..1 boldness gained per nearby conspecific
    val refugeDependence: Float, // 0..1 emboldening from cover / home-range proximity
    val curiosity: Float,        // 0..1 neophilia; approach of a still, non-gazing human
    val boldnessFloor: Float     // minimum tolerance even when maximally wary
)

object EthoModel {

    /** Temperament mapped to a wariness weight (boldness–shyness axis). */
    private fun wariness(t: String): Float = when (t) {
        "ALOOF", "FLICKERY", "BASHFUL", "OTHERWORLDLY", "JITTERY", "WRY" -> 1.0f
        "POLITE", "SERENE", "DROWSY", "PEDANTIC", "SNUG", "SPLASHY" -> 0.55f
        "ZOOMY", "BRASH", "BOISTEROUS", "GIGGLY", "BREEZY", "RUMBLY",
        "DRAMATIC", "CLEVER", "PUNCTUAL" -> 0.35f
        "COZY", "HELPFUL", "STEADFAST", "DOZY", "GREEDY" -> 0.05f
        else -> 0.4f
    }

    private val cache = HashMap<Int, Ethogram>()

    fun of(i: Int): Ethogram = cache.getOrPut(i) { derive(i) }

    private fun derive(i: Int): Ethogram {
        val sp = Species.ALL[i]
        val w = wariness(sp.temperament)
        // Larger, faster, warier animals flush at longer range (allometry + risk).
        var fid = 1.5f + sp.energy * 1.7f + w * 1.7f
        var gaze = 0.30f + w * 0.55f
        var speed = 0.45f + sp.energy * 0.55f
        var neo = 0.20f + w * 0.60f
        var habit = 0.45f + sp.social * 0.65f - w * 0.20f
        var food = 0.30f + (1f - w) * 0.45f
        var pack = 0.20f + sp.social * 0.80f
        var refuge = 0.30f +
            (if (sp.motion == Species.BURROW || sp.zone == "VOID" || sp.zone == "THICKET") 0.40f else 0f)
        var curiosity = 0.30f + sp.energy * 0.40f - w * 0.20f
        var floor = 0.8f + (1f - w) * 0.8f

        // Lore-driven overrides where a species' story demands a specific psychology.
        when (i) {
            0 -> { fid *= 0.6f; food += 0.3f; floor += 0.6f }               // LEAFLING — lawn commensal, near-tame
            3 -> { fid *= 0.6f; food += 0.35f; floor += 0.6f; habit += 0.2f } // EMBERLING — hearth commensal
            6 -> { food += 0.35f; curiosity += 0.2f }                       // COINIX — greedy, food-forward
            10 -> { gaze = 0.95f; curiosity = 0.75f; fid *= 0.8f; habit += 0.15f } // SHADEPAW — gaze-wary yet stays in reach
            11, 23 -> { neo += 0.30f; food -= 0.30f; habit -= 0.25f; fid *= 1.2f } // PRISMKIN / SANDSHIFT — otherworldly, aloof
            13, 22 -> { fid *= 1.4f; gaze += 0.30f; speed += 0.20f }        // MOONHARE / HORNHOP — lagomorph prey wariness
            15 -> { fid *= 0.55f; food += 0.30f; floor += 0.7f; habit += 0.25f } // PUCKLE — house brownie, commensal
            16 -> { speed -= 0.30f; fid *= 0.7f }                           // DREAMBAKU — drowsy, slow to flush
            19 -> { fid *= 0.4f; floor += 1.3f; neo -= 0.2f; speed -= 0.2f }// CLAYWARD — golem, all but fearless
            24 -> { fid *= 1.3f; speed += 0.25f; curiosity += 0.15f }       // ZEPHYRET — skittish low-flier
            25 -> { fid *= 1.1f; food += 0.15f }                            // NIXLET — surfaces for an audience
            26 -> { refuge += 0.3f; gaze -= 0.2f }                          // MOLDEWARP — near-blind, dives to its tunnels
        }
        return Ethogram(
            fid.coerceIn(1.0f, 5.5f), 1.7f,
            gaze.coerceIn(0f, 1f), speed.coerceIn(0f, 1.3f), neo.coerceIn(0f, 1f),
            habit.coerceIn(0.10f, 1.3f), food.coerceIn(0f, 1f), pack.coerceIn(0f, 1f),
            refuge.coerceIn(0f, 0.9f), curiosity.coerceIn(0f, 1f), floor.coerceIn(0.5f, 3f)
        )
    }

    // ---- learned quantities, from the recorded field history (0..1) ----

    /** Familiarity saturates with total benign contact — the "dear stranger" fading. */
    fun familiarity(pets: Int, treats: Int, berries: Int, encounters: Int): Float =
        (1f - exp(-((pets + treats * 2 + berries * 1.5f + encounters * 0.3f) / 12f))).coerceIn(0f, 1f)

    /** Habituation is benign contact minus the aversive startles. */
    fun habituation(pets: Int, treats: Int, berries: Int, startles: Int): Float =
        ((pets + treats * 1.5f + berries + 2f - startles * 1.6f) / 22f).coerceIn(0f, 1f)

    /** Food association drives solicitation; treats teach faster than berries. */
    fun foodAssociation(treats: Int, berries: Int): Float =
        ((treats * 2f + berries) / 8f).coerceIn(0f, 1f)

    // ------------------------- dex labels -------------------------

    fun warinessLabel(e: Ethogram): String = when {
        e.baseFID < 1.9f -> "Tame"
        e.baseFID < 2.8f -> "Tolerant"
        e.baseFID < 3.8f -> "Wary"
        else -> "Skittish"
    }

    /** A field-guide approach tip synthesized from the model. */
    fun approachTip(e: Ethogram): String {
        val parts = ArrayList<String>()
        parts += if (e.speedSensitivity > 0.8f) "approach slowly" else "a steady walk is fine"
        if (e.gazeSensitivity > 0.7f) parts += "don't stare it down"
        if (e.foodDrive > 0.6f) parts += "food wins it over fast"
        if (e.packBuffer > 0.7f) parts += "bolder among its own kind"
        if (e.curiosity > 0.6f) parts += "stand still and it may come to you"
        if (e.refugeDependence > 0.6f) parts += "keeps a line to cover"
        return parts.joinToString(", ").replaceFirstChar { it.uppercase() } + "."
    }
}
