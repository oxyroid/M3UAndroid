package com.m3u.stability.receiver

import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.application.call
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.request.receiveChannel
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.utils.io.readAvailable
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.time.Clock
import java.util.concurrent.atomic.AtomicLong
import java.util.zip.GZIPInputStream
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

fun main() {
    val configuration = ReceiverConfiguration.fromEnvironment()
    val store = CrashStore(configuration.stateFile)
    val alertSink = when (val alert = configuration.alertConfiguration) {
        AlertConfiguration.Stdout -> StdoutAlertSink()
        is AlertConfiguration.Smtp -> SmtpAlertSink(alert)
    }
    val dispatcher = AlertDispatcher(store, alertSink)
    val service = CrashReceiverService(
        store = store,
        dispatcher = dispatcher,
        adminToken = configuration.adminToken,
    )
    embeddedServer(
        factory = Netty,
        host = configuration.host,
        port = configuration.port,
        module = { crashReceiverModule(service) },
    ).start(wait = true)
}

internal fun Application.crashReceiverModule(service: CrashReceiverService) {
    service.start()
    monitor.subscribe(ApplicationStopped) { service.close() }
    routing {
        get("/health") {
            call.respondText("ok", ContentType.Text.Plain)
        }
        get("/ready") {
            if (service.isDeliveryReady()) {
                call.respondText("ready", ContentType.Text.Plain)
            } else {
                call.respondText(
                    text = "delivery degraded",
                    contentType = ContentType.Text.Plain,
                    status = HttpStatusCode.ServiceUnavailable,
                )
            }
        }
        get("/internal/status") {
            if (!service.isAdminAuthorized(call.request.headers[HttpHeaders.Authorization])) {
                call.respond(HttpStatusCode.NotFound)
                return@get
            }
            call.respondText(
                text = responseJson.encodeToString(service.status()),
                contentType = ContentType.Application.Json,
            )
        }
        post("/internal/test-alert") {
            if (!service.isAdminAuthorized(call.request.headers[HttpHeaders.Authorization])) {
                call.respond(HttpStatusCode.NotFound)
                return@post
            }
            val alert = service.enqueueDeliveryTest()
            if (alert == null) {
                call.respondText(
                    text = "alert queue is full",
                    contentType = ContentType.Text.Plain,
                    status = HttpStatusCode.ServiceUnavailable,
                )
                return@post
            }
            call.respondText(
                text = alert.id,
                contentType = ContentType.Text.Plain,
                status = HttpStatusCode.Accepted,
            )
        }
        post("/reports") {
            when (val result = service.receive(call)) {
                is ReceiveResult.Accepted -> call.respondText(
                    text = "accepted",
                    contentType = ContentType.Text.Plain,
                    status = HttpStatusCode.Accepted,
                )
                is ReceiveResult.Rejected -> call.respondText(
                    text = result.reason,
                    contentType = ContentType.Text.Plain,
                    status = result.status,
                )
            }
        }
    }
}

internal class CrashReceiverService(
    private val store: CrashStore,
    private val dispatcher: AlertDispatcher,
    private val adminToken: String?,
    private val rateLimiter: GlobalRateLimiter = GlobalRateLimiter(Clock.systemUTC()),
) : AutoCloseable {
    fun start() = dispatcher.start()

    suspend fun receive(call: ApplicationCall): ReceiveResult {
        if (!rateLimiter.tryAcquire()) {
            return ReceiveResult.Rejected(
                status = HttpStatusCode.ServiceUnavailable,
                reason = "receiver is busy",
            )
        }
        if (!call.hasValidReportHeaders()) {
            return ReceiveResult.Rejected(HttpStatusCode.BadRequest, "invalid report headers")
        }
        val declaredLength = call.request.headers[HttpHeaders.ContentLength]?.toLongOrNull()
        if (declaredLength != null && declaredLength > MAX_COMPRESSED_REPORT_BYTES) {
            return ReceiveResult.Rejected(HttpStatusCode.PayloadTooLarge, "report is too large")
        }
        val compressed = call.receiveBounded(MAX_COMPRESSED_REPORT_BYTES)
            ?: return ReceiveResult.Rejected(
                HttpStatusCode.PayloadTooLarge,
                "report is too large",
            )
        val payload = compressed.gunzipBounded(MAX_UNCOMPRESSED_REPORT_BYTES)
            ?: return ReceiveResult.Rejected(HttpStatusCode.BadRequest, "invalid gzip report")
        return when (val parsed = CrashReportParser.parse(payload)) {
            is ReportParseResult.Rejected -> ReceiveResult.Rejected(
                HttpStatusCode.BadRequest,
                parsed.reason,
            )
            is ReportParseResult.Accepted -> {
                store.record(parsed.report)
                dispatcher.requestFlush()
                ReceiveResult.Accepted
            }
        }
    }

    fun status(): ReceiverStatus = store.status()

    fun isDeliveryReady(): Boolean = status().lastAlertFailureType == null

    fun enqueueDeliveryTest(): StoredAlert? = store.enqueueDeliveryTest()?.also {
        dispatcher.requestFlush()
    }

    fun isAdminAuthorized(authorization: String?): Boolean {
        val expected = adminToken ?: return false
        val candidate = authorization?.removePrefix("Bearer ")?.takeIf {
            authorization.startsWith("Bearer ")
        } ?: return false
        return MessageDigest.isEqual(
            expected.encodeToByteArray(),
            candidate.encodeToByteArray(),
        )
    }

    override fun close() = dispatcher.close()
}

internal sealed interface ReceiveResult {
    data object Accepted : ReceiveResult

    data class Rejected(
        val status: HttpStatusCode,
        val reason: String,
    ) : ReceiveResult
}

internal class GlobalRateLimiter(
    private val clock: Clock,
    private val limitPerMinute: Long = 240L,
) {
    private val windowMinute = AtomicLong(-1L)
    private val count = AtomicLong(0L)

    @Synchronized
    fun tryAcquire(): Boolean {
        val minute = clock.millis() / 60_000L
        if (windowMinute.getAndSet(minute) != minute) {
            count.set(0L)
        }
        return count.incrementAndGet() <= limitPerMinute
    }
}

private fun ApplicationCall.hasValidReportHeaders(): Boolean =
    request.headers[HttpHeaders.ContentType]
        ?.substringBefore(';')
        ?.trim()
        ?.equals(ContentType.Application.Json.toString(), ignoreCase = true) == true &&
        request.headers[HttpHeaders.ContentEncoding]
            ?.equals("gzip", ignoreCase = true) == true &&
        request.headers["X-M3U-Report-Schema"] == REPORT_SCHEMA

private suspend fun ApplicationCall.receiveBounded(maxBytes: Int): ByteArray? {
    val channel = receiveChannel()
    val output = ByteArrayOutputStream(minOf(maxBytes, 8 * 1024))
    val buffer = ByteArray(8 * 1024)
    var total = 0
    while (true) {
        val read = channel.readAvailable(buffer, 0, buffer.size)
        if (read < 0) break
        if (read == 0) continue
        total += read
        if (total > maxBytes) return null
        output.write(buffer, 0, read)
    }
    return output.toByteArray()
}

private fun ByteArray.gunzipBounded(maxBytes: Int): ByteArray? = runCatching {
    GZIPInputStream(ByteArrayInputStream(this)).use { gzip ->
        gzip.readNBytes(maxBytes + 1).takeIf { bytes -> bytes.size <= maxBytes }
    }
}.getOrNull()

private val responseJson = Json {
    prettyPrint = true
    explicitNulls = false
}
