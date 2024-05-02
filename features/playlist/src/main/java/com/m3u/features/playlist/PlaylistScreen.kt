@file:Suppress("UsingMaterialAndMaterial3Libraries")

package com.m3u.features.playlist

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.res.Configuration.UI_MODE_TYPE_APPLIANCE
import android.content.res.Configuration.UI_MODE_TYPE_CAR
import android.content.res.Configuration.UI_MODE_TYPE_DESK
import android.content.res.Configuration.UI_MODE_TYPE_NORMAL
import android.content.res.Configuration.UI_MODE_TYPE_TELEVISION
import android.content.res.Configuration.UI_MODE_TYPE_VR_HEADSET
import android.content.res.Configuration.UI_MODE_TYPE_WATCH
import android.provider.Settings
import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardDoubleArrowUp
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.InternalComposeApi
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import com.google.accompanist.permissions.rememberPermissionState
import com.m3u.core.architecture.preferences.LocalPreferences
import com.m3u.core.util.basic.title
import com.m3u.core.wrapper.Event
import com.m3u.core.wrapper.eventOf
import com.m3u.data.database.model.Playlist
import com.m3u.data.database.model.Programme
import com.m3u.data.database.model.Stream
import com.m3u.data.database.model.type
import com.m3u.data.service.MediaCommand
import com.m3u.features.playlist.internal.SmartphonePlaylistScreenImpl
import com.m3u.features.playlist.internal.TvPlaylistScreenImpl
import com.m3u.i18n.R.string
import com.m3u.material.components.Background
import com.m3u.material.ktx.checkPermissionOrRationale
import com.m3u.material.ktx.interceptVolumeEvent
import com.m3u.material.ktx.isTelevision
import com.m3u.material.ktx.thenIf
import com.m3u.material.model.LocalSpacing
import com.m3u.ui.Destination
import com.m3u.ui.EpisodesBottomSheet
import com.m3u.ui.Sort
import com.m3u.ui.helper.Fob
import com.m3u.ui.helper.LocalHelper
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

