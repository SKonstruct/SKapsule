package com.skarm.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppUpdaterTest {
    private fun release(
        tag: String = "v2.4.1",
        html: String = "https://github.com/SKonstruct/SKapsule/releases/tag/v2.4.1",
        assets: String = """[{"name":"skapsule-v2.4.1.apk","browser_download_url":"https://github.com/SKonstruct/SKapsule/releases/download/v2.4.1/skapsule-v2.4.1.apk"}]""",
    ) = """{"tag_name":"$tag","html_url":"$html","assets":$assets}"""

    @Test
    fun readsTheTagWithoutItsPrefixAndTheApkAsset() {
        val r = AppUpdater.parseRelease(release())!!
        assertEquals("2.4.1", r.version)
        assertEquals("https://github.com/SKonstruct/SKapsule/releases/download/v2.4.1/skapsule-v2.4.1.apk", r.apkUrl)
    }

    @Test
    fun aReleaseWithoutAnApkStillParses() {
        assertNull(AppUpdater.parseRelease(release(assets = "[]"))!!.apkUrl)
    }

    @Test
    fun linksOffGitHubOrWithoutHttpsAreNotBelieved() {
        assertNull(AppUpdater.parseRelease(release(html = "http://github.com/x"))) // not https
        assertNull(AppUpdater.parseRelease(release(html = "https://evil.example/x")))
        val noApk = AppUpdater.parseRelease(
            release(assets = """[{"name":"a.apk","browser_download_url":"https://evil.example/a.apk"}]"""),
        )!!
        assertNull(noApk.apkUrl)
    }

    @Test
    fun incompleteAnswersAreRejected() {
        assertNull(AppUpdater.parseRelease("""{"html_url":"https://github.com/x"}"""))
        assertNull(AppUpdater.parseRelease("""{"tag_name":"v1.0.0"}"""))
    }

    @Test
    fun versionsCompareByNumberAndIgnoreSuffixes() {
        assertEquals(-1, AppUpdater.compareVersions("2.3.0", "2.4.0"))
        assertEquals(1, AppUpdater.compareVersions("2.10.0", "2.9.9"))
        assertEquals(0, AppUpdater.compareVersions("2.4.0", "2.4"))
        assertEquals(0, AppUpdater.compareVersions("2.4.1-test", "2.4.1"))
        assertEquals(0, AppUpdater.compareVersions("2.4.1-rc1", "2.4.1"))
        assertEquals(-1, AppUpdater.compareVersions("2.4.0-test", "2.4.1"))
    }
}
