package com.m3u.data.repository.provider

import android.content.Context
import androidx.work.WorkManager
import com.m3u.data.database.dao.PlaylistDao
import com.m3u.data.database.dao.ProviderDao
import com.m3u.data.database.model.ChannelPlaybackReference
import com.m3u.data.database.model.DataSource
import com.m3u.data.database.model.ProviderAccount
import com.m3u.data.database.model.ProviderCredentialEntity
import com.m3u.data.database.model.ProviderPlaybackSessionEntity
import com.m3u.data.extension.SubscriptionProviderImporter
import com.m3u.data.extension.security.ActiveExtensionPrincipalRegistry
import com.m3u.data.extension.security.BrokerValueResolutionBudget
import com.m3u.data.extension.security.CredentialVault
import com.m3u.data.extension.security.ExtensionPrincipal
import com.m3u.data.extension.security.ExtensionPrincipalLease
import com.m3u.data.extension.security.InactiveExtensionPrincipalLeaseException
import com.m3u.data.extension.security.CapturedProviderAuthentication
import com.m3u.data.extension.security.ProviderBrokerScopeStore
import com.m3u.data.extension.security.ProviderCredentialMaterial
import com.m3u.data.extension.security.renderForHost
import com.m3u.data.extension.security.toCanonicalHttpOrigin
import com.m3u.data.repository.extension.ExtensionContributionScheduler
import com.m3u.data.repository.extension.ExtensionContributionRunCoordinator
import com.m3u.data.worker.ProviderSessionCleanupWorker
import com.m3u.extension.api.HookResult
import com.m3u.extension.api.Hook
import com.m3u.extension.api.ExtensionSettingType
import com.m3u.extension.api.ExtensionId
import com.m3u.extension.api.ExtensionPayload
import com.m3u.extension.api.ExtensionResult
import com.m3u.extension.api.security.BrokerScopeHandle
import com.m3u.extension.api.security.referencesCredential
import com.m3u.extension.api.security.referencesOpaqueContext
import com.m3u.extension.api.subscription.PlaybackReference
import com.m3u.extension.api.subscription.PlaybackHeaderValue
import com.m3u.extension.api.subscription.PlaybackSessionCloseReason
import com.m3u.extension.api.subscription.PlaybackSessionCloseRequest
import com.m3u.extension.api.subscription.PlaybackSessionCloseResult
import com.m3u.extension.api.subscription.PlaybackSessionDescriptor
import com.m3u.extension.api.subscription.PlaybackSourceResolveRequest
import com.m3u.extension.api.subscription.PlaybackSourceResolveResult
import com.m3u.extension.api.subscription.ProviderAccountReference
import com.m3u.extension.api.subscription.ProviderAuthenticationContextKeys
import com.m3u.extension.api.subscription.ProviderCredential
import com.m3u.extension.api.subscription.ProviderKind
import com.m3u.extension.api.subscription.ProviderValidationEvidence
import com.m3u.extension.api.subscription.SubscriptionContentRefreshRequest
import com.m3u.extension.api.subscription.SubscriptionContentRefreshResult
import com.m3u.extension.api.subscription.SubscriptionProviderValidateRequest
import com.m3u.extension.api.subscription.SubscriptionProviderValidateResult
import com.m3u.extension.api.subscription.SubscriptionProviderDescriptor
import com.m3u.extension.api.subscription.SubscriptionProviderDiscoverRequest
import com.m3u.extension.api.subscription.SubscriptionProviderErrorCodes
import com.m3u.extension.api.subscription.SubscriptionProviderSettingKeys
import com.m3u.extension.api.security.CredentialHandle
import com.m3u.extension.api.subscription.SubscriptionRefreshReason
import com.m3u.extension.api.subscription.SubscriptionHookSpecs
import com.m3u.extension.api.subscription.ValidatedProviderAccount
import com.m3u.extension.runtime.ExtensionExecutionKind
import com.m3u.extension.runtime.ExtensionRuntime
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import timber.log.Timber

