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
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.tv.material3.DrawerState
import androidx.tv.material3.DrawerValue
import com.m3u.material.components.Icon
import androidx.tv.material3.ModalNavigationDrawer
import androidx.tv.material3.NavigationDrawerItem
import androidx.tv.material3.NavigationDrawerItemDefaults
import androidx.tv.material3.Text
import com.m3u.data.database.model.Stream
import com.m3u.i18n.R.string
import com.m3u.material.model.LocalSpacing
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

@Immutable
internal data class DrawerItem(
    val title: String,
    val icon: ImageVector,
    val onClick: () -> Boolean
)

@Composable
internal fun PlaylistDrawer(
    items: ImmutableList<DrawerItem>,
    drawerState: DrawerState,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val spacing = LocalSpacing.current

    BackHandler(drawerState.currentValue == DrawerValue.Open) {
        drawerState.setValue(DrawerValue.Closed)
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        modifier = modifier,
        content = content,
        drawerContent = {
            AnimatedVisibility(hasFocus) {
                Column(
                    Modifier
                        .fillMaxHeight()
                        .padding(spacing.medium),
                    horizontalAlignment = Alignment.Start,
                    verticalArrangement = Arrangement.spacedBy(spacing.small)
                ) {
                    items.forEach { item ->
                        NavigationDrawerItem(
                            selected = false,
                            colors = NavigationDrawerItemDefaults.colors(
                                contentColor = Color.White
                            ),
                            onClick = {
                                val result = item.onClick()
                                if (result) drawerState.setValue(DrawerValue.Closed)
                            },
                            leadingContent = {
                                Icon(
                                    imageVector = item.icon,
                                    contentDescription = item.title
                                )
                            },
                            content = {
                                Text(item.title.uppercase())
                            },
                        )
                    }
                }
            }
        }
    )
}

internal object PlaylistDrawerDefaults {
    @Composable
    fun rememberStreamMenuItems(
        stream: Stream?,
        onFavorite: (streamId: Int, target: Boolean) -> Unit,
        hide: (streamId: Int) -> Unit,
        createShortcut: (streamId: Int) -> Unit,
        savePicture: (streamId: Int) -> Unit,
    ): ImmutableList<DrawerItem> {
        val favouriteTitle = stringResource(
            if (stream?.favourite == true) string.feat_playlist_dialog_favourite_cancel_title
            else string.feat_playlist_dialog_favourite_title
        )
        val hideTitle = stringResource(string.feat_playlist_dialog_hide_title)
        val createShortcutTitle = stringResource(string.feat_playlist_dialog_create_shortcut_title)
        val savePictureTitle = stringResource(string.feat_playlist_dialog_save_picture_title)
        return remember(stream, favouriteTitle, hideTitle, createShortcutTitle, savePictureTitle) {
            persistentListOf(
                DrawerItem(
                    title = favouriteTitle,
                    icon = Icons.Rounded.Favorite,
                    onClick = {
                        stream?.let { stream -> onFavorite(stream.id, !stream.favourite) }
                        false
                    }
                ),
                DrawerItem(
                    title = hideTitle,
                    icon = Icons.Rounded.Delete,
                    onClick = {
                        stream?.let { hide(it.id) }
                        true
                    }
                ),
                DrawerItem(
                    title = createShortcutTitle,
                    icon = Icons.AutoMirrored.Rounded.Shortcut,
                    onClick = {
                        stream?.let { createShortcut(it.id) }
                        true
                    }
                ),
                DrawerItem(
                    title = savePictureTitle,
                    icon = Icons.Rounded.Image,
                    onClick = {
                        stream?.let { savePicture(it.id) }
                        true
                    }
                )
            )
        }
    }
}