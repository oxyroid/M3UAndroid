package com.m3u.testing.mockserver

import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import java.net.HttpURLConnection
import java.net.ServerSocket
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class EmbyVodEndpointTest {
    @Test
    fun `Emby VOD fixture browses resolves and records playback lifecycle`() {
        val port = ServerSocket(0).use { socket -> socket.localPort }
        val server = embeddedServer(
            factory = Netty,
            host = "127.0.0.1",
            port = port,
            module = io.ktor.server.application.Application::mockServerModule,
        ).start(wait = false)
        val baseUrl = "http://127.0.0.1:$port"
        try {
            val rootPageQuery = embyRootBrowseQuery(startIndex = 0, limit = 2)
            assertEquals(
                401,
                request("$baseUrl/Users/mock-user-id/Items?$rootPageQuery").statusCode,
            )

            val login = request(
                url = "$baseUrl/Users/AuthenticateByName",
                method = "POST",
                headers = mapOf(
                    "Authorization" to
                        """Emby Client="Fixture", Device="JVM", DeviceId="fixture", Version="1"""",
                    "Content-Type" to "application/json",
                ),
                body = """{"Username":"m3u","Pw":"m3u"}""",
            )
            assertEquals(200, login.statusCode)
            val loginBody = login.jsonBody()
            val token = requireNotNull(loginBody["AccessToken"]?.jsonPrimitive?.content)
            val userId = requireNotNull(
                loginBody["User"]?.jsonObject?.get("Id")?.jsonPrimitive?.content,
            )
            val authorization = mapOf(
                "Authorization" to
                    """Emby UserId="$userId", Client="Fixture", Device="JVM", DeviceId="fixture", Version="1"""",
                "X-Emby-Token" to token,
            )

            assertEquals(
                401,
                request(
                    "$baseUrl/Users/wrong-user/Items?$rootPageQuery",
                    headers = authorization,
                ).statusCode,
            )
            assertEquals(
                400,
                request(
                    "$baseUrl/Users/$userId/Items?" +
                        embyRootBrowseQuery(startIndex = 0, limit = 0),
                    headers = authorization,
                ).statusCode,
            )

            val rootPageOne = request(
                "$baseUrl/Users/$userId/Items?$rootPageQuery",
                headers = authorization,
            )
            assertEquals(200, rootPageOne.statusCode)
            val rootPageOneBody = rootPageOne.jsonBody()
            assertEquals(
                3,
                rootPageOneBody["TotalRecordCount"]?.jsonPrimitive?.content?.toInt(),
            )
            val rootPageOneItems = requireNotNull(rootPageOneBody["Items"]?.jsonArray)
            assertEquals(2, rootPageOneItems.size)
            assertEquals(
                listOf("Movie", "Series"),
                rootPageOneItems.map { item ->
                    item.jsonObject["Type"]?.jsonPrimitive?.content
                },
            )
            val seriesId = requireNotNull(
                rootPageOneItems
                    .first { item ->
                        item.jsonObject["Type"]?.jsonPrimitive?.content == "Series"
                    }
                    .jsonObject["Id"]
                    ?.jsonPrimitive
                    ?.content,
            )

            val rootPageTwo = request(
                "$baseUrl/Users/$userId/Items?" +
                    embyRootBrowseQuery(startIndex = 2, limit = 2),
                headers = authorization,
            )
            assertEquals(200, rootPageTwo.statusCode)
            assertEquals(1, rootPageTwo.jsonBody()["Items"]?.jsonArray?.size)

            val episodePageOne = request(
                "$baseUrl/Shows/$seriesId/Episodes?" +
                    embyEpisodeBrowseQuery(userId = userId, startIndex = 0, limit = 2),
                headers = authorization,
            )
            assertEquals(200, episodePageOne.statusCode)
            val episodePageOneBody = episodePageOne.jsonBody()
            assertEquals(
                3,
                episodePageOneBody["TotalRecordCount"]?.jsonPrimitive?.content?.toInt(),
            )
            val episodePageOneItems = requireNotNull(episodePageOneBody["Items"]?.jsonArray)
            assertEquals(2, episodePageOneItems.size)
            assertTrue(
                episodePageOneItems.all { item ->
                    item.jsonObject["Type"]?.jsonPrimitive?.content == "Episode"
                },
            )
            val episodeId = requireNotNull(
                episodePageOneItems.first().jsonObject["Id"]?.jsonPrimitive?.content,
            )
            val episodePageTwo = request(
                "$baseUrl/Shows/$seriesId/Episodes?" +
                    embyEpisodeBrowseQuery(userId = userId, startIndex = 2, limit = 2),
                headers = authorization,
            )
            assertEquals(200, episodePageTwo.statusCode)
            assertEquals(1, episodePageTwo.jsonBody()["Items"]?.jsonArray?.size)

            assertEquals(
                200,
                request(
                    url = "$baseUrl/Items/mock.news/PlaybackInfo",
                    method = "POST",
                    headers = authorization + ("Content-Type" to "application/json"),
                    body = embyPlaybackInfoBody(
                        userId = userId,
                        autoOpenLiveStream = true,
                    ),
                ).statusCode,
            )
            assertEquals(
                400,
                request(
                    url = "$baseUrl/Items/$episodeId/PlaybackInfo",
                    method = "POST",
                    headers = authorization + ("Content-Type" to "application/json"),
                    body = embyPlaybackInfoBody(
                        userId = userId,
                        autoOpenLiveStream = false,
                        enableDirectStream = false,
                    ),
                ).statusCode,
            )
            val playback = request(
                url = "$baseUrl/Items/$episodeId/PlaybackInfo",
                method = "POST",
                headers = authorization + ("Content-Type" to "application/json"),
                body = embyPlaybackInfoBody(
                    userId = userId,
                    autoOpenLiveStream = false,
                ),
            )
            assertEquals(200, playback.statusCode)
            val playbackBody = playback.jsonBody()
            val playSessionId = requireNotNull(
                playbackBody["PlaySessionId"]?.jsonPrimitive?.content,
            )
            val mediaSourceId = requireNotNull(
                playbackBody["MediaSources"]
                    ?.jsonArray
                    ?.single()
                    ?.jsonObject
                    ?.get("Id")
                    ?.jsonPrimitive
                    ?.content,
            )

            val resolvedState = request(
                "$baseUrl/mock/emby/sessions/$playSessionId",
                headers = authorization,
            ).jsonBody()
            assertEquals("resolved", resolvedState.string("state"))
            assertEquals(0, resolvedState.int("start_count"))

            val streamUrl = "$baseUrl/Videos/$episodeId/stream?" + query(
                "static" to "true",
                "MediaSourceId" to mediaSourceId,
                "PlaySessionId" to playSessionId,
            )
            assertEquals(401, request(streamUrl).statusCode)
            val stream = request(streamUrl, headers = authorization)
            assertEquals(200, stream.statusCode)
            assertEquals("audio/wav", stream.contentType)
            assertEquals("RIFF", stream.body.copyOfRange(0, 4).decodeToString())

            val start = request(
                url = "$baseUrl/Sessions/Playing",
                method = "POST",
                headers = authorization + ("Content-Type" to "application/json"),
                body = playbackUpdateBody(
                    itemId = episodeId,
                    mediaSourceId = mediaSourceId,
                    playSessionId = playSessionId,
                    positionTicks = 0,
                ),
            )
            assertEquals(204, start.statusCode)

            val progress = request(
                url = "$baseUrl/Sessions/Playing/Progress",
                method = "POST",
                headers = authorization + ("Content-Type" to "application/json"),
                body = playbackUpdateBody(
                    itemId = episodeId,
                    mediaSourceId = mediaSourceId,
                    playSessionId = playSessionId,
                    positionTicks = 12_000_000,
                    eventName = "timeupdate",
                ),
            )
            assertEquals(204, progress.statusCode)
            val backwardSeekProgress = request(
                url = "$baseUrl/Sessions/Playing/Progress",
                method = "POST",
                headers = authorization + ("Content-Type" to "application/json"),
                body = playbackUpdateBody(
                    itemId = episodeId,
                    mediaSourceId = mediaSourceId,
                    playSessionId = playSessionId,
                    positionTicks = 4_000_000,
                    eventName = "timeupdate",
                ),
            )
            assertEquals(204, backwardSeekProgress.statusCode)
            val progressState = request(
                "$baseUrl/mock/emby/sessions/$playSessionId",
                headers = authorization,
            ).jsonBody()
            assertEquals("playing", progressState.string("state"))
            assertEquals(1, progressState.int("start_count"))
            assertEquals(2, progressState.int("progress_count"))
            assertEquals(4_000_000L, progressState.long("last_position_ticks"))

            val stop = request(
                url = "$baseUrl/Sessions/Playing/Stopped",
                method = "POST",
                headers = authorization + ("Content-Type" to "application/json"),
                body = """
                    {
                      "ItemId":"$episodeId",
                      "MediaSourceId":"$mediaSourceId",
                      "PlaySessionId":"$playSessionId",
                      "PositionTicks":3000000
                    }
                """.trimIndent(),
            )
            assertEquals(204, stop.statusCode)

            val stoppedState = request(
                "$baseUrl/mock/emby/sessions/$playSessionId",
                headers = authorization,
            ).jsonBody()
            assertEquals("stopped", stoppedState.string("state"))
            assertEquals(1, stoppedState.int("start_count"))
            assertEquals(2, stoppedState.int("progress_count"))
            assertEquals(1, stoppedState.int("stop_count"))
            assertEquals(3_000_000L, stoppedState.long("last_position_ticks"))
            assertEquals("DirectStream", stoppedState.string("play_method"))
            assertEquals("timeupdate", stoppedState.string("last_event_name"))
        } finally {
            server.stop(gracePeriodMillis = 100, timeoutMillis = 1_000)
        }
    }

    private fun embyRootBrowseQuery(startIndex: Int, limit: Int): String = query(
        "Recursive" to "true",
        "IncludeItemTypes" to "Movie,Series",
        "StartIndex" to startIndex.toString(),
        "Limit" to limit.toString(),
        "EnableImages" to "true",
        "EnableImageTypes" to "Primary",
        "Fields" to
            "Overview,Genres,ProductionYear,SeriesName,ParentIndexNumber,IndexNumber",
        "SortBy" to "SortName,ProductionYear",
        "SortOrder" to "Ascending",
    )

    private fun embyEpisodeBrowseQuery(
        userId: String,
        startIndex: Int,
        limit: Int,
    ): String = query(
        "UserId" to userId,
        "StartIndex" to startIndex.toString(),
        "Limit" to limit.toString(),
        "EnableImages" to "true",
        "EnableImageTypes" to "Primary",
        "Fields" to
            "Overview,Genres,ProductionYear,SeriesName,ParentIndexNumber,IndexNumber",
    )

    private fun embyPlaybackInfoBody(
        userId: String,
        autoOpenLiveStream: Boolean,
        enableDirectStream: Boolean = true,
    ): String = """
        {
          "UserId":"$userId",
          "StartTimeTicks":0,
          "IsPlayback":true,
          "AutoOpenLiveStream":$autoOpenLiveStream,
          "EnableDirectPlay":true,
          "EnableDirectStream":$enableDirectStream,
          "EnableTranscoding":true,
          "AllowVideoStreamCopy":true,
          "AllowAudioStreamCopy":true,
          "DeviceProfile":{
            "Name":"M3UAndroid Media3",
            "SupportedMediaTypes":"Video",
            "DirectPlayProfiles":[{
              "Container":"mp4,m4v,mov,mkv,ts,mpegts",
              "AudioCodec":"aac,mp3",
              "VideoCodec":"h264",
              "Type":"Video"
            }],
            "TranscodingProfiles":[{
              "Container":"ts",
              "Type":"Video",
              "VideoCodec":"h264",
              "AudioCodec":"aac",
              "Protocol":"hls",
              "Context":"Streaming",
              "CopyTimestamps":false,
              "MaxAudioChannels":"2",
              "MinSegments":2,
              "BreakOnNonKeyFrames":true
            }],
            "ContainerProfiles":[],
            "CodecProfiles":[{
              "Type":"Video",
              "Codec":"h264",
              "Conditions":[
                {"Condition":"EqualsAny","Property":"VideoProfile","Value":"high|main|baseline|constrained baseline"},
                {"Condition":"LessThanEqual","Property":"VideoLevel","Value":"41"},
                {"Condition":"LessThanEqual","Property":"Width","Value":"1920"},
                {"Condition":"LessThanEqual","Property":"Height","Value":"1080"},
                {"Condition":"NotEquals","Property":"IsInterlaced","Value":"true"}
              ]
            },{
              "Type":"VideoAudio",
              "Conditions":[{
                "Condition":"LessThanEqual",
                "Property":"AudioChannels",
                "Value":"2"
              }]
            }],
            "SubtitleProfiles":[]
          }
        }
    """.trimIndent()

    private fun playbackUpdateBody(
        itemId: String,
        mediaSourceId: String,
        playSessionId: String,
        positionTicks: Long,
        eventName: String? = null,
    ): String {
        val eventProperty = eventName?.let { ",\"EventName\":\"$it\"" }.orEmpty()
        return """
            {
              "ItemId":"$itemId",
              "MediaSourceId":"$mediaSourceId",
              "PlaySessionId":"$playSessionId",
              "PositionTicks":$positionTicks,
              "IsPaused":false,
              "PlayMethod":"DirectStream"$eventProperty
            }
        """.trimIndent()
    }

    private fun query(vararg parameters: Pair<String, String>): String =
        parameters.joinToString("&") { (name, value) ->
            "${name.urlEncoded()}=${value.urlEncoded()}"
        }

    private fun String.urlEncoded(): String =
        URLEncoder.encode(this, StandardCharsets.UTF_8)

    private fun request(
        url: String,
        method: String = "GET",
        headers: Map<String, String> = emptyMap(),
        body: String? = null,
    ): HttpResponse {
        val connection = URI(url).toURL().openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = method
            connection.connectTimeout = 2_000
            connection.readTimeout = 2_000
            headers.forEach(connection::setRequestProperty)
            if (body != null) {
                connection.doOutput = true
                connection.outputStream.use { output ->
                    output.write(body.encodeToByteArray())
                }
            }
            val statusCode = connection.responseCode
            val stream = if (statusCode >= 400) connection.errorStream else connection.inputStream
            HttpResponse(
                statusCode = statusCode,
                contentType = connection.contentType?.substringBefore(';'),
                body = stream?.use { it.readBytes() } ?: byteArrayOf(),
            )
        } finally {
            connection.disconnect()
        }
    }

    private fun HttpResponse.jsonBody(): JsonObject =
        Json.parseToJsonElement(body.decodeToString()).jsonObject

    private fun JsonObject.string(name: String): String? =
        this[name]?.jsonPrimitive?.content

    private fun JsonObject.int(name: String): Int? = string(name)?.toIntOrNull()

    private fun JsonObject.long(name: String): Long? = string(name)?.toLongOrNull()

    private data class HttpResponse(
        val statusCode: Int,
        val contentType: String?,
        val body: ByteArray,
    )
}
