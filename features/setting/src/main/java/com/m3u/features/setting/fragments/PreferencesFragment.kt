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
import com.m3u.core.annotation.OnClipMode
import com.m3u.core.annotation.OnFeedStrategy
import com.m3u.core.architecture.configuration.Configuration
import com.m3u.core.util.basic.title
import com.m3u.features.setting.NavigateToAbout
import com.m3u.features.setting.NavigateToConsole
import com.m3u.features.setting.components.CheckBoxSharedPreference
import com.m3u.i18n.R.string
import com.m3u.material.components.IconPreference
import com.m3u.material.components.Preference
import com.m3u.material.components.TextPreference
import com.m3u.material.model.LocalSpacing
import com.m3u.ui.Destination

@Composable
internal fun PreferencesFragment(
    contentPadding: PaddingValues,
    versionName: String,
    versionCode: Int,
    @FeedStrategy feedStrategy: Int,
    @ConnectTimeout connectTimeout: Int,
    @ClipMode clipMode: Int,
    useCommonUIMode: Boolean,
    useCommonUIModeEnable: Boolean,
    useDynamicColors: Boolean,
    onUseDynamicColors: () -> Unit,
    experimentalMode: Boolean,
    godMode: Boolean,
    scrollMode: Boolean,
    fullInfoPlayer: Boolean,
    onFeedStrategy: OnFeedStrategy,
    onClipMode: OnClipMode,
    onUIMode: () -> Unit,
    onGodMode: () -> Unit,
    onScrollMode: () -> Unit,
    onFeedManagement: () -> Unit,
    onScriptManagement: () -> Unit,
    onConnectTimeout: () -> Unit,
    onExperimentalMode: () -> Unit,
    onFullInfoPlayer: () -> Unit,
    navigateToConsole: NavigateToConsole,
    navigateToAbout: NavigateToAbout,
    autoRefresh: Boolean,
    onAutoRefresh: () -> Unit,
    isSSLVerificationEnabled: Boolean,
    onSSLVerificationEnabled: () -> Unit,
    initialRootDestination: Int,
    onInitialTabIndex: () -> Unit,
    noPictureMode: Boolean,
    onNoPictureMode: () -> Unit,
    cinemaMode: Boolean,
    onCinemaMode: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalSpacing.current
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
                    enabled = true,
                    onClick = onFeedManagement
                )

                TextPreference(
                    title = stringResource(string.feat_setting_sync_mode).title(),
                    trailing = when (feedStrategy) {
                        FeedStrategy.ALL -> stringResource(string.feat_setting_sync_mode_all)
                        FeedStrategy.SKIP_FAVORITE -> stringResource(string.feat_setting_sync_mode_skip_favourite)
                        else -> ""
                    }.title(),
                    onClick = onFeedStrategy
                )
                TextPreference(
                    title = stringResource(string.feat_setting_clip_mode).title(),
                    trailing = when (clipMode) {
                        ClipMode.ADAPTIVE -> stringResource(string.feat_setting_clip_mode_adaptive)
                        ClipMode.CLIP -> stringResource(string.feat_setting_clip_mode_clip)
                        ClipMode.STRETCHED -> stringResource(string.feat_setting_clip_mode_stretched)
                        else -> ""
                    }.title(),
                    onClick = onClipMode
                )
                TextPreference(
                    title = stringResource(string.feat_setting_connect_timeout).title(),
                    trailing = "${connectTimeout / 1000}s",
                    onClick = onConnectTimeout
                )
                TextPreference(
                    title = stringResource(string.feat_setting_initial_tab).title(),
                    trailing = stringResource(Destination.Root.entries[initialRootDestination].iconTextId).title(),
                    onClick = onInitialTabIndex
                )
                CheckBoxSharedPreference(
                    title = string.feat_setting_auto_refresh,
                    content = string.feat_setting_auto_refresh_description,
                    checked = autoRefresh,
                    onChanged = onAutoRefresh
                )
                CheckBoxSharedPreference(
                    title = string.feat_setting_no_picture_mode,
                    content = string.feat_setting_no_picture_mode_description,
                    checked = noPictureMode,
                    onChanged = onNoPictureMode
                )
                CheckBoxSharedPreference(
                    title = string.feat_setting_full_info_player,
                    content = string.feat_setting_full_info_player_description,
                    checked = fullInfoPlayer,
                    onChanged = onFullInfoPlayer
                )
                CheckBoxSharedPreference(
                    title = string.feat_setting_god_mode,
                    content = string.feat_setting_god_mode_description,
                    checked = godMode,
                    onChanged = onGodMode
                )
                CheckBoxSharedPreference(
                    title = string.feat_setting_common_ui_mode,
                    content = if (useCommonUIModeEnable) string.feat_setting_common_ui_mode_description
                    else string.feat_setting_common_ui_mode_disabled_description,
                    enabled = useCommonUIModeEnable,
                    checked = useCommonUIMode,
                    onChanged = onUIMode
                )
                val useDynamicColorsAvailable = Configuration.DEFAULT_USE_DYNAMIC_COLORS
                CheckBoxSharedPreference(
                    title = string.feat_setting_use_dynamic_colors,
                    content = string
                        .feat_setting_use_dynamic_colors_unavailable
                        .takeUnless { useDynamicColorsAvailable },
                    checked = useDynamicColors,
                    onChanged = onUseDynamicColors,
                    enabled = useDynamicColorsAvailable
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
                    checked = experimentalMode,
                    onChanged = onExperimentalMode
                )
                AnimatedVisibility(
                    visible = experimentalMode,
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
                            checked = cinemaMode,
                            onChanged = onCinemaMode,
                            enabled = false
                        )
                        Preference(
                            title = stringResource(string.feat_setting_script_management).title(),
                            content = stringResource(string.feat_setting_not_implementation).title(),
                            enabled = false,
                            onClick = onScriptManagement
                        )
                        Preference(
                            title = stringResource(string.feat_setting_console_editor).title(),
                            content = stringResource(string.feat_setting_not_implementation).title(),
                            enabled = false,
                            onClick = navigateToConsole
                        )
                        CheckBoxSharedPreference(
                            title = string.feat_setting_scroll_mode,
                            checked = scrollMode,
                            onChanged = onScrollMode
                        )
                        CheckBoxSharedPreference(
                            title = string.feat_setting_ssl_verification_enabled,
                            content = string.feat_setting_ssl_verification_enabled_description,
                            checked = isSSLVerificationEnabled,
                            onChanged = onSSLVerificationEnabled
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
                val context = LocalContext.current
                IconPreference(
                    title = stringResource(string.feat_setting_system_setting).title(),
                    imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                    onClick = {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", context.packageName, null)
                        }
                        context.startActivity(intent)
                    }
                )
                // TODO: https://www.dropbox.com/developers/documentation/http/documentation#file_requests-list
                Preference(
                    title = stringResource(string.feat_setting_dropbox).uppercase(),
                    onClick = navigateToAbout,
                    enabled = false
                )
                Preference(
                    title = stringResource(string.feat_setting_project_about).title(),
                    onClick = navigateToAbout,
//                    enabled = false
                )
                Preference(
                    title = stringResource(string.feat_setting_app_version).title(),
                    content = "$versionName ($versionCode)"
                )
            }
        }
    }
}
