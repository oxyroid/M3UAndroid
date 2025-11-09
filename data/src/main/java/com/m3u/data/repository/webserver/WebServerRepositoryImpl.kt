package com.m3u.data.repository.webserver

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.cio.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import kotlin.time.Duration.Companion.minutes
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import com.m3u.data.repository.playlist.PlaylistRepository
import timber.log.Timber
import java.io.File
import java.net.InetAddress
import java.net.NetworkInterface
import javax.inject.Inject
import javax.inject.Singleton

private const val MAX_FILE_SIZE_BYTES = 400L * 1024 * 1024 // 400 MB

@Serializable
data class UrlImportRequest(
    val url: String,
    val title: String
)

@Serializable
data class XtreamImportRequest(
    val title: String,
    val basicUrl: String,
    val username: String,
    val password: String,
    val type: String? = null
)

@Serializable
data class EpgImportRequest(
    val url: String,
    val name: String
)

@Serializable
data class UploadResponse(
    val success: Boolean,
    val message: String,
    val count: Int = 0,
    val error: String? = null
)

@Singleton
internal class WebServerRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val playlistRepository: PlaylistRepository
) : WebServerRepository {

    private val timber = Timber.tag("WebServerRepository")

    private val _state = MutableStateFlow(WebServerState())
    override val state: StateFlow<WebServerState> = _state.asStateFlow()

    private var server: EmbeddedServer<*, *>? = null

    // Background scope for long-running imports that shouldn't be cancelled
    private val backgroundScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override suspend fun start(port: Int): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (server != null) {
                return@withContext Result.failure(IllegalStateException("Server is already running"))
            }

            val ipAddress = getLocalIpAddress()
            if (ipAddress == null) {
                return@withContext Result.failure(Exception("Could not determine local IP address"))
            }

            val embeddedServerInstance = embeddedServer(CIO, port = port, host = "0.0.0.0") {
                install(ContentNegotiation) {
                    json(Json {
                        prettyPrint = true
                        ignoreUnknownKeys = true
                    })
                }
                install(CORS) {
                    anyHost()
                    allowHeader(HttpHeaders.ContentType)
                }
                configureRouting()
            }
            embeddedServerInstance.start(wait = false)
            server = embeddedServerInstance

            _state.value = WebServerState(
                isRunning = true,
                ipAddress = ipAddress,
                port = port,
                error = null
            )

            timber.d("Web server started at http://$ipAddress:$port")
            Result.success(Unit)
        } catch (e: Exception) {
            timber.e(e, "Failed to start web server")
            _state.value = WebServerState(
                isRunning = false,
                error = e.message
            )
            Result.failure(e)
        }
    }

    override suspend fun stop(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            server?.stop(1000, 2000)
            server = null
            _state.value = WebServerState(isRunning = false)
            timber.d("Web server stopped")
            Result.success(Unit)
        } catch (e: Exception) {
            timber.e(e, "Failed to stop web server")
            Result.failure(e)
        }
    }

    override fun isRunning(): Boolean = server != null

    private fun Application.configureRouting() {
        routing {
            // Serve HTML upload page
            get("/") {
                val html = this::class.java.classLoader?.getResourceAsStream("upload.html")?.use { it.readBytes() }
                    ?: throw Exception("Could not load upload.html")
                call.respondBytes(html, ContentType.Text.Html)
            }

            // Status endpoint
            get("/status") {
                call.respond(
                    mapOf(
                        "server" to "M3U Android",
                        "version" to "1.0.0",
                        "ip" to (_state.value.ipAddress ?: "unknown"),
                        "port" to _state.value.port,
                        "playlists" to (playlistRepository.getAll().size)
                    )
                )
            }

            // File upload endpoint
            post("/upload") {
                try {
                    val multipart = call.receiveMultipart()
                    var fileBytes: ByteArray? = null
                    var filename: String? = null
                    var title: String? = null

                    multipart.forEachPart { part ->
                        when (part) {
                            is PartData.FormItem -> {
                                if (part.name == "title") {
                                    title = part.value
                                }
                            }
                            is PartData.FileItem -> {
                                filename = part.originalFileName ?: "playlist.m3u"
                                fileBytes = part.streamProvider().readBytes()
                            }
                            else -> {}
                        }
                        part.dispose()
                    }

                    if (fileBytes == null) {
                        call.respond(
                            HttpStatusCode.BadRequest,
                            UploadResponse(
                                success = false,
                                message = "No file uploaded",
                                error = "Missing file"
                            )
                        )
                        return@post
                    }

                    // Validate file size (400 MB max)
                    if (fileBytes!!.size > MAX_FILE_SIZE_BYTES) {
                        call.respond(
                            HttpStatusCode.PayloadTooLarge,
                            UploadResponse(
                                success = false,
                                message = "File too large",
                                error = "Maximum file size is 400 MB. Your file is ${fileBytes!!.size / (1024 * 1024)} MB"
                            )
                        )
                        return@post
                    }

                    // Save file temporarily
                    val tempFile = File(context.cacheDir, filename ?: "upload_${System.currentTimeMillis()}.m3u")
                    tempFile.writeBytes(fileBytes!!)

                    // Import using existing repository method
                    val playlistTitle = title ?: filename?.replace(Regex("\\.(m3u|m3u8)$", RegexOption.IGNORE_CASE), "") ?: "Uploaded Playlist"
                    var channelCount = 0

                    playlistRepository.m3uOrThrow(
                        title = playlistTitle,
                        url = tempFile.toURI().toString(),
                        callback = { count -> channelCount = count }
                    )

                    // Clean up temp file
                    tempFile.delete()

                    call.respond(
                        UploadResponse(
                            success = true,
                            message = "Playlist imported successfully",
                            count = channelCount
                        )
                    )
                    timber.d("File upload successful: $playlistTitle with $channelCount channels")
                } catch (e: Exception) {
                    timber.e(e, "File upload failed")
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        UploadResponse(
                            success = false,
                            message = "Upload failed",
                            error = e.message
                        )
                    )
                }
            }

            // URL import endpoint
            post("/import-url") {
                try {
                    val request = call.receive<UrlImportRequest>()
                    var channelCount = 0

                    playlistRepository.m3uOrThrow(
                        title = request.title,
                        url = request.url,
                        callback = { count -> channelCount = count }
                    )

                    call.respond(
                        UploadResponse(
                            success = true,
                            message = "Playlist imported from URL",
                            count = channelCount
                        )
                    )
                    timber.d("URL import successful: ${request.title} with $channelCount channels")
                } catch (e: Exception) {
                    timber.e(e, "URL import failed")
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        UploadResponse(
                            success = false,
                            message = "URL import failed",
                            error = e.message
                        )
                    )
                }
            }

            // Xtream codes import endpoint
            post("/import-xtream") {
                try {
                    val request = call.receive<XtreamImportRequest>()
                    var channelCount = 0

                    playlistRepository.xtreamOrThrow(
                        title = request.title,
                        basicUrl = request.basicUrl,
                        username = request.username,
                        password = request.password,
                        type = request.type,
                        callback = { count -> channelCount = count }
                    )

                    call.respond(
                        UploadResponse(
                            success = true,
                            message = "Xtream playlist imported successfully",
                            count = channelCount
                        )
                    )
                    timber.d("Xtream import successful: ${request.title} with $channelCount channels")
                } catch (e: Exception) {
                    timber.e(e, "Xtream import failed")
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        UploadResponse(
                            success = false,
                            message = "Xtream import failed",
                            error = e.message
                        )
                    )
                }
            }

            // EPG source import endpoint
            post("/import-epg") {
                try {
                    val request = call.receive<EpgImportRequest>()

                    playlistRepository.insertEpgAsPlaylist(
                        title = request.name,
                        epg = request.url
                    )

                    call.respond(
                        UploadResponse(
                            success = true,
                            message = "EPG source added successfully"
                        )
                    )
                    timber.d("EPG import successful: ${request.name} at ${request.url}")
                } catch (e: Exception) {
                    timber.e(e, "EPG import failed")
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        UploadResponse(
                            success = false,
                            message = "EPG import failed",
                            error = e.message
                        )
                    )
                }
            }
        }
    }

    private fun getLocalIpAddress(): String? {
        try {
            // Check if running in Android emulator
            val isEmulator = android.os.Build.FINGERPRINT.contains("generic") ||
                            android.os.Build.FINGERPRINT.contains("emulator") ||
                            android.os.Build.MODEL.contains("Emulator") ||
                            android.os.Build.MODEL.contains("Android SDK")

            if (isEmulator) {
                // For emulator, return localhost which will work with adb port forwarding
                // User needs to run: adb forward tcp:8080 tcp:8080
                return "localhost"
            }

            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                val addresses = networkInterface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val address = addresses.nextElement()
                    if (!address.isLoopbackAddress && address is InetAddress && address.hostAddress?.contains(":") == false) {
                        return address.hostAddress
                    }
                }
            }
        } catch (e: Exception) {
            timber.e(e, "Failed to get local IP address")
        }
        return null
    }
}
