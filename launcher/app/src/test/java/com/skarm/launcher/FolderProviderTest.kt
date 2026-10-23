package com.skarm.launcher

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FolderProviderTest {
    @Test
    fun onlyPlainFileNamesAreAccepted() {
        assertTrue(FolderProvider.isSafeName("config.jar"))
        assertTrue(FolderProvider.isSafeName(".userPrefs"))
        assertFalse(FolderProvider.isSafeName(""))
        assertFalse(FolderProvider.isSafeName("."))
        assertFalse(FolderProvider.isSafeName(".."))
        assertFalse(FolderProvider.isSafeName("../steam.creds"))
        assertFalse(FolderProvider.isSafeName("a/b"))
        assertFalse(FolderProvider.isSafeName("a\u0000b"))
    }

    @Test
    fun theSavedSteamLoginAndItsKeyAreHidden() {
        assertTrue(FolderProvider.isHidden("/data/user/0/p/files/home/.local/share/frenchpress/steam.creds"))
        assertTrue(FolderProvider.isHidden("/data/user/0/p/files/home/.userPrefs/.java/.userPrefs/co/frenchpress/prefs.xml"))
        assertTrue(FolderProvider.isHidden("/data/user/0/p/files/x/steam.creds"))
        assertFalse(FolderProvider.isHidden("/data/user/0/p/files/sk/mods/a.zip"))
        assertFalse(FolderProvider.isHidden("/data/user/0/p/files/home/.userPrefs/.java/.userPrefs/projectx/prefs.xml"))
    }
}
