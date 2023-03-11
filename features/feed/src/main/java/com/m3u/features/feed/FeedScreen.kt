package com.m3u.features.feed

import android.content.res.Configuration.*
import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.foundation.lazy.grid.TvGridCells
import androidx.tv.foundation.lazy.grid.TvLazyVerticalGrid
import androidx.tv.foundation.lazy.grid.items
import androidx.tv.foundation.lazy.grid.rememberTvLazyGridState
import com.google.accompanist.pager.ExperimentalPagerApi
import com.google.accompanist.pager.HorizontalPager
import com.google.accompanist.pager.pagerTabIndicatorOffset
import com.google.accompanist.pager.rememberPagerState
import com.m3u.core.util.basic.uppercaseFirst
import com.m3u.core.util.context.toast
import com.m3u.core.wrapper.Event
import com.m3u.data.local.entity.Live
import com.m3u.features.feed.components.DialogState
import com.m3u.features.feed.components.FeedDialog
import com.m3u.features.feed.components.LiveItem
import com.m3u.ui.components.Background
import com.m3u.ui.components.TextField
import com.m3u.ui.model.*
import com.m3u.ui.util.EventHandler
import com.m3u.ui.util.LifecycleEffect
import com.m3u.ui.util.interceptVolumeEvent
import com.m3u.ui.util.isAtTop
import kotlinx.coroutines.launch

@Composable
internal fun FeedRoute(
    url: String,
    navigateToLive: (Int) -> Unit,
    navigateToLivePlayList: (List<Int>, Int) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: FeedViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val utils = LocalUtils.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    var dialogState: DialogState by remember { mutableStateOf(DialogState.Idle) }
    LifecycleEffect { event ->
        when (event) {
            Lifecycle.Event.ON_START -> {
                val actions = listOf(
                    AppAction(
                        icon = Icon.ImageVectorIcon(Icons.Rounded.Refresh),
                        contentDescription = "refresh",
                        onClick = {
                            viewModel.onEvent(FeedEvent.FetchFeed)
                        }
                    )
                )
                utils.setActions(actions)
            }

            Lifecycle.Event.ON_PAUSE -> {
                utils.setTitle()
                utils.setActions()
            }

            else -> {}
        }
    }
    EventHandler(state.message) {
        context.toast(it)
    }
    LaunchedEffect(url) {
        viewModel.onEvent(FeedEvent.ObserveFeed(url))
    }
    LaunchedEffect(state.title) {
        utils.setTitle(state.title)
    }
    val rowCount = state.rowCount
    fun onRowCount(target: Int) {
        viewModel.onEvent(FeedEvent.SetRowCount(target))
    }
    BackHandler(state.query.isNotEmpty()) {
        viewModel.onEvent(FeedEvent.OnQuery(""))
    }
    val interceptVolumeEventModifier = if (state.editMode) {
        Modifier.interceptVolumeEvent { event ->
            when (event) {
                KeyEvent.KEYCODE_VOLUME_UP -> onRowCount((rowCount - 1).coerceAtLeast(1))
                KeyEvent.KEYCODE_VOLUME_DOWN -> onRowCount((rowCount + 1).coerceAtMost(3))
            }
        }
    } else Modifier

    FeedScreen(
        query = state.query,
        onQuery = { viewModel.onEvent(FeedEvent.OnQuery(it)) },
        useCommonUIMode = state.useCommonUIMode,
        scrollMode = state.scrollMode,
        rowCount = rowCount,
        lives = state.lives,
        scrollUp = state.scrollUp,
        refreshing = state.fetching,
        onSyncingLatest = { viewModel.onEvent(FeedEvent.FetchFeed) },
        navigateToLive = navigateToLive,
        navigateToLivePlayList = navigateToLivePlayList,
        onLiveAction = { dialogState = DialogState.Menu(it) },
        onScrollUp = { viewModel.onEvent(FeedEvent.ScrollUp) },
        modifier = modifier
            .fillMaxSize()
            .then(interceptVolumeEventModifier)
    )

    FeedDialog(
        state = dialogState,
        onUpdate = { dialogState = it },
        onFavorite = { id, target -> viewModel.onEvent(FeedEvent.FavouriteLive(id, target)) },
        onMute = { id, target -> viewModel.onEvent(FeedEvent.MuteLive(id, target)) },
        onSavePicture = { viewModel.onEvent(FeedEvent.SavePicture(it)) },
        modifier = Modifier.padding(LocalSpacing.current.medium)
    )
}

