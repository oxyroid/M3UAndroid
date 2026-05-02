package com.m3u.tv.remote

import android.content.Context
import android.util.Log
import androidx.work.WorkManager
import com.m3u.data.worker.SubscriptionWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

object TvSubscribeServer {
    private const val TAG = "TvSubscribeServer"
    private const val DEFAULT_PORT = 8989
    private const val SUBSCRIBE_PATH = "/subscribe/m3u"
    private const val HEALTH_PATH = "/health"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val running = AtomicBoolean(false)
    private var serverSocket: ServerSocket? = null

    fun start(context: Context, port: Int = DEFAULT_PORT) {
        if (!running.compareAndSet(false, true)) return
        val appContext = context.applicationContext
        scope.launch {
            runCatching {
                ServerSocket().use { socket ->
                    serverSocket = socket
                    socket.reuseAddress = true
                    socket.bind(InetSocketAddress("0.0.0.0", port))
                    Log.i(TAG, "Listening on $port")
                    while (running.get()) {
                        val client = socket.accept()
                        scope.launch { handleClient(appContext, client) }
                    }
                }
            }.onFailure { error ->
                running.set(false)
                Log.e(TAG, "Server stopped", error)
            }
        }
    }

    fun stop() {
        running.set(false)
        runCatching { serverSocket?.close() }
        serverSocket = null
    }

    private fun handleClient(context: Context, socket: Socket) {
        socket.use { client ->
            val reader = BufferedReader(InputStreamReader(client.getInputStream(), StandardCharsets.UTF_8))
            val writer = BufferedWriter(OutputStreamWriter(client.getOutputStream(), StandardCharsets.UTF_8))
            val requestLine = reader.readLine().orEmpty()
            val request = HttpRequest.parse(requestLine)
                ?: return writer.respond(400, """{"error":"bad_request"}""")

            val headers = readHeaders(reader)
            val contentLength = headers["content-length"]?.toIntOrNull() ?: 0
            val body = if (contentLength > 0) {
                CharArray(contentLength).also { reader.read(it, 0, contentLength) }.concatToString()
            } else {
                ""
            }

            when {
                request.method == "GET" && request.path == HEALTH_PATH -> {
                    writer.respond(200, """{"status":"ok"}""")
                }

                request.method == "POST" && request.path == SUBSCRIBE_PATH -> {
                    val params = parseParams(request.query) + parseParams(body)
                    val title = params["title"].orEmpty().ifBlank { "Remote M3U" }
                    val url = params["url"].orEmpty()
                    if (url.isBlank()) {
                        writer.respond(422, """{"error":"missing_url"}""")
                        return
                    }

                    SubscriptionWorker.m3u(
                        workManager = WorkManager.getInstance(context),
                        title = title,
                        url = url
                    )
                    Log.i(TAG, "Accepted M3U subscription '$title' from ${client.inetAddress.hostAddress}")
                    writer.respond(202, """{"status":"accepted","title":${title.jsonString()}}""")
                }

                else -> writer.respond(404, """{"error":"not_found"}""")
            }
        }
    }

    private fun readHeaders(reader: BufferedReader): Map<String, String> {
        val headers = linkedMapOf<String, String>()
        while (true) {
            val line = reader.readLine() ?: break
            if (line.isEmpty()) break
            val separator = line.indexOf(':')
            if (separator <= 0) continue
            val name = line.substring(0, separator).trim().lowercase(Locale.US)
            val value = line.substring(separator + 1).trim()
            headers[name] = value
        }
        return headers
    }

    private fun parseParams(source: String): Map<String, String> {
        if (source.isBlank()) return emptyMap()
        return source
            .split('&')
            .mapNotNull { pair ->
                val separator = pair.indexOf('=')
                if (separator <= 0) return@mapNotNull null
                val key = pair.substring(0, separator).formDecode()
                val value = pair.substring(separator + 1).formDecode()
                key to value
            }
            .toMap()
    }

    private fun BufferedWriter.respond(status: Int, body: String) {
        val reason = when (status) {
            200 -> "OK"
            202 -> "Accepted"
            400 -> "Bad Request"
            404 -> "Not Found"
            422 -> "Unprocessable Content"
            else -> "OK"
        }
        write("HTTP/1.1 $status $reason\r\n")
        write("Content-Type: application/json; charset=utf-8\r\n")
        write("Content-Length: ${body.toByteArray(StandardCharsets.UTF_8).size}\r\n")
        write("Connection: close\r\n")
        write("\r\n")
        write(body)
        flush()
    }

    private fun String.formDecode(): String =
        URLDecoder.decode(this, StandardCharsets.UTF_8.name())

    private fun String.jsonString(): String =
        "\"" + replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\""

    private data class HttpRequest(
        val method: String,
        val path: String,
        val query: String
    ) {
        companion object {
            fun parse(line: String): HttpRequest? {
                val parts = line.split(' ')
                if (parts.size < 2) return null
                val target = parts[1]
                val queryStart = target.indexOf('?')
                val path = if (queryStart >= 0) target.substring(0, queryStart) else target
                val query = if (queryStart >= 0) target.substring(queryStart + 1) else ""
                return HttpRequest(parts[0].uppercase(Locale.US), path, query)
            }
        }
    }
}
