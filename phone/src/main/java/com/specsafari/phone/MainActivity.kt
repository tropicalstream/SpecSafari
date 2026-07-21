package com.specsafari.phone

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import com.specsafari.shared.EcologyModel
import com.specsafari.shared.EthoModel
import com.specsafari.shared.JourneyCodec
import com.specsafari.shared.Season
import com.specsafari.shared.SeasonalEcology
import com.specsafari.shared.Species
import com.specsafari.shared.Sprites
import com.specsafari.shared.WorldBiome
import org.json.JSONObject

/**
 * The hunter's hub: beam control, the HunterDex, the journey ledger, and
 * live glasses settings — all fed by the same Bluetooth link that carries
 * the GPS. RPG-styled, zero dependencies, one file.
 */
class MainActivity : Activity() {

    // ------------------------------------------------------------- palette
    private val bgColor = Color.rgb(9, 15, 26)
    private val cardColor = Color.rgb(20, 31, 44)
    private val cardEdge = Color.rgb(62, 88, 110)
    private val gold = Color.rgb(232, 198, 96)
    private val parchment = Color.rgb(233, 223, 200)
    private val teal = Color.rgb(62, 224, 168)
    private val dim = Color.rgb(140, 160, 178)

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var content: FrameLayout
    private val tabs = mutableListOf<Pair<Button, () -> View>>()
    private var activeTab = 0
    private var journeySubscreen = 0
    private var dex: JSONObject? = null

    // Beam tab widgets refreshed by the poller.
    private var statusText: TextView? = null
    private var fixText: TextView? = null
    private var sentText: TextView? = null
    private var toggleButton: Button? = null

    private val poll = object : Runnable {
        override fun run() {
            statusText?.text = "Link: ${LocationBeamService.linkStatus}"
            fixText?.text = "Fix: ${LocationBeamService.lastFixText}"
            sentText?.text = "Fixes beamed: ${LocationBeamService.fixesSent}"
            toggleButton?.text = if (LocationBeamService.running) "STOP THE BEAM" else "START THE BEAM"
            refreshDex()
            handler.postDelayed(this, 1500)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The system "SpecSafari Hub" bar was overlapping and clipping the app's
        // own gold header and the first card; hide it (as the Den does) so the
        // styled header stands clear with proper breathing room beneath it.
        actionBar?.hide()
        refreshDex()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bgColor)
        }
        root.addView(header())
        content = FrameLayout(this)
        root.addView(content, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        root.addView(navBar())
        setContentView(root)
        showTab(0)
    }

    override fun onResume() {
        super.onResume()
        refreshDex()
        // Returning from the Den must immediately expose newly learned trust,
        // fear, feeds and closest approach in an already-open Dex tab.
        if (::content.isInitialized && activeTab == 1) showTab(1)
        handler.post(poll)
    }
    override fun onPause() { handler.removeCallbacks(poll); super.onPause() }

    private fun refreshDex() {
        val json = LocationBeamService.dexJson
            ?: getSharedPreferences("hunterdex", Context.MODE_PRIVATE).getString("dex", null)
            ?: return
        runCatching { dex = JSONObject(json) }
    }

    // ------------------------------------------------------------ chrome

    private fun header(): View = TextView(this).apply {
        text = "⚔ SPECSAFARI ⚔"
        textSize = 26f
        setTextColor(gold)
        typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
        gravity = Gravity.CENTER
        setPadding(0, dp(18), 0, dp(10))
    }

