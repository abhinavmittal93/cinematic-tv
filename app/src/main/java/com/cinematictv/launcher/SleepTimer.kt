package com.cinematictv.launcher

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Sleep timer. When it fires:
 *  - if Cinematic TV is an active device admin ("force lock" only), lockNow() turns the TV off (standby);
 *  - otherwise the screensaver starts, and the TV's own idle timeout powers it off later.
 */
object SleepTimer {
    val OPTIONS_MIN = intArrayOf(0, 30, 60, 90, 120)

    private fun pi(ctx: Context) = PendingIntent.getBroadcast(
        ctx, 1, Intent(ctx, SleepReceiver::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    fun set(ctx: Context, minutes: Int) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(pi(ctx))
        val prefs = Prefs(ctx)
        if (minutes <= 0) { prefs.sleepAt = 0; return }
        val at = System.currentTimeMillis() + minutes * 60_000L
        prefs.sleepAt = at
        am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi(ctx))
    }

    /** Minutes left, or 0 when off/expired. */
    fun remaining(ctx: Context): Int {
        val left = Prefs(ctx).sleepAt - System.currentTimeMillis()
        return if (left > 0) ((left + 59_999) / 60_000).toInt() else 0
    }

    fun admin(ctx: Context) = ComponentName(ctx, SleepAdmin::class.java)

    fun canPowerOff(ctx: Context): Boolean =
        (ctx.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager).isAdminActive(admin(ctx))

    fun sleepNow(ctx: Context) {
        val dpm = ctx.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        try {
            if (dpm.isAdminActive(admin(ctx))) { dpm.lockNow(); return }
        } catch (t: Throwable) { Log.w("CinematicTV", "lockNow failed", t) }
        // Fallback: start the screensaver.
        try {
            ctx.startActivity(Intent("com.google.android.pano.action.SLEEP").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (t: Throwable) { Log.w("CinematicTV", "Screensaver fallback failed", t) }
    }
}

class SleepReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Prefs(context).sleepAt = 0
        SleepTimer.sleepNow(context)
    }
}

/** Device admin limited to the "force lock" policy (turning the screen off). Enabled only via adb by the owner. */
class SleepAdmin : DeviceAdminReceiver()
