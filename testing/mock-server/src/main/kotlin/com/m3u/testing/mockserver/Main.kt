package com.m3u.testing.mockserver

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.request.host
import io.ktor.server.request.port
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.concurrent.ConcurrentHashMap

private const val DEFAULT_HOST = "0.0.0.0"
private const val DEFAULT_PORT = 8080
private const val DEFAULT_USERNAME = "m3u"
private const val DEFAULT_PASSWORD = "m3u"
private const val EMBY_ACCESS_TOKEN = "mock-emby-access-token"
private const val EMBY_SERVER_ID = "mock-server-id"
private const val EMBY_USER_ID = "mock-user-id"
private const val EMBY_VOD_SERIES_ID = "mock.series.orbit"
private const val EMBY_VOD_EPISODE_ID = "mock.episode.orbit.s01e01"
private const val EMBY_VOD_FIELDS =
    "Overview,Genres,ProductionYear,SeriesName,ParentIndexNumber,IndexNumber"
private const val REFERENCE_PASSWORD = "reference-password"
private const val REFERENCE_ACCESS_TOKEN = "mock-reference-access-token"
private const val REFERENCE_SERVER_ID = "reference-server-id"
private const val REFERENCE_USER_ID = "reference-user-id"
private const val REFERENCE_WAV_SAMPLE_RATE = 8_000
private const val REFERENCE_WAV_DURATION_SECONDS = 10
private const val REFERENCE_WAV_CHANNEL_COUNT = 1
private const val REFERENCE_WAV_BITS_PER_SAMPLE = 16
private const val REFERENCE_WAV_TONE_FREQUENCY = 500

private val referenceChannelIds = setOf("reference.news", "reference.sports")
private val referenceSessions = ConcurrentHashMap<String, ReferenceSession>()
private val referenceWavFixture by lazy(::createReferenceWavFixture)
private val embyVodPlayableItemIds = setOf(
    "mock.movie.harbor",
    "mock.movie.signal",
    EMBY_VOD_EPISODE_ID,
    "mock.episode.orbit.s01e02",
    "mock.episode.orbit.s02e01",
)
private val embyVodSessions = ConcurrentHashMap<String, EmbyVodSession>()

private val json = Json {
    prettyPrint = true
    explicitNulls = false
}

fun main(args: Array<String>) {
    val options = ServerOptions.parse(args)
    embeddedServer(
        factory = Netty,
        host = options.host,
        port = options.port,
        module = Application::mockServerModule
    ).start(wait = true)
}