@Composable
internal fun PlaylistRoute(
    navigateToStream: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PlaylistViewModel = hiltViewModel(),
    contentPadding: PaddingValues = PaddingValues()
) {
    val context = LocalContext.current
    val preferences = LocalPreferences.current
    val helper = LocalHelper.current
    val coroutineScope = rememberCoroutineScope()

    val tv = isTelevision()

    val zapping by viewModel.zapping.collectAsStateWithLifecycle()
    val playlistUrl by viewModel.playlistUrl.collectAsStateWithLifecycle()
    val playlist by viewModel.playlist.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val streamPaged = viewModel.streamPaged.collectAsLazyPagingItems()
    val episodes by viewModel.episodes.collectAsStateWithLifecycle()
    val pinnedCategories by viewModel.pinnedCategories.collectAsStateWithLifecycle()
    val refreshing by viewModel.subscribingOrRefreshing.collectAsStateWithLifecycle()
    val series by viewModel.series.collectAsStateWithLifecycle()

    val isSeriesPlaylist by remember {
        derivedStateOf {
            playlist?.type in Playlist.SERIES_TYPES
        }
    }
    val isVodPlaylist by remember {
        derivedStateOf {
            playlist?.type in Playlist.VOD_TYPES
        }
    }

    val sorts = viewModel.sorts
    val sort by viewModel.sort.collectAsStateWithLifecycle()

    val writeExternalPermission = rememberPermissionState(
        Manifest.permission.WRITE_EXTERNAL_STORAGE
    )

    @SuppressLint("InlinedApi")
    val postNotificationPermission = rememberPermissionState(
        Manifest.permission.POST_NOTIFICATIONS
    )

    LifecycleResumeEffect(playlist) {
        helper.title = playlist?.title?.title().orEmpty()
        onPauseOrDispose {
        }
    }

    LaunchedEffect(preferences.autoRefresh, playlistUrl) {
        if (playlistUrl.isNotEmpty() && preferences.autoRefresh) {
            viewModel.refresh()
        }
    }

    BackHandler(viewModel.query.isNotEmpty()) {
        viewModel.query = ""
    }

    Background {
        Box {
            PlaylistScreen(
                title = playlist?.title.orEmpty(),
                query = viewModel.query,
                onQuery = { viewModel.query = it },
                rowCount = preferences.rowCount,
                zapping = zapping,
                categories = categories,
                streamPaged = streamPaged,
                pinnedCategories = pinnedCategories,
                onPinOrUnpinCategory = { viewModel.pinOrUnpinCategory(it) },
                onHideCategory = { viewModel.hideCategory(it) },
                scrollUp = viewModel.scrollUp,
                sorts = sorts,
                sort = sort,
                onSort = { viewModel.sort(it) },
                onStream = { stream ->
                    if (!isSeriesPlaylist) {
                        coroutineScope.launch {
                            helper.play(MediaCommand.Live(stream.id))
                            navigateToStream()
                        }
                    } else {
                        viewModel.series.value = stream
                    }
                },
                onScrollUp = { viewModel.scrollUp = eventOf(Unit) },
                refreshing = refreshing,
                onRefresh = {
                    postNotificationPermission.checkPermissionOrRationale(
                        showRationale = {
                            val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                .apply {
                                    putExtra(
                                        Settings.EXTRA_APP_PACKAGE,
                                        helper.activityContext.packageName
                                    )
                                }
                            helper.activityContext.startActivity(intent)
                        },
                        block = {
                            viewModel.refresh()
                        }
                    )
                },
                contentPadding = contentPadding,
                favourite = viewModel::favourite,
                hide = viewModel::hide,
                savePicture = { id ->
                    writeExternalPermission.checkPermissionOrRationale {
                        viewModel.savePicture(id)
                    }
                },
                createShortcut = { id -> viewModel.createShortcut(context, id) },
                createTvRecommend = { id -> viewModel.createTvRecommend(context, id) },
                isVodPlaylist = isVodPlaylist,
                isSeriesPlaylist = isSeriesPlaylist,
                getProgrammeCurrently = { channelId -> viewModel.getProgrammeCurrently(channelId) },
                modifier = Modifier
                    .fillMaxSize()
                    .thenIf(!tv && preferences.godMode) {
                        Modifier.interceptVolumeEvent { event ->
                            preferences.rowCount = when (event) {
                                KeyEvent.KEYCODE_VOLUME_UP ->
                                    (preferences.rowCount - 1).coerceAtLeast(1)

                                KeyEvent.KEYCODE_VOLUME_DOWN ->
                                    (preferences.rowCount + 1).coerceAtMost(2)

                                else -> return@interceptVolumeEvent
                            }
                        }
                    }
                    .then(modifier)
            )

            if (isSeriesPlaylist) {
                EpisodesBottomSheet(
                    series = series,
                    episodes = episodes,
                    onEpisodeClick = { episode ->
                        coroutineScope.launch {
                            series?.let {
                                val input = MediaCommand.XtreamEpisode(
                                    streamId = it.id,
                                    episode = episode
                                )
                                helper.play(input)
                                navigateToStream()
                            }
                        }
                    },
                    onRefresh = { viewModel.seriesReplay.value += 1 },
                    onDismissRequest = {
                        viewModel.series.value = null
                    }
                )
            }
        }
    }
}

