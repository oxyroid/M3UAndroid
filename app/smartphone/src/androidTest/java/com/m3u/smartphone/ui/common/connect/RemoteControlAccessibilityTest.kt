package com.m3u.smartphone.ui.common.connect

import android.content.res.Configuration
import android.view.View
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.m3u.core.foundation.architecture.Abi
import com.m3u.data.tv.model.RemoteDirection
import com.m3u.data.tv.model.TvInfo
import com.m3u.i18n.R.string
import com.m3u.smartphone.MainActivity
import java.util.Collections
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class RemoteControlAccessibilityTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun pairingContentRemainsReachableAndKeepsDigitsLtr() {
        assertCompactRtlLargeConfiguration()
        showRemoteControlSheet(
            value = RemoteControlSheetValue.Prepare(
                code = PAIRING_CODE,
                searchingOrConnecting = false,
            ),
        )

        composeRule.onNode(
            hasText(
                context.getString(string.ui_remote_control_pair_title),
                substring = false,
                ignoreCase = false,
            ),
        ).assertIsDisplayed()
        composeRule.onNode(
            hasText(
                context.getString(string.ui_remote_control_connect),
                substring = false,
                ignoreCase = false,
            ) and hasClickAction(),
        )
            .performScrollTo()
            .assertIsDisplayed()
            .assertMinimumTouchTarget()
        listOf(
            string.ui_remote_control_keypad_backspace,
            string.ui_remote_control_keypad_clear,
        ).forEach { descriptionResource ->
            composeRule.onNode(
                hasContentDescription(
                    context.getString(descriptionResource),
                    substring = false,
                    ignoreCase = false,
                ) and hasClickAction(),
            )
                .performScrollTo()
                .assertIsDisplayed()
                .assertMinimumTouchTarget()
        }

        val pairingDescription = composeRule.onNode(
            SemanticsMatcher("pairing code keeps its logical digit order") { node ->
                node.config
                    .getOrElse(SemanticsProperties.ContentDescription) { emptyList() }
                    .joinToString(separator = " ")
                    .stripBidiControlsForAssertion()
                    .contains(SPACED_PAIRING_CODE)
            },
        ).fetchSemanticsNode().config[
            SemanticsProperties.ContentDescription
        ].joinToString(separator = " ")
        assertTrue(
            "Pairing-code semantics changed the digit order: $pairingDescription",
            pairingDescription
                .stripBidiControlsForAssertion()
                .contains(SPACED_PAIRING_CODE),
        )

        val firstKey = composeRule.onNode(
            hasText("1", substring = false, ignoreCase = false) and
                SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button),
        ).fetchSemanticsNode().boundsInWindow
        val secondKey = composeRule.onNode(
            hasText("2", substring = false, ignoreCase = false) and
                SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button),
        ).fetchSemanticsNode().boundsInWindow
        val thirdKey = composeRule.onNode(
            hasText("3", substring = false, ignoreCase = false) and
                SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button),
        ).fetchSemanticsNode().boundsInWindow
        assertTrue(
            "The numeric keypad must stay 1-2-3 from physical left to right in RTL: " +
                "1=$firstKey, 2=$secondKey, 3=$thirdKey",
            firstKey.center.x < secondKey.center.x &&
                secondKey.center.x < thirdKey.center.x,
        )
        assertTrue(
            "The first keypad row must remain horizontally aligned: " +
                "1=$firstKey, 2=$secondKey, 3=$thirdKey",
            firstKey.center.y == secondKey.center.y &&
                secondKey.center.y == thirdKey.center.y,
        )
    }

    @Test
    fun directionPadKeepsPhysicalDirectionsAndActionsReachable() {
        assertCompactRtlLargeConfiguration()
        val emittedDirections = Collections.synchronizedList(
            mutableListOf<RemoteDirection>(),
        )
        showRemoteControlSheet(
            value = RemoteControlSheetValue.DPad(
                tvInfo = TvInfo(
                    model = LONG_TV_MODEL,
                    version = 1,
                    abi = Abi.universal,
                ),
            ),
            onRemoteDirection = { emittedDirections.add(it) },
        )

        val directionPad = composeRule.onNode(
            hasContentDescription(
                context.getString(string.ui_remote_control_direction_pad),
                substring = false,
                ignoreCase = false,
            ),
        )
            .assertIsDisplayed()
            .assertMinimumTouchTarget()
        composeRule.onNode(
            hasText(
                context.getString(string.ui_remote_control_disconnect),
                substring = false,
                ignoreCase = false,
            ) and hasClickAction(),
        )
            .performScrollTo()
            .assertIsDisplayed()
            .assertMinimumTouchTarget()
        directionPad
            .performScrollTo()
            .assertIsDisplayed()

        directionPad.performTouchInput {
            down(
                Offset(
                    x = visibleSize.width * PHYSICAL_EDGE_PRESS_FRACTION,
                    y = visibleSize.height / 2f,
                ),
            )
            advanceEventTime(PRESS_DURATION_MILLIS)
            up()
        }
        composeRule.mainClock.advanceTimeBy(PRESS_DURATION_MILLIS)
        composeRule.waitForIdle()
        composeRule.waitUntil(UI_TIMEOUT_MILLIS) {
            emittedDirections.isNotEmpty()
        }
        assertEquals(
            "Pressing the physical left edge must still send LEFT in RTL",
            RemoteDirection.LEFT,
            emittedDirections.removeAt(0),
        )

        directionPad.performTouchInput {
            down(
                Offset(
                    x = visibleSize.width * (1f - PHYSICAL_EDGE_PRESS_FRACTION),
                    y = visibleSize.height / 2f,
                ),
            )
            advanceEventTime(PRESS_DURATION_MILLIS)
            up()
        }
        composeRule.mainClock.advanceTimeBy(PRESS_DURATION_MILLIS)
        composeRule.waitForIdle()
        composeRule.waitUntil(UI_TIMEOUT_MILLIS) {
            emittedDirections.isNotEmpty()
        }
        assertEquals(
            "Pressing the physical right edge must still send RIGHT in RTL",
            RemoteDirection.RIGHT,
            emittedDirections.removeAt(0),
        )
    }

    private fun showRemoteControlSheet(
        value: RemoteControlSheetValue,
        onRemoteDirection: (RemoteDirection) -> Unit = {},
    ) {
        composeRule.runOnUiThread {
            composeRule.activity.setContent {
                MaterialTheme {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        RemoteControlSheet(
                            value = value,
                            visible = true,
                            onCode = {},
                            checkTvCodeOnSmartphone = {},
                            forgetTvCodeOnSmartphone = {},
                            onRemoteDirection = onRemoteDirection,
                            onDismissRequest = {},
                        )
                    }
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun assertCompactRtlLargeConfiguration() {
        val configuration = currentConfiguration()
        assertEquals(
            MATRIX_CASE_COMPACT_RTL_LARGE,
            requestedAccessibilityMatrixCase(),
        )
        assertEquals(
            "Remote Control RTL coverage must use the Arabic pseudo locale",
            LOCALE_RTL_PSEUDO,
            configuration.locales[0].toLanguageTag(),
        )
        assertEquals(
            "Remote Control RTL coverage must use locale-driven RTL",
            View.LAYOUT_DIRECTION_RTL,
            configuration.layoutDirection,
        )
        assertTrue(
            "Expected 200% text, actual=${configuration.fontScale}",
            configuration.fontScale >= LARGE_TEXT_MINIMUM_SCALE,
        )
        assertTrue(
            "Expected a 320dp narrow window, actual=${configuration.screenWidthDp}",
            configuration.screenWidthDp in NARROW_WIDTH_RANGE,
        )
    }

    private fun currentConfiguration(): Configuration = composeRule.runOnIdle {
        Configuration(composeRule.activity.resources.configuration)
    }

    private fun requestedAccessibilityMatrixCase(): String =
        InstrumentationRegistry.getArguments()
            .getString(ARG_ACCESSIBILITY_MATRIX_CASE)
            ?: error(
                "Missing required instrumentation argument " +
                    "$ARG_ACCESSIBILITY_MATRIX_CASE. Run " +
                    "testing/bin/run-smartphone-provider-ui-matrix.sh.",
            )

    private fun SemanticsNodeInteraction.assertMinimumTouchTarget():
        SemanticsNodeInteraction = assertWidthIsAtLeast(MINIMUM_TOUCH_TARGET_DP.dp)
        .assertHeightIsAtLeast(MINIMUM_TOUCH_TARGET_DP.dp)

    private fun String.stripBidiControlsForAssertion(): String = filterNot { character ->
        character == '\u061C' ||
            character == '\u200E' ||
            character == '\u200F' ||
            character in '\u202A'..'\u202E' ||
            character in '\u2066'..'\u2069'
    }

    private companion object {
        const val ARG_ACCESSIBILITY_MATRIX_CASE = "accessibilityMatrixCase"
        const val MATRIX_CASE_COMPACT_RTL_LARGE = "compact-rtl-large"
        const val LOCALE_RTL_PSEUDO = "ar-XB"
        const val LARGE_TEXT_MINIMUM_SCALE = 1.95f
        const val NARROW_WIDTH_MINIMUM_DP = 315
        const val NARROW_WIDTH_MAXIMUM_DP = 325
        val NARROW_WIDTH_RANGE =
            NARROW_WIDTH_MINIMUM_DP..NARROW_WIDTH_MAXIMUM_DP
        const val UI_TIMEOUT_MILLIS = 5_000L
        const val MINIMUM_TOUCH_TARGET_DP = 48
        const val PRESS_DURATION_MILLIS = 200L
        const val PHYSICAL_EDGE_PRESS_FRACTION = 0.15f
        const val PAIRING_CODE = "123456"
        const val SPACED_PAIRING_CODE = "1 2 3 4 5 6"
        const val LONG_TV_MODEL =
            "Living Room Television With A Deliberately Long Device Name"
    }
}
