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
    fun `reference provider serves authenticated deterministic media and tracks sessions`() {
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
            assertEquals(
                "$baseUrl/reference-provider/stream/reference.news/sample.wav",
                streamUrl,
            )

            assertEquals(401, request(streamUrl).statusCode)
            assertEquals(
                401,
                request(
                    streamUrl,
                    headers = mapOf("X-Emby-Token" to requireNotNull(token)),
                ).statusCode,
            )
            val media = request(streamUrl, headers = authorization)
            assertEquals(200, media.statusCode)
            assertEquals("audio/wav", media.contentType)
            media.assertReferenceWav()
            val openSession = request(
                "$baseUrl/reference-provider/sessions/$playSessionId",
                headers = authorization,
            ).jsonBody()
            assertEquals("open", openSession["state"]?.jsonPrimitive?.content)
            assertEquals(0, openSession["close_count"]?.jsonPrimitive?.content?.toInt())
            assertEquals(0, openSession["update_count"]?.jsonPrimitive?.content?.toInt())
            assertEquals(
                0L,
                openSession["last_position_ticks"]?.jsonPrimitive?.content?.toLong(),
            )
            assertEquals(null, openSession["last_event"])

            val updateBody =
                """{"item_id":"reference.news","play_session_id":"$playSessionId","live_stream_id":"$liveStreamId","event":"progress","position_ticks":12345,"play_method":"direct_play","is_paused":false}"""
            assertEquals(
                401,
                request(
                    url = "$baseUrl/reference-provider/sessions/update",
                    method = "POST",
                    headers = authorization - "X-Reference-User" +
                        ("Content-Type" to "application/json"),
                    body = updateBody,
                ).statusCode,
            )
            assertEquals(
                400,
                request(
                    url = "$baseUrl/reference-provider/sessions/update",
                    method = "POST",
                    headers = authorization + ("Content-Type" to "application/json"),
                    body =
                        """{"play_session_id":"$playSessionId","item_id":"reference.news","live_stream_id":"$liveStreamId","event":"progress","position_ticks":12345,"play_method":"direct_play","is_paused":false}""",
                ).statusCode,
            )
            assertEquals(
                400,
                request(
                    url = "$baseUrl/reference-provider/sessions/update",
                    method = "POST",
                    headers = authorization + ("Content-Type" to "application/json"),
                    body = updateBody.dropLast(1) + ""","unexpected":true}""",
                ).statusCode,
            )
            val update = request(
                url = "$baseUrl/reference-provider/sessions/update",
                method = "POST",
                headers = authorization + ("Content-Type" to "application/json"),
                body = updateBody,
            )
            assertEquals(200, update.statusCode)
            val updateResult = update.jsonBody()
            assertEquals(true, updateResult["accepted"]?.jsonPrimitive?.content?.toBoolean())
            assertEquals(1, updateResult["update_count"]?.jsonPrimitive?.content?.toInt())
            assertEquals(
                12_345L,
                updateResult["last_position_ticks"]?.jsonPrimitive?.content?.toLong(),
            )
            assertEquals("progress", updateResult["last_event"]?.jsonPrimitive?.content)
            val updatedSession = request(
                "$baseUrl/reference-provider/sessions/$playSessionId",
                headers = authorization,
            ).jsonBody()
            assertEquals(1, updatedSession["update_count"]?.jsonPrimitive?.content?.toInt())
            assertEquals(
                12_345L,
                updatedSession["last_position_ticks"]?.jsonPrimitive?.content?.toLong(),
            )
            assertEquals("progress", updatedSession["last_event"]?.jsonPrimitive?.content)

            val close = request(
                url = "$baseUrl/reference-provider/sessions/close",
                method = "POST",
                headers = authorization + ("Content-Type" to "application/json"),
                body = """{"item_id":"reference.news","play_session_id":"$playSessionId","live_stream_id":"$liveStreamId","reason":"stopped"}""",
            )
            assertEquals(200, close.statusCode)
            assertEquals(true, close.jsonBody()["closed"]?.jsonPrimitive?.content?.toBoolean())
            val closedSession = request(
                "$baseUrl/reference-provider/sessions/$playSessionId",
                headers = authorization,
            ).jsonBody()
            assertEquals("closed", closedSession["state"]?.jsonPrimitive?.content)
            assertEquals(1, closedSession["close_count"]?.jsonPrimitive?.content?.toInt())
            assertEquals(
                "stopped",
                closedSession["last_close_reason"]?.jsonPrimitive?.content,
            )
            assertEquals(1, closedSession["update_count"]?.jsonPrimitive?.content?.toInt())
            assertEquals(
                12_345L,
                closedSession["last_position_ticks"]?.jsonPrimitive?.content?.toLong(),
            )
            assertEquals("progress", closedSession["last_event"]?.jsonPrimitive?.content)
            assertEquals(
                409,
                request(
                    url = "$baseUrl/reference-provider/sessions/update",
                    method = "POST",
                    headers = authorization + ("Content-Type" to "application/json"),
                    body = updateBody,
                ).statusCode,
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
            HttpResponse(
                statusCode = statusCode,
                contentType = connection.contentType?.substringBefore(';'),
                body = stream?.use { it.readBytes() } ?: byteArrayOf(),
            )
        } finally {
            connection.disconnect()
        }
    }

    private fun HttpResponse.jsonBody() = Json.parseToJsonElement(body.decodeToString()).jsonObject

    private fun HttpResponse.assertReferenceWav() {
        assertEquals(160_044, body.size)
        assertEquals("RIFF", body.ascii(offset = 0, length = 4))
        assertEquals(body.size - 8, body.littleEndianInt(offset = 4))
        assertEquals("WAVE", body.ascii(offset = 8, length = 4))
        assertEquals("fmt ", body.ascii(offset = 12, length = 4))
        assertEquals(16, body.littleEndianInt(offset = 16))
        assertEquals(1, body.littleEndianShort(offset = 20))
        assertEquals(1, body.littleEndianShort(offset = 22))
        assertEquals(8_000, body.littleEndianInt(offset = 24))
        assertEquals(16_000, body.littleEndianInt(offset = 28))
        assertEquals(2, body.littleEndianShort(offset = 32))
        assertEquals(16, body.littleEndianShort(offset = 34))
        assertEquals("data", body.ascii(offset = 36, length = 4))
        assertEquals(body.size - 44, body.littleEndianInt(offset = 40))
        assertEquals(64, body.littleEndianSignedShort(offset = 44))
        assertEquals(-64, body.littleEndianSignedShort(offset = 60))
    }

    private fun ByteArray.ascii(offset: Int, length: Int): String =
        copyOfRange(offset, offset + length).decodeToString()

    private fun ByteArray.littleEndianInt(offset: Int): Int =
        (this[offset].toInt() and 0xff) or
            ((this[offset + 1].toInt() and 0xff) shl 8) or
            ((this[offset + 2].toInt() and 0xff) shl 16) or
            ((this[offset + 3].toInt() and 0xff) shl 24)

    private fun ByteArray.littleEndianShort(offset: Int): Int =
        (this[offset].toInt() and 0xff) or
            ((this[offset + 1].toInt() and 0xff) shl 8)

    private fun ByteArray.littleEndianSignedShort(offset: Int): Int =
        littleEndianShort(offset).toShort().toInt()

    private data class HttpResponse(
        val statusCode: Int,
        val contentType: String?,
        val body: ByteArray,
    )
}
