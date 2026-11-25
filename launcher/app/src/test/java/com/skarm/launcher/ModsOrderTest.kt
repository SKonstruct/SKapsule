package com.skarm.launcher

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * The automatic mod order. The same cases are checked on iOS (SKModOrderTests.m), and the rule is
 * SKradle's: the mods that change the most files go first, equal ones by name, so a small mod
 * applied later beats a broad one.
 */
class ModsOrderTest {
    private val protected = setOf("config/item.dat", "config/wall.dat")

    private fun written(vararg names: String) = ModsOrder.filesWritten(names.asSequence(), protected)

    private fun zipBytes(vararg names: String): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { z ->
            for (n in names) {
                z.putNextEntry(ZipEntry(n))
                if (!n.endsWith("/")) z.write(1)
                z.closeEntry()
            }
        }
        return out.toByteArray()
    }

    private fun zipFile(fileName: String, vararg names: String): File {
        val dir = File.createTempFile("modsorder", "").apply { delete(); mkdirs(); deleteOnExit() }
        return File(dir, fileName).apply { writeBytes(zipBytes(*names)); deleteOnExit() }
    }

    @Test
    fun countsOnlyTheFilesAModWrites() {
        assertEquals(3, written("a.txt", "dir/b.txt", "dir/sub/c.txt"))
        assertEquals("directories", 1, written("dir/", "dir/sub/", "dir/f.txt"))
        assertEquals("metadata at any depth", 1, written("mod.json", "mod.png", "pack/mod.json", "pack/mod.png", "x.txt"))
        assertEquals("protected files, any case", 1, written("config/item.dat", "Config/WALL.DAT", "config/other.dat"))
        assertEquals("paths that leave the root", 1, written("../evil.txt", "/abs.txt", "a/../../b.txt", "ok.txt"))
        assertEquals("a .. that stays inside is fine", 1, written("a/../b.txt"))
        assertEquals(0, written())
    }

    @Test
    fun theMostFilesGoFirstThenByNameIgnoringCase() {
        val counts = mapOf("small.zip" to 2, "Broad.zip" to 50, "mid.zip" to 10, "Alpha.zip" to 10, "beta.zip" to 10)
        val order = ModsOrder.optimal(counts.keys.toList(), { it }, { counts.getValue(it) })
        assertEquals(listOf("Broad.zip", "Alpha.zip", "beta.zip", "mid.zip", "small.zip"), order)
    }

    @Test
    fun theOrderDoesNotDependOnTheInputOrder() {
        val counts = mapOf("a.zip" to 3, "B.zip" to 3, "b.zip" to 3, "c.zip" to 9, "d.modpack" to 0)
        val expected = ModsOrder.optimal(counts.keys.toList(), { it }, { counts.getValue(it) })
        repeat(20) { seed ->
            val shuffled = counts.keys.shuffled(java.util.Random(seed.toLong()))
            assertEquals(expected, ModsOrder.optimal(shuffled, { it }, { counts.getValue(it) }))
        }
        // Names that differ only in case still get one fixed order.
        assertEquals(listOf("B.zip", "b.zip"), expected.filter { it.equals("b.zip", ignoreCase = true) })
    }

    @Test
    fun aTargetedModComesAfterABroadOneSoItWins() {
        val order = ModsOrder.optimal(listOf("tweak.zip", "overhaul.zip"), { it }, { if (it == "tweak.zip") 1 else 400 })
        assertEquals("overhaul.zip", order.first())
        assertEquals("tweak.zip", order.last())
    }

    @Test
    fun countsAZipFromItsEntries() {
        val f = zipFile("m.zip", "a.txt", "dir/", "dir/b.txt", "mod.json", "config/item.dat")
        assertEquals(2, ModsApplier.filesChanged(f))
    }

    @Test
    fun aModpackIsTheSumOfItsInnerMods() {
        val inner1 = zipBytes("a.txt", "b.txt", "mod.json") // 2
        val inner2 = zipBytes("c.txt", "config/wall.dat") // 1 (the protected file is left out)
        val nested = zipBytes("never/counted.txt") // inside a folder: apply ignores it
        val dir = File.createTempFile("modsorder", "").apply { delete(); mkdirs(); deleteOnExit() }
        val pack = File(dir, "pack.modpack").apply { deleteOnExit() }
        ZipOutputStream(pack.outputStream()).use { z ->
            fun put(name: String, bytes: ByteArray) {
                z.putNextEntry(ZipEntry(name))
                z.write(bytes)
                z.closeEntry()
            }
            put("one.zip", inner1)
            put("two.zip", inner2)
            put("sub/three.zip", nested)
            put("readme.txt", byteArrayOf(1, 2, 3))
        }
        assertEquals(3, ModsApplier.filesChanged(pack))
    }

    @Test
    fun anUnreadableModCountsZeroAndSortsLast() {
        val bad = File.createTempFile("bad", ".zip").apply { writeText("not a zip"); deleteOnExit() }
        assertEquals(0, ModsApplier.filesChanged(bad))
        val good = zipFile("good.zip", "a.txt")
        val order = ModsOrder.optimal(listOf(bad, good), { it.name }, ModsApplier::filesChanged)
        assertEquals(good, order.first())
    }
}
