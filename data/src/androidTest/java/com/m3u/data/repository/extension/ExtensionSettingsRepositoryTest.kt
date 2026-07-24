package com.m3u.data.repository.extension

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.m3u.data.extension.security.AndroidKeystoreCredentialVault
import com.m3u.extension.api.ExtensionApiRange
import com.m3u.extension.api.ExtensionApiVersions
import com.m3u.extension.api.ExtensionCallContext
import com.m3u.extension.api.ExtensionCapabilityIds
import com.m3u.extension.api.ExtensionCapabilityRequest
import com.m3u.extension.api.ExtensionEntrypoint
import com.m3u.extension.api.ExtensionError
import com.m3u.extension.api.ExtensionErrorCode
import com.m3u.extension.api.ExtensionHandler
import com.m3u.extension.api.ExtensionHookDeclaration
import com.m3u.extension.api.ExtensionId
import com.m3u.extension.api.ExtensionManifest
import com.m3u.extension.api.ExtensionSemanticVersion
import com.m3u.extension.api.ExtensionSettingChoice
import com.m3u.extension.api.ExtensionSettingField
import com.m3u.extension.api.ExtensionSettingSchema
import com.m3u.extension.api.ExtensionSettingSection
import com.m3u.extension.api.ExtensionSettingType
import com.m3u.extension.api.ExtensionSettingsSnapshot
import com.m3u.extension.api.HookResult
import com.m3u.extension.api.HostHookSpecs
import com.m3u.extension.api.SettingsSchemaRequest
import com.m3u.extension.api.SettingsSchemaResult
import com.m3u.extension.api.security.CredentialHandle
import com.m3u.extension.runtime.ExtensionRegistrationResult
import com.m3u.extension.runtime.ExtensionRuntime
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExtensionSettingsRepositoryTest {
    private lateinit var context: Context
    private lateinit var secretStore: AndroidKeystoreCredentialVault
    private lateinit var store: ExtensionSettingStore
    private lateinit var repository: ExtensionSettingsRepository
    private lateinit var repositoryImpl: ExtensionSettingsRepositoryImpl
    private var lastSettingsContext: ExtensionCallContext? = null
    private var failSettingsHook = false
    private var settingsSections = listOf(playbackSection())
    private var concurrentSettingsGate: CompletableDeferred<Unit>? = null
    private val concurrentSettingsCalls = AtomicInteger()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        secretStore = AndroidKeystoreCredentialVault(context)
        store = ExtensionSettingStore(context, secretStore)
        store.clear(EXTENSION_ID.value)
        val runtime = ExtensionRuntime(
            hostApiVersion = ExtensionApiVersions.Current,
            settingsProvider = store,
        )
        assertTrue(runtime.register(entrypoint()) is ExtensionRegistrationResult.Registered)
        repositoryImpl = ExtensionSettingsRepositoryImpl(runtime, store, secretStore)
        repository = repositoryImpl
    }

    @After
    fun tearDown() {
        store.clear(EXTENSION_ID.value)
    }

    @Test
    fun settingsAreTypedNamespacedEncryptedAndDeliveredToHooks() = runBlocking {
        val initial = requireNotNull(repository.configuration(EXTENSION_ID, "en-US", "phone"))
        assertEquals(JsonPrimitive(true), initial.snapshot.values["manifest/enabled"])
        assertEquals(JsonPrimitive("auto"), initial.snapshot.values["playback/quality"])

        val secretUpdate = updateCurrent("manifest", "api-key", SECRET)
        assertTrue(secretUpdate is ExtensionSettingUpdateResult.Updated)
        val choiceUpdate = updateCurrent("playback", "quality", "direct")
        assertTrue(choiceUpdate is ExtensionSettingUpdateResult.Updated)
        val invalidChoice = updateCurrent("playback", "quality", "unsupported")
        assertTrue(invalidChoice is ExtensionSettingUpdateResult.Rejected)

        val current = requireNotNull(repository.configuration(EXTENSION_ID, "en-US", "phone"))
        val handle = current.snapshot.credentialHandles["manifest/api-key"]
        assertNotNull(handle)
        assertEquals(SECRET, secretStore.resolve(EXTENSION_ID.value, requireNotNull(handle)))
        assertEquals(JsonPrimitive("direct"), current.snapshot.values["playback/quality"])
        assertEquals(current.snapshot, lastSettingsContext?.settings)

        val persistedText = context.getSharedPreferences("extension-settings", Context.MODE_PRIVATE)
            .all.values.joinToString()
        val persistedSecrets = context.getSharedPreferences(
            "extension-setting-secrets",
            Context.MODE_PRIVATE,
        ).all.values.joinToString()
        assertFalse(persistedText.contains(SECRET))
        assertFalse(persistedSecrets.contains(SECRET))

        repository.clear(EXTENSION_ID)
        assertEquals(null, secretStore.resolve(EXTENSION_ID.value, handle))
    }

    @Test
    fun numberSettingsRejectNonFiniteValues() = runBlocking {
        settingsSections = listOf(
            ExtensionSettingSection(
                id = "numeric",
                title = "Numeric",
                schema = ExtensionSettingSchema(
                    version = 1,
                    fields = listOf(
                        ExtensionSettingField(
                            key = "ratio",
                            label = "Ratio",
                            type = ExtensionSettingType.NUMBER,
                        )
                    ),
                ),
            )
        )

        listOf("NaN", "Infinity", "-Infinity").forEach { value ->
            assertTrue(
                updateCurrent("numeric", "ratio", value) is
                    ExtensionSettingUpdateResult.Rejected
            )
        }
        assertTrue(
            updateCurrent("numeric", "ratio", "1.5") is
                ExtensionSettingUpdateResult.Updated
        )
    }

    @Test
    fun networkOriginApprovalRequiresExplicitSaveAfterFieldUpgrade() = runBlocking {
        settingsSections = listOf(originSection(networkOrigin = false))
        assertTrue(
            updateCurrent("network", "origin", "https://legacy.example", localeTag = null) is
                ExtensionSettingUpdateResult.Updated
        )
        assertTrue(
            store.approvedSettingOrigins(
                EXTENSION_ID.value,
                store.snapshot(EXTENSION_ID.value),
            ).isEmpty()
        )

        settingsSections = listOf(originSection(networkOrigin = true))
        val upgraded = requireNotNull(
            repository.configuration(EXTENSION_ID, null, "phone")
        )
        assertEquals(
            JsonPrimitive("https://legacy.example"),
            upgraded.snapshot.values["network/origin"],
        )
        assertTrue(
            store.approvedSettingOrigins(EXTENSION_ID.value, upgraded.snapshot).isEmpty()
        )

        assertTrue(
            updateCurrent("network", "origin", "https://legacy.example", localeTag = null) is
                ExtensionSettingUpdateResult.Updated
        )
        assertEquals(
            setOf("https://legacy.example:443"),
            store.approvedSettingOrigins(
                EXTENSION_ID.value,
                store.snapshot(EXTENSION_ID.value),
            ),
        )

        val changedWithoutApproval = store.snapshot(EXTENSION_ID.value).let { current ->
            current.copy(
                values = current.values +
                    ("network/origin" to JsonPrimitive("https://changed.example")),
            )
        }
        store.save(EXTENSION_ID.value, changedWithoutApproval)
        assertTrue(
            store.approvedSettingOrigins(
                EXTENSION_ID.value,
                changedWithoutApproval,
            ).isEmpty()
        )
        val restoredOldValue = changedWithoutApproval.copy(
            values = changedWithoutApproval.values +
                ("network/origin" to JsonPrimitive("https://legacy.example")),
        )
        store.save(EXTENSION_ID.value, restoredOldValue)
        assertTrue(
            store.approvedSettingOrigins(
                EXTENSION_ID.value,
                restoredOldValue,
            ).isEmpty()
        )

        settingsSections = emptyList()
        repository.configuration(EXTENSION_ID, null, "phone")
        assertTrue(
            store.approvedSettingOrigins(
                EXTENSION_ID.value,
                store.snapshot(EXTENSION_ID.value),
            ).isEmpty()
        )
    }

    @Test
    fun secretDraftIsRejectedWhenDisplayedFieldBecomesText() = runBlocking {
        settingsSections = listOf(transitionSection(ExtensionSettingType.SECRET))
        val stored = updateCurrent(
            sectionId = "transition",
            fieldKey = "value",
            rawValue = SECRET,
            localeTag = null,
        ) as ExtensionSettingUpdateResult.Updated
        val oldHandle = requireNotNull(
            stored.snapshot.credentialHandles["transition/value"]
        )
        val displayedSecretField = requireNotNull(
            repository.configuration(EXTENSION_ID, null, "phone")
        )

        settingsSections = listOf(transitionSection(ExtensionSettingType.TEXT))
        val draftSecret = "draft-secret-must-not-become-text"
        val update = updateFromConfiguration(
            configuration = displayedSecretField,
            sectionId = "transition",
            fieldKey = "value",
            rawValue = draftSecret,
        )

        assertTrue(update is ExtensionSettingUpdateResult.Rejected)
        val refreshed = requireNotNull(
            repository.configuration(EXTENSION_ID, null, "phone")
        )
        assertFalse(refreshed.snapshot.values.containsKey("transition/value"))
        assertFalse(refreshed.snapshot.credentialHandles.containsKey("transition/value"))
        assertEquals(null, secretStore.resolve(EXTENSION_ID.value, oldHandle))
        val persisted = context.getSharedPreferences(
            "extension-settings",
            Context.MODE_PRIVATE,
        ).all.values.joinToString()
        assertFalse(persisted.contains(SECRET))
        assertFalse(persisted.contains(draftSecret))
    }

    @Test
    fun ordinaryDraftCannotApproveFieldThatBecomesNetworkOrigin() = runBlocking {
        settingsSections = listOf(originSection(networkOrigin = false))
        assertTrue(
            updateCurrent(
                sectionId = "network",
                fieldKey = "origin",
                rawValue = "https://legacy.example",
                localeTag = null,
            ) is ExtensionSettingUpdateResult.Updated
        )
        val displayedOrdinaryField = requireNotNull(
            repository.configuration(EXTENSION_ID, null, "phone")
        )

        settingsSections = listOf(originSection(networkOrigin = true))
        val update = updateFromConfiguration(
            configuration = displayedOrdinaryField,
            sectionId = "network",
            fieldKey = "origin",
            rawValue = "https://legacy.example",
        )

        assertTrue(update is ExtensionSettingUpdateResult.Rejected)
        val refreshed = requireNotNull(
            repository.configuration(EXTENSION_ID, null, "phone")
        )
        assertTrue(
            store.approvedSettingOrigins(EXTENSION_ID.value, refreshed.snapshot).isEmpty()
        )
        val replay = updateFromConfiguration(
            configuration = displayedOrdinaryField,
            sectionId = "network",
            fieldKey = "origin",
            rawValue = "https://legacy.example",
        )
        assertTrue(replay is ExtensionSettingUpdateResult.Rejected)
        assertTrue(
            store.approvedSettingOrigins(
                EXTENSION_ID.value,
                store.snapshot(EXTENSION_ID.value),
            ).isEmpty()
        )
    }

    @Test
    fun schemaVersionChangeDropsValuesAndDeletesSecret() = runBlocking {
        val updated = updateCurrent(
            sectionId = "manifest",
            fieldKey = "api-key",
            rawValue = SECRET,
            localeTag = null,
        ) as ExtensionSettingUpdateResult.Updated
        val handle = requireNotNull(updated.snapshot.credentialHandles["manifest/api-key"])

        val reconciled = store.reconcileSection(
            extensionId = EXTENSION_ID.value,
            sectionId = "manifest",
            schema = MANIFEST_SCHEMA.copy(version = 2),
        )

        assertFalse(reconciled.credentialHandles.containsKey("manifest/api-key"))
        assertEquals(null, secretStore.resolve(EXTENSION_ID.value, handle))
    }

    @Test
    fun staleSchemaUpdateIsRejectedWithoutRestoringRemovedSecret() = runBlocking {
        val firstUpdate = updateCurrent(
            sectionId = "playback",
            fieldKey = "token",
            rawValue = SECRET,
            localeTag = null,
        ) as ExtensionSettingUpdateResult.Updated
        val removedHandle = requireNotNull(
            firstUpdate.snapshot.credentialHandles["playback/token"]
        )
        val staleConfiguration = requireNotNull(
            repository.configuration(EXTENSION_ID, null, "phone")
        )

        settingsSections = listOf(
            playbackSection(schema = PLAYBACK_SCHEMA.copy(version = 2))
        )
        val upgraded = requireNotNull(
            repository.configuration(EXTENSION_ID, null, "phone")
        )
        assertEquals(2, upgraded.snapshot.schemaVersions["playback"])
        assertEquals(null, secretStore.resolve(EXTENSION_ID.value, removedHandle))

        val staleUpdate = updateFromConfiguration(
            configuration = staleConfiguration,
            sectionId = "playback",
            fieldKey = "token",
            rawValue = "must-not-be-restored",
        )

        assertTrue(staleUpdate is ExtensionSettingUpdateResult.Rejected)
        assertFalse(
            store.snapshot(EXTENSION_ID.value)
                .credentialHandles
                .containsKey("playback/token")
        )
        val persistedSecrets = context.getSharedPreferences(
            "extension-setting-secrets",
            Context.MODE_PRIVATE,
        ).all
        assertFalse(
            persistedSecrets.any { (key, value) ->
                key.endsWith(":owner") && value == EXTENSION_ID.value
            }
        )
    }

    @Test
    fun temporaryDynamicSchemaFailurePreservesValuesAndSecret() = runBlocking {
        val updated = updateCurrent(
            sectionId = "playback",
            fieldKey = "token",
            rawValue = SECRET,
            localeTag = null,
        ) as ExtensionSettingUpdateResult.Updated
        val handle = requireNotNull(updated.snapshot.credentialHandles["playback/token"])

        failSettingsHook = true
        val degraded = requireNotNull(repository.configuration(EXTENSION_ID, null, "phone"))

        assertTrue(degraded.sections.none { section -> section.id == "playback" })
        val persisted = store.snapshot(EXTENSION_ID.value)
        assertEquals(handle, persisted.credentialHandles["playback/token"])
        assertEquals(SECRET, secretStore.resolve(EXTENSION_ID.value, handle))
    }

    @Test
    fun invalidDynamicSchemaPreservesValuesAndSecret() = runBlocking {
        val updated = updateCurrent(
            sectionId = "playback",
            fieldKey = "token",
            rawValue = SECRET,
            localeTag = null,
        ) as ExtensionSettingUpdateResult.Updated
        val handle = requireNotNull(updated.snapshot.credentialHandles["playback/token"])
        val invalidResponses = listOf(
            List(20) { index ->
                playbackSection(id = "section-$index")
            },
            listOf(
                playbackSection(id = "duplicate"),
                playbackSection(id = "duplicate"),
            ),
            listOf(
                playbackSection(
                    id = "too-many-fields",
                    schema = ExtensionSettingSchema(
                        version = 1,
                        fields = List(101) { index ->
                            ExtensionSettingField(
                                key = "field-$index",
                                label = "Field $index",
                                type = ExtensionSettingType.TEXT,
                            )
                        },
                    ),
                )
            ),
            listOf(
                playbackSection(
                    id = "oversized-label",
                    title = "x".repeat(161),
                )
            ),
        )

        invalidResponses.forEach { invalidSections ->
            settingsSections = invalidSections

            val degraded = requireNotNull(
                repository.configuration(EXTENSION_ID, null, "phone")
            )

            assertEquals(
                listOf(ExtensionSettingStore.MANIFEST_SECTION_ID),
                degraded.sections.map(ExtensionSettingSection::id),
            )
            val persisted = store.snapshot(EXTENSION_ID.value)
            assertEquals(handle, persisted.credentialHandles["playback/token"])
            assertEquals(SECRET, secretStore.resolve(EXTENSION_ID.value, handle))
        }
    }

    @Test
    fun validEmptyDynamicSchemaRemovesMissingValuesAndSecret() = runBlocking {
        val updated = updateCurrent(
            sectionId = "playback",
            fieldKey = "token",
            rawValue = SECRET,
            localeTag = null,
        ) as ExtensionSettingUpdateResult.Updated
        val handle = requireNotNull(updated.snapshot.credentialHandles["playback/token"])

        settingsSections = emptyList()
        val configuration = requireNotNull(
            repository.configuration(EXTENSION_ID, null, "phone")
        )

        assertEquals(
            listOf(ExtensionSettingStore.MANIFEST_SECTION_ID),
            configuration.sections.map(ExtensionSettingSection::id),
        )
        val persisted = store.snapshot(EXTENSION_ID.value)
        assertFalse(persisted.credentialHandles.containsKey("playback/token"))
        assertEquals(null, secretStore.resolve(EXTENSION_ID.value, handle))
    }

    @Test
    fun restoredSettingsDropMissingCredentialHandlesButKeepOrdinaryValues() {
        val missingHandle = CredentialHandle("extension-secret:missing-after-restore")
        val restored = ExtensionSettingsSnapshot(
            schemaVersions = mapOf("playback" to 1),
            values = mapOf("playback/quality" to JsonPrimitive("direct")),
            credentialHandles = mapOf(
                "playback/token" to missingHandle
            ),
        )
        store.save(EXTENSION_ID.value, restored)

        val repaired = store.snapshot(EXTENSION_ID.value)

        assertEquals(restored.schemaVersions, repaired.schemaVersions)
        assertEquals(restored.values, repaired.values)
        assertTrue(repaired.credentialHandles.isEmpty())
        assertEquals(repaired, store.snapshot(EXTENSION_ID.value))
        val persisted = context.getSharedPreferences("extension-settings", Context.MODE_PRIVATE)
            .getString(EXTENSION_ID.value, null)
            .orEmpty()
        assertFalse(persisted.contains(missingHandle.value))
    }

    @Test
    fun concurrentFieldUpdatesDoNotOverwriteEachOther() = runBlocking {
        val displayed = requireNotNull(
            repository.configuration(EXTENSION_ID, null, "phone")
        )
        concurrentSettingsCalls.set(0)
        concurrentSettingsGate = CompletableDeferred()

        val updates = listOf(
            async(Dispatchers.Default) {
                updateFromConfiguration(
                    configuration = displayed,
                    sectionId = "manifest",
                    fieldKey = "enabled",
                    rawValue = "false",
                )
            },
            async(Dispatchers.Default) {
                updateFromConfiguration(
                    configuration = displayed,
                    sectionId = "playback",
                    fieldKey = "quality",
                    rawValue = "direct",
                )
            },
        ).awaitAll()
        concurrentSettingsGate = null

        assertTrue(updates.all { update -> update is ExtensionSettingUpdateResult.Updated })
        val snapshot = store.snapshot(EXTENSION_ID.value)
        assertEquals(JsonPrimitive(false), snapshot.values["manifest/enabled"])
        assertEquals(JsonPrimitive("direct"), snapshot.values["playback/quality"])
    }

    private suspend fun updateCurrent(
        sectionId: String,
        fieldKey: String,
        rawValue: String?,
        localeTag: String? = "en-US",
        surface: String = "phone",
    ): ExtensionSettingUpdateResult {
        val configuration = requireNotNull(
            repository.configuration(EXTENSION_ID, localeTag, surface)
        )
        return updateFromConfiguration(configuration, sectionId, fieldKey, rawValue)
    }

    private suspend fun updateFromConfiguration(
        configuration: ExtensionSettingsConfiguration,
        sectionId: String,
        fieldKey: String,
        rawValue: String?,
    ): ExtensionSettingUpdateResult = repository.update(
        extensionId = configuration.extensionId,
        sectionId = sectionId,
        fieldKey = fieldKey,
        editToken = requireNotNull(configuration.editToken(sectionId, fieldKey)),
        rawValue = rawValue,
    )

    private fun entrypoint(): ExtensionEntrypoint = object : ExtensionEntrypoint {
        override val manifest = ExtensionManifest(
            id = EXTENSION_ID,
            displayName = "Settings test",
            extensionVersion = ExtensionSemanticVersion(1, 0, 0),
            apiRange = ExtensionApiRange(ExtensionApiVersions.Current, ExtensionApiVersions.Current),
            hooks = setOf(
                ExtensionHookDeclaration(
                    HostHookSpecs.SettingsSchema.hook,
                    HostHookSpecs.SettingsSchema.schemaVersion,
                    setOf(ExtensionCapabilityIds.SettingsContribute),
                )
            ),
            capabilities = setOf(
                ExtensionCapabilityRequest(
                    ExtensionCapabilityIds.SettingsContribute,
                    "Contribute settings",
                )
            ),
            settingsSchema = MANIFEST_SCHEMA,
        )
        override val handlers: Collection<ExtensionHandler<*, *>> = listOf(
            object : ExtensionHandler<SettingsSchemaRequest, SettingsSchemaResult> {
                override val spec = HostHookSpecs.SettingsSchema
                override suspend fun invoke(
                    context: ExtensionCallContext,
                    request: SettingsSchemaRequest,
                ): HookResult<SettingsSchemaResult> {
                    lastSettingsContext = context
                    concurrentSettingsGate?.let { gate ->
                        if (concurrentSettingsCalls.incrementAndGet() == 2) {
                            gate.complete(Unit)
                        }
                        gate.await()
                    }
                    if (failSettingsHook) {
                        return HookResult.Failure(
                            ExtensionError(
                                code = ExtensionErrorCode("settings.temporary"),
                                message = "Temporary settings failure",
                                recoverable = true,
                            )
                        )
                    }
                    return HookResult.Success(
                        SettingsSchemaResult(settingsSections)
                    )
                }
            }
        )
    }

    private fun playbackSection(
        id: String = "playback",
        title: String = "Playback",
        schema: ExtensionSettingSchema = PLAYBACK_SCHEMA,
    ) = ExtensionSettingSection(
        id = id,
        title = title,
        schema = schema,
    )

    private fun originSection(networkOrigin: Boolean) = ExtensionSettingSection(
        id = "network",
        title = "Network",
        schema = ExtensionSettingSchema(
            version = 1,
            fields = listOf(
                ExtensionSettingField(
                    key = "origin",
                    label = "Origin",
                    type = ExtensionSettingType.TEXT,
                    networkOrigin = networkOrigin,
                )
            ),
        ),
    )

    private fun transitionSection(type: ExtensionSettingType) = ExtensionSettingSection(
        id = "transition",
        title = "Transition",
        schema = ExtensionSettingSchema(
            version = 1,
            fields = listOf(
                ExtensionSettingField(
                    key = "value",
                    label = "Value",
                    type = type,
                )
            ),
        ),
    )

    private companion object {
        val EXTENSION_ID = ExtensionId("com.m3u.test.settings")
        const val SECRET = "never-write-this-plaintext"
        val MANIFEST_SCHEMA = ExtensionSettingSchema(
            version = 1,
            fields = listOf(
                ExtensionSettingField(
                    key = "enabled",
                    label = "Enabled",
                    type = ExtensionSettingType.BOOLEAN,
                    defaultValue = JsonPrimitive(true),
                ),
                ExtensionSettingField(
                    key = "api-key",
                    label = "API key",
                    type = ExtensionSettingType.SECRET,
                ),
            ),
        )
        val PLAYBACK_SCHEMA = ExtensionSettingSchema(
            version = 1,
            fields = listOf(
                ExtensionSettingField(
                    key = "quality",
                    label = "Quality",
                    type = ExtensionSettingType.SINGLE_CHOICE,
                    choices = listOf(
                        ExtensionSettingChoice("auto", "Automatic"),
                        ExtensionSettingChoice("direct", "Direct"),
                    ),
                    defaultValue = JsonPrimitive("auto"),
                ),
                ExtensionSettingField(
                    key = "token",
                    label = "Token",
                    type = ExtensionSettingType.SECRET,
                ),
            ),
        )
    }
}
