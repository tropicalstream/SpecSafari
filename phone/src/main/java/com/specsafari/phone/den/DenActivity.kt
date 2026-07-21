package com.specsafari.phone.den

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.opengl.GLSurfaceView
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.*
import com.specsafari.phone.LocationBeamService
import com.specsafari.shared.EthoModel
import com.specsafari.shared.ForageKind
import com.specsafari.shared.SeasonalEcology
import com.specsafari.shared.Species
import com.specsafari.shared.WorldBiome
import org.json.JSONObject
import kotlin.math.abs

/**
 * The Creature Den: one continuous nine-biome transect, walked first-person.
 * Creatures nest, migrate, forage, sleep, play and sing; climate ambience
 * crossfades as you stroll. Residents can be set free back into the real
 * world: tap one, SET FREE, and it runs for the horizon.
 */
class DenActivity : Activity() {

    private val gold = Color.rgb(232, 198, 96)
    private val parchment = Color.rgb(233, 223, 200)
    private val teal = Color.rgb(62, 224, 168)
    private val dim = Color.rgb(140, 160, 178)
    private val cardBg = Color.rgb(20, 31, 44)

    private lateinit var glView: GLSurfaceView
    private lateinit var stick: StickView
    private val renderer = DenRenderer()
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var prefs: android.content.SharedPreferences
    private lateinit var mirror: android.content.SharedPreferences
    private var audio: DenAudio? = null
    private lateinit var field: FieldLog

    private var dex: JSONObject? = null
    private var walletText: TextView? = null
    private var envText: TextView? = null
    private var infoName: TextView? = null
    private var infoLine: TextView? = null
    private var hintText: TextView? = null
    private var freeButton: Button? = null
    private var satchelBtn: Button? = null
    private var satchelPanel: HorizontalScrollView? = null
    private var satchelCards: LinearLayout? = null
    private val shopCards = HashMap<String, View>()
    private val biomeChips = ArrayList<Button>()
    private var armedTreat: String? = null
    private val petAt = HashMap<Int, Long>()

    private val devMode get() = mirror.getBoolean("devMode", false)

    // ----------------------------------------------------------- money

    private fun glassesEssence() = dex?.optInt("essence") ?: 0
    private fun overlay() = prefs.getInt("spentOverlay", 0)
    private fun wallet() = (glassesEssence() - overlay()).coerceAtLeast(0)

    private fun refreshDex() {
        val json = LocationBeamService.dexJson
            ?: getSharedPreferences("hunterdex", Context.MODE_PRIVATE).getString("dex", null)
            ?: return
        runCatching {
            val fresh = JSONObject(json)
            val prevEss = prefs.getInt("lastDexEssence", -1)
            val newEss = fresh.optInt("essence")
            if (prevEss >= 0 && newEss != prevEss) {
                val absorbed = (prevEss - newEss).coerceAtLeast(0)
                prefs.edit().putInt("spentOverlay", (overlay() - absorbed).coerceAtLeast(0)).apply()
            }
            prefs.edit().putInt("lastDexEssence", newEss).apply()
            // Trail berries ride the same ledger: local spends overlay the dex
            // until the glasses absorb them.
            val prevBer = prefs.getInt("lastDexBerries", -1)
            val newBer = fresh.optInt("berries")
            if (prevBer >= 0 && newBer != prevBer) {
                val eaten = (prevBer - newBer).coerceAtLeast(0)
                prefs.edit().putInt("berrySpent",
                    (berryOverlay() - eaten).coerceAtLeast(0)).apply()
            }
            prefs.edit().putInt("lastDexBerries", newBer).apply()
            dex = fresh
        }
    }

    // ------------------------------------------------------------ satchel

    private fun berryOverlay() = prefs.getInt("berrySpent", 0)
    private fun trailBerries() = ((dex?.optInt("berries") ?: 0) - berryOverlay()).coerceAtLeast(0)

    /** Items currently out of the bag: in hand or lying in the world. */
    private fun inPlay(kind: Int): Int {
        var n = if (renderer.carriedKind == kind) 1 else 0
        for (li in renderer.looseItems) if (li.kind == kind) n++
        return n
    }

    private fun satchelCount(kind: Int): Int = when (kind) {
        DenRenderer.KIND_BERRY -> trailBerries() - inPlay(kind)
        DenRenderer.KIND_HONEY -> pouch("honey") - inPlay(kind)
        else -> pouch("pudding") - inPlay(kind)
    }.coerceAtLeast(0)

    private fun spend(amount: Int): Boolean {
        if (devMode) return true
        if (!LocationBeamService.connected) { hint("LINK THE GLASSES TO SPEND"); return false }
        if (wallet() < amount) return false
        prefs.edit().putInt("spentOverlay", overlay() + amount).apply()
        LocationBeamService.sendLine("SET spend $amount")
        return true
    }

    private fun pushBond(species: Int, pts: Int) {
        if (LocationBeamService.connected) LocationBeamService.sendLine("SET bond $species:$pts")
    }

    // -------------------------------------------------- field history DB

    private fun pushFieldStats() {
        renderer.setFieldStats(field.habituationArray(), field.familiarityArray(),
            field.foodArray(), field.fearArray())
    }

    /** Beam the running totals to the glasses save for cross-device persistence. */
    private fun beamField(species: Int) {
        if (LocationBeamService.connected) LocationBeamService.sendLine("SET field ${field.beamLine(species)}")
    }

