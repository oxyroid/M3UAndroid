package com.m3u.stability.receiver

import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.ServerSocket
import java.net.URL
import java.util.zip.GZIPOutputStream
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CrashReceiverEndpointTest {
    @Test
    fun `accepts bounded safe reports and exposes status only with the admin token`() {
        val directory = createTempDirectory("m3u-crash-endpoint-test")
        val port = ServerSocket(0).use { socket -> socket.localPort }
        val store = CrashStore(directory.resolve("state.json"))
        val service = CrashReceiverService(
            store = store,
            dispatcher = AlertDispatcher(store) { error("offline") },
            adminToken = "admin-test-token",
        )
        val server = embeddedServer(
            factory = Netty,
            host = "127.0.0.1",
            port = port,
            module = { crashReceiverModule(service) },
        ).start(wait = false)
        val baseUrl = "http://127.0.0.1:$port"
        try {
            assertEquals(200, request("$baseUrl/health").statusCode)
            assertEquals(404, request("$baseUrl/internal/status").statusCode)
            assertEquals(
                404,
                request(
                    "$baseUrl/internal/status",
                    headers = mapOf("Authorization" to "Bearer wrong"),
                ).statusCode,
            )
            assertEquals(
                400,
                request(
                    url = "$baseUrl/reports",
                    method = "POST",
                    headers = reportHeaders() - "Content-Encoding",
                    body = validReport().encodeToByteArray(),
                ).statusCode,
            )
            assertEquals(
                400,
                request(
                    url = "$baseUrl/reports",
                    method = "POST",
                    headers = reportHeaders(),
                    body = gzip(validReport(extraReportField = "\"USER_EMAIL\":\"secret\",")),
                ).statusCode,
            )
            assertEquals(
                202,
                request(
                    url = "$baseUrl/reports",
                    method = "POST",
                    headers = reportHeaders(),
                    body = gzip(validReport()),
                ).statusCode,
            )

            val status = request(
                "$baseUrl/internal/status",
                headers = mapOf("Authorization" to "Bearer admin-test-token"),
            )
            assertEquals(200, status.statusCode)
            val statusBody = status.body.decodeToString()
            assertTrue(statusBody.contains("\"acceptedReportCount\": 1"))
            assertFalse(statusBody.contains("admin-test-token"))
            assertFalse(statusBody.contains("Probe.kt"))
        } finally {
            server.stop(gracePeriodMillis = 100, timeoutMillis = 1_000)
            directory.toFile().deleteRecursively()
        }
    }

    @Test
    fun `rejects a compressed request over the declared limit before reading it`() {
        val directory = createTempDirectory("m3u-crash-limit-test")
        val port = ServerSocket(0).use { socket -> socket.localPort }
        val store = CrashStore(directory.resolve("state.json"))
        val server = embeddedServer(
            factory = Netty,
            host = "127.0.0.1",
            port = port,
            module = {
                crashReceiverModule(
                    CrashReceiverService(
                        store = store,
                        dispatcher = AlertDispatcher(store) { },
                        adminToken = null,
                    )
                )
            },
        ).start(wait = false)
        try {
            val oversized = ByteArray(MAX_COMPRESSED_REPORT_BYTES + 1)
            assertEquals(
                413,
                request(
                    url = "http://127.0.0.1:$port/reports",
                    method = "POST",
                    headers = reportHeaders(),
                    body = oversized,
                ).statusCode,
            )
        } finally {
            server.stop(gracePeriodMillis = 100, timeoutMillis = 1_000)
            directory.toFile().deleteRecursively()
        }
    }

    private fun reportHeaders(): Map<String, String> = mapOf(
        "Content-Type" to "application/json",
        "Content-Encoding" to "gzip",
        "X-M3U-Report-Schema" to REPORT_SCHEMA,
    )

    private fun gzip(value: String): ByteArray = ByteArrayOutputStream().use { bytes ->
        GZIPOutputStream(bytes).use { gzip -> gzip.write(value.encodeToByteArray()) }
        bytes.toByteArray()
    }

    private fun request(
        url: String,
        method: String = "GET",
        headers: Map<String, String> = emptyMap(),
        body: ByteArray? = null,
    ): HttpResponse {
        val connection = URL(url).openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = method
            connection.connectTimeout = 2_000
            connection.readTimeout = 2_000
            headers.forEach(connection::setRequestProperty)
            if (body != null) {
                connection.doOutput = true
                connection.outputStream.use { output -> output.write(body) }
            }
            val statusCode = connection.responseCode
            val stream = if (statusCode >= 400) connection.errorStream else connection.inputStream
            HttpResponse(
                statusCode = statusCode,
                body = stream?.use { it.readBytes() } ?: byteArrayOf(),
            )
        } finally {
            connection.disconnect()
        }
    }

    private data class HttpResponse(
        val statusCode: Int,
        val body: ByteArray,
    )
}
