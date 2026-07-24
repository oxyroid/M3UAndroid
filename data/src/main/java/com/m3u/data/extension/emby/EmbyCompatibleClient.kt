package com.m3u.data.extension.emby

import android.content.Context
import com.m3u.core.foundation.architecture.Publisher
import com.m3u.data.api.ProviderOkhttpClient
import com.m3u.extension.api.subscription.EmbyCompatibleProviderKinds
import com.m3u.extension.api.subscription.PlaybackPreferences
import com.m3u.extension.api.subscription.PlaybackReference
import com.m3u.extension.api.subscription.ProviderKind
import com.m3u.extension.api.subscription.SubscriptionChannelDescriptor
import com.m3u.extension.api.subscription.ValidatedProviderAccount
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.Semaphore as JavaSemaphore
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.RequestBody.Companion.toRequestBody
import okio.Buffer
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class EmbyPlaybackCleanupScheduler @Inject constructor() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val admissionPermits = JavaSemaphore(MAX_PENDING_CLEANUPS, true)
    private val executionPermits = Semaphore(MAX_CONCURRENT_CLEANUPS)

    fun tryReserve(): EmbyPlaybackCleanupAdmission? {
        if (!admissionPermits.tryAcquire()) return null
        return EmbyPlaybackCleanupAdmission(this)
    }

    internal fun launchReserved(close: suspend () -> Unit) {
        scope.launch {
            try {
                withTimeout(CLEANUP_TIMEOUT_MILLIS) {
                    executionPermits.withPermit {
                        close()
                    }
                }
            } catch (_: Exception) {
                // This is the last-resort path after the owning invocation has already failed.
            } finally {
                admissionPermits.release()
            }
        }
    }

    internal fun releaseReserved() {
        admissionPermits.release()
    }

    companion object {
        internal const val MAX_PENDING_CLEANUPS = 64
        private const val MAX_CONCURRENT_CLEANUPS = 4
        const val CLEANUP_TIMEOUT_MILLIS = 30_000L
    }
}

internal class EmbyPlaybackCleanupAdmission(
    private val scheduler: EmbyPlaybackCleanupScheduler,
) {
    private val consumed = AtomicBoolean()

    fun schedule(close: suspend () -> Unit) {
        if (consumed.compareAndSet(false, true)) {
            scheduler.launchReserved(close)
        }
    }

    fun release() {
        if (consumed.compareAndSet(false, true)) {
            scheduler.releaseReserved()
        }
    }
}

internal interface EmbyCompatibleClient {
    suspend fun validate(
        baseUrl: String,
        requestedKind: ProviderKind,
        username: String,
        password: String,
    ): EmbyValidation

    suspend fun refreshChannels(
        account: ValidatedProviderAccount,
        accessToken: String,
    ): EmbyChannelRefresh

    suspend fun resolvePlayback(
        account: ValidatedProviderAccount,
        accessToken: String,
        reference: PlaybackReference,
        preferences: PlaybackPreferences,
    ): EmbyPlaybackSource

    suspend fun resolvePlaybackWithCleanupAdmission(
        account: ValidatedProviderAccount,
        accessToken: String,
        reference: PlaybackReference,
        preferences: PlaybackPreferences,
        cleanupAdmission: EmbyPlaybackCleanupAdmission,
    ): EmbyPlaybackSource = resolvePlayback(
        account = account,
        accessToken = accessToken,
        reference = reference,
        preferences = preferences,
    )

    suspend fun closePlayback(
        account: ValidatedProviderAccount,
        accessToken: String,
        itemId: String,
        mediaSourceId: String?,
        session: EmbyPlaybackSession,
    ): Boolean
}

internal data class EmbyValidation(
    val account: ValidatedProviderAccount,
    val accessToken: String,
)

internal data class EmbyChannelRefresh(
    val channels: List<SubscriptionChannelDescriptor>,
    val totalRecordCount: Int,
)

internal data class EmbyPlaybackSource(
    val url: String,
    val headers: Map<String, String>,
    val mediaSourceId: String?,
    val session: EmbyPlaybackSession?,
)

