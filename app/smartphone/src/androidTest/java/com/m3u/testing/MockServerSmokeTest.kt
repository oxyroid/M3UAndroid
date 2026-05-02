package com.m3u.testing

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.HttpURLConnection
import java.net.URL

class MockServerSmokeTest {
    private val baseUrl: String
        get() = InstrumentationRegistry
            .getArguments()
            .getString("m3uMockServerUrl", "http://10.0.2.2:8080")
            .trimEnd('/')

    @Test
    fun mockServerServesM3uAndXtreamFixtures() {
        assertTrue(httpGet("$baseUrl/playlist/live.m3u").contains("#EXTM3U"))
        assertTrue(
            httpGet("$baseUrl/player_api.php?username=m3u&password=m3u")
                .contains("\"user_info\"")
        )
        assertTrue(
            httpGet("$baseUrl/player_api.php?username=m3u&password=m3u&action=get_live_streams")
                .contains("Mock News")
        )
    }

    private fun httpGet(url: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 5_000
        connection.readTimeout = 5_000
        return try {
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }
}
