package com.cinematictv.launcher

import android.content.pm.ApplicationInfo

enum class AppCategory(val label: Int) {
    MOVIES(R.string.c_movies), SPORTS(R.string.c_sports), LIVE(R.string.c_live),
    MUSIC(R.string.c_music), GAMES(R.string.c_games), UTILITIES(R.string.c_utilities)
}

/**
 * Most TV apps don't declare ApplicationInfo.category, so classify in three steps:
 * a curated list of well-known TV packages, then the app's own metadata, then package-name keywords.
 */
object Categories {
    private val KNOWN: Map<String, AppCategory> = buildMap {
        fun put(c: AppCategory, vararg p: String) = p.forEach { put(it, c) }
        put(AppCategory.MOVIES,
            "com.netflix.ninja", "com.netflix.mediaclient",
            "com.amazon.amazonvideo.livingroom", "com.amazon.avod.thirdpartyclient",
            "com.disney.disneyplus", "com.apple.atve.androidtv.appletv", "com.apple.atve.amazon.appletv",
            "ca.bellmedia.cravetv", "com.cbs.ca", "com.cbs.ott", "com.tubitv",
            "com.google.android.videos", "com.google.android.youtube.tv", "com.google.android.youtube.tvkids",
            "com.hulu.livingroomplus", "com.wbd.stream", "com.hbo.hbonow", "com.peacocktv.peacockandroid",
            "com.plexapp.android", "org.xbmc.kodi", "com.mxtech.videoplayer.ad", "org.videolan.vlc",
            "com.crunchyroll.crunchyroid", "com.britbox.us", "com.mubi", "com.curiositystream.curiositystream",
            "com.gotv.crackle.handset", "com.sling", "com.philo.philo", "com.vudu.android.app",
            "com.zee5.android.tv", "in.startv.hotstar", "com.jio.media.stb.ondemand", "com.sonyliv")
        put(AppCategory.SPORTS,
            "tv.willow", "com.cricbuzz.android.tv", "com.espn.score_center", "com.espn.gtv",
            "com.dazn", "com.fubo.firetv.screen", "com.fubotv.android", "com.nba.app", "com.nfl.fantasy.core.android",
            "com.mlb.atbat", "com.bce.tsn.android", "ca.rogers.sportsnet", "com.foxsports.android",
            "com.formula1.f1tv", "com.ufc.fightpass")
        put(AppCategory.LIVE,
            "com.mediatek.wwtv.tvcenter", "com.google.android.tv", "com.android.tv", "tv.pluto.android",
            "com.sling.livetv", "com.cbc.gem", "ca.ctv.ctvgo", "com.global.globalgo", "com.samsung.android.tvplus",
            "com.xumo.xumo.tv", "com.haystack.android")
        put(AppCategory.MUSIC,
            "com.google.android.youtube.tvmusic", "com.spotify.tv.android", "com.spotify.music",
            "com.apple.android.music", "com.amazon.mp3", "com.aspiro.tidal", "deezer.android.tv",
            "com.pandora.android", "com.soundcloud.android", "com.audible.application", "tunein.player",
            "com.stingray.music", "com.qobuz.music")
        put(AppCategory.GAMES,
            "com.google.android.play.games", "com.nvidia.geforcenow", "com.valvesoftware.steamlink",
            "com.microsoft.xcloud", "com.gameloft.android.ANMP.GloftA8HM")
        put(AppCategory.UTILITIES,
            "com.android.vending", "com.android.tv.settings", "jp.funai.android.tv.helpapps",
            "com.google.android.katniss", "com.android.chrome", "org.mozilla.tv.firefox",
            "com.estrongs.android.pop", "com.google.android.apps.tv.launcherx")
    }

    private val KEYWORDS = listOf(
        AppCategory.SPORTS to Regex("sport|espn|cricket|cricbuzz|willow|nba|nfl|nhl|mlb|soccer|football|f1tv|dazn|golf|tennis|ufc"),
        AppCategory.MUSIC to Regex("music|audio|radio|spotify|podcast|tidal|deezer|pandora|soundcloud|karaoke"),
        AppCategory.GAMES to Regex("game|games|gaming|arcade|puzzle"),
        AppCategory.LIVE to Regex("livetv|live\\.tv|tvcenter|channels|iptv|pluto|news"),
        AppCategory.MOVIES to Regex("video|movie|movies|film|cinema|stream|tvplus|ott|vod|anime|kids"),
    )

    fun of(e: AppEntry): AppCategory {
        KNOWN[e.packageName]?.let { return it }
        when (e.category) {
            ApplicationInfo.CATEGORY_VIDEO -> return AppCategory.MOVIES
            ApplicationInfo.CATEGORY_AUDIO -> return AppCategory.MUSIC
            ApplicationInfo.CATEGORY_GAME -> return AppCategory.GAMES
            ApplicationInfo.CATEGORY_NEWS -> return AppCategory.LIVE
        }
        if (e.isGame) return AppCategory.GAMES
        val name = e.packageName.lowercase() + " " + e.label.lowercase()
        if (!e.isSystem) KEYWORDS.firstOrNull { it.second.containsMatchIn(name) }?.let { return it.first }
        return AppCategory.UTILITIES
    }
}
