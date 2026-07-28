package com.m3u.testing

import android.content.res.Configuration
import android.graphics.Rect
import android.os.SystemClock
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import com.m3u.i18n.R.string
import com.m3u.smartphone.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import java.util.regex.Pattern

class SubscriptionSourceSelectionTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val device = UiDevice.getInstance(instrumentation)

    @Test
    fun embyAndJellyfinCanBeSelectedAcrossTheFullMenuRow() {
        ActivityScenario.launch(MainActivity::class.java).use {
            openSubscriptionScreen()

            selectSourceAcrossFullRow(
                currentResId = string.feat_setting_data_source_m3u,
                targetResId = string.feat_setting_data_source_jellyfin,
                menuSentinelResId = string.feat_setting_data_source_emby,
            )
            assertProviderFieldsVisible()

            selectSourceAcrossFullRow(
                currentResId = string.feat_setting_data_source_jellyfin,
                targetResId = string.feat_setting_data_source_emby,
                menuSentinelResId = string.feat_setting_data_source_jellyfin,
            )
            assertProviderFieldsVisible()
        }
    }

    @Test
    fun builtInProviderVariantLoadsItsDescriptorFormDirectly() {
        ActivityScenario.launch(MainActivity::class.java).use {
            openSubscriptionScreen()

            selectSourceAcrossFullRow(
                currentResId = string.feat_setting_data_source_m3u,
                targetResId = string.feat_setting_data_source_emby,
                menuSentinelResId = string.feat_setting_data_source_xtream,
            )

            assertProviderFieldsVisible()
        }
    }

    @Test
    fun providerFormWorksInRequestedAccessibilityConfiguration() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val configuration = AtomicReference<Configuration>()
            scenario.onActivity { activity ->
                configuration.set(Configuration(activity.resources.configuration))
            }
            val matrixCase = requestedAccessibilityMatrixCase()
            assertRequestedAccessibilityConfiguration(configuration.get(), matrixCase)

            openSubscriptionScreen()
            selectSourceAcrossFullRow(
                currentResId = string.feat_setting_data_source_m3u,
                targetResId = string.feat_setting_data_source_jellyfin,
                menuSentinelResId = string.feat_setting_data_source_emby,
            )
            assertProviderFieldsVisible()
            assertProviderFieldEdgesAligned()
            if (matrixCase == MATRIX_CASE_WIDE_LTR) {
                assertWideSettingPanesAreArrangedSideBySide()
            }
            assertExtensionPluginHeaderWrapsWithoutOverlap()
        }
    }

    @Test
    fun collapsedSelectorExposesItsNameAndDropdownRoleAndCanCloseAgain() {
        ActivityScenario.launch(MainActivity::class.java).use {
            openSubscriptionScreen()

            val currentLabel = context.getString(string.feat_setting_data_source_m3u)
            val selector = findSourceSelector(currentLabel)

            assertEquals("android.widget.Spinner", selector.className)
            val selectorBounds = selector.visibleBounds
            selector.click()
            assertTrue(
                device.wait(
                    Until.hasObject(
                        By.text(
                            caseInsensitiveContaining(
                                context.getString(string.feat_setting_data_source_emby)
                            )
                        )
                    ),
                    UI_TIMEOUT_MILLIS,
                )
            )

            assertTrue(
                device.click(selectorBounds.centerX(), selectorBounds.centerY())
            )

            assertTrue(
                "Dropdown menu did not close after clicking its trigger again",
                device.wait(
                    Until.gone(
                        By.text(
                            caseInsensitiveContaining(
                                context.getString(string.feat_setting_data_source_emby)
                            )
                        )
                    ),
                    UI_TIMEOUT_MILLIS,
                ),
            )
        }
    }

    @Test
    fun firstSubscriptionTabIsFullyVisibleAfterReturningToTheScreen() {
        ActivityScenario.launch(MainActivity::class.java).use {
            openSubscriptionScreen()

            val firstTabSelector = By.text(
                caseInsensitive(context.getString(string.feat_setting_label_add_playlist))
            )
            val alternateTabSelector = By.text(
                caseInsensitive(context.getString(string.feat_setting_extension_plugins))
            )

            device.findRequiredObject(alternateTabSelector).clickableAncestor().click()
            device.findRequiredObject(firstTabSelector).clickableAncestor().click()
            SystemClock.sleep(TAB_ANIMATION_SETTLE_MILLIS)
            val fullyVisibleBounds = device.findRequiredObject(firstTabSelector).visibleBounds

            val isRtl =
                context.resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL
            device.swipe(
                if (isRtl) device.displayWidth / 4 else device.displayWidth * 3 / 4,
                fullyVisibleBounds.centerY(),
                if (isRtl) device.displayWidth * 3 / 4 else device.displayWidth / 4,
                fullyVisibleBounds.centerY(),
                TAB_ROW_SCROLL_STEPS,
            )
            device.waitForIdle()
            SystemClock.sleep(TAB_ANIMATION_SETTLE_MILLIS)
            val shiftedBounds = runCatching {
                device.findObject(firstTabSelector)?.visibleBounds
            }.getOrNull()
            assertTrue(
                "The tab-row swipe did not move the first tab, so the restoration was not tested",
                shiftedBounds == null || shiftedBounds != fullyVisibleBounds,
            )
            device.pressBack()
            device.findRequiredObject(
                By.text(caseInsensitive(context.getString(string.feat_setting_playlist_management)))
            ).click()
            SystemClock.sleep(TAB_ANIMATION_SETTLE_MILLIS)

            val restoredBounds = device.findRequiredObject(firstTabSelector).visibleBounds
            assertTrue(
                "The selected first tab remained clipped after re-entering subscriptions: " +
                    "expected=$fullyVisibleBounds, actual=$restoredBounds",
                restoredBounds.left == fullyVisibleBounds.left &&
                    restoredBounds.width() == fullyVisibleBounds.width(),
            )
        }
    }

    @Test
    fun jellyfinPasswordFieldIsBroughtAboveTheIme() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            openSubscriptionScreen()

            selectSourceAcrossFullRow(
                currentResId = string.feat_setting_data_source_m3u,
                targetResId = string.feat_setting_data_source_jellyfin,
                menuSentinelResId = string.feat_setting_data_source_emby,
            )
            val passwordLabel = context.getString(string.feat_setting_placeholder_password)
            device.findRequiredObject(
                By.desc(caseInsensitiveContaining(passwordLabel))
            ).ancestorOfClass("android.widget.EditText").click()

            val imeBottom = waitForStableImeBottom(scenario)
            device.waitForIdle()
            SystemClock.sleep(IME_RELOCATION_SETTLE_MILLIS)
            val focusedField = device.findRequiredObject(
                By.clazz("android.widget.EditText").focused(true)
            )
            val imeTop = device.displayHeight - imeBottom

            assertTrue(
                "Focused password field ${focusedField.visibleBounds} overlaps IME top $imeTop",
                focusedField.visibleBounds.bottom <= imeTop,
            )

            assertTrue(device.pressBack())
            waitForImeHidden(scenario)
        }
    }

    private fun openSubscriptionScreen() {
        val settingsDestination = By.desc(
            caseInsensitive(context.getString(string.ui_destination_setting))
        )
        val playlistManagement = By.text(
            caseInsensitive(context.getString(string.feat_setting_playlist_management))
        )
        val subscriptionScreen = By.text(
            caseInsensitive(context.getString(string.feat_setting_label_add_playlist))
        )

        repeat(NAVIGATION_RETRY_COUNT) {
            device.waitForIdle()

            device.findObject(playlistManagement)?.let { row ->
                row.clickableAncestor().click()
                if (device.wait(Until.hasObject(subscriptionScreen), NAVIGATION_STEP_TIMEOUT_MILLIS)) {
                    return
                }
            }

            device.findObject(settingsDestination)?.let { destination ->
                destination.clickableAncestor().click()
                device.waitForIdle()
                device.wait(
                    Until.findObject(playlistManagement),
                    NAVIGATION_STEP_TIMEOUT_MILLIS,
                )?.let { row ->
                    row.clickableAncestor().click()
                    if (
                        device.wait(
                            Until.hasObject(subscriptionScreen),
                            NAVIGATION_STEP_TIMEOUT_MILLIS,
                        )
                    ) {
                        return
                    }
                }
            }

            device.pressBack()
        }

        error("Could not navigate from the current app state to the subscription screen")
    }

    private fun waitForStableImeBottom(scenario: ActivityScenario<MainActivity>): Int {
        val bottom = AtomicInteger()
        val deadline = SystemClock.uptimeMillis() + UI_TIMEOUT_MILLIS
        var lastBottom = 0
        var stableSamples = 0
        while (SystemClock.uptimeMillis() < deadline) {
            scenario.onActivity { activity ->
                bottom.set(
                    ViewCompat.getRootWindowInsets(activity.window.decorView)
                        ?.getInsets(WindowInsetsCompat.Type.ime())
                        ?.bottom
                        ?: 0
                )
            }
            val currentBottom = bottom.get()
            stableSamples = if (currentBottom > 0 && currentBottom == lastBottom) {
                stableSamples + 1
            } else {
                0
            }
            if (stableSamples >= IME_STABLE_SAMPLE_COUNT) {
                return currentBottom
            }
            lastBottom = currentBottom
            SystemClock.sleep(IME_INSET_POLL_MILLIS)
        }
        error("IME did not become visible and stable; last bottom inset=${bottom.get()}")
    }

    private fun waitForImeHidden(scenario: ActivityScenario<MainActivity>) {
        val bottom = AtomicInteger(Int.MAX_VALUE)
        val deadline = SystemClock.uptimeMillis() + UI_TIMEOUT_MILLIS
        while (SystemClock.uptimeMillis() < deadline) {
            scenario.onActivity { activity ->
                bottom.set(
                    ViewCompat.getRootWindowInsets(activity.window.decorView)
                        ?.getInsets(WindowInsetsCompat.Type.ime())
                        ?.bottom
                        ?: 0
                )
            }
            if (bottom.get() == 0) return
            SystemClock.sleep(IME_INSET_POLL_MILLIS)
        }
        error("IME remained visible after pressing Back; last bottom inset=${bottom.get()}")
    }

    private fun selectSourceAcrossFullRow(
        currentResId: Int,
        targetResId: Int,
        menuSentinelResId: Int,
    ) {
        val currentLabel = context.getString(currentResId)
        val selected = findSourceSelector(currentLabel)
        assertEquals("android.widget.Spinner", selected.className)
        val selectorBounds = selected.visibleBounds
        selected.click()

        val (option, optionBounds) = waitForFullWidthOption(
            selector = By.text(caseInsensitiveContaining(context.getString(targetResId))),
            minimumWidth = selectorBounds.width() - ROW_WIDTH_ROUNDING_TOLERANCE_PX,
            popupAnchorBounds = selectorBounds,
        )
        assertTrue(
            "The current source is not exposed as selected to accessibility services",
            device.findObjects(
                By.text(caseInsensitiveContaining(currentLabel))
            ).any { current ->
                current.hasSelectedOrCheckedAncestor()
            },
        )
        assertFalse(option.hasSelectedOrCheckedAncestor())
        assertTrue(
            "Dropdown option width ${optionBounds.width()} must match selector width " +
                selectorBounds.width(),
            optionBounds.width() + ROW_WIDTH_ROUNDING_TOLERANCE_PX >= selectorBounds.width(),
        )
        val isRtl = context.resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL
        val rowEmptyEdgeX = if (isRtl) {
            selectorBounds.left + ROW_END_INSET_PX
        } else {
            selectorBounds.right - ROW_END_INSET_PX
        }
        assertTrue(device.click(rowEmptyEdgeX, optionBounds.centerY()))

        assertTrue(
            "Dropdown menu did not close after selecting ${context.getString(targetResId)}",
            device.wait(
                Until.gone(
                    By.text(caseInsensitiveContaining(context.getString(menuSentinelResId)))
                ),
                UI_TIMEOUT_MILLIS,
            ),
        )
        val targetLabel = context.getString(targetResId)
        val updatedSelection = findSourceSelector(targetLabel)
        assertEquals("android.widget.Spinner", updatedSelection.className)
        assertTrue(updatedSelection.visibleBounds.width() >= selectorBounds.width())
    }

    private fun findSourceSelector(expectedLabel: String): UiObject2 {
        val selector = device.findRequiredObject(By.clazz("android.widget.Spinner"))
        val expectedDescription = context.getString(
            string.feat_setting_data_source_selector_description,
            expectedLabel,
        )
        assertEquals(
            "The selector must expose its localized name and current value",
            expectedDescription.withoutBidiControls(),
            selector.contentDescription.withoutBidiControls(),
        )
        return selector
    }

    private fun assertProviderFieldsVisible() {
        device.findRequiredObject(
            By.text(
                caseInsensitiveContaining(
                    context.getString(string.feat_setting_placeholder_basic_url)
                        .withoutBidiControls()
                )
            )
        )
        device.findRequiredObject(
            By.text(
                caseInsensitiveContaining(
                    context.getString(string.feat_setting_placeholder_username)
                        .withoutBidiControls()
                )
            )
        )
        device.findRequiredObject(
            By.text(
                caseInsensitiveContaining(
                    context.getString(string.feat_setting_placeholder_password)
                        .withoutBidiControls()
                )
            )
        )
    }

    private fun assertProviderFieldEdgesAligned() {
        val isRtl = context.resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL
        listOf(
            string.feat_setting_placeholder_basic_url,
            string.feat_setting_placeholder_username,
            string.feat_setting_placeholder_password,
        ).forEach { labelResId ->
            val label = context.getString(labelResId).withoutBidiControls()
            val labelNode = device.findObjects(
                By.text(caseInsensitiveContaining(label))
            ).firstOrNull { node -> node.className == "android.widget.TextView" }
                ?: error("Provider field label was not found: $label")
            val fieldNode = device.findRequiredObject(
                By.desc(caseInsensitiveContaining(label))
            ).ancestorOfClass("android.widget.EditText")
            val labelEdge = if (isRtl) {
                labelNode.visibleBounds.right
            } else {
                labelNode.visibleBounds.left
            }
            val fieldEdge = if (isRtl) {
                fieldNode.visibleBounds.right
            } else {
                fieldNode.visibleBounds.left
            }
            assertTrue(
                "Provider label and field are not aligned for $label: " +
                    "label=${labelNode.visibleBounds}, field=${fieldNode.visibleBounds}",
                kotlin.math.abs(labelEdge - fieldEdge) <= FIELD_EDGE_TOLERANCE_PX,
            )
        }
    }

    private fun assertWideSettingPanesAreArrangedSideBySide() {
        val providerSelector = findSourceSelector(
            context.getString(string.feat_setting_data_source_jellyfin)
        )
        val providerBounds = providerSelector.visibleBounds
        val playlistLabel = context.getString(string.feat_setting_playlist_management)
        val listPaneLabel = (
            device.findObjects(By.text(caseInsensitive(playlistLabel))) +
                device.findObjects(By.desc(caseInsensitive(playlistLabel)))
            )
            .firstOrNull { candidate ->
                candidate.visibleBounds.right <= providerBounds.left
            }
            ?: error(
                "Wide settings list pane was not found beside provider detail: " +
                    "provider=$providerBounds",
            )
        assertFalse(
            "Wide settings list and provider detail overlap: " +
                "list=${listPaneLabel.visibleBounds}, provider=$providerBounds",
            Rect.intersects(listPaneLabel.visibleBounds, providerBounds),
        )
    }

    private fun assertExtensionPluginHeaderWrapsWithoutOverlap() {
        val extensionTitle = context.getString(string.feat_setting_extension_plugins)
        val extensionTab = device.findObjects(
            By.text(caseInsensitive(extensionTitle))
        ).minByOrNull { node -> node.visibleBounds.top }
            ?: error("Extension plugins tab was not found")
        extensionTab.clickableAncestor().click()

        val hint = device.wait(
            Until.findObject(
                By.text(
                    caseInsensitiveContaining(
                        context.getString(string.feat_setting_extension_enable_external_hint)
                    )
                )
            ),
            UI_TIMEOUT_MILLIS,
        ) ?: error("Extension plugins page did not become visible")
        val header = device.findObjects(
            By.text(caseInsensitive(extensionTitle))
        ).maxByOrNull { node -> node.visibleBounds.top }
            ?: error("Extension plugins page heading was not found")
        val refresh = device.findRequiredObject(
            By.text(
                caseInsensitive(
                    context.getString(string.feat_setting_codec_pack_refresh)
                )
            )
        )
        assertTrue(
            "Extension title and refresh action overlap: " +
                "title=${header.visibleBounds}, refresh=${refresh.visibleBounds}",
            header.visibleBounds.bottom <= refresh.visibleBounds.top,
        )
        assertTrue(
            "Extension hint overlaps the refresh action: " +
                "refresh=${refresh.visibleBounds}, hint=${hint.visibleBounds}",
            refresh.visibleBounds.bottom <= hint.visibleBounds.top,
        )
        val isRtl =
            context.resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL
        assertTrue(
            "Extension refresh action is not aligned to the logical end: " +
                "title=${header.visibleBounds}, refresh=${refresh.visibleBounds}, rtl=$isRtl",
            if (isRtl) {
                refresh.visibleBounds.left <= header.visibleBounds.left
            } else {
                refresh.visibleBounds.right >= header.visibleBounds.right
            },
        )
    }

    private fun assertRequestedAccessibilityConfiguration(
        configuration: Configuration,
        matrixCase: String,
    ) {
        when (matrixCase) {
            MATRIX_CASE_COMPACT_LTR -> {
                assertLocale(configuration, LOCALE_ENGLISH, matrixCase)
                assertLayoutDirection(configuration, View.LAYOUT_DIRECTION_LTR, matrixCase)
                assertTrue(
                    "Expected normal text for $matrixCase, actual=${configuration.fontScale}",
                    configuration.fontScale < LARGE_TEXT_THRESHOLD,
                )
                assertTrue(
                    "Expected compact width for $matrixCase, actual=${configuration.screenWidthDp}",
                    configuration.screenWidthDp < WIDE_WINDOW_MINIMUM_DP,
                )
            }
            MATRIX_CASE_COMPACT_RTL_LARGE -> {
                assertLocale(configuration, LOCALE_RTL_PSEUDO, matrixCase)
                assertLayoutDirection(configuration, View.LAYOUT_DIRECTION_RTL, matrixCase)
                assertTrue(
                    "Expected 200% text for $matrixCase, actual=${configuration.fontScale}",
                    configuration.fontScale >= LARGE_TEXT_MINIMUM_SCALE,
                )
                assertTrue(
                    "Expected compact width for $matrixCase, actual=${configuration.screenWidthDp}",
                    configuration.screenWidthDp < WIDE_WINDOW_MINIMUM_DP,
                )
            }
            MATRIX_CASE_WIDE_LTR -> {
                assertLocale(configuration, LOCALE_ENGLISH, matrixCase)
                assertLayoutDirection(configuration, View.LAYOUT_DIRECTION_LTR, matrixCase)
                assertTrue(
                    "Expected normal text for $matrixCase, actual=${configuration.fontScale}",
                    configuration.fontScale < LARGE_TEXT_THRESHOLD,
                )
                assertTrue(
                    "Expected wide width for $matrixCase, actual=${configuration.screenWidthDp}",
                    configuration.screenWidthDp >= WIDE_WINDOW_MINIMUM_DP,
                )
            }
            else -> error(
                "Unknown $ARG_ACCESSIBILITY_MATRIX_CASE=$matrixCase. " +
                    "Expected $MATRIX_CASE_COMPACT_LTR, $MATRIX_CASE_COMPACT_RTL_LARGE, " +
                    "or $MATRIX_CASE_WIDE_LTR.",
            )
        }
    }

    private fun requestedAccessibilityMatrixCase(): String =
        InstrumentationRegistry.getArguments()
            .getString(ARG_ACCESSIBILITY_MATRIX_CASE)
            ?: error(
                "Missing required instrumentation argument $ARG_ACCESSIBILITY_MATRIX_CASE. " +
                    "Run testing/bin/run-smartphone-provider-ui-matrix.sh.",
            )

    private fun assertLocale(
        configuration: Configuration,
        expected: String,
        matrixCase: String,
    ) {
        assertEquals(
            "Unexpected app locale for $matrixCase",
            expected,
            configuration.locales[0].toLanguageTag(),
        )
    }

    private fun assertLayoutDirection(
        configuration: Configuration,
        expected: Int,
        matrixCase: String,
    ) {
        assertEquals(
            "Unexpected layout direction for $matrixCase",
            expected,
            configuration.layoutDirection,
        )
    }

    private fun UiDevice.findRequiredObject(selector: BySelector): UiObject2 =
        wait(Until.findObject(selector), UI_TIMEOUT_MILLIS)
            ?: error("Required UI object was not found: $selector")

    private fun waitForFullWidthOption(
        selector: BySelector,
        minimumWidth: Int,
        popupAnchorBounds: Rect,
    ): Pair<UiObject2, Rect> {
        val deadline = SystemClock.uptimeMillis() + UI_TIMEOUT_MILLIS
        var nextScrollAt = SystemClock.uptimeMillis() + MENU_SCROLL_DELAY_MILLIS
        var lastWidth = 0
        while (SystemClock.uptimeMillis() < deadline) {
            val option = device.findObject(selector)
            if (option != null) {
                val bounds = option.clickableAncestor().visibleBounds
                lastWidth = bounds.width()
                if (lastWidth >= minimumWidth) {
                    return option to bounds
                }
            }
            if (SystemClock.uptimeMillis() >= nextScrollAt) {
                scrollDropdownFromAnchor(popupAnchorBounds)
                nextScrollAt += MENU_SCROLL_INTERVAL_MILLIS
            }
            SystemClock.sleep(MENU_ANIMATION_POLL_MILLIS)
        }
        error(
            "Dropdown option did not settle to selector width: " +
                "lastWidth=$lastWidth, minimumWidth=$minimumWidth",
        )
    }

    private fun scrollDropdownFromAnchor(anchorBounds: Rect) {
        val endY = (anchorBounds.bottom + MENU_SCROLL_EDGE_INSET_PX)
            .coerceAtMost(device.displayHeight - MENU_SCROLL_EDGE_INSET_PX)
        val startY = (device.displayHeight - MENU_SCROLL_EDGE_INSET_PX)
            .coerceAtLeast(endY)
        if (startY > endY) {
            device.swipe(
                anchorBounds.centerX(),
                startY,
                anchorBounds.centerX(),
                endY,
                MENU_SCROLL_STEPS,
            )
        }
    }

    private fun caseInsensitive(value: String): Pattern = Pattern.compile(
        Pattern.quote(value),
        Pattern.CASE_INSENSITIVE,
    )

    private fun caseInsensitiveContaining(value: String): Pattern = Pattern.compile(
        ".*${Pattern.quote(value)}.*",
        Pattern.CASE_INSENSITIVE,
    )

    private fun String.withoutBidiControls(): String = filterNot { char ->
        char == '\u061C' ||
            char == '\u200E' ||
            char == '\u200F' ||
            char in '\u202A'..'\u202E' ||
            char in '\u2066'..'\u2069'
    }

    private fun UiObject2.clickableAncestor(): UiObject2 {
        var current = this
        while (!current.isClickable) {
            current = current.parent ?: return this
        }
        return current
    }

    private fun UiObject2.scrollableAncestors(): List<UiObject2> {
        val ancestors = mutableListOf<UiObject2>()
        var current: UiObject2? = parent
        while (current != null) {
            if (current.isScrollable) {
                ancestors += current
            }
            current = current.parent
        }
        return ancestors.ifEmpty {
            error("Object has no scrollable ancestor: $this")
        }
    }

    private fun UiObject2.ancestorOfClass(className: String): UiObject2 {
        var current: UiObject2? = this
        while (current != null) {
            if (current.className == className) return current
            current = current.parent
        }
        error("Object has no $className ancestor: $this")
    }

    private fun UiObject2.hasSelectedOrCheckedAncestor(): Boolean {
        var current: UiObject2? = this
        while (current != null) {
            // Compose exposes radio-button selection as AccessibilityNodeInfo.checked,
            // while tab selection is exposed as AccessibilityNodeInfo.selected.
            if (current.isSelected || current.isChecked) return true
            current = current.parent
        }
        return false
    }

    private companion object {
        const val UI_TIMEOUT_MILLIS = 5_000L
        const val NAVIGATION_STEP_TIMEOUT_MILLIS = 2_000L
        const val NAVIGATION_RETRY_COUNT = 3
        const val MENU_ANIMATION_POLL_MILLIS = 16L
        const val MENU_SCROLL_DELAY_MILLIS = 500L
        const val MENU_SCROLL_INTERVAL_MILLIS = 750L
        const val MENU_SCROLL_EDGE_INSET_PX = 48
        const val MENU_SCROLL_STEPS = 12
        const val ROW_WIDTH_ROUNDING_TOLERANCE_PX = 1
        const val ROW_END_INSET_PX = 24
        const val FIELD_EDGE_TOLERANCE_PX = 2
        const val TAB_ROW_SCROLL_STEPS = 24
        const val TAB_ANIMATION_SETTLE_MILLIS = 500L
        const val IME_INSET_POLL_MILLIS = 50L
        const val IME_STABLE_SAMPLE_COUNT = 3
        const val IME_RELOCATION_SETTLE_MILLIS = 300L
        const val ARG_ACCESSIBILITY_MATRIX_CASE = "accessibilityMatrixCase"
        const val MATRIX_CASE_COMPACT_LTR = "compact-ltr"
        const val MATRIX_CASE_COMPACT_RTL_LARGE = "compact-rtl-large"
        const val MATRIX_CASE_WIDE_LTR = "wide-ltr"
        const val LOCALE_ENGLISH = "en"
        const val LOCALE_RTL_PSEUDO = "ar-XB"
        const val LARGE_TEXT_MINIMUM_SCALE = 1.95f
        const val LARGE_TEXT_THRESHOLD = 1.3f
        const val WIDE_WINDOW_MINIMUM_DP = 600
    }
}
