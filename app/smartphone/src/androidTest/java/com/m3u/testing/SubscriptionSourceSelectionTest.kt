package com.m3u.testing

import android.graphics.Rect
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import com.m3u.i18n.R.string
import com.m3u.smartphone.MainActivity
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.regex.Pattern

class SubscriptionSourceSelectionTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val device = UiDevice.getInstance(instrumentation)

    @Test
    fun embyAndJellyfinCanBeSelectedAcrossTheFullMenuRow() {
        ActivityScenario.launch(MainActivity::class.java).use {
            device.waitForIdle()
            device.findRequiredObject(
                By.desc(caseInsensitive(context.getString(string.ui_destination_setting)))
            ).click()
            device.findRequiredObject(
                By.text(caseInsensitive(context.getString(string.feat_setting_playlist_management)))
            ).click()

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
    fun builtInProviderFormIsLoadedFromDiscovery() {
        ActivityScenario.launch(MainActivity::class.java).use {
            device.waitForIdle()
            device.findRequiredObject(
                By.desc(caseInsensitive(context.getString(string.ui_destination_setting)))
            ).click()
            device.findRequiredObject(
                By.text(caseInsensitive(context.getString(string.feat_setting_playlist_management)))
            ).click()

            selectSourceAcrossFullRow(
                currentResId = string.feat_setting_data_source_m3u,
                targetResId = string.feat_setting_data_source_provider,
                menuSentinelResId = string.feat_setting_data_source_xtream,
            )

            device.findRequiredObject(By.text(caseInsensitive("Emby Compatible")))
            device.findRequiredObject(By.text(caseInsensitive("Server URL")))
            device.findRequiredObject(By.text(caseInsensitive("Username")))
            device.findRequiredObject(By.text(caseInsensitive("Password")))
        }
    }

    private fun selectSourceAcrossFullRow(
        currentResId: Int,
        targetResId: Int,
        menuSentinelResId: Int,
    ) {
        val selected = device.findRequiredObject(By.text(context.getString(currentResId)))
        val selectorBounds = selected.clickableAncestor().visibleBounds
        selected.click()

        val (option, optionBounds) = waitForFullWidthOption(
            selector = By.text(context.getString(targetResId)),
            minimumWidth = selectorBounds.width() - ROW_WIDTH_ROUNDING_TOLERANCE_PX,
        )
        assertTrue(
            "Dropdown option width ${optionBounds.width()} must match selector width " +
                selectorBounds.width(),
            optionBounds.width() + ROW_WIDTH_ROUNDING_TOLERANCE_PX >= selectorBounds.width(),
        )
        assertTrue(device.click(selectorBounds.right - ROW_END_INSET_PX, optionBounds.centerY()))

        assertTrue(
            "Dropdown menu did not close after selecting ${context.getString(targetResId)}",
            device.wait(
                Until.gone(By.text(context.getString(menuSentinelResId))),
                UI_TIMEOUT_MILLIS,
            ),
        )
        val updatedSelection = device.findRequiredObject(By.text(context.getString(targetResId)))
        assertTrue(updatedSelection.clickableAncestor().visibleBounds.width() >= selectorBounds.width())
    }

    private fun assertProviderFieldsVisible() {
        device.findRequiredObject(
            By.text(caseInsensitive(context.getString(string.feat_setting_placeholder_basic_url)))
        )
        device.findRequiredObject(
            By.text(caseInsensitive(context.getString(string.feat_setting_placeholder_username)))
        )
        device.findRequiredObject(
            By.text(caseInsensitive(context.getString(string.feat_setting_placeholder_password)))
        )
    }

    private fun UiDevice.findRequiredObject(selector: BySelector): UiObject2 =
        wait(Until.findObject(selector), UI_TIMEOUT_MILLIS)
            ?: error("Required UI object was not found: $selector")

    private fun waitForFullWidthOption(
        selector: BySelector,
        minimumWidth: Int,
    ): Pair<UiObject2, Rect> {
        val deadline = SystemClock.uptimeMillis() + UI_TIMEOUT_MILLIS
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
            SystemClock.sleep(MENU_ANIMATION_POLL_MILLIS)
        }
        error(
            "Dropdown option did not settle to selector width: " +
                "lastWidth=$lastWidth, minimumWidth=$minimumWidth",
        )
    }

    private fun caseInsensitive(value: String): Pattern = Pattern.compile(
        Pattern.quote(value),
        Pattern.CASE_INSENSITIVE,
    )

    private fun UiObject2.clickableAncestor(): UiObject2 {
        var current = this
        while (!current.isClickable) {
            current = current.parent ?: return this
        }
        return current
    }

    private companion object {
        const val UI_TIMEOUT_MILLIS = 5_000L
        const val MENU_ANIMATION_POLL_MILLIS = 16L
        const val ROW_WIDTH_ROUNDING_TOLERANCE_PX = 1
        const val ROW_END_INSET_PX = 24
    }
}
