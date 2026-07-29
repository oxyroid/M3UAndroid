package com.m3u.data.repository.extension

import com.m3u.extension.api.ExtensionId
import com.m3u.extension.api.ExtensionSettingKeys
import com.m3u.extension.api.ExtensionSettingSection
import com.m3u.extension.api.ExtensionSettingsSnapshot
import com.m3u.extension.runtime.ExtensionRegistrationLease

interface ExtensionSettingsRepository {
    suspend fun configuration(
        extensionId: ExtensionId,
        localeTag: String?,
        surface: String,
    ): ExtensionSettingsConfiguration?

    suspend fun update(
        extensionId: ExtensionId,
        sectionId: String,
        fieldKey: String,
        editToken: ExtensionSettingEditToken,
        rawValue: String?,
    ): ExtensionSettingUpdateResult

    fun clear(extensionId: ExtensionId)

    fun suspendDynamicSchemas(extensionId: ExtensionId) = Unit

    fun activateDynamicSchemas(
        extensionId: ExtensionId,
        registrationLease: ExtensionRegistrationLease,
    ): Boolean = true

    fun knownDynamicSchemaSurfaces(extensionId: ExtensionId): Set<String> = emptySet()

    suspend fun revalidateDynamicSchemas(
        extensionId: ExtensionId,
        registrationLease: ExtensionRegistrationLease,
        localeTag: String?,
        surfaces: Set<String> = knownDynamicSchemaSurfaces(extensionId),
    ): ExtensionDynamicSchemaRevalidationResult =
        ExtensionDynamicSchemaRevalidationResult(
            suspendedSurfaces = surfaces,
        )
}

data class ExtensionDynamicSchemaRevalidationResult(
    val verifiedSurfaces: Set<String> = emptySet(),
    val suspendedSurfaces: Set<String> = emptySet(),
    val authoritativelyCleared: Boolean = false,
) {
    val fullyVerified: Boolean
        get() = suspendedSurfaces.isEmpty()
}

enum class ExtensionNetworkOriginState {
    NOT_CONFIGURED,
    INVALID,
    REQUIRES_APPROVAL,
    APPROVED,
    SUSPENDED,
    UNVERIFIED,
}

data class ExtensionSettingNetworkOrigin(
    val sectionId: String,
    val fieldKey: String,
    val label: String?,
    val currentOrigin: String?,
    val state: ExtensionNetworkOriginState,
) {
    val qualifiedKey: String
        get() = ExtensionSettingKeys.qualified(sectionId, fieldKey)
}

class ExtensionSettingsConfiguration internal constructor(
    val extensionId: ExtensionId,
    val sections: List<ExtensionSettingSection>,
    val snapshot: ExtensionSettingsSnapshot,
    private val editTokens: Map<String, ExtensionSettingEditToken>,
    val settingNetworkOrigins: List<ExtensionSettingNetworkOrigin> = emptyList(),
) {
    private val settingNetworkOriginsByKey =
        settingNetworkOrigins.associateBy(ExtensionSettingNetworkOrigin::qualifiedKey)

    init {
        require(settingNetworkOriginsByKey.size == settingNetworkOrigins.size) {
            "Setting network origins must be unique"
        }
    }

    fun editToken(
        sectionId: String,
        fieldKey: String,
    ): ExtensionSettingEditToken? =
        runCatching { ExtensionSettingKeys.qualified(sectionId, fieldKey) }
            .getOrNull()
            ?.let(editTokens::get)

    fun settingNetworkOrigin(
        sectionId: String,
        fieldKey: String,
    ): ExtensionSettingNetworkOrigin? =
        runCatching { ExtensionSettingKeys.qualified(sectionId, fieldKey) }
            .getOrNull()
            ?.let(settingNetworkOriginsByKey::get)

    fun settingNetworkOrigin(qualifiedKey: String): ExtensionSettingNetworkOrigin? =
        settingNetworkOriginsByKey[qualifiedKey]

    fun networkOriginState(
        sectionId: String,
        fieldKey: String,
    ): ExtensionNetworkOriginState? = settingNetworkOrigin(sectionId, fieldKey)?.state
}

class ExtensionSettingEditToken internal constructor(
    internal val value: String,
) {
    override fun equals(other: Any?): Boolean =
        other is ExtensionSettingEditToken && value == other.value

    override fun hashCode(): Int = value.hashCode()

    override fun toString(): String = "ExtensionSettingEditToken(redacted)"
}

sealed interface ExtensionSettingUpdateResult {
    data class Updated(val snapshot: ExtensionSettingsSnapshot) : ExtensionSettingUpdateResult
    data class Rejected(val reason: String) : ExtensionSettingUpdateResult
}
