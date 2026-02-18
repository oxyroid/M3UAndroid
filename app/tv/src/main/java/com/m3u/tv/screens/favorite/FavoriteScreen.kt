package com.m3u.tv.screens.favorite

import android.view.KeyEvent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
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
import com.m3u.core.wrapper.Resource
import com.m3u.data.database.model.Channel
import com.m3u.i18n.R
import com.m3u.tv.StandardDialog
import com.m3u.tv.screens.dashboard.rememberChildPadding
import com.m3u.tv.screens.playlist.favouriteChannelGallery
import com.m3u.tv.screens.playlist.playlistItemWidthForSize
import com.m3u.tv.screens.profile.AccountsSectionDialogButton
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
    val lazyListState = rememberLazyListState()
    var channelForRemoveMenu by remember { mutableStateOf<Channel?>(null) }
    val consumeNextCenterKeyUp = remember { mutableStateOf(false) }

    val shouldShowTopBar by remember {
        derivedStateOf {
            lazyListState.firstVisibleItemIndex == 0 &&
                lazyListState.firstVisibleItemScrollOffset == 0
        }
    }

    LaunchedEffect(shouldShowTopBar) {
        onScroll(shouldShowTopBar)
    }
    LaunchedEffect(isTopBarVisible) {
        if (isTopBarVisible) {
            lazyListState.animateScrollToItem(0)
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
            LazyColumn(
                state = lazyListState,
                contentPadding = PaddingValues(
                    top = childPadding.top,
                    bottom = 104.dp
                ),
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
                favouriteChannelGallery(
                    channels = channels.data,
                    onChannelClick = onChannelClick,
                    onChannelLongClick = { channel ->
                        channelForRemoveMenu = channel
                        consumeNextCenterKeyUp.value = true
                    },
                    startPadding = childPadding.start,
                    endPadding = childPadding.end,
                    itemWidth = itemWidth
                )
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
