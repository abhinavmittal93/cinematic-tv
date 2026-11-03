package com.cinematictv.launcher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

class Photo(val file: File, val title: String, val caption: String)

/**
 * Daily landscape photos from Bing's public image archive (no account, no key).
 * Downloaded at most every 12 h, cached on disk (max 16, ~6 MB), decoded downsampled as RGB_565.
 * The only network host contacted is www.bing.com.
 */
object Photos {
    private const val TAG = "CinematicTV"
    private const val HOST = "https://www.bing.com"
    private const val MAX = 16
    private const val REFRESH_MS = 12L * 60 * 60 * 1000
    private val io = Executors.newSingleThreadExecutor { r -> Thread(r, "ctv-photos").apply { priority = Thread.MIN_PRIORITY } }
    private val decoder = Executors.newSingleThreadExecutor { r -> Thread(r, "ctv-decode").apply { priority = Thread.NORM_PRIORITY - 1 } }
    private val main = Handler(Looper.getMainLooper())

    @Volatile var list: List<Photo> = emptyList(); private set
    private val listeners = ArrayList<() -> Unit>()

    fun addListener(l: () -> Unit) { listeners.add(l) }
    fun removeListener(l: () -> Unit) { listeners.remove(l) }

    private fun dir(ctx: Context) = File(ctx.cacheDir, "photos").apply { mkdirs() }
    private fun index(ctx: Context) = File(dir(ctx), "index.json")

    /** Turn online photos on/off. Off deletes the cache and stops all network use. */
    fun setEnabled(ctx: Context, on: Boolean) {
        val app = ctx.applicationContext
        if (on) {
            app.getSharedPreferences("cinematic_tv", Context.MODE_PRIVATE).edit().remove("photos_fetched").apply()
            init(app)
        } else io.execute {
            dir(app).listFiles()?.forEach { it.delete() }
            publish(emptyList())
        }
    }

    /** Load the cached list immediately, then refresh from the network if stale. */
    fun init(ctx: Context) {
        val app = ctx.applicationContext
        if (!Prefs(app).onlinePhotos) { if (list.isNotEmpty()) publish(emptyList()); return }
        io.execute {
            val cached = readIndex(app)
            if (cached.isNotEmpty()) publish(cached)
            val prefs = app.getSharedPreferences("cinematic_tv", Context.MODE_PRIVATE)
            val last = prefs.getLong("photos_fetched", 0)
            if (cached.isEmpty() || System.currentTimeMillis() - last > REFRESH_MS) {
                try {
                    val fresh = fetch(app)
                    if (fresh.isNotEmpty()) {
                        prefs.edit().putLong("photos_fetched", System.currentTimeMillis()).apply()
                        publish(fresh)
                    }
                } catch (t: Throwable) {
                    Log.w(TAG, "Photo refresh failed (offline?)", t)
                }
            }
        }
    }

    private fun publish(p: List<Photo>) = main.post { list = p; listeners.toList().forEach { it() } }

    private fun readIndex(ctx: Context): List<Photo> = try {
        val arr = JSONArray(index(ctx).readText())
        (0 until arr.length()).mapNotNull {
            val o = arr.getJSONObject(it)
            val f = File(dir(ctx), o.getString("file"))
            if (f.length() > 0) Photo(f, o.optString("title"), o.optString("caption")) else null
        }
    } catch (_: Throwable) { emptyList() }

    private fun get(url: String): ByteArray {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 8000; c.readTimeout = 15000
        c.setRequestProperty("User-Agent", "CinematicTV/1.0")
        try {
            if (c.responseCode != 200) throw IllegalStateException("HTTP ${c.responseCode}")
            return c.inputStream.use { it.readBytes() }
        } finally { c.disconnect() }
    }

    private fun fetch(ctx: Context): List<Photo> {
        val out = LinkedHashMap<String, Photo>()
        val dir = dir(ctx)
        // The archive serves the last ~16 days in two pages of 8.
        for (idx in intArrayOf(0, 8)) {
            val json = JSONObject(String(get("$HOST/HPImageArchive.aspx?format=js&idx=$idx&n=8&mkt=en-US")))
            val images = json.optJSONArray("images") ?: continue
            for (i in 0 until images.length()) {
                val o = images.getJSONObject(i)
                val path = o.optString("url")
                if (!path.startsWith("/th?id=")) continue   // only same-host relative image URLs
                val name = (o.optString("hsh").ifEmpty { path.hashCode().toString() }) + ".jpg"
                val f = File(dir, name)
                if (f.length() == 0L) {
                    val bytes = get(HOST + path)
                    if (bytes.size < 10_000) continue
                    File(dir, "$name.part").apply { writeBytes(bytes); renameTo(f) }
                }
                val caption = o.optString("copyright").replace(Regex("\\s*\\(©.*\\)\\s*$"), "")
                out[name] = Photo(f, o.optString("title"), caption)
                if (out.size >= MAX) break
            }
        }
        val photos = out.values.toList()
        // Write index, then drop files no longer referenced.
        val arr = JSONArray()
        photos.forEach { arr.put(JSONObject().put("file", it.file.name).put("title", it.title).put("caption", it.caption)) }
        index(ctx).writeText(arr.toString())
        val keep = photos.map { it.file.name }.toSet() + "index.json"
        dir.listFiles()?.forEach { if (it.name !in keep) it.delete() }
        return photos
    }

    /**
     * Decode off the main thread at roughly [targetW]×[targetH]; result delivered on main.
     * [hq] = full 32-bit colour (no banding) for on-screen photos; false = RGB_565 for soft/blurred backdrops.
     */
    fun decode(photo: Photo, targetW: Int, targetH: Int, hq: Boolean = true, done: (Bitmap?) -> Unit) {
        decoder.execute {
            val bmp = try {
                val b = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(photo.file.path, b)
                var s = 1
                while (b.outWidth / (s * 2) >= targetW && b.outHeight / (s * 2) >= targetH) s *= 2
                BitmapFactory.decodeFile(photo.file.path, BitmapFactory.Options().apply {
                    inSampleSize = s
                    inPreferredConfig = if (hq) Bitmap.Config.ARGB_8888 else Bitmap.Config.RGB_565
                    inDither = !hq
                })?.let { b ->
                    // Never upscale here; only trim oversize sources to the screen with a filtered scale.
                    if (hq && (b.width > targetW * 1.25f && b.height > targetH * 1.25f)) {
                        val f = maxOf(targetW / b.width.toFloat(), targetH / b.height.toFloat())
                        Bitmap.createScaledBitmap(b, (b.width * f).toInt(), (b.height * f).toInt(), true).also { if (it !== b) b.recycle() }
                    } else b
                }
            } catch (t: Throwable) { Log.w(TAG, "Decode failed", t); null }
            // Start the GPU texture upload now, off the UI thread, so the crossfade's first frame doesn't stall.
            bmp?.prepareToDraw()
            main.post { done(bmp) }
        }
    }
}
