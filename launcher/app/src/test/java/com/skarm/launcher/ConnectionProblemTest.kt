package com.skarm.launcher

import java.io.IOException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectionProblemTest {
    @Test
    fun droppedAndUnreachableConnectionsAreNotDefects() {
        assertTrue(CrashReporting.isConnectionProblem(SocketException("Software caused connection abort")))
        assertTrue(CrashReporting.isConnectionProblem(SocketTimeoutException("timeout")))
        assertTrue(CrashReporting.isConnectionProblem(UnknownHostException("github.com")))
        assertTrue(CrashReporting.isConnectionProblem(IOException("wrapped", SocketException("reset"))))
    }

    @Test
    fun otherFailuresStayReportable() {
        assertFalse(CrashReporting.isConnectionProblem(IOException("No space left on device")))
        assertFalse(CrashReporting.isConnectionProblem(IllegalStateException("bad state")))
    }
}
