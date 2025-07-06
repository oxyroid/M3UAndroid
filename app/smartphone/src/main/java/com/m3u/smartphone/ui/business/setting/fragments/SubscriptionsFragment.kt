package com.m3u.smartphone.ui.business.setting.fragments

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import com.google.accompanist.permissions.rememberPermissionState
import com.m3u.data.database.model.DataSource
import com.m3u.data.database.model.Playlist
import com.m3u.data.database.model.Channel
import com.m3u.business.setting.BackingUpAndRestoringState
import com.m3u.i18n.R.string
import com.m3u.smartphone.ui.material.components.HorizontalPagerIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.getValue
import com.m3u.business.setting.SettingProperties
import com.m3u.core.architecture.preferences.PreferencesKeys
import com.m3u.core.architecture.preferences.preferenceOf
import com.m3u.smartphone.ui.material.components.PlaceholderField
import com.m3u.smartphone.ui.material.ktx.checkPermissionOrRationale
import com.m3u.smartphone.ui.material.ktx.textHorizontalLabel
import com.m3u.smartphone.ui.material.model.LocalSpacing
import com.m3u.smartphone.ui.business.setting.components.DataSourceSelection
import com.m3u.smartphone.ui.business.setting.components.EpgPlaylistItem
import com.m3u.smartphone.ui.business.setting.components.HiddenChannelItem
import com.m3u.smartphone.ui.business.setting.components.HiddenPlaylistGroupItem
import com.m3u.smartphone.ui.business.setting.components.LocalStorageButton
import com.m3u.smartphone.ui.common.helper.LocalHelper

private enum class SubscriptionsFragmentPage {
    MAIN, EPG_PLAYLISTS, HIDDEN_STREAMS, HIDDEN_PLAYLIST_CATEGORIES
}