    private fun seedFieldFromDex() {
        val d = dex ?: return
        fun arr(k: String): IntArray? {
            val a = d.optJSONArray(k) ?: return null
            return IntArray(a.length()) { a.optInt(it) }
        }
        field.seedFrom(arr("flPets"), arr("flTreats"), arr("flBerries"),
            arr("flStartles"), arr("flClosest"))
    }

    private fun drainBehavior() {
        var changed = false
        while (true) {
            val ev = renderer.behaviorEvents.poll() ?: break
            when (ev[0]) {
                DenRenderer.BEV_FLEE -> { field.recordStartle(ev[1]); changed = true; beamField(ev[1]) }
                DenRenderer.BEV_SOLICIT -> {
                    field[ev[1]]   // ensure loaded
                    val g = renderer.voiceGain(ev[1])
                    if (g > 0.03f) audio?.voice(ev[1], 7, g)
                    hint("${Species.ALL[ev[1]].name} EDGES CLOSER, HOPEFUL")
                }
                DenRenderer.BEV_CLOSE -> { field.recordClosest(ev[1], ev[2]); beamField(ev[1]) }
                DenRenderer.BEV_NOTICE -> {
                    field.recordEncounter(ev[1], ev[2]); changed = true
                }
            }
        }
        if (changed) pushFieldStats()
    }

    // ------------------------------------- live moments beamed from the field

    /** The glasses just found treasure / ran a fetch: make the den rejoice.
     *  Events older than 8 s are skipped, so opening the den long afterward
     *  doesn't replay a stale celebration. */
    private fun drainBeamEvents() {
        while (true) {
            val ev = LocationBeamService.events.poll() ?: break
            if (System.currentTimeMillis() - ev.at > 8000L) continue
            val parts = ev.line.split(' ')
            when (parts.getOrNull(0)) {
                "treasure" -> {
                    val ess = parts.getOrNull(1)?.toIntOrNull() ?: 0
                    renderer.treasureCheer(); cheerVoice()
                    hint(if (ess > 0) "⚔ THE HUNTER STRUCK GOLD — +$ess ESSENCE OUT IN THE WILD"
                        else "⚔ A CACHE CRACKED OPEN OUT IN THE WILD")
                }
                "fetchgo" -> {
                    val sp = parts.getOrNull(1)?.toIntOrNull() ?: continue
                    val name = Species.ALL.getOrNull(sp)?.name ?: "A VOLUNTEER"
                    val idx = renderer.creatures.indexOfFirst { it.species == sp }
                    if (idx >= 0) { renderer.questDash(idx); audio?.voice(sp, 5) }
                    hint("$name DASHES OFF ON A FETCH QUEST!")
                }
                "reunion" -> {
                    val sp = parts.getOrNull(1)?.toIntOrNull() ?: continue
                    val name = Species.ALL.getOrNull(sp)?.name ?: "A LOST FRIEND"
                    renderer.treasureCheer()
                    val idx = renderer.creatures.indexOfFirst { it.species == sp }
                    if (idx >= 0) { renderer.celebrate(idx, big = true); audio?.voice(sp, 2) }
                    else cheerVoice()
                    hint("$name FOUND ITS WAY HOME ♥")
                }
                "fetch" -> {
                    val sp = parts.getOrNull(1)?.toIntOrNull() ?: continue
                    val won = parts.getOrNull(2) == "1"
                    val returned = parts.getOrNull(3) == "1"
                    val ess = parts.getOrNull(4)?.toIntOrNull() ?: 0
                    val name = Species.ALL.getOrNull(sp)?.name ?: "YOUR FRIEND"
                    if (won) renderer.treasureCheer()
                    val idx = renderer.creatures.indexOfFirst { it.species == sp }
                    if (returned && idx >= 0) renderer.celebrate(idx, big = true)
                    if (returned) audio?.voice(sp, 2) else cheerVoice()
                    hint(when {
                        won && returned -> "$name FETCHED GOLD (+$ess) AND CAME HOME A HERO ♥"
                        won && !returned -> "$name SENT BACK GOLD (+$ess) — THEN CHASED THE HORIZON…"
                        !won && returned -> "$name FOUND NOTHING, BUT TROTTED PROUDLY HOME"
                        else -> "$name WANDERED OFF EMPTY-PAWED — IT'S OUT THERE TO FIND AGAIN"
                    })
                }
            }
        }
    }

    private fun cheerVoice() {
        val res = renderer.creatures
        if (res.isNotEmpty()) audio?.voice(res[(Math.random() * res.size).toInt()].species, 2)
    }

    // ------------------------------------------------------------ state

    private fun population(): List<Int> {
        if (devMode) return Species.ALL.indices.toList()
        val counts = dex?.optJSONArray("counts") ?: return emptyList()
        val caught = Species.ALL.indices.filter { counts.optInt(it) > 0 }
        // Give every caught species one resident before adding siblings. The old
        // loop let early-index duplicates crowd all rare/ecology species out.
        val priority = listOf(Species.SYLVARCH, Species.MISTCROWN, Species.MOLDEWARP,
            Species.NIXLET, Species.ZEPHYRET, Species.PRISMKIN, Species.SANDSHIFT)
        val ordered = priority.filter { it in caught } + caught.filterNot { it in priority }
        val out = ordered.take(20).toMutableList()
        for (s in ordered) {
            if (out.size >= 20) break
            if (counts.optInt(s) >= 3) out += s
            if (out.size < 20 && counts.optInt(s) >= 6) out += s
        }
        return out.take(20)
    }

