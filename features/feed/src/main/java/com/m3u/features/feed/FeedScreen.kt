@file:OptIn(ExperimentalFoundationApi::class, ExperimentalMaterialApi::class)

package com.m3u.features.feed

import android.content.res.Configuration.ORIENTATION_LANDSCAPE
import android.content.res.Configuration.ORIENTATION_PORTRAIT
import android.content.res.Configuration.UI_MODE_TYPE_APPLIANCE
import android.content.res.Configuration.UI_MODE_TYPE_CAR
import android.content.res.Configuration.UI_MODE_TYPE_DESK
import android.content.res.Configuration.UI_MODE_TYPE_MASK
import android.content.res.Configuration.UI_MODE_TYPE_NORMAL
import android.content.res.Configuration.UI_MODE_TYPE_TELEVISION
import android.content.res.Configuration.UI_MODE_TYPE_VR_HEADSET
import android.content.res.Configuration.UI_MODE_TYPE_WATCH
import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.ExtendedFloatingActionButton
import androidx.compose.material.FloatingActionButtonDefaults
import androidx.compose.material.Icon
import androidx.compose.material.ScrollableTabRow
import androidx.compose.material.Surface
import androidx.compose.material.Tab
import androidx.compose.material.TabRowDefaults
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.foundation.lazy.grid.TvGridCells
import androidx.tv.foundation.lazy.grid.TvLazyVerticalGrid
import androidx.tv.foundation.lazy.grid.items
import androidx.tv.foundation.lazy.grid.rememberTvLazyGridState
import com.m3u.core.util.basic.uppercaseFirst
import com.m3u.core.wrapper.Event
import com.m3u.data.database.entity.Live
import com.m3u.features.feed.components.DialogStatus
import com.m3u.features.feed.components.FeedDialog
import com.m3u.features.feed.components.LiveItem
import com.m3u.ui.components.TextField
import com.m3u.ui.model.AppAction
import com.m3u.ui.model.LocalDuration
import com.m3u.ui.model.LocalHelper
import com.m3u.ui.model.LocalScalable
import com.m3u.ui.model.LocalSpacing
import com.m3u.ui.model.LocalTheme
import com.m3u.ui.model.Scalable
import com.m3u.ui.util.EventHandler
import com.m3u.ui.util.RepeatOnCreate
import com.m3u.ui.util.interceptVolumeEvent
import com.m3u.ui.util.isAtTop
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private typealias NavigateToLive = (liveId: Int) -> Unit
private typealias NavigateToPlaylist = (playlist: List<Int>, initialIndex: Int) -> Unit
private typealias OnLongClickLive = (Live) -> Unit
private typealias OnScrollUp = () -> Unit
private typealias OnRefresh = () -> Unit

@Composable
internal fun FeedRoute(
    url: String,
    navigateToLive: NavigateToLive,
    navigateToPlaylist: NavigateToPlaylist,
    modifier: Modifier = Modifier,
    viewModel: FeedViewModel = hiltViewModel()
) {
    val helper = LocalHelper.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    var dialogStatus: DialogStatus by remember { mutableStateOf(DialogStatus.Idle) }

    LaunchedEffect(url) {
        viewModel.onEvent(FeedEvent.Observe(url))
    }
    RepeatOnCreate {
        helper.actions = listOf(
            AppAction(
                icon = Icons.Rounded.Refresh,
                contentDescription = "refresh",
                onClick = {
                    viewModel.onEvent(FeedEvent.Refresh)
                }
            )
        )
    }
    LaunchedEffect(state.autoRefresh, state.url) {
        if (state.url.isNotEmpty() && state.autoRefresh) {
            viewModel.onEvent(FeedEvent.Refresh)
        }
    }
    val rowCount = state.rowCount
    fun onRowCount(target: Int) {
        state.rowCount = target
    }
    BackHandler(state.query.isNotEmpty()) {
        viewModel.onEvent(FeedEvent.Query(""))
    }
    val interceptVolumeEventModifier = remember(state.godMode) {
        if (state.godMode) {
            Modifier.interceptVolumeEvent { event ->
                when (event) {
                    KeyEvent.KEYCODE_VOLUME_UP -> onRowCount((rowCount - 1).coerceAtLeast(1))
                    KeyEvent.KEYCODE_VOLUME_DOWN -> onRowCount((rowCount + 1).coerceAtMost(3))
                }
            }
        } else Modifier
    }

    CompositionLocalProvider(
        LocalScalable provides Scalable(1f / rowCount)
    ) {
        FeedScreen(
            query = state.query,
            onQuery = { viewModel.onEvent(FeedEvent.Query(it)) },
            useCommonUIMode = state.useCommonUIMode,
            scrollMode = state.scrollMode,
            noPictureMode = state.noPictureMode,
            rowCount = rowCount,
            lives = state.lives,
            scrollUp = state.scrollUp,
            refreshing = state.fetching,
            onRefresh = { viewModel.onEvent(FeedEvent.Refresh) },
            navigateToLive = navigateToLive,
            navigateToPlaylist = navigateToPlaylist,
            onLongClickLive = {
                dialogStatus = DialogStatus.Selections(it)
            },
            onScrollUp = { viewModel.onEvent(FeedEvent.ScrollUp) },
            modifier = modifier
                .fillMaxSize()
                .then(interceptVolumeEventModifier)
        )
    }

    FeedDialog(
        status = dialogStatus,
        onUpdate = { dialogStatus = it },
        onFavorite = { id, target -> viewModel.onEvent(FeedEvent.Favourite(id, target)) },
        onBanned = { id, target -> viewModel.onEvent(FeedEvent.Mute(id, target)) },
        onSavePicture = { viewModel.onEvent(FeedEvent.SavePicture(it)) }
    )
}

