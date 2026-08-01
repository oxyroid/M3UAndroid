package com.m3u.smartphone.ui.material.components

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.AddToHomeScreen
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.m3u.data.database.model.Channel
import com.m3u.data.database.model.Playlist
import com.m3u.i18n.R.string
import com.m3u.smartphone.ui.material.ktx.rememberUiBidiFormatter
import com.m3u.smartphone.ui.material.model.LocalSpacing

@Immutable
sealed class MediaSheetValue {
    data class ForyouScreen(
        val playlist: Playlist? = null
    ) : MediaSheetValue()

    data class PlaylistScreen(
        val channel: Channel? = null
    ) : MediaSheetValue()

    data class FavoriteScreen(
        val channel: Channel? = null
    ) : MediaSheetValue()
}


@Composable
fun MediaSheet(
    value: MediaSheetValue,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    onUnsubscribePlaylist: (Playlist) -> Unit = { noImpl() },
    onPlaylistConfiguration: (Playlist) -> Unit = { noImpl() },
    onFavoriteChannel: (Channel) -> Unit = { noImpl() },
    onHideChannel: (Channel) -> Unit = { noImpl() },
    onSaveChannelCover: (Channel) -> Unit = { noImpl() },
    onCreateShortcut: (Channel) -> Unit = { noImpl() }
) {
    val spacing = LocalSpacing.current
    val clipboardManager = LocalClipboardManager.current

    val sheetState = rememberModalBottomSheetState()
    val visible = when (value) {
        is MediaSheetValue.ForyouScreen -> value.playlist != null
        is MediaSheetValue.PlaylistScreen -> value.channel != null
        is MediaSheetValue.FavoriteScreen -> value.channel != null
    }
    BottomSheet(
        sheetState = sheetState,
        visible = visible,
        header = {
            when (value) {
                is MediaSheetValue.ForyouScreen -> ForyouScreenMediaSheetHeaderImpl(
                    playlist = value.playlist,
                    onPlaylistConfiguration = onPlaylistConfiguration
                )

                is MediaSheetValue.PlaylistScreen -> PlaylistScreenMediaSheetHeaderImpl(
                    channel = value.channel
                )

                is MediaSheetValue.FavoriteScreen -> FavoriteScreenMediaSheetHeaderImpl(
                    channel = value.channel
                )
            }
        },
        body = {
            val actions = when (value) {
                is MediaSheetValue.ForyouScreen -> value.playlist?.let { playlist ->
                    listOf(
                        MediaSheetAction(
                            stringRes = string.feat_foryou_copy_playlist_url,
                            icon = Icons.Rounded.ContentCopy,
                            onClick = {
                                clipboardManager.setText(AnnotatedString(playlist.url))
                            },
                        ),
                        MediaSheetAction(
                            stringRes = string.feat_foryou_unsubscribe_playlist,
                            icon = Icons.Rounded.DeleteOutline,
                            destructive = true,
                            onClick = { onUnsubscribePlaylist(playlist) },
                        ),
                    )
                }.orEmpty()

                is MediaSheetValue.PlaylistScreen -> value.channel?.let { channel ->
                    buildList {
                        add(channel.favoriteAction(onFavoriteChannel))
                        add(
                            MediaSheetAction(
                                stringRes = string.feat_playlist_dialog_hide_title,
                                icon = Icons.Rounded.VisibilityOff,
                                onClick = { onHideChannel(channel) },
                            )
                        )
                        if (channel.playable && !channel.browsable) {
                            add(channel.shortcutAction(onCreateShortcut))
                        }
                        add(
                            MediaSheetAction(
                                stringRes = string.feat_playlist_dialog_save_picture_title,
                                icon = Icons.Rounded.Download,
                                onClick = { onSaveChannelCover(channel) },
                            )
                        )
                    }
                }.orEmpty()

                is MediaSheetValue.FavoriteScreen -> value.channel?.let { channel ->
                    buildList {
                        add(channel.favoriteAction(onFavoriteChannel))
                        if (channel.playable && !channel.browsable) {
                            add(channel.shortcutAction(onCreateShortcut))
                        }
                    }
                }.orEmpty()
            }
            if (actions.isNotEmpty()) {
                OutlinedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(spacing.medium),
                ) {
                    actions.forEachIndexed { index, action ->
                        MediaSheetItem(
                            action = action,
                            onClick = {
                                action.onClick()
                                onDismissRequest()
                            },
                        )
                        if (index != actions.lastIndex) {
                            HorizontalDivider(
                                modifier = Modifier.padding(start = 56.dp),
                            )
                        }
                    }
                }
            }
        },
        onDismissRequest = onDismissRequest,
        modifier = modifier
    )
}

