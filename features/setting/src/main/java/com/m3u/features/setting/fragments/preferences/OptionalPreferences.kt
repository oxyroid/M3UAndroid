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
import androidx.compose.material.icons.rounded.Loop
import androidx.compose.material.icons.rounded.PictureInPicture
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material.icons.rounded.SlowMotionVideo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.m3u.core.architecture.pref.LocalPref
import com.m3u.core.util.basic.title
import com.m3u.features.setting.components.CheckBoxSharedPreference
import com.m3u.i18n.R.string
import com.m3u.material.components.Preference
import com.m3u.material.ktx.isTvDevice

@Composable
fun OptionalPreferences(modifier: Modifier = Modifier) {
    val pref = LocalPref.current
    val tv = isTvDevice()
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
                    title = string.feat_setting_full_info_player,
                    content = string.feat_setting_full_info_player_description,
                    icon = Icons.Rounded.Details,
                    checked = pref.fullInfoPlayer,
                    onChanged = { pref.fullInfoPlayer = !pref.fullInfoPlayer }
                )
                if (!tv) {
                    CheckBoxSharedPreference(
                        title = string.feat_setting_zapping_mode,
                        content = string.feat_setting_zapping_mode_description,
                        icon = Icons.Rounded.PictureInPicture,
                        checked = pref.zappingMode,
                        onChanged = { pref.zappingMode = !pref.zappingMode }
                    )
                    CheckBoxSharedPreference(
                        title = string.feat_setting_gesture_brightness,
                        icon = Icons.Rounded.BrightnessMedium,
                        checked = pref.brightnessGesture,
                        onChanged = { pref.brightnessGesture = !pref.brightnessGesture }
                    )
                    CheckBoxSharedPreference(
                        title = string.feat_setting_gesture_volume,
                        icon = Icons.AutoMirrored.Rounded.VolumeUp,
                        checked = pref.volumeGesture,
                        onChanged = { pref.volumeGesture = !pref.volumeGesture }
                    )
                }

                CheckBoxSharedPreference(
                    title = string.feat_setting_record,
                    content = string.feat_setting_not_implementation,
                    icon = Icons.Rounded.SlowMotionVideo,
                    enabled = false,
                    checked = pref.record,
                    onChanged = { pref.record = !pref.record }
                )
                if (!tv) {
                    CheckBoxSharedPreference(
                        title = string.feat_setting_screen_rotating,
                        content = string.feat_setting_screen_rotating_description,
                        icon = Icons.Rounded.ScreenRotation,
                        checked = pref.screenRotating,
                        onChanged = { pref.screenRotating = !pref.screenRotating }
                    )
                    CheckBoxSharedPreference(
                        title = string.feat_setting_screencast,
                        icon = Icons.Rounded.Cast,
                        checked = pref.screencast,
                        onChanged = { pref.screencast = !pref.screencast }
                    )
                }

                CheckBoxSharedPreference(
                    title = string.feat_setting_auto_reconnect,
                    icon = Icons.Rounded.Loop,
                    checked = pref.autoReconnect,
                    onChanged = { pref.autoReconnect = !pref.autoReconnect }
                )
            }
        }
    }
}