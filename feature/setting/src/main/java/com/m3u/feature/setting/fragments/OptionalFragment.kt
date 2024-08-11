package com.m3u.feature.setting.fragments

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
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material.icons.rounded.SettingsEthernet
import androidx.compose.material.icons.rounded.SettingsRemote
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Terrain
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Unarchive
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.m3u.core.architecture.preferences.ConnectTimeout
import com.m3u.core.architecture.preferences.PlaylistStrategy
import com.m3u.core.architecture.preferences.ReconnectMode
import com.m3u.core.architecture.preferences.UnseensMilliseconds
import com.m3u.core.architecture.preferences.hiltPreferences
import com.m3u.core.util.basic.title
import com.m3u.feature.setting.components.SwitchSharedPreference
import com.m3u.i18n.R.string
import com.m3u.material.components.TextPreference
import com.m3u.material.ktx.includeChildGlowPadding
import com.m3u.material.ktx.tv
import com.m3u.material.ktx.plus
import com.m3u.material.model.LocalSpacing
import kotlin.time.DurationUnit
import kotlin.time.toDuration

@Composable
internal fun OptionalFragment(
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier
) {
    val spacing = LocalSpacing.current
    val preferences = hiltPreferences()
    val tv = tv()
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(spacing.small),
        contentPadding = contentPadding + PaddingValues(horizontal = spacing.medium),
        modifier = modifier
            .fillMaxSize()
            .includeChildGlowPadding()
    ) {
        item {
            SwitchSharedPreference(
                title = string.feat_setting_tunneling,
                content = string.feat_setting_tunneling_description,
                icon = Icons.Rounded.FlashOn,
                checked = preferences.tunneling,
                onChanged = { preferences.tunneling = !preferences.tunneling }
            )
        }
        item {
            SwitchSharedPreference(
                title = string.feat_setting_full_info_player,
                content = string.feat_setting_full_info_player_description,
                icon = Icons.Rounded.Details,
                checked = preferences.fullInfoPlayer,
                onChanged = { preferences.fullInfoPlayer = !preferences.fullInfoPlayer }
            )
        }
        item {
            SwitchSharedPreference(
                title = string.feat_setting_slider,
                icon = Icons.Rounded.SettingsEthernet,
                checked = preferences.slider,
                onChanged = { preferences.slider = !preferences.slider }
            )
        }

        if (!tv) {
            item {
                SwitchSharedPreference(
                    title = string.feat_setting_zapping_mode,
                    content = string.feat_setting_zapping_mode_description,
                    icon = Icons.Rounded.PictureInPicture,
                    checked = preferences.zappingMode,
                    onChanged = { preferences.zappingMode = !preferences.zappingMode }
                )
            }
            item {
                SwitchSharedPreference(
                    title = string.feat_setting_gesture_brightness,
                    icon = Icons.Rounded.BrightnessMedium,
                    checked = preferences.brightnessGesture,
                    onChanged = { preferences.brightnessGesture = !preferences.brightnessGesture }
                )
            }
            item {
                SwitchSharedPreference(
                    title = string.feat_setting_gesture_volume,
                    icon = Icons.AutoMirrored.Rounded.VolumeUp,
                    checked = preferences.volumeGesture,
                    onChanged = { preferences.volumeGesture = !preferences.volumeGesture }
                )
            }
        }
        item {
            SwitchSharedPreference(
                title = string.feat_setting_always_replay,
                icon = Icons.Rounded.ReplayCircleFilled,
                checked = preferences.alwaysShowReplay,
                onChanged = { preferences.alwaysShowReplay = !preferences.alwaysShowReplay }
            )
        }

        item {
            SwitchSharedPreference(
                title = string.feat_setting_player_panel,
                content = string.feat_setting_player_panel_description,
                icon = Icons.Rounded.Unarchive,
                checked = preferences.panel,
                onChanged = { preferences.panel = !preferences.panel }
            )
        }
        item {
            SwitchSharedPreference(
                title = string.feat_setting_cache,
                content = string.feat_setting_cache_description,
                checked = preferences.cache,
                icon = Icons.Rounded.Save,
                onChanged = { preferences.cache = !preferences.cache }
            )
        }
        if (!tv) {
            item {
                SwitchSharedPreference(
                    title = string.feat_setting_screen_rotating,
                    content = string.feat_setting_screen_rotating_description,
                    icon = Icons.Rounded.ScreenRotation,
                    checked = preferences.screenRotating,
                    onChanged = { preferences.screenRotating = !preferences.screenRotating }
                )
            }
            item {
                SwitchSharedPreference(
                    title = string.feat_setting_screencast,
                    icon = Icons.Rounded.Cast,
                    checked = preferences.screencast,
                    onChanged = { preferences.screencast = !preferences.screencast }
                )
            }
        }
        item {
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
        item {
            TextPreference(
                title = stringResource(string.feat_setting_sync_mode).title(),
                icon = Icons.Rounded.Sync,
                trailing = when (preferences.playlistStrategy) {
                    PlaylistStrategy.ALL -> stringResource(string.feat_setting_sync_mode_all)
                    PlaylistStrategy.KEEP -> stringResource(string.feat_setting_sync_mode_keep)
                    else -> ""
                }.title(),
                onClick = {
                    preferences.playlistStrategy = when (preferences.playlistStrategy) {
                        PlaylistStrategy.ALL -> PlaylistStrategy.KEEP
                        else -> PlaylistStrategy.ALL
                    }
                }
            )
        }
        item {
            TextPreference(
                title = stringResource(string.feat_setting_connect_timeout).title(),
                icon = Icons.Rounded.Timer,
                trailing = "${preferences.connectTimeout / 1000}s",
                onClick = {
                    preferences.connectTimeout = when (preferences.connectTimeout) {
                        ConnectTimeout.LONG -> ConnectTimeout.SHORT
                        ConnectTimeout.SHORT -> ConnectTimeout.LONG
                        else -> ConnectTimeout.SHORT
                    }
                }
            )
        }
        item {
            val unseensMilliseconds = preferences.unseensMilliseconds
            val unseensMillisecondsText = remember(unseensMilliseconds) {
                val duration = unseensMilliseconds
                    .toDuration(DurationUnit.MILLISECONDS)
                if (unseensMilliseconds > UnseensMilliseconds.DAYS_30) "Never"
                else duration
                    .toString()
                    .title()
            }
            TextPreference(
                title = stringResource(string.feat_setting_unseen_limit).title(),
                icon = Icons.Rounded.Recommend,
                trailing = unseensMillisecondsText,
                onClick = {
                    preferences.unseensMilliseconds = when (unseensMilliseconds) {
                        UnseensMilliseconds.DAYS_3 -> UnseensMilliseconds.DAYS_7
                        UnseensMilliseconds.DAYS_7 -> UnseensMilliseconds.DAYS_30
                        UnseensMilliseconds.DAYS_30 -> UnseensMilliseconds.NEVER
                        else -> UnseensMilliseconds.DAYS_3
                    }
                }
            )
        }
        item {
            SwitchSharedPreference(
                title = string.feat_setting_auto_refresh_channels,
                content = string.feat_setting_auto_refresh_channels_description,
                icon = Icons.Rounded.Refresh,
                checked = preferences.autoRefreshChannels,
                onChanged = { preferences.autoRefreshChannels = !preferences.autoRefreshChannels }
            )

        }
        item {
            SwitchSharedPreference(
                title = string.feat_setting_randomly_in_favourite,
                icon = Icons.Rounded.Terrain,
                checked = preferences.randomlyInFavourite,
                onChanged = { preferences.randomlyInFavourite = !preferences.randomlyInFavourite }
            )

        }
        item {
            SwitchSharedPreference(
                title = string.feat_setting_epg_clock_mode,
                icon = Icons.Rounded.AccessTime,
                checked = preferences.twelveHourClock,
                onChanged = { preferences.twelveHourClock = !preferences.twelveHourClock }
            )
        }
        item {
            SwitchSharedPreference(
                title = if (!tv) string.feat_setting_remote_control
                else string.feat_setting_remote_control_tv_side,
                content = if (!tv) string.feat_setting_remote_control_description
                else string.feat_setting_remote_control_tv_side_description,
                icon = Icons.Rounded.SettingsRemote,
                checked = preferences.remoteControl,
                onChanged = { preferences.remoteControl = !preferences.remoteControl }
            )
        }
    }
}