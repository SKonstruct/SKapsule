package com.skarm.launcher

import com.skarm.launcher.touch.dragPercent
import org.junit.Assert.assertEquals
import org.junit.Test

class TouchControlGeometryTest {
    // A 100-wide control on a 1000-wide overlay, grabbed 30 px right of its left edge.
    private val size = 100f
    private val total = 1000f
    private val grab = -30f

    @Test
    fun centreFollowsTheFingerKeepingTheGrabPoint() {
        // Finger at 530 means the left edge is at 500, so the centre is at 550.
        assertEquals(0.55f, dragPercent(530f, grab, size, total), 1e-6f)
    }

    @Test
    fun staysOnScreenAtBothEdges() {
        assertEquals(0.05f, dragPercent(-500f, grab, size, total), 1e-6f)
        assertEquals(0.95f, dragPercent(5000f, grab, size, total), 1e-6f)
    }

    @Test
    fun aControlWiderThanTheOverlayDoesNotThrow() {
        // Pinned to the left edge: centre at half its width.
        assertEquals(200f / 300f, dragPercent(10f, 0f, 400f, 300f), 1e-6f)
    }
}
