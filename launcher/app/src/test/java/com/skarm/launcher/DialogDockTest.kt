package com.skarm.launcher

import com.skarm.launcher.touch.Box
import com.skarm.launcher.touch.DialogDock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Where the selected control's dialog goes: always the top right, below the Done button if there is one. */
class DialogDockTest {
    private val w = 2000f
    private val h = 900f
    private val dialogW = 480f
    private val dialogH = 360f
    private val tabStrip = Box(w / 2 - 300, 0f, w / 2 + 300, 120f)
    private val done = Box(w - 260, 0f, w, 150f)

    private fun place(reserved: List<Box>, dh: Float = dialogH): Box {
        val (x, y) = DialogDock.place(w, h, dialogW, dh, reserved, margin = 24f, gap = 24f)
        return Box(x, y, x + dialogW, y + dh)
    }

    @Test
    fun withNothingReservedItSitsInTheTopRightCorner() {
        val d = place(emptyList())
        assertEquals(w - dialogW - 24f, d.left, 0f)
        assertEquals(24f, d.top, 0f)
    }

    @Test
    fun itStaysBelowTheDoneButton() {
        val d = place(listOf(tabStrip, done))
        assertEquals(w - dialogW - 24f, d.left, 0f)
        assertFalse("dialog covers Done", d.intersects(done))
        assertEquals(done.bottom + 24f, d.top, 0f)
    }

    @Test
    fun theTabStripInTheMiddleDoesNotMoveIt() {
        val d = place(listOf(tabStrip))
        assertEquals(24f, d.top, 0f)
    }

    @Test
    fun itNeverLeavesTheScreen() {
        val d = place(listOf(done), dh = 800f)
        assertTrue(d.left >= 0f && d.top >= 0f && d.right <= w && d.bottom <= h)
    }
}
