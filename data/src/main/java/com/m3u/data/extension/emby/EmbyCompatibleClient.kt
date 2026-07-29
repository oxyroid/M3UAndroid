package com.m3u.data.extension.emby

import android.content.Context
import com.m3u.core.foundation.architecture.Publisher
import com.m3u.data.api.ProviderOkhttpClient
import com.m3u.data.extension.artwork.PROVIDER_ARTWORK_TAG_QUERY
import com.m3u.extension.api.subscription.EmbyCompatibleProviderKinds
import com.m3u.extension.api.subscription.PlaybackMethod
import com.m3u.extension.api.subscription.PlaybackMethods
import com.m3u.extension.api.subscription.PlaybackPreferences
import com.m3u.extension.api.subscription.PlaybackReference
import com.m3u.extension.api.subscription.PlaybackSessionEvent
import com.m3u.extension.api.subscription.PlaybackSessionEvents
import com.m3u.extension.api.subscription.ProviderMediaKind
import com.m3u.extension.api.subscription.ProviderMediaKinds
import com.m3u.extension.api.subscription.ProviderKind
import com.m3u.extension.api.subscription.SubscriptionChannelDescriptor
import com.m3u.extension.api.subscription.SubscriptionContentBrowseRequest
import com.m3u.extension.api.subscription.SubscriptionContentItemDescriptor
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
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
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

    suspend fun browseContent(
        account: ValidatedProviderAccount,
        accessToken: String,
        parentReference: PlaybackReference?,
        cursor: String?,
        limit: Int,
    ): EmbyContentPage

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

    suspend fun updatePlayback(
        account: ValidatedProviderAccount,
        accessToken: String,
        reference: PlaybackReference,
        mediaSourceId: String?,
        session: EmbyPlaybackSession,
        event: PlaybackSessionEvent,
        positionTicks: Long,
        playMethod: PlaybackMethod,
        isPaused: Boolean,
    ): Boolean

    suspend fun closePlayback(
        account: ValidatedProviderAccount,
        accessToken: String,
        itemId: String,
        mediaSourceId: String?,
        session: EmbyPlaybackSession,
        positionTicks: Long,
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

internal data class EmbyContentPage(
    val items: List<SubscriptionContentItemDescriptor>,
    val nextCursor: String?,
    val total: Int?,
)

internal data class EmbyPlaybackSource(
    val url: String,
    val headers: Map<String, String>,
    val mediaSourceId: String?,
    val session: EmbyPlaybackSession?,
    val playMethod: PlaybackMethod,
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
        .protocols(listOf(Protocol.HTTP_1_1))
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
        require(
            requestedKind == EmbyCompatibleProviderKinds.Emby ||
                requestedKind == EmbyCompatibleProviderKinds.Jellyfin
        ) {
            "Unsupported Emby-compatible provider kind"
        }
        val normalizedBaseUrl = normalizeBaseUrl(baseUrl)
        val publicInfo: SystemInfoResponse = executeJson(
            requestBuilder(
                baseUrl = normalizedBaseUrl,
                path = "System/Info/Public",
                providerKind = requestedKind,
            ).get().build()
        )
        val advertisedKind = detectProviderKind(publicInfo.productName)
        if (advertisedKind != null && requestedKind != advertisedKind) {
            throw EmbyProtocolException(
                "Selected provider kind $requestedKind does not match advertised server kind " +
                    "$advertisedKind"
            )
        }
        val effectiveKind = requestedKind
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
                val imageTag = item.imageTags
                    ?.entries
                    ?.firstOrNull { (key, value) ->
                        key.equals("Primary", ignoreCase = true) && value.isNotBlank()
                    }
                    ?.value
                    ?: item.primaryImageTag?.takeIf(String::isNotBlank)
                val imageUrl = imageTag?.let { tag ->
                    url(account.normalizedBaseUrl, "Items/$itemId/Images/Primary")
                        .newBuilder()
                        .addQueryParameter(PROVIDER_ARTWORK_TAG_QUERY, tag)
                        .build()
                        .toString()
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

    override suspend fun browseContent(
        account: ValidatedProviderAccount,
        accessToken: String,
        parentReference: PlaybackReference?,
        cursor: String?,
        limit: Int,
    ): EmbyContentPage = withContext(Dispatchers.IO) {
        require(limit in 1..SubscriptionContentBrowseRequest.MAX_LIMIT) {
            "Provider browse limit is outside the contract range"
        }
        val startIndex = decodeBrowseCursor(cursor)
        parentReference?.let(::validateSeriesReference)
        val endpoint = if (parentReference == null) {
            urlWithSegments(
                account.normalizedBaseUrl,
                "Users",
                account.userId,
                "Items",
            )
        } else {
            urlWithSegments(
                account.normalizedBaseUrl,
                "Shows",
                parentReference.itemId,
                "Episodes",
            )
        }
        val requestUrl = endpoint.newBuilder()
            .apply {
                if (parentReference == null) {
                    addQueryParameter("Recursive", "true")
                    addQueryParameter("IncludeItemTypes", "Movie,Series")
                } else {
                    addQueryParameter("UserId", account.userId)
                }
                addQueryParameter("StartIndex", startIndex.toString())
                addQueryParameter("Limit", limit.toString())
                addQueryParameter("EnableImages", "true")
                addQueryParameter("EnableImageTypes", "Primary")
                addQueryParameter(
                    "Fields",
                    "Overview,Genres,ProductionYear,SeriesName,ParentIndexNumber,IndexNumber",
                )
                if (parentReference == null) {
                    addQueryParameter("SortBy", "SortName,ProductionYear")
                    addQueryParameter("SortOrder", "Ascending")
                }
            }
            .build()
        val response: ContentItemsResponse = executeJson(
            requestBuilder(
                baseUrl = account.normalizedBaseUrl,
                path = if (parentReference == null) "Users/Items" else "Shows/Episodes",
                accessToken = accessToken,
                providerKind = account.detectedKind,
                userId = account.userId,
            )
                .url(requestUrl)
                .get()
                .build()
        )
        val pageItems = response.items.orEmpty()
        if (pageItems.size > limit) {
            throw EmbyProtocolException("Provider returned more media items than requested")
        }
        val total = response.totalRecordCount
        if (total != null && total !in 0..MAX_CONTENT_ITEMS) {
            throw EmbyProtocolException("Provider media count exceeds the host limit")
        }
        val completeCount = startIndex.toLong() + pageItems.size
        if (completeCount > MAX_CONTENT_ITEMS) {
            throw EmbyProtocolException("Provider media pagination exceeds the host limit")
        }
        if (total != null && completeCount > total) {
            throw EmbyProtocolException(
                "Provider returned more media items than its reported count"
            )
        }
        if (total != null && startIndex < total && pageItems.isEmpty()) {
            throw EmbyProtocolException(
                "Provider media pagination ended before the reported count"
            )
        }
        val seenItemIds = mutableSetOf<String>()
        val items = pageItems.map { item ->
            item.toContentDescriptor(
                account = account,
                expectedKind = if (parentReference == null) {
                    ExpectedContentKind.ROOT
                } else {
                    ExpectedContentKind.EPISODE
                },
                seenItemIds = seenItemIds,
            )
        }
        val nextCursor = when {
            total != null && completeCount < total -> completeCount.toString()
            total == null && pageItems.size == limit -> completeCount.toString()
            else -> null
        }
        EmbyContentPage(
            items = items,
            nextCursor = nextCursor,
            total = total,
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
        preferences.maxStreamingBitrate?.let { maxStreamingBitrate ->
            if (maxStreamingBitrate <= 0L) {
                throw EmbyProtocolException("Maximum streaming bitrate must be positive")
            }
        }
        val negotiatedMaxStreamingBitrate = preferences.maxStreamingBitrate
            ?.coerceAtMost(Int.MAX_VALUE.toLong())
        val admission = cleanupAdmission
            ?: cleanupScheduler.tryReserve()
            ?: throw EmbyProtocolException("Provider playback cleanup capacity is exhausted")
        val releaseOnSuccess = cleanupAdmission == null
        var acquiredSession: EmbyPlaybackSession? = null
        var resolvedMediaSourceId: String? = reference.mediaSourceId
        try {
            val source = withContext(Dispatchers.IO) {
                val livePlayback = reference.sourceType == LIVE_PLAYBACK_SOURCE_TYPE
                val playbackInfoUrl = urlWithSegments(
                    account.normalizedBaseUrl,
                    "Items",
                    reference.itemId,
                    "PlaybackInfo",
                )
                val playbackInfoBody = json.encodeToString(
                    PlaybackInfoRequest(
                        userId = account.userId,
                        mediaSourceId = reference.mediaSourceId,
                        startTimeTicks = preferences.startPositionTicks,
                        isPlayback = true,
                        autoOpenLiveStream = livePlayback,
                        enableDirectPlay = true,
                        enableDirectStream = true,
                        enableTranscoding = preferences.allowTranscoding,
                        allowVideoStreamCopy = true,
                        allowAudioStreamCopy = true,
                        maxStreamingBitrate = negotiatedMaxStreamingBitrate,
                        deviceProfile = androidMedia3DeviceProfile(
                            maxStreamingBitrate = negotiatedMaxStreamingBitrate,
                        ),
                    )
                )
                val playbackInfoBuilder = requestBuilder(
                    baseUrl = account.normalizedBaseUrl,
                    path = "Items/PlaybackInfo",
                    accessToken = accessToken,
                    providerKind = account.detectedKind,
                    userId = account.userId,
                )
                    .url(playbackInfoUrl)
                val response: PlaybackInfoResponse = executeJson(
                    playbackInfoBuilder
                        .post(playbackInfoBody.toRequestBody(JSON_MEDIA_TYPE))
                        .build()
                )
                response.errorCode
                    ?.takeIf(String::isNotBlank)
                    ?.let { errorCode ->
                        throw EmbyProtocolException(
                            "Provider could not resolve playback: $errorCode"
                        )
                    }
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
                val resolved = resolvePlaybackUrl(
                    baseUrl = account.normalizedBaseUrl,
                    reference = reference,
                    mediaSource = mediaSource,
                    playSessionId = playSessionId,
                    allowTranscoding = preferences.allowTranscoding,
                    livePlayback = livePlayback,
                )
                val sameOrigin = account.normalizedBaseUrl.toHttpUrl()
                    .hasSameOrigin(resolved.url.toHttpUrl())
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
                    url = resolved.url,
                    headers = headers,
                    mediaSourceId = mediaSource.id,
                    session = acquiredSession,
                    playMethod = resolved.playMethod,
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

    override suspend fun updatePlayback(
        account: ValidatedProviderAccount,
        accessToken: String,
        reference: PlaybackReference,
        mediaSourceId: String?,
        session: EmbyPlaybackSession,
        event: PlaybackSessionEvent,
        positionTicks: Long,
        playMethod: PlaybackMethod,
        isPaused: Boolean,
    ): Boolean = withContext(Dispatchers.IO) {
        require(positionTicks >= 0L) { "Playback position must not be negative" }
        val endpoint = if (event == PlaybackSessionEvents.Started) {
            "Sessions/Playing"
        } else {
            "Sessions/Playing/Progress"
        }
        val body = json.encodeToString(
            PlaybackUpdateRequest(
                itemId = reference.itemId,
                mediaSourceId = mediaSourceId,
                playSessionId = session.playSessionId,
                liveStreamId = session.liveStreamId,
                positionTicks = positionTicks,
                isPaused = isPaused,
                playMethod = playMethod.toEmbyValue(),
                eventName = event.toEmbyEventName(),
            )
        )
        executeNoContent(
            requestBuilder(
                baseUrl = account.normalizedBaseUrl,
                path = endpoint,
                accessToken = accessToken,
                providerKind = account.detectedKind,
                userId = account.userId,
            )
                .post(body.toRequestBody(JSON_MEDIA_TYPE))
                .build(),
            allowNotFound = false,
        )
        true
    }

    override suspend fun closePlayback(
        account: ValidatedProviderAccount,
        accessToken: String,
        itemId: String,
        mediaSourceId: String?,
        session: EmbyPlaybackSession,
        positionTicks: Long,
    ): Boolean = withContext(Dispatchers.IO) {
        require(positionTicks >= 0L) { "Playback position must not be negative" }
        supervisorScope {
            val stoppedClose = async {
                val stoppedBody = json.encodeToString(
                    PlaybackStoppedRequest(
                        itemId = itemId,
                        mediaSourceId = mediaSourceId,
                        playSessionId = session.playSessionId,
                        liveStreamId = session.liveStreamId,
                        positionTicks = positionTicks,
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
                positionTicks = 0L,
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
        providerKind: ProviderKind,
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

    private fun decodeBrowseCursor(cursor: String?): Int {
        if (cursor == null) return 0
        val startIndex = cursor.toIntOrNull()
        if (
            startIndex == null ||
            startIndex < 0 ||
            startIndex.toString() != cursor
        ) {
            throw EmbyProtocolException("Provider browse cursor is invalid")
        }
        if (startIndex > MAX_CONTENT_ITEMS) {
            throw EmbyProtocolException("Provider media pagination exceeds the host limit")
        }
        return startIndex
    }

    private fun validateSeriesReference(reference: PlaybackReference) {
        if (
            reference.providerId != EmbyCompatibleProvider.ID ||
            reference.sourceType != SERIES_PLAYBACK_SOURCE_TYPE
        ) {
            throw EmbyProtocolException("Provider can only browse an Emby-compatible series")
        }
    }

    private fun ContentItemResponse.toContentDescriptor(
        account: ValidatedProviderAccount,
        expectedKind: ExpectedContentKind,
        seenItemIds: MutableSet<String>,
    ): SubscriptionContentItemDescriptor {
        val itemId = requiredWireText(
            value = id,
            label = "media identifier",
            maxBytes = PlaybackReference.MAX_ID_UTF8_BYTES,
        )
        if (!seenItemIds.add(itemId)) {
            throw EmbyProtocolException("Provider returned a duplicate media identifier")
        }
        val title = requiredWireText(
            value = name,
            label = "media title",
            maxBytes = SubscriptionContentItemDescriptor.MAX_TITLE_UTF8_BYTES,
        )
        val normalizedType = type?.trim()?.lowercase()
            ?: throw EmbyProtocolException("Provider media item did not contain a type")
        val mapping = when (expectedKind) {
            ExpectedContentKind.ROOT -> when (normalizedType) {
                "movie" -> ContentKindMapping(
                    mediaKind = ProviderMediaKinds.Movie,
                    sourceType = MOVIE_PLAYBACK_SOURCE_TYPE,
                    playable = true,
                    browsable = false,
                )

                "series" -> ContentKindMapping(
                    mediaKind = ProviderMediaKinds.Series,
                    sourceType = SERIES_PLAYBACK_SOURCE_TYPE,
                    playable = false,
                    browsable = true,
                )

                else -> throw EmbyProtocolException(
                    "Provider returned an unsupported root media type"
                )
            }

            ExpectedContentKind.EPISODE -> {
                if (normalizedType != "episode") {
                    throw EmbyProtocolException(
                        "Provider returned a non-episode inside a series"
                    )
                }
                ContentKindMapping(
                    mediaKind = ProviderMediaKinds.Episode,
                    sourceType = EPISODE_PLAYBACK_SOURCE_TYPE,
                    playable = true,
                    browsable = false,
                )
            }
        }
        val imageTag = imageTags
            ?.entries
            ?.firstOrNull { (key, value) ->
                key.equals("Primary", ignoreCase = true) && value.isNotBlank()
            }
            ?.value
            ?: primaryImageTag?.takeIf(String::isNotBlank)
        val imageUrl = imageTag?.let { tag ->
            urlWithSegments(
                account.normalizedBaseUrl,
                "Items",
                itemId,
                "Images",
                "Primary",
            )
                .newBuilder()
                .addQueryParameter(PROVIDER_ARTWORK_TAG_QUERY, tag)
                .build()
                .toString()
        }
        val category = genres
            .orEmpty()
            .firstOrNull { genre -> genre.isNotBlank() }
            ?.let { genre ->
                optionalWireText(
                    value = genre,
                    label = "media category",
                    maxBytes = SubscriptionContentItemDescriptor.MAX_CATEGORY_UTF8_BYTES,
                )
            }
        val subtitle = if (mapping.mediaKind == ProviderMediaKinds.Episode) {
            optionalWireText(
                value = seriesName,
                label = "media subtitle",
                maxBytes = SubscriptionContentItemDescriptor.MAX_SUBTITLE_UTF8_BYTES,
            )
        } else {
            null
        }
        val safeOverview = optionalWireText(
            value = overview?.normalizeOverviewLineBreaks(),
            label = "media overview",
            maxBytes = SubscriptionContentItemDescriptor.MAX_OVERVIEW_UTF8_BYTES,
        )
        val safeProductionYear = productionYear?.also { year ->
            if (year !in 1..SubscriptionContentItemDescriptor.MAX_PRODUCTION_YEAR) {
                throw EmbyProtocolException("Provider media production year is out of range")
            }
        }
        val safeSeasonNumber = parentIndexNumber?.also { number ->
            if (number !in 0..SubscriptionContentItemDescriptor.MAX_INDEX_NUMBER) {
                throw EmbyProtocolException("Provider media season number is out of range")
            }
        }
        val safeEpisodeNumber = indexNumber?.also { number ->
            if (number !in 0..SubscriptionContentItemDescriptor.MAX_INDEX_NUMBER) {
                throw EmbyProtocolException("Provider media episode number is out of range")
            }
        }
        return SubscriptionContentItemDescriptor(
            reference = PlaybackReference(
                providerId = EmbyCompatibleProvider.ID,
                itemId = itemId,
                sourceType = mapping.sourceType,
            ),
            mediaKind = mapping.mediaKind,
            title = title,
            playable = mapping.playable,
            browsable = mapping.browsable,
            imageUrl = imageUrl,
            category = category,
            subtitle = subtitle,
            overview = safeOverview,
            productionYear = safeProductionYear,
            seasonNumber = safeSeasonNumber,
            episodeNumber = safeEpisodeNumber,
        )
    }

    private fun requiredWireText(
        value: String?,
        label: String,
        maxBytes: Int,
    ): String {
        val normalized = value?.trim()?.takeIf(String::isNotEmpty)
            ?: throw EmbyProtocolException("Provider $label is missing")
        if (normalized.encodeToByteArray().size > maxBytes) {
            throw EmbyProtocolException("Provider $label exceeds the host limit")
        }
        return normalized
    }

    private fun optionalWireText(
        value: String?,
        label: String,
        maxBytes: Int,
    ): String? {
        val normalized = value?.trim()?.takeIf(String::isNotEmpty) ?: return null
        if (normalized.encodeToByteArray().size > maxBytes) {
            throw EmbyProtocolException("Provider $label exceeds the host limit")
        }
        return normalized
    }

    private fun String.normalizeOverviewLineBreaks(): String =
        replace("\r\n", "\n").replace('\r', '\n')

    private fun url(baseUrl: String, path: String): HttpUrl = baseUrl
        .toHttpUrl()
        .newBuilder()
        .addPathSegments(path.trimStart('/'))
        .build()

    private fun urlWithSegments(baseUrl: String, vararg pathSegments: String): HttpUrl =
        baseUrl.toHttpUrl()
            .newBuilder()
            .apply {
                pathSegments.forEach(::addPathSegment)
            }
            .build()

    private fun resolvePlaybackUrl(
        baseUrl: String,
        reference: PlaybackReference,
        mediaSource: MediaSourceResponse,
        playSessionId: String?,
        allowTranscoding: Boolean,
        livePlayback: Boolean,
    ): ResolvedPlaybackUrl {
        if (mediaSource.supportsDirectPlay == true) {
            mediaSource.path
                .toAbsoluteHttpUrl()
                ?.let { url ->
                    return ResolvedPlaybackUrl(
                        url = url.toString(),
                        playMethod = PlaybackMethods.DirectPlay,
                    )
                }
            if (!livePlayback) {
                return ResolvedPlaybackUrl(
                    url = buildStaticPlaybackUrl(
                        baseUrl = baseUrl,
                        reference = reference,
                        mediaSource = mediaSource,
                        playSessionId = playSessionId,
                    ),
                    playMethod = PlaybackMethods.DirectPlay,
                )
            }
        }
        if (mediaSource.supportsDirectStream == true) {
            (mediaSource.directStreamUrl ?: mediaSource.transcodingUrl)
                .toResolvedHttpUrl(baseUrl)
                ?.let { url ->
                    return ResolvedPlaybackUrl(
                        url = url.toString(),
                        playMethod = PlaybackMethods.DirectStream,
                    )
                }
        }
        if (allowTranscoding && mediaSource.supportsTranscoding == true) {
            mediaSource.transcodingUrl
                .toResolvedHttpUrl(baseUrl)
                ?.let { url ->
                    return ResolvedPlaybackUrl(
                        url = url.toString(),
                        playMethod = PlaybackMethods.Transcode,
                    )
                }
        }
        throw EmbyProtocolException("Playback response did not contain a usable URL")
    }

    private fun buildStaticPlaybackUrl(
        baseUrl: String,
        reference: PlaybackReference,
        mediaSource: MediaSourceResponse,
        playSessionId: String?,
    ): String = urlWithSegments(
        baseUrl,
        "Videos",
        reference.itemId,
        "stream",
    )
        .newBuilder()
        .addQueryParameter("static", "true")
        .apply {
            mediaSource.id?.takeIf(String::isNotBlank)?.let { mediaSourceId ->
                addQueryParameter("MediaSourceId", mediaSourceId)
            }
            playSessionId?.takeIf(String::isNotBlank)?.let { sessionId ->
                addQueryParameter("PlaySessionId", sessionId)
            }
        }
        .build()
        .toString()

    private fun androidMedia3DeviceProfile(
        maxStreamingBitrate: Long?,
    ): DeviceProfile = DeviceProfile(
        name = "M3UAndroid Media3",
        maxStreamingBitrate = maxStreamingBitrate,
        directPlayProfiles = listOf(
            DirectPlayProfile(
                container = "mp4,m4v,mov,mkv,ts,mpegts",
                audioCodec = "aac,mp3",
                videoCodec = "h264",
                type = "Video",
            ),
        ),
        transcodingProfiles = listOf(
            TranscodingProfile(
                container = "ts",
                type = "Video",
                videoCodec = "h264",
                audioCodec = "aac",
                protocol = "hls",
                context = "Streaming",
                copyTimestamps = false,
                maxAudioChannels = "2",
                minSegments = 2,
                breakOnNonKeyFrames = true,
            ),
        ),
        codecProfiles = listOf(
            CodecProfile(
                type = "Video",
                codec = "h264",
                conditions = listOf(
                    ProfileCondition(
                        condition = "EqualsAny",
                        property = "VideoProfile",
                        value = "high|main|baseline|constrained baseline",
                    ),
                    ProfileCondition(
                        condition = "LessThanEqual",
                        property = "VideoLevel",
                        value = "41",
                    ),
                    ProfileCondition(
                        condition = "LessThanEqual",
                        property = "Width",
                        value = "1920",
                    ),
                    ProfileCondition(
                        condition = "LessThanEqual",
                        property = "Height",
                        value = "1080",
                    ),
                    ProfileCondition(
                        condition = "NotEquals",
                        property = "IsInterlaced",
                        value = "true",
                    ),
                ),
            ),
            CodecProfile(
                type = "VideoAudio",
                codec = null,
                conditions = listOf(
                    ProfileCondition(
                        condition = "LessThanEqual",
                        property = "AudioChannels",
                        value = "2",
                    ),
                ),
            ),
        ),
    )

    private fun String?.toResolvedHttpUrl(baseUrl: String): HttpUrl? {
        val candidate = this?.trim()?.takeIf(String::isNotEmpty) ?: return null
        val absolute = candidate.toHttpUrlOrNull()
        if (absolute != null) return absolute.takeIf { url -> url.isHttp() }
        val base = baseUrl.toHttpUrl()
        val origin = base.newBuilder()
            .encodedPath("/")
            .query(null)
            .fragment(null)
            .build()
        val originResolved = origin.resolve(
            if (candidate.startsWith('/')) candidate else "/$candidate"
        )?.takeIf { url -> url.isHttp() } ?: return null
        val basePath = base.encodedPath.trimEnd('/').ifEmpty { "/" }
        if (basePath == "/") return originResolved
        val candidatePath = originResolved.encodedPath
        if (candidatePath == basePath || candidatePath.startsWith("$basePath/")) {
            return originResolved
        }
        return originResolved.newBuilder()
            .encodedPath("$basePath$candidatePath")
            .build()
    }

    private fun String?.toAbsoluteHttpUrl(): HttpUrl? {
        val candidate = this?.trim()?.takeIf(String::isNotEmpty) ?: return null
        return candidate.toHttpUrlOrNull()?.takeIf { url -> url.isHttp() }
    }

    private fun HttpUrl.isHttp(): Boolean = scheme == "http" || scheme == "https"

    private fun PlaybackMethod.toEmbyValue(): String = when (this) {
        PlaybackMethods.DirectPlay -> "DirectPlay"
        PlaybackMethods.Transcode -> "Transcode"
        else -> "DirectStream"
    }

    private fun PlaybackSessionEvent.toEmbyEventName(): String? = when (this) {
        PlaybackSessionEvents.Started -> null
        PlaybackSessionEvents.Paused -> "pause"
        PlaybackSessionEvents.Resumed -> "unpause"
        else -> "timeupdate"
    }

    private fun detectProviderKind(productName: String?): ProviderKind? {
        val identity = productName?.lowercase().orEmpty()
        return when {
            "jellyfin" in identity -> EmbyCompatibleProviderKinds.Jellyfin
            "emby" in identity -> EmbyCompatibleProviderKinds.Emby
            else -> null
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

            else -> error("Unsupported Emby-compatible provider kind")
        }
    }

    companion object {
        internal const val MAX_JSON_RESPONSE_BYTES = 4 * 1024 * 1024
        internal const val CHANNEL_PAGE_SIZE = 500
        private const val MAX_CHANNELS_PER_REFRESH = 50_000
        private const val MAX_CHANNEL_PAGE_REQUESTS = 512
        private const val MAX_CONTENT_ITEMS = 50_000
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
        private const val LIVE_PLAYBACK_SOURCE_TYPE = "live_tv"
        private const val PLAYBACK_SOURCE_TYPE = LIVE_PLAYBACK_SOURCE_TYPE
        private const val MOVIE_PLAYBACK_SOURCE_TYPE = "movie"
        private const val SERIES_PLAYBACK_SOURCE_TYPE = "series"
        private const val EPISODE_PLAYBACK_SOURCE_TYPE = "episode"
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

private enum class ExpectedContentKind {
    ROOT,
    EPISODE,
}

private data class ContentKindMapping(
    val mediaKind: ProviderMediaKind,
    val sourceType: String,
    val playable: Boolean,
    val browsable: Boolean,
)

private data class ResolvedPlaybackUrl(
    val url: String,
    val playMethod: PlaybackMethod,
)

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
    @SerialName("ImageTags") val imageTags: Map<String, String>? = null,
    @SerialName("PrimaryImageTag") val primaryImageTag: String? = null,
)

@Serializable
private data class ContentItemsResponse(
    @SerialName("Items") val items: List<ContentItemResponse>? = null,
    @SerialName("TotalRecordCount") val totalRecordCount: Int? = null,
)

@Serializable
private data class ContentItemResponse(
    @SerialName("Id") val id: String? = null,
    @SerialName("Name") val name: String? = null,
    @SerialName("Type") val type: String? = null,
    @SerialName("ImageTags") val imageTags: Map<String, String>? = null,
    @SerialName("PrimaryImageTag") val primaryImageTag: String? = null,
    @SerialName("Genres") val genres: List<String>? = null,
    @SerialName("Overview") val overview: String? = null,
    @SerialName("ProductionYear") val productionYear: Int? = null,
    @SerialName("SeriesName") val seriesName: String? = null,
    @SerialName("ParentIndexNumber") val parentIndexNumber: Int? = null,
    @SerialName("IndexNumber") val indexNumber: Int? = null,
)

@Serializable
private data class PlaybackInfoRequest(
    @SerialName("UserId") val userId: String,
    @SerialName("MediaSourceId") val mediaSourceId: String? = null,
    @SerialName("StartTimeTicks") val startTimeTicks: Long,
    @SerialName("IsPlayback") val isPlayback: Boolean,
    @SerialName("AutoOpenLiveStream") val autoOpenLiveStream: Boolean,
    @SerialName("EnableDirectPlay") val enableDirectPlay: Boolean,
    @SerialName("EnableDirectStream") val enableDirectStream: Boolean,
    @SerialName("EnableTranscoding") val enableTranscoding: Boolean,
    @SerialName("AllowVideoStreamCopy") val allowVideoStreamCopy: Boolean,
    @SerialName("AllowAudioStreamCopy") val allowAudioStreamCopy: Boolean,
    @SerialName("MaxStreamingBitrate") val maxStreamingBitrate: Long? = null,
    @SerialName("DeviceProfile") val deviceProfile: DeviceProfile,
)

@Serializable
private data class DeviceProfile(
    @SerialName("Name") val name: String,
    @SerialName("SupportedMediaTypes") val supportedMediaTypes: String = "Video",
    @SerialName("MaxStreamingBitrate") val maxStreamingBitrate: Long? = null,
    @SerialName("DirectPlayProfiles")
    val directPlayProfiles: List<DirectPlayProfile>,
    @SerialName("TranscodingProfiles")
    val transcodingProfiles: List<TranscodingProfile>,
    @SerialName("ContainerProfiles")
    val containerProfiles: List<ContainerProfile> = emptyList(),
    @SerialName("CodecProfiles")
    val codecProfiles: List<CodecProfile>,
    @SerialName("SubtitleProfiles")
    val subtitleProfiles: List<SubtitleProfile> = emptyList(),
)

@Serializable
private data class DirectPlayProfile(
    @SerialName("Container") val container: String,
    @SerialName("AudioCodec") val audioCodec: String,
    @SerialName("VideoCodec") val videoCodec: String,
    @SerialName("Type") val type: String,
)

@Serializable
private data class TranscodingProfile(
    @SerialName("Container") val container: String,
    @SerialName("Type") val type: String,
    @SerialName("VideoCodec") val videoCodec: String,
    @SerialName("AudioCodec") val audioCodec: String,
    @SerialName("Protocol") val protocol: String,
    @SerialName("Context") val context: String,
    @SerialName("CopyTimestamps") val copyTimestamps: Boolean,
    @SerialName("MaxAudioChannels") val maxAudioChannels: String,
    @SerialName("MinSegments") val minSegments: Int,
    @SerialName("BreakOnNonKeyFrames") val breakOnNonKeyFrames: Boolean,
)

@Serializable
private class ContainerProfile

@Serializable
private data class CodecProfile(
    @SerialName("Type") val type: String,
    @SerialName("Codec") val codec: String? = null,
    @SerialName("Conditions") val conditions: List<ProfileCondition>,
)

@Serializable
private data class ProfileCondition(
    @SerialName("Condition") val condition: String,
    @SerialName("Property") val property: String,
    @SerialName("Value") val value: String,
    @SerialName("IsRequired") val isRequired: Boolean = false,
)

@Serializable
private class SubtitleProfile

@Serializable
private data class PlaybackInfoResponse(
    @SerialName("MediaSources") val mediaSources: List<MediaSourceResponse>? = null,
    @SerialName("PlaySessionId") val playSessionId: String? = null,
    @SerialName("ErrorCode") val errorCode: String? = null,
)

@Serializable
private data class MediaSourceResponse(
    @SerialName("Id") val id: String? = null,
    @SerialName("Path") val path: String? = null,
    @SerialName("SupportsDirectPlay") val supportsDirectPlay: Boolean? = null,
    @SerialName("SupportsDirectStream") val supportsDirectStream: Boolean? = null,
    @SerialName("SupportsTranscoding") val supportsTranscoding: Boolean? = null,
    @SerialName("DirectStreamUrl") val directStreamUrl: String? = null,
    @SerialName("TranscodingUrl") val transcodingUrl: String? = null,
    @SerialName("LiveStreamId") val liveStreamId: String? = null,
    @SerialName("RequiredHttpHeaders") val requiredHttpHeaders: Map<String, String>? = null,
)

@Serializable
private data class PlaybackUpdateRequest(
    @SerialName("ItemId") val itemId: String,
    @SerialName("MediaSourceId") val mediaSourceId: String? = null,
    @SerialName("PlaySessionId") val playSessionId: String? = null,
    @SerialName("LiveStreamId") val liveStreamId: String? = null,
    @SerialName("PositionTicks") val positionTicks: Long,
    @SerialName("IsPaused") val isPaused: Boolean,
    @SerialName("PlayMethod") val playMethod: String,
    @SerialName("EventName") val eventName: String? = null,
)

@Serializable
private data class PlaybackStoppedRequest(
    @SerialName("ItemId") val itemId: String,
    @SerialName("MediaSourceId") val mediaSourceId: String? = null,
    @SerialName("PlaySessionId") val playSessionId: String? = null,
    @SerialName("LiveStreamId") val liveStreamId: String? = null,
    @SerialName("PositionTicks") val positionTicks: Long = 0L,
)
