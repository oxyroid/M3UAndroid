package com.m3u.data.extension.emby

import android.content.Context
import com.m3u.core.foundation.architecture.Publisher
import com.m3u.data.api.ProviderOkhttpClient
import com.m3u.extension.api.subscription.EmbyCompatibleProviderKinds
import com.m3u.extension.api.subscription.PlaybackPreferences
import com.m3u.extension.api.subscription.PlaybackReference
import com.m3u.extension.api.subscription.PlaybackSessionDescriptor
import com.m3u.extension.api.subscription.ProviderKind
import com.m3u.extension.api.subscription.SubscriptionChannelDescriptor
import com.m3u.extension.api.subscription.ValidatedProviderAccount
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.UUID
import javax.inject.Inject

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

    suspend fun closePlayback(
        account: ValidatedProviderAccount,
        accessToken: String,
        reference: PlaybackReference,
        session: PlaybackSessionDescriptor,
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
    val session: PlaybackSessionDescriptor?,
)

internal class OkHttpEmbyCompatibleClient @Inject constructor(
    @ApplicationContext context: Context,
    publisher: Publisher,
    @param:ProviderOkhttpClient private val okHttpClient: OkHttpClient,
) : EmbyCompatibleClient {
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }
    private val clientIdentity = ClientIdentity(
        device = publisher.model.ifBlank { "Android" },
        deviceId = context.providerDeviceId(),
        version = publisher.versionName,
    )

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
                    .addQueryParameter("StartIndex", "0")
                    .addQueryParameter("EnableImages", "true")
                    .build()
            )
            .get()
            .build()
        val response: LiveTvChannelsResponse = executeJson(request)
        val providerId = EmbyCompatibleProvider.ID
        val channels = response.items.orEmpty().mapNotNull { item ->
            val itemId = item.id?.takeIf(String::isNotBlank) ?: return@mapNotNull null
            val title = item.name?.takeIf(String::isNotBlank) ?: return@mapNotNull null
            val imageUrl = item.primaryImageTag?.takeIf(String::isNotBlank)?.let { tag ->
                url(account.normalizedBaseUrl, "Items/$itemId/Images/Primary")
                    .newBuilder()
                    .addQueryParameter("tag", tag)
                    .build()
                    .toString()
            }
            SubscriptionChannelDescriptor(
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
                epgReference = itemId,
                metadata = buildMap {
                    item.channelNumber?.takeIf(String::isNotBlank)?.let { put("channelNumber", it) }
                },
            )
        }
        EmbyChannelRefresh(
            channels = channels,
            totalRecordCount = response.totalRecordCount ?: channels.size,
        )
    }

    override suspend fun resolvePlayback(
        account: ValidatedProviderAccount,
        accessToken: String,
        reference: PlaybackReference,
        preferences: PlaybackPreferences,
    ): EmbyPlaybackSource = withContext(Dispatchers.IO) {
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
        val mediaSource = response.mediaSources.orEmpty()
            .firstOrNull { source -> reference.mediaSourceId != null && source.id == reference.mediaSourceId }
            ?: response.mediaSources.orEmpty().firstOrNull()
            ?: throw EmbyProtocolException("Playback response did not contain a media source")
        val resolvedUrl = sequenceOf(
            mediaSource.directStreamUrl,
            mediaSource.transcodingUrl.takeIf { preferences.allowTranscoding },
            mediaSource.path,
            reference.fallbackDirectUrl,
        )
            .filterNotNull()
            .map(String::trim)
            .firstOrNull(String::isNotEmpty)
            ?.let { candidate -> absoluteUrl(account.normalizedBaseUrl, candidate) }
            ?: throw EmbyProtocolException("Playback response did not contain a usable URL")
        val headers = buildMap {
            putAll(mediaSource.requiredHttpHeaders.orEmpty())
            putAll(
                clientIdentity.authenticationHeaders(
                    providerKind = account.detectedKind,
                    accessToken = accessToken,
                    userId = account.userId,
                )
            )
        }
        val session = PlaybackSessionDescriptor(
            playSessionId = response.playSessionId?.takeIf(String::isNotBlank),
            liveStreamId = mediaSource.liveStreamId?.takeIf(String::isNotBlank),
        ).takeIf { it.playSessionId != null || it.liveStreamId != null }

        EmbyPlaybackSource(
            url = resolvedUrl,
            headers = headers,
            mediaSourceId = mediaSource.id,
            session = session,
        )
    }

    override suspend fun closePlayback(
        account: ValidatedProviderAccount,
        accessToken: String,
        reference: PlaybackReference,
        session: PlaybackSessionDescriptor,
    ): Boolean = withContext(Dispatchers.IO) {
        val stoppedBody = json.encodeToString(
            PlaybackStoppedRequest(
                itemId = reference.itemId,
                mediaSourceId = reference.mediaSourceId,
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
        session.liveStreamId?.let { liveStreamId ->
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
        true
    }

    private inline fun <reified T> executeJson(request: Request): T {
        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw EmbyHttpException(response.code, "Provider request failed with HTTP ${response.code}")
            }
            val body = response.body.string()
            if (body.isEmpty()) {
                throw EmbyProtocolException("Provider response body was empty")
            }
            return json.decodeFromString(body)
        }
    }

    private fun executeNoContent(request: Request, allowNotFound: Boolean) {
        okHttpClient.newCall(request).execute().use { response ->
            if (response.isSuccessful || allowNotFound && response.code == 404) return
            throw EmbyHttpException(response.code, "Provider request failed with HTTP ${response.code}")
        }
    }

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

    private companion object {
        val JSON_MEDIA_TYPE = "application/json".toMediaType()
        const val CLIENT_NAME = "M3UAndroid"
        const val AUTHORIZATION_HEADER = "Authorization"
        const val EMBY_TOKEN_HEADER = "X-Emby-Token"
        const val DEFAULT_CHANNEL_CATEGORY = "Live TV"
        const val PLAYBACK_SOURCE_TYPE = "live_tv"
    }
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
