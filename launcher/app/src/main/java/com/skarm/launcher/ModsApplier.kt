package com.skarm.launcher

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.Properties
import java.util.jar.JarEntry
import java.util.jar.JarInputStream
import java.util.jar.JarOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream

object ModsApplier {
    private const val TAG = "ModsApplier"

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
        "config/status_effect.dat", "config/status_effect.xml",
        "config/tileset.dat", "config/tileset.xml",
        "config/toy.dat", "config/toy.xml",
        "config/vfx.dat", "config/vfx.xml",
        "config/view.dat", "config/view.xml",
        "config/weapon.dat", "config/weapon.xml",
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
                        extractZipToDirectory(modFile, classChangesDir, false)
                        stats.classModsApplied++
                        hasClassMods = true
                    } else {
                        stats.modsSkipped++
                        val warning = "Class mod '${metadata.name}' requires game version ${metadata.pxVersion} but current is $gameVersion - SKIPPED"
                        stats.warnings.add(warning)
                    }
                } else {
                    extractZipToDirectory(modFile, rsrcDir, false)
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

    private fun parseModMetadata(modFile: File): ModMetadata {
        val metadata = ModMetadata(name = modFile.name)
        try {
            ZipFile(modFile).use { zipFile ->
                val entry = zipFile.getEntry("mod.json") ?: zipFile.entries().asSequence().find { it.name.endsWith("/mod.json") }
                if (entry != null) {
                    zipFile.getInputStream(entry).use { inputStream ->
                        val content = inputStream.bufferedReader().readText()
                        val json = JSONObject(content)
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
        purgeDirectory(rsrcDir)
        rebuildResources(rsrcDir, onProgress)
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

                if (checkProtected && PROTECTED_FILES.contains(entryName)) {
                    Log.w(TAG, "Skipping protected file: $entryName")
                    zis.closeEntry()
                    continue
                }

                val destFile = File(targetDir, entryName)
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

    private fun extractJarWithoutOverwrite(jarFile: File, targetDir: File) {
        val buffer = ByteArray(8192)
        JarInputStream(FileInputStream(jarFile)).use { jis ->
            var entry: JarEntry?
            while (jis.nextJarEntry.also { entry = it } != null) {
                val e = entry!!
                if (e.isDirectory) {
                    File(targetDir, e.name).mkdirs()
                    continue
                }
                val destFile = File(targetDir, e.name)
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
                if (e.isDirectory) {
                    File(localeChangesDir, e.name).mkdirs()
                    continue
                }
                val destFile = File(localeChangesDir, e.name)
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
            val propsFile = File(localeChangesDir, "rsrc/i18n/$bundlePath")
            if (propsFile.exists()) {
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
