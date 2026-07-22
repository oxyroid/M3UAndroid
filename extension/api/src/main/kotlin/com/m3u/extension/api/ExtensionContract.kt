package com.m3u.extension.api

private val CONTRACT_ID_PATTERN = Regex("[a-z0-9]+(?:[._-][a-z0-9]+)*")

@JvmInline
value class ExtensionId(val value: String) {
    init {
        require(value.matches(CONTRACT_ID_PATTERN)) {
            "Extension id must be a lowercase, dot-separated identifier"
        }
    }

    override fun toString(): String = value
}

@JvmInline
value class InvocationId(val value: String) {
    init {
        require(value.isNotBlank()) { "Invocation id must not be blank" }
    }

    override fun toString(): String = value
}

@JvmInline
value class Hook(val id: String) {
    init {
        require(id.matches(CONTRACT_ID_PATTERN)) {
            "Hook id must be a lowercase, dot-separated identifier"
        }
    }

    override fun toString(): String = id
}

@JvmInline
value class Capability(val id: String) {
    init {
        require(id.matches(CONTRACT_ID_PATTERN)) {
            "Capability id must be a lowercase, dot-separated identifier"
        }
    }

    override fun toString(): String = id
}

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
}

data class ExtensionCapabilityRequest(
    val capability: Capability,
    val reason: String,
    val required: Boolean = true,
) {
    init {
        require(reason.isNotBlank()) { "Capability reason must not be blank" }
    }
}

data class ExtensionHookDeclaration(
    val hook: Hook,
    val requiredCapabilities: Set<Capability> = emptySet(),
)

data class ExtensionManifest(
    val id: ExtensionId,
    val displayName: String,
    val extensionVersion: String,
    val apiRange: ExtensionApiRange,
    val hooks: Set<ExtensionHookDeclaration>,
    val capabilities: Set<ExtensionCapabilityRequest>,
) {
    init {
        require(displayName.isNotBlank()) { "Extension display name must not be blank" }
        require(extensionVersion.isNotBlank()) { "Extension version must not be blank" }
        require(hooks.map(ExtensionHookDeclaration::hook).toSet().size == hooks.size) {
            "Extension manifest must not declare a hook more than once"
        }
        require(capabilities.map(ExtensionCapabilityRequest::capability).toSet().size == capabilities.size) {
            "Extension manifest must not request a capability more than once"
        }

        val requestedCapabilities = capabilities.mapTo(mutableSetOf(), ExtensionCapabilityRequest::capability)
        val undeclaredCapabilities = hooks
            .flatMapTo(mutableSetOf(), ExtensionHookDeclaration::requiredCapabilities)
            .minus(requestedCapabilities)
        require(undeclaredCapabilities.isEmpty()) {
            "Hook capabilities must also be requested by the extension manifest: $undeclaredCapabilities"
        }
    }
}

interface ExtensionPayload

data object EmptyExtensionPayload : ExtensionPayload

data class ExtensionInvocation(
    val id: InvocationId,
    val extensionId: ExtensionId,
    val hook: Hook,
    val grantedCapabilities: Set<Capability>,
    val payload: ExtensionPayload,
)

@JvmInline
value class ExtensionErrorCode(val value: String) {
    init {
        require(value.matches(CONTRACT_ID_PATTERN)) {
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
}

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

sealed interface ExtensionHookOutcome {
    data class Success(
        val payload: ExtensionPayload = EmptyExtensionPayload,
    ) : ExtensionHookOutcome

    data class Failure(
        val error: ExtensionError,
    ) : ExtensionHookOutcome
}

data class ExtensionResult(
    val invocationId: InvocationId,
    val extensionId: ExtensionId,
    val hook: Hook,
    val outcome: ExtensionHookOutcome,
)

interface ExtensionHook {
    val hook: Hook

    suspend fun invoke(invocation: ExtensionInvocation): ExtensionHookOutcome
}

interface ExtensionEntrypoint {
    val manifest: ExtensionManifest
    val hooks: Collection<ExtensionHook>
}
