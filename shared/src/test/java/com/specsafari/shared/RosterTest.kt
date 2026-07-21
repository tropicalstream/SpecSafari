package com.specsafari.shared

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RosterTest {

    @Test
    fun `roster round-trips through the codec`() {
        val list = listOf(
            Individual(1753142400000L, 3, 2, 0x5EED1234, "2026-07-21", "Cinderkin", "THE HALL OF MEADOW"),
            Individual(1753142400001L, 10, 1, -77, "2026-07-20", "Wren", "OLDE KEARNEY AVENUE"),
        )
        assertEquals(list, Roster.decode(Roster.encode(list)))
    }

    @Test
    fun `pipes and newlines cannot break the record`() {
        val tricky = Individual(5L, 0, 1, 9, "2026-07-21", "Bad|Name", "TWO\nLINES")
        val back = Roster.decode(Roster.encode(listOf(tricky)))
        assertEquals(1, back.size)
        assertEquals("Bad/Name", back[0].name)
        assertEquals("TWO LINES", back[0].place)
    }

    @Test
    fun `phenotype is deterministic per seed`() {
        for (seed in intArrayOf(0, 1, 12345, -99999, Int.MAX_VALUE)) {
            assertEquals(Roster.autoName(seed), Roster.autoName(seed))
            assertEquals(Roster.coatName(seed), Roster.coatName(seed))
            assertEquals(Roster.stature(seed), Roster.stature(seed), 0f)
            assertTrue(Roster.stature(seed) in 0.87f..1.13f)
            assertTrue(Roster.markKind(seed) in 0..3)
            assertTrue(Roster.autoName(seed).isNotBlank())
        }
    }

    @Test
    fun `bad lines are dropped not fatal`() {
        val junk = "garbage\n1|2|3\n" + Roster.encode(
            listOf(Individual(7L, 1, 1, 1, "2026-07-21", "Pip", "A GLADE")))
        assertEquals(1, Roster.decode(junk).size)
    }
}
