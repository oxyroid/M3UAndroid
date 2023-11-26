package com.m3u

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.m3u.androidApp.MainActivity
import com.m3u.i18n.R
import org.junit.Rule
import org.junit.Test

class FeatureSettingsTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()
    private val activity: MainActivity get() = rule.activity

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun subscribe() {
        val settings = activity.getString(R.string.ui_destination_setting)
        rule.onNodeWithText(settings, ignoreCase = true).performClick()
        val playlistManagement = activity.getString(R.string.feat_setting_feed_management)
        rule.onNodeWithText(playlistManagement, ignoreCase = true).performClick()
        val playlistManagementName = activity.getString(R.string.feat_setting_placeholder_title)
        rule.onNodeWithText(playlistManagementName, ignoreCase = true)
            .performTextInput("全国景区源")
        val playlistManagementUrl = activity.getString(R.string.feat_setting_placeholder_url)
        rule.onNodeWithText(playlistManagementUrl, ignoreCase = true).performTextInput(
            "https://raw.githubusercontent.com/imDazui/Tvlist-awesome-m3u-m3u8/master/m3u/全国景区源.m3u8"
        )
        val playlistManagementButton = activity.getString(R.string.feat_setting_label_subscribe)
        rule.onNodeWithText(playlistManagementButton, ignoreCase = true).performClick()

        val main = activity.getString(R.string.ui_destination_main)
        rule.onNode(
            hasText(main, ignoreCase = true)
                    and
                    hasTestTag("destination")
        ).performClick()

        rule.waitUntilAtLeastOneExists(hasText("全国景区源"))
    }
}