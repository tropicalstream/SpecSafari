package com.specsafari.phone.den

import android.content.SharedPreferences
import com.specsafari.shared.EthoModel
import com.specsafari.shared.LearningState
import com.specsafari.shared.Species

/**
 * The recorded natural history of your dealings with each creature — the
 * "internal database" behind the dex. Every meeting, pet, treat, berry, and
 * startled flight is logged per species, along with the closest a wild one
 * has ever let you come and which habitats you tend to meet it in. This is
 * what turns the ethology model from generic instinct into a relationship
 * with a history: a Shadepaw you have fed forty times flees at arm's length,
 * not across the meadow.
 *
 * Authoritative on the phone (works offline, always records); totals are
 * beamed to the glasses save for cross-device persistence.
 */
class FieldRecord {
    var pets = 0; var treats = 0; var berries = 0
    var startles = 0; var encounters = 0
    var closestCm = 9999      // closest a member of this species has tolerated (cm)
    var firstMs = 0L; var lastMs = 0L
    // Nine Whittaker biomes. Old four-biome CSV rows are expanded on read.
    val biome = IntArray(Habitats.BIOMES.size)

    val positive get() = pets + treats + berries
    fun learning(nowMs: Long = System.currentTimeMillis()): LearningState {
        val days = if (lastMs <= 0L) 0f else
            ((nowMs - lastMs).coerceAtLeast(0L) / 86_400_000f)
        return EthoModel.learningState(pets, treats, berries, startles, encounters, days)
    }
    fun familiarity() = learning().familiarity
    fun habituation() = learning().habituation
    fun foodAssociation() = learning().foodExpectation
    fun fear() = learning().fear
    fun favoriteBiome(): Int = biome.indices.maxByOrNull { biome[it] } ?: 0
}

class FieldLog(private val prefs: SharedPreferences) {

    private val records = Array(Species.ALL.size) { FieldRecord() }
    private var loaded = false

    private fun key(s: Int) = "fl_$s"

    fun load() {
        if (loaded) return
        loaded = true
        for (s in records.indices) {
            val raw = prefs.getString(key(s), null) ?: continue
            val p = raw.split(',').mapNotNull { it.toLongOrNull() }
            if (p.size < 8) continue
            records[s].apply {
                pets = p[0].toInt(); treats = p[1].toInt(); berries = p[2].toInt()
                startles = p[3].toInt(); encounters = p[4].toInt(); closestCm = p[5].toInt()
                firstMs = p[6]; lastMs = p[7]
                if (p.size == 12) {
                    // Legacy world: MEADOW, COVE, EMBER HOLLOW, STARFALL SHRINE.
                    // Preserve those observations in the closest new climate.
                    biome[2] = p[8].toInt()  // GLADE / temperate seasonal forest
                    biome[3] = p[9].toInt()  // MISTWOOD / wet temperate forest
                    biome[8] = p[10].toInt() // SCRUB / fire-adapted woodland
                    biome[7] = p[11].toInt() // SAGE / open moon-shadow desert
                } else {
                    for (i in biome.indices) biome[i] = p.getOrNull(8 + i)?.toInt() ?: 0
                }
            }
        }
    }

    /** Seed an empty local log from the glasses dex (survives a reinstall). */
    fun seedFrom(petsArr: IntArray?, treatsArr: IntArray?, berriesArr: IntArray?,
                 startlesArr: IntArray?, closestArr: IntArray?) {
        load()
        for (s in records.indices) {
            val r = records[s]
            if (r.positive + r.startles + r.encounters > 0) continue   // don't clobber real local data
            r.pets = petsArr?.getOrNull(s) ?: 0
            r.treats = treatsArr?.getOrNull(s) ?: 0
            r.berries = berriesArr?.getOrNull(s) ?: 0
            r.startles = startlesArr?.getOrNull(s) ?: 0
            val c = closestArr?.getOrNull(s) ?: 0
            if (c in 1..9998) r.closestCm = c
            if (r.positive + r.startles > 0 || r.closestCm < 9999) save(s)
        }
    }

    operator fun get(s: Int): FieldRecord = run { load(); records[s.coerceIn(0, records.size - 1)] }

    private fun save(s: Int) {
        val r = records[s]
        val values = listOf(
            r.pets, r.treats, r.berries, r.startles, r.encounters, r.closestCm,
            r.firstMs, r.lastMs
        ) + r.biome.toList()
        prefs.edit().putString(key(s), values.joinToString(",")).apply()
    }

    private fun touch(r: FieldRecord) {
        val now = System.currentTimeMillis()
        if (r.firstMs == 0L) r.firstMs = now
        r.lastMs = now
    }

    fun recordEncounter(s: Int, biomeIdx: Int) {
        load(); val r = records[s]
        r.encounters++; touch(r)
        if (biomeIdx in r.biome.indices) r.biome[biomeIdx]++
        save(s)
    }

    fun recordPet(s: Int) { load(); val r = records[s]; r.pets++; touch(r); save(s) }
    fun recordTreat(s: Int) { load(); val r = records[s]; r.treats++; touch(r); save(s) }
    fun recordBerry(s: Int) { load(); val r = records[s]; r.berries++; touch(r); save(s) }
    fun recordStartle(s: Int) { load(); val r = records[s]; r.startles++; touch(r); save(s) }

    fun recordClosest(s: Int, cm: Int) {
        load(); val r = records[s]
        if (cm in 1 until r.closestCm) { r.closestCm = cm; save(s) }
    }

    /** Per-species learned quantities for the renderer's perception model. */
    fun habituationArray(): FloatArray { load(); return FloatArray(records.size) { records[it].habituation() } }
    fun familiarityArray(): FloatArray { load(); return FloatArray(records.size) { records[it].familiarity() } }
    fun foodArray(): FloatArray { load(); return FloatArray(records.size) { records[it].foodAssociation() } }
    fun fearArray(): FloatArray { load(); return FloatArray(records.size) { records[it].fear() } }

    /** Compact totals string for the beam: "s:pets:treats:berries:startles:closest". */
    fun beamLine(s: Int): String {
        val r = records[s]
        return "$s:${r.pets}:${r.treats}:${r.berries}:${r.startles}:${r.closestCm}"
    }
}
