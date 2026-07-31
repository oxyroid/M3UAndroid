package com.m3u.smartphone.ui.material.components

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.m3u.smartphone.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class PageStateContentAccessibilityTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun longStateCopyAndActionRemainReachableAtTwoHundredPercentTextInRtl() {
        var actionInvocations = 0

        composeRule.runOnUiThread {
            composeRule.activity.setContent {
                MaterialTheme {
                    val baseDensity = LocalDensity.current
                    CompositionLocalProvider(
                        LocalDensity provides Density(
                            density = baseDensity.density,
                            fontScale = 2f,
                        ),
                        LocalLayoutDirection provides LayoutDirection.Rtl,
                    ) {
                        Surface(modifier = Modifier.fillMaxSize()) {
                            PageStateContent(
                                icon = Icons.Rounded.FavoriteBorder,
                                title = STATE_TITLE,
                                description = STATE_DESCRIPTION,
                                actionLabel = ACTION_LABEL,
                                actionIcon = Icons.AutoMirrored.Rounded.List,
                                onAction = { actionInvocations += 1 },
                                modifier = Modifier
                                    .width(320.dp)
                                    .padding(vertical = 16.dp),
                            )
                        }
                    }
                }
            }
        }

        composeRule.onNodeWithText(STATE_TITLE)
            .assertIsDisplayed()
        composeRule.onNodeWithText(STATE_DESCRIPTION)
            .assertIsDisplayed()
        val action = composeRule.onNodeWithText(ACTION_LABEL)
            .assertIsDisplayed()
            .assertHasClickAction()
        val minimumTouchTarget = composeRule.activity.resources.displayMetrics.density * 48f
        assertTrue(
            "The page-state action is shorter than 48dp",
            action.fetchSemanticsNode().size.height >= minimumTouchTarget,
        )

        action.performClick()
        composeRule.runOnIdle {
            assertEquals(1, actionInvocations)
        }
    }

    private companion object {
        const val STATE_TITLE =
            "No saved favorites from this media library yet"
        const val STATE_DESCRIPTION =
            "Long-press any channel to keep it here for quick access on this device."
        const val ACTION_LABEL =
            "Browse every available playlist"
    }
}
