package com.specsafari.shared

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EcologyModelTest {

    @Test fun everySpeciesHasACompleteDistinctProfile() {
        assertEquals(Species.ALL.size, EcologyModel.ALL.size)
        assertEquals(29, EcologyModel.ALL.size)
        assertEquals(29, EcologyModel.ALL.map { it.morphology }.toSet().size)
        assertTrue(EcologyModel.ALL.map { it.primarySense }.toSet().size >= 8)
        EcologyModel.ALL.forEachIndexed { index, p ->
            assertTrue("species $index detection", p.detectionRange in 7f..20f)
            assertTrue("species $index radius", p.bodyRadius in .12f..0.50f)
            assertTrue("species $index FOV", p.fieldOfViewDeg in 90f..360f)
            assertTrue("species $index social range", p.socialRange > p.personalSpace)
            assertTrue("species $index sensory note", p.sensoryEcology.length > 40)
            assertTrue("species $index social note", p.socialEcology.length > 40)
        }
    }

    @Test fun rareForestIntelligencesHaveDistinctHighCuriositySensoriums() {
        val sylvarch = EcologyModel.of(Species.SYLVARCH)
        val mistcrown = EcologyModel.of(Species.MISTCROWN)
        assertEquals(PreferredMedium.GROUND, sylvarch.medium)
        assertEquals(PreferredMedium.AIR, mistcrown.medium)
        assertEquals(SenseKind.VISION, sylvarch.primarySense)
        assertEquals(SenseKind.CHEMICAL, mistcrown.primarySense)
        assertTrue(sylvarch.detectionRange >= 19f)
        assertTrue(mistcrown.detectionRange >= 19f)
        assertTrue(EthoModel.of(Species.SYLVARCH).curiosity > .92f)
        assertTrue(EthoModel.of(Species.MISTCROWN).curiosity > .92f)

        val calm = HumanPerceptionContext(humanSpeed = .1f, closingSpeed = 0f, humanGaze = .1f)
        assertEquals(PlayerResponse.INVESTIGATE,
            EthoModel.response(Species.SYLVARCH, 4f, LearningState(), calm))
        assertEquals(PlayerResponse.INVESTIGATE,
            EthoModel.response(Species.MISTCROWN, 4f, LearningState(), calm))
    }

    @Test fun sightFogAndCircadianRhythmChangeDetection() {
        val gustrilClear = EcologyModel.detectionDistance(9,
            SensingContext(daylight = 1f, facingDot = 1f, targetSpeed = 1f))
        val gustrilFog = EcologyModel.detectionDistance(9,
            SensingContext(daylight = 1f, facingDot = 1f, targetSpeed = 1f, fog = true))
        assertTrue(gustrilClear > gustrilFog * 1.25f)

        val shadeNight = EcologyModel.detectionDistance(10,
            SensingContext(daylight = 0f, facingDot = 1f, targetSpeed = .5f))
        val shadeDay = EcologyModel.detectionDistance(10,
            SensingContext(daylight = 1f, facingDot = 1f, targetSpeed = .5f))
        assertTrue(shadeNight > shadeDay * 1.25f)

        val occludedGustril = EcologyModel.detectionDistance(9,
            SensingContext(daylight = 1f, facingDot = 1f, targetSpeed = .2f, visualOccluded = true))
        assertTrue(gustrilClear > occludedGustril * 1.35f)
    }

    @Test fun morphologySpecificNonvisualChannelsRemainFunctional() {
        val moleBelow = EcologyModel.detectionDistance(26,
            SensingContext(daylight = 1f, targetSpeed = 1.4f,
                observerUnderground = true, visualOccluded = true))
        val moleAboveStill = EcologyModel.detectionDistance(26,
            SensingContext(daylight = 1f, targetSpeed = 0f, visualOccluded = true))
        assertTrue(moleBelow > moleAboveStill * 1.8f)

        val ferroStone = EcologyModel.detectionDistance(7,
            SensingContext(targetSpeed = 1.1f, onStone = true, visualOccluded = true))
        val ferroSoft = EcologyModel.detectionDistance(7,
            SensingContext(targetSpeed = 1.1f, onStone = false, visualOccluded = true))
        assertTrue(ferroStone > ferroSoft * 1.15f)

        val nixletWater = EcologyModel.detectionDistance(25,
            SensingContext(observerInWater = true, targetInWater = true, targetSpeed = .8f))
        val nixletLand = EcologyModel.detectionDistance(25,
            SensingContext(observerInWater = false, targetInWater = false, targetSpeed = .8f))
        assertTrue(nixletWater > nixletLand * 2f)
    }

    @Test fun creatureRelationsDependOnKinThreatMediumAndRange() {
        val water = SensingContext(observerInWater = true, targetInWater = true,
            targetSpeed = .6f, daylight = .7f)
        val school = EcologyModel.socialResponse(25, 2, 3f, water)
        assertEquals(SocialAction.APPROACH, school.action)

        val heatThreat = EcologyModel.socialResponse(25, 3, 2.2f, water)
        assertEquals(SocialAction.AVOID, heatThreat.action)

        val stalker = EcologyModel.socialResponse(13, 10, 3f,
            SensingContext(targetSpeed = .5f, daylight = .25f))
        assertEquals(SocialAction.AVOID, stalker.action)

        val kinAlarm = EcologyModel.socialResponse(13, 13, 4f,
            SensingContext(targetSpeed = 1f, daylight = .25f), targetAlarmed = true)
        assertEquals(SocialAction.ALARM, kinAlarm.action)

        val sentinel = EcologyModel.socialResponse(19, 0, 4f,
            SensingContext(targetSpeed = 1f, onStone = true), targetAlarmed = true)
        assertNotEquals(SocialAction.ALARM, sentinel.action)

        val far = EcologyModel.socialResponse(24, 9, 40f,
            SensingContext(targetSpeed = 1f))
        assertEquals(SocialAction.UNAWARE, far.action)
    }
}
