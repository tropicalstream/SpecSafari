package com.specsafari.shared

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EthoModelTest {

    private val calmApproach = HumanPerceptionContext(
        daylight = .35f,
        creatureFacingDot = 1f,
        humanSpeed = .30f,
        closingSpeed = .15f,
        humanGaze = .18f
    )

    @Test fun closingDirectionMattersWithoutExplodingAtNormalWalkSpeed() {
        val side = EthoModel.thresholds(1, LearningState(),
            HumanPerceptionContext(humanSpeed = 3.1f, closingSpeed = 0f, humanGaze = .5f))
        val direct = EthoModel.thresholds(1, LearningState(),
            HumanPerceptionContext(humanSpeed = 3.1f, closingSpeed = 3.1f, humanGaze = .5f))
        assertTrue(direct.flightDistance > side.flightDistance)
        assertTrue(direct.flightDistance < EthoModel.of(1).baseFID * 2.2f)
        assertTrue(direct.alertDistance <= direct.detectionDistance)
    }

    @Test fun repeatedFeedingReversesNaiveFlightIntoVoluntaryApproach() {
        val species = 13 // Moonhare: deliberately wary prey morphology
        val distance = 2.0f
        assertEquals(PlayerResponse.FLEE,
            EthoModel.response(species, distance, LearningState(), calmApproach))

        var firstApproach = -1
        for (feeds in 1..20) {
            val learned = EthoModel.learningState(0, feeds, 0, 0, feeds / 2)
            if (EthoModel.response(species, distance, learned, calmApproach) == PlayerResponse.APPROACH) {
                firstApproach = feeds
                break
            }
        }
        assertTrue("Moonhare never reversed to approach", firstApproach in 3..20)

        val trained = EthoModel.learningState(0, firstApproach, 0, 0, firstApproach / 2)
        assertTrue(EthoModel.thresholds(species, trained, calmApproach).flightDistance <
            EthoModel.thresholds(species, LearningState(), calmApproach).flightDistance)
        assertEquals(PlayerResponse.APPROACH,
            EthoModel.response(species, distance, trained, calmApproach))
    }

    @Test fun aversiveHistoryReSensitizesAndSuppressesApproach() {
        val species = 13
        val trained = EthoModel.learningState(0, 10, 0, 0, 5)
        val startled = EthoModel.learningState(0, 10, 0, 7, 5)
        assertTrue(trained.foodExpectation > startled.foodExpectation)
        assertTrue(startled.fear > trained.fear)
        assertTrue(EthoModel.thresholds(species, startled, calmApproach).flightDistance >
            EthoModel.thresholds(species, trained, calmApproach).flightDistance)
        assertFalse(EthoModel.thresholds(species, startled, calmApproach).approachReady)
    }

    @Test fun everySpeciesCanLearnButRetainsSpeciesSpecificPace() {
        val milestones = Species.ALL.indices.map { species ->
            val n = EthoModel.treatsToApproach(species)
            val learned = EthoModel.learningState(0, n, 0, 0, maxOf(1, n / 2))
            assertTrue("species $species cannot learn by $n treats",
                EthoModel.thresholds(species, learned).approachReady)
            n
        }
        assertTrue(milestones.toSet().size >= 3)
        assertTrue(milestones.all { it in 2..20 })
    }

    @Test fun interactionReachIsMorphologyBounded() {
        Species.ALL.indices.forEach { species ->
            val t = EthoModel.thresholds(species)
            assertTrue(t.petReach in .85f..1.25f)
            assertTrue(t.feedRange in 3.4f..6f)
            assertTrue(t.feedRange > t.petReach * 2.5f)
        }
    }
}