internal fun Application.mockServerModule() {
    routing {
        get("/") {
            val baseUrl = call.baseUrl()
            call.respondText(
                text = endpointIndex(baseUrl),
                contentType = ContentType.Application.Json
            )
        }

        get("/health") {
            call.respondText("ok", ContentType.Text.Plain)
        }

        post("/reference-provider/login") {
            val body = runCatching {
                Json.parseToJsonElement(call.receiveText()).jsonObject
            }.getOrNull()
            if (body == null || body.keys != setOf("username", "password")) {
                call.respond(HttpStatusCode.BadRequest, "invalid reference login payload")
                return@post
            }
            val username = body["username"]?.jsonPrimitive?.content
            val password = body["password"]?.jsonPrimitive?.content
            if (username != DEFAULT_USERNAME || password != REFERENCE_PASSWORD) {
                call.respond(HttpStatusCode.Unauthorized, "invalid reference credentials")
                return@post
            }
            call.respondText(
                text = json.encodeToString(referenceLoginResponse()),
                contentType = ContentType.Application.Json,
            )
        }

        get("/reference-provider/channels") {
            if (
                !call.referenceAuthenticated() ||
                call.request.headers["X-Reference-User"] != REFERENCE_USER_ID
            ) {
                call.respond(HttpStatusCode.Unauthorized, "missing reference provider token")
                return@get
            }
            call.respondText(
                text = json.encodeToString(referenceChannels()),
                contentType = ContentType.Application.Json,
            )
        }

        get("/reference-provider/playback/{item}") {
            if (!call.referenceAuthenticated()) {
                call.respond(HttpStatusCode.Unauthorized, "missing reference provider token")
                return@get
            }
            val itemId = call.parameters["item"].orEmpty()
            if (itemId !in referenceChannelIds) {
                call.respond(HttpStatusCode.NotFound, "unknown reference channel")
                return@get
            }
            val session = ReferenceSession(
                itemId = itemId,
                playSessionId = "reference-play-session-$itemId",
                liveStreamId = "reference-live-stream-$itemId",
                closed = false,
                closeCount = 0,
                lastCloseReason = null,
                updateCount = 0,
                lastPositionTicks = 0L,
                lastEvent = null,
            )
            referenceSessions[session.playSessionId] = session
            call.respondText(
                text = json.encodeToString(referencePlayback(call.baseUrl(), session)),
                contentType = ContentType.Application.Json,
            )
        }

        post("/reference-provider/sessions/update") {
            if (!call.referencePlaybackAuthenticated()) {
                call.respond(HttpStatusCode.Unauthorized, "missing reference playback headers")
                return@post
            }
            if (!call.isJsonRequest()) {
                call.respond(HttpStatusCode.BadRequest, "reference update must be JSON")
                return@post
            }
            val update = call.receiveJsonObjectOrNull().toReferenceSessionUpdate()
            if (update == null) {
                call.respond(HttpStatusCode.BadRequest, "invalid reference update payload")
                return@post
            }
            val session = referenceSessions[update.playSessionId]
            if (
                session == null ||
                session.itemId != update.itemId ||
                session.liveStreamId != update.liveStreamId
            ) {
                call.respond(HttpStatusCode.NotFound, "reference session was not found")
                return@post
            }
            if (session.closed) {
                call.respond(HttpStatusCode.Conflict, "reference session is already closed")
                return@post
            }
            if (!update.hasConsistentPauseState()) {
                call.respond(HttpStatusCode.BadRequest, "invalid reference update state")
                return@post
            }
            val updatedSession = session.copy(
                updateCount = session.updateCount + 1,
                lastPositionTicks = update.positionTicks,
                lastEvent = update.event,
            )
            referenceSessions[session.playSessionId] = updatedSession
            call.respondText(
                text = json.encodeToString(referenceSessionUpdateResult(updatedSession)),
                contentType = ContentType.Application.Json,
            )
        }

        post("/reference-provider/sessions/close") {
            if (!call.referenceAuthenticated()) {
                call.respond(HttpStatusCode.Unauthorized, "missing reference provider token")
                return@post
            }
            val body = runCatching {
                Json.parseToJsonElement(call.receiveText()).jsonObject
            }.getOrNull()
            val expectedKeys = setOf(
                "item_id",
                "play_session_id",
                "live_stream_id",
                "reason",
            )
            if (body == null || body.keys != expectedKeys) {
                call.respond(HttpStatusCode.BadRequest, "invalid reference close payload")
                return@post
            }
            val itemId = body["item_id"]?.jsonPrimitive?.content.orEmpty()
            val playSessionId = body["play_session_id"]?.jsonPrimitive?.content.orEmpty()
            val liveStreamId = body["live_stream_id"]?.jsonPrimitive?.content.orEmpty()
            val reason = body["reason"]?.jsonPrimitive?.content.orEmpty()
            val session = referenceSessions[playSessionId]
            if (
                session == null ||
                session.itemId != itemId ||
                session.liveStreamId != liveStreamId ||
                reason.isBlank()
            ) {
                call.respond(HttpStatusCode.NotFound, "reference session was not found")
                return@post
            }
            referenceSessions[playSessionId] = session.copy(
                closed = true,
                closeCount = session.closeCount + 1,
                lastCloseReason = reason,
            )
            call.respondText(
                text = json.encodeToString(buildJsonObject { put("closed", true) }),
                contentType = ContentType.Application.Json,
            )
        }

        get("/reference-provider/sessions/{session}") {
            if (!call.referenceAuthenticated()) {
                call.respond(HttpStatusCode.Unauthorized, "missing reference provider token")
                return@get
            }
            val playSessionId = call.parameters["session"].orEmpty()
            val session = referenceSessions[playSessionId]
            if (session == null) {
                call.respond(HttpStatusCode.NotFound, "reference session was not found")
                return@get
            }
            call.respondText(
                text = json.encodeToString(referenceSessionState(session)),
                contentType = ContentType.Application.Json,
            )
        }

        get("/reference-provider/stream/{item}/sample.wav") {
            if (!call.referencePlaybackAuthenticated()) {
                call.respond(HttpStatusCode.Unauthorized, "missing reference playback headers")
                return@get
            }
            val itemId = call.parameters["item"].orEmpty()
            if (itemId !in referenceChannelIds) {
                call.respond(HttpStatusCode.NotFound, "unknown reference channel")
                return@get
            }
            call.respondBytes(
                bytes = referenceWavFixture,
                contentType = ContentType.parse("audio/wav"),
            )
        }

        get("/playlist/live.m3u") {
            call.respondText(
                text = livePlaylist(call.baseUrl()),
                contentType = ContentType.parse("audio/x-mpegurl")
            )
        }

        get("/playlist/mixed.m3u") {
            call.respondText(
                text = mixedPlaylist(call.baseUrl()),
                contentType = ContentType.parse("audio/x-mpegurl")
            )
        }

        get("/hls/{channel}/index.m3u8") {
            val channel = call.parameters["channel"].orEmpty()
            call.respondText(
                text = hlsPlaylist(channel),
                contentType = ContentType.parse("application/vnd.apple.mpegurl")
            )
        }

        get("/hls/{channel}/segment-{number}.ts") {
            val channel = call.parameters["channel"].orEmpty()
            val number = call.parameters["number"]?.toIntOrNull() ?: 0
            call.respondBytes(
                bytes = transportStreamPlaceholder(channel, number),
                contentType = ContentType.parse("video/mp2t")
            )
        }

        get("/System/Info/Public") {
            call.respondText(
                text = json.encodeToString(embySystemInfo()),
                contentType = ContentType.Application.Json
            )
        }

        post("/Users/AuthenticateByName") {
            if (!call.embyIdentityAuthenticated()) {
                call.respond(HttpStatusCode.Unauthorized, "invalid Emby authorization")
                return@post
            }
            val body = Json.parseToJsonElement(call.receiveText()).jsonObject
            val username = body["Username"]?.jsonPrimitive?.content
            val password = body["Pw"]?.jsonPrimitive?.content
            if (username != DEFAULT_USERNAME || password != DEFAULT_PASSWORD) {
                call.respond(HttpStatusCode.Unauthorized, "invalid media server credentials")
                return@post
            }
            call.respondText(
                text = json.encodeToString(embyAuthentication()),
                contentType = ContentType.Application.Json
            )
        }

        get("/LiveTv/Channels") {
            if (!call.embyAuthenticated()) {
                call.respond(HttpStatusCode.Unauthorized, "missing media server token")
                return@get
            }
            call.respondText(
                text = json.encodeToString(embyLiveTvChannels()),
                contentType = ContentType.Application.Json
            )
        }

        get("/Users/{user}/Items") {
            val userId = call.parameters["user"].orEmpty()
            if (!call.embyVodAuthenticated(userId)) {
                call.respond(HttpStatusCode.Unauthorized, "invalid Emby VOD user")
                return@get
            }
            val pagination = call.embyBrowsePagination(
                expectedParameters = mapOf(
                    "Recursive" to "true",
                    "IncludeItemTypes" to "Movie,Series",
                    "EnableImages" to "true",
                    "EnableImageTypes" to "Primary",
                    "Fields" to EMBY_VOD_FIELDS,
                    "SortBy" to "SortName,ProductionYear",
                    "SortOrder" to "Ascending",
                ),
            )
            val items = embyVodRootItems()
            if (pagination == null || pagination.startIndex > items.size) {
                call.respond(HttpStatusCode.BadRequest, "invalid Emby VOD browse query")
                return@get
            }
            call.respondText(
                text = json.encodeToString(
                    embyContentPage(
                        items = items,
                        pagination = pagination,
                    ),
                ),
                contentType = ContentType.Application.Json,
            )
        }

        get("/Shows/{series}/Episodes") {
            if (!call.embyVodAuthenticated(call.request.queryParameters["UserId"])) {
                call.respond(HttpStatusCode.Unauthorized, "invalid Emby episode user")
                return@get
            }
            if (call.parameters["series"] != EMBY_VOD_SERIES_ID) {
                call.respond(HttpStatusCode.NotFound, "unknown Emby series")
                return@get
            }
            val pagination = call.embyBrowsePagination(
                expectedParameters = mapOf(
                    "UserId" to EMBY_USER_ID,
                    "EnableImages" to "true",
                    "EnableImageTypes" to "Primary",
                    "Fields" to EMBY_VOD_FIELDS,
                ),
            )
            val items = embyVodEpisodes()
            if (pagination == null || pagination.startIndex > items.size) {
                call.respond(HttpStatusCode.BadRequest, "invalid Emby episode browse query")
                return@get
            }
            call.respondText(
                text = json.encodeToString(
                    embyContentPage(
                        items = items,
                        pagination = pagination,
                    ),
                ),
                contentType = ContentType.Application.Json,
            )
        }

        post("/Items/{item}/PlaybackInfo") {
            val itemId = call.parameters["item"].orEmpty()
            if (!call.embyAuthenticated()) {
                call.respond(HttpStatusCode.Unauthorized, "invalid Emby playback user")
                return@post
            }
            if (call.request.queryParameters.names().isNotEmpty()) {
                call.respond(HttpStatusCode.BadRequest, "Emby playback query must be empty")
                return@post
            }
            val livePlayback = itemId !in embyVodPlayableItemIds
            if (itemId == EMBY_VOD_SERIES_ID) {
                call.respond(HttpStatusCode.NotFound, "series is not directly playable")
                return@post
            }
            val body = call.receiveJsonObjectOrNull()
            if (
                !call.isJsonRequest() ||
                !body.isValidEmbyPlaybackInfoRequest(
                    expectedAutoOpenLiveStream = livePlayback,
                )
            ) {
                call.respond(HttpStatusCode.BadRequest, "invalid Emby playback payload")
                return@post
            }
            if (livePlayback) {
                call.respondText(
                    text = json.encodeToString(embyPlaybackInfo(call.baseUrl(), itemId)),
                    contentType = ContentType.Application.Json,
                )
                return@post
            }
            val requestedMediaSourceId = body?.string("MediaSourceId")
            val expectedMediaSourceId = embyVodMediaSourceId(itemId)
            if (
                requestedMediaSourceId != null &&
                requestedMediaSourceId != expectedMediaSourceId
            ) {
                call.respond(HttpStatusCode.BadRequest, "unknown Emby VOD media source")
                return@post
            }
            val session = EmbyVodSession(
                itemId = itemId,
                mediaSourceId = expectedMediaSourceId,
                playSessionId = embyVodPlaySessionId(itemId),
                state = EmbyVodSessionState.Resolved,
                startCount = 0,
                progressCount = 0,
                stopCount = 0,
                lastPositionTicks = 0L,
                isPaused = false,
                playMethod = null,
                lastEventName = null,
            )
            embyVodSessions[session.playSessionId] = session
            call.respondText(
                text = json.encodeToString(embyVodPlaybackInfo(session)),
                contentType = ContentType.Application.Json,
            )
        }

        get("/emby-stream/{item}/index.m3u8") {
            if (!call.embyAuthenticated() || call.request.headers["X-Mock-Playback"] != "allowed") {
                call.respond(HttpStatusCode.Unauthorized, "missing playback headers")
                return@get
            }
            call.respondText(
                text = hlsPlaylist(call.parameters["item"].orEmpty()),
                contentType = ContentType.parse("application/vnd.apple.mpegurl")
            )
        }

        get("/Videos/{item}/stream") {
            val itemId = call.parameters["item"].orEmpty()
            if (!call.embyVodAuthenticatedFromHeader()) {
                call.respond(HttpStatusCode.Unauthorized, "invalid Emby VOD stream user")
                return@get
            }
            if (itemId !in embyVodPlayableItemIds) {
                call.respond(HttpStatusCode.NotFound, "unknown Emby VOD item")
                return@get
            }
            val expectedMediaSourceId = embyVodMediaSourceId(itemId)
            val expectedPlaySessionId = embyVodPlaySessionId(itemId)
            if (
                !call.hasExactQueryParameters(
                    expected = mapOf(
                        "static" to "true",
                        "MediaSourceId" to expectedMediaSourceId,
                        "PlaySessionId" to expectedPlaySessionId,
                    ),
                ) ||
                embyVodSessions[expectedPlaySessionId]?.itemId != itemId
            ) {
                call.respond(HttpStatusCode.BadRequest, "invalid Emby VOD stream reference")
                return@get
            }
            call.respondBytes(
                bytes = referenceWavFixture,
                contentType = ContentType.parse("audio/wav"),
            )
        }

        post("/Sessions/Playing") {
            if (!call.embyAuthenticated()) {
                call.respond(HttpStatusCode.Unauthorized, "missing media server token")
                return@post
            }
            val body = call.receiveJsonObjectOrNull()
            if (!body.targetsKnownEmbyVodSession()) {
                call.respond(HttpStatusCode.NoContent)
                return@post
            }
            if (!call.embyVodAuthenticatedFromHeader()) {
                call.respond(HttpStatusCode.Unauthorized, "invalid Emby playback user")
                return@post
            }
            val update = body?.toEmbyPlaybackUpdate(requireEventName = false)
            val session = update?.let { embyVodSessions[it.playSessionId] }
            if (
                !call.isJsonRequest() ||
                update == null ||
                session == null ||
                !update.matches(session) ||
                session.state != EmbyVodSessionState.Resolved
            ) {
                call.respond(HttpStatusCode.BadRequest, "invalid Emby playback start")
                return@post
            }
            embyVodSessions[session.playSessionId] = session.copy(
                state = EmbyVodSessionState.Playing,
                startCount = session.startCount + 1,
                lastPositionTicks = update.positionTicks,
                isPaused = update.isPaused,
                playMethod = update.playMethod,
            )
            call.respond(HttpStatusCode.NoContent)
        }

        post("/Sessions/Playing/Progress") {
            if (!call.embyAuthenticated()) {
                call.respond(HttpStatusCode.Unauthorized, "missing media server token")
                return@post
            }
            val body = call.receiveJsonObjectOrNull()
            if (!body.targetsKnownEmbyVodSession()) {
                call.respond(HttpStatusCode.NoContent)
                return@post
            }
            if (!call.embyVodAuthenticatedFromHeader()) {
                call.respond(HttpStatusCode.Unauthorized, "invalid Emby playback user")
                return@post
            }
            val update = body?.toEmbyPlaybackUpdate(requireEventName = true)
            val session = update?.let { embyVodSessions[it.playSessionId] }
            if (
                !call.isJsonRequest() ||
                update == null ||
                session == null ||
                !update.matches(session) ||
                session.state != EmbyVodSessionState.Playing ||
                !update.hasConsistentPauseState()
            ) {
                call.respond(HttpStatusCode.BadRequest, "invalid Emby playback progress")
                return@post
            }
            embyVodSessions[session.playSessionId] = session.copy(
                progressCount = session.progressCount + 1,
                lastPositionTicks = update.positionTicks,
                isPaused = update.isPaused,
                playMethod = update.playMethod,
                lastEventName = update.eventName,
            )
            call.respond(HttpStatusCode.NoContent)
        }

        post("/Sessions/Playing/Stopped") {
            if (!call.embyAuthenticated()) {
                call.respond(HttpStatusCode.Unauthorized, "missing media server token")
                return@post
            }
            val body = call.receiveJsonObjectOrNull()
            if (body.targetsKnownEmbyVodSession()) {
                if (!call.embyVodAuthenticatedFromHeader()) {
                    call.respond(HttpStatusCode.Unauthorized, "invalid Emby playback user")
                    return@post
                }
                val stopped = body?.toEmbyPlaybackStopped()
                val session = stopped?.let { embyVodSessions[it.playSessionId] }
                if (
                    !call.isJsonRequest() ||
                    stopped == null ||
                    session == null ||
                    !stopped.matches(session) ||
                    session.state != EmbyVodSessionState.Playing
                ) {
                    call.respond(HttpStatusCode.BadRequest, "invalid Emby playback stop")
                    return@post
                }
                embyVodSessions[session.playSessionId] = session.copy(
                    state = EmbyVodSessionState.Stopped,
                    stopCount = session.stopCount + 1,
                    lastPositionTicks = stopped.positionTicks,
                )
            }
            call.respond(HttpStatusCode.NoContent)
        }

        get("/mock/emby/sessions/{session}") {
            if (!call.embyVodAuthenticatedFromHeader()) {
                call.respond(HttpStatusCode.Unauthorized, "invalid Emby playback user")
                return@get
            }
            val session = embyVodSessions[call.parameters["session"].orEmpty()]
            if (session == null) {
                call.respond(HttpStatusCode.NotFound, "Emby VOD session was not found")
                return@get
            }
            call.respondText(
                text = json.encodeToString(embyVodSessionState(session)),
                contentType = ContentType.Application.Json,
            )
        }

        post("/LiveStreams/Close") {
            if (!call.embyAuthenticated()) {
                call.respond(HttpStatusCode.Unauthorized, "missing media server token")
                return@post
            }
            call.respond(HttpStatusCode.NoContent)
        }

        get("/jellyfin/System/Info/Public") {
            call.respondText(
                text = json.encodeToString(jellyfinSystemInfo()),
                contentType = ContentType.Application.Json
            )
        }

        post("/jellyfin/Users/AuthenticateByName") {
            if (!call.jellyfinIdentityAuthenticated()) {
                call.respond(HttpStatusCode.Unauthorized, "deprecated media server authorization")
                return@post
            }
            val body = Json.parseToJsonElement(call.receiveText()).jsonObject
            val username = body["Username"]?.jsonPrimitive?.content
            val password = body["Pw"]?.jsonPrimitive?.content
            if (username != DEFAULT_USERNAME || password != DEFAULT_PASSWORD) {
                call.respond(HttpStatusCode.Unauthorized, "invalid media server credentials")
                return@post
            }
            call.respondText(
                text = json.encodeToString(embyAuthentication()),
                contentType = ContentType.Application.Json
            )
        }

        get("/jellyfin/LiveTv/Channels") {
            if (!call.jellyfinAuthenticated()) {
                call.respond(HttpStatusCode.Unauthorized, "missing media server token")
                return@get
            }
            call.respondText(
                text = json.encodeToString(embyLiveTvChannels()),
                contentType = ContentType.Application.Json
            )
        }

        post("/jellyfin/Items/{item}/PlaybackInfo") {
            if (!call.jellyfinAuthenticated()) {
                call.respond(HttpStatusCode.Unauthorized, "missing media server token")
                return@post
            }
            val itemId = call.parameters["item"].orEmpty()
            val body = call.receiveJsonObjectOrNull()
            if (
                call.request.queryParameters.names().isNotEmpty() ||
                !call.isJsonRequest() ||
                !body.isValidEmbyPlaybackInfoRequest(
                    expectedAutoOpenLiveStream = true,
                )
            ) {
                call.respond(HttpStatusCode.BadRequest, "invalid Jellyfin playback payload")
                return@post
            }
            call.respondText(
                text = json.encodeToString(
                    embyPlaybackInfo("${call.baseUrl()}/jellyfin", itemId)
                ),
                contentType = ContentType.Application.Json
            )
        }

        get("/jellyfin/emby-stream/{item}/index.m3u8") {
            if (!call.jellyfinAuthenticated() || call.request.headers["X-Mock-Playback"] != "allowed") {
                call.respond(HttpStatusCode.Unauthorized, "missing playback headers")
                return@get
            }
            call.respondText(
                text = hlsPlaylist(call.parameters["item"].orEmpty()),
                contentType = ContentType.parse("application/vnd.apple.mpegurl")
            )
        }

        post("/jellyfin/Sessions/Playing/Stopped") {
            if (!call.jellyfinAuthenticated()) {
                call.respond(HttpStatusCode.Unauthorized, "missing media server token")
                return@post
            }
            call.respond(HttpStatusCode.NoContent)
        }

        post("/jellyfin/LiveStreams/Close") {
            if (!call.jellyfinAuthenticated()) {
                call.respond(HttpStatusCode.Unauthorized, "missing media server token")
                return@post
            }
            call.respond(HttpStatusCode.NoContent)
        }

        get("/live/{username}/{password}/{stream}.ts") {
            val auth = call.xtreamAuth()
            if (!auth.valid) {
                call.respond(HttpStatusCode.Unauthorized, "invalid xtream credentials")
                return@get
            }
            call.respondBytes(
                bytes = transportStreamPlaceholder(call.parameters["stream"].orEmpty(), 1),
                contentType = ContentType.parse("video/mp2t")
            )
        }

        get("/movie/{username}/{password}/{stream}.mp4") {
            val auth = call.xtreamAuth()
            if (!auth.valid) {
                call.respond(HttpStatusCode.Unauthorized, "invalid xtream credentials")
                return@get
            }
            call.respondBytes(
                bytes = mp4Placeholder(call.parameters["stream"].orEmpty()),
                contentType = ContentType.Video.MP4
            )
        }

        get("/series/{username}/{password}/{episode}.mp4") {
            val auth = call.xtreamAuth()
            if (!auth.valid) {
                call.respond(HttpStatusCode.Unauthorized, "invalid xtream credentials")
                return@get
            }
            call.respondBytes(
                bytes = mp4Placeholder(call.parameters["episode"].orEmpty()),
                contentType = ContentType.Video.MP4
            )
        }

        get("/player_api.php") {
            val auth = call.xtreamAuth()
            if (!auth.valid) {
                call.respondText(
                    text = json.encodeToString(xtreamUnauthorized()),
                    contentType = ContentType.Application.Json,
                    status = HttpStatusCode.Unauthorized
                )
                return@get
            }

            val baseUrl = call.baseUrl()
            val payload = when (call.request.queryParameters["action"]) {
                null -> xtreamInfo(baseUrl)
                "get_live_categories" -> liveCategories()
                "get_live_streams" -> liveStreams(baseUrl)
                "get_vod_categories" -> vodCategories()
                "get_vod_streams" -> vodStreams(baseUrl)
                "get_series_categories" -> seriesCategories()
                "get_series" -> seriesStreams(baseUrl)
                "get_series_info" -> seriesInfo(
                    seriesId = call.request.queryParameters["series_id"]?.toIntOrNull() ?: 3001
                )
                else -> JsonObject(emptyMap())
            }

            call.respondText(
                text = json.encodeToString(payload),
                contentType = ContentType.Application.Json
            )
        }
    }
}