internal data class EmbyPlaybackSession(
    val playSessionId: String?,
    val liveStreamId: String?,
)

internal class OkHttpEmbyCompatibleClient private constructor(
    device: String,
    deviceId: String,
    version: String,
    okHttpClient: OkHttpClient,
    controlCallTimeoutMillis: Long,
    private val cleanupScheduler: EmbyPlaybackCleanupScheduler,
) : EmbyCompatibleClient {
    @Inject
    constructor(
        @ApplicationContext context: Context,
        publisher: Publisher,
        @ProviderOkhttpClient okHttpClient: OkHttpClient,
        cleanupScheduler: EmbyPlaybackCleanupScheduler = EmbyPlaybackCleanupScheduler(),
    ) : this(
        device = publisher.model.ifBlank { "Android" },
        deviceId = context.providerDeviceId(),
        version = publisher.versionName,
        okHttpClient = okHttpClient,
        controlCallTimeoutMillis = CONTROL_CALL_TIMEOUT_MILLIS,
        cleanupScheduler = cleanupScheduler,
    )

    internal constructor(
        okHttpClient: OkHttpClient,
        controlCallTimeoutMillis: Long = CONTROL_CALL_TIMEOUT_MILLIS,
        cleanupScheduler: EmbyPlaybackCleanupScheduler = EmbyPlaybackCleanupScheduler(),
    ) : this(
        device = "Android test",
        deviceId = "test-device",
        version = "test",
        okHttpClient = okHttpClient,
        controlCallTimeoutMillis = controlCallTimeoutMillis,
        cleanupScheduler = cleanupScheduler,
    )

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }
    private val clientIdentity = ClientIdentity(
        device = device,
        deviceId = deviceId,
        version = version,
    )
    private val controlClient = okHttpClient.newBuilder()
        .followRedirects(false)
        .followSslRedirects(false)
        .addInterceptor(SameOriginRedirectInterceptor)
        .callTimeout(
            controlCallTimeoutMillis.also { timeout ->
                require(timeout > 0) { "Provider control call timeout must be positive" }
            },
            TimeUnit.MILLISECONDS,
        )
        .build()

    override suspend fun validate(
        baseUrl: String,
        requestedKind: ProviderKind,
        username: String,
        password: String,
    ): EmbyValidation = withContext(Dispatchers.IO) {
        val normalizedBaseUrl = normalizeBaseUrl(baseUrl)
        val publicInfo: SystemInfoResponse = executeJson(
            requestBuilder(normalizedBaseUrl, "System/Info/Public").get().build()
        )
        val detectedKind = detectProviderKind(publicInfo.productName, publicInfo.serverName)
        val effectiveKind = when {
            detectedKind == EmbyCompatibleProviderKinds.Auto -> requestedKind
            requestedKind == EmbyCompatibleProviderKinds.Auto -> detectedKind
            requestedKind == detectedKind -> detectedKind
            else -> throw EmbyProtocolException(
                "Selected provider kind $requestedKind does not match detected server kind $detectedKind"
            )
        }
        val authenticationBody = json.encodeToString(
            AuthenticateByNameRequest(username = username, password = password)
        )
        val authentication: AuthenticationResponse = executeJson(
            requestBuilder(
                baseUrl = normalizedBaseUrl,
                path = "Users/AuthenticateByName",
                providerKind = effectiveKind,
            )
                .post(authenticationBody.toRequestBody(JSON_MEDIA_TYPE))
                .build()
        )
        val accessToken = authentication.accessToken?.takeIf(String::isNotBlank)
            ?: throw EmbyProtocolException("Authentication response did not contain an access token")
        val user = authentication.user
            ?: throw EmbyProtocolException("Authentication response did not contain a user")
        val serverId = authentication.serverId
            ?.takeIf(String::isNotBlank)
            ?: publicInfo.id?.takeIf(String::isNotBlank)
            ?: throw EmbyProtocolException("Server response did not contain an identifier")

        EmbyValidation(
            account = ValidatedProviderAccount(
                normalizedBaseUrl = normalizedBaseUrl,
                detectedKind = effectiveKind,
                serverId = serverId,
                serverName = publicInfo.serverName.orEmpty().ifBlank { effectiveKind.value },
                serverVersion = publicInfo.version.orEmpty(),
                userId = user.id,
                username = user.name.ifBlank { username },
            ),
            accessToken = accessToken,
        )
    }

    override suspend fun refreshChannels(
        account: ValidatedProviderAccount,
        accessToken: String,
    ): EmbyChannelRefresh = withContext(Dispatchers.IO) {
        val providerId = EmbyCompatibleProvider.ID
        val channels = mutableListOf<SubscriptionChannelDescriptor>()
        val seenChannelIds = mutableSetOf<String>()
        var expectedTotal: Int? = null
        var startIndex = 0
        var requestCount = 0
        while (true) {
            if (requestCount >= MAX_CHANNEL_PAGE_REQUESTS) {
                throw EmbyProtocolException("Provider channel pagination exceeded the host limit")
            }
            val remainingCapacity = MAX_CHANNELS_PER_REFRESH - channels.size
            val pageLimit = minOf(CHANNEL_PAGE_SIZE, maxOf(remainingCapacity, 1))
            val request = requestBuilder(
                baseUrl = account.normalizedBaseUrl,
                path = "LiveTv/Channels",
                accessToken = accessToken,
                providerKind = account.detectedKind,
                userId = account.userId,
            )
                .url(
                    url(account.normalizedBaseUrl, "LiveTv/Channels")
                        .newBuilder()
                        .addQueryParameter("UserId", account.userId)
                        .addQueryParameter("StartIndex", startIndex.toString())
                        .addQueryParameter("Limit", pageLimit.toString())
                        .addQueryParameter("EnableImages", "true")
                        .build()
                )
                .get()
                .build()
            requestCount++
            val response: LiveTvChannelsResponse = executeJson(request)
            val pageItems = response.items.orEmpty()
            if (pageItems.size > pageLimit) {
                throw EmbyProtocolException("Provider returned more channels than requested")
            }
            val pageTotal = response.totalRecordCount
            if (pageTotal != null && pageTotal !in 0..MAX_CHANNELS_PER_REFRESH) {
                throw EmbyProtocolException("Provider channel count exceeds the host limit")
            }
            if (expectedTotal != null && pageTotal != expectedTotal) {
                throw EmbyProtocolException("Provider channel count changed during pagination")
            }
            if (expectedTotal == null && pageTotal != null) {
                expectedTotal = pageTotal
            }
            val completeCount = channels.size + pageItems.size
            if (completeCount > MAX_CHANNELS_PER_REFRESH) {
                throw EmbyProtocolException("Provider channel count exceeds the host limit")
            }
            if (expectedTotal != null && completeCount > expectedTotal) {
                throw EmbyProtocolException("Provider returned more channels than its reported count")
            }
            pageItems.forEach { item ->
                val itemId = item.id?.trim()?.takeIf(String::isNotEmpty)
                    ?: throw EmbyProtocolException("Provider channel did not contain an identifier")
                val title = item.name?.trim()?.takeIf(String::isNotEmpty)
                    ?: throw EmbyProtocolException("Provider channel did not contain a title")
                if (!seenChannelIds.add(itemId)) {
                    throw EmbyProtocolException("Provider returned a duplicate channel identifier")
                }
                val imageUrl = item.primaryImageTag?.takeIf(String::isNotBlank)?.let {
                    url(account.normalizedBaseUrl, "Items/$itemId/Images/Primary").toString()
                }
                channels += SubscriptionChannelDescriptor(
                    remoteId = itemId,
                    title = title,
                    logoUrl = imageUrl,
                    category = item.channelType
                        ?.takeIf(String::isNotBlank)
                        ?: item.mediaType?.takeIf(String::isNotBlank)
                        ?: DEFAULT_CHANNEL_CATEGORY,
                    playbackReference = PlaybackReference(
                        providerId = providerId,
                        itemId = itemId,
                        sourceType = PLAYBACK_SOURCE_TYPE,
                    ),
                )
            }
            startIndex = completeCount

            val reportedTotal = expectedTotal
            if (reportedTotal != null) {
                if (channels.size == reportedTotal) break
                if (pageItems.isEmpty()) {
                    throw EmbyProtocolException(
                        "Provider channel pagination ended before the reported count"
                    )
                }
            } else {
                if (pageItems.size < pageLimit) break
            }
        }
        EmbyChannelRefresh(
            channels = channels,
            totalRecordCount = expectedTotal ?: channels.size,
        )
    }

    override suspend fun resolvePlayback(
        account: ValidatedProviderAccount,
        accessToken: String,
        reference: PlaybackReference,
        preferences: PlaybackPreferences,
    ): EmbyPlaybackSource = resolvePlaybackInternal(
        account = account,
        accessToken = accessToken,
        reference = reference,
        preferences = preferences,
        cleanupAdmission = null,
    )

    override suspend fun resolvePlaybackWithCleanupAdmission(
        account: ValidatedProviderAccount,
        accessToken: String,
        reference: PlaybackReference,
        preferences: PlaybackPreferences,
        cleanupAdmission: EmbyPlaybackCleanupAdmission,
    ): EmbyPlaybackSource = resolvePlaybackInternal(
        account = account,
        accessToken = accessToken,
        reference = reference,
        preferences = preferences,
        cleanupAdmission = cleanupAdmission,
    )

    private suspend fun resolvePlaybackInternal(
        account: ValidatedProviderAccount,
        accessToken: String,
        reference: PlaybackReference,
        preferences: PlaybackPreferences,
        cleanupAdmission: EmbyPlaybackCleanupAdmission?,
    ): EmbyPlaybackSource {
        val admission = cleanupAdmission
            ?: cleanupScheduler.tryReserve()
            ?: throw EmbyProtocolException("Provider playback cleanup capacity is exhausted")
        val releaseOnSuccess = cleanupAdmission == null
        var acquiredSession: EmbyPlaybackSession? = null
        var resolvedMediaSourceId: String? = reference.mediaSourceId
        try {
            val source = withContext(Dispatchers.IO) {
                val playbackInfoUrl = url(
                    account.normalizedBaseUrl,
                    "Items/${reference.itemId}/PlaybackInfo",
                )
                    .newBuilder()
                    .addQueryParameter("UserId", account.userId)
                    .addQueryParameter("IsPlayback", "true")
                    .addQueryParameter("AutoOpenLiveStream", "true")
                    .apply {
                        preferences.maxStreamingBitrate?.let { bitrate ->
                            addQueryParameter("MaxStreamingBitrate", bitrate.toString())
                        }
                    }
                    .build()
                val response: PlaybackInfoResponse = executeJson(
                    requestBuilder(
                        baseUrl = account.normalizedBaseUrl,
                        path = "Items/${reference.itemId}/PlaybackInfo",
                        accessToken = accessToken,
                        providerKind = account.detectedKind,
                        userId = account.userId,
                    )
                        .url(playbackInfoUrl)
                        .get()
                        .build()
                )
                acquiredSession = response.playSessionId
                    ?.takeIf(String::isNotBlank)
                    ?.let { playSessionId ->
                        EmbyPlaybackSession(
                            playSessionId = playSessionId,
                            liveStreamId = null,
                        )
                    }
                val mediaSource = response.mediaSources.orEmpty()
                    .firstOrNull { source ->
                        reference.mediaSourceId != null &&
                            source.id == reference.mediaSourceId
                    }
                    ?: response.mediaSources.orEmpty().firstOrNull()
                    ?: throw EmbyProtocolException(
                        "Playback response did not contain a media source"
                    )
                resolvedMediaSourceId = mediaSource.id
                val playSessionId = response.playSessionId?.takeIf(String::isNotBlank)
                val liveStreamId = mediaSource.liveStreamId?.takeIf(String::isNotBlank)
                acquiredSession = if (playSessionId != null || liveStreamId != null) {
                    EmbyPlaybackSession(
                        playSessionId = playSessionId,
                        liveStreamId = liveStreamId,
                    )
                } else {
                    null
                }
                val resolvedUrl = sequenceOf(
                    mediaSource.directStreamUrl,
                    mediaSource.transcodingUrl.takeIf { preferences.allowTranscoding },
                    mediaSource.path,
                )
                    .filterNotNull()
                    .map(String::trim)
                    .firstOrNull(String::isNotEmpty)
                    ?.let { candidate -> absoluteUrl(account.normalizedBaseUrl, candidate) }
                    ?: throw EmbyProtocolException(
                        "Playback response did not contain a usable URL"
                    )
                val sameOrigin = account.normalizedBaseUrl.toHttpUrl()
                    .hasSameOrigin(resolvedUrl.toHttpUrl())
                val headers = buildMap {
                    mediaSource.requiredHttpHeaders.orEmpty().forEach { (name, value) ->
                        if (sameOrigin || name.lowercase() !in SENSITIVE_PLAYBACK_HEADERS) {
                            put(name, value)
                        }
                    }
                    if (sameOrigin) {
                        putAll(
                            clientIdentity.authenticationHeaders(
                                providerKind = account.detectedKind,
                                accessToken = accessToken,
                                userId = account.userId,
                            )
                        )
                    }
                }

                EmbyPlaybackSource(
                    url = resolvedUrl,
                    headers = headers,
                    mediaSourceId = mediaSource.id,
                    session = acquiredSession,
                )
            }
            if (releaseOnSuccess) {
                admission.release()
            }
            return source
        } catch (failure: Exception) {
            acquiredSession?.let { session ->
                scheduleCloseAfterResolveFailure(
                    admission = admission,
                    account = account,
                    accessToken = accessToken,
                    itemId = reference.itemId,
                    mediaSourceId = resolvedMediaSourceId,
                    session = session,
                )
            } ?: admission.release()
            throw failure
        }
    }

    override suspend fun closePlayback(
        account: ValidatedProviderAccount,
        accessToken: String,
        itemId: String,
        mediaSourceId: String?,
        session: EmbyPlaybackSession,
    ): Boolean = withContext(Dispatchers.IO) {
        supervisorScope {
            val stoppedClose = async {
                val stoppedBody = json.encodeToString(
                    PlaybackStoppedRequest(
                        itemId = itemId,
                        mediaSourceId = mediaSourceId,
                        playSessionId = session.playSessionId,
                        liveStreamId = session.liveStreamId,
                    )
                )
                executeNoContent(
                    requestBuilder(
                        baseUrl = account.normalizedBaseUrl,
                        path = "Sessions/Playing/Stopped",
                        accessToken = accessToken,
                        providerKind = account.detectedKind,
                        userId = account.userId,
                    )
                        .post(stoppedBody.toRequestBody(JSON_MEDIA_TYPE))
                        .build(),
                    allowNotFound = true,
                )
            }
            val liveStreamClose = session.liveStreamId?.let { liveStreamId ->
                async {
                    val closeUrl = url(account.normalizedBaseUrl, "LiveStreams/Close")
                        .newBuilder()
                        .addQueryParameter("LiveStreamId", liveStreamId)
                        .build()
                    executeNoContent(
                        requestBuilder(
                            baseUrl = account.normalizedBaseUrl,
                            path = "LiveStreams/Close",
                            accessToken = accessToken,
                            providerKind = account.detectedKind,
                            userId = account.userId,
                        )
                            .url(closeUrl)
                            .post(ByteArray(0).toRequestBody(null))
                            .build(),
                        allowNotFound = true,
                    )
                }
            }
            val stoppedFailure = stoppedClose.failureOrNull()
            val liveStreamFailure = liveStreamClose?.failureOrNull()
            val firstFailure = stoppedFailure ?: liveStreamFailure
            if (firstFailure != null) {
                liveStreamFailure
                    ?.takeUnless { failure -> failure === firstFailure }
                    ?.let(firstFailure::addSuppressed)
                throw firstFailure
            }
            true
        }
    }

    private suspend fun Deferred<Unit>.failureOrNull(): Exception? =
        try {
            await()
            null
        } catch (failure: Exception) {
            failure
        }

    private fun scheduleCloseAfterResolveFailure(
        admission: EmbyPlaybackCleanupAdmission,
        account: ValidatedProviderAccount,
        accessToken: String,
        itemId: String,
        mediaSourceId: String?,
        session: EmbyPlaybackSession,
    ) {
        admission.schedule {
            closePlayback(
                account = account,
                accessToken = accessToken,
                itemId = itemId,
                mediaSourceId = mediaSourceId,
                session = session,
            )
        }
    }

    private suspend inline fun <reified T> executeJson(request: Request): T =
        execute(request) { response ->
            if (!response.isSuccessful) {
                throw EmbyHttpException(response.code, "Provider request failed with HTTP ${response.code}")
            }
            val body = response.readBodyWithinLimit()
            if (body.isEmpty()) {
                throw EmbyProtocolException("Provider response body was empty")
            }
            json.decodeFromString(body)
        }

    private suspend fun executeNoContent(request: Request, allowNotFound: Boolean) {
        execute(request) { response ->
            if (!response.isSuccessful && !(allowNotFound && response.code == 404)) {
                throw EmbyHttpException(
                    response.code,
                    "Provider request failed with HTTP ${response.code}",
                )
            }
        }
    }

    private suspend fun <T> execute(
        request: Request,
        transform: (Response) -> T,
    ): T = suspendCancellableCoroutine { continuation ->
        val call = controlClient.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) {
                    continuation.resumeWith(Result.failure(e))
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val result = runCatching {
                    response.use(transform)
                }
                if (continuation.isActive) {
                    continuation.resumeWith(result)
                }
            }
        })
    }

    private fun Response.readBodyWithinLimit(): String {
        val contentLength = body.contentLength()
        if (contentLength > MAX_JSON_RESPONSE_BYTES) {
            throw responseTooLarge()
        }
        val source = body.source()
        val buffer = Buffer()
        val limit = MAX_JSON_RESPONSE_BYTES.toLong() + 1
        while (buffer.size < limit) {
            val read = source.read(buffer, minOf(8_192L, limit - buffer.size))
            if (read == -1L) break
        }
        if (buffer.size > MAX_JSON_RESPONSE_BYTES) {
            throw responseTooLarge()
        }
        return buffer.readUtf8()
    }

    private fun responseTooLarge() = EmbyProtocolException(
        "Provider response body exceeds the host limit"
    )

    private fun requestBuilder(
        baseUrl: String,
        path: String,
        accessToken: String? = null,
        providerKind: ProviderKind = EmbyCompatibleProviderKinds.Auto,
        userId: String? = null,
    ): Request.Builder = Request.Builder()
        .url(url(baseUrl, path))
        .apply {
            clientIdentity.authenticationHeaders(providerKind, accessToken, userId).forEach { (name, value) ->
                header(name, value)
            }
        }

    private fun normalizeBaseUrl(value: String): String {
        val url = value.trim().toHttpUrl()
        require(url.scheme == "http" || url.scheme == "https") {
            "Provider base URL must use HTTP or HTTPS"
        }
        require(url.username.isEmpty() && url.password.isEmpty()) {
            "Provider base URL must not contain user information"
        }
        require(url.query == null && url.fragment == null) {
            "Provider base URL must not contain a query or fragment"
        }
        return url.toString().removeSuffix("/")
    }

    private fun url(baseUrl: String, path: String): HttpUrl = baseUrl
        .toHttpUrl()
        .newBuilder()
        .addPathSegments(path.trimStart('/'))
        .build()

    private fun absoluteUrl(baseUrl: String, candidate: String): String {
        if (candidate.startsWith("http://") || candidate.startsWith("https://")) return candidate
        return baseUrl.toHttpUrl().resolve(candidate)?.toString()
            ?: throw EmbyProtocolException("Provider returned a malformed playback URL")
    }

    private fun detectProviderKind(productName: String?, serverName: String?): ProviderKind {
        val identity = listOfNotNull(productName, serverName).joinToString(" ").lowercase()
        return when {
            "jellyfin" in identity -> EmbyCompatibleProviderKinds.Jellyfin
            "emby" in identity -> EmbyCompatibleProviderKinds.Emby
            else -> EmbyCompatibleProviderKinds.Auto
        }
    }

    private data class ClientIdentity(
        val device: String,
        val deviceId: String,
        val version: String,
    ) {
        private fun authorization(
            scheme: String,
            accessToken: String?,
            userId: String?,
        ): String = buildString {
            append(scheme)
            append(' ')
            userId?.takeIf(String::isNotBlank)?.let {
                append("UserId=\"")
                append(it.replace("\"", ""))
                append("\", ")
            }
            append("Client=\"")
            append(CLIENT_NAME)
            append("\", Device=\"")
            append(device.replace("\"", ""))
            append("\", DeviceId=\"")
            append(deviceId.replace("\"", ""))
            append("\", Version=\"")
            append(version.replace("\"", ""))
            append('"')
            accessToken?.let {
                append(", Token=\"")
                append(it.replace("\"", ""))
                append('"')
            }
        }

        fun authenticationHeaders(
            providerKind: ProviderKind,
            accessToken: String?,
            userId: String?,
        ): Map<String, String> = when (providerKind) {
            EmbyCompatibleProviderKinds.Emby -> buildMap {
                put(
                    AUTHORIZATION_HEADER,
                    authorization(scheme = "Emby", accessToken = null, userId = userId),
                )
                accessToken?.let { put(EMBY_TOKEN_HEADER, it) }
            }

            EmbyCompatibleProviderKinds.Jellyfin -> mapOf(
                AUTHORIZATION_HEADER to authorization(
                    scheme = "MediaBrowser",
                    accessToken = accessToken,
                    userId = null,
                )
            )

            else -> mapOf(
                AUTHORIZATION_HEADER to authorization(
                    scheme = "MediaBrowser",
                    accessToken = accessToken,
                    userId = null,
                )
            )
        }
    }

    companion object {
        internal const val MAX_JSON_RESPONSE_BYTES = 4 * 1024 * 1024
        internal const val CHANNEL_PAGE_SIZE = 500
        private const val MAX_CHANNELS_PER_REFRESH = 50_000
        private const val MAX_CHANNEL_PAGE_REQUESTS = 512
        private const val CONTROL_CALL_TIMEOUT_MILLIS = 25_000L
        private val JSON_MEDIA_TYPE = "application/json".toMediaType()
        private const val CLIENT_NAME = "M3UAndroid"
        private const val AUTHORIZATION_HEADER = "Authorization"
        private const val EMBY_TOKEN_HEADER = "X-Emby-Token"
        private val SENSITIVE_PLAYBACK_HEADERS = setOf(
            "authorization",
            "cookie",
            "proxy-authorization",
            "x-emby-token",
        )
        private const val DEFAULT_CHANNEL_CATEGORY = "Live TV"
        private const val PLAYBACK_SOURCE_TYPE = "live_tv"
    }
}

