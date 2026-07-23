package com.m3u.testing

import android.os.SystemClock
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardDoubleArrowUp
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import com.m3u.i18n.R.string
import com.m3u.smartphone.MainActivity
import com.m3u.smartphone.ui.common.helper.Fob
import com.m3u.smartphone.ui.common.helper.Metadata
import com.m3u.smartphone.ui.material.components.Destination
import org.junit.Assume.assumeTrue
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.regex.Pattern

class FloatingNavigationBehaviorTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val device = UiDevice.getInstance(instrumentation)

    @Test
    fun compactNavigationStaysForScrollActionAndHidesForDetailAndSearch() {
        assumeTrue(
            device.displayWidth / context.resources.displayMetrics.density < COMPACT_WIDTH_DP,
        )

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            device.waitForIdle()

            val forYou = navigationSelector(string.ui_destination_foryou)
            val favorite = navigationSelector(string.ui_destination_favourite)
            val setting = navigationSelector(string.ui_destination_setting)

            val selectedForYou = device.findRequiredObject(forYou)
            assertFalse(
                "Android should not expose the already-selected tab as clickable",
                selectedForYou.isClickable,
            )
            assertTrue(
                "The selected top-level destination did not expose selected semantics",
                selectedForYou.isSelected,
            )
            assertTrue(
                "The compact navigation unexpectedly rendered destination labels",
                selectedForYou.text.isNullOrEmpty(),
            )
            scenario.onActivity {
                Metadata.fob = Fob(
                    destination = Destination.Foryou,
                    icon = Icons.Rounded.KeyboardDoubleArrowUp,
                    iconTextId = string.feat_playlist_scroll_up,
                    onClick = {},
                )
            }
            device.waitForIdle()
            SystemClock.sleep(NAVIGATION_ANIMATION_SETTLE_MILLIS)
            device.findRequiredObject(forYou)
            scenario.onActivity {
                Metadata.fob = null
            }

            device.findRequiredObject(
                By.text(
                    caseInsensitive(
                        context.getString(string.ui_search_placeholder),
                    )
                )
            ).clickableAncestor().click()
            device.findRequiredObject(
                By.desc(
                    caseInsensitive(
                        context.getString(string.ui_cd_top_bar_on_back_pressed),
                    )
                )
            )
            assertTrue(
                "Floating navigation remained visible while search was expanded",
                device.wait(Until.gone(forYou), UI_TIMEOUT_MILLIS)
            )

            device.pressBack()
            device.pressBack()
            device.findRequiredObject(forYou)

            val dragStart = device.findRequiredObject(forYou).visibleBounds
            val dragEnd = device.findRequiredObject(favorite).visibleBounds
            device.swipe(
                dragStart.centerX(),
                dragStart.centerY(),
                dragEnd.centerX(),
                dragEnd.centerY(),
                NAVIGATION_DRAG_STEPS,
            )
            device.waitForIdle()
            SystemClock.sleep(NAVIGATION_ANIMATION_SETTLE_MILLIS)
            assertTrue(
                "Dragging the glass indicator did not select the favorite destination",
                device.findRequiredObject(favorite).isSelected,
            )
            device.findRequiredObject(setting).run {
                assertTrue(
                    "The named navigation destination did not expose a click action",
                    isClickable,
                )
                click()
            }
            assertTrue(
                "The clicked settings destination did not expose selected semantics",
                device.findRequiredObject(setting).isSelected,
            )

            val playlistManagement = By.text(
                caseInsensitive(
                    context.getString(string.feat_setting_playlist_management),
                )
            )
            device.findRequiredObject(playlistManagement).click()
            assertTrue(
                "Floating navigation remained visible on the settings detail pane",
                device.wait(Until.gone(setting), UI_TIMEOUT_MILLIS)
            )

            device.pressBack()
            device.findRequiredObject(setting)
        }
    }

    private fun navigationSelector(stringResId: Int): BySelector =
        By.desc(caseInsensitive(context.getString(stringResId)))

    private fun UiDevice.findRequiredObject(selector: BySelector): UiObject2 =
        wait(Until.findObject(selector), UI_TIMEOUT_MILLIS)
            ?: error("Required UI object was not found: $selector")

    private fun UiObject2.clickableAncestor(): UiObject2 {
        var current = this
        while (!current.isClickable) {
            current = current.parent ?: return this
        }
        return current
    }

    private fun caseInsensitive(value: String): Pattern = Pattern.compile(
        Pattern.quote(value),
        Pattern.CASE_INSENSITIVE,
    )

    private companion object {
        const val COMPACT_WIDTH_DP = 600f
        const val NAVIGATION_DRAG_STEPS = 24
        const val NAVIGATION_ANIMATION_SETTLE_MILLIS = 500L
        const val UI_TIMEOUT_MILLIS = 5_000L
    }
}
