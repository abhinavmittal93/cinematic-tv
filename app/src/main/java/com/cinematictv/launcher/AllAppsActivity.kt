package com.cinematictv.launcher

import android.app.Activity
import android.graphics.PorterDuff
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/** Apps: filter pills, centred 6-column grid, search, focus-centred scrolling. Views are pooled. */
class AllAppsActivity : Activity() {

    /** All / In Dock, or one of Cinematic TV's own categories. */
    private data class Filter(val label: Int, val category: AppCategory? = null) {
        companion object {
            val ALL = Filter(R.string.f_all)
            val DOCK = Filter(R.string.f_dock)
            fun of(c: AppCategory) = Filter(c.label, c)
        }
    }

    private lateinit var prefs: Prefs
    private lateinit var sections: LinearLayout
    private lateinit var scroll: ScrollView
    private lateinit var search: EditText
    private lateinit var noResults: TextView
    private lateinit var hintPill: TextView
    private lateinit var count: TextView
    private lateinit var filtersRow: LinearLayout

    private var all: List<AppEntry> = emptyList()
    private var dock: Set<String> = emptySet()
    private var filter = Filter.ALL
    private val filterViews = HashMap<Filter, TextView>()
    private val tilePool = ArrayList<TileHolder>()
    private val rowPool = ArrayList<LinearLayout>()
    private var longHandled = false
    private val cols = 6

    private val tileFocus = View.OnFocusChangeListener { v, has ->
        val h = v.tag as? TileHolder ?: return@OnFocusChangeListener
        h.setFocused(has)
        if (has) { centre(v); showHint(h) }
        else if (currentFocus?.tag !is TileHolder) hintPill.animate().alpha(0f).setDuration(150).start()
    }

