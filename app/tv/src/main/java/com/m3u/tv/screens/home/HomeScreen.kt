package com.m3u.tv.screens.home

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import android.view.KeyEvent
import com.m3u.i18n.R
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.CompactCard
import androidx.tv.material3.ListItemDefaults
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.m3u.business.foryou.ForyouViewModel
import com.m3u.business.foryou.Recommend
import com.m3u.core.foundation.components.CircularProgressIndicator
import com.m3u.core.wrapper.Resource
import com.m3u.data.database.model.Channel
import com.m3u.data.database.model.Playlist
import com.m3u.data.database.model.PlaylistWithCount
import com.m3u.tv.StandardDialog
import com.m3u.tv.screens.dashboard.rememberChildPadding
import com.m3u.tv.screens.profile.AccountsSectionDialogButton
import com.m3u.tv.theme.LexendExa
import com.m3u.tv.ui.component.TextField
import com.m3u.tv.utils.longPressKeyHandler
import androidx.compose.runtime.MutableState

@Composable
fun HomeScreen(
    navigateToPlaylist: (playlistUrl: String) -> Unit,
    navigateToChannel: (channel: Channel) -> Unit,
    onScroll: (isTopBarVisible: Boolean) -> Unit,
    isTopBarVisible: Boolean,
    viewModel: ForyouViewModel = hiltViewModel(),
) {
    val playlists: Resource<List<PlaylistWithCount>> by viewModel.playlists.collectAsStateWithLifecycle()
    val specs: List<Recommend.Spec> by viewModel.specs.collectAsStateWithLifecycle()
    Box(Modifier.fillMaxSize()) {
        when (val playlists = playlists) {
            Resource.Loading -> {
                CircularProgressIndicator(
                    Modifier.align(Alignment.Center)
                )
            }
            is Resource.Success -> {
                Catalog(
                    playlists = playlists.data,
                    specs = specs,
                    viewModel = viewModel,
                    onScroll = onScroll,
                    navigateToPlaylist = navigateToPlaylist,
                    navigateToChannel = navigateToChannel,
                    isTopBarVisible = isTopBarVisible
                )
            }
            is Resource.Failure -> {
                Text(
                    text = playlists.message.orEmpty()
                )
            }
        }
    }
}

@Composable
private fun Catalog(
    playlists: List<PlaylistWithCount>,
    specs: List<Recommend.Spec>,
    viewModel: ForyouViewModel,
    onScroll: (isTopBarVisible: Boolean) -> Unit,
    navigateToPlaylist: (playlistUrl: String) -> Unit,
    navigateToChannel: (channel: Channel) -> Unit,
    modifier: Modifier = Modifier,
    isTopBarVisible: Boolean = true,
) {
    var playlistMenuPlaylist by remember { mutableStateOf<Playlist?>(null) }
    var editPlaylist by remember { mutableStateOf<Playlist?>(null) }
    var deleteConfirmPlaylist by remember { mutableStateOf<Playlist?>(null) }
    val consumeNextCenterKeyUp = remember { mutableStateOf(false) }

    val lazyListState = rememberLazyListState()
    val childPadding = rememberChildPadding()

    val shouldShowTopBar by remember {
        derivedStateOf {
            lazyListState.firstVisibleItemIndex == 0 &&
                lazyListState.firstVisibleItemScrollOffset < 300
        }
    }

    LaunchedEffect(shouldShowTopBar) {
        onScroll(shouldShowTopBar)
    }
    LaunchedEffect(isTopBarVisible) {
        if (isTopBarVisible) lazyListState.animateScrollToItem(0)
    }

    LazyColumn(
        state = lazyListState,
        contentPadding = PaddingValues(bottom = 108.dp),
        modifier = modifier
    ) {
        if (specs.isNotEmpty()) {
            item(contentType = "FeaturedChannelsCarousel") {
                FeaturedSpecsCarousel(
                    specs = specs,
                    padding = childPadding,
                    onClickSpec = { spec ->
                        when (spec) {
                            is Recommend.UnseenSpec -> {
                                navigateToChannel(spec.channel)
                            }
                            is Recommend.DiscoverSpec -> TODO()
                            is Recommend.NewRelease -> TODO()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(324.dp)
                    /*
                     Setting height for the FeaturedChannelCarousel to keep it rendered with same height,
                     regardless of the top bar's visibility
                     */
                )
            }
        }

        item(contentType = "PlaylistsRow") {
            val startPadding: Dp = rememberChildPadding().start
            val endPadding: Dp = rememberChildPadding().end
            Text(
                text = "Playlist",
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.Medium,
                    fontSize = 30.sp
                ),
                modifier = Modifier
                    .alpha(1f)
                    .padding(start = startPadding, top = 16.dp, bottom = 16.dp)
            )
            LazyRow(
                modifier = Modifier
                    .focusGroup()
                    .padding(top = 16.dp),
                contentPadding = PaddingValues(start = startPadding, end = endPadding)
            ) {
                items(playlists) { (playlist, count) ->
                    CompactCard(
                        onClick = { },
                        modifier = Modifier
                            .longPressKeyHandler(
                                onClick = { navigateToPlaylist(playlist.url) },
                                onLongClick = {
                                    playlistMenuPlaylist = playlist
                                    consumeNextCenterKeyUp.value = true
                                }
                            )
                            .width(325.dp)
                            .aspectRatio(2f),
                        title = {
                            Column(
                                modifier = Modifier.padding(16.dp)
                            ) {
                                Text(
                                    text = playlist.title,
                                    fontSize = 36.sp,
                                    lineHeight = 36.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = LexendExa
                                )
                                Box(
                                    modifier = Modifier
                                        .padding(top = 8.dp)
                                        .background(
                                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.2f),
                                            shape = RoundedCornerShape(24.dp)
                                        )
                                        .padding(horizontal = 12.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = stringResource(R.string.feat_foryou_playlist_count, count),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                }
                            }
                        },
                        colors = CardDefaults.compactCardColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        ),
                        image = {},
                    )
                }
            }
        }
    }

    PlaylistMenuDialog(
        playlist = playlistMenuPlaylist,
        consumeNextCenterKeyUp = consumeNextCenterKeyUp,
        onDismiss = { playlistMenuPlaylist = null },
        onOpen = {
            navigateToPlaylist(it.url)
            playlistMenuPlaylist = null
        },
        onEdit = {
            editPlaylist = it
            playlistMenuPlaylist = null
        },
        onDelete = {
            deleteConfirmPlaylist = it
            playlistMenuPlaylist = null
        },
    )
    EditPlaylistDialog(
        playlist = editPlaylist,
        onDismiss = { editPlaylist = null },
        onSave = { oldUrl, newUrl, title ->
            viewModel.onUpdatePlaylist(oldUrl, newUrl, title)
            editPlaylist = null
        },
    )
    DeletePlaylistConfirmDialog(
        playlist = deleteConfirmPlaylist,
        onDismiss = { deleteConfirmPlaylist = null },
        onConfirm = {
            viewModel.onUnsubscribePlaylist(it.url)
            deleteConfirmPlaylist = null
        },
    )
}

