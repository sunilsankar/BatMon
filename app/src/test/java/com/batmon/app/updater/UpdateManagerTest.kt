package com.batmon.app.updater

import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class UpdateManagerTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun appVersion_parsesAndComparesCorrectly() {
        val current = AppVersion.parse("0.1.0-beta")
        val older = AppVersion.parse("v0.0.9")
        val same = AppVersion.parse("v0.1.0-beta")
        val finalRelease = AppVersion.parse("v0.1.0")
        val newerPatch = AppVersion.parse("v0.1.1")
        val newerMinor = AppVersion.parse("0.2.0")

        assertTrue(current > older)
        assertEquals(0, current.compareTo(same))
        assertTrue(finalRelease > current)
        assertTrue(newerPatch > current)
        assertTrue(newerMinor > current)
    }

    @Test
    fun checkForUpdate_detectsNewerTag() = runBlocking {
        val tagsJson = """
            [
              {
                "name": "v0.1.1",
                "commit": { "sha": "abcdef123456" }
              },
              {
                "name": "v0.1.0-beta",
                "commit": { "sha": "123456abcdef" }
              }
            ]
        """.trimIndent()

        server.enqueue(MockResponse().setResponseCode(200).setBody(tagsJson))

        val updateManager = UpdateManager(
            client = OkHttpClient(),
            apiBaseUrl = server.url("").toString().removeSuffix("/")
        )

        val result = updateManager.checkForUpdate(
            repoOwner = "sunilsankar",
            repoName = "BatMon",
            currentVersion = "0.1.0-beta"
        )

        assertTrue(result.isSuccess)
        val info = result.getOrNull()
        assertNotNull(info)
        assertEquals("v0.1.1", info!!.tagName)
        assertTrue(info.isNewer)
        assertTrue(info.downloadUrl.contains("batmon-v0.1.1.apk"))
    }

    @Test
    fun checkForUpdate_returnsNotNewerWhenSameVersion() = runBlocking {
        val tagsJson = """
            [
              {
                "name": "v0.1.0-beta",
                "commit": { "sha": "123456abcdef" }
              }
            ]
        """.trimIndent()

        server.enqueue(MockResponse().setResponseCode(200).setBody(tagsJson))

        val updateManager = UpdateManager(
            client = OkHttpClient(),
            apiBaseUrl = server.url("").toString().removeSuffix("/")
        )

        val result = updateManager.checkForUpdate(
            repoOwner = "sunilsankar",
            repoName = "BatMon",
            currentVersion = "0.1.0-beta"
        )

        assertTrue(result.isSuccess)
        val info = result.getOrNull()
        assertNotNull(info)
        assertFalse(info!!.isNewer)
    }
}
