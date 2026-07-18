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
 * The Creature Den, phone edition: a 3D diorama you stroll through with a
 * thumb-drag, four habitats wide open to whoever you've caught. Tap a
 * creature to pet it; buy treats and habitat furniture with essence — every
 * coin spent and bond earned is beamed to the glasses save, which stays
 * the single source of truth.
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

    private var dex: JSONObject? = null
    private var walletText: TextView? = null
    private var infoName: TextView? = null
    private var infoLine: TextView? = null
    private var hintText: TextView? = null
    private val shopCards = HashMap<String, View>()
    private val habitatChips = ArrayList<Button>()
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
                // The glasses absorbed some (or all) of our spending — shrink the IOU.
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

    // ------------------------------------------------------------ state

    private fun population(): List<Int> {
        if (devMode) return Species.ALL.indices.toList()
        val counts = dex?.optJSONArray("counts") ?: return emptyList()
        // The box comes home: repeated catches earn extra den residents of
        // that species (a 2nd at 3 catches, a 3rd at 6), so a devoted
        // Shadepaw-hunter gets a visible clowder, not a lone ambassador.
        val out = mutableListOf<Int>()
        for (s in 0 until Species.ALL.size) {
            val n = counts.optInt(s)
            if (n <= 0) continue
            out += s
            if (n >= 3) out += s
            if (n >= 6) out += s
        }
        return out.take(16)
    }

    private fun placedFor(h: Int): MutableList<String> =
        (prefs.getString("items_$h", "") ?: "").split(',').filter { it.isNotBlank() }.toMutableList()

    private fun pouch(id: String) = prefs.getInt("pouch_$id", 0)

    // ------------------------------------------------------------- view

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        actionBar?.hide()
        prefs = getSharedPreferences("den", Context.MODE_PRIVATE)
        mirror = getSharedPreferences("mirror", Context.MODE_PRIVATE)
        refreshDex()

        val habitat = prefs.getInt("habitat", 0)
        renderer.configure(population(), habitat, placedFor(habitat))
        renderer.resetCamera()

        glView = GLSurfaceView(this).apply {
            setEGLContextClientVersion(2)
            setRenderer(renderer)
        }

        // Minecraft manners: a floating joystick under the left thumb walks,
        // the right thumb drags to look around, and a still tap pets.
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
                                renderer.setMove(dx, -dy)   // screen-up = forward
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
                        // A still left-thumb tap is a pet, not a walk.
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
            renderer.celebrate(i, big = true)
            hint("${sp.name} DEVOURED THE ${item.name}!")
        } else {
            val now = System.currentTimeMillis()
            if (now - (petAt[c.species] ?: 0L) > 8000L) {
                petAt[c.species] = now
                pushBond(c.species, 1)
            }
            renderer.celebrate(i, big = false)
            hint("${sp.name} — THE ${sp.temperament} ONE — ${sp.nature}")
        }
        updateInfo()
        updateShop()
    }

    // -------------------------------------------------------- overlay UI

    private fun overlayUi(): View {
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        // Top bar: back, title, wallet.
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

        // Habitat chips.
        val chips = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(8), 0, dp(8), 0)
        }
        for ((i, hab) in Habitats.ALL.withIndex()) {
            val b = Button(this).apply {
                text = hab.name.split(' ')[0]; textSize = 11f
                typeface = Typeface.DEFAULT_BOLD
                setBackgroundColor(Color.TRANSPARENT)
                setOnClickListener { switchHabitat(i) }
            }
            habitatChips += b
            chips.addView(b, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
        col.addView(chips)

        // Spacer pushes the shop down.
        col.addView(View(this), LinearLayout.LayoutParams(1, 0, 1f))

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

        // The shop: one horizontal shelf of boxes.
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

        switchHabitat(prefs.getInt("habitat", 0))
        return col
    }

    private val poll = object : Runnable {
        override fun run() {
            refreshDex(); updateWallet(); updateShop(); updateInfo()
            handler.postDelayed(this, 1500)
        }
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
            val h = prefs.getInt("habitat", 0)
            val placed = placedFor(h)
            if (item.id in placed) { hint("ALREADY IN THIS HABITAT") ; return }
            if (placed.size >= Habitats.SLOTS) { hint("THIS HABITAT IS FULLY FURNISHED"); return }
            if (!spend(if (devMode) 0 else item.price)) { updateShop(); return }
            placed += item.id
            prefs.edit().putString("items_$h", placed.joinToString(",")).apply()
            renderer.placeItems(placed)
            hint("${item.name} PLACED — SOMEONE WILL LOVE IT")
        }
        updateWallet(); updateShop()
    }

    private fun switchHabitat(i: Int) {
        prefs.edit().putInt("habitat", i).apply()
        renderer.configure(population(), i, placedFor(i))
        for ((k, chip) in habitatChips.withIndex())
            chip.setTextColor(if (k == i) gold else dim)
        hint(Habitats.ALL[i].name)
        updateShop()
    }

    // ------------------------------------------------------------ redraw

    private fun updateWallet() {
        walletText?.text = if (devMode) "◆ DEV" else "◆ ${wallet()}"
    }

    private fun updateShop() {
        val h = prefs.getInt("habitat", 0)
        val placed = placedFor(h)
        for (item in Habitats.ITEMS) {
            val card = shopCards[item.id] ?: continue
            val price = card.findViewWithTag<TextView>("price") ?: continue
            val afford = devMode || (wallet() >= item.price && LocationBeamService.connected)
            price.text = when {
                item.treat && pouch(item.id) > 0 -> "×${pouch(item.id)} READY"
                item.id in placed -> "PLACED"
                devMode -> "FREE·DEV"
                else -> "◆ ${item.price}"
            }
            card.alpha = if (afford || pouch(item.id) > 0 || item.id in placed) 1f else 0.35f
        }
    }

    private fun updateInfo() {
        val c = renderer.creatures.getOrNull(renderer.selected)
        if (c == null) {
            infoName?.text = if (renderer.creatures.isEmpty())
                "The den is empty — catch creatures on the glasses first." else
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
        infoLine?.text = sp.nature
    }

    private fun hint(s: String) { hintText?.text = s }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onResume() { super.onResume(); glView.onResume(); handler.post(poll) }
    override fun onPause() { glView.onPause(); handler.removeCallbacksAndMessages(null); super.onPause() }
}

/** The floating joystick's on-screen ghost: a ring where the thumb landed,
 *  a puck where it is now. Not touchable — pure feedback. */
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
