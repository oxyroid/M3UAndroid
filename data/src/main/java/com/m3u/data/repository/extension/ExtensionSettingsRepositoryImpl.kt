package com.m3u.data.repository.extension

import com.m3u.data.extension.security.ExtensionSecretStore
import com.m3u.extension.api.ExtensionId
import com.m3u.extension.api.ExtensionSettingField
import com.m3u.extension.api.ExtensionSettingKeys
import com.m3u.extension.api.ExtensionSettingSection
import com.m3u.extension.api.ExtensionSettingType
import com.m3u.extension.api.ExtensionState
import com.m3u.extension.api.HookResult
import com.m3u.extension.api.HostHookSpecs
import com.m3u.extension.api.SettingsSchemaRequest
import com.m3u.extension.runtime.ExtensionRuntime
import javax.inject.Inject
import kotlinx.serialization.json.JsonPrimitive

internal class ExtensionSettingsRepositoryImpl @Inject constructor(
    private val runtime: ExtensionRuntime,
    private val store: ExtensionSettingStore,
    private val secretStore: ExtensionSecretStore,
) : ExtensionSettingsRepository {
    override suspend fun configuration(
        extensionId: ExtensionId,
        localeTag: String?,
        surface: String,
    ): ExtensionSettingsConfiguration? {
        require(surface.isNotBlank()) { "Settings surface must not be blank" }
        val extension = runtime.registeredExtensions()
            .singleOrNull { candidate -> candidate.manifest.id == extensionId }
            ?: return null
        if (extension.state != ExtensionState.ENABLED) return null

        val sections = buildList {
            extension.manifest.settingsSchema?.let { schema ->
                add(
                    ExtensionSettingSection(
                        id = ExtensionSettingStore.MANIFEST_SECTION_ID,
                        title = extension.manifest.displayName,
                        schema = schema,
                    )
                )
            }
            if (extension.boundHooks.contains(HostHookSpecs.SettingsSchema.hook)) {
                when (
                    val outcome = runtime.invoke(
                        extensionId,
                        HostHookSpecs.SettingsSchema,
                        SettingsSchemaRequest(localeTag = localeTag, surface = surface),
                    ).outcome
                ) {
                    is HookResult.Success -> addAll(outcome.payload.sections)
                    is HookResult.Failure -> Unit
                }
            }
        }
            .asSequence()
            .filter { section -> section.schema.fields.size <= MAX_FIELDS_PER_SECTION }
            .distinctBy(ExtensionSettingSection::id)
            .take(MAX_SECTIONS)
            .toList()
        val snapshot = store.reconcile(extensionId.value, sections)
        return ExtensionSettingsConfiguration(extensionId, sections, snapshot)
    }

    override suspend fun update(
        extensionId: ExtensionId,
        sectionId: String,
        fieldKey: String,
        rawValue: String?,
        localeTag: String?,
        surface: String,
    ): ExtensionSettingUpdateResult {
        val configuration = configuration(extensionId, localeTag, surface)
            ?: return ExtensionSettingUpdateResult.Rejected("Extension settings are unavailable")
        val section = configuration.sections.singleOrNull { candidate -> candidate.id == sectionId }
            ?: return ExtensionSettingUpdateResult.Rejected("Settings section was not found")
        val field = section.schema.fields.singleOrNull { candidate -> candidate.key == fieldKey }
            ?: return ExtensionSettingUpdateResult.Rejected("Setting field was not found")
        val key = ExtensionSettingKeys.qualified(sectionId, fieldKey)
        val previous = configuration.snapshot

        if (rawValue == null) {
            previous.credentialHandles[key]?.let { handle ->
                secretStore.delete(extensionId.value, handle)
            }
            val cleared = previous.copy(
                values = previous.values - key,
                credentialHandles = previous.credentialHandles - key,
            )
            store.save(extensionId.value, cleared)
            return ExtensionSettingUpdateResult.Updated(cleared)
        }
        if (rawValue.length > MAX_SETTING_VALUE_LENGTH) {
            return ExtensionSettingUpdateResult.Rejected("Setting value exceeds the host limit")
        }
        if (field.required && rawValue.isBlank()) {
            return ExtensionSettingUpdateResult.Rejected("Required setting must not be blank")
        }

        val updated = if (field.type == ExtensionSettingType.SECRET) {
            val handle = secretStore.store(
                extensionId = extensionId.value,
                settingKey = key,
                secret = rawValue,
                existingHandle = previous.credentialHandles[key],
            )
            previous.copy(
                values = previous.values - key,
                credentialHandles = previous.credentialHandles + (key to handle),
            )
        } else {
            val value = field.parse(rawValue)
                ?: return ExtensionSettingUpdateResult.Rejected(
                    "Setting value does not match the declared type"
                )
            previous.credentialHandles[key]?.let { handle ->
                secretStore.delete(extensionId.value, handle)
            }
            previous.copy(
                values = previous.values + (key to value),
                credentialHandles = previous.credentialHandles - key,
            )
        }
        store.save(extensionId.value, updated)
        return ExtensionSettingUpdateResult.Updated(updated)
    }

    override fun clear(extensionId: ExtensionId) {
        store.clear(extensionId.value)
    }

    private fun ExtensionSettingField.parse(rawValue: String): JsonPrimitive? = when (type) {
        ExtensionSettingType.TEXT -> JsonPrimitive(rawValue)
        ExtensionSettingType.SECRET -> null
        ExtensionSettingType.BOOLEAN -> rawValue.toBooleanStrictOrNull()?.let(::JsonPrimitive)
        ExtensionSettingType.NUMBER -> rawValue.toDoubleOrNull()?.let(::JsonPrimitive)
        ExtensionSettingType.SINGLE_CHOICE -> rawValue
            .takeIf { value -> choices.any { choice -> choice.value == value } }
            ?.let(::JsonPrimitive)
    }

    private companion object {
        const val MAX_SECTIONS = 20
        const val MAX_FIELDS_PER_SECTION = 100
        const val MAX_SETTING_VALUE_LENGTH = 16_384
    }
}
