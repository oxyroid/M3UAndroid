package com.m3u.testing

import android.os.SystemClock
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.m3u.core.foundation.architecture.preferences.PreferencesKeys
import com.m3u.core.foundation.architecture.preferences.settings
import com.m3u.i18n.R.string
import com.m3u.tv.MainActivity
import java.util.regex.Pattern
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TvExternalExtensionAuthorizationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val device = UiDevice.getInstance(instrumentation)

    @Before
    fun enableExternalExtensions() {
        runBlocking {
            context.settings.edit { preferences ->
                preferences[PreferencesKeys.EXTERNAL_EXTENSIONS] = true
            }
        }
    }

    @After
    fun restoreExternalExtensionPreference() {
        runBlocking {
            context.settings.edit { preferences ->
                preferences[PreferencesKeys.EXTERNAL_EXTENSIONS] = false
            }
        }
    }

    @Test
    fun authorizationFocusesCancelBeforeTheApprovalAfterPermissionDetails() {
        ActivityScenario.launch(MainActivity::class.java).use {
            val home = context.getString(string.tv_home_title)
            val settings = context.getString(string.tv_settings_title)
            val externalExtensions =
                context.getString(string.feat_setting_external_extensions)
            val enable = context.getString(string.feat_setting_extension_enable)
            val cancel = context.getString(android.R.string.cancel)
            val confirmationTitle =
                context.getString(string.feat_setting_extension_confirm_title)
            val capabilities =
                context.getString(string.feat_setting_extension_requested_capabilities)
            val origins = context.getString(string.feat_setting_extension_network_origins)

            focusSettingsDestination(home = home, settings = settings)
            assertTrue(device.pressDPadCenter())
            assertTrue(pressTowardsContent(home))
            assertTrue(
                "DPad did not reach the external extension developer mode switch",
                moveFocusDownToAction(externalExtensions),
            )
            assertTrue(
                "DPad did not reach the reference extension enable action",
                moveFocusDownToAction(enable),
            )
            assertTrue(
                "Reference extension was not discovered",
                waitForLabel(REFERENCE_EXTENSION_NAME),
            )
            assertTrue(
                "Reference extension enable action lost focus: ${accessibilitySnapshot()}",
                waitForFocusedAction(enable),
            )

            assertTrue(device.pressDPadCenter())
            device.waitForIdle()
            assertTrue(
                "Authorization confirmation did not open: ${accessibilitySnapshot()}",
                waitForLabel(confirmationTitle),
            )
            assertTrue(
                "Cancel must receive initial authorization focus",
                waitForFocusedAction(cancel),
            )
            assertFalse(
                "Approval must not receive initial authorization focus",
                isActionFocused(enable),
            )
            assertTrue(
                "DPad could not reach requested capabilities before approval",
                moveFocusDownToLabel(capabilities),
            )
            assertFalse(
                "Approval became focused before requested capabilities were reviewed",
                isActionFocused(enable),
            )
            assertTrue(
                "DPad could not reach requested network origins before approval",
                moveFocusDownToLabel(origins),
            )
            assertFalse(
                "Approval became focused before requested network origins were reviewed",
                isActionFocused(enable),
            )
            assertTrue(
                "DPad could not reach approval after the permission details",
                moveFocusDownToAction(enable),
            )

            assertTrue(device.pressBack())
            assertTrue(
                "Back did not return to the reference extension",
                waitForLabel(REFERENCE_EXTENSION_NAME),
            )
        }
    }

    private fun focusSettingsDestination(
        home: String,
        settings: String,
    ) {
        val destinations = listOf(
            home,
            context.getString(string.tv_library_title),
            context.getString(string.tv_favorites_title),
            settings,
        )
        assertTrue(device.wait(Until.hasObject(By.desc(exact(home))), UI_TIMEOUT_MILLIS))
        var railHasFocus = destinations.any(::isDescriptionFocused)
        repeat(MAX_DPAD_STEPS) {
            if (!railHasFocus) {
                assertTrue(pressTowardsNavigationRail(home))
                SystemClock.sleep(DPAD_SETTLE_MILLIS)
                railHasFocus = destinations.any(::isDescriptionFocused)
            }
        }
        assertTrue(
            "DPad could not return to the navigation rail",
            railHasFocus,
        )
        repeat(destinations.size) {
            assertTrue(device.pressDPadUp())
            SystemClock.sleep(DPAD_SETTLE_MILLIS)
        }
        assertTrue(
            "DPad did not reach $home: ${accessibilitySnapshot()}",
            waitForFocusedLabel(home),
        )
        SystemClock.sleep(DPAD_SETTLE_MILLIS)
        destinations.drop(1).forEach { destination ->
            assertTrue(device.pressDPadDown())
            assertTrue(
                "DPad did not reach $destination: ${accessibilitySnapshot()}",
                waitForFocusedLabel(destination),
            )
            SystemClock.sleep(DPAD_SETTLE_MILLIS)
        }
    }

    private fun moveFocusDownToAction(label: String): Boolean {
        repeat(MAX_DPAD_STEPS) {
            if (waitForFocusedAction(label, ACTION_POLL_MILLIS)) return true
            if (!device.pressDPadDown()) return false
            SystemClock.sleep(DPAD_SETTLE_MILLIS)
        }
        return waitForFocusedAction(label, ACTION_POLL_MILLIS)
    }

    private fun moveFocusDownToLabel(label: String): Boolean {
        repeat(MAX_DPAD_STEPS) {
            if (waitForFocusedLabel(label, ACTION_POLL_MILLIS)) return true
            if (!device.pressDPadDown()) return false
            SystemClock.sleep(DPAD_SETTLE_MILLIS)
        }
        return waitForFocusedLabel(label, ACTION_POLL_MILLIS)
    }

    private fun waitForFocusedAction(
        label: String,
        timeoutMillis: Long = UI_TIMEOUT_MILLIS,
    ): Boolean = waitForAccessibilityNode(timeoutMillis) { node ->
        node.isFocused && node.isClickable && node.hasOwnLabel(label)
    } != null

    private fun isActionFocused(label: String): Boolean =
        waitForFocusedAction(label, ACTION_POLL_MILLIS)

    private fun waitForFocusedLabel(
        label: String,
        timeoutMillis: Long = UI_TIMEOUT_MILLIS,
    ): Boolean =
        waitForAccessibilityNode(timeoutMillis) { node ->
            node.isFocused && node.hasOwnLabel(label)
        } != null

    private fun waitForLabel(label: String): Boolean =
        waitForAccessibilityNode { node -> node.hasOwnLabel(label) } != null

    private fun isDescriptionFocused(description: String): Boolean =
        device.hasObject(By.desc(exact(description)).focused(true))

    private fun pressTowardsNavigationRail(home: String): Boolean =
        if (navigationRailIsOnLeft(home)) {
            device.pressDPadLeft()
        } else {
            device.pressDPadRight()
        }

    private fun pressTowardsContent(home: String): Boolean =
        if (navigationRailIsOnLeft(home)) {
            device.pressDPadRight()
        } else {
            device.pressDPadLeft()
        }

    private fun navigationRailIsOnLeft(home: String): Boolean {
        val destination = device.findObject(By.desc(exact(home)))
            ?: error("TV navigation rail is not visible")
        val isRtl = context.resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL
        val railIsOnLeft = destination.visibleBounds.centerX() < device.displayWidth / 2
        assertTrue("TV navigation rail does not match layout direction", railIsOnLeft != isRtl)
        return railIsOnLeft
    }

    private fun waitForAccessibilityNode(
        timeoutMillis: Long = UI_TIMEOUT_MILLIS,
        predicate: (AccessibilityNodeInfo) -> Boolean,
    ): AccessibilityNodeInfo? {
        val deadline = SystemClock.uptimeMillis() + timeoutMillis
        while (SystemClock.uptimeMillis() < deadline) {
            instrumentation.uiAutomation.rootInActiveWindow
                ?.firstNode(predicate)
                ?.let { return it }
            SystemClock.sleep(ACCESSIBILITY_POLL_MILLIS)
        }
        return null
    }

    private fun AccessibilityNodeInfo.firstNode(
        predicate: (AccessibilityNodeInfo) -> Boolean,
    ): AccessibilityNodeInfo? {
        if (predicate(this)) return this
        repeat(childCount) { index ->
            getChild(index)?.firstNode(predicate)?.let { return it }
        }
        return null
    }

    private fun accessibilitySnapshot(): String = buildList {
        fun collect(node: AccessibilityNodeInfo) {
            val label = node.contentDescription?.toString()
                ?: node.text?.toString()
            if (!label.isNullOrBlank()) {
                add(
                    "${if (node.isFocused) "focused " else ""}" +
                        "${if (node.isClickable) "clickable " else ""}" +
                        label,
                )
            }
            repeat(node.childCount) { index ->
                node.getChild(index)?.let(::collect)
            }
        }
        instrumentation.uiAutomation.rootInActiveWindow?.let(::collect)
    }.take(MAX_SNAPSHOT_LABELS).joinToString(separator = " | ")

    private fun AccessibilityNodeInfo.hasOwnLabel(expected: String): Boolean =
        listOfNotNull(contentDescription?.toString(), text?.toString()).any { label ->
            label.equals(expected, ignoreCase = true) ||
                label.startsWith("$expected. ", ignoreCase = true)
        }

    private fun exact(value: String): Pattern = Pattern.compile(
        Pattern.quote(value),
        Pattern.CASE_INSENSITIVE,
    )

    private companion object {
        const val REFERENCE_EXTENSION_NAME = "Reference Provider"
        const val MAX_DPAD_STEPS = 48
        const val UI_TIMEOUT_MILLIS = 15_000L
        const val ACTION_POLL_MILLIS = 300L
        const val ACCESSIBILITY_POLL_MILLIS = 100L
        const val DPAD_SETTLE_MILLIS = 150L
        const val MAX_SNAPSHOT_LABELS = 80
    }
}
