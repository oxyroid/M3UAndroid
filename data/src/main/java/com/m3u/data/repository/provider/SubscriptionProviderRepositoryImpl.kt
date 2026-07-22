package com.m3u.data.repository.provider

import com.m3u.data.database.dao.PlaylistDao
import com.m3u.data.database.dao.ProviderDao
import com.m3u.data.database.model.ChannelPlaybackReference
import com.m3u.data.database.model.DataSource
import com.m3u.data.database.model.ProviderAccount
import com.m3u.data.extension.SubscriptionProviderImporter
import com.m3u.data.extension.emby.EmbyCompatibleProvider
import com.m3u.extension.api.ExtensionCapabilityIds
import com.m3u.extension.api.ExtensionHookIds
import com.m3u.extension.api.ExtensionHookOutcome
import com.m3u.extension.api.ExtensionId
import com.m3u.extension.api.ExtensionPayload
import com.m3u.extension.api.ExtensionResult
import com.m3u.extension.api.subscription.EmbyCompatibleProviderKinds
import com.m3u.extension.api.subscription.PlaybackReference
import com.m3u.extension.api.subscription.PlaybackSessionCloseReason
import com.m3u.extension.api.subscription.PlaybackSessionCloseRequest
import com.m3u.extension.api.subscription.PlaybackSessionCloseResult
import com.m3u.extension.api.subscription.PlaybackSessionDescriptor
import com.m3u.extension.api.subscription.PlaybackSourceResolveRequest
import com.m3u.extension.api.subscription.PlaybackSourceResolveResult
import com.m3u.extension.api.subscription.ProviderAccountReference
import com.m3u.extension.api.subscription.ProviderAuthentication
import com.m3u.extension.api.subscription.ProviderCredential
import com.m3u.extension.api.subscription.ProviderKind
import com.m3u.extension.api.subscription.SubscriptionContentRefreshRequest
import com.m3u.extension.api.subscription.SubscriptionContentRefreshResult
import com.m3u.extension.api.subscription.SubscriptionProviderValidateRequest
import com.m3u.extension.api.subscription.SubscriptionProviderValidateResult
import com.m3u.extension.api.subscription.SubscriptionRefreshReason
import com.m3u.extension.runtime.ExtensionRuntime
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

