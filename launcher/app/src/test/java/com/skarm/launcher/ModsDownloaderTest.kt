package com.skarm.launcher

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModsDownloaderTest {
    @Test
    fun modNamesAreSingleComponents() {
        assertTrue(ModsDownloader.isSafeModName("Cool Mod v1.zip"))
        assertFalse(ModsDownloader.isSafeModName(""))
        assertFalse(ModsDownloader.isSafeModName(".."))
        assertFalse(ModsDownloader.isSafeModName("../x.zip"))
        assertFalse(ModsDownloader.isSafeModName("a/b.zip"))
        assertFalse(ModsDownloader.isSafeModName("a\\b.zip"))
    }

    @Test
    fun onlyRawGitHubOverHttpsIsTrusted() {
        assertTrue(ModsDownloader.isTrustedDownloadUrl("https://raw.githubusercontent.com/SirDank/Spiral-Knights-Modpack/main/mods/a.zip"))
        assertFalse(ModsDownloader.isTrustedDownloadUrl("http://raw.githubusercontent.com/x/y/main/a.zip"))
        assertFalse(ModsDownloader.isTrustedDownloadUrl("https://evil.example/a.zip"))
        assertFalse(ModsDownloader.isTrustedDownloadUrl("https://raw.githubusercontent.com.evil.example/a.zip"))
        assertFalse(ModsDownloader.isTrustedDownloadUrl("not a url"))
    }
}
