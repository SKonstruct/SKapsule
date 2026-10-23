package com.skarm.launcher

import android.view.MotionEvent
import com.skarm.launcher.touch.mouseButtonChanges
import com.skarm.launcher.touch.mouseButtonMask
import org.junit.Assert.assertEquals
import org.junit.Test

class MouseButtonTest {
    @Test
    fun androidBitsMapToGlfwButtons() {
        assertEquals(0, mouseButtonMask(0))
        assertEquals(1, mouseButtonMask(MotionEvent.BUTTON_PRIMARY))
        assertEquals(2, mouseButtonMask(MotionEvent.BUTTON_SECONDARY))
        assertEquals(3, mouseButtonMask(MotionEvent.BUTTON_PRIMARY or MotionEvent.BUTTON_SECONDARY))
        assertEquals(0, mouseButtonMask(MotionEvent.BUTTON_BACK))
    }

    @Test
    fun releasingLeftWhileRightIsHeldReleasesOnlyLeft() {
        // Left released, right pressed in the same event: no UP for the pointer ever arrives.
        assertEquals(Pair(2, 1), mouseButtonChanges(previous = 1, now = 2))
    }

    @Test
    fun aSecondButtonPressedWhileFirstIsHeld() {
        assertEquals(Pair(2, 0), mouseButtonChanges(previous = 1, now = 3))
        assertEquals(Pair(0, 3), mouseButtonChanges(previous = 3, now = 0))
    }
}