private fun HttpUrl.hasSameOrigin(other: HttpUrl): Boolean =
    scheme == other.scheme && host == other.host && port == other.port

private object SameOriginRedirectInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        var request = chain.request()
        repeat(MAX_REDIRECTS + 1) { redirectCount ->
            val response = chain.proceed(request)
            val location = response.header("Location")
            if (response.code !in REDIRECT_STATUS_CODES || location == null) return response
            val target = response.request.url.resolve(location) ?: return response
            if (!response.request.url.hasSameOrigin(target)) return response
            if (redirectCount == MAX_REDIRECTS) return response
            response.close()
            request = request.redirectedTo(target, response.code)
        }
        error("Unreachable")
    }

    private fun Request.redirectedTo(target: HttpUrl, statusCode: Int): Request {
        val switchToGet = statusCode in setOf(301, 302, 303) &&
            method != "GET" &&
            method != "HEAD"
        return newBuilder()
            .url(target)
            .apply {
                if (switchToGet) {
                    method("GET", null)
                    removeHeader("Content-Length")
                    removeHeader("Content-Type")
                    removeHeader("Transfer-Encoding")
                }
            }
            .build()
    }

    private const val MAX_REDIRECTS = 5
    private val REDIRECT_STATUS_CODES = setOf(301, 302, 303, 307, 308)
}

