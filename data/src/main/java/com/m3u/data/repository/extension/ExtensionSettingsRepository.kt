package com.m3u.data.repository.extension

import com.m3u.extension.api.ExtensionId
import com.m3u.extension.api.ExtensionSettingKeys
import com.m3u.extension.api.ExtensionSettingSection
import com.m3u.extension.api.ExtensionSettingsSnapshot

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
}

class ExtensionSettingsConfiguration internal constructor(
    val extensionId: ExtensionId,
    val sections: List<ExtensionSettingSection>,
    val snapshot: ExtensionSettingsSnapshot,
    private val editTokens: Map<String, ExtensionSettingEditToken>,
) {
    fun editToken(
        sectionId: String,
        fieldKey: String,
    ): ExtensionSettingEditToken? =
        runCatching { ExtensionSettingKeys.qualified(sectionId, fieldKey) }
            .getOrNull()
            ?.let(editTokens::get)
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