@OptIn(InternalComposeApi::class)
@Composable
private fun PlaylistScreen(
    title: String,
    query: String,
    onQuery: (String) -> Unit,
    rowCount: Int,
    zapping: Stream?,
    categories: List<Category>,
    streamPaged: LazyPagingItems<Stream>,
    pinnedCategories: List<String>,
    onPinOrUnpinCategory: (String) -> Unit,
    onHideCategory: (String) -> Unit,
    sorts: List<Sort>,
    sort: Sort,
    onSort: (Sort) -> Unit,
    scrollUp: Event<Unit>,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    onStream: (Stream) -> Unit,
    onScrollUp: () -> Unit,
    favourite: (streamId: Int) -> Unit,
    hide: (streamId: Int) -> Unit,
    savePicture: (streamId: Int) -> Unit,
    createShortcut: (streamId: Int) -> Unit,
    createTvRecommend: (streamId: Int) -> Unit,
    contentPadding: PaddingValues,
    isVodPlaylist: Boolean,
    isSeriesPlaylist: Boolean,
    getProgrammeCurrently: suspend (channelId: String) -> Programme?,
    modifier: Modifier = Modifier
) {
    val helper = LocalHelper.current
    val currentOnScrollUp by rememberUpdatedState(onScrollUp)

    val isAtTopState = remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        snapshotFlow { isAtTopState.value }
            .distinctUntilChanged()
            .onEach { newValue ->
                helper.fob = if (newValue) null
                else {
                    Fob(
                        icon = Icons.Rounded.KeyboardDoubleArrowUp,
                        rootDestination = Destination.Root.Foryou,
                        iconTextId = string.feat_playlist_scroll_up,
                        onClick = currentOnScrollUp
                    )
                }
            }
            .launchIn(this)
    }

    DisposableEffect(Unit) {
        onDispose { helper.fob = null }
    }

    val tv = isTelevision()
    if (!tv) {
        SmartphonePlaylistScreenImpl(
            categories = categories,
            streamPaged = streamPaged,
            pinnedCategories = pinnedCategories,
            onPinOrUnpinCategory = onPinOrUnpinCategory,
            onHideCategory = onHideCategory,
            zapping = zapping,
            query = query,
            onQuery = onQuery,
            rowCount = rowCount,
            scrollUp = scrollUp,
            contentPadding = contentPadding,
            onStream = onStream,
            isAtTopState = isAtTopState,
            refreshing = refreshing,
            onRefresh = onRefresh,
            sorts = sorts,
            sort = sort,
            onSort = onSort,
            favourite = favourite,
            onHide = hide,
            onSaveCover = savePicture,
            onCreateShortcut = createShortcut,
            isVodOrSeriesPlaylist = isVodPlaylist || isSeriesPlaylist,
            getProgrammeCurrently = getProgrammeCurrently,
            modifier = modifier
        )
    } else {
        TvPlaylistScreenImpl(
            title = title,
            categories = categories,
            streamPaged = streamPaged,
            query = query,
            onQuery = onQuery,
            onStream = onStream,
            onRefresh = onRefresh,
            sorts = sorts,
            sort = sort,
            onSort = onSort,
            favorite = favourite,
            hide = hide,
            savePicture = savePicture,
            createTvRecommend = createTvRecommend,
            isVodOrSeriesPlaylist = isVodPlaylist || isSeriesPlaylist,
            modifier = modifier
        )
    }
}

@Composable
@Suppress("UNUSED")
private fun UnsupportedUIModeContent(
    type: Int,
    modifier: Modifier = Modifier,
    description: String? = null,
) {
    val spacing = LocalSpacing.current

    val device = remember(type) {
        when (type) {
            UI_MODE_TYPE_NORMAL -> "Normal"
            UI_MODE_TYPE_DESK -> "Desktop"
            UI_MODE_TYPE_CAR -> "Car"
            UI_MODE_TYPE_TELEVISION -> "Television"
            UI_MODE_TYPE_APPLIANCE -> "Appliance"
            UI_MODE_TYPE_WATCH -> "Watch"
            UI_MODE_TYPE_VR_HEADSET -> "VR-Headset"
            else -> "Device Type: $type"
        }
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(
            spacing.medium,
            Alignment.CenterVertically
        ),
        modifier = modifier.fillMaxSize()
    ) {
        Text("Unsupported UI Mode: $device")
        if (description != null) {
            Text(description)
        }
    }
}
