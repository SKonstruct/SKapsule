package com.skarm.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LogScrubberTest {
    @Test
    fun keyedSecretsAreRedacted() {
        assertEquals("FRENCHPRESS_STEAM_PASS=[redacted]", LogScrubber.scrub("FRENCHPRESS_STEAM_PASS=hunter2"))
        assertEquals("SK_WEB_PASS=[redacted]", LogScrubber.scrub("SK_WEB_PASS=hunter2"))
        assertEquals("password=[redacted]", LogScrubber.scrub("password: hunter2"))
        assertEquals("a token=[redacted]", LogScrubber.scrub("a token=abc123"))
    }

    @Test
    fun jsonSecretsAreRedacted() {
        val out = LogScrubber.scrub("""{"refresh_token":"eyJabc","name":"x","password": "p w"}""")
        assertFalse(out.contains("eyJabc"))
        assertFalse(out.contains("p w"))
        assertTrue(out.contains("\"name\":\"x\""))
    }

    @Test
    fun authorizationHeadersLoseTheirValueIncludingTheScheme() {
        val out = LogScrubber.scrub("Authorization: Bearer abc.def.ghi")
        assertFalse(out.contains("abc.def.ghi"))
        assertFalse(out.contains("Bearer abc"))
    }

    @Test
    fun emailsAndSteamIdsAreRedacted() {
        assertEquals("[redacted-email] is [steamid]", LogScrubber.scrub("me@example.com is 76561198000000000"))
    }

    @Test
    fun privateAppPathsBecomePlaceholders() {
        assertEquals(
            "open <app>/files/sk/code/x.jar",
            LogScrubber.scrub("open /data/user/0/com.skarm.launcher/files/sk/code/x.jar", listOf("/data/user/0/com.skarm.launcher")),
        )
    }

    @Test
    fun ordinaryLogLinesAreUntouched() {
        val line = "I sklauncher: JVM thread: JNI_CreateJavaVM OK 12345"
        assertEquals(line, LogScrubber.scrub(line))
    }
}
