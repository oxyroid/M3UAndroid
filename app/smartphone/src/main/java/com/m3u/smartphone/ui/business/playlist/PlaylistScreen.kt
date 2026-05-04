package com.m3u.smartphone.ui.business.playlist

import android.Manifest
import android.content.Intent
import android.content.res.Configuration.ORIENTATION_LANDSCAPE
import android.content.res.Configuration.ORIENTATION_PORTRAIT
import android.content.res.Configuration.UI_MODE_TYPE_APPLIANCE
import android.content.res.Configuration.UI_MODE_TYPE_CAR
import android.content.res.Configuration.UI_MODE_TYPE_DESK
import android.content.res.Configuration.UI_MODE_TYPE_NORMAL
import android.content.res.Configuration.UI_MODE_TYPE_TELEVISION
import android.content.res.Configuration.UI_MODE_TYPE_VR_HEADSET
import android.content.res.Configuration.UI_MODE_TYPE_WATCH
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.KeyboardDoubleArrowUp
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.InternalComposeApi
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.PagingData
import com.google.accompanist.permissions.rememberPermissionState
import com.m3u.business.playlist.ChannelWithProgramme
import com.m3u.business.playlist.PlaylistViewModel
import com.m3u.core.foundation.architecture.preferences.PreferencesKeys
import com.m3u.core.foundation.architecture.preferences.mutablePreferenceOf
import com.m3u.core.foundation.architecture.preferences.preferenceOf
import com.m3u.core.foundation.ui.thenIf
import com.m3u.core.foundation.util.basic.title
import com.m3u.core.foundation.wrapper.Event
import com.m3u.core.foundation.wrapper.Sort
import com.m3u.core.foundation.wrapper.eventOf
import com.m3u.data.database.model.Channel
import com.m3u.data.database.model.isSeries
import com.m3u.data.database.model.isVod
import com.m3u.data.service.MediaCommand
import com.m3u.i18n.R.string
import com.m3u.smartphone.ui.business.playlist.components.ChannelGallery
import com.m3u.smartphone.ui.business.playlist.components.PlaylistTabRow
import com.m3u.smartphone.ui.common.helper.Action
import com.m3u.smartphone.ui.common.helper.Fob
import com.m3u.smartphone.ui.common.helper.LocalHelper
import com.m3u.smartphone.ui.common.helper.Metadata
import com.m3u.smartphone.ui.material.components.Destination
import com.m3u.smartphone.ui.material.components.EpisodesBottomSheet
import com.m3u.smartphone.ui.material.components.EventHandler
import com.m3u.smartphone.ui.material.components.MediaSheet
import com.m3u.smartphone.ui.material.components.MediaSheetValue
import com.m3u.smartphone.ui.material.components.SortBottomSheet
import com.m3u.smartphone.ui.material.ktx.checkPermissionOrRationale
import com.m3u.smartphone.ui.material.ktx.interceptVolumeEvent
import com.m3u.smartphone.ui.material.ktx.isAtTop
import com.m3u.smartphone.ui.material.ktx.minus
import com.m3u.smartphone.ui.material.ktx.only
import com.m3u.smartphone.ui.material.model.LocalHazeState
import com.m3u.smartphone.ui.material.model.LocalSpacing
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