    /** The one world's furniture; migrates the old per-stage purchases. */
    private fun placed(): MutableList<String> {
        if (!prefs.contains("items_world")) {
            val merged = LinkedHashSet<String>()
            for (h in 0..3) (prefs.getString("items_$h", "") ?: "")
                .split(',').filter { it.isNotBlank() }.forEach { merged.add(it) }
            prefs.edit().putString("items_world", merged.take(Habitats.SLOTS).joinToString(",")).apply()
        }
        return (prefs.getString("items_world", "") ?: "")
            .split(',').filter { it.isNotBlank() }.toMutableList()
    }

    private fun pouch(id: String) = prefs.getInt("pouch_$id", 0)

    // ------------------------------------------------------------- view

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        actionBar?.hide()
        prefs = getSharedPreferences("den", Context.MODE_PRIVATE)
        mirror = getSharedPreferences("mirror", Context.MODE_PRIVATE)
        field = FieldLog(prefs)
        refreshDex()
        seedFieldFromDex()
        pushFieldStats()

        renderer.configure(population(), placed())
        renderer.resetCamera()

        glView = GLSurfaceView(this).apply {
            setEGLContextClientVersion(2)
            setRenderer(renderer)
        }

        stick = StickView(this)
        var movePid = -1; var lookPid = -1
        var moveOx = 0f; var moveOy = 0f
        var moveDownAt = 0L; var moveMoved = false
        var lookLastX = 0f; var lookLastY = 0f
        var lookDownX = 0f; var lookDownY = 0f; var lookDownAt = 0L; var lookMoved = false
        val stickRadius = dp(64).toFloat()
        glView.setOnTouchListener { v, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                    val i = e.actionIndex; val pid = e.getPointerId(i)
                    if (e.getX(i) < v.width / 2f && movePid < 0) {
                        movePid = pid; moveOx = e.getX(i); moveOy = e.getY(i)
                        moveDownAt = System.currentTimeMillis(); moveMoved = false
                        stick.show(moveOx, moveOy, moveOx, moveOy, stickRadius)
                    } else if (lookPid < 0) {
                        lookPid = pid
                        lookLastX = e.getX(i); lookLastY = e.getY(i)
                        lookDownX = lookLastX; lookDownY = lookLastY
                        lookDownAt = System.currentTimeMillis(); lookMoved = false
                    }
                }
                MotionEvent.ACTION_MOVE -> {
                    for (i in 0 until e.pointerCount) {
                        when (e.getPointerId(i)) {
                            movePid -> {
                                var dx = (e.getX(i) - moveOx) / stickRadius
                                var dy = (e.getY(i) - moveOy) / stickRadius
                                val len = kotlin.math.sqrt(dx * dx + dy * dy)
                                if (len > 1f) { dx /= len; dy /= len }
                                if (abs(e.getX(i) - moveOx) > 24f ||
                                    abs(e.getY(i) - moveOy) > 24f) moveMoved = true
                                renderer.setMove(dx, -dy)
                                stick.show(moveOx, moveOy,
                                    moveOx + dx * stickRadius, moveOy + dy * stickRadius, stickRadius)
                            }
                            lookPid -> {
                                val dx = e.getX(i) - lookLastX; val dy = e.getY(i) - lookLastY
                                if (abs(e.getX(i) - lookDownX) > 24f ||
                                    abs(e.getY(i) - lookDownY) > 24f) lookMoved = true
                                renderer.look(-dx * 0.22f, -dy * 0.18f)
                                lookLastX = e.getX(i); lookLastY = e.getY(i)
                            }
                        }
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                    val pid = e.getPointerId(e.actionIndex)
                    if (pid == movePid) {
                        movePid = -1; renderer.setMove(0f, 0f); stick.hide()
                        if (!moveMoved && System.currentTimeMillis() - moveDownAt < 350L)
                            onTap(moveOx, moveOy)
                    }
                    if (pid == lookPid) {
                        if (!lookMoved && System.currentTimeMillis() - lookDownAt < 350L)
                            onTap(e.getX(e.actionIndex), e.getY(e.actionIndex))
                        lookPid = -1
                    }
                }
                MotionEvent.ACTION_CANCEL -> {
                    movePid = -1; lookPid = -1; renderer.setMove(0f, 0f); stick.hide()
                }
            }
            true
        }

        val root = FrameLayout(this)
        root.addView(glView)
        root.addView(stick)
        root.addView(overlayUi())
        setContentView(root)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun onTap(x: Float, y: Float) {
        // An item in hand goes FIRST: to the chosen creature (the diamond)
        // if it's near and in view — otherwise it's tossed ahead.
        if (renderer.carriedKind >= 0) {
            val si = renderer.selected
            val sc = renderer.creatures.getOrNull(si)
            val kind = renderer.carriedKind
            if (sc != null && !sc.releasing && renderer.distanceToPlayer(si) <= 30f &&
                renderer.isVisible(si) && renderer.clearInteractionPath(si)
            ) {
                renderer.sendCarriedTo(si)
                hint("THE ${kindName(kind)} SAILS TO ${Species.ALL[sc.species].name}…")
            } else {
                renderer.throwCarried()
                hint(when {
                    sc == null -> "TOSSED AHEAD — WALK OVER IT TO TAKE IT BACK"
                    renderer.distanceToPlayer(si) > 30f ->
                        "${Species.ALL[sc.species].name} IS MORE THAN 30 m AWAY — TOSSED AHEAD"
                    else -> "${Species.ALL[sc.species].name} ISN'T VISIBLE IN CLEAR VIEW — TOSSED AHEAD"
                })
            }
            updateSatchel()
            return
        }
        val i = renderer.pick(x, y)
        if (i < 0) return
        renderer.selected = i
        val c = renderer.creatures.getOrNull(i) ?: return
        val sp = Species.ALL[c.species]
        val distance = renderer.distanceToPlayer(i)
        val armed = armedTreat
        if (armed != null && pouch(armed) > 0) {
            if (c.fleeing) {
                hint("${sp.name} IS IN ACTIVE FLIGHT — GIVE IT SPACE, THEN OFFER FOOD CALMLY")
                updateInfo(); return
            }
            val range = renderer.feedRange(i)
            if (distance > range) {
                hint("TOO FAR TO OFFER FOOD — ${"%.1f".format(distance)} m (TOSS RANGE ${"%.1f".format(range)} m)")
                updateInfo(); return
            }
            if (!renderer.clearInteractionPath(i)) {
                hint("A TREE OR ROCK BLOCKS THE FOOD TOSS — STEP INTO A CLEAR LINE")
                updateInfo(); return
            }
            val item = Habitats.item(armed) ?: return
            prefs.edit().putInt("pouch_$armed", pouch(armed) - 1).apply()
            if (pouch(armed) <= 0) armedTreat = null
            pushBond(c.species, item.bondPts)
            if (item.id == "honey" || item.id == "pudding") field.recordTreat(c.species)
            else field.recordBerry(c.species)
            beamField(c.species); pushFieldStats()
            renderer.acceptFeed(i)
            audio?.voice(c.species, 1)
            val learned = field[c.species].learning()
            val ready = EthoModel.thresholds(c.species, learned).approachReady
            hint(if (ready) "${sp.name} ACCEPTS THE ${item.name} — FLIGHT IS REVERSING INTO APPROACH"
                else "${sp.name} ACCEPTS THE ${item.name} — TRUST IS BUILDING")
        } else {
            val reach = renderer.petReach(i)
            if (distance > reach) {
                hint("${sp.name} IS ${"%.1f".format(distance)} m AWAY — WALK WITHIN ${"%.1f".format(reach)} m TO PET")
                updateInfo(); return
            }
            if (c.fleeing) {
                hint("${sp.name} IS FLEEING — STOP, LOOK ASIDE, AND LET IT SETTLE")
                updateInfo(); return
            }
            if (!renderer.clearInteractionPath(i)) {
                hint("YOU CANNOT REACH THROUGH THE HABITAT — STEP AROUND")
                updateInfo(); return
            }
            val now = System.currentTimeMillis()
            if (now - (petAt[c.species] ?: 0L) > 8000L) {
                petAt[c.species] = now
                pushBond(c.species, 1)
                field.recordPet(c.species); beamField(c.species); pushFieldStats()
            }
            renderer.celebrate(i, big = false)
            audio?.voice(c.species, 0)
            hint(if (c.sleeping) "${sp.name} WAKES, MOSTLY FORGIVING"
                else "${sp.name} — THE ${sp.temperament} ONE — ${sp.nature}")
        }
        updateInfo()
        updateShop()
    }

    // -------------------------------------------------------- overlay UI

    private fun overlayUi(): View {
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(10), dp(10), dp(4))
        }
        top.addView(Button(this).apply {
            text = "‹ HUB"; textSize = 13f; typeface = Typeface.DEFAULT_BOLD
            setTextColor(teal); setBackgroundColor(Color.TRANSPARENT)
            setOnClickListener { finish() }
        })
        top.addView(TextView(this).apply {
            text = "CREATURE DEN"; textSize = 18f
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
            setTextColor(gold); gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        walletText = TextView(this).apply {
            textSize = 15f; typeface = Typeface.DEFAULT_BOLD; setTextColor(gold)
        }
        top.addView(walletText)
        col.addView(top)

        // The world's regions — tapping glides you there. One world, no swaps.
        val chips = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(8), 0, dp(8), 0)
        }
        for ((i, biome) in Habitats.BIOMES.withIndex()) {
            val b = Button(this).apply {
                text = biome.name; textSize = 11f
                contentDescription = "${biome.name}, ${WorldBiome.values()[i].label}"
                typeface = Typeface.DEFAULT_BOLD
                minWidth = dp(92)
                setBackgroundColor(Color.TRANSPARENT)
                setOnClickListener {
                    renderer.travelTo(i)
                    hint("STROLLING TO THE ${biome.name}…")
                }
            }
            biomeChips += b
            chips.addView(b, LinearLayout.LayoutParams(dp(96), ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        val biomeScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            isFillViewport = false
            addView(chips, ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        col.addView(biomeScroll, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        // The real sky's report card: local hour and local weather.
        envText = TextView(this).apply {
            textSize = 11f; setTextColor(dim); gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
        }
        col.addView(envText)

        col.addView(View(this), LinearLayout.LayoutParams(1, 0, 1f))

        // The satchel: everything the trail has provided, one tap away.
        satchelCards = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(8), 0, dp(8), dp(2))
        }
        satchelPanel = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            visibility = View.GONE
            addView(satchelCards)
        }
        col.addView(satchelPanel)
        val satchelBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            setPadding(0, 0, dp(12), dp(4))
        }
        satchelBtn = Button(this).apply {
            textSize = 13f; typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.rgb(30, 22, 10))
            background = GradientDrawable().apply {
                setColor(gold); cornerRadius = dp(18).toFloat()
            }
            setPadding(dp(16), dp(6), dp(16), dp(6))
            setOnClickListener { onSatchelTap() }
        }
        satchelBar.addView(satchelBtn)
        col.addView(satchelBar)

        // SET FREE — visible only when someone is chosen.
        val freeRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            setPadding(0, 0, dp(12), dp(4))
        }
        freeButton = Button(this).apply {
            text = "🕊 SET FREE"
            textSize = 13f; typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.rgb(10, 24, 18))
            background = GradientDrawable().apply {
                setColor(teal); cornerRadius = dp(18).toFloat()
            }
            setPadding(dp(16), dp(6), dp(16), dp(6))
            visibility = View.GONE
            // With food in hand this button IS the offer; empty-handed, the farewell.
            setOnClickListener {
                if (renderer.carriedKind >= 0) giveCarried() else confirmRelease()
            }
        }
        freeRow.addView(freeButton)
        col.addView(freeRow)

        hintText = TextView(this).apply {
            textSize = 12f; setTextColor(teal); gravity = Gravity.CENTER
            setPadding(dp(10), 0, dp(10), dp(2))
        }
        col.addView(hintText)
        // The readout sits over the live 3D world, so on a bright daytime den
        // plain light text washed out. A translucent dark panel + shadows keep
        // it legible against any background, and the stats run bold and bright.
        infoName = TextView(this).apply {
            textSize = 16f; typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
            setTextColor(Color.rgb(255, 246, 228))
            setShadowLayer(4f, 0f, 1f, Color.argb(230, 0, 0, 0))
            setPadding(dp(12), dp(7), dp(12), dp(2))
        }
        infoLine = TextView(this).apply {
            textSize = 13f; typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.rgb(150, 236, 255))
            setShadowLayer(4f, 0f, 1f, Color.argb(230, 0, 0, 0))
            setPadding(dp(12), 0, dp(12), dp(8))
        }
        val infoPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(Color.argb(155, 8, 14, 20)); cornerRadius = dp(12).toFloat()
            }
            addView(infoName)
            addView(infoLine)
        }
        col.addView(infoPanel, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(dp(8), 0, dp(8), dp(4)) })

        val shelf = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(8), 0, dp(8), dp(10))
        }
        for (item in Habitats.ITEMS) {
            val card = shopCard(item)
            shopCards[item.id] = card
            shelf.addView(card)
        }
        col.addView(HorizontalScrollView(this).apply {
            addView(shelf); isHorizontalScrollBarEnabled = false
        })
        return col
    }

    private fun shopCard(item: ItemDef): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            background = GradientDrawable().apply {
                setColor(cardBg); cornerRadius = dp(10).toFloat()
                setStroke(dp(1), Color.rgb(62, 88, 110))
            }
            setPadding(dp(8), dp(8), dp(8), dp(8))
            val lp = LinearLayout.LayoutParams(dp(96), ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.rightMargin = dp(8)
            layoutParams = lp
        }
        card.addView(View(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL; setColor(item.cardColor)
            }
        }, LinearLayout.LayoutParams(dp(22), dp(22)))
        card.addView(TextView(this).apply {
            text = item.name; textSize = 10.5f; setTextColor(parchment)
            typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
        })
        card.addView(TextView(this).apply {
            tag = "price"; textSize = 11f; setTextColor(gold); typeface = Typeface.DEFAULT_BOLD
        })
        card.setOnClickListener { buy(item) }
        return card
    }

    private fun buy(item: ItemDef) {
        if (item.treat) {
            if (armedTreat == item.id && pouch(item.id) > 0) {
                hint("TAP A CREATURE TO FEED THE ${item.name}")
                return
            }
            if (!spend(if (devMode) 0 else item.price)) { updateShop(); return }
            prefs.edit().putInt("pouch_${item.id}", pouch(item.id) + 1).apply()
            armedTreat = item.id
            hint("TAP A CREATURE TO FEED THE ${item.name}")
        } else {
            val placedNow = placed()
            if (item.id in placedNow) { hint("ALREADY SOMEWHERE IN THE WORLD"); return }
            if (placedNow.size >= Habitats.SLOTS) { hint("THE WORLD IS FULLY FURNISHED"); return }
            if (!spend(if (devMode) 0 else item.price)) { updateShop(); return }
            placedNow += item.id
            prefs.edit().putString("items_world", placedNow.joinToString(",")).apply()
            renderer.placeItems(placedNow)
            hint("${item.name} PLACED — SOMEONE WILL LOVE IT")
        }
        updateWallet(); updateShop()
    }

    // --------------------------------------------------- satchel behavior

    private fun kindName(kind: Int) = when (kind) {
        DenRenderer.KIND_HONEY -> "HONEY TREAT"
        DenRenderer.KIND_PUDDING -> "ROYAL PUDDING"
        else -> "TRAIL BERRY"
    }

    /** Carrying something? Tap stows it. Otherwise the satchel opens/closes. */
    private fun onSatchelTap() {
        if (renderer.carriedKind >= 0) {
            val k = renderer.stowCarried()
            hint("${kindName(k)} BACK IN THE SATCHEL")
            updateSatchel(); return
        }
        val open = satchelPanel?.visibility == View.VISIBLE
        if (!open) rebuildSatchelCards()
        satchelPanel?.visibility = if (open) View.GONE else View.VISIBLE
    }

    private fun rebuildSatchelCards() {
        val row = satchelCards ?: return
        row.removeAllViews()
        val entries = listOf(
            Triple(DenRenderer.KIND_BERRY, Color.rgb(255, 110, 170), "from the trail"),
            Triple(DenRenderer.KIND_HONEY, Color.rgb(255, 190, 80), "from the shop"),
            Triple(DenRenderer.KIND_PUDDING, Color.rgb(255, 140, 190), "from the shop"))
        for ((kind, color, source) in entries) {
            val n = satchelCount(kind)
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                background = GradientDrawable().apply {
                    setColor(cardBg); cornerRadius = dp(10).toFloat()
                    setStroke(dp(1), Color.rgb(62, 88, 110))
                }
                setPadding(dp(8), dp(8), dp(8), dp(8))
                alpha = if (n > 0) 1f else 0.35f
                val lp = LinearLayout.LayoutParams(dp(104), ViewGroup.LayoutParams.WRAP_CONTENT)
                lp.rightMargin = dp(8)
                layoutParams = lp
                setOnClickListener { equipFromSatchel(kind) }
            }
            card.addView(View(this).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL; setColor(color)
                }
            }, LinearLayout.LayoutParams(dp(22), dp(22)))
            card.addView(TextView(this).apply {
                text = kindName(kind); textSize = 10.5f; setTextColor(parchment)
                typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
            })
            card.addView(TextView(this).apply {
                text = "×$n · $source"; textSize = 10f; setTextColor(gold)
                typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
            })
            row.addView(card)
        }
    }

    private fun equipFromSatchel(kind: Int) {
        if (satchelCount(kind) <= 0) {
            hint(if (kind == DenRenderer.KIND_BERRY)
                "NO BERRIES LEFT — THE TRAIL PROVIDES ONE EVERY 500 M WALKED"
            else "NONE LEFT — THE SHOP SELLS MORE"); return
        }
        renderer.equipItem(kind)
        armedTreat = null   // the satchel supersedes the shop's arming flow
        satchelPanel?.visibility = View.GONE
        hint("CARRYING A ${kindName(kind)} — TAP TO OFFER OR TOSS IT")
        updateSatchel()
    }

    private fun updateSatchel() {
        refreshGiveButton(renderer.creatures.getOrNull(renderer.selected))
        val carrying = renderer.carriedKind
        satchelBtn?.text = if (carrying >= 0) "🎒 STOW ${kindName(carrying)}"
        else {
            val total = satchelCount(DenRenderer.KIND_BERRY) +
                satchelCount(DenRenderer.KIND_HONEY) + satchelCount(DenRenderer.KIND_PUDDING)
            "🎒 SATCHEL ×$total"
        }
    }

    /** Feeding and pickup outcomes reported back by the world. */
    private fun drainItemEvents() {
        while (true) {
            val ev = renderer.itemEvents.poll() ?: break
            when (ev[0]) {
                DenRenderer.IEV_FED -> {
                    val kind = ev[1]; val species = ev[2]
                    val enjoyed = ev.getOrElse(3) { 1 } == 1
                    when (kind) {
                        DenRenderer.KIND_BERRY -> {
                            // Only bank the spend if the glasses heard it — an
                            // unlinked den never drifts from the real satchel.
                            if (LocationBeamService.sendLine("SET berry 1"))
                                prefs.edit().putInt("berrySpent", berryOverlay() + 1).apply()
                            pushBond(species, 3); field.recordBerry(species)
                        }
                        DenRenderer.KIND_HONEY -> {
                            prefs.edit().putInt("pouch_honey", (pouch("honey") - 1).coerceAtLeast(0)).apply()
                            pushBond(species, 2); field.recordTreat(species)
                        }
                        else -> {
                            prefs.edit().putInt("pouch_pudding", (pouch("pudding") - 1).coerceAtLeast(0)).apply()
                            pushBond(species, 5); field.recordTreat(species)
                        }
                    }
                    beamField(species); pushFieldStats()
                    audio?.voice(species, if (enjoyed) 1 else 5)
                    hint(if (enjoyed) "${Species.ALL[species].name} LOVED THE ${kindName(kind)}!"
                        else "${Species.ALL[species].name} EATS, BUT WARILY — TOO FRIGHTENED " +
                            "TO ENJOY IT. KEEP FEEDING, GENTLY.")
                    updateInfo()
                }
                DenRenderer.IEV_PICKUP -> {
                    val kind = ev[1]
                    hint("${kindName(kind)} BACK IN THE SATCHEL")
                }
            }
            updateSatchel()
        }
    }

    /** The GIVE ITEM button: offer what's in hand to the chosen creature. */
    private fun giveCarried() {
        val si = renderer.selected
        val sc = renderer.creatures.getOrNull(si) ?: return
        val kind = renderer.carriedKind
        if (kind < 0) return
        when {
            sc.releasing -> hint("IT IS ALREADY BOUND FOR THE HORIZON")
            renderer.distanceToPlayer(si) > 30f ->
                hint("${Species.ALL[sc.species].name} IS TOO FAR — WALK WITHIN 30 M TO OFFER FOOD")
            !renderer.clearInteractionPath(si) ->
                hint("${Species.ALL[sc.species].name} ISN'T IN CLEAR VIEW — STEP INTO THE OPEN")
            else -> {
                renderer.sendCarriedTo(si)
                hint("THE ${kindName(kind)} SAILS TO ${Species.ALL[sc.species].name}…")
            }
        }
        updateSatchel(); updateInfo()
    }

    // ----------------------------------------------------------- release

    private fun confirmRelease() {
        val c = renderer.creatures.getOrNull(renderer.selected) ?: return
        val sp = Species.ALL[c.species]
        if (devMode) { hint("DEV CREATURES ARE ONLY VISITING"); return }
        if (!LocationBeamService.connected) { hint("LINK THE GLASSES TO SET FREE"); return }
        android.app.AlertDialog.Builder(this)
            .setTitle("Set ${sp.name} free?")
            .setMessage(
                "It returns to the wild places it came from — ${sp.habitat.lowercase()}. " +
                    "One leaves your box; it leaves 3 essence in parting, and its kind " +
                    "will remember you fondly.")
            .setPositiveButton("SET FREE") { _, _ ->
                LocationBeamService.sendLine("SET release ${c.species}")
                val sel = renderer.selected
                renderer.release(sel)
                audio?.voice(c.species, 4)
                freeButton?.visibility = View.GONE
                hint("${sp.name} RUNS FOR THE HORIZON. WALK YOUR STREETS — IT'S OUT THERE.")
            }
            .setNegativeButton("STAY A WHILE", null)
            .show()
    }

    // ------------------------------------------------------------ redraw

    private fun updateWallet() {
        walletText?.text = if (devMode) "◆ DEV" else "◆ ${wallet()}"
    }

    private fun updateShop() {
        val placedNow = placed()
        for (item in Habitats.ITEMS) {
            val card = shopCards[item.id] ?: continue
            val price = card.findViewWithTag<TextView>("price") ?: continue
            val afford = devMode || (wallet() >= item.price && LocationBeamService.connected)
            price.text = when {
                item.treat && pouch(item.id) > 0 -> "×${pouch(item.id)} READY"
                item.id in placedNow -> "PLACED"
                devMode -> "FREE·DEV"
                else -> "◆ ${item.price}"
            }
            card.alpha = if (afford || pouch(item.id) > 0 || item.id in placedNow) 1f else 0.35f
        }
    }

    /** One button, two hats: GIVE the carried food, or SET FREE when empty-handed. */
    private fun refreshGiveButton(c: DenC?) {
        val carrying = renderer.carriedKind >= 0
        freeButton?.visibility =
            if (c != null && (carrying || !devMode)) View.VISIBLE else View.GONE
        freeButton?.text = if (carrying) "🍽 GIVE ${kindName(renderer.carriedKind)}" else "🕊 SET FREE"
        (freeButton?.background as? GradientDrawable)?.setColor(
            if (carrying) Color.rgb(255, 170, 120) else teal)
    }

    private fun updateInfo() {
        val c = renderer.creatures.getOrNull(renderer.selected)
        refreshGiveButton(c)
        if (c == null) {
            infoName?.text = if (renderer.creatures.isEmpty())
                "The world is waiting — catch creatures on the glasses first." else
                "Left thumb walks · right thumb looks · tap to pet."
            infoLine?.text = ""
            return
        }
        val sp = Species.ALL[c.species]
        val bonds = dex?.optJSONArray("bonds")
        val pts = bonds?.optInt(c.species) ?: 0
        val caught = dex?.optJSONArray("counts")?.optInt(c.species) ?: 0
        val hearts = intArrayOf(0, 5, 12, 25, 45, 70).count { pts >= it } - 1
        infoName?.text = "${sp.name}${if (caught > 1) " ×$caught" else ""} · " +
            "the ${sp.temperament.lowercase()} one · " +
            "♥".repeat(hearts.coerceAtLeast(0)) + "♡".repeat((5 - hearts).coerceAtLeast(0))
        val rec = field[c.species]
        val learned = rec.learning()
        val seasonal = SeasonalEcology.of(c.species)
        val forageName = Habitats.FOODS.getOrNull(c.forageFood)?.kind?.let { kind ->
            ForageKind.values().firstOrNull { it.id == kind }?.label
        }
        val state = when {
            c.sleeping -> "Asleep in its nest"
            c.fleeing -> "Fleeing — you crossed its flight distance"
            c.soliciting -> "Approaching you, hoping for a treat"
            c.investigating -> "Curiously investigating your stillness"
            c.migrationTransitT > 0f -> "Migrating through a connected water passage"
            c.migrating -> "Seasonally migrating toward ${Habitats.BIOMES.getOrNull(c.seasonalBiome)?.name ?: "preferred climate"}"
            c.feedingT > 0f -> "Foraging — carefully eating ${forageName ?: "wild food"}"
            c.foraging -> "Foraging for ${forageName ?: "wild food"}"
            c.alert -> "Watching you warily"
            c.aware && c.under -> "Reading your footsteps from underground"
            c.aware -> "Aware of you at range"
            else -> sp.nature
        }
        val note = if (rec.positive + rec.startles > 0)
            "  ·  met ${rec.encounters}× · pet ${rec.pets} · fed ${rec.treats + rec.berries} · startled ${rec.startles}"
        else ""
        val relationship = when {
            learned.fear > .48f -> "sensitized"
            EthoModel.thresholds(c.species, learned).approachReady -> "food-conditioned"
            learned.habituation > .55f -> "habituated"
            learned.familiarity > .25f -> "familiar"
            else -> "unfamiliar"
        }
        val liveRange = if (c.detectionDistance > 0f)
            "  ·  detect ${"%.1f".format(c.detectionDistance)} m · flight ${"%.1f".format(c.flightDistance)} m"
        else ""
        infoLine?.text = "$state$liveRange\n${seasonal.migrationMode.label.replaceFirstChar { it.uppercase() }} · " +
            "hunger ${(c.hunger * 100).toInt()}%\nRelationship: $relationship · " +
            "trust ${(learned.familiarity * 100).toInt()}% · food ${(learned.foodExpectation * 100).toInt()}% · " +
            "fear ${(learned.fear * 100).toInt()}%$note"
    }

    private fun hint(s: String) { hintText?.text = s }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    // ----------------------------------------------------------- weather

    /** Open-Meteo (no key) at the phone's last position, cached 30 min. */
    private fun fetchWeather() {
        val rememberedLat = if (prefs.contains("wxLat")) prefs.getFloat("wxLat", 0f) else Float.NaN
        val liveLat = LocationBeamService.lastLat
        val hemisphereLat = if (!liveLat.isNaN()) liveLat.toFloat() else rememberedLat
        if (!hemisphereLat.isNaN()) renderer.setSouthernHemisphere(hemisphereLat < 0f)
        val age = System.currentTimeMillis() - prefs.getLong("wxAt", 0L)
        if (age < 30 * 60_000L) {
            renderer.setWeather(prefs.getInt("wxCode", 0), prefs.getFloat("wxWind", 0f))
            return
        }
        var lat = LocationBeamService.lastLat
        var lon = LocationBeamService.lastLon
        val mayReadLocation = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (lat.isNaN() && mayReadLocation) {
            runCatching {
                val lm = getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager
                val loc = lm.allProviders.mapNotNull {
                    runCatching { lm.getLastKnownLocation(it) }.getOrNull()
                }.maxByOrNull { it.time }
                if (loc != null) { lat = loc.latitude; lon = loc.longitude }
            }
        }
        if (lat.isNaN()) return   // no position, no forecast; the clock still rules
        renderer.setSouthernHemisphere(lat < 0.0)
        Thread {
            runCatching {
                val url = java.net.URL(
                    "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
                        "&current=temperature_2m,weather_code,wind_speed_10m")
                val json = JSONObject(url.openStream().bufferedReader().readText())
                val cur = json.getJSONObject("current")
                val code = cur.optInt("weather_code")
                val temp = cur.optDouble("temperature_2m")
                val wind = cur.optDouble("wind_speed_10m").toFloat()
                prefs.edit().putLong("wxAt", System.currentTimeMillis())
                    .putInt("wxCode", code).putFloat("wxTemp", temp.toFloat())
                    .putFloat("wxWind", wind).putFloat("wxLat", lat.toFloat()).apply()
                renderer.setWeather(code, wind)
            }
        }.start()
    }

    private fun weatherLabel(code: Int): String = when {
        code >= 95 -> "THUNDER"
        code in 71..77 || code == 85 || code == 86 -> "SNOW"
        code in 51..67 || code in 80..82 -> "RAIN"
        code == 45 || code == 48 -> "FOG"
        code == 3 -> "OVERCAST"
        code in 1..2 -> "SOME CLOUDS"
        else -> "CLEAR"
    }

    private fun updateEnv() {
        val time = android.text.format.DateFormat.getTimeFormat(this).format(java.util.Date())
        val hasWx = prefs.getLong("wxAt", 0L) > 0L
        val wx = if (hasWx)
            " · ${weatherLabel(prefs.getInt("wxCode", 0))} · ${prefs.getFloat("wxTemp", 0f).toInt()}°"
        else ""
        envText?.text = "$time$wx"
    }

    // ----------------------------------------------------------- pulses

    private val poll = object : Runnable {
        override fun run() {
            refreshDex(); updateWallet(); updateShop(); updateInfo(); updateSatchel()
            fetchWeather(); updateEnv()
            handler.postDelayed(this, 1500)
        }
    }

    /** Fast pulse: creature voices, ambience mix, and behavior recording. */
    private val audioPump = object : Runnable {
        override fun run() {
            audio?.setListener(renderer.camPX)
            audio?.setPrecip(renderer.precipRain, renderer.precipSnow)
            drainBehavior()
            drainBeamEvents()
            drainItemEvents()
            while (true) {
                val ev = renderer.audioEvents.poll() ?: break
                val kind = when (ev[0]) {
                    DenRenderer.EV_MEET -> 2
                    DenRenderer.EV_CHASE_END -> 3
                    DenRenderer.EV_RELEASED -> 4
                    DenRenderer.EV_ALARM -> 6
                    DenRenderer.EV_FORAGE -> 1
                    else -> 5
                }
                // Ambient life is heard only when it's near: a creature
                // foraging three biomes away doesn't carry across the world.
                val gain = renderer.voiceGain(ev[1])
                if (gain > 0.03f) audio?.voice(ev[1], kind, gain)
            }
            // The biome you stand in glows in the travel chips.
            val here = Habitats.biomeAt(renderer.camPX)
            for ((i, chip) in biomeChips.withIndex())
                chip.setTextColor(if (Habitats.BIOMES[i] === here) gold else dim)
            handler.postDelayed(this, 150)
        }
    }

    override fun onResume() {
        super.onResume()
        glView.onResume()
        if (audio == null) audio = DenAudio(this)
        handler.post(poll); handler.post(audioPump)
    }

    override fun onPause() {
        glView.onPause()
        handler.removeCallbacksAndMessages(null)
        audio?.release(); audio = null
        super.onPause()
    }
}

/** The floating joystick's on-screen ghost. */
private class StickView(ctx: Context) : View(ctx) {
    private var active = false
    private var ox = 0f; private var oy = 0f
    private var tx = 0f; private var ty = 0f
    private var radius = 100f
    private val ring = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        style = android.graphics.Paint.Style.STROKE; strokeWidth = 4f
        color = Color.argb(90, 255, 255, 255)
    }
    private val puck = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(130, 62, 224, 168)
    }
    init { isClickable = false }
    fun show(originX: Float, originY: Float, thumbX: Float, thumbY: Float, r: Float) {
        active = true; ox = originX; oy = originY; tx = thumbX; ty = thumbY; radius = r
        invalidate()
    }
    fun hide() { active = false; invalidate() }
    override fun onDraw(c: android.graphics.Canvas) {
        if (!active) return
        c.drawCircle(ox, oy, radius, ring)
        c.drawCircle(tx, ty, radius * 0.38f, puck)
    }
}
