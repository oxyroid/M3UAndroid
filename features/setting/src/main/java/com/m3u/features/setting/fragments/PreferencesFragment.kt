package com.m3u.features.setting.fragments

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
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
import com.m3u.features.setting.NavigateToAbout
import com.m3u.features.setting.NavigateToConsole
import com.m3u.features.setting.components.CheckBoxPreference
import com.m3u.features.setting.components.IconPreference
import com.m3u.features.setting.components.Preference
import com.m3u.features.setting.components.TextPreference
import com.m3u.ui.Destination
import com.m3u.ui.model.LocalSpacing
import com.m3u.i18n.R as I18R

@Composable
internal fun PreferencesFragment(
    version: String,
    @FeedStrategy feedStrategy: Int,
    @ConnectTimeout connectTimeout: Int,
    @ClipMode clipMode: Int,
    useCommonUIMode: Boolean,
    useCommonUIModeEnable: Boolean,
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
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Column(
                modifier = Modifier
                    .padding(spacing.medium)
                    .clip(RoundedCornerShape(spacing.medium)),
                verticalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                Preference(
                    title = stringResource(I18R.string.feat_setting_feed_management),
                    enabled = true,
                    onClick = onFeedManagement
                )

                TextPreference(
                    title = stringResource(I18R.string.feat_setting_sync_mode),
                    content = when (feedStrategy) {
                        FeedStrategy.ALL -> stringResource(I18R.string.feat_setting_sync_mode_all)
                        FeedStrategy.SKIP_FAVORITE -> stringResource(I18R.string.feat_setting_sync_mode_skip_favourite)
                        else -> ""
                    },
                    onClick = onFeedStrategy
                )
                TextPreference(
                    title = stringResource(I18R.string.feat_setting_clip_mode),
                    content = when (clipMode) {
                        ClipMode.ADAPTIVE -> stringResource(I18R.string.feat_setting_clip_mode_adaptive)
                        ClipMode.CLIP -> stringResource(I18R.string.feat_setting_clip_mode_clip)
                        ClipMode.STRETCHED -> stringResource(I18R.string.feat_setting_clip_mode_stretched)
                        else -> ""
                    },
                    onClick = onClipMode
                )
                TextPreference(
                    title = stringResource(I18R.string.feat_setting_connect_timeout),
                    content = "${connectTimeout / 1000}s",
                    onClick = onConnectTimeout
                )
                TextPreference(
                    title = stringResource(I18R.string.feat_setting_initial_tab),
                    content = stringResource(Destination.Root.entries[initialRootDestination].iconTextId),
                    onClick = onInitialTabIndex
                )
                CheckBoxPreference(
                    title = stringResource(I18R.string.feat_setting_auto_refresh),
                    subtitle = stringResource(I18R.string.feat_setting_auto_refresh_description),
                    checked = autoRefresh,
                    onCheckedChange = { newValue ->
                        if (newValue != autoRefresh) {
                            onAutoRefresh()
                        }
                    }
                )
                CheckBoxPreference(
                    title = stringResource(I18R.string.feat_setting_no_picture_mode),
                    subtitle = stringResource(I18R.string.feat_setting_no_picture_mode_description),
                    checked = noPictureMode,
                    onCheckedChange = { newValue ->
                        if (newValue != noPictureMode) {
                            onNoPictureMode()
                        }
                    }
                )
                CheckBoxPreference(
                    title = stringResource(I18R.string.feat_setting_full_info_player),
                    subtitle = stringResource(I18R.string.feat_setting_full_info_player_description),
                    checked = fullInfoPlayer,
                    onCheckedChange = { newValue ->
                        if (newValue != fullInfoPlayer) {
                            onFullInfoPlayer()
                        }
                    }
                )
                CheckBoxPreference(
                    title = stringResource(I18R.string.feat_setting_god_mode),
                    subtitle = stringResource(I18R.string.feat_setting_god_mode_description),
                    checked = godMode,
                    onCheckedChange = { newValue ->
                        if (newValue != godMode) {
                            onGodMode()
                        }
                    }
                )
                CheckBoxPreference(
                    title = stringResource(I18R.string.feat_setting_common_ui_mode),
                    subtitle = if (useCommonUIModeEnable) stringResource(I18R.string.feat_setting_common_ui_mode_description)
                    else stringResource(I18R.string.feat_setting_common_ui_mode_disabled_description),
                    enabled = useCommonUIModeEnable,
                    checked = useCommonUIMode,
                    onCheckedChange = { newValue ->
                        if (newValue != useCommonUIMode) {
                            onUIMode()
                        }
                    }
                )

            }
        }
        item {
            Column(
                modifier = Modifier
                    .padding(spacing.medium)
                    .clip(RoundedCornerShape(spacing.medium)),
                verticalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                CheckBoxPreference(
                    title = stringResource(I18R.string.feat_setting_experimental_mode),
                    subtitle = stringResource(I18R.string.feat_setting_experimental_mode_description),
                    checked = experimentalMode,
                    onCheckedChange = { newValue ->
                        if (newValue != experimentalMode) {
                            onExperimentalMode()
                        }
                    }
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
                        CheckBoxPreference(
                            title = stringResource(I18R.string.feat_setting_cinema_mode),
                            subtitle = stringResource(I18R.string.feat_setting_cinema_mode_description),
                            checked = cinemaMode,
                            onCheckedChange = { newValue ->
                                if (newValue != cinemaMode) {
                                    onCinemaMode()
                                }
                            }
                        )
                        Preference(
                            title = stringResource(I18R.string.feat_setting_script_management),
                            enabled = true,
                            onClick = onScriptManagement
                        )
                        Preference(
                            title = stringResource(I18R.string.feat_setting_console_editor),
                            onClick = navigateToConsole
                        )
                        CheckBoxPreference(
                            title = stringResource(I18R.string.feat_setting_scroll_mode),
                            checked = scrollMode,
                            onCheckedChange = { newValue ->
                                if (newValue != scrollMode) {
                                    onScrollMode()
                                }
                            }
                        )
                        CheckBoxPreference(
                            title = stringResource(I18R.string.feat_setting_ssl_verification_enabled),
                            subtitle = stringResource(I18R.string.feat_setting_ssl_verification_enabled_description),
                            checked = isSSLVerificationEnabled,
                            onCheckedChange = { newValue ->
                                if (newValue != isSSLVerificationEnabled) {
                                    onSSLVerificationEnabled()
                                }
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
                    .clip(RoundedCornerShape(spacing.medium)),
                verticalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                val context = LocalContext.current
                IconPreference(
                    title = stringResource(I18R.string.feat_setting_system_setting),
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
                    title = stringResource(I18R.string.feat_setting_dropbox).uppercase(),
                    onClick = navigateToAbout,
                    enabled = false
                )
                Preference(
                    title = stringResource(I18R.string.feat_setting_project_about),
                    onClick = navigateToAbout,
//                    enabled = false
                )
                Preference(
                    title = stringResource(I18R.string.feat_setting_app_version),
                    subtitle = version
                )
            }
        }
    }
}
