package com.m3u.features.setting.fragments.preferences

import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.ColorLens
import androidx.compose.material.icons.rounded.DeviceHub
import androidx.compose.material.icons.rounded.FitScreen
import androidx.compose.material.icons.rounded.HideImage
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pages
import androidx.compose.material.icons.rounded.Recommend
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SettingsRemote
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Terrain
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.m3u.core.architecture.preferences.annotation.ClipMode
import com.m3u.core.architecture.preferences.annotation.ConnectTimeout
import com.m3u.core.architecture.preferences.annotation.PlaylistStrategy
import com.m3u.core.architecture.preferences.annotation.UnseensMilliseconds
import com.m3u.core.architecture.preferences.hiltPreferences
import com.m3u.core.util.basic.title
import com.m3u.features.setting.components.CheckBoxSharedPreference
import com.m3u.i18n.R.string
import com.m3u.material.components.Preference
import com.m3u.material.components.TextPreference
import com.m3u.material.ktx.isTelevision
import com.m3u.ui.Destination
import com.m3u.ui.SettingDestination
import kotlin.time.DurationUnit
import kotlin.time.toDuration

@Composable
internal fun RegularPreferences(
    destination: SettingDestination,
    navigateToPlaylistManagement: () -> Unit,
    navigateToThemeSelector: () -> Unit,
    modifier: Modifier = Modifier
) {
    val preferences = hiltPreferences()
    val tv = isTelevision()

    Column(modifier) {
        Preference(
            title = stringResource(string.feat_setting_playlist_management).title(),
            icon = Icons.Rounded.MusicNote,
            enabled = destination != SettingDestination.Playlists,
            onClick = navigateToPlaylistManagement
        )
        Preference(
            title = stringResource(string.feat_setting_appearance).title(),
            icon = Icons.Rounded.ColorLens,
            enabled = destination != SettingDestination.Appearance,
            onClick = navigateToThemeSelector
        )
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
        TextPreference(
            title = stringResource(string.feat_setting_clip_mode).title(),
            icon = Icons.Rounded.FitScreen,
            trailing = when (preferences.clipMode) {
                ClipMode.ADAPTIVE -> stringResource(string.feat_setting_clip_mode_adaptive)
                ClipMode.CLIP -> stringResource(string.feat_setting_clip_mode_clip)
                ClipMode.STRETCHED -> stringResource(string.feat_setting_clip_mode_stretched)
                else -> ""
            }.title(),
            onClick = {
                preferences.clipMode = when (preferences.clipMode) {
                    ClipMode.ADAPTIVE -> ClipMode.CLIP
                    ClipMode.CLIP -> ClipMode.STRETCHED
                    ClipMode.STRETCHED -> ClipMode.ADAPTIVE
                    else -> ClipMode.ADAPTIVE
                }
            }
        )
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
        TextPreference(
            title = stringResource(string.feat_setting_initial_screen).title(),
            icon = Icons.Rounded.Pages,
            trailing = stringResource(Destination.Root.entries[preferences.rootDestination].iconTextId).title(),
            onClick = {
                val total = Destination.Root.entries.size
                val prev = preferences.rootDestination
                preferences.rootDestination = (prev + 1).takeIf { it < total } ?: 0
            }
        )
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
        CheckBoxSharedPreference(
            title = string.feat_setting_no_picture_mode,
            content = string.feat_setting_no_picture_mode_description,
            icon = Icons.Rounded.HideImage,
            checked = preferences.noPictureMode,
            onChanged = { preferences.noPictureMode = !preferences.noPictureMode }
        )
        CheckBoxSharedPreference(
            title = string.feat_setting_auto_refresh,
            content = string.feat_setting_auto_refresh_description,
            icon = Icons.Rounded.Refresh,
            checked = preferences.autoRefresh,
            onChanged = { preferences.autoRefresh = !preferences.autoRefresh }
        )

        CheckBoxSharedPreference(
            title = string.feat_setting_randomly_in_favourite,
            icon = Icons.Rounded.Terrain,
            checked = preferences.randomlyInFavourite,
            onChanged = { preferences.randomlyInFavourite = !preferences.randomlyInFavourite }
        )

        CheckBoxSharedPreference(
            title = string.feat_setting_epg_clock_mode,
            icon = Icons.Rounded.AccessTime,
            checked = preferences.twelveHourClock,
            onChanged = { preferences.twelveHourClock = !preferences.twelveHourClock }
        )

        CheckBoxSharedPreference(
            title = if (!tv) string.feat_setting_remote_control
            else string.feat_setting_remote_control_tv_side,
            content = if (!tv) string.feat_setting_remote_control_description
            else string.feat_setting_remote_control_tv_side_description,
            icon = Icons.Rounded.SettingsRemote,
            checked = preferences.remoteControl,
            onChanged = { preferences.remoteControl = !preferences.remoteControl }
        )

        if (!tv) {
            CheckBoxSharedPreference(
                title = string.feat_setting_god_mode,
                content = string.feat_setting_god_mode_description,
                icon = Icons.Rounded.DeviceHub,
                checked = preferences.godMode,
                onChanged = { preferences.godMode = !preferences.godMode }
            )
        }
    }
}