private fun Context.providerDeviceId(): String {
    val preferences = getSharedPreferences(PROVIDER_PREFERENCES, Context.MODE_PRIVATE)
    return preferences.getString(PROVIDER_DEVICE_ID, null)
        ?.takeIf(String::isNotBlank)
        ?: UUID.randomUUID().toString().also { deviceId ->
            preferences.edit().putString(PROVIDER_DEVICE_ID, deviceId).apply()
        }
}

private const val PROVIDER_PREFERENCES = "subscription_provider"
private const val PROVIDER_DEVICE_ID = "device_id"

internal class EmbyHttpException(
    val statusCode: Int,
    message: String,
) : IOException(message)

internal class EmbyProtocolException(message: String) : IOException(message)

@Serializable
private data class SystemInfoResponse(
    @SerialName("Id") val id: String? = null,
    @SerialName("ServerName") val serverName: String? = null,
    @SerialName("Version") val version: String? = null,
    @SerialName("ProductName") val productName: String? = null,
)

@Serializable
private data class AuthenticateByNameRequest(
    @SerialName("Username") val username: String,
    @SerialName("Pw") val password: String,
)

@Serializable
private data class AuthenticationResponse(
    @SerialName("AccessToken") val accessToken: String? = null,
    @SerialName("ServerId") val serverId: String? = null,
    @SerialName("User") val user: UserResponse? = null,
)