@Composable
internal fun PlaylistRoute(
    navigateToChannel: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PlaylistViewModel = hiltViewModel(),
    contentPadding: PaddingValues = PaddingValues()
) {
    val context = LocalContext.current
    val helper = LocalHelper.current
    val coroutineScope = rememberCoroutineScope()
    val colorScheme = MaterialTheme.colorScheme

    val autoRefreshChannels by preferenceOf(PreferencesKeys.AUTO_REFRESH_CHANNELS)
    var rowCount by mutablePreferenceOf(PreferencesKeys.ROW_COUNT)
    var godMode by mutablePreferenceOf(PreferencesKeys.GOD_MODE)

    val zapping by viewModel.zapping.collectAsStateWithLifecycle()
    val playlistUrl by viewModel.playlistUrl.collectAsStateWithLifecycle()
    val playlist by viewModel.playlist.collectAsStateWithLifecycle()

    val channels: Map<String, Flow<PagingData<ChannelWithProgramme>>> by viewModel.channels.collectAsStateWithLifecycle(
        minActiveState = Lifecycle.State.RESUMED
    )

    val episodes by viewModel.episodes.collectAsStateWithLifecycle()
    val pinnedCategories by viewModel.pinnedCategories.collectAsStateWithLifecycle()
    val refreshing by viewModel.subscribingOrRefreshing.collectAsStateWithLifecycle()
    val series by viewModel.series.collectAsStateWithLifecycle()

    val isSeriesPlaylist by remember { derivedStateOf { playlist?.isSeries ?: false } }
    val isVodPlaylist by remember { derivedStateOf { playlist?.isVod ?: false } }

    val sorts = Sort.entries
    val sort by viewModel.sort.collectAsStateWithLifecycle()

    val query by viewModel.query.collectAsStateWithLifecycle()
    val scrollUp by viewModel.scrollUp.collectAsStateWithLifecycle()

    val writeExternalPermission =
        rememberPermissionState(Manifest.permission.WRITE_EXTERNAL_STORAGE)

    val postNotificationPermission = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) null
    else rememberPermissionState(Manifest.permission.POST_NOTIFICATIONS)

    val title by remember { derivedStateOf { playlist?.title?.title().orEmpty() } }

    LifecycleResumeEffect(
        title,
        colorScheme
    ) {
        Metadata.title = AnnotatedString(title)
        Metadata.color = colorScheme.secondaryContainer
        Metadata.contentColor = colorScheme.onSecondaryContainer
        onPauseOrDispose {}
    }

    LaunchedEffect(autoRefreshChannels, playlistUrl) {
        if (playlistUrl.isNotEmpty() && autoRefreshChannels) {
            viewModel.refresh()
        }
    }

    BackHandler(query.isNotEmpty()) {
        viewModel.query.value = ""
    }

    PlaylistScreen(
        state = PlaylistScreenState(
            rowCount = rowCount,
            zapping = zapping,
            channels = channels,
            pinnedCategories = pinnedCategories,
            scrollUp = scrollUp,
            sorts = sorts,
            sort = sort,
            refreshing = refreshing,
            contentPadding = contentPadding,
            isVodPlaylist = isVodPlaylist,
            isSeriesPlaylist = isSeriesPlaylist,
        ),
        actions = PlaylistScreenActions(
            onPinOrUnpinCategory = { viewModel.onPinOrUnpinCategory(it) },
            onHideCategory = { viewModel.onHideCategory(it) },
            onSort = { viewModel.sort(it) },
            onPlayChannel = { channel ->
                if (!isSeriesPlaylist) {
                    coroutineScope.launch {
                        helper.play(MediaCommand.Common(channel.id))
                        navigateToChannel()
                    }
                } else {
                    viewModel.series.value = channel
                }
            },
            onScrollUp = { viewModel.scrollUp.value = eventOf(Unit) },
            onRefresh = {
                if (postNotificationPermission == null) {
                    viewModel.refresh()
                } else {
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
                }
            },
            favourite = viewModel::favourite,
            hide = viewModel::hide,
            savePicture = { id ->
                writeExternalPermission.checkPermissionOrRationale {
                    viewModel.savePicture(id)
                }
            },
            createShortcut = { id -> viewModel.createShortcut(context, id) },
            reloadThumbnail = { channelUrl -> viewModel.reloadThumbnail(channelUrl) },
            syncThumbnail = { channelUrl ->
                /** disabled in smartphone because it will cost too much data*/
                null
            },
        ),
        modifier = Modifier
            .fillMaxSize()
            .thenIf(godMode) {
                Modifier.interceptVolumeEvent { event ->
                    rowCount = when (event) {
                        KeyEvent.KEYCODE_VOLUME_UP ->
                            (rowCount - 1).coerceAtLeast(1)

                        KeyEvent.KEYCODE_VOLUME_DOWN ->
                            (rowCount + 1).coerceAtMost(2)

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
                            channelId = it.id,
                            episode = episode
                        )
                        helper.play(input)
                        navigateToChannel()
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

@Immutable
private data class PlaylistScreenState(
    val rowCount: Int,
    val zapping: Channel?,
    val channels: Map<String, Flow<PagingData<ChannelWithProgramme>>>,
    val pinnedCategories: List<String>,
    val scrollUp: Event<Unit>,
    val sorts: List<Sort>,
    val sort: Sort,
    val refreshing: Boolean,
    val contentPadding: PaddingValues,
    val isVodPlaylist: Boolean,
    val isSeriesPlaylist: Boolean,
)

@Immutable
private data class PlaylistScreenActions(
    val onPinOrUnpinCategory: (String) -> Unit,
    val onHideCategory: (String) -> Unit,
    val onSort: (Sort) -> Unit,
    val onPlayChannel: (Channel) -> Unit,
    val onScrollUp: () -> Unit,
    val onRefresh: () -> Unit,
    val favourite: (channelId: Int) -> Unit,
    val hide: (channelId: Int) -> Unit,
    val savePicture: (channelId: Int) -> Unit,
    val createShortcut: (channelId: Int) -> Unit,
    val reloadThumbnail: suspend (channelUrl: String) -> Uri?,
    val syncThumbnail: suspend (channelUrl: String) -> Uri?,
)

@OptIn(InternalComposeApi::class)
@Composable
private fun PlaylistScreen(
    state: PlaylistScreenState,
    actions: PlaylistScreenActions,
    modifier: Modifier = Modifier
) {
    val currentOnScrollUp by rememberUpdatedState(actions.onScrollUp)
    val currentOnRefresh by rememberUpdatedState(actions.onRefresh)

    val isAtTopState = remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        snapshotFlow { isAtTopState.value }
            .distinctUntilChanged()
            .onEach { newValue ->
                Metadata.fob = if (newValue) null
                else {
                    Fob(
                        icon = Icons.Rounded.KeyboardDoubleArrowUp,
                        destination = Destination.Foryou,
                        iconTextId = string.feat_playlist_scroll_up,
                        onClick = currentOnScrollUp
                    )
                }
            }
            .launchIn(this)
    }

    DisposableEffect(Unit) {
        onDispose { Metadata.fob = null }
    }

    val configuration = LocalConfiguration.current

    val sheetState = rememberModalBottomSheetState()

    var mediaSheetValue: MediaSheetValue.PlaylistScreen by remember { mutableStateOf(MediaSheetValue.PlaylistScreen()) }
    var isSortSheetVisible by rememberSaveable { mutableStateOf(false) }

    LifecycleResumeEffect(state.refreshing) {
        Metadata.actions = buildList {
            Action(
                icon = Icons.AutoMirrored.Rounded.Sort,
                contentDescription = "sort",
                onClick = { isSortSheetVisible = true }
            ).also { add(it) }
            Action(
                icon = Icons.Rounded.Refresh,
                enabled = !state.refreshing,
                contentDescription = "refresh",
                onClick = currentOnRefresh
            ).also { add(it) }
        }
        onPauseOrDispose {
            Metadata.actions = emptyList()
        }
    }

    val categories = remember(state.channels) { state.channels.map { it.key } }
    var category by remember(categories) { mutableStateOf(categories.firstOrNull().orEmpty()) }

    val gridState = rememberLazyStaggeredGridState()
    LaunchedEffect(Unit) {
        snapshotFlow { gridState.isAtTop }
            .onEach { isAtTopState.value = it }
            .launchIn(this)
    }
    EventHandler(state.scrollUp) {
        gridState.scrollToItem(0)
    }
    val orientation = configuration.orientation
    val actualRowCount = remember(orientation, state.rowCount) {
        when (orientation) {
            ORIENTATION_LANDSCAPE -> state.rowCount + 2
            ORIENTATION_PORTRAIT -> state.rowCount
            else -> state.rowCount
        }
    }
    var isExpanded by remember(state.sort == Sort.MIXED) {
        mutableStateOf(false)
    }
    BackHandler(isExpanded) { isExpanded = false }

    var targetPageIndex: Event<Int> by remember { mutableStateOf(Event.Handled()) }

    val tabs = @Composable {
        PlaylistTabRow(
            selectedCategory = category,
            categories = categories,
            isExpanded = isExpanded,
            bottomContentPadding = state.contentPadding only WindowInsetsSides.Bottom,
            onExpanded = { isExpanded = !isExpanded },
            onCategoryChanged = {
                category = it
                targetPageIndex = categories.indexOf(it)
                    .takeIf { it != -1 }
                    ?.let { eventOf(it) }
                    ?: Event.Handled()
            },
            pinnedCategories = state.pinnedCategories,
            onPinOrUnpinCategory = actions.onPinOrUnpinCategory,
            onHideCategory = actions.onHideCategory
        )
    }

    val gallery = @Composable {
        val pagerState = rememberPagerState { state.channels.size }
        val entries = state.channels.entries.toList()
        LaunchedEffect(entries) {
            snapshotFlow { pagerState.settledPage }
                .collectLatest { index ->
                    category = entries.getOrNull(index)?.key.orEmpty()
                }
        }
        EventHandler(targetPageIndex) {
            pagerState.scrollToPage(it)
        }
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .hazeSource(LocalHazeState.current)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
        ) { index ->
            val (_, channels) = entries[index]

            ChannelGallery(
                state = gridState,
                rowCount = actualRowCount,
                channels = channels,
                zapping = state.zapping,
                recently = state.sort == Sort.RECENTLY,
                isVodOrSeriesPlaylist = state.isVodPlaylist || state.isSeriesPlaylist,
                onClick = actions.onPlayChannel,
                contentPadding = state.contentPadding.minus(state.contentPadding.only(WindowInsetsSides.Top)),
                onLongClick = {
                    mediaSheetValue = MediaSheetValue.PlaylistScreen(it)
                },
                reloadThumbnail = actions.reloadThumbnail,
                syncThumbnail = actions.syncThumbnail,
            )
        }
    }
    Column(
        Modifier
            .padding(state.contentPadding.minus(state.contentPadding.only(WindowInsetsSides.Bottom)))
            .then(modifier)
    ) {
        if (!isExpanded) {
            AnimatedVisibility(
                visible = categories.size > 1,
                enter = fadeIn(animationSpec = tween(400)),
            ) {
                tabs()
            }
            gallery()
        } else {
            AnimatedVisibility(
                visible = categories.size > 1,
                enter = fadeIn(animationSpec = tween(400))
            ) {
                tabs()
            }
        }
    }

    SortBottomSheet(
        visible = isSortSheetVisible,
        sort = state.sort,
        sorts = state.sorts,
        sheetState = sheetState,
        onChanged = actions.onSort,
        onDismissRequest = { isSortSheetVisible = false }
    )

    MediaSheet(
        value = mediaSheetValue,
        onFavoriteChannel = { channel ->
            actions.favourite(channel.id)
            mediaSheetValue = MediaSheetValue.PlaylistScreen()
        },
        onHideChannel = { channel ->
            actions.hide(channel.id)
            mediaSheetValue = MediaSheetValue.PlaylistScreen()
        },
        onSaveChannelCover = { channel ->
            actions.savePicture(channel.id)
            mediaSheetValue = MediaSheetValue.PlaylistScreen()
        },
        onCreateShortcut = { channel ->
            actions.createShortcut(channel.id)
            mediaSheetValue = MediaSheetValue.PlaylistScreen()
        },
        onDismissRequest = { mediaSheetValue = MediaSheetValue.PlaylistScreen() }
    )
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
            UI_MODE_TYPE_TELEVISION -> "Tv"
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
