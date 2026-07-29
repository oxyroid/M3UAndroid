package com.m3u.data.extension.artwork

import android.net.Uri
import coil.ImageLoader
import coil.decode.DataSource
import coil.decode.ImageSource
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.fetch.SourceResult
import coil.request.Options
import com.m3u.data.api.ProviderOkhttpClient
import com.m3u.data.database.dao.ProviderDao
import com.m3u.data.extension.security.CredentialVault
import com.m3u.extension.api.subscription.EmbyCompatibleProviderKinds
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okio.Buffer

@Singleton
class ProviderArtworkFetcherFactory internal constructor(
    private val accessResolver: ProviderArtworkAccessResolver,
    okHttpClient: OkHttpClient,
) : Fetcher.Factory<Uri> {
    @Inject
    constructor(
        providerDao: ProviderDao,
        credentialVault: CredentialVault,
        @ProviderOkhttpClient okHttpClient: OkHttpClient,
    ) : this(
        accessResolver = RoomProviderArtworkAccessResolver(
            providerDao = providerDao,
            credentialVault = credentialVault,
        ),
        okHttpClient = okHttpClient,
    )

    private val artworkClient = okHttpClient.newBuilder()
        .followRedirects(false)
        .followSslRedirects(false)
        .callTimeout(ARTWORK_CALL_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
        .build()

    override fun create(
        data: Uri,
        options: Options,
        imageLoader: ImageLoader,
    ): Fetcher? {
        val serializedReference = data.toString()
        if (!ProviderArtworkReferences.isReference(serializedReference)) return null
        val reference = runCatching {
            checkNotNull(ProviderArtworkReferences.parse(serializedReference))
        }.getOrElse { failure ->
            return Fetcher {
                throw IOException("Provider artwork reference is invalid", failure)
            }
        }
        return ProviderArtworkFetcher(
            reference = reference,
            options = options,
            accessResolver = accessResolver,
            okHttpClient = artworkClient,
        )
    }
}

private class ProviderArtworkFetcher(
    private val reference: ProviderArtworkReference,
    private val options: Options,
    private val accessResolver: ProviderArtworkAccessResolver,
    private val okHttpClient: OkHttpClient,
) : Fetcher {
    override suspend fun fetch(): FetchResult = withContext(Dispatchers.IO) {
        if (!options.networkCachePolicy.readEnabled) {
            throw IOException("Provider artwork network access is disabled")
        }
        val access = accessResolver.resolve(reference.accountId)
            ?: throw IOException("Provider artwork account is unavailable")
        val baseUrl = access.baseUrl.toHttpUrlOrNull()
            ?: throw IOException("Provider artwork account origin is invalid")
        requireSafeBaseUrl(baseUrl)
        var target = baseUrl.newBuilder()
            .encodedPath(reference.encodedPath)
            .query(null)
            .fragment(null)
            .addQueryParameter(PROVIDER_ARTWORK_TAG_QUERY, reference.imageTag)
            .build()
        if (!target.hasSameOriginAs(baseUrl)) {
            throw IOException("Provider artwork cannot leave its account origin")
        }

        val accessToken = access.accessToken.requireSafeHeaderToken()
        for (redirectCount in 0..MAX_REDIRECTS) {
            currentCoroutineContext().ensureActive()
            val response = okHttpClient.newCall(
                Request.Builder()
                    .url(target)
                    // Header authentication keeps secrets out of URLs and persisted references.
                    .apply {
                        when (access.providerKind) {
                            EmbyCompatibleProviderKinds.Emby.value ->
                                header(EMBY_TOKEN_HEADER, accessToken)

                            EmbyCompatibleProviderKinds.Jellyfin.value ->
                                header(
                                    AUTHORIZATION_HEADER,
                                    "$MEDIA_BROWSER_SCHEME Token=\"$accessToken\"",
                                )

                            else -> throw IOException(
                                "Provider artwork account kind is unsupported"
                            )
                        }
                    }
                    .get()
                    .build()
            ).execute()

            if (response.isRedirect) {
                val nextTarget = response.use { redirect ->
                    redirect.header(LOCATION_HEADER)
                        ?.let(redirect.request.url::resolve)
                        ?: throw IOException("Provider artwork redirect is invalid")
                }
                if (!nextTarget.hasSameOriginAs(baseUrl)) {
                    throw IOException("Provider artwork cannot redirect outside its account origin")
                }
                if (redirectCount == MAX_REDIRECTS) {
                    throw IOException("Provider artwork redirected too many times")
                }
                target = nextTarget
                continue
            }

            return@withContext response.use(::readArtwork)
        }
        throw IOException("Provider artwork redirected too many times")
    }

    private fun readArtwork(response: Response): SourceResult {
        if (!response.isSuccessful) {
            throw IOException("Provider artwork request failed with HTTP ${response.code}")
        }
        val body = response.body
        val contentLength = body.contentLength()
        if (contentLength > MAX_ARTWORK_RESPONSE_BYTES) {
            throw artworkTooLarge()
        }
        val mimeType = body.contentType()?.toString()?.substringBefore(';')
        if (
            mimeType != null &&
            !mimeType.startsWith(IMAGE_MIME_PREFIX) &&
            mimeType != BINARY_MIME_TYPE
        ) {
            throw IOException("Provider artwork response is not an image")
        }

        val source = body.source()
        val buffer = Buffer()
        val readLimit = MAX_ARTWORK_RESPONSE_BYTES.toLong() + 1L
        while (buffer.size < readLimit) {
            val read = source.read(
                buffer,
                minOf(READ_BUFFER_BYTES, readLimit - buffer.size),
            )
            if (read == -1L) break
        }
        if (buffer.size > MAX_ARTWORK_RESPONSE_BYTES) {
            throw artworkTooLarge()
        }
        if (buffer.size == 0L) {
            throw IOException("Provider artwork response is empty")
        }
        return SourceResult(
            source = ImageSource(buffer, options.context),
            mimeType = mimeType,
            dataSource = DataSource.NETWORK,
        )
    }

    private fun requireSafeBaseUrl(baseUrl: HttpUrl) {
        if (
            baseUrl.scheme !in setOf("http", "https") ||
            baseUrl.username.isNotEmpty() ||
            baseUrl.password.isNotEmpty() ||
            baseUrl.query != null ||
            baseUrl.fragment != null
        ) {
            throw IOException("Provider artwork account origin is invalid")
        }
    }

    private fun artworkTooLarge() =
        IOException("Provider artwork response exceeds the host limit")

    private fun String.requireSafeHeaderToken(): String {
        if (
            length > MAX_HEADER_TOKEN_LENGTH ||
            any { character ->
                character == '"' || character.code !in 0x21..0x7e
            }
        ) {
            throw IOException("Provider artwork credential is invalid")
        }
        return this
    }
}

internal fun interface ProviderArtworkAccessResolver {
    suspend fun resolve(accountId: String): ProviderArtworkAccess?
}

internal data class ProviderArtworkAccess(
    val baseUrl: String,
    val providerKind: String,
    val accessToken: String,
)

private class RoomProviderArtworkAccessResolver(
    private val providerDao: ProviderDao,
    private val credentialVault: CredentialVault,
) : ProviderArtworkAccessResolver {
    override suspend fun resolve(accountId: String): ProviderArtworkAccess? {
        val account = providerDao.getAccount(accountId)
            ?.takeIf { stored ->
                stored.id == accountId &&
                    !stored.requiresReauthentication &&
                    stored.hasBuiltInOwner() &&
                    stored.isEmbyCompatibleProvider()
            }
            ?: return null
        val credential = providerDao.getCredential(account.id) ?: return null
        val accessToken = credentialVault.decrypt(credential)
            ?.takeIf(String::isNotBlank)
            ?: return null
        return ProviderArtworkAccess(
            baseUrl = account.baseUrl,
            providerKind = account.providerKind,
            accessToken = accessToken,
        )
    }
}

internal const val MAX_ARTWORK_RESPONSE_BYTES = 8 * 1024 * 1024
private const val ARTWORK_CALL_TIMEOUT_MILLIS = 15_000L
private const val READ_BUFFER_BYTES = 8_192L
private const val MAX_REDIRECTS = 3
private const val MAX_HEADER_TOKEN_LENGTH = 8_192
private const val EMBY_TOKEN_HEADER = "X-Emby-Token"
private const val AUTHORIZATION_HEADER = "Authorization"
private const val MEDIA_BROWSER_SCHEME = "MediaBrowser"
private const val LOCATION_HEADER = "Location"
private const val IMAGE_MIME_PREFIX = "image/"
private const val BINARY_MIME_TYPE = "application/octet-stream"
