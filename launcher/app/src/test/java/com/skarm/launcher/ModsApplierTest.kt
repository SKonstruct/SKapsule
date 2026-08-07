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
}
