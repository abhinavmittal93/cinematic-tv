package com.cinematictv.launcher

import android.content.ComponentName
import android.content.pm.ApplicationInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LogicTest {

    private fun app(pkg: String, label: String = pkg, leanback: Boolean = true, category: Int = ApplicationInfo.CATEGORY_UNDEFINED,
                    isGame: Boolean = false, isSystem: Boolean = false) =
        AppEntry(pkg, label, ComponentName(pkg, "$pkg.Main"), leanback, null, null, 0L, category, isGame, isSystem)

    // ---- Categories ----

    @Test fun knownPackagesUseCuratedCategory() {
        assertEquals(AppCategory.MOVIES, app("com.netflix.ninja").kind)
        assertEquals(AppCategory.MOVIES, app("com.cbs.ca", "Paramount+").kind)
        assertEquals(AppCategory.SPORTS, app("tv.willow").kind)
        assertEquals(AppCategory.SPORTS, app("com.cricbuzz.android.tv").kind)
        assertEquals(AppCategory.LIVE, app("com.mediatek.wwtv.tvcenter").kind)
        assertEquals(AppCategory.MUSIC, app("com.google.android.youtube.tvmusic").kind)
        assertEquals(AppCategory.GAMES, app("com.google.android.play.games").kind)
        assertEquals(AppCategory.UTILITIES, app("com.android.vending").kind)
    }

    @Test fun declaredMetadataBeatsKeywords() {
        assertEquals(AppCategory.MUSIC, app("com.example.videoish", category = ApplicationInfo.CATEGORY_AUDIO).kind)
        assertEquals(AppCategory.GAMES, app("com.example.thing", isGame = true).kind)
    }

    @Test fun keywordsClassifyUnknownApps() {
        assertEquals(AppCategory.SPORTS, app("com.example.golfnow").kind)
        assertEquals(AppCategory.MUSIC, app("com.example.radio").kind)
        assertEquals(AppCategory.MOVIES, app("com.example.moviebox").kind)
    }

    @Test fun unknownAndSystemAppsAreUtilities() {
        assertEquals(AppCategory.UTILITIES, app("com.example.flashlight").kind)
        // System apps never match on keywords (e.g. a vendor "video" service).
        assertEquals(AppCategory.UTILITIES, app("com.vendor.videoservice", isSystem = true).kind)
    }

    // ---- Dock seeding ----

    @Test fun seedPutsPreferredStreamingAppsFirstAndSkipsStore() {
        val installed = listOf(
            app("com.android.vending"), app("a.first"), app("com.disney.disneyplus"),
            app("com.netflix.ninja"), app("b.phone.only", leanback = false),
        )
        val seed = Prefs.seed(installed)
        assertEquals(listOf("com.netflix.ninja", "com.disney.disneyplus", "a.first"), seed)
    }

    @Test fun seedIsCappedAtEight() {
        val installed = (1..20).map { app("pkg.$it") }
        assertEquals(8, Prefs.seed(installed).size)
    }

    // ---- Recent ----

    @Test fun recentMovesToFrontWithoutDuplicates() {
        assertEquals(listOf("b", "a", "c"), Prefs.pushRecent(listOf("a", "b", "c"), "b"))
        assertEquals(listOf("new", "a"), Prefs.pushRecent(listOf("a"), "new"))
    }

    @Test fun recentIsCapped() {
        val r = (1..30).fold(emptyList<String>()) { acc, i -> Prefs.pushRecent(acc, "p$i") }
        assertEquals(Prefs.RECENT_MAX, r.size)
        assertEquals("p30", r.first())
        assertTrue("p1" !in r)
    }
}
