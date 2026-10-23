package com.skarm.launcher

import org.junit.Test
import org.junit.Assert.*
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import java.lang.reflect.Method

class ModsApplierTest {

    @Test
    fun benchmarkParseModMetadata() {
        val tempFile = File.createTempFile("dummy_mod", ".zip")
        tempFile.deleteOnExit()

        // Create a zip with 10,000 files, with mod.json as the LAST file to measure worst-case scenario
        ZipOutputStream(FileOutputStream(tempFile)).use { zout ->
            // Add dummy files first
            for (i in 0 until 10000) {
                zout.putNextEntry(ZipEntry("dummy/file$i.txt"))
                zout.write("dummy content".toByteArray())
                zout.closeEntry()
            }

            // Add mod.json last
            zout.putNextEntry(ZipEntry("mod.json"))
            zout.write("{\"mod\": {\"name\": \"test mod\", \"type\": \"class\"}}".toByteArray())
            zout.closeEntry()
        }

        // Access private parseModMetadata
        val applier = ModsApplier
        val method: Method = ModsApplier::class.java.getDeclaredMethod("parseModMetadata", File::class.java)
        method.isAccessible = true

        // Warmup
        for (i in 0 until 5) {
            method.invoke(applier, tempFile)
        }

        // Benchmark
        val start = System.nanoTime()
        val result = method.invoke(applier, tempFile)
        val end = System.nanoTime()

        val durationMs = (end - start) / 1_000_000.0

        System.err.println("==> Worst-Case Benchmark: Parsed mod.json from 10,000 file zip in $durationMs ms")

        val nameField = result.javaClass.getDeclaredField("name")
        nameField.isAccessible = true
        val name = nameField.get(result) as String

        val typeField = result.javaClass.getDeclaredField("type")
        typeField.isAccessible = true
        val type = typeField.get(result) as String?

        assertEquals("test mod", name)
        assertEquals("class", type)
    }

    private fun zipOf(vararg entries: Pair<String, String>): File {
        val f = File.createTempFile("mod", ".zip").apply { deleteOnExit() }
        ZipOutputStream(FileOutputStream(f)).use { z ->
            for ((name, body) in entries) {
                z.putNextEntry(ZipEntry(name)); z.write(body.toByteArray()); z.closeEntry()
            }
        }
        return f
    }

    @Test
    fun destinationsStayInsideTheirRoot() {
        val root = java.nio.file.Files.createTempDirectory("mods-root").toFile()
        val i18n = File(root, "rsrc/i18n").also { it.mkdirs() }
        assertNotNull(ModsApplier.safeDestination(i18n, "messages.properties"))
        assertNotNull(ModsApplier.safeDestination(i18n, "sub/dir/messages.properties"))
        assertNull(ModsApplier.safeDestination(i18n, "../../../steam.creds"))
        assertNull(ModsApplier.safeDestination(i18n, "../../../../outside.properties"))
        // An absolute name is re-rooted under the directory by File(parent, child), not honoured.
        val rerooted = ModsApplier.safeDestination(i18n, "/etc/passwd")!!
        assertTrue(rerooted.canonicalPath.startsWith(i18n.canonicalPath + File.separator))
        // A sibling sharing the root's name as a prefix is not inside it.
        assertNull(ModsApplier.safeDestination(i18n, "../i18n-evil/x"))
        root.deleteRecursively()
    }

    @Test
    fun getdownDigestHashesEveryEntryInNameOrder() {
        // Written out of order, with META-INF, which the v2 digest includes.
        val jar = zipOf("b.txt" to "B", "META-INF/MANIFEST.MF" to "M", "a.txt" to "A")
        val expected = java.security.MessageDigest.getInstance("SHA-256")
            .digest("MAB".toByteArray()).joinToString("") { "%02x".format(it) }
        assertEquals(expected, ModsApplier.getdownDigest(jar))
    }

    @Test
    fun modsNeverReplaceProtectedFiles() {
        val mod = zipOf(
            "config/Wall.dat" to "protected, matched case-insensitively",
            "texture/ok.png" to "ordinary",
        )
        val out = kotlin.io.path.createTempDirectory("rsrc").toFile().apply { deleteOnExit() }
        val extract = ModsApplier::class.java.getDeclaredMethod(
            "extractZipToDirectory", File::class.java, File::class.java, Boolean::class.javaPrimitiveType,
        ).apply { isAccessible = true }
        extract.invoke(ModsApplier, mod, out, true)

        assertFalse(File(out, "config/Wall.dat").exists())
        assertTrue(File(out, "texture/ok.png").exists())
    }

    @Test
    fun restoresModdedCodeJarsFromTheLocalPristineCopyOffline() {
        val home = kotlin.io.path.createTempDirectory("sk").toFile().apply { deleteOnExit() }
        val code = File(home, "code").apply { mkdirs() }
        val digests = listOf("config.jar", "projectx-config.jar").joinToString("\n") { name ->
            val jar = zipOf("rsrc/$name.txt" to "vanilla $name")
            jar.copyTo(File(code, name))
            "code/$name = ${ModsApplier.getdownDigest(jar)}"
        }
        File(home, "digest2.txt").writeText(digests + "\n")
        // Unreachable server: any download fails, so success proves the local path.
        File(home, "getdown.txt").writeText("version = 1\nappbase = http://127.0.0.1:9/%VERSION%\n")

        val restore = ModsApplier::class.java.getDeclaredMethod("restorePristineCodeJars", File::class.java)
            .apply { isAccessible = true }
        restore.invoke(ModsApplier, home)                       // pristine: snapshot taken
        File(code, "config.jar").writeText("modded")            // a merge, or a torn write
        restore.invoke(ModsApplier, home)                       // must restore without network

        val expected = digests.lines().first().substringAfter(" = ")
        assertEquals(expected, ModsApplier.getdownDigest(File(code, "config.jar")))
    }
}
