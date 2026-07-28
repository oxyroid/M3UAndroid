package com.m3u.testing

import android.os.Build
import android.os.SystemClock
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.m3u.i18n.R.string
import com.m3u.tv.MainActivity
import java.util.regex.Pattern
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TvProviderAccessibilityTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val device = UiDevice.getInstance(instrumentation)

    @Test
    fun dpadBackFromBuiltInProviderFormRestoresTheProviderEntry() {
        ActivityScenario.launch(MainActivity::class.java).use {
            val homeDescription = context.getString(string.tv_home_title)
            val statusDescription = context.getString(string.tv_settings_title)
            val providerLabel = context.getString(string.feat_setting_data_source_emby)
            val externalExtensionsLabel =
                context.getString(string.feat_setting_external_extensions)

            assertNavigationRailMatchesLayoutDirection(homeDescription)
            focusNavigationDestination(
                homeDescription = homeDescription,
                statusDescription = statusDescription,
            )
            assertTrue(device.pressDPadCenter())
            assertTrue(
                "The built-in provider did not expose an accessibility label",
                waitForAccessibilityLabel(providerLabel),
            )
            assertTrue(pressTowardsContent(homeDescription))
            assertTrue(
                "DPad navigation did not reach the $providerLabel action",
                moveFocusDownToAccessibilityAction(providerLabel),
            )
            assertFocusedActionContract(providerLabel)

            assertTrue(device.pressDPadCenter())
            assertTrue(
                device.wait(
                    Until.hasObject(By.clazz(EDIT_TEXT_CLASS).focused(true)),
                    UI_TIMEOUT_MILLIS,
                )
            )
            assertTrue(
                "The provider kind group did not expose a selected radio button",
                waitForAccessibilityNode { node ->
                    node.className?.toString() == RADIO_BUTTON_CLASS &&
                        node.isCheckable &&
                        node.isAccessibilityChecked()
                } != null,
            )

            closeProviderForm()

            assertFocusedActionContract(providerLabel)
            assertFalse(device.hasObject(By.desc(exact(homeDescription)).focused(true)))
            assertFalse(device.hasObject(By.desc(exact(statusDescription)).focused(true)))

            assertTrue(
                "DPad navigation did not reach the external extensions switch",
                moveFocusDownToAccessibilityAction(externalExtensionsLabel),
            )
            val extensionsSwitch =
                waitForFocusedAccessibilityAction(externalExtensionsLabel)
                    ?: error("The external extensions switch lost focus")
            assertTrue(
                "The external extensions control does not expose switch state",
                extensionsSwitch.isCheckable,
            )
        }
    }

    private fun focusNavigationDestination(
        homeDescription: String,
        statusDescription: String,
    ) {
        val railDescriptions = listOf(
            context.getString(string.tv_home_title),
            context.getString(string.tv_library_title),
            context.getString(string.tv_favorites_title),
            statusDescription,
        )
        assertTrue(
            device.wait(
                Until.hasObject(By.desc(exact(homeDescription))),
                UI_TIMEOUT_MILLIS,
            )
        )
        var railHasFocus = railDescriptions.any(::isDescriptionFocused)
        repeat(MAX_DPAD_STEPS) {
            if (!railHasFocus) {
                assertTrue(pressTowardsNavigationRail(homeDescription))
                SystemClock.sleep(DPAD_SETTLE_MILLIS)
                railHasFocus = railDescriptions.any(::isDescriptionFocused)
            }
        }
        assertTrue(
            "DPad navigation could not return to the navigation rail",
            railHasFocus,
        )
        repeat(railDescriptions.size) {
            assertTrue(device.pressDPadUp())
            SystemClock.sleep(DPAD_SETTLE_MILLIS)
        }
        assertTrue(waitForFocusedLabel(homeDescription))
        SystemClock.sleep(DPAD_SETTLE_MILLIS)
        railDescriptions.drop(1).forEach { expectedDescription ->
            assertTrue(device.pressDPadDown())
            assertTrue(
                "DPad navigation did not reach $expectedDescription",
                waitForFocusedLabel(expectedDescription),
            )
            SystemClock.sleep(DPAD_SETTLE_MILLIS)
        }
        assertTrue(waitForFocusedLabel(statusDescription))
    }

    private fun isDescriptionFocused(description: String): Boolean =
        device.hasObject(By.desc(exact(description)).focused(true))

    private fun pressTowardsNavigationRail(homeDescription: String): Boolean =
        if (navigationRailIsOnLeft(homeDescription)) {
            device.pressDPadLeft()
        } else {
            device.pressDPadRight()
        }

    private fun pressTowardsContent(homeDescription: String): Boolean =
        if (navigationRailIsOnLeft(homeDescription)) {
            device.pressDPadRight()
        } else {
            device.pressDPadLeft()
        }

    private fun navigationRailIsOnLeft(homeDescription: String): Boolean {
        val homeDestination = device.findObject(By.desc(exact(homeDescription)))
            ?: error("The navigation rail is not visible")
        return homeDestination.visibleBounds.centerX() < device.displayWidth / 2
    }

    private fun assertNavigationRailMatchesLayoutDirection(homeDescription: String) {
        assertTrue(
            device.wait(
                Until.hasObject(By.desc(exact(homeDescription))),
                UI_TIMEOUT_MILLIS,
            )
        )
        val isRtl =
            context.resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL
        assertEquals(
            "The TV navigation rail must mirror with the app layout direction",
            !isRtl,
            navigationRailIsOnLeft(homeDescription),
        )
    }

    /*
     * An existing account can insert reauthentication actions before Emby.
     * Walking focused actions keeps this test independent of that fixture state.
     */
    private fun moveFocusDownToAccessibilityAction(label: String): Boolean {
        repeat(MAX_DPAD_STEPS) {
            if (waitForFocusedAccessibilityAction(label, ACTION_POLL_MILLIS) != null) {
                return true
            }
            if (!device.pressDPadDown()) return false
            SystemClock.sleep(DPAD_SETTLE_MILLIS)
        }
        return waitForFocusedAccessibilityAction(label, ACTION_POLL_MILLIS) != null
    }

    private fun assertFocusedActionContract(label: String) {
        val focusedAction = waitForFocusedAccessibilityAction(label)
            ?: error("Focus did not land on the $label action")
        assertTrue(
            "$label is focused but is not clickable",
            focusedAction.isClickable,
        )
        assertTrue(
            "$label does not expose Accessibility ACTION_CLICK",
            focusedAction.actionList.any { action ->
                action.id == AccessibilityNodeInfo.AccessibilityAction.ACTION_CLICK.id
            },
        )
    }

    private fun waitForFocusedAccessibilityAction(
        label: String,
        timeoutMillis: Long = UI_TIMEOUT_MILLIS,
    ): AccessibilityNodeInfo? = waitForAccessibilityNode(timeoutMillis) { node ->
        node.isFocused &&
            node.isClickable &&
            node.hasOwnLabel(label)
    }

    private fun AccessibilityNodeInfo.isAccessibilityChecked(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
            checked == AccessibilityNodeInfo.CHECKED_STATE_TRUE
        } else {
            @Suppress("DEPRECATION")
            isChecked
        }

    private fun AccessibilityNodeInfo.hasOwnLabel(expected: String): Boolean =
        contentDescription?.toString()?.equals(expected, ignoreCase = true) == true ||
            text?.toString()?.equals(expected, ignoreCase = true) == true

    private fun waitForAccessibilityNode(
        timeoutMillis: Long = UI_TIMEOUT_MILLIS,
        predicate: (AccessibilityNodeInfo) -> Boolean,
    ): AccessibilityNodeInfo? {
        val deadline = SystemClock.uptimeMillis() + timeoutMillis
        while (SystemClock.uptimeMillis() < deadline) {
            val match = instrumentation.uiAutomation.rootInActiveWindow
                ?.firstNode(predicate)
            if (match != null) return match
            SystemClock.sleep(ACCESSIBILITY_POLL_MILLIS)
        }
        return null
    }

    private fun AccessibilityNodeInfo.firstNode(
        predicate: (AccessibilityNodeInfo) -> Boolean,
    ): AccessibilityNodeInfo? {
        if (predicate(this)) return this
        repeat(childCount) { index ->
            val child = getChild(index) ?: return@repeat
            child.firstNode(predicate)?.let { match -> return match }
        }
        return null
    }

    private fun closeProviderForm() {
        val editText = By.clazz(EDIT_TEXT_CLASS)
        repeat(MAX_BACK_PRESSES_TO_CLOSE_FORM) {
            if (!device.hasObject(editText)) return
            assertTrue(device.pressBack())
            if (device.wait(Until.gone(editText), BACK_SETTLE_MILLIS)) return
        }
        assertFalse(
            "Provider form remained open after Back",
            device.hasObject(editText),
        )
    }

    private fun waitForFocusedLabel(label: String): Boolean =
        waitForAccessibilityNode { node ->
            node.isFocused && node.hasOwnLabel(label)
        } != null

    private fun waitForAccessibilityLabel(label: String): Boolean =
        waitForAccessibilityNode { node -> node.hasOwnLabel(label) } != null

    private fun exact(value: String): Pattern = Pattern.compile(
        Pattern.quote(value),
        Pattern.CASE_INSENSITIVE,
    )

    private companion object {
        const val MAX_BACK_PRESSES_TO_CLOSE_FORM = 2
        const val MAX_DPAD_STEPS = 12
        const val UI_TIMEOUT_MILLIS = 15_000L
        const val ACTION_POLL_MILLIS = 300L
        const val BACK_SETTLE_MILLIS = 1_000L
        const val ACCESSIBILITY_POLL_MILLIS = 100L
        const val DPAD_SETTLE_MILLIS = 150L
        const val EDIT_TEXT_CLASS = "android.widget.EditText"
        const val RADIO_BUTTON_CLASS = "android.widget.RadioButton"
    }
}