@Serializable
private data class UserResponse(
    @SerialName("Id") val id: String,
    @SerialName("Name") val name: String = "",
)

@Serializable
private data class LiveTvChannelsResponse(
    @SerialName("Items") val items: List<LiveTvChannelResponse>? = null,
    @SerialName("TotalRecordCount") val totalRecordCount: Int? = null,
)

@Serializable
private data class LiveTvChannelResponse(
    @SerialName("Id") val id: String? = null,
    @SerialName("Name") val name: String? = null,
    @SerialName("ChannelNumber") val channelNumber: String? = null,
    @SerialName("ChannelType") val channelType: String? = null,
    @SerialName("MediaType") val mediaType: String? = null,
    @SerialName("PrimaryImageTag") val primaryImageTag: String? = null,
)

@Serializable
private data class PlaybackInfoResponse(
    @SerialName("MediaSources") val mediaSources: List<MediaSourceResponse>? = null,
    @SerialName("PlaySessionId") val playSessionId: String? = null,
)

@Serializable
private data class MediaSourceResponse(
    @SerialName("Id") val id: String? = null,
    @SerialName("Path") val path: String? = null,
    @SerialName("DirectStreamUrl") val directStreamUrl: String? = null,
    @SerialName("TranscodingUrl") val transcodingUrl: String? = null,
    @SerialName("LiveStreamId") val liveStreamId: String? = null,
    @SerialName("RequiredHttpHeaders") val requiredHttpHeaders: Map<String, String>? = null,
)

@Serializable
private data class PlaybackStoppedRequest(
    @SerialName("ItemId") val itemId: String,
    @SerialName("MediaSourceId") val mediaSourceId: String? = null,
    @SerialName("PlaySessionId") val playSessionId: String? = null,
    @SerialName("LiveStreamId") val liveStreamId: String? = null,
    @SerialName("PositionTicks") val positionTicks: Long = 0L,
)