private data class ServerOptions(
    val host: String,
    val port: Int
) {
    companion object {
        fun parse(args: Array<String>): ServerOptions {
            var host = DEFAULT_HOST
            var port = DEFAULT_PORT

            args.toList().windowed(size = 2, step = 1).forEach { (key, value) ->
                when (key) {
                    "--host" -> host = value
                    "--port" -> port = value.toInt()
                }
            }
            return ServerOptions(host, port)
        }
    }
}

private data class XtreamAuth(val valid: Boolean)

private data class ReferenceSession(
    val itemId: String,
    val playSessionId: String,
    val liveStreamId: String,
    val closed: Boolean,
    val closeCount: Int,
    val lastCloseReason: String?,
    val updateCount: Int,
    val lastPositionTicks: Long,
    val lastEvent: String?,
)

private data class ReferenceSessionUpdate(
    val itemId: String,
    val playSessionId: String,
    val liveStreamId: String,
    val event: String,
    val positionTicks: Long,
    val playMethod: String,
    val isPaused: Boolean,
)

private data class EmbyPagination(
    val startIndex: Int,
    val limit: Int,
)

private enum class EmbyVodSessionState(val wireValue: String) {
    Resolved("resolved"),
    Playing("playing"),
    Stopped("stopped"),
}

private data class EmbyVodSession(
    val itemId: String,
    val mediaSourceId: String,
    val playSessionId: String,
    val state: EmbyVodSessionState,
    val startCount: Int,
    val progressCount: Int,
    val stopCount: Int,
    val lastPositionTicks: Long,
    val isPaused: Boolean,
    val playMethod: String?,
    val lastEventName: String?,
)

