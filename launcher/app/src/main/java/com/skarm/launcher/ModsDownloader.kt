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

        // Delete local files not in remote
        onProgress("Checking local files to remove…", 0, 0)
        modsDir.listFiles()?.forEach { file ->
            if (file.isFile && !remoteModsMap.containsKey(file.name)) {
                Log.i(TAG, "Deleting removed mod: ${file.name}")
                if (file.delete()) {
                    stats.deleted++
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
            downloadFile(mod.downloadUrl, destFile)
            stats.downloaded++
        }

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
                    if (type == "file") {
                        mods.add(ModFile(name, downloadUrl, sha))
                    }
                }
                reader.endArray()
            }
            return mods
        } finally {
            conn.disconnect()
        }
    }

    private fun downloadFile(urlString: String, destFile: File) {
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
            conn.inputStream.use { input ->
                destFile.outputStream().use { output ->
                    input.copyTo(output)
                }
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