@OptIn(ExperimentalMaterialApi::class, ExperimentalAnimationApi::class)
@Composable
private fun FeedScreen(
    query: String,
    onQuery: (String) -> Unit,
    useCommonUIMode: Boolean,
    scrollMode: Boolean,
    rowCount: Int,
    lives: Map<String, List<Live>>,
    scrollUp: Event<Unit>,
    refreshing: Boolean,
    onSyncingLatest: () -> Unit,
    navigateToLive: (Int) -> Unit,
    navigateToLivePlayList: (List<Int>, Int) -> Unit,
    onLiveAction: (Live) -> Unit,
    onScrollUp: () -> Unit,
    modifier: Modifier = Modifier
) {
    val configuration = LocalConfiguration.current
    val state = rememberPullRefreshState(
        refreshing = refreshing,
        onRefresh = onSyncingLatest
    )
    Background {
        Column {
            TextField(
                text = query,
                onValueChange = onQuery,
                height = 32.dp,
                placeholder = stringResource(R.string.query_placeholder).uppercaseFirst(),
                modifier = Modifier
                    .padding(LocalSpacing.current.medium,)
                    .fillMaxWidth()
            )
            Box(
                modifier = Modifier.pullRefresh(state)
            ) {
                val isAtTopState = remember { mutableStateOf(true) }
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
                                rowCount = rowCount,
                                isAtTopState = isAtTopState,
                                scrollUp = scrollUp,
                                navigateToLive = navigateToLive,
                                navigateToLivePlayList = navigateToLivePlayList,
                                onLiveAction = onLiveAction,
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
                                rowCount = rowCount,
                                isAtTopState = isAtTopState,
                                scrollUp = scrollUp,
                                navigateToLive = navigateToLive,
                                navigateToLivePlayList = navigateToLivePlayList,
                                onLiveAction = onLiveAction,
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
                    contentColor = LocalTheme.current.onTint,
                    backgroundColor = LocalTheme.current.tint
                )
                this@Column.AnimatedVisibility(
                    visible = !isAtTopState.value,
                    enter = scaleIn(),
                    exit = scaleOut(),
                    modifier = Modifier
                        .padding(LocalSpacing.current.medium)
                        .align(Alignment.BottomEnd)
                ) {
                    FloatingActionButton(
                        onClick = onScrollUp,
                        backgroundColor = LocalTheme.current.tint,
                        contentColor = LocalTheme.current.onTint
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ArrowUpward,
                            contentDescription = null
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LandscapeOrientationContent(
    useCommonUIMode: Boolean,
    scrollMode: Boolean,
    rowCount: Int,
    lives: List<Live>,
    scrollUp: Event<Unit>,
    isAtTopState: MutableState<Boolean>,
    navigateToLive: (Int) -> Unit,
    navigateToLivePlayList: (List<Int>, Int) -> Unit,
    onLiveAction: (Live) -> Unit,
    modifier: Modifier = Modifier
) {
    val configuration = LocalConfiguration.current
    val type = configuration.uiMode and UI_MODE_TYPE_MASK
    val ids = remember(scrollMode, lives) {
        if (scrollMode) lives.map { it.id }
        else emptyList()
    }

    Column {
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
                modifier = modifier.fillMaxSize()
            ) {
                items(
                    items = lives,
                    key = { it.id }
                ) { live ->
                    LiveItem(
                        live = live,
                        onClick = {
                            if (scrollMode) {
                                val initialIndex = ids.indexOfFirst { it == live.id }
                                navigateToLivePlayList(ids, initialIndex)
                            } else {
                                navigateToLive(live.id)
                            }
                        },
                        onLongClick = { onLiveAction(live) },
                        modifier = Modifier.fillMaxWidth(),
                        scaleTime = rowCount
                    )
                }
            }
        } else {
            when (type) {
                UI_MODE_TYPE_TELEVISION -> {
                    TelevisionUIModeContent(
                        lives = lives,
                        experimentalMode = scrollMode,
                        isAtTopState = isAtTopState,
                        scrollUp = scrollUp,
                        navigateToLive = navigateToLive,
                        navigateToLivePlayList = navigateToLivePlayList,
                        onLiveAction = onLiveAction,
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
}

@Composable
private fun PortraitOrientationContent(
    lives: List<Live>,
    scrollMode: Boolean,
    rowCount: Int,
    scrollUp: Event<Unit>,
    isAtTopState: MutableState<Boolean>,
    navigateToLive: (Int) -> Unit,
    navigateToLivePlayList: (List<Int>, Int) -> Unit,
    onLiveAction: (Live) -> Unit,
    modifier: Modifier = Modifier
) {
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
        verticalArrangement = Arrangement.spacedBy(1.dp),
        modifier = modifier.fillMaxSize()
    ) {
        items(
            items = lives,
            key = { live -> live.id }
        ) { live ->
            LiveItem(
                live = live,
                onClick = {
                    if (scrollMode) {
                        val initialIndex = ids.indexOfFirst { it == live.id }
                        navigateToLivePlayList(ids, initialIndex)
                    } else {
                        navigateToLive(live.id)
                    }
                },
                onLongClick = { onLiveAction(live) },
                modifier = Modifier.fillMaxWidth(),
                scaleTime = rowCount
            )
        }
    }
}

@Composable
private fun TelevisionUIModeContent(
    lives: List<Live>,
    experimentalMode: Boolean,
    isAtTopState: MutableState<Boolean>,
    scrollUp: Event<Unit>,
    navigateToLive: (Int) -> Unit,
    navigateToLivePlayList: (List<Int>, Int) -> Unit,
    onLiveAction: (Live) -> Unit,
    modifier: Modifier = Modifier
) {
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
        modifier = modifier.fillMaxSize()
    ) {
        items(
            items = lives,
            key = { it.id }
        ) { live ->
            LiveItem(
                live = live,
                onClick = {
                    if (experimentalMode) {
                        val initialIndex = ids.indexOfFirst { it == live.id }
                        navigateToLivePlayList(ids, initialIndex)
                    } else {
                        navigateToLive(live.id)
                    }
                },
                onLongClick = { onLiveAction(live) },
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
        Text(text = "Unsupported UI Mode: $device")
        if (description != null) {
            Text(text = description)
        }
    }
}

@OptIn(ExperimentalPagerApi::class)
@Composable
private fun FeedPager(
    lives: Map<String, List<Live>>,
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
                    TabRowDefaults.Indicator(
                        Modifier.pagerTabIndicatorOffset(pagerState, tabPositions)
                    )
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
        val c = remember(lives) { lives.values.toList() }
        HorizontalPager(
            count = c.size,
            state = pagerState
        ) { pager ->
            content(c[pager])
        }
    }
}