private data class EmbyPlaybackUpdate(
    val itemId: String,
    val mediaSourceId: String,
    val playSessionId: String,
    val positionTicks: Long,
    val isPaused: Boolean,
    val playMethod: String,
    val eventName: String?,
) {
    fun matches(session: EmbyVodSession): Boolean =
        itemId == session.itemId &&
            mediaSourceId == session.mediaSourceId &&
            playSessionId == session.playSessionId &&
            (session.playMethod == null || playMethod == session.playMethod)

    fun hasConsistentPauseState(): Boolean = when (eventName) {
        "pause" -> isPaused
        "unpause" -> !isPaused
        "timeupdate" -> true
        else -> false
    }
}

private data class EmbyPlaybackStopped(
    val itemId: String,
    val mediaSourceId: String,
    val playSessionId: String,
    val positionTicks: Long,
) {
    fun matches(session: EmbyVodSession): Boolean =
        itemId == session.itemId &&
            mediaSourceId == session.mediaSourceId &&
            playSessionId == session.playSessionId
}

private fun ApplicationCall.xtreamAuth(): XtreamAuth {
    val username = parameters["username"] ?: request.queryParameters["username"]
    val password = parameters["password"] ?: request.queryParameters["password"]
    return XtreamAuth(username == DEFAULT_USERNAME && password == DEFAULT_PASSWORD)
}

private fun ApplicationCall.baseUrl(): String {
    val forwardedProto = request.headers["X-Forwarded-Proto"]
    val scheme = forwardedProto ?: request.local.scheme
    val host = request.host()
    val port = request.port()
    val includePort = (scheme == "http" && port != 80) || (scheme == "https" && port != 443)
    return if (includePort) "$scheme://$host:$port" else "$scheme://$host"
}

private fun ApplicationCall.embyIdentityAuthenticated(): Boolean =
    request.headers["Authorization"]?.startsWith("Emby ") == true &&
        request.headers["X-Emby-Authorization"] == null

private fun ApplicationCall.embyAuthenticated(): Boolean =
    embyIdentityAuthenticated() && request.headers["X-Emby-Token"] == EMBY_ACCESS_TOKEN

private fun ApplicationCall.embyVodAuthenticated(userId: String?): Boolean =
    userId == EMBY_USER_ID && embyVodAuthenticatedFromHeader()

private fun ApplicationCall.embyVodAuthenticatedFromHeader(): Boolean =
    embyAuthenticated() &&
        "UserId=\"$EMBY_USER_ID\"" in request.headers["Authorization"].orEmpty()

