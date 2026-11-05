package com.cinematictv.launcher

import android.content.Context

/** Ordered favourites, persisted in SharedPreferences. */
class Prefs(context: Context) {
    private val appCtx = context.applicationContext
    private val sp = context.getSharedPreferences("cinematic_tv", Context.MODE_PRIVATE)

    var seeded: Boolean
        get() = sp.getBoolean("seeded", false)
        set(v) { sp.edit().putBoolean("seeded", v).apply() }

    var favourites: MutableList<String>
        get() = sp.getString("favourites", "")!!.split(',').filter { it.isNotEmpty() }.toMutableList()
        set(v) { sp.edit().putString("favourites", v.joinToString(",")).apply() }

    fun isFavourite(pkg: String) = favourites.contains(pkg)

    /** Most-recently-opened apps, newest first. Stored only on the TV; used for the Recent filter. */
    val recent: List<String>
        get() = sp.getString("recent", "")!!.split(',').filter { it.isNotEmpty() }

    fun recordLaunch(pkg: String) {
        val list = pushRecent(recent, pkg)
        sp.edit().putString("recent", list.joinToString(",")).apply()
    }

    fun forgetRecent() = sp.edit().remove("recent").apply()

    /** Bring Home back after boot instead of staying on the last TV input. */
    var startOnHome: Boolean
        get() = sp.getBoolean("start_on_home", true)
        set(v) {
            sp.edit().putBoolean("start_on_home", v).apply()
            bootPrefs(appCtx).edit().putBoolean("start_on_home", v).apply()
        }

    // Weather
    var weatherOn: Boolean
        get() = sp.getBoolean("weather_on", true)
        set(v) { sp.edit().putBoolean("weather_on", v).apply() }
    var fahrenheit: Boolean
        get() = sp.getBoolean("fahrenheit", false)
        set(v) { sp.edit().putBoolean("fahrenheit", v).putLong("weather_fetched", 0).apply() }
    var weatherCity: String
        get() = sp.getString("weather_city", null) ?: Weather.defaultCity()
        set(v) { sp.edit().putString("weather_city", v).remove("weather_lat").remove("weather_lon")
            .remove("weather_temp").putLong("weather_fetched", 0).apply() }
    val weatherLat: Double get() = sp.getString("weather_lat", null)?.toDoubleOrNull() ?: Double.NaN
    val weatherLon: Double get() = sp.getString("weather_lon", null)?.toDoubleOrNull() ?: Double.NaN
    val weatherPlace: String get() = sp.getString("weather_place", null) ?: weatherCity
    fun setWeatherLocation(lat: Double, lon: Double, place: String) =
        sp.edit().putString("weather_lat", lat.toString()).putString("weather_lon", lon.toString()).putString("weather_place", place).apply()
    var weatherTemp: Int?
        get() = if (sp.contains("weather_temp")) sp.getInt("weather_temp", 0) else null
        set(v) { sp.edit().apply { if (v == null) remove("weather_temp") else putInt("weather_temp", v) }.apply() }
    var weatherText: String
        get() = sp.getString("weather_text", "") ?: ""
        set(v) { sp.edit().putString("weather_text", v).apply() }
    var weatherFetched: Long
        get() = sp.getLong("weather_fetched", 0)
        set(v) { sp.edit().putLong("weather_fetched", v).apply() }

    var sleepAt: Long
        get() = sp.getLong("sleep_at", 0)
        set(v) { sp.edit().putLong("sleep_at", v).apply() }

    // Screensaver / photo options
    var onlinePhotos: Boolean
        get() = sp.getBoolean("online_photos", true)
        set(v) { sp.edit().putBoolean("online_photos", v).apply() }
    var photoSeconds: Int
        get() = sp.getInt("photo_seconds", 25)
        set(v) { sp.edit().putInt("photo_seconds", v).apply() }
    var dreamClock: Boolean
        get() = sp.getBoolean("dream_clock", true)
        set(v) { sp.edit().putBoolean("dream_clock", v).apply() }
    var dreamMotion: Boolean
        get() = sp.getBoolean("dream_motion", true)
        set(v) { sp.edit().putBoolean("dream_motion", v).apply() }

    fun toggleFavourite(pkg: String): Boolean {
        val list = favourites
        val added = if (list.remove(pkg)) false else { list.add(pkg); true }
        favourites = list
        return added
    }

    companion object {
        /** Device-protected copy of boot options, readable before the user is unlocked (early boot). */
        fun bootPrefs(ctx: Context) =
            ctx.createDeviceProtectedStorageContext().getSharedPreferences("cinematic_tv_boot", Context.MODE_PRIVATE)

        /** Preferred order for well-known streaming apps — used only if they are actually installed. */
        val PREFERRED = listOf(
            "com.netflix.ninja", "com.netflix.mediaclient",
            "com.amazon.amazonvideo.livingroom", "com.amazon.avod.thirdpartyclient",
            "com.google.android.youtube.tv", "com.google.android.youtube.tvunplugged",
            "com.disney.disneyplus",
            "com.apple.atve.androidtv.appletv", "com.apple.atve.amazon.appletv",
        )
        private const val SEED_COUNT = 8
        const val RECENT_MAX = 12
        val PHOTO_INTERVALS = intArrayOf(15, 25, 45, 90)
        private val SKIP_SEED = setOf("com.android.vending", "com.android.tv.settings", "com.google.android.tvlauncher")

        /** Move [pkg] to the front, de-duplicated, capped at [RECENT_MAX]. */
        fun pushRecent(recent: List<String>, pkg: String): List<String> =
            (listOf(pkg) + recent.filter { it != pkg }).take(RECENT_MAX)

        fun seed(installed: List<AppEntry>): List<String> {
            val pkgs = installed.map { it.packageName }.toSet()
            val out = LinkedHashSet<String>()
            PREFERRED.filterTo(out) { it in pkgs }
            for (e in installed) {
                if (out.size >= SEED_COUNT) break
                if (e.isLeanback && e.packageName !in SKIP_SEED) out.add(e.packageName)
            }
            return out.toList()
        }
    }
}
