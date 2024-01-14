package com.m3u.features.playlist.impl

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Shortcut
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.foundation.lazy.list.TvLazyColumn
import androidx.tv.foundation.lazy.list.TvLazyRow
import androidx.tv.foundation.lazy.list.itemsIndexed
import androidx.tv.material3.DrawerValue
import androidx.tv.material3.Icon
import androidx.tv.material3.ImmersiveList
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.ModalNavigationDrawer
import androidx.tv.material3.NavigationDrawerItem
import androidx.tv.material3.Text
import androidx.tv.material3.rememberDrawerState
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.m3u.core.wrapper.Message
import com.m3u.features.playlist.Channel
import com.m3u.features.playlist.R
import com.m3u.features.playlist.components.TvStreamItem
import com.m3u.i18n.R.string
import com.m3u.material.components.Background
import com.m3u.material.components.IconButton
import com.m3u.material.ktx.Edge
import com.m3u.material.ktx.blurEdge
import com.m3u.material.model.LocalSpacing
import com.m3u.ui.AppSnackHost
import com.m3u.ui.LocalHelper
import com.m3u.ui.Sort
import kotlinx.collections.immutable.ImmutableList

@Composable
internal fun TvPlaylistScreenImpl(
    title: String,
    message: Message,
    channels: ImmutableList<Channel>,
    query: String,
    onQuery: (String) -> Unit,
    sorts: ImmutableList<Sort>,
    sort: Sort,
    onFavorite: (streamId: Int, target: Boolean) -> Unit,
    ban: (streamId: Int) -> Unit,
    onSavePicture: (streamId: Int) -> Unit,
    createShortcut: (streamId: Int) -> Unit,
    navigateToStream: () -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val helper = LocalHelper.current
    val spacing = LocalSpacing.current
    val multiCatalogs = channels.size > 1
    val maxBrowserHeight = if (multiCatalogs) 256.dp else 180.dp

    val drawerState = rememberDrawerState(DrawerValue.Closed)

    var pressMixed: Int? by remember { mutableStateOf(null) }
    var focusMixed: Int? by remember { mutableStateOf(null) }

    val pressStream by remember(channels) {
        derivedStateOf {
            pressMixed?.let {
                val (i, j) = TvPlaylistScreenImplDefaults.separate(it)
                channels[i].streams[j]
            }
        }
    }

    val focusStream by remember(channels) {
        derivedStateOf {
            focusMixed?.let {
                val (i, j) = TvPlaylistScreenImplDefaults.separate(it)
                channels[i].streams[j]
            }
        }
    }

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
                            pressStream?.let { stream ->
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
                                    if (pressStream?.favourite == true) string.feat_playlist_dialog_favourite_cancel_title
                                    else string.feat_playlist_dialog_favourite_title
                                ).uppercase()
                            )
                        },
                    )

                    NavigationDrawerItem(
                        selected = false,
                        onClick = {
                            pressStream?.let { ban(it.id) }
                            drawerState.setValue(DrawerValue.Closed)
                        },
                        leadingContent = {
                            Icon(
                                imageVector = Icons.Rounded.Delete,
                                contentDescription = "ban"
                            )
                        },
                        content = {
                            Text(stringResource(string.feat_playlist_dialog_mute_title).uppercase())
                        }
                    )
                    NavigationDrawerItem(
                        selected = false,
                        onClick = {
                            pressStream?.let { createShortcut(it.id) }
                            drawerState.setValue(DrawerValue.Closed)
                        },
                        leadingContent = {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.Shortcut,
                                contentDescription = "shortcut"
                            )
                        },
                        content = {
                            Text(stringResource(string.feat_playlist_dialog_create_shortcut_title).uppercase())
                        }
                    )

                    NavigationDrawerItem(
                        selected = false,
                        onClick = {
                            pressStream?.let { onSavePicture(it.id) }
                            drawerState.setValue(DrawerValue.Closed)
                        },
                        leadingContent = {
                            Icon(
                                imageVector = Icons.Rounded.Image,
                                contentDescription = "save-cover"
                            )
                        },
                        content = {
                            Text(stringResource(string.feat_playlist_dialog_save_picture_title).uppercase())
                        }
                    )
                }
            }
        }
    ) {
        ImmersiveList(
            modifier = modifier.fillMaxWidth(),
            background = { _, _ ->
                val stream = focusStream
                Background {
                    if (stream != null) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.TopEnd
                        ) {
                            val request = remember(stream.cover) {
                                ImageRequest.Builder(context)
                                    .data(stream.cover.orEmpty())
                                    .crossfade(1600)
                                    .build()
                            }
                            AsyncImage(
                                model = request,
                                contentScale = ContentScale.Crop,
                                contentDescription = stream.title,
                                modifier = Modifier
                                    .fillMaxWidth(0.78f)
                                    .aspectRatio(16 / 9f)
                            )
                            Icon(
                                painter = painterResource(R.drawable.scrim),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.background,
                                modifier = Modifier
                                    .fillMaxWidth(0.78f)
                                    .aspectRatio(16 / 9f)
                            )
                            Column(
                                Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(spacing.medium)
                                    .fillMaxWidth()
                            ) {
                                Text(
                                    text = stream.title,
                                    style = MaterialTheme.typography.headlineLarge,
                                    fontWeight = FontWeight.ExtraBold,
                                    maxLines = 1
                                )
                                Text(
                                    text = stream.url,
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = LocalContentColor.current.copy(0.68f),
                                    maxLines = 1
                                )
                                Spacer(
                                    modifier = Modifier.heightIn(min = maxBrowserHeight)
                                )
                            }
                        }
                    }
                    Column(
                        Modifier.padding(spacing.medium),
                        verticalArrangement = Arrangement.spacedBy(spacing.medium)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(spacing.small),
                        ) {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.headlineLarge,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            IconButton(
                                icon = Icons.Rounded.Search,
                                contentDescription = "search",
                                onClick = { /*TODO*/ }
                            )
                            IconButton(
                                icon = Icons.Rounded.Refresh,
                                contentDescription = "refresh",
                                onClick = onRefresh
                            )
                        }
                        AppSnackHost(
                            message = message,
                        )
                    }
                }
            },
            list = {
                SideEffect {
                    Log.e("TAG", "TvPlaylistScreenImpl: ")
                }
                TvLazyColumn(
                    verticalArrangement = Arrangement.spacedBy(spacing.medium),
                    contentPadding = PaddingValues(vertical = spacing.medium),
                    modifier = Modifier
                        .heightIn(max = maxBrowserHeight)
                        .blurEdge(
                            color = MaterialTheme.colorScheme.background,
                            edge = Edge.Top
                        )
                ) {
                    itemsIndexed(channels) { i, channel ->
                        if (multiCatalogs) {
                            Text(
                                text = channel.title,
                                style = MaterialTheme.typography.headlineMedium,
                                modifier = Modifier.padding(spacing.medium)
                            )
                        }
                        val streams = channel.streams
                        TvLazyRow(
                            horizontalArrangement = Arrangement.spacedBy(spacing.medium),
                            contentPadding = PaddingValues(horizontal = spacing.medium)
                        ) {
                            itemsIndexed(
                                items = streams,
                                key = { _, stream -> stream.id },
                                contentType = { _, stream -> stream.cover.isNullOrEmpty() }
                            ) { j, stream ->
                                val mixed = TvPlaylistScreenImplDefaults.combine(i, j)
                                TvStreamItem(
                                    stream = stream,
                                    onClick = {
                                        helper.play(stream.url)
                                        navigateToStream()
                                    },
                                    onLongClick = {
                                        pressMixed = mixed
                                        drawerState.setValue(DrawerValue.Open)
                                    },
                                    modifier = Modifier
                                        .onFocusChanged {
                                            if (it.hasFocus) {
                                                focusMixed = mixed
                                            }
                                        }
                                    //.immersiveListItem(mixed)
                                )
                            }
                        }
                    }
                }
            }
        )
    }
}

private object TvPlaylistScreenImplDefaults {
    fun combine(i: Int, j: Int): Int = i shl 16 or j
    fun separate(mixed: Int): Pair<Int, Int> {
        val i = mixed shr 16 and 0x7FFF
        val j = mixed shr 0 and 0x7FFF
        return i to j
    }
}
