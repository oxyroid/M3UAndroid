package com.m3u.data.repository.extension

import com.m3u.extension.api.ExtensionId
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
        rawValue: String?,
        localeTag: String?,
        surface: String,
    ): ExtensionSettingUpdateResult

    fun clear(extensionId: ExtensionId)
}

data class ExtensionSettingsConfiguration(
    val extensionId: ExtensionId,
    val sections: List<ExtensionSettingSection>,
    val snapshot: ExtensionSettingsSnapshot,
)

sealed interface ExtensionSettingUpdateResult {
    data class Updated(val snapshot: ExtensionSettingsSnapshot) : ExtensionSettingUpdateResult
    data class Rejected(val reason: String) : ExtensionSettingUpdateResult
}