private fun ApplicationCall.embyBrowsePagination(
    expectedParameters: Map<String, String>,
): EmbyPagination? {
    val startIndexValue = request.queryParameters["StartIndex"] ?: return null
    val limitValue = request.queryParameters["Limit"] ?: return null
    if (
        !hasExactQueryParameters(
            expected = expectedParameters + mapOf(
                "StartIndex" to startIndexValue,
                "Limit" to limitValue,
            ),
        )
    ) {
        return null
    }
    val startIndex = startIndexValue.toIntOrNull()?.takeIf { it >= 0 } ?: return null
    val limit = limitValue.toIntOrNull()?.takeIf { it in 1..200 } ?: return null
    return EmbyPagination(startIndex = startIndex, limit = limit)
}

private fun ApplicationCall.hasExactQueryParameters(
    expected: Map<String, String>,
    optional: Set<String> = emptySet(),
): Boolean {
    val parameters = request.queryParameters
    val names = parameters.names()
    if (!names.containsAll(expected.keys) || names.any { it !in expected.keys && it !in optional }) {
        return false
    }
    if (expected.any { (name, value) -> parameters.getAll(name) != listOf(value) }) {
        return false
    }
    return optional.all { name ->
        name !in names || parameters.getAll(name)?.size == 1
    }
}

private fun ApplicationCall.isJsonRequest(): Boolean =
    request.headers["Content-Type"]
        ?.substringBefore(';')
        ?.trim()
        ?.equals(ContentType.Application.Json.toString(), ignoreCase = true) == true

private suspend fun ApplicationCall.receiveJsonObjectOrNull(): JsonObject? = runCatching {
    Json.parseToJsonElement(receiveText()).jsonObject
}.getOrNull()

private fun JsonObject.toEmbyPlaybackUpdate(
    requireEventName: Boolean,
): EmbyPlaybackUpdate? {
    val expectedKeys = buildSet {
        add("ItemId")
        add("MediaSourceId")
        add("PlaySessionId")
        add("PositionTicks")
        add("IsPaused")
        add("PlayMethod")
        if (requireEventName) add("EventName")
    }
    if (keys != expectedKeys) return null
    val eventName = string("EventName")
    if (requireEventName && eventName !in embyProgressEventNames) return null
    val itemId = string("ItemId") ?: return null
    if (itemId !in embyVodPlayableItemIds) return null
    val playMethod = string("PlayMethod")
        ?.takeIf { it in embyPlayMethods }
        ?: return null
    return EmbyPlaybackUpdate(
        itemId = itemId,
        mediaSourceId = string("MediaSourceId") ?: return null,
        playSessionId = string("PlaySessionId") ?: return null,
        positionTicks = long("PositionTicks")?.takeIf { it >= 0L } ?: return null,
        isPaused = boolean("IsPaused") ?: return null,
        playMethod = playMethod,
        eventName = eventName,
    )
}

private fun JsonObject?.isValidEmbyPlaybackInfoRequest(
    expectedAutoOpenLiveStream: Boolean,
): Boolean {
    val body = this ?: return false
    val requiredKeys = setOf(
        "UserId",
        "StartTimeTicks",
        "IsPlayback",
        "AutoOpenLiveStream",
        "EnableDirectPlay",
        "EnableDirectStream",
        "EnableTranscoding",
        "AllowVideoStreamCopy",
        "AllowAudioStreamCopy",
        "DeviceProfile",
    )
    val optionalKeys = setOf("MediaSourceId", "MaxStreamingBitrate")
    if (!body.keys.containsAll(requiredKeys) || body.keys.any { it !in requiredKeys + optionalKeys }) {
        return false
    }
    val maxStreamingBitrate = body.long("MaxStreamingBitrate")
    val validMaxBitrate = "MaxStreamingBitrate" !in body ||
        maxStreamingBitrate in 1L..Int.MAX_VALUE.toLong()
    return body.string("UserId") == EMBY_USER_ID &&
        body.long("StartTimeTicks")?.let { it >= 0L } == true &&
        body.boolean("IsPlayback") == true &&
        body.boolean("AutoOpenLiveStream") == expectedAutoOpenLiveStream &&
        body.boolean("EnableDirectPlay") == true &&
        body.boolean("EnableDirectStream") == true &&
        body.boolean("EnableTranscoding") != null &&
        body.boolean("AllowVideoStreamCopy") == true &&
        body.boolean("AllowAudioStreamCopy") == true &&
        validMaxBitrate &&
        (body["DeviceProfile"] as? JsonObject)
            .isValidMedia3DeviceProfile(maxStreamingBitrate)
}

private fun JsonObject?.isValidMedia3DeviceProfile(
    expectedMaxStreamingBitrate: Long?,
): Boolean {
    val profile = this ?: return false
    val requiredKeys = setOf(
        "Name",
        "SupportedMediaTypes",
        "DirectPlayProfiles",
        "TranscodingProfiles",
        "ContainerProfiles",
        "CodecProfiles",
        "SubtitleProfiles",
    )
    val optionalKeys = setOf("MaxStreamingBitrate")
    if (
        !profile.keys.containsAll(requiredKeys) ||
        profile.keys.any { it !in requiredKeys + optionalKeys }
    ) {
        return false
    }
    if (
        profile.string("Name") != "M3UAndroid Media3" ||
        profile.string("SupportedMediaTypes") != "Video" ||
        profile.long("MaxStreamingBitrate") != expectedMaxStreamingBitrate
    ) {
        return false
    }
    val directPlay = (profile["DirectPlayProfiles"] as? JsonArray)
        ?.singleOrNull() as? JsonObject
        ?: return false
    if (
        directPlay.string("Container") != "mp4,m4v,mov,mkv,ts,mpegts" ||
        directPlay.string("AudioCodec") != "aac,mp3" ||
        directPlay.string("VideoCodec") != "h264" ||
        directPlay.string("Type") != "Video"
    ) {
        return false
    }
    val transcoding = (profile["TranscodingProfiles"] as? JsonArray)
        ?.singleOrNull() as? JsonObject
        ?: return false
    if (
        transcoding.string("Container") != "ts" ||
        transcoding.string("Type") != "Video" ||
        transcoding.string("VideoCodec") != "h264" ||
        transcoding.string("AudioCodec") != "aac" ||
        transcoding.string("Protocol") != "hls" ||
        transcoding.string("Context") != "Streaming" ||
        transcoding.boolean("CopyTimestamps") != false ||
        transcoding.string("MaxAudioChannels") != "2"
    ) {
        return false
    }
    val codecProfiles = profile["CodecProfiles"] as? JsonArray ?: return false
    val videoProfile = codecProfiles.getOrNull(0) as? JsonObject ?: return false
    val audioProfile = codecProfiles.getOrNull(1) as? JsonObject ?: return false
    return codecProfiles.size == 2 &&
        videoProfile.string("Type") == "Video" &&
        videoProfile.string("Codec") == "h264" &&
        (videoProfile["Conditions"] as? JsonArray)?.size == 5 &&
        audioProfile.string("Type") == "VideoAudio" &&
        (audioProfile["Conditions"] as? JsonArray)?.singleOrNull() != null &&
        (profile["ContainerProfiles"] as? JsonArray)?.isEmpty() == true &&
        (profile["SubtitleProfiles"] as? JsonArray)?.isEmpty() == true
}

private fun JsonObject.toEmbyPlaybackStopped(): EmbyPlaybackStopped? {
    if (keys != setOf("ItemId", "MediaSourceId", "PlaySessionId", "PositionTicks")) {
        return null
    }
    val itemId = string("ItemId") ?: return null
    if (itemId !in embyVodPlayableItemIds) return null
    return EmbyPlaybackStopped(
        itemId = itemId,
        mediaSourceId = string("MediaSourceId") ?: return null,
        playSessionId = string("PlaySessionId") ?: return null,
        positionTicks = long("PositionTicks")?.takeIf { it >= 0L } ?: return null,
    )
}

private fun JsonObject.string(name: String): String? =
    (this[name] as? JsonPrimitive)?.content?.takeIf(String::isNotBlank)

private fun JsonObject.long(name: String): Long? =
    (this[name] as? JsonPrimitive)?.content?.toLongOrNull()