internal class SubscriptionProviderRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val runtime: ExtensionRuntime,
    private val providerDao: ProviderDao,
    private val playlistDao: PlaylistDao,
    private val importer: SubscriptionProviderImporter,
    private val credentialVault: CredentialVault,
    private val extensionContributionScheduler: ExtensionContributionScheduler,
    private val extensionContributionRunCoordinator: ExtensionContributionRunCoordinator,
    private val activePrincipalRegistry: ActiveExtensionPrincipalRegistry,
    private val providerBrokerScopeStore: ProviderBrokerScopeStore,
    private val lifecycleCoordinator: ProviderLifecycleCoordinator,
) : SubscriptionProviderRepository {
    private val timber = Timber.tag("SubscriptionProviderRepository")
    private val activePlaybackContexts = ConcurrentHashMap<ProviderPlaybackSession, PlaybackCloseContext>()
    private val playbackSessionAdmissionMutex = Mutex()
    private val playbackSessionReservations = mutableSetOf<PlaybackSessionReservation>()

    override suspend fun discoverProviders(): List<DiscoveredSubscriptionProvider> = supervisorScope {
        val localeTag = context.resources.configuration.locales[0].toLanguageTag()
        val candidates = runtime.extensionsSupporting(SubscriptionHookSpecs.Discover.hook)
            .filter { extension ->
                extension.executionKind != ExtensionExecutionKind.EXTERNAL ||
                    activePrincipalRegistry.active(extension.manifest.id) != null
            }
        val attempts = candidates.map { extension ->
            async {
                try {
                    val descriptor = runtime.invoke(
                        extensionId = extension.manifest.id,
                        spec = SubscriptionHookSpecs.Discover,
                        request = SubscriptionProviderDiscoverRequest(
                            localeTag = localeTag,
                        ),
                        validateResponse = { response ->
                            require(response.provider.isUsableFor(extension.manifest.id))
                        },
                    ).payloadOrThrow().provider
                    ProviderDiscoveryAttempt.Succeeded(
                        DiscoveredSubscriptionProvider(
                            descriptor = descriptor,
                            executionKind = extension.executionKind.toProviderExecutionKind(),
                        )
                    )
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    ProviderDiscoveryAttempt.Failed
                }
            }
        }.awaitAll()
        val providers = attempts.mapNotNull { attempt ->
            (attempt as? ProviderDiscoveryAttempt.Succeeded)?.provider
        }
        if (providers.isEmpty()) {
            val failureCount = attempts.count { attempt -> attempt == ProviderDiscoveryAttempt.Failed }
            if (failureCount > 0) throw ProviderDiscoveryException(failureCount)
        }
        providers
    }

    override fun observeAccountSummaries(): Flow<List<ProviderAccountSummary>> = flow {
        invalidateUndecryptableCredentials()
        emitAll(
            providerDao.observeAccountSummaries().map { rows ->
                rows.map { row ->
                    val providerId = ExtensionId(row.account.providerId)
                    val hasBuiltInOwner = runtime.registeredExtensions().any { extension ->
                        extension.manifest.id == providerId &&
                            extension.executionKind == ExtensionExecutionKind.BUILT_IN
                    }
                    ProviderAccountSummary(
                        playlistTitle = row.playlistTitle,
                        playlistUrl = row.account.playlistUrl,
                        providerId = providerId,
                        providerKind = ProviderKind(row.account.providerKind),
                        baseUrl = row.account.baseUrl,
                        username = row.account.username,
                        serverName = row.account.serverName,
                        requiresReauthentication = row.account.requiresReauthentication,
                        requiresExtensionOwnerConfirmation =
                            row.account.requiresReauthentication && !hasBuiltInOwner,
                    )
                }
            }
        )
    }

    override fun stageCredential(secret: String): CredentialHandle = credentialVault.stage(secret)

    override suspend fun subscribe(request: ProviderSubscriptionRequest): ProviderSubscriptionResult {
        if (request.title.isBlank() || request.title.length > MAX_PROVIDER_NAME_LENGTH) {
            throw ProviderOperationException("Subscription name is invalid")
        }
        val registration = runtime.registeredExtensions().singleOrNull { extension ->
            extension.manifest.id == request.providerId
        } ?: throw ProviderOperationException("Subscription provider is unavailable")
        val principalLease = when (registration.executionKind) {
            ExtensionExecutionKind.BUILT_IN -> null
            ExtensionExecutionKind.EXTERNAL -> activePrincipalRegistry.captureLease(request.providerId)
                ?: throw ProviderOperationException("Provider extension is not active")
        }
        val principal = principalLease?.principal
        val requestedReauthenticationAccount = request.reauthenticationPlaylistUrl?.let { url ->
            val account = providerDao.getAccountByPlaylistUrl(url)
                ?: throw ProviderOperationException(
                    "The provider account selected for reauthentication no longer exists"
                )
            if (
                !account.requiresReauthentication ||
                account.providerId != request.providerId.value ||
                account.providerKind != request.providerKind.value
            ) {
                throw ProviderOperationException(
                    "The selected provider account cannot be reauthenticated by this provider"
                )
            }
            account
        }
        val descriptor = discoverProviders().singleOrNull { candidate ->
            candidate.descriptor.providerId == request.providerId &&
                candidate.descriptor.variants.any { variant ->
                    variant.kind == request.providerKind
                }
        }?.descriptor ?: throw ProviderOperationException("Subscription provider is unavailable")
        val settingValues = descriptor.validatedValues(request)
        var activeScope: BrokerScopeHandle? = principal?.let { externalPrincipal ->
            val baseUrl = settingValues[SubscriptionProviderSettingKeys.BaseUrl]
                ?: throw ProviderOperationException("Server URL is required")
            runCatching {
                providerBrokerScopeStore.mintAuthenticationScope(
                    principal = externalPrincipal,
                    approvedBaseUrl = baseUrl,
                    transientCredentials = request.credentialHandles,
                )
            }.getOrElse { error ->
                throw ProviderOperationException(
                    "Provider authentication could not be started",
                    cause = error,
                )
            }
        }
        try {
            val validation = runtime.invoke(
                extensionId = request.providerId,
                spec = SubscriptionHookSpecs.Validate,
                request = SubscriptionProviderValidateRequest(
                    providerKind = request.providerKind,
                    settingValues = settingValues,
                    credentialHandles = request.credentialHandles,
                ),
                brokerScope = activeScope,
                validateResponse = { response ->
                    when (registration.executionKind) {
                        ExtensionExecutionKind.BUILT_IN -> {
                            val evidence =
                                response.evidence as? ProviderValidationEvidence.TrustedDirect
                                    ?: error("Built-in validation evidence is invalid")
                            evidence.account.requireValidFor(descriptor)
                        }

                        ExtensionExecutionKind.EXTERNAL -> require(
                            response.evidence is ProviderValidationEvidence.HostBrokerReceipt
                        )
                    }
                },
            ).payloadOrThrow<SubscriptionProviderValidateResult>()
            val validationMaterial = when (registration.executionKind) {
                ExtensionExecutionKind.BUILT_IN -> {
                    val evidence = validation.evidence as? ProviderValidationEvidence.TrustedDirect
                        ?: throw ProviderOperationException(
                            "Built-in provider returned unsupported validation evidence"
                        )
                    ProviderValidationMaterial(
                        account = evidence.account,
                        credential = evidence.credential,
                    )
                }

                ExtensionExecutionKind.EXTERNAL -> {
                    val evidence = validation.evidence as? ProviderValidationEvidence.HostBrokerReceipt
                        ?: throw ProviderOperationException(
                            "External provider returned unsupported validation evidence"
                        )
                    val scope = checkNotNull(activeScope)
                    val captured = try {
                        providerBrokerScopeStore.consumeAuthenticationReceipt(
                            scope = scope,
                            principal = checkNotNull(principal),
                            receipt = evidence.receipt,
                        )
                    } catch (error: Exception) {
                        throw ProviderOperationException(
                            "Provider authentication receipt is invalid",
                            cause = error,
                        )
                    }
                    ProviderValidationMaterial(
                        account = captured.toValidatedAccount(
                            request = request,
                            descriptor = descriptor,
                            settingValues = settingValues,
                        ),
                        credential = captured.credentialHandle,
                    )
                }
            }
            val validated = validationMaterial.account
            val validatedCredential = validationMaterial.credential
            validated.requireValidFor(descriptor)
            if (principal != null) {
                val approvedBaseUrl = settingValues.getValue(SubscriptionProviderSettingKeys.BaseUrl)
                if (
                    runCatching { approvedBaseUrl.toCanonicalHttpOrigin() }.getOrNull() !=
                    runCatching { validated.normalizedBaseUrl.toCanonicalHttpOrigin() }.getOrNull()
                ) {
                    throw ProviderOperationException(
                        "Provider returned an account on an unapproved server"
                    )
                }
            }
            suspend fun completeSubscription(
                existing: ProviderAccount?,
            ): ProviderSubscriptionResult {
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
                val accessToken: String
                val refresh: SubscriptionContentRefreshResult
                if (principal == null) {
                    accessToken = credentialVault.consume(validatedCredential)
                        ?: throw ProviderOperationException("Provider credential capture expired")
                    refresh = refresh(
                        account = accountReference,
                        credentialHandle = credentialVault.stage(accessToken),
                        reason = SubscriptionRefreshReason.Initial,
                    )
                } else {
                    val authenticationScope = checkNotNull(activeScope)
                    activeScope = providerBrokerScopeStore.advanceToInitialRefresh(
                        authenticationScope = authenticationScope,
                        principal = principal,
                        capturedHandle = validatedCredential,
                    )
                    refresh = refresh(
                        account = accountReference,
                        credentialHandle = validatedCredential,
                        reason = SubscriptionRefreshReason.Initial,
                        brokerScope = activeScope,
                    )
                    accessToken = providerBrokerScopeStore.completeInitialRefresh(
                        refreshScope = checkNotNull(activeScope),
                        principal = principal,
                        capturedHandle = validatedCredential,
                    )
                    activeScope = null
                }
                val account = accountReference.toEntity(playlistUrl, principal)
                existing?.let { current ->
                    prepareAccountForReplacement(
                        account = current,
                        principalLease = principalLease,
                    )
                }
                val count = extensionContributionRunCoordinator.withPlaylist(playlistUrl) {
                    commitProviderPersistence(principalLease) {
                        importProviderSubscription(
                            title = request.title,
                            account = account,
                            accessToken = accessToken,
                            refresh = refresh,
                        )
                    }
                }
                scheduleExtensionContributions(account.playlistUrl)
                return ProviderSubscriptionResult(
                    playlistUrl = playlistUrl,
                    channelCount = count,
                )
            }
            return lifecycleCoordinator.withRemoteIdentity(
                providerId = request.providerId.value,
                serverId = validated.serverId,
                userId = validated.userId,
            ) {
                val existing = providerDao.getAccountByRemoteIdentity(
                    providerId = request.providerId.value,
                    serverId = validated.serverId,
                    userId = validated.userId,
                )
                val explicitRestoredAccountClaim = existing?.let { account ->
                    requestedReauthenticationAccount?.let { requested ->
                        account == requested && account.requiresReauthentication
                    }
                } == true
                if (
                    existing != null &&
                    !existing.isOwnedBy(principal) &&
                    !explicitRestoredAccountClaim
                ) {
                    throw ProviderOperationException(
                        "This provider account belongs to a different extension identity"
                    )
                }
                if (existing == null && requestedReauthenticationAccount != null) {
                    throw ProviderOperationException(
                        "The authenticated provider account does not match the selected subscription"
                    )
                }
                if (existing == null) {
                    completeSubscription(existing = null)
                } else {
                    lifecycleCoordinator.withAccount(existing.id) {
                        val current = providerDao.getAccountByRemoteIdentity(
                            providerId = request.providerId.value,
                            serverId = validated.serverId,
                            userId = validated.userId,
                        )
                        when {
                            current == null -> completeSubscription(existing = null)
                            current.id != existing.id ||
                                current.playlistUrl != existing.playlistUrl ||
                                (
                                    !current.isOwnedBy(principal) &&
                                        !(
                                            explicitRestoredAccountClaim &&
                                                current.requiresReauthentication &&
                                                current == requestedReauthenticationAccount
                                            )
                                    ) -> {
                                throw ProviderOperationException(
                                    message =
                                        "Provider account changed during reauthentication",
                                    code = "provider.account_changed",
                                    recoverable = true,
                                )
                            }

                            else -> completeSubscription(existing = current)
                        }
                    }
                }
            }
        } finally {
            activeScope?.let(providerBrokerScopeStore::close)
        }
    }

    override suspend fun refresh(
        playlistUrl: String,
        reason: SubscriptionRefreshReason,
    ): ProviderSubscriptionResult {
        val initialAccount = providerDao.getAccountByPlaylistUrl(playlistUrl)
            ?: throw ProviderOperationException("Provider account was not found")
        return lifecycleCoordinator.withAccount(initialAccount.id) {
            val account = providerDao.getAccount(initialAccount.id)
                ?.takeIf { current -> current.playlistUrl == playlistUrl }
                ?: throw ProviderOperationException("Provider account was not found")
            refreshLocked(
                account = account,
                reason = reason,
            )
        }
    }

    private suspend fun refreshLocked(
        account: ProviderAccount,
        reason: SubscriptionRefreshReason,
    ): ProviderSubscriptionResult {
        val playlistUrl = account.playlistUrl
        val principalLease = requireActiveOwner(account)
        val principal = principalLease?.principal
        val credential = requireAuthenticatedCredential(account, principalLease)
        if (playlistDao.get(playlistUrl) == null) {
            throw ProviderOperationException("Provider playlist was not found")
        }
        val brokerScope = principal?.let { externalPrincipal ->
            mintAccountBrokerScope(
                principal = externalPrincipal,
                hook = SubscriptionHookSpecs.Refresh.hook,
                account = account,
                credential = credential,
                principalLease = principalLease,
            )
        }
        val refresh = try {
            withAuthenticationFailureInvalidation(account, principalLease) {
                refresh(
                    account = account.toReference(),
                    credentialHandle = CredentialHandle(credential.credentialHandle),
                    reason = reason,
                    brokerScope = brokerScope,
                )
            }
        } finally {
            brokerScope?.let(providerBrokerScopeStore::close)
        }
        val count = extensionContributionRunCoordinator.withPlaylist(playlistUrl) {
            commitProviderPersistence(principalLease) {
                refreshProviderSnapshot(
                    account = account,
                    refresh = refresh,
                )
            }
        }
        scheduleExtensionContributions(account.playlistUrl)
        return ProviderSubscriptionResult(playlistUrl = playlistUrl, channelCount = count)
    }

    private suspend fun scheduleExtensionContributions(playlistUrl: String) {
        try {
            extensionContributionScheduler.enqueue(playlistUrl)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            timber.w(
                "Extension contribution scheduling failed (%s)",
                error.javaClass.simpleName,
            )
        }
    }

    override suspend fun resolvePlayback(channelId: Int): ProviderPlaybackSource? {
        val initialReference = providerDao.getPlaybackReference(channelId) ?: return null
        return lifecycleCoordinator.withAccount(initialReference.accountId) {
            val reference = providerDao.getPlaybackReference(channelId) ?: return@withAccount null
            if (reference.accountId != initialReference.accountId) {
                throw ProviderOperationException(
                    message = "Provider playback reference changed before playback could start",
                    code = "provider.account_changed",
                    recoverable = true,
                )
            }
            val account = providerDao.getAccount(reference.accountId)
                ?: throw ProviderOperationException("Provider account was not found")
            if (reference.providerId != account.providerId) {
                throw ProviderOperationException("Provider playback reference is inconsistent")
            }
            resolvePlaybackLocked(reference, account)
        }
    }

    private suspend fun resolvePlaybackLocked(
        reference: ChannelPlaybackReference,
        account: ProviderAccount,
    ): ProviderPlaybackSource {
        val principalLease = requireActiveOwner(account)
        val principal = principalLease?.principal
        val credential = requireAuthenticatedCredential(account, principalLease)
        val reservation = reservePlaybackSessionCapacity(account.id)
        try {
            val brokerScope = principal?.let { externalPrincipal ->
                mintAccountBrokerScope(
                    principal = externalPrincipal,
                    hook = SubscriptionHookSpecs.ResolvePlayback.hook,
                    account = account,
                    credential = credential,
                    principalLease = principalLease,
                )
            }
            val payload = try {
                withAuthenticationFailureInvalidation(account, principalLease) {
                    runtime.invoke(
                        extensionId = ExtensionId(reference.providerId),
                        spec = SubscriptionHookSpecs.ResolvePlayback,
                        request = PlaybackSourceResolveRequest(
                            account = account.toReference(),
                            credential = ProviderCredential(
                                CredentialHandle(credential.credentialHandle)
                            ),
                            reference = reference.toContract(),
                        ),
                        brokerScope = brokerScope,
                    ).payloadOrThrow<PlaybackSourceResolveResult>()
                }
            } finally {
                brokerScope?.let(providerBrokerScopeStore::close)
            }
            val session = payload.session?.let { providerSession ->
                ProviderPlaybackSession(
                    id = UUID.randomUUID().toString(),
                    accountId = account.id,
                    providerId = reference.providerId,
                    itemId = reference.itemId,
                    mediaSourceId = payload.mediaSourceId ?: reference.mediaSourceId,
                    sourceType = reference.sourceType,
                    playSessionId = providerSession.playSessionId,
                    liveStreamId = providerSession.liveStreamId,
                )
            }
            val playbackContext = PlaybackCloseContext(
                account = account,
                credential = credential,
            )
            return try {
                if (session == null) {
                    releasePlaybackSessionReservation(reservation)
                } else {
                    persistPlaybackCleanupTombstone(
                        session = session,
                        reservation = reservation,
                    )
                }
                currentCoroutineContext().ensureActive()
                val resolvedHeaders = try {
                    validatePlaybackUrl(
                        value = payload.url,
                        headers = payload.headers,
                        account = account,
                        principal = principal,
                    )
                    resolvePlaybackHeaders(
                        values = payload.headers,
                        account = account,
                        credential = credential,
                        principal = principal,
                        principalLease = principalLease,
                    )
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    if (error is ProviderOperationException) throw error
                    throw ProviderOperationException(
                        message = "Provider returned an invalid playback result",
                        code = "provider.invalid_playback_result",
                        cause = error,
                    )
                }
                currentCoroutineContext().ensureActive()
                session?.let { activeSession ->
                    activePlaybackContexts[activeSession] = playbackContext
                }
                currentCoroutineContext().ensureActive()
                ProviderPlaybackSource(
                    url = payload.url,
                    headers = resolvedHeaders,
                    session = session,
                    allowCrossOriginRequests = principal == null,
                )
            } catch (cancelled: CancellationException) {
                retainCancelledPlaybackSessionForRecovery(session)
                throw cancelled
            } catch (error: Exception) {
                compensateUnacceptedPlaybackSession(
                    session = session,
                    context = playbackContext,
                )
                throw error
            }
        } finally {
            releasePlaybackSessionReservation(reservation)
        }
    }

    override suspend fun closePlayback(
        session: ProviderPlaybackSession,
        reason: ProviderPlaybackCloseReason,
    ): Boolean {
        return try {
            lifecycleCoordinator.withAccount(session.accountId) {
                closePlaybackLocked(session, reason)
            }.also { closed ->
                if (!closed && reason != ProviderPlaybackCloseReason.RECOVERY) {
                    enqueuePlaybackSessionCleanupIfPersisted(session.id)
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            if (reason != ProviderPlaybackCloseReason.RECOVERY) {
                enqueuePlaybackSessionCleanupIfPersisted(session.id)
            }
            throw error
        }
    }

    private suspend fun enqueuePlaybackSessionCleanupIfPersisted(sessionId: String) {
        if (providerDao.getPlaybackSession(sessionId) != null) {
            enqueuePlaybackSessionCleanup()
        }
    }

    private fun enqueuePlaybackSessionCleanup() {
        runCatching {
            ProviderSessionCleanupWorker.enqueueRetry(WorkManager.getInstance(context))
        }
    }

    private suspend fun closePlaybackLocked(
        session: ProviderPlaybackSession,
        reason: ProviderPlaybackCloseReason,
    ): Boolean {
        val context = activePlaybackContexts[session] ?: run {
            val persisted = providerDao.getPlaybackSession(session.id) ?: return true
            if (persisted.toModel() != session) {
                throw ProviderOperationException(
                    "Playback session does not match the persisted provider session"
                )
            }
            val account = providerDao.getAccount(session.accountId) ?: return false
            val principalLease = requireActiveOwner(account)
            val credential = runCatching {
                requireAuthenticatedCredential(account, principalLease)
            }.getOrElse { error ->
                if (error is CancellationException) throw error
                return false
            }
            PlaybackCloseContext(
                account = account,
                credential = credential,
            )
        }
        if (context.account.id != session.accountId || context.account.providerId != session.providerId) {
            throw ProviderOperationException("Playback session does not belong to this provider account")
        }
        val closed = closePlaybackWithContext(session, reason, context)
        if (closed) {
            activePlaybackContexts.remove(session, context)
        }
        return closed
    }

    private suspend fun closePlaybackWithContext(
        session: ProviderPlaybackSession,
        reason: ProviderPlaybackCloseReason,
        context: PlaybackCloseContext,
        deletePersistedSession: Boolean = true,
    ): Boolean {
        val currentAccount = providerDao.getAccount(context.account.id) ?: return false
        if (currentAccount != context.account) {
            throw ProviderOperationException(
                message = "Provider account changed before the playback session could be closed",
                code = "provider.account_changed",
                recoverable = true,
            )
        }
        val principalLease = requireActiveOwner(currentAccount)
        val principal = principalLease?.principal
        val credential = runCatching {
            requireAuthenticatedCredential(currentAccount, principalLease)
        }.getOrElse { error ->
            if (error is CancellationException) throw error
            return false
        }
        if (credential != context.credential) {
            throw ProviderOperationException(
                message = "Provider credential changed before the playback session could be closed",
                code = "provider.account_changed",
                recoverable = true,
            )
        }
        val brokerScope = principal?.let { externalPrincipal ->
            mintAccountBrokerScope(
                principal = externalPrincipal,
                hook = SubscriptionHookSpecs.ClosePlayback.hook,
                account = currentAccount,
                credential = credential,
                principalLease = principalLease,
            )
        }
        val payload = try {
            withAuthenticationFailureInvalidation(currentAccount, principalLease) {
                runtime.invoke(
                    extensionId = ExtensionId(session.providerId),
                    spec = SubscriptionHookSpecs.ClosePlayback,
                    request = PlaybackSessionCloseRequest(
                        account = currentAccount.toReference(),
                        credential = ProviderCredential(
                            CredentialHandle(credential.credentialHandle)
                        ),
                        reference = PlaybackReference(
                            providerId = ExtensionId(session.providerId),
                            itemId = session.itemId,
                            mediaSourceId = session.mediaSourceId,
                            sourceType = session.sourceType,
                        ),
                        session = PlaybackSessionDescriptor(
                            playSessionId = session.playSessionId,
                            liveStreamId = session.liveStreamId,
                        ),
                        reason = reason.toContract(),
                    ),
                    brokerScope = brokerScope,
                ).payloadOrThrow<PlaybackSessionCloseResult>()
            }
        } finally {
            brokerScope?.let(providerBrokerScopeStore::close)
        }
        if (payload.closed && deletePersistedSession) {
            deletePlaybackCleanupTombstone(session.id)
            activePlaybackContexts.remove(session, context)
            currentCoroutineContext().ensureActive()
        }
        return payload.closed
    }

    private suspend fun prepareAccountForReplacement(
        account: ProviderAccount,
        principalLease: ExtensionPrincipalLease?,
    ) {
        val persistedSessions = providerDao.getPlaybackSessions()
            .asSequence()
            .filter { session -> session.accountId == account.id }
            .map { session -> session.toModel() }
            .toList()
        val activeSessions = activePlaybackContexts.keys
            .filter { session -> session.accountId == account.id }
        val sessions = (persistedSessions + activeSessions).distinct()
        if (sessions.isEmpty()) return

        val credential = providerDao.getCredential(account.id)
        val credentialIsUsable = !account.requiresReauthentication &&
            credential != null &&
            credentialVault.decrypt(credential) != null
        if (!credentialIsUsable) {
            invalidateCredential(account.id, principalLease)
            return
        }
        checkNotNull(credential)

        for (session in sessions) {
            val context = activePlaybackContexts[session] ?: PlaybackCloseContext(
                account = account,
                credential = credential,
            )
            val closed = try {
                closePlaybackWithContext(
                    session = session,
                    reason = ProviderPlaybackCloseReason.STOPPED,
                    context = context,
                )
            } catch (error: ProviderOperationException) {
                if (
                    error.code == SubscriptionProviderErrorCodes.AuthenticationFailed.value &&
                    providerDao.getCredential(account.id) == null
                ) {
                    return
                }
                throw error
            }
            if (!closed) {
                throw ProviderOperationException(
                    message =
                        "Existing playback sessions must close before this account can be updated",
                    code = "provider.session_close_pending",
                    recoverable = true,
                )
            }
            activePlaybackContexts.remove(session, context)
        }
    }

    override suspend fun removeAccount(playlistUrl: String) = lifecycleCoordinator.withOperation {
        val initialAccount = providerDao.getAccountByPlaylistUrl(playlistUrl)
        if (initialAccount == null) {
            extensionContributionRunCoordinator.withPlaylist(playlistUrl) {
                providerDao.deleteProviderSubscription(playlistUrl)
            }
            return@withOperation
        }
        lifecycleCoordinator.withAccount(initialAccount.id) {
            extensionContributionRunCoordinator.withPlaylist(playlistUrl) {
                removeAccountLocked(
                    accountId = initialAccount.id,
                    playlistUrl = playlistUrl,
                )
            }
        }
    }

    private suspend fun removeAccountLocked(
        accountId: String,
        playlistUrl: String,
    ) {
        val account = providerDao.getAccount(accountId)
            ?.takeIf { current -> current.playlistUrl == playlistUrl }
            ?: return
        val sessions = providerDao.getPlaybackSessions()
            .asSequence()
            .filter { session -> session.accountId == account.id }
            .map { session -> session.toModel() }
            .toList()
        val persistedCredential = providerDao.getCredential(account.id)
        sessions.forEach { session ->
            val context = activePlaybackContexts[session] ?: persistedCredential?.let { credential ->
                PlaybackCloseContext(
                    account = account,
                    credential = credential,
                )
            }
            if (context == null) return@forEach
            try {
                closePlaybackWithContext(
                    session = session,
                    reason = ProviderPlaybackCloseReason.STOPPED,
                    context = context,
                    deletePersistedSession = false,
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Unsubscribing is a local operation. Remote session cleanup is best-effort
                // and must not leave an offline or unavailable provider account undeletable.
            }
        }
        providerDao.deleteProviderSubscription(playlistUrl)
        sessions.forEach { session ->
            activePlaybackContexts.remove(session)
        }
    }

    private suspend fun retainCancelledPlaybackSessionForRecovery(
        session: ProviderPlaybackSession?,
    ) {
        if (session == null) return
        withContext(NonCancellable) {
            activePlaybackContexts.remove(session)
            enqueuePlaybackSessionCleanup()
        }
    }

    private suspend fun compensateUnacceptedPlaybackSession(
        session: ProviderPlaybackSession?,
        context: PlaybackCloseContext,
    ) {
        if (session == null) return
        withContext(NonCancellable) {
            activePlaybackContexts.remove(session)
            val closed = try {
                closePlaybackWithContext(
                    session = session,
                    reason = ProviderPlaybackCloseReason.PLAYBACK_FAILED,
                    context = context,
                )
            } catch (_: Exception) {
                false
            }
            if (!closed) {
                try {
                    persistPlaybackCleanupTombstone(session)
                } catch (_: Exception) {
                    // The original failure remains authoritative. A missing account cannot
                    // retain a cleanup record, but must not mask cancellation or validation.
                }
            }
        }
    }

    /**
     * A returned playback session is already a remote side effect, not plugin-owned host data.
     * Its cleanup tombstone must survive extension disablement and a stale persistence lease.
     */
    private suspend fun persistPlaybackCleanupTombstone(
        session: ProviderPlaybackSession,
        reservation: PlaybackSessionReservation? = null,
    ) {
        withContext(NonCancellable) {
            playbackSessionAdmissionMutex.withLock {
                if (reservation != null) {
                    check(reservation in playbackSessionReservations) {
                        "Playback session capacity reservation is no longer active"
                    }
                }
                val persisted = providerDao.getPlaybackSession(session.id)
                if (persisted == null) {
                    providerDao.insertOrReplace(session.toEntity())
                } else if (persisted.toModel() != session) {
                    throw ProviderOperationException(
                        "Playback session does not match its persisted cleanup record"
                    )
                }
                reservation?.let { playbackSessionReservations.remove(it) }
            }
        }
    }

    private suspend fun deletePlaybackCleanupTombstone(sessionId: String) {
        withContext(NonCancellable) {
            providerDao.deletePlaybackSession(sessionId)
        }
    }

    override suspend fun invalidateUndecryptableCredentials(): Int = withContext(Dispatchers.IO) {
        var invalidatedCount = 0
        providerDao.getAccounts().forEach { initialAccount ->
            lifecycleCoordinator.withAccount(initialAccount.id) {
                val account = providerDao.getAccount(initialAccount.id) ?: return@withAccount
                val credential = providerDao.getCredential(account.id)
                val isDecryptable = credential?.let { encrypted ->
                    credentialVault.decrypt(encrypted) != null
                } == true
                if (!isDecryptable || account.requiresReauthentication) {
                    invalidateCredential(account.id, principalLease = null)
                    if (!account.requiresReauthentication) {
                        invalidatedCount++
                    }
                }
            }
        }
        invalidatedCount
    }

    override suspend fun closeOrphanedPlaybackSessions(
        afterCreatedAtEpochMillis: Long?,
        afterSessionId: String?,
    ): ProviderSessionCleanupResult {
        require((afterCreatedAtEpochMillis == null) == (afterSessionId == null))
        require(afterCreatedAtEpochMillis == null || afterCreatedAtEpochMillis >= 0L)
        require(afterSessionId == null || afterSessionId.isNotBlank())
        var closed = 0
        var pending = 0
        var recoverablePending = 0
        val candidates = providerDao.getValidPlaybackSessionPage(
            afterCreatedAtEpochMillis = afterCreatedAtEpochMillis,
            afterSessionId = afterSessionId,
            limit = MAX_SESSION_CLEANUP_BATCH,
        )
        candidates.forEach { entity ->
            try {
                when (closeOrphanedPlaybackSession(entity)) {
                    OrphanedSessionCloseOutcome.CLOSED -> closed++
                    OrphanedSessionCloseOutcome.PENDING -> {
                        pending++
                        recoverablePending++
                    }

                    OrphanedSessionCloseOutcome.SKIPPED -> Unit
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: ProviderOperationException) {
                val authenticationDiscarded =
                    error.code == SubscriptionProviderErrorCodes.AuthenticationFailed.value &&
                        providerDao.getPlaybackSession(entity.id) == null
                if (!authenticationDiscarded) {
                    pending++
                    if (error.recoverable) recoverablePending++
                }
            }
        }
        return ProviderSessionCleanupResult(
            closedCount = closed,
            pendingCount = pending,
            recoverablePendingCount = recoverablePending,
            continuationCreatedAtEpochMillis = candidates
                .takeIf { batch -> batch.size == MAX_SESSION_CLEANUP_BATCH }
                ?.last()
                ?.createdAtEpochMillis,
            continuationSessionId = candidates
                .takeIf { batch -> batch.size == MAX_SESSION_CLEANUP_BATCH }
                ?.last()
                ?.id,
        )
    }

    private suspend fun closeOrphanedPlaybackSession(
        candidate: ProviderPlaybackSessionEntity,
    ): OrphanedSessionCloseOutcome = lifecycleCoordinator.withAccount(candidate.accountId) {
        val persisted = providerDao.getPlaybackSession(candidate.id)
            ?: return@withAccount OrphanedSessionCloseOutcome.SKIPPED
        if (persisted != candidate) {
            return@withAccount OrphanedSessionCloseOutcome.SKIPPED
        }
        val session = runCatching { persisted.toModel() }.getOrElse {
            providerDao.deletePlaybackSession(persisted.id)
            return@withAccount OrphanedSessionCloseOutcome.SKIPPED
        }
        if (activePlaybackContexts.containsKey(session)) {
            return@withAccount OrphanedSessionCloseOutcome.SKIPPED
        }
        if (closePlaybackLocked(session, ProviderPlaybackCloseReason.RECOVERY)) {
            OrphanedSessionCloseOutcome.CLOSED
        } else {
            OrphanedSessionCloseOutcome.PENDING
        }
    }

    private suspend fun reservePlaybackSessionCapacity(
        accountId: String,
    ): PlaybackSessionReservation = playbackSessionAdmissionMutex.withLock {
        val reservedForAccount = playbackSessionReservations.count { reservation ->
            reservation.accountId == accountId
        }
        if (
            providerDao.countPlaybackSessions(accountId) + reservedForAccount >=
            MAX_SESSIONS_PER_ACCOUNT ||
            providerDao.countPlaybackSessions() + playbackSessionReservations.size >=
            MAX_SESSIONS_GLOBAL
        ) {
            throw ProviderOperationException(
                message = "Pending provider playback sessions must be closed before playing again",
                code = "provider.session_capacity_reached",
                recoverable = true,
            )
        }
        PlaybackSessionReservation(
            id = UUID.randomUUID().toString(),
            accountId = accountId,
        ).also(playbackSessionReservations::add)
    }

    private suspend fun releasePlaybackSessionReservation(
        reservation: PlaybackSessionReservation,
    ) {
        withContext(NonCancellable) {
            playbackSessionAdmissionMutex.withLock {
                playbackSessionReservations.remove(reservation)
            }
        }
    }

    private suspend fun refresh(
        account: ProviderAccountReference,
        credentialHandle: CredentialHandle,
        reason: SubscriptionRefreshReason,
        brokerScope: BrokerScopeHandle? = null,
    ): SubscriptionContentRefreshResult = runtime.invoke(
        extensionId = account.providerId,
        spec = SubscriptionHookSpecs.Refresh,
        request = SubscriptionContentRefreshRequest(
            account = account,
            credential = ProviderCredential(credentialHandle),
            reason = reason,
        ),
        brokerScope = brokerScope,
        validateResponse = { response ->
            importer.validateProviderSnapshot(
                account = account,
                allowRemoteArtwork = brokerScope == null,
                refresh = response,
            )
        },
    ).payloadOrThrow()

    private suspend fun mintAccountBrokerScope(
        principal: ExtensionPrincipal,
        hook: Hook,
        account: ProviderAccount,
        credential: ProviderCredentialEntity,
        principalLease: ExtensionPrincipalLease?,
    ): BrokerScopeHandle {
        if (credentialVault.decrypt(credential) == null) {
            invalidateCredential(account.id, principalLease)
            throw ProviderOperationException("Provider credentials must be entered again")
        }
        return runCatching {
            providerBrokerScopeStore.mintAccountScope(
                principal = principal,
                allowedHook = hook,
                account = account,
                credential = credential,
            )
        }.getOrElse { error ->
            throw ProviderOperationException(
                message = "Provider broker scope could not be created",
                cause = error,
            )
        }
    }

    private suspend fun requireAuthenticatedCredential(
        account: ProviderAccount,
        principalLease: ExtensionPrincipalLease?,
    ): ProviderCredentialEntity {
        if (account.requiresReauthentication) {
            invalidateCredential(account.id, principalLease)
            throw ProviderOperationException("Provider credentials must be entered again")
        }
        val credential = providerDao.getCredential(account.id)
            ?: throw ProviderOperationException("Provider credentials must be entered again")
        if (credentialVault.decrypt(credential) == null) {
            invalidateCredential(account.id, principalLease)
            throw ProviderOperationException("Provider credentials must be entered again")
        }
        return credential
    }

    private suspend fun importProviderSubscription(
        title: String,
        account: ProviderAccount,
        accessToken: String,
        refresh: SubscriptionContentRefreshResult,
    ): Int = try {
        importer.importSubscription(
            title = title,
            source = DataSource.Provider,
            account = account,
            accessToken = accessToken,
            refresh = refresh,
        )
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: IllegalArgumentException) {
        throw ProviderOperationException(
            message = "Provider returned an invalid content snapshot",
            code = "provider.invalid_snapshot",
            cause = error,
        )
    }

    private suspend fun refreshProviderSnapshot(
        account: ProviderAccount,
        refresh: SubscriptionContentRefreshResult,
    ): Int = try {
        importer.refresh(
            account = account,
            refresh = refresh,
        )
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: IllegalArgumentException) {
        throw ProviderOperationException(
            message = "Provider returned an invalid content snapshot",
            code = "provider.invalid_snapshot",
            cause = error,
        )
    }

    private suspend fun <T> commitProviderPersistence(
        principalLease: ExtensionPrincipalLease?,
        block: suspend () -> T,
    ): T {
        if (principalLease == null) return block()
        return try {
            activePrincipalRegistry.commit(principalLease, block)
        } catch (error: InactiveExtensionPrincipalLeaseException) {
            throw ProviderOperationException(
                message = "Provider extension became inactive before its result was saved",
                code = "provider.inactive",
                cause = error,
            )
        }
    }

    private suspend fun invalidateCredential(
        accountId: String,
        principalLease: ExtensionPrincipalLease?,
    ) {
        commitProviderPersistence(principalLease) {
            providerDao.invalidateCredential(accountId)
        }
        discardActivePlaybackContexts(accountId)
    }

    private suspend fun <T> withAuthenticationFailureInvalidation(
        account: ProviderAccount,
        principalLease: ExtensionPrincipalLease?,
        block: suspend () -> T,
    ): T = try {
        block()
    } catch (error: ProviderOperationException) {
        if (
            error.code == SubscriptionProviderErrorCodes.AuthenticationFailed.value &&
            providerDao.getAccount(account.id) == account
        ) {
            invalidateCredential(account.id, principalLease)
        }
        throw error
    }

    private fun discardActivePlaybackContexts(accountId: String) {
        activePlaybackContexts.keys
            .filter { session -> session.accountId == accountId }
            .forEach { session -> activePlaybackContexts.remove(session) }
    }

    private fun <T : ExtensionPayload> ExtensionResult<T>.payloadOrThrow(): T {
        return when (val current = outcome) {
            is HookResult.Success -> current.payload
            is HookResult.Failure -> throw ProviderOperationException(
                message = current.error.message,
                code = current.error.code.value,
                recoverable = current.error.recoverable,
                details = current.error.details,
            )
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

    private fun ProviderAccountReference.toEntity(
        playlistUrl: String,
        principal: ExtensionPrincipal?,
    ): ProviderAccount = ProviderAccount(
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
        ownerPackageName = principal?.packageName,
        ownerServiceName = principal?.serviceName,
        ownerCertificateSha256 = principal?.certificateSha256,
    )

    private fun requireActiveOwner(account: ProviderAccount): ExtensionPrincipalLease? {
        val ownerValues = listOf(
            account.ownerPackageName,
            account.ownerServiceName,
            account.ownerCertificateSha256,
        )
        if (ownerValues.all { value -> value == null }) {
            val externalRegistration = runtime.registeredExtensions().any { extension ->
                extension.manifest.id.value == account.providerId &&
                    extension.executionKind == ExtensionExecutionKind.EXTERNAL
            }
            if (externalRegistration) {
                throw ProviderOperationException(
                    "Provider account must be reauthenticated for this extension"
                )
            }
            return null
        }
        if (ownerValues.any { value -> value == null }) {
            throw ProviderOperationException("Provider account identity is incomplete")
        }
        val principalLease = activePrincipalRegistry.captureLease(ExtensionId(account.providerId))
            ?: throw ProviderOperationException(
                message = "Provider extension is not active",
                code = "provider.inactive",
                recoverable = true,
            )
        val principal = principalLease.principal
        if (!principal.owns(account)) {
            throw ProviderOperationException("Provider extension identity no longer matches this account")
        }
        return principalLease
    }

    private fun ProviderAccount.isOwnedBy(principal: ExtensionPrincipal?): Boolean =
        if (principal == null) {
            ownerPackageName == null &&
                ownerServiceName == null &&
                ownerCertificateSha256 == null
        } else {
            principal.owns(this)
        }

    private fun validatePlaybackUrl(
        value: String,
        headers: Map<String, PlaybackHeaderValue>,
        account: ProviderAccount,
        principal: ExtensionPrincipal?,
    ) {
        if (value.length > MAX_PLAYBACK_URL_LENGTH) {
            throw ProviderOperationException("Provider returned a playback URL that is too long")
        }
        val url = runCatching { value.toHttpUrl() }.getOrElse {
            throw ProviderOperationException("Provider returned an invalid playback URL")
        }
        val approved = runCatching { account.baseUrl.toHttpUrl() }.getOrElse {
            throw ProviderOperationException("Provider account has an invalid server URL")
        }
        if (url.origin == approved.origin) return
        if (principal != null) {
            throw ProviderOperationException("Provider returned an unapproved playback origin")
        }
        val exposesProtectedMaterial = headers.any { (name, value) ->
            name.isSensitivePlaybackHeader() ||
                value.parts.any { part ->
                    part.referencesCredential() || part.referencesOpaqueContext()
                }
        }
        if (exposesProtectedMaterial) {
            throw ProviderOperationException(
                "Provider returned authentication material for a cross-origin playback URL"
            )
        }
    }

    private suspend fun resolvePlaybackHeaders(
        values: Map<String, PlaybackHeaderValue>,
        account: ProviderAccount,
        credential: ProviderCredentialEntity,
        principal: ExtensionPrincipal?,
        principalLease: ExtensionPrincipalLease?,
    ): Map<String, String> {
        if (values.size > MAX_PLAYBACK_HEADERS) {
            throw ProviderOperationException("Provider returned too many playback headers")
        }
        val protectedMaterialIsReferenced = values.values.any { value ->
            value.parts.any { part ->
                part.referencesCredential() || part.referencesOpaqueContext()
            }
        }
        val protectedMaterial = if (protectedMaterialIsReferenced) {
            val decrypted = credentialVault.decrypt(credential) ?: run {
                invalidateCredential(account.id, principalLease)
                throw ProviderOperationException("Provider credentials must be entered again")
            }
            if (principal == null) {
                ProviderCredentialMaterial(primaryCredential = decrypted)
            } else {
                runCatching { ProviderCredentialMaterial.decode(decrypted) }.getOrElse {
                    invalidateCredential(account.id, principalLease)
                    throw ProviderOperationException("Provider credentials must be entered again")
                }
            }
        } else {
            null
        }
        val resolutionBudget = BrokerValueResolutionBudget()
        return values.mapValues { (name, value) ->
            if (!name.matches(HTTP_HEADER_NAME)) {
                throw ProviderOperationException("Provider returned an invalid playback header")
            }
            if (name.equals("Host", true) || name.equals("Content-Length", true)) {
                throw ProviderOperationException("Provider cannot set a transport-owned header")
            }
            if (
                principal != null &&
                name.isSensitivePlaybackHeader() &&
                value.parts.none { part -> part.referencesCredential() }
            ) {
                throw ProviderOperationException(
                    "External provider authentication headers require a credential reference"
                )
            }
            buildString {
                value.parts.forEach { part ->
                    append(
                        part.renderForHost(
                            maximumOutputLength = MAX_PLAYBACK_HEADER_VALUE_LENGTH,
                            budget = resolutionBudget,
                            resolveSecret = { reference ->
                                if (reference.handle.value != credential.credentialHandle) {
                                    throw ProviderOperationException(
                                        "Playback credential does not belong to this provider account"
                                    )
                                }
                                checkNotNull(protectedMaterial).primaryCredential
                            },
                            resolveContext = { reference ->
                                checkNotNull(protectedMaterial)
                                    .opaqueContexts[reference.key]
                                    ?: throw ProviderOperationException(
                                        "Playback context does not belong to this provider account"
                                    )
                            },
                        )
                    )
                    if (length > MAX_PLAYBACK_HEADER_VALUE_LENGTH) {
                        throw ProviderOperationException(
                            "Provider returned an invalid playback header value"
                        )
                    }
                }
                if (contains('\r') || contains('\n')) {
                    throw ProviderOperationException(
                        "Provider returned an invalid playback header value"
                    )
                }
            }
        }
    }

    private val HttpUrl.origin: String
        get() = "$scheme://$host:$port"

    private fun SubscriptionProviderDescriptor.validatedValues(
        request: ProviderSubscriptionRequest,
    ): Map<String, String> {
        val schema = settingsSchema
            ?: throw ProviderOperationException("Subscription provider has no settings form")
        val knownKeys = schema.fields.mapTo(mutableSetOf()) { field -> field.key }
        if ((request.settingValues.keys + request.credentialHandles.keys).any { it !in knownKeys }) {
            throw ProviderOperationException("Subscription contains an unknown setting")
        }
        val values = request.settingValues.toMutableMap()
        schema.fields.forEach { field ->
            if (field.type == ExtensionSettingType.SECRET) {
                if (field.required && request.credentialHandles[field.key] == null) {
                    throw ProviderOperationException("${field.label} is required")
                }
                if (field.key in values) {
                    throw ProviderOperationException("${field.label} must use a credential handle")
                }
                return@forEach
            }
            if (field.key in request.credentialHandles) {
                throw ProviderOperationException("${field.label} does not accept a credential handle")
            }
            val value = values[field.key]
                ?: (field.defaultValue as? JsonPrimitive)?.content?.also { default ->
                    values[field.key] = default
                }
            if (field.required && value.isNullOrBlank()) {
                throw ProviderOperationException("${field.label} is required")
            }
            if (value == null) return@forEach
            val valid = when (field.type) {
                ExtensionSettingType.TEXT -> value.length <= MAX_SETTING_VALUE_LENGTH
                ExtensionSettingType.SECRET -> false
                ExtensionSettingType.BOOLEAN -> JsonPrimitive(value).booleanOrNull != null
                ExtensionSettingType.NUMBER ->
                    JsonPrimitive(value).doubleOrNull?.isFinite() == true
                ExtensionSettingType.SINGLE_CHOICE -> field.choices.any { choice ->
                    choice.value == value
                }
            }
            if (!valid) throw ProviderOperationException("${field.label} has an invalid value")
        }
        values[SubscriptionProviderSettingKeys.BaseUrl]?.let { baseUrl ->
            if (baseUrl.length > MAX_BASE_URL_LENGTH) {
                throw ProviderOperationException("Server URL is too long")
            }
            if (!baseUrl.isStableProviderBaseUrl()) {
                throw ProviderOperationException(
                    "Server URL must not contain user information, a query, or a fragment"
                )
            }
        }
        return values
    }

    private fun SubscriptionProviderDescriptor.isUsableFor(extensionId: ExtensionId): Boolean {
        if (
            providerId != extensionId ||
            !displayName.isSafeExtensionText(MAX_PROVIDER_NAME_LENGTH) ||
            variants.size > MAX_PROVIDER_KINDS ||
            variants.any { variant ->
                !variant.displayName.isSafeExtensionText(MAX_PROVIDER_NAME_LENGTH)
            }
        ) {
            return false
        }
        val schema = settingsSchema ?: return false
        if (schema.fields.isEmpty() || schema.fields.size > MAX_PROVIDER_SETTINGS) return false
        if (
            schema.fields.count { field -> field.type == ExtensionSettingType.SECRET } >
            MAX_PROVIDER_CREDENTIALS
        ) {
            return false
        }
        if (schema.fields.any { field ->
                !field.label.isSafeExtensionText(MAX_SETTING_LABEL_LENGTH) ||
                    (
                        field.description?.isSafeExtensionText(
                            maximumLength = MAX_SETTING_DESCRIPTION_LENGTH,
                            allowBlank = true,
                        ) == false
                    ) ||
                    field.choices.size > MAX_SETTING_CHOICES ||
                    field.choices.any { choice ->
                        choice.value.length > MAX_SETTING_CHOICE_VALUE_LENGTH ||
                            !choice.label.isSafeExtensionText(MAX_SETTING_LABEL_LENGTH)
                    }
            }
        ) {
            return false
        }
        return schema.fields.any { field ->
            field.key == SubscriptionProviderSettingKeys.BaseUrl &&
                field.type == ExtensionSettingType.TEXT &&
                field.required
        }
    }

    private fun ValidatedProviderAccount.requireValidFor(
        descriptor: SubscriptionProviderDescriptor,
    ) {
        if (descriptor.variants.none { variant -> variant.kind == detectedKind }) {
            throw ProviderOperationException("Provider returned an unsupported account kind")
        }
        if (
            normalizedBaseUrl.length > MAX_BASE_URL_LENGTH ||
            !normalizedBaseUrl.isStableProviderBaseUrl()
        ) {
            throw ProviderOperationException("Provider returned an invalid server URL")
        }
        val identifiers = listOf(serverId, userId)
        if (identifiers.any { value -> value.isBlank() || value.length > MAX_ACCOUNT_ID_LENGTH }) {
            throw ProviderOperationException("Provider returned an invalid account identity")
        }
        if (
            !serverName.isSafeExtensionText(MAX_ACCOUNT_LABEL_LENGTH) ||
            !serverVersion.isSafeExtensionText(
                maximumLength = MAX_ACCOUNT_LABEL_LENGTH,
                allowBlank = true,
            ) ||
            !username.isSafeExtensionText(
                maximumLength = MAX_ACCOUNT_LABEL_LENGTH,
                allowBlank = true,
            )
        ) {
            throw ProviderOperationException("Provider returned invalid account metadata")
        }
    }

    private fun ChannelPlaybackReference.toContract(): PlaybackReference = PlaybackReference(
        providerId = ExtensionId(providerId),
        itemId = itemId,
        mediaSourceId = mediaSourceId,
        sourceType = sourceType,
    )

    private fun String.isStableProviderBaseUrl(): Boolean = runCatching {
        val url = toHttpUrl()
        toCanonicalHttpOrigin()
        require(url.query == null && url.fragment == null)
    }.isSuccess

    private fun String.isSafeExtensionText(
        maximumLength: Int,
        allowBlank: Boolean = false,
    ): Boolean =
        (allowBlank || isNotBlank()) &&
            length <= maximumLength &&
            none { character ->
                character.isISOControl() ||
                    character.code in 0x202A..0x202E ||
                    character.code in 0x2066..0x2069 ||
                    character.code == 0x200E ||
                    character.code == 0x200F
            }

    private fun String.isSensitivePlaybackHeader(): Boolean {
        val normalized = lowercase()
        return normalized == "authorization" ||
            normalized == "proxy-authorization" ||
            normalized == "cookie" ||
            normalized == "set-cookie" ||
            normalized.contains("authentication") ||
            normalized.contains("token") ||
            normalized.contains("api-key") ||
            normalized.contains("apikey") ||
            normalized.contains("secret") ||
            normalized.contains("credential")
    }

    private fun ProviderPlaybackCloseReason.toContract(): PlaybackSessionCloseReason = when (this) {
        ProviderPlaybackCloseReason.STOPPED -> PlaybackSessionCloseReason.Stopped
        ProviderPlaybackCloseReason.ENDED -> PlaybackSessionCloseReason.Ended
        ProviderPlaybackCloseReason.CHANNEL_CHANGED -> PlaybackSessionCloseReason.ChannelChanged
        ProviderPlaybackCloseReason.PLAYBACK_FAILED -> PlaybackSessionCloseReason.PlaybackFailed
        ProviderPlaybackCloseReason.RECOVERY -> PlaybackSessionCloseReason.Recovery
    }

    private fun ProviderPlaybackSession.toEntity() = ProviderPlaybackSessionEntity(
        id = id,
        accountId = accountId,
        providerId = providerId,
        itemId = itemId,
        mediaSourceId = mediaSourceId,
        sourceType = sourceType,
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
        playSessionId = playSessionId,
        liveStreamId = liveStreamId,
    )

    private fun CapturedProviderAuthentication.toValidatedAccount(
        request: ProviderSubscriptionRequest,
        descriptor: SubscriptionProviderDescriptor,
        settingValues: Map<String, String>,
    ): ValidatedProviderAccount {
        val normalizedBaseUrl = settingValues.getValue(SubscriptionProviderSettingKeys.BaseUrl)
            .trim()
            .trimEnd('/')
        if (normalizedBaseUrl.toCanonicalHttpOrigin() != approvedOrigin) {
            throw ProviderOperationException(
                "Provider authentication completed on an unapproved server"
            )
        }
        val username = settingValues[SubscriptionProviderSettingKeys.Username].orEmpty()
        val serverIdentity = opaqueContexts[ProviderAuthenticationContextKeys.ServerId]
            ?.takeIf(String::isNotBlank)
            ?: throw ProviderOperationException(
                "Provider login did not return a stable server identity"
            )
        val userIdentity = opaqueContexts[ProviderAuthenticationContextKeys.UserId]
            ?.takeIf(String::isNotBlank)
            ?: throw ProviderOperationException(
                "Provider login did not return a stable user identity"
            )
        return ValidatedProviderAccount(
            normalizedBaseUrl = normalizedBaseUrl,
            detectedKind = request.providerKind,
            serverId = stableOpaqueIdentity(
                providerId = request.providerId.value,
                origin = approvedOrigin,
                key = ProviderAuthenticationContextKeys.ServerId,
                value = serverIdentity,
            ),
            serverName = descriptor.displayName,
            serverVersion = "",
            userId = stableOpaqueIdentity(
                providerId = request.providerId.value,
                origin = approvedOrigin,
                key = ProviderAuthenticationContextKeys.UserId,
                value = userIdentity,
            ),
            username = username,
        )
    }

    private fun stableOpaqueIdentity(
        providerId: String,
        origin: String,
        key: String,
        value: String,
    ): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(
            listOf(providerId, origin, key, value).joinToString("\u0000").encodeToByteArray()
        )
        return buildString(OPAQUE_ID_PREFIX.length + digest.size * 2) {
            append(OPAQUE_ID_PREFIX)
            digest.forEach { byte ->
                val valueByte = byte.toInt() and 0xff
                append(HEX_DIGITS[valueByte ushr 4])
                append(HEX_DIGITS[valueByte and 0x0f])
            }
        }
    }

    private fun providerPlaylistUrl(accountId: String): String = "m3u-provider://account/$accountId/live"

    private fun ExtensionExecutionKind.toProviderExecutionKind(): SubscriptionProviderExecutionKind =
        when (this) {
            ExtensionExecutionKind.BUILT_IN -> SubscriptionProviderExecutionKind.BUILT_IN
            ExtensionExecutionKind.EXTERNAL -> SubscriptionProviderExecutionKind.EXTERNAL
        }

    private sealed interface ProviderDiscoveryAttempt {
        data class Succeeded(
            val provider: DiscoveredSubscriptionProvider,
        ) : ProviderDiscoveryAttempt

        data object Failed : ProviderDiscoveryAttempt
    }

    private enum class OrphanedSessionCloseOutcome {
        CLOSED,
        PENDING,
        SKIPPED,
    }

    private data class PlaybackCloseContext(
        val account: ProviderAccount,
        val credential: ProviderCredentialEntity,
    )

    private data class PlaybackSessionReservation(
        val id: String,
        val accountId: String,
    )

    private data class ProviderValidationMaterial(
        val account: ValidatedProviderAccount,
        val credential: CredentialHandle,
    )

    private companion object {
        const val MAX_PROVIDER_KINDS = 16
        const val MAX_PROVIDER_SETTINGS = 32
        const val MAX_PROVIDER_CREDENTIALS = 16
        const val MAX_PROVIDER_NAME_LENGTH = 80
        const val MAX_SETTING_LABEL_LENGTH = 160
        const val MAX_SETTING_DESCRIPTION_LENGTH = 1_024
        const val MAX_SETTING_CHOICES = 64
        const val MAX_SETTING_CHOICE_VALUE_LENGTH = 512
        const val MAX_SETTING_VALUE_LENGTH = 4_096
        const val MAX_BASE_URL_LENGTH = 2_048
        const val MAX_ACCOUNT_ID_LENGTH = 512
        const val MAX_ACCOUNT_LABEL_LENGTH = 1_024
        const val MAX_PLAYBACK_URL_LENGTH = 8_192
        const val MAX_PLAYBACK_HEADERS = 32
        const val MAX_PLAYBACK_HEADER_VALUE_LENGTH = 8_192
        const val MAX_SESSIONS_PER_ACCOUNT = 8
        const val MAX_SESSIONS_GLOBAL = 64
        const val MAX_SESSION_CLEANUP_BATCH = 128
        const val OPAQUE_ID_PREFIX = "opaque:"
        const val HEX_DIGITS = "0123456789abcdef"
        val HTTP_HEADER_NAME = Regex("[!#$%&'*+.^_`|~0-9A-Za-z-]+")
    }

}