@Composable
context(_ :SettingProperties)
internal fun SubscriptionsFragment(
    backingUpOrRestoring: BackingUpAndRestoringState,
    hiddenChannels: List<Channel>,
    hiddenCategoriesWithPlaylists: List<Pair<Playlist, String>>,
    onUnhideChannel: (Int) -> Unit,
    onUnhidePlaylistCategory: (playlistUrl: String, category: String) -> Unit,
    onClipboard: (String) -> Unit,
    onSubscribe: () -> Unit,
    backup: () -> Unit,
    restore: () -> Unit,
    epgs: List<Playlist>,
    onDeleteEpgPlaylist: (String) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues()
) {
    val spacing = LocalSpacing.current
    val pagerState = rememberPagerState(initialPage = 0) { SubscriptionsFragmentPage.entries.size }

    Box {
        HorizontalPager(
            state = pagerState,
            verticalAlignment = Alignment.Top,
            contentPadding = contentPadding,
            modifier = modifier
        ) { page ->
            when (SubscriptionsFragmentPage.entries[page]) {
                SubscriptionsFragmentPage.MAIN -> {
                    MainContentImpl(
                        backingUpOrRestoring = backingUpOrRestoring,
                        onClipboard = onClipboard,
                        onSubscribe = onSubscribe,
                        backup = backup,
                        restore = restore,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                SubscriptionsFragmentPage.EPG_PLAYLISTS -> {
                    EpgsContentImpl(
                        epgs = epgs,
                        onDeleteEpgPlaylist = onDeleteEpgPlaylist,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                SubscriptionsFragmentPage.HIDDEN_STREAMS -> {
                    HiddenStreamContentImpl(
                        hiddenChannels = hiddenChannels,
                        onUnhideChannel = onUnhideChannel,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                SubscriptionsFragmentPage.HIDDEN_PLAYLIST_CATEGORIES -> {
                    HiddenPlaylistCategoriesContentImpl(
                        hiddenCategoriesWithPlaylists = hiddenCategoriesWithPlaylists,
                        onUnhidePlaylistCategory = onUnhidePlaylistCategory,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
        HorizontalPagerIndicator(
            pagerState = pagerState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(contentPadding)
                .padding(spacing.medium)
        )
    }
}

@Composable
context(properties :SettingProperties)
private fun MainContentImpl(
    backingUpOrRestoring: BackingUpAndRestoringState,
    onClipboard: (String) -> Unit,
    onSubscribe: () -> Unit,
    backup: () -> Unit,
    restore: () -> Unit,
    modifier: Modifier = Modifier
) {
    val spacing = LocalSpacing.current
    val clipboardManager = LocalClipboardManager.current
    val helper = LocalHelper.current

    val remoteControl by preferenceOf(PreferencesKeys.REMOTE_CONTROL)

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(spacing.small),
        contentPadding = PaddingValues(spacing.medium),
        modifier = modifier
    ) {
        item {
            DataSourceSelection(
                selectedState = properties.selectedState,
                supported = listOf(
                    DataSource.M3U,
                    DataSource.EPG,
                    DataSource.Xtream,
                    DataSource.Emby,
                    DataSource.Dropbox
                )
            )
        }

        item {
            when (properties.selectedState.value) {
                DataSource.M3U -> M3UInputContent()
                DataSource.EPG -> EPGInputContent()
                DataSource.Xtream -> XtreamInputContent()
                DataSource.Emby -> {}
                DataSource.Dropbox -> {}
            }
        }

        item {
            @SuppressLint("InlinedApi")
            val postNotificationPermission = rememberPermissionState(
                Manifest.permission.POST_NOTIFICATIONS
            )
            Button(
                onClick = {
                    postNotificationPermission.checkPermissionOrRationale(
                        showRationale = {
                            val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                .apply {
                                    putExtra(
                                        Settings.EXTRA_APP_PACKAGE,
                                        helper.activityContext.packageName
                                    )
                                }
                            helper.activityContext.startActivity(intent)
                        },
                        block = {
                            onSubscribe()
                        }
                    )
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(string.feat_setting_label_subscribe).uppercase())
            }
            when (properties.selectedState.value) {
                DataSource.M3U, DataSource.Xtream -> {
                    FilledTonalButton(
                        enabled = !properties.localStorageState.value,
                        onClick = {
                            onClipboard(clipboardManager.getText()?.text.orEmpty())
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(string.feat_setting_label_parse_from_clipboard).uppercase())
                    }
                }

                else -> {}
            }
        }

        item {
            FilledTonalButton(
                enabled = backingUpOrRestoring == BackingUpAndRestoringState.NONE,
                onClick = backup,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = stringResource(string.feat_setting_label_backup).uppercase())
            }
            FilledTonalButton(
                enabled = backingUpOrRestoring == BackingUpAndRestoringState.NONE,
                onClick = restore,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = stringResource(string.feat_setting_label_restore).uppercase())
            }
        }

        item {
            Spacer(Modifier.imePadding())
        }
    }
}


@Composable
private fun EpgsContentImpl(
    epgs: List<Playlist>,
    onDeleteEpgPlaylist: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth()
    ) {
        Text(
            text = stringResource(string.feat_setting_label_epg_playlists),
            color = MaterialTheme.colorScheme.onPrimary,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.textHorizontalLabel()
        )
        epgs.forEach { epgPlaylist ->
            EpgPlaylistItem(
                epgPlaylist = epgPlaylist,
                onDeleteEpgPlaylist = { onDeleteEpgPlaylist(epgPlaylist.url) }
            )
        }
    }
}

@Composable
private fun HiddenStreamContentImpl(
    hiddenChannels: List<Channel>,
    onUnhideChannel: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth()
    ) {
        Text(
            text = stringResource(string.feat_setting_label_hidden_channels),
            color = MaterialTheme.colorScheme.onPrimary,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.textHorizontalLabel()
        )
        hiddenChannels.forEach { channel ->
            HiddenChannelItem(
                channel = channel,
                onHidden = { onUnhideChannel(channel.id) }
            )
        }
    }
}

@Composable
private fun HiddenPlaylistCategoriesContentImpl(
    hiddenCategoriesWithPlaylists: List<Pair<Playlist, String>>,
    onUnhidePlaylistCategory: (playlistUrl: String, category: String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier.fillMaxWidth()) {
        Text(
            text = stringResource(string.feat_setting_label_hidden_playlist_groups),
            color = MaterialTheme.colorScheme.onPrimary,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.textHorizontalLabel()
        )
        hiddenCategoriesWithPlaylists.forEach { (playlist, category) ->
            HiddenPlaylistGroupItem(
                playlist = playlist,
                group = category,
                onHidden = { onUnhidePlaylistCategory(playlist.url, category) }
            )
        }
    }
}

@Composable
context(properties :SettingProperties)
private fun M3UInputContent(
    modifier: Modifier = Modifier
) {
    val spacing = LocalSpacing.current
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(spacing.small)
    ) {
        PlaceholderField(
            text = properties.titleState.value,
            placeholder = stringResource(string.feat_setting_placeholder_title).uppercase(),
            onValueChange = { properties.titleState.value = Uri.decode(it) },
            modifier = Modifier.fillMaxWidth()
        )
        Crossfade(
            targetState = properties.localStorageState.value,
            label = "url"
        ) { localStorage ->
            if (!localStorage) {
                PlaceholderField(
                    text = properties.urlState.value,
                    placeholder = stringResource(string.feat_setting_placeholder_url).uppercase(),
                    onValueChange = { properties.urlState.value = Uri.decode(it) },
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                LocalStorageButton(
                    titleState = properties.titleState,
                    uriState = properties.uriState,
                )
            }
        }
    }
}

@Composable
context(properties :SettingProperties)
private fun EPGInputContent(
    modifier: Modifier = Modifier
) {
    val spacing = LocalSpacing.current
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(spacing.small)
    ) {
        PlaceholderField(
            text = properties.titleState.value,
            placeholder = stringResource(string.feat_setting_placeholder_epg_title).uppercase(),
            onValueChange = { properties.titleState.value = Uri.decode(it) },
            modifier = Modifier.fillMaxWidth()
        )
        PlaceholderField(
            text = properties.epgState.value,
            placeholder = stringResource(string.feat_setting_placeholder_epg).uppercase(),
            onValueChange = { properties.epgState.value = Uri.decode(it) },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
context(properties :SettingProperties)
private fun XtreamInputContent(modifier: Modifier = Modifier) {
    val spacing = LocalSpacing.current

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(spacing.small)
    ) {
        PlaceholderField(
            text = properties.titleState.value,
            placeholder = stringResource(string.feat_setting_placeholder_title).uppercase(),
            onValueChange = { properties.titleState.value = Uri.decode(it) },
            modifier = Modifier.fillMaxWidth()
        )
        PlaceholderField(
            text = properties.basicUrlState.value,
            placeholder = stringResource(string.feat_setting_placeholder_basic_url).uppercase(),
            onValueChange = { properties.basicUrlState.value = it },
            modifier = Modifier.fillMaxWidth()
        )
        PlaceholderField(
            text = properties.usernameState.value,
            placeholder = stringResource(string.feat_setting_placeholder_username).uppercase(),
            onValueChange = { properties.usernameState.value = it },
            modifier = Modifier.fillMaxWidth()
        )
        PlaceholderField(
            text = properties.passwordState.value,
            placeholder = stringResource(string.feat_setting_placeholder_password).uppercase(),
            onValueChange = { properties.passwordState.value = it },
            modifier = Modifier.fillMaxWidth()
        )
        Warning(stringResource(string.feat_setting_warning_xtream_takes_much_more_time))
    }
}

@Composable
private fun Warning(
    text: String,
    modifier: Modifier = Modifier
) {
    val spacing = LocalSpacing.current
    CompositionLocalProvider(
        LocalContentColor provides LocalContentColor.current.copy(0.54f)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(spacing.small),
            verticalAlignment = Alignment.CenterVertically,
            modifier = modifier
        ) {
            Icon(imageVector = Icons.Rounded.Warning, contentDescription = null)
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}
