package com.m3u.data.repository.extension

import com.m3u.data.extension.security.ExtensionSecretStore
import com.m3u.extension.api.ExtensionId
import com.m3u.extension.api.ExtensionNetworkOrigin
import com.m3u.extension.api.ExtensionSettingField
import com.m3u.extension.api.ExtensionSettingKeys
import com.m3u.extension.api.ExtensionSettingSection
import com.m3u.extension.api.ExtensionSettingType
import com.m3u.extension.api.ExtensionState
import com.m3u.extension.api.HookResult
import com.m3u.extension.api.HostHookSpecs
import com.m3u.extension.api.SettingsSchemaRequest
import com.m3u.extension.runtime.ExtensionRuntime
import java.util.UUID
import javax.inject.Inject
import kotlinx.serialization.json.JsonPrimitive

internal class ExtensionSettingsRepositoryImpl @Inject constructor(
    private val runtime: ExtensionRuntime,
    private val store: ExtensionSettingStore,
    private val secretStore: ExtensionSecretStore,
) : ExtensionSettingsRepository {
    private val editTokenLock = Any()
    private val activeEditTokens = LinkedHashMap<String, EditGrant>()

    override suspend fun configuration(
        extensionId: ExtensionId,
        localeTag: String?,
        surface: String,
    ): ExtensionSettingsConfiguration? = loadConfiguration(
        extensionId = extensionId,
        localeTag = localeTag,
        surface = surface,
        issueEditTokens = true,
    )

    private suspend fun loadConfiguration(
        extensionId: ExtensionId,
        localeTag: String?,
        surface: String,
        issueEditTokens: Boolean,
    ): ExtensionSettingsConfiguration? {
        require(surface.isNotBlank()) { "Settings surface must not be blank" }
        val extension = runtime.registeredExtensions()
            .singleOrNull { candidate -> candidate.manifest.id == extensionId }
            ?: return null
        if (extension.state != ExtensionState.ENABLED) return null

        var dynamicSchemaAuthoritative =
            !extension.boundHooks.contains(HostHookSpecs.SettingsSchema.hook)
        val manifestSections = extension.manifest.settingsSchema?.let { schema ->
            listOf(
                ExtensionSettingSection(
                    id = ExtensionSettingStore.MANIFEST_SECTION_ID,
                    title = extension.manifest.displayName,
                    schema = schema,
                )
            )
        }.orEmpty()
        val dynamicSections =
            if (extension.boundHooks.contains(HostHookSpecs.SettingsSchema.hook)) {
                when (
                    val outcome = runtime.invoke(
                        extensionId,
                        HostHookSpecs.SettingsSchema,
                        SettingsSchemaRequest(localeTag = localeTag, surface = surface),
                    ).outcome
                ) {
                    is HookResult.Success -> outcome.payload.sections
                        .takeIf { sections ->
                            sections.isValidDynamicSchema(
                                existingSectionIds = manifestSections
                                    .mapTo(mutableSetOf(), ExtensionSettingSection::id),
                                maximumSectionCount = MAX_SECTIONS - manifestSections.size,
                            )
                        }
                        ?.also { dynamicSchemaAuthoritative = true }
                        .orEmpty()
                    is HookResult.Failure -> emptyList()
                }
            } else {
                emptyList()
            }
        val sections = (manifestSections + dynamicSections).hostOwnedSnapshot()
        val snapshot = store.reconcile(
            extensionId = extensionId.value,
            sections = sections,
            removeMissingSections = dynamicSchemaAuthoritative,
        )
        val editTokens = if (issueEditTokens) {
            issueEditTokens(
                extensionId = extensionId,
                sections = sections,
                localeTag = localeTag,
                surface = surface,
            )
        } else {
            emptyMap()
        }
        return ExtensionSettingsConfiguration(extensionId, sections, snapshot, editTokens)
    }

    override suspend fun update(
        extensionId: ExtensionId,
        sectionId: String,
        fieldKey: String,
        editToken: ExtensionSettingEditToken,
        rawValue: String?,
    ): ExtensionSettingUpdateResult {
        val grant = consumeEditToken(editToken)
            ?.takeIf { candidate ->
                candidate.extensionId == extensionId &&
                    candidate.sectionId == sectionId &&
                    candidate.fieldKey == fieldKey
            }
            ?: return ExtensionSettingUpdateResult.Rejected(SCHEMA_CHANGED_MESSAGE)
        val configuration = loadConfiguration(
            extensionId = extensionId,
            localeTag = grant.localeTag,
            surface = grant.surface,
            issueEditTokens = false,
        ) ?: return ExtensionSettingUpdateResult.Rejected(
            "Extension settings are unavailable"
        )
        if (configuration.sections != grant.displayedSections) {
            return ExtensionSettingUpdateResult.Rejected(SCHEMA_CHANGED_MESSAGE)
        }
        return applyUpdate(
            configuration = configuration,
            sectionId = sectionId,
            fieldKey = fieldKey,
            expectedSchemaFingerprint = grant.sectionFingerprint,
            rawValue = rawValue,
        )
    }

    private fun applyUpdate(
        configuration: ExtensionSettingsConfiguration,
        sectionId: String,
        fieldKey: String,
        expectedSchemaFingerprint: String,
        rawValue: String?,
    ): ExtensionSettingUpdateResult {
        val extensionId = configuration.extensionId
        val section = configuration.sections.singleOrNull { candidate -> candidate.id == sectionId }
            ?: return ExtensionSettingUpdateResult.Rejected("Settings section was not found")
        val field = section.schema.fields.singleOrNull { candidate -> candidate.key == fieldKey }
            ?: return ExtensionSettingUpdateResult.Rejected("Setting field was not found")
        val key = ExtensionSettingKeys.qualified(sectionId, fieldKey)

        if (rawValue == null) {
            val cleared = store.mutateIfSchema(
                extensionId = extensionId.value,
                sectionId = sectionId,
                expectedSchemaVersion = section.schema.version,
                expectedSchemaFingerprint = expectedSchemaFingerprint,
                settingKey = key,
                approvedOrigin = null,
            ) { current ->
                current.credentialHandles[key]?.let { handle ->
                    secretStore.delete(extensionId.value, handle)
                }
                current.copy(
                    values = current.values - key,
                    credentialHandles = current.credentialHandles - key,
                )
            } ?: return ExtensionSettingUpdateResult.Rejected(SCHEMA_CHANGED_MESSAGE)
            return ExtensionSettingUpdateResult.Updated(cleared)
        }
        if (rawValue.length > MAX_SETTING_VALUE_LENGTH) {
            return ExtensionSettingUpdateResult.Rejected("Setting value exceeds the host limit")
        }
        if (field.required && rawValue.isBlank()) {
            return ExtensionSettingUpdateResult.Rejected("Required setting must not be blank")
        }

        val parsedValue = when {
            field.type == ExtensionSettingType.SECRET -> null
            field.networkOrigin -> runCatching {
                JsonPrimitive(ExtensionNetworkOrigin(rawValue).canonicalValue)
            }.getOrElse {
                return ExtensionSettingUpdateResult.Rejected(
                    "Network setting must be an exact HTTP or HTTPS origin"
                )
            }
            else -> field.parse(rawValue)
                ?: return ExtensionSettingUpdateResult.Rejected(
                    "Setting value does not match the declared type"
                )
        }
        val updated = store.mutateIfSchema(
            extensionId = extensionId.value,
            sectionId = sectionId,
            expectedSchemaVersion = section.schema.version,
            expectedSchemaFingerprint = expectedSchemaFingerprint,
            settingKey = key,
            approvedOrigin = parsedValue?.content?.takeIf { field.networkOrigin },
        ) { current ->
            when (field.type) {
                ExtensionSettingType.SECRET -> {
                    val handle = secretStore.store(
                        extensionId = extensionId.value,
                        settingKey = key,
                        secret = rawValue,
                        existingHandle = current.credentialHandles[key],
                    )
                    current.copy(
                        values = current.values - key,
                        credentialHandles = current.credentialHandles + (key to handle),
                    )
                }
                else -> {
                    current.credentialHandles[key]?.let { handle ->
                        secretStore.delete(extensionId.value, handle)
                    }
                    current.copy(
                        values = current.values + (key to checkNotNull(parsedValue)),
                        credentialHandles = current.credentialHandles - key,
                    )
                }
            }
        } ?: return ExtensionSettingUpdateResult.Rejected(SCHEMA_CHANGED_MESSAGE)
        return ExtensionSettingUpdateResult.Updated(updated)
    }

    override fun clear(extensionId: ExtensionId) {
        synchronized(editTokenLock) {
            activeEditTokens.entries.removeAll { (_, grant) ->
                grant.extensionId == extensionId
            }
        }
        store.clear(extensionId.value)
    }

    private fun issueEditTokens(
        extensionId: ExtensionId,
        sections: List<ExtensionSettingSection>,
        localeTag: String?,
        surface: String,
    ): Map<String, ExtensionSettingEditToken> {
        val now = System.nanoTime()
        return synchronized(editTokenLock) {
            pruneExpiredEditTokens(now)
            buildMap {
                sections.forEach { section ->
                    val sectionFingerprint = store.schemaFingerprint(section)
                    section.schema.fields.forEach { field ->
                        while (activeEditTokens.size >= MAX_ACTIVE_EDIT_TOKENS) {
                            val eldest = activeEditTokens.entries.iterator()
                            if (!eldest.hasNext()) break
                            eldest.next()
                            eldest.remove()
                        }
                        val token = generateSequence { UUID.randomUUID().toString() }
                            .first { candidate -> candidate !in activeEditTokens }
                        activeEditTokens[token] = EditGrant(
                            extensionId = extensionId,
                            sectionId = section.id,
                            fieldKey = field.key,
                            sectionFingerprint = sectionFingerprint,
                            displayedSections = sections,
                            localeTag = localeTag,
                            surface = surface,
                            issuedAtNanos = now,
                        )
                        put(
                            ExtensionSettingKeys.qualified(section.id, field.key),
                            ExtensionSettingEditToken(token),
                        )
                    }
                }
            }
        }
    }

    private fun consumeEditToken(editToken: ExtensionSettingEditToken): EditGrant? {
        val now = System.nanoTime()
        return synchronized(editTokenLock) {
            pruneExpiredEditTokens(now)
            activeEditTokens.remove(editToken.value)
        }
    }

    private fun pruneExpiredEditTokens(now: Long) {
        activeEditTokens.entries.removeAll { (_, grant) ->
            now - grant.issuedAtNanos > EDIT_TOKEN_TTL_NANOS
        }
    }

    private fun List<ExtensionSettingSection>.hostOwnedSnapshot():
        List<ExtensionSettingSection> = map { section ->
        section.copy(
            schema = section.schema.copy(
                fields = section.schema.fields.map { field ->
                    field.copy(choices = field.choices.toList())
                }
            )
        )
    }

    private fun ExtensionSettingField.parse(rawValue: String): JsonPrimitive? = when (type) {
        ExtensionSettingType.TEXT -> JsonPrimitive(rawValue)
        ExtensionSettingType.SECRET -> null
        ExtensionSettingType.BOOLEAN -> rawValue.toBooleanStrictOrNull()?.let(::JsonPrimitive)
        ExtensionSettingType.NUMBER -> rawValue.toDoubleOrNull()
            ?.takeIf(Double::isFinite)
            ?.let(::JsonPrimitive)
        ExtensionSettingType.SINGLE_CHOICE -> rawValue
            .takeIf { value -> choices.any { choice -> choice.value == value } }
            ?.let(::JsonPrimitive)
    }

    private fun List<ExtensionSettingSection>.isValidDynamicSchema(
        existingSectionIds: Set<String>,
        maximumSectionCount: Int,
    ): Boolean {
        if (size > maximumSectionCount) return false
        val sectionIds = existingSectionIds.toMutableSet()
        return all { section ->
            sectionIds.add(section.id) &&
                section.isWithinHostLimits()
        }
    }

    private fun ExtensionSettingSection.isWithinHostLimits(): Boolean =
        title.isSafeExtensionText(MAX_SETTING_LABEL_LENGTH) &&
            runCatching {
                ExtensionSettingKeys.qualified(id, VALIDATION_FIELD_KEY)
            }.isSuccess &&
            schema.version > 0 &&
            schema.fields.size <= MAX_FIELDS_PER_SECTION &&
            schema.fields.map(ExtensionSettingField::key).distinct().size == schema.fields.size &&
            schema.fields.all { field -> field.isWithinHostLimits(id) }

    private fun ExtensionSettingField.isWithinHostLimits(sectionId: String): Boolean =
        runCatching { ExtensionSettingKeys.qualified(sectionId, key) }.isSuccess &&
            label.isSafeExtensionText(MAX_SETTING_LABEL_LENGTH) &&
            (
                description?.isSafeExtensionText(
                    maximumLength = MAX_SETTING_DESCRIPTION_LENGTH,
                    allowBlank = true,
                ) != false
            ) &&
            choices.size <= MAX_SETTING_CHOICES &&
            choices.map { choice -> choice.value }.distinct().size == choices.size &&
            choices.all { choice ->
                choice.label.isSafeExtensionText(MAX_SETTING_LABEL_LENGTH) &&
                    choice.value.length <= MAX_SETTING_CHOICE_VALUE_LENGTH
            } &&
            (defaultValue?.toString()?.encodeToByteArray()?.size ?: 0) <=
            MAX_SETTING_DEFAULT_BYTES

    private fun String.isSafeExtensionText(
        maximumLength: Int,
        allowBlank: Boolean = false,
    ): Boolean =
        (allowBlank || isNotBlank()) &&
            length <= maximumLength &&
            none { character ->
                character.isISOControl() ||
                    character.code in 0x202A..0x202E ||
                    character.code in 0x2066..0x2069 ||
                    character.code == 0x200E ||
                    character.code == 0x200F
            }

    private companion object {
        const val MAX_ACTIVE_EDIT_TOKENS = 4_096
        const val MAX_SECTIONS = 20
        const val MAX_FIELDS_PER_SECTION = 100
        const val MAX_SETTING_LABEL_LENGTH = 160
        const val MAX_SETTING_DESCRIPTION_LENGTH = 1_024
        const val MAX_SETTING_CHOICES = 64
        const val MAX_SETTING_CHOICE_VALUE_LENGTH = 512
        const val MAX_SETTING_DEFAULT_BYTES = 4_096
        const val MAX_SETTING_VALUE_LENGTH = 16_384
        const val SCHEMA_CHANGED_MESSAGE = "Settings schema changed; reload and try again"
        const val VALIDATION_FIELD_KEY = "field"
        const val EDIT_TOKEN_TTL_NANOS = 10L * 60L * 1_000_000_000L
    }

    private data class EditGrant(
        val extensionId: ExtensionId,
        val sectionId: String,
        val fieldKey: String,
        val sectionFingerprint: String,
        val displayedSections: List<ExtensionSettingSection>,
        val localeTag: String?,
        val surface: String,
        val issuedAtNanos: Long,
    )
}