@Composable
private fun RowScope.ForyouScreenMediaSheetHeaderImpl(
    playlist: Playlist?,
    onPlaylistConfiguration: (Playlist) -> Unit,
) {
    val bidiFormatter = rememberUiBidiFormatter()
    playlist?.let {
        Column(
            modifier = Modifier.weight(1f),
        ) {
            Text(
                text = bidiFormatter.natural(it.title),
                style = MaterialTheme.typography.titleLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            it.userAgent?.ifEmpty { null }?.let { ua ->
                Text(
                    text = bidiFormatter.ltr(ua),
                    style = MaterialTheme.typography.bodyMedium,
                    color = LocalContentColor.current.copy(0.60f),
                    maxLines = 2,
                    fontFamily = FontFamilies.LexendExa,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        IconButton(
            onClick = { onPlaylistConfiguration(playlist) },
        ) {
            Icon(
                imageVector = Icons.Rounded.Edit,
                contentDescription = stringResource(string.feat_foryou_edit_playlist),
            )
        }
    }
}

@Composable
private fun RowScope.PlaylistScreenMediaSheetHeaderImpl(
    channel: Channel?
) {
    val bidiFormatter = rememberUiBidiFormatter()
    channel?.let {
        Text(
            text = bidiFormatter.natural(it.title),
            style = MaterialTheme.typography.titleLarge,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun RowScope.FavoriteScreenMediaSheetHeaderImpl(
    channel: Channel?
) {
    val bidiFormatter = rememberUiBidiFormatter()
    channel?.let {
        Text(
            text = bidiFormatter.natural(it.title),
            style = MaterialTheme.typography.titleLarge,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

private data class MediaSheetAction(
    @StringRes val stringRes: Int,
    val icon: ImageVector,
    val destructive: Boolean = false,
    val onClick: () -> Unit,
)

private fun Channel.favoriteAction(
    onFavoriteChannel: (Channel) -> Unit,
): MediaSheetAction = MediaSheetAction(
    stringRes = if (favourite) {
        string.feat_playlist_dialog_favourite_cancel_title
    } else {
        string.feat_playlist_dialog_favourite_title
    },
    icon = if (favourite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
    onClick = { onFavoriteChannel(this) },
)

private fun Channel.shortcutAction(
    onCreateShortcut: (Channel) -> Unit,
): MediaSheetAction = MediaSheetAction(
    stringRes = string.feat_playlist_dialog_create_shortcut_title,
    icon = Icons.AutoMirrored.Rounded.AddToHomeScreen,
    onClick = { onCreateShortcut(this) },
)

@Composable
private fun MediaSheetItem(
    action: MediaSheetAction,
    onClick: () -> Unit,
) {
    val contentColor = if (action.destructive) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    ListItem(
        headlineContent = {
            Text(
                text = stringResource(action.stringRes),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
            )
        },
        leadingContent = {
            Icon(
                imageVector = action.icon,
                contentDescription = null,
            )
        },
        colors = ListItemDefaults.colors(
            containerColor = Color.Transparent,
            headlineColor = contentColor,
            leadingIconColor = contentColor,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clickable(
                role = Role.Button,
                onClick = onClick,
            ),
    )
}

private fun noImpl(): Nothing =
    throw NotImplementedError("A Media Sheet operation is not implemented")
