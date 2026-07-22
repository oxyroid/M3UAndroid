package com.m3u.data.repository.extension

import com.m3u.extension.api.ChannelMetadataPatch
import com.m3u.extension.api.ChannelMetadataSnapshot
import com.m3u.extension.api.ExtensionState
import com.m3u.extension.api.ExtensionProgramme
import com.m3u.extension.api.EpgRefreshRequest
import com.m3u.extension.api.HookResult
import com.m3u.extension.api.HostHookSpecs
import com.m3u.extension.api.MetadataEnrichmentRequest
import com.m3u.extension.api.SearchProviderRequest
import com.m3u.extension.runtime.ExtensionRuntime
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope

internal class ExtensionContributionRepositoryImpl @Inject constructor(
    private val runtime: ExtensionRuntime,
) : ExtensionContributionRepository {
    override suspend fun search(query: String, limit: Int): List<ExtensionSearchContribution> {
        val normalizedQuery = query.trim()
        if (normalizedQuery.isEmpty() || limit <= 0) return emptyList()
        return supervisorScope {
            runtime.extensionsSupporting(HostHookSpecs.SearchProvider.hook)
                .filter { extension -> extension.state == ExtensionState.ENABLED }
                .map { extension ->
                    async {
                        try {
                            when (
                                val outcome = runtime.invoke(
                                    extension.manifest.id,
                                    HostHookSpecs.SearchProvider,
                                    SearchProviderRequest(normalizedQuery, limit),
                                ).outcome
                            ) {
                                is HookResult.Success -> outcome.payload.items.map { item ->
                                    ExtensionSearchContribution(extension.manifest.id, item)
                                }
                                is HookResult.Failure -> emptyList()
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
                .distinctBy { contribution ->
                    contribution.extensionId.value to contribution.item.stableReference
                }
                .take(limit)
        }
    }

    override suspend fun enrichChannels(
        channels: List<ChannelMetadataSnapshot>,
    ): List<ExtensionMetadataContribution> {
        val acceptedChannels = channels
            .asSequence()
            .filter { channel -> channel.stableReference.isNotBlank() }
            .distinctBy(ChannelMetadataSnapshot::stableReference)
            .take(MAX_CHANNELS_PER_ENRICHMENT)
            .toList()
        if (acceptedChannels.isEmpty()) return emptyList()
        return supervisorScope {
            runtime.extensionsSupporting(HostHookSpecs.MetadataEnrichment.hook)
                .filter { extension -> extension.state == ExtensionState.ENABLED }
                .map { extension ->
                    async {
                        try {
                            acceptedChannels.chunked(CHANNEL_ENRICHMENT_BATCH_SIZE).flatMap { batch ->
                                val batchReferences = batch
                                    .mapTo(mutableSetOf(), ChannelMetadataSnapshot::stableReference)
                                when (
                                    val outcome = runtime.invoke(
                                        extension.manifest.id,
                                        HostHookSpecs.MetadataEnrichment,
                                        MetadataEnrichmentRequest(batch),
                                    ).outcome
                                ) {
                                    is HookResult.Success -> outcome.payload.patches
                                        .asSequence()
                                        .filter { patch -> patch.isValidFor(batchReferences) }
                                        .distinctBy(ChannelMetadataPatch::stableReference)
                                        .take(batch.size)
                                        .map { patch ->
                                            ExtensionMetadataContribution(extension.manifest.id, patch)
                                        }
                                        .toList()
                                    is HookResult.Failure -> emptyList()
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
                .distinctBy { contribution -> contribution.patch.stableReference }
        }
    }

    override suspend fun refreshEpg(
        channelReferences: List<String>,
        fromEpochMillis: Long,
        toEpochMillis: Long,
    ): List<ExtensionEpgContribution> {
        if (fromEpochMillis >= toEpochMillis) return emptyList()
        val acceptedReferences = channelReferences
            .asSequence()
            .filter(String::isNotBlank)
            .distinct()
            .take(MAX_CHANNELS_PER_ENRICHMENT)
            .toList()
        if (acceptedReferences.isEmpty()) return emptyList()
        return supervisorScope {
            runtime.extensionsSupporting(HostHookSpecs.EpgRefresh.hook)
                .filter { extension -> extension.state == ExtensionState.ENABLED }
                .map { extension ->
                    async {
                        try {
                            acceptedReferences.chunked(CHANNEL_ENRICHMENT_BATCH_SIZE).flatMap { batch ->
                                val batchReferences = batch.toSet()
                                when (
                                    val outcome = runtime.invoke(
                                        extension.manifest.id,
                                        HostHookSpecs.EpgRefresh,
                                        EpgRefreshRequest(batch, fromEpochMillis, toEpochMillis),
                                    ).outcome
                                ) {
                                    is HookResult.Success -> outcome.payload.programmes
                                        .asSequence()
                                        .filter { programme ->
                                            programme.isValidFor(
                                                batchReferences,
                                                fromEpochMillis,
                                                toEpochMillis,
                                            )
                                        }
                                        .take(MAX_PROGRAMMES_PER_BATCH)
                                        .map { programme ->
                                            ExtensionEpgContribution(extension.manifest.id, programme)
                                        }
                                        .toList()
                                    is HookResult.Failure -> emptyList()
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
                .distinctBy { contribution ->
                    with(contribution.programme) {
                        listOf(
                            contribution.extensionId.value,
                            channelReference,
                            startEpochMillis.toString(),
                            endEpochMillis.toString(),
                            title,
                        )
                    }
                }
                .take(MAX_PROGRAMMES_TOTAL)
        }
    }

    private fun ChannelMetadataPatch.isValidFor(acceptedReferences: Set<String>): Boolean =
        stableReference in acceptedReferences &&
            (title?.isValidDisplayValue() != false) &&
            (category?.isValidDisplayValue() != false)

    private fun String.isValidDisplayValue(): Boolean = isNotBlank() && length <= MAX_DISPLAY_LENGTH

    private fun ExtensionProgramme.isValidFor(
        acceptedReferences: Set<String>,
        fromEpochMillis: Long,
        toEpochMillis: Long,
    ): Boolean = channelReference in acceptedReferences &&
        title.isValidDisplayValue() &&
        startEpochMillis < endEpochMillis &&
        endEpochMillis >= fromEpochMillis &&
        startEpochMillis <= toEpochMillis &&
        (description?.length?.let { length -> length <= MAX_DESCRIPTION_LENGTH } != false)

    private companion object {
        const val CHANNEL_ENRICHMENT_BATCH_SIZE = 200
        const val MAX_CHANNELS_PER_ENRICHMENT = 5_000
        const val MAX_DISPLAY_LENGTH = 512
        const val MAX_DESCRIPTION_LENGTH = 16_384
        const val MAX_PROGRAMMES_PER_BATCH = 10_000
        const val MAX_PROGRAMMES_TOTAL = 50_000
    }
}
