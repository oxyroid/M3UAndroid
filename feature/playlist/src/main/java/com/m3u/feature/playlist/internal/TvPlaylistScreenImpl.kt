package com.m3u.feature.playlist.internal

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.tv.material3.DenseListItem
import com.m3u.core.architecture.preferences.hiltPreferences
import com.m3u.data.database.model.Programme
import com.m3u.data.database.model.Channel
import com.m3u.feature.playlist.PlaylistViewModel
import com.m3u.feature.playlist.components.ImmersiveBackground
import com.m3u.feature.playlist.components.TvChannelGallery
import com.m3u.i18n.R
import com.m3u.material.components.Background
import com.m3u.material.components.Icon
import com.m3u.material.components.tv.dialogFocusable
import com.m3u.material.ktx.Edge
import com.m3u.material.ktx.blurEdge
import com.m3u.material.model.LocalHazeState
import com.m3u.ui.Sort
import com.m3u.ui.TvSortFullScreenDialog
import dev.chrisbanes.haze.HazeDefaults
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.haze
import dev.chrisbanes.haze.hazeChild
import androidx.tv.material3.ListItemDefaults as TvListItemDefaults
import androidx.tv.material3.MaterialTheme as TvMaterialTheme
import androidx.tv.material3.Text as TvText

@Composable
@InternalComposeApi
internal fun TvPlaylistScreenImpl(
    title: String,
    categoryWithChannels: List<PlaylistViewModel.CategoryWithChannels>,
    query: String,
    onQuery: (String) -> Unit,
    sorts: List<Sort>,
    sort: Sort,
    onSort: (Sort) -> Unit,
    favorite: (channelId: Int) -> Unit,
    hide: (channelId: Int) -> Unit,
    savePicture: (channelId: Int) -> Unit,
    createTvRecommend: (channelId: Int) -> Unit,
    onPlayChannel: (Channel) -> Unit,
    onRefresh: () -> Unit,
    getProgrammeCurrently: suspend (channelId: String) -> Programme?,
    isVodOrSeriesPlaylist: Boolean,
    modifier: Modifier = Modifier
) {
    val preferences = hiltPreferences()
    val multiCategories = categoryWithChannels.size > 1
    val noPictureMode = preferences.noPictureMode
    val useGridLayout = sort != Sort.UNSPECIFIED

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

    var press: Channel? by remember { mutableStateOf(null) }
    var focus: Channel? by remember { mutableStateOf(null) }

    val content = @Composable {
        Box(
            modifier = modifier.fillMaxWidth()
        ) {
            ImmersiveBackground(
                title = title,
                channel = focus,
                maxBrowserHeight = maxBrowserHeight,
                onRefresh = onRefresh,
                openSearchDrawer = {},
                openSortDrawer = { isSortSheetVisible = true },
                getProgrammeCurrently = getProgrammeCurrently,
                modifier = Modifier.haze(
                    LocalHazeState.current,
                    HazeDefaults.style(TvMaterialTheme.colorScheme.background)
                )
            )
            TvChannelGallery(
                categoryWithChannels = categoryWithChannels,
                maxBrowserHeight = maxBrowserHeight,
                isSpecifiedSort = useGridLayout,
                isVodOrSeriesPlaylist = isVodOrSeriesPlaylist,
                onClick = onPlayChannel,
                onLongClick = { channel -> press = channel },
                onFocus = { channel -> focus = channel },
                modifier = Modifier
                    .hazeChild(
                        LocalHazeState.current,
                        style = HazeStyle(blurRadius = 4.dp)
                    )
                    .blurEdge(
                        color = TvMaterialTheme.colorScheme.background,
                        edge = Edge.Top
                    )
                    .align(Alignment.BottomCenter)
            )
        }
    }

    Background {
        content()
        MenuFullScreenDialog(
            channel = press,
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
    channel: Channel?,
    favorite: (channelId: Int) -> Unit,
    hide: (channelId: Int) -> Unit,
    savePicture: (channelId: Int) -> Unit,
    createShortcutOrTvRecommend: (channelId: Int) -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier
) {
    val favouriteTitle = stringResource(
        if (channel?.favourite == true) R.string.feat_playlist_dialog_favourite_cancel_title
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
            visible = channel != null,
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(0.4f)
                .align(Alignment.CenterEnd)
        ) {
            LazyColumn(
                Modifier
                    .fillMaxHeight()
                    .background(TvMaterialTheme.colorScheme.surfaceVariant)
                    .padding(12.dp)
                    .selectableGroup()
                    .dialogFocusable(),
                horizontalAlignment = Alignment.Start,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    DenseListItem(
                        selected = false,
                        headlineContent = {
                            TvText(
                                text = channel?.title.orEmpty(),
                                maxLines = 1,
                                style = TvMaterialTheme.typography.titleLarge
                            )
                        },
                        onClick = {}
                    )
                }
                item {
                    DenseListItem(
                        selected = false,
                        headlineContent = {
                            TvText(
                                text = favouriteTitle
                            )
                        },
                        onClick = {
                            channel?.let { channel ->
                                favorite(channel.id)
                                onDismissRequest()
                            }
                        },
                        leadingContent = {
                            Icon(
                                imageVector = Icons.Rounded.Favorite,
                                contentDescription = null
                            )
                        },
                        scale = TvListItemDefaults.scale(0.95f, 1f)
                    )
                }
                item {
                    DenseListItem(
                        selected = false,
                        onClick = {
                            channel?.let {
                                hide(it.id)
                                onDismissRequest()
                            }
                        },
                        headlineContent = {
                            TvText(
                                text = hideTitle
                            )
                        },
                        leadingContent = {
                            Icon(
                                imageVector = Icons.Rounded.Delete,
                                contentDescription = null
                            )
                        },
                        scale = TvListItemDefaults.scale(0.95f, 1f)
                    )
                }
                item {
                    DenseListItem(
                        selected = false,
                        onClick = {
                            channel?.let {
                                createShortcutOrTvRecommend(it.id)
                                onDismissRequest()
                            }
                        },
                        headlineContent = {
                            TvText(
                                text = createShortcutTitle
                            )
                        },
                        leadingContent = {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.Shortcut,
                                contentDescription = null
                            )
                        },
                        scale = TvListItemDefaults.scale(0.95f, 1f)
                    )
                }
                item {
                    DenseListItem(
                        selected = false,
                        onClick = {
                            channel?.let {
                                savePicture(it.id)
                                onDismissRequest()
                            }
                        },
                        headlineContent = {
                            TvText(
                                text = savePictureTitle
                            )
                        },
                        leadingContent = {
                            Icon(
                                imageVector = Icons.Rounded.Image,
                                contentDescription = null
                            )
                        },
                        scale = TvListItemDefaults.scale(0.95f, 1f)
                    )
                }
            }
            BackHandler {
                onDismissRequest()
            }
        }
    }
}

