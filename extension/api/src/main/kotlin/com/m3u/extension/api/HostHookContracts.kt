package com.m3u.extension.api

import com.m3u.extension.api.subscription.ProviderAccountReference
import com.m3u.extension.api.subscription.ProviderCredential
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
) {
    init {
        require(id.matches(Regex("[a-z][a-z0-9._-]*"))) { "Invalid settings section id: $id" }
        require(title.isNotBlank()) { "Settings section title must not be blank" }
    }
}

@Serializable
data class SettingsSchemaResult(
    val sections: List<ExtensionSettingSection>,
) : ExtensionPayload

@Serializable
data class EpgRefreshRequest(
    val sourceIds: List<String>,
    val fromEpochMillis: Long,
    val toEpochMillis: Long,
    val account: ProviderAccountReference? = null,
    val credential: ProviderCredential? = null,
) : ExtensionPayload {
    init {
        require((account == null) == (credential == null)) {
            "Account-scoped EPG requests require both an account and credential"
        }
    }
}

@Serializable
data class ExtensionProgramme(
    val channelReference: String,
    val title: String,
    val startEpochMillis: Long,
    val endEpochMillis: Long,
    val description: String? = null,
    val categories: List<String> = emptyList(),
)

@Serializable
data class EpgRefreshResult(
    val programmes: List<ExtensionProgramme>,
) : ExtensionPayload

@Serializable
data class ChannelMetadataSnapshot(
    val stableReference: String,
    val title: String,
    val category: String,
)

@Serializable
data class MetadataEnrichmentRequest(
    val channels: List<ChannelMetadataSnapshot>,
    val account: ProviderAccountReference? = null,
    val credential: ProviderCredential? = null,
) : ExtensionPayload {
    init {
        require((account == null) == (credential == null)) {
            "Account-scoped metadata requests require both an account and credential"
        }
    }
}

@Serializable
data class ChannelMetadataPatch(
    val stableReference: String,
    val title: String? = null,
    val category: String? = null,
)

@Serializable
data class MetadataEnrichmentResult(
    val patches: List<ChannelMetadataPatch>,
) : ExtensionPayload

@Serializable
data class SearchProviderRequest(
    val query: String,
    val account: ProviderAccountReference? = null,
    val credential: ProviderCredential? = null,
    val limit: Int = 50,
) : ExtensionPayload {
    init {
        require(query.isNotBlank() && query.encodeToByteArray().size <= 512)
        require(limit in 1..100)
        require((account == null) == (credential == null)) {
            "Account-scoped search requests require both an account and credential"
        }
    }
}

@Serializable
data class SearchProviderItem(
    val accountId: String,
    val remoteId: String,
) {
    init {
        require(accountId.isNotBlank() && accountId.encodeToByteArray().size <= 512)
        require(remoteId.isNotBlank() && remoteId.encodeToByteArray().size <= 512)
    }
}

@Serializable
data class SearchProviderResult(
    val items: List<SearchProviderItem>,
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
        4,
        EpgRefreshRequest.serializer(),
        EpgRefreshResult.serializer(),
    )
    val MetadataEnrichment = HookSpec(
        ExtensionHookIds.MetadataChannelEnrich,
        3,
        MetadataEnrichmentRequest.serializer(),
        MetadataEnrichmentResult.serializer(),
    )
    val SearchProvider = HookSpec(
        ExtensionHookIds.SearchProviderQuery,
        4,
        SearchProviderRequest.serializer(),
        SearchProviderResult.serializer(),
    )
    val BackgroundTask = HookSpec(
        ExtensionHookIds.BackgroundTaskRun,
        2,
        BackgroundTaskRequest.serializer(),
        BackgroundTaskResult.serializer(),
    )
}
