package com.m3u.data.service.internal

import androidx.test.ext.junit.runners.AndroidJUnit4
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.Headers.Companion.headersOf
import okhttp3.Call
import okhttp3.EventListener
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProviderPlaybackHeadersTest {
    private val servers = mutableListOf<MockWebServer>()

    @After
    fun tearDown() {
        servers.forEach(MockWebServer::close)
    }

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

    @Test
    fun crossOriginRedirectRemovesHeadersAddedOnTheApprovedOrigin() {
        val source = server()
        val target = server()
        source.enqueue(
            MockResponse(
                code = 302,
                headers = headersOf("Location", target.url("/segment.ts").toString()),
            )
        )
        target.enqueue(MockResponse(body = "segment"))
        val client = OkHttpClient().withProviderPlaybackHeaders(
            entryUrl = source.url("/master.m3u8").toString(),
            headers = PROVIDER_HEADERS,
            allowCrossOriginRequests = true,
        )

        client.newCall(request(source.url("/master.m3u8").toString())).execute().use { response ->
            assertEquals(200, response.code)
        }

        val sourceRequest = source.takeRequest()
        val targetRequest = target.takeRequest(5, TimeUnit.SECONDS)
            ?: error("Redirect target did not receive the request")
        assertEquals("Bearer provider-secret", sourceRequest.headers["Authorization"])
        assertEquals("provider-account", sourceRequest.headers["X-Provider-Account"])
        assertEquals("Provider/player", sourceRequest.headers["User-Agent"])
        assertNull(targetRequest.headers["Authorization"])
        assertNull(targetRequest.headers["X-Provider-Account"])
        assertEquals("M3UAndroid/player", targetRequest.headers["User-Agent"])
        assertEquals("bytes=1024-", targetRequest.headers["Range"])
    }

    @Test
    fun strictExternalPolicyRejectsCrossOriginRedirectBeforeSecondRequest() {
        val source = server()
        val target = server()
        source.enqueue(
            MockResponse(
                code = 302,
                headers = headersOf("Location", target.url("/segment.ts").toString()),
            )
        )
        val client = OkHttpClient().withProviderPlaybackHeaders(
            entryUrl = source.url("/master.m3u8").toString(),
            headers = emptyMap(),
            allowCrossOriginRequests = false,
        )

        runCatching {
            client.newCall(request(source.url("/master.m3u8").toString())).execute().close()
        }.onSuccess {
            error("Cross-origin redirect unexpectedly succeeded")
        }

        assertEquals("/master.m3u8", source.takeRequest().url.encodedPath)
        assertNull(target.takeRequest(250, TimeUnit.MILLISECONDS))
    }

    @Test
    fun strictExternalPolicyRejectsAbsoluteCrossOriginSegment() {
        val approved = server()
        val target = server()
        var targetConnectionStarted = false
        val client = OkHttpClient.Builder()
            .eventListener(
                object : EventListener() {
                    override fun connectStart(
                        call: Call,
                        inetSocketAddress: InetSocketAddress,
                        proxy: Proxy,
                    ) {
                        if (inetSocketAddress.port == target.port) {
                            targetConnectionStarted = true
                        }
                    }
                }
            )
            .build()
            .withProviderPlaybackHeaders(
            entryUrl = approved.url("/master.m3u8").toString(),
            headers = emptyMap(),
            allowCrossOriginRequests = false,
        )

        runCatching {
            client.newCall(request(target.url("/segment.ts").toString())).execute().close()
        }.onSuccess {
            error("Cross-origin segment unexpectedly succeeded")
        }

        assertNull(target.takeRequest(250, TimeUnit.MILLISECONDS))
        assertFalse(targetConnectionStarted)
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

    private fun server(): MockWebServer = MockWebServer().also { server ->
        server.start()
        servers += server
    }

    private companion object {
        val APPROVED_URL = "https://media.example/live/master.m3u8".toHttpUrl()
        val PROVIDER_HEADERS = mapOf(
            "Authorization" to "Bearer provider-secret",
            "X-Provider-Account" to "provider-account",
            "User-Agent" to "Provider/player",
        )
    }
}
