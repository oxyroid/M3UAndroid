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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Animation
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.BatchPrediction
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
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pages
import androidx.compose.material.icons.rounded.PermDeviceInformation
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SafetyCheck
import androidx.compose.material.icons.rounded.Source
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.m3u.core.annotation.ClipMode
import com.m3u.core.annotation.ConnectTimeout
import com.m3u.core.annotation.FeedStrategy
import com.m3u.core.architecture.configuration.Configuration
import com.m3u.core.architecture.configuration.ExperimentalConfiguration
import com.m3u.core.architecture.configuration.LocalConfiguration
import com.m3u.core.util.basic.title
import com.m3u.features.setting.NavigateToAbout
import com.m3u.features.setting.NavigateToConsole
import com.m3u.features.setting.SettingFragment
import com.m3u.features.setting.components.CheckBoxSharedPreference
import com.m3u.i18n.R.string
import com.m3u.material.components.IconPreference
import com.m3u.material.components.Preference
import com.m3u.material.components.TextPreference
import com.m3u.material.model.LocalSpacing
import com.m3u.ui.Destination

@OptIn(ExperimentalConfiguration::class)
@Composable
internal fun PreferencesFragment(
    fragment: SettingFragment,
    contentPadding: PaddingValues,
    versionName: String,
    versionCode: Int,
    useCommonUIModeEnable: Boolean,
    navigateToPlaylistManagement: () -> Unit,
    navigateToScriptManagement: () -> Unit,
    navigateToConsole: NavigateToConsole,
    navigateToAbout: NavigateToAbout,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalSpacing.current
    val context = LocalContext.current
    val configuration = LocalConfiguration.current

    LazyColumn(
        modifier = modifier,
        contentPadding = contentPadding,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Column(
                modifier = Modifier
                    .padding(spacing.medium)
                    .clip(MaterialTheme.shapes.medium),
                verticalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                Preference(
                    title = stringResource(string.feat_setting_feed_management).title(),
                    icon = Icons.Rounded.MusicNote,
                    enabled = fragment != SettingFragment.Subscriptions,
                    onClick = navigateToPlaylistManagement
                )
                TextPreference(
                    title = stringResource(string.feat_setting_sync_mode).title(),
                    icon = Icons.Rounded.Sync,
                    trailing = when (configuration.feedStrategy) {
                        FeedStrategy.ALL -> stringResource(string.feat_setting_sync_mode_all)
                        FeedStrategy.SKIP_FAVORITE -> stringResource(string.feat_setting_sync_mode_skip_favourite)
                        else -> ""
                    }.title(),
                    onClick = {
                        configuration.feedStrategy = when (configuration.feedStrategy) {
                            FeedStrategy.ALL -> FeedStrategy.SKIP_FAVORITE
                            else -> FeedStrategy.ALL
                        }
                    }
                )
                TextPreference(
                    title = stringResource(string.feat_setting_clip_mode).title(),
                    icon = Icons.Rounded.FitScreen,
                    trailing = when (configuration.clipMode) {
                        ClipMode.ADAPTIVE -> stringResource(string.feat_setting_clip_mode_adaptive)
                        ClipMode.CLIP -> stringResource(string.feat_setting_clip_mode_clip)
                        ClipMode.STRETCHED -> stringResource(string.feat_setting_clip_mode_stretched)
                        else -> ""
                    }.title(),
                    onClick = {
                        configuration.clipMode = when (configuration.clipMode) {
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
                    trailing = "${configuration.connectTimeout / 1000}s",
                    onClick = {
                        configuration.connectTimeout = when (configuration.connectTimeout) {
                            ConnectTimeout.LONG -> ConnectTimeout.SHORT
                            ConnectTimeout.SHORT -> ConnectTimeout.LONG
                            else -> ConnectTimeout.SHORT
                        }
                    }
                )
                TextPreference(
                    title = stringResource(string.feat_setting_initial_tab).title(),
                    icon = Icons.Rounded.Pages,
                    trailing = stringResource(Destination.Root.entries[configuration.rootDestination].iconTextId).title(),
                    onClick = {
                        val total = Destination.Root.entries.size
                        val prev = configuration.rootDestination
                        configuration.rootDestination = (prev + 1).takeIf { it < total } ?: 0
                    }
                )
                CheckBoxSharedPreference(
                    title = string.feat_setting_auto_refresh,
                    content = string.feat_setting_auto_refresh_description,
                    icon = Icons.Rounded.Refresh,
                    checked = configuration.autoRefresh,
                    onChanged = { configuration.autoRefresh = !configuration.autoRefresh }
                )
                CheckBoxSharedPreference(
                    title = string.feat_setting_no_picture_mode,
                    content = string.feat_setting_no_picture_mode_description,
                    icon = Icons.Rounded.HideImage,
                    checked = configuration.noPictureMode,
                    onChanged = { configuration.noPictureMode = !configuration.noPictureMode }
                )
                CheckBoxSharedPreference(
                    title = string.feat_setting_full_info_player,
                    content = string.feat_setting_full_info_player_description,
                    icon = Icons.Rounded.Details,
                    checked = configuration.fullInfoPlayer,
                    onChanged = { configuration.fullInfoPlayer = !configuration.fullInfoPlayer }
                )
                CheckBoxSharedPreference(
                    title = string.feat_setting_god_mode,
                    content = string.feat_setting_god_mode_description,
                    icon = Icons.Rounded.DeviceHub,
                    checked = configuration.godMode,
                    onChanged = { configuration.godMode = !configuration.godMode }
                )
                CheckBoxSharedPreference(
                    title = string.feat_setting_common_ui_mode,
                    content = if (useCommonUIModeEnable) string.feat_setting_common_ui_mode_description
                    else string.feat_setting_common_ui_mode_disabled_description,
                    icon = Icons.Rounded.Animation,
                    enabled = useCommonUIModeEnable,
                    checked = configuration.useCommonUIMode,
                    onChanged = { configuration.useCommonUIMode = !configuration.useCommonUIMode }
                )
                val useDynamicColorsAvailable = Configuration.DEFAULT_USE_DYNAMIC_COLORS

                CheckBoxSharedPreference(
                    title = string.feat_setting_use_dynamic_colors,
                    content = string
                        .feat_setting_use_dynamic_colors_unavailable
                        .takeUnless { useDynamicColorsAvailable },
                    icon = Icons.Rounded.ColorLens,
                    checked = configuration.useDynamicColors,
                    onChanged = {
                        configuration.useDynamicColors = !configuration.useDynamicColors
                    },
                    enabled = useDynamicColorsAvailable
                )
                CheckBoxSharedPreference(
                    title = string.feat_setting_zap_mode,
                    checked = configuration.zapMode,
                    onChanged = { configuration.zapMode = !configuration.zapMode }
                )
            }
        }
        item {
            Column(
                modifier = Modifier
                    .padding(spacing.medium)
                    .clip(MaterialTheme.shapes.medium),
                verticalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                CheckBoxSharedPreference(
                    title = string.feat_setting_experimental_mode,
                    content = string.feat_setting_experimental_mode_description,
                    icon = Icons.Rounded.Dangerous,
                    checked = configuration.experimentalMode,
                    onChanged = { configuration.experimentalMode = !configuration.experimentalMode }
                )
                AnimatedVisibility(
                    visible = configuration.experimentalMode,
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
                            title = string.feat_setting_cinema_mode,
                            content = string.feat_setting_not_implementation,
//                            subtitle = string.feat_setting_cinema_mode_description,
                            icon = Icons.Rounded.Chair,
                            checked = configuration.cinemaMode,
                            onChanged = { configuration.cinemaMode = !configuration.cinemaMode },
                            enabled = false
                        )
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
                            title = string.feat_setting_scroll_mode,
                            icon = Icons.Rounded.BatchPrediction,
                            checked = configuration.scrollMode,
                            onChanged = { configuration.scrollMode = !configuration.scrollMode }
                        )
                        CheckBoxSharedPreference(
                            title = string.feat_setting_ssl_verification_enabled,
                            content = string.feat_setting_ssl_verification_enabled_description,
                            icon = Icons.Rounded.SafetyCheck,
                            checked = configuration.isSSLVerification,
                            onChanged = {
                                configuration.isSSLVerification = !configuration.isSSLVerification
                            }
                        )
                    }
                }
            }
        }
        item {
            Column(
                modifier = Modifier
                    .padding(spacing.medium)
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
