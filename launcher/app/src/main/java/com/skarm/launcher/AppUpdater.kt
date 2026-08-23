package com.skarm.launcher

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInstaller
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * GitHub release check and (Android-only) in-place APK install.
 *
 * The API allows 60 unauthenticated requests an hour per IP, so a successful
 * response is cached and reused for [CACHE_TTL_MS] rather than re-fetched on
 * every cold start.
 */
object AppUpdater {
    private const val TAG = "AppUpdater"
    private const val RELEASE_URL = "https://api.github.com/repos/SKonstruct/SKapsule/releases/latest"
    private const val PREFS_NAME = "launcher_prefs"
    private const val KEY_CACHE_JSON = "update_cache_json"
    private const val KEY_CACHE_TIME = "update_cache_time"
    private const val KEY_SKIPPED_VERSION = "update_skipped_version"
    private const val CACHE_TTL_MS = 6 * 60 * 60 * 1000L
    private const val INSTALL_ACTION = "com.skarm.launcher.INSTALL_RESULT"

    data class Release(
        val version: String,
        val htmlUrl: String,
        /** APK asset to download, or null if the release ships no APK. */
        val apkUrl: String?,
    )

    fun skippedVersion(context: Context): String? =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_SKIPPED_VERSION, null)

    fun skipVersion(context: Context, version: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_SKIPPED_VERSION, version).apply()
    }

    suspend fun fetchLatest(context: Context): Release? = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val cached = prefs.getString(KEY_CACHE_JSON, null)
        val age = System.currentTimeMillis() - prefs.getLong(KEY_CACHE_TIME, 0L)
        val body = if (cached != null && age in 0 until CACHE_TTL_MS) {
            cached
        } else {
            val fetched = try {
                fetchReleaseJson()
            } catch (e: Exception) {
                Log.w(TAG, "Update check failed", e)
                null
            }
            if (fetched != null) {
                prefs.edit()
                    .putString(KEY_CACHE_JSON, fetched)
                    .putLong(KEY_CACHE_TIME, System.currentTimeMillis())
                    .apply()
            }
            // Fall back to a stale cache so an offline start still knows the last result.
            fetched ?: cached
        } ?: return@withContext null

        try {
            parseRelease(body)
        } catch (e: Exception) {
            Log.w(TAG, "Malformed release payload", e)
            null
        }
    }

    private fun fetchReleaseJson(): String? {
        val conn = (URL(RELEASE_URL).openConnection() as HttpURLConnection).apply {
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "SKapsule-Android")
            connectTimeout = 10_000
            readTimeout = 10_000
        }
        return try {
            if (conn.responseCode != 200) null
            else conn.inputStream.bufferedReader().readText()
        } finally {
            conn.disconnect()
        }
    }

    private fun parseRelease(body: String): Release? {
        val json = JSONObject(body)
        // optString returns "" (never null) for a missing key, so check emptiness.
        val tag = json.optString("tag_name").ifEmpty { return null }
        val htmlUrl = json.optString("html_url").ifEmpty { return null }
        val assets = json.optJSONArray("assets")
        var apkUrl: String? = null
        for (i in 0 until (assets?.length() ?: 0)) {
            val asset = assets!!.optJSONObject(i) ?: continue
            if (asset.optString("name").endsWith(".apk", ignoreCase = true)) {
                apkUrl = asset.optString("browser_download_url").ifEmpty { null }
                if (apkUrl != null) break
            }
        }
        return Release(tag.removePrefix("v"), htmlUrl, apkUrl)
    }

    /**
     * Downloads the APK and hands it to [PackageInstaller]. The system then shows
     * its own confirmation prompt; this returns as soon as that prompt is raised.
     */
    suspend fun downloadAndInstall(
        context: Context,
        release: Release,
        onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit,
    ) {
        val apkUrl = release.apkUrl ?: throw IOException("Release has no APK asset")
        val apk = File(context.cacheDir, "update-${release.version}.apk")
        withContext(Dispatchers.IO) {
            download(apkUrl, apk, onProgress)
            install(context, apk)
        }
    }

    private fun download(url: String, dest: File, onProgress: (Long, Long) -> Unit) {
        // Download to a temp file and rename, so an interrupted download never
        // leaves a truncated APK behind under the final name.
        val tmp = File(dest.absolutePath + ".part")
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            setRequestProperty("User-Agent", "SKapsule-Android")
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = true
        }
        try {
            if (conn.responseCode != 200) {
                throw IOException("Download failed with HTTP ${conn.responseCode}")
            }
            val total = conn.contentLengthLong
            var written = 0L
            conn.inputStream.use { input ->
                tmp.outputStream().use { output ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val read = input.read(buf)
                        if (read < 0) break
                        output.write(buf, 0, read)
                        written += read
                        onProgress(written, total)
                    }
                }
            }
        } finally {
            conn.disconnect()
        }
        dest.delete()
        if (!tmp.renameTo(dest)) {
            tmp.delete()
            throw IOException("Failed to stage the downloaded APK")
        }
    }

    private fun install(context: Context, apk: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(
            PackageInstaller.SessionParams.MODE_FULL_INSTALL
        )
        val sessionId = installer.createSession(params)
        try {
            installer.openSession(sessionId).use { session ->
                session.openWrite("apk", 0, apk.length()).use { output ->
                    apk.inputStream().use { it.copyTo(output) }
                    session.fsync(output)
                }
                val intent = Intent(INSTALL_ACTION).setPackage(context.packageName)
                val pending = PendingIntent.getBroadcast(
                    context, sessionId, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                )
                session.commit(pending.intentSender)
            }
        } catch (e: Exception) {
            // Closing the handle does not discard the session; without this, repeated
            // failures pile up against the 50-session-per-installer cap.
            runCatching { installer.abandonSession(sessionId) }
            throw e
        }
    }

    /**
     * Registers the receiver that forwards PackageInstaller's "needs user
     * confirmation" intent to the system installer UI. Returns the receiver so
     * the caller can unregister it.
     */
    fun registerInstallReceiver(context: Context, onFailure: (String) -> Unit): BroadcastReceiver {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -1)) {
                    PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                        val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                        confirm?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        confirm?.let { ctx.startActivity(it) }
                    }
                    PackageInstaller.STATUS_SUCCESS -> Unit
                    else -> onFailure(
                        intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                            ?: "Install failed"
                    )
                }
            }
        }
        ContextCompat.registerReceiver(
            context, receiver, IntentFilter(INSTALL_ACTION),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        return receiver
    }
}
