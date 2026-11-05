package com.cinematictv.launcher

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private lateinit var prefs: Prefs
    private lateinit var row: LinearLayout
    private lateinit var scroll: HorizontalScrollView
    private lateinit var heroA: ImageView
    private lateinit var heroB: ImageView
    private lateinit var heroTitle: TextView
    private lateinit var heroKicker: TextView
    private lateinit var heroHint: TextView
    private lateinit var topLabel: TextView
    private lateinit var editBar: TextView
    private lateinit var emptyFavs: TextView

    private val pool = ArrayList<TileHolder>()
    private var launchedAsHome = false
    private var firstBind = true

    // Hero cross-fade state
    private var heroFront: ImageView? = null
    private var heroEntry: AppEntry? = null
    private var pendingHero: AppEntry? = null
    private val heroRunnable = Runnable { pendingHero?.let { showHero(it) } }

    // Photo backdrop
    private var photoIndex = -1
    private val photoTick = Runnable { nextPhoto() }
    private val onPhotos: () -> Unit = {
        if (Photos.list.isEmpty()) {
            // Photos switched off: fall back to the focused app's artwork.
            row.removeCallbacks(photoTick); photoIndex = -1
            findViewById<View>(R.id.photoCaption).alpha = 0f
            heroEntry.also { heroEntry = null }?.let(::showHero) ?: fadeHero(null)
        } else if (photoIndex < 0) nextPhoto()
    }

    // Edit mode
    private var editing = false
    private var carryIndex = -1
    private var swallowSelectUp = false

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            i.data?.schemeSpecificPart?.let { AppRepository.invalidate(it) }
            reload()
        }
    }

    private val tileFocus = View.OnFocusChangeListener { v, has ->
        val h = v.tag as? TileHolder ?: return@OnFocusChangeListener
        h.setFocused(has)
        if (has) {
            h.entry?.let { scheduleHero(it) }
            if (carryIndex >= 0) h.setCarried(true)
        }
    }

    private val topFocus = View.OnFocusChangeListener { v, has ->
        FocusFx.scaleOnFocus.onFocusChange(v, has)
        if (has) {
            topLabel.text = v.contentDescription
            topLabel.animate().alpha(1f).setDuration(160).start()
        } else if (findViewById<View>(R.id.topBar).findFocus() == null) {
            topLabel.animate().alpha(0f).setDuration(120).start()
        }
    }

    private val tileClick = View.OnClickListener { v ->
        val h = v.tag as? TileHolder ?: return@OnClickListener
        if (editing) { toggleCarry(); return@OnClickListener }
        val e = h.entry ?: return@OnClickListener
        if (!AppRepository.launch(this, e)) {
            Toast.makeText(this, getString(R.string.app_unavailable, e.label), Toast.LENGTH_SHORT).show()
            AppRepository.invalidate(e.packageName); reload()
        }
    }

    private val tileLongClick = View.OnLongClickListener {
        if (!editing) { enterEdit(pickUp = true); swallowSelectUp = true }
        true
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        prefs = Prefs(this)
        row = findViewById(R.id.favRow)
        scroll = findViewById(R.id.favScroll)
        heroA = findViewById(R.id.heroA)
        heroB = findViewById(R.id.heroB)
        heroTitle = findViewById(R.id.heroTitle)
        heroKicker = findViewById(R.id.heroKicker)
        heroHint = findViewById(R.id.heroHint)
        topLabel = findViewById(R.id.topLabel)
        editBar = findViewById(R.id.editBar)
        emptyFavs = findViewById(R.id.emptyFavs)
        launchedAsHome = intent?.hasCategory(Intent.CATEGORY_HOME) == true

        bindTop(R.id.btnAllApps) { startActivity(Intent(this, AllAppsActivity::class.java)) }
        bindTop(R.id.btnSearch) {
            Launch.first(this, R.string.search, Intent(Intent.ACTION_ASSIST), Intent("android.speech.action.WEB_SEARCH"))
        }
        bindTop(R.id.btnInputs) { startActivity(Intent(this, InputsActivity::class.java)) }
        bindTop(R.id.btnNotifications) {
            Launch.first(this, R.string.notifications, Intent("com.android.tv.action.OPEN_NOTIFICATIONS_PANEL"))
        }
        bindTop(R.id.btnSettings) { startActivity(Intent(this, QuickSettingsActivity::class.java)) }

        registerReceiver(packageReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED); addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED); addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        })
        reload()
        Photos.addListener(onPhotos)
    }

    override fun onStart() {
        super.onStart()
        Photos.init(this) // cheap; only hits the network if the cache is >12 h old
        weatherTick.run()
        if (Photos.list.isNotEmpty()) {
            if (heroFront == null) nextPhoto() else row.postDelayed(photoTick, prefs.photoSeconds * 1000L)
        }
    }

    override fun onStop() {
        row.removeCallbacks(photoTick)
        row.removeCallbacks(weatherTick)
        super.onStop()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= TRIM_MEMORY_UI_HIDDEN) {
            // Up to ~16 MB of full-res photos; drop them while hidden and redecode on return.
            heroA.setImageDrawable(null); heroB.setImageDrawable(null)
            heroA.alpha = 0f; heroB.alpha = 0f; heroFront = null
            preloadBmp = null; preloadIdx = -1
            photoIndex = (photoIndex - 1).coerceAtLeast(-1)
        }
    }

    private val weatherTick: Runnable = object : Runnable {
        override fun run() {
            showWeather()
            Weather.refresh(this@MainActivity) { if (!isDestroyed) showWeather() }
            row.postDelayed(this, 30 * 60 * 1000L)
        }
    }

    private fun showWeather() {
        val tv = findViewById<TextView>(R.id.weather)
        val line = Weather.line(this)
        tv.text = line
        tv.visibility = if (line == null) View.GONE else View.VISIBLE
    }

    // ---- Photo backdrop ----

    // The next photo is decoded ahead of time so each change is just a crossfade.
    private var preloadIdx = -1
    private var preloadBmp: android.graphics.Bitmap? = null

    private fun nextPhoto() {
        row.removeCallbacks(photoTick)
        val list = Photos.list
        if (list.isEmpty()) return
        photoIndex = (photoIndex + 1) % list.size
        val p = list[photoIndex]
        val ready = preloadBmp.takeIf { preloadIdx == photoIndex }
        preloadBmp = null; preloadIdx = -1
        if (ready != null) showPhoto(p, ready) else {
            val dm = resources.displayMetrics
            Photos.decode(p, dm.widthPixels, dm.heightPixels) { bmp -> if (!isDestroyed && bmp != null) showPhoto(p, bmp) }
        }
        row.postDelayed(photoTick, prefs.photoSeconds * 1000L)
    }

    private fun showPhoto(p: Photo, bmp: android.graphics.Bitmap) {
        // Full screen resolution, 32-bit colour: sharp detail and no banding in skies.
        fadeHero(android.graphics.drawable.BitmapDrawable(resources, bmp).apply { isFilterBitmap = true }, tint = PHOTO_TINT)
        val cap = findViewById<View>(R.id.photoCaption)
        cap.animate().alpha(0f).setDuration(200).withEndAction {
            findViewById<TextView>(R.id.photoTitle).text = p.title
            findViewById<TextView>(R.id.photoSub).text = p.caption
            cap.animate().alpha(1f).setDuration(400).start()
        }.start()
        // Preload the following photo a few seconds later, once this crossfade has finished.
        val list = Photos.list
        if (list.size < 2) return
        val ni = (photoIndex + 1) % list.size
        row.postDelayed({
            if (isDestroyed || Photos.list !== list) return@postDelayed
            val dm = resources.displayMetrics
            Photos.decode(list[ni], dm.widthPixels, dm.heightPixels) { b -> if (b != null) { preloadIdx = ni; preloadBmp = b } }
        }, 3000)
    }

    private fun bindTop(id: Int, action: () -> Unit) {
        findViewById<View>(id).apply {
            onFocusChangeListener = topFocus
            setOnClickListener { if (!editing) action() }
            nextFocusDownId = R.id.favRow
        }
    }

    override fun onResume() {
        super.onResume()
        // Only rebuild if the Dock changed elsewhere (e.g. in Apps); keeps focus where the user left it.
        if (AppRepository.apps.isNotEmpty() && prefs.favourites != currentOrder()) bindFavourites()
    }

    private fun currentOrder(): List<String> =
        (0 until row.childCount).mapNotNull { (row.getChildAt(it).tag as? TileHolder)?.entry?.packageName }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.hasCategory(Intent.CATEGORY_HOME)) {
            launchedAsHome = true
            if (editing) exitEdit()
            scroll.smoothScrollTo(0, 0)
            row.getChildAt(0)?.requestFocus()
        }
    }

    override fun onDestroy() {
        row.removeCallbacks(heroRunnable)
        row.removeCallbacks(photoTick)
        Photos.removeListener(onPhotos)
        try { unregisterReceiver(packageReceiver) } catch (_: IllegalArgumentException) {}
        super.onDestroy()
    }

    private fun reload() {
        AppRepository.refresh(this) { apps ->
            if (isDestroyed) return@refresh
            if (!prefs.seeded && apps.isNotEmpty()) {
                prefs.favourites = Prefs.seed(apps).toMutableList(); prefs.seeded = true
            }
            bindFavourites()
        }
    }

    private fun bindFavourites() {
        if (editing) return
        val favs = prefs.favourites
        val valid = favs.filter { AppRepository.get(it) != null }
        if (valid.size != favs.size && AppRepository.apps.isNotEmpty()) prefs.favourites = valid.toMutableList()

        val focusedIdx = row.indexOfChild(row.focusedChild)
        val hadFocus = focusedIdx >= 0
        while (pool.size < valid.size) {
            val h = TileHolder.inflate(layoutInflater, row, alwaysShowLabel = false)
            h.root.onFocusChangeListener = tileFocus
            h.root.setOnClickListener(tileClick)
            h.root.setOnLongClickListener(tileLongClick)
            h.root.nextFocusUpId = R.id.btnAllApps
            (h.root.layoutParams as LinearLayout.LayoutParams).marginEnd = resources.getDimensionPixelSize(R.dimen.tile_gap)
            pool.add(h)
        }
        row.removeAllViews()
        for (i in valid.indices) {
            val h = pool[i]
            h.bind(AppRepository.get(valid[i])!!)
            h.setFocused(false, animate = false)
            h.setCarried(false)
            row.addView(h.root)
        }
        val empty = valid.isEmpty()
        emptyFavs.visibility = if (empty) View.VISIBLE else View.GONE
        findViewById<View>(R.id.heroText).visibility = if (empty) View.INVISIBLE else View.VISIBLE
        if (empty) {
            heroEntry = null; if (Photos.list.isEmpty()) fadeHero(null)
            findViewById<View>(R.id.btnAllApps).requestFocus(); firstBind = false; return
        }
        if (firstBind || hadFocus || currentFocus == null) {
            val target = row.getChildAt(focusedIdx.coerceIn(0, valid.size - 1))
            target?.requestFocus()
            (target?.tag as? TileHolder)?.let { it.setFocused(true, animate = false); it.entry?.let(::showHero) }
        }
        firstBind = false
    }

    // ---- Top shelf (hero) ----

    private fun scheduleHero(e: AppEntry) {
        pendingHero = e
        row.removeCallbacks(heroRunnable)
        // Debounce while scrolling quickly through the dock.
        row.postDelayed(heroRunnable, if (heroEntry == null) 0 else 110)
    }

    private fun showHero(e: AppEntry) {
        heroTitle.text = e.label
        if (heroEntry === e) return
        heroEntry = e
        // Photos own the backdrop when available; app art is the offline fallback.
        if (Photos.list.isEmpty()) fadeHero(e.banner ?: e.icon)
    }

    private fun fadeHero(art: android.graphics.drawable.Drawable?, tint: Int = HERO_TINT) {
        val back = if (heroFront === heroA) heroB else heroA
        val front = heroFront
        if (art != null) {
            // Darken the art so bright banners (white Netflix/YouTube) don't wash out the shelf.
            // Photos keep their true colours (scrims handle contrast); app art is dimmed as a fallback backdrop.
            back.setImageDrawable(if (tint == PHOTO_TINT) art else art.fresh().apply { setColorFilter(tint, android.graphics.PorterDuff.Mode.MULTIPLY) })
            back.animate().alpha(if (tint == PHOTO_TINT) 1f else HERO_ALPHA).setDuration(380).setInterpolator(TileHolder.EASE).start()
            heroFront = back
        } else heroFront = null
        front?.animate()?.alpha(0f)?.setDuration(380)?.withEndAction { if (heroFront !== front) front.setImageDrawable(null) }?.start()
    }

    // ---- Edit mode ----

    private fun enterEdit(pickUp: Boolean) {
        if (row.childCount == 0) return
        editing = true
        for (i in 0 until row.childCount) (row.getChildAt(i).tag as TileHolder).startJiggle(i)
        editBar.visibility = View.VISIBLE
        findViewById<View>(R.id.topBar).animate().alpha(0f).setDuration(150).start()
        heroKicker.setText(R.string.editing)
        heroKicker.visibility = View.VISIBLE
        heroHint.visibility = View.GONE
        if (pickUp) toggleCarry() else editBar.setText(R.string.edit_hint)
    }

    private fun exitEdit() {
        if (carryIndex >= 0) toggleCarry()
        editing = false
        for (i in 0 until row.childCount) (row.getChildAt(i).tag as TileHolder).stopJiggle()
        editBar.visibility = View.GONE
        findViewById<View>(R.id.topBar).animate().alpha(1f).setDuration(150).start()
        heroKicker.visibility = View.GONE
        heroHint.visibility = View.VISIBLE
        saveOrder()
    }

    private fun toggleCarry() {
        val idx = row.indexOfChild(row.focusedChild)
        if (carryIndex >= 0) {
            (row.getChildAt(carryIndex)?.tag as? TileHolder)?.setCarried(false)
            carryIndex = -1
            editBar.setText(R.string.edit_hint)
            saveOrder()
        } else if (idx >= 0) {
            carryIndex = idx
            (row.getChildAt(idx).tag as TileHolder).setCarried(true)
            editBar.setText(R.string.edit_hint_carry)
        }
    }

    private fun moveCarried(delta: Int) {
        val to = carryIndex + delta
        if (to < 0 || to >= row.childCount) return
        val v = row.getChildAt(carryIndex)
        row.removeViewAt(carryIndex)
        row.addView(v, to)
        carryIndex = to
        v.requestFocus()
        scroll.post { scroll.requestChildFocus(row, v) }
    }

    private fun removeFocused() {
        val v = row.focusedChild ?: return
        val h = v.tag as? TileHolder ?: return
        val e = h.entry ?: return
        if (carryIndex >= 0) toggleCarry()
        val idx = row.indexOfChild(v)
        h.stopJiggle(); h.setFocused(false, animate = false)
        row.removeView(v)
        saveOrder()
        Toast.makeText(this, getString(R.string.removed_fav, e.label), Toast.LENGTH_SHORT).show()
        if (row.childCount == 0) { exitEdit(); bindFavourites(); return }
        row.getChildAt(idx.coerceAtMost(row.childCount - 1))?.requestFocus()
    }

    private fun saveOrder() {
        val order = ArrayList<String>(row.childCount)
        for (i in 0 until row.childCount) (row.getChildAt(i).tag as TileHolder).entry?.let { order.add(it.packageName) }
        prefs.favourites = order
    }

    private fun isSelect(code: Int) =
        code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER || code == KeyEvent.KEYCODE_NUMPAD_ENTER

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (!editing && Launch.handleSourceKey(this, event)) return true
        val code = event.keyCode
        val down = event.action == KeyEvent.ACTION_DOWN
        // Key-repeat long press (reliable on every remote, unlike the view timer).
        if (!editing && isSelect(code) && down && (event.repeatCount > 0 || event.isLongPress)) {
            if (row.focusedChild != null && !swallowSelectUp) { enterEdit(pickUp = true); swallowSelectUp = true }
            return true
        }
        if (swallowSelectUp && isSelect(code)) {
            if (!down) { swallowSelectUp = false; row.focusedChild?.let { it.cancelLongPress(); it.isPressed = false } }
            return true
        }
        if (editing) {
            if (!down) return code != KeyEvent.KEYCODE_HOME
            when (code) {
                KeyEvent.KEYCODE_DPAD_LEFT -> { if (carryIndex >= 0) { moveCarried(-1); return true } }
                KeyEvent.KEYCODE_DPAD_RIGHT -> { if (carryIndex >= 0) { moveCarried(1); return true } }
                KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN -> return true
                KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_DEL -> { removeFocused(); return true }
                KeyEvent.KEYCODE_BACK -> { exitEdit(); return true }
                else -> if (isSelect(code)) { toggleCarry(); return true }
            }
        } else if (down && code == KeyEvent.KEYCODE_MENU && row.focusedChild != null) {
            enterEdit(pickUp = false); return true
        }
        return super.dispatchKeyEvent(event)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (!launchedAsHome) super.onBackPressed()
        else if (row.findFocus() == null) row.getChildAt(0)?.requestFocus()
    }

    companion object {
        private const val HERO_ALPHA = 0.7f
        private const val HERO_TINT = 0xFF6E6E74.toInt()
        private const val PHOTO_TINT = 0xFFB4B4B8.toInt()
    }
}

/** Safe launching helpers shared by screens. */
object Launch {
    /** Remote Source/Input key → our Inputs sheet (when the system lets the key reach the app). */
    fun handleSourceKey(a: Activity, event: KeyEvent): Boolean {
        if (event.keyCode != KeyEvent.KEYCODE_TV_INPUT) return false
        if (event.action == KeyEvent.ACTION_UP) a.startActivity(Intent(a, InputsActivity::class.java))
        return true
    }

    fun first(a: Activity, labelRes: Int, vararg intents: Intent): Boolean {
        for (i in intents) {
            try { a.startActivity(Intent(i).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); return true } catch (t: Throwable) {
                Log.w("CinematicTV", "Unavailable: ${i.action}", t)
            }
        }
        Toast.makeText(a, a.getString(R.string.app_unavailable, a.getString(labelRes)), Toast.LENGTH_SHORT).show()
        return false
    }
}
