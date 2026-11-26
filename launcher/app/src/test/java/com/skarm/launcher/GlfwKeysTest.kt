package com.skarm.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The same cases run on iOS (Tests/SKGlfwKeysTests.m). */
class GlfwKeysTest {
    @Test fun `letters map to their key whatever the case`() {
        assertEquals(65, GlfwKeys.keyFor('a'.code))
        assertEquals(65, GlfwKeys.keyFor('A'.code))
        assertEquals(90, GlfwKeys.keyFor('z'.code))
        assertFalse(GlfwKeys.shifted('a'.code))
        assertTrue(GlfwKeys.shifted('A'.code))
    }

    @Test fun `digits and their shifted symbols share a key`() {
        assertEquals(48, GlfwKeys.keyFor('0'.code))
        assertEquals(57, GlfwKeys.keyFor('9'.code))
        assertEquals(49, GlfwKeys.keyFor('!'.code))
        assertEquals(48, GlfwKeys.keyFor(')'.code))
        assertEquals(57, GlfwKeys.keyFor('('.code))
        assertTrue(GlfwKeys.shifted('!'.code))
        assertFalse(GlfwKeys.shifted('1'.code))
    }

    @Test fun `punctuation and space`() {
        assertEquals(32, GlfwKeys.keyFor(' '.code))
        assertEquals(44, GlfwKeys.keyFor(','.code))
        assertEquals(44, GlfwKeys.keyFor('<'.code))
        assertEquals(46, GlfwKeys.keyFor('.'.code))
        assertEquals(45, GlfwKeys.keyFor('_'.code))
        assertEquals(61, GlfwKeys.keyFor('+'.code))
        assertEquals(96, GlfwKeys.keyFor('~'.code))
        assertEquals(39, GlfwKeys.keyFor('"'.code))
        assertTrue(GlfwKeys.shifted('"'.code))
        assertFalse(GlfwKeys.shifted('\''.code))
    }

    @Test fun `text with no key has none`() {
        assertEquals(GlfwKeys.UNKNOWN, GlfwKeys.keyFor('é'.code))
        assertEquals(GlfwKeys.UNKNOWN, GlfwKeys.keyFor(0x1F600))
        assertEquals(GlfwKeys.UNKNOWN, GlfwKeys.keyFor('\n'.code))
        assertEquals(GlfwKeys.UNKNOWN, GlfwKeys.keyFor(0x1002C)) // not a comma
        assertFalse(GlfwKeys.shifted('é'.code))
    }
}
