package com.skarm.launcher

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class QrModulesTest {
    private val dark = 0xFF000000.toInt()
    private val light = 0xFFFFFFFF.toInt()

    @Test
    fun parsesRowsOfOnesAndZeros() {
        val grid = QrModules.parse("10\n01")
        assertNotNull(grid)
        assertArrayEquals(booleanArrayOf(true, false), grid!![0])
        assertArrayEquals(booleanArrayOf(false, true), grid[1])
    }

    @Test
    fun toleratesATrailingNewline() {
        assertEquals(2, QrModules.parse("10\n01\n")!!.size)
    }

    @Test
    fun rejectsMalformedGrids() {
        assertNull(QrModules.parse(""))
        assertNull(QrModules.parse("101\n010"))
        assertNull(QrModules.parse("10\n0"))
        assertNull(QrModules.parse("1x\n01"))
    }

    @Test
    fun scaleFitsTheTargetIncludingTheQuietZone() {
        val grid = QrModules.parse(List(21) { "0".repeat(21) }.joinToString("\n"))!!
        val scale = QrModules.scaleFor(grid, 260)
        assertEquals(8, scale)
        assertEquals((21 + 2 * QrModules.QUIET_ZONE) * 8, QrModules.sidePx(grid, scale))
    }

    @Test
    fun scaleNeverDropsBelowOnePixelPerModule() {
        val grid = QrModules.parse(List(21) { "0".repeat(21) }.joinToString("\n"))!!
        assertEquals(1, QrModules.scaleFor(grid, 10))
    }

    @Test
    fun pixelsAreWhiteWithTheQuietZoneAndDarkModulesScaledUp() {
        val grid = QrModules.parse("10\n01")!!
        val scale = 2
        val side = QrModules.sidePx(grid, scale)
        val pixels = QrModules.toPixels(grid, scale)
        assertEquals(side * side, pixels.size)

        fun at(x: Int, y: Int) = pixels[y * side + x]
        val origin = QrModules.QUIET_ZONE * scale
        assertEquals(light, at(0, 0))
        assertEquals(light, at(origin - 1, origin - 1))
        // module (0,0) is dark and covers a 2x2 block
        for (dy in 0 until scale) for (dx in 0 until scale) assertEquals(dark, at(origin + dx, origin + dy))
        // module (1,0) is light
        assertEquals(light, at(origin + scale, origin))
        // module (1,1) is dark
        assertEquals(dark, at(origin + scale, origin + scale))
        assertEquals(light, at(side - 1, side - 1))
    }
}
