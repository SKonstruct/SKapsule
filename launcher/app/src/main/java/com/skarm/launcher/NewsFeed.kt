package com.skarm.launcher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

/**
 * The in-game announcement shown on the home screen, from KnightLauncher's
 * "Flamingo" service — the same feed the desktop launcher renders, maintained by
 * its author, so both launchers show the same thing at the same time.
 *
 * The service speaks plain HTTP on port 6060 (it has no TLS listener), so the
 * payload is untrusted in the strong sense: an attacker on the path can rewrite
 * it. Everything it hands us is therefore treated as hostile — the image and the
 * link are dropped unless they are HTTPS, and the download is size-capped.
 */
object NewsFeed {

    private const val TAG = "NewsFeed"

    // machineId is a constant, not a device id: Flamingo uses it to bind beta
    // codes, which we never redeem, so there is nothing to gain by identifying
    // this install and a tracking vector in doing so.
    private const val URL_STRING =
        "http://flamingo.knightlauncher.com:6060/server-list/?machineId=skapsule-android"

    private const val PREFS_NAME = "launcher_prefs"
    private const val KEY_CACHE = "news_cache_json"
    private const val KEY_CACHE_TIME = "news_cache_time"
    private val CACHE_TTL_MS = TimeUnit.HOURS.toMillis(6)

    private const val MAX_IMAGE_BYTES = 1024 * 1024

    data class Announcement(
        val title: String,
        val body: String,
        val imageUrl: String,
        val link: String,
        val endsAt: Long,
    )

    suspend fun fetch(context: Context): Announcement? = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val cached = prefs.getString(KEY_CACHE, null)
        val age = System.currentTimeMillis() - prefs.getLong(KEY_CACHE_TIME, 0L)

        val body = if (cached != null && age in 0 until CACHE_TTL_MS) {
            cached
        } else {
            val fetched = runCatching { request() }.getOrElse {
                Log.w(TAG, "news fetch failed", it); null
            }
            if (fetched != null) {
                prefs.edit()
                    .putString(KEY_CACHE, fetched)
                    .putLong(KEY_CACHE_TIME, System.currentTimeMillis())
                    .apply()
            }
            // A stale cache still beats an empty home screen when the host is down.
            fetched ?: cached
        } ?: return@withContext null

        runCatching { parse(body) }.getOrElse { Log.w(TAG, "malformed news payload", it); null }
    }

    private fun request(): String? {
        val conn = (URL(URL_STRING).openConnection() as HttpURLConnection).apply {
            setRequestProperty("User-Agent", "SKapsule-Android")
            connectTimeout = 10_000
            readTimeout = 10_000
        }
        return try {
            if (conn.responseCode != 200) null else conn.inputStream.bufferedReader().readText()
        } finally {
            conn.disconnect()
        }
    }

    /**
     * Flamingo packs two values into each announcement field:
     * `announceBanner` is `url|intensity` (the intensity is the desktop
     * launcher's dimming factor; we use our own scrim) and `announceContent` is
     * `title|body`. The timestamps arrive as JSON *strings* despite being longs
     * upstream. Returns null for anything that is not a complete, live, HTTPS
     * announcement.
     */
    private fun parse(payload: String): Announcement? {
        val servers = JSONObject(payload).optJSONArray("serverlist") ?: return null
        for (i in 0 until servers.length()) {
            val server = servers.optJSONObject(i) ?: continue
            if (server.optString("announceType").let { it.isEmpty() || it == "0" }) continue

            val imageUrl = server.optString("announceBanner").substringBefore('|')
            val content = server.optString("announceContent")
            val link = server.optString("announceBannerLink")
            if (!imageUrl.startsWith("https://") || !link.startsWith("https://")) continue
            if (!content.contains('|')) continue

            val now = System.currentTimeMillis()
            val startsAt = server.optString("announceBannerStartsAt").toLongOrNull() ?: 0L
            val endsAt = server.optString("announceBannerEndsAt").toLongOrNull() ?: 0L
            if (startsAt > now || endsAt <= now) continue

            return Announcement(
                title = content.substringBefore('|'),
                body = content.substringAfter('|').replace("\\n", "\n"),
                imageUrl = imageUrl,
                link = link,
                endsAt = endsAt,
            )
        }
        return null
    }

    /** Countdown for the card's chip. */
    fun endsInLabel(context: Context, endsAt: Long): String {
        val remaining = endsAt - System.currentTimeMillis()
        val hours = TimeUnit.MILLISECONDS.toHours(remaining)
        return when {
            hours >= 48 -> context.getString(R.string.news_ends_in_days, hours / 24)
            hours >= 1 -> context.getString(R.string.news_ends_in_hours, hours)
            else -> context.getString(R.string.news_ends_soon)
        }
    }

    /** Banner image, cached on disk. Null if it cannot be fetched or decoded. */
    suspend fun image(context: Context, url: String, reqWidth: Int): Bitmap? =
        withContext(Dispatchers.IO) {
            runCatching {
                val dir = File(context.cacheDir, "news").apply { mkdirs() }
                val file = File(dir, url.hashCode().toUInt().toString(16) + ".img")
                if (!file.exists()) download(url, file)
                decode(file, reqWidth)
            }.getOrElse { Log.w(TAG, "banner image failed", it); null }
        }

    private fun download(url: String, dest: File) {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            setRequestProperty("User-Agent", "SKapsule-Android")
            connectTimeout = 10_000
            readTimeout = 15_000
            instanceFollowRedirects = true
        }
        try {
            if (conn.responseCode != 200) throw java.io.IOException("HTTP ${conn.responseCode}")
            val tmp = File(dest.absolutePath + ".part")
            conn.inputStream.use { input ->
                tmp.outputStream().use { output ->
                    val buffer = ByteArray(16 * 1024)
                    var total = 0
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        total += read
                        // The service is unauthenticated cleartext; do not let it
                        // decide how much of the cache directory to fill.
                        if (total > MAX_IMAGE_BYTES) throw java.io.IOException("banner too large")
                        output.write(buffer, 0, read)
                    }
                }
            }
            if (!tmp.renameTo(dest)) {
                tmp.delete()
                throw java.io.IOException("failed to stage banner")
            }
        } finally {
            conn.disconnect()
        }
    }

    private fun decode(file: File, reqWidth: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0) return null

        var sample = 1
        while (reqWidth > 0 && bounds.outWidth / sample > reqWidth * 2) sample *= 2
        return BitmapFactory.decodeFile(
            file.absolutePath,
            BitmapFactory.Options().apply { inSampleSize = sample },
        )
    }
}
