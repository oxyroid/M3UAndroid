package com.m3u.data.extension.artwork

import android.util.Base64
import com.m3u.data.database.model.ProviderAccount
import com.m3u.data.extension.emby.EmbyCompatibleProvider
import com.m3u.extension.api.subscription.EmbyCompatibleProviderKinds
import java.nio.charset.StandardCharsets
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

internal data class ProviderArtworkReference(
    val accountId: String,
    val encodedPath: String,
    val imageTag: String,
)

internal object ProviderArtworkReferences {
    fun create(
        account: ProviderAccount,
        remoteUrl: String?,
    ): String? {
        if (remoteUrl == null || !account.hasBuiltInOwner()) return null
        if (!account.isEmbyCompatibleProvider()) return remoteUrl

        val baseUrl = account.baseUrl.toHttpUrl()
        val artworkUrl = remoteUrl.toHttpUrl()
        validateRemoteUrl(baseUrl, artworkUrl)
        val imageTag = checkNotNull(
            artworkUrl.queryParameterValues(PROVIDER_ARTWORK_TAG_QUERY).singleOrNull()
        )
        return buildString {
            append(PREFIX)
            append(account.id.encodeReferencePart())
            append(SEPARATOR)
            append(artworkUrl.encodedPath.encodeReferencePart())
            append(SEPARATOR)
            append(imageTag.encodeReferencePart())
        }
    }

    fun isValidRemoteUrl(baseUrl: String, remoteUrl: String): Boolean = runCatching {
        validateRemoteUrl(baseUrl.toHttpUrl(), remoteUrl.toHttpUrl())
    }.isSuccess

    private fun validateRemoteUrl(baseUrl: HttpUrl, artworkUrl: HttpUrl) {
        require(
            baseUrl.scheme in setOf("http", "https") &&
                baseUrl.username.isEmpty() &&
                baseUrl.password.isEmpty() &&
                baseUrl.query == null &&
                baseUrl.fragment == null
        ) {
            "Provider artwork account origin is invalid"
        }
        require(baseUrl.hasSameOriginAs(artworkUrl)) {
            "Provider artwork must use the account origin"
        }
        require(artworkUrl.username.isEmpty() && artworkUrl.password.isEmpty()) {
            "Provider artwork URL must contain no user information"
        }
        require(artworkUrl.fragment == null) {
            "Provider artwork URL must contain no fragment"
        }
        require(artworkUrl.queryParameterNames == setOf(PROVIDER_ARTWORK_TAG_QUERY)) {
            "Provider artwork URL must contain only its image tag"
        }
        artworkUrl.queryParameterValues(PROVIDER_ARTWORK_TAG_QUERY)
            .singleOrNull()
            ?.takeIf { value -> value.isValidImageTag() }
            ?: throw IllegalArgumentException("Provider artwork image tag is invalid")
    }

    fun parse(value: String): ProviderArtworkReference? {
        if (!value.startsWith(PREFIX)) return null
        val payload = value.removePrefix(PREFIX)
        val parts = payload.split(SEPARATOR)
        require(parts.size == REFERENCE_PART_COUNT && parts.all(String::isNotEmpty)) {
            "Provider artwork reference is malformed"
        }
        val accountId = parts[0].decodeReferencePart()
        val encodedPath = parts[1].decodeReferencePart()
        val imageTag = parts[2].decodeReferencePart()
        require(accountId.isNotBlank() && accountId.length <= MAX_ACCOUNT_ID_LENGTH) {
            "Provider artwork account is invalid"
        }
        require(
            encodedPath.startsWith('/') &&
                encodedPath.length <= MAX_ENCODED_PATH_LENGTH
        ) {
            "Provider artwork path is invalid"
        }
        require(imageTag.isValidImageTag()) {
            "Provider artwork image tag is invalid"
        }
        return ProviderArtworkReference(
            accountId = accountId,
            encodedPath = encodedPath,
            imageTag = imageTag,
        )
    }

    fun isReference(value: String): Boolean = value.startsWith(PREFIX)

    private fun String.encodeReferencePart(): String = Base64.encodeToString(
        toByteArray(StandardCharsets.UTF_8),
        Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING,
    )

    private fun String.decodeReferencePart(): String = String(
        Base64.decode(
            this,
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING,
        ),
        StandardCharsets.UTF_8,
    )

    private fun String.isValidImageTag(): Boolean =
        isNotBlank() &&
            encodeToByteArray().size <= MAX_IMAGE_TAG_UTF8_BYTES &&
            none(Char::isISOControl)

    private const val PREFIX = "m3u-provider-artwork:"
    private const val SEPARATOR = '.'
    private const val REFERENCE_PART_COUNT = 3
    private const val MAX_ACCOUNT_ID_LENGTH = 512
    private const val MAX_ENCODED_PATH_LENGTH = 8_192
    private const val MAX_IMAGE_TAG_UTF8_BYTES = 512
}

internal const val PROVIDER_ARTWORK_TAG_QUERY = "tag"

internal fun ProviderAccount.isEmbyCompatibleProvider(): Boolean =
    providerId == EmbyCompatibleProvider.ID.value &&
        providerKind in setOf(
            EmbyCompatibleProviderKinds.Emby.value,
            EmbyCompatibleProviderKinds.Jellyfin.value,
        )

internal fun ProviderAccount.hasBuiltInOwner(): Boolean =
    ownerPackageName == null &&
        ownerServiceName == null &&
        ownerCertificateSha256 == null

internal fun HttpUrl.hasSameOriginAs(other: HttpUrl): Boolean =
    scheme == other.scheme && host == other.host && port == other.port
