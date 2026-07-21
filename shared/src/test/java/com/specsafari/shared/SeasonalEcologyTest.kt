package com.specsafari.shared

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SeasonalEcologyTest {

    @Test fun everySpeciesHasAUsableSeasonalRangeAndForageNiche() {
        assertEquals(Species.ALL.size, SeasonalEcology.ALL.size)
        assertEquals(29, SeasonalEcology.ALL.size)
        val foodByBiome = listOf(
            setOf(ForageKind.LICHEN, ForageKind.AQUATIC),
            setOf(ForageKind.MUSHROOM, ForageKind.BERRY, ForageKind.AQUATIC),
            setOf(ForageKind.BERRY, ForageKind.MUSHROOM, ForageKind.AQUATIC),
            setOf(ForageKind.MUSHROOM, ForageKind.FRUIT, ForageKind.AQUATIC),
            setOf(ForageKind.FRUIT, ForageKind.AQUATIC),
            setOf(ForageKind.FRUIT, ForageKind.SEED, ForageKind.AQUATIC),
            setOf(ForageKind.CACTUS, ForageKind.SEED, ForageKind.LICHEN),
            setOf(ForageKind.SEED, ForageKind.BERRY, ForageKind.LICHEN),
            setOf(ForageKind.BERRY, ForageKind.SEED)
        )
        SeasonalEcology.ALL.forEachIndexed { species, profile ->
            assertTrue("species $species climate note", profile.climatePreference.length > 25)
            assertTrue("species $species migration note", profile.migrationNote.length > 35)
            assertTrue("species $species forage note", profile.forageNote.length > 30)
            Season.values().forEach { season ->
                val biome = profile.targetBiome(season)
                assertTrue("species $species has no food in ${WorldBiome.values()[biome]}",
                    profile.forageKinds.intersect(foodByBiome[biome]).isNotEmpty())
            }
        }
    }

    @Test fun calendarAndHemisphereSelectOppositeSeasons() {
        assertEquals(Season.WINTER, SeasonalEcology.currentSeason(1, false))
        assertEquals(Season.SUMMER, SeasonalEcology.currentSeason(1, true))
        assertEquals(Season.SPRING, SeasonalEcology.currentSeason(4, false))
        assertEquals(Season.AUTUMN, SeasonalEcology.currentSeason(4, true))
    }

    @Test fun representativeMigrantsTrackBiologicallyRelevantClimates() {
        val moonhare = SeasonalEcology.of(13)
        assertEquals(WorldBiome.SAGE.ordinal, moonhare.targetBiome(Season.WINTER))
        assertEquals(WorldBiome.TAIGA.ordinal, moonhare.targetBiome(Season.SUMMER))
        assertNotEquals(moonhare.targetBiome(Season.WINTER), moonhare.targetBiome(Season.SUMMER))

        val puddlim = SeasonalEcology.of(2)
        assertEquals(MigrationMode.RAIN_TRACKER, puddlim.migrationMode)
        assertTrue(ForageKind.AQUATIC in puddlim.forageKinds)
        assertEquals(WorldBiome.JUNGLE.ordinal, puddlim.targetBiome(Season.SUMMER))
    }

    @Test fun rareForestIntelligencesStayFaithfulToTheirEvolvedHabitats() {
        Season.values().forEach { season ->
            assertEquals(WorldBiome.GLADE.ordinal,
                SeasonalEcology.targetBiome(Species.SYLVARCH, season))
            assertEquals(WorldBiome.MISTWOOD.ordinal,
                SeasonalEcology.targetBiome(Species.MISTCROWN, season))
        }
        assertEquals(MigrationMode.RESIDENT, SeasonalEcology.of(Species.SYLVARCH).migrationMode)
        assertEquals(MigrationMode.RESIDENT, SeasonalEcology.of(Species.MISTCROWN).migrationMode)
    }

    @Test fun rareSpawningIsHabitatGatedAndPrecedesGenericMythics() {
        assertEquals(Species.SYLVARCH, Species.forCategory("leisure=park", 0, 0))
        assertEquals(Species.MISTCROWN, Species.forCategory("natural=wood", 0, 0))
        assertNotEquals(Species.SYLVARCH, Species.forCategory("amenity=library", 0, 0))
        assertNotEquals(Species.MISTCROWN, Species.forCategory("amenity=library", 0, 0))
    }

    @Test fun cropAvailabilityChangesWithSeasonAndClimate() {
        val autumnMushroom = SeasonalEcology.foodAvailability(
            ForageKind.MUSHROOM.id, Season.AUTUMN, WorldBiome.MISTWOOD.ordinal)
        val desertMushroom = SeasonalEcology.foodAvailability(
            ForageKind.MUSHROOM.id, Season.SUMMER, WorldBiome.DUNES.ordinal)
        val summerFruit = SeasonalEcology.foodAvailability(
            ForageKind.FRUIT.id, Season.SUMMER, WorldBiome.JUNGLE.ordinal)
        val winterFruit = SeasonalEcology.foodAvailability(
            ForageKind.FRUIT.id, Season.WINTER, WorldBiome.JUNGLE.ordinal)
        assertTrue(autumnMushroom > desertMushroom * 3f)
        assertTrue(summerFruit > winterFruit * 3f)
    }
}
