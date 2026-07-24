package com.m3u.business.setting

import com.m3u.extension.api.ExtensionId
import com.m3u.extension.api.subscription.ProviderKind

data class ProviderSubmissionOperation(
    val providerId: ExtensionId,
    val providerKind: ProviderKind,
    val reauthenticationPlaylistUrl: String? = null,
)

data class ProviderOperationState(
    val submission: ProviderSubmissionOperation? = null,
    val preparingReauthenticationPlaylistUrl: String? = null,
) {
    val isSubmitting: Boolean
        get() = submission != null

    val isBusy: Boolean
        get() = submission != null || preparingReauthenticationPlaylistUrl != null

    fun isReauthenticating(playlistUrl: String): Boolean =
        preparingReauthenticationPlaylistUrl == playlistUrl ||
            submission?.reauthenticationPlaylistUrl == playlistUrl
}