@Composable
private fun FeedScreen(
    query: String,
    onQuery: (String) -> Unit,
    useCommonUIMode: Boolean,
    scrollMode: Boolean,
    noPictureMode: Boolean,
    rowCount: Int,
    lives: MappedLives,
    scrollUp: Event<Unit>,
    refreshing: Boolean,
    onRefresh: OnRefresh,
    navigateToLive: NavigateToLive,
    navigateToPlaylist: NavigateToPlaylist,
    onLongClickLive: OnLongClickLive,
    onScrollUp: OnScrollUp,
    modifier: Modifier = Modifier
) {
    val theme = LocalTheme.current
    val configuration = LocalConfiguration.current
    val spacing = LocalSpacing.current
    val duration = LocalDuration.current
    val state = rememberPullRefreshState(
        refreshing = refreshing,
        onRefresh = onRefresh
    )
    Column {
        val isAtTopState = remember { mutableStateOf(true) }
        var flag by remember { mutableStateOf(false) }
        val surfaceElevation by animateDpAsState(
            if (!isAtTopState.value && flag) spacing.medium else spacing.none
        )
        LaunchedEffect(Unit) {
            delay(duration.medium.toLong())
            flag = true
        }
        Surface(
            elevation = surfaceElevation,
            color = theme.background,
            contentColor = theme.onBackground
        ) {
            TextField(
                text = query,
                onValueChange = onQuery,
                height = 32.dp,
                placeholder = stringResource(R.string.query_placeholder).uppercaseFirst(),
                modifier = Modifier
                    .padding(LocalSpacing.current.medium)
                    .fillMaxWidth()
            )
        }
        Box(
            modifier = Modifier.pullRefresh(state)
        ) {
            when (configuration.orientation) {
                ORIENTATION_LANDSCAPE -> {
                    FeedPager(
                        lives = lives,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        LandscapeOrientationContent(
                            lives = it,
                            useCommonUIMode = useCommonUIMode,
                            scrollMode = scrollMode,
                            noPictureMode = noPictureMode,
                            rowCount = rowCount,
                            isAtTopState = isAtTopState,
                            scrollUp = scrollUp,
                            navigateToLive = navigateToLive,
                            navigateToPlaylist = navigateToPlaylist,
                            onLongClickLive = onLongClickLive,
                            modifier = modifier
                        )
                    }
                }

                ORIENTATION_PORTRAIT -> {
                    FeedPager(
                        lives = lives,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        PortraitOrientationContent(
                            lives = it,
                            scrollMode = scrollMode,
                            noPictureMode = noPictureMode,
                            rowCount = rowCount,
                            isAtTopState = isAtTopState,
                            scrollUp = scrollUp,
                            navigateToLive = navigateToLive,
                            navigateToLivePlayList = navigateToPlaylist,
                            onLongClickLive = onLongClickLive,
                            modifier = modifier
                        )
                    }
                }

                else -> {}
            }
            PullRefreshIndicator(
                refreshing = refreshing,
                state = state,
                modifier = Modifier.align(Alignment.TopCenter),
                scale = true,
                contentColor = theme.onTint,
                backgroundColor = theme.tint
            )
            this@Column.AnimatedVisibility(
                visible = !isAtTopState.value,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
                modifier = Modifier
                    .padding(LocalSpacing.current.medium)
                    .align(Alignment.BottomEnd)
            ) {
                ExtendedFloatingActionButton(
                    text = {
                        Text(stringResource(R.string.scroll_up).uppercase())
                    },
                    onClick = onScrollUp,
                    backgroundColor = theme.tint,
                    contentColor = theme.onTint,
                    icon = {
                        Icon(
                            imageVector = Icons.Rounded.ArrowUpward,
                            contentDescription = null
                        )
                    },
                    elevation = FloatingActionButtonDefaults.elevation(
                        defaultElevation = spacing.none,
                        pressedElevation = spacing.small,
                        hoveredElevation = spacing.extraSmall,
                        focusedElevation = spacing.extraSmall,
                    ),
                    modifier = Modifier
                        .border(
                            spacing.extraSmall,
                            Color.Black.copy(alpha = 0.114f),
                            shape = CircleShape
                        )
                )
            }
        }
    }
}

