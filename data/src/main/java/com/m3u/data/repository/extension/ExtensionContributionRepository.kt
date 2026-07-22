package com.m3u.data.repository.extension

import com.m3u.extension.api.ExtensionId
import com.m3u.extension.api.ExtensionProgramme
import com.m3u.extension.api.ChannelMetadataPatch
import com.m3u.extension.api.ChannelMetadataSnapshot
import com.m3u.extension.api.SearchProviderItem

interface ExtensionContributionRepository {
    suspend fun search(query: String, limit: Int = 50): List<ExtensionSearchContribution>

    suspend fun enrichChannels(
        channels: List<ChannelMetadataSnapshot>,
    ): List<ExtensionMetadataContribution>

    suspend fun refreshEpg(
        channelReferences: List<String>,
        fromEpochMillis: Long,
        toEpochMillis: Long,
    ): List<ExtensionEpgContribution>
}

data class ExtensionSearchContribution(
    val extensionId: ExtensionId,
    val item: SearchProviderItem,
)

data class ExtensionMetadataContribution(
    val extensionId: ExtensionId,
    val patch: ChannelMetadataPatch,
)

data class ExtensionEpgContribution(
    val extensionId: ExtensionId,
    val programme: ExtensionProgramme,
)
