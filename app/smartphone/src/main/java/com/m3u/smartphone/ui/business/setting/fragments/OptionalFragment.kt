package com.m3u.smartphone.ui.business.setting.fragments

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.BrightnessMedium
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.Details
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material.icons.rounded.Loop
import androidx.compose.material.icons.rounded.PictureInPicture
import androidx.compose.material.icons.rounded.Recommend
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.ReplayCircleFilled
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material.icons.rounded.SettingsEthernet
import androidx.compose.material.icons.rounded.SettingsRemote
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Unarchive
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.m3u.core.architecture.preferences.ConnectTimeout
import com.m3u.core.architecture.preferences.PlaylistStrategy
import com.m3u.core.architecture.preferences.PreferencesKeys
import com.m3u.core.architecture.preferences.ReconnectMode
import com.m3u.core.architecture.preferences.UnseensMilliseconds
import com.m3u.core.architecture.preferences.mutablePreferenceOf
import com.m3u.core.util.basic.title
import com.m3u.i18n.R.string
import com.m3u.smartphone.ui.business.setting.components.SwitchSharedPreference
import com.m3u.smartphone.ui.material.components.TextPreference
import com.m3u.smartphone.ui.material.ktx.plus
import com.m3u.smartphone.ui.material.model.LocalSpacing
import kotlin.time.DurationUnit
import kotlin.time.toDuration

