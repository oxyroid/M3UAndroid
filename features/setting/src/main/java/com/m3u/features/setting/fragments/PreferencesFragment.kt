package com.m3u.features.setting.fragments

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Animation
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.BrightnessMedium
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.Chair
import androidx.compose.material.icons.rounded.ColorLens
import androidx.compose.material.icons.rounded.Dangerous
import androidx.compose.material.icons.rounded.Details
import androidx.compose.material.icons.rounded.DeviceHub
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.FitScreen
import androidx.compose.material.icons.rounded.HideImage
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Loop
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pages
import androidx.compose.material.icons.rounded.PermDeviceInformation
import androidx.compose.material.icons.rounded.PictureInPicture
import androidx.compose.material.icons.rounded.Recommend
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SafetyCheck
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material.icons.rounded.SlowMotionVideo
import androidx.compose.material.icons.rounded.Source
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import com.m3u.core.architecture.pref.LocalPref
import com.m3u.core.architecture.pref.Pref.Companion.DEFAULT_USE_DYNAMIC_COLORS
import com.m3u.core.architecture.pref.annotation.ClipMode
import com.m3u.core.architecture.pref.annotation.ConnectTimeout
import com.m3u.core.architecture.pref.annotation.PlaylistStrategy
import com.m3u.core.architecture.pref.annotation.UnseensMilliseconds
import com.m3u.core.util.basic.title
import com.m3u.features.setting.SettingFragment
import com.m3u.features.setting.components.CheckBoxSharedPreference
import com.m3u.i18n.R.string
import com.m3u.material.components.IconPreference
import com.m3u.material.components.Preference
import com.m3u.material.components.TextPreference
import com.m3u.material.ktx.plus
import com.m3u.material.model.LocalSpacing
import com.m3u.ui.Destination
import kotlin.time.DurationUnit
import kotlin.time.toDuration

@Composable
internal fun PreferencesFragment(
    fragment: SettingFragment,
    contentPadding: PaddingValues,
    versionName: String,
    versionCode: Int,
    useCommonUIModeEnable: Boolean,
    navigateToPlaylistManagement: () -> Unit,
    navigateToScriptManagement: () -> Unit,
    navigateToConsole: () -> Unit,
    navigateToAbout: () -> Unit,
    modifier: Modifier = Modifier
) {
    val spacing = LocalSpacing.current
    val context = LocalContext.current
    val pref = LocalPref.current

    LazyColumn(
        modifier = modifier,
        contentPadding = contentPadding + PaddingValues(spacing.medium),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(spacing.medium)
    ) {
        item {
            Column(
                modifier = Modifier
                    .clip(MaterialTheme.shapes.medium),
                verticalArrangement = Arrangement.spacedBy(1.dp)
            ) {
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

                val useDynamicColorsAvailable = DEFAULT_USE_DYNAMIC_COLORS

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
            }
        }
        item {
            var expended by rememberSaveable { mutableStateOf(false) }
            Column(
                modifier = Modifier
                    .clip(MaterialTheme.shapes.medium),
                verticalArrangement = Arrangement.spacedBy(1.dp)
            ) {
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
                    Column(
                        verticalArrangement = Arrangement.spacedBy(1.dp)
                    ) {
                        CheckBoxSharedPreference(
                            title = string.feat_setting_full_info_player,
                            content = string.feat_setting_full_info_player_description,
                            icon = Icons.Rounded.Details,
                            checked = pref.fullInfoPlayer,
                            onChanged = { pref.fullInfoPlayer = !pref.fullInfoPlayer }
                        )
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
                        CheckBoxSharedPreference(
                            title = string.feat_setting_record,
                            icon = Icons.Rounded.SlowMotionVideo,
                            enabled = false,
                            checked = pref.record,
                            onChanged = { pref.record = !pref.record }
                        )
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
        item {
            Column(
                modifier = Modifier
                    .clip(MaterialTheme.shapes.medium),
                verticalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                val toggleableState by remember {
                    derivedStateOf {
                        when {
                            !pref.experimentalMode -> ToggleableState.Off
                            with(pref) { !cinemaMode || !isSSLVerification } -> ToggleableState.Indeterminate
                            else -> ToggleableState.On
                        }
                    }
                }
                Preference(
                    title = stringResource(string.feat_setting_experimental_mode).title(),
                    content = stringResource(string.feat_setting_experimental_mode_description),
                    icon = Icons.Rounded.Dangerous,
                    onClick = { pref.experimentalMode = !pref.experimentalMode },
                    trailing = {
                        TriStateCheckbox(
                            state = toggleableState,
                            onClick = null
                        )
                    }
                )
                AnimatedVisibility(
                    visible = pref.experimentalMode,
                    enter = expandVertically(
                        expandFrom = Alignment.Bottom
                    ),
                    exit = shrinkVertically(
                        shrinkTowards = Alignment.Bottom
                    )
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(1.dp)
                    ) {
                        Preference(
                            title = stringResource(string.feat_setting_script_management).title(),
                            content = stringResource(string.feat_setting_not_implementation).title(),
                            icon = Icons.Rounded.Extension,
                            enabled = false,
                            onClick = navigateToScriptManagement
                        )
                        Preference(
                            title = stringResource(string.feat_setting_console_editor).title(),
                            content = stringResource(string.feat_setting_not_implementation).title(),
                            icon = Icons.Rounded.Edit,
                            enabled = false,
                            onClick = navigateToConsole
                        )
                        CheckBoxSharedPreference(
                            title = string.feat_setting_auto_refresh,
                            content = string.feat_setting_auto_refresh_description,
                            icon = Icons.Rounded.Refresh,
                            checked = pref.autoRefresh,
                            onChanged = { pref.autoRefresh = !pref.autoRefresh }
                        )
                        CheckBoxSharedPreference(
                            title = string.feat_setting_ssl_verification_enabled,
                            content = string.feat_setting_ssl_verification_enabled_description,
                            icon = Icons.Rounded.SafetyCheck,
                            checked = pref.isSSLVerification,
                            onChanged = {
                                pref.isSSLVerification = !pref.isSSLVerification
                            }
                        )
                    }
                }
            }
        }
        item {
            Column(
                modifier = Modifier
                    .clip(MaterialTheme.shapes.medium),
                verticalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                IconPreference(
                    title = stringResource(string.feat_setting_system_setting).title(),
                    imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                    icon = Icons.Rounded.PermDeviceInformation,
                    onClick = {
                        val intent =
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.fromParts("package", context.packageName, null)
                            }
                        context.startActivity(intent)
                    }
                )
                // TODO: https://www.dropbox.com/developers/documentation/http/documentation#file_requests-list
                Preference(
                    title = stringResource(string.feat_setting_dropbox).uppercase(),
                    icon = Icons.Rounded.Backup,
                    onClick = navigateToAbout,
                    enabled = false
                )
                Preference(
                    title = stringResource(string.feat_setting_project_about).title(),
                    icon = Icons.Rounded.Source,
                    onClick = navigateToAbout,
//                    enabled = false
                )
                Preference(
                    title = stringResource(string.feat_setting_app_version).title(),
                    content = "$versionName ($versionCode)",
                    icon = Icons.Rounded.Info,
                )
            }
        }
    }
}
