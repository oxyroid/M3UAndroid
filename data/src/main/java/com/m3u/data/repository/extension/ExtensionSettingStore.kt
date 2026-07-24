package com.m3u.data.repository.extension

import android.content.Context
import com.m3u.data.extension.security.ExtensionSecretStore
import com.m3u.extension.api.ExtensionManifest
import com.m3u.extension.api.ExtensionNetworkOrigin
import com.m3u.extension.api.ExtensionSettingKeys
import com.m3u.extension.api.ExtensionSettingSchema
import com.m3u.extension.api.ExtensionSettingSection
import com.m3u.extension.api.ExtensionSettingType
import com.m3u.extension.api.ExtensionSettingsSnapshot
import com.m3u.extension.api.security.CredentialHandle
import com.m3u.extension.runtime.ExtensionSettingsProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

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
        val manifestSection = ExtensionSettingSection(
            id = MANIFEST_SECTION_ID,
            title = manifest.displayName,
            schema = schema,
        )
        rememberNetworkOriginFields(
            extensionId = manifest.id.value,
            sections = listOf(manifestSection),
            removeMissingSections = false,
        )
        rememberSchemaFingerprints(
            extensionId = manifest.id.value,
            sections = listOf(manifestSection),
            removeMissingSections = false,
        )
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
    fun snapshot(extensionId: String): ExtensionSettingsSnapshot {
        val stored = preferences
            .getString(extensionId, null)
            ?.let { encoded ->
                runCatching {
                    json.decodeFromString<ExtensionSettingsSnapshot>(encoded)
                }.getOrNull()
            }
            ?: ExtensionSettingsSnapshot()
        val validCredentialHandles = stored.credentialHandles.filterValues { handle ->
            secretStore.resolve(extensionId, handle) != null
        }
        if (validCredentialHandles.size == stored.credentialHandles.size) return stored
        return stored.copy(credentialHandles = validCredentialHandles).also { repaired ->
            save(extensionId, repaired)
        }
    }

    @Synchronized
    fun save(extensionId: String, snapshot: ExtensionSettingsSnapshot) {
        val retainedOriginApprovals = networkOriginApprovals(extensionId)
            .filter { (key, approvedOrigin) ->
                val value = (snapshot.values[key] as? JsonPrimitive)?.contentOrNull
                    ?: return@filter false
                runCatching { ExtensionNetworkOrigin(value).canonicalValue }.getOrNull() ==
                    approvedOrigin
            }
        preferences.edit()
            .putString(extensionId, json.encodeToString(snapshot))
            .putString(
                networkOriginApprovalsPreference(extensionId),
                json.encodeToString(retainedOriginApprovals),
            )
            .apply()
    }

    @Synchronized
    fun mutateIfSchema(
        extensionId: String,
        sectionId: String,
        expectedSchemaVersion: Int,
        expectedSchemaFingerprint: String,
        settingKey: String,
        approvedOrigin: String?,
        transform: (ExtensionSettingsSnapshot) -> ExtensionSettingsSnapshot,
    ): ExtensionSettingsSnapshot? {
        val previous = snapshot(extensionId)
        if (previous.schemaVersions[sectionId] != expectedSchemaVersion) return null
        if (schemaFingerprints(extensionId)[sectionId] != expectedSchemaFingerprint) return null
        val updated = transform(previous)
        if (updated != previous) save(extensionId, updated)
        updateSettingOriginApproval(extensionId, settingKey, approvedOrigin)
        return updated
    }

    @Synchronized
    fun reconcileSection(
        extensionId: String,
        sectionId: String,
        schema: ExtensionSettingSchema,
        snapshot: ExtensionSettingsSnapshot = snapshot(extensionId),
    ): ExtensionSettingsSnapshot {
        val prefix = "$sectionId/"
        if (snapshot.schemaVersions[sectionId] == schema.version) {
            val fieldsByKey = schema.fields.associateBy { field ->
                ExtensionSettingKeys.qualified(sectionId, field.key)
            }
            val removedHandles = snapshot.credentialHandles.filter { (key, _) ->
                key.startsWith(prefix) &&
                    fieldsByKey[key]?.type != ExtensionSettingType.SECRET
            }
            removedHandles.values.forEach { handle ->
                secretStore.delete(extensionId, handle)
            }
            return snapshot.copy(
                values = snapshot.values.filterKeys { key ->
                    !key.startsWith(prefix) ||
                        fieldsByKey[key]?.type?.let { type ->
                            type != ExtensionSettingType.SECRET
                        } == true
                },
                credentialHandles = snapshot.credentialHandles.filterKeys { key ->
                    !key.startsWith(prefix) ||
                        fieldsByKey[key]?.type == ExtensionSettingType.SECRET
                },
            )
        }
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
        removeMissingSections: Boolean = true,
    ): ExtensionSettingsSnapshot {
        rememberNetworkOriginFields(extensionId, sections, removeMissingSections)
        rememberSchemaFingerprints(extensionId, sections, removeMissingSections)
        val activeSectionIds = sections.mapTo(mutableSetOf(), ExtensionSettingSection::id)
        var current = snapshot(extensionId)
        if (removeMissingSections) {
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
        preferences.edit()
            .remove(extensionId)
            .remove(networkOriginKeysPreference(extensionId))
            .remove(networkOriginApprovalsPreference(extensionId))
            .remove(schemaFingerprintsPreference(extensionId))
            .apply()
    }

    internal fun schemaFingerprint(section: ExtensionSettingSection): String {
        val bytes = json.encodeToString(section).encodeToByteArray()
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return buildString(digest.size * 2) {
            digest.forEach { byte ->
                val value = byte.toInt() and 0xff
                append(HEX_DIGITS[value ushr 4])
                append(HEX_DIGITS[value and 0x0f])
            }
        }
    }

    @Synchronized
    fun resolveBrokerCredentials(
        extensionId: String,
        handles: Collection<CredentialHandle>,
    ): Map<CredentialHandle, String> = handles
        .distinct()
        .mapNotNull { handle ->
            secretStore.resolve(extensionId, handle)?.let { secret -> handle to secret }
        }
        .toMap()

    @Synchronized
    fun approvedSettingOrigins(
        extensionId: String,
        snapshot: ExtensionSettingsSnapshot,
    ): Set<String> {
        val declaredKeys = networkOriginSettingKeys(extensionId)
        return networkOriginApprovals(extensionId)
            .mapNotNullTo(linkedSetOf()) { (key, approvedOrigin) ->
                if (key !in declaredKeys) return@mapNotNullTo null
                val currentValue = (snapshot.values[key] as? JsonPrimitive)
                    ?.contentOrNull
                    ?: return@mapNotNullTo null
                val currentOrigin = runCatching {
                    ExtensionNetworkOrigin(currentValue).canonicalValue
                }.getOrNull()
                approvedOrigin.takeIf { origin -> origin == currentOrigin }
            }
    }

    private fun updateSettingOriginApproval(
        extensionId: String,
        key: String,
        origin: String?,
    ) {
        val approvals = networkOriginApprovals(extensionId).toMutableMap()
        if (origin != null && key in networkOriginSettingKeys(extensionId)) {
            approvals[key] = ExtensionNetworkOrigin(origin).canonicalValue
        } else {
            approvals.remove(key)
        }
        saveNetworkOriginApprovals(extensionId, approvals)
    }

    private fun rememberNetworkOriginFields(
        extensionId: String,
        sections: List<ExtensionSettingSection>,
        removeMissingSections: Boolean,
    ) {
        val providedSectionIds = sections.mapTo(mutableSetOf(), ExtensionSettingSection::id)
        val previous = networkOriginSettingKeys(extensionId)
        val retained = if (removeMissingSections) {
            emptySet()
        } else {
            previous.filterTo(mutableSetOf()) { key ->
                providedSectionIds.none { sectionId -> key.startsWith("$sectionId/") }
            }
        }
        val declared = sections.flatMapTo(mutableSetOf()) { section ->
            section.schema.fields.mapNotNull { field ->
                ExtensionSettingKeys.qualified(section.id, field.key)
                    .takeIf { field.networkOrigin }
            }
        }
        val activeKeys = retained + declared
        val retainedApprovals = networkOriginApprovals(extensionId)
            .filterKeys { key -> key in activeKeys }
        preferences.edit()
            .putStringSet(networkOriginKeysPreference(extensionId), activeKeys)
            .putString(
                networkOriginApprovalsPreference(extensionId),
                json.encodeToString(retainedApprovals),
            )
            .apply()
    }

    private fun rememberSchemaFingerprints(
        extensionId: String,
        sections: List<ExtensionSettingSection>,
        removeMissingSections: Boolean,
    ) {
        val providedSectionIds = sections.mapTo(mutableSetOf(), ExtensionSettingSection::id)
        val retained = if (removeMissingSections) {
            emptyMap()
        } else {
            schemaFingerprints(extensionId).filterKeys { sectionId ->
                sectionId !in providedSectionIds
            }
        }
        val current = sections.associate { section ->
            section.id to schemaFingerprint(section)
        }
        preferences.edit()
            .putString(
                schemaFingerprintsPreference(extensionId),
                json.encodeToString(retained + current),
            )
            .apply()
    }

    private fun schemaFingerprints(extensionId: String): Map<String, String> =
        preferences.getString(schemaFingerprintsPreference(extensionId), null)
            ?.let { encoded ->
                runCatching { json.decodeFromString<Map<String, String>>(encoded) }.getOrNull()
            }
            .orEmpty()

    private fun schemaFingerprintsPreference(extensionId: String): String =
        "$SCHEMA_FINGERPRINTS_PREFIX$extensionId"

    private fun networkOriginSettingKeys(extensionId: String): Set<String> =
        preferences.getStringSet(
            networkOriginKeysPreference(extensionId),
            emptySet(),
        ).orEmpty().toSet()

    private fun networkOriginKeysPreference(extensionId: String): String =
        "$NETWORK_ORIGIN_KEYS_PREFIX$extensionId"

    private fun networkOriginApprovals(extensionId: String): Map<String, String> =
        preferences.getString(networkOriginApprovalsPreference(extensionId), null)
            ?.let { encoded ->
                runCatching { json.decodeFromString<Map<String, String>>(encoded) }.getOrNull()
            }
            .orEmpty()

    private fun saveNetworkOriginApprovals(
        extensionId: String,
        approvals: Map<String, String>,
    ) {
        preferences.edit()
            .putString(
                networkOriginApprovalsPreference(extensionId),
                json.encodeToString(approvals),
            )
            .apply()
    }

    private fun networkOriginApprovalsPreference(extensionId: String): String =
        "$NETWORK_ORIGIN_APPROVALS_PREFIX$extensionId"

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
        private const val NETWORK_ORIGIN_KEYS_PREFIX = "network-origin-keys:"
        private const val NETWORK_ORIGIN_APPROVALS_PREFIX = "network-origin-approvals:"
        private const val SCHEMA_FINGERPRINTS_PREFIX = "schema-fingerprints:"
        private const val HEX_DIGITS = "0123456789abcdef"
    }
}
