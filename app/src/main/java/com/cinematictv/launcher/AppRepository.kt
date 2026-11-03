package com.cinematictv.launcher

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.util.concurrent.Executors

class AppEntry(
    val packageName: String,
    val label: String,
    val component: ComponentName,
    val isLeanback: Boolean,
    val banner: Drawable?,
    val icon: Drawable?,
    val lastUpdate: Long,
    /** ApplicationInfo.category (API 26+), or CATEGORY_UNDEFINED. */
    val category: Int = ApplicationInfo.CATEGORY_UNDEFINED,
    val isGame: Boolean = false,
    val isSystem: Boolean = false,
) {
    /** Cinematic TV's own category (see Categories). */
    val kind: AppCategory by lazy { Categories.of(this) }
}

/**
 * Discovers launchable apps dynamically (LEANBACK_LAUNCHER first, then LAUNCHER) and keeps a
 * process-wide cache of labels/banners/icons so drawables are loaded once per package version.
 */
object AppRepository {
    private const val TAG = "CinematicTV"
    private val io = Executors.newSingleThreadExecutor { r -> Thread(r, "ctv-apps").apply { priority = Thread.MIN_PRIORITY } }
    private val main = Handler(Looper.getMainLooper())
    private val cache = HashMap<String, AppEntry>()

    @Volatile
    var apps: List<AppEntry> = emptyList()
        private set
    private val byPackage = HashMap<String, AppEntry>()

    fun get(pkg: String): AppEntry? = byPackage[pkg]

    fun invalidate(pkg: String) = synchronized(cache) { cache.remove(pkg) }

    fun refresh(context: Context, done: (List<AppEntry>) -> Unit) {
        val app = context.applicationContext
        io.execute {
            val result = try { query(app) } catch (t: Throwable) {
                Log.e(TAG, "App discovery failed", t); apps
            }
            main.post {
                apps = result
                byPackage.clear()
                for (e in result) byPackage[e.packageName] = e
                done(result)
            }
        }
    }

    private fun query(ctx: Context): List<AppEntry> {
        val pm = ctx.packageManager
        val self = ctx.packageName
        val seen = LinkedHashMap<String, AppEntry>()

        fun collect(category: String, leanback: Boolean) {
            val intent = Intent(Intent.ACTION_MAIN).addCategory(category)
            for (ri in pm.queryIntentActivities(intent, 0)) {
                val ai = ri.activityInfo ?: continue
                val pkg = ai.packageName
                if (pkg == self || seen.containsKey(pkg) || !ai.enabled || !ai.applicationInfo.enabled) continue
                // Phone-style system entries (factory/test services) have no place on a TV home screen.
                if (!leanback && ai.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0) continue
                val updated = try { pm.getPackageInfo(pkg, 0).lastUpdateTime } catch (_: PackageManager.NameNotFoundException) { continue }
                val cached = synchronized(cache) { cache[pkg] }
                val entry = if (cached != null && cached.lastUpdate == updated && cached.isLeanback == leanback) cached else {
                    val banner = if (leanback) safe { ai.loadBanner(pm) ?: ai.applicationInfo.loadBanner(pm) } else null
                    val icon = if (banner == null) safe { ai.loadIcon(pm) } else null
                    AppEntry(pkg, ri.loadLabel(pm)?.toString() ?: pkg, ComponentName(pkg, ai.name), leanback, banner, icon, updated,
                        ai.applicationInfo.category,
                        @Suppress("DEPRECATION") (ai.applicationInfo.flags and ApplicationInfo.FLAG_IS_GAME != 0),
                        ai.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0 &&
                            ai.applicationInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP == 0)
                        .also { synchronized(cache) { cache[pkg] = it } }
                }
                seen[pkg] = entry
            }
        }
        collect(Intent.CATEGORY_LEANBACK_LAUNCHER, true)
        collect(Intent.CATEGORY_LAUNCHER, false)
        synchronized(cache) { cache.keys.retainAll(seen.keys) }
        return seen.values.sortedBy { it.label.lowercase() }
    }

    private inline fun <T> safe(block: () -> T?): T? = try { block() } catch (t: Throwable) { null }

    /** Launch an app. Returns false (never throws) if it is missing or can't be started. */
    fun launch(context: Context, entry: AppEntry): Boolean {
        val pm = context.packageManager
        val intent = pm.getLeanbackLaunchIntentForPackage(entry.packageName)
            ?: pm.getLaunchIntentForPackage(entry.packageName)
            ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        return try {
            context.startActivity(intent)
            Prefs(context).recordLaunch(entry.packageName)
            true
        } catch (t: Throwable) {
            Log.w(TAG, "Cannot launch ${entry.packageName}", t); false
        }
    }
}
