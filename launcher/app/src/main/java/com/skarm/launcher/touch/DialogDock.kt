package com.skarm.launcher.touch

/** A rectangle in overlay coordinates, kept free of android.graphics so it runs in plain unit tests. */
class Box(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (top + bottom) / 2f

    fun intersects(o: Box): Boolean = left < o.right && o.left < right && top < o.bottom && o.top < bottom

    fun inflated(by: Float) = Box(left - by, top - by, right + by, bottom + by)

    fun overlapArea(o: Box): Float {
        val w = minOf(right, o.right) - maxOf(left, o.left)
        val h = minOf(bottom, o.bottom) - maxOf(top, o.top)
        return if (w > 0f && h > 0f) w * h else 0f
    }
}

/**
 * Where the selected control's small dialog goes: a spot that does not cover the control (the player is
 * dragging it), keeps clear of the tab strip and the Done button, covers as little of the [others] as it
 * can, and is as far from the control as the screen allows. The candidates are the four corners and four
 * edge middles. If nothing is free (a very small screen) the farthest candidate is used anyway.
 */
object DialogDock {
    fun place(
        screenW: Float,
        screenH: Float,
        dialogW: Float,
        dialogH: Float,
        control: Box,
        reserved: List<Box>,
        margin: Float,
        gap: Float,
        others: List<Box> = emptyList(),
    ): Pair<Float, Float> {
        val right = screenW - dialogW - margin
        val bottom = screenH - dialogH - margin
        val midX = (screenW - dialogW) / 2f
        val midY = (screenH - dialogH) / 2f
        val candidates = listOf(
            margin to margin, right to margin, margin to bottom, right to bottom,
            margin to midY, right to midY, midX to margin, midX to bottom,
        )
        val keepClear = control.inflated(gap)
        fun box(c: Pair<Float, Float>) = Box(c.first, c.second, c.first + dialogW, c.second + dialogH)
        fun distance(c: Pair<Float, Float>): Float {
            val b = box(c)
            val dx = b.centerX - control.centerX
            val dy = b.centerY - control.centerY
            return dx * dx + dy * dy
        }
        val free = candidates.filter { c -> !box(c).intersects(keepClear) && reserved.none { box(c).intersects(it) } }
        val pool = free.ifEmpty { candidates.filter { c -> !box(c).intersects(keepClear) } }.ifEmpty { candidates }
        fun covered(c: Pair<Float, Float>) = others.sumOf { box(c).overlapArea(it).toDouble() }
        return pool.minWithOrNull(compareBy<Pair<Float, Float>> { covered(it) }.thenByDescending { distance(it) })
            ?: candidates.first()
    }
}
