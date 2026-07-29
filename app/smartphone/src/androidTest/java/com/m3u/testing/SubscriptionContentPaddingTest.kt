package com.m3u.testing

import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.m3u.i18n.R.string
import com.m3u.smartphone.MainActivity
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SubscriptionContentPaddingTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun overviewRestoreActionCanScrollAboveTheSystemSafeArea() {
        openPlaylistManagementOverview()

        val restoreAction = hasClickAction() and hasTestTag(RESTORE_ACTION_TAG)
        val content = composeRule.onNodeWithTag(OVERVIEW_TAG)
        content.performScrollToNode(restoreAction)
        content.performTouchInput { swipeUp() }
        composeRule.waitForIdle()

        val actionBounds = composeRule.onNodeWithTag(RESTORE_ACTION_TAG)
            .fetchSemanticsNode()
            .boundsInWindow
        val rootBottom = composeRule.onRoot().fetchSemanticsNode().boundsInWindow.bottom
        val safeBottom = composeRule.runOnIdle {
            val activity = composeRule.activity
            val inset = ViewCompat.getRootWindowInsets(activity.window.decorView)
                ?.getInsets(
                    WindowInsetsCompat.Type.systemBars() or
                        WindowInsetsCompat.Type.displayCutout()
                )
                ?.bottom
                ?: 0
            val gap = MINIMUM_PROVIDER_BOTTOM_GAP_DP *
                activity.resources.displayMetrics.density
            rootBottom - inset - gap
        }

        assertTrue(
            "The playlist restore action remains behind the system safe area: " +
                "action=$actionBounds, safeBottom=$safeBottom",
            actionBounds.bottom <= safeBottom,
        )
    }

    private fun openPlaylistManagementOverview() {
        val playlistManagement =
            composeRule.activity.getString(string.feat_setting_playlist_management)
        waitUntilExists(
            hasText(playlistManagement, substring = false, ignoreCase = true) or
                hasContentDescription(
                    composeRule.activity.getString(string.ui_destination_setting),
                    substring = false,
                    ignoreCase = true,
                )
        )
        if (
            composeRule.onAllNodes(
                hasText(playlistManagement, substring = false, ignoreCase = true) and
                    hasClickAction()
            ).fetchSemanticsNodes().isEmpty()
        ) {
            composeRule.onNode(
                hasContentDescription(
                    composeRule.activity.getString(string.ui_destination_setting),
                    substring = false,
                    ignoreCase = true,
                ) and hasClickAction()
            ).performClick()
        }
        waitUntilExists(
            hasText(playlistManagement, substring = false, ignoreCase = true) and
                hasClickAction()
        )
        composeRule.onNode(
            hasText(playlistManagement, substring = false, ignoreCase = true) and
                hasClickAction()
        ).performClick()
        waitUntilExists(hasTestTag(OVERVIEW_TAG))
    }

    private fun waitUntilExists(matcher: SemanticsMatcher) {
        composeRule.waitUntil(UI_TIMEOUT_MILLIS) {
            composeRule.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private companion object {
        const val UI_TIMEOUT_MILLIS = 5_000L
        const val MINIMUM_PROVIDER_BOTTOM_GAP_DP = 12
        const val OVERVIEW_TAG = "playlist-management-overview"
        const val RESTORE_ACTION_TAG = "playlist-restore-action"
    }
}
