package com.skarm.launcher

/** The Steam sign-in QR as frenchpress hands it over: rows of '1' (dark) and '0' (light). */
object QrModules {
    const val QUIET_ZONE = 4

    private const val DARK = 0xFF000000.toInt()
    private const val LIGHT = 0xFFFFFFFF.toInt()

    /** @return the square module grid, or null if [modules] is empty, ragged or not a square of '0'/'1'. */
    fun parse(modules: String): Array<BooleanArray>? {
        val rows = modules.trim().split('\n')
        val size = rows.size
        if (size == 0 || rows.any { it.length != size || it.any { c -> c != '0' && c != '1' } }) return null
        return Array(size) { y -> BooleanArray(size) { x -> rows[y][x] == '1' } }
    }

    /** Largest whole pixels-per-module that keeps the QR and its quiet zone within [targetPx]. */
    fun scaleFor(grid: Array<BooleanArray>, targetPx: Int): Int =
        maxOf(1, targetPx / (grid.size + 2 * QUIET_ZONE))

    fun sidePx(grid: Array<BooleanArray>, scale: Int): Int = (grid.size + 2 * QUIET_ZONE) * scale

    /** ARGB pixels, row-major, nearest-neighbour scaled by [scale] on a white quiet zone. */
    fun toPixels(grid: Array<BooleanArray>, scale: Int): IntArray {
        val side = sidePx(grid, scale)
        val pixels = IntArray(side * side) { LIGHT }
        for (y in grid.indices) {
            for (x in grid.indices) {
                if (!grid[y][x]) continue
                val left = (x + QUIET_ZONE) * scale
                val top = (y + QUIET_ZONE) * scale
                for (dy in 0 until scale) {
                    val row = (top + dy) * side + left
                    pixels.fill(DARK, row, row + scale)
                }
            }
        }
        return pixels
    }
}
