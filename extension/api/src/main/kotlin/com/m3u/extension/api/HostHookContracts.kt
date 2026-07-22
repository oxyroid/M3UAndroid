package com.m3u.extension.api

import kotlinx.serialization.Serializable

@Serializable
data class SettingsSchemaRequest(
    val localeTag: String? = null,
    val surface: String,
) : ExtensionPayload

@Serializable
data class ExtensionSettingSection(
    val id: String,
    val title: String,
    val schema: ExtensionSettingSchema,
)

@Serializable
data class SettingsSchemaResult(
    val sections: List<ExtensionSettingSection>,
) : ExtensionPayload

@Serializable
data class EpgRefreshRequest(
    val sourceIds: List<String>,
    val fromEpochMillis: Long,
    val toEpochMillis: Long,
) : ExtensionPayload

@Serializable
data class ExtensionProgramme(
    val channelReference: String,
    val title: String,
    val startEpochMillis: Long,
    val endEpochMillis: Long,
    val description: String? = null,
    val metadata: Map<String, String> = emptyMap(),
)

@Serializable
data class EpgRefreshResult(
    val programmes: List<ExtensionProgramme>,
    val syncMetadata: Map<String, String> = emptyMap(),
) : ExtensionPayload

@Serializable
data class ChannelMetadataSnapshot(
    val stableReference: String,
    val title: String,
    val category: String,
    val metadata: Map<String, String> = emptyMap(),
)

@Serializable
data class MetadataEnrichmentRequest(
    val channels: List<ChannelMetadataSnapshot>,
) : ExtensionPayload

@Serializable
data class ChannelMetadataPatch(
    val stableReference: String,
    val title: String? = null,
    val category: String? = null,
    val metadata: Map<String, String> = emptyMap(),
)

@Serializable
data class MetadataEnrichmentResult(
    val patches: List<ChannelMetadataPatch>,
) : ExtensionPayload

@Serializable
data class SearchProviderRequest(
    val query: String,
    val limit: Int = 50,
    val continuationToken: String? = null,
) : ExtensionPayload

@Serializable
data class SearchProviderItem(
    val stableReference: String,
    val title: String,
    val subtitle: String? = null,
    val artworkUrl: String? = null,
    val metadata: Map<String, String> = emptyMap(),
)

@Serializable
data class SearchProviderResult(
    val items: List<SearchProviderItem>,
    val continuationToken: String? = null,
) : ExtensionPayload

@Serializable
data class BackgroundTaskRequest(
    val taskId: String,
    val input: Map<String, String> = emptyMap(),
    val runAttempt: Int = 0,
) : ExtensionPayload

@Serializable
data class BackgroundTaskResult(
    val output: Map<String, String> = emptyMap(),
    val retryAfterMillis: Long? = null,
) : ExtensionPayload

object HostHookSpecs {
    val SettingsSchema = HookSpec(
        ExtensionHookIds.SettingsSchemaContribute,
        1,
        SettingsSchemaRequest.serializer(),
        SettingsSchemaResult.serializer(),
    )
    val EpgRefresh = HookSpec(
        ExtensionHookIds.EpgContentRefresh,
        1,
        EpgRefreshRequest.serializer(),
        EpgRefreshResult.serializer(),
    )
    val MetadataEnrichment = HookSpec(
        ExtensionHookIds.MetadataChannelEnrich,
        1,
        MetadataEnrichmentRequest.serializer(),
        MetadataEnrichmentResult.serializer(),
    )
    val SearchProvider = HookSpec(
        ExtensionHookIds.SearchProviderQuery,
        1,
        SearchProviderRequest.serializer(),
        SearchProviderResult.serializer(),
    )
    val BackgroundTask = HookSpec(
        ExtensionHookIds.BackgroundTaskRun,
        1,
        BackgroundTaskRequest.serializer(),
        BackgroundTaskResult.serializer(),
    )
}
