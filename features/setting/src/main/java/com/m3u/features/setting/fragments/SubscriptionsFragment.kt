package com.m3u.features.setting.fragments

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import com.google.accompanist.permissions.rememberPermissionState
import com.m3u.core.architecture.pref.LocalPref
import com.m3u.data.database.model.DataSource
import com.m3u.data.database.model.Playlist
import com.m3u.data.database.model.Stream
import com.m3u.features.setting.BackingUpAndRestoringState
import com.m3u.features.setting.components.DataSourceSelection
import com.m3u.features.setting.components.HiddenPlaylistGroupItem
import com.m3u.features.setting.components.HiddenStreamItem
import com.m3u.features.setting.components.LocalStorageButton
import com.m3u.features.setting.components.LocalStorageSwitch
import com.m3u.features.setting.components.RemoteControlSubscribeSwitch
import com.m3u.i18n.R.string
import com.m3u.material.components.Button
import com.m3u.material.components.HorizontalPagerIndicator
import com.m3u.material.components.Icon
import com.m3u.material.components.PlaceholderField
import com.m3u.material.components.TonalButton
import com.m3u.material.ktx.checkPermissionOrRationale
import com.m3u.material.ktx.isTelevision
import com.m3u.material.ktx.textHorizontalLabel
import com.m3u.material.model.LocalSpacing
import com.m3u.ui.helper.LocalHelper
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

private enum class SubscriptionsFragmentPage {
    MAIN, HIDDEN_STREAMS, HIDDEN_PLAYLIST_CATEGORIES
}

