package com.m3u.data.extension.security

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.m3u.data.database.M3UDatabase
import com.m3u.data.database.model.DataSource
import com.m3u.data.database.model.Playlist
import com.m3u.data.database.model.ProviderAccount
import com.m3u.data.database.model.ProviderCredentialEntity
import com.m3u.data.repository.extension.ExtensionSettingStore
import com.m3u.extension.api.BackgroundTaskRequest
import com.m3u.extension.api.ExtensionApiRange
import com.m3u.extension.api.ExtensionApiVersions
import com.m3u.extension.api.ExtensionCapabilityIds
import com.m3u.extension.api.ExtensionCapabilityRequest
import com.m3u.extension.api.ExtensionEntrypoint
import com.m3u.extension.api.ExtensionHandler
import com.m3u.extension.api.ExtensionHookDeclaration
import com.m3u.extension.api.ExtensionId
import com.m3u.extension.api.ExtensionManifest
import com.m3u.extension.api.ExtensionNetworkOrigin
import com.m3u.extension.api.ExtensionSemanticVersion
import com.m3u.extension.api.ExtensionSettingField
import com.m3u.extension.api.ExtensionSettingKeys
import com.m3u.extension.api.ExtensionSettingSchema
import com.m3u.extension.api.ExtensionSettingSection
import com.m3u.extension.api.ExtensionSettingType
import com.m3u.extension.api.ExtensionSettingsSnapshot
import com.m3u.extension.api.HostHookSpecs
import com.m3u.extension.api.SearchProviderRequest
import com.m3u.extension.api.SettingsSchemaRequest
import com.m3u.extension.api.security.BrokerScopeHandle
import com.m3u.extension.api.security.ContextReference
import com.m3u.extension.api.security.CredentialHandle
import com.m3u.extension.api.subscription.ProviderAccountReference
import com.m3u.extension.api.subscription.ProviderCredential
import com.m3u.extension.api.subscription.ProviderKind
import com.m3u.extension.runtime.ExtensionBrokerScopeRequest
import com.m3u.extension.runtime.ExtensionRegistrationLease
import com.m3u.extension.runtime.ExtensionRegistrationResult
import com.m3u.extension.runtime.ExtensionRuntime
import com.m3u.extension.transport.android.ExtensionTrustStore
import com.m3u.extension.transport.android.InstalledExtensionService
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExtensionHookBrokerScopeProviderTest {
    private lateinit var database: M3UDatabase
    private lateinit var principalRegistry: ActiveExtensionPrincipalRegistry
    private lateinit var scopeStore: ProviderBrokerScopeStore
    private lateinit var provider: ExtensionHookBrokerScopeProvider
    private lateinit var settingStore: ExtensionSettingStore
    private lateinit var extensionSecretStore: RecordingExtensionSecretStore
    private lateinit var trustStore: ExtensionTrustStore
    private lateinit var registrationLease: ExtensionRegistrationLease

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("extension-trust", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        context.getSharedPreferences("extension-settings", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        database = Room.inMemoryDatabaseBuilder(context, M3UDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        principalRegistry = ActiveExtensionPrincipalRegistry().apply {
            activate(PRINCIPAL)
        }
        var nextScopeId = 0
        scopeStore = ProviderBrokerScopeStore(
            credentialVault = RejectingCredentialVault,
            principalRegistry = principalRegistry,
            clock = { 1_000L },
            idFactory = { "account-network-${nextScopeId++}" },
            defaultTtlMillis = 60_000L,
        )
        extensionSecretStore = RecordingExtensionSecretStore()
        settingStore = ExtensionSettingStore(context, extensionSecretStore)
        val runtime = ExtensionRuntime(ExtensionApiVersions.Current)
        assertTrue(
            runtime.register(
                object : ExtensionEntrypoint {
                    override val manifest = ExtensionManifest(
                        id = NETWORK_EXTENSION_ID,
                        displayName = "Settings lease",
                        extensionVersion = ExtensionSemanticVersion(1, 0, 0),
                        apiRange = ExtensionApiRange(
                            ExtensionApiVersions.Current,
                            ExtensionApiVersions.Current,
                        ),
                        hooks = emptySet(),
                        capabilities = emptySet(),
                    )
                    override val handlers: Collection<ExtensionHandler<*, *>> = emptyList()
                }
            ) is ExtensionRegistrationResult.Registered
        )
        registrationLease = requireNotNull(
            runtime.captureRegistration(NETWORK_EXTENSION_ID)
        ).lease
        trustStore = ExtensionTrustStore(context)
        provider = ExtensionHookBrokerScopeProvider(
            principalRegistry = principalRegistry,
            providerDao = database.providerDao(),
            scopeStore = scopeStore,
            settingStore = settingStore,
            trustStore = trustStore,
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun accountHookWithoutCredentialReadReceivesOriginOnlyBrokerScope() {
        runBlocking {
            database.playlistDao().insertOrReplace(
                Playlist(
                    title = "Provider",
                    url = PLAYLIST_URL,
                    source = DataSource.Provider,
                )
            )
            database.providerDao().insertOrReplace(ACCOUNT)
            database.providerDao().insertOrReplace(CREDENTIAL)

            val lease = provider.open(
                ExtensionBrokerScopeRequest(
                    manifest = MANIFEST,
                    hook = HostHookSpecs.SearchProvider.hook,
                    payload = SearchProviderRequest(
                        query = "news",
                        account = ACCOUNT_REFERENCE,
                        credential = ProviderCredential(CREDENTIAL_HANDLE),
                    ),
                    settings = ExtensionSettingsSnapshot(),
                    grantedCapabilities = setOf(
                        ExtensionCapabilityIds.SearchRead,
                        ExtensionCapabilityIds.Network,
                    ),
                )
            )

            assertNotNull(lease)
            val handle = requireNotNull(lease).handle
            val access = scopeStore.authorize(
                handle,
                PRINCIPAL,
                HostHookSpecs.SearchProvider.hook,
            )
            assertEquals(ProviderBrokerScopeKind.ACCOUNT, access.kind)
            assertEquals(ACCOUNT.id, access.accountId)
            assertEquals(setOf("https://media.example.test:443"), access.approvedOrigins)
            assertTrue(access.credentialHandles.isEmpty())
            assertTrue(access.opaqueContextKeys.isEmpty())
            assertThrows(SecurityException::class.java) {
                scopeStore.resolveCredential(
                    handle,
                    PRINCIPAL,
                    HostHookSpecs.SearchProvider.hook,
                    CREDENTIAL_HANDLE,
                )
            }
            assertThrows(SecurityException::class.java) {
                scopeStore.resolveContext(
                    handle,
                    PRINCIPAL,
                    HostHookSpecs.SearchProvider.hook,
                    ContextReference("user_id"),
                )
            }

            lease.close()
            assertThrows(SecurityException::class.java) {
                scopeStore.authorize(
                    handle,
                    PRINCIPAL,
                    HostHookSpecs.SearchProvider.hook,
                )
            }
        }
    }

    @Test
    fun settingsSchemaCannotBootstrapFromAnApprovedSettingOrigin() = runBlocking {
        val settings = prepareApprovedDynamicOrigin()
        trustNetworkManifest()

        val lease = provider.open(
            ExtensionBrokerScopeRequest(
                manifest = NETWORK_MANIFEST,
                hook = HostHookSpecs.SettingsSchema.hook,
                payload = SettingsSchemaRequest(localeTag = "en-US", surface = "phone"),
                settings = settings,
                grantedCapabilities = setOf(
                    ExtensionCapabilityIds.Network,
                    ExtensionCapabilityIds.SettingsContribute,
                ),
            )
        )

        val access = scopeStore.authorize(
            requireNotNull(lease).handle,
            PRINCIPAL,
            HostHookSpecs.SettingsSchema.hook,
        )
        assertEquals(setOf(FIXED_ORIGIN), access.approvedOrigins)
        lease.close()
    }

    @Test
    fun generalHookUsesSettingOriginOnlyForTheSoleTrustedOwner() = runBlocking {
        val settings = prepareApprovedDynamicOrigin()
        trustNetworkManifest()

        val lease = requireNotNull(
            provider.open(
                ExtensionBrokerScopeRequest(
                    manifest = NETWORK_MANIFEST,
                    hook = HostHookSpecs.BackgroundTask.hook,
                    payload = BackgroundTaskRequest(taskId = "refresh"),
                    settings = settings,
                    grantedCapabilities = setOf(
                        ExtensionCapabilityIds.BackgroundTask,
                        ExtensionCapabilityIds.Network,
                    ),
                )
            )
        )
        val access = scopeStore.authorize(
            lease.handle,
            PRINCIPAL,
            HostHookSpecs.BackgroundTask.hook,
        )
        assertEquals(setOf(FIXED_ORIGIN, DYNAMIC_ORIGIN), access.approvedOrigins)
        lease.close()

        trustStore.trust(
            service = OTHER_SERVICE,
            extensionId = NETWORK_EXTENSION_ID.value,
            capabilities = setOf(ExtensionCapabilityIds.Network.id),
            displayName = "Duplicate owner",
            version = "1.0.0",
            developer = null,
        )

        assertNull(
            provider.open(
                ExtensionBrokerScopeRequest(
                    manifest = NETWORK_MANIFEST,
                    hook = HostHookSpecs.BackgroundTask.hook,
                    payload = BackgroundTaskRequest(taskId = "refresh"),
                    settings = settings,
                    grantedCapabilities = setOf(
                        ExtensionCapabilityIds.BackgroundTask,
                        ExtensionCapabilityIds.Network,
                    ),
                )
            )
        )
    }

    @Test
    fun principalRevokedDuringScopeMintCannotReceiveOrRetainBrokerScope() = runBlocking {
        trustNetworkManifest()
        val mintedScope = BrokerScopeHandle("broker-scope:revoked-during-mint")
        var mintStarted = false
        scopeStore = ProviderBrokerScopeStore(
            credentialVault = RejectingCredentialVault,
            principalRegistry = principalRegistry,
            clock = { 1_000L },
            idFactory = {
                mintStarted = true
                assertEquals(
                    PRINCIPAL,
                    principalRegistry.deactivate(
                        extensionId = NETWORK_EXTENSION_ID,
                        packageName = PRINCIPAL.packageName,
                        serviceName = PRINCIPAL.serviceName,
                    ),
                )
                "revoked-during-mint"
            },
            defaultTtlMillis = 60_000L,
        )
        provider = ExtensionHookBrokerScopeProvider(
            principalRegistry = principalRegistry,
            providerDao = database.providerDao(),
            scopeStore = scopeStore,
            settingStore = settingStore,
            trustStore = trustStore,
        )

        val lease = provider.open(
            ExtensionBrokerScopeRequest(
                manifest = NETWORK_MANIFEST,
                hook = HostHookSpecs.BackgroundTask.hook,
                payload = BackgroundTaskRequest(taskId = "refresh"),
                settings = ExtensionSettingsSnapshot(),
                grantedCapabilities = setOf(
                    ExtensionCapabilityIds.BackgroundTask,
                    ExtensionCapabilityIds.Network,
                ),
            )
        )

        assertTrue(mintStarted)
        assertNull(lease)
        assertFalse(scopeStore.close(mintedScope))
        principalRegistry.activate(PRINCIPAL)
        assertThrows(SecurityException::class.java) {
            scopeStore.authorize(
                mintedScope,
                PRINCIPAL,
                HostHookSpecs.BackgroundTask.hook,
            )
        }
        Unit
    }

    @Test
    fun suspendedDynamicCredentialIsNotResolvedFromAnEarlierInvocationSnapshot() = runBlocking {
        trustNetworkManifest()
        val section = NETWORK_SECTION.copy(
            schema = NETWORK_SECTION.schema.copy(
                fields = NETWORK_SECTION.schema.fields +
                    ExtensionSettingField(
                        key = "token",
                        label = "Token",
                        type = ExtensionSettingType.SECRET,
                    ),
            ),
        )
        val session = settingStore.beginDynamicSchemaSession(
            NETWORK_EXTENSION_ID.value,
            registrationLease,
        )
        val validation = requireNotNull(
            settingStore.beginDynamicSchemaValidation(session, "phone")
        )
        assertTrue(
            settingStore.revalidateDynamicSchemas(
                extensionId = NETWORK_EXTENSION_ID.value,
                validation = validation,
                sections = listOf(section),
            )
        )
        val settingKey = ExtensionSettingKeys.qualified(section.id, "token")
        val handle = extensionSecretStore.store(
            extensionId = NETWORK_EXTENSION_ID.value,
            settingKey = settingKey,
            secret = "dynamic-secret",
            existingHandle = null,
        )
        requireNotNull(
            settingStore.mutateIfSchema(
                extensionId = NETWORK_EXTENSION_ID.value,
                expectedSession = session,
                sectionId = section.id,
                expectedSchemaVersion = section.schema.version,
                expectedSchemaFingerprint = settingStore.stableSchemaFingerprint(section),
                settingKey = settingKey,
                approvedOrigin = null,
            ) { current ->
                current.copy(
                    credentialHandles = current.credentialHandles + (settingKey to handle)
                )
            }
        )
        val capturedSettings = settingStore.snapshot(NETWORK_MANIFEST)
        val resolvesBeforeSuspension = extensionSecretStore.resolveCount
        settingStore.suspendDynamicSchemas(NETWORK_EXTENSION_ID.value)
        val credentialManifest = NETWORK_MANIFEST.copy(
            hooks = NETWORK_MANIFEST.hooks.mapTo(linkedSetOf()) { declaration ->
                if (declaration.hook == HostHookSpecs.BackgroundTask.hook) {
                    declaration.copy(
                        requiredCapabilities = declaration.requiredCapabilities +
                            ExtensionCapabilityIds.CredentialRead,
                    )
                } else {
                    declaration
                }
            },
            capabilities = NETWORK_MANIFEST.capabilities +
                ExtensionCapabilityRequest(
                    ExtensionCapabilityIds.CredentialRead,
                    "Use saved credentials",
                ),
        )

        val lease = provider.open(
            ExtensionBrokerScopeRequest(
                manifest = credentialManifest,
                hook = HostHookSpecs.BackgroundTask.hook,
                payload = BackgroundTaskRequest(taskId = "refresh"),
                settings = capturedSettings,
                grantedCapabilities = setOf(
                    ExtensionCapabilityIds.BackgroundTask,
                    ExtensionCapabilityIds.Network,
                    ExtensionCapabilityIds.CredentialRead,
                ),
            )
        )

        assertNotNull(lease)
        assertEquals(resolvesBeforeSuspension, extensionSecretStore.resolveCount)
        requireNotNull(lease).close()
    }

    private fun prepareApprovedDynamicOrigin(): ExtensionSettingsSnapshot {
        val session = settingStore.beginDynamicSchemaSession(
            NETWORK_EXTENSION_ID.value,
            registrationLease,
        )
        val validation = requireNotNull(
            settingStore.beginDynamicSchemaValidation(
                session = session,
                surface = "phone",
            )
        )
        assertTrue(
            settingStore.revalidateDynamicSchemas(
                extensionId = NETWORK_EXTENSION_ID.value,
                validation = validation,
                sections = listOf(NETWORK_SECTION),
            )
        )
        val key = ExtensionSettingKeys.qualified(NETWORK_SECTION.id, "origin")
        val updated = requireNotNull(
            settingStore.mutateIfSchema(
                extensionId = NETWORK_EXTENSION_ID.value,
                expectedSession = session,
                sectionId = NETWORK_SECTION.id,
                expectedSchemaVersion = NETWORK_SECTION.schema.version,
                expectedSchemaFingerprint = settingStore.stableSchemaFingerprint(NETWORK_SECTION),
                settingKey = key,
                approvedOrigin = DYNAMIC_ORIGIN,
            ) { current ->
                current.copy(values = current.values + (key to JsonPrimitive(DYNAMIC_ORIGIN)))
            }
        )
        assertEquals(setOf(DYNAMIC_ORIGIN), settingStore.approvedSettingOrigins(
            NETWORK_EXTENSION_ID.value,
            updated,
        ))
        return settingStore.snapshot(NETWORK_MANIFEST)
    }

    private fun trustNetworkManifest() {
        trustStore.trust(
            service = NETWORK_SERVICE,
            extensionId = NETWORK_EXTENSION_ID.value,
            capabilities = setOf(
                ExtensionCapabilityIds.BackgroundTask.id,
                ExtensionCapabilityIds.Network.id,
                ExtensionCapabilityIds.SettingsContribute.id,
            ),
            displayName = NETWORK_MANIFEST.displayName,
            version = NETWORK_MANIFEST.extensionVersion.toString(),
            developer = null,
            networkOrigins = setOf(FIXED_ORIGIN),
        )
    }

    private object RejectingCredentialVault : CredentialVault {
        override fun encrypt(
            accountId: String,
            secret: String,
            credentialHandle: String?,
        ): ProviderCredentialEntity = error("Not used")

        override fun decrypt(credential: ProviderCredentialEntity): String? =
            error("Credential material must not be decrypted without credential.read")

        override fun stage(secret: String): CredentialHandle = error("Not used")

        override fun consume(handle: CredentialHandle): String? = error("Not used")
    }

    private class RecordingExtensionSecretStore : ExtensionSecretStore {
        private val secrets = mutableMapOf<CredentialHandle, String>()
        var resolveCount = 0
            private set

        override fun store(
            extensionId: String,
            settingKey: String,
            secret: String,
            existingHandle: CredentialHandle?,
        ): CredentialHandle {
            val handle = existingHandle ?: CredentialHandle("setting:$settingKey")
            secrets[handle] = secret
            return handle
        }

        override fun resolve(
            extensionId: String,
            handle: CredentialHandle,
        ): String? {
            resolveCount++
            return secrets[handle]
        }

        override fun delete(extensionId: String, handle: CredentialHandle) {
            secrets.remove(handle)
        }

        override fun clear(extensionId: String) {
            secrets.clear()
        }
    }

    private companion object {
        const val PLAYLIST_URL = "m3u-provider://account/account-1/live"
        val EXTENSION_ID = ExtensionId("com.example.account.network")
        val NETWORK_EXTENSION_ID = EXTENSION_ID
        val CREDENTIAL_HANDLE = CredentialHandle("provider-credential:account-1")
        val PRINCIPAL = ExtensionPrincipal(
            extensionId = EXTENSION_ID,
            packageName = "com.example.account.network",
            serviceName = "com.example.account.network.ExtensionService",
            certificateSha256 = "11".repeat(32),
            uid = 10_001,
        )
        val NETWORK_SERVICE = InstalledExtensionService(
            packageName = PRINCIPAL.packageName,
            serviceName = PRINCIPAL.serviceName,
            certificateSha256 = PRINCIPAL.certificateSha256,
            uid = PRINCIPAL.uid,
        )
        val OTHER_SERVICE = InstalledExtensionService(
            packageName = "com.example.settings.other",
            serviceName = "com.example.settings.other.ExtensionService",
            certificateSha256 = "22".repeat(32),
            uid = 10_002,
        )
        const val FIXED_ORIGIN = "https://fixed.example.test:443"
        const val DYNAMIC_ORIGIN = "https://dynamic.example.test:443"
        val NETWORK_SECTION = ExtensionSettingSection(
            id = "network",
            title = "Network",
            schema = ExtensionSettingSchema(
                version = 1,
                fields = listOf(
                    ExtensionSettingField(
                        key = "origin",
                        label = "Origin",
                        type = ExtensionSettingType.TEXT,
                        networkOrigin = true,
                    )
                ),
            ),
        )
        val NETWORK_MANIFEST = ExtensionManifest(
            id = NETWORK_EXTENSION_ID,
            displayName = "Settings network test",
            extensionVersion = ExtensionSemanticVersion(1, 0, 0),
            apiRange = ExtensionApiRange(
                ExtensionApiVersions.Current,
                ExtensionApiVersions.Current,
            ),
            hooks = setOf(
                ExtensionHookDeclaration(
                    hook = HostHookSpecs.SettingsSchema.hook,
                    schemaVersion = HostHookSpecs.SettingsSchema.schemaVersion,
                    requiredCapabilities = setOf(
                        ExtensionCapabilityIds.SettingsContribute,
                        ExtensionCapabilityIds.Network,
                    ),
                ),
                ExtensionHookDeclaration(
                    hook = HostHookSpecs.BackgroundTask.hook,
                    schemaVersion = HostHookSpecs.BackgroundTask.schemaVersion,
                    requiredCapabilities = setOf(
                        ExtensionCapabilityIds.BackgroundTask,
                        ExtensionCapabilityIds.Network,
                    ),
                ),
            ),
            capabilities = setOf(
                ExtensionCapabilityRequest(
                    ExtensionCapabilityIds.BackgroundTask,
                    "Run background work",
                ),
                ExtensionCapabilityRequest(
                    ExtensionCapabilityIds.SettingsContribute,
                    "Contribute settings",
                ),
                ExtensionCapabilityRequest(
                    ExtensionCapabilityIds.Network,
                    "Reach approved origins",
                ),
            ),
            networkOrigins = setOf(ExtensionNetworkOrigin(FIXED_ORIGIN)),
        )
        val ACCOUNT = ProviderAccount(
            id = "account-1",
            providerId = EXTENSION_ID.value,
            providerKind = "example",
            baseUrl = "https://media.example.test/library",
            serverId = "server-1",
            serverName = "Example server",
            serverVersion = "1.0",
            userId = "user-1",
            username = "viewer",
            playlistUrl = PLAYLIST_URL,
            ownerPackageName = PRINCIPAL.packageName,
            ownerServiceName = PRINCIPAL.serviceName,
            ownerCertificateSha256 = PRINCIPAL.certificateSha256,
        )
        val ACCOUNT_REFERENCE = ProviderAccountReference(
            accountId = ACCOUNT.id,
            providerId = EXTENSION_ID,
            providerKind = ProviderKind(ACCOUNT.providerKind),
            baseUrl = ACCOUNT.baseUrl,
            serverId = ACCOUNT.serverId,
            serverName = ACCOUNT.serverName,
            serverVersion = ACCOUNT.serverVersion,
            userId = ACCOUNT.userId,
            username = ACCOUNT.username,
        )
        val CREDENTIAL = ProviderCredentialEntity(
            accountId = ACCOUNT.id,
            credentialHandle = CREDENTIAL_HANDLE.value,
            ciphertext = "ciphertext",
            nonce = "nonce",
            keyVersion = 1,
        )
        val MANIFEST = ExtensionManifest(
            id = EXTENSION_ID,
            displayName = "Account network test",
            extensionVersion = ExtensionSemanticVersion(1, 0, 0),
            apiRange = ExtensionApiRange(
                ExtensionApiVersions.Current,
                ExtensionApiVersions.Current,
            ),
            hooks = setOf(
                ExtensionHookDeclaration(
                    hook = HostHookSpecs.SearchProvider.hook,
                    schemaVersion = HostHookSpecs.SearchProvider.schemaVersion,
                    requiredCapabilities = setOf(
                        ExtensionCapabilityIds.SearchRead,
                        ExtensionCapabilityIds.Network,
                    ),
                )
            ),
            capabilities = setOf(
                ExtensionCapabilityRequest(
                    ExtensionCapabilityIds.SearchRead,
                    "Search this account",
                ),
                ExtensionCapabilityRequest(
                    ExtensionCapabilityIds.Network,
                    "Reach this account server",
                ),
            ),
        )
    }
}