internal class SubscriptionProviderRepositoryImpl @Inject constructor(
    private val runtime: ExtensionRuntime,
    private val providerDao: ProviderDao,
    private val playlistDao: PlaylistDao,
    private val importer: SubscriptionProviderImporter,
) : SubscriptionProviderRepository {
    private val activePlaybackContexts = ConcurrentHashMap<ProviderPlaybackSession, PlaybackCloseContext>()

    override suspend fun subscribe(request: ProviderSubscriptionRequest): ProviderSubscriptionResult {
        val requestedKind = request.source.toProviderKind()
        val validation = runtime.invoke(
            extensionId = PROVIDER_ID,
            hook = ExtensionHookIds.SubscriptionProviderValidate,
            grantedCapabilities = VALIDATE_CAPABILITIES,
            payload = SubscriptionProviderValidateRequest(
                baseUrl = request.baseUrl,
                providerKind = requestedKind,
                authentication = ProviderAuthentication.UsernamePassword(
                    username = request.username,
                    password = request.password,
                ),
            ),
        ).payloadOrThrow<SubscriptionProviderValidateResult>()
        val validated = validation.account
        val existing = providerDao.getAccountByRemoteIdentity(
            providerId = PROVIDER_ID.value,
            serverId = validated.serverId,
            userId = validated.userId,
        )
        val accountId = existing?.id ?: UUID.randomUUID().toString()
        val playlistUrl = existing?.playlistUrl ?: providerPlaylistUrl(accountId)
        val accountReference = ProviderAccountReference(
            accountId = accountId,
            providerId = PROVIDER_ID,
            providerKind = validated.detectedKind,
            baseUrl = validated.normalizedBaseUrl,
            serverId = validated.serverId,
            serverName = validated.serverName,
            serverVersion = validated.serverVersion,
            userId = validated.userId,
            username = validated.username,
        )
        val refresh = refresh(
            account = accountReference,
            accessToken = validation.accessToken,
            reason = SubscriptionRefreshReason.INITIAL,
        )
        val account = accountReference.toEntity(playlistUrl)
        val source = account.providerKind.toDataSource()
        val count = importer.import(
            title = request.title,
            source = source,
            account = account,
            accessToken = validation.accessToken,
            refresh = refresh,
        )
        return ProviderSubscriptionResult(playlistUrl = playlistUrl, channelCount = count)
    }

    override suspend fun refresh(playlistUrl: String): ProviderSubscriptionResult {
        val account = providerDao.getAccountByPlaylistUrl(playlistUrl)
            ?: throw ProviderOperationException("Provider account was not found")
        val credential = providerDao.getCredential(account.id)
            ?: throw ProviderOperationException("Provider credential was not found")
        val title = playlistDao.get(playlistUrl)?.title
            ?: throw ProviderOperationException("Provider playlist was not found")
        val refresh = refresh(
            account = account.toReference(),
            accessToken = credential.accessToken,
            reason = SubscriptionRefreshReason.MANUAL,
        )
        val count = importer.import(
            title = title,
            source = account.providerKind.toDataSource(),
            account = account,
            accessToken = credential.accessToken,
            refresh = refresh,
        )
        return ProviderSubscriptionResult(playlistUrl = playlistUrl, channelCount = count)
    }

    override suspend fun resolvePlayback(channelId: Int): ProviderPlaybackSource? {
        val reference = providerDao.getPlaybackReference(channelId) ?: return null
        val account = providerDao.getAccount(reference.accountId)
            ?: throw ProviderOperationException("Provider account was not found")
        val credential = providerDao.getCredential(reference.accountId)
            ?: throw ProviderOperationException("Provider credential was not found")
        val payload = runtime.invoke(
            extensionId = ExtensionId(reference.providerId),
            hook = ExtensionHookIds.PlaybackSourceResolve,
            grantedCapabilities = PLAYBACK_CAPABILITIES,
            payload = PlaybackSourceResolveRequest(
                account = account.toReference(),
                credential = ProviderCredential(credential.accessToken),
                reference = reference.toContract(),
            ),
        ).payloadOrThrow<PlaybackSourceResolveResult>()
        val session = payload.session?.let { providerSession ->
            ProviderPlaybackSession(
                accountId = account.id,
                providerId = reference.providerId,
                itemId = reference.itemId,
                mediaSourceId = payload.mediaSourceId ?: reference.mediaSourceId,
                sourceType = reference.sourceType,
                fallbackDirectUrl = reference.fallbackDirectUrl,
                playSessionId = providerSession.playSessionId,
                liveStreamId = providerSession.liveStreamId,
            )
        }
        session?.let { activeSession ->
            activePlaybackContexts[activeSession] = PlaybackCloseContext(
                account = account.toReference(),
                accessToken = credential.accessToken,
            )
        }
        return ProviderPlaybackSource(
            url = payload.url,
            headers = payload.headers,
            session = session,
        )
    }

    override suspend fun closePlayback(
        session: ProviderPlaybackSession,
        reason: ProviderPlaybackCloseReason,
    ): Boolean {
        val context = activePlaybackContexts.remove(session) ?: run {
            val account = providerDao.getAccount(session.accountId) ?: return false
            val credential = providerDao.getCredential(session.accountId) ?: return false
            PlaybackCloseContext(
                account = account.toReference(),
                accessToken = credential.accessToken,
            )
        }
        val payload = runtime.invoke(
            extensionId = ExtensionId(session.providerId),
            hook = ExtensionHookIds.PlaybackSessionClose,
            grantedCapabilities = PLAYBACK_CAPABILITIES,
            payload = PlaybackSessionCloseRequest(
                account = context.account,
                credential = ProviderCredential(context.accessToken),
                reference = PlaybackReference(
                    providerId = ExtensionId(session.providerId),
                    itemId = session.itemId,
                    mediaSourceId = session.mediaSourceId,
                    sourceType = session.sourceType,
                    fallbackDirectUrl = session.fallbackDirectUrl,
                ),
                session = PlaybackSessionDescriptor(
                    playSessionId = session.playSessionId,
                    liveStreamId = session.liveStreamId,
                ),
                reason = reason.toContract(),
            ),
        ).payloadOrThrow<PlaybackSessionCloseResult>()
        return payload.closed
    }

    override suspend fun removeAccount(playlistUrl: String) {
        providerDao.deleteAccountByPlaylistUrl(playlistUrl)
    }

    private suspend fun refresh(
        account: ProviderAccountReference,
        accessToken: String,
        reason: SubscriptionRefreshReason,
    ): SubscriptionContentRefreshResult = runtime.invoke(
        extensionId = PROVIDER_ID,
        hook = ExtensionHookIds.SubscriptionContentRefresh,
        grantedCapabilities = REFRESH_CAPABILITIES,
        payload = SubscriptionContentRefreshRequest(
            account = account,
            credential = ProviderCredential(accessToken),
            reason = reason,
        ),
    ).payloadOrThrow()

    private inline fun <reified T : ExtensionPayload> ExtensionResult.payloadOrThrow(): T {
        return when (val current = outcome) {
            is ExtensionHookOutcome.Success -> current.payload as? T
                ?: throw ProviderOperationException("Provider returned an unexpected result")

            is ExtensionHookOutcome.Failure -> throw ProviderOperationException(current.error.message)
        }
    }

    private fun ProviderAccount.toReference(): ProviderAccountReference = ProviderAccountReference(
        accountId = id,
        providerId = ExtensionId(providerId),
        providerKind = ProviderKind(providerKind),
        baseUrl = baseUrl,
        serverId = serverId,
        serverName = serverName,
        serverVersion = serverVersion,
        userId = userId,
        username = username,
    )

    private fun ProviderAccountReference.toEntity(playlistUrl: String): ProviderAccount = ProviderAccount(
        id = accountId,
        providerId = providerId.value,
        providerKind = providerKind.value,
        baseUrl = baseUrl,
        serverId = serverId,
        serverName = serverName,
        serverVersion = serverVersion,
        userId = userId,
        username = username,
        playlistUrl = playlistUrl,
    )

    private fun ChannelPlaybackReference.toContract(): PlaybackReference = PlaybackReference(
        providerId = ExtensionId(providerId),
        itemId = itemId,
        mediaSourceId = mediaSourceId,
        sourceType = sourceType,
        fallbackDirectUrl = fallbackDirectUrl,
    )

    private fun DataSource.toProviderKind(): ProviderKind = when (this) {
        DataSource.Emby -> EmbyCompatibleProviderKinds.Emby
        DataSource.Jellyfin -> EmbyCompatibleProviderKinds.Jellyfin
        else -> throw ProviderOperationException("Unsupported provider data source")
    }

    private fun String.toDataSource(): DataSource = when (this) {
        EmbyCompatibleProviderKinds.Emby.value -> DataSource.Emby
        EmbyCompatibleProviderKinds.Jellyfin.value -> DataSource.Jellyfin
        else -> throw ProviderOperationException("Unsupported provider kind")
    }

    private fun ProviderPlaybackCloseReason.toContract(): PlaybackSessionCloseReason = when (this) {
        ProviderPlaybackCloseReason.STOPPED -> PlaybackSessionCloseReason.STOPPED
        ProviderPlaybackCloseReason.CHANNEL_CHANGED -> PlaybackSessionCloseReason.CHANNEL_CHANGED
        ProviderPlaybackCloseReason.PLAYBACK_FAILED -> PlaybackSessionCloseReason.PLAYBACK_FAILED
    }

    private fun providerPlaylistUrl(accountId: String): String = "m3u-provider://account/$accountId/live"

    private data class PlaybackCloseContext(
        val account: ProviderAccountReference,
        val accessToken: String,
    )

    private companion object {
        val PROVIDER_ID = EmbyCompatibleProvider.ID
        val VALIDATE_CAPABILITIES = setOf(
            ExtensionCapabilityIds.Network,
            ExtensionCapabilityIds.CredentialWrite,
        )
        val REFRESH_CAPABILITIES = setOf(
            ExtensionCapabilityIds.Network,
            ExtensionCapabilityIds.CredentialRead,
            ExtensionCapabilityIds.SubscriptionRead,
        )
        val PLAYBACK_CAPABILITIES = setOf(
            ExtensionCapabilityIds.Network,
            ExtensionCapabilityIds.CredentialRead,
            ExtensionCapabilityIds.PlaybackResolve,
        )
    }
}
