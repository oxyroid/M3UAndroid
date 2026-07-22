package com.m3u.data.service.internal

import androidx.test.ext.junit.runners.AndroidJUnit4
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProviderPlaybackHeadersTest {
    @Test
    fun sameCanonicalOriginReceivesProviderHeaders() {
        val request = request("https://media.example:443/live/segment.ts")

        val scoped = request.withProviderPlaybackHeaders(
            approvedUrl = "https://MEDIA.example/live/master.m3u8".toHttpUrl(),
            headers = PROVIDER_HEADERS,
        )

        assertEquals("Bearer provider-secret", scoped.header("Authorization"))
        assertEquals("provider-account", scoped.header("X-Provider-Account"))
        assertEquals("Provider/player", scoped.header("User-Agent"))
        assertEquals("bytes=1024-", scoped.header("Range"))
    }

    @Test
    fun crossHostDoesNotReceiveProviderHeaders() {
        assertProviderHeadersAbsent(
            request("https://cdn.example/live/segment.ts").withProviderPlaybackHeaders(
                approvedUrl = APPROVED_URL,
                headers = PROVIDER_HEADERS,
            )
        )
    }

    @Test
    fun crossSchemeDoesNotReceiveProviderHeaders() {
        assertProviderHeadersAbsent(
            request("http://media.example/live/segment.ts").withProviderPlaybackHeaders(
                approvedUrl = APPROVED_URL,
                headers = PROVIDER_HEADERS,
            )
        )
    }

    @Test
    fun crossPortDoesNotReceiveProviderHeaders() {
        assertProviderHeadersAbsent(
            request("https://media.example:8443/live/segment.ts").withProviderPlaybackHeaders(
                approvedUrl = APPROVED_URL,
                headers = PROVIDER_HEADERS,
            )
        )
    }

    private fun assertProviderHeadersAbsent(request: Request) {
        assertNull(request.header("Authorization"))
        assertNull(request.header("X-Provider-Account"))
        assertHostHeadersPreserved(request)
    }

    private fun assertHostHeadersPreserved(request: Request) {
        assertEquals("bytes=1024-", request.header("Range"))
        assertEquals("M3UAndroid/player", request.header("User-Agent"))
    }

    private fun request(url: String): Request = Request.Builder()
        .url(url)
        .header("Range", "bytes=1024-")
        .header("User-Agent", "M3UAndroid/player")
        .build()

    private companion object {
        val APPROVED_URL = "https://media.example/live/master.m3u8".toHttpUrl()
        val PROVIDER_HEADERS = mapOf(
            "Authorization" to "Bearer provider-secret",
            "X-Provider-Account" to "provider-account",
            "User-Agent" to "Provider/player",
        )
    }
}
