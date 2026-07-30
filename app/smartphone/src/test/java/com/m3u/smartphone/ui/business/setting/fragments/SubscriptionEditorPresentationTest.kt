package com.m3u.smartphone.ui.business.setting.fragments

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SubscriptionEditorPresentationTest {
    @Test
    fun `reauthentication preparation owns the busy notice`() {
        assertFalse(
            shouldShowPlaylistMaintenanceNotice(
                operationInProgress = true,
                providerSubmissionInProgress = false,
                providerReauthenticationInProgress = true,
            )
        )
    }

    @Test
    fun `generic maintenance is shown only for an unrelated busy operation`() {
        assertTrue(
            shouldShowPlaylistMaintenanceNotice(
                operationInProgress = true,
                providerSubmissionInProgress = false,
                providerReauthenticationInProgress = false,
            )
        )
        assertFalse(
            shouldShowPlaylistMaintenanceNotice(
                operationInProgress = true,
                providerSubmissionInProgress = true,
                providerReauthenticationInProgress = false,
            )
        )
        assertFalse(
            shouldShowPlaylistMaintenanceNotice(
                operationInProgress = false,
                providerSubmissionInProgress = false,
                providerReauthenticationInProgress = false,
            )
        )
    }
}
