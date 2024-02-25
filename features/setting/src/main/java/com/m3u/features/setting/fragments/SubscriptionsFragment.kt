package com.m3u.features.setting.fragments

import android.net.Uri
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import com.m3u.core.architecture.pref.LocalPref
import com.m3u.data.database.model.DataSource
import com.m3u.data.database.model.Stream
import com.m3u.features.setting.BackingUpAndRestoringState
import com.m3u.features.setting.components.DataSourceSelection
import com.m3u.features.setting.components.LocalStorageButton
import com.m3u.features.setting.components.LocalStorageSwitch
import com.m3u.features.setting.components.RemoteControlSubscribeSwitch
import com.m3u.features.setting.components.hiddenStreamstreamItem
import com.m3u.i18n.R.string
import com.m3u.material.components.Button
import com.m3u.material.components.PlaceholderField
import com.m3u.material.components.TonalButton
import com.m3u.material.ktx.isTelevision
import com.m3u.material.ktx.plus
import com.m3u.material.model.LocalSpacing
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

@Composable
internal fun SubscriptionsFragment(
    contentPadding: PaddingValues,
    title: String,
    url: String,
    uri: Uri,
    selected: DataSource,
    onSelected: (DataSource) -> Unit,
    address: String,
    onAddress: (String) -> Unit,
    username: String,
    onUsername: (String) -> Unit,
    password: String,
    onPassword: (String) -> Unit,
    localStorage: Boolean,
    subscribeForTv: Boolean,
    backingUpOrRestoring: BackingUpAndRestoringState,
    hiddenStreams: ImmutableList<Stream>,
    onHidden: (Int) -> Unit,
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
    val clipboardManager = LocalClipboardManager.current
    val pref = LocalPref.current

    val tv = isTelevision()
    val remoteControl = pref.remoteControl

    LazyColumn(
        contentPadding = PaddingValues(spacing.medium) + contentPadding,
        verticalArrangement = Arrangement.spacedBy(spacing.small),
        modifier = modifier
    ) {
        if (hiddenStreams.isNotEmpty()) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(string.feat_setting_label_hidden_streams),
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.medium)
                            .background(MaterialTheme.colorScheme.primary)
                            .padding(
                                vertical = spacing.extraSmall,
                                horizontal = spacing.medium
                            )
                    )
                    hiddenStreams.forEach { stream ->
                        hiddenStreamstreamItem(
                            stream = stream,
                            onHidden = { onHidden(stream.id) }
                        )
                    }
                }
            }
        }

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
                        onSubscribe = onSubscribe,
                        localStorage = localStorage
                    )
                }

                DataSource.Xtream -> {
                    XtreamInputContent(
                        title = title,
                        onTitle = onTitle,
                        address = address,
                        onAddress = onAddress,
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
            Column {
                Button(
                    text = stringResource(string.feat_setting_label_subscribe),
                    onClick = onSubscribe,
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
        }
        item {
            Column {
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
        }
        item {
            Spacer(Modifier.imePadding())
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
    onSubscribe: () -> Unit,
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
                    keyboardActions = KeyboardActions(
                        onDone = {
                            onSubscribe()
                        }
                    ),
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
    address: String,
    onAddress: (String) -> Unit,
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
            text = address,
            placeholder = stringResource(string.feat_setting_placeholder_address).uppercase(),
            onValueChange = onAddress,
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
    }
}
