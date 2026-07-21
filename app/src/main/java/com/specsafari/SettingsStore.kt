package com.specsafari

import android.content.Context
import android.os.Build

/** Persistent settings + the hunter's progress. RayNeo hardware detected by identity, not model (guide gotcha #24). */
class SettingsStore(context: Context) {
    private val p = context.getSharedPreferences("specsafari", Context.MODE_PRIVATE)

    private val deviceText = listOf(
        Build.MODEL, Build.MANUFACTURER, Build.BRAND, Build.DEVICE, Build.PRODUCT
    ).joinToString(" ").lowercase()

    private val isRayNeoX3 =
        "rayneo" in deviceText || "leiniao" in deviceText || "ffalcon" in deviceText ||
            ("x3" in deviceText && ("tcl" in deviceText || "falcon" in deviceText))

    init {
        if (isRayNeoX3 && !p.getBoolean("rayneoSbsV1", false)) {
            p.edit().putBoolean("sbs", true).putBoolean("rayneoSbsV1", true).apply()
        }
    }

    var soundVolume: Int
        get() = p.getInt("sndVol", 8)
        set(v) { p.edit().putInt("sndVol", v.coerceIn(0, 10)).apply() }

    var swipeSens: Float
        get() = p.getFloat("swipeSens", 1.0f)
        set(v) { p.edit().putFloat("swipeSens", v.coerceIn(0.4f, 2.5f)).apply() }

    var flipVertical: Boolean
        get() = p.getBoolean("flipV", false)
        set(v) { p.edit().putBoolean("flipV", v).apply() }

    var flipHorizontal: Boolean
        get() = p.getBoolean("flipH", false)
        set(v) { p.edit().putBoolean("flipH", v).apply() }

    var safeTap: Boolean
        get() = p.getBoolean("safeTap", true)
        set(v) { p.edit().putBoolean("safeTap", v).apply() }

    var frameCap30: Boolean
        get() = p.getBoolean("cap30", true)   // an all-day outdoor app; sip power
        set(v) { p.edit().putBoolean("cap30", v).apply() }

    var sbs: Boolean
        get() = p.getBoolean("sbs", isRayNeoX3)
        set(v) { p.edit().putBoolean("sbs", v).apply() }

    /** Restore every preference to its default (never touches progress). */
    fun resetSettings() {
        p.edit()
            .remove("sndVol").remove("swipeSens").remove("flipV").remove("flipH")
            .remove("safeTap").remove("cap30").remove("sbs")
            .apply()
    }

    // ------------------------------------------------------- hunter progress

    var essence: Int
        get() = p.getInt("essence", 0)
        set(v) { p.edit().putInt("essence", v.coerceAtLeast(0)).apply() }

    /** Upgrade tiers 0..5: 0 orb polish, 1 scanner, 2 lure charm, 3 satchel. */
    fun upgradeTier(track: Int): Int = p.getInt("upg$track", 0)
    fun setUpgradeTier(track: Int, tier: Int) {
        p.edit().putInt("upg$track", tier.coerceIn(0, 5)).apply()
    }

    /** The box: JSON array of [speciesIdx, level, epochSeconds] triples. */
    var boxJson: String
        get() = p.getString("box", "[]") ?: "[]"
        set(v) { p.edit().putString("box", v).apply() }

    var lifetimeCaught: Int
        get() = p.getInt("lifeCaught", 0)
        set(v) { p.edit().putInt("lifeCaught", v).apply() }

    var bestLadder: Int
        get() = p.getInt("bestLadder", 0)
        set(v) { p.edit().putInt("bestLadder", v).apply() }

    /** Lifetime meters walked with the hunt running (for the HunterDex). */
    var lifetimeDistanceM: Float
        get() = p.getFloat("lifeDistM", 0f)
        set(v) { p.edit().putFloat("lifeDistM", v).apply() }

    /** Berries found along the way, waiting to be fed in the den. */
    var berries: Int
        get() = p.getInt("berries", 0)
        set(v) { p.edit().putInt("berries", v.coerceAtLeast(0)).apply() }

    /** Bond points per species, CSV. */
    var bondCsv: String
        get() = p.getString("bonds", "") ?: ""
        set(v) { p.edit().putString("bonds", v).apply() }

    /** Field-history database, mirrored from the phone. One row per species,
     *  "pets,treats,berries,startles,closestCm", rows joined by ';'. */
    var fieldCsv: String
        get() = p.getString("fieldDb", "") ?: ""
        set(v) { p.edit().putString("fieldDb", v).apply() }

    /** Lairs and caches used recently, "poiId:epochMin" — so a fresh session
     *  doesn't respawn yesterday's creature in yesterday's spot. */
    var recentPoiCsv: String
        get() = p.getString("recentPois", "") ?: ""
        set(v) { p.edit().putString("recentPois", v).apply() }

    /** Creatures away on a failed fetch: "species,level,epochSec,place" per line. */
    var lostCsv: String
        get() = p.getString("lostOnes", "") ?: ""
        set(v) { p.edit().putString("lostOnes", v).apply() }

    /** Havens where freed creatures settled the land: "lat,lon,vitality,species" per line. */
    var havenCsv: String
        get() = p.getString("havens", "") ?: ""
        set(v) { p.edit().putString("havens", v).apply() }

    /** The nuclear option, invoked from the phone hub's Reset Game. */
    fun wipe() { p.edit().clear().apply() }
}
