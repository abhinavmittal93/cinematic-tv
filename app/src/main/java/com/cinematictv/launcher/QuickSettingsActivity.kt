package com.cinematictv.launcher

import android.content.Intent
import android.provider.Settings

/** Control Centre: shortcuts to the most-used TV settings. Rows whose target is missing are hidden. */
class QuickSettingsActivity : PanelActivity() {

    override fun kicker(): CharSequence = getString(R.string.settings)
    override fun sheetTitle(): CharSequence = getString(R.string.quick_settings)

    override fun build() {
        sleepRow()
        row(R.drawable.ic_wifi, R.string.network, Intent(Settings.ACTION_WIFI_SETTINGS))
        row(R.drawable.ic_display, R.string.display, Intent(Settings.ACTION_DISPLAY_SETTINGS))
        row(R.drawable.ic_volume, R.string.sound, Intent(Settings.ACTION_SOUND_SETTINGS))
        row(R.drawable.ic_bluetooth, R.string.remotes, Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
        row(R.drawable.ic_clock, R.string.date_time, Intent(Settings.ACTION_DATE_SETTINGS))
        row(R.drawable.ic_bell, R.string.notifications, Intent("com.android.tv.action.OPEN_NOTIFICATIONS_PANEL"))
        addDivider()
        addRow(R.drawable.ic_tv, getString(R.string.ctv_settings)) {
            startActivity(android.content.Intent(this, SettingsActivity::class.java)); finish()
        }
        row(R.drawable.ic_settings, R.string.all_settings,
            Intent(Settings.ACTION_SETTINGS).setPackage("com.android.tv.settings"), Intent(Settings.ACTION_SETTINGS))
    }

    private fun sleepLabel(): String {
        val left = SleepTimer.remaining(this)
        if (left == 0) return getString(R.string.off)
        val at = java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(java.util.Date(Prefs(this).sleepAt))
        val how = if (SleepTimer.canPowerOff(this)) "" else " · " + getString(R.string.sleep_saver)
        return getString(R.string.sleep_in, left, at) + how
    }

    private fun sleepRow() {
        lateinit var v: android.view.View
        v = addRow(R.drawable.ic_clock, getString(R.string.sleep_timer), sub = sleepLabel(), chevron = false) {
            // Cycle Off → 30 → 60 → 90 → 120 → Off, starting from the option after the current remaining time.
            val left = SleepTimer.remaining(this)
            val opts = SleepTimer.OPTIONS_MIN
            val next = opts.firstOrNull { it > left } ?: 0
            SleepTimer.set(this, next)
            v.findViewById<android.widget.TextView>(R.id.rowSub).text = sleepLabel()
        }
        addDivider()
    }

    private fun row(icon: Int, label: Int, vararg intents: Intent) {
        val target = intents.firstOrNull { it.resolveActivity(packageManager) != null } ?: return
        addRow(icon, getString(label)) { if (Launch.first(this, label, target)) finish() }
    }
}
