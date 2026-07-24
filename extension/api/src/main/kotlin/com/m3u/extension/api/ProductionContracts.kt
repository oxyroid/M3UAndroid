package com.m3u.extension.api

import com.m3u.extension.api.security.BrokerScopeHandle
import com.m3u.extension.api.security.CredentialHandle
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull

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
        require(preRelease == null || preRelease.isValidSemanticVersionPreRelease()) {
            "Pre-release version is invalid"
        }
    }

    override fun compareTo(other: ExtensionSemanticVersion): Int {
        compareValuesBy(
            this,
            other,
            ExtensionSemanticVersion::major,
            ExtensionSemanticVersion::minor,
            ExtensionSemanticVersion::patch,
        ).takeIf { comparison -> comparison != 0 }?.let { return it }
        return compareSemanticVersionPreRelease(preRelease, other.preRelease)
    }

    override fun toString(): String = buildString {
        append("$major.$minor.$patch")
        preRelease?.let { append("-$it") }
    }
}

private fun String.isValidSemanticVersionPreRelease(): Boolean =
    isNotEmpty() &&
        length <= 128 &&
        split('.').all { identifier ->
            identifier.isNotEmpty() &&
                identifier.all { character ->
                    character in '0'..'9' ||
                        character in 'A'..'Z' ||
                        character in 'a'..'z' ||
                        character == '-'
                } &&
                (identifier.any { character -> character !in '0'..'9' } ||
                    identifier == "0" ||
                    !identifier.startsWith('0'))
        }

private fun compareSemanticVersionPreRelease(left: String?, right: String?): Int {
    if (left == right) return 0
    if (left == null) return 1
    if (right == null) return -1
    val leftParts = left.split('.')
    val rightParts = right.split('.')
    repeat(minOf(leftParts.size, rightParts.size)) { index ->
        val leftPart = leftParts[index]
        val rightPart = rightParts[index]
        if (leftPart == rightPart) return@repeat
        val leftNumeric = leftPart.all { character -> character in '0'..'9' }
        val rightNumeric = rightPart.all { character -> character in '0'..'9' }
        return when {
            leftNumeric && rightNumeric -> compareValuesBy(
                leftPart,
                rightPart,
                String::length,
                { value -> value },
            )
            leftNumeric -> -1
            rightNumeric -> 1
            else -> leftPart.compareTo(rightPart)
        }
    }
    return leftParts.size.compareTo(rightParts.size)
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
    val networkOrigin: Boolean = false,
) {
    init {
        require(key.matches(Regex("[a-z][a-z0-9._-]*"))) { "Invalid setting key: $key" }
        require(label.isNotBlank()) { "Setting label must not be blank" }
        require(type == ExtensionSettingType.SINGLE_CHOICE || choices.isEmpty()) {
            "Choices are only valid for single-choice fields"
        }
        require(type != ExtensionSettingType.SINGLE_CHOICE || choices.isNotEmpty()) {
            "Single-choice fields must declare at least one choice"
        }
        require(choices.map(ExtensionSettingChoice::value).distinct().size == choices.size) {
            "Setting choice values must be unique"
        }
        require(type != ExtensionSettingType.SECRET || defaultValue == null) {
            "Secret settings cannot declare a plaintext default"
        }
        require(defaultValue == null || defaultValue.isValidDefaultFor(type, choices)) {
            "Setting default does not match its declared type"
        }
        require(!networkOrigin || type == ExtensionSettingType.TEXT) {
            "Network origin settings must be text fields"
        }
        require(!networkOrigin || defaultValue == null) {
            "Network origin settings require an explicit user value"
        }
    }
}

private fun JsonElement.isValidDefaultFor(
    type: ExtensionSettingType,
    choices: List<ExtensionSettingChoice>,
): Boolean {
    val primitive = this as? JsonPrimitive ?: return false
    return when (type) {
        ExtensionSettingType.TEXT -> primitive.isString
        ExtensionSettingType.SECRET -> false
        ExtensionSettingType.BOOLEAN -> primitive.booleanOrNull != null
        ExtensionSettingType.NUMBER -> primitive.doubleOrNull?.isFinite() == true
        ExtensionSettingType.SINGLE_CHOICE -> primitive.contentOrNull in
            choices.map(ExtensionSettingChoice::value)
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
data class ExtensionSettingsSnapshot(
    val schemaVersions: Map<String, Int> = emptyMap(),
    val values: Map<String, JsonElement> = emptyMap(),
    val credentialHandles: Map<String, CredentialHandle> = emptyMap(),
) {
    init {
        require(schemaVersions.values.all { version -> version > 0 }) {
            "Settings schema versions must be positive"
        }
        require(values.keys.intersect(credentialHandles.keys).isEmpty()) {
            "A setting cannot contain both a literal value and a credential handle"
        }
    }
}

object ExtensionSettingKeys {
    fun qualified(sectionId: String, fieldKey: String): String {
        require(sectionId.matches(Regex("[a-z][a-z0-9._-]*"))) {
            "Invalid settings section id: $sectionId"
        }
        require(fieldKey.matches(Regex("[a-z][a-z0-9._-]*"))) {
            "Invalid setting field key: $fieldKey"
        }
        return "$sectionId/$fieldKey"
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
    val settings: ExtensionSettingsSnapshot = ExtensionSettingsSnapshot(),
    val grantedCapabilities: Set<Capability> = emptySet(),
    val brokerScope: BrokerScopeHandle? = null,
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
    val settings: ExtensionSettingsSnapshot = ExtensionSettingsSnapshot(),
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
