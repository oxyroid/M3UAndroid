package com.m3u.data.repository.provider

import com.m3u.data.database.dao.PlaylistDao
import com.m3u.data.database.dao.ProviderDao
import com.m3u.data.database.model.ChannelPlaybackReference
import com.m3u.data.database.model.DataSource
import com.m3u.data.database.model.ProviderAccount
import com.m3u.data.database.model.ProviderPlaybackSessionEntity
import com.m3u.data.extension.SubscriptionProviderImporter
import com.m3u.data.extension.security.CredentialVault
import com.m3u.data.repository.extension.ExtensionContributionScheduler
import com.m3u.extension.api.HookResult
import com.m3u.extension.api.ExtensionId
import com.m3u.extension.api.ExtensionPayload
import com.m3u.extension.api.ExtensionResult
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
import com.m3u.extension.api.subscription.SubscriptionProviderDescriptor
import com.m3u.extension.api.subscription.SubscriptionProviderDiscoverRequest
import com.m3u.extension.api.security.CredentialHandle
import com.m3u.extension.api.subscription.SubscriptionRefreshReason
import com.m3u.extension.api.subscription.SubscriptionHookSpecs
import com.m3u.extension.runtime.ExtensionRuntime
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

internal class SubscriptionProviderRepositoryImpl @Inject constructor(
    private val runtime: ExtensionRuntime,
    private val providerDao: ProviderDao,
    private val playlistDao: PlaylistDao,
    private val importer: SubscriptionProviderImporter,
    private val credentialVault: CredentialVault,
    private val extensionContributionScheduler: ExtensionContributionScheduler,
) : SubscriptionProviderRepository {
    private val activePlaybackContexts = ConcurrentHashMap<ProviderPlaybackSession, PlaybackCloseContext>()

    override suspend fun discoverProviders(): List<SubscriptionProviderDescriptor> = runtime
        .extensionsSupporting(SubscriptionHookSpecs.Discover.hook)
        .flatMap { extension ->
            runtime.invoke(
                extensionId = extension.manifest.id,
                spec = SubscriptionHookSpecs.Discover,
                request = SubscriptionProviderDiscoverRequest(),
            ).payloadOrThrow().providers
        }

    override fun stageCredential(secret: String): CredentialHandle = credentialVault.stage(secret)

    override suspend fun subscribe(request: ProviderSubscriptionRequest): ProviderSubscriptionResult {
        val validation = runtime.invoke(
            extensionId = request.providerId,
            spec = SubscriptionHookSpecs.Validate,
            request = SubscriptionProviderValidateRequest(
                providerKind = request.providerKind,
                settingValues = request.settingValues,
                credentialHandles = request.credentialHandles,
            ),
        ).payloadOrThrow<SubscriptionProviderValidateResult>()
        val accessToken = credentialVault.consume(validation.credential)
            ?: throw ProviderOperationException("Provider credential capture expired")
        val validated = validation.account
        val existing = providerDao.getAccountByRemoteIdentity(
            providerId = request.providerId.value,
            serverId = validated.serverId,
            userId = validated.userId,
        )
        val accountId = existing?.id ?: UUID.randomUUID().toString()
        val playlistUrl = existing?.playlistUrl ?: providerPlaylistUrl(accountId)
        val accountReference = ProviderAccountReference(
            accountId = accountId,
            providerId = request.providerId,
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
            credentialHandle = credentialVault.stage(accessToken),
            reason = SubscriptionRefreshReason.Initial,
        )
        val account = accountReference.toEntity(playlistUrl)
        val source = DataSource.Provider
        val count = importer.import(
            title = request.title,
            source = source,
            account = account,
            accessToken = accessToken,
            refresh = refresh,
        )
        extensionContributionScheduler.enqueue(account.playlistUrl)
        return ProviderSubscriptionResult(playlistUrl = playlistUrl, channelCount = count)
    }

    override suspend fun refresh(
        playlistUrl: String,
        reason: SubscriptionRefreshReason,
    ): ProviderSubscriptionResult {
        val account = providerDao.getAccountByPlaylistUrl(playlistUrl)
            ?: throw ProviderOperationException("Provider account was not found")
        val credential = providerDao.getCredential(account.id)
            ?: throw ProviderOperationException("Provider credentials must be entered again")
        val title = playlistDao.get(playlistUrl)?.title
            ?: throw ProviderOperationException("Provider playlist was not found")
        val refresh = refresh(
            account = account.toReference(),
            credentialHandle = CredentialHandle(credential.credentialHandle),
            reason = reason,
        )
        val accessToken = credentialVault.decrypt(credential) ?: run {
            providerDao.deleteCredential(account.id)
            providerDao.setRequiresReauthentication(account.id, true)
            throw ProviderOperationException("Provider credentials must be entered again")
        }
        val count = importer.import(
            title = title,
            source = DataSource.Provider,
            account = account,
            accessToken = accessToken,
            refresh = refresh,
        )
        extensionContributionScheduler.enqueue(account.playlistUrl)
        return ProviderSubscriptionResult(playlistUrl = playlistUrl, channelCount = count)
    }

    override suspend fun resolvePlayback(channelId: Int): ProviderPlaybackSource? {
        val reference = providerDao.getPlaybackReference(channelId) ?: return null
        val account = providerDao.getAccount(reference.accountId)
            ?: throw ProviderOperationException("Provider account was not found")
        val credential = providerDao.getCredential(reference.accountId)
            ?: throw ProviderOperationException("Provider credentials must be entered again")
        val payload = runtime.invoke(
            extensionId = ExtensionId(reference.providerId),
            spec = SubscriptionHookSpecs.ResolvePlayback,
            request = PlaybackSourceResolveRequest(
                account = account.toReference(),
                credential = ProviderCredential(CredentialHandle(credential.credentialHandle)),
                reference = reference.toContract(),
            ),
        ).payloadOrThrow<PlaybackSourceResolveResult>()
        val session = payload.session?.let { providerSession ->
            ProviderPlaybackSession(
                id = UUID.randomUUID().toString(),
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
            val closeCredential = credentialVault.decrypt(credential) ?: run {
                providerDao.deleteCredential(account.id)
                providerDao.setRequiresReauthentication(account.id, true)
                throw ProviderOperationException("Provider credentials must be entered again")
            }
            providerDao.insertOrReplace(activeSession.toEntity())
            activePlaybackContexts[activeSession] = PlaybackCloseContext(
                account = account.toReference(),
                credentialHandle = credentialVault.stage(closeCredential),
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
                credentialHandle = CredentialHandle(credential.credentialHandle),
            )
        }
        val payload = runtime.invoke(
            extensionId = ExtensionId(session.providerId),
            spec = SubscriptionHookSpecs.ClosePlayback,
            request = PlaybackSessionCloseRequest(
                account = context.account,
                credential = ProviderCredential(context.credentialHandle),
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
        if (payload.closed) providerDao.deletePlaybackSession(session.id)
        return payload.closed
    }

    override suspend fun removeAccount(playlistUrl: String) {
        providerDao.deleteAccountByPlaylistUrl(playlistUrl)
    }

    override suspend fun closeOrphanedPlaybackSessions(): Int {
        var closed = 0
        providerDao.getPlaybackSessions().forEach { entity ->
            if (closePlayback(entity.toModel(), ProviderPlaybackCloseReason.STOPPED)) closed++
        }
        return closed
    }

    private suspend fun refresh(
        account: ProviderAccountReference,
        credentialHandle: CredentialHandle,
        reason: SubscriptionRefreshReason,
    ): SubscriptionContentRefreshResult = runtime.invoke(
        extensionId = account.providerId,
        spec = SubscriptionHookSpecs.Refresh,
        request = SubscriptionContentRefreshRequest(
            account = account,
            credential = ProviderCredential(credentialHandle),
            reason = reason,
        ),
    ).payloadOrThrow()


    private fun <T : ExtensionPayload> ExtensionResult<T>.payloadOrThrow(): T {
        return when (val current = outcome) {
            is HookResult.Success -> current.payload
            is HookResult.Failure -> throw ProviderOperationException(current.error.message)
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

    private fun ProviderPlaybackCloseReason.toContract(): PlaybackSessionCloseReason = when (this) {
        ProviderPlaybackCloseReason.STOPPED -> PlaybackSessionCloseReason.Stopped
        ProviderPlaybackCloseReason.CHANNEL_CHANGED -> PlaybackSessionCloseReason.ChannelChanged
        ProviderPlaybackCloseReason.PLAYBACK_FAILED -> PlaybackSessionCloseReason.PlaybackFailed
    }

    private fun ProviderPlaybackSession.toEntity() = ProviderPlaybackSessionEntity(
        id = id,
        accountId = accountId,
        providerId = providerId,
        itemId = itemId,
        mediaSourceId = mediaSourceId,
        sourceType = sourceType,
        fallbackDirectUrl = fallbackDirectUrl,
        playSessionId = playSessionId,
        liveStreamId = liveStreamId,
        createdAtEpochMillis = System.currentTimeMillis(),
    )

    private fun ProviderPlaybackSessionEntity.toModel() = ProviderPlaybackSession(
        id = id,
        accountId = accountId,
        providerId = providerId,
        itemId = itemId,
        mediaSourceId = mediaSourceId,
        sourceType = sourceType,
        fallbackDirectUrl = fallbackDirectUrl,
        playSessionId = playSessionId,
        liveStreamId = liveStreamId,
    )

    private fun providerPlaylistUrl(accountId: String): String = "m3u-provider://account/$accountId/live"

    private data class PlaybackCloseContext(
        val account: ProviderAccountReference,
        val credentialHandle: CredentialHandle,
    )

}
