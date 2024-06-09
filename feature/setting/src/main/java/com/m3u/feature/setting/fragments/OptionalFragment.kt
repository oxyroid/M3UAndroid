package com.m3u.feature.setting.fragments

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.BrightnessMedium
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.Details
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material.icons.rounded.Loop
import androidx.compose.material.icons.rounded.PictureInPicture
import androidx.compose.material.icons.rounded.ReplayCircleFilled
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material.icons.rounded.SettingsEthernet
import androidx.compose.material.icons.rounded.Unarchive
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.m3u.core.architecture.preferences.ReconnectMode
import com.m3u.core.architecture.preferences.hiltPreferences
import com.m3u.core.util.basic.title
import com.m3u.feature.setting.components.CheckBoxSharedPreference
import com.m3u.i18n.R
import com.m3u.material.components.TextPreference
import com.m3u.material.ktx.includeChildGlowPadding
import com.m3u.material.ktx.isTelevision

@Composable
internal fun OptionalFragment(
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier
) {
    val preferences = hiltPreferences()
    val tv = isTelevision()
    Column(
        modifier
            .fillMaxSize()
            .padding(contentPadding)
            .includeChildGlowPadding()
    ) {
        CheckBoxSharedPreference(
            title = R.string.feat_setting_tunneling,
            content = R.string.feat_setting_tunneling_description,
            icon = Icons.Rounded.FlashOn,
            checked = preferences.tunneling,
            onChanged = { preferences.tunneling = !preferences.tunneling }
        )
        CheckBoxSharedPreference(
            title = R.string.feat_setting_full_info_player,
            content = R.string.feat_setting_full_info_player_description,
            icon = Icons.Rounded.Details,
            checked = preferences.fullInfoPlayer,
            onChanged = { preferences.fullInfoPlayer = !preferences.fullInfoPlayer }
        )
        CheckBoxSharedPreference(
            title = R.string.feat_setting_slider,
            icon = Icons.Rounded.SettingsEthernet,
            checked = preferences.slider,
            onChanged = { preferences.slider = !preferences.slider }
        )
        if (!tv) {
            CheckBoxSharedPreference(
                title = R.string.feat_setting_zapping_mode,
                content = R.string.feat_setting_zapping_mode_description,
                icon = Icons.Rounded.PictureInPicture,
                checked = preferences.zappingMode,
                onChanged = { preferences.zappingMode = !preferences.zappingMode }
            )
            CheckBoxSharedPreference(
                title = R.string.feat_setting_gesture_brightness,
                icon = Icons.Rounded.BrightnessMedium,
                checked = preferences.brightnessGesture,
                onChanged = { preferences.brightnessGesture = !preferences.brightnessGesture }
            )
            CheckBoxSharedPreference(
                title = R.string.feat_setting_gesture_volume,
                icon = Icons.AutoMirrored.Rounded.VolumeUp,
                checked = preferences.volumeGesture,
                onChanged = { preferences.volumeGesture = !preferences.volumeGesture }
            )
            CheckBoxSharedPreference(
                title = R.string.feat_setting_always_replay,
                icon = Icons.Rounded.ReplayCircleFilled,
                checked = preferences.alwaysShowReplay,
                onChanged = { preferences.alwaysShowReplay = !preferences.alwaysShowReplay }
            )
            CheckBoxSharedPreference(
                title = R.string.feat_setting_player_panel,
                content = R.string.feat_setting_player_panel_description,
                icon = Icons.Rounded.Unarchive,
                checked = preferences.panel,
                onChanged = { preferences.panel = !preferences.panel }
            )
            CheckBoxSharedPreference(
                title = R.string.feat_setting_cache,
                content = R.string.feat_setting_cache_description,
                checked = preferences.cache,
                icon = Icons.Rounded.Save,
                onChanged = { preferences.cache = !preferences.cache }
            )
        }

        if (!tv) {
            CheckBoxSharedPreference(
                title = R.string.feat_setting_screen_rotating,
                content = R.string.feat_setting_screen_rotating_description,
                icon = Icons.Rounded.ScreenRotation,
                checked = preferences.screenRotating,
                onChanged = { preferences.screenRotating = !preferences.screenRotating }
            )
            CheckBoxSharedPreference(
                title = R.string.feat_setting_screencast,
                icon = Icons.Rounded.Cast,
                checked = preferences.screencast,
                onChanged = { preferences.screencast = !preferences.screencast }
            )
        }

        TextPreference(
            title = stringResource(R.string.feat_setting_reconnect_mode).title(),
            icon = Icons.Rounded.Loop,
            trailing = when (preferences.reconnectMode) {
                ReconnectMode.RETRY -> stringResource(R.string.feat_setting_reconnect_mode_retry)
                ReconnectMode.RECONNECT -> stringResource(R.string.feat_setting_reconnect_mode_reconnect)
                else -> stringResource(R.string.feat_setting_reconnect_mode_no)
            },
            onClick = {
                preferences.reconnectMode = when (preferences.reconnectMode) {
                    ReconnectMode.RETRY -> ReconnectMode.RECONNECT
                    ReconnectMode.RECONNECT -> ReconnectMode.NO
                    else -> ReconnectMode.RETRY
                }
            }
        )
    }
}