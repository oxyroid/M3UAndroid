package com.m3u.tv.screens.favorite

import android.view.KeyEvent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.m3u.business.favorite.FavouriteViewModel
import com.m3u.core.architecture.preferences.hiltPreferences
import com.m3u.core.foundation.components.CircularProgressIndicator
import com.m3u.core.util.basic.title
import com.m3u.core.wrapper.Resource
import com.m3u.data.database.model.Channel
import com.m3u.i18n.R
import com.m3u.tv.StandardDialog
import com.m3u.tv.screens.dashboard.rememberChildPadding
import com.m3u.tv.screens.playlist.ChannelGalleryItem
import com.m3u.tv.screens.playlist.playlistItemWidthForSize
import com.m3u.tv.ui.component.TextField
import androidx.tv.material3.ListItemDefaults
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

@Composable
fun FavoriteScreen(
    favoriteTabFocusRequester: FocusRequester?,
    onChannelClick: (Channel) -> Unit,
    onChannelLongClick: (Channel) -> Unit,
    onScroll: (isTopBarVisible: Boolean) -> Unit,
    isTopBarVisible: Boolean,
    viewModel: FavouriteViewModel = hiltViewModel(),
) {
    val channelsResource by viewModel.channels.collectAsStateWithLifecycle()
    val preferences = hiltPreferences()
    val childPadding = rememberChildPadding()
    val itemWidth = playlistItemWidthForSize(preferences.playlistItemSize)
    val gridState = rememberLazyGridState()
    var searchQuery by remember { mutableStateOf("") }
    var channelForRemoveMenu by remember { mutableStateOf<Channel?>(null) }
    val consumeNextCenterKeyUp = remember { mutableStateOf(false) }

    val shouldShowTopBar by remember {
        derivedStateOf {
            gridState.firstVisibleItemIndex == 0 &&
                gridState.firstVisibleItemScrollOffset == 0
        }
    }

    LaunchedEffect(shouldShowTopBar) {
        onScroll(shouldShowTopBar)
    }
    LaunchedEffect(isTopBarVisible) {
        if (isTopBarVisible) {
            gridState.animateScrollToItem(0)
        }
    }

    when (val channels = channelsResource) {
        Resource.Loading -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }

        is Resource.Success -> {
            val query = searchQuery.trim()
            val filteredChannels = remember(channels.data, query) {
                if (query.isBlank()) channels.data
                else channels.data.filter { channel ->
                    channel.title.contains(query, ignoreCase = true) ||
                        channel.category.contains(query, ignoreCase = true)
                }
            }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (favoriteTabFocusRequester != null) {
                            Modifier.focusProperties {
                                up = favoriteTabFocusRequester
                            }
                        } else {
                            Modifier
                        }
                    )
            ) {
                TextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = stringResource(R.string.feat_playlist_query_placeholder).title(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            start = childPadding.start,
                            end = childPadding.end,
                            top = childPadding.top,
                            bottom = 8.dp
                        )
                )
                LazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Adaptive(itemWidth),
                    contentPadding = PaddingValues(
                        start = childPadding.start,
                        end = childPadding.end,
                        bottom = 104.dp
                    ),
                    horizontalArrangement = Arrangement.spacedBy(32.dp),
                    verticalArrangement = Arrangement.spacedBy(32.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(
                        items = filteredChannels,
                        key = { it.id }
                    ) { channel ->
                        ChannelGalleryItem(
                            channel = channel,
                            modifier = Modifier.fillMaxWidth(),
                            onChannelClick = onChannelClick,
                            onChannelLongClick = { ch ->
                                channelForRemoveMenu = ch
                                consumeNextCenterKeyUp.value = true
                            }
                        )
                    }
                }
            }
        }

        is Resource.Failure -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.ui_error_unknown),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
    }

    RemoveFromFavoriteDialog(
        channel = channelForRemoveMenu,
        consumeNextCenterKeyUp = consumeNextCenterKeyUp,
        onDismiss = { channelForRemoveMenu = null },
        onRemove = { channel ->
            viewModel.favourite(channel.id)
            channelForRemoveMenu = null
        },
    )
}

@Composable
private fun RemoveFromFavoriteDialog(
    channel: Channel?,
    consumeNextCenterKeyUp: MutableState<Boolean>,
    onDismiss: () -> Unit,
    onRemove: (Channel) -> Unit,
) {
    if (channel == null) return
    fun isCenterOrEnter(keyCode: Int) = keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
        keyCode == KeyEvent.KEYCODE_ENTER ||
        keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER
    StandardDialog(
        showDialog = true,
        onDismissRequest = onDismiss,
        title = { Text(channel.title) },
        textContentColor = MaterialTheme.colorScheme.onSurface,
        text = {
            Box(
                modifier = Modifier.onPreviewKeyEvent { event ->
                    if (consumeNextCenterKeyUp.value &&
                        event.nativeKeyEvent.action == KeyEvent.ACTION_UP &&
                        isCenterOrEnter(event.nativeKeyEvent.keyCode)
                    ) {
                        consumeNextCenterKeyUp.value = false
                        return@onPreviewKeyEvent true
                    }
                    false
                }
            ) {
                Column {
                    androidx.tv.material3.ListItem(
                        selected = false,
                        headlineContent = {
                            Text(
                                stringResource(R.string.feat_favourite_remove_from_favourite),
                                color = LocalContentColor.current,
                            )
                        },
                        onClick = { onRemove(channel) },
                        colors = ListItemDefaults.colors(
                            contentColor = MaterialTheme.colorScheme.onSurface,
                            focusedContentColor = MaterialTheme.colorScheme.inverseOnSurface,
                            focusedContainerColor = MaterialTheme.colorScheme.inverseSurface,
                        ),
                    )
                }
            }
        },
        dismissButton = { },
        confirmButton = { },
    )
}
