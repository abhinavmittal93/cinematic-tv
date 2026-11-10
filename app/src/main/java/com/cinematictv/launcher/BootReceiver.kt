package com.cinematictv.launcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log

/**
 * At boot the MediaTek firmware resumes the last TV input (ACTION_REQUEST_TOP_RESUME), which leaves a
 * "No signal" screen over Home. Google's launcher used to bring Home back afterwards; this does the same.
 * Controlled by Settings › "Start on Home screen".
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != Intent.ACTION_LOCKED_BOOT_COMPLETED && action != Intent.ACTION_BOOT_COMPLETED) return
        // LOCKED_BOOT_COMPLETED arrives early, before normal app storage is available: read the device-protected copy.
        if (!Prefs.bootPrefs(context).getBoolean("start_on_home", true)) return
        val app = context.applicationContext
        if (action == Intent.ACTION_BOOT_COMPLETED && handledEarly) return
        if (action == Intent.ACTION_LOCKED_BOOT_COMPLETED) handledEarly = true
        val pending = goAsync()
        val main = Handler(Looper.getMainLooper())
        // The firmware resumes the TV input a moment after boot; bring Home forward after it, twice to be safe.
        main.postDelayed({ goHome(app) }, 2500)
        main.postDelayed({ goHome(app); pending.finish() }, 7000)
    }

    private companion object { @Volatile var handledEarly = false }

    private fun goHome(ctx: Context) {
        try {
            // Whichever home screen the user has chosen (Cinematic TV or another launcher), not necessarily ours.
            val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            ctx.packageManager.resolveActivity(home, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)
                ?.activityInfo?.takeIf { it.name != "com.android.internal.app.ResolverActivity" }
                ?.let { home.setClassName(it.packageName, it.name) }
                ?: home.setClass(ctx, MainActivity::class.java)   // no single default: fall back to Cinematic TV
            ctx.startActivity(home.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
        } catch (t: Throwable) {
            Log.w("CinematicTV", "Could not bring Home forward after boot", t)
        }
    }
}
