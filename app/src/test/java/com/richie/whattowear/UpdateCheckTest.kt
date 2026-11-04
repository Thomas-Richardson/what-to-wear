package com.richie.whattowear

import com.richie.whattowear.model.UpdateCheck
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The "Check for updates" button, checked against a real GitHub "latest release" reply (saved, never fetched). */
class UpdateCheckTest {
    // A real reply for another open-source Android app's releases, trimmed to four attachments.
    private val otherRepo = javaClass.getResource("/github-latest-release-other-repo.json")!!.readText()
    private val ours = otherRepo.replace(
        "https://github.com/ImranR98/Obtainium/releases/download/",
        "https://github.com/Thomas-Richardson/what-to-wear/releases/download/",
    )

    @Test
    fun neverDownloadsFromAnotherRepository() {
        assertNull(UpdateCheck.parse(otherRepo))
    }

    @Test
    fun readsTheVersionAndAppFile() {
        val release = UpdateCheck.parse(ours)!!
        assertEquals("1.7.4", release.version)
        assertTrue(release.apkUrl, release.apkUrl.startsWith("https://github.com/Thomas-Richardson/what-to-wear/releases/download/"))
        assertTrue(release.apkUrl.endsWith(".apk"))
        assertTrue(release.apkBytes > 1_000_000)
    }

    @Test
    fun aReleaseWithoutAnAppFileIsIgnored() {
        assertNull(UpdateCheck.parse("""{"tag_name": "v0.12", "assets": []}"""))
    }

    @Test
    fun comparesVersionsAsNumbersNotText() {
        assertTrue(UpdateCheck.isNewer("0.12", "0.11"))
        assertTrue(UpdateCheck.isNewer("v0.10", "0.9"))
        assertTrue(UpdateCheck.isNewer("1.0", "0.11"))
        assertTrue(UpdateCheck.isNewer("0.11.1", "0.11"))
        assertFalse(UpdateCheck.isNewer("0.11", "0.11"))
        assertFalse(UpdateCheck.isNewer("0.9", "0.10"))
    }
}