private fun JsonObject.boolean(name: String): Boolean? =
    (this[name] as? JsonPrimitive)?.content?.toBooleanStrictOrNull()

private fun JsonObject?.toReferenceSessionUpdate(): ReferenceSessionUpdate? {
    val body = this ?: return null
    if (body.keys.toList() != referenceUpdateFieldOrder) return null
    val event = body.strictString("event")
        ?.takeIf(referencePlaybackEvents::contains)
        ?: return null
    val playMethod = body.strictString("play_method")
        ?.takeIf { value -> value == "direct_play" }
        ?: return null
    return ReferenceSessionUpdate(
        itemId = body.strictString("item_id")
            ?.takeIf(referenceChannelIds::contains)
            ?: return null,
        playSessionId = body.strictString("play_session_id") ?: return null,
        liveStreamId = body.strictString("live_stream_id") ?: return null,
        event = event,
        positionTicks = body.strictLong("position_ticks")
            ?.takeIf { value -> value >= 0L }
            ?: return null,
        playMethod = playMethod,
        isPaused = body.strictBoolean("is_paused") ?: return null,
    )
}

private fun JsonObject.strictString(name: String): String? =
    (this[name] as? JsonPrimitive)
        ?.takeIf(JsonPrimitive::isString)
        ?.content
        ?.takeIf(String::isNotBlank)

private fun JsonObject.strictLong(name: String): Long? =
    (this[name] as? JsonPrimitive)
        ?.takeUnless(JsonPrimitive::isString)
        ?.content
        ?.toLongOrNull()

private fun JsonObject.strictBoolean(name: String): Boolean? =
    (this[name] as? JsonPrimitive)
        ?.takeUnless(JsonPrimitive::isString)
        ?.content
        ?.toBooleanStrictOrNull()

private fun ReferenceSessionUpdate.hasConsistentPauseState(): Boolean =
    when (event) {
        "paused" -> isPaused
        "started", "resumed" -> !isPaused
        else -> true
    }

private fun JsonObject?.targetsKnownEmbyVodSession(): Boolean =
    this?.string("ItemId") in embyVodPlayableItemIds ||
        this?.string("PlaySessionId")?.let(embyVodSessions::containsKey) == true

private fun ApplicationCall.jellyfinIdentityAuthenticated(): Boolean =
    request.headers["Authorization"]?.startsWith("MediaBrowser ") == true &&
        request.headers["X-Emby-Authorization"] == null &&
        request.headers["X-Emby-Token"] == null

private fun ApplicationCall.jellyfinAuthenticated(): Boolean =
    jellyfinIdentityAuthenticated() &&
        "Token=\"$EMBY_ACCESS_TOKEN\"" in request.headers["Authorization"].orEmpty()

private fun ApplicationCall.referenceAuthenticated(): Boolean =
    request.headers["X-Emby-Token"] == REFERENCE_ACCESS_TOKEN

private fun ApplicationCall.referencePlaybackAuthenticated(): Boolean =
    referenceAuthenticated() && request.headers["X-Reference-User"] == REFERENCE_USER_ID

private val embyPlayMethods = setOf("DirectPlay", "DirectStream", "Transcode")
private val embyProgressEventNames = setOf("timeupdate", "pause", "unpause")
private val referencePlaybackEvents = setOf("started", "progress", "paused", "resumed")
private val referenceUpdateFieldOrder = listOf(
    "item_id",
    "play_session_id",
    "live_stream_id",
    "event",
    "position_ticks",
    "play_method",
    "is_paused",
)

private fun endpointIndex(baseUrl: String): String = json.encodeToString(
    buildJsonObject {
        put("name", "M3U mock server")
        put("m3u_live", "$baseUrl/playlist/live.m3u")
        put("m3u_mixed", "$baseUrl/playlist/mixed.m3u")
        put("hls_sample", "$baseUrl/hls/news/index.m3u8")
        put("xtream", "$baseUrl/player_api.php?username=$DEFAULT_USERNAME&password=$DEFAULT_PASSWORD")
        put("emby", baseUrl)
        put("jellyfin", "$baseUrl/jellyfin")
        put("reference_provider", "$baseUrl/reference-provider")
    }
)

private fun referenceLoginResponse(): JsonObject = buildJsonObject {
    put("accessToken", REFERENCE_ACCESS_TOKEN)
    put("server_id", REFERENCE_SERVER_ID)
    put("server_name", "M3U Reference Provider")
    put("server_version", "1.0.0")
    put("user_id", REFERENCE_USER_ID)
    put("username", DEFAULT_USERNAME)
}

private fun referenceChannels(): JsonObject = buildJsonObject {
    put("source_id", REFERENCE_SERVER_ID)
    put("source_title", "Reference Live TV")
    put("revision", "1")
    putJsonArray("channels") {
        add(
            buildJsonObject {
                put("id", "reference.news")
                put("title", "Reference News")
                put("category", "News")
                put("epg_reference", "reference.news")
            }
        )
        add(
            buildJsonObject {
                put("id", "reference.sports")
                put("title", "Reference Sports")
                put("category", "Sports")
                put("epg_reference", "reference.sports")
            }
        )
    }
}

private fun referencePlayback(baseUrl: String, session: ReferenceSession): JsonObject =
    buildJsonObject {
        put(
            "url",
            "$baseUrl/reference-provider/stream/${session.itemId}/sample.wav",
        )
        put("media_source_id", "reference-media-${session.itemId}")
        put("play_session_id", session.playSessionId)
        put("live_stream_id", session.liveStreamId)
    }

private fun createReferenceWavFixture(): ByteArray {
    val bytesPerSample = REFERENCE_WAV_BITS_PER_SAMPLE / Byte.SIZE_BITS
    val sampleCount = REFERENCE_WAV_SAMPLE_RATE * REFERENCE_WAV_DURATION_SECONDS
    val dataSize = sampleCount * REFERENCE_WAV_CHANNEL_COUNT * bytesPerSample
    val fixture = ByteArray(WAV_HEADER_SIZE + dataSize)
    val byteRate = REFERENCE_WAV_SAMPLE_RATE * REFERENCE_WAV_CHANNEL_COUNT * bytesPerSample
    val blockAlign = REFERENCE_WAV_CHANNEL_COUNT * bytesPerSample

    fixture.writeAscii(offset = 0, value = "RIFF")
    fixture.writeLittleEndianInt(offset = 4, value = fixture.size - 8)
    fixture.writeAscii(offset = 8, value = "WAVE")
    fixture.writeAscii(offset = 12, value = "fmt ")
    fixture.writeLittleEndianInt(offset = 16, value = 16)
    fixture.writeLittleEndianShort(offset = 20, value = 1)
    fixture.writeLittleEndianShort(offset = 22, value = REFERENCE_WAV_CHANNEL_COUNT)
    fixture.writeLittleEndianInt(offset = 24, value = REFERENCE_WAV_SAMPLE_RATE)
    fixture.writeLittleEndianInt(offset = 28, value = byteRate)
    fixture.writeLittleEndianShort(offset = 32, value = blockAlign)
    fixture.writeLittleEndianShort(offset = 34, value = REFERENCE_WAV_BITS_PER_SAMPLE)
    fixture.writeAscii(offset = 36, value = "data")
    fixture.writeLittleEndianInt(offset = 40, value = dataSize)

    val halfPeriodSamples = REFERENCE_WAV_SAMPLE_RATE / (REFERENCE_WAV_TONE_FREQUENCY * 2)
    repeat(sampleCount) { sampleIndex ->
        val sample = if ((sampleIndex / halfPeriodSamples) % 2 == 0) {
            REFERENCE_WAV_AMPLITUDE
        } else {
            -REFERENCE_WAV_AMPLITUDE
        }
        fixture.writeLittleEndianShort(
            offset = WAV_HEADER_SIZE + sampleIndex * bytesPerSample,
            value = sample,
        )
    }
    return fixture
}

