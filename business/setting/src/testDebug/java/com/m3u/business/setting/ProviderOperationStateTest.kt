package com.m3u.business.setting

import com.m3u.extension.api.ExtensionId
import com.m3u.extension.api.subscription.ProviderKind
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProviderOperationStateTest {
    @Test
    fun `submission reports a globally busy provider form`() {
        val state = ProviderOperationState(
            submission = ProviderSubmissionOperation(
                providerId = ExtensionId("com.example.provider"),
                providerKind = ProviderKind("example"),
            )
        )

        assertTrue(state.isSubmitting)
        assertTrue(state.isBusy)
        assertFalse(state.isReauthenticating("provider://account/other"))
    }

    @Test
    fun `reauthentication is busy during preparation and submission`() {
        val playlistUrl = "provider://account/one"
        val preparing = ProviderOperationState(
            preparingReauthenticationPlaylistUrl = playlistUrl,
        )
        val submitting = ProviderOperationState(
            submission = ProviderSubmissionOperation(
                providerId = ExtensionId("com.example.provider"),
                providerKind = ProviderKind("example"),
                reauthenticationPlaylistUrl = playlistUrl,
            )
        )

        assertTrue(preparing.isReauthenticating(playlistUrl))
        assertTrue(preparing.isBusy)
        assertTrue(submitting.isReauthenticating(playlistUrl))
        assertTrue(submitting.isBusy)
        assertFalse(submitting.isReauthenticating("provider://account/two"))
    }
}
