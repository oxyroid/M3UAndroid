package com.m3u.extension.api

import kotlinx.serialization.Serializable

private val CONTRACT_ID_PATTERN = Regex("[a-z0-9]+(?:[._-][a-z0-9]+)*")
private const val MAX_CONTRACT_ID_UTF8_BYTES = 128

@Serializable
@JvmInline
value class ExtensionId(val value: String) {
    init {
        require(
            value.encodeToByteArray().size <= MAX_CONTRACT_ID_UTF8_BYTES &&
                value.matches(CONTRACT_ID_PATTERN)
        ) {
            "Extension id must be a lowercase, dot-separated identifier"
        }
    }

    override fun toString(): String = value
}

@Serializable
@JvmInline
value class InvocationId(val value: String) {
    init {
        require(
            value.isNotBlank() &&
                value.encodeToByteArray().size <= MAX_CONTRACT_ID_UTF8_BYTES
        ) { "Invocation id must not be blank or too large" }
    }

    override fun toString(): String = value
}

@Serializable
@JvmInline
value class Hook(val id: String) {
    init {
        require(
            id.encodeToByteArray().size <= MAX_CONTRACT_ID_UTF8_BYTES &&
                id.matches(CONTRACT_ID_PATTERN)
        ) {
            "Hook id must be a lowercase, dot-separated identifier"
        }
    }

    override fun toString(): String = id
}

@Serializable
@JvmInline
value class Capability(val id: String) {
    init {
        require(
            id.encodeToByteArray().size <= MAX_CONTRACT_ID_UTF8_BYTES &&
                id.matches(CONTRACT_ID_PATTERN)
        ) {
            "Capability id must be a lowercase, dot-separated identifier"
        }
    }

    override fun toString(): String = id
}

@Serializable
data class ExtensionApiVersion(
    val major: Int,
    val minor: Int,
) : Comparable<ExtensionApiVersion> {
    init {
        require(major >= 0) { "API version major must not be negative" }
        require(minor >= 0) { "API version minor must not be negative" }
    }

    override fun compareTo(other: ExtensionApiVersion): Int =
        compareValuesBy(this, other, ExtensionApiVersion::major, ExtensionApiVersion::minor)

    override fun toString(): String = "$major.$minor"
}

@Serializable
data class ExtensionApiRange(
    val minimum: ExtensionApiVersion,
    val maximum: ExtensionApiVersion,
) {
    init {
        require(minimum <= maximum) { "Minimum API version must not exceed maximum API version" }
    }

    operator fun contains(version: ExtensionApiVersion): Boolean = version in minimum..maximum
}

object ExtensionApiVersions {
    val Current = ExtensionApiVersion(major = 1, minor = 0)
}

object ExtensionHookIds {
    val SubscriptionProviderDiscover = Hook("subscription.provider.discover")
    val SubscriptionProviderValidate = Hook("subscription.provider.validate")
    val SubscriptionContentRefresh = Hook("subscription.content.refresh")
    val PlaybackSourceResolve = Hook("playback.source.resolve")
    val PlaybackSessionClose = Hook("playback.session.close")
    val MetadataChannelEnrich = Hook("metadata.channel.enrich")
    val EpgContentRefresh = Hook("epg.content.refresh")
    val SettingsSchemaContribute = Hook("settings.schema.contribute")
    val SearchProviderQuery = Hook("search.provider.query")
    val BackgroundTaskRun = Hook("background.task.run")
}

object ExtensionCapabilityIds {
    val Network = Capability("network")
    val CredentialRead = Capability("credential.read")
    val CredentialWrite = Capability("credential.write")
    val SubscriptionRead = Capability("subscription.read")
    val SubscriptionWrite = Capability("subscription.write")
    val PlaybackResolve = Capability("playback.resolve")
    val EpgRead = Capability("epg.read")
    val MetadataWrite = Capability("metadata.write")
    val SettingsContribute = Capability("settings.contribute")
    val SearchRead = Capability("search.read")
    val BackgroundTask = Capability("background.task")

    val All = setOf(
        Network,
        CredentialRead,
        CredentialWrite,
        SubscriptionRead,
        SubscriptionWrite,
        PlaybackResolve,
        EpgRead,
        MetadataWrite,
        SettingsContribute,
        SearchRead,
        BackgroundTask,
    )
}

