package com.m3u.data.extension.emby

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.m3u.data.extension.artwork.PROVIDER_ARTWORK_TAG_QUERY
import com.m3u.extension.api.subscription.EmbyCompatibleProviderKinds
import com.m3u.extension.api.subscription.PlaybackMethods
import com.m3u.extension.api.subscription.PlaybackPreferences
import com.m3u.extension.api.subscription.PlaybackReference
import com.m3u.extension.api.subscription.PlaybackSessionEvents
import com.m3u.extension.api.subscription.ProviderMediaKinds
import com.m3u.extension.api.subscription.ValidatedProviderAccount
import java.net.InetAddress
import java.net.Proxy
import java.util.Collections
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
import mockwebserver3.SocketEffect
import okhttp3.Call
import okhttp3.Connection
import okhttp3.EventListener
import okhttp3.Headers.Companion.headersOf
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun explicitEmbySelectionAcceptsPublicInfoWithoutProductBrand() = runBlocking {
        val server = server()
        server.enqueue(
            MockResponse(
                body = """
                    {
                      "ServerName":"mebian",
                      "Version":"4.10.0.20",
                      "Id":"server"
                    }
                """.trimIndent()
            )
        )
        server.enqueue(
            MockResponse(
                body = """
                    {
                      "AccessToken":"access-token",
                      "ServerId":"server",
                      "User":{"Id":"user","Name":"viewer"}
                    }
                """.trimIndent()
            )
        )

        val validation = client().validate(
            baseUrl = server.testUrl("/").toString(),
            requestedKind = EmbyCompatibleProviderKinds.Emby,
            username = "viewer",
            password = "password",
        )

        assertEquals(EmbyCompatibleProviderKinds.Emby, validation.account.detectedKind)
        assertEquals("access-token", validation.accessToken)
        assertEquals("/System/Info/Public", server.takeRequest().url.encodedPath)
        val authentication = server.takeRequest()
        assertEquals("/Users/AuthenticateByName", authentication.url.encodedPath)
        assertTrue(authentication.headers["Authorization"].orEmpty().startsWith("Emby "))
    }

    @Test
    fun controlRequestsUseHttp11AcrossServerClosedConnections() = runBlocking {
        val serverCertificate = HeldCertificate.Builder()
            .commonName(LOOPBACK_HOST)
            .addSubjectAlternativeName(LOOPBACK_HOST)
            .build()
        val serverCertificates = HandshakeCertificates.Builder()
            .heldCertificate(serverCertificate)
            .build()
        val clientCertificates = HandshakeCertificates.Builder()
            .addTrustedCertificate(serverCertificate.certificate)
            .build()
        val server = MockWebServer().also { mockServer ->
            mockServer.useHttps(serverCertificates.sslSocketFactory())
            mockServer.protocols = listOf(Protocol.HTTP_2, Protocol.HTTP_1_1)
            mockServer.start(InetAddress.getByAddress(byteArrayOf(127, 0, 0, 1)), 0)
            servers += mockServer
        }
        server.enqueue(
            MockResponse.Builder()
                .body(
                    """
                        {
                          "ServerName":"mebian",
                          "Version":"4.10.0.20",
                          "Id":"server"
                        }
                    """.trimIndent()
                )
                .onResponseEnd(SocketEffect.CloseSocket())
                .build()
        )
        server.enqueue(
            MockResponse(
                body = """
                    {
                      "AccessToken":"access-token",
                      "ServerId":"server",
                      "User":{"Id":"user","Name":"viewer"}
                    }
                """.trimIndent()
            )
        )
        val negotiatedProtocols = Collections.synchronizedList(mutableListOf<Protocol>())
        val baseClient = OkHttpClient.Builder()
            .proxy(Proxy.NO_PROXY)
            .sslSocketFactory(
                clientCertificates.sslSocketFactory(),
                clientCertificates.trustManager,
            )
            .protocols(listOf(Protocol.HTTP_2, Protocol.HTTP_1_1))
            .eventListener(
                object : EventListener() {
                    override fun connectionAcquired(call: Call, connection: Connection) {
                        negotiatedProtocols += connection.protocol()
                    }
                }
            )
            .build()
        val client = OkHttpEmbyCompatibleClient(okHttpClient = baseClient)

        val validation = client.validate(
            baseUrl = server.testUrl("/").toString(),
            requestedKind = EmbyCompatibleProviderKinds.Emby,
            username = "viewer",
            password = "password",
        )

        assertEquals("access-token", validation.accessToken)
        assertEquals(
            setOf(Protocol.HTTP_1_1),
            negotiatedProtocols.toSet(),
        )
        assertEquals(2, server.requestCount)
    }

    @Test
    fun customServerNameIsNotUsedAsProviderBrandEvidence() = runBlocking {
        val server = server()
        server.enqueue(
            MockResponse(
                body = """
                    {
                      "ServerName":"Jellyfin Family Room",
                      "Version":"4.10.0.20",
                      "Id":"server"
                    }
                """.trimIndent()
            )
        )
        server.enqueue(
            MockResponse(
                body = """
                    {
                      "AccessToken":"access-token",
                      "ServerId":"server",
                      "User":{"Id":"user","Name":"viewer"}
                    }
                """.trimIndent()
            )
        )

        val validation = client().validate(
            baseUrl = server.testUrl("/").toString(),
            requestedKind = EmbyCompatibleProviderKinds.Emby,
            username = "viewer",
            password = "password",
        )

        assertEquals(EmbyCompatibleProviderKinds.Emby, validation.account.detectedKind)
        assertEquals("Jellyfin Family Room", validation.account.serverName)
    }

    @Test
    fun explicitProviderSelectionRejectsAnAdvertisedOppositeBrand() = runBlocking {
        val server = server()
        server.enqueue(
            MockResponse(
                body = """
                    {
                      "ProductName":"Jellyfin Server",
                      "ServerName":"Media",
                      "Version":"10.10.0",
                      "Id":"server"
                    }
                """.trimIndent()
            )
        )

        val failure = expectFailure<EmbyProtocolException> {
            client().validate(
                baseUrl = server.testUrl("/").toString(),
                requestedKind = EmbyCompatibleProviderKinds.Emby,
                username = "viewer",
                password = "password",
            )
        }

        assertTrue(failure.message.orEmpty().contains("advertised server kind"))
        assertEquals(1, server.requestCount)
    }

    @Test
    fun sameOriginRedirectIsFollowedWithoutDroppingAuthentication() = runBlocking {
        val server = server()
        server.enqueue(
            MockResponse(
                code = 302,
                headers = headersOf("Location", server.testUrl("/redirected").toString()),
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
                headers = headersOf("Location", target.testUrl("/capture").toString()),
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
                        "SupportsDirectStream":true,
                        "DirectStreamUrl":"${target.testUrl("/live.ts")}",
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
            preferences = PlaybackPreferences(startPositionTicks = 0L),
        )

        val controlRequest = source.takeRequest()
        assertEquals("POST", controlRequest.method)
        assertEquals(ACCESS_TOKEN, controlRequest.headers["X-Emby-Token"])
        val controlBody = controlRequest.body?.utf8().orEmpty()
        assertTrue(controlBody.contains(""""AutoOpenLiveStream":true"""))
        assertTrue(controlBody.contains(""""DeviceProfile""""))
        assertEquals(target.testUrl("/live.ts").toString(), playback.url)
        assertNull(playback.headers["Authorization"])
        assertNull(playback.headers["X-Emby-Token"])
        assertNull(playback.headers["Cookie"])
        assertEquals("Provider playback", playback.headers["User-Agent"])

        target.enqueue(MockResponse(body = "stream"))
        OkHttpClient.Builder()
            .proxy(Proxy.NO_PROXY)
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
    fun rootBrowseReturnsPagedMoviesAndSeriesWithPrimaryImageMetadata() = runBlocking {
        val server = server()
        server.enqueue(
            MockResponse(
                body = """
                    {
                      "Items":[
                        {
                          "Id":"movie-1",
                          "Name":"Movie One",
                          "Type":"Movie",
                          "ImageTags":{"Primary":"image-tag"},
                          "Genres":["Drama"],
                          "Overview":"Line one\r\nLine two\rLine three\nLine four",
                          "ProductionYear":2024
                        },
                        {
                          "Id":"series-1",
                          "Name":"Series One",
                          "Type":"Series",
                          "ImageTags":{"Primary":"series-image"}
                        }
                      ],
                      "TotalRecordCount":3
                    }
                """.trimIndent()
            )
        )
        val client = client()

        val page = client.browseContent(
            account = account(server),
            accessToken = ACCESS_TOKEN,
            parentReference = null,
            cursor = null,
            limit = 2,
        )

        assertEquals(3, page.total)
        assertEquals("2", page.nextCursor)
        assertEquals(
            listOf(ProviderMediaKinds.Movie, ProviderMediaKinds.Series),
            page.items.map { item -> item.mediaKind },
        )
        assertTrue(page.items[0].playable)
        assertFalse(page.items[0].browsable)
        assertFalse(page.items[1].playable)
        assertTrue(page.items[1].browsable)
        assertEquals("movie", page.items[0].reference.sourceType)
        assertEquals("series", page.items[1].reference.sourceType)
        assertEquals("Drama", page.items[0].category)
        assertEquals(
            "Line one\nLine two\nLine three\nLine four",
            page.items[0].overview,
        )
        assertEquals(2024, page.items[0].productionYear)
        assertEquals(
            server.testUrl("/Items/movie-1/Images/Primary")
                .newBuilder()
                .addQueryParameter(PROVIDER_ARTWORK_TAG_QUERY, "image-tag")
                .build()
                .toString(),
            page.items[0].imageUrl,
        )

        val request = server.takeRequest()
        assertEquals("/Users/user/Items", request.url.encodedPath)
        assertEquals("true", request.url.queryParameter("Recursive"))
        assertEquals("Movie,Series", request.url.queryParameter("IncludeItemTypes"))
        assertEquals("0", request.url.queryParameter("StartIndex"))
        assertEquals("2", request.url.queryParameter("Limit"))
        assertTrue(request.url.queryParameter("Fields").orEmpty().contains("Overview"))
        assertEquals(ACCESS_TOKEN, request.headers["X-Emby-Token"])
    }

    @Test
    fun seriesBrowseUsesEpisodesEndpointAndPreservesEpisodeNumbers() = runBlocking {
        val server = server()
        server.enqueue(
            MockResponse(
                body = """
                    {
                      "Items":[{
                        "Id":"episode-2",
                        "Name":"The Second Episode",
                        "Type":"Episode",
                        "SeriesName":"Series One",
                        "ParentIndexNumber":1,
                        "IndexNumber":2,
                        "PrimaryImageTag":"legacy-image-tag"
                      }],
                      "TotalRecordCount":3
                    }
                """.trimIndent()
            )
        )
        val client = client()

        val page = client.browseContent(
            account = account(server),
            accessToken = ACCESS_TOKEN,
            parentReference = PlaybackReference(
                providerId = EmbyCompatibleProvider.ID,
                itemId = "series-1",
                sourceType = "series",
            ),
            cursor = "1",
            limit = 1,
        )

        assertEquals("2", page.nextCursor)
        val episode = page.items.single()
        assertEquals(ProviderMediaKinds.Episode, episode.mediaKind)
        assertEquals("episode", episode.reference.sourceType)
        assertEquals("Series One", episode.subtitle)
        assertEquals(1, episode.seasonNumber)
        assertEquals(2, episode.episodeNumber)
        assertTrue(episode.playable)
        assertFalse(episode.browsable)
        assertEquals(
            server.testUrl("/Items/episode-2/Images/Primary")
                .newBuilder()
                .addQueryParameter(
                    PROVIDER_ARTWORK_TAG_QUERY,
                    "legacy-image-tag",
                )
                .build()
                .toString(),
            episode.imageUrl,
        )

        val request = server.takeRequest()
        assertEquals("/Shows/series-1/Episodes", request.url.encodedPath)
        assertEquals("user", request.url.queryParameter("UserId"))
        assertEquals("1", request.url.queryParameter("StartIndex"))
        assertEquals("1", request.url.queryParameter("Limit"))
        assertNull(request.url.queryParameter("Recursive"))
        assertNull(request.url.queryParameter("SortBy"))
        assertNull(request.url.queryParameter("SortOrder"))
    }

    @Test
    fun browseRejectsDuplicateItemsAndTruncatedReportedPages() = runBlocking {
        val duplicateServer = server()
        duplicateServer.enqueue(
            MockResponse(
                body = """
                    {
                      "Items":[
                        {"Id":"same","Name":"One","Type":"Movie"},
                        {"Id":"same","Name":"Two","Type":"Movie"}
                      ],
                      "TotalRecordCount":2
                    }
                """.trimIndent()
            )
        )
        val client = client()

        val duplicate = expectFailure<EmbyProtocolException> {
            client.browseContent(
                account = account(duplicateServer),
                accessToken = ACCESS_TOKEN,
                parentReference = null,
                cursor = null,
                limit = 2,
            )
        }
        assertTrue(duplicate.message.orEmpty().contains("duplicate media identifier"))

        val truncatedServer = server()
        truncatedServer.enqueue(
            MockResponse(body = """{"Items":[],"TotalRecordCount":2}""")
        )
        val truncated = expectFailure<EmbyProtocolException> {
            client.browseContent(
                account = account(truncatedServer),
                accessToken = ACCESS_TOKEN,
                parentReference = null,
                cursor = "1",
                limit = 1,
            )
        }
        assertTrue(truncated.message.orEmpty().contains("ended before"))
    }

    @Test
    fun browseRejectsInvalidCursorUnexpectedTypeAndOversizedCount() = runBlocking {
        val client = client()
        val invalidCursor = expectFailure<EmbyProtocolException> {
            client.browseContent(
                account = account(server()),
                accessToken = ACCESS_TOKEN,
                parentReference = null,
                cursor = "01",
                limit = 1,
            )
        }
        assertTrue(invalidCursor.message.orEmpty().contains("cursor"))

        val typeServer = server()
        typeServer.enqueue(
            MockResponse(
                body = """
                    {
                      "Items":[{"Id":"episode","Name":"Episode","Type":"Episode"}],
                      "TotalRecordCount":1
                    }
                """.trimIndent()
            )
        )
        val unexpectedType = expectFailure<EmbyProtocolException> {
            client.browseContent(
                account = account(typeServer),
                accessToken = ACCESS_TOKEN,
                parentReference = null,
                cursor = null,
                limit = 1,
            )
        }
        assertTrue(unexpectedType.message.orEmpty().contains("unsupported root media type"))

        val countServer = server()
        countServer.enqueue(
            MockResponse(body = """{"Items":[],"TotalRecordCount":50001}""")
        )
        val oversizedCount = expectFailure<EmbyProtocolException> {
            client.browseContent(
                account = account(countServer),
                accessToken = ACCESS_TOKEN,
                parentReference = null,
                cursor = null,
                limit = 1,
            )
        }
        assertTrue(oversizedCount.message.orEmpty().contains("host limit"))
    }

    @Test
    fun vodPlaybackUsesProfileAndConstructsSafeDirectPlayUrl() = runBlocking {
        val server = server()
        server.enqueue(
            MockResponse(
                body = """
                    {
                      "PlaySessionId":"play/session",
                      "MediaSources":[{
                        "Id":"source&one",
                        "Path":"C:\\Media\\movie.mkv",
                        "SupportsDirectPlay":true,
                        "SupportsDirectStream":false
                      }]
                    }
                """.trimIndent()
            )
        )
        val client = client()

        val playback = client.resolvePlayback(
            account = account(server),
            accessToken = ACCESS_TOKEN,
            reference = PlaybackReference(
                providerId = EmbyCompatibleProvider.ID,
                itemId = "movie/one",
                sourceType = "movie",
            ),
            preferences = PlaybackPreferences(
                maxStreamingBitrate = 8_000_000,
                allowTranscoding = false,
                startPositionTicks = 123_000_000L,
            ),
        )

        assertEquals(PlaybackMethods.DirectPlay, playback.playMethod)
        val playbackUrl = playback.url.toHttpUrl()
        assertEquals("/Videos/movie%2Fone/stream", playbackUrl.encodedPath)
        assertEquals("true", playbackUrl.queryParameter("static"))
        assertEquals("source&one", playbackUrl.queryParameter("MediaSourceId"))
        assertEquals("play/session", playbackUrl.queryParameter("PlaySessionId"))
        assertFalse(playback.url.contains("C:"))
        assertEquals(ACCESS_TOKEN, playback.headers["X-Emby-Token"])

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/Items/movie%2Fone/PlaybackInfo", request.url.encodedPath)
        assertEquals(ACCESS_TOKEN, request.headers["X-Emby-Token"])
        assertTrue(request.headers["Content-Type"].orEmpty().startsWith("application/json"))
        val body = request.body?.utf8().orEmpty()
        assertTrue(body.contains(""""EnableDirectPlay":true"""))
        assertTrue(body.contains(""""EnableDirectStream":true"""))
        assertTrue(body.contains(""""EnableTranscoding":false"""))
        assertTrue(body.contains(""""MaxStreamingBitrate":8000000"""))
        assertTrue(body.contains(""""StartTimeTicks":123000000"""))
        assertTrue(body.contains(""""AutoOpenLiveStream":false"""))
        assertTrue(body.contains(""""AllowVideoStreamCopy":true"""))
        assertTrue(body.contains(""""AllowAudioStreamCopy":true"""))
        assertTrue(body.contains(""""Name":"M3UAndroid Media3""""))
        assertTrue(body.contains(""""VideoCodec":"h264""""))
        assertTrue(body.contains(""""Protocol":"hls""""))
        assertTrue(body.contains(""""Value":"1920""""))
        assertNull(request.url.query)
    }

    @Test
    fun incompatibleVodUsesServerTranscodingUrlWhenAllowed() = runBlocking {
        val server = server()
        server.enqueue(
            MockResponse(
                body = """
                    {
                      "PlaySessionId":"play-session",
                      "MediaSources":[{
                        "Id":"media-source",
                        "SupportsDirectPlay":false,
                        "SupportsDirectStream":false,
                        "SupportsTranscoding":true,
                        "TranscodingUrl":"/videos/movie/master.m3u8?PlaySessionId=play-session"
                      }]
                    }
                """.trimIndent()
            )
        )

        val account = account(server).copy(
            normalizedBaseUrl = server.testUrl("/jellyfin").toString().removeSuffix("/"),
        )
        val playback = client().resolvePlayback(
            account = account,
            accessToken = ACCESS_TOKEN,
            reference = PlaybackReference(
                providerId = EmbyCompatibleProvider.ID,
                itemId = "movie",
                sourceType = "movie",
            ),
            preferences = PlaybackPreferences(
                maxStreamingBitrate = 5_000_000,
                allowTranscoding = true,
                startPositionTicks = 0L,
            ),
        )

        assertEquals(PlaybackMethods.Transcode, playback.playMethod)
        assertEquals(
            server.testUrl(
                "/jellyfin/videos/movie/master.m3u8?PlaySessionId=play-session"
            ).toString(),
            playback.url,
        )
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        val body = request.body?.utf8().orEmpty()
        assertTrue(body.contains(""""EnableTranscoding":true"""))
        assertTrue(body.contains(""""MaxStreamingBitrate":5000000"""))
        assertTrue(body.contains(""""TranscodingProfiles""""))
    }

    @Test
    fun providerPlaybackUrlAlreadyContainingBasePathIsNotPrefixedTwice() = runBlocking {
        val server = server()
        server.enqueue(
            MockResponse(
                body = """
                    {
                      "PlaySessionId":"play-session",
                      "MediaSources":[{
                        "Id":"media-source",
                        "SupportsDirectStream":true,
                        "DirectStreamUrl":"/jellyfin/videos/movie/stream"
                      }]
                    }
                """.trimIndent()
            )
        )
        val account = account(server).copy(
            normalizedBaseUrl = server.testUrl("/jellyfin").toString().removeSuffix("/"),
        )

        val playback = client().resolvePlayback(
            account = account,
            accessToken = ACCESS_TOKEN,
            reference = PlaybackReference(
                providerId = EmbyCompatibleProvider.ID,
                itemId = "movie",
                sourceType = "movie",
            ),
            preferences = PlaybackPreferences(startPositionTicks = 0L),
        )

        assertEquals(
            server.testUrl("/jellyfin/videos/movie/stream").toString(),
            playback.url,
        )
        assertEquals(PlaybackMethods.DirectStream, playback.playMethod)
    }

    @Test
    fun directStreamCanUseRemuxUrlWhenTranscodingIsDisabled() = runBlocking {
        val server = server()
        server.enqueue(
            MockResponse(
                body = """
                    {
                      "PlaySessionId":"play-session",
                      "MediaSources":[{
                        "Id":"media-source",
                        "SupportsDirectPlay":false,
                        "SupportsDirectStream":true,
                        "SupportsTranscoding":true,
                        "TranscodingUrl":"/videos/movie/remux.m3u8"
                      }]
                    }
                """.trimIndent()
            )
        )

        val playback = client().resolvePlayback(
            account = account(server),
            accessToken = ACCESS_TOKEN,
            reference = PlaybackReference(
                providerId = EmbyCompatibleProvider.ID,
                itemId = "movie",
                sourceType = "movie",
            ),
            preferences = PlaybackPreferences(
                allowTranscoding = false,
                startPositionTicks = 0L,
            ),
        )

        assertEquals(PlaybackMethods.DirectStream, playback.playMethod)
        assertEquals(
            server.testUrl("/videos/movie/remux.m3u8").toString(),
            playback.url,
        )
    }

    @Test
    fun playbackUpdatesAndStopCarryPositionAndMappedLifecycleFields() = runBlocking {
        val server = server()
        repeat(3) {
            server.enqueue(MockResponse(code = 204))
        }
        val client = client()
        val reference = PlaybackReference(
            providerId = EmbyCompatibleProvider.ID,
            itemId = "movie",
            mediaSourceId = "source",
            sourceType = "movie",
        )
        val session = EmbyPlaybackSession(
            playSessionId = "play-session",
            liveStreamId = null,
        )

        assertTrue(
            client.updatePlayback(
                account = account(server),
                accessToken = ACCESS_TOKEN,
                reference = reference,
                mediaSourceId = "source",
                session = session,
                event = PlaybackSessionEvents.Started,
                positionTicks = 10_000_000L,
                playMethod = PlaybackMethods.DirectPlay,
                isPaused = false,
            )
        )
        assertTrue(
            client.updatePlayback(
                account = account(server),
                accessToken = ACCESS_TOKEN,
                reference = reference,
                mediaSourceId = "source",
                session = session,
                event = PlaybackSessionEvents.Paused,
                positionTicks = 20_000_000L,
                playMethod = PlaybackMethods.Transcode,
                isPaused = true,
            )
        )
        assertTrue(
            client.closePlayback(
                account = account(server),
                accessToken = ACCESS_TOKEN,
                itemId = reference.itemId,
                mediaSourceId = reference.mediaSourceId,
                session = session,
                positionTicks = 30_000_000L,
            )
        )

        val started = server.takeRequest()
        assertEquals("/Sessions/Playing", started.url.encodedPath)
        val startedBody = started.body?.utf8().orEmpty()
        assertTrue(startedBody.contains(""""PlayMethod":"DirectPlay""""))
        assertFalse(startedBody.contains("EventName"))

        val paused = server.takeRequest()
        assertEquals("/Sessions/Playing/Progress", paused.url.encodedPath)
        val pausedBody = paused.body?.utf8().orEmpty()
        assertTrue(pausedBody.contains(""""PositionTicks":20000000"""))
        assertTrue(pausedBody.contains(""""IsPaused":true"""))
        assertTrue(pausedBody.contains(""""PlayMethod":"Transcode""""))
        assertTrue(pausedBody.contains(""""EventName":"pause""""))

        val stopped = server.takeRequest()
        assertEquals("/Sessions/Playing/Stopped", stopped.url.encodedPath)
        assertTrue(stopped.body?.utf8().orEmpty().contains(""""PositionTicks":30000000"""))
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
                preferences = PlaybackPreferences(startPositionTicks = 0L),
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
                positionTicks = 0L,
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
            .proxy(Proxy.NO_PROXY)
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
            okHttpClient = testHttpClient(),
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
        server.start(InetAddress.getByAddress(byteArrayOf(127, 0, 0, 1)), 0)
        servers += server
    }

    private fun client() = OkHttpEmbyCompatibleClient(okHttpClient = testHttpClient())

    private fun testHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .proxy(Proxy.NO_PROXY)
        .build()

    private fun MockWebServer.testUrl(path: String) = url(path)
        .newBuilder()
        .host(LOOPBACK_HOST)
        .build()

    private fun account(server: MockWebServer) = ValidatedProviderAccount(
        normalizedBaseUrl = server.testUrl("/").toString().removeSuffix("/"),
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
        const val LOOPBACK_HOST = "127.0.0.1"
    }
}
