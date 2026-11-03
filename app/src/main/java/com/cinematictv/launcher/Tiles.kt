package com.cinematictv.launcher

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.PathInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/** A Drawable can only belong to one View at a time; share the bitmap, not the instance. */
fun Drawable.fresh(): Drawable = constantState?.newDrawable()?.mutate() ?: this

/** Rounded app tile; one instance per view, reused. */
class TileHolder(val root: View, private val alwaysShowLabel: Boolean) {
    val card: View = root.findViewById(R.id.card)
    private val banner: ImageView = root.findViewById(R.id.banner)
    private val icon: ImageView = root.findViewById(R.id.icon)
    private val dockDot: View = root.findViewById(R.id.dockDot)
    private val label: TextView = root.findViewById(R.id.label)
    private val ring: View = root.findViewById(R.id.moveRing)
    var entry: AppEntry? = null
        private set
    private var jiggle: ObjectAnimator? = null
    var focused = false
        private set

    init {
        card.clipToOutline = true
        root.tag = this
        label.alpha = if (alwaysShowLabel) LABEL_DIM else 0f
    }

    fun resize(w: Int, h: Int) {
        (card.layoutParams).apply { width = w; height = h }
        root.layoutParams = (root.layoutParams ?: LinearLayout.LayoutParams(w, ViewGroup.LayoutParams.WRAP_CONTENT)).also { it.width = w }
    }

    fun bind(e: AppEntry, inDock: Boolean = false) {
        dockDot.visibility = if (inDock) View.VISIBLE else View.INVISIBLE
        if (entry === e) return
        entry = e
        label.text = e.label
        root.contentDescription = e.label
        if (e.banner != null) {
            banner.setImageDrawable(e.banner.fresh()); banner.visibility = View.VISIBLE
            icon.setImageDrawable(null); icon.visibility = View.GONE
        } else {
            banner.setImageDrawable(null); banner.visibility = View.GONE
            icon.setImageDrawable(e.icon?.fresh()); icon.visibility = View.VISIBLE
        }
    }

    fun setFocused(f: Boolean, animate: Boolean = true) {
        focused = f
        val s = if (f) FOCUS_SCALE else 1f
        val z = if (f) FOCUS_Z else 0f
        val y = if (f) -LIFT else 0f
        val la = if (f) 1f else if (alwaysShowLabel) LABEL_DIM else 0f
        if (animate) {
            card.animate().scaleX(s).scaleY(s).translationZ(z).translationY(y).setDuration(DURATION).setInterpolator(EASE).start()
            label.animate().alpha(la).translationY(if (f) LABEL_SHIFT else 0f).setDuration(DURATION).setInterpolator(EASE).start()
        } else {
            card.animate().cancel(); label.animate().cancel()
            card.scaleX = s; card.scaleY = s; card.translationZ = z; card.translationY = y
            label.alpha = la; label.translationY = if (f) LABEL_SHIFT else 0f
        }
    }

    /** Picked-up state in edit mode: extra lift and a ring. */
    fun setCarried(c: Boolean) {
        ring.visibility = if (c) View.VISIBLE else View.GONE
        val s = if (c) CARRY_SCALE else if (focused) FOCUS_SCALE else 1f
        card.animate().scaleX(s).scaleY(s).translationY(if (c) -LIFT * 2 else if (focused) -LIFT else 0f)
            .setDuration(DURATION).setInterpolator(EASE).start()
    }

    fun startJiggle(seed: Int) {
        if (jiggle != null) return
        val amp = 1.1f
        jiggle = ObjectAnimator.ofFloat(root, View.ROTATION, -amp, amp).apply {
            duration = 130L + (seed % 4) * 12
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            startDelay = (seed % 5) * 25L
            start()
        }
    }

    fun stopJiggle() {
        jiggle?.cancel(); jiggle = null
        root.animate().rotation(0f).setDuration(120).start()
    }

    companion object {
        const val FOCUS_SCALE = 1.14f
        const val CARRY_SCALE = 1.22f
        const val FOCUS_Z = 28f
        const val LIFT = 4f
        const val LABEL_DIM = 0.6f
        const val LABEL_SHIFT = 8f
        const val DURATION = 200L
        val EASE = PathInterpolator(0.2f, 0f, 0f, 1f)

        fun inflate(inflater: LayoutInflater, parent: ViewGroup, alwaysShowLabel: Boolean): TileHolder =
            TileHolder(inflater.inflate(R.layout.view_tile, parent, false), alwaysShowLabel)
    }
}

/** Shared focus scale for round icon buttons and rows. */
object FocusFx {
    val scaleOnFocus = View.OnFocusChangeListener { v, has ->
        val s = if (has) 1.12f else 1f
        v.animate().scaleX(s).scaleY(s).setDuration(TileHolder.DURATION).setInterpolator(TileHolder.EASE).start()
    }
}
