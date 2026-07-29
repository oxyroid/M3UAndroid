package com.m3u.testing

import android.view.View
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.platform.app.InstrumentationRegistry
import com.m3u.core.foundation.architecture.preferences.PreferencesKeys
import com.m3u.core.foundation.architecture.preferences.set
import com.m3u.core.foundation.architecture.preferences.settings
import com.m3u.smartphone.MainActivity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import kotlin.math.abs

class FloatingRemoteControlDockTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun remoteControlIsAnIndependentLogicalTrailingAction() {
        val density = context.resources.displayMetrics.density
        assumeTrue(
            context.resources.configuration.screenWidthDp < COMPACT_WIDTH_DP,
        )

        runBlocking {
            context.settings.set(PreferencesKeys.REMOTE_CONTROL, true)
        }
        try {
            composeRule.waitUntil(UI_TIMEOUT_MILLIS) {
                composeRule.onAllNodesWithTag(REMOTE_CONTROL_ACTION_TAG)
                    .fetchSemanticsNodes()
                    .isNotEmpty()
            }
            composeRule.waitForIdle()

            val remoteControlNode = composeRule
                .onNodeWithTag(REMOTE_CONTROL_ACTION_TAG)
                .fetchSemanticsNode()
            val navigationNode = composeRule
                .onNodeWithTag(FLOATING_NAVIGATION_TAG)
                .fetchSemanticsNode()
            assertEquals(
                "Remote control must remain an action instead of becoming a tab",
                Role.Button,
                remoteControlNode.config[SemanticsProperties.Role],
            )
            assertFalse(
                "Remote control must not participate in destination selection",
                remoteControlNode.config.contains(SemanticsProperties.Selected),
            )
            assertTrue(
                "Remote control action must provide at least a 48dp touch target",
                remoteControlNode.boundsInWindow.width >= MINIMUM_TOUCH_TARGET_DP * density &&
                    remoteControlNode.boundsInWindow.height >=
                    MINIMUM_TOUCH_TARGET_DP * density,
            )

            val remoteBounds = remoteControlNode.boundsInWindow
            val navigationBounds = navigationNode.boundsInWindow
            val isRtl =
                context.resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL
            val logicalGap = if (isRtl) {
                navigationBounds.left - remoteBounds.right
            } else {
                remoteBounds.left - navigationBounds.right
            }
            assertTrue(
                "Remote control must sit outside the navigation at logical trailing",
                logicalGap in
                    MINIMUM_DOCK_GAP_DP * density..MAXIMUM_DOCK_GAP_DP * density,
            )
            assertTrue(
                "Remote control and navigation must share the same vertical center",
                abs(remoteBounds.center.y - navigationBounds.center.y) <= density,
            )
        } finally {
            runBlocking {
                context.settings.set(PreferencesKeys.REMOTE_CONTROL, false)
            }
        }
    }

    private companion object {
        const val COMPACT_WIDTH_DP = 600
        const val MINIMUM_DOCK_GAP_DP = 10f
        const val MAXIMUM_DOCK_GAP_DP = 14f
        const val MINIMUM_TOUCH_TARGET_DP = 48f
        const val UI_TIMEOUT_MILLIS = 5_000L
        const val FLOATING_NAVIGATION_TAG = "floating-app-navigation"
        const val REMOTE_CONTROL_ACTION_TAG = "floating-remote-control-action"
    }
}
