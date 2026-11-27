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
 * Where the selected control's small dialog goes: fixed at the top right, [margin] in from the edges, so it is
 * always in the same place. It is moved down only as far as needed to clear the [reserved] areas up there (the
 * standalone editor's Done button), and never off the bottom of the screen.
 */
object DialogDock {
    fun place(
        screenW: Float,
        screenH: Float,
        dialogW: Float,
        dialogH: Float,
        reserved: List<Box>,
        margin: Float,
        gap: Float,
    ): Pair<Float, Float> {
        val x = screenW - dialogW - margin
        var y = margin
        // Highest first, so one pass clears a stack of reserved areas.
        for (r in reserved.sortedBy { it.top }) {
            val box = Box(x, y, x + dialogW, y + dialogH)
            if (box.intersects(r)) y = r.bottom + gap
        }
        return x to y.coerceIn(0f, maxOf(0f, screenH - dialogH - margin))
    }
}
