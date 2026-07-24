package com.m3u.data.repository.extension

import com.m3u.data.database.model.Channel
import com.m3u.extension.api.ExtensionId
import com.m3u.extension.api.ExtensionProgramme
import com.m3u.extension.api.ChannelMetadataPatch
import com.m3u.extension.api.ChannelMetadataSnapshot

interface ExtensionContributionRepository {
    suspend fun search(query: String, limit: Int = 50): List<ExtensionSearchContribution>

    suspend fun enrichChannels(
        channels: List<ChannelMetadataSnapshot>,
        playlistUrl: String? = null,
    ): List<ExtensionMetadataRefreshContribution>

    suspend fun refreshEpg(
        channelReferences: List<String>,
        fromEpochMillis: Long,
        toEpochMillis: Long,
        playlistUrl: String? = null,
    ): List<ExtensionEpgRefreshContribution>
}

data class ExtensionSearchContribution(
    val extensionId: ExtensionId,
    val channel: Channel,
)

data class ExtensionMetadataRefreshContribution(
    val extensionId: ExtensionId,
    val patches: List<ChannelMetadataPatch>,
)

data class ExtensionEpgRefreshContribution(
    val extensionId: ExtensionId,
    val programmes: List<ExtensionProgramme>,
)
