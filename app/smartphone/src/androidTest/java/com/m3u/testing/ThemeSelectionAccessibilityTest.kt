package com.m3u.testing

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.datastore.preferences.core.edit
import androidx.test.platform.app.InstrumentationRegistry
import com.m3u.core.foundation.architecture.preferences.PreferencesKeys
import com.m3u.core.foundation.architecture.preferences.ThemePreset
import com.m3u.core.foundation.architecture.preferences.ThemeStyle
import com.m3u.core.foundation.architecture.preferences.get
import com.m3u.core.foundation.architecture.preferences.settings
import com.m3u.core.foundation.architecture.preferences.themePreferences
import com.m3u.i18n.R.string
import com.m3u.smartphone.MainActivity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ThemeSelectionAccessibilityTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun warmThemesExposeOneNamedRadioButtonWithoutAnUnavailableEditor() {
        val previous = runBlocking {
            ThemePreferences(
                argb = context.settings[PreferencesKeys.COLOR_ARGB],
                isDark = context.settings[PreferencesKeys.DARK_MODE],
                style = context.settings[PreferencesKeys.THEME_STYLE],
                presetId = context.settings[PreferencesKeys.THEME_PRESET_ID],
                useDynamicColors = context.settings[PreferencesKeys.USE_DYNAMIC_COLORS],
                followSystemTheme = context.settings[PreferencesKeys.FOLLOW_SYSTEM_THEME],
            )
        }
        try {
            runBlocking {
                context.settings.edit { preferences ->
                    preferences[PreferencesKeys.COLOR_ARGB] =
                        ThemePreset.WARM_EDITORIAL_SEED
                    preferences[PreferencesKeys.DARK_MODE] = false
                    preferences[PreferencesKeys.THEME_STYLE] =
                        ThemeStyle.WARM_EDITORIAL
                    preferences[PreferencesKeys.THEME_PRESET_ID] =
                        ThemePreset.WARM_EDITORIAL
                    preferences[PreferencesKeys.USE_DYNAMIC_COLORS] = false
                    preferences[PreferencesKeys.FOLLOW_SYSTEM_THEME] = false
                }
            }

            openAppearance()
            val parchment = context.getString(string.feat_setting_theme_parchment)
            val ink = context.getString(string.feat_setting_theme_ink)
            scrollThemeListTo(ink)

            assertThemeRadioButton(
                name = parchment,
                expectedSelected = true,
            )
            assertThemeRadioButton(
                name = ink,
                expectedSelected = false,
            )

            composeRule.onNode(
                hasContentDescription(
                    ink,
                    substring = false,
                    ignoreCase = false,
                )
            ).performClick()
            composeRule.waitUntil(UI_TIMEOUT_MILLIS) {
                themeIsSelected(ink)
            }
            assertThemeRadioButton(
                name = parchment,
                expectedSelected = false,
            )
            assertThemeRadioButton(
                name = ink,
                expectedSelected = true,
            )
            assertTrue(
                runBlocking {
                    context.settings.themePreferences().first()
                        .selection.isDark
                }
            )
        } finally {
            runBlocking {
                context.settings.edit { preferences ->
                    preferences[PreferencesKeys.COLOR_ARGB] = previous.argb
                    preferences[PreferencesKeys.DARK_MODE] = previous.isDark
                    preferences[PreferencesKeys.THEME_STYLE] = previous.style
                    preferences[PreferencesKeys.THEME_PRESET_ID] =
                        previous.presetId
                    preferences[PreferencesKeys.USE_DYNAMIC_COLORS] =
                        previous.useDynamicColors
                    preferences[PreferencesKeys.FOLLOW_SYSTEM_THEME] =
                        previous.followSystemTheme
                }
            }
        }
    }

    private fun assertThemeRadioButton(
        name: String,
        expectedSelected: Boolean,
    ) {
        val matcher = hasContentDescription(
            name,
            substring = false,
            ignoreCase = false,
        )
        val item = composeRule.onNode(matcher)
            .assertIsDisplayed()
            .assertHasClickAction()
        val semantics = item.fetchSemanticsNode().config

        assertEquals(Role.RadioButton, semantics[SemanticsProperties.Role])
        assertEquals(expectedSelected, semantics[SemanticsProperties.Selected])
        assertFalse(
            "$name is a built-in preset and must not advertise a color editor",
            semantics.contains(SemanticsActions.OnLongClick),
        )
        val minimumSize = 48f * context.resources.displayMetrics.density
        val size = item.fetchSemanticsNode().size
        assertTrue("$name is narrower than 48dp", size.width >= minimumSize)
        assertTrue("$name is shorter than 48dp", size.height >= minimumSize)
    }

    private fun openAppearance() {
        if (tagExists(THEME_LIST_TAG)) return

        val appearance = hasText(
            context.getString(string.feat_setting_appearance),
            substring = false,
            ignoreCase = true,
        ) and hasClickAction()
        val settings = hasContentDescription(
            context.getString(string.ui_destination_setting),
            substring = false,
            ignoreCase = true,
        ) and hasClickAction()

        waitUntilMatcherExists(appearance or settings)
        if (composeRule.onAllNodes(appearance).fetchSemanticsNodes().isEmpty()) {
            composeRule.onNode(settings).performClick()
        }
        waitUntilMatcherExists(appearance)
        composeRule.onNode(appearance).performClick()
        composeRule.waitUntil(UI_TIMEOUT_MILLIS) { tagExists(THEME_LIST_TAG) }
    }

    private fun scrollThemeListTo(name: String) {
        val target = hasContentDescription(
            name,
            substring = false,
            ignoreCase = false,
        )
        composeRule.waitUntil(UI_TIMEOUT_MILLIS) {
            runCatching {
                composeRule.onNodeWithTag(THEME_LIST_TAG)
                    .performScrollToNode(target)
            }.isSuccess
        }
        composeRule.waitForIdle()
    }

    private fun waitUntilMatcherExists(matcher: SemanticsMatcher) {
        composeRule.waitUntil(UI_TIMEOUT_MILLIS) {
            composeRule.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun tagExists(tag: String): Boolean =
        composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()

    private fun themeIsSelected(name: String): Boolean {
        val nodes = composeRule.onAllNodes(
            hasContentDescription(
                name,
                substring = false,
                ignoreCase = false,
            )
        ).fetchSemanticsNodes()
        return nodes.singleOrNull()
            ?.config
            ?.getOrElse(SemanticsProperties.Selected) { false }
            ?: false
    }

    private data class ThemePreferences(
        val argb: Int,
        val isDark: Boolean,
        val style: Int,
        val presetId: String,
        val useDynamicColors: Boolean,
        val followSystemTheme: Boolean,
    )

    private companion object {
        const val THEME_LIST_TAG = "appearance-theme-selection-list"
        const val UI_TIMEOUT_MILLIS = 10_000L
    }
}
