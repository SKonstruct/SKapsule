package com.skarm.launcher

import android.util.Log
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

object ModsDownloader {
    private const val TAG = "ModsDownloader"
    private const val GITHUB_API_URL = "https://api.github.com/repos/SirDank/Spiral-Knights-Modpack/contents/mods"

    /** Names this app downloaded last time, so a sync removes only what it put there itself. */
    private const val SYNCED_MANIFEST = ".modpack-synced"

    /** A mod's file name: one path component, no traversal. The listing is remote data. */
    internal fun isSafeModName(name: String): Boolean =
        name.isNotEmpty() && name != "." && name != ".." && '/' !in name && '\\' !in name && '\u0000' !in name

    /** The contents API serves files from raw.githubusercontent.com; anything else is not believed. */
    internal fun isTrustedDownloadUrl(url: String): Boolean {
        val uri = try { java.net.URI(url) } catch (e: Exception) { return false }
        return uri.scheme == "https" && uri.host == "raw.githubusercontent.com"
    }

    data class SyncStats(
        var downloaded: Int = 0,
        var skipped: Int = 0,
        var deleted: Int = 0,
    )

    data class ModFile(
        val name: String,
        val downloadUrl: String,
        val sha: String,
    )

    suspend fun sync(
        modsDir: File,
        onProgress: (status: String, current: Int, total: Int) -> Unit,
    ): SyncStats = withContext(Dispatchers.IO) {
        val stats = SyncStats()

        if (!modsDir.exists() && !modsDir.mkdirs()) {
            throw IOException("Failed to create mods directory: ${modsDir.absolutePath}")
        }

        onProgress("Fetching mods list from GitHub…", 0, 0)
        val remoteMods = fetchRemoteModsList()
        if (remoteMods.isEmpty()) {
            throw IOException("No mods found in the remote repository.")
        }

        val remoteModsMap = remoteMods.associateBy { it.name }

        // Remove mods the modpack has dropped, but only ones this app downloaded earlier: the player
        // keeps their own mods in this folder too (Open Folder invites it).
        onProgress("Checking local files to remove…", 0, 0)
        val manifest = File(modsDir, SYNCED_MANIFEST)
        val previouslySynced = runCatching { manifest.readLines().filter { it.isNotBlank() }.toSet() }
            .getOrDefault(emptySet())
        previouslySynced.forEach { name ->
            if (isSafeModName(name) && !remoteModsMap.containsKey(name)) {
                val file = File(modsDir, name)
                if (file.isFile) {
                    Log.i(TAG, "Deleting removed mod: $name")
                    if (file.delete()) stats.deleted++
                }
            }
        }

        val total = remoteMods.size
        remoteMods.forEachIndexed { index, mod ->
            val current = index + 1
            val destFile = File(modsDir, mod.name)

            if (destFile.exists() && mod.sha.isNotEmpty()) {
                onProgress("Checking: ${mod.name}", current, total)
                try {
                    val localSha = calculateGitBlobSha(destFile)
                    if (localSha == mod.sha) {
                        Log.i(TAG, "Skipping unchanged: ${mod.name}")
                        stats.skipped++
                        return@forEachIndexed
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to calculate SHA for ${mod.name}, will re-download", e)
                }
            }

            onProgress("[$current/$total] ${mod.name}", current, total)
            downloadFile(mod.downloadUrl, destFile, mod.sha)
            stats.downloaded++
        }

        runCatching { manifest.writeText(remoteMods.joinToString("\n") { it.name }) }
            .onFailure { Log.w(TAG, "could not record the synced mods", it) }
        stats
    }

    private fun fetchRemoteModsList(): List<ModFile> {
        val url = URL(GITHUB_API_URL)
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 10_000
        conn.setRequestProperty("User-Agent", "SKapsule-Launcher")

        try {
            conn.connect()
            if (conn.responseCode != HttpURLConnection.HTTP_OK) {
                throw IOException("GitHub API returned HTTP ${conn.responseCode}")
            }
            val mods = mutableListOf<ModFile>()
            JsonReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).use { reader ->
                reader.beginArray()
                while (reader.hasNext()) {
                    reader.beginObject()
                    var type = ""
                    var name = ""
                    var downloadUrl = ""
                    var sha = ""
                    while (reader.hasNext()) {
                        val key = reader.nextName()
                        if (reader.peek() == JsonToken.NULL) {
                            reader.nextNull()
                            continue
                        }
                        when (key) {
                            "type" -> type = reader.nextString()
                            "name" -> name = reader.nextString()
                            "download_url" -> downloadUrl = reader.nextString()
                            "sha" -> sha = reader.nextString()
                            else -> reader.skipValue()
                        }
                    }
                    reader.endObject()
                    if (type != "file") continue
                    if (!isSafeModName(name) || !isTrustedDownloadUrl(downloadUrl)) {
                        Log.w(TAG, "Ignoring listing entry with an unexpected name or URL: $name")
                        continue
                    }
                    mods.add(ModFile(name, downloadUrl, sha))
                }
                reader.endArray()
            }
            return mods
        } finally {
            conn.disconnect()
        }
    }

    private fun downloadFile(urlString: String, destFile: File, expectedSha: String) {
        val url = URL(urlString)
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = 15_000
        conn.readTimeout = 15_000

        try {
            conn.connect()
            if (conn.responseCode != HttpURLConnection.HTTP_OK) {
                throw IOException("Server returned HTTP ${conn.responseCode} for $urlString")
            }
            destFile.parentFile?.mkdirs()
            // Write to a temp file and rename, so an interrupted download can't leave a
            // truncated jar in mods/ for the game to load.
            val tmp = File(destFile.absolutePath + ".part")
            conn.inputStream.use { input ->
                tmp.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            // The listing carries each file's git blob hash: a captive portal's page or a truncated
            // transfer must not become a mod.
            if (expectedSha.isNotEmpty() && calculateGitBlobSha(tmp) != expectedSha) {
                tmp.delete()
                throw IOException("${destFile.name} did not match its published hash")
            }
            destFile.delete()
            if (!tmp.renameTo(destFile)) {
                tmp.delete()
                throw IOException("Failed to move downloaded file into ${destFile.name}")
            }
        } finally {
            conn.disconnect()
        }
    }

    private fun calculateGitBlobSha(file: File): String {
        val digest = MessageDigest.getInstance("SHA-1")
        val size = file.length()
        val header = "blob $size\u0000"
        digest.update(header.toByteArray(Charsets.UTF_8))
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            var read: Int
            while (input.read(buffer).also { read = it } >= 0) {
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
