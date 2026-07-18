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

enum class State { TITLE, ACQUIRE, HUNT, ENGAGE, RESULT, LOOT, HUB, DEN, BOX, UPGRADE, SETTINGS }

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
        const val CHEST_RANGE_M = 10f              // generous vs street-side GPS drift
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
    private var nearChimeCd = 0f
    private var targetChimeCd = 0f
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
        toastT = (toastT - dt).coerceAtLeast(0f)
        if (state == State.DEN) updateDen(dt)
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
                // Berries underfoot.
                if (sessionWalkedM >= nextBerryAtM) {
                    nextBerryAtM = sessionWalkedM +
                        (if (demo) 40f + Random.nextFloat() * 40f
                        else 500f + Random.nextFloat() * 500f)
                    berries += 1
                    toastText = "FOUND A ${BERRY_NAMES[Random.nextInt(BERRY_NAMES.size)]}!"
                    toastT = 4f
                    host.sound(Audio.ESSENCE, 1.2f)
                    host.sound(Audio.TARGET, 1.3f, 0.6f)
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
                host.sound(Audio.BACK); engageTarget = null; switch(State.HUNT)
            }
            State.HUB -> { host.sound(Audio.BACK); switch(State.HUNT) }
            State.DEN, State.BOX, State.UPGRADE, State.SETTINGS -> {
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
        // A bonded species trusts you: each heart widens the arc 3%.
        zoneWidth = (64f * (1f + 0.12f * tier(UPG_ORB)) * (1f + 0.03f * bondHearts(c.species)))
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
                store.essence = store.essence + 3
                addBond(s, 2)
            }
            "reset" -> {
                store.wipe()
                box = parseBox(store.boxJson)
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
