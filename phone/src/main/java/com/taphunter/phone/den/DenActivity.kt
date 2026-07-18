package com.taphunter.phone.den

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
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
import com.taphunter.phone.LocationBeamService
import com.taphunter.shared.Species
import org.json.JSONObject
import kotlin.math.abs

/**
 * The Creature Den: ONE living world, sixteen screens wide — meadow into
 * cove into hollow into shrine — walked Minecraft-style. Creatures nest,
 * sleep, play, and sing; the ambience crossfades between four real field
 * recordings as you stroll. Residents can be set free back into the real
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
    private var recordedThisVisit = false

    private var dex: JSONObject? = null
    private var walletText: TextView? = null
    private var envText: TextView? = null
    private var infoName: TextView? = null
    private var infoLine: TextView? = null
    private var hintText: TextView? = null
    private var freeButton: Button? = null
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
            dex = fresh
        }
    }

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
        renderer.setFieldStats(field.habituationArray(), field.familiarityArray(), field.foodArray())
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

    /** Log every resident present in the world once per visit, by habitat. */
    private fun recordVisit() {
        if (recordedThisVisit) return
        recordedThisVisit = true
        val seen = HashSet<Int>()
        for (c in renderer.creatures) if (seen.add(c.species)) {
            val biome = Habitats.BIOMES.indexOf(Habitats.biomeAt(c.x)).coerceAtLeast(0)
            field.recordEncounter(c.species, biome)
        }
        pushFieldStats()
    }

    private fun drainBehavior() {
        var changed = false
        while (true) {
            val ev = renderer.behaviorEvents.poll() ?: break
            when (ev[0]) {
                DenRenderer.BEV_FLEE -> { field.recordStartle(ev[1]); changed = true; beamField(ev[1]) }
                DenRenderer.BEV_SOLICIT -> {
                    field[ev[1]]   // ensure loaded
                    audio?.voice(ev[1], 7)
                    hint("${Species.ALL[ev[1]].name} EDGES CLOSER, HOPEFUL")
                }
                DenRenderer.BEV_CLOSE -> field.recordClosest(ev[1], ev[2])
            }
        }
        if (changed) pushFieldStats()
    }

    // ------------------------------------------------------------ state

    private fun population(): List<Int> {
        if (devMode) return Species.ALL.indices.toList()
        val counts = dex?.optJSONArray("counts") ?: return emptyList()
        val out = mutableListOf<Int>()
        for (s in 0 until Species.ALL.size) {
            val n = counts.optInt(s)
            if (n <= 0) continue
            out += s
            if (n >= 3) out += s
            if (n >= 6) out += s
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
        val i = renderer.pick(x, y)
        if (i < 0) return
        renderer.selected = i
        val c = renderer.creatures.getOrNull(i) ?: return
        val sp = Species.ALL[c.species]
        val armed = armedTreat
        if (armed != null && pouch(armed) > 0) {
            val item = Habitats.item(armed) ?: return
            prefs.edit().putInt("pouch_$armed", pouch(armed) - 1).apply()
            if (pouch(armed) <= 0) armedTreat = null
            pushBond(c.species, item.bondPts)
            if (item.id == "honey" || item.id == "pudding") field.recordTreat(c.species)
            else field.recordBerry(c.species)
            beamField(c.species); pushFieldStats()
            renderer.celebrate(i, big = true)
            audio?.voice(c.species, 1)
            hint("${sp.name} DEVOURED THE ${item.name}! (it will remember)")
        } else {
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
                typeface = Typeface.DEFAULT_BOLD
                setBackgroundColor(Color.TRANSPARENT)
                setOnClickListener {
                    renderer.travelTo(i)
                    hint("STROLLING TO THE ${biome.name}…")
                }
            }
            biomeChips += b
            chips.addView(b, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
        col.addView(chips)

        // The real sky's report card: local hour and local weather.
        envText = TextView(this).apply {
            textSize = 11f; setTextColor(dim); gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
        }
        col.addView(envText)

        col.addView(View(this), LinearLayout.LayoutParams(1, 0, 1f))

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
            setOnClickListener { confirmRelease() }
        }
        freeRow.addView(freeButton)
        col.addView(freeRow)

        hintText = TextView(this).apply {
            textSize = 12f; setTextColor(teal); gravity = Gravity.CENTER
            setPadding(dp(10), 0, dp(10), dp(2))
        }
        col.addView(hintText)
        infoName = TextView(this).apply {
            textSize = 15f; typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
            setTextColor(parchment); setPadding(dp(12), 0, dp(12), 0)
        }
        col.addView(infoName)
        infoLine = TextView(this).apply {
            textSize = 12f; setTextColor(dim); setPadding(dp(12), 0, dp(12), dp(6))
        }
        col.addView(infoLine)

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

    private fun updateInfo() {
        val c = renderer.creatures.getOrNull(renderer.selected)
        freeButton?.visibility = if (c != null && !devMode) View.VISIBLE else View.GONE
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
        val state = when {
            c.sleeping -> "Asleep in its nest"
            c.fleeing -> "Fleeing — you crossed its flight distance"
            c.soliciting -> "Approaching you, hoping for a treat"
            c.alert -> "Watching you warily"
            else -> sp.nature
        }
        val note = if (rec.positive + rec.startles > 0)
            "  ·  met ${rec.encounters}× · pet ${rec.pets} · fed ${rec.treats + rec.berries} · startled ${rec.startles}"
        else ""
        infoLine?.text = state + note
    }

    private fun hint(s: String) { hintText?.text = s }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    // ----------------------------------------------------------- weather

    /** Open-Meteo (no key) at the phone's last position, cached 30 min. */
    private fun fetchWeather() {
        val age = System.currentTimeMillis() - prefs.getLong("wxAt", 0L)
        if (age < 30 * 60_000L) {
            renderer.setWeather(prefs.getInt("wxCode", 0), prefs.getFloat("wxWind", 0f))
            return
        }
        var lat = LocationBeamService.lastLat
        var lon = LocationBeamService.lastLon
        if (lat.isNaN()) {
            runCatching {
                val lm = getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager
                val loc = lm.allProviders.mapNotNull {
                    runCatching { lm.getLastKnownLocation(it) }.getOrNull()
                }.maxByOrNull { it.time }
                if (loc != null) { lat = loc.latitude; lon = loc.longitude }
            }
        }
        if (lat.isNaN()) return   // no position, no forecast; the clock still rules
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
                    .putFloat("wxWind", wind).apply()
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
            refreshDex(); updateWallet(); updateShop(); updateInfo()
            fetchWeather(); updateEnv()
            handler.postDelayed(this, 1500)
        }
    }

    /** Fast pulse: creature voices, ambience mix, and behavior recording. */
    private val audioPump = object : Runnable {
        override fun run() {
            audio?.setListener(renderer.camPX)
            recordVisit()
            drainBehavior()
            while (true) {
                val ev = renderer.audioEvents.poll() ?: break
                val kind = when (ev[0]) {
                    DenRenderer.EV_MEET -> 2
                    DenRenderer.EV_CHASE_END -> 3
                    DenRenderer.EV_RELEASED -> 4
                    else -> 5
                }
                audio?.voice(ev[1], kind)
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
