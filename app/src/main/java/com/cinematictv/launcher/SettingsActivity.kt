package com.cinematictv.launcher

import android.content.ComponentName
import android.content.Intent
import android.provider.Settings
import android.view.View
import android.widget.TextView
import android.widget.Toast

/** Cinematic TV's own options (photos and screensaver). Also the screensaver's settings screen. */
class SettingsActivity : PanelActivity() {

    private lateinit var prefs: Prefs

    override fun kicker(): CharSequence = getString(R.string.app_name)
    override fun sheetTitle(): CharSequence = getString(R.string.settings)

    override fun build() {
        prefs = Prefs(this)

        toggle(R.drawable.ic_tv, R.string.opt_start_home, { prefs.startOnHome }) { prefs.startOnHome = it }
        addDivider()
        toggle(R.drawable.ic_wifi, R.string.opt_online_photos, { prefs.onlinePhotos }) {
            prefs.onlinePhotos = it
            Photos.setEnabled(this, it)
        }
        cycle(R.drawable.ic_clock, R.string.opt_photo_speed,
            { getString(R.string.seconds, prefs.photoSeconds) }) {
            val opts = Prefs.PHOTO_INTERVALS
            prefs.photoSeconds = opts[(opts.indexOf(prefs.photoSeconds) + 1).mod(opts.size)]
        }
        addDivider()
        toggle(R.drawable.ic_clock, R.string.opt_dream_clock, { prefs.dreamClock }) { prefs.dreamClock = it }
        toggle(R.drawable.ic_display, R.string.opt_dream_motion, { prefs.dreamMotion }) { prefs.dreamMotion = it }
        addRow(R.drawable.ic_display, getString(R.string.opt_preview)) { previewScreensaver() }
        Intent(Settings.ACTION_DREAM_SETTINGS).takeIf { it.resolveActivity(packageManager) != null }?.let { i ->
            addRow(R.drawable.ic_settings, getString(R.string.opt_system_screensaver)) { Launch.first(this, R.string.settings, i) }
        }
        addDivider()
        addRow(R.drawable.ic_apps, getString(R.string.opt_clear_recent), chevron = false) {
            prefs.forgetRecent()
            Toast.makeText(this, R.string.recent_cleared, Toast.LENGTH_SHORT).show()
        }
    }

    private fun toggle(icon: Int, label: Int, get: () -> Boolean, set: (Boolean) -> Unit) {
        lateinit var row: View
        row = addRow(icon, getString(label), sub = onOff(get()), chevron = false) {
            set(!get()); sub(row, onOff(get()))
        }
    }

    private fun cycle(icon: Int, label: Int, value: () -> String, next: () -> Unit) {
        lateinit var row: View
        row = addRow(icon, getString(label), sub = value(), chevron = false) { next(); sub(row, value()) }
    }

    private fun onOff(v: Boolean) = getString(if (v) R.string.on else R.string.off)
    private fun sub(row: View, text: String) { row.findViewById<TextView>(R.id.rowSub).text = text }

    private fun previewScreensaver() {
        // SystemUI's "Somnambulator" starts the currently selected screensaver.
        val i = Intent(Intent.ACTION_MAIN).setComponent(ComponentName("com.android.systemui", "com.android.systemui.Somnambulator"))
        if (i.resolveActivity(packageManager) != null) Launch.first(this, R.string.opt_preview, i)
        else Toast.makeText(this, getString(R.string.app_unavailable, getString(R.string.opt_preview)), Toast.LENGTH_SHORT).show()
    }
}
