package com.m3u.testing.mockserver

import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.ServerSocket
import java.net.URL
import java.util.zip.GZIPOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class CrashReportEndpointTest {
    @Test
    fun `receiver accepts only gzip schema one reports with safe fields`() {
        val port = ServerSocket(0).use { socket -> socket.localPort }
        val server = embeddedServer(
            factory = Netty,
            host = "127.0.0.1",
            port = port,
            module = io.ktor.server.application.Application::mockServerModule,
        ).start(wait = false)
        val endpoint = "http://127.0.0.1:$port/crash-reports"
        try {
            assertEquals(404, request("$endpoint/latest").statusCode)
            assertEquals(
                400,
                request(
                    url = endpoint,
                    method = "POST",
                    headers = crashHeaders() - "Content-Encoding",
                    body = safeReport().encodeToByteArray(),
                ).statusCode,
            )
            assertEquals(
                400,
                request(
                    url = endpoint,
                    method = "POST",
                    headers = crashHeaders() + ("X-M3U-Report-Schema" to "2"),
                    body = gzip(safeReport()),
                ).statusCode,
            )
            assertEquals(
                400,
                request(
                    url = endpoint,
                    method = "POST",
                    headers = crashHeaders(),
                    body = gzip(safeReport(extraField = "\"BUILD_CONFIG\":{\"PASSWORD\":\"secret\"},")),
                ).statusCode,
            )

            assertEquals(
                202,
                request(
                    url = endpoint,
                    method = "POST",
                    headers = crashHeaders(),
                    body = gzip(safeReport()),
                ).statusCode,
            )
            val stored = request("$endpoint/latest")
            assertEquals(200, stored.statusCode)
            val body = stored.body.decodeToString()
            val storedReport = Json.parseToJsonElement(body).jsonObject
            assertTrue(body.contains("DebugCrashProbeException"))
            assertEquals(
                "app",
                storedReport["CUSTOM_DATA"]?.jsonObject?.get("feature")?.jsonPrimitive?.content,
            )
            assertFalse(body.contains("password"))
            assertFalse(body.contains("secret"))

            assertEquals(204, request(endpoint, method = "DELETE").statusCode)
            assertEquals(404, request("$endpoint/latest").statusCode)
        } finally {
            server.stop(gracePeriodMillis = 100, timeoutMillis = 1_000)
        }
    }

    private fun safeReport(extraField: String = ""): String =
        """{"REPORT_ID":"probe","APP_VERSION_CODE":145,"APP_VERSION_NAME":"1.15.1","PACKAGE_NAME":"com.m3u.smartphone",${extraField}"STACK_TRACE":"DebugCrashProbeException\n\tat Probe.run(Probe.kt:1)\n","STACK_TRACE_HASH":"probe-hash","CUSTOM_DATA":{"feature":"app"}}"""

    private fun crashHeaders(): Map<String, String> = mapOf(
        "Content-Type" to "application/json",
        "Content-Encoding" to "gzip",
        "X-M3U-Report-Schema" to "1",
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
                contentType = connection.contentType?.substringBefore(';'),
                body = stream?.use { it.readBytes() } ?: byteArrayOf(),
            )
        } finally {
            connection.disconnect()
        }
    }

    private data class HttpResponse(
        val statusCode: Int,
        val contentType: String?,
        val body: ByteArray,
    )
}
