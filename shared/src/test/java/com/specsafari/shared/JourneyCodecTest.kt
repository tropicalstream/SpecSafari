package com.specsafari.shared

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JourneyCodecTest {
    private val journey = JourneyRecord(
        day = "2026-07-21",
        distanceM = 487,
        treasures = 1,
        essence = 12,
        berries = 2,
        foundSpecies = listOf(6, 3, 10, 10),
        releasedSpecies = listOf(3),
        region = "94602",
    )

    @Test fun roundTripPreservesTheJourney() {
        assertEquals(journey, JourneyCodec.decode(JourneyCodec.encode(journey)))
    }

    @Test fun checksumRejectsATamperedCode() {
        val code = JourneyCodec.encode(journey)
        val tampered = code.dropLast(1) + if (code.last() == '0') '1' else '0'
        assertNull(JourneyCodec.decode(tampered))
    }

    @Test fun summaryIsOnePrivacySafeParagraph() {
        val summary = journey.summary()
        assertFalse(summary.contains('\n'))
        assertFalse(summary.contains("Dublin", ignoreCase = true))
        assertTrue(summary.contains("487 m"))
        assertTrue(summary.contains("94602"))
    }
}
