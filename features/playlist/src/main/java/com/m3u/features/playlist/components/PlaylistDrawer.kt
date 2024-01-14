package com.m3u.features.playlist.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Shortcut
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.tv.material3.DrawerState
import androidx.tv.material3.DrawerValue
import androidx.tv.material3.Icon
import androidx.tv.material3.ModalNavigationDrawer
import androidx.tv.material3.NavigationDrawerItem
import androidx.tv.material3.Text
import com.m3u.data.database.model.Stream
import com.m3u.i18n.R
import com.m3u.material.model.LocalSpacing

@Composable
internal fun PlaylistDrawer(
    drawerState: DrawerState,
    stream: Stream?,
    onFavorite: (streamId: Int, target: Boolean) -> Unit,
    ban: (streamId: Int) -> Unit,
    createShortcut: (streamId: Int) -> Unit,
    savePicture: (streamId: Int) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val spacing = LocalSpacing.current

    BackHandler(drawerState.currentValue == DrawerValue.Open) {
        drawerState.setValue(DrawerValue.Closed)
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            AnimatedVisibility(hasFocus) {
                Column(
                    Modifier
                        .fillMaxHeight()
                        .padding(spacing.medium),
                    horizontalAlignment = Alignment.Start,
                    verticalArrangement = Arrangement.spacedBy(spacing.small)
                ) {
                    NavigationDrawerItem(
                        selected = false,
                        onClick = {
                            stream?.let { stream ->
                                onFavorite(stream.id, !stream.favourite)
                            }
                        },
                        leadingContent = {
                            Icon(
                                imageVector = Icons.Rounded.Favorite,
                                contentDescription = "favorite"
                            )
                        },
                        content = {
                            Text(
                                stringResource(
                                    if (stream?.favourite == true) R.string.feat_playlist_dialog_favourite_cancel_title
                                    else R.string.feat_playlist_dialog_favourite_title
                                ).uppercase()
                            )
                        },
                    )

                    NavigationDrawerItem(
                        selected = false,
                        onClick = {
                            stream?.let { ban(it.id) }
                            drawerState.setValue(DrawerValue.Closed)
                        },
                        leadingContent = {
                            Icon(
                                imageVector = Icons.Rounded.Delete,
                                contentDescription = "ban"
                            )
                        },
                        content = {
                            Text(stringResource(R.string.feat_playlist_dialog_mute_title).uppercase())
                        }
                    )
                    NavigationDrawerItem(
                        selected = false,
                        onClick = {
                            stream?.let { createShortcut(it.id) }
                            drawerState.setValue(DrawerValue.Closed)
                        },
                        leadingContent = {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.Shortcut,
                                contentDescription = "shortcut"
                            )
                        },
                        content = {
                            Text(stringResource(R.string.feat_playlist_dialog_create_shortcut_title).uppercase())
                        }
                    )

                    NavigationDrawerItem(
                        selected = false,
                        onClick = {
                            stream?.let { savePicture(it.id) }
                            drawerState.setValue(DrawerValue.Closed)
                        },
                        leadingContent = {
                            Icon(
                                imageVector = Icons.Rounded.Image,
                                contentDescription = "save-cover"
                            )
                        },
                        content = {
                            Text(stringResource(R.string.feat_playlist_dialog_save_picture_title).uppercase())
                        }
                    )
                }
            }
        },
        modifier = modifier,
        content = content
    )
}