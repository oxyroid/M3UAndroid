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
import com.m3u.extension.api.Hook
import com.m3u.extension.api.HostHookSpecs
import com.m3u.extension.api.security.CredentialHandle
import com.m3u.extension.runtime.ExtensionRegistrationLease
import com.m3u.extension.runtime.ExtensionSettingsProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

private const val DYNAMIC_SCHEMA_REGISTRY_VERSION = 2

internal class DynamicSchemaSession internal constructor(
    internal val extensionId: String,
    internal val epoch: Long,
    internal val registrationLease: ExtensionRegistrationLease,
) {
    override fun toString(): String = "DynamicSchemaSession(opaque)"
}

internal class DynamicSchemaValidation internal constructor(
    internal val session: DynamicSchemaSession,
    internal val surface: String,
    internal val attempt: Long,
) {
    override fun toString(): String = "DynamicSchemaValidation(opaque)"
}

internal enum class ExtensionSettingOriginReviewState {
    NOT_CONFIGURED,
    INVALID,
    REQUIRES_APPROVAL,
    APPROVED,
    SUSPENDED,
    UNVERIFIED,
}

internal data class ExtensionSettingOriginReview(
    val sectionId: String,
    val fieldKey: String,
    val label: String? = null,
    val currentOrigin: String?,
    val state: ExtensionSettingOriginReviewState,
)

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
    private val dynamicSchemaEpochs = mutableMapOf<String, Long>()
    private val activeDynamicSchemaSessions =
        mutableMapOf<String, ActiveDynamicSchemaSession>()

    @Synchronized
    override fun snapshot(manifest: ExtensionManifest): ExtensionSettingsSnapshot {
        return snapshotInternal(manifest)
    }

    @Synchronized
    override fun snapshot(
        manifest: ExtensionManifest,
        hook: Hook,
    ): ExtensionSettingsSnapshot {
        val snapshot = snapshotInternal(manifest)
        if (hook != HostHookSpecs.SettingsSchema.hook) return snapshot
        return snapshot.copy(
            schemaVersions = snapshot.schemaVersions.filterKeys { sectionId ->
                sectionId == MANIFEST_SECTION_ID
            },
            values = snapshot.values.filterKeys { key ->
                key.startsWith("$MANIFEST_SECTION_ID/")
            },
            credentialHandles = snapshot.credentialHandles.filterKeys { key ->
                key.startsWith("$MANIFEST_SECTION_ID/")
            },
        )
    }

    @Synchronized
    fun snapshot(
        manifest: ExtensionManifest,
        expectedSession: DynamicSchemaSession,
    ): ExtensionSettingsSnapshot? {
        if (
            activeDynamicSchemaSessions[manifest.id.value]?.session !== expectedSession
        ) {
            return null
        }
        return snapshotInternal(manifest)
    }

    private fun snapshotInternal(manifest: ExtensionManifest): ExtensionSettingsSnapshot {
        val extensionId = manifest.id.value
        var stored = storedSnapshot(extensionId)
        val manifestSection = manifest.settingsSchema?.let { schema ->
            ExtensionSettingSection(
                id = MANIFEST_SECTION_ID,
                title = manifest.displayName,
                schema = schema,
            )
        }
        if (manifestSection != null) {
            rememberNetworkOriginFields(
                extensionId = extensionId,
                sections = listOf(manifestSection),
                removeMissingSections = false,
            )
            rememberSchemaFingerprints(
                extensionId = extensionId,
                sections = listOf(manifestSection),
                removeMissingSections = false,
            )
            stored = reconcileSection(
                extensionId = extensionId,
                sectionId = MANIFEST_SECTION_ID,
                schema = manifestSection.schema,
                snapshot = stored,
            ).withDefaults(MANIFEST_SECTION_ID, manifestSection.schema).also { reconciled ->
                if (reconciled != stored) save(extensionId, reconciled)
            }
        }
        return runtimeSnapshot(
            extensionId = extensionId,
            manifestSection = manifestSection,
            stored = stored,
        )
    }

    @Synchronized
    fun snapshot(extensionId: String): ExtensionSettingsSnapshot {
        val stored = storedSnapshot(extensionId)
        val validCredentialHandles = stored.credentialHandles.filterValues { handle ->
            secretStore.resolve(extensionId, handle) != null
        }
        if (validCredentialHandles.size == stored.credentialHandles.size) return stored
        return stored.copy(credentialHandles = validCredentialHandles).also { repaired ->
            save(extensionId, repaired)
        }
    }

    private fun storedSnapshot(extensionId: String): ExtensionSettingsSnapshot =
        preferences
            .getString(extensionId, null)
            ?.let { encoded ->
                runCatching {
                    json.decodeFromString<ExtensionSettingsSnapshot>(encoded)
                }.getOrNull()
            }
            ?: ExtensionSettingsSnapshot()

    @Synchronized
    fun save(extensionId: String, snapshot: ExtensionSettingsSnapshot) {
        val retainedOriginApprovals = retainedNetworkOriginApprovals(
            extensionId = extensionId,
            snapshot = snapshot,
        )
        preferences.edit()
            .putString(extensionId, json.encodeToString(snapshot))
            .putString(
                networkOriginApprovalsPreference(extensionId),
                json.encodeToString(retainedOriginApprovals),
            )
            .apply()
    }

    private fun retainedNetworkOriginApprovals(
        extensionId: String,
        snapshot: ExtensionSettingsSnapshot,
        allowedKeys: Set<String>? = null,
    ): Map<String, String> = networkOriginApprovals(extensionId)
        .filter { (key, approvedOrigin) ->
            (allowedKeys == null || key in allowedKeys) &&
                (snapshot.values[key] as? JsonPrimitive)
                    ?.contentOrNull
                    ?.let { value ->
                        runCatching {
                            ExtensionNetworkOrigin(value).canonicalValue
                        }.getOrNull()
                    } == approvedOrigin
        }

    @Synchronized
    fun beginDynamicSchemaSession(
        extensionId: String,
        registrationLease: ExtensionRegistrationLease,
    ): DynamicSchemaSession {
        val epoch = nextDynamicSchemaEpoch(extensionId)
        dynamicSchemaEpochs[extensionId] = epoch
        val session = DynamicSchemaSession(extensionId, epoch, registrationLease)
        activeDynamicSchemaSessions[extensionId] = ActiveDynamicSchemaSession(session)
        return session
    }

    @Synchronized
    fun hasActiveDynamicSchemaSession(
        extensionId: String,
        registrationLease: ExtensionRegistrationLease,
    ): Boolean = activeDynamicSchemaSessions[extensionId]
        ?.session
        ?.registrationLease === registrationLease

    @Synchronized
    fun currentDynamicSchemaSession(
        extensionId: String,
        registrationLease: ExtensionRegistrationLease,
    ): DynamicSchemaSession? = activeDynamicSchemaSessions[extensionId]
        ?.session
        ?.takeIf { session -> session.registrationLease === registrationLease }

    @Synchronized
    fun suspendDynamicSchemas(extensionId: String) {
        dynamicSchemaEpochs[extensionId] = nextDynamicSchemaEpoch(extensionId)
        activeDynamicSchemaSessions.remove(extensionId)
    }

    @Synchronized
    fun suspendDynamicSchemas(session: DynamicSchemaSession): Boolean {
        if (activeDynamicSchemaSessions[session.extensionId]?.session !== session) return false
        suspendDynamicSchemas(session.extensionId)
        return true
    }

    @Synchronized
    fun beginDynamicSchemaValidation(
        session: DynamicSchemaSession,
        surface: String,
    ): DynamicSchemaValidation? {
        require(surface.isValidDynamicSchemaSurface()) {
            "Dynamic settings surface is invalid"
        }
        val extensionId = session.extensionId
        val active = activeDynamicSchemaSessions[extensionId] ?: return null
        if (
            active.session !== session ||
            active.session.registrationLease !== session.registrationLease
        ) {
            return null
        }
        val registry = dynamicSchemaRegistry(extensionId)
        if (surface !in registry.surfaces) {
            if (registry.surfaces.size >= MAX_DYNAMIC_SCHEMA_SURFACES) return null
            preferences.edit()
                .putString(
                    dynamicSchemaRegistryPreference(extensionId),
                    json.encodeToString(
                        registry.copy(
                            surfaces = registry.surfaces +
                                (surface to PersistedDynamicSchemaSurface()),
                        )
                    ),
                )
                .apply()
        }
        val attempt = active.attempts[surface]
            ?.takeIf { current -> current < Long.MAX_VALUE }
            ?.plus(1L)
            ?: 1L
        active.attempts[surface] = attempt
        active.verifiedSurfaces.remove(surface)
        return DynamicSchemaValidation(
            session = active.session,
            surface = surface,
            attempt = attempt,
        )
    }

    @Synchronized
    fun knownDynamicSchemaSurfaces(extensionId: String): Set<String> =
        dynamicSchemaRegistry(extensionId).surfaces.keys.toSortedSet()

    @Synchronized
    fun hasRetainedDynamicSettings(extensionId: String): Boolean {
        val stored = storedSnapshot(extensionId)
        return stored.schemaVersions.keys.any { sectionId -> sectionId != MANIFEST_SECTION_ID } ||
            stored.values.keys.any { key -> !key.startsWith("$MANIFEST_SECTION_ID/") } ||
            stored.credentialHandles.keys.any { key ->
                !key.startsWith("$MANIFEST_SECTION_ID/")
            }
    }

    @Synchronized
    fun revalidateDynamicSchemas(
        extensionId: String,
        validation: DynamicSchemaValidation,
        sections: List<ExtensionSettingSection>,
    ): Boolean {
        val active = activeDynamicSchemaSessions[extensionId] ?: return false
        if (
            validation.session.extensionId != extensionId ||
            validation.session !== active.session
        ) {
            return false
        }
        val descriptor = runCatching {
            sections.toStableDynamicSchemaSurface()
        }.getOrNull() ?: return rejectDynamicSchemaValidation(active, validation)
        val previousRegistry = dynamicSchemaRegistry(extensionId)
        val previousSurface = previousRegistry.surfaces[validation.surface]
            ?: PersistedDynamicSchemaSurface()
        val latestAttempt = active.attempts[validation.surface] ?: return false
        if (
            validation.attempt != latestAttempt &&
            previousSurface.descriptor != descriptor
        ) {
            return false
        }
        if (
            previousSurface.descriptor
                ?.hasIncompatibleSameVersionSections(descriptor) == true
        ) {
            return rejectDynamicSchemaValidation(active, validation)
        }
        val verifiedOtherDescriptors = active.verifiedSurfaces
            .asSequence()
            .filter { surface -> surface != validation.surface }
            .mapNotNull { surface ->
                previousRegistry.surfaces[surface]?.descriptor
            }
            .toList()
        if (
            verifiedOtherDescriptors.any { other ->
                other.conflictsWith(descriptor)
            }
        ) {
            return rejectDynamicSchemaValidation(active, validation)
        }
        val updatedRegistry = previousRegistry.copy(
            surfaces = previousRegistry.surfaces +
                (
                    validation.surface to previousSurface.copy(
                        descriptor = descriptor,
                    )
                ),
        )
        reconcileDynamicSchemas(
            extensionId = extensionId,
            previousRegistry = previousRegistry,
            updatedRegistry = updatedRegistry,
            validatedSections = sections,
        )
        active.verifiedSurfaces += validation.surface
        active.verifiedAttempts[validation.surface] = maxOf(
            active.verifiedAttempts[validation.surface] ?: 0L,
            validation.attempt,
        )
        return true
    }

    @Synchronized
    fun commitDynamicSchemaValidations(
        extensionId: String,
        session: DynamicSchemaSession,
        validations: Map<DynamicSchemaValidation, List<ExtensionSettingSection>>,
    ): Set<String> {
        val active = activeDynamicSchemaSessions[extensionId] ?: return emptySet()
        if (
            session.extensionId != extensionId ||
            session !== active.session
        ) {
            return emptySet()
        }
        return validations.entries
            .sortedBy { (validation, _) -> validation.surface }
            .mapNotNullTo(linkedSetOf()) { (validation, sections) ->
                validation.surface.takeIf {
                    validation.session === session &&
                        revalidateDynamicSchemas(extensionId, validation, sections)
                }
            }
    }

    @Synchronized
    fun clearDynamicSchemas(
        extensionId: String,
        session: DynamicSchemaSession,
    ): Boolean {
        if (activeDynamicSchemaSessions[extensionId]?.session !== session) return false
        val stored = storedSnapshot(extensionId)
        val removedHandles = stored.credentialHandles
            .filterKeys { key -> !key.startsWith("$MANIFEST_SECTION_ID/") }
            .values
        val cleared = stored.copy(
            schemaVersions = stored.schemaVersions.filterKeys { sectionId ->
                sectionId == MANIFEST_SECTION_ID
            },
            values = stored.values.filterKeys { key ->
                key.startsWith("$MANIFEST_SECTION_ID/")
            },
            credentialHandles = stored.credentialHandles.filterKeys { key ->
                key.startsWith("$MANIFEST_SECTION_ID/")
            },
        )
        val manifestOriginKeys = networkOriginSettingKeys(extensionId)
            .filterTo(mutableSetOf()) { key ->
                key.startsWith("$MANIFEST_SECTION_ID/")
            }
        val retainedApprovals = retainedNetworkOriginApprovals(
            extensionId = extensionId,
            snapshot = cleared,
            allowedKeys = manifestOriginKeys,
        )
        val persisted = preferences.edit()
            .putString(extensionId, json.encodeToString(cleared))
            .putStringSet(networkOriginKeysPreference(extensionId), manifestOriginKeys)
            .putString(
                networkOriginApprovalsPreference(extensionId),
                json.encodeToString(retainedApprovals),
            )
            .putString(
                schemaFingerprintsPreference(extensionId),
                json.encodeToString(
                    schemaFingerprints(extensionId).filterKeys { sectionId ->
                        sectionId == MANIFEST_SECTION_ID
                    }
                ),
            )
            .remove(dynamicSchemaRegistryPreference(extensionId))
            .commit()
        if (persisted) {
            removedHandles.forEach { handle -> secretStore.delete(extensionId, handle) }
        }
        activeDynamicSchemaSessions[extensionId]?.let { active ->
            active.attempts.clear()
            active.verifiedAttempts.clear()
            active.verifiedSurfaces.clear()
        }
        return true
    }

    @Synchronized
    fun mutateIfSchema(
        extensionId: String,
        expectedSession: DynamicSchemaSession,
        sectionId: String,
        expectedSchemaVersion: Int,
        expectedSchemaFingerprint: String,
        settingKey: String,
        approvedOrigin: String?,
        transform: (ExtensionSettingsSnapshot) -> ExtensionSettingsSnapshot,
    ): ExtensionSettingsSnapshot? {
        if (activeDynamicSchemaSessions[extensionId]?.session !== expectedSession) return null
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
        val registrationLease = activeDynamicSchemaSessions[extensionId]
            ?.session
            ?.registrationLease
        if (registrationLease == null) {
            suspendDynamicSchemas(extensionId)
        } else {
            beginDynamicSchemaSession(extensionId, registrationLease)
        }
        secretStore.clear(extensionId)
        preferences.edit()
            .remove(extensionId)
            .remove(networkOriginKeysPreference(extensionId))
            .remove(networkOriginApprovalsPreference(extensionId))
            .remove(schemaFingerprintsPreference(extensionId))
            .remove(dynamicSchemaRegistryPreference(extensionId))
            .apply()
    }

    private fun nextDynamicSchemaEpoch(extensionId: String): Long =
        dynamicSchemaEpochs[extensionId]
            ?.takeIf { current -> current < Long.MAX_VALUE }
            ?.plus(1L)
            ?: 1L

    internal fun stableSchemaFingerprint(section: ExtensionSettingSection): String {
        val bytes = json.encodeToString(section.toStableDescriptor()).encodeToByteArray()
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
        manifest: ExtensionManifest,
        candidates: Map<String, CredentialHandle>,
    ): Map<CredentialHandle, String> {
        val extensionId = manifest.id.value
        val currentHandles = runtimeSnapshot(
            extensionId = extensionId,
            manifestSection = manifest.settingsSchema?.let { schema ->
                ExtensionSettingSection(
                    id = MANIFEST_SECTION_ID,
                    title = manifest.displayName,
                    schema = schema,
                )
            },
            stored = storedSnapshot(extensionId),
        ).credentialHandles
        return candidates
            .mapNotNull { (key, handle) ->
                handle.takeIf { currentHandles[key] == handle }
            }
        .distinct()
        .mapNotNull { handle ->
            secretStore.resolve(extensionId, handle)?.let { secret -> handle to secret }
        }
        .toMap()
    }

    @Synchronized
    fun approvedSettingOrigins(
        extensionId: String,
        snapshot: ExtensionSettingsSnapshot,
    ): Set<String> = settingOriginReview(extensionId, snapshot)
        .mapNotNullTo(linkedSetOf()) { review ->
            review.currentOrigin.takeIf {
                review.state == ExtensionSettingOriginReviewState.APPROVED
            }
        }

    @Synchronized
    fun settingOriginReview(
        extensionId: String,
        snapshot: ExtensionSettingsSnapshot = storedSnapshot(extensionId),
    ): List<ExtensionSettingOriginReview> {
        val registry = dynamicSchemaRegistry(extensionId)
        val active = activeDynamicSchemaSessions[extensionId]
        val allDynamicOriginKeys = registry.surfaces.values
            .mapNotNull(PersistedDynamicSchemaSurface::descriptor)
            .flatMapTo(mutableSetOf()) { surface ->
                surface.sections.flatMap { section ->
                    section.fields.mapNotNull { field ->
                        ExtensionSettingKeys.qualified(section.id, field.key)
                            .takeIf { field.networkOrigin }
                    }
                }
            }
        val verifiedDynamicOriginKeys = active
            ?.verifiedSurfaces
            .orEmpty()
            .mapNotNull { surface -> registry.surfaces[surface]?.descriptor }
            .flatMapTo(mutableSetOf()) { surface ->
                surface.sections.flatMap { section ->
                    section.fields.mapNotNull { field ->
                        ExtensionSettingKeys.qualified(section.id, field.key)
                            .takeIf { field.networkOrigin }
                    }
                }
            }
        val approvals = networkOriginApprovals(extensionId)
        return networkOriginSettingKeys(extensionId)
            .sorted()
            .mapNotNull { qualifiedKey ->
                val separator = qualifiedKey.indexOf('/')
                if (separator <= 0 || separator == qualifiedKey.lastIndex) {
                    return@mapNotNull null
                }
                val sectionId = qualifiedKey.substring(0, separator)
                val fieldKey = qualifiedKey.substring(separator + 1)
                val currentValue = (snapshot.values[qualifiedKey] as? JsonPrimitive)
                    ?.contentOrNull
                val currentOrigin = currentValue?.let { value ->
                    runCatching { ExtensionNetworkOrigin(value).canonicalValue }.getOrNull()
                }
                val accessState = when {
                    sectionId == MANIFEST_SECTION_ID -> null
                    qualifiedKey in verifiedDynamicOriginKeys -> null
                    qualifiedKey in allDynamicOriginKeys ->
                        ExtensionSettingOriginReviewState.SUSPENDED
                    else -> ExtensionSettingOriginReviewState.UNVERIFIED
                }
                ExtensionSettingOriginReview(
                    sectionId = sectionId,
                    fieldKey = fieldKey,
                    currentOrigin = currentOrigin,
                    state = accessState ?: when {
                        currentValue == null ->
                            ExtensionSettingOriginReviewState.NOT_CONFIGURED
                        currentOrigin == null ->
                            ExtensionSettingOriginReviewState.INVALID
                        approvals[qualifiedKey] == currentOrigin ->
                            ExtensionSettingOriginReviewState.APPROVED
                        else -> ExtensionSettingOriginReviewState.REQUIRES_APPROVAL
                    },
                )
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
            section.id to stableSchemaFingerprint(section)
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

    private fun runtimeSnapshot(
        extensionId: String,
        manifestSection: ExtensionSettingSection?,
        stored: ExtensionSettingsSnapshot,
    ): ExtensionSettingsSnapshot {
        val allowedFields = linkedMapOf<String, ExtensionSettingType>()
        val allowedSchemaVersions = linkedMapOf<String, Int>()
        manifestSection?.let { section ->
            allowedSchemaVersions[section.id] = section.schema.version
            section.schema.fields.forEach { field ->
                allowedFields[ExtensionSettingKeys.qualified(section.id, field.key)] = field.type
            }
        }
        val registry = dynamicSchemaRegistry(extensionId)
        val active = activeDynamicSchemaSessions[extensionId]
        val verifiedSections = active
            ?.verifiedSurfaces
            .orEmpty()
            .mapNotNull { surface -> registry.surfaces[surface]?.descriptor }
            .flatMap(StableDynamicSchemaSurface::sections)
            .groupBy(StableDynamicSchemaSection::id)
            .mapNotNull { (_, candidates) ->
                candidates.firstOrNull()?.takeIf { candidate ->
                    candidates.all { other -> other == candidate }
                }
            }
        verifiedSections.forEach { section ->
            if (stored.schemaVersions[section.id] != section.version) return@forEach
            allowedSchemaVersions[section.id] = section.version
            section.fields.forEach { field ->
                allowedFields[
                    ExtensionSettingKeys.qualified(section.id, field.key)
                ] = field.type
            }
        }
        val credentialHandles = stored.credentialHandles.filter { (key, handle) ->
            allowedFields[key] == ExtensionSettingType.SECRET &&
                secretStore.resolve(extensionId, handle) != null
        }
        return stored.copy(
            schemaVersions = stored.schemaVersions.filter { (sectionId, version) ->
                allowedSchemaVersions[sectionId] == version
            },
            values = stored.values.filterKeys { key ->
                allowedFields[key]?.let { type -> type != ExtensionSettingType.SECRET } == true
            },
            credentialHandles = credentialHandles,
        )
    }

    private fun rejectDynamicSchemaValidation(
        active: ActiveDynamicSchemaSession,
        validation: DynamicSchemaValidation,
    ): Boolean {
        val verifiedAttempt = active.verifiedAttempts[validation.surface] ?: 0L
        if (validation.attempt >= verifiedAttempt) {
            active.verifiedSurfaces.remove(validation.surface)
        }
        return false
    }

    private fun reconcileDynamicSchemas(
        extensionId: String,
        previousRegistry: PersistedDynamicSchemaRegistry,
        updatedRegistry: PersistedDynamicSchemaRegistry,
        validatedSections: List<ExtensionSettingSection>,
    ) {
        val previousSectionIds = previousRegistry.allSectionDescriptors()
            .mapTo(mutableSetOf(), StableDynamicSchemaSection::id)
        val updatedDescriptors = updatedRegistry.allSectionDescriptors()
        val updatedSectionIds = updatedDescriptors
            .mapTo(mutableSetOf(), StableDynamicSchemaSection::id)
        val touchedSectionIds = previousSectionIds + updatedSectionIds
        val consensusDescriptors = updatedDescriptors
            .groupBy(StableDynamicSchemaSection::id)
            .mapNotNull { (_, candidates) ->
                candidates.firstOrNull()?.takeIf { candidate ->
                    candidates.all { other -> other == candidate }
                }
            }
            .associateBy(StableDynamicSchemaSection::id)
        var current = storedSnapshot(extensionId)
        val removedHandles = linkedSetOf<CredentialHandle>()
        (previousSectionIds - updatedSectionIds).forEach { sectionId ->
            val prefix = "$sectionId/"
            removedHandles += current.credentialHandles
                .filterKeys { key -> key.startsWith(prefix) }
                .values
            current = current.copy(
                schemaVersions = current.schemaVersions - sectionId,
                values = current.values.filterKeys { key -> !key.startsWith(prefix) },
                credentialHandles = current.credentialHandles.filterKeys { key ->
                    !key.startsWith(prefix)
                },
            )
        }
        consensusDescriptors.values.forEach { descriptor ->
            val prefix = "${descriptor.id}/"
            val fieldsByKey = descriptor.fields.associateBy { field ->
                ExtensionSettingKeys.qualified(descriptor.id, field.key)
            }
            if (current.schemaVersions[descriptor.id] != descriptor.version) {
                removedHandles += current.credentialHandles
                    .filterKeys { key -> key.startsWith(prefix) }
                    .values
                current = current.copy(
                    schemaVersions = current.schemaVersions +
                        (descriptor.id to descriptor.version),
                    values = current.values.filterKeys { key -> !key.startsWith(prefix) },
                    credentialHandles = current.credentialHandles.filterKeys { key ->
                        !key.startsWith(prefix)
                    },
                )
            } else {
                removedHandles += current.credentialHandles
                    .filter { (key, _) ->
                        key.startsWith(prefix) &&
                            fieldsByKey[key]?.type != ExtensionSettingType.SECRET
                    }
                    .values
                current = current.copy(
                    values = current.values.filterKeys { key ->
                        !key.startsWith(prefix) ||
                            fieldsByKey[key]?.type?.let { type ->
                                type != ExtensionSettingType.SECRET
                            } == true
                    },
                    credentialHandles = current.credentialHandles.filterKeys { key ->
                        !key.startsWith(prefix) ||
                            fieldsByKey[key]?.type == ExtensionSettingType.SECRET
                    },
                )
            }
        }
        validatedSections.forEach { section ->
            if (consensusDescriptors[section.id] == section.toStableDescriptor()) {
                current = current.withDefaults(section.id, section.schema)
            }
        }
        val retainedOriginKeys = networkOriginSettingKeys(extensionId)
            .filterTo(mutableSetOf()) { key ->
                touchedSectionIds.none { sectionId -> key.startsWith("$sectionId/") }
            }
        val dynamicOriginKeys = consensusDescriptors.values.flatMapTo(mutableSetOf()) { section ->
            section.fields.mapNotNull { field ->
                ExtensionSettingKeys.qualified(section.id, field.key)
                    .takeIf { field.networkOrigin }
            }
        }
        val activeOriginKeys = retainedOriginKeys + dynamicOriginKeys
        val retainedApprovals = retainedNetworkOriginApprovals(
            extensionId = extensionId,
            snapshot = current,
            allowedKeys = activeOriginKeys,
        )
        val persisted = preferences.edit()
            .putString(extensionId, json.encodeToString(current))
            .putStringSet(networkOriginKeysPreference(extensionId), activeOriginKeys)
            .putString(
                networkOriginApprovalsPreference(extensionId),
                json.encodeToString(retainedApprovals),
            )
            .putString(
                schemaFingerprintsPreference(extensionId),
                json.encodeToString(
                    schemaFingerprints(extensionId)
                        .filterKeys { sectionId -> sectionId !in touchedSectionIds } +
                        consensusDescriptors.mapValues { (_, descriptor) ->
                            stableSchemaFingerprint(descriptor)
                    }
                ),
            )
            .putString(
                dynamicSchemaRegistryPreference(extensionId),
                json.encodeToString(updatedRegistry),
            )
            .commit()
        if (persisted) {
            removedHandles.forEach { handle -> secretStore.delete(extensionId, handle) }
        }
    }

    private fun dynamicSchemaRegistry(extensionId: String): PersistedDynamicSchemaRegistry =
        preferences.getString(dynamicSchemaRegistryPreference(extensionId), null)
            ?.let { encoded ->
                runCatching {
                    json.decodeFromString<PersistedDynamicSchemaRegistry>(encoded)
                }.getOrNull()
            }
            ?.takeIf { registry -> registry.formatVersion == DYNAMIC_SCHEMA_REGISTRY_VERSION }
            ?: PersistedDynamicSchemaRegistry(
                formatVersion = DYNAMIC_SCHEMA_REGISTRY_VERSION,
            )

    private fun dynamicSchemaRegistryPreference(extensionId: String): String =
        "$DYNAMIC_SCHEMA_REGISTRY_PREFIX$extensionId"

    private fun stableSchemaFingerprint(descriptor: StableDynamicSchemaSection): String {
        val bytes = json.encodeToString(descriptor).encodeToByteArray()
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return buildString(digest.size * 2) {
            digest.forEach { byte ->
                val value = byte.toInt() and 0xff
                append(HEX_DIGITS[value ushr 4])
                append(HEX_DIGITS[value and 0x0f])
            }
        }
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
        private const val NETWORK_ORIGIN_KEYS_PREFIX = "network-origin-keys:"
        private const val NETWORK_ORIGIN_APPROVALS_PREFIX = "network-origin-approvals:"
        private const val SCHEMA_FINGERPRINTS_PREFIX = "schema-fingerprints:"
        private const val DYNAMIC_SCHEMA_REGISTRY_PREFIX = "dynamic-schema-registry:"
        private const val MAX_DYNAMIC_SCHEMA_SURFACES = 8
        private const val MAX_DYNAMIC_SCHEMA_SURFACE_LENGTH = 64
        private const val HEX_DIGITS = "0123456789abcdef"
    }
}

private data class ActiveDynamicSchemaSession(
    val session: DynamicSchemaSession,
    val attempts: MutableMap<String, Long> = mutableMapOf(),
    val verifiedAttempts: MutableMap<String, Long> = mutableMapOf(),
    val verifiedSurfaces: MutableSet<String> = mutableSetOf(),
)

@Serializable
private data class PersistedDynamicSchemaRegistry(
    val formatVersion: Int,
    val surfaces: Map<String, PersistedDynamicSchemaSurface> = emptyMap(),
)

@Serializable
private data class PersistedDynamicSchemaSurface(
    val descriptor: StableDynamicSchemaSurface? = null,
)

@Serializable
private data class StableDynamicSchemaSurface(
    val sections: List<StableDynamicSchemaSection>,
)

@Serializable
private data class StableDynamicSchemaSection(
    val id: String,
    val version: Int,
    val fields: List<StableDynamicSchemaField>,
)

@Serializable
private data class StableDynamicSchemaField(
    val key: String,
    val type: ExtensionSettingType,
    val required: Boolean = false,
    val choiceValues: List<String> = emptyList(),
    val defaultValue: JsonElement? = null,
    val networkOrigin: Boolean,
)

private fun List<ExtensionSettingSection>.toStableDynamicSchemaSurface():
    StableDynamicSchemaSurface {
    require(map(ExtensionSettingSection::id).distinct().size == size) {
        "Dynamic settings sections must be unique"
    }
    require(none { section -> section.id == ExtensionSettingStore.MANIFEST_SECTION_ID }) {
        "Dynamic settings cannot use the manifest section"
    }
    return StableDynamicSchemaSurface(
        sections = map(ExtensionSettingSection::toStableDescriptor)
            .sortedBy(StableDynamicSchemaSection::id),
    )
}

private fun ExtensionSettingSection.toStableDescriptor(): StableDynamicSchemaSection =
    StableDynamicSchemaSection(
        id = id,
        version = schema.version,
        fields = schema.fields
            .map { field ->
                StableDynamicSchemaField(
                    key = field.key,
                    type = field.type,
                    required = field.required,
                    choiceValues = field.choices
                        .map { choice -> choice.value }
                        .sorted(),
                    defaultValue = field.defaultValue,
                    networkOrigin = field.networkOrigin,
                )
            }
            .sortedBy(StableDynamicSchemaField::key),
    )

private fun PersistedDynamicSchemaRegistry.allSectionDescriptors():
    List<StableDynamicSchemaSection> =
    surfaces.values
        .mapNotNull(PersistedDynamicSchemaSurface::descriptor)
        .flatMap(StableDynamicSchemaSurface::sections)

private fun StableDynamicSchemaSurface.hasIncompatibleSameVersionSections(
    replacement: StableDynamicSchemaSurface,
): Boolean {
    val currentById = sections.associateBy(StableDynamicSchemaSection::id)
    return replacement.sections.any { section ->
        currentById[section.id]?.let { current ->
            current.version == section.version && current != section
        } == true
    }
}

private fun StableDynamicSchemaSurface.conflictsWith(
    other: StableDynamicSchemaSurface,
): Boolean {
    val sectionsById = sections.associateBy(StableDynamicSchemaSection::id)
    if (
        other.sections.any { section ->
            sectionsById[section.id]?.let { current -> current != section } == true
        }
    ) {
        return true
    }
    val fieldsByQualifiedKey = sections.flatMap { section ->
        section.fields.map { field ->
            ExtensionSettingKeys.qualified(section.id, field.key) to field
        }
    }.toMap()
    return other.sections.any { section ->
        section.fields.any { field ->
            fieldsByQualifiedKey[
                ExtensionSettingKeys.qualified(section.id, field.key)
            ]?.let { current -> current != field } == true
        }
    }
}

private fun String.isValidDynamicSchemaSurface(): Boolean =
    length in 1..64 &&
        first() in 'a'..'z' &&
        all { character ->
            character in 'a'..'z' ||
                character in '0'..'9' ||
                character == '.' ||
                character == '_' ||
                character == '-'
        }
