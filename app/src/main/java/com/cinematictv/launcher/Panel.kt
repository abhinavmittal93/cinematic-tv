package com.cinematictv.launcher

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.view.animation.PathInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/** Base for right-hand side sheets (Inputs, Control Centre). */
abstract class PanelActivity : Activity() {
    protected lateinit var list: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_panel)
        list = findViewById(R.id.panelList)
        findViewById<TextView>(R.id.panelKicker).text = kicker()
        findViewById<TextView>(R.id.panelTitle).text = sheetTitle()
        build()
        // Stagger rows in for a little polish; cheap (a handful of views, once).
        for (i in 0 until list.childCount) {
            val v = list.getChildAt(i)
            v.alpha = 0f; v.translationX = 24f
            v.animate().alpha(v.tag as? Float ?: 1f).translationX(0f).setStartDelay(40L + i * 28L)
                .setDuration(220).setInterpolator(EASE).start()
        }
        list.getChildAt(0)?.requestFocus()
    }

    abstract fun kicker(): CharSequence
    abstract fun sheetTitle(): CharSequence
    abstract fun build()

    protected fun addRow(icon: Int, title: CharSequence, sub: CharSequence? = null, dot: Boolean = false,
                         dim: Boolean = false, chevron: Boolean = true, onClick: () -> Unit): View {
        val v = layoutInflater.inflate(R.layout.view_panel_row, list, false)
        v.findViewById<ImageView>(R.id.rowIcon).setImageResource(icon)
        v.findViewById<TextView>(R.id.rowTitle).text = title
        v.findViewById<TextView>(R.id.rowSub).apply { text = sub; visibility = if (sub == null) View.GONE else View.VISIBLE }
        v.findViewById<View>(R.id.rowDot).visibility = if (dot) View.VISIBLE else View.GONE
        v.findViewById<View>(R.id.rowChevron).visibility = if (chevron) View.VISIBLE else View.GONE
        if (dim) v.tag = 0.5f
        v.setOnClickListener { onClick() }
        list.addView(v)
        return v
    }

    protected fun addDivider() {
        list.addView(View(this).apply {
            setBackgroundColor(getColor(R.color.hairline))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1).apply {
                val m = (12 * resources.displayMetrics.density).toInt(); setMargins(m, m / 2, m, m)
            }
        })
    }

    companion object { private val EASE = PathInterpolator(0.2f, 0f, 0f, 1f) }
}
