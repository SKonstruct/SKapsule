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

    /** How long each event stays on the card before the next one, as in KnightLauncher. */
    const val ROTATE_INTERVAL_MS = 8_000L

    // An event running out within this long gets the warning chip (Event.isEndingSoon upstream).
    private val ENDING_SOON_MS = TimeUnit.DAYS.toMillis(2)

    /** One entry in the card's rotation. [imageUrl] and [link] are null when absent or not HTTPS. */
    data class Event(
        val title: String,
        val body: String,
        val imageUrl: String?,
        val link: String?,
        val startsAt: Long,
        val endsAt: Long,
    ) {
        fun isEndingSoon(now: Long = System.currentTimeMillis()): Boolean =
            endsAt != 0L && startsAt <= now && now < endsAt && endsAt - now < ENDING_SOON_MS
    }

    /** The events to rotate through, in feed order; empty when there is nothing to show. */
    suspend fun fetch(context: Context): List<Event> = withContext(Dispatchers.IO) {
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
        } ?: return@withContext emptyList()

        runCatching { parse(body) }.getOrElse { Log.w(TAG, "malformed news payload", it); emptyList() }
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
     * Mirrors KnightLauncher 2.8's `LauncherEventHandler.updateBanner`: the Official
     * server's `events`, minus any that have already ended (upcoming ones stay), and
     * `defaultEvent*` only when nothing else is left. The legacy `announce*` fields are
     * still sent for older launchers but KnightLauncher no longer reads them, so neither
     * do we.
     *
     * Each field packs two values: `banner` is `url|intensity` (the intensity is the
     * desktop launcher's dimming factor; we use our own scrim) and `content` is
     * `title|body`. Timestamps arrive as JSON *strings*.
     */
    internal fun parse(payload: String, now: Long = System.currentTimeMillis()): List<Event> {
        val servers = JSONObject(stripTrailingCommas(payload)).optJSONArray("serverlist")
            ?: return emptyList()
        val all = (0 until servers.length()).mapNotNull { servers.optJSONObject(it) }
        val server = all.firstOrNull { it.optString("name") == "Official" } ?: all.firstOrNull()
            ?: return emptyList()

        val listed = server.optJSONArray("events")
        val events = (0 until (listed?.length() ?: 0))
            .mapNotNull { listed!!.optJSONObject(it) }
            .mapNotNull { event(it.optString("banner"), it.optString("content"), it.optString("link"),
                it.optString("startsAt"), it.optString("endsAt")) }
            .filter { it.endsAt == 0L || it.endsAt > now }
        if (events.isNotEmpty()) return events

        return listOfNotNull(event(server.optString("defaultEventBanner"),
            server.optString("defaultEventContent"), server.optString("defaultEventLink"),
            server.optString("defaultEventStartsAt"), server.optString("defaultEventEndsAt")))
    }

    /**
     * One event, or null if it has no title to show. The service is cleartext, so the
     * image and link are only kept when they are HTTPS; an event without them still
     * shows, as it does upstream, just without a banner or a tap target.
     */
    private fun event(banner: String, content: String, link: String, startsAt: String, endsAt: String): Event? {
        val title = content.substringBefore('|').trim()
        if (title.isEmpty() || title.equals("null", ignoreCase = true)) return null
        return Event(
            title = title,
            body = if ('|' in content) content.substringAfter('|').replace("\\n", "\n") else "",
            imageUrl = banner.substringBefore('|').takeIf { it.startsWith("https://") },
            link = link.takeIf { it.startsWith("https://") },
            startsAt = startsAt.toLongOrNull() ?: 0L,
            endsAt = endsAt.toLongOrNull() ?: 0L,
        )
    }

    /**
     * Flamingo writes a trailing comma after the last field of every `events` entry
     * (`"endsAt":"…",}`). KnightLauncher links the reference org.json, which accepts that;
     * Android's org.json does not, and rejects the whole payload. Drops any comma that
     * directly precedes a `}` or `]`, leaving string contents untouched.
     */
    internal fun stripTrailingCommas(json: String): String {
        val out = StringBuilder(json.length)
        var inString = false
        var escaped = false
        for (c in json) {
            if (inString) {
                when {
                    escaped -> escaped = false
                    c == '\\' -> escaped = true
                    c == '"' -> inString = false
                }
            } else if (c == '"') {
                inString = true
            } else if (c == '}' || c == ']') {
                // Outside a string, everything after the previous token is whitespace, so a
                // comma found here is structural.
                var i = out.length - 1
                while (i >= 0 && out[i].isWhitespace()) i--
                if (i >= 0 && out[i] == ',') out.deleteCharAt(i)
            }
            out.append(c)
        }
        return out.toString()
    }

    /**
     * The card's chip: how long until the event starts, how long it has left, or that it
     * has ended. Null when neither bound is set -- an evergreen event has nothing to
     * count down.
     */
    fun statusLabel(context: Context, startsAt: Long, endsAt: Long): String? {
        val now = System.currentTimeMillis()
        return when {
            startsAt <= 0L && endsAt <= 0L -> null
            startsAt > now -> context.getString(R.string.news_starts_in, until(context, startsAt - now))
            endsAt in 1 until now -> context.getString(R.string.news_ended)
            endsAt > 0L -> context.getString(R.string.news_ends_in, until(context, endsAt - now))
            else -> null
        }
    }

    private fun until(context: Context, remaining: Long): String {
        val hours = TimeUnit.MILLISECONDS.toHours(remaining)
        return when {
            hours >= 48 -> context.getString(R.string.news_duration_days, hours / 24)
            hours >= 1 -> context.getString(R.string.news_duration_hours, hours)
            else -> context.getString(R.string.news_duration_soon)
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
