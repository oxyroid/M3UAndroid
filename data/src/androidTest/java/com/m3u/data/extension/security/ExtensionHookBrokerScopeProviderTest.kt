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
import com.m3u.extension.api.ExtensionApiRange
import com.m3u.extension.api.ExtensionApiVersions
import com.m3u.extension.api.ExtensionCapabilityIds
import com.m3u.extension.api.ExtensionCapabilityRequest
import com.m3u.extension.api.ExtensionHookDeclaration
import com.m3u.extension.api.ExtensionId
import com.m3u.extension.api.ExtensionManifest
import com.m3u.extension.api.ExtensionSemanticVersion
import com.m3u.extension.api.ExtensionSettingsSnapshot
import com.m3u.extension.api.HostHookSpecs
import com.m3u.extension.api.SearchProviderRequest
import com.m3u.extension.api.security.ContextReference
import com.m3u.extension.api.security.CredentialHandle
import com.m3u.extension.api.subscription.ProviderAccountReference
import com.m3u.extension.api.subscription.ProviderCredential
import com.m3u.extension.api.subscription.ProviderKind
import com.m3u.extension.runtime.ExtensionBrokerScopeRequest
import com.m3u.extension.transport.android.ExtensionTrustStore
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
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
        provider = ExtensionHookBrokerScopeProvider(
            principalRegistry = principalRegistry,
            providerDao = database.providerDao(),
            scopeStore = scopeStore,
            settingStore = ExtensionSettingStore(context, RejectingExtensionSecretStore),
            trustStore = ExtensionTrustStore(context),
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

    private object RejectingExtensionSecretStore : ExtensionSecretStore {
        override fun store(
            extensionId: String,
            settingKey: String,
            secret: String,
            existingHandle: CredentialHandle?,
        ): CredentialHandle = error("Not used")

        override fun resolve(
            extensionId: String,
            handle: CredentialHandle,
        ): String? = error("Extension credentials must not be resolved without credential.read")

        override fun delete(extensionId: String, handle: CredentialHandle) = Unit

        override fun clear(extensionId: String) = Unit
    }

    private companion object {
        const val PLAYLIST_URL = "m3u-provider://account/account-1/live"
        val EXTENSION_ID = ExtensionId("com.example.account.network")
        val CREDENTIAL_HANDLE = CredentialHandle("provider-credential:account-1")
        val PRINCIPAL = ExtensionPrincipal(
            extensionId = EXTENSION_ID,
            packageName = "com.example.account.network",
            serviceName = "com.example.account.network.ExtensionService",
            certificateSha256 = "11".repeat(32),
            uid = 10_001,
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
