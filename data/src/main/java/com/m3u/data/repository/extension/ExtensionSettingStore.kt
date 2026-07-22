package com.m3u.data.repository.extension

import android.content.Context
import com.m3u.data.extension.security.ExtensionSecretStore
import com.m3u.extension.api.ExtensionManifest
import com.m3u.extension.api.ExtensionSettingSchema
import com.m3u.extension.api.ExtensionSettingKeys
import com.m3u.extension.api.ExtensionSettingSection
import com.m3u.extension.api.ExtensionSettingsSnapshot
import com.m3u.extension.runtime.ExtensionSettingsProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Singleton
internal class ExtensionSettingStore @Inject constructor(
    @ApplicationContext context: Context,
    private val secretStore: ExtensionSecretStore,
) : ExtensionSettingsProvider {
    private val preferences = context.getSharedPreferences(
        EXTENSION_SETTINGS_PREFERENCES,
        Context.MODE_PRIVATE,
    )
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    @Synchronized
    override fun snapshot(manifest: ExtensionManifest): ExtensionSettingsSnapshot {
        val stored = snapshot(manifest.id.value)
        val schema = manifest.settingsSchema ?: return stored
        return reconcileSection(
            extensionId = manifest.id.value,
            sectionId = MANIFEST_SECTION_ID,
            schema = schema,
            snapshot = stored,
        ).withDefaults(MANIFEST_SECTION_ID, schema).also { reconciled ->
            if (reconciled != stored) save(manifest.id.value, reconciled)
        }
    }

    @Synchronized
    fun snapshot(extensionId: String): ExtensionSettingsSnapshot = preferences
        .getString(extensionId, null)
        ?.let { encoded ->
            runCatching { json.decodeFromString<ExtensionSettingsSnapshot>(encoded) }.getOrNull()
        }
        ?: ExtensionSettingsSnapshot()

    @Synchronized
    fun save(extensionId: String, snapshot: ExtensionSettingsSnapshot) {
        preferences.edit().putString(extensionId, json.encodeToString(snapshot)).apply()
    }

    @Synchronized
    fun reconcileSection(
        extensionId: String,
        sectionId: String,
        schema: ExtensionSettingSchema,
        snapshot: ExtensionSettingsSnapshot = snapshot(extensionId),
    ): ExtensionSettingsSnapshot {
        if (snapshot.schemaVersions[sectionId] == schema.version) return snapshot
        val prefix = "$sectionId/"
        snapshot.credentialHandles
            .filterKeys { key -> key.startsWith(prefix) }
            .values
            .forEach { handle -> secretStore.delete(extensionId, handle) }
        return snapshot.copy(
            schemaVersions = snapshot.schemaVersions + (sectionId to schema.version),
            values = snapshot.values.filterKeys { key -> !key.startsWith(prefix) },
            credentialHandles = snapshot.credentialHandles.filterKeys { key ->
                !key.startsWith(prefix)
            },
        )
    }

    @Synchronized
    fun reconcile(
        extensionId: String,
        sections: List<ExtensionSettingSection>,
    ): ExtensionSettingsSnapshot {
        val activeSectionIds = sections.mapTo(mutableSetOf(), ExtensionSettingSection::id)
        var current = snapshot(extensionId)
        val removedSections = current.schemaVersions.keys - activeSectionIds
        removedSections.forEach { sectionId ->
            val prefix = "$sectionId/"
            current.credentialHandles
                .filterKeys { key -> key.startsWith(prefix) }
                .values
                .forEach { handle -> secretStore.delete(extensionId, handle) }
            current = current.copy(
                schemaVersions = current.schemaVersions - sectionId,
                values = current.values.filterKeys { key -> !key.startsWith(prefix) },
                credentialHandles = current.credentialHandles.filterKeys { key ->
                    !key.startsWith(prefix)
                },
            )
        }
        sections.forEach { section ->
            current = reconcileSection(extensionId, section.id, section.schema, current)
                .withDefaults(section.id, section.schema)
        }
        save(extensionId, current)
        return current
    }

    @Synchronized
    fun clear(extensionId: String) {
        secretStore.clear(extensionId)
        preferences.edit().remove(extensionId).apply()
    }

    private fun ExtensionSettingsSnapshot.withDefaults(
        sectionId: String,
        schema: ExtensionSettingSchema,
    ): ExtensionSettingsSnapshot {
        val defaults = schema.fields.mapNotNull { field ->
            val key = ExtensionSettingKeys.qualified(sectionId, field.key)
            field.defaultValue?.takeIf {
                key !in values && key !in credentialHandles
            }?.let { value -> key to value }
        }.toMap()
        return if (defaults.isEmpty()) this else copy(values = values + defaults)
    }

    companion object {
        const val MANIFEST_SECTION_ID = "manifest"
        private const val EXTENSION_SETTINGS_PREFERENCES = "extension-settings"
    }
}
