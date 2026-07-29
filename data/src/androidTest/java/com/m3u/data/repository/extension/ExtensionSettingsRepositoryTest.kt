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
    private lateinit var runtime: ExtensionRuntime
    private lateinit var repository: ExtensionSettingsRepository
    private lateinit var repositoryImpl: ExtensionSettingsRepositoryImpl
    private var lastSettingsContext: ExtensionCallContext? = null
    private var failSettingsHook = false
    private var settingsSections = listOf(playbackSection())
    private var settingsSectionsProvider:
        ((SettingsSchemaRequest) -> List<ExtensionSettingSection>)? = null
    private var settingsInvocationStarted: CompletableDeferred<Unit>? = null
    private var settingsInvocationRelease: CompletableDeferred<Unit>? = null
    private var concurrentSettingsGate: CompletableDeferred<Unit>? = null
    private val concurrentSettingsCalls = AtomicInteger()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        secretStore = AndroidKeystoreCredentialVault(context)
        store = ExtensionSettingStore(context, secretStore)
        store.clear(EXTENSION_ID.value)
        runtime = ExtensionRuntime(
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
        assertFalse(
            requireNotNull(lastSettingsContext)
                .settings
                .values
                .containsKey("playback/quality")
        )

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
    fun secretSettingsPreserveLeadingAndTrailingWhitespace() = runBlocking {
        val exactSecret = "  密钥 value  "

        val update = updateCurrent("manifest", "api-key", exactSecret)

        val snapshot = (update as ExtensionSettingUpdateResult.Updated).snapshot
        val handle = requireNotNull(snapshot.credentialHandles["manifest/api-key"])
        assertEquals(exactSecret, secretStore.resolve(EXTENSION_ID.value, handle))
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

        forgetDynamicSchemaRegistryToSimulateLegacyState()
        settingsSections = listOf(originSection(networkOrigin = true))
        val upgraded = requireNotNull(
            repository.configuration(EXTENSION_ID, null, "phone")
        )
        assertEquals(
            JsonPrimitive("https://legacy.example"),
            upgraded.snapshot.values["network/origin"],
        )
        assertEquals(
            ExtensionNetworkOriginState.REQUIRES_APPROVAL,
            upgraded.networkOriginState("network", "origin"),
        )
        assertEquals(
            "Origin",
            upgraded.settingNetworkOrigin("network", "origin")?.label,
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
        val approved = requireNotNull(
            repository.configuration(EXTENSION_ID, null, "phone")
        )
        assertEquals(
            ExtensionNetworkOriginState.APPROVED,
            approved.networkOriginState("network", "origin"),
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

        settingsSections = listOf(
            transitionSection(
                type = ExtensionSettingType.TEXT,
                version = 2,
            )
        )
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

        forgetDynamicSchemaRegistryToSimulateLegacyState()
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
    fun consumedEditTokenCannotWriteAfterItsSchemaSessionIsSuspended() = runBlocking {
        val configuration = requireNotNull(
            repository.configuration(EXTENSION_ID, "en-US", "phone")
        )
        settingsInvocationStarted = CompletableDeferred()
        settingsInvocationRelease = CompletableDeferred()

        val update = async(Dispatchers.Default) {
            updateFromConfiguration(
                configuration = configuration,
                sectionId = "playback",
                fieldKey = "quality",
                rawValue = "direct",
            )
        }
        requireNotNull(settingsInvocationStarted).await()
        repository.suspendDynamicSchemas(EXTENSION_ID)
        requireNotNull(settingsInvocationRelease).complete(Unit)

        assertTrue(update.await() is ExtensionSettingUpdateResult.Rejected)
        assertEquals(
            JsonPrimitive("auto"),
            store.snapshot(EXTENSION_ID.value).values["playback/quality"],
        )
        settingsInvocationStarted = null
        settingsInvocationRelease = null
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
    fun localizedCopyAndSurfaceOnlySectionsPreserveVerifiedValues() = runBlocking {
        settingsSectionsProvider = { request ->
            val playback = playbackSection(
                title = if (request.localeTag == "zh-CN") "播放" else "Playback",
                schema = localizedPlaybackSchema(request.localeTag == "zh-CN"),
            )
            if (request.surface == "tv") {
                listOf(
                    playback,
                    playbackSection(
                        id = "television",
                        title = "Television",
                        schema = ExtensionSettingSchema(
                            version = 1,
                            fields = listOf(
                                ExtensionSettingField(
                                    key = "large-controls",
                                    label = "Large controls",
                                    type = ExtensionSettingType.BOOLEAN,
                                    defaultValue = JsonPrimitive(true),
                                )
                            ),
                        ),
                    ),
                )
            } else {
                listOf(playback)
            }
        }
        assertTrue(
            updateCurrent("playback", "token", SECRET, localeTag = "en-US") is
                ExtensionSettingUpdateResult.Updated
        )

        val tv = requireNotNull(
            repository.configuration(EXTENSION_ID, "en-US", "tv")
        )
        val localizedPhone = requireNotNull(
            repository.configuration(EXTENSION_ID, "zh-CN", "phone")
        )

        assertEquals(
            setOf("phone", "tv"),
            repository.knownDynamicSchemaSurfaces(EXTENSION_ID),
        )
        assertEquals(
            listOf("manifest", "playback"),
            localizedPhone.sections.map(ExtensionSettingSection::id),
        )
        assertEquals(
            listOf("manifest", "playback", "television"),
            tv.sections.map(ExtensionSettingSection::id),
        )
        val schemaContext = requireNotNull(lastSettingsContext).settings
        assertFalse(schemaContext.values.containsKey("playback/quality"))
        assertFalse(schemaContext.values.containsKey("television/large-controls"))
        assertFalse(schemaContext.credentialHandles.containsKey("playback/token"))
        val runtimeSnapshot = store.snapshot(entrypoint().manifest)
        assertEquals(JsonPrimitive("auto"), runtimeSnapshot.values["playback/quality"])
        assertEquals(
            JsonPrimitive(true),
            runtimeSnapshot.values["television/large-controls"],
        )
        val token = requireNotNull(runtimeSnapshot.credentialHandles["playback/token"])
        assertEquals(SECRET, secretStore.resolve(EXTENSION_ID.value, token))
    }

    @Test
    fun failedSurfaceSuspendsDynamicValuesSecretsAndOriginApproval() = runBlocking {
        settingsSections = listOf(
            playbackSection(),
            originSection(networkOrigin = true),
        )
        assertTrue(
            updateCurrent("playback", "token", SECRET) is
                ExtensionSettingUpdateResult.Updated
        )
        assertTrue(
            updateCurrent("network", "origin", "HTTPS://API.EXAMPLE.TEST") is
                ExtensionSettingUpdateResult.Updated
        )
        val rawBeforeFailure = store.snapshot(EXTENSION_ID.value)
        val handle = requireNotNull(rawBeforeFailure.credentialHandles["playback/token"])

        failSettingsHook = true
        val degraded = requireNotNull(
            repository.configuration(EXTENSION_ID, "en-US", "phone")
        )

        assertEquals(
            listOf(ExtensionSettingStore.MANIFEST_SECTION_ID),
            degraded.sections.map(ExtensionSettingSection::id),
        )
        val raw = store.snapshot(EXTENSION_ID.value)
        assertEquals(JsonPrimitive("auto"), raw.values["playback/quality"])
        assertEquals(SECRET, secretStore.resolve(EXTENSION_ID.value, handle))
        val runtimeSnapshot = store.snapshot(entrypoint().manifest)
        assertFalse(runtimeSnapshot.values.containsKey("playback/quality"))
        assertFalse(runtimeSnapshot.values.containsKey("network/origin"))
        assertFalse(runtimeSnapshot.credentialHandles.containsKey("playback/token"))
        assertFalse(runtimeSnapshot.schemaVersions.containsKey("playback"))
        val review = store.settingOriginReview(EXTENSION_ID.value).single {
            it.sectionId == "network" && it.fieldKey == "origin"
        }
        assertEquals("https://api.example.test:443", review.currentOrigin)
        assertEquals(ExtensionNetworkOriginState.SUSPENDED, review.state)
        assertTrue(store.approvedSettingOrigins(EXTENSION_ID.value, raw).isEmpty())
    }

    @Test
    fun persistedRegistryRequiresANewSessionAndRejectsPreClearResponse() = runBlocking {
        repository.configuration(EXTENSION_ID, "en-US", "phone")
        val restartedStore = ExtensionSettingStore(context, secretStore)

        assertEquals(
            setOf("phone"),
            restartedStore.knownDynamicSchemaSurfaces(EXTENSION_ID.value),
        )
        assertFalse(
            restartedStore.snapshot(entrypoint().manifest)
                .values
                .containsKey("playback/quality")
        )
        val session = restartedStore.beginDynamicSchemaSession(
            EXTENSION_ID.value,
            registrationLease(),
        )
        val validation = requireNotNull(
            restartedStore.beginDynamicSchemaValidation(session, "phone")
        )
        restartedStore.clearDynamicSchemas(EXTENSION_ID.value, session)

        assertFalse(
            restartedStore.revalidateDynamicSchemas(
                extensionId = EXTENSION_ID.value,
                validation = validation,
                sections = listOf(playbackSection()),
            )
        )
        assertEquals("DynamicSchemaSession(opaque)", session.toString())
    }

    @Test
    fun legacyRegistryWithRetainedValuesRevalidatesPhoneAndTvSurfaces() = runBlocking {
        repository.configuration(EXTENSION_ID, "en-US", "phone")
        val preferences = context.getSharedPreferences(
            "extension-settings",
            Context.MODE_PRIVATE,
        )
        val registryKey = "dynamic-schema-registry:${EXTENSION_ID.value}"
        val encodedRegistry = requireNotNull(preferences.getString(registryKey, null))
        val legacyRegistry = encodedRegistry
            .replace("\"formatVersion\":2", "\"formatVersion\":1")
            .let { replaced ->
                if (replaced != encodedRegistry) {
                    replaced
                } else {
                    "${encodedRegistry.dropLast(1)},\"formatVersion\":1}"
                }
            }
        assertTrue(preferences.edit().putString(registryKey, legacyRegistry).commit())
        val restartedStore = ExtensionSettingStore(context, secretStore)
        val restartedRepository = ExtensionSettingsRepositoryImpl(
            runtime,
            restartedStore,
            secretStore,
        )

        assertEquals(
            setOf("phone", "tv"),
            restartedRepository.knownDynamicSchemaSurfaces(EXTENSION_ID),
        )
    }

    @Test
    fun olderSuccessfulValidationCannotReplaceTheLatestSurfaceAttempt() {
        val session = store.beginDynamicSchemaSession(
            EXTENSION_ID.value,
            registrationLease(),
        )
        val olderValidation = requireNotNull(
            store.beginDynamicSchemaValidation(session, "phone")
        )
        val latestValidation = requireNotNull(
            store.beginDynamicSchemaValidation(session, "phone")
        )

        assertTrue(
            store.revalidateDynamicSchemas(
                extensionId = EXTENSION_ID.value,
                validation = latestValidation,
                sections = listOf(
                    playbackSection(schema = PLAYBACK_SCHEMA.copy(version = 2))
                ),
            )
        )
        assertFalse(
            store.revalidateDynamicSchemas(
                extensionId = EXTENSION_ID.value,
                validation = olderValidation,
                sections = listOf(playbackSection()),
            )
        )

        val runtimeSnapshot = store.snapshot(entrypoint().manifest)
        assertEquals(2, runtimeSnapshot.schemaVersions["playback"])
    }

    @Test
    fun sameVersionDynamicSchemaRejectsSemanticFieldChanges() {
        val session = store.beginDynamicSchemaSession(
            EXTENSION_ID.value,
            registrationLease(),
        )
        val initialValidation = requireNotNull(
            store.beginDynamicSchemaValidation(session, "phone")
        )
        assertTrue(
            store.revalidateDynamicSchemas(
                extensionId = EXTENSION_ID.value,
                validation = initialValidation,
                sections = listOf(playbackSection()),
            )
        )
        val semanticChanges = listOf(
            PLAYBACK_SCHEMA.copy(
                fields = PLAYBACK_SCHEMA.fields.map { field ->
                    if (field.key == "quality") field.copy(required = true) else field
                },
            ),
            PLAYBACK_SCHEMA.copy(
                fields = PLAYBACK_SCHEMA.fields.map { field ->
                    if (field.key == "quality") {
                        field.copy(
                            choices = field.choices +
                                ExtensionSettingChoice("transcoded", "Transcoded")
                        )
                    } else {
                        field
                    }
                },
            ),
            PLAYBACK_SCHEMA.copy(
                fields = PLAYBACK_SCHEMA.fields.map { field ->
                    if (field.key == "quality") {
                        field.copy(defaultValue = JsonPrimitive("direct"))
                    } else {
                        field
                    }
                },
            ),
        )

        semanticChanges.forEach { changedSchema ->
            val validation = requireNotNull(
                store.beginDynamicSchemaValidation(session, "phone")
            )
            assertFalse(
                store.revalidateDynamicSchemas(
                    extensionId = EXTENSION_ID.value,
                    validation = validation,
                    sections = listOf(playbackSection(schema = changedSchema)),
                )
            )
        }
    }

    @Test
    fun dynamicNetworkOriginLabelIsNotSecurityShapeAndSurvivesRestart() = runBlocking {
        settingsSections = listOf(
            originSection(
                networkOrigin = true,
                label = "Server origin",
            )
        )
        val initial = requireNotNull(
            repository.configuration(EXTENSION_ID, "en-US", "phone")
        )
        assertEquals(
            "Server origin",
            initial.settingNetworkOrigin("network", "origin")?.label,
        )
        val preferences = context.getSharedPreferences(
            "extension-settings",
            Context.MODE_PRIVATE,
        )
        val fingerprintKey = "schema-fingerprints:${EXTENSION_ID.value}"
        val initialFingerprint = requireNotNull(
            preferences.getString(fingerprintKey, null)
        )

        settingsSections = listOf(
            originSection(
                networkOrigin = true,
                label = "服务器地址",
            )
        )
        val localized = requireNotNull(
            repository.configuration(EXTENSION_ID, "zh-CN", "phone")
        )

        assertEquals(
            "服务器地址",
            localized.settingNetworkOrigin("network", "origin")?.label,
        )
        assertEquals(initialFingerprint, preferences.getString(fingerprintKey, null))
        val restartedStore = ExtensionSettingStore(context, secretStore)
        val retained = restartedStore.settingOriginReview(EXTENSION_ID.value).single {
            it.sectionId == "network" && it.fieldKey == "origin"
        }
        assertEquals("服务器地址", retained.label)
        assertEquals(ExtensionNetworkOriginState.SUSPENDED, retained.state)
    }

    @Test
    fun manifestNetworkOriginLabelIsNotSecurityShapeAndSurvivesRestart() {
        val initialManifest = entrypoint().manifest.copy(
            settingsSchema = networkOriginSchema(label = "Server origin"),
            capabilities = entrypoint().manifest.capabilities +
                ExtensionCapabilityRequest(
                    ExtensionCapabilityIds.Network,
                    "Reach the configured server",
                ),
        )
        store.snapshot(initialManifest)
        val preferences = context.getSharedPreferences(
            "extension-settings",
            Context.MODE_PRIVATE,
        )
        val fingerprintKey = "schema-fingerprints:${EXTENSION_ID.value}"
        val initialFingerprint = requireNotNull(
            preferences.getString(fingerprintKey, null)
        )

        store.snapshot(
            initialManifest.copy(
                settingsSchema = networkOriginSchema(label = "服务器地址"),
            )
        )

        assertEquals(initialFingerprint, preferences.getString(fingerprintKey, null))
        val restartedStore = ExtensionSettingStore(context, secretStore)
        val retained = restartedStore.settingOriginReview(EXTENSION_ID.value).single {
            it.sectionId == ExtensionSettingStore.MANIFEST_SECTION_ID &&
                it.fieldKey == "origin"
        }
        assertEquals("服务器地址", retained.label)
        assertEquals(ExtensionNetworkOriginState.NOT_CONFIGURED, retained.state)
    }

    @Test
    fun configurationExposesEveryNetworkOriginStateByQualifiedKey() {
        val origins = ExtensionNetworkOriginState.values().map { state ->
            ExtensionSettingNetworkOrigin(
                sectionId = "network",
                fieldKey = state.name.lowercase(),
                label = state.name,
                currentOrigin = null,
                state = state,
            )
        }
        val configuration = ExtensionSettingsConfiguration(
            extensionId = EXTENSION_ID,
            sections = emptyList(),
            snapshot = ExtensionSettingsSnapshot(),
            editTokens = emptyMap(),
            settingNetworkOrigins = origins,
        )

        assertEquals(
            ExtensionNetworkOriginState.values().toList(),
            configuration.settingNetworkOrigins.map(ExtensionSettingNetworkOrigin::state),
        )
        origins.forEach { origin ->
            assertEquals(
                origin,
                configuration.settingNetworkOrigin(origin.sectionId, origin.fieldKey),
            )
            assertEquals(
                origin,
                configuration.settingNetworkOrigin(origin.qualifiedKey),
            )
            assertEquals(
                origin.state,
                configuration.networkOriginState(origin.sectionId, origin.fieldKey),
            )
        }
    }

    @Test
    fun sameVersionNetworkOriginPromotionSuspendsTheWholeDynamicSurface() = runBlocking {
        settingsSections = listOf(securityShapeSection(promotedNetworkOrigin = false))
        assertTrue(
            updateCurrent(
                sectionId = "security",
                fieldKey = "promoted-origin",
                rawValue = "https://promoted.example",
                localeTag = null,
            ) is ExtensionSettingUpdateResult.Updated
        )
        assertTrue(
            updateCurrent(
                sectionId = "security",
                fieldKey = "approved-origin",
                rawValue = "https://approved.example",
                localeTag = null,
            ) is ExtensionSettingUpdateResult.Updated
        )
        assertTrue(
            updateCurrent(
                sectionId = "security",
                fieldKey = "token",
                rawValue = SECRET,
                localeTag = null,
            ) is ExtensionSettingUpdateResult.Updated
        )
        val rawBeforeRejection = store.snapshot(EXTENSION_ID.value)
        val secretHandle = requireNotNull(
            rawBeforeRejection.credentialHandles["security/token"]
        )
        assertEquals(
            setOf("https://approved.example:443"),
            store.approvedSettingOrigins(EXTENSION_ID.value, rawBeforeRejection),
        )
        val runtimeBeforeRejection = store.snapshot(entrypoint().manifest)
        assertEquals(
            JsonPrimitive("https://promoted.example"),
            runtimeBeforeRejection.values["security/promoted-origin"],
        )
        assertEquals(
            JsonPrimitive("https://approved.example:443"),
            runtimeBeforeRejection.values["security/approved-origin"],
        )
        assertEquals(
            secretHandle,
            runtimeBeforeRejection.credentialHandles["security/token"],
        )
        assertEquals(1, runtimeBeforeRejection.schemaVersions["security"])

        val session = requireNotNull(
            store.currentDynamicSchemaSession(
                EXTENSION_ID.value,
                registrationLease(),
            )
        )
        val validation = requireNotNull(
            store.beginDynamicSchemaValidation(session, "phone")
        )
        assertFalse(
            store.revalidateDynamicSchemas(
                extensionId = EXTENSION_ID.value,
                validation = validation,
                sections = listOf(securityShapeSection(promotedNetworkOrigin = true)),
            )
        )

        val rawAfterRejection = store.snapshot(EXTENSION_ID.value)
        assertEquals(rawBeforeRejection, rawAfterRejection)
        assertEquals(SECRET, secretStore.resolve(EXTENSION_ID.value, secretHandle))
        val runtimeSnapshot = store.snapshot(entrypoint().manifest)
        assertFalse(runtimeSnapshot.values.containsKey("security/promoted-origin"))
        assertFalse(runtimeSnapshot.values.containsKey("security/approved-origin"))
        assertFalse(runtimeSnapshot.credentialHandles.containsKey("security/token"))
        assertFalse(runtimeSnapshot.schemaVersions.containsKey("security"))
        assertTrue(
            store.approvedSettingOrigins(
                EXTENSION_ID.value,
                rawAfterRejection,
            ).isEmpty()
        )
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
            listOf(
                playbackSection(
                    id = "unsafe-section-title",
                    title = "Playback\u061Cforged",
                )
            ),
            listOf(
                playbackSection(
                    id = "unsafe-description",
                    schema = ExtensionSettingSchema(
                        version = 1,
                        fields = listOf(
                            ExtensionSettingField(
                                key = "description",
                                label = "Description",
                                type = ExtensionSettingType.TEXT,
                                description = "First line\u2028forged line",
                            )
                        ),
                    ),
                )
            ),
            listOf(
                playbackSection(
                    id = "unsafe-choice",
                    schema = ExtensionSettingSchema(
                        version = 1,
                        fields = listOf(
                            ExtensionSettingField(
                                key = "choice",
                                label = "Choice",
                                type = ExtensionSettingType.SINGLE_CHOICE,
                                choices = listOf(
                                    ExtensionSettingChoice(
                                        value = "direct\u2066forged",
                                        label = "Direct",
                                    )
                                ),
                            )
                        ),
                    ),
                )
            ),
            listOf(
                playbackSection(
                    id = "unsafe-default",
                    schema = ExtensionSettingSchema(
                        version = 1,
                        fields = listOf(
                            ExtensionSettingField(
                                key = "default",
                                label = "Default",
                                type = ExtensionSettingType.TEXT,
                                defaultValue = JsonPrimitive("value\u2029forged"),
                            )
                        ),
                    ),
                )
            ),
            listOf(
                playbackSection(
                    id = "oversized-default",
                    schema = ExtensionSettingSchema(
                        version = 1,
                        fields = listOf(
                            ExtensionSettingField(
                                key = "default",
                                label = "Default",
                                type = ExtensionSettingType.TEXT,
                                defaultValue = JsonPrimitive("界".repeat(1_366)),
                            )
                        ),
                    ),
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
                    settingsInvocationStarted?.complete(Unit)
                    settingsInvocationRelease?.await()
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
                        SettingsSchemaResult(
                            settingsSectionsProvider?.invoke(request) ?: settingsSections
                        )
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

    private fun originSection(
        networkOrigin: Boolean,
        label: String = "Origin",
    ) = ExtensionSettingSection(
        id = "network",
        title = "Network",
        schema = ExtensionSettingSchema(
            version = 1,
            fields = listOf(
                ExtensionSettingField(
                    key = "origin",
                    label = label,
                    type = ExtensionSettingType.TEXT,
                    networkOrigin = networkOrigin,
                )
            ),
        ),
    )

    private fun networkOriginSchema(label: String) = ExtensionSettingSchema(
        version = 1,
        fields = listOf(
            ExtensionSettingField(
                key = "origin",
                label = label,
                type = ExtensionSettingType.TEXT,
                networkOrigin = true,
            )
        ),
    )

    private fun securityShapeSection(
        promotedNetworkOrigin: Boolean,
    ) = ExtensionSettingSection(
        id = "security",
        title = "Security",
        schema = ExtensionSettingSchema(
            version = 1,
            fields = listOf(
                ExtensionSettingField(
                    key = "promoted-origin",
                    label = "Promoted origin",
                    type = ExtensionSettingType.TEXT,
                    networkOrigin = promotedNetworkOrigin,
                ),
                ExtensionSettingField(
                    key = "approved-origin",
                    label = "Approved origin",
                    type = ExtensionSettingType.TEXT,
                    networkOrigin = true,
                ),
                ExtensionSettingField(
                    key = "token",
                    label = "Token",
                    type = ExtensionSettingType.SECRET,
                ),
            ),
        ),
    )

    private fun transitionSection(
        type: ExtensionSettingType,
        version: Int = 1,
    ) = ExtensionSettingSection(
        id = "transition",
        title = "Transition",
        schema = ExtensionSettingSchema(
            version = version,
            fields = listOf(
                ExtensionSettingField(
                    key = "value",
                    label = "Value",
                    type = type,
                )
            ),
        ),
    )

    private fun forgetDynamicSchemaRegistryToSimulateLegacyState() {
        context.getSharedPreferences("extension-settings", Context.MODE_PRIVATE)
            .edit()
            .remove("dynamic-schema-registry:${EXTENSION_ID.value}")
            .commit()
    }

    private fun registrationLease() =
        requireNotNull(runtime.captureRegistration(EXTENSION_ID)).lease

    private fun localizedPlaybackSchema(chinese: Boolean): ExtensionSettingSchema =
        PLAYBACK_SCHEMA.copy(
            fields = PLAYBACK_SCHEMA.fields.map { field ->
                field.copy(
                    label = when {
                        !chinese -> field.label
                        field.key == "quality" -> "画质"
                        else -> "令牌"
                    },
                    choices = field.choices.map { choice ->
                        choice.copy(
                            label = if (chinese) {
                                "选项-${choice.value}"
                            } else {
                                choice.label
                            }
                        )
                    },
                )
            },
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