object ExtensionContractCatalog {
    val SupportedHookSchemaVersions: Map<Hook, Set<Int>> = mapOf(
        ExtensionHookIds.SubscriptionProviderDiscover to setOf(3),
        ExtensionHookIds.SubscriptionProviderValidate to setOf(2),
        ExtensionHookIds.SubscriptionContentRefresh to setOf(4),
        ExtensionHookIds.PlaybackSourceResolve to setOf(4),
        ExtensionHookIds.PlaybackSessionClose to setOf(3),
        ExtensionHookIds.MetadataChannelEnrich to setOf(3),
        ExtensionHookIds.EpgContentRefresh to setOf(4),
        ExtensionHookIds.SettingsSchemaContribute to setOf(1),
        ExtensionHookIds.SearchProviderQuery to setOf(4),
        ExtensionHookIds.BackgroundTaskRun to setOf(2),
    )

    val SupportedCapabilities: Set<Capability> = ExtensionCapabilityIds.All

    val RequiredCapabilitiesByHook: Map<Hook, Set<Capability>> = mapOf(
        ExtensionHookIds.SubscriptionProviderValidate to setOf(
            ExtensionCapabilityIds.CredentialWrite,
        ),
        ExtensionHookIds.SubscriptionContentRefresh to setOf(
            ExtensionCapabilityIds.SubscriptionRead,
        ),
        ExtensionHookIds.PlaybackSourceResolve to setOf(
            ExtensionCapabilityIds.PlaybackResolve,
        ),
        ExtensionHookIds.PlaybackSessionClose to setOf(
            ExtensionCapabilityIds.PlaybackResolve,
        ),
        ExtensionHookIds.MetadataChannelEnrich to setOf(
            ExtensionCapabilityIds.MetadataWrite,
        ),
        ExtensionHookIds.EpgContentRefresh to setOf(
            ExtensionCapabilityIds.EpgRead,
        ),
        ExtensionHookIds.SettingsSchemaContribute to setOf(
            ExtensionCapabilityIds.SettingsContribute,
        ),
        ExtensionHookIds.SearchProviderQuery to setOf(
            ExtensionCapabilityIds.SearchRead,
        ),
        ExtensionHookIds.BackgroundTaskRun to setOf(
            ExtensionCapabilityIds.BackgroundTask,
        ),
    )
}

@Serializable
data class ExtensionCapabilityRequest(
    val capability: Capability,
    val reason: String,
    val required: Boolean = true,
) {
    init {
        require(reason.isNotBlank()) { "Capability reason must not be blank" }
    }
}

@Serializable
data class ExtensionHookDeclaration(
    val hook: Hook,
    val schemaVersion: Int = 1,
    val requiredCapabilities: Set<Capability> = emptySet(),
) {
    init {
        require(schemaVersion > 0) { "Hook schema version must be positive" }
    }
}

@Serializable
data class ExtensionBackgroundTaskDeclaration(
    val taskId: String,
    val repeatIntervalHours: Int,
    val requiresNetwork: Boolean = false,
) {
    init {
        require(
            taskId.encodeToByteArray().size <= MAX_CONTRACT_ID_UTF8_BYTES &&
                taskId.matches(CONTRACT_ID_PATTERN)
        ) {
            "Background task id must be a stable lowercase identifier"
        }
        require(repeatIntervalHours in MIN_REPEAT_INTERVAL_HOURS..MAX_REPEAT_INTERVAL_HOURS) {
            "Background task repeat interval must be between $MIN_REPEAT_INTERVAL_HOURS and " +
                "$MAX_REPEAT_INTERVAL_HOURS hours"
        }
    }

    companion object {
        const val MIN_REPEAT_INTERVAL_HOURS = 6
        const val MAX_REPEAT_INTERVAL_HOURS = 168
    }
}

