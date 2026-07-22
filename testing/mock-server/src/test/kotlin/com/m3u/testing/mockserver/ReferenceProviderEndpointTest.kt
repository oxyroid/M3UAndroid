package com.m3u.testing.mockserver

import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import java.net.HttpURLConnection
import java.net.ServerSocket
import java.net.URL
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class ReferenceProviderEndpointTest {
    @Test
    fun `reference provider enforces token and tracks session lifecycle`() {
        val port = ServerSocket(0).use { socket -> socket.localPort }
        val server = embeddedServer(
            factory = Netty,
            host = "127.0.0.1",
            port = port,
            module = io.ktor.server.application.Application::mockServerModule,
        ).start(wait = false)
        val baseUrl = "http://127.0.0.1:$port"
        try {
            assertEquals(
                401,
                request("$baseUrl/reference-provider/channels").statusCode,
            )

            val login = request(
                url = "$baseUrl/reference-provider/login",
                method = "POST",
                headers = mapOf("Content-Type" to "application/json"),
                body = """{"username":"m3u","password":"reference-password"}""",
            )
            assertEquals(200, login.statusCode)
            val loginPayload = login.jsonBody()
            val token = loginPayload["accessToken"]?.jsonPrimitive?.content
            assertEquals("mock-reference-access-token", token)
            val authorization = mapOf(
                "X-Emby-Token" to requireNotNull(token),
                "X-Reference-User" to requireNotNull(
                    loginPayload["user_id"]?.jsonPrimitive?.content
                ),
            )

            val channels = request(
                "$baseUrl/reference-provider/channels",
                headers = authorization,
            )
            assertEquals(200, channels.statusCode)
            assertEquals(2, channels.jsonBody()["channels"]?.jsonArray?.size)

            val playback = request(
                "$baseUrl/reference-provider/playback/reference.news",
                headers = authorization,
            )
            assertEquals(200, playback.statusCode)
            val playbackJson = playback.jsonBody()
            val playSessionId = requireNotNull(
                playbackJson["play_session_id"]?.jsonPrimitive?.content
            )
            val liveStreamId = requireNotNull(
                playbackJson["live_stream_id"]?.jsonPrimitive?.content
            )
            val streamUrl = requireNotNull(playbackJson["url"]?.jsonPrimitive?.content)

            assertEquals(401, request(streamUrl).statusCode)
            assertEquals(200, request(streamUrl, headers = authorization).statusCode)
            assertEquals(
                "open",
                request(
                    "$baseUrl/reference-provider/sessions/$playSessionId",
                    headers = authorization,
                ).jsonBody()["state"]?.jsonPrimitive?.content,
            )

            val close = request(
                url = "$baseUrl/reference-provider/sessions/close",
                method = "POST",
                headers = authorization + ("Content-Type" to "application/json"),
                body = """{"item_id":"reference.news","play_session_id":"$playSessionId","live_stream_id":"$liveStreamId","reason":"stopped"}""",
            )
            assertEquals(200, close.statusCode)
            assertEquals(true, close.jsonBody()["closed"]?.jsonPrimitive?.content?.toBoolean())
            assertEquals(
                "closed",
                request(
                    "$baseUrl/reference-provider/sessions/$playSessionId",
                    headers = authorization,
                ).jsonBody()["state"]?.jsonPrimitive?.content,
            )
        } finally {
            server.stop(gracePeriodMillis = 100, timeoutMillis = 1_000)
        }
    }

    private fun request(
        url: String,
        method: String = "GET",
        headers: Map<String, String> = emptyMap(),
        body: String? = null,
    ): HttpResponse {
        val connection = URL(url).openConnection() as HttpURLConnection
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
            HttpResponse(statusCode, stream?.bufferedReader()?.use { it.readText() }.orEmpty())
        } finally {
            connection.disconnect()
        }
    }

    private fun HttpResponse.jsonBody() = Json.parseToJsonElement(body).jsonObject

    private data class HttpResponse(
        val statusCode: Int,
        val body: String,
    )
}
