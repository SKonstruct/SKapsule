package com.skarm.launcher

import android.content.Context
import android.util.Log
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipFile

/**
 * Bootstraps the Spiral Knights getdown layout under filesDir/sk/:
 */
object SkInstaller {

    private const val TAG = "SkInstaller"
    private const val DIR_NAME = "sk"
    private const val GETDOWN_PRO_ASSET = "sk/getdown-pro.jar"
    private const val SK_BOOTSTRAP_ASSET = "sk/sk-bootstrap.jar"
    private const val MANIFEST_URL = "http://gamemedia2.spiralknights.com/spiral/latest/getdown.txt"

    fun homeDir(context: Context): File = File(context.filesDir, DIR_NAME)
    fun codeDir(context: Context): File = File(homeDir(context), "code")
    fun tmpDir(context: Context): File = File(homeDir(context), "tmp")
    fun getdownJar(context: Context): File = File(codeDir(context), "getdown-pro.jar")
    fun bootstrapJar(context: Context): File = File(codeDir(context), "sk-bootstrap.jar")
    fun manifest(context: Context): File = File(homeDir(context), "getdown.txt")

    fun bootstrapClasspath(context: Context): String = listOf(
        bootstrapJar(context).absolutePath,
        getdownJar(context).absolutePath,
    ).joinToString(":")

    fun isBootstrapped(context: Context): Boolean =
        isUsableJar(getdownJar(context)) && isUsableJar(bootstrapJar(context)) && manifest(context).isFile

    /**
     * True if [jar] exists and opens as a non-empty zip. A copy killed halfway through still
     * passes isFile(), and the JVM then skips a jar it cannot open without saying so.
     */
    fun isUsableJar(jar: File): Boolean =
        jar.isFile && jar.length() > 0 &&
            runCatching { ZipFile(jar).use { it.size() > 0 } }.getOrDefault(false)

    /** The jars on [classpath] (colon-separated) that are missing or cannot be opened. */
    fun unusableJars(classpath: String): List<File> =
        classpath.split(":").filter { it.isNotEmpty() }.map(::File).filterNot(::isUsableJar)

    /**
     * Copies an asset through a temp file, so a kill mid-copy can never leave a truncated file
     * under the final name.
     */
    private fun stageAsset(context: Context, asset: String, dest: File) {
        val tmp = File(dest.path + ".part")
        context.assets.open(asset).use { input ->
            tmp.outputStream().use { out ->
                input.copyTo(out)
                out.fd.sync()
            }
        }
        if (!tmp.renameTo(dest)) {
            dest.delete()
            check(tmp.renameTo(dest)) { "could not stage ${dest.name}" }
        }
    }

    /**
     * Drops projectx-config.jar when it no longer yields its resources, so getdown fetches it
     * again on this launch.
     *
     * Getdown calls a resource valid when the file and its `.jarv` marker exist; it never
     * re-hashes. A jar our own mod path rewrote into something java.util.zip cannot read would
     * stay broken forever, and the game dies in its config code with the boot overlay still
     * showing getdown's last status. The entry checked is the first one SK's ConfigManager asks
     * for. Refetching replaces the jar wholesale, so locale mods baked into it need re-applying.
     */
    fun repairGameJars(context: Context) {
        val jar = File(codeDir(context), "projectx-config.jar")
        if (!jar.isFile) return
        val ok = runCatching {
            ZipFile(jar).use { it.getEntry("rsrc/config/manager.properties") != null }
        }.getOrDefault(false)
        if (ok) return
        Log.w(TAG, "projectx-config.jar yields no resources; refetching (mods in it need re-applying)")
        dropGetdownResource(jar)
    }

    /**
     * Makes getdown unpack the rsrc bundles again when the unpacked tree has gone missing.
     *
     * They are "uresource" entries: getdown unpacks them once, writes a `.jarv` marker and never
     * looks again, while mod apply/remove purges and rebuilds the tree by hand, so an interrupted
     * run leaves jars and markers with nothing extracted. Only the markers are deleted: the jars
     * still match the digest, so nothing is downloaded again, but the unpack re-runs.
     */
    fun repairUnpackedResources(context: Context) {
        val rsrc = File(homeDir(context), "rsrc")
        // A missing canary alone is not enough: if the game ever moves it, every launch would
        // clear the markers and re-unpack over the user's mods. Any unpacked UI tree counts.
        if (File(rsrc, "ui/hud/default/chatoverlay.dat").exists()) return
        if (File(rsrc, "ui").list()?.isNotEmpty() == true) return
        var dropped = 0
        rsrc.listFiles { f -> f.name.endsWith(".jarv") }?.forEach { if (it.delete()) dropped++ }
        // crucible.jar is a uresource too, beside rsrc rather than inside it.
        if (File(homeDir(context), "crucible.jarv").delete()) dropped++
        if (dropped > 0) Log.w(TAG, "rsrc bundles are not unpacked; cleared $dropped marker(s) to force it")
    }

    /** Removes a getdown resource and the marker that makes getdown skip validating it. */
    private fun dropGetdownResource(file: File) {
        file.delete()
        File(file.path + "v").delete()
    }

    fun stageGetdownJar(context: Context) {
        codeDir(context).mkdirs()
        stageAsset(context, GETDOWN_PRO_ASSET, getdownJar(context))
        Log.i(TAG, "Staged getdown-pro.jar (${getdownJar(context).length()} bytes)")
    }

    /** Re-copies sk-bootstrap.jar from the APK; cheap, and an app update must replace it. */
    fun stageBootstrapJar(context: Context) {
        codeDir(context).mkdirs()
        stageAsset(context, SK_BOOTSTRAP_ASSET, bootstrapJar(context))
        Log.i(TAG, "Staged sk-bootstrap.jar (${bootstrapJar(context).length()} bytes)")
    }

    fun bootstrap(context: Context, onProgress: (String) -> Unit = {}) {
        codeDir(context).mkdirs()
        tmpDir(context).mkdirs() // java.io.tmpdir target for the FCL JVM

        if (!isUsableJar(getdownJar(context))) {
            onProgress("Staging getdown-pro.jar…")
            stageGetdownJar(context)
        }

        onProgress("Staging sk-bootstrap.jar…")
        stageBootstrapJar(context)

        if (!manifest(context).isFile) {
            onProgress("Fetching SK manifest…")
            downloadManifest(context)
        }
    }

    private fun downloadManifest(context: Context) {
        val url = URL(MANIFEST_URL)
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = 15_000
        conn.readTimeout = 30_000
        conn.instanceFollowRedirects = true
        try {
            conn.connect()
            if (conn.responseCode !in 200..299) {
                error("manifest fetch failed: HTTP ${conn.responseCode}")
            }
            conn.inputStream.use { input ->
                manifest(context).outputStream().use { out -> input.copyTo(out) }
            }
            Log.i(TAG, "Fetched getdown.txt (${manifest(context).length()} bytes) from $MANIFEST_URL")
        } finally {
            conn.disconnect()
        }
    }
}
