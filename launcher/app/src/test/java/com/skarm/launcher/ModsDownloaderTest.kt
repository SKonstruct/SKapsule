package com.skarm.launcher

import com.skarm.launcher.ModsDownloader.ModFile
import com.skarm.launcher.ModsDownloader.PackState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModsDownloaderTest {
    private fun mod(name: String) = ModFile(name, "https://raw.githubusercontent.com/x/y/main/$name", "sha-$name", 10)

    /** [here] is what is in the mods folder; [stale] the names that no longer match the listing. */
    private fun status(
        remote: List<String>,
        synced: Set<String> = emptySet(),
        here: Set<String> = emptySet(),
        stale: Set<String> = emptySet(),
    ) = ModsDownloader.packStatus(
        remote.map(::mod),
        synced,
        exists = { it in here },
        isCurrent = { it.name in here && it.name !in stale },
    )

    @Test
    fun nothingDownloadedYetIsNotInstalled() {
        val s = status(remote = listOf("a.zip", "b.zip"))
        assertEquals(PackState.NOT_INSTALLED, s.state)
        assertEquals(2, s.toDownload)
    }

    @Test
    fun aNewOrChangedModPackIsAnUpdate() {
        // b.zip was added to the pack since the last sync.
        assertEquals(PackState.UPDATE, status(remote = listOf("a.zip", "b.zip"), synced = setOf("a.zip"), here = setOf("a.zip")).state)
        // a.zip changed.
        val changed = status(remote = listOf("a.zip"), synced = setOf("a.zip"), here = setOf("a.zip"), stale = setOf("a.zip"))
        assertEquals(PackState.UPDATE, changed.state)
        assertEquals(1, changed.toDownload)
    }

    @Test
    fun aModTheModpackDroppedIsAnUpdateOnlyIfASyncWroteIt() {
        val dropped = status(remote = listOf("a.zip"), synced = setOf("a.zip", "old.zip"), here = setOf("a.zip", "old.zip"))
        assertEquals(PackState.UPDATE, dropped.state)
        assertEquals(1, dropped.toDelete)
        // The player's own mod is not the pack's to remove, so it is no reason to update.
        val own = status(remote = listOf("a.zip"), synced = setOf("a.zip"), here = setOf("a.zip", "mine.zip"))
        assertEquals(PackState.CURRENT, own.state)
    }

    @Test
    fun matchingFilesAreCurrent() {
        val s = status(remote = listOf("a.zip", "b.zip"), synced = setOf("a.zip", "b.zip"), here = setOf("a.zip", "b.zip"))
        assertEquals(PackState.CURRENT, s.state)
        assertEquals(0, s.toDownload + s.toDelete)
    }

    @Test
    fun aSingleSharedFileCountsAsInstalledSoTheRestIsAnUpdate() {
        assertEquals(PackState.UPDATE, status(remote = listOf("a.zip", "b.zip"), here = setOf("a.zip")).state)
    }

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
