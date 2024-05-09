package com.m3u.features.playlist.internal

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Shortcut
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.InternalComposeApi
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.paging.compose.LazyPagingItems
import androidx.tv.foundation.lazy.list.TvLazyColumn
import androidx.tv.material3.DenseListItem
import androidx.tv.material3.ImmersiveList
import androidx.tv.material3.ListItemDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.m3u.core.architecture.preferences.hiltPreferences
import com.m3u.data.database.model.Stream
import com.m3u.features.playlist.Category
import com.m3u.features.playlist.components.ImmersiveBackground
import com.m3u.features.playlist.components.TvStreamGallery
import com.m3u.i18n.R
import com.m3u.material.components.Icon
import com.m3u.material.components.television.dialogFocusable
import com.m3u.material.ktx.Edge
import com.m3u.material.ktx.blurEdge
import com.m3u.material.ktx.thenIf
import com.m3u.material.model.LocalHazeState
import com.m3u.ui.Sort
import com.m3u.ui.TvSortFullScreenDialog
import dev.chrisbanes.haze.HazeDefaults
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.haze
import dev.chrisbanes.haze.hazeChild

@Composable
@InternalComposeApi
internal fun TvPlaylistScreenImpl(
    title: String,
    categories: List<Category>,
    streamPaged: LazyPagingItems<Stream>,
    query: String,
    onQuery: (String) -> Unit,
    sorts: List<Sort>,
    sort: Sort,
    onSort: (Sort) -> Unit,
    favorite: (streamId: Int) -> Unit,
    hide: (streamId: Int) -> Unit,
    savePicture: (streamId: Int) -> Unit,
    createTvRecommend: (streamId: Int) -> Unit,
    onStream: (Stream) -> Unit,
    onRefresh: () -> Unit,
    isVodOrSeriesPlaylist: Boolean,
    modifier: Modifier = Modifier
) {
    val preferences = hiltPreferences()

    val paging = preferences.paging
    val multiCategories = categories.size > 1
    val noPictureMode = preferences.noPictureMode
    val darkMode = if (preferences.followSystemTheme) isSystemInDarkTheme()
    else preferences.darkMode
    val useGridLayout = sort != Sort.UNSPECIFIED || paging

    val maxBrowserHeight by animateDpAsState(
        targetValue = when {
            useGridLayout || isVodOrSeriesPlaylist -> 360.dp
            noPictureMode -> 320.dp
            multiCategories -> 256.dp
            else -> 180.dp
        },
        label = "max-browser-height"
    )

    var isSortSheetVisible by rememberSaveable { mutableStateOf(false) }

    var press: Stream? by remember { mutableStateOf(null) }
    var focus: Stream? by remember { mutableStateOf(null) }

    val content = @Composable {
        ImmersiveList(
            modifier = modifier.fillMaxWidth(),
            background = { _, _ ->
                ImmersiveBackground(
                    title = title,
                    stream = focus,
                    maxBrowserHeight = maxBrowserHeight,
                    onRefresh = onRefresh,
                    openSearchDrawer = {},
                    openSortDrawer = { isSortSheetVisible = true },
                    modifier = Modifier.haze(
                        LocalHazeState.current,
                        HazeDefaults.style(MaterialTheme.colorScheme.background)
                    )
                )
            },
            list = {
                TvStreamGallery(
                    categories = categories,
                    streamPaged = streamPaged,
                    maxBrowserHeight = maxBrowserHeight,
                    useGridLayout = useGridLayout,
                    isVodOrSeriesPlaylist = isVodOrSeriesPlaylist,
                    onClick = onStream,
                    onLongClick = { stream -> press = stream },
                    onFocus = { stream -> focus = stream },
                    modifier = Modifier.thenIf(darkMode) {
                        Modifier
                            .hazeChild(
                                LocalHazeState.current,
                                style = HazeStyle(blurRadius = 4.dp)
                            )
                            .blurEdge(
                                color = MaterialTheme.colorScheme.background,
                                edge = Edge.Top
                            )
                    }
                )
            }
        )
    }

    Box {
        content()
        MenuFullScreenDialog(
            stream = press,
            favorite = favorite,
            hide = hide,
            savePicture = savePicture,
            createShortcutOrTvRecommend = createTvRecommend,
            onDismissRequest = { press = null }
        )
        TvSortFullScreenDialog(
            visible = isSortSheetVisible,
            sort = sort,
            sorts = sorts,
            onChanged = { onSort(it) },
            onDismissRequest = { isSortSheetVisible = false }
        )
    }
}

