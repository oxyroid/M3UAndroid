package com.m3u.data.repository.extension

import com.m3u.data.database.dao.ChannelDao
import com.m3u.data.database.dao.ProviderDao
import com.m3u.data.database.model.ProviderAccount
import com.m3u.extension.api.ChannelMetadataPatch
import com.m3u.extension.api.ChannelMetadataSnapshot
import com.m3u.extension.api.ExtensionState
import com.m3u.extension.api.ExtensionProgramme
import com.m3u.extension.api.EpgRefreshRequest
import com.m3u.extension.api.HookResult
import com.m3u.extension.api.HostHookSpecs
import com.m3u.extension.api.MetadataEnrichmentRequest
import com.m3u.extension.api.SearchProviderRequest
import com.m3u.extension.api.ExtensionId
import com.m3u.extension.api.security.CredentialHandle
import com.m3u.extension.api.subscription.ProviderAccountReference
import com.m3u.extension.api.subscription.ProviderCredential
import com.m3u.extension.api.subscription.ProviderKind
import com.m3u.extension.runtime.ExtensionRuntime
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope

internal class ExtensionContributionRepositoryImpl @Inject constructor(
    private val runtime: ExtensionRuntime,
    private val providerDao: ProviderDao,
    private val channelDao: ChannelDao,
) : ExtensionContributionRepository {
    override suspend fun search(query: String, limit: Int): List<ExtensionSearchContribution> {
        val normalizedQuery = query.trim()
        if (normalizedQuery.isEmpty() || limit <= 0) return emptyList()
        val acceptedLimit = limit.coerceAtMost(MAX_SEARCH_RESULTS)
        return supervisorScope {
            runtime.extensionsSupporting(HostHookSpecs.SearchProvider.hook)
                .filter { extension -> extension.state == ExtensionState.ENABLED }
                .map { extension ->
                    async<List<ExtensionSearchContribution>> {
                        try {
                            val accountEntities = providerDao
                                .getAccountsByProviderId(extension.manifest.id.value)
                            val invocationAccounts: List<ProviderAccount?> =
                                accountEntities.ifEmpty { listOf(null) }
                            buildList {
                                for (account in invocationAccounts) {
                                    if (size >= acceptedLimit) break
                                    val binding = account?.toInvocationBinding()
                                    if (account != null && binding == null) continue
                                    val remainingLimit = acceptedLimit - size
                                    val outcome = try {
                                        runtime.invoke(
                                            extension.manifest.id,
                                            HostHookSpecs.SearchProvider,
                                            SearchProviderRequest(
                                                query = normalizedQuery,
                                                account = binding?.account,
                                                credential = binding?.credential,
                                                limit = remainingLimit,
                                            ),
                                            validateResponse = { response ->
                                                require(response.items.size <= remainingLimit)
                                                require(
                                                    response.items.all { item ->
                                                        (account == null ||
                                                            item.accountId == account.id) &&
                                                            item.accountId.isSafeExtensionText(
                                                                MAX_STABLE_REFERENCE_LENGTH
                                                            ) &&
                                                            item.remoteId.isSafeExtensionText(
                                                                MAX_STABLE_REFERENCE_LENGTH
                                                            )
                                                    }
                                                )
                                                require(
                                                    response.items
                                                        .map { item ->
                                                            item.accountId to item.remoteId
                                                        }
                                                        .distinct()
                                                        .size == response.items.size
                                                )
                                            },
                                        ).outcome
                                    } catch (cancelled: CancellationException) {
                                        throw cancelled
                                    } catch (_: Exception) {
                                        continue
                                    }
                                    if (outcome is HookResult.Success) {
                                        outcome.payload.items
                                            .distinctBy { item ->
                                                item.accountId to item.remoteId
                                            }
                                            .take(acceptedLimit - size)
                                            .forEach { item ->
                                                val resolvedAccount = account
                                                    ?: providerDao.getAccount(item.accountId)
                                                    ?: return@forEach
                                                val channel = channelDao
                                                    .getByPlaylistUrlAndRelationId(
                                                        playlistUrl =
                                                            resolvedAccount.playlistUrl,
                                                        relationId = item.remoteId,
                                                    )
                                                if (channel != null && !channel.hidden) {
                                                    add(
                                                        ExtensionSearchContribution(
                                                            extensionId = extension.manifest.id,
                                                            channel = channel,
                                                        )
                                                    )
                                                }
                                            }
                                    }
                                }
                            }
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            emptyList()
                        }
                    }
                }
                .awaitAll()
                .flatten()
                .distinctBy { contribution -> contribution.channel.id }
                .take(acceptedLimit)
        }
    }

    override suspend fun enrichChannels(
        channels: List<ChannelMetadataSnapshot>,
        playlistUrl: String?,
    ): List<ExtensionMetadataRefreshContribution> {
        val acceptedChannels = channels
            .asSequence()
            .filter { channel -> channel.stableReference.isNotBlank() }
            .distinctBy(ChannelMetadataSnapshot::stableReference)
            .take(MAX_CHANNELS_PER_ENRICHMENT)
            .toList()
        if (acceptedChannels.isEmpty()) return emptyList()
        val playlistBinding = playlistUrl?.let { url ->
            providerDao.getAccountByPlaylistUrl(url)?.toInvocationBinding()
        }
        return supervisorScope {
            runtime.extensionsSupporting(HostHookSpecs.MetadataEnrichment.hook)
                .filter { extension -> extension.state == ExtensionState.ENABLED }
                .map { extension ->
                    async<ExtensionMetadataRefreshContribution?> {
                        try {
                            val accountBinding = playlistBinding?.takeIf { binding ->
                                binding.account.providerId == extension.manifest.id
                            }
                            val patches = mutableListOf<ChannelMetadataPatch>()
                            for (batch in acceptedChannels.chunked(CHANNEL_ENRICHMENT_BATCH_SIZE)) {
                                val batchReferences = batch
                                    .mapTo(mutableSetOf(), ChannelMetadataSnapshot::stableReference)
                                when (
                                    val outcome = runtime.invoke(
                                        extension.manifest.id,
                                        HostHookSpecs.MetadataEnrichment,
                                        MetadataEnrichmentRequest(
                                            channels = batch,
                                            account = accountBinding?.account,
                                            credential = accountBinding?.credential,
                                        ),
                                        validateResponse = { response ->
                                            require(response.patches.size <= batch.size)
                                            require(
                                                response.patches.all { patch ->
                                                    patch.isValidFor(batchReferences)
                                                }
                                            )
                                            require(
                                                response.patches
                                                    .map(ChannelMetadataPatch::stableReference)
                                                    .distinct()
                                                    .size == response.patches.size
                                            )
                                    },
                                ).outcome
                                ) {
                                    is HookResult.Success -> patches += outcome.payload.patches
                                        .asSequence()
                                        .filter { patch -> patch.isValidFor(batchReferences) }
                                        .distinctBy(ChannelMetadataPatch::stableReference)
                                        .take(batch.size)
                                        .toList()
                                    is HookResult.Failure -> {
                                        if (outcome.error.recoverable) {
                                            throw RecoverableExtensionContributionException(
                                                outcome.error.code.value
                                            )
                                        }
                                        return@async null
                                    }
                                }
                            }
                            ExtensionMetadataRefreshContribution(
                                extensionId = extension.manifest.id,
                                patches = patches,
                            )
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (recoverable: RecoverableExtensionContributionException) {
                            throw recoverable
                        } catch (failure: Exception) {
                            throw RecoverableExtensionContributionException(
                                failure.javaClass.simpleName
                            )
                        }
                    }
                }
                .awaitAll()
                .filterNotNull()
        }
    }

    override suspend fun refreshEpg(
        channelReferences: List<String>,
        fromEpochMillis: Long,
        toEpochMillis: Long,
        playlistUrl: String?,
    ): List<ExtensionEpgRefreshContribution> {
        if (fromEpochMillis >= toEpochMillis) return emptyList()
        val acceptedReferences = channelReferences
            .asSequence()
            .filter(String::isNotBlank)
            .distinct()
            .take(MAX_CHANNELS_PER_ENRICHMENT + 1)
            .toList()
        if (acceptedReferences.isEmpty()) return emptyList()
        if (acceptedReferences.size > MAX_CHANNELS_PER_ENRICHMENT) return emptyList()
        val playlistBinding = playlistUrl?.let { url ->
            providerDao.getAccountByPlaylistUrl(url)?.toInvocationBinding()
        }
        return supervisorScope {
            runtime.extensionsSupporting(HostHookSpecs.EpgRefresh.hook)
                .filter { extension -> extension.state == ExtensionState.ENABLED }
                .map { extension ->
                    async<ExtensionEpgRefreshContribution?> {
                        try {
                            val accountBinding = playlistBinding?.takeIf { binding ->
                                binding.account.providerId == extension.manifest.id
                            }
                            val programmes = mutableListOf<ExtensionProgramme>()
                            var receivedProgrammeCount = 0
                            for (batch in acceptedReferences.chunked(CHANNEL_ENRICHMENT_BATCH_SIZE)) {
                                val batchReferences = batch.toSet()
                                when (
                                    val outcome = runtime.invoke(
                                        extension.manifest.id,
                                        HostHookSpecs.EpgRefresh,
                                        EpgRefreshRequest(
                                            sourceIds = batch,
                                            fromEpochMillis = fromEpochMillis,
                                            toEpochMillis = toEpochMillis,
                                            account = accountBinding?.account,
                                            credential = accountBinding?.credential,
                                        ),
                                        validateResponse = { response ->
                                            require(
                                                response.programmes.size <=
                                                    MAX_PROGRAMMES_PER_BATCH
                                            )
                                            require(
                                                response.programmes.all { programme ->
                                                    programme.isValidFor(
                                                        batchReferences,
                                                        fromEpochMillis,
                                                        toEpochMillis,
                                                    )
                                                }
                                            )
                                        },
                                    ).outcome
                                ) {
                                    is HookResult.Success -> {
                                        val returnedProgrammes = outcome.payload.programmes
                                        val exceedsExtensionLimit = returnedProgrammes.size >
                                            MAX_PROGRAMMES_PER_EXTENSION - receivedProgrammeCount
                                        if (
                                            returnedProgrammes.size > MAX_PROGRAMMES_PER_BATCH ||
                                            exceedsExtensionLimit
                                        ) {
                                            return@async null
                                        }
                                        receivedProgrammeCount += returnedProgrammes.size
                                        programmes += returnedProgrammes
                                            .asSequence()
                                            .filter { programme ->
                                                programme.isValidFor(
                                                    batchReferences,
                                                    fromEpochMillis,
                                                    toEpochMillis,
                                                )
                                            }
                                            .toList()
                                    }
                                    is HookResult.Failure -> {
                                        if (outcome.error.recoverable) {
                                            throw RecoverableExtensionContributionException(
                                                outcome.error.code.value
                                            )
                                        }
                                        return@async null
                                    }
                                }
                            }
                            ExtensionEpgRefreshContribution(
                                extensionId = extension.manifest.id,
                                programmes = programmes
                                    .distinctBy { programme ->
                                        with(programme) {
                                            listOf(
                                                channelReference,
                                                startEpochMillis.toString(),
                                                endEpochMillis.toString(),
                                                title,
                                            )
                                        }
                                    }
                                    .toList(),
                            )
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (recoverable: RecoverableExtensionContributionException) {
                            throw recoverable
                        } catch (failure: Exception) {
                            throw RecoverableExtensionContributionException(
                                failure.javaClass.simpleName
                            )
                        }
                    }
                }
                .awaitAll()
                .filterNotNull()
        }
    }

    private fun ChannelMetadataPatch.isValidFor(acceptedReferences: Set<String>): Boolean =
        stableReference in acceptedReferences &&
            (title?.isValidDisplayValue() != false) &&
            (category?.isValidDisplayValue() != false)

    private fun String.isValidDisplayValue(): Boolean =
        isSafeExtensionText(MAX_DISPLAY_LENGTH)

    private fun ExtensionProgramme.isValidFor(
        acceptedReferences: Set<String>,
        fromEpochMillis: Long,
        toEpochMillis: Long,
    ): Boolean = channelReference in acceptedReferences &&
        title.isValidDisplayValue() &&
        startEpochMillis < endEpochMillis &&
        endEpochMillis >= fromEpochMillis &&
        startEpochMillis <= toEpochMillis &&
        (
            description?.isSafeExtensionText(
                maximumLength = MAX_DESCRIPTION_LENGTH,
                allowBlank = true,
            ) != false
        ) &&
        categories.size <= MAX_CATEGORIES &&
        categories.all { category ->
            category.isSafeExtensionText(MAX_CATEGORY_LENGTH)
        }

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

    private suspend fun ProviderAccount.toInvocationBinding(): ProviderInvocationBinding? {
        if (requiresReauthentication) return null
        val credential = providerDao.getCredential(id) ?: return null
        return ProviderInvocationBinding(
            account = ProviderAccountReference(
                accountId = id,
                providerId = ExtensionId(providerId),
                providerKind = ProviderKind(providerKind),
                baseUrl = baseUrl,
                serverId = serverId,
                serverName = serverName,
                serverVersion = serverVersion,
                userId = userId,
                username = username,
            ),
            credential = ProviderCredential(
                handle = CredentialHandle(credential.credentialHandle)
            ),
        )
    }

    private data class ProviderInvocationBinding(
        val account: ProviderAccountReference,
        val credential: ProviderCredential,
    )

    private companion object {
        const val CHANNEL_ENRICHMENT_BATCH_SIZE = 200
        const val MAX_CHANNELS_PER_ENRICHMENT = 5_000
        const val MAX_DISPLAY_LENGTH = 512
        const val MAX_STABLE_REFERENCE_LENGTH = 512
        const val MAX_SEARCH_RESULTS = 100
        const val MAX_DESCRIPTION_LENGTH = 16_384
        const val MAX_CATEGORIES = 32
        const val MAX_CATEGORY_LENGTH = 256
        const val MAX_PROGRAMMES_PER_BATCH = 10_000
        const val MAX_PROGRAMMES_PER_EXTENSION = 50_000
    }
}

internal class RecoverableExtensionContributionException(
    diagnosticCode: String,
) : Exception("Extension contribution can be retried ($diagnosticCode)")
