package com.skarm.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GfxSettingsTest {
    @Test
    fun theOffscreenPathIsOnlyUsedWhenSomethingNeedsIt() {
        assertFalse(GfxSettings.usesOffscreen(0.6f, 0, false))
        assertTrue(GfxSettings.usesOffscreen(0.6f, 40, false))
        assertTrue(GfxSettings.usesOffscreen(1.0f, 10, false))
        assertTrue(GfxSettings.usesOffscreen(0.6f, 0, true))
    }

    @Test
    fun sharpHudDoesNothingAtFullResolution() {
        assertFalse(GfxSettings.sharpHudApplies(1.0f))
        assertFalse(GfxSettings.usesOffscreen(1.0f, 0, true))
        assertTrue(GfxSettings.sharpHudApplies(0.6f))
    }

    @Test
    fun theGameIsToldTheSceneSizeUnlessSharpHudLaysItOutAtFullSize() {
        assertEquals(Pair(1714, 768), GfxSettings.gameSize(2856, 1280, 0.6f, false))
        assertEquals(Pair(2856, 1280), GfxSettings.gameSize(2856, 1280, 0.6f, true))
        assertEquals(Pair(2856, 1280), GfxSettings.gameSize(2856, 1280, 1.0f, false))
    }

    @Test
    fun sizesRoundHalfUpAndNeverReachZero() {
        assertEquals(1, GfxSettings.scaled(1, 0.25f))
        assertEquals(3, GfxSettings.scaled(5, 0.5f))
        assertEquals(578, GfxSettings.scaled(963, 0.6f))
    }
}
