package com.skarm.launcher

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Test

class KeyMappingTest {
    @Test
    fun lettersAndDigitsAreUppercaseAscii() {
        assertEquals('A'.code, GameActivity.glfwKeyCode(KeyEvent.KEYCODE_A))
        assertEquals('Z'.code, GameActivity.glfwKeyCode(KeyEvent.KEYCODE_Z))
        assertEquals('0'.code, GameActivity.glfwKeyCode(KeyEvent.KEYCODE_0))
        assertEquals('9'.code, GameActivity.glfwKeyCode(KeyEvent.KEYCODE_9))
    }

    @Test
    fun punctuationAndNumpadUseGlfwCodes() {
        assertEquals(32, GameActivity.glfwKeyCode(KeyEvent.KEYCODE_SPACE))
        assertEquals(91, GameActivity.glfwKeyCode(KeyEvent.KEYCODE_LEFT_BRACKET))
        assertEquals(334, GameActivity.glfwKeyCode(KeyEvent.KEYCODE_NUMPAD_ADD))
    }

    @Test
    fun anUnmappedKeyIsMinusOne() {
        assertEquals(-1, GameActivity.glfwKeyCode(KeyEvent.KEYCODE_VOLUME_UP))
    }

    @Test
    fun modifiersMapToGlfwBits() {
        assertEquals(0, GameActivity.glfwModifiers(0))
        assertEquals(0x0001, GameActivity.glfwModifiers(KeyEvent.META_SHIFT_ON))
        assertEquals(0x0003, GameActivity.glfwModifiers(KeyEvent.META_SHIFT_ON or KeyEvent.META_CTRL_ON))
        assertEquals(0x0020, GameActivity.glfwModifiers(KeyEvent.META_NUM_LOCK_ON))
    }
}