@Composable
private fun LandscapeOrientationContent(
    useCommonUIMode: Boolean,
    scrollMode: Boolean,
    noPictureMode: Boolean,
    rowCount: Int,
    lives: List<Live>,
    scrollUp: Event<Unit>,
    isAtTopState: MutableState<Boolean>,
    navigateToLive: NavigateToLive,
    navigateToPlaylist: NavigateToPlaylist,
    onLongClickLive: OnLongClickLive,
    modifier: Modifier = Modifier
) {
    val scalable = LocalScalable.current
    val spacing = with(scalable) {
        LocalSpacing.current.scaled
    }
    val configuration = LocalConfiguration.current
    val type = configuration.uiMode and UI_MODE_TYPE_MASK
    val ids = remember(scrollMode, lives) {
        if (scrollMode) lives.map { it.id }
        else emptyList()
    }

    if (useCommonUIMode || type == UI_MODE_TYPE_NORMAL) {
        val state = rememberLazyGridState()
        LaunchedEffect(state.isAtTop) {
            isAtTopState.value = state.isAtTop
        }
        EventHandler(scrollUp) {
            state.scrollToItem(0)
        }
        LazyVerticalGrid(
            state = state,
            columns = GridCells.Fixed(rowCount + 2),
            verticalArrangement = Arrangement.spacedBy(spacing.medium),
            horizontalArrangement = Arrangement.spacedBy(spacing.medium),
            contentPadding = PaddingValues(LocalSpacing.current.medium),
            modifier = modifier.fillMaxSize()
        ) {
            items(
                items = lives,
                key = { it.id },
                contentType = { it.cover.isNullOrEmpty() }
            ) { live ->
                LiveItem(
                    live = live,
                    noPictureMode = noPictureMode,
                    onClick = {
                        if (scrollMode) {
                            val initialIndex = ids.indexOfFirst { it == live.id }
                            navigateToPlaylist(ids, initialIndex)
                        } else {
                            navigateToLive(live.id)
                        }
                    },
                    onLongClick = { onLongClickLive(live) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    } else {
        when (type) {
            UI_MODE_TYPE_TELEVISION -> {
                TelevisionUIModeContent(
                    lives = lives,
                    experimentalMode = scrollMode,
                    noPictureMode = noPictureMode,
                    isAtTopState = isAtTopState,
                    scrollUp = scrollUp,
                    navigateToLive = navigateToLive,
                    navigateToLivePlayList = navigateToPlaylist,
                    onLongClickLive = onLongClickLive,
                    modifier = modifier
                )
            }

            else -> {
                UnsupportedUIModeContent(
                    type = type,
                    modifier = modifier
                )
            }
        }
    }
}

@Composable
private fun PortraitOrientationContent(
    lives: List<Live>,
    scrollMode: Boolean,
    noPictureMode: Boolean,
    rowCount: Int,
    scrollUp: Event<Unit>,
    isAtTopState: MutableState<Boolean>,
    navigateToLive: (Int) -> Unit,
    navigateToLivePlayList: (List<Int>, Int) -> Unit,
    onLongClickLive: OnLongClickLive,
    modifier: Modifier = Modifier
) {
    val scalable = LocalScalable.current
    val spacing = with(scalable) {
        LocalSpacing.current.scaled
    }
    val state = rememberLazyGridState()
    val ids = remember(scrollMode, lives) {
        if (scrollMode) lives.map { it.id }
        else emptyList()
    }

    LaunchedEffect(state.isAtTop) {
        isAtTopState.value = state.isAtTop
    }
    EventHandler(scrollUp) {
        state.scrollToItem(0)
    }
    LazyVerticalGrid(
        state = state,
        columns = GridCells.Fixed(rowCount),
        verticalArrangement = Arrangement.spacedBy(spacing.medium),
        horizontalArrangement = Arrangement.spacedBy(spacing.medium),
        contentPadding = PaddingValues(LocalSpacing.current.medium),
        modifier = modifier.fillMaxSize()
    ) {
        items(
            items = lives,
            key = { live -> live.id },
            contentType = { it.cover.isNullOrEmpty() }
        ) { live ->
            LiveItem(
                live = live,
                noPictureMode = noPictureMode,
                onClick = {
                    if (scrollMode) {
                        val initialIndex = ids.indexOfFirst { it == live.id }
                        navigateToLivePlayList(ids, initialIndex)
                    } else {
                        navigateToLive(live.id)
                    }
                },
                onLongClick = { onLongClickLive(live) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun TelevisionUIModeContent(
    lives: List<Live>,
    experimentalMode: Boolean,
    noPictureMode: Boolean,
    isAtTopState: MutableState<Boolean>,
    scrollUp: Event<Unit>,
    navigateToLive: (Int) -> Unit,
    navigateToLivePlayList: (List<Int>, Int) -> Unit,
    onLongClickLive: (Live) -> Unit,
    modifier: Modifier = Modifier
) {
    val scalable = LocalScalable.current
    val spacing = with(scalable) {
        LocalSpacing.current.scaled
    }
    val state = rememberTvLazyGridState()
    val ids = remember(experimentalMode, lives) {
        if (experimentalMode) lives.map { it.id }
        else emptyList()
    }

    LaunchedEffect(state.isAtTop) {
        isAtTopState.value = state.isAtTop
    }
    EventHandler(scrollUp) {
        state.scrollToItem(0)
    }
    TvLazyVerticalGrid(
        state = state,
        columns = TvGridCells.Fixed(4),
        verticalArrangement = Arrangement.spacedBy(spacing.medium),
        horizontalArrangement = Arrangement.spacedBy(spacing.medium),
        contentPadding = PaddingValues(LocalSpacing.current.medium),
        modifier = modifier.fillMaxSize()
    ) {
        items(
            items = lives,
            key = { it.id },
            contentType = { it.cover.isNullOrEmpty() }
        ) { live ->
            LiveItem(
                live = live,
                noPictureMode = noPictureMode,
                onClick = {
                    if (experimentalMode) {
                        val initialIndex = ids.indexOfFirst { it == live.id }
                        navigateToLivePlayList(ids, initialIndex)
                    } else {
                        navigateToLive(live.id)
                    }
                },
                onLongClick = { onLongClickLive(live) },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun UnsupportedUIModeContent(
    type: Int,
    modifier: Modifier = Modifier,
    description: String? = null,
) {
    val device = remember(type) {
        when (type) {
            UI_MODE_TYPE_NORMAL -> "Normal"
            UI_MODE_TYPE_DESK -> "Desk"
            UI_MODE_TYPE_CAR -> "Car"
            UI_MODE_TYPE_TELEVISION -> "Television"
            UI_MODE_TYPE_APPLIANCE -> "Appliance"
            UI_MODE_TYPE_WATCH -> "Watch"
            UI_MODE_TYPE_VR_HEADSET -> "VR-Headset"
            else -> "Device Type $type"
        }
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(
            LocalSpacing.current.medium,
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FeedPager(
    lives: MappedLives,
    modifier: Modifier = Modifier,
    content: @Composable (List<Live>) -> Unit,
) {
    Column(
        modifier = modifier
    ) {
        val pagerState = rememberPagerState()
        val coroutineScope = rememberCoroutineScope()
        if (lives.size > 1) {
            ScrollableTabRow(
                selectedTabIndex = pagerState.currentPage,
                indicator = { tabPositions ->
                    val index = pagerState.currentPage
                    with(TabRowDefaults) {
                        Modifier.tabIndicatorOffset(
                            currentTabPosition = tabPositions[index]
                        )
                    }
                },
                tabs = {
                    lives.keys.forEachIndexed { index, title ->
                        Tab(
                            text = { Text(title) },
                            selected = pagerState.currentPage == index,
                            onClick = {
                                coroutineScope.launch {
                                    pagerState.scrollToPage(index)
                                }
                            },
                        )
                    }
                },
                divider = {},
                backgroundColor = LocalTheme.current.background,
                contentColor = LocalTheme.current.onBackground,
                modifier = Modifier.fillMaxWidth()
            )
        }
        val entities = remember(lives) { lives.values.toList() }
        HorizontalPager(
            pageCount = entities.size,
            state = pagerState
        ) { pager ->
            content(entities[pager])
        }
    }
}