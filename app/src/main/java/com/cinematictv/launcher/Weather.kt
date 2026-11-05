package com.cinematictv.launcher

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.Executors
import kotlin.math.roundToInt

/**
 * Current weather from Open-Meteo (free, no account or key). Contacts only *.open-meteo.com.
 * Refreshed at most every 30 minutes; the city is resolved once via Open-Meteo geocoding and cached.
 */
object Weather {
    private const val TAG = "CinematicTV"
    private const val REFRESH_MS = 30L * 60 * 1000
    private val io = Executors.newSingleThreadExecutor { r -> Thread(r, "ctv-weather").apply { priority = Thread.MIN_PRIORITY } }
    private val main = Handler(Looper.getMainLooper())

    /** e.g. "15° · Cloudy · Toronto", or null when off/unavailable. */
    fun line(ctx: Context): String? {
        val p = Prefs(ctx)
        if (!p.weatherOn) return null
        val t = p.weatherTemp ?: return null
        val unit = if (p.fahrenheit) "°F" else "°"
        return "$t$unit · ${p.weatherText} · ${p.weatherPlace}"
    }

    fun refresh(ctx: Context, force: Boolean = false, done: () -> Unit) {
        val app = ctx.applicationContext
        val p = Prefs(app)
        if (!p.weatherOn) { main.post(done); return }
        if (!force && System.currentTimeMillis() - p.weatherFetched < REFRESH_MS && p.weatherTemp != null) { main.post(done); return }
        io.execute {
            try {
                if (p.weatherLat.isNaN()) geocode(p)
                if (!p.weatherLat.isNaN()) {
                    val unit = if (p.fahrenheit) "&temperature_unit=fahrenheit" else ""
                    val j = JSONObject(get("https://api.open-meteo.com/v1/forecast?latitude=${p.weatherLat}&longitude=${p.weatherLon}" +
                        "&current=temperature_2m,weather_code&timezone=auto$unit"))
                    val cur = j.getJSONObject("current")
                    p.weatherTemp = cur.getDouble("temperature_2m").roundToInt()
                    p.weatherText = describe(cur.getInt("weather_code"))
                    p.weatherFetched = System.currentTimeMillis()
                }
            } catch (t: Throwable) { Log.w(TAG, "Weather refresh failed (offline?)", t) }
            main.post(done)
        }
    }

    private fun geocode(p: Prefs) {
        val q = URLEncoder.encode(p.weatherCity, "UTF-8")
        val r = JSONObject(get("https://geocoding-api.open-meteo.com/v1/search?name=$q&count=1")).optJSONArray("results") ?: return
        if (r.length() == 0) return
        val o = r.getJSONObject(0)
        p.setWeatherLocation(o.getDouble("latitude"), o.getDouble("longitude"), o.getString("name"))
    }

    private fun get(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 8000; c.readTimeout = 10000
        try {
            if (c.responseCode != 200) throw IllegalStateException("HTTP ${c.responseCode}")
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally { c.disconnect() }
    }

    /** WMO weather interpretation codes. */
    fun describe(code: Int): String = when (code) {
        0 -> "Clear"
        1 -> "Mostly clear"
        2 -> "Partly cloudy"
        3 -> "Cloudy"
        45, 48 -> "Fog"
        in 51..57 -> "Drizzle"
        in 61..67 -> "Rain"
        in 71..77 -> "Snow"
        in 80..82 -> "Showers"
        85, 86 -> "Snow showers"
        in 95..99 -> "Thunderstorm"
        else -> "—"
    }

    /** Default city from the TV's time zone, e.g. America/Toronto → Toronto. */
    fun defaultCity(): String =
        java.util.TimeZone.getDefault().id.substringAfterLast('/').replace('_', ' ').ifBlank { "London" }
}
