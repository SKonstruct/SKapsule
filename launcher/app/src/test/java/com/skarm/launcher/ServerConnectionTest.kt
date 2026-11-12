package com.skarm.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.Properties
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

class ServerConnectionTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private fun configJar(extra: Map<String, String> = mapOf("mod/Patched.class" to "bytecode")): File {
        val home = tmp.newFolder("home")
        val jar = File(home, "code/config.jar").also { it.parentFile.mkdirs() }
        ZipOutputStream(jar.outputStream()).use { out ->
            out.putNextEntry(ZipEntry("deployment.properties"))
            Properties().apply {
                setProperty("server_host", "game.spiralknights.com")
                setProperty("client_title", "Spiral Knights")
            }.store(out, null)
            out.closeEntry()
            extra.forEach { (name, body) ->
                out.putNextEntry(ZipEntry(name))
                out.write(body.toByteArray())
                out.closeEntry()
            }
        }
        return jar
    }

    private fun props(jar: File) = ZipFile(jar).use { z ->
        Properties().also { p -> z.getInputStream(z.getEntry("deployment.properties")).use { p.load(it) } }
    }

    private val home = ServerStore.Server("a", "Home", "10.0.2.2", 5000, "ab#10001", "https://c.example/client/x", "")

    @Test
    fun officialDefaultsFillBlankFields() {
        val d = ServerConnection.desired(home.copy(publicKey = ""))
        assertEquals("10.0.2.2", d["server_host"])
        assertEquals("5000", d["server_ports"])
        assertEquals("5000", d["datagram_ports"])
        assertEquals(ServerConnection.OFFICIAL_KEY, d["key.public"])
        assertEquals("https://c.example", d["client_root_url"])
        assertEquals(ServerConnection.OFFICIAL_CONTENT_URL, d["content_url"])
        assertEquals(ServerConnection.OFFICIAL_HOST, ServerConnection.desired(null)["server_host"])
        assertEquals("47624", ServerConnection.desired(null)["server_ports"])
    }

    @Test
    fun patchKeepsOtherEntriesAndOtherKeys() {
        val jar = configJar()
        assertTrue(ServerConnection.apply(jar.parentFile.parentFile, home))
        val p = props(jar)
        assertEquals("10.0.2.2", p.getProperty("server_host"))
        assertEquals("ab#10001", p.getProperty("key.public"))
        assertEquals("Spiral Knights", p.getProperty("client_title"))
        ZipFile(jar).use { z ->
            assertEquals("bytecode", z.getInputStream(z.getEntry("mod/Patched.class")).readBytes().decodeToString())
        }
        assertFalse(File(jar.parentFile, "config.jar.server").exists())
    }

    @Test
    fun switchingBackToOfficialRestoresDefaults() {
        val jar = configJar()
        val gameHome = jar.parentFile.parentFile
        ServerConnection.apply(gameHome, home)
        ServerConnection.apply(gameHome, null)
        val p = props(jar)
        assertEquals(ServerConnection.OFFICIAL_HOST, p.getProperty("server_host"))
        assertEquals(ServerConnection.OFFICIAL_KEY, p.getProperty("key.public"))
    }

    @Test
    fun unchangedSettingsDoNotRewriteTheJar() {
        val jar = configJar()
        val gameHome = jar.parentFile.parentFile
        ServerConnection.apply(gameHome, home)
        val stamp = jar.lastModified()
        val size = jar.length()
        Thread.sleep(20)
        ServerConnection.apply(gameHome, home)
        assertEquals(stamp, jar.lastModified())
        assertEquals(size, jar.length())
    }

    @Test
    fun missingJarMeansGameNotInstalled() {
        assertFalse(ServerConnection.apply(tmp.newFolder("empty"), home))
    }

    @Test
    fun readsTheManifestVersion() {
        assertEquals("20261110044614", ServerConnection.manifestVersion("appbase = http://x/%VERSION%\nversion = 20261110044614\n"))
        assertEquals("1", ServerConnection.manifestVersion("  version=1  "))
        assertEquals(null, ServerConnection.manifestVersion("versioned = 2\nappbase = x"))
        assertEquals(null, ServerConnection.manifestVersion(null))
    }
}
