package com.skarm.launcher

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.net.URL
import java.net.HttpURLConnection
import java.util.Properties
import java.util.jar.JarEntry
import java.util.jar.JarInputStream
import java.util.jar.JarOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream

object ModsApplier {
    private const val TAG = "ModsApplier"

    // Paths are matched case-insensitively against archive entries, as KnightLauncher does.
    // Kept in step with KnightLauncher's ModManager.FILTER_LIST: configs that very often
    // collide with game updates, so a mod is not allowed to replace them.
    private val PROTECTED_FILES = setOf(
        "config/accessory.dat", "config/accessory.xml",
        "config/actor.dat", "config/actor.xml",
        "config/area.dat", "config/area.xml",
        "config/attack.dat", "config/attack.xml",
        "config/battle_sprite.dat", "config/battle_sprite.xml",
        "config/catalog.dat", "config/catalog.xml",
        "config/conversation.dat", "config/conversation.xml",
        "config/cursor.dat", "config/cursor.xml",
        "config/depot_catalog.dat", "config/depot_catalog.xml",
        "config/depth_scale.dat", "config/depth_scale.xml",
        "config/description.dat", "config/description.xml",
        "config/effect.dat", "config/effect.xml",
        "config/emote.dat", "config/emote.xml",
        "config/event.dat", "config/event.xml",
        "config/fire_action.dat", "config/fire_action.xml",
        "config/font.dat", "config/font.xml",
        "config/forge_property.dat", "config/forge_property.xml",
        "config/gift.dat", "config/gift.xml",
        "config/ground.dat", "config/ground.xml",
        "config/harness.dat", "config/harness.xml",
        "config/interact.dat", "config/interact.xml",
        "config/interface_script.dat", "config/interface_script.xml",
        "config/item.dat", "config/item.xml",
        "config/item_depth_weight.dat", "config/item_depth_weight.xml",
        "config/item_property.dat", "config/item_property.xml",
        "config/level_table.dat", "config/level_table.xml",
        "config/material.dat", "config/material.xml",
        "config/mission.dat", "config/mission.xml",
        "config/mission_group.dat", "config/mission_group.xml",
        "config/mission_property.dat", "config/mission_property.xml",
        "config/parameterized_handler.dat", "config/parameterized_handler.xml",
        "config/path.dat", "config/path.xml",
        "config/placeable.dat", "config/placeable.xml",
        "config/recipe.dat", "config/recipe.xml",
        "config/recipe_property.dat", "config/recipe_property.xml",
        "config/render_effect.dat", "config/render_effect.xml",
        "config/render_queue.dat", "config/render_queue.xml",
        "config/render_scheme.dat", "config/render_scheme.xml",
        "config/scene_global.dat", "config/scene_global.xml",
        "config/shader.dat", "config/shader.xml",
        "config/sounder.dat", "config/sounder.xml",
        "config/status_condition.dat", "config/status_condition.xml",
        "config/style.dat", "config/style.xml",
        "config/texture.dat", "config/texture.xml",
        "config/tile.dat", "config/tile.xml",
        "config/tile_replacement.dat", "config/tile_replacement.xml",
        "config/uplink.dat", "config/uplink.xml",
        "config/variant.dat", "config/variant.xml",
        "config/wall.dat", "config/wall.xml",
    )

    data class ApplyStats(
        var jarsUnpacked: Int = 0,
        var resourceModsApplied: Int = 0,
        var classModsApplied: Int = 0,
        var modpacksApplied: Int = 0,
        var modsSkipped: Int = 0,
        var localeChangesApplied: Int = 0,
        val warnings: MutableList<String> = mutableListOf(),
    ) {
        fun getTotalModsApplied(): Int = resourceModsApplied + classModsApplied + modpacksApplied
    }

    data class ModMetadata(
        var name: String,
        var type: String? = null,
        var pxVersion: String? = null,
        var locale: Map<String, Map<String, String>>? = null,
    ) {
        fun isClassMod(): Boolean = "class".equals(type, ignoreCase = true)
        fun hasLocaleChanges(): Boolean = !locale.isNullOrEmpty()
    }

