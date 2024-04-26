package com.m3u.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.ListItem
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.m3u.core.util.basic.title
import com.m3u.data.database.model.Playlist
import com.m3u.data.database.model.Stream
import com.m3u.i18n.R.string
import com.m3u.material.components.BottomSheet
import com.m3u.material.components.IconButton
import com.m3u.material.model.LocalSpacing
import com.m3u.ui.MediaSheetValue.FavouriteScreen
import com.m3u.ui.MediaSheetValue.ForyouScreen
import com.m3u.ui.MediaSheetValue.PlaylistScreen

@Immutable
sealed class MediaSheetValue {
    data class ForyouScreen(
        val playlist: Playlist? = null
    ) : MediaSheetValue()

    data class PlaylistScreen(
        val stream: Stream? = null
    ) : MediaSheetValue()

    data class FavouriteScreen(
        val stream: Stream? = null
    ) : MediaSheetValue()
}


@Composable
fun MediaSheet(
    value: MediaSheetValue,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    onUnsubscribePlaylist: (Playlist) -> Unit = { noImpl() },
    onPlaylistConfiguration: (Playlist) -> Unit = { noImpl() },
    onFavouriteStream: (Stream) -> Unit = { noImpl() },
    onHideStream: (Stream) -> Unit = { noImpl() },
    onSaveStreamCover: (Stream) -> Unit = { noImpl() },
    onCreateStreamShortcut: (Stream) -> Unit = { noImpl() }
) {
    val spacing = LocalSpacing.current
    val clipboardManager = LocalClipboardManager.current

    val sheetState = rememberModalBottomSheetState()
    val visible = when (value) {
        is ForyouScreen -> value.playlist != null
        is PlaylistScreen -> value.stream != null
        is FavouriteScreen -> value.stream != null
    }
    BottomSheet(
        sheetState = sheetState,
        visible = visible,
        shouldDismissOnBackPress = false,
        header = {
            when (value) {
                is ForyouScreen -> ForyouScreenMediaSheetHeaderImpl(
                    playlist = value.playlist,
                    onPlaylistConfiguration = onPlaylistConfiguration
                )

                is PlaylistScreen -> PlaylistScreenMediaSheetHeaderImpl(
                    stream = value.stream
                )

                is FavouriteScreen -> FavouriteScreenMediaSheetHeaderImpl(
                    stream = value.stream
                )
            }
        },
        body = {
            Column(
                verticalArrangement = Arrangement.spacedBy(spacing.small),
                modifier = Modifier.padding(spacing.medium)
            ) {
                when (value) {
                    is ForyouScreen -> {
                        value.playlist?.let { playlist ->
                            val playlistUrl = playlist.url
                            MediaSheetItem(
                                stringRes = string.feat_foryou_unsubscribe_playlist,
                                onClick = {
                                    onUnsubscribePlaylist(playlist)
                                    onDismissRequest()
                                }
                            )
                            MediaSheetItem(
                                stringRes = string.feat_foryou_copy_playlist_url,
                                onClick = {
                                    clipboardManager.setText(
                                        AnnotatedString(playlistUrl)
                                    )
                                    onDismissRequest()
                                }
                            )
                        }
                    }

                    is PlaylistScreen -> {
                        value.stream?.let {
                            MediaSheetItem(
                                stringRes = if (!it.favourite) string.feat_playlist_dialog_favourite_title
                                else string.feat_playlist_dialog_favourite_cancel_title,
                                onClick = {
                                    onFavouriteStream(it)
                                    onDismissRequest()
                                }
                            )
                            MediaSheetItem(
                                stringRes = string.feat_playlist_dialog_hide_title,
                                onClick = {
                                    onHideStream(it)
                                    onDismissRequest()
                                }
                            )
                            MediaSheetItem(
                                stringRes = string.feat_playlist_dialog_create_shortcut_title,
                                onClick = {
                                    onCreateStreamShortcut(it)
                                    onDismissRequest()
                                }
                            )
                            MediaSheetItem(
                                stringRes = string.feat_playlist_dialog_save_picture_title,
                                onClick = {
                                    onSaveStreamCover(it)
                                    onDismissRequest()
                                }
                            )
                        }
                    }

                    is FavouriteScreen -> {
                        value.stream?.let {
                            MediaSheetItem(
                                stringRes = if (!it.favourite) string.feat_playlist_dialog_favourite_title
                                else string.feat_playlist_dialog_favourite_cancel_title,
                                onClick = {
                                    onFavouriteStream(it)
                                    onDismissRequest()
                                }
                            )
                            MediaSheetItem(
                                stringRes = string.feat_playlist_dialog_create_shortcut_title,
                                onClick = {
                                    onCreateStreamShortcut(it)
                                    onDismissRequest()
                                }
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
    val spacing = LocalSpacing.current
    playlist?.let {
        Row {
            Column(
                verticalArrangement = Arrangement.spacedBy(spacing.extraSmall),
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = it.title,
                    style = MaterialTheme.typography.titleLarge
                )
                it.userAgent?.ifEmpty { null }?.let { ua ->
                    Text(
                        text = ua,
                        style = MaterialTheme.typography.bodyMedium,
                        color = LocalContentColor.current.copy(0.38f),
                        maxLines = 1,
                        fontFamily = FontFamilies.LexendExa,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            IconButton(
                icon = Icons.Rounded.Edit,
                contentDescription = null,
                onClick = { onPlaylistConfiguration(playlist) }
            )
        }
    }
}

@Composable
private fun RowScope.PlaylistScreenMediaSheetHeaderImpl(
    stream: Stream?
) {
    stream?.let {
        Text(
            text = it.title,
            style = MaterialTheme.typography.titleLarge
        )
    }
    Spacer(modifier = Modifier.weight(1f))
}

@Composable
private fun RowScope.FavouriteScreenMediaSheetHeaderImpl(
    stream: Stream?
) {
    stream?.let {
        Text(
            text = it.title,
            style = MaterialTheme.typography.titleLarge
        )
    }
    Spacer(modifier = Modifier.weight(1f))
}

@Composable
private fun MediaSheetItem(
    @StringRes stringRes: Int,
    onClick: () -> Unit
) {
    OutlinedCard {
        ListItem(
            headlineContent = {
                Text(
                    text = stringResource(stringRes).title(),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
            },
            modifier = Modifier.clickable { onClick() }
        )
    }
}

private fun noImpl(): Nothing =
    throw NotImplementedError("A Media Sheet operation is not implemented")