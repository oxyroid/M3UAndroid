package com.m3u.feature.setting.fragments

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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import com.google.accompanist.permissions.rememberPermissionState
import com.m3u.core.architecture.preferences.hiltPreferences
import com.m3u.data.database.model.DataSource
import com.m3u.data.database.model.Playlist
import com.m3u.data.database.model.Channel
import com.m3u.feature.setting.BackingUpAndRestoringState
import com.m3u.feature.setting.components.DataSourceSelection
import com.m3u.feature.setting.components.EpgPlaylistItem
import com.m3u.feature.setting.components.HiddenPlaylistGroupItem
import com.m3u.feature.setting.components.HiddenChannelItem
import com.m3u.feature.setting.components.LocalStorageButton
import com.m3u.feature.setting.components.LocalStorageSwitch
import com.m3u.feature.setting.components.RemoteControlSubscribeSwitch
import com.m3u.i18n.R.string
import com.m3u.material.components.Button
import com.m3u.material.components.HorizontalPagerIndicator
import com.m3u.material.components.Icon
import com.m3u.material.components.PlaceholderField
import com.m3u.material.components.TonalButton
import com.m3u.material.ktx.checkPermissionOrRationale
import com.m3u.material.ktx.tv
import com.m3u.material.ktx.textHorizontalLabel
import com.m3u.material.model.LocalSpacing
import com.m3u.ui.helper.LocalHelper

private enum class SubscriptionsFragmentPage {
    MAIN, EPG_PLAYLISTS, HIDDEN_STREAMS, HIDDEN_PLAYLIST_CATEGORIES
}

