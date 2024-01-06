package com.m3u.features.setting.fragments.preferences

import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Animation
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Chair
import androidx.compose.material.icons.rounded.ColorLens
import androidx.compose.material.icons.rounded.DeviceHub
import androidx.compose.material.icons.rounded.FitScreen
import androidx.compose.material.icons.rounded.HideImage
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pages
import androidx.compose.material.icons.rounded.Recommend
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.m3u.core.architecture.pref.LocalPref
import com.m3u.core.architecture.pref.Pref
import com.m3u.core.architecture.pref.annotation.ClipMode
import com.m3u.core.architecture.pref.annotation.ConnectTimeout
import com.m3u.core.architecture.pref.annotation.PlaylistStrategy
import com.m3u.core.architecture.pref.annotation.UnseensMilliseconds
import com.m3u.core.util.basic.title
import com.m3u.features.setting.SettingFragment
import com.m3u.features.setting.components.CheckBoxSharedPreference
import com.m3u.i18n.R.string
import com.m3u.material.components.Preference
import com.m3u.material.components.TextPreference
import com.m3u.ui.Destination
import kotlin.time.DurationUnit
import kotlin.time.toDuration

@Composable
internal fun RegularPreferences(
    fragment: SettingFragment,
    useCommonUIModeEnable: Boolean,
    navigateToPlaylistManagement: () -> Unit,
    modifier: Modifier = Modifier
) {
    val pref = LocalPref.current
    Column(modifier) {
        Preference(
            title = stringResource(string.feat_setting_playlist_management).title(),
            icon = Icons.Rounded.MusicNote,
            enabled = fragment != SettingFragment.Subscriptions,
            onClick = navigateToPlaylistManagement
        )
        TextPreference(
            title = stringResource(string.feat_setting_sync_mode).title(),
            icon = Icons.Rounded.Sync,
            trailing = when (pref.playlistStrategy) {
                PlaylistStrategy.ALL -> stringResource(string.feat_setting_sync_mode_all)
                PlaylistStrategy.SKIP_FAVORITE -> stringResource(string.feat_setting_sync_mode_skip_favourite)
                else -> ""
            }.title(),
            onClick = {
                pref.playlistStrategy = when (pref.playlistStrategy) {
                    PlaylistStrategy.ALL -> PlaylistStrategy.SKIP_FAVORITE
                    else -> PlaylistStrategy.ALL
                }
            }
        )
        TextPreference(
            title = stringResource(string.feat_setting_clip_mode).title(),
            icon = Icons.Rounded.FitScreen,
            trailing = when (pref.clipMode) {
                ClipMode.ADAPTIVE -> stringResource(string.feat_setting_clip_mode_adaptive)
                ClipMode.CLIP -> stringResource(string.feat_setting_clip_mode_clip)
                ClipMode.STRETCHED -> stringResource(string.feat_setting_clip_mode_stretched)
                else -> ""
            }.title(),
            onClick = {
                pref.clipMode = when (pref.clipMode) {
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
            trailing = "${pref.connectTimeout / 1000}s",
            onClick = {
                pref.connectTimeout = when (pref.connectTimeout) {
                    ConnectTimeout.LONG -> ConnectTimeout.SHORT
                    ConnectTimeout.SHORT -> ConnectTimeout.LONG
                    else -> ConnectTimeout.SHORT
                }
            }
        )
        TextPreference(
            title = stringResource(string.feat_setting_initial_screen).title(),
            icon = Icons.Rounded.Pages,
            trailing = stringResource(Destination.Root.entries[pref.rootDestination].iconTextId).title(),
            onClick = {
                val total = Destination.Root.entries.size
                val prev = pref.rootDestination
                pref.rootDestination = (prev + 1).takeIf { it < total } ?: 0
            }
        )
        val unseensMilliseconds = pref.unseensMilliseconds
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
                pref.unseensMilliseconds = when (unseensMilliseconds) {
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
            checked = pref.noPictureMode,
            onChanged = { pref.noPictureMode = !pref.noPictureMode }
        )
        CheckBoxSharedPreference(
            title = string.feat_setting_god_mode,
            content = string.feat_setting_god_mode_description,
            icon = Icons.Rounded.DeviceHub,
            checked = pref.godMode,
            onChanged = { pref.godMode = !pref.godMode }
        )
        CheckBoxSharedPreference(
            title = string.feat_setting_common_ui_mode,
            content = if (useCommonUIModeEnable) string.feat_setting_common_ui_mode_description
            else string.feat_setting_common_ui_mode_disabled_description,
            icon = Icons.Rounded.Animation,
            enabled = useCommonUIModeEnable,
            checked = pref.useCommonUIMode,
            onChanged = { pref.useCommonUIMode = !pref.useCommonUIMode }
        )
        CheckBoxSharedPreference(
            title = string.feat_setting_cinema_mode,
            content = string.feat_setting_cinema_mode_description,
            icon = Icons.Rounded.Chair,
            checked = pref.cinemaMode,
            onChanged = { pref.cinemaMode = !pref.cinemaMode }
        )

        val useDynamicColorsAvailable = Pref.DEFAULT_USE_DYNAMIC_COLORS

        CheckBoxSharedPreference(
            title = string.feat_setting_use_dynamic_colors,
            content = string
                .feat_setting_use_dynamic_colors_unavailable
                .takeUnless { useDynamicColorsAvailable },
            icon = Icons.Rounded.ColorLens,
            checked = pref.useDynamicColors,
            onChanged = {
                pref.useDynamicColors = !pref.useDynamicColors
            },
            enabled = useDynamicColorsAvailable
        )

        CheckBoxSharedPreference(
            title = string.feat_setting_compact,
            icon = Icons.Rounded.AutoAwesome,
            checked = pref.compact,
            onChanged = {
                pref.compact = !pref.compact
            }
        )
    }
}