package com.m3u.smartphone.ui.business.setting.fragments

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.m3u.smartphone.MainActivity
import com.m3u.smartphone.ui.material.ktx.UiBidiFormatter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ExtensionSettingChoiceLayoutTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun longSamePrefixChoicesWrapWithoutEllipsisInNarrowLargeTextRtlLayout() {
        val bidiFormatter = UiBidiFormatter(isRtlContext = true)
        val displayLabels = RAW_LABELS.map(bidiFormatter::natural)

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
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                itemsIndexed(displayLabels) { index, label ->
                                    ExtensionSettingChoiceRow(
                                        selected = index == 0,
                                        enabled = true,
                                        onClick = {},
                                        label = label,
                                        contentDescription = label,
                                        testTag = choiceTag(index),
                                        modifier = Modifier.width(CHOICE_WIDTH),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        displayLabels.forEachIndexed { index, label ->
            val choice = composeRule.onNodeWithTag(choiceTag(index))
                .performScrollTo()
                .assertIsDisplayed()
                .assertHasClickAction()
                .assertHeightIsAtLeast(48.dp)
            val choiceBounds = choice.getUnclippedBoundsInRoot()
            val rootBounds = composeRule.onRoot().getUnclippedBoundsInRoot()
            assertTrue(
                "Choice row must remain fully reachable inside the viewport",
                choiceBounds.top >= rootBounds.top &&
                    choiceBounds.bottom <= rootBounds.bottom,
            )
            val semantics = choice.fetchSemanticsNode().config
            assertEquals(Role.RadioButton, semantics[SemanticsProperties.Role])
            assertEquals(index == 0, semantics[SemanticsProperties.Selected])

            val textLayout = textLayoutFor(label)
            assertEquals(LayoutDirection.Rtl, textLayout.layoutInput.layoutDirection)
            assertEquals(2f, textLayout.layoutInput.density.fontScale, 0f)
            assertEquals(Int.MAX_VALUE, textLayout.layoutInput.maxLines)
            assertTrue(textLayout.layoutInput.softWrap)
            assertEquals(TextOverflow.Clip, textLayout.layoutInput.overflow)
            assertTrue(
                "The regression fixture must occupy more than the former three-line limit",
                textLayout.lineCount > 3,
            )
            repeat(textLayout.lineCount) { line ->
                assertFalse(
                    "Choice label line $line was unexpectedly ellipsized",
                    textLayout.isLineEllipsized(line),
                )
            }
            assertEquals(
                "The distinguishing suffix was not laid out",
                label.length,
                textLayout.getLineEnd(textLayout.lineCount - 1),
            )
        }
    }

    @Test
    fun invalidChoiceGroupExposesItsErrorOnce() {
        val errorMessage = "Choose one available playback mode"
        val groupTag = "invalid-choice-group"
        val errorTag = "invalid-choice-error"

        composeRule.runOnUiThread {
            composeRule.activity.setContent {
                MaterialTheme {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            ExtensionSettingChoiceGroup(
                                errorMessage = errorMessage,
                                testTag = groupTag,
                                errorTestTag = errorTag,
                            ) {
                                ExtensionSettingChoiceRow(
                                    selected = false,
                                    enabled = true,
                                    onClick = {},
                                    label = "Automatic",
                                    contentDescription = "Automatic playback mode",
                                    testTag = choiceTag(0),
                                )
                            }
                        }
                    }
                }
            }
        }

        composeRule.onNodeWithTag(groupTag).assert(
            SemanticsMatcher("choice group does not duplicate its error") { node ->
                !node.config.contains(SemanticsProperties.Error)
            }
        )
        composeRule.onNodeWithTag(errorTag)
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
        composeRule.onAllNodes(
            SemanticsMatcher("has error semantics") { node ->
                node.config.contains(SemanticsProperties.Error)
            },
            useUnmergedTree = true,
        ).assertCountEquals(1)
    }

    private fun textLayoutFor(label: String): TextLayoutResult {
        val textNode = composeRule.onNodeWithText(
            text = label,
            substring = false,
            ignoreCase = false,
            useUnmergedTree = true,
        ).fetchSemanticsNode()
        val layouts = mutableListOf<TextLayoutResult>()
        val action = requireNotNull(
            textNode.config[SemanticsActions.GetTextLayoutResult].action
        )
        assertTrue(action.invoke(layouts))
        return layouts.single { result ->
            result.layoutInput.text.text == label
        }
    }

    private fun choiceTag(index: Int): String = "long-extension-choice:$index"

    private companion object {
        val CHOICE_WIDTH = 288.dp
        val RAW_LABELS = listOf(
            "خادم الوسائط المنزلي المشترك للغرفة وللعائلة أثناء التشغيل والمزامنة — جيلي",
            "خادم الوسائط المنزلي المشترك للغرفة وللعائلة أثناء التشغيل والمزامنة — إمبي",
        )
    }
}