@Composable
private fun MenuFullScreenDialog(
    stream: Stream?,
    favorite: (streamId: Int) -> Unit,
    hide: (streamId: Int) -> Unit,
    savePicture: (streamId: Int) -> Unit,
    createShortcutOrTvRecommend: (streamId: Int) -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier
) {
    val favouriteTitle = stringResource(
        if (stream?.favourite == true) R.string.feat_playlist_dialog_favourite_cancel_title
        else R.string.feat_playlist_dialog_favourite_title
    ).uppercase()
    val hideTitle = stringResource(R.string.feat_playlist_dialog_hide_title).uppercase()
    val createShortcutTitle =
        stringResource(R.string.feat_playlist_dialog_create_shortcut_title).uppercase()
    val savePictureTitle =
        stringResource(R.string.feat_playlist_dialog_save_picture_title).uppercase()
    Box(
        Modifier
            .fillMaxSize()
            .then(modifier)
    ) {
        AnimatedVisibility(
            visible = stream != null,
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(0.4f)
                .align(Alignment.CenterEnd)
        ) {
            TvLazyColumn(
                Modifier
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(12.dp)
                    .selectableGroup()
                    .dialogFocusable(),
                horizontalAlignment = Alignment.Start,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    DenseListItem(
                        selected = false,
                        onClick = {}
                    ) {
                        Text(
                            text = stream?.title.orEmpty(),
                            maxLines = 1,
                            style = MaterialTheme.typography.titleLarge
                        )
                    }
                }
                item {
                    DenseListItem(
                        selected = false,
                        onClick = {
                            stream?.let { stream ->
                                favorite(stream.id)
                                onDismissRequest()
                            }
                        },
                        leadingContent = {
                            Icon(
                                imageVector = Icons.Rounded.Favorite,
                                contentDescription = null
                            )
                        },
                        scale = ListItemDefaults.scale(0.95f, 1f)
                    ) {
                        Text(
                            text = favouriteTitle
                        )
                    }
                }
                item {
                    DenseListItem(
                        selected = false,
                        onClick = {
                            stream?.let {
                                hide(it.id)
                                onDismissRequest()
                            }
                        },
                        leadingContent = {
                            Icon(
                                imageVector = Icons.Rounded.Delete,
                                contentDescription = null
                            )
                        },
                        scale = ListItemDefaults.scale(0.95f, 1f)
                    ) {
                        Text(
                            text = hideTitle
                        )
                    }
                }
                item {
                    DenseListItem(
                        selected = false,
                        onClick = {
                            stream?.let {
                                createShortcutOrTvRecommend(it.id)
                                onDismissRequest()
                            }
                        },
                        leadingContent = {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.Shortcut,
                                contentDescription = null
                            )
                        },
                        scale = ListItemDefaults.scale(0.95f, 1f)
                    ) {
                        Text(
                            text = createShortcutTitle
                        )
                    }
                }
                item {
                    DenseListItem(
                        selected = false,
                        onClick = {
                            stream?.let {
                                savePicture(it.id)
                                onDismissRequest()
                            }
                        },
                        leadingContent = {
                            Icon(
                                imageVector = Icons.Rounded.Image,
                                contentDescription = null
                            )
                        },
                        scale = ListItemDefaults.scale(0.95f, 1f)
                    ) {
                        Text(
                            text = savePictureTitle
                        )
                    }
                }
            }
            BackHandler {
                onDismissRequest()
            }
        }
    }
}

