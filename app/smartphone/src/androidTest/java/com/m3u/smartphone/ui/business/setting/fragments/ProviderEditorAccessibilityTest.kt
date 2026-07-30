package com.m3u.smartphone.ui.business.setting.fragments

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.m3u.business.setting.ProviderSettingFieldError
import com.m3u.business.setting.ProviderSubscriptionFormField
import com.m3u.extension.api.ExtensionSettingField
import com.m3u.extension.api.ExtensionSettingType
import com.m3u.i18n.R.string
import com.m3u.smartphone.MainActivity
import com.m3u.smartphone.ui.material.ktx.UiBidiFormatter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ProviderEditorAccessibilityTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun externalProviderIdentityIsSanitizedAndFullyLaidOutInLargeTextRtl() {
        val safeProviderId = (
            "com.example.external-provider-" +
                "long-technical-identity-segment-".repeat(5)
            ).take(MAX_PROVIDER_ID_LENGTH)
        val rawProviderId = safeProviderId
            .substring(0, BIDI_CONTROL_INSERTION_INDEX) +
            BIDI_OVERRIDE +
            safeProviderId.substring(BIDI_CONTROL_INSERTION_INDEX)
        val identifierLabel = composeRule.activity.getString(
            string.feat_setting_provider_identifier
        )

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
                            SubscriptionSourceSummary(
                                label = "موفر الوسائط",
                                supporting = "Example Media",
                                technicalIdentity = rawProviderId,
                                icon = Icons.Rounded.Extension,
                                modifier = Modifier.width(TECHNICAL_IDENTITY_WIDTH),
                            )
                        }
                    }
                }
            }
        }

        composeRule.onNodeWithText("موفر الوسائط")
            .assertIsDisplayed()
        composeRule.onNodeWithTag(TECHNICAL_IDENTITY_TAG)
            .assertIsDisplayed()
        composeRule.onNodeWithText(identifierLabel)
            .assertIsDisplayed()
        composeRule.onNodeWithText(
            text = safeProviderId,
            substring = false,
            ignoreCase = false,
        ).assertIsDisplayed()

        val textLayout = textLayoutForTag(TECHNICAL_IDENTITY_TAG)
        assertEquals(LayoutDirection.Rtl, textLayout.layoutInput.layoutDirection)
        assertEquals(2f, textLayout.layoutInput.density.fontScale, 0f)
        assertEquals(FontFamily.Monospace, textLayout.layoutInput.style.fontFamily)
        assertEquals(TextDirection.Ltr, textLayout.layoutInput.style.textDirection)
        assertEquals(Int.MAX_VALUE, textLayout.layoutInput.maxLines)
        assertTrue(textLayout.layoutInput.softWrap)
        assertEquals(TextOverflow.Clip, textLayout.layoutInput.overflow)
        assertTrue(
            "The fixture must wrap beyond the removed three-line limit",
            textLayout.lineCount > 3,
        )
        repeat(textLayout.lineCount) { line ->
            assertFalse(
                "Provider identity line $line was unexpectedly ellipsized",
                textLayout.isLineEllipsized(line),
            )
        }
        assertEquals(
            safeProviderId.length,
            textLayout.getLineEnd(textLayout.lineCount - 1),
        )
        assertFalse(
            "Unsafe bidi controls reached the rendered technical identity",
            textLayout.layoutInput.text.text.contains(BIDI_OVERRIDE),
        )
    }

    @Test
    fun invalidRequiredTextFieldOwnsItsLabelAndRequiredErrorOnce() {
        val rawLabel = "عنوان الخادم"
        val displayLabel = UiBidiFormatter(isRtlContext = true).natural(rawLabel)
        val required = composeRule.activity.getString(
            string.feat_setting_provider_error_required
        )
        val field = ProviderSubscriptionFormField(
            definition = ExtensionSettingField(
                key = "server.url",
                label = rawLabel,
                type = ExtensionSettingType.TEXT,
                required = true,
            ),
            error = ProviderSettingFieldError.REQUIRED,
        )

        setProviderFieldContent(field)

        composeRule.onAllNodes(
            hasContentDescription(displayLabel),
            useUnmergedTree = true,
        ).assertCountEquals(1)
        composeRule.onAllNodes(
            SemanticsMatcher.expectValue(SemanticsProperties.Error, required),
            useUnmergedTree = true,
        ).assertCountEquals(1)
        composeRule.onAllNodes(
            SemanticsMatcher.expectValue(
                SemanticsProperties.StateDescription,
                required,
            ),
            useUnmergedTree = true,
        ).assertCountEquals(0)
        composeRule.onAllNodesWithText(
            text = displayLabel,
            substring = false,
            ignoreCase = false,
            useUnmergedTree = true,
        ).assertCountEquals(0)
        composeRule.onAllNodesWithText(
            text = required,
            substring = false,
            ignoreCase = false,
            useUnmergedTree = true,
        ).assertCountEquals(0)
    }

    @Test
    fun requiredChoiceGroupOwnsFieldContextWhileRadioButtonsOwnOptionLabels() {
        val rawLabel = "وضع التشغيل"
        val displayLabel = UiBidiFormatter(isRtlContext = true).natural(rawLabel)
        val required = composeRule.activity.getString(
            string.feat_setting_provider_error_required
        )
        val field = ProviderSubscriptionFormField(
            definition = ExtensionSettingField(
                key = "playback.enabled",
                label = rawLabel,
                type = ExtensionSettingType.BOOLEAN,
                required = true,
            ),
            input = "true",
        )

        setProviderFieldContent(field)

        composeRule.onAllNodes(
            hasContentDescription(displayLabel),
            useUnmergedTree = true,
        ).assertCountEquals(1)
        composeRule.onAllNodes(
            SemanticsMatcher.expectValue(
                SemanticsProperties.StateDescription,
                required,
            ),
            useUnmergedTree = true,
        ).assertCountEquals(1)
        val radioNodes = composeRule.onAllNodes(
            SemanticsMatcher.expectValue(
                SemanticsProperties.Role,
                Role.RadioButton,
            )
        )
            .fetchSemanticsNodes()
        assertEquals(2, radioNodes.size)
        radioNodes.forEach { node ->
            assertFalse(
                "Radio choice must not repeat the field content description",
                node.config.contains(SemanticsProperties.ContentDescription),
            )
            assertTrue(
                "Radio choice lost its visible option label",
                node.config.contains(SemanticsProperties.Text),
            )
        }
    }

    private fun setProviderFieldContent(field: ProviderSubscriptionFormField) {
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
                            ProviderFormField(
                                field = field,
                                bidiFormatter = UiBidiFormatter(isRtlContext = true),
                                enabled = true,
                                isLast = true,
                                onUpdate = {},
                            )
                        }
                    }
                }
            }
        }
    }

    private fun textLayoutForTag(tag: String): TextLayoutResult {
        val textNode = composeRule.onNodeWithTag(
            testTag = tag,
            useUnmergedTree = true,
        ).fetchSemanticsNode()
        val layouts = mutableListOf<TextLayoutResult>()
        val action = requireNotNull(
            textNode.config[SemanticsActions.GetTextLayoutResult].action
        )
        assertTrue(action.invoke(layouts))
        return layouts.single()
    }

    private companion object {
        const val TECHNICAL_IDENTITY_TAG = "provider-technical-identity"
        const val MAX_PROVIDER_ID_LENGTH = 128
        const val BIDI_CONTROL_INSERTION_INDEX = 20
        const val BIDI_OVERRIDE = '\u202E'
        val TECHNICAL_IDENTITY_WIDTH = 220.dp
    }
}