    private val tileClick = View.OnClickListener { v ->
        val e = (v.tag as? TileHolder)?.entry ?: return@OnClickListener
        if (!AppRepository.launch(this, e)) {
            Toast.makeText(this, getString(R.string.app_unavailable, e.label), Toast.LENGTH_SHORT).show()
            AppRepository.invalidate(e.packageName); load()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_all_apps)
        prefs = Prefs(this)
        sections = findViewById(R.id.sections)
        scroll = findViewById(R.id.scroll)
        search = findViewById(R.id.search)
        noResults = findViewById(R.id.noResults)
        hintPill = findViewById(R.id.hintPill)
        count = findViewById(R.id.count)
        filtersRow = findViewById(R.id.filters)

        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) = render(focusFirst = false)
        })
        search.setOnEditorActionListener { _, id, _ ->
            if (id == EditorInfo.IME_ACTION_SEARCH || id == EditorInfo.IME_ACTION_DONE) {
                hideIme(); firstTile()?.requestFocus(); true
            } else false
        }
        all = AppRepository.apps
        dock = prefs.favourites.toSet()
        buildFilters()
        render(focusFirst = true)
        loadBackdrop()
        load()
    }

    private fun load() {
        AppRepository.refresh(this) { apps ->
            if (isDestroyed) return@refresh
            all = apps
            buildFilters()
            render(focusFirst = false)
        }
    }

    private fun loadBackdrop() {
        val list = Photos.list
        if (list.isEmpty()) return
        val p = list[(System.currentTimeMillis() / 60_000 % list.size).toInt()]
        Photos.decode(p, 480, 270, hq = false) { bmp ->
            if (bmp == null || isDestroyed) return@decode
            val iv = findViewById<ImageView>(R.id.backdrop)
            iv.setImageDrawable(BitmapDrawable(resources, bmp).apply { setColorFilter(0xFF8E8E93.toInt(), PorterDuff.Mode.MULTIPLY) })
            iv.animate().alpha(1f).setDuration(500).start()
        }
    }

    // ---- Filters ----

    private fun matches(e: AppEntry, f: Filter): Boolean = when {
        f == Filter.ALL -> true
        f == Filter.DOCK -> e.packageName in dock
        else -> e.kind == f.category
    }

    private fun buildFilters() {
        // Categories in a fixed order, only those that actually contain apps.
        val shown = listOf(Filter.ALL, Filter.DOCK) +
            AppCategory.values().filter { c -> all.any { it.kind == c } }.map(Filter::of)
        if (filter !in shown) filter = Filter.ALL
        if (filterViews.keys == shown.toSet()) { syncFilters(); return }
        filtersRow.removeAllViews(); filterViews.clear()
        for (f in shown) {
            val tv = TextView(this).apply {
                setText(f.label)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
                setTextColor(getColorStateList(R.color.pill_text))
                background = getDrawable(R.drawable.pill_toggle)
                gravity = Gravity.CENTER
                isFocusable = true; isClickable = true
                setPadding(dp(14), 0, dp(14), 0)
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(32)).apply { marginEnd = dp(6) }
                onFocusChangeListener = View.OnFocusChangeListener { _, has -> if (has) setFilter(f) }
                setOnClickListener { setFilter(f); firstTile()?.requestFocus() }
            }
            filterViews[f] = tv
            filtersRow.addView(tv)
        }
        syncFilters()
    }

    private fun setFilter(f: Filter) {
        if (f == filter) return
        filter = f
        syncFilters()
        render(focusFirst = false, keepFocus = false)
    }

    private fun syncFilters() { filterViews.forEach { (f, v) -> v.isActivated = f == filter } }

    // ---- Grid ----

    private fun render(focusFirst: Boolean, keepFocus: Boolean = true) {
        val q = search.text.toString().trim()
        val list = all.filter { matches(it, filter) && (q.isEmpty() || it.label.contains(q, ignoreCase = true)) }
        val keep = currentFocus
        val keepIdx = tilePool.indexOfFirst { it.root === keep }

        for (i in 0 until sections.childCount) (sections.getChildAt(i) as? LinearLayout)?.removeAllViews()
        sections.removeAllViews()

        val tileW = resources.getDimensionPixelSize(R.dimen.grid_tile_w)
        val tileH = resources.getDimensionPixelSize(R.dimen.grid_tile_h)
        val gap = resources.getDimensionPixelSize(R.dimen.grid_gap)
        // Fixed-width container keeps a partial last row left-aligned while the grid stays centred.
        sections.layoutParams.width = cols * tileW + (cols - 1) * gap
        var tile = 0; var rowIdx = 0; var k = 0
        while (k < list.size) {
            val row = rowAt(rowIdx++)
            val end = minOf(k + cols, list.size)
            for (p in k until end) {
                val h = tileAt(tile++, tileW, tileH)
                val e = list[p]
                h.bind(e, inDock = e.packageName in dock)
                h.setFocused(false, animate = false)
                (h.root.layoutParams as LinearLayout.LayoutParams).marginEnd = if (p == end - 1) 0 else gap
                row.addView(h.root)
            }
            sections.addView(row)
            k = end
        }
        count.text = resources.getQuantityString(R.plurals.apps_count, list.size, list.size)
        noResults.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
        noResults.text = if (q.isNotEmpty()) getString(R.string.no_results, q) else getString(R.string.nothing_here)

        val inHeader = keep === search || filterViews.values.any { it === keep }
        if (focusFirst || (!inHeader && keepFocus)) {
            val target = tilePool.getOrNull(keepIdx)?.root?.takeIf { !focusFirst && it.parent != null } ?: firstTile()
            target?.requestFocus()
            (target?.tag as? TileHolder)?.setFocused(true, animate = false)
        }
        if (focusFirst || !keepFocus) scroll.scrollTo(0, 0)
    }

    /**
     * Snap scrolling to whole rows: the focused row sits second from the top (first when it is the
     * first row), so no half-cut row ever shows above it and the scaled tile + label always fit.
     */
    private fun centre(v: View) {
        val row = v.parent as? View ?: return
        if (sections.indexOfChild(row) < 0) return
        val viewport = scroll.height - scroll.paddingTop - scroll.paddingBottom
        val labelRoom = dp(28) // label + scale overflow below the focused row
        val need = row.bottom + labelRoom - viewport
        // Smallest row boundary that shows the whole focused row; never stop mid-row.
        var target = 0
        if (need > 0) {
            for (i in 0 until sections.childCount) {
                val top = sections.getChildAt(i).top - dp(10)
                if (top >= need) { target = top; break }
            }
        }
        if (target > row.top - dp(10)) target = row.top - dp(10)
        scroll.post { scroll.smoothScrollTo(0, target.coerceAtLeast(0)) }
    }

    private val restoreHint = Runnable { (currentFocus?.tag as? TileHolder)?.let { showHint(it) } }

    private fun showHint(h: TileHolder) {
        val e = h.entry ?: return
        hintPill.text = getString(if (e.packageName in dock) R.string.hint_remove else R.string.hint_add, e.label)
        hintPill.animate().alpha(1f).setDuration(180).start()
    }

    private fun firstTile(): View? = (0 until sections.childCount).asSequence()
        .map { sections.getChildAt(it) }.filterIsInstance<LinearLayout>().firstOrNull { it.childCount > 0 }?.getChildAt(0)

    private fun tileAt(i: Int, w: Int, h: Int): TileHolder {
        while (tilePool.size <= i) {
            val t = TileHolder.inflate(layoutInflater, sections, alwaysShowLabel = false)
            t.resize(w, h)
            t.root.onFocusChangeListener = tileFocus
            t.root.setOnClickListener(tileClick)
            t.root.nextFocusUpId = View.NO_ID
            tilePool.add(t)
        }
        return tilePool[i]
    }

    private fun rowAt(i: Int): LinearLayout {
        while (rowPool.size <= i) rowPool.add(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            clipChildren = false; clipToPadding = false
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(10)
            }
        })
        return rowPool[i]
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun hideIme() {
        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(search.windowToken, 0)
    }

    private fun toggleFavourite(h: TileHolder) {
        val e = h.entry ?: return
        val added = prefs.toggleFavourite(e.packageName)
        dock = prefs.favourites.toSet()
        h.bind(e, inDock = added)
        // Confirm in the hint pill (a toast would cover it), then return to the contextual hint.
        hintPill.text = getString(if (added) R.string.added_fav else R.string.removed_fav, e.label)
        hintPill.animate().alpha(1f).setDuration(120).start()
        hintPill.removeCallbacks(restoreHint)
        hintPill.postDelayed(restoreHint, 1600)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val select = event.keyCode == KeyEvent.KEYCODE_DPAD_CENTER || event.keyCode == KeyEvent.KEYCODE_ENTER ||
            event.keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER
        val focusedTile = currentFocus?.tag as? TileHolder
        if (select && focusedTile != null && event.action == KeyEvent.ACTION_DOWN && (event.repeatCount > 0 || event.isLongPress)) {
            if (!longHandled) { toggleFavourite(focusedTile); longHandled = true }
            return true
        }
        if (select && event.action == KeyEvent.ACTION_UP && longHandled) {
            longHandled = false
            currentFocus?.let { it.cancelLongPress(); it.isPressed = false }
            return true
        }
        // Back from the header returns to the grid; Back from the grid leaves.
        if (event.keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP && focusedTile == null && firstTile() != null) {
            hideIme(); firstTile()?.requestFocus(); return true
        }
        return super.dispatchKeyEvent(event)
    }
}