private fun ByteArray.writeAscii(offset: Int, value: String) {
    value.encodeToByteArray().copyInto(this, destinationOffset = offset)
}

private fun ByteArray.writeLittleEndianInt(offset: Int, value: Int) {
    repeat(Int.SIZE_BYTES) { index ->
        this[offset + index] = (value ushr (index * Byte.SIZE_BITS)).toByte()
    }
}

private fun ByteArray.writeLittleEndianShort(offset: Int, value: Int) {
    repeat(Short.SIZE_BYTES) { index ->
        this[offset + index] = (value ushr (index * Byte.SIZE_BITS)).toByte()
    }
}

private const val WAV_HEADER_SIZE = 44
// Keep physical-device runs effectively silent while retaining deterministic non-zero PCM.
private const val REFERENCE_WAV_AMPLITUDE = 64

private fun referenceSessionState(session: ReferenceSession): JsonObject = buildJsonObject {
    put("item_id", session.itemId)
    put("play_session_id", session.playSessionId)
    put("live_stream_id", session.liveStreamId)
    put("state", if (session.closed) "closed" else "open")
    put("close_count", session.closeCount)
    session.lastCloseReason?.let { reason -> put("last_close_reason", reason) }
    put("update_count", session.updateCount)
    put("last_position_ticks", session.lastPositionTicks)
    session.lastEvent?.let { event -> put("last_event", event) }
}

private fun referenceSessionUpdateResult(session: ReferenceSession): JsonObject =
    buildJsonObject {
        put("accepted", true)
        put("update_count", session.updateCount)
        put("last_position_ticks", session.lastPositionTicks)
        put("last_event", requireNotNull(session.lastEvent))
    }

private fun embySystemInfo(): JsonObject = buildJsonObject {
    put("Id", EMBY_SERVER_ID)
    put("ServerName", "M3U Mock Emby")
    put("Version", "4.9.0.0")
    put("ProductName", "Emby Server")
}

private fun jellyfinSystemInfo(): JsonObject = buildJsonObject {
    put("Id", EMBY_SERVER_ID)
    put("ServerName", "M3U Mock Jellyfin")
    put("Version", "10.11.0")
    put("ProductName", "Jellyfin Server")
}

private fun embyAuthentication(): JsonObject = buildJsonObject {
    put("AccessToken", EMBY_ACCESS_TOKEN)
    put("ServerId", EMBY_SERVER_ID)
    putJsonObject("User") {
        put("Id", EMBY_USER_ID)
        put("Name", DEFAULT_USERNAME)
    }
}

private fun embyLiveTvChannels(): JsonObject = buildJsonObject {
    putJsonArray("Items") {
        add(
            buildJsonObject {
                put("Id", "mock.news")
                put("Name", "Mock News")
                put("ChannelNumber", "1")
                put("ChannelType", "TV")
                put("MediaType", "Video")
                put("PrimaryImageTag", "news-image")
            }
        )
        add(
            buildJsonObject {
                put("Id", "mock.sports")
                put("Name", "Mock Sports")
                put("ChannelNumber", "2")
                put("ChannelType", "TV")
                put("MediaType", "Video")
                put("PrimaryImageTag", "sports-image")
            }
        )
    }
    put("TotalRecordCount", 2)
}

private fun embyPlaybackInfo(baseUrl: String, itemId: String): JsonObject = buildJsonObject {
    put("PlaySessionId", "mock-play-session-$itemId")
    putJsonArray("MediaSources") {
        add(
            buildJsonObject {
                put("Id", "mock-media-source-$itemId")
                put("Path", "$baseUrl/emby-stream/$itemId/index.m3u8")
                put("SupportsDirectPlay", true)
                put("SupportsDirectStream", true)
                put("SupportsTranscoding", true)
                put("LiveStreamId", "mock-live-stream-$itemId")
                putJsonObject("RequiredHttpHeaders") {
                    put("X-Mock-Playback", "allowed")
                }
            }
        )
    }
}

private fun embyVodRootItems(): List<JsonObject> = listOf(
    buildJsonObject {
        put("Id", "mock.movie.harbor")
        put("Name", "Harbor of Glass")
        put("Type", "Movie")
        putJsonObject("ImageTags") { put("Primary", "harbor-poster-v1") }
        putJsonArray("Genres") {
            add(JsonPrimitive("Drama"))
            add(JsonPrimitive("Mystery"))
        }
        put("Overview", "A cartographer follows a fictional signal across a quiet harbor.")
        put("ProductionYear", 2024)
    },
    buildJsonObject {
        put("Id", EMBY_VOD_SERIES_ID)
        put("Name", "Orbital Letters")
        put("Type", "Series")
        putJsonObject("ImageTags") { put("Primary", "orbit-series-poster-v1") }
        putJsonArray("Genres") { add(JsonPrimitive("Science Fiction")) }
        put("Overview", "Fictional couriers exchange letters between research stations.")
        put("ProductionYear", 2025)
    },
    buildJsonObject {
        put("Id", "mock.movie.signal")
        put("Name", "Signal Garden")
        put("Type", "Movie")
        putJsonObject("ImageTags") { put("Primary", "signal-poster-v1") }
        putJsonArray("Genres") { add(JsonPrimitive("Adventure")) }
        put("Overview", "An entirely fictional radio garden wakes after the first rain.")
        put("ProductionYear", 2023)
    },
)

private fun embyVodEpisodes(): List<JsonObject> = listOf(
    buildJsonObject {
        put("Id", EMBY_VOD_EPISODE_ID)
        put("Name", "The First Envelope")
        put("Type", "Episode")
        putJsonObject("ImageTags") { put("Primary", "orbit-s01e01-v1") }
        put("SeriesName", "Orbital Letters")
        put("Overview", "A fictional courier receives an envelope with no return orbit.")
        put("ProductionYear", 2025)
        put("ParentIndexNumber", 1)
        put("IndexNumber", 1)
    },
    buildJsonObject {
        put("Id", "mock.episode.orbit.s01e02")
        put("Name", "Relay at Dawn")
        put("Type", "Episode")
        putJsonObject("ImageTags") { put("Primary", "orbit-s01e02-v1") }
        put("SeriesName", "Orbital Letters")
        put("Overview", "A fictional relay station answers before sunrise.")
        put("ProductionYear", 2025)
        put("ParentIndexNumber", 1)
        put("IndexNumber", 2)
    },
    buildJsonObject {
        put("Id", "mock.episode.orbit.s02e01")
        put("Name", "A New Constellation")
        put("Type", "Episode")
        putJsonObject("ImageTags") { put("Primary", "orbit-s02e01-v1") }
        put("SeriesName", "Orbital Letters")
        put("Overview", "The fictional route map gains one impossible constellation.")
        put("ProductionYear", 2026)
        put("ParentIndexNumber", 2)
        put("IndexNumber", 1)
    },
)

private fun embyContentPage(
    items: List<JsonObject>,
    pagination: EmbyPagination,
): JsonObject = buildJsonObject {
    put(
        "Items",
        JsonArray(items.drop(pagination.startIndex).take(pagination.limit)),
    )
    put("TotalRecordCount", items.size)
}

private fun embyVodMediaSourceId(itemId: String): String = "mock-vod-media-$itemId"

private fun embyVodPlaySessionId(itemId: String): String = "mock-vod-session-$itemId"

private fun embyVodPlaybackInfo(session: EmbyVodSession): JsonObject = buildJsonObject {
    put("PlaySessionId", session.playSessionId)
    putJsonArray("MediaSources") {
        add(
            buildJsonObject {
                put("Id", session.mediaSourceId)
                put("SupportsDirectPlay", true)
                put("SupportsDirectStream", false)
                put("SupportsTranscoding", true)
            },
        )
    }
}