@Composable
internal fun OptionalFragment(
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier
) {
    val spacing = LocalSpacing.current
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(spacing.small),
        contentPadding = contentPadding + PaddingValues(spacing.medium),
        modifier = modifier.fillMaxSize()
    ) {
        item {
            var tunneling by mutablePreferenceOf(PreferencesKeys.TUNNELING)
            SwitchSharedPreference(
                title = string.feat_setting_tunneling,
                content = string.feat_setting_tunneling_description,
                icon = Icons.Rounded.FlashOn,
                checked = tunneling,
                onChanged = { tunneling = !tunneling }
            )
        }
        item {
            var fullInfoPlayer by mutablePreferenceOf(PreferencesKeys.FULL_INFO_PLAYER)
            SwitchSharedPreference(
                title = string.feat_setting_full_info_player,
                content = string.feat_setting_full_info_player_description,
                icon = Icons.Rounded.Details,
                checked = fullInfoPlayer,
                onChanged = { fullInfoPlayer = !fullInfoPlayer }
            )
        }
        item {
            var slider by mutablePreferenceOf(PreferencesKeys.SLIDER)
            SwitchSharedPreference(
                title = string.feat_setting_slider,
                icon = Icons.Rounded.SettingsEthernet,
                checked = slider,
                onChanged = { slider = !slider }
            )
        }

        item {
            var zappingMode by mutablePreferenceOf(PreferencesKeys.ZAPPING_MODE)
            SwitchSharedPreference(
                title = string.feat_setting_zapping_mode,
                content = string.feat_setting_zapping_mode_description,
                icon = Icons.Rounded.PictureInPicture,
                checked = zappingMode,
                onChanged = { zappingMode = !zappingMode }
            )
        }
        item {
            var brightnessGesture by mutablePreferenceOf(PreferencesKeys.BRIGHTNESS_GESTURE)
            SwitchSharedPreference(
                title = string.feat_setting_gesture_brightness,
                icon = Icons.Rounded.BrightnessMedium,
                checked = brightnessGesture,
                onChanged = { brightnessGesture = !brightnessGesture }
            )
        }
        item {
            var volumeGesture by mutablePreferenceOf(PreferencesKeys.VOLUME_GESTURE)
            SwitchSharedPreference(
                title = string.feat_setting_gesture_volume,
                icon = Icons.AutoMirrored.Rounded.VolumeUp,
                checked = volumeGesture,
                onChanged = { volumeGesture = !volumeGesture }
            )
        }
        item {
            var alwaysShowReplay by mutablePreferenceOf(PreferencesKeys.ALWAYS_SHOW_REPLAY)
            SwitchSharedPreference(
                title = string.feat_setting_always_replay,
                icon = Icons.Rounded.ReplayCircleFilled,
                checked = alwaysShowReplay,
                onChanged = { alwaysShowReplay = !alwaysShowReplay }
            )
        }

        item {
            var panel by mutablePreferenceOf(PreferencesKeys.PLAYER_PANEL)
            SwitchSharedPreference(
                title = string.feat_setting_player_panel,
                content = string.feat_setting_player_panel_description,
                icon = Icons.Rounded.Unarchive,
                checked = panel,
                onChanged = { panel = !panel }
            )
        }
        item {
            var screenRotating by mutablePreferenceOf(PreferencesKeys.SCREEN_ROTATING)
            SwitchSharedPreference(
                title = string.feat_setting_screen_rotating,
                content = string.feat_setting_screen_rotating_description,
                icon = Icons.Rounded.ScreenRotation,
                checked = screenRotating,
                onChanged = { screenRotating = !screenRotating }
            )
        }
        item {
            var screencast by mutablePreferenceOf(PreferencesKeys.SCREENCAST)
            SwitchSharedPreference(
                title = string.feat_setting_screencast,
                icon = Icons.Rounded.Cast,
                checked = screencast,
                onChanged = { screencast = !screencast }
            )
        }
        item {
            var reconnectMode by mutablePreferenceOf(PreferencesKeys.RECONNECT_MODE)
            TextPreference(
                title = stringResource(string.feat_setting_reconnect_mode).title(),
                icon = Icons.Rounded.Loop,
                trailing = when (reconnectMode) {
                    ReconnectMode.RETRY -> stringResource(string.feat_setting_reconnect_mode_retry)
                    ReconnectMode.RECONNECT -> stringResource(string.feat_setting_reconnect_mode_reconnect)
                    else -> stringResource(string.feat_setting_reconnect_mode_no)
                },
                onClick = {
                    reconnectMode = when (reconnectMode) {
                        ReconnectMode.RETRY -> ReconnectMode.RECONNECT
                        ReconnectMode.RECONNECT -> ReconnectMode.NO
                        else -> ReconnectMode.RETRY
                    }
                }
            )
        }
        item {
            var playlistStrategy by mutablePreferenceOf(PreferencesKeys.PLAYLIST_STRATEGY)
            TextPreference(
                title = stringResource(string.feat_setting_sync_mode).title(),
                icon = Icons.Rounded.Sync,
                trailing = when (playlistStrategy) {
                    PlaylistStrategy.ALL -> stringResource(string.feat_setting_sync_mode_all)
                    PlaylistStrategy.KEEP -> stringResource(string.feat_setting_sync_mode_keep)
                    else -> ""
                }.title(),
                onClick = {
                    playlistStrategy = when (playlistStrategy) {
                        PlaylistStrategy.ALL -> PlaylistStrategy.KEEP
                        else -> PlaylistStrategy.ALL
                    }
                }
            )
        }
        item {
            var connectTimeout by mutablePreferenceOf(PreferencesKeys.CONNECT_TIMEOUT)
            TextPreference(
                title = stringResource(string.feat_setting_connect_timeout).title(),
                icon = Icons.Rounded.Timer,
                trailing = "${connectTimeout / 1000}s",
                onClick = {
                    connectTimeout = when (connectTimeout) {
                        ConnectTimeout.LONG -> ConnectTimeout.SHORT
                        ConnectTimeout.SHORT -> ConnectTimeout.LONG
                        else -> ConnectTimeout.SHORT
                    }
                }
            )
        }
        item {
            var unseensMilliseconds by mutablePreferenceOf(PreferencesKeys.UNSEENS_MILLISECONDS)
            val unseensMillisecondsText by remember {
                derivedStateOf {
                    val duration = unseensMilliseconds.toDuration(DurationUnit.MILLISECONDS)
                    if (unseensMilliseconds > UnseensMilliseconds.DAYS_30) "Never"
                    else duration
                        .toString()
                        .title()
                }
            }
            TextPreference(
                title = stringResource(string.feat_setting_unseen_limit).title(),
                icon = Icons.Rounded.Recommend,
                trailing = unseensMillisecondsText,
                onClick = {
                    unseensMilliseconds = when (unseensMilliseconds) {
                        UnseensMilliseconds.DAYS_3 -> UnseensMilliseconds.DAYS_7
                        UnseensMilliseconds.DAYS_7 -> UnseensMilliseconds.DAYS_30
                        UnseensMilliseconds.DAYS_30 -> UnseensMilliseconds.NEVER
                        else -> UnseensMilliseconds.DAYS_3
                    }
                }
            )
        }
        item {
            var autoRefreshChannels by mutablePreferenceOf(PreferencesKeys.AUTO_REFRESH_CHANNELS)
            SwitchSharedPreference(
                title = string.feat_setting_auto_refresh_channels,
                content = string.feat_setting_auto_refresh_channels_description,
                icon = Icons.Rounded.Refresh,
                checked = autoRefreshChannels,
                onChanged = { autoRefreshChannels = !autoRefreshChannels }
            )

        }
        item {
            var twelveHourClock by mutablePreferenceOf(PreferencesKeys.CLOCK_MODE)
            SwitchSharedPreference(
                title = string.feat_setting_epg_clock_mode,
                icon = Icons.Rounded.AccessTime,
                checked = twelveHourClock,
                onChanged = { twelveHourClock = !twelveHourClock }
            )
        }
        item {
            var remoteControl by mutablePreferenceOf(PreferencesKeys.REMOTE_CONTROL)
            SwitchSharedPreference(
                title = string.feat_setting_remote_control,
                content = string.feat_setting_remote_control_description,
                icon = Icons.Rounded.SettingsRemote,
                checked = remoteControl,
                onChanged = { remoteControl = !remoteControl }
            )
        }
    }
}