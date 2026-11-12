package com.skarm.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerStoreTest {
    private fun server(
        name: String = "Home",
        host: String = "10.0.2.2",
        port: Int = 47624,
        key: String = "",
        client: String = "",
        content: String = "",
        id: String = "a",
    ) = ServerStore.Server(id, name, host, port, key, client, content)

    @Test
    fun roundTripsAndSkipsMalformedEntries() {
        val saved = listOf(server(key = "ab12#10001", client = "http://h", content = "https://c/"), server(name = "B", id = "b"))
        assertEquals(saved, ServerStore.parse(ServerStore.serialize(saved)))
        assertEquals(emptyList<ServerStore.Server>(), ServerStore.parse(null))
        assertEquals(emptyList<ServerStore.Server>(), ServerStore.parse("not json"))
        // A record with a bad port is dropped; the good one survives.
        val json = """[{"id":"x","name":"Bad","host":"h","port":0},{"id":"y","name":"Ok","host":"h","port":5}]"""
        assertEquals(listOf("Ok"), ServerStore.parse(json).map { it.name })
    }

    @Test
    fun validatesEachField() {
        assertNull(ServerStore.validate(server(), emptyList()))
        assertNull(ServerStore.validate(server(key = "AB12#10001", client = "https://h/x", content = "http://c"), emptyList()))
        assertEquals(ServerStore.Problem.NAME, ServerStore.validate(server(name = ""), emptyList()))
        assertEquals(ServerStore.Problem.NAME, ServerStore.validate(server(name = "x".repeat(41)), emptyList()))
        assertEquals(ServerStore.Problem.DUPLICATE, ServerStore.validate(server(name = "official"), emptyList()))
        assertEquals(ServerStore.Problem.DUPLICATE, ServerStore.validate(server(id = "z", name = "HOME"), listOf(server())))
        // Editing a server keeps its own name.
        assertNull(ServerStore.validate(server(), listOf(server())))
        assertEquals(ServerStore.Problem.HOST, ServerStore.validate(server(host = "a b"), emptyList()))
        assertEquals(ServerStore.Problem.HOST, ServerStore.validate(server(host = "http://h"), emptyList()))
        assertEquals(ServerStore.Problem.PORT, ServerStore.validate(server(port = 0), emptyList()))
        assertEquals(ServerStore.Problem.PORT, ServerStore.validate(server(port = 70000), emptyList()))
        assertEquals(ServerStore.Problem.KEY, ServerStore.validate(server(key = "zz#1"), emptyList()))
        assertEquals(ServerStore.Problem.KEY, ServerStore.validate(server(key = "abcd"), emptyList()))
        assertEquals(ServerStore.Problem.URL, ServerStore.validate(server(client = "ftp://h"), emptyList()))
        assertEquals(ServerStore.Problem.URL, ServerStore.validate(server(content = "http://"), emptyList()))
    }

    @Test
    fun clientRootDropsThePath() {
        assertEquals("http://gamemedia2.spiralknights.com", ServerStore.toClientRoot("http://gamemedia2.spiralknights.com/spiral/client/"))
        assertEquals("https://h:8080", ServerStore.toClientRoot("HTTPS://h:8080/a/b"))
        assertEquals("http://h", ServerStore.toClientRoot("h/x"))
        assertEquals("", ServerStore.toClientRoot("  "))
    }

    @Test
    fun localNetworkHostsAreTheOnesAndroid17Guards() {
        for (host in listOf("10.0.2.2", "192.168.1.20", "172.16.0.5", "172.31.9.9", "169.254.1.1", "mypc", "nas.local", "fd12::1", "[fe80::1]")) {
            assertTrue(host, ServerStore.isLocalNetworkHost(host))
        }
        for (host in listOf("game.example.com", "8.8.8.8", "172.32.0.1", "172.15.0.1", "localhost", "127.0.0.1", "")) {
            assertFalse(host, ServerStore.isLocalNetworkHost(host))
        }
    }
}
