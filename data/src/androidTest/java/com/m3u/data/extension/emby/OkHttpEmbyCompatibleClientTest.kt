package com.m3u.data.extension.emby

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.m3u.extension.api.subscription.EmbyCompatibleProviderKinds
import com.m3u.extension.api.subscription.PlaybackPreferences
import com.m3u.extension.api.subscription.PlaybackReference
import com.m3u.extension.api.subscription.ValidatedProviderAccount
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.system.measureTimeMillis
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.Call
import okhttp3.EventListener
import okhttp3.Headers.Companion.headersOf
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OkHttpEmbyCompatibleClientTest {
    private val servers = mutableListOf<MockWebServer>()

    @After
    fun tearDown() {
        servers.forEach(MockWebServer::close)
    }

    @Test
    fun sameOriginRedirectIsFollowedWithoutDroppingAuthentication() = runBlocking {
        val server = server()
        server.enqueue(
            MockResponse(
                code = 302,
                headers = headersOf("Location", server.url("/redirected").toString()),
            )
        )
        server.enqueue(MockResponse(body = EMPTY_CHANNEL_RESPONSE))
        val client = client()

        client.refreshChannels(account(server), ACCESS_TOKEN)

        assertEquals(2, server.requestCount)
        val original = server.takeRequest()
        val redirected = server.takeRequest()
        assertEquals("/LiveTv/Channels", original.url.encodedPath)
        assertEquals(
            "UserId=user&StartIndex=0&Limit=500&EnableImages=true",
            original.url.encodedQuery,
        )
        assertEquals("/redirected", redirected.url.encodedPath)
        assertEquals(ACCESS_TOKEN, redirected.headers["X-Emby-Token"])
    }

    @Test
    fun crossOriginRedirectDoesNotReceiveEmbyToken() = runBlocking {
        val source = server()
        val target = server()
        source.enqueue(
            MockResponse(
                code = 302,
                headers = headersOf("Location", target.url("/capture").toString()),
            )
        )
        val client = client()

        val error = expectFailure<EmbyHttpException> {
            client.refreshChannels(account(source), ACCESS_TOKEN)
        }

        assertEquals(302, error.statusCode)
        assertEquals(ACCESS_TOKEN, source.takeRequest().headers["X-Emby-Token"])
        assertNull(target.takeRequest(250, TimeUnit.MILLISECONDS))
        assertEquals(0, target.requestCount)
    }

    @Test
    fun crossOriginPlaybackSourceDoesNotExportProviderAuthentication() = runBlocking {
        val source = server()
        val target = server()
        source.enqueue(
            MockResponse(
                body = """
                    {
                      "PlaySessionId":"play-session",
                      "MediaSources":[{
                        "Id":"media-source",
                        "DirectStreamUrl":"${target.url("/live.ts")}",
                        "RequiredHttpHeaders":{
                          "Authorization":"Bearer echoed-secret",
                          "X-Emby-Token":"echoed-secret",
                          "Cookie":"session=echoed-secret",
                          "User-Agent":"Provider playback"
                        }
                      }]
                    }
                """.trimIndent()
            )
        )
        val client = client()

        val playback = client.resolvePlayback(
            account = account(source),
            accessToken = ACCESS_TOKEN,
            reference = PlaybackReference(
                providerId = EmbyCompatibleProvider.ID,
                itemId = "channel",
                sourceType = "live_tv",
            ),
            preferences = PlaybackPreferences(),
        )

        val controlRequest = source.takeRequest()
        assertEquals(ACCESS_TOKEN, controlRequest.headers["X-Emby-Token"])
        assertEquals(target.url("/live.ts").toString(), playback.url)
        assertNull(playback.headers["Authorization"])
        assertNull(playback.headers["X-Emby-Token"])
        assertNull(playback.headers["Cookie"])
        assertEquals("Provider playback", playback.headers["User-Agent"])

        target.enqueue(MockResponse(body = "stream"))
        OkHttpClient.Builder()
            .build()
            .newCall(
                okhttp3.Request.Builder()
                    .url(playback.url)
                    .apply {
                        playback.headers.forEach { (name, value) -> header(name, value) }
                    }
                    .build()
            )
            .execute()
            .close()
        val playbackRequest = target.takeRequest()
        assertNull(playbackRequest.headers["Authorization"])
        assertNull(playbackRequest.headers["X-Emby-Token"])
        assertNull(playbackRequest.headers["Cookie"])
    }

    @Test
    fun invalidPlaybackSourceClosesSessionOpenedByPlaybackInfo() = runBlocking {
        val server = server()
        server.enqueue(
            MockResponse(
                body = """
                    {
                      "PlaySessionId":"play-session",
                      "MediaSources":[{
                        "Id":"media-source",
                        "LiveStreamId":"live-stream"
                      }]
                    }
                """.trimIndent()
            )
        )
        server.enqueue(MockResponse(code = 204))
        server.enqueue(MockResponse(code = 204))
        val client = client()

        val error = expectFailure<EmbyProtocolException> {
            client.resolvePlayback(
                account = account(server),
                accessToken = ACCESS_TOKEN,
                reference = PlaybackReference(
                    providerId = EmbyCompatibleProvider.ID,
                    itemId = "channel",
                    sourceType = "live_tv",
                ),
                preferences = PlaybackPreferences(),
            )
        }

        assertTrue(error.message.orEmpty().contains("usable URL"))
        assertEquals("/Items/channel/PlaybackInfo", server.takeRequest().url.encodedPath)
        val cleanupRequests = listOf(server.takeRequest(), server.takeRequest())
        val stopped = cleanupRequests.single { request ->
            request.url.encodedPath == "/Sessions/Playing/Stopped"
        }
        assertEquals("/Sessions/Playing/Stopped", stopped.url.encodedPath)
        val stoppedBody = stopped.body?.utf8().orEmpty()
        assertTrue(stoppedBody.contains("play-session"))
        assertTrue(stoppedBody.contains("live-stream"))
        val liveStreamClose = cleanupRequests.single { request ->
            request.url.encodedPath == "/LiveStreams/Close"
        }
        assertEquals("/LiveStreams/Close", liveStreamClose.url.encodedPath)
        assertEquals("live-stream", liveStreamClose.url.queryParameter("LiveStreamId"))
    }

    @Test
    fun closePlaybackAttemptsBothRequestsAndPreservesEveryFailure() = runBlocking {
        val server = server()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                when (request.url.encodedPath) {
                    "/Sessions/Playing/Stopped" -> MockResponse(code = 500)
                    "/LiveStreams/Close" -> MockResponse(code = 503)
                    else -> MockResponse(code = 404)
                }
        }
        val client = client()

        val error = expectFailure<EmbyHttpException> {
            client.closePlayback(
                account = account(server),
                accessToken = ACCESS_TOKEN,
                itemId = "channel",
                mediaSourceId = "media-source",
                session = EmbyPlaybackSession(
                    playSessionId = "play-session",
                    liveStreamId = "live-stream",
                ),
            )
        }

        assertEquals(500, error.statusCode)
        val additional = error.suppressed.single() as EmbyHttpException
        assertEquals(503, additional.statusCode)
        val paths = listOf(
            server.takeRequest().url.encodedPath,
            server.takeRequest().url.encodedPath,
        )
        assertEquals(
            setOf("/Sessions/Playing/Stopped", "/LiveStreams/Close"),
            paths.toSet(),
        )
    }

    @Test
    fun cancellationCancelsSlowResponseCall() = runBlocking {
        val callCancelled = CountDownLatch(1)
        val baseClient = OkHttpClient.Builder()
            .eventListener(
                object : EventListener() {
                    override fun canceled(call: Call) {
                        callCancelled.countDown()
                    }
                }
            )
            .build()
        val server = server()
        server.enqueue(
            MockResponse.Builder()
                .body(EMPTY_CHANNEL_RESPONSE)
                .throttleBody(1, 5, TimeUnit.SECONDS)
                .build()
        )
        val client = OkHttpEmbyCompatibleClient(
            okHttpClient = baseClient,
            controlCallTimeoutMillis = TimeUnit.SECONDS.toMillis(30),
        )
        val invocation = async(Dispatchers.IO) {
            client.refreshChannels(account(server), ACCESS_TOKEN)
        }
        assertNotNull(server.takeRequest(5, TimeUnit.SECONDS))

        val cancellationMillis = measureTimeMillis {
            invocation.cancelAndJoin()
        }

        assertTrue(callCancelled.await(2, TimeUnit.SECONDS))
        assertTrue("Cancellation took ${cancellationMillis}ms", cancellationMillis < 2_000)
    }

    @Test
    fun responseLargerThanHostLimitIsRejected() = runBlocking {
        val server = server()
        server.enqueue(
            MockResponse(
                body = "x".repeat(OkHttpEmbyCompatibleClient.MAX_JSON_RESPONSE_BYTES + 1)
            )
        )
        val client = client()

        val error = expectFailure<EmbyProtocolException> {
            client.refreshChannels(account(server), ACCESS_TOKEN)
        }

        assertTrue(error.message.orEmpty().contains("host limit"))
    }

    @Test
    fun refreshChannelsFetchesEveryReportedPage() = runBlocking {
        val server = server()
        server.enqueue(
            MockResponse(body = channelResponse(ids = listOf("one"), totalRecordCount = 2))
        )
        server.enqueue(
            MockResponse(body = channelResponse(ids = listOf("two"), totalRecordCount = 2))
        )
        val client = client()

        val refresh = client.refreshChannels(account(server), ACCESS_TOKEN)

        assertEquals(listOf("one", "two"), refresh.channels.map { channel -> channel.remoteId })
        assertEquals(2, refresh.totalRecordCount)
        assertEquals("0", server.takeRequest().url.queryParameter("StartIndex"))
        assertEquals("1", server.takeRequest().url.queryParameter("StartIndex"))
    }

    @Test
    fun refreshChannelsRejectsPageThatDoesNotAdvance() = runBlocking {
        val server = server()
        server.enqueue(
            MockResponse(body = channelResponse(ids = listOf("one"), totalRecordCount = 2))
        )
        server.enqueue(
            MockResponse(body = channelResponse(ids = listOf("one"), totalRecordCount = 2))
        )
        val client = client()

        val error = expectFailure<EmbyProtocolException> {
            client.refreshChannels(account(server), ACCESS_TOKEN)
        }

        assertTrue(error.message.orEmpty().contains("duplicate channel identifier"))
        assertEquals(2, server.requestCount)
    }

    @Test
    fun refreshChannelsRejectsTruncatedReportedResult() = runBlocking {
        val server = server()
        server.enqueue(
            MockResponse(body = channelResponse(ids = listOf("one"), totalRecordCount = 2))
        )
        server.enqueue(
            MockResponse(body = channelResponse(ids = emptyList(), totalRecordCount = 2))
        )
        val client = client()

        val error = expectFailure<EmbyProtocolException> {
            client.refreshChannels(account(server), ACCESS_TOKEN)
        }

        assertTrue(error.message.orEmpty().contains("ended before"))
    }

    @Test
    fun refreshChannelsRejectsCountAboveImporterLimit() = runBlocking {
        val server = server()
        server.enqueue(
            MockResponse(
                body = channelResponse(
                    ids = emptyList(),
                    totalRecordCount = 50_001,
                )
            )
        )
        val client = client()

        val error = expectFailure<EmbyProtocolException> {
            client.refreshChannels(account(server), ACCESS_TOKEN)
        }

        assertTrue(error.message.orEmpty().contains("host limit"))
        assertEquals(1, server.requestCount)
    }

    @Test
    fun refreshChannelsRejectsMalformedEntryInsteadOfReturningPartialSnapshot() = runBlocking {
        val server = server()
        server.enqueue(
            MockResponse(
                body = """
                    {
                      "Items":[
                        {"Id":"valid","Name":"Valid"},
                        {"Id":"invalid"}
                      ],
                      "TotalRecordCount":2
                    }
                """.trimIndent()
            )
        )
        val client = client()

        val error = expectFailure<EmbyProtocolException> {
            client.refreshChannels(account(server), ACCESS_TOKEN)
        }

        assertTrue(error.message.orEmpty().contains("did not contain a title"))
    }

    @Test
    fun refreshChannelsRejectsChangingReportedCount() = runBlocking {
        val server = server()
        server.enqueue(
            MockResponse(body = channelResponse(ids = listOf("one"), totalRecordCount = 2))
        )
        server.enqueue(
            MockResponse(body = channelResponse(ids = listOf("two"), totalRecordCount = 3))
        )
        val client = client()

        val error = expectFailure<EmbyProtocolException> {
            client.refreshChannels(account(server), ACCESS_TOKEN)
        }

        assertTrue(error.message.orEmpty().contains("changed during pagination"))
    }

    @Test
    fun slowResponseIsBoundedByTotalCallTimeout() = runBlocking {
        val server = server()
        server.enqueue(
            MockResponse.Builder()
                .body(EMPTY_CHANNEL_RESPONSE)
                .throttleBody(1, 5, TimeUnit.SECONDS)
                .build()
        )
        val client = OkHttpEmbyCompatibleClient(
            okHttpClient = OkHttpClient(),
            controlCallTimeoutMillis = 500,
        )

        val elapsedMillis = measureTimeMillis {
            expectFailure<java.io.InterruptedIOException> {
                client.refreshChannels(account(server), ACCESS_TOKEN)
            }
        }

        assertTrue("Call timeout took ${elapsedMillis}ms", elapsedMillis < 3_000)
    }

    private fun server(): MockWebServer = MockWebServer().also { server ->
        server.start()
        servers += server
    }

    private fun client() = OkHttpEmbyCompatibleClient(okHttpClient = OkHttpClient())

    private fun account(server: MockWebServer) = ValidatedProviderAccount(
        normalizedBaseUrl = server.url("/").toString().removeSuffix("/"),
        detectedKind = EmbyCompatibleProviderKinds.Emby,
        serverId = "server",
        serverName = "Server",
        serverVersion = "1",
        userId = "user",
        username = "user",
    )

    private fun channelResponse(
        ids: List<String>,
        totalRecordCount: Int?,
    ): String = buildString {
        append("""{"Items":[""")
        ids.forEachIndexed { index, id ->
            if (index > 0) append(',')
            append("""{"Id":"""")
            append(id)
            append("""","Name":"Channel """)
            append(id)
            append("\"}")
        }
        append(']')
        totalRecordCount?.let { count ->
            append(""","TotalRecordCount":""")
            append(count)
        }
        append('}')
    }

    private suspend inline fun <reified T : Throwable> expectFailure(
        crossinline block: suspend () -> Unit,
    ): T {
        try {
            block()
        } catch (error: Throwable) {
            if (error is T) return error
            throw AssertionError(
                "Expected ${T::class.java.simpleName}, got ${error.javaClass.simpleName}",
                error,
            )
        }
        throw AssertionError("Expected ${T::class.java.simpleName}")
    }

    private companion object {
        const val ACCESS_TOKEN = "provider-secret"
        const val EMPTY_CHANNEL_RESPONSE = """{"Items":[],"TotalRecordCount":0}"""
    }
}
