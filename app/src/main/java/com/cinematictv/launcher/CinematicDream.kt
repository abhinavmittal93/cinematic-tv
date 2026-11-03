package com.cinematictv.launcher

import android.graphics.drawable.BitmapDrawable
import android.service.dreams.DreamService
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.ImageView
import android.widget.TextView

/**
 * Screensaver: full-screen daily photos with a slow Ken Burns pan/zoom, crossfade, clock and caption.
 * Animation is a GPU-composited transform on a hardware layer, so each frame is a cheap texture draw.
 */
class CinematicDream : DreamService() {

    private lateinit var a: ImageView
    private lateinit var b: ImageView
    private var front: ImageView? = null
    private var index = -1
    private lateinit var prefs: Prefs
    private val showMs get() = prefs.photoSeconds * 1000L
    private var step = 0
    private val tick = Runnable { next() }
    private val onPhotos: () -> Unit = { if (index < 0) next() }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isInteractive = false
        isFullscreen = true
        isScreenBright = true
        setContentView(R.layout.dream)
        a = findViewById(R.id.photoA)
        b = findViewById(R.id.photoB)
        prefs = Prefs(this)
        findViewById<View>(R.id.clockBlock).visibility = if (prefs.dreamClock) View.VISIBLE else View.GONE
    }

    override fun onDreamingStarted() {
        super.onDreamingStarted()
        Photos.addListener(onPhotos)
        Photos.init(this)
        index = (Photos.list.size * Math.random()).toInt() - 1
        if (Photos.list.isNotEmpty()) next()
    }

    override fun onDreamingStopped() {
        a.removeCallbacks(tick)
        Photos.removeListener(onPhotos)
        a.animate().cancel(); b.animate().cancel()
        a.setImageDrawable(null); b.setImageDrawable(null)
        super.onDreamingStopped()
    }

    private fun next() {
        a.removeCallbacks(tick)
        val list = Photos.list
        if (list.isEmpty()) return
        index = (index + 1).mod(list.size)
        val p = list[index]
        val w = resources.displayMetrics.widthPixels
        val h = resources.displayMetrics.heightPixels
        Photos.decode(p, w, h) { bmp ->
            if (bmp == null) { a.postDelayed(tick, 2000); return@decode }
            val back = if (front === a) b else a
            val old = front
            back.setImageDrawable(BitmapDrawable(resources, bmp).apply { isFilterBitmap = true })
            kenBurns(back)
            back.animate().alpha(1f).setDuration(FADE_MS).setInterpolator(TileHolder.EASE).start()
            old?.animate()?.alpha(0f)?.setDuration(FADE_MS)?.withEndAction {
                if (front !== old) { old.animate().cancel(); old.setImageDrawable(null); old.setLayerType(View.LAYER_TYPE_NONE, null) }
            }?.start()
            front = back
            caption(p)
            a.postDelayed(tick, showMs)
        }
    }

    private fun kenBurns(v: ImageView) {
        if (!prefs.dreamMotion) { v.scaleX = 1f; v.scaleY = 1f; v.translationX = 0f; v.translationY = 0f; return }
        // Alternate zoom direction and pan corner for variety.
        val s0 = if (step % 2 == 0) 1.0f else 1.12f
        val s1 = if (step % 2 == 0) 1.12f else 1.0f
        val dx = (if (step % 4 < 2) 1 else -1) * v.width * 0.03f
        val dy = (if (step % 3 == 0) 1 else -1) * v.height * 0.02f
        step++
        v.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        v.scaleX = s0; v.scaleY = s0; v.translationX = -dx; v.translationY = -dy
        v.animate().scaleX(s1).scaleY(s1).translationX(dx).translationY(dy)
            .setDuration(showMs + FADE_MS).setInterpolator(LinearInterpolator()).start()
    }

    private fun caption(p: Photo) {
        val c = findViewById<View>(R.id.caption)
        c.animate().alpha(0f).setDuration(300).withEndAction {
            findViewById<TextView>(R.id.capTitle).text = p.title
            findViewById<TextView>(R.id.capSub).text = p.caption
            c.animate().alpha(1f).setStartDelay(600).setDuration(800).start()
        }.start()
    }

    private companion object {
        const val FADE_MS = 1_600L
    }
}