    private fun navBar(): View {
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(cardColor)
        }
        val names = listOf("BEAM", "DEX", "JOURNEY", "GEAR")
        val builders = listOf<() -> View>({ beamTab() }, { dexTab() }, { journeyTab() }, { gearTab() })
        for (i in names.indices) {
            val b = Button(this).apply {
                text = names[i]
                textSize = 13f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(if (i == activeTab) gold else dim)
                setBackgroundColor(Color.TRANSPARENT)
                setOnClickListener { showTab(i) }
            }
            tabs.add(b to builders[i])
            bar.addView(b, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
        return bar
    }

    private fun showTab(i: Int) {
        activeTab = i
        for ((k, pair) in tabs.withIndex()) pair.first.setTextColor(if (k == i) gold else dim)
        statusText = null; fixText = null; sentText = null; toggleButton = null
        content.removeAllViews()
        content.addView(tabs[i].second())
    }

    private fun scroll(vararg children: View): View {
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(16), dp(14), dp(20))
        }
        for (c in children) col.addView(c)
        return ScrollView(this).apply { addView(col); isVerticalScrollBarEnabled = false }
    }

    private fun card(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = GradientDrawable().apply {
            setColor(cardColor)
            cornerRadius = dp(10).toFloat()
            setStroke(dp(1), cardEdge)
        }
        setPadding(dp(14), dp(12), dp(14), dp(12))
        val lp = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.bottomMargin = dp(10)
        layoutParams = lp
    }

    private fun text(s: String, size: Float, color: Int, bold: Boolean = false, serif: Boolean = false) =
        TextView(this).apply {
            text = s
            textSize = size
            setTextColor(color)
            typeface = when {
                serif -> Typeface.create(Typeface.SERIF, if (bold) Typeface.BOLD else Typeface.NORMAL)
                bold -> Typeface.DEFAULT_BOLD
                else -> Typeface.DEFAULT
            }
            // Serif titles crowd the body line right beneath them; give the
            // header its own breathing room, and keep its tall ascenders from
            // clipping against whatever sits above it.
            if (serif && bold) setPadding(0, dp(2), 0, dp(6))
        }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    // ---------------------------------------------------------------- BEAM

    private fun beamTab(): View {
        val c = card()
        c.addView(text("The Beam", 19f, gold, bold = true, serif = true))
        c.addView(text(
            "Streams this phone's GPS to the glasses over Bluetooth. Pair the " +
                "devices once in Bluetooth settings, start the beam, and go hunt.",
            13.5f, parchment))
        statusText = text("Link: …", 15f, teal, bold = true).also { it.setPadding(0, dp(10), 0, 0); c.addView(it) }
        fixText = text("Fix: …", 13.5f, parchment).also { c.addView(it) }
        sentText = text("Fixes beamed: …", 13.5f, parchment).also { c.addView(it) }
        toggleButton = Button(this).apply {
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(bgColor)
            background = GradientDrawable().apply { setColor(gold); cornerRadius = dp(8).toFloat() }
            setOnClickListener { toggleBeam() }
        }
        val lp = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.topMargin = dp(12)
        c.addView(toggleButton, lp)

        val s = card()
        s.addView(text("This Hunt", 17f, gold, bold = true, serif = true))
        val d = dex
        s.addView(text(
            if (d == null) "No word from the glasses yet — start the beam and the hunt."
            else "Hunt level ${d.optInt("huntLevel", 1)} · walked ${meters(d.optInt("sessionM"))} " +
                "this session · ${d.optInt("essence")} essence banked.",
            13.5f, parchment))
        return scroll(c, s)
    }

    private fun toggleBeam() {
        if (LocationBeamService.running) {
            startService(Intent(this, LocationBeamService::class.java).setAction("stop"))
            return
        }
        val missing = neededPermissions()
        if (missing.isNotEmpty()) { requestPermissions(missing, 1); return }
        startBeam()
    }

    private fun neededPermissions(): Array<String> {
        val wanted = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= 31) wanted += Manifest.permission.BLUETOOTH_CONNECT
        if (Build.VERSION.SDK_INT >= 33) wanted += Manifest.permission.POST_NOTIFICATIONS
        return wanted.filter {
            checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()
    }

    override fun onRequestPermissionsResult(code: Int, perms: Array<String>, results: IntArray) {
        super.onRequestPermissionsResult(code, perms, results)
        if (neededPermissions().isEmpty()) startBeam()
    }

    private fun startBeam() {
        val i = Intent(this, LocationBeamService::class.java)
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(i) else startService(i)
    }

    // ----------------------------------------------------------------- DEX

    private fun dexTab(): View {
        val d = dex
        val counts = d?.optJSONArray("counts")
        val best = d?.optJSONArray("best")
        val bonds = d?.optJSONArray("bonds")
        val devMode = getSharedPreferences("mirror", Context.MODE_PRIVATE).getBoolean("devMode", false)
        val discovered = (0 until Species.ALL.size).count { (counts?.optInt(it) ?: 0) > 0 }
        // The door to the den sits on top of the Dex.
        val denCard = card().apply {
            background = GradientDrawable().apply {
                setColor(Color.rgb(16, 40, 38))
                cornerRadius = dp(10).toFloat()
                setStroke(dp(2), teal)
            }
            addView(text("⌂ CREATURE DEN", 19f, teal, bold = true, serif = true))
            addView(text(
                "One living world across nine climate biomes — your companions migrate, " +
                    "forage, nest, sleep, play, and sing. Pet, feed, furnish… or set one free. " +
                    "Tap to enter.", 13f, parchment))
            setOnClickListener {
                startActivity(android.content.Intent(this@MainActivity,
                    com.specsafari.phone.den.DenActivity::class.java))
            }
        }
        val headerCard = card().apply {
            addView(text("SafariDex", 19f, gold, bold = true, serif = true))
            addView(text(
                "$discovered of ${Species.ALL.size} creatures discovered. " +
                    "Each haunts a kind of real place — walk there and they come. " +
                    "Bond with them in the den: pet, feed berries from the trail, " +
                    "spoil with treats.",
                13.5f, parchment))
        }
        val cards = mutableListOf<View>(denCard, headerCard)
        for (i in Species.ALL.indices) {
            val sp = Species.ALL[i]
            val count = counts?.optInt(i) ?: 0
            val bestL = best?.optInt(i) ?: 0
            val bondPts = bonds?.optInt(i) ?: 0
            val hearts = intArrayOf(0, 5, 12, 25, 45, 70).count { bondPts >= it } - 1
            val known = count > 0 || devMode
            val c = card()
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            // Discovered creatures pose live — the den's own model in miniature,
            // turning on a card-sized stage. The unknown stay silhouettes.
            row.addView(
                if (known) com.specsafari.phone.den.MiniModelView(this, i)
                else SpriteView(this, i, false),
                LinearLayout.LayoutParams(dp(72), dp(72)))
            val info = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(12), 0, 0, 0)
            }
            info.addView(text(
                if (known) "No.${i + 1}  ${sp.name}" else "No.${i + 1}  ???",
                16f, if (known) Color.WHITE else dim, bold = true, serif = true))
            info.addView(text(sp.habitat, 12f, teal, bold = true))
            if (known) {
                info.addView(text("The ${sp.temperament.lowercase().replaceFirstChar { it.uppercase() }} one",
                    12f, Color.rgb(255, 160, 220), bold = true))
                info.addView(text(sp.niche, 11f, Color.rgb(150, 210, 130), bold = true))
                info.addView(text(
                    "Caught ×$count · best L$bestL · " +
                        "♥".repeat(hearts.coerceAtLeast(0)) + "♡".repeat((5 - hearts).coerceAtLeast(0)),
                    12.5f, gold))
            }
            row.addView(info, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            c.addView(row)
            c.addView(text(
                if (known) sp.lore else "Not yet discovered. Its haunt is listed — go look.",
                13f, if (known) parchment else dim).apply { setPadding(0, dp(8), 0, 0) })
            if (known) {
                c.addView(text("Field biology — ${sp.biology}", 12.5f, Color.rgb(150, 190, 165))
                    .apply { setPadding(0, dp(6), 0, 0) })

                // Structured morphology, sensorium, rhythm and social ecology.
                val eco = EcologyModel.of(i)
                c.addView(text("Morphology — ${eco.morphology}", 12.5f, Color.rgb(165, 205, 175))
                    .apply { setPadding(0, dp(6), 0, 0) })
                c.addView(text("Senses — ${eco.sensoryEcology}", 12.5f, Color.rgb(145, 205, 235))
                    .apply { setPadding(0, dp(4), 0, 0) })
                c.addView(text("Activity — ${eco.activity.label.replaceFirstChar { it.uppercase() }}.",
                    12f, Color.rgb(175, 185, 235)).apply { setPadding(0, dp(4), 0, 0) })
                c.addView(text("Social ecology — ${eco.socialEcology}", 12.5f, Color.rgb(190, 175, 225))
                    .apply { setPadding(0, dp(4), 0, 0) })
                c.addView(text("Diet — ${eco.diet}", 12f, Color.rgb(205, 190, 145))
                    .apply { setPadding(0, dp(4), 0, 0) })

                val seasonal = SeasonalEcology.of(i)
                val route = Season.values().joinToString(" · ") { season ->
                    "${season.label.take(2).uppercase()} ${WorldBiome.values()[seasonal.targetBiome(season)].name}"
                }
                c.addView(text(
                    "Seasonal ecology — ${seasonal.migrationMode.label}; ${seasonal.climatePreference}. " +
                        "${seasonal.migrationNote}\n$route",
                    12f, Color.rgb(156, 214, 186)).apply { setPadding(0, dp(4), 0, 0) })
                c.addView(text("Foraging — ${seasonal.forageNote}",
                    12f, Color.rgb(196, 214, 150)).apply { setPadding(0, dp(4), 0, 0) })

                val eth = EthoModel.of(i)
                val reference = EthoModel.referenceThresholds(i)
                c.addView(text(
                    "Player ethology — ${EthoModel.warinessLabel(eth)}; normally detects you near " +
                        "${"%.1f".format(reference.detectionDistance)} m and initiates flight near " +
                        "${"%.1f".format(reference.flightDistance)} m during a visible, closing approach. " +
                        "Calm feeding typically reverses avoidance after about ${EthoModel.treatsToApproach(i)} accepted treats. " +
                        EthoModel.approachTip(eth),
                    12.5f, Color.rgb(150, 190, 245)).apply { setPadding(0, dp(6), 0, 0) })

                // Recorded field history — the phone's local log is authoritative
                // (works offline); fall back to the glasses sync when it's blank.
                val local = getSharedPreferences("den", Context.MODE_PRIVATE)
                    .getString("fl_$i", null)?.split(',')?.mapNotNull { it.toLongOrNull() }
                fun fh(idx: Int, key: String): Int {
                    val lv = local?.getOrNull(idx)?.toInt()
                    if (lv != null && (idx != 5 || lv < 9999)) return lv
                    return d?.optJSONArray(key)?.optInt(i) ?: if (idx == 5) 9999 else 0
                }
                val flPets = fh(0, "flPets")
                val flTreats = fh(1, "flTreats")
                val flBerries = fh(2, "flBerries")
                val flStartles = fh(3, "flStartles")
                val flEncounters = fh(4, "flEncounters")
                val flClosest = fh(5, "flClosest")
                val hasHistory = flPets + flTreats + flBerries + flStartles + flEncounters > 0 || flClosest < 9999
                if (hasHistory) {
                    val closeStr = if (flClosest in 1..9998) " · closest ${"%.1f".format(flClosest / 100f)} m" else ""
                    val learned = EthoModel.learningState(flPets, flTreats, flBerries,
                        flStartles, flEncounters)
                    fun meter(v: Float): String {
                        val n = (v.coerceIn(0f, 1f) * 5).toInt()
                        return "●".repeat(n) + "○".repeat(5 - n)
                    }
                    val current = EthoModel.thresholds(i, learned)
                    val stage = when {
                        learned.fear > .48f -> "sensitized — restore calm distance"
                        current.approachReady -> "conditioned — may voluntarily approach"
                        learned.habituation > .55f -> "habituated — tolerates closer presence"
                        learned.familiarity > .25f -> "familiar — still evaluating you"
                        else -> "unfamiliar"
                    }
                    c.addView(text(
                        "Field notes — encountered ×$flEncounters · pet ×$flPets · treats ×$flTreats · " +
                            "berries ×$flBerries · startled ×$flStartles$closeStr\n" +
                            "Relationship: $stage\nTrust ${meter(learned.familiarity)}  " +
                            "Food ${meter(learned.foodExpectation)}  Fear ${meter(learned.fear)}",
                        12f, Color.rgb(210, 190, 140)).apply { setPadding(0, dp(6), 0, 0) })
                } else {
                    c.addView(text("Field notes — no encounters logged yet. Visit it in the den.",
                        12f, dim).apply { setPadding(0, dp(6), 0, 0) })
                }
                c.addView(text("“${sp.nature}”", 12.5f, dim).apply { setPadding(0, dp(4), 0, 0) })
            }
            cards.add(c)
        }
        return scroll(*cards.toTypedArray())
    }

    /** Shared vector creature, silhouetted until first capture (dex style). */
    private class SpriteView(
        context: Context, private val species: Int, private val known: Boolean
    ) : View(context) {
        private val dark = Paint().apply {
            color = Color.rgb(27, 40, 54)
            xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP)
        }
        override fun onDraw(canvas: Canvas) {
            val s = width * 0.22f
            if (known) {
                Sprites.creature(canvas, species, width / 2f, height / 2f, s, 0.9f, excited = false)
            } else {
                val save = canvas.saveLayer(0f, 0f, width.toFloat(), height.toFloat(), null)
                Sprites.creature(canvas, species, width / 2f, height / 2f, s, 0.9f, excited = false)
                canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), dark)
                canvas.restoreToCount(save)
            }
        }
    }

    // -------------------------------------------------------------- JOURNEY

    private fun rankFor(caught: Int) = when {
        caught >= 60 -> "REALM WARDEN"
        caught >= 30 -> "BEASTMASTER"
        caught >= 15 -> "HUNTER"
        caught >= 5 -> "TRACKER"
        else -> "WANDERER"
    }

    private fun journeyTab(): View {
        val d = dex
        val nav = card()
        nav.addView(text("The Journey", 19f, gold, bold = true, serif = true))
        nav.addView(text(
            "A private daily field note you can copy or exchange as an offline journey code.",
            13f, parchment))
        val choices = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(10), 0, 0)
        }
        listOf("TODAY", "SHARED").forEachIndexed { index, label ->
            choices.addView(Button(this).apply {
                text = label
                textSize = 13f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(if (journeySubscreen == index) bgColor else gold)
                background = GradientDrawable().apply {
                    setColor(if (journeySubscreen == index) gold else cardColor)
                    cornerRadius = dp(7).toFloat()
                    setStroke(dp(1), gold)
                }
                setOnClickListener {
                    journeySubscreen = index
                    showTab(2)
                }
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                if (index > 0) leftMargin = dp(8)
            })
        }
        nav.addView(choices)

        if (journeySubscreen == 1) return sharedJourneys(nav)

        val daily = card()
        daily.addView(text("Today's Walk", 17f, gold, bold = true, serif = true))
        val compactCode = getSharedPreferences("journeys", Context.MODE_PRIVATE)
            .getString("currentCode", null)
        val compactJourney = compactCode?.let(JourneyCodec::decode)
        if (d == null && compactJourney == null) {
            daily.addView(text("The daily note appears after the glasses report in.", 13.5f, parchment))
            return scroll(nav, daily)
        }
        val summary = compactJourney?.summary()
            ?: d?.optString("journeySummary", "No walk recorded today.")
            ?: "No walk recorded today."
        val code = compactCode ?: d?.optString("journeyCode").orEmpty()
        daily.addView(text(summary, 14f, parchment))
        if (code.isNotBlank()) {
            daily.addView(codeBlock(code))
            daily.addView(copyButton(code))
        }

        if (d == null) return scroll(nav, daily)

        val caught = d.optInt("lifeCaught")
        val c = card()
        c.addView(text("Lifetime Rank", 17f, gold, bold = true, serif = true))
        c.addView(text(rankFor(caught), 22f, teal, bold = true, serif = true))
        c.addView(text("Rank rises with lifetime captures.", 12f, dim))

        fun statRow(label: String, value: String): View {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, dp(6), 0, 0)
            }
            row.addView(text(label, 14f, parchment),
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(text(value, 14f, gold, bold = true))
            return row
        }
        val s = card()
        s.addView(text("Ledger", 17f, gold, bold = true, serif = true))
        s.addView(statRow("Total distance traveled", meters(d.optInt("lifeDistM"))))
        s.addView(statRow("This session", meters(d.optInt("sessionM"))))
        s.addView(statRow("Creatures captured", "$caught"))
        s.addView(statRow("Deepest hunt", "Level ${d.optInt("bestLadder").coerceAtLeast(1)}"))
        s.addView(statRow("Essence held", "${d.optInt("essence")} ◆"))
        s.addView(statRow("Trail berries in satchel", "${d.optInt("berries")} ●"))

        val u = card()
        u.addView(text("Gear of the Hunt", 17f, gold, bold = true, serif = true))
        val tiers = d.optJSONArray("tiers")
        val names = listOf("Orb Polish", "Scanner", "Lure Charm", "Satchel")
        for (i in names.indices) {
            val tier = tiers?.optInt(i) ?: 0
            u.addView(statRow(names[i], "◆".repeat(tier).padEnd(5, '·')))
        }
        return scroll(nav, daily, c, s, u)
    }

    /** Importable codes are self-contained and checksum-protected, so this
     * subscreen works offline and never needs a journey server. */
    private fun sharedJourneys(nav: View): View {
        val import = card()
        import.addView(text("Import a Journey", 17f, gold, bold = true, serif = true))
        import.addView(text(
            "Paste a friend's SSJ1 code to view its privacy-safe field note.",
            13f, parchment))
        val field = EditText(this).apply {
            hint = "SSJ1…"
            textSize = 13f
            setTextColor(Color.WHITE)
            setHintTextColor(dim)
            setSingleLine(false)
            minLines = 2
            setPadding(dp(10), dp(8), dp(10), dp(8))
            background = GradientDrawable().apply {
                setColor(bgColor)
                cornerRadius = dp(7).toFloat()
                setStroke(dp(1), cardEdge)
            }
        }
        import.addView(field, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(9) })
        import.addView(Button(this).apply {
            text = "IMPORT JOURNEY"
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(bgColor)
            background = GradientDrawable().apply {
                setColor(gold); cornerRadius = dp(7).toFloat()
            }
            setOnClickListener {
                val normalized = field.text.toString().filterNot(Char::isWhitespace)
                val journey = JourneyCodec.decode(normalized)
                if (journey == null) {
                    Toast.makeText(this@MainActivity,
                        "That journey code is incomplete or mistyped.", Toast.LENGTH_LONG).show()
                } else {
                    val prefs = getSharedPreferences("journeys", Context.MODE_PRIVATE)
                    val saved = prefs.getStringSet("imported", emptySet())?.toMutableSet()
                        ?: mutableSetOf()
                    saved += JourneyCodec.encode(journey)
                    prefs.edit().putStringSet("imported", saved).apply()
                    Toast.makeText(this@MainActivity, "Journey added.", Toast.LENGTH_SHORT).show()
                    showTab(2)
                }
            }
        }, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(9) })

        val cards = mutableListOf<View>(nav, import)
        val saved = getSharedPreferences("journeys", Context.MODE_PRIVATE)
            .getStringSet("imported", emptySet()).orEmpty()
            .mapNotNull { code -> JourneyCodec.decode(code)?.let { Triple(it.day, code, it) } }
            .sortedByDescending { it.first }
        if (saved.isEmpty()) {
            cards += card().apply {
                addView(text("No shared journeys yet.", 13.5f, dim))
            }
        } else {
            saved.forEach { (_, code, journey) ->
                cards += card().apply {
                    addView(text(journey.day.ifBlank { "Shared Journey" }, 17f, teal,
                        bold = true, serif = true))
                    addView(text(journey.summary(), 14f, parchment))
                    addView(codeBlock(code))
                    addView(copyButton(code))
                }
            }
        }
        return scroll(*cards.toTypedArray())
    }

    private fun codeBlock(code: String): TextView = text(code, 11f, dim).apply {
        setTextIsSelectable(true)
        setPadding(0, dp(9), 0, dp(4))
    }

    private fun copyButton(code: String): Button = Button(this).apply {
        text = "COPY JOURNEY CODE"
        textSize = 13f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(gold)
        background = GradientDrawable().apply {
            setColor(bgColor); cornerRadius = dp(7).toFloat(); setStroke(dp(1), gold)
        }
        setOnClickListener {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("SpecSafari Journey", code))
            Toast.makeText(this@MainActivity, "Journey code copied.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun meters(m: Int): String =
        if (m < 1000) "$m m" else String.format("%.1f km", m / 1000f)

    // ----------------------------------------------------------------- GEAR

    private fun gearTab(): View {
        val prefs = getSharedPreferences("mirror", Context.MODE_PRIVATE)
        val c = card()
        c.addView(text("Glasses Settings", 19f, gold, bold = true, serif = true))
        c.addView(text(
            "Changes apply on the glasses instantly over the beam. " +
                if (LocationBeamService.connected) "Link is live." else "Link is down — start the beam first.",
            13f, if (LocationBeamService.connected) teal else dim))

        fun push(key: String, value: String) { LocationBeamService.sendLine("SET $key $value") }

        fun sliderRow(label: String, prefKey: String, def: Int, max: Int, toWire: (Int) -> Pair<String, String>): View {
            val col = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL; setPadding(0, dp(10), 0, 0)
            }
            val title = text("$label: ${prefs.getInt(prefKey, def)}", 14f, parchment, bold = true)
            col.addView(title)
            col.addView(SeekBar(this).apply {
                this.max = max
                progress = prefs.getInt(prefKey, def)
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(sb: SeekBar?, v: Int, fromUser: Boolean) {
                        title.text = "$label: $v"
                        if (fromUser) {
                            prefs.edit().putInt(prefKey, v).apply()
                            val (k, wire) = toWire(v)
                            push(k, wire)
                        }
                    }
                    override fun onStartTrackingTouch(sb: SeekBar?) {}
                    override fun onStopTrackingTouch(sb: SeekBar?) {}
                })
            })
            return col
        }

        fun switchRow(label: String, prefKey: String, def: Boolean, wireKey: String): View {
            @Suppress("UseSwitchCompatOrMaterialCode")
            val sw = Switch(this).apply {
                text = label
                textSize = 14f
                setTextColor(parchment)
                typeface = Typeface.DEFAULT_BOLD
                isChecked = prefs.getBoolean(prefKey, def)
                setPadding(0, dp(10), 0, 0)
                setOnCheckedChangeListener { _, on ->
                    prefs.edit().putBoolean(prefKey, on).apply()
                    push(wireKey, if (on) "1" else "0")
                }
            }
            return sw
        }

        c.addView(sliderRow("Sound volume", "sound", 8, 10) { v -> "sound" to v.toString() })
        c.addView(sliderRow("Swipe speed ×10", "swipe", 10, 25) { v ->
            "swipe" to String.format("%.1f", (v.coerceAtLeast(4)) / 10f)
        })
        c.addView(switchRow("Safe tap (double-tap grace)", "safetap", true, "safetap"))
        c.addView(switchRow("Flip swipe horizontal", "fliph", false, "fliph"))
        c.addView(switchRow("Flip swipe vertical", "flipv", false, "flipv"))
        c.addView(switchRow("SBS 3D mode", "sbs", true, "sbs"))

        // ------------------------------------------------- the danger drawer
        val danger = card()
        danger.addView(text("The Workshop", 17f, gold, bold = true, serif = true))
        @Suppress("UseSwitchCompatOrMaterialCode")
        val devSw = Switch(this).apply {
            text = "Dev mode (reveal all creatures, free den items)"
            textSize = 14f; setTextColor(parchment); typeface = Typeface.DEFAULT_BOLD
            isChecked = prefs.getBoolean("devMode", false)
            setPadding(0, dp(10), 0, 0)
            setOnCheckedChangeListener { _, on ->
                prefs.edit().putBoolean("devMode", on).apply()
            }
        }
        danger.addView(devSw)
        danger.addView(Button(this).apply {
            text = "RESET GAME"
            textSize = 14f; typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.rgb(255, 120, 110))
            setBackgroundColor(Color.rgb(40, 22, 26))
            setOnClickListener {
                android.app.AlertDialog.Builder(this@MainActivity)
                    .setTitle("Reset everything?")
                    .setMessage(
                        "Wipes the glasses save — creatures, essence, bonds, gear — " +
                            "and the phone den. The glasses must be linked. There is no undo.")
                    .setPositiveButton("WIPE IT ALL") { _, _ ->
                        val sent = LocationBeamService.sendLine("SET reset 1")
                        getSharedPreferences("den", Context.MODE_PRIVATE).edit().clear().apply()
                        getSharedPreferences("hunterdex", Context.MODE_PRIVATE).edit().clear().apply()
                        LocationBeamService.dexJson = null
                        dex = null
                        Toast.makeText(this@MainActivity,
                            if (sent) "The realm forgets. Walk anew."
                            else "Phone wiped — link the glasses and reset again for the full wipe.",
                            Toast.LENGTH_LONG).show()
                    }
                    .setNegativeButton("KEEP MY LIFE'S WORK", null)
                    .show()
            }
        }, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(12) })

        val note = card()
        note.addView(text("Field Notes", 17f, gold, bold = true, serif = true))
        note.addView(text(
            "Creatures materialize over the minimap as you close in and can only " +
                "be captured within 3 meters — walk right up to them. Treasure " +
                "opens from 10 meters, and everything waits ON the street — " +
                "never inside a building. The realm redraws itself wherever you roam.",
            13f, parchment))
        return scroll(c, danger, note)
    }
}