private fun embyVodSessionState(session: EmbyVodSession): JsonObject = buildJsonObject {
    put("item_id", session.itemId)
    put("media_source_id", session.mediaSourceId)
    put("play_session_id", session.playSessionId)
    put("state", session.state.wireValue)
    put("start_count", session.startCount)
    put("progress_count", session.progressCount)
    put("stop_count", session.stopCount)
    put("last_position_ticks", session.lastPositionTicks)
    put("is_paused", session.isPaused)
    session.playMethod?.let { put("play_method", it) }
    session.lastEventName?.let { put("last_event_name", it) }
}

private fun livePlaylist(baseUrl: String): String = """
    #EXTM3U
    #EXTINF:-1 tvg-id="mock.news" tvg-name="Mock News" tvg-logo="$baseUrl/images/news.png" group-title="News",Mock News
    $baseUrl/hls/news/index.m3u8
    #EXTINF:-1 tvg-id="mock.sports" tvg-name="Mock Sports" tvg-logo="$baseUrl/images/sports.png" group-title="Sports",Mock Sports
    $baseUrl/hls/sports/index.m3u8
    #EXTINF:-1 tvg-id="mock.kids" tvg-name="Mock Kids" tvg-logo="$baseUrl/images/kids.png" group-title="Kids",Mock Kids
    $baseUrl/hls/kids/index.m3u8
""".trimIndent()

private fun mixedPlaylist(baseUrl: String): String = """
    #EXTM3U
    #EXTINF:-1 tvg-id="mock.news" tvg-name="Mock News" tvg-logo="$baseUrl/images/news.png" group-title="Live",Mock News
    $baseUrl/hls/news/index.m3u8
    #EXTINF:600 tvg-id="mock.movie" tvg-name="Mock Movie" tvg-logo="$baseUrl/images/movie.png" group-title="VOD",Mock Movie
    $baseUrl/movie/$DEFAULT_USERNAME/$DEFAULT_PASSWORD/2001.mp4
    #EXTINF:1200 tvg-id="mock.episode" tvg-name="Mock Episode" tvg-logo="$baseUrl/images/series.png" group-title="Series",Mock Series S01E01
    $baseUrl/series/$DEFAULT_USERNAME/$DEFAULT_PASSWORD/9001.mp4
""".trimIndent()

private fun hlsPlaylist(channel: String): String = """
    #EXTM3U
    #EXT-X-VERSION:3
    #EXT-X-TARGETDURATION:6
    #EXT-X-MEDIA-SEQUENCE:1
    #EXTINF:6.000,
    segment-1.ts
    #EXTINF:6.000,
    segment-2.ts
    #EXTINF:6.000,
    segment-3.ts
    #EXT-X-DISCONTINUITY
    #EXTINF:6.000,
    segment-4.ts
    #EXT-X-ENDLIST
    # $channel
""".trimIndent()

private fun xtreamInfo(baseUrl: String): JsonObject = buildJsonObject {
    putJsonObject("user_info") {
        put("username", DEFAULT_USERNAME)
        put("password", DEFAULT_PASSWORD)
        put("status", "Active")
        put("auth", 1)
        put("active_cons", "0")
        put("max_connections", "3")
        put("created_at", "1704067200")
        put("is_trial", "0")
        putJsonArray("allowed_output_formats") {
            add(JsonPrimitive("ts"))
            add(JsonPrimitive("m3u8"))
            add(JsonPrimitive("mp4"))
        }
    }
    putJsonObject("server_info") {
        put("url", baseUrl.removePrefix("http://").removePrefix("https://").substringBefore(":"))
        put("port", baseUrl.substringAfterLast(":", "8080"))
        put("server_protocol", baseUrl.substringBefore("://"))
        put("https_port", "8443")
        put("time_now", "2026-05-02 00:00:00")
        put("timestamp_now", "1777651200")
        put("timezone", "Asia/Shanghai")
    }
}

private fun xtreamUnauthorized(): JsonObject = buildJsonObject {
    putJsonObject("user_info") {
        put("auth", 0)
        put("status", "Disabled")
    }
}

private fun liveCategories(): JsonArray = categories(
    10 to "News",
    11 to "Sports",
    12 to "Kids"
)

private fun vodCategories(): JsonArray = categories(
    20 to "Movies",
    21 to "Documentaries"
)

private fun seriesCategories(): JsonArray = categories(
    30 to "Series",
    31 to "Learning"
)

private fun categories(vararg values: Pair<Int, String>): JsonArray = buildJsonArray {
    values.forEach { (id, name) ->
        add(
            buildJsonObject {
                put("category_id", id)
                put("category_name", name)
                put("parent_id", 0)
            }
        )
    }
}

private fun liveStreams(baseUrl: String): JsonArray = JsonArray(
    listOf(
        liveStream(1, 1001, 10, "Mock News", "$baseUrl/images/news.png", "mock.news"),
        liveStream(2, 1002, 11, "Mock Sports", "$baseUrl/images/sports.png", "mock.sports"),
        liveStream(3, 1003, 12, "Mock Kids", "$baseUrl/images/kids.png", "mock.kids")
    )
)

private fun vodStreams(baseUrl: String): JsonArray = JsonArray(
    listOf(
        vodStream(1, 2001, 20, "Mock Movie", "$baseUrl/images/movie.png"),
        vodStream(2, 2002, 21, "Mock Documentary", "$baseUrl/images/documentary.png")
    )
)

private fun seriesStreams(baseUrl: String): JsonArray = JsonArray(
    listOf(
        seriesStream(1, 3001, 30, "Mock Series", "$baseUrl/images/series.png"),
        seriesStream(2, 3002, 31, "Mock Course", "$baseUrl/images/course.png")
    )
)

private fun seriesInfo(seriesId: Int): JsonObject = buildJsonObject {
    putJsonObject("episodes") {
        put(
            "1",
            JsonArray(
                listOf(
                    episode(id = "9001", number = "1", title = "Pilot"),
                    episode(id = "9002", number = "2", title = "Second Source")
                )
            )
        )
        if (seriesId == 3002) {
            put("2", JsonArray(listOf(episode(id = "9010", number = "1", title = "Advanced Playback"))))
        }
    }
}

private fun liveStream(
    number: Int,
    streamId: Int,
    categoryId: Int,
    name: String,
    icon: String,
    epgId: String
) = buildJsonObject {
    put("num", number)
    put("name", name)
    put("stream_type", "live")
    put("stream_id", streamId)
    put("stream_icon", icon)
    put("epg_channel_id", epgId)
    put("category_id", categoryId)
    put("tv_archive", 0)
    put("tv_archive_duration", 0)
}

private fun vodStream(
    number: Int,
    streamId: Int,
    categoryId: Int,
    name: String,
    icon: String
) = buildJsonObject {
    put("num", number)
    put("name", name)
    put("stream_type", "movie")
    put("stream_id", streamId)
    put("stream_icon", icon)
    put("category_id", categoryId)
    put("container_extension", "mp4")
    put("rating", "7.8")
}

private fun seriesStream(
    number: Int,
    seriesId: Int,
    categoryId: Int,
    name: String,
    cover: String
) = buildJsonObject {
    put("num", number)
    put("name", name)
    put("series_id", seriesId)
    put("cover", cover)
    put("category_id", categoryId)
    put("episode_run_time", "42")
}

private fun episode(
    id: String,
    number: String,
    title: String
) = buildJsonObject {
    put("id", id)
    put("episode_num", number)
    put("title", title)
    put("container_extension", "mp4")
}

private fun transportStreamPlaceholder(channel: String, number: Int): ByteArray {
    val payload = "M3U mock transport stream: channel=$channel segment=$number\n".encodeToByteArray()
    return ByteArray(188 * 8) { index ->
        when {
            index % 188 == 0 -> 0x47
            index - 4 in payload.indices -> payload[index - 4]
            else -> 0xFF.toByte()
        }
    }
}

private fun mp4Placeholder(id: String): ByteArray =
    "M3U mock MP4 placeholder: id=$id\n".encodeToByteArray()
