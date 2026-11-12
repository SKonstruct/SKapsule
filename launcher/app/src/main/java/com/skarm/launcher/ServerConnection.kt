package com.skarm.launcher

import android.util.Log
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.Properties
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * Points the stock client at a server by rewriting the connection keys in the
 * `deployment.properties` inside `code/config.jar`, the same keys KnightLauncher writes.
 *
 * Every launch writes the full set (Official's values when no server is selected), so a
 * previous server's settings never linger and a game update that restores the jar is healed.
 * Only that one entry changes, so class and locale mods merged into the jar are untouched.
 */
object ServerConnection {
    private const val TAG = "ServerConnection"
    private const val ENTRY = "deployment.properties"

    const val OFFICIAL_HOST = "game.spiralknights.com"
    const val OFFICIAL_PORT = 47624
    const val OFFICIAL_CLIENT_URL = "http://gamemedia2.spiralknights.com"
    const val OFFICIAL_CONTENT_URL = "http://content.spiralknights.com/"
    const val OFFICIAL_KEY =
        "a5ed0dc3892b9472cfb668e236064e989e95945dad18f3d7e7d8e474d6e03de38bc044c3429b9ca649d0881d601c" +
            "0eb8ffebc3756f0503f73a8ca1760943ea0e8921ad6f8102026586db3133844bbadbcfcfc666d23982d7684511fb" +
            "f6cd8bb1d02a14270d0854098d16fe88f99c05825b0fe1b6fd497709106f2c418796aaf7aab7c92f26fcd9fbb3c4" +
            "3df48075fed8dd931273a7b0a333c8de5967797874c1944aed65b47f0792b273a529ac22a2dce08dad04eeebeeff" +
            "67c7bc99b97682bff488038b28e24f4b5eea77ed966caede52f2c1ecf2b403110a9765daa81ddf718129a040823b" +
            "ead3a0bdca70ef6d08f483757a6d3b6e01fbbcb32006b7872bcd#10001"

    /** The connection keys for [server], or for Official when null; blank fields fall back to Official's. */
    internal fun desired(server: ServerStore.Server?): Map<String, String> {
        val port = (server?.port ?: OFFICIAL_PORT).toString()
        return linkedMapOf(
            "server_host" to (server?.host ?: OFFICIAL_HOST),
            "server_ports" to port,
            "datagram_ports" to port,
            "key.public" to server?.publicKey?.ifEmpty { null }.orDefault(OFFICIAL_KEY),
            "client_root_url" to server?.clientUrl?.ifEmpty { null }
                ?.let { ServerStore.toClientRoot(it) }.orDefault(OFFICIAL_CLIENT_URL),
            "content_url" to server?.contentUrl?.ifEmpty { null }.orDefault(OFFICIAL_CONTENT_URL),
        )
    }

    private fun String?.orDefault(default: String) = this ?: default

    private const val MANIFEST_URL = "https://gamemedia2.spiralknights.com/spiral/latest/getdown.txt"

    /** The `version = ` line of a getdown manifest, or null. */
    internal fun manifestVersion(text: String?): String? =
        text?.lineSequence()?.map { it.trim() }
            ?.firstOrNull { it.startsWith("version") && it.removePrefix("version").trimStart().startsWith("=") }
            ?.substringAfter("=")?.trim()?.ifEmpty { null }

    /**
     * Whether Official has a newer game than the installed one. The game's updater would then replace
     * config.jar after we edit it, and the game would silently connect to Official. An unreachable
     * or unreadable manifest counts as no update, so an offline private server still launches.
     */
    fun updatePending(gameHome: File): Boolean {
        val local = manifestVersion(File(gameHome, "getdown.txt").takeIf { it.isFile }?.readText())
        val remote = try {
            val conn = URL(MANIFEST_URL).openConnection() as HttpURLConnection
            conn.connectTimeout = 8_000
            conn.readTimeout = 8_000
            try {
                if (conn.responseCode != 200) null
                else manifestVersion(conn.inputStream.use { it.readNBytesCapped(64 * 1024) })
            } finally {
                conn.disconnect()
            }
        } catch (e: IOException) {
            null
        }
        return local != null && remote != null && local != remote
    }

    // InputStream.readNBytes is API 33; this app ships to 26.
    private fun java.io.InputStream.readNBytesCapped(max: Int): String {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(4096)
        while (out.size() < max) {
            val n = read(buf)
            if (n < 0) break
            out.write(buf, 0, n)
        }
        return out.toString(Charsets.UTF_8.name())
    }

    /**
     * Writes [server]'s settings (Official's when null) into the game's config.jar.
     *
     * @return false when the game isn't installed yet, so there is no config.jar to point.
     * @throws IOException if the jar can't be read or replaced; the original is left as it was.
     */
    fun apply(gameHome: File, server: ServerStore.Server?): Boolean {
        val jar = File(gameHome, "code/config.jar")
        if (!jar.isFile) return false
        return patch(jar, desired(server), gameHome)
    }

    internal fun patch(jar: File, wanted: Map<String, String>, gameHome: File? = null): Boolean {
        val current = ZipFile(jar).use { zip ->
            val entry = zip.getEntry(ENTRY) ?: throw IOException("config.jar has no $ENTRY")
            Properties().also { p -> zip.getInputStream(entry).use { p.load(it) } }
        }
        if (wanted.all { (k, v) -> current.getProperty(k) == v }) return true

        // The first edit is the one that has to be undoable offline: keep the game's own jar.
        gameHome?.let { ModsApplier.preservePristine(it, jar.name) }

        wanted.forEach { (k, v) -> current.setProperty(k, v) }
        val tmp = File(jar.parentFile, "${jar.name}.server")
        try {
            ZipFile(jar).use { zip ->
                ZipOutputStream(tmp.outputStream().buffered()).use { out ->
                    for (entry in zip.entries()) {
                        val copy = ZipEntry(entry.name).apply { time = entry.time }
                        out.putNextEntry(copy)
                        if (entry.name == ENTRY) {
                            current.store(out, null)
                        } else if (!entry.isDirectory) {
                            zip.getInputStream(entry).use { it.copyTo(out) }
                        }
                        out.closeEntry()
                    }
                }
            }
            if (!tmp.renameTo(jar)) throw IOException("Couldn't replace ${jar.name}")
        } finally {
            tmp.delete()
        }
        Log.i(TAG, "Pointed ${jar.name} at ${wanted["server_host"]}:${wanted["server_ports"]}")
        return true
    }
}