@Composable
internal fun SubscriptionsFragment(
    titleState: MutableState<String>,
    urlState: MutableState<String>,
    uriState: MutableState<Uri>,
    selectedState: MutableState<DataSource>,
    basicUrlState: MutableState<String>,
    usernameState: MutableState<String>,
    passwordState: MutableState<String>,
    epgState: MutableState<String>,
    localStorageState: MutableState<Boolean>,
    forTvState: MutableState<Boolean>,
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
    val pagerState = rememberPagerState { SubscriptionsFragmentPage.entries.size }

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
                        titleState = titleState,
                        urlState = urlState,
                        uriState = uriState,
                        selectedState = selectedState,
                        basicUrlState = basicUrlState,
                        usernameState = usernameState,
                        passwordState = passwordState,
                        localStorageState = localStorageState,
                        forTvState = forTvState,
                        backingUpOrRestoring = backingUpOrRestoring,
                        epgState = epgState,
                        onClipboard = onClipboard,
                        onSubscribe = onSubscribe,
                        backup = backup,
                        restore = restore
                    )
                }

                SubscriptionsFragmentPage.EPG_PLAYLISTS -> {
                    EpgsContentImpl(
                        epgs = epgs,
                        onDeleteEpgPlaylist = onDeleteEpgPlaylist
                    )
                }

                SubscriptionsFragmentPage.HIDDEN_STREAMS -> {
                    HiddenStreamContentImpl(
                        hiddenChannels = hiddenChannels,
                        onUnhideChannel = onUnhideChannel
                    )
                }

                SubscriptionsFragmentPage.HIDDEN_PLAYLIST_CATEGORIES -> {
                    HiddenPlaylistCategoriesContentImpl(
                        hiddenCategoriesWithPlaylists = hiddenCategoriesWithPlaylists,
                        onUnhidePlaylistCategory = onUnhidePlaylistCategory
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
private fun MainContentImpl(
    titleState: MutableState<String>,
    urlState: MutableState<String>,
    uriState: MutableState<Uri>,
    selectedState: MutableState<DataSource>,
    basicUrlState: MutableState<String>,
    usernameState: MutableState<String>,
    passwordState: MutableState<String>,
    localStorageState: MutableState<Boolean>,
    forTvState: MutableState<Boolean>,
    backingUpOrRestoring: BackingUpAndRestoringState,
    epgState: MutableState<String>,
    onClipboard: (String) -> Unit,
    onSubscribe: () -> Unit,
    backup: () -> Unit,
    restore: () -> Unit,
    modifier: Modifier = Modifier
) {
    val spacing = LocalSpacing.current
    val preferences = hiltPreferences()
    val clipboardManager = LocalClipboardManager.current
    val helper = LocalHelper.current

    val tv = tv()
    val remoteControl = preferences.remoteControl

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(spacing.small),
        contentPadding = PaddingValues(spacing.medium),
        modifier = modifier
    ) {
        item {
            DataSourceSelection(
                selectedState = selectedState,
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
            when (selectedState.value) {
                DataSource.M3U -> {
                    M3UInputContent(
                        titleState = titleState,
                        urlState = urlState,
                        uriState = uriState,
                        localStorageState = localStorageState
                    )
                }

                DataSource.EPG -> {
                    EPGInputContent(
                        titleState = titleState,
                        epgState = epgState
                    )
                }

                DataSource.Xtream -> {
                    XtreamInputContent(
                        titleState = titleState,
                        basicUrlState = basicUrlState,
                        usernameState = usernameState,
                        passwordState = passwordState
                    )
                }

                DataSource.Emby -> {}
                DataSource.Dropbox -> {}
            }
        }

        item {
            if (selectedState.value == DataSource.M3U) {
                LocalStorageSwitch(
                    checked = localStorageState.value,
                    onChanged = { localStorageState.value = it },
                    enabled = !forTvState.value
                )
            }
            if (!tv && remoteControl) {
                RemoteControlSubscribeSwitch(
                    checked = forTvState.value,
                    onChanged = { forTvState.value = !forTvState.value },
                    enabled = !localStorageState.value
                )
            }
        }
        item {
            @SuppressLint("InlinedApi")
            val postNotificationPermission = rememberPermissionState(
                Manifest.permission.POST_NOTIFICATIONS
            )
            Button(
                text = stringResource(string.feat_setting_label_subscribe),
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
            )
            when (selectedState.value) {
                DataSource.M3U, DataSource.Xtream -> {
                    TonalButton(
                        text = stringResource(string.feat_setting_label_parse_from_clipboard),
                        enabled = !localStorageState.value,
                        onClick = {
                            onClipboard(clipboardManager.getText()?.text.orEmpty())
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                else -> {}
            }
        }

        item {
            TonalButton(
                text = stringResource(string.feat_setting_label_backup),
                enabled = !forTvState.value && backingUpOrRestoring == BackingUpAndRestoringState.NONE,
                onClick = backup,
                modifier = Modifier.fillMaxWidth()
            )
            TonalButton(
                text = stringResource(string.feat_setting_label_restore),
                enabled = !forTvState.value && backingUpOrRestoring == BackingUpAndRestoringState.NONE,
                onClick = restore,
                modifier = Modifier.fillMaxWidth()
            )
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
private fun M3UInputContent(
    titleState: MutableState<String>,
    urlState: MutableState<String>,
    uriState: MutableState<Uri>,
    localStorageState: MutableState<Boolean>,
    modifier: Modifier = Modifier
) {
    val spacing = LocalSpacing.current
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(spacing.small)
    ) {
        PlaceholderField(
            text = titleState.value,
            placeholder = stringResource(string.feat_setting_placeholder_title).uppercase(),
            onValueChange = { titleState.value = Uri.decode(it) },
            modifier = Modifier.fillMaxWidth()
        )
        Crossfade(
            targetState = localStorageState.value,
            label = "url"
        ) { localStorage ->
            if (!localStorage) {
                PlaceholderField(
                    text = urlState.value,
                    placeholder = stringResource(string.feat_setting_placeholder_url).uppercase(),
                    onValueChange = { urlState.value = Uri.decode(it) },
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                LocalStorageButton(
                    titleState = titleState,
                    uriState = uriState,
                )
            }
        }
    }
}

@Composable
private fun EPGInputContent(
    titleState: MutableState<String>,
    epgState: MutableState<String>,
    modifier: Modifier = Modifier
) {
    val spacing = LocalSpacing.current
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(spacing.small)
    ) {
        PlaceholderField(
            text = titleState.value,
            placeholder = stringResource(string.feat_setting_placeholder_epg_title).uppercase(),
            onValueChange = { titleState.value = Uri.decode(it) },
            modifier = Modifier.fillMaxWidth()
        )
        PlaceholderField(
            text = epgState.value,
            placeholder = stringResource(string.feat_setting_placeholder_epg).uppercase(),
            onValueChange = { epgState.value = Uri.decode(it) },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun XtreamInputContent(
    titleState: MutableState<String>,
    basicUrlState: MutableState<String>,
    usernameState: MutableState<String>,
    passwordState: MutableState<String>,
    modifier: Modifier = Modifier
) {
    val spacing = LocalSpacing.current

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(spacing.small)
    ) {
        PlaceholderField(
            text = titleState.value,
            placeholder = stringResource(string.feat_setting_placeholder_title).uppercase(),
            onValueChange = { titleState.value = Uri.encode(it) },
            modifier = Modifier.fillMaxWidth()
        )
        PlaceholderField(
            text = basicUrlState.value,
            placeholder = stringResource(string.feat_setting_placeholder_basic_url).uppercase(),
            onValueChange = { basicUrlState.value = it },
            modifier = Modifier.fillMaxWidth()
        )
        PlaceholderField(
            text = usernameState.value,
            placeholder = stringResource(string.feat_setting_placeholder_username).uppercase(),
            onValueChange = { usernameState.value = it },
            modifier = Modifier.fillMaxWidth()
        )
        PlaceholderField(
            text = passwordState.value,
            placeholder = stringResource(string.feat_setting_placeholder_password).uppercase(),
            onValueChange = { passwordState.value = it },
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