@Composable
private fun PlaylistMenuDialog(
    playlist: Playlist?,
    consumeNextCenterKeyUp: MutableState<Boolean>,
    onDismiss: () -> Unit,
    onOpen: (Playlist) -> Unit,
    onEdit: (Playlist) -> Unit,
    onDelete: (Playlist) -> Unit,
) {
    if (playlist == null) return
    fun isCenterOrEnter(keyCode: Int) = keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
        keyCode == KeyEvent.KEYCODE_ENTER ||
        keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER
    StandardDialog(
        showDialog = true,
        onDismissRequest = onDismiss,
        title = { Text(playlist.title) },
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
                                stringResource(R.string.feat_foryou_open_playlist),
                                color = LocalContentColor.current,
                            )
                        },
                        onClick = { onOpen(playlist) },
                        colors = ListItemDefaults.colors(
                            contentColor = MaterialTheme.colorScheme.onSurface,
                            focusedContentColor = MaterialTheme.colorScheme.inverseOnSurface,
                            focusedContainerColor = MaterialTheme.colorScheme.inverseSurface,
                        ),
                    )
                    androidx.tv.material3.ListItem(
                        selected = false,
                        headlineContent = {
                            Text(
                                stringResource(R.string.feat_foryou_edit_playlist),
                                color = LocalContentColor.current,
                            )
                        },
                        onClick = { onEdit(playlist) },
                        colors = ListItemDefaults.colors(
                            contentColor = MaterialTheme.colorScheme.onSurface,
                            focusedContentColor = MaterialTheme.colorScheme.inverseOnSurface,
                            focusedContainerColor = MaterialTheme.colorScheme.inverseSurface,
                        ),
                    )
                    androidx.tv.material3.ListItem(
                        selected = false,
                        headlineContent = {
                            Text(
                                stringResource(R.string.feat_foryou_delete_playlist),
                                color = LocalContentColor.current,
                            )
                        },
                        onClick = { onDelete(playlist) },
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

@Composable
private fun EditPlaylistDialog(
    playlist: Playlist?,
    onDismiss: () -> Unit,
    onSave: (oldUrl: String, newUrl: String, title: String) -> Unit,
) {
    if (playlist == null) return
    var title by remember(playlist) { mutableStateOf(playlist.title) }
    var url by remember(playlist) { mutableStateOf(playlist.url) }
    StandardDialog(
        showDialog = true,
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.feat_foryou_edit_playlist)) },
        text = {
            Column {
                Text(stringResource(com.m3u.i18n.R.string.feat_playlist_configuration_title))
                TextField(
                    value = title,
                    onValueChange = { title = it },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("URL", modifier = Modifier.padding(top = 16.dp))
                TextField(
                    value = url,
                    onValueChange = { url = it },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        dismissButton = {
            AccountsSectionDialogButton(
                text = "Cancel",
                shouldRequestFocus = false,
                onClick = onDismiss,
            )
        },
        confirmButton = {
            AccountsSectionDialogButton(
                text = "Save",
                shouldRequestFocus = true,
                onClick = { onSave(playlist.url, url, title) },
            )
        },
    )
}

@Composable
private fun DeletePlaylistConfirmDialog(
    playlist: Playlist?,
    onDismiss: () -> Unit,
    onConfirm: (Playlist) -> Unit,
) {
    if (playlist == null) return
    StandardDialog(
        showDialog = true,
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.feat_foryou_delete_playlist)) },
        text = { Text(stringResource(R.string.feat_foryou_delete_playlist_confirm)) },
        dismissButton = {
            AccountsSectionDialogButton(
                text = "Cancel",
                shouldRequestFocus = false,
                onClick = onDismiss,
            )
        },
        confirmButton = {
            AccountsSectionDialogButton(
                text = stringResource(R.string.feat_foryou_delete_playlist),
                shouldRequestFocus = true,
                onClick = { onConfirm(playlist) },
            )
        },
    )
}