    suspend fun apply(
        gameHome: File,
        onProgress: (status: String, current: Int, total: Int) -> Unit,
    ): ApplyStats = withContext(Dispatchers.IO) {
        val stats = ApplyStats()
        val rsrcDir = File(gameHome, "rsrc")
        if (!rsrcDir.exists()) {
            throw IOException("Game not installed. Please launch the game first to download files.")
        }

        val modsDir = File(gameHome, "mods")
        val codeDir = File(gameHome, "code")
        val classChangesDir = File(codeDir, "class-changes")
        val localeChangesDir = File(codeDir, "locale-changes")

        val modFiles = modsDir.listFiles { _, name ->
            name.endsWith(".zip") || name.endsWith(".modpack")
        }?.toList() ?: emptyList()

        if (modFiles.isEmpty()) {
            throw IOException("No mods found in the mods folder. Download mods first.")
        }

        val gameVersion = getGameVersion(gameHome)
        Log.i(TAG, "Current game version: $gameVersion")

        // Start the code jars from the game's own copies, as KnightLauncher does on every
        // mount: merging into last run's output keeps changes from mods since removed. First,
        // so a failure (offline, with modded jars) leaves the install as it was.
        onProgress("Restoring original code jars…", 0, 0)
        restorePristineCodeJars(gameHome)

        // Step 1: Rebuild resources (unpack jar bundles)
        purgeDirectory(rsrcDir)
        stats.jarsUnpacked = rebuildResources(rsrcDir, onProgress)

        // Step 2: Mount mods based on their type
        if (classChangesDir.exists()) deleteDirectory(classChangesDir)
        classChangesDir.mkdirs()

        val globalLocaleChanges = mutableMapOf<String, MutableMap<String, String>>()
        var hasClassMods = false
        val total = modFiles.size

        modFiles.forEachIndexed { index, modFile ->
            val current = index + 1
            val metadata = parseModMetadata(modFile)
            val isModpack = modFile.name.endsWith(".modpack")

            val typeLabel = when {
                isModpack -> " [modpack]"
                metadata.isClassMod() -> " [class]"
                else -> " [resource]"
            }

            onProgress("Processing: ${metadata.name}$typeLabel", current, total)
            try {
                if (isModpack) {
                    val tempDir = File(modsDir, ".modpack_temp")
                    if (tempDir.exists()) deleteDirectory(tempDir)
                    tempDir.mkdirs()
                    try {
                        extractZipToDirectory(modFile, tempDir, false)
                        val innerMods = tempDir.listFiles { _, name -> name.endsWith(".zip") }
                        var applied = 0
                        innerMods?.forEach { innerMod ->
                            val innerMeta = parseModMetadata(innerMod)
                            if (innerMeta.isClassMod()) {
                                if (isClassModCompatible(innerMeta, gameVersion)) {
                                    extractZipToDirectory(innerMod, classChangesDir, true)
                                    stats.classModsApplied++
                                    hasClassMods = true
                                    applied++
                                } else {
                                    stats.modsSkipped++
                                    stats.warnings.add("Skipped incompatible class mod: ${innerMeta.name}")
                                }
                            } else {
                                extractZipToDirectory(innerMod, rsrcDir, true)
                                stats.resourceModsApplied++
                                applied++
                            }

                            innerMeta.locale?.forEach { (bundle, changes) ->
                                globalLocaleChanges.getOrPut(bundle) { mutableMapOf() }.putAll(changes)
                                stats.localeChangesApplied += changes.size
                            }
                        }
                        if (applied > 0) {
                            stats.modpacksApplied++
                        }
                    } finally {
                        deleteDirectory(tempDir)
                    }
                } else if (metadata.isClassMod()) {
                    if (isClassModCompatible(metadata, gameVersion)) {
                        extractZipToDirectory(modFile, classChangesDir, true)
                        stats.classModsApplied++
                        hasClassMods = true
                    } else {
                        stats.modsSkipped++
                        val warning = "Class mod '${metadata.name}' requires game version ${metadata.pxVersion} but current is $gameVersion - SKIPPED"
                        stats.warnings.add(warning)
                    }
                } else {
                    extractZipToDirectory(modFile, rsrcDir, true)
                    stats.resourceModsApplied++
                }

                metadata.locale?.forEach { (bundle, changes) ->
                    globalLocaleChanges.getOrPut(bundle) { mutableMapOf() }.putAll(changes)
                    stats.localeChangesApplied += changes.size
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to apply mod: ${modFile.name}", e)
                stats.warnings.add("Failed to apply ${metadata.name}: ${e.message}")
            }
        }

        // Step 3: Merge class changes into config.jar
        if (hasClassMods) {
            onProgress("Merging class changes into config.jar…", 0, 0)
            mergeClassChanges(codeDir, classChangesDir)
        }

        // Step 4: Apply locale changes to projectx-config.jar
        if (globalLocaleChanges.isNotEmpty()) {
            onProgress("Applying locale changes…", 0, 0)
            applyLocaleChanges(codeDir, localeChangesDir, globalLocaleChanges)
        }

        // Clean up
        deleteDirectory(classChangesDir)
        File(rsrcDir, "mod.json").delete()
        File(rsrcDir, "mod.png").delete()

        stats
    }

    private fun getGameVersion(gameHome: File): String? {
        val getdownFile = File(gameHome, "getdown.txt")
        if (!getdownFile.exists()) return null
        return try {
            getdownFile.useLines { lines ->
                lines.find { it.startsWith("version = ") }
                    ?.substring("version = ".length)?.trim()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read game version from getdown.txt", e)
            null
        }
    }

    /**
     * Drops a comma that directly precedes a closing brace or bracket, outside strings. Mod
     * authors write `"version": "1.0",` before the final `}` and the desktop launcher's JSON
     * library accepts it; Android's rejects it, which lost those mods' metadata.
     */
    internal fun stripTrailingCommas(json: String): String {
        val out = StringBuilder(json.length)
        var inString = false
        var escaped = false
        var i = 0
        while (i < json.length) {
            val c = json[i]
            when {
                inString -> {
                    out.append(c)
                    if (escaped) escaped = false else if (c == '\\') escaped = true else if (c == '"') inString = false
                }
                c == '"' -> { inString = true; out.append(c) }
                c == ',' -> {
                    var j = i + 1
                    while (j < json.length && json[j].isWhitespace()) j++
                    if (j >= json.length || (json[j] != '}' && json[j] != ']')) out.append(c)
                }
                else -> out.append(c)
            }
            i++
        }
        return out.toString()
    }

    private fun parseModMetadata(modFile: File): ModMetadata {
        val metadata = ModMetadata(name = modFile.name)
        try {
            ZipFile(modFile).use { zipFile ->
                val entry = zipFile.getEntry("mod.json") ?: zipFile.entries().asSequence().find { it.name.endsWith("/mod.json") }
                if (entry != null) {
                    zipFile.getInputStream(entry).use { inputStream ->
                        val content = inputStream.bufferedReader().readText()
                        val json = JSONObject(stripTrailingCommas(content))
                        val modObj = json.optJSONObject("mod") ?: json

                        metadata.name = modObj.optString("name", modFile.name)
                        metadata.type = modObj.optString("type", null)
                        metadata.pxVersion = modObj.optString("pxVersion", null)

                        modObj.optJSONObject("locale")?.let { localeObj ->
                            val localeMap = mutableMapOf<String, Map<String, String>>()
                            localeObj.keys().forEach { bundle ->
                                val bundleObj = localeObj.getJSONObject(bundle)
                                val changesMap = mutableMapOf<String, String>()
                                bundleObj.keys().forEach { key ->
                                    changesMap[key] = bundleObj.getString(key)
                                }
                                localeMap[bundle] = changesMap
                            }
                            metadata.locale = localeMap
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse mod metadata from ${modFile.name}", e)
        }
        return metadata
    }

    private fun isClassModCompatible(metadata: ModMetadata, gameVersion: String?): Boolean {
        if (metadata.pxVersion.isNullOrEmpty() || gameVersion.isNullOrEmpty()) return true
        return metadata.pxVersion.equals(gameVersion, ignoreCase = true)
    }

    private fun rebuildResources(rsrcDir: File, onProgress: (String, Int, Int) -> Unit): Int {
        val jarFiles = rsrcDir.listFiles { _, name -> name.endsWith(".jar") } ?: return 0
        val total = jarFiles.size
        var unpacked = 0
        jarFiles.forEachIndexed { index, jarFile ->
            val current = index + 1
            onProgress("Extracting resource [$current/$total]: ${jarFile.name}", current, total)
            Log.i(TAG, "Unpacking jar bundle: ${jarFile.name}")
            try {
                extractZipToDirectory(jarFile, rsrcDir, false)
                unpacked++
            } catch (e: IOException) {
                Log.e(TAG, "Failed to unpack jar: ${jarFile.name}", e)
            }
        }
        return unpacked
    }

    suspend fun remove(
        gameHome: File,
        onProgress: (status: String, current: Int, total: Int) -> Unit,
    ): Int = withContext(Dispatchers.IO) {
        val rsrcDir = File(gameHome, "rsrc")
        if (!rsrcDir.exists()) {
            throw IOException("Game not installed. Please launch the game first.")
        }
        // Class and locale mods live in the code jars, not rsrc/.
        onProgress("Restoring original code jars…", 0, 0)
        restorePristineCodeJars(gameHome)
        purgeDirectory(rsrcDir)
        rebuildResources(rsrcDir, onProgress)
    }

    private val CODE_JARS = listOf("config.jar", "projectx-config.jar")
    private const val MAX_CODE_JAR_BYTES = 64L * 1024 * 1024

    /**
     * Puts the game's own `code/config.jar` and `code/projectx-config.jar` back, which is
     * where class and locale mods are merged. "The game's own" means matching the install's
     * getdown `digest2.txt`; a jar that does not is re-fetched for the installed version and
     * only swapped in once it matches too, so the cleartext-era host cannot hand us anything
     * else. Untouched jars need no network.
     */
    private fun restorePristineCodeJars(gameHome: File) {
        val digests = readGetdown(File(gameHome, "digest2.txt"))
        val getdown = readGetdown(File(gameHome, "getdown.txt"))
        for (name in CODE_JARS) {
            val path = "code/$name"
            val expected = digests[path] ?: throw IOException("No digest for $path; launch the game once, then try again.")
            val jar = File(gameHome, path)
            val pristine = File(jar.parentFile, "$name.pristine")
            // An unreadable jar (say, a merge interrupted mid-write) is simply not pristine.
            fun matches(f: File) = f.exists() && runCatching { getdownDigest(f) }.getOrNull() == expected
            // Keep a verified copy before mods touch the jar, so later applies and removes
            // can restore it offline; the download below is only the fallback.
            if (matches(jar)) {
                if (!matches(pristine)) jar.copyTo(pristine, overwrite = true)
                continue
            }
            if (matches(pristine)) {
                pristine.copyTo(jar, overwrite = true)
                continue
            }

            val version = getdown["version"]
            val appbase = getdown["appbase"]
            if (version == null || appbase == null) throw IOException("Can't find the game server to restore $path.")
            val url = appbase.replace("%VERSION%", version).replaceFirst("http://", "https://") + "/" + path
            val tmp = File(jar.parentFile, "$name.download")
            try {
                download(url, tmp)
                if (runCatching { getdownDigest(tmp) }.getOrNull() != expected) {
                    throw IOException("The original $path failed verification.")
                }
                tmp.copyTo(pristine, overwrite = true)
                if (!tmp.renameTo(jar)) throw IOException("Couldn't replace $path.")
                Log.i(TAG, "Restored original $path")
            } finally {
                tmp.delete()
            }
        }
    }

    /** `key = value` lines, as getdown writes getdown.txt and digest2.txt. */
    private fun readGetdown(file: File): Map<String, String> =
        if (!file.exists()) emptyMap() else file.readLines().mapNotNull { line ->
            val i = line.indexOf(" = ")
            if (i <= 0 || line.startsWith("#")) null else line.substring(0, i).trim() to line.substring(i + 3).trim()
        }.toMap()

    /**
     * Getdown's version-2 jar digest: SHA-256 over every entry's contents in name order,
     * so it identifies the contents regardless of how the archive was written.
     */
    internal fun getdownDigest(jar: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(8192)
        ZipFile(jar).use { zip ->
            for (entry in zip.entries().toList().sortedBy { it.name }) {
                zip.getInputStream(entry).use { input ->
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        md.update(buffer, 0, n)
                    }
                }
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    private fun download(url: String, dest: File) {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 15_000
        conn.readTimeout = 30_000
        try {
            if (conn.responseCode != 200) throw IOException("HTTP ${conn.responseCode} fetching $url")
            // Verified against the digest afterwards, but bound it so a bad response cannot
            // fill storage first. The real jars are a few MB.
            conn.inputStream.use { input ->
                dest.outputStream().use { out ->
                    val buffer = ByteArray(16 * 1024)
                    var total = 0L
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        total += n
                        if (total > MAX_CODE_JAR_BYTES) throw IOException("Unexpectedly large download from $url")
                        out.write(buffer, 0, n)
                    }
                }
            }
        } finally {
            conn.disconnect()
        }
    }

    private fun purgeDirectory(dir: File) {
        dir.listFiles()?.forEach { file ->
            if (file.name.endsWith(".jar") || file.name.endsWith(".zip") || file.name.endsWith(".jarv")) {
                return@forEach
            }
            if (file.isDirectory) {
                deleteDirectory(file)
            } else {
                file.delete()
            }
        }
    }

    private fun extractZipToDirectory(zipFile: File, targetDir: File, checkProtected: Boolean) {
        val buffer = ByteArray(8192)
        ZipInputStream(FileInputStream(zipFile)).use { zis ->
            var entry: ZipEntry?
            while (zis.nextEntry.also { entry = it } != null) {
                val e = entry!!
                if (e.isDirectory) {
                    zis.closeEntry()
                    continue
                }

                val entryName = e.name
                if (entryName == "mod.json" || entryName.endsWith("/mod.json") ||
                    entryName == "mod.png" || entryName.endsWith("/mod.png")
                ) {
                    zis.closeEntry()
                    continue
                }

                val key = entryName.lowercase()
                if (checkProtected && key in PROTECTED_FILES) {
                    Log.w(TAG, "Skipping protected file: $entryName")
                    zis.closeEntry()
                    continue
                }

                val destFile = safeDestination(targetDir, entryName) ?: continue
                destFile.parentFile?.mkdirs()
                FileOutputStream(destFile).use { fos ->
                    var len: Int
                    while (zis.read(buffer).also { len = it } > 0) {
                        fos.write(buffer, 0, len)
                    }
                }
                zis.closeEntry()
            }
        }
    }

    private fun mergeClassChanges(codeDir: File, classChangesDir: File) {
        val configJar = File(codeDir, "config.jar")
        if (!configJar.exists()) {
            Log.w(TAG, "config.jar not found, skipping class merge")
            return
        }

        // Unpack config.jar into classChangesDir WITHOUT overwriting files already there (the mods take precedence)
        extractJarWithoutOverwrite(configJar, classChangesDir)

        val configNewJar = File(codeDir, "config-new.jar")
        val configOldJar = File(codeDir, "config-old.jar")

        createJarFromDirectory(classChangesDir, configNewJar)

        if (configOldJar.exists()) configOldJar.delete()
        if (configJar.renameTo(configOldJar)) {
            if (configNewJar.renameTo(configJar)) {
                configOldJar.delete()
                Log.i(TAG, "Successfully merged class changes into config.jar")
            } else {
                configOldJar.renameTo(configJar)
                throw IOException("Failed to rename config-new.jar to config.jar")
            }
        } else {
            throw IOException("Failed to rename config.jar to config-old.jar")
        }
    }

    /**
     * Resolves an archive entry against the extraction root, or null if it escapes it.
     *
     * Entry names are attacker-controlled — mod archives come from a third-party GitHub
     * repo — and nothing in the zip format stops one being "../../../shared_prefs/x.xml".
     */
    internal fun safeDestination(targetDir: File, entryName: String): File? {
        val dest = File(targetDir, entryName)
        val rootPath = targetDir.canonicalPath
        val destPath = dest.canonicalPath
        // Compare against root + separator so a sibling sharing the prefix can't pass.
        if (destPath != rootPath && !destPath.startsWith(rootPath + File.separator)) {
            Log.w(TAG, "Rejected entry escaping the extraction root: $entryName")
            return null
        }
        return dest
    }

    private fun extractJarWithoutOverwrite(jarFile: File, targetDir: File) {
        val buffer = ByteArray(8192)
        JarInputStream(FileInputStream(jarFile)).use { jis ->
            var entry: JarEntry?
            while (jis.nextJarEntry.also { entry = it } != null) {
                val e = entry!!
                if (e.isDirectory) {
                    (safeDestination(targetDir, e.name) ?: continue).mkdirs()
                    continue
                }
                val destFile = safeDestination(targetDir, e.name) ?: continue
                if (!destFile.exists()) {
                    destFile.parentFile?.mkdirs()
                    FileOutputStream(destFile).use { fos ->
                        var len: Int
                        while (jis.read(buffer).also { len = it } > 0) {
                            fos.write(buffer, 0, len)
                        }
                    }
                }
                jis.closeEntry()
            }
        }
    }

    private fun applyLocaleChanges(
        codeDir: File,
        localeChangesDir: File,
        changes: Map<String, Map<String, String>>,
    ) {
        val projectxConfig = File(codeDir, "projectx-config.jar")
        if (!projectxConfig.exists()) {
            Log.w(TAG, "projectx-config.jar not found, skipping locale changes")
            return
        }

        if (localeChangesDir.exists()) deleteDirectory(localeChangesDir)
        localeChangesDir.mkdirs()

        // Extract projectx-config.jar fully (overwrite is fine here because we are modifying properties files)
        val buffer = ByteArray(8192)
        JarInputStream(FileInputStream(projectxConfig)).use { jis ->
            var entry: JarEntry?
            while (jis.nextJarEntry.also { entry = it } != null) {
                val e = entry!!
                val destFile = safeDestination(localeChangesDir, e.name) ?: continue
                if (e.isDirectory) {
                    destFile.mkdirs()
                    continue
                }
                destFile.parentFile?.mkdirs()
                FileOutputStream(destFile).use { fos ->
                    var len: Int
                    while (jis.read(buffer).also { len = it } > 0) {
                        fos.write(buffer, 0, len)
                    }
                }
                jis.closeEntry()
            }
        }

        // Apply properties overrides
        changes.forEach { (bundlePath, overrides) ->
            // The key comes from a mod's mod.json: it must name a file inside rsrc/i18n, not climb out.
            val propsFile = safeDestination(File(localeChangesDir, "rsrc/i18n"), bundlePath)
            if (propsFile == null) {
                Log.w(TAG, "Ignored locale bundle outside rsrc/i18n: $bundlePath")
            } else if (propsFile.exists()) {
                val props = Properties()
                FileInputStream(propsFile).use { props.load(it) }
                overrides.forEach { (key, value) ->
                    props.setProperty(key, value)
                }
                FileOutputStream(propsFile).use { props.store(it, null) }
                Log.i(TAG, "Applied ${overrides.size} locale changes to $bundlePath")
            } else {
                Log.w(TAG, "Locale bundle file not found: rsrc/i18n/$bundlePath")
            }
        }

        val newJar = File(codeDir, "projectx-config-new.jar")
        val oldJar = File(codeDir, "projectx-config-old.jar")

        createJarFromDirectory(localeChangesDir, newJar)

        if (oldJar.exists()) oldJar.delete()
        if (projectxConfig.renameTo(oldJar)) {
            if (newJar.renameTo(projectxConfig)) {
                oldJar.delete()
                Log.i(TAG, "Successfully applied locale changes to projectx-config.jar")
            } else {
                oldJar.renameTo(projectxConfig)
                throw IOException("Failed to rename projectx-config-new.jar")
            }
        } else {
            newJar.delete()
            throw IOException("Failed to move projectx-config.jar aside; locale changes were not applied")
        }
        deleteDirectory(localeChangesDir)
    }

    private fun createJarFromDirectory(sourceDir: File, jarFile: File) {
        JarOutputStream(FileOutputStream(jarFile)).use { jos ->
            addDirectoryToJar(sourceDir, sourceDir, jos)
        }
    }

    private fun addDirectoryToJar(baseDir: File, currentDir: File, jos: JarOutputStream) {
        val files = currentDir.listFiles() ?: return
        val buffer = ByteArray(8192)
        for (file in files) {
            var entryName = baseDir.toURI().relativize(file.toURI()).path
            if (file.isDirectory) {
                if (!entryName.endsWith("/")) {
                    entryName += "/"
                }
                val entry = JarEntry(entryName)
                jos.putNextEntry(entry)
                jos.closeEntry()
                addDirectoryToJar(baseDir, file, jos)
            } else {
                if (entryName == "mod.json" || entryName == "mod.png") {
                    continue
                }
                val entry = JarEntry(entryName)
                jos.putNextEntry(entry)
                FileInputStream(file).use { fis ->
                    var len: Int
                    while (fis.read(buffer).also { len = it } > 0) {
                        jos.write(buffer, 0, len)
                    }
                }
                jos.closeEntry()
            }
        }
    }

    private fun deleteDirectory(dir: File) {
        dir.listFiles()?.forEach { file ->
            if (file.isDirectory) {
                deleteDirectory(file)
            } else {
                file.delete()
            }
        }
        dir.delete()
    }
}
