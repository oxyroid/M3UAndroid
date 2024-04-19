package com.m3u.features.setting.fragments.preferences

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.BrightnessMedium
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.Details
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material.icons.rounded.Loop
import androidx.compose.material.icons.rounded.PictureInPicture
import androidx.compose.material.icons.rounded.ReplayCircleFilled
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material.icons.rounded.SettingsEthernet
import androidx.compose.material.icons.rounded.Unarchive
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.m3u.core.architecture.preferences.LocalPreferences
import com.m3u.core.architecture.preferences.annotation.ReconnectMode
import com.m3u.core.util.basic.title
import com.m3u.features.setting.components.CheckBoxSharedPreference
import com.m3u.i18n.R.string
import com.m3u.material.components.Preference
import com.m3u.material.components.TextPreference
import com.m3u.material.ktx.isTelevision

@Composable
fun OptionalPreferences(modifier: Modifier = Modifier) {
    val preferences = LocalPreferences.current
    val tv = isTelevision()

    var expended by rememberSaveable { mutableStateOf(false) }

    Column(modifier) {
        Preference(
            title = stringResource(string.feat_setting_optional_player_features).title(),
            icon = Icons.Rounded.Extension,
            onClick = { expended = !expended },
        )
        AnimatedVisibility(
            visible = expended,
            enter = expandVertically(
                expandFrom = Alignment.Bottom
            ),
            exit = shrinkVertically(
                shrinkTowards = Alignment.Bottom
            )
        ) {
            Column {
                CheckBoxSharedPreference(
                    title = string.feat_setting_tunneling,
                    content = string.feat_setting_tunneling_description,
                    icon = Icons.Rounded.FlashOn,
                    checked = preferences.tunneling,
                    onChanged = { preferences.tunneling = !preferences.tunneling }
                )
                CheckBoxSharedPreference(
                    title = string.feat_setting_full_info_player,
                    content = string.feat_setting_full_info_player_description,
                    icon = Icons.Rounded.Details,
                    checked = preferences.fullInfoPlayer,
                    onChanged = { preferences.fullInfoPlayer = !preferences.fullInfoPlayer }
                )
                CheckBoxSharedPreference(
                    title = string.feat_setting_progress,
                    icon = Icons.Rounded.SettingsEthernet,
                    checked = preferences.progress,
                    onChanged = { preferences.progress = !preferences.progress }
                )
                if (!tv) {
                    CheckBoxSharedPreference(
                        title = string.feat_setting_zapping_mode,
                        content = string.feat_setting_zapping_mode_description,
                        icon = Icons.Rounded.PictureInPicture,
                        checked = preferences.zappingMode,
                        onChanged = { preferences.zappingMode = !preferences.zappingMode }
                    )
                    CheckBoxSharedPreference(
                        title = string.feat_setting_gesture_brightness,
                        icon = Icons.Rounded.BrightnessMedium,
                        checked = preferences.brightnessGesture,
                        onChanged = { preferences.brightnessGesture = !preferences.brightnessGesture }
                    )
                    CheckBoxSharedPreference(
                        title = string.feat_setting_gesture_volume,
                        icon = Icons.AutoMirrored.Rounded.VolumeUp,
                        checked = preferences.volumeGesture,
                        onChanged = { preferences.volumeGesture = !preferences.volumeGesture }
                    )
                    CheckBoxSharedPreference(
                        title = string.feat_setting_always_replay,
                        icon = Icons.Rounded.ReplayCircleFilled,
                        checked = preferences.alwaysShowReplay,
                        onChanged = { preferences.alwaysShowReplay = !preferences.alwaysShowReplay }
                    )
                    CheckBoxSharedPreference(
                        title = string.feat_setting_player_panel,
                        content = string.feat_setting_player_panel_description,
                        icon = Icons.Rounded.Unarchive,
                        checked = preferences.playerPanel,
                        onChanged = { preferences.playerPanel = !preferences.playerPanel }
                    )
                }

                if (!tv) {
                    CheckBoxSharedPreference(
                        title = string.feat_setting_screen_rotating,
                        content = string.feat_setting_screen_rotating_description,
                        icon = Icons.Rounded.ScreenRotation,
                        checked = preferences.screenRotating,
                        onChanged = { preferences.screenRotating = !preferences.screenRotating }
                    )
                    CheckBoxSharedPreference(
                        title = string.feat_setting_screencast,
                        icon = Icons.Rounded.Cast,
                        checked = preferences.screencast,
                        onChanged = { preferences.screencast = !preferences.screencast }
                    )
                }

                TextPreference(
                    title = stringResource(string.feat_setting_reconnect_mode).title(),
                    icon = Icons.Rounded.Loop,
                    trailing = when (preferences.reconnectMode) {
                        ReconnectMode.RETRY -> stringResource(string.feat_setting_reconnect_mode_retry)
                        ReconnectMode.RECONNECT -> stringResource(string.feat_setting_reconnect_mode_reconnect)
                        else -> stringResource(string.feat_setting_reconnect_mode_no)
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
    }
}