@Serializable
data class ExtensionManifest(
    val id: ExtensionId,
    val displayName: String,
    val extensionVersion: ExtensionSemanticVersion,
    val apiRange: ExtensionApiRange,
    val hooks: Set<ExtensionHookDeclaration>,
    val capabilities: Set<ExtensionCapabilityRequest>,
    val settingsSchema: ExtensionSettingSchema? = null,
    val metadata: Map<String, String> = emptyMap(),
    val backgroundTasks: List<ExtensionBackgroundTaskDeclaration> = emptyList(),
    val networkOrigins: Set<ExtensionNetworkOrigin> = emptySet(),
) {
    init {
        require(displayName.isNotBlank()) { "Extension display name must not be blank" }
        require(hooks.map(ExtensionHookDeclaration::hook).toSet().size == hooks.size) {
            "Extension manifest must not declare a hook more than once"
        }
        require(capabilities.map(ExtensionCapabilityRequest::capability).toSet().size == capabilities.size) {
            "Extension manifest must not request a capability more than once"
        }
        require(backgroundTasks.size <= MAX_BACKGROUND_TASKS) {
            "Extension manifest must not declare more than $MAX_BACKGROUND_TASKS background tasks"
        }
        require(backgroundTasks.map(ExtensionBackgroundTaskDeclaration::taskId).distinct().size ==
            backgroundTasks.size) {
            "Extension manifest must not declare a background task more than once"
        }
        require(networkOrigins.size <= MAX_NETWORK_ORIGINS) {
            "Extension manifest must not declare more than $MAX_NETWORK_ORIGINS network origins"
        }
        require(networkOrigins.map(ExtensionNetworkOrigin::canonicalValue).distinct().size ==
            networkOrigins.size) {
            "Extension manifest network origins must be unique"
        }

        val requestedCapabilities = capabilities.mapTo(mutableSetOf(), ExtensionCapabilityRequest::capability)
        val undeclaredCapabilities = hooks
            .flatMapTo(mutableSetOf(), ExtensionHookDeclaration::requiredCapabilities)
            .minus(requestedCapabilities)
        require(undeclaredCapabilities.isEmpty()) {
            "Hook capabilities must also be requested by the extension manifest: $undeclaredCapabilities"
        }
        val missingHookCapabilities = hooks.mapNotNull { declaration ->
            val missing = ExtensionContractCatalog.RequiredCapabilitiesByHook[
                declaration.hook
            ].orEmpty() - declaration.requiredCapabilities
            missing.takeIf(Set<Capability>::isNotEmpty)?.let { capabilities ->
                declaration.hook to capabilities
            }
        }.toMap()
        require(missingHookCapabilities.isEmpty()) {
            "Hooks must declare their base capabilities: $missingHookCapabilities"
        }
        if (backgroundTasks.isNotEmpty()) {
            val backgroundHook = hooks.singleOrNull {
                declaration -> declaration.hook == ExtensionHookIds.BackgroundTaskRun
            }
            require(backgroundHook != null) {
                "Background task declarations require the background task Hook"
            }
            require(ExtensionCapabilityIds.BackgroundTask in backgroundHook.requiredCapabilities) {
                "The background task Hook must require the background task capability"
            }
            require(ExtensionCapabilityIds.BackgroundTask in requestedCapabilities) {
                "Background task declarations require the background task capability"
            }
            if (backgroundTasks.any(ExtensionBackgroundTaskDeclaration::requiresNetwork)) {
                require(ExtensionCapabilityIds.Network in backgroundHook.requiredCapabilities) {
                    "Network background tasks require the network capability on their Hook"
                }
                require(ExtensionCapabilityIds.Network in requestedCapabilities) {
                    "Network background tasks require the network capability"
                }
            }
        }
        if (
            networkOrigins.isNotEmpty() ||
            settingsSchema?.fields?.any(ExtensionSettingField::networkOrigin) == true
        ) {
            require(ExtensionCapabilityIds.Network in requestedCapabilities) {
                "Network origins require the network capability"
            }
        }
    }

    companion object {
        const val MAX_BACKGROUND_TASKS = 8
        const val MAX_NETWORK_ORIGINS = 16
    }
}

interface ExtensionPayload

@Serializable
data object EmptyExtensionPayload : ExtensionPayload

@Serializable
@JvmInline
value class ExtensionErrorCode(val value: String) {
    init {
        require(
            value.encodeToByteArray().size <= MAX_CONTRACT_ID_UTF8_BYTES &&
                value.matches(CONTRACT_ID_PATTERN)
        ) {
            "Extension error code must be a lowercase, dot-separated identifier"
        }
    }

    override fun toString(): String = value
}

object ExtensionErrorCodes {
    val ExtensionNotFound = ExtensionErrorCode("extension.not_found")
    val ExtensionAlreadyRegistered = ExtensionErrorCode("extension.already_registered")
    val ApiIncompatible = ExtensionErrorCode("api.incompatible")
    val HookNotDeclared = ExtensionErrorCode("hook.not_declared")
    val HookNotBound = ExtensionErrorCode("hook.not_bound")
    val CapabilityDenied = ExtensionErrorCode("capability.denied")
    val RegistrationInvalid = ExtensionErrorCode("registration.invalid")
    val InvocationFailed = ExtensionErrorCode("invocation.failed")
    val ResponseInvalid = ExtensionErrorCode("invocation.response_invalid")
    val InvocationTimedOut = ExtensionErrorCode("invocation.timed_out")
    val PayloadTooLarge = ExtensionErrorCode("invocation.payload_too_large")
    val SchemaIncompatible = ExtensionErrorCode("hook.schema_incompatible")
    val ExtensionDisabled = ExtensionErrorCode("extension.disabled")
    val ExtensionUnhealthy = ExtensionErrorCode("extension.unhealthy")
}

@Serializable
data class ExtensionError(
    val code: ExtensionErrorCode,
    val message: String,
    val recoverable: Boolean,
    val details: Map<String, String> = emptyMap(),
) {
    init {
        require(message.isNotBlank()) { "Extension error message must not be blank" }
    }
}
