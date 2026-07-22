package com.m3u.extension.api

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class ExtensionSemanticVersion(
    val major: Int,
    val minor: Int,
    val patch: Int,
    val preRelease: String? = null,
) : Comparable<ExtensionSemanticVersion> {
    init {
        require(major >= 0 && minor >= 0 && patch >= 0) {
            "Extension version components must not be negative"
        }
        require(preRelease == null || preRelease.isNotBlank()) {
            "Pre-release version must not be blank"
        }
    }

    override fun compareTo(other: ExtensionSemanticVersion): Int = compareValuesBy(
        this,
        other,
        ExtensionSemanticVersion::major,
        ExtensionSemanticVersion::minor,
        ExtensionSemanticVersion::patch,
    )

    override fun toString(): String = buildString {
        append("$major.$minor.$patch")
        preRelease?.let { append("-$it") }
    }
}

@Serializable
enum class ExtensionState {
    ENABLED,
    DISABLED,
    INCOMPATIBLE,
    UNHEALTHY,
}

@Serializable
enum class ExtensionSettingType {
    TEXT,
    SECRET,
    BOOLEAN,
    NUMBER,
    SINGLE_CHOICE,
}

@Serializable
data class ExtensionSettingChoice(
    val value: String,
    val label: String,
)

@Serializable
data class ExtensionSettingField(
    val key: String,
    val label: String,
    val type: ExtensionSettingType,
    val required: Boolean = false,
    val description: String? = null,
    val choices: List<ExtensionSettingChoice> = emptyList(),
    val defaultValue: JsonElement? = null,
) {
    init {
        require(key.matches(Regex("[a-z][a-z0-9._-]*"))) { "Invalid setting key: $key" }
        require(label.isNotBlank()) { "Setting label must not be blank" }
        require(type == ExtensionSettingType.SINGLE_CHOICE || choices.isEmpty()) {
            "Choices are only valid for single-choice fields"
        }
    }
}

@Serializable
data class ExtensionSettingSchema(
    val version: Int,
    val fields: List<ExtensionSettingField>,
) {
    init {
        require(version > 0) { "Settings schema version must be positive" }
        require(fields.map(ExtensionSettingField::key).distinct().size == fields.size) {
            "Settings schema keys must be unique"
        }
    }
}

@Serializable
data class SerializedExtensionEnvelope(
    val apiVersion: ExtensionApiVersion,
    val invocationId: InvocationId,
    val extensionId: ExtensionId,
    val hook: Hook,
    val schemaVersion: Int,
    val payload: JsonElement,
)

@Serializable
data class SerializedExtensionResult(
    val invocationId: InvocationId,
    val extensionId: ExtensionId,
    val hook: Hook,
    val schemaVersion: Int,
    val payload: JsonElement? = null,
    val error: ExtensionError? = null,
) {
    init {
        require((payload == null) != (error == null)) {
            "Serialized result must contain exactly one payload or error"
        }
    }
}

class HookSpec<Request : ExtensionPayload, Response : ExtensionPayload>(
    val hook: Hook,
    val schemaVersion: Int,
    val requestSerializer: KSerializer<Request>,
    val responseSerializer: KSerializer<Response>,
) {
    init {
        require(schemaVersion > 0) { "Hook schema version must be positive" }
    }

    override fun equals(other: Any?): Boolean = other is HookSpec<*, *> &&
        hook == other.hook && schemaVersion == other.schemaVersion

    override fun hashCode(): Int = 31 * hook.hashCode() + schemaVersion
}

data class ExtensionCallContext(
    val invocationId: InvocationId,
    val extensionId: ExtensionId,
    val grantedCapabilities: Set<Capability>,
)

sealed interface HookResult<out Response : ExtensionPayload> {
    data class Success<Response : ExtensionPayload>(
        val payload: Response,
    ) : HookResult<Response>

    data class Failure(
        val error: ExtensionError,
    ) : HookResult<Nothing>
}

data class ExtensionResult<Response : ExtensionPayload>(
    val invocationId: InvocationId,
    val extensionId: ExtensionId,
    val spec: HookSpec<*, Response>,
    val outcome: HookResult<Response>,
)

interface ExtensionHandler<Request : ExtensionPayload, Response : ExtensionPayload> {
    val spec: HookSpec<Request, Response>

    suspend fun invoke(context: ExtensionCallContext, request: Request): HookResult<Response>
}

interface ExtensionEntrypoint {
    val manifest: ExtensionManifest
    val handlers: Collection<ExtensionHandler<*, *>>
}
