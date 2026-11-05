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
        toggle(R.drawable.ic_wifi, R.string.opt_weather, { prefs.weatherOn }) { prefs.weatherOn = it; Weather.refresh(this, force = true) {} }
        lateinit var city: View
        city = addRow(R.drawable.ic_search, getString(R.string.opt_weather_city), sub = prefs.weatherPlace, chevron = false) {
            askCity { name ->
                prefs.weatherCity = name
                sub(city, name)
                Weather.refresh(this, force = true) { sub(city, prefs.weatherPlace) }
            }
        }
        toggle(R.drawable.ic_clock, R.string.opt_fahrenheit, { prefs.fahrenheit }) { prefs.fahrenheit = it; Weather.refresh(this, force = true) {} }
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
        addRow(R.drawable.ic_settings, getString(R.string.opt_restore), sub = getString(R.string.opt_restore_sub), chevron = false) {
            if (Backup.restore(this)) {
                Toast.makeText(this, R.string.restored, Toast.LENGTH_SHORT).show(); recreate()
            } else Toast.makeText(this, R.string.no_backup, Toast.LENGTH_LONG).show()
        }
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

    private fun askCity(done: (String) -> Unit) {
        val input = android.widget.EditText(this).apply {
            setText(prefs.weatherCity); setSelectAllOnFocus(true); isSingleLine = true
            imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_DONE
        }
        val dialog = android.app.AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle(R.string.opt_weather_city)
            .setView(input)
            .setPositiveButton(android.R.string.ok) { _, _ -> input.text.toString().trim().takeIf { it.isNotEmpty() }?.let(done) }
            .setNegativeButton(android.R.string.cancel, null)
            .create()
        input.setOnEditorActionListener { _, _, _ ->
            input.text.toString().trim().takeIf { it.isNotEmpty() }?.let(done); dialog.dismiss(); true
        }
        dialog.show()
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
