package com.m3u.data.extension.security

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.m3u.data.database.M3UDatabase
import com.m3u.data.database.model.DataSource
import com.m3u.data.database.model.Playlist
import com.m3u.data.database.model.ProviderAccount
import com.m3u.extension.api.security.BrokerValue
import com.m3u.extension.api.security.BrokeredHttpRequest
import com.m3u.extension.api.security.SecretReference
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HostNetworkBrokerSecurityTest {
    @Test
    fun brokerEnforcesPluginAccountOriginAndAuthenticationBoundaries() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, M3UDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val providerDao = database.providerDao()
            database.playlistDao().insertOrReplace(
                Playlist("Provider", PLAYLIST_URL, source = DataSource.Provider)
            )
            providerDao.insertOrReplace(
                ProviderAccount(
                    id = ACCOUNT_ID,
                    providerId = EXTENSION_ID,
                    providerKind = "reference",
                    baseUrl = BASE_URL,
                    serverId = "server",
                    serverName = "Server",
                    serverVersion = "1",
                    userId = "user",
                    username = "name",
                    playlistUrl = PLAYLIST_URL,
                )
            )
            val vault = AndroidKeystoreCredentialVault()
            val credential = vault.encrypt(ACCOUNT_ID, "secret-token")
            providerDao.insertOrReplace(credential)
            database.playlistDao().insertOrReplace(
                Playlist("Other Provider", OTHER_PLAYLIST_URL, source = DataSource.Provider)
            )
            providerDao.insertOrReplace(
                ProviderAccount(
                    id = OTHER_ACCOUNT_ID,
                    providerId = "com.other.provider",
                    providerKind = "reference",
                    baseUrl = BASE_URL,
                    serverId = "other-server",
                    serverName = "Other Server",
                    serverVersion = "1",
                    userId = "other-user",
                    username = "other-name",
                    playlistUrl = OTHER_PLAYLIST_URL,
                )
            )
            val otherCredential = vault.encrypt(OTHER_ACCOUNT_ID, "other-secret")
            providerDao.insertOrReplace(otherCredential)
            var observedAuthorization: String? = null
            val client = OkHttpClient.Builder().addInterceptor { chain ->
                observedAuthorization = chain.request().header("Authorization")
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .header("Set-Cookie", "session=must-not-leak")
                    .body("""{"value":"safe","accessToken":"must-not-leak"}""".toResponseBody())
                    .build()
            }.build()
            val broker = HostNetworkBrokerImpl(client, providerDao, vault)

            assertFails {
                broker.execute("com.other.provider", ACCOUNT_ID, request("$BASE_URL/items"))
            }
            assertFails {
                broker.execute(EXTENSION_ID, ACCOUNT_ID, request("https://evil.example/items"))
            }
            assertFails {
                broker.execute(
                    EXTENSION_ID,
                    ACCOUNT_ID,
                    request(
                        "$BASE_URL/items",
                        headers = mapOf("authorization" to BrokerValue.Literal("Bearer stolen")),
                    ),
                )
            }
            assertFails {
                broker.execute(
                    EXTENSION_ID,
                    ACCOUNT_ID,
                    request(
                        "$BASE_URL/items",
                        headers = mapOf(
                            "Authorization" to BrokerValue.Secret(
                                SecretReference(
                                    com.m3u.extension.api.security.CredentialHandle(
                                        otherCredential.credentialHandle
                                    )
                                )
                            )
                        ),
                    ),
                )
            }

            val response = broker.execute(
                EXTENSION_ID,
                ACCOUNT_ID,
                request(
                    "$BASE_URL/items",
                    headers = mapOf(
                        "Authorization" to BrokerValue.Secret(
                            SecretReference(com.m3u.extension.api.security.CredentialHandle(credential.credentialHandle))
                        )
                    ),
                ),
            )
            assertEquals("secret-token", observedAuthorization)
            assertTrue(response.body.contains("safe"))
            assertFalse(response.body.contains("must-not-leak"))
            assertFalse(response.headers.keys.any { it.equals("Set-Cookie", ignoreCase = true) })
        } finally {
            database.close()
        }
    }

    @Test
    fun brokerRejectsCrossOriginRedirect() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, M3UDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            database.playlistDao().insertOrReplace(
                Playlist("Provider", PLAYLIST_URL, source = DataSource.Provider)
            )
            database.providerDao().insertOrReplace(
                ProviderAccount(
                    ACCOUNT_ID,
                    EXTENSION_ID,
                    "reference",
                    BASE_URL,
                    "server",
                    "Server",
                    "1",
                    "user",
                    "name",
                    PLAYLIST_URL,
                )
            )
            val client = OkHttpClient.Builder().addInterceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(302)
                    .message("Found")
                    .header("Location", "https://evil.example/token")
                    .body("".toResponseBody())
                    .build()
            }.build()
            val broker = HostNetworkBrokerImpl(
                client,
                database.providerDao(),
                AndroidKeystoreCredentialVault(),
            )

            assertFails { broker.execute(EXTENSION_ID, ACCOUNT_ID, request("$BASE_URL/login")) }
        } finally {
            database.close()
        }
    }

    private fun request(
        url: String,
        headers: Map<String, BrokerValue> = emptyMap(),
    ) = BrokeredHttpRequest(method = "GET", url = url, headers = headers)

    private suspend fun assertFails(block: suspend () -> Unit) {
        assertTrue(runCatching { block() }.isFailure)
    }

    private companion object {
        const val EXTENSION_ID = "com.m3u.reference.provider"
        const val ACCOUNT_ID = "account"
        const val OTHER_ACCOUNT_ID = "other-account"
        const val BASE_URL = "https://media.example.test"
        const val PLAYLIST_URL = "m3u-provider://account/account/live"
        const val OTHER_PLAYLIST_URL = "m3u-provider://account/other/live"
    }
}
