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
import com.m3u.extension.api.HookResult
import com.m3u.extension.api.HostHookSpecs
import com.m3u.extension.api.SettingsSchemaRequest
import com.m3u.extension.api.SettingsSchemaResult
import com.m3u.extension.runtime.ExtensionRegistrationResult
import com.m3u.extension.runtime.ExtensionRuntime
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
    private var lastSettingsContext: ExtensionCallContext? = null

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
        repository = ExtensionSettingsRepositoryImpl(runtime, store, secretStore)
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

        val secretUpdate = repository.update(
            EXTENSION_ID,
            "manifest",
            "api-key",
            SECRET,
            "en-US",
            "phone",
        )
        assertTrue(secretUpdate is ExtensionSettingUpdateResult.Updated)
        val choiceUpdate = repository.update(
            EXTENSION_ID,
            "playback",
            "quality",
            "direct",
            "en-US",
            "phone",
        )
        assertTrue(choiceUpdate is ExtensionSettingUpdateResult.Updated)
        val invalidChoice = repository.update(
            EXTENSION_ID,
            "playback",
            "quality",
            "unsupported",
            "en-US",
            "phone",
        )
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
    fun schemaVersionChangeDropsValuesAndDeletesSecret() = runBlocking {
        repository.configuration(EXTENSION_ID, null, "phone")
        val updated = repository.update(
            EXTENSION_ID,
            "manifest",
            "api-key",
            SECRET,
            null,
            "phone",
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
                    return HookResult.Success(
                        SettingsSchemaResult(
                            listOf(
                                ExtensionSettingSection(
                                    id = "playback",
                                    title = "Playback",
                                    schema = PLAYBACK_SCHEMA,
                                )
                            )
                        )
                    )
                }
            }
        )
    }

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
                )
            ),
        )
    }
}