@Composable
internal fun SubscriptionsFragment(
    contentPadding: PaddingValues,
    title: String,
    url: String,
    uri: Uri,
    selected: DataSource,
    onSelected: (DataSource) -> Unit,
    basicUrl: String,
    onBasicUrl: (String) -> Unit,
    username: String,
    onUsername: (String) -> Unit,
    password: String,
    onPassword: (String) -> Unit,
    localStorage: Boolean,
    subscribeForTv: Boolean,
    backingUpOrRestoring: BackingUpAndRestoringState,
    hiddenStreams: ImmutableList<Stream>,
    hiddenCategoriesWithPlaylists: ImmutableList<Pair<Playlist, String>>,
    onUnhideStream: (Int) -> Unit,
    onUnhidePlaylistCategory: (playlistUrl: String, category: String) -> Unit,
    onTitle: (String) -> Unit,
    onUrl: (String) -> Unit,
    onClipboard: (String) -> Unit,
    onSubscribe: () -> Unit,
    onLocalStorage: (Boolean) -> Unit,
    onSubscribeForTv: () -> Unit,
    openDocument: (Uri) -> Unit,
    backup: () -> Unit,
    restore: () -> Unit,
    modifier: Modifier = Modifier
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
                        title = title,
                        url = url,
                        uri = uri,
                        selected = selected,
                        onSelected = onSelected,
                        basicUrl = basicUrl,
                        onBasicUrl = onBasicUrl,
                        username = username,
                        onUsername = onUsername,
                        password = password,
                        onPassword = onPassword,
                        localStorage = localStorage,
                        subscribeForTv = subscribeForTv,
                        backingUpOrRestoring = backingUpOrRestoring,
                        onTitle = onTitle,
                        onUrl = onUrl,
                        onClipboard = onClipboard,
                        onSubscribe = onSubscribe,
                        onLocalStorage = onLocalStorage,
                        onSubscribeForTv = onSubscribeForTv,
                        openDocument = openDocument,
                        backup = backup,
                        restore = restore
                    )
                }

                SubscriptionsFragmentPage.HIDDEN_STREAMS -> {
                    HiddenStreamContentImpl(
                        hiddenStreams = hiddenStreams,
                        onUnhideStream = onUnhideStream
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
    title: String,
    url: String,
    uri: Uri,
    selected: DataSource,
    onSelected: (DataSource) -> Unit,
    basicUrl: String,
    onBasicUrl: (String) -> Unit,
    username: String,
    onUsername: (String) -> Unit,
    password: String,
    onPassword: (String) -> Unit,
    localStorage: Boolean,
    subscribeForTv: Boolean,
    backingUpOrRestoring: BackingUpAndRestoringState,
    onTitle: (String) -> Unit,
    onUrl: (String) -> Unit,
    onClipboard: (String) -> Unit,
    onSubscribe: () -> Unit,
    onLocalStorage: (Boolean) -> Unit,
    onSubscribeForTv: () -> Unit,
    openDocument: (Uri) -> Unit,
    backup: () -> Unit,
    restore: () -> Unit,
    modifier: Modifier = Modifier
) {
    val spacing = LocalSpacing.current
    val pref = LocalPref.current
    val clipboardManager = LocalClipboardManager.current
    val helper = LocalHelper.current

    val tv = isTelevision()
    val remoteControl = pref.remoteControl
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(spacing.small),
        contentPadding = PaddingValues(spacing.medium),
        modifier = modifier
    ) {
        item {
            DataSourceSelection(
                selected = selected,
                supported = persistentListOf(
                    DataSource.M3U,
                    DataSource.Xtream,
                    DataSource.Emby,
                    DataSource.Dropbox,
                    DataSource.Aliyun
                ),
                onSelected = onSelected
            )
        }

        item {
            when (selected) {
                DataSource.M3U -> {
                    M3UInputContent(
                        title = title,
                        onTitle = onTitle,
                        url = url,
                        onUrl = onUrl,
                        uri = uri,
                        openDocument = openDocument,
                        localStorage = localStorage
                    )
                }

                DataSource.Xtream -> {
                    XtreamInputContent(
                        title = title,
                        onTitle = onTitle,
                        basicUrl = basicUrl,
                        onBasicUrl = onBasicUrl,
                        username = username,
                        onUsername = onUsername,
                        password = password,
                        onPassword = onPassword
                    )
                }

                DataSource.Emby -> {}
                DataSource.Dropbox -> {}
                DataSource.Aliyun -> {}
            }
        }

        item {
            if (selected == DataSource.M3U) {
                LocalStorageSwitch(
                    checked = localStorage,
                    onChanged = onLocalStorage,
                    enabled = !subscribeForTv
                )
            }
            if (!tv && remoteControl) {
                RemoteControlSubscribeSwitch(
                    checked = subscribeForTv,
                    onChanged = onSubscribeForTv,
                    enabled = !localStorage
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
            TonalButton(
                text = stringResource(string.feat_setting_label_parse_from_clipboard),
                enabled = !localStorage,
                onClick = {
                    onClipboard(clipboardManager.getText()?.text.orEmpty())
                },
                modifier = Modifier.fillMaxWidth()
            )
        }

        item {
            TonalButton(
                text = stringResource(string.feat_setting_label_backup),
                enabled = !subscribeForTv && backingUpOrRestoring == BackingUpAndRestoringState.NONE,
                onClick = backup,
                modifier = Modifier.fillMaxWidth()
            )
            TonalButton(
                text = stringResource(string.feat_setting_label_restore),
                enabled = !subscribeForTv && backingUpOrRestoring == BackingUpAndRestoringState.NONE,
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
fun HiddenStreamContentImpl(
    hiddenStreams: ImmutableList<Stream>,
    onUnhideStream: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth()
    ) {
        Text(
            text = stringResource(string.feat_setting_label_hidden_streams),
            color = MaterialTheme.colorScheme.onPrimary,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.textHorizontalLabel()
        )
        hiddenStreams.forEach { stream ->
            HiddenStreamItem(
                stream = stream,
                onHidden = { onUnhideStream(stream.id) }
            )
        }
    }
}

@Composable
fun HiddenPlaylistCategoriesContentImpl(
    hiddenCategoriesWithPlaylists: ImmutableList<Pair<Playlist, String>>,
    onUnhidePlaylistCategory: (playlistUrl: String, category: String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier.fillMaxWidth()) {
        Text(
            text = stringResource(string.feat_setting_label_hidden_playlist_groups),
            color = MaterialTheme.colorScheme.onPrimary,
            style = MaterialTheme.typography.labelMedium,
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
    title: String,
    onTitle: (String) -> Unit,
    url: String,
    onUrl: (String) -> Unit,
    uri: Uri,
    openDocument: (Uri) -> Unit,
    localStorage: Boolean,
    modifier: Modifier = Modifier
) {
    val spacing = LocalSpacing.current
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(spacing.small)
    ) {
        PlaceholderField(
            text = title,
            placeholder = stringResource(string.feat_setting_placeholder_title).uppercase(),
            onValueChange = onTitle,
            modifier = Modifier.fillMaxWidth()
        )
        Crossfade(
            targetState = localStorage,
            label = "url"
        ) { localStorage ->
            if (!localStorage) {
                PlaceholderField(
                    text = url,
                    placeholder = stringResource(string.feat_setting_placeholder_url).uppercase(),
                    onValueChange = onUrl,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                LocalStorageButton(
                    uri = uri,
                    onTitle = onTitle,
                    openDocument = openDocument,
                )
            }
        }
    }
}

@Composable
private fun XtreamInputContent(
    title: String,
    onTitle: (String) -> Unit,
    basicUrl: String,
    onBasicUrl: (String) -> Unit,
    username: String,
    onUsername: (String) -> Unit,
    password: String,
    onPassword: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val spacing = LocalSpacing.current
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(spacing.small)
    ) {
        PlaceholderField(
            text = title,
            placeholder = stringResource(string.feat_setting_placeholder_title).uppercase(),
            onValueChange = onTitle,
            modifier = Modifier.fillMaxWidth()
        )
        PlaceholderField(
            text = basicUrl,
            placeholder = stringResource(string.feat_setting_placeholder_basic_url).uppercase(),
            onValueChange = onBasicUrl,
            modifier = Modifier.fillMaxWidth()
        )
        PlaceholderField(
            text = username,
            placeholder = stringResource(string.feat_setting_placeholder_username).uppercase(),
            onValueChange = onUsername,
            modifier = Modifier.fillMaxWidth()
        )
        PlaceholderField(
            text = password,
            placeholder = stringResource(string.feat_setting_placeholder_password).uppercase(),
            onValueChange = onPassword,
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
