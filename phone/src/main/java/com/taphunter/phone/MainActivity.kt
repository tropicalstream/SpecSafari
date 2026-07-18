package com.taphunter.phone

import android.Manifest
import android.app.Activity
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
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import com.taphunter.shared.Species
import com.taphunter.shared.Sprites
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

    override fun onResume() { super.onResume(); handler.post(poll) }
    override fun onPause() { handler.removeCallbacks(poll); super.onPause() }

    private fun refreshDex() {
        val json = LocationBeamService.dexJson
            ?: getSharedPreferences("hunterdex", Context.MODE_PRIVATE).getString("dex", null)
            ?: return
        runCatching { dex = JSONObject(json) }
    }

    // ------------------------------------------------------------ chrome

    private fun header(): View = TextView(this).apply {
        text = "⚔ TAPHUNTER ⚔"
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
            setPadding(dp(14), dp(6), dp(14), dp(20))
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
        val discovered = (0 until Species.ALL.size).count { (counts?.optInt(it) ?: 0) > 0 }
        val headerCard = card().apply {
            addView(text("HunterDex", 19f, gold, bold = true, serif = true))
            addView(text(
                "$discovered of ${Species.ALL.size} creatures discovered. " +
                    "Each haunts a kind of real place — walk there and they come.",
                13.5f, parchment))
        }
        val cards = mutableListOf<View>(headerCard)
        for (i in Species.ALL.indices) {
            val sp = Species.ALL[i]
            val count = counts?.optInt(i) ?: 0
            val bestL = best?.optInt(i) ?: 0
            val known = count > 0
            val c = card()
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            row.addView(SpriteView(this, i, known), LinearLayout.LayoutParams(dp(72), dp(72)))
            val info = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(12), 0, 0, 0)
            }
            info.addView(text(
                if (known) "No.${i + 1}  ${sp.name}" else "No.${i + 1}  ???",
                16f, if (known) Color.WHITE else dim, bold = true, serif = true))
            info.addView(text(sp.habitat, 12f, teal, bold = true))
            if (known) {
                info.addView(text("Caught ×$count · best L$bestL", 12.5f, gold))
            }
            row.addView(info, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            c.addView(row)
            c.addView(text(
                if (known) sp.lore else "Not yet discovered. Its haunt is listed — go look.",
                13f, if (known) parchment else dim).apply { setPadding(0, dp(8), 0, 0) })
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
        val c = card()
        c.addView(text("The Journey", 19f, gold, bold = true, serif = true))
        if (d == null) {
            c.addView(text("The ledger is blank until the glasses report in.", 13.5f, parchment))
            return scroll(c)
        }
        val caught = d.optInt("lifeCaught")
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

        val u = card()
        u.addView(text("Gear of the Hunt", 17f, gold, bold = true, serif = true))
        val tiers = d.optJSONArray("tiers")
        val names = listOf("Orb Polish", "Scanner", "Lure Charm", "Satchel")
        for (i in names.indices) {
            val tier = tiers?.optInt(i) ?: 0
            u.addView(statRow(names[i], "◆".repeat(tier).padEnd(5, '·')))
        }
        return scroll(c, s, u)
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

        val note = card()
        note.addView(text("Field Notes", 17f, gold, bold = true, serif = true))
        note.addView(text(
            "Creatures materialize over the minimap as you close in and can only " +
                "be captured within 3 meters — walk right up to them. Treasure " +
                "opens from 6 meters. The realm redraws itself wherever you roam.",
            13f, parchment))
        return scroll(c, note)
    }
}
