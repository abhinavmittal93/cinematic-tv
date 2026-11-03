package com.cinematictv.launcher

import android.content.Context

/** Ordered favourites, persisted in SharedPreferences. */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("cinematic_tv", Context.MODE_PRIVATE)

    var seeded: Boolean
        get() = sp.getBoolean("seeded", false)
        set(v) { sp.edit().putBoolean("seeded", v).apply() }

    var favourites: MutableList<String>
        get() = sp.getString("favourites", "")!!.split(',').filter { it.isNotEmpty() }.toMutableList()
        set(v) { sp.edit().putString("favourites", v.joinToString(",")).apply() }

    fun isFavourite(pkg: String) = favourites.contains(pkg)

    fun toggleFavourite(pkg: String): Boolean {
        val list = favourites
        val added = if (list.remove(pkg)) false else { list.add(pkg); true }
        favourites = list
        return added
    }

    companion object {
        /** Preferred order for well-known streaming apps — used only if they are actually installed. */
        val PREFERRED = listOf(
            "com.netflix.ninja", "com.netflix.mediaclient",
            "com.amazon.amazonvideo.livingroom", "com.amazon.avod.thirdpartyclient",
            "com.google.android.youtube.tv", "com.google.android.youtube.tvunplugged",
            "com.disney.disneyplus",
            "com.apple.atve.androidtv.appletv", "com.apple.atve.amazon.appletv",
        )
        private const val SEED_COUNT = 8
        private val SKIP_SEED = setOf("com.android.vending", "com.android.tv.settings", "com.google.android.tvlauncher")

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
