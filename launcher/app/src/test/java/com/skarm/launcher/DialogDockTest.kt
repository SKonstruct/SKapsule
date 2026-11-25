package com.skarm.launcher

import com.skarm.launcher.touch.Box
import com.skarm.launcher.touch.DialogDock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Where the selected control's dialog goes: never over the control, clear of the tab strip and Done. */
class DialogDockTest {
    private val w = 2000f
    private val h = 900f
    private val dialogW = 480f
    private val dialogH = 360f
    private val reserved = listOf(Box(w / 2 - 300, 0f, w / 2 + 300, 120f), Box(w - 260, 0f, w, 150f))

    private fun place(control: Box): Box {
        val (x, y) = DialogDock.place(w, h, dialogW, dialogH, control, reserved, margin = 24f, gap = 24f)
        return Box(x, y, x + dialogW, y + dialogH)
    }

    private fun box(cx: Float, cy: Float, size: Float = 160f) = Box(cx - size / 2, cy - size / 2, cx + size / 2, cy + size / 2)

    @Test
    fun aControlOnTheLeftPutsTheDialogOnTheRight() {
        val d = place(box(300f, 600f))
        assertTrue("the dialog is on the right half", d.centerX > w / 2)
    }

    @Test
    fun aControlOnTheRightPutsTheDialogOnTheLeft() {
        val d = place(box(1700f, 600f))
        assertTrue("the dialog is on the left half", d.centerX < w / 2)
    }

    @Test
    fun theDialogNeverCoversTheControl() {
        // Every control position on a grid, including the centred bottom row (the awkward case).
        for (cx in 100..1900 step 150) for (cy in 100..850 step 150) {
            val c = box(cx.toFloat(), cy.toFloat())
            assertFalse("control at $cx,$cy is covered", place(c).intersects(c))
        }
    }

    @Test
    fun theDialogKeepsClearOfTheTabStripAndDone() {
        for (cx in 100..1900 step 200) for (cy in 100..850 step 200) {
            val d = place(box(cx.toFloat(), cy.toFloat()))
            reserved.forEach { assertFalse("dialog over a reserved area for $cx,$cy", d.intersects(it)) }
        }
    }

    @Test
    fun theDialogStaysOnScreen() {
        val d = place(box(1000f, 450f))
        assertTrue(d.left >= 0f && d.top >= 0f && d.right <= w && d.bottom <= h)
    }

    @Test
    fun itPrefersASpotThatCoversNoOtherControl() {
        val selected = box(300f, 600f)
        // Without company the farthest spot is on the right; put controls there and it moves.
        val busy = listOf(Box(1500f, 500f, 1980f, 880f), Box(1500f, 100f, 1980f, 480f))
        val (x, y) = DialogDock.place(w, h, dialogW, dialogH, selected, reserved, margin = 24f, gap = 24f, others = busy)
        val d = Box(x, y, x + dialogW, y + dialogH)
        busy.forEach { assertFalse("dialog covers another control", d.intersects(it)) }
        assertFalse(d.intersects(selected))
    }

    @Test
    fun aVeryLargeControlStillGetsASpot() {
        // Nothing is free, so it falls back to the farthest candidate rather than failing.
        val d = place(Box(0f, 0f, w, h))
        assertEquals(dialogW, d.width, 0f)
    }
}
