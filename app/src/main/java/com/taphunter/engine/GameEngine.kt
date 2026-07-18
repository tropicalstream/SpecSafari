package com.taphunter.engine

import com.taphunter.SettingsStore
import com.taphunter.audio.Audio
import com.taphunter.geo.GeoMath
import com.taphunter.geo.GeoPoint
import com.taphunter.geo.OsmPoi
import com.taphunter.geo.OsmRepository
import com.taphunter.geo.OsmRoad
import com.taphunter.shared.Species
import org.json.JSONArray
import kotlin.math.abs
import kotlin.random.Random

interface Host {
    fun sound(id: Int, pitch: Float = 1f, vol: Float = 1f)
    fun applySettings()
}

enum class State { TITLE, ACQUIRE, HUNT, ENGAGE, RESULT, LOOT, HUB, BOX, UPGRADE, SETTINGS }

/** One caught creature in the box. */
class BoxEntry(val species: Int, val level: Int, val at: Long)

class GameEngine(
    private val store: SettingsStore,
    private val host: Host,
    private val osm: OsmRepository
) {
    companion object {
        val ZOOM_RADII = floatArrayOf(80f, 160f, 320f, 800f)
        val UPGRADE_NAMES = arrayOf("ORB POLISH", "SCANNER", "LURE CHARM", "SATCHEL")
        val UPGRADE_BLURBS = arrayOf(
            "WIDER, SLOWER CAPTURE ARC",
            "QUARRY APPARITION FROM FARTHER",
            "RARE PRISMKIN APPEARS MORE",
            "TREASURE YIELDS MORE ESSENCE"
        )
        const val UPG_ORB = 0; const val UPG_SCANNER = 1; const val UPG_CHARM = 2; const val UPG_SATCHEL = 3
        fun upgradeCost(tier: Int) = 20 shl tier   // 20 40 80 160 320
        const val RESULT_SECONDS = 3.2f
        const val LOOT_SECONDS = 3.5f
        const val ENGAGE_SECONDS = 18f             // spec: interactions stay under 20 s
        const val CAPTURE_RANGE_M = 3f             // spec: creatures engage only within 3 m
        const val CHEST_RANGE_M = 6f
    }

    var state = State.TITLE; private set
    var stateT = 0f; private set
    private var booted = false
    private var pausedAtMs = 0L

    // ------------------------------------------------------------ world
    var player: GeoPoint? = null; private set
    var gpsAccuracy = 999f; private set
    var heading = 0f; private set
    var compassLive = false; private set
    var roads: List<OsmRoad> = emptyList(); private set
    var pois: List<OsmPoi> = emptyList(); private set
    var travelBearing: Float? = null; private set

    val spawner = Spawner(Random(System.nanoTime()))
    var zoomIdx = 1; private set
    val zoomRadius get() = ZOOM_RADII[zoomIdx]
    private var targetIdx = 0
    var pingT = 0f; private set
    private var nearAnnounced = false
    private var ensureCd = 0f

    /** Demo stroll (adb): the fake hunter walks toward the target by itself. */
    var demo = false
    var demoDriver: ((GeoPoint) -> Unit)? = null

    /** Live text from the location stack, shown while acquiring. */
    var locStatus = ""

    // ------------------------------------------------------------ engage
    var engageTarget: Spawn? = null; private set
    var hits = 0; private set
    var misses = 0; private set
    var zoneCenter = 0f; private set
    var zoneWidth = 60f; private set
    var pulseDeg = 0f; private set
    private var pulseSpeed = 140f
    var engageTimer = 0f; private set

    // ------------------------------------------------------------ results
    var resultCaught = false; private set
    var resultSpecies = 0; private set
    var resultLevel = 1; private set
    var lootEssence = 0; private set
    var lootShard = false; private set
    var lootPlace = ""; private set

    // ------------------------------------------------------------ menus
    var menuIdx = 0; private set
    var boxScroll = 0; private set
    var box: MutableList<BoxEntry> = mutableListOf(); private set
    val essence get() = store.essence
    fun tier(track: Int) = store.upgradeTier(track)

    private val hubRows = arrayOf("RETURN TO HUNT", "CREATURE BOX", "UPGRADES", "SETTINGS")
    fun hubRowCount() = hubRows.size
    fun hubRow(i: Int) = hubRows[i]

    // Settings rows: SBS last per suite convention (x3 menu rule).
    private val settingsRows = arrayOf(
        "SOUND", "SWIPE SPEED", "FLIP HORIZONTAL", "FLIP VERTICAL", "SAFE TAP", "RESET SETTINGS", "SBS 3D MODE"
    )
    fun settingsRowCount() = settingsRows.size
    fun settingsRow(i: Int) = settingsRows[i]

    fun boot() {
        if (booted) {
            // A long pause outdoors = a fresh hunt (the ladder restarts at 50 m).
            if (pausedAtMs > 0 && System.currentTimeMillis() - pausedAtMs > 30 * 60_000L &&
                state != State.TITLE
            ) {
                state = State.TITLE; stateT = 0f
            }
            pausedAtMs = 0L
            return
        }
        booted = true
        box = parseBox(store.boxJson)
    }

    fun onAppPause() { pausedAtMs = System.currentTimeMillis() }

    // ------------------------------------------------------------- inputs

    var sessionWalkedM = 0f; private set
    private var flushedWalkedM = 0f

    fun onLocation(p: GeoPoint, accuracy: Float, travel: Float?, walkedM: Float) {
        player = p
        gpsAccuracy = accuracy
        travelBearing = travel
        sessionWalkedM = walkedM
        // Bank lifetime distance in 25 m slices so a crash loses little.
        if (walkedM - flushedWalkedM >= 25f) {
            store.lifetimeDistanceM = store.lifetimeDistanceM + (walkedM - flushedWalkedM)
            flushedWalkedM = walkedM
        }
    }

    fun onHeading(deg: Float) { heading = deg; compassLive = true }

    fun onOsm(r: Collection<OsmRoad>, q: Collection<OsmPoi>) {
        roads = r.toList()
        pois = q.toList()
    }

    // ------------------------------------------------------------- update

    fun update(dt: Float) {
        stateT += dt
        pingT = (pingT - dt).coerceAtLeast(0f)
        when (state) {
            State.ACQUIRE -> {
                player?.let { p ->
                    osm.ensureAround(p)
                    if (stateT > 1.2f) {
                        spawner.beginSession(p)
                        spawner.ensure(p, travelBearing, roads, pois, tier(UPG_CHARM))
                        if (demo) {
                            // The desk demo tours everything: plant a cache too.
                            spawner.treasures += Spawn(
                                GeoMath.destination(p, 135f, 40f),
                                false, 0, 1, "A WAYSIDE CACHE", 0L
                            )
                        }
                        host.sound(Audio.LOCK)
                        host.sound(Audio.TARGET)
                        switch(State.HUNT)
                    }
                }
            }
            State.HUNT -> {
                val p = player ?: return
                ensureCd -= dt
                if (ensureCd <= 0f) {
                    ensureCd = 2.5f
                    osm.ensureAround(p)
                    spawner.creature?.let { c ->
                        if (GeoMath.distanceM(p, c.p) > 250f) osm.ensureAround(c.p)
                    }
                    if (spawner.ensure(p, travelBearing, roads, pois, tier(UPG_CHARM))) {
                        host.sound(Audio.TARGET, 1f, 0.8f)
                    }
                }
                // Near-quarry chime, once per approach.
                val c = spawner.creature
                if (c != null && GeoMath.distanceM(p, c.p) <= CAPTURE_RANGE_M) {
                    if (!nearAnnounced) { nearAnnounced = true; host.sound(Audio.NEAR) }
                } else nearAnnounced = false
                if (demo) demoWalk(dt, p)
            }
            State.ENGAGE -> {
                pulseDeg = (pulseDeg + pulseSpeed * dt) % 360f
                if (pulseDeg < 0f) pulseDeg += 360f
                engageTimer -= dt
                if (engageTimer <= 0f) endEngage(caught = false)
            }
            State.RESULT -> if (stateT >= RESULT_SECONDS) switch(State.HUNT)
            State.LOOT -> if (stateT >= LOOT_SECONDS) switch(State.HUNT)
            else -> {}
        }
    }

    private fun demoWalk(dt: Float, p: GeoPoint) {
        val goal = target()?.p ?: return
        val d = GeoMath.distanceM(p, goal)
        if (d < 1.5f) return
        val step = 1.6f * dt
        val brg = GeoMath.bearingDeg(p, goal)
        demoDriver?.invoke(GeoMath.destination(p, brg, step.coerceAtMost(d)))
    }

    // -------------------------------------------------------------- taps

    fun click() {
        when (state) {
            State.TITLE -> { host.sound(Audio.SELECT); switch(State.ACQUIRE) }
            State.ACQUIRE -> {}
            State.HUNT -> huntClick()
            State.ENGAGE -> judgePulse()
            State.RESULT -> switch(State.HUNT)
            State.LOOT -> switch(State.HUNT)
            State.HUB -> hubActivate()
            State.BOX -> {}
            State.UPGRADE -> buySelected()
            State.SETTINGS -> adjustSetting(+1)
        }
    }

    fun doubleTap() {
        when (state) {
            State.HUNT -> { host.sound(Audio.SELECT); menuIdx = 0; switch(State.HUB) }
            State.ENGAGE -> {   // retreat quietly: no strike, the quarry stays
                host.sound(Audio.BACK); engageTarget = null; switch(State.HUNT)
            }
            State.HUB -> { host.sound(Audio.BACK); switch(State.HUNT) }
            State.BOX, State.UPGRADE, State.SETTINGS -> {
                host.sound(Audio.BACK); menuIdx = 0; switch(State.HUB)
            }
            State.RESULT, State.LOOT -> switch(State.HUNT)
            else -> {}
        }
    }

    fun onBack(): Boolean {
        if (state == State.TITLE) return false
        doubleTap()
        return true
    }

    /** 0 up, 1 down, 2 left, 3 right — one discrete step per swipe. */
    fun swipeDir(dir: Int) {
        when (state) {
            State.HUNT -> when (dir) {
                0 -> { zoomIdx = (zoomIdx - 1).coerceAtLeast(0); host.sound(Audio.SELECT, 1.3f, 0.5f) }
                1 -> { zoomIdx = (zoomIdx + 1).coerceAtMost(ZOOM_RADII.size - 1); host.sound(Audio.SELECT, 0.9f, 0.5f) }
                2 -> cycleTarget(-1)
                3 -> cycleTarget(+1)
            }
            State.HUB -> menuStep(dir, hubRows.size)
            State.UPGRADE -> menuStep(dir, UPGRADE_NAMES.size)
            State.SETTINGS -> {
                if (dir == 2 || dir == 3) adjustSetting(if (dir == 3) +1 else -1)
                else menuStep(dir, settingsRows.size)
            }
            State.BOX -> {
                val rows = boxLines().size
                if (dir == 1) boxScroll = (boxScroll + 1).coerceAtMost((rows - 6).coerceAtLeast(0))
                if (dir == 0) boxScroll = (boxScroll - 1).coerceAtLeast(0)
            }
            else -> {}
        }
    }

    private fun menuStep(dir: Int, count: Int) {
        if (dir == 0) menuIdx = (menuIdx - 1 + count) % count
        if (dir == 1) menuIdx = (menuIdx + 1) % count
        host.sound(Audio.SELECT, 1.1f, 0.5f)
    }

    // -------------------------------------------------------------- hunt

    /** Everything of interest, creature first: markers, target cycling. */
    fun interest(): List<Spawn> {
        val list = mutableListOf<Spawn>()
        spawner.creature?.let { list += it }
        list += spawner.treasures
        return list
    }

    fun target(): Spawn? {
        val list = interest()
        if (list.isEmpty()) return null
        return list[targetIdx.coerceIn(0, list.size - 1)]
    }

    private fun cycleTarget(step: Int) {
        val n = interest().size
        if (n == 0) return
        targetIdx = ((targetIdx + step) % n + n) % n
        host.sound(Audio.SELECT, 1.2f, 0.5f)
    }

    /** Captures happen at arm's length; the scanner extends the APPARITION. */
    fun rangeFor(s: Spawn): Float = if (s.isCreature) CAPTURE_RANGE_M else CHEST_RANGE_M

    /** How far out a creature's 3D apparition materializes over the map. */
    fun appearRange(): Float = 30f + 15f * tier(UPG_SCANNER)

    private fun huntClick() {
        val p = player ?: return
        val c = spawner.creature
        if (c != null && GeoMath.distanceM(p, c.p) <= CAPTURE_RANGE_M) { startEngage(c); return }
        val chest = spawner.treasures.firstOrNull { GeoMath.distanceM(p, it.p) <= CHEST_RANGE_M }
        if (chest != null) { openChest(chest); return }
        pingT = 1.2f
        host.sound(Audio.PING)
    }

    // ------------------------------------------------------------ engage

    private fun startEngage(c: Spawn) {
        engageTarget = c
        hits = 0; misses = 0
        zoneWidth = (64f * (1f + 0.12f * tier(UPG_ORB)))
        zoneCenter = Random.nextFloat() * 360f
        pulseDeg = (zoneCenter + 180f) % 360f
        pulseSpeed = (140f + c.level * 6f) * (1f - 0.06f * tier(UPG_ORB))
        engageTimer = ENGAGE_SECONDS
        host.sound(Audio.ENGAGE)
        switch(State.ENGAGE)
    }

    private fun judgePulse() {
        // Desk demo tours the whole loop; the timing duel is for real hunts.
        val inZone = demo || abs(GeoMath.angleDiff(pulseDeg, zoneCenter)) <= zoneWidth / 2f
        if (inZone) {
            hits++
            host.sound(Audio.HIT, 1f + hits * 0.15f)
            if (hits >= 3) { endEngage(caught = true); return }
            zoneWidth = (zoneWidth - 12f).coerceAtLeast(18f)
            zoneCenter = (zoneCenter + 120f + Random.nextFloat() * 120f) % 360f
            pulseSpeed = -pulseSpeed * 1.18f
        } else {
            misses++
            host.sound(Audio.MISS)
            if (misses >= 3) endEngage(caught = false)
        }
    }

    private fun endEngage(caught: Boolean) {
        val c = engageTarget ?: spawner.creature
        engageTarget = null
        resultCaught = caught
        resultSpecies = c?.species ?: 0
        resultLevel = c?.level ?: 1
        if (caught && c != null) {
            box.add(BoxEntry(c.species, c.level, System.currentTimeMillis() / 1000))
            store.boxJson = dumpBox(box)
            store.lifetimeCaught = store.lifetimeCaught + 1
            val gained = ((2 + c.level) * (1f + 0.2f * tier(UPG_SATCHEL))).toInt()
            store.essence = store.essence + gained
            spawner.onCaught()
            if (spawner.level > store.bestLadder) store.bestLadder = spawner.level
            host.sound(Audio.CATCH)
            host.sound(Audio.LEVEL, 1f, 0.7f)
        } else {
            spawner.onFled()
            host.sound(Audio.FLEE)
        }
        targetIdx = 0
        switch(State.RESULT)
    }

    // ------------------------------------------------------------- loot

    private fun openChest(chest: Spawn) {
        spawner.openTreasure(chest)
        val base = 8 + chest.level * 4
        lootEssence = (base * (1f + 0.2f * tier(UPG_SATCHEL))).toInt()
        lootShard = Random.nextFloat() < 0.2f
        if (lootShard) lootEssence += 5
        lootPlace = chest.placeName
        store.essence = store.essence + lootEssence
        targetIdx = 0
        host.sound(Audio.CHEST)
        host.sound(Audio.ESSENCE, 1f, 0.8f)
        switch(State.LOOT)
    }

    // ------------------------------------------------------------- menus

    private fun hubActivate() {
        host.sound(Audio.SELECT)
        when (menuIdx) {
            0 -> switch(State.HUNT)
            1 -> { boxScroll = 0; switch(State.BOX) }
            2 -> { menuIdx = 0; switch(State.UPGRADE) }
            3 -> { menuIdx = 0; switch(State.SETTINGS) }
        }
    }

    private fun buySelected() {
        val track = menuIdx.coerceIn(0, 3)
        val t = tier(track)
        if (t >= 5) { host.sound(Audio.DENY); return }
        val cost = upgradeCost(t)
        if (store.essence < cost) { host.sound(Audio.DENY); return }
        store.essence = store.essence - cost
        store.setUpgradeTier(track, t + 1)
        host.sound(Audio.UPGRADE)
    }

    private fun adjustSetting(step: Int) {
        when (menuIdx) {
            0 -> store.soundVolume = store.soundVolume + step
            1 -> store.swipeSens = store.swipeSens + step * 0.3f
            2 -> store.flipHorizontal = !store.flipHorizontal
            3 -> store.flipVertical = !store.flipVertical
            4 -> store.safeTap = !store.safeTap
            5 -> { store.resetSettings(); host.sound(Audio.BACK) }
            6 -> store.sbs = !store.sbs
        }
        host.sound(Audio.SELECT, 1.05f, 0.6f)
        host.applySettings()
    }

    fun settingValue(i: Int): String = when (i) {
        0 -> store.soundVolume.toString()
        1 -> String.format("%.1f", store.swipeSens)
        2 -> if (store.flipHorizontal) "ON" else "OFF"
        3 -> if (store.flipVertical) "ON" else "OFF"
        4 -> if (store.safeTap) "ON" else "OFF"
        5 -> ""
        6 -> if (store.sbs) "ON" else "OFF"
        else -> ""
    }

    // --------------------------------------------------------------- box

    /** Aggregated lines: species -> count, best level. */
    fun boxLines(): List<Triple<Int, Int, Int>> {
        val bySpecies = box.groupBy { it.species }
        return bySpecies.entries
            .map { (s, list) -> Triple(s, list.size, list.maxOf { it.level }) }
            .sortedByDescending { it.third }
    }

    // -------------------------------------------------- phone companionship

    /** Progress snapshot for the HunterDex on the phone. */
    fun dexJson(): String {
        val counts = IntArray(Species.ALL.size)
        val best = IntArray(Species.ALL.size)
        val firstAt = LongArray(Species.ALL.size)
        for (e in box) {
            counts[e.species]++
            if (e.level > best[e.species]) best[e.species] = e.level
            if (firstAt[e.species] == 0L || e.at < firstAt[e.species]) firstAt[e.species] = e.at
        }
        val sb = StringBuilder("{")
        sb.append("\"essence\":").append(store.essence)
        sb.append(",\"lifeCaught\":").append(store.lifetimeCaught)
        sb.append(",\"bestLadder\":").append(store.bestLadder)
        sb.append(",\"lifeDistM\":").append(store.lifetimeDistanceM.toInt())
        sb.append(",\"sessionM\":").append(sessionWalkedM.toInt())
        sb.append(",\"huntLevel\":").append(spawner.level)
        sb.append(",\"tiers\":[").append((0..3).joinToString(",") { tier(it).toString() }).append("]")
        sb.append(",\"counts\":[").append(counts.joinToString(",")).append("]")
        sb.append(",\"best\":[").append(best.joinToString(",")).append("]")
        sb.append(",\"firstAt\":[").append(firstAt.joinToString(",")).append("]")
        sb.append("}")
        return sb.toString()
    }

    /** Settings pushed live from the phone hub: SET <key> <value>. */
    fun applyRemoteSetting(key: String, value: String) {
        when (key) {
            "sound" -> value.toIntOrNull()?.let { store.soundVolume = it }
            "swipe" -> value.toFloatOrNull()?.let { store.swipeSens = it }
            "safetap" -> store.safeTap = value == "1"
            "sbs" -> store.sbs = value == "1"
            "fliph" -> store.flipHorizontal = value == "1"
            "flipv" -> store.flipVertical = value == "1"
            else -> return
        }
        host.sound(Audio.SELECT, 1.05f, 0.4f)
        host.applySettings()
    }

    private fun parseBox(json: String): MutableList<BoxEntry> {
        val out = mutableListOf<BoxEntry>()
        runCatching {
            val a = JSONArray(json)
            for (i in 0 until a.length()) {
                val e = a.optJSONArray(i) ?: continue
                out += BoxEntry(e.optInt(0), e.optInt(1), e.optLong(2))
            }
        }
        return out
    }

    private fun dumpBox(list: List<BoxEntry>): String {
        val a = JSONArray()
        for (e in list) a.put(JSONArray().put(e.species).put(e.level).put(e.at))
        return a.toString()
    }

    private fun switch(next: State) {
        state = next
        stateT = 0f
        if (next != State.HUNT) pingT = 0f
    }
}
