package com.m3u.smartphone.ui.business.setting.fragments

import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.m3u.business.setting.ExtensionSettingsState
import com.m3u.extension.api.ExtensionId
import com.m3u.i18n.R.string
import com.m3u.smartphone.MainActivity
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ExtensionSettingsStatusAccessibilityTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun asynchronousErrorIsAnnouncedAndOffersRetry() {
        val retryRequested = AtomicBoolean(false)
        val extensionId = ExtensionId("dev.example.extension")

        composeRule.runOnUiThread {
            composeRule.activity.setContent {
                MaterialTheme {
                    Surface {
                        ExtensionSettingsScreen(
                            state = ExtensionSettingsState.Error(extensionId),
                            extensionId = extensionId.value,
                            onRetry = { retryRequested.set(true) },
                            onUpdate = { _, _, _, _ -> },
                        )
                    }
                }
            }
        }

        val errorMessage = composeRule.activity.getString(
            string.feat_setting_extension_operation_failed
        )
        composeRule.onNodeWithTag("extension-settings-error")
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.Error,
                    errorMessage,
                )
            )
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.LiveRegion,
                    LiveRegionMode.Polite,
                )
            )

        composeRule.onNodeWithTag("extension-settings-retry")
            .assertHasClickAction()
            .assertTextContains(
                composeRule.activity.getString(string.ui_action_retry),
                substring = false,
            )
            .performClick()

        composeRule.runOnIdle {
            assertTrue(retryRequested.get())
        }
    }
}
