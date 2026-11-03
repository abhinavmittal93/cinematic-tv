package com.cinematictv.launcher

import android.content.Intent
import android.provider.Settings

/** Control Centre: shortcuts to the most-used TV settings. Rows whose target is missing are hidden. */
class QuickSettingsActivity : PanelActivity() {

    override fun kicker(): CharSequence = getString(R.string.settings)
    override fun sheetTitle(): CharSequence = getString(R.string.quick_settings)

    override fun build() {
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

    private fun row(icon: Int, label: Int, vararg intents: Intent) {
        val target = intents.firstOrNull { it.resolveActivity(packageManager) != null } ?: return
        addRow(icon, getString(label)) { if (Launch.first(this, label, target)) finish() }
    }
}
