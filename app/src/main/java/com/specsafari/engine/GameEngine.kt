package com.specsafari.engine

import com.specsafari.SettingsStore
import com.specsafari.audio.Audio
import com.specsafari.geo.GeoMath
import com.specsafari.geo.GeoPoint
import com.specsafari.geo.OsmPoi
import com.specsafari.geo.OsmRepository
import com.specsafari.geo.OsmRoad
import com.specsafari.shared.Species
import com.specsafari.shared.JourneyCodec
import com.specsafari.shared.JourneyRecord
import com.specsafari.shared.Individual
import com.specsafari.shared.Roster
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import kotlin.math.abs
import kotlin.random.Random

interface Host {
    fun sound(id: Int, pitch: Float = 1f, vol: Float = 1f)
    fun applySettings()
    /** Fire-and-forget event line to the phone (e.g. the den's celebrations); no-op when unlinked. */
    fun beam(line: String) {}
}

enum class State { TITLE, ACQUIRE, HUNT, ENGAGE, RESULT, LOOT, FETCH, RESCUE, HUB, DEN, BOX, UPGRADE, SETTINGS }

/** One creature at home in the den, wandering to its own temperament. */
class DenPet(val species: Int) {
    var x = 0f; var y = 0f
    var vx = 0f; var vy = 0f
    var phase = 0f          // gait animation
    var pauseT = 0f         // standing around, thinking creature thoughts
    var happyT = 0f         // hearts and wiggles after affection/meetings
    var meetCd = 0f         // between social interactions
    // The little biology: a nest spot, waking hours, sleep.
    var homeX = 0f; var homeY = 0f
    var awakeT = 20f
    var sleeping = false
    var homing = false
}

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
        const val CHEST_RANGE_M = 15f              // generous vs street-side GPS drift + anchors set back from the walkway
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
    /** The ecology of release: havens freed creatures leave behind. */
    val eco = EcoWorld({ store.havenCsv }, { store.havenCsv = it })
    private var ecoGiftCd = 0f
    var zoomIdx = 1; private set
    val zoomRadius get() = ZOOM_RADII[zoomIdx]
    private var targetIdx = 0
    var pingT = 0f; private set
    private var nearAnnounced = false
    private var nearChimeCd = 0f
    private var chestAnnounced = false
    private var treasureChimeCd = 0f
    private var targetChimeCd = 0f
    private var ensureCd = 0f

    /** Demo stroll (adb): the fake hunter walks toward the target by itself. */
    var demo = false
    var demoDriver: ((GeoPoint) -> Unit)? = null

    /** Live text from the location stack, shown while acquiring. */
    var locStatus = ""

    // ------------------------------------------------------------ engage
    var engageTarget: Spawn? = null; private set
    // A creature the player retreated from: it won't hijack the action button
    // away from a cache that's also in reach. Cleared when they leave its side.
    private var skippedCreature: Spawn? = null
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
    var reunion = false; private set   // this RESULT is a lost friend come home
    var lootEssence = 0; private set
    var lootShard = false; private set
    var lootPlace = ""; private set

    // ------------------------------------------------------------ menus
    var menuIdx = 0; private set
    var boxScroll = 0; private set
    var box: MutableList<BoxEntry> = mutableListOf(); private set

    // --------------------------------------------------- the roster
    // Every caught creature is an INDIVIDUAL: named at birth, marked for
    // life by its seed, remembered by where and when it was found.
    private var roster: MutableList<Individual> = mutableListOf()
    private var rosterLoaded = false

    private fun loadRoster() {
        if (rosterLoaded) return
        rosterLoaded = true
        roster = Roster.decode(store.rosterJson)
        // Backfill: creatures caught before the register existed get born
        // into it now, so every resident of the box has a name and a story.
        var changed = false
        for (s in Species.ALL.indices) {
            val want = box.count { it.species == s }
            while (roster.count { it.species == s } < want) {
                val seed = Random.nextInt(1, Int.MAX_VALUE)
                roster += Individual(System.currentTimeMillis() + roster.size, s,
                    box.first { it.species == s }.level, seed, "long ago",
                    Roster.autoName(seed), "THE EARLY WILDS")
                changed = true
            }
        }
        if (changed) store.rosterJson = Roster.encode(roster)
    }

    /** Register a newly-arrived box resident, unless it is a known individual
     *  coming home (fetch return, reunion) — counts tell the two apart. */
    private fun bornIfNew(species: Int, level: Int, place: String) {
        loadRoster()
        if (roster.count { it.species == species } >= box.count { it.species == species }) return
        val seed = Random.nextInt(1, Int.MAX_VALUE)
        roster += Individual(System.currentTimeMillis(), species, level, seed,
            LocalDate.now().toString(), Roster.autoName(seed), place)
        store.rosterJson = Roster.encode(roster)
    }

    fun rosterList(): List<Individual> { loadRoster(); return roster }
    val essence get() = store.essence
    fun tier(track: Int) = store.upgradeTier(track)

    private val hubRows = arrayOf("RETURN TO HUNT", "CREATURE DEN", "CREATURE BOX", "UPGRADES", "SETTINGS")
    fun hubRowCount() = hubRows.size
    fun hubRow(i: Int) = hubRows[i]

    // ------------------------------------------------------- den + bonds
    val denPets = mutableListOf<DenPet>()
    var denSel = 0; private set
    var denW = 320f; var denH = 480f          // arena, set by the renderer
    var berries
        get() = store.berries
        private set(v) { store.berries = v }
    private var bonds = IntArray(Species.ALL.size)
    private var bondsLoaded = false
    private var nextBerryAtM = Float.MAX_VALUE
    var toastText = ""; private set
    var toastT = 0f; private set

    private val BERRY_NAMES = arrayOf("MOONBERRY", "EMBERBERRY", "MISTBERRY", "SUNBERRY", "DUSKBERRY")
    private val HEART_STEPS = intArrayOf(0, 5, 12, 25, 45, 70)

    private fun loadBonds() {
        if (bondsLoaded) return
        bondsLoaded = true
        val parts = store.bondCsv.split(',')
        for (i in bonds.indices) bonds[i] = parts.getOrNull(i)?.toIntOrNull() ?: 0
    }

    fun bondPoints(species: Int): Int { loadBonds(); return bonds[species.coerceIn(0, bonds.size - 1)] }

    /** 0..5 hearts, the den's currency of affection. */
    fun bondHearts(species: Int): Int {
        val pts = bondPoints(species)
        var h = 0
        for (i in HEART_STEPS.indices) if (pts >= HEART_STEPS[i]) h = i
        return h
    }

    private fun addBond(species: Int, pts: Int) {
        loadBonds()
        bonds[species] += pts
        store.bondCsv = bonds.joinToString(",")
    }

    // Field-history mirror from the phone: [species][pets,treats,berries,startles,closestCm].
    private val fieldDb = Array(Species.ALL.size) { intArrayOf(0, 0, 0, 0, 9999) }
    private var fieldLoaded = false
    private fun loadField() {
        if (fieldLoaded) return
        fieldLoaded = true
        val rows = store.fieldCsv.split(';')
        for (s in fieldDb.indices) {
            val cols = rows.getOrNull(s)?.split(',') ?: continue
            for (k in 0 until 5) fieldDb[s][k] = cols.getOrNull(k)?.toIntOrNull() ?: fieldDb[s][k]
        }
    }

    // Settings rows: SBS last per suite convention (x3 menu rule).
    private val settingsRows = arrayOf(
        "SOUND", "SWIPE SPEED", "FLIP HORIZONTAL", "FLIP VERTICAL", "SAFE TAP", "RESET SETTINGS", "SBS 3D MODE"
    )
    fun settingsRowCount() = settingsRows.size
    fun settingsRow(i: Int) = settingsRows[i]

    fun boot() {
        ensureJourneyToday()
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

    /** Drop the current session cold (demo -> real handover): fresh hunt. */
    fun abandonSession() {
        engageTarget = null
        switch(State.TITLE)
    }

    /**
     * The origin + RNG seed for a session beginning at [p]. Restarting the app
     * in the same place on the same day returns the SAME pair, so the level-1
     * quarry (and the whole ladder) is reproduced rather than re-rolled — you
     * can't close the app and reopen it to fish for a closer or rarer spawn.
     * A genuine move (>60 m) or a new day mints a fresh hunt. The desk demo is
     * never pinned; it stays a fresh tour each time.
     */
    private fun sessionAnchor(p: GeoPoint): Pair<GeoPoint, Long> {
        if (demo) return p to (System.nanoTime() or 1L)
        val today = LocalDate.now().toString()
        val savedSeed = store.sessionSeed
        if (savedSeed != 0L && store.sessionDay == today) {
            val parts = store.sessionOrigin.split(',')
            val savedLat = parts.getOrNull(0)?.toDoubleOrNull()
            val savedLon = parts.getOrNull(1)?.toDoubleOrNull()
            if (savedLat != null && savedLon != null) {
                val savedOrigin = GeoPoint(savedLat, savedLon)
                if (GeoMath.distanceM(p, savedOrigin) <= 60f) return savedOrigin to savedSeed
            }
        }
        val seed = System.nanoTime() or 1L
        store.sessionSeed = seed
        store.sessionOrigin = "${p.lat},${p.lon}"
        store.sessionDay = today
        return p to seed
    }

    // ------------------------------------------------------------- inputs

    var sessionWalkedM = 0f; private set
    private var flushedWalkedM = 0f
    private var journeySourceM = 0f

    private fun ensureJourneyToday() {
        val today = LocalDate.now().toString()
        if (store.journeyDay != today) store.resetJourney(today)
    }

    private fun journeyIds(csv: String): MutableList<Int> = csv.split(',')
        .mapNotNull { it.toIntOrNull() }.filter { it in Species.ALL.indices }.toMutableList()

    private fun recordFound(species: Int) {
        ensureJourneyToday()
        val ids = journeyIds(store.journeyFoundCsv).apply { add(species) }
        store.journeyFoundCsv = ids.takeLast(64).joinToString(",")
    }

    private fun recordReleased(species: Int) {
        ensureJourneyToday()
        val ids = journeyIds(store.journeyReleasedCsv).apply { add(species) }
        store.journeyReleasedCsv = ids.takeLast(64).joinToString(",")
    }

    private fun recordTreasure(essence: Int) {
        ensureJourneyToday()
        store.journeyTreasures++
        store.journeyEssence += essence.coerceAtLeast(0)
    }

    fun onJourneyRegion(region: String) {
        ensureJourneyToday()
        if (region.isNotBlank()) store.journeyRegion = region
    }

    fun journeyRecord(): JourneyRecord {
        ensureJourneyToday()
        return JourneyRecord(
            day = store.journeyDay,
            distanceM = store.journeyDistanceM.toInt(),
            treasures = store.journeyTreasures,
            essence = store.journeyEssence,
            berries = store.journeyBerries,
            foundSpecies = journeyIds(store.journeyFoundCsv),
            releasedSpecies = journeyIds(store.journeyReleasedCsv),
            region = store.journeyRegion,
        )
    }

    fun onLocation(p: GeoPoint, accuracy: Float, travel: Float?, walkedM: Float) {
        player = p
        gpsAccuracy = accuracy
        travelBearing = travel
        sessionWalkedM = walkedM
        ensureJourneyToday()
        if (walkedM < journeySourceM) journeySourceM = walkedM
        val journeyDelta = (walkedM - journeySourceM).coerceIn(0f, 200f)
        if (!demo && journeyDelta > 0f) store.journeyDistanceM += journeyDelta
        journeySourceM = walkedM
        // Bank lifetime distance in 25 m slices so a crash loses little —
        // but NEVER from a desk demo: the odometer is real steps only.
        if (walkedM < flushedWalkedM) flushedWalkedM = walkedM
        if (!demo && walkedM - flushedWalkedM >= 25f) {
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
        toastT = (toastT - dt).coerceAtLeast(0f)
        if (state == State.DEN) updateDen(dt)
        when (state) {
            State.ACQUIRE -> {
                player?.let { p ->
                    osm.ensureAround(p)
                    if (stateT > 1.2f) {
                        val (origin, seed) = sessionAnchor(p)
                        spawner.beginSession(origin, seed, recentPoiIds())
                        spawner.ensure(origin, travelBearing, roads, pois, tier(UPG_CHARM))
                        if (demo) {
                            // The desk demo tours everything: plant a cache close
                            // enough to reach in a few strides.
                            spawner.treasures += Spawn(
                                // Far enough that the 15 m chest range cannot
                                // swallow every tap near the demo's start.
                                GeoMath.destination(p, 135f, 45f),
                                false, 0, 1, "A WAYSIDE CACHE", 0L
                            )
                            // ...and a lost friend a few strides the other way,
                            // so the reunion walk can be exercised at the desk.
                            spawner.seedLostOne(Spawn(
                                GeoMath.destination(p, 315f, 14f),
                                true, 2, 1, "A DEMO HOLLOW", 0L, lost = true
                            ))
                            // ...and a flourishing haven underfoot, so its grove
                            // + berry/treat bounty show without a live release.
                            eco.seedDemo(p, 4f, 10)
                        }
                        // The trail provides: a berry every 500-1000 m walked.
                        nextBerryAtM = sessionWalkedM +
                            (if (demo) 40f + Random.nextFloat() * 40f
                            else 500f + Random.nextFloat() * 500f)
                        host.sound(Audio.LOCK)
                        host.sound(Audio.TARGET)
                        switch(State.HUNT)
                    }
                }
            }
            State.HUNT -> {
                val p = player ?: return
                ensureCd -= dt
                nearChimeCd = (nearChimeCd - dt).coerceAtLeast(0f)
                targetChimeCd = (targetChimeCd - dt).coerceAtLeast(0f)
                if (ensureCd <= 0f) {
                    ensureCd = 2.5f
                    osm.ensureAround(p)
                    spawner.creature?.let { c ->
                        if (GeoMath.distanceM(p, c.p) > 250f) osm.ensureAround(c.p)
                    }
                    // A garbage fix must not scatter spawns around the map;
                    // only the first creature may place off a coarse guess.
                    if (spawner.creature == null || gpsAccuracy <= 60f || demo) {
                        if (spawner.ensure(p, travelBearing, roads, pois, tier(UPG_CHARM)) &&
                            targetChimeCd <= 0f
                        ) {
                            targetChimeCd = 12f
                            host.sound(Audio.TARGET, 1f, 0.8f)
                        }
                        // A lost friend, if any, waits out on the map to be found.
                        // (Demo seeds its own; real hunts draw from the lost list.)
                        if (!demo) {
                            val lost = lostOnes().firstOrNull()
                            if (lost != null)
                                spawner.ensureLostOne(p, roads, pois, lost.first, lost.second, lost.third)
                            else spawner.clearLostOne()
                        }
                    }
                }
                // Near-quarry chime: once per approach, never off a wild fix,
                // never more than once in 20 s.
                val c = spawner.creature
                if (c != null && GeoMath.distanceM(p, c.p) <= CAPTURE_RANGE_M &&
                    (demo || gpsAccuracy <= 30f)
                ) {
                    if (!nearAnnounced && nearChimeCd <= 0f) {
                        nearAnnounced = true
                        nearChimeCd = 20f
                        host.sound(Audio.NEAR)
                    }
                } else nearAnnounced = false
                // Treasure within 50 m: a distinct chime, once per approach, so
                // a cache underfoot never passes unnoticed (the Los Palomas fix).
                treasureChimeCd = (treasureChimeCd - dt).coerceAtLeast(0f)
                val nearChest = spawner.treasures.any {
                    GeoMath.distanceM(p, it.p) <= 50f && (demo || gpsAccuracy <= 40f)
                }
                if (nearChest) {
                    if (!chestAnnounced && treasureChimeCd <= 0f) {
                        chestAnnounced = true; treasureChimeCd = 18f
                        host.sound(Audio.CHEST, 1f, 0.5f)
                        host.sound(Audio.TARGET, 1.2f, 0.5f)
                    }
                } else chestAnnounced = false
                // Berries underfoot — a flourishing haven ripens them sooner
                // and, at its heart, offers an extra in the same find.
                val vit = eco.vitalityAt(p)
                if (sessionWalkedM >= nextBerryAtM) {
                    val gap = (if (demo) 40f + Random.nextFloat() * 40f
                        else 500f + Random.nextFloat() * 500f)
                    nextBerryAtM = sessionWalkedM + gap / (1f + vit * 0.35f)
                    val bonus = if (vit > 1.5f && Random.nextFloat() < 0.4f) 1 else 0
                    val foundBerries = 1 + bonus
                    berries += foundBerries
                    store.journeyBerries += foundBerries
                    toastText = if (bonus > 0) "THE HAVEN YIELDS TWO ${BERRY_NAMES[Random.nextInt(BERRY_NAMES.size)]}S!"
                        else "FOUND A ${BERRY_NAMES[Random.nextInt(BERRY_NAMES.size)]}!"
                    toastT = 4f
                    host.sound(Audio.ESSENCE, 1.2f)
                    host.sound(Audio.TARGET, 1.3f, 0.6f)
                }
                // Flourishing land also offers the odd extra cache (a treat).
                ecoGiftCd = (ecoGiftCd - dt).coerceAtLeast(0f)
                if (vit > 2f && ecoGiftCd <= 0f && spawner.treasures.size < 4) {
                    ecoGiftCd = 45f
                    val brg = Random.nextFloat() * 360f
                    val at = GeoMath.destination(p, brg, 25f + Random.nextFloat() * 35f)
                    spawner.treasures += Spawn(at, false, 0, 1, "A GIFT OF THE HAVEN", 0L)
                }
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
            State.RESCUE -> {   // the volunteer runs; ~2.6 s of animation
                fetchT += dt
                if (fetchT >= 2.6f) resolveFetch()
            }
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
            State.FETCH -> host.sound(Audio.DENY, 0.8f, 0.35f) // sending is deliberately triple-tap
            State.RESCUE -> {}
            State.RESULT -> switch(State.HUNT)
            State.LOOT -> switch(State.HUNT)
            State.HUB -> hubActivate()
            State.DEN -> petSelected()
            State.BOX -> {}
            State.UPGRADE -> buySelected()
            State.SETTINGS -> adjustSetting(+1)
        }
    }

    fun doubleTap() {
        when (state) {
            State.HUNT -> { host.sound(Audio.SELECT); menuIdx = 0; switch(State.HUB) }
            State.ENGAGE -> {   // retreat quietly: no strike, the quarry stays
                host.sound(Audio.BACK); skippedCreature = engageTarget; engageTarget = null; switch(State.HUNT)
            }
            State.HUB -> { host.sound(Audio.BACK); switch(State.HUNT) }
            State.FETCH -> fetchDecision(send = false)   // double-tap = open it yourself
            State.DEN, State.BOX, State.UPGRADE, State.SETTINGS -> {
                host.sound(Audio.BACK); menuIdx = 0; switch(State.HUB)
            }
            State.RESULT, State.LOOT -> switch(State.HUNT)
            else -> {}
        }
    }

    fun tripleTap() {
        if (state == State.FETCH) fetchDecision(send = true)
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
            State.DEN -> when (dir) {
                2 -> cycleDen(-1)
                3 -> cycleDen(+1)
                0 -> feedSelected(berry = true)
                1 -> feedSelected(berry = false)
            }
            else -> {}
        }
    }

    private fun menuStep(dir: Int, count: Int) {
        if (dir == 0) menuIdx = (menuIdx - 1 + count) % count
        if (dir == 1) menuIdx = (menuIdx + 1) % count
        host.sound(Audio.SELECT, 1.1f, 0.5f)
    }

    // ------------------------------------------- lair memory + lost ones

    /** Lairs served in the last 20 hours stay cold across sessions. */
    private fun recentPoiIds(): Set<Long> {
        val now = System.currentTimeMillis() / 60_000L
        return store.recentPoiCsv.split(';').mapNotNull {
            val p = it.split(':')
            val id = p.getOrNull(0)?.toLongOrNull() ?: return@mapNotNull null
            val at = p.getOrNull(1)?.toLongOrNull() ?: return@mapNotNull null
            if (now - at < 20 * 60 && id != 0L) id else null
        }.toSet()
    }

    private fun rememberPoi(id: Long) {
        if (id == 0L) return
        val now = System.currentTimeMillis() / 60_000L
        val kept = store.recentPoiCsv.split(';').filter {
            val p = it.split(':')
            val at = p.getOrNull(1)?.toLongOrNull() ?: return@filter false
            now - at < 20 * 60 && p.getOrNull(0)?.toLongOrNull() != id
        }
        store.recentPoiCsv = (kept + "$id:$now").takeLast(40).joinToString(";")
    }

    /** Creatures away on a failed fetch, oldest first. */
    fun lostOnes(): List<Triple<Int, Int, String>> =
        store.lostCsv.split('\n').filter { it.isNotBlank() }.mapNotNull {
            // Place names can legitimately contain commas; the first three
            // fields are structural and the remainder is the name.
            val p = it.split(',', limit = 4)
            val s = p.getOrNull(0)?.toIntOrNull() ?: return@mapNotNull null
            val l = p.getOrNull(1)?.toIntOrNull() ?: return@mapNotNull null
            Triple(s, l, p.getOrNull(3) ?: "THE WILDS")
        }

    private fun addLost(species: Int, level: Int, place: String) {
        val lines = store.lostCsv.split('\n').filter { it.isNotBlank() }.takeLast(2)
        store.lostCsv = (lines + "$species,$level,${System.currentTimeMillis() / 1000},$place")
            .joinToString("\n")
    }

    private fun removeLostFirst() {
        store.lostCsv = store.lostCsv.split('\n').filter { it.isNotBlank() }
            .drop(1).joinToString("\n")
    }

    // -------------------------------------------------------------- hunt

    /** Everything of interest, creature first: markers, target cycling. */
    fun interest(): List<Spawn> {
        val list = mutableListOf<Spawn>()
        spawner.creature?.let { list += it }
        spawner.lostOne?.let { list += it }
        list += spawner.wildlife
        list += spawner.treasures
        return list
    }

    /** A lost friend within 5-90 m throws its own heart-lit trail on the disc. */
    fun lostHint(): Triple<Float, Float, Int>? {
        val p = player ?: return null
        val c = spawner.lostOne ?: return null
        val d = GeoMath.distanceM(p, c.p)
        if (d !in 5f..90f) return null
        return Triple(GeoMath.bearingDeg(p, c.p), d, c.species)
    }

    /** The nearest chest for the HUD's gold arrow, when 5-60 m out. */
    fun treasureHint(): Triple<Float, Float, String>? {
        val p = player ?: return null
        val t = spawner.treasures.minByOrNull { GeoMath.distanceM(p, it.p) } ?: return null
        val d = GeoMath.distanceM(p, t.p)
        if (d !in 5f..60f) return null
        return Triple(GeoMath.bearingDeg(p, t.p), d, t.placeName)
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

    /**
     * A RELEASED friend, not a lost one and not a stranger: the haven a freed
     * creature founded remembers its species — a creature of that kind met
     * inside its own haven's reach IS the settled friend, living free. It is
     * visited, never re-captured; a LOST one (failed fetch) is the opposite —
     * it wants rescuing home. This is the distinction between the two fates.
     */
    fun isFreedFriend(c: Spawn): Boolean {
        if (demo || !c.isCreature || c.lost) return false
        return eco.havens.any {
            it.species == c.species && GeoMath.distanceM(c.p, it.p) <= eco.havenRadius(it)
        }
    }

    /** Walking up to a freed friend: a greeting, not a hunt. The bond deepens,
     *  the friend melts back into its thriving ground, and stays free. */
    private fun visitFreed(c: Spawn) {
        addBond(c.species, 1)
        toastText = "${Species.ALL[c.species].name} REMEMBERS YOU — IT IS HOME HERE ♥"
        toastT = 5f
        host.sound(Audio.SELECT)
        host.sound(Audio.LEVEL, 1.15f, 0.5f)
        // It slips back into the green; the ladder quarry re-lairs elsewhere.
        if (c === spawner.creature) spawner.onFled() else spawner.wildlife.remove(c)
    }

    private fun huntClick() {
        val p = player ?: return
        // Nearest quarry across the whole spoke wheel, plus a lost friend if one
        // is out there — but a found friend is welcomed home, not duelled.
        val creatures = listOfNotNull(spawner.creature, spawner.lostOne) + spawner.wildlife
        // A skip only holds while you linger by that creature; walk off, or let
        // it leave, and it's fair game again.
        skippedCreature?.let { s ->
            if (s !in creatures || GeoMath.distanceM(p, s.p) > CAPTURE_RANGE_M) skippedCreature = null
        }
        val cr = creatures.minByOrNull { GeoMath.distanceM(p, it.p) }
        val crInRange = cr != null && GeoMath.distanceM(p, cr.p) <= CAPTURE_RANGE_M
        val chest = spawner.treasures.firstOrNull { GeoMath.distanceM(p, it.p) <= CHEST_RANGE_M }
        // A creature you just retreated from won't hijack a cache you walked up
        // to; but with no cache in reach, tapping again lets you change your mind.
        if (crInRange && !(cr === skippedCreature && chest != null)) {
            skippedCreature = null
            when {
                cr!!.lost -> recoverLost(cr)
                isFreedFriend(cr) -> visitFreed(cr)
                else -> startEngage(cr)
            }
            return
        }
        if (chest != null) { skippedCreature = null; offerChest(chest); return }
        pingT = 1.2f
        host.sound(Audio.PING)
    }

    /** No duel: a friend that wandered off already trusts you. Reaching it is
     *  the whole quest — it leaps back into your box, the bond a little deeper. */
    private fun recoverLost(c: Spawn) {
        box.add(BoxEntry(c.species, c.level, System.currentTimeMillis() / 1000))
        store.boxJson = dumpBox(box)
        bornIfNew(c.species, c.level, c.placeName)  // a stranger's rescue still gets a name
        addBond(c.species, 3)                       // a reunion runs deep
        if (!demo && lostOnes().isNotEmpty()) removeLostFirst()
        spawner.clearLostOne()
        resultCaught = true
        resultSpecies = c.species
        resultLevel = c.level
        recordFound(c.species)
        reunion = true
        host.sound(Audio.CATCH)
        host.sound(Audio.LEVEL, 1.1f, 0.7f)
        host.beam("EVT reunion ${c.species}")       // the den welcomes it too
        targetIdx = 0
        switch(State.RESULT)
    }

    // ------------------------------------------------------ fetch quest

    var fetchChest: Spawn? = null; private set
    var fetchSpecies = -1; private set
    var fetchLevel = 0; private set
    var fetchText = ""; private set
    var fetchStage = 0; private set     // 0 asking, 1 running, 2 outcome
    private var fetchT = 0f
    var fetchWon = false; private set
    var fetchReturned = false; private set
    var fetchWasQuest = false; private set
    fun fetchProgress(): Float = fetchT / 2.6f

    /** A cache within reach: open it, OR — if a low creature can go — ask
     *  whether to send one to fetch it, the intuitive choice up front. */
    private fun offerChest(chest: Spawn) {
        val runt = lowestBoxCreature()
        if (runt == null) { openChest(chest); return }
        fetchChest = chest
        fetchSpecies = runt.species
        fetchLevel = runt.level
        fetchStage = 0
        host.sound(Audio.SELECT)
        switch(State.FETCH)
    }

    /** The weakest creature in the box — the eager volunteer. */
    private fun lowestBoxCreature(): BoxEntry? = box.minByOrNull { it.level }

    /** FETCH screen: triple-tap sends the volunteer; double-tap opens it yourself. */
    private fun fetchDecision(send: Boolean) {
        val chest = fetchChest ?: return
        if (!send) { fetchChest = null; openChest(chest); return }
        // The volunteer sets off. Bond raises the odds it comes home with more.
        val idx = box.indexOfFirst { it.species == fetchSpecies && it.level == fetchLevel }
        if (idx >= 0) { box.removeAt(idx); store.boxJson = dumpBox(box) }
        recordReleased(fetchSpecies)
        fetchStage = 1
        fetchWasQuest = true
        fetchT = 0f
        fetchWon = Random.nextFloat() < 0.90f                  // 90% fetches the gold
        val hearts = bondHearts(fetchSpecies)
        // A beloved creature is far likelier to come back at all: 20% base,
        // climbing with every heart of affection.
        fetchReturned = Random.nextFloat() < (0.20f + hearts * 0.14f)
        host.sound(Audio.ENGAGE)
        host.beam("EVT fetchgo $fetchSpecies")   // the den sees it dash off
        switch(State.RESCUE)   // reuse the running-animation state
    }

    private fun resolveFetch() {
        val chest = fetchChest ?: return
        spawner.openTreasure(chest)
        rememberPoi(chest.poiId)
        if (fetchWon) {
            val base = 8 + chest.level * 4
            lootEssence = (base * (1f + 0.2f * tier(UPG_SATCHEL))).toInt()
            store.essence = store.essence + lootEssence
        } else lootEssence = 0
        recordTreasure(lootEssence)
        if (fetchReturned) {
            box.add(BoxEntry(fetchSpecies, fetchLevel, System.currentTimeMillis() / 1000))
            store.boxJson = dumpBox(box)
            bornIfNew(fetchSpecies, fetchLevel, lootPlace)  // known ones just come home
            addBond(fetchSpecies, 2)     // a shared adventure deepens the bond
            host.sound(Audio.CATCH)
        } else {
            // It wandered off after the treasure — recoverable later.
            addLost(fetchSpecies, fetchLevel, chest.placeName)
            host.sound(Audio.FLEE)
        }
        // Mirror the outcome into the den, if it happens to be open on the phone.
        host.beam("EVT fetch $fetchSpecies ${if (fetchWon) 1 else 0} " +
            "${if (fetchReturned) 1 else 0} $lootEssence")
        lootPlace = chest.placeName
        fetchChest = null
        targetIdx = 0
        switch(State.LOOT)
    }

    // ------------------------------------------------------------ engage

    private fun startEngage(c: Spawn) {
        engageTarget = c
        hits = 0; misses = 0
        // A bonded species trusts you: each heart widens the arc 3%. A creature
        // met in a flourishing haven is calmer still — the freed ones' kindness
        // spreads to their neighbors.
        val vit = eco.vitalityAt(c.p)
        zoneWidth = (64f * (1f + 0.12f * tier(UPG_ORB)) *
            (1f + 0.03f * bondHearts(c.species)) * (1f + 0.05f * vit))
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
        reunion = false
        resultCaught = caught
        resultSpecies = c?.species ?: 0
        resultLevel = c?.level ?: 1
        val isLadder = c != null && c === spawner.creature
        if (caught && c != null) {
            box.add(BoxEntry(c.species, c.level, System.currentTimeMillis() / 1000))
            store.boxJson = dumpBox(box)
            bornIfNew(c.species, c.level, c.placeName)
            rememberPoi(c.poiId)
            store.lifetimeCaught = store.lifetimeCaught + 1
            recordFound(c.species)
            val gained = ((2 + c.level) * (1f + 0.2f * tier(UPG_SATCHEL))).toInt()
            store.essence = store.essence + gained
            if (isLadder) {
                spawner.onCaught()   // only the ladder quarry advances the level
                if (spawner.level > store.bestLadder) store.bestLadder = spawner.level
            } else spawner.wildlife.remove(c)   // wildlife just clears its spoke
            // Raised among the freed, it comes to you already half-tame.
            val vit = eco.vitalityAt(c.p)
            if (vit > 1f) addBond(c.species, (vit * 0.6f).toInt().coerceIn(1, 4))
            host.sound(Audio.CATCH)
            host.sound(Audio.LEVEL, 1f, 0.7f)
        } else {
            if (isLadder) spawner.onFled() else spawner.wildlife.remove(c)
            host.sound(Audio.FLEE)
        }
        targetIdx = 0
        switch(State.RESULT)
    }

    // ------------------------------------------------------------- loot

    private fun openChest(chest: Spawn) {
        spawner.openTreasure(chest)
        rememberPoi(chest.poiId)
        val base = 8 + chest.level * 4
        lootEssence = (base * (1f + 0.2f * tier(UPG_SATCHEL))).toInt()
        lootShard = Random.nextFloat() < 0.2f
        if (lootShard) lootEssence += 5
        lootPlace = chest.placeName
        fetchWasQuest = false
        store.essence = store.essence + lootEssence
        recordTreasure(lootEssence)
        targetIdx = 0
        host.sound(Audio.CHEST)
        host.sound(Audio.ESSENCE, 1f, 0.8f)
        host.beam("EVT treasure $lootEssence")   // the den rejoices too, if it's open
        switch(State.LOOT)
    }

    // ------------------------------------------------------------- menus

    private fun hubActivate() {
        host.sound(Audio.SELECT)
        when (menuIdx) {
            0 -> switch(State.HUNT)
            1 -> enterDen()
            2 -> { boxScroll = 0; switch(State.BOX) }
            3 -> { menuIdx = 0; switch(State.UPGRADE) }
            4 -> { menuIdx = 0; switch(State.SETTINGS) }
        }
    }

    // --------------------------------------------------------------- den

    private fun enterDen() {
        denPets.clear()
        // One representative of every caught species wanders in, most-bonded first.
        val species = boxLines().map { it.first }.sortedByDescending { bondPoints(it) }.take(10)
        for ((i, s) in species.withIndex()) {
            denPets += DenPet(s).apply {
                homeX = 50f + (denW - 100f) * ((i * 7) % 10) / 9f
                homeY = 120f + (denH - 230f) * ((i * 3) % 5) / 4f
                x = homeX + Random.nextFloat() * 40f - 20f
                y = homeY + Random.nextFloat() * 40f - 20f
                phase = Random.nextFloat() * 6f
                awakeT = 15f + Random.nextFloat() * 30f
            }
        }
        denSel = 0
        switch(State.DEN)
    }

    private fun updateDen(dt: Float) {
        val left = 26f; val right = denW - 26f
        val top = 100f; val bottom = denH - 90f
        for ((i, pet) in denPets.withIndex()) {
            val sp = Species.ALL[pet.species]
            pet.phase += dt * (if (pet.sleeping) 0.2f else 1f + sp.energy * 2f)
            pet.happyT = (pet.happyT - dt).coerceAtLeast(0f)
            pet.meetCd = (pet.meetCd - dt).coerceAtLeast(0f)
            // Bedtime: wander while awake, then home to the nest to sleep.
            if (pet.sleeping) {
                pet.awakeT -= dt
                pet.vx = 0f; pet.vy = 0f
                if (pet.awakeT <= 0f) {
                    pet.sleeping = false
                    pet.awakeT = (20f + Random.nextFloat() * 30f) * (0.6f + sp.energy)
                }
                continue
            }
            pet.awakeT -= dt
            if (pet.awakeT <= 0f && !pet.homing) { pet.homing = true }
            if (pet.homing) {
                val dx = pet.homeX - pet.x; val dy = pet.homeY - pet.y
                val d = kotlin.math.sqrt(dx * dx + dy * dy)
                if (d < 10f) {
                    pet.homing = false; pet.sleeping = true
                    pet.awakeT = (8f + Random.nextFloat() * 8f) * (1.6f - sp.energy)
                } else {
                    pet.vx = dx / d * 30f; pet.vy = dy / d * 30f
                    pet.x += pet.vx * dt; pet.y += pet.vy * dt
                }
                continue
            }
            if (pet.pauseT > 0f) {
                pet.pauseT -= dt
                pet.vx *= 0.85f; pet.vy *= 0.85f
            } else {
                // Temperament is the whole physics engine.
                val speed = 8f + sp.energy * 34f
                when (sp.motion) {
                    Species.BURROW -> if (Random.nextFloat() < dt * 0.3f) {
                        // Vanish, tunnel, pop up elsewhere — the glasses-den version.
                        pet.x = 26f + Random.nextFloat() * (denW - 52f)
                        pet.y = 100f + Random.nextFloat() * (denH - 190f)
                        pet.happyT = maxOf(pet.happyT, 0.6f)
                        pet.pauseT = 1f + Random.nextFloat()
                    }
                    Species.SKIM -> {
                        val a = pet.phase * 1.3f
                        pet.vx += (kotlin.math.cos(a) * speed * 1.6f - pet.vx) * dt * 2f
                        pet.vy += (kotlin.math.sin(a * 1.7f) * speed * 0.8f - pet.vy) * dt * 2f
                    }
                    Species.SWIM -> {
                        val a = pet.phase * 0.8f
                        pet.vx += (kotlin.math.cos(a) * speed * 0.9f - pet.vx) * dt * 2f
                        pet.vy += (kotlin.math.sin(a * 1.9f) * speed * 0.5f - pet.vy) * dt * 2f
                    }
                    Species.DART -> if (Random.nextFloat() < dt * sp.energy * 1.4f) {
                        val a = Random.nextFloat() * 6.283f
                        pet.vx = kotlin.math.cos(a) * speed * 2f
                        pet.vy = kotlin.math.sin(a) * speed * 2f
                        pet.pauseT = 0.5f + Random.nextFloat()
                    }
                    Species.HOP -> if (Random.nextFloat() < dt * 1.8f) {
                        val a = Random.nextFloat() * 6.283f
                        pet.vx = kotlin.math.cos(a) * speed * 1.4f
                        pet.vy = kotlin.math.sin(a) * speed * 1.4f
                        pet.pauseT = 0.35f
                    }
                    else -> {
                        val a = pet.phase * (0.3f + sp.energy * 0.4f)
                        pet.vx += (kotlin.math.cos(a) * speed - pet.vx) * dt
                        pet.vy += (kotlin.math.sin(a * 0.7f) * speed * 0.6f - pet.vy) * dt
                        if (Random.nextFloat() < dt * (1f - sp.energy)) pet.pauseT = 1f + Random.nextFloat() * 2f
                    }
                }
                // The gregarious drift together; loners keep their distance.
                var nearest: DenPet? = null; var nd = Float.MAX_VALUE
                for (o in denPets) {
                    if (o === pet) continue
                    val dx = o.x - pet.x; val dy = o.y - pet.y
                    val d2 = dx * dx + dy * dy
                    if (d2 < nd) { nd = d2; nearest = o }
                }
                nearest?.let { o ->
                    val d = kotlin.math.sqrt(nd)
                    val pull = (sp.social - 0.35f) * 14f
                    if (d > 1f) {
                        pet.vx += (o.x - pet.x) / d * pull * dt * 8f
                        pet.vy += (o.y - pet.y) / d * pull * dt * 8f
                    }
                    // A meeting: hearts, chirps, and a polite step apart.
                    if (d < 34f && pet.meetCd <= 0f && o.meetCd <= 0f) {
                        pet.meetCd = 6f; o.meetCd = 6f
                        pet.happyT = 1.6f; o.happyT = 1.6f
                        host.sound(Audio.SELECT, 1.5f, 0.4f)
                        pet.vx -= (o.x - pet.x) * 0.4f; pet.vy -= (o.y - pet.y) * 0.4f
                    }
                }
            }
            pet.x = (pet.x + pet.vx * dt).coerceIn(left, right)
            pet.y = (pet.y + pet.vy * dt).coerceIn(top, bottom)
            if (pet.x <= left || pet.x >= right) pet.vx = -pet.vx
            if (pet.y <= top || pet.y >= bottom) pet.vy = -pet.vy
        }
        if (denSel >= denPets.size) denSel = 0
    }

    private fun cycleDen(step: Int) {
        if (denPets.isEmpty()) return
        denSel = ((denSel + step) % denPets.size + denPets.size) % denPets.size
        host.sound(Audio.SELECT, 1.1f, 0.5f)
    }

    private fun petSelected() {
        val pet = denPets.getOrNull(denSel) ?: return
        pet.happyT = 2.2f
        addBond(pet.species, 1)
        host.sound(Audio.ESSENCE, 1.4f, 0.8f)
    }

    private fun feedSelected(berry: Boolean) {
        val pet = denPets.getOrNull(denSel) ?: return
        if (berry) {
            if (berries <= 0) { host.sound(Audio.DENY); return }
            berries -= 1
            addBond(pet.species, 3)
            host.sound(Audio.TARGET, 1.2f)
        } else {
            if (store.essence < 5) { host.sound(Audio.DENY); return }
            store.essence = store.essence - 5
            addBond(pet.species, 2)
            host.sound(Audio.UPGRADE, 1.2f, 0.7f)
        }
        pet.happyT = 3f
        toastText = "${Species.ALL[pet.species].name} LOVED IT!"
        toastT = 2.5f
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
    fun journeyCode(): String = JourneyCodec.encode(journeyRecord())

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
        val journey = journeyRecord()
        sb.append(",\"journeySummary\":").append(JSONObject.quote(journey.summary()))
        sb.append(",\"journeyCode\":").append(JSONObject.quote(JourneyCodec.encode(journey)))
        loadRoster()
        sb.append(",\"roster\":").append(JSONObject.quote(Roster.encode(roster)))
        sb.append(",\"tiers\":[").append((0..3).joinToString(",") { tier(it).toString() }).append("]")
        sb.append(",\"counts\":[").append(counts.joinToString(",")).append("]")
        sb.append(",\"best\":[").append(best.joinToString(",")).append("]")
        sb.append(",\"firstAt\":[").append(firstAt.joinToString(",")).append("]")
        loadBonds()
        sb.append(",\"berries\":").append(berries)
        sb.append(",\"bonds\":[").append(bonds.joinToString(",")).append("]")
        loadField()
        sb.append(",\"flPets\":[").append(fieldDb.joinToString(",") { it[0].toString() }).append("]")
        sb.append(",\"flTreats\":[").append(fieldDb.joinToString(",") { it[1].toString() }).append("]")
        sb.append(",\"flBerries\":[").append(fieldDb.joinToString(",") { it[2].toString() }).append("]")
        sb.append(",\"flStartles\":[").append(fieldDb.joinToString(",") { it[3].toString() }).append("]")
        sb.append(",\"flClosest\":[").append(fieldDb.joinToString(",") { it[4].toString() }).append("]")
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
            // Phone den commerce: essence spent and bonds earned on the phone
            // land here so the glasses save stays the single truth.
            "spend" -> value.toIntOrNull()?.let { store.essence = store.essence - it } ?: return
            "berry" -> value.toIntOrNull()?.let { berries = berries - it } ?: return
            "field" -> {
                // Phone-authoritative field history for one species, absolute totals.
                val p = value.split(':')
                val s = p.getOrNull(0)?.toIntOrNull() ?: return
                if (s !in Species.ALL.indices) return
                loadField()
                for (k in 0 until 5) fieldDb[s][k] = p.getOrNull(k + 1)?.toIntOrNull() ?: fieldDb[s][k]
                store.fieldCsv = fieldDb.joinToString(";") { it.joinToString(",") }
            }
            "bond" -> {
                val parts = value.split(':')
                val s = parts.getOrNull(0)?.toIntOrNull() ?: return
                val pts = parts.getOrNull(1)?.toIntOrNull() ?: return
                if (s in Species.ALL.indices) addBond(s, pts.coerceIn(1, 8)) else return
            }
            "release" -> {
                // Set one free: the weakest of that species leaves the box,
                // pays a 3-essence parting gift, and deepens the kinship.
                val s = value.toIntOrNull() ?: return
                val idx = box.withIndex().filter { it.value.species == s }
                    .minByOrNull { it.value.level }?.index ?: return
                box.removeAt(idx)
                store.boxJson = dumpBox(box)
                // The same rule picks which INDIVIDUAL leaves the register.
                loadRoster()
                roster.filter { it.species == s }.minByOrNull { it.level }?.let {
                    roster.remove(it)
                    store.rosterJson = Roster.encode(roster)
                }
                store.essence = store.essence + 3
                addBond(s, 2)
                recordReleased(s)
                // It ventures to a habitat of its kind; a fond parting leaves
                // that place kinder and greener for the creatures that remain.
                player?.let { p ->
                    val hearts = bondHearts(s)
                    eco.release(p, s, pois, hearts)?.let { where ->
                        toastText = if (hearts >= 1)
                            "${Species.ALL[s].name} VENTURES TO $where — THE WILDS GROW KINDER"
                        else "${Species.ALL[s].name} SLIPS AWAY TOWARD $where"
                        toastT = 5f
                        host.sound(Audio.LEVEL, 1f, 0.6f)
                    }
                }
            }
            "rename" -> {
                // "id:new name" — the biocard's rename, beamed from the phone.
                val id = value.substringBefore(':').toLongOrNull() ?: return
                val name = value.substringAfter(':', "").trim()
                    .filter { it.isLetterOrDigit() || it == ' ' || it == '-' }.take(14)
                if (name.isBlank()) return
                loadRoster()
                val i = roster.indexOfFirst { it.id == id }
                if (i < 0) return
                roster[i] = roster[i].copy(name = name)
                store.rosterJson = Roster.encode(roster)
            }
            "reset" -> {
                store.wipe()
                eco.havens.clear()
                box = parseBox(store.boxJson)
                roster.clear(); rosterLoaded = false
                bondsLoaded = false
                fieldLoaded = false
                for (row in fieldDb) { row[0] = 0; row[1] = 0; row[2] = 0; row[3] = 0; row[4] = 9999 }
                denPets.clear()
                engageTarget = null
                menuIdx = 0
                state = State.TITLE; stateT = 0f
            }
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
