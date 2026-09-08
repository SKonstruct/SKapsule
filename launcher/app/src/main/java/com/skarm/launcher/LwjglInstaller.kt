package com.skarm.launcher

import android.content.Context
import android.util.Log
import androidx.core.content.pm.PackageInfoCompat
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

/**
 * Stages LWJGL 3.4.1 (AngelAuraMC Android build) onto internal storage.
 */
object LwjglInstaller {

    private const val TAG = "LwjglInstaller"
    private const val DIR_NAME = "lwjgl"
    private const val STAMP_NAME = ".version"
    private const val NATIVES_ASSET = "lwjgl/lwjgl-3.4.1-android-natives-arm64.zip"
    private const val MODULES_ASSET = "lwjgl/lwjgl-3.4.1-android-modules.zip"
    private const val VERSION = "3.4.1-aam-2026-05-21"

    fun homeDir(context: Context): File = File(context.filesDir, DIR_NAME)
    fun libDir(context: Context): File = File(homeDir(context), "lib")
    fun jarsDir(context: Context): File = File(homeDir(context), "jars")

    fun isInstalled(context: Context): Boolean {
        val stamp = File(homeDir(context), STAMP_NAME)
        if (!stamp.isFile) return false
        return stamp.readText().trim() == stampValue(context)
    }

    private fun stampValue(context: Context): String = "$VERSION+${appStamp(context)}"

    /**
     * The installed app's own version, folded into the stamp below.
     *
     * The component version alone is not enough: shipping a rebuilt runtime under an
     * unchanged version string would leave the previous unpack in place, so the game
     * keeps running last release's files. Any app update changes this.
     */
    private fun appStamp(context: Context): String = try {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        // PackageInfoCompat, not longVersionCode: that getter is API 28 and this module
        // ships to API 26, where the miss is a NoSuchMethodError -- an Error, which the
        // catch below would not stop.
        "${info.versionName}-${PackageInfoCompat.getLongVersionCode(info)}"
    } catch (e: Exception) {
        "unknown"
    }


    /** Returns a JVM-style classpath of all staged jars, colon-separated. */
    fun classpath(context: Context): String =
        jarsDir(context).listFiles { f -> f.isFile && f.name.endsWith(".jar") }
            ?.sortedBy { it.name }
            ?.joinToString(":") { it.absolutePath }
            ?: ""

    fun install(context: Context, onProgress: (String) -> Unit = {}) {
        val home = homeDir(context)
        if (home.exists()) home.deleteRecursively()
        libDir(context).mkdirs()
        jarsDir(context).mkdirs()

        onProgress("Unpacking LWJGL natives…")
        extractFlatZip(context, NATIVES_ASSET, libDir(context)) { name ->
            if (name.endsWith(".so")) name.substringAfterLast('/') else null
        }

        onProgress("Unpacking LWJGL modules…")
        extractFlatZip(context, MODULES_ASSET, jarsDir(context)) { name ->
            // Modules zip nests jars under per-module dirs...
            // Flatten and skip license txts.
            if (name.endsWith(".jar")) name.substringAfterLast('/') else null
        }

        File(home, STAMP_NAME).writeText(stampValue(context))
        onProgress("LWJGL ready.")
        Log.i(TAG, "LWJGL $VERSION staged at $home; jars=${jarsDir(context).list()?.size}, natives=${libDir(context).list()?.size}")
    }

    private fun extractFlatZip(
        context: Context,
        assetPath: String,
        into: File,
        nameFor: (String) -> String?,
    ) {
        context.assets.open(assetPath).use { raw ->
            ZipInputStream(raw.buffered()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (entry.isDirectory) {
                        zip.closeEntry()
                        continue
                    }
                    val outName = nameFor(entry.name)
                    if (outName == null) {
                        zip.closeEntry()
                        continue
                    }

                    val target = File(into, outName)
                    target.parentFile?.mkdirs()
                    FileOutputStream(target).use { out ->
                        val buf = ByteArray(64 * 1024)
                        while (true) {
                            val n = zip.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                        }
                    }
                    target.setReadable(true, false)
                    if (outName.endsWith(".so")) target.setExecutable(true, false)
                    zip.closeEntry()
                }
            }
        }
    }
}
