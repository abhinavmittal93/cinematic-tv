package com.cinematictv.launcher

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Dock + settings backup as JSON in the app's external files dir, which adb can read and write
 * without extra permissions:  /sdcard/Android/data/com.cinematictv.launcher/files/cinematic-tv-backup.json
 * Written automatically (debounced) whenever a backed-up setting changes. Caches and history are not included.
 */
object Backup {
    const val FILE = "cinematic-tv-backup.json"
    private val BOOL = listOf("seeded", "start_on_home", "online_photos", "dream_clock", "dream_motion", "weather_on", "fahrenheit")
    private val INT = listOf("photo_seconds")
    private val STR = listOf("weather_city")
    private val KEYS = BOOL + INT + STR + "favourites"

    fun file(ctx: Context): File? = ctx.getExternalFilesDir(null)?.let { File(it, FILE) }

    fun export(ctx: Context) {
        val sp = ctx.getSharedPreferences("cinematic_tv", Context.MODE_PRIVATE)
        val o = JSONObject().put("version", 1).put("app", BuildConfig.VERSION_NAME)
        o.put("favourites", JSONArray(Prefs(ctx).favourites))
        BOOL.filter(sp::contains).forEach { o.put(it, sp.getBoolean(it, false)) }
        INT.filter(sp::contains).forEach { o.put(it, sp.getInt(it, 0)) }
        STR.filter(sp::contains).forEach { o.put(it, sp.getString(it, "")) }
        try { file(ctx)?.writeText(o.toString(2)) } catch (t: Throwable) { Log.w("CinematicTV", "Backup write failed", t) }
    }

    /** Returns false if there is no readable backup. */
    fun restore(ctx: Context): Boolean {
        val f = file(ctx)?.takeIf { it.exists() } ?: return false
        return try {
            val o = JSONObject(f.readText())
            val prefs = Prefs(ctx)
            o.optJSONArray("favourites")?.let { a -> prefs.favourites = (0 until a.length()).map(a::getString).toMutableList() }
            val e = ctx.getSharedPreferences("cinematic_tv", Context.MODE_PRIVATE).edit()
            BOOL.filter(o::has).forEach { e.putBoolean(it, o.getBoolean(it)) }
            INT.filter(o::has).forEach { e.putInt(it, o.getInt(it)) }
            STR.filter(o::has).forEach { e.putString(it, o.getString(it)) }
            e.commit()
            if (o.has("weather_city")) prefs.weatherCity = o.getString("weather_city") // also resets cached location
            if (o.has("start_on_home")) prefs.startOnHome = o.getBoolean("start_on_home") // mirror for early boot
            true
        } catch (t: Throwable) { Log.w("CinematicTV", "Restore failed", t); false }
    }

    internal fun isBackedUp(key: String?) = key in KEYS
}

/** Keeps the backup file current. */
class CinematicApp : Application() {
    private val main = Handler(Looper.getMainLooper())
    private val write = Runnable { Backup.export(this) }
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (Backup.isBackedUp(key)) { main.removeCallbacks(write); main.postDelayed(write, 1500) }
    }

    override fun onCreate() {
        super.onCreate()
        getSharedPreferences("cinematic_tv", MODE_PRIVATE).registerOnSharedPreferenceChangeListener(listener)
        main.postDelayed(write, 5000) // make sure a backup exists after install/upgrade
    }
}
