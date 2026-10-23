package com.skarm.launcher

import java.io.StringReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UrlRelayTest {
    @Test
    fun readsOneLine() {
        assertEquals("https://wiki.spiralknights.com", GameActivity.readBoundedLine(StringReader("https://wiki.spiralknights.com\nrest"), 2048))
    }

    @Test
    fun aLineWithoutANewlineEndsAtTheStream() {
        assertEquals("https://a.example", GameActivity.readBoundedLine(StringReader("https://a.example"), 2048))
        assertNull(GameActivity.readBoundedLine(StringReader(""), 2048))
    }

    @Test
    fun anOversizedLineIsRefused() {
        assertNull(GameActivity.readBoundedLine(StringReader("x".repeat(5000) + "\n"), 2048))
        assertEquals("x".repeat(10), GameActivity.readBoundedLine(StringReader("x".repeat(10) + "\n"), 10))
    }
}
