package com.joaohouto.clusterlauncher.data.update

import com.joaohouto.clusterlauncher.data.scanner.StorageScanner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateManagerTest {

    @Test
    fun isNewerVersion_comparesSemanticVersionsCorrectly() {
        assertTrue(AppUpdateManager.isNewerVersion("v2.0.0", "1.6.1"))
        assertTrue(AppUpdateManager.isNewerVersion("2.0.1", "v2.0.0"))
        assertTrue(AppUpdateManager.isNewerVersion("v1.10.0", "v1.9.5"))
        assertFalse(AppUpdateManager.isNewerVersion("v1.6.1", "1.6.1"))
        assertFalse(AppUpdateManager.isNewerVersion("v1.6.1", "1.6.1-debug"))
        assertFalse(AppUpdateManager.isNewerVersion("v1.5.0", "2.0.0"))
    }

    @Test
    fun parseLatestReleaseJson_extractsReleaseMetadataAndApkUrl() {
        val sampleJson = """
            {
              "tag_name": "v2.0.0",
              "name": "ClusterLauncher v2.0.0",
              "html_url": "https://github.com/joaohouto/clusterlauncher/releases/tag/v2.0.0",
              "body": "### Novidades\\n- Player Integrado\\n- Auto Update",
              "assets": [
                {
                  "name": "ClusterLauncher-v2.0.0.apk",
                  "size": 45898240,
                  "browser_download_url": "https://github.com/joaohouto/clusterlauncher/releases/download/v2.0.0/ClusterLauncher-v2.0.0.apk"
                }
              ]
            }
        """.trimIndent()

        val release = AppUpdateManager.parseLatestReleaseJson(sampleJson)
        assertNotNull(release)
        assertEquals("v2.0.0", release!!.tagName)
        assertEquals("2.0.0", release.versionName)
        assertEquals("ClusterLauncher v2.0.0", release.title)
        assertEquals(
            "https://github.com/joaohouto/clusterlauncher/releases/download/v2.0.0/ClusterLauncher-v2.0.0.apk",
            release.apkDownloadUrl
        )
        assertEquals(45898240L, release.apkSizeBytes)
        assertTrue(release.changelog.contains("Player Integrado"))
    }

    @Test
    fun parseLatestReleaseJson_returnsNullWhenNoApkAsset() {
        val noApkJson = """
            {
              "tag_name": "v2.0.0",
              "name": "ClusterLauncher v2.0.0",
              "assets": []
            }
        """.trimIndent()

        assertNull(AppUpdateManager.parseLatestReleaseJson(noApkJson))
    }

    @Test
    fun storageScanner_supportsOpusExtension() {
        assertTrue(StorageScanner.SUPPORTED_EXTENSIONS.contains("opus"))
    }
}
