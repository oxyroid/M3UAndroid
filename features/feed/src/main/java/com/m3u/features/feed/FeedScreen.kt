package com.m3u.features.feed

import android.content.res.Configuration.*
import android.view.KeyEvent
import androidx.annotation.StringRes
import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.foundation.lazy.grid.TvGridCells
import androidx.tv.foundation.lazy.grid.TvLazyVerticalGrid
import androidx.tv.foundation.lazy.grid.items
import androidx.tv.foundation.lazy.grid.rememberTvLazyGridState
import com.m3u.core.util.context.toast
import com.m3u.core.wrapper.Event
import com.m3u.data.entity.Live
import com.m3u.features.feed.components.LiveItem
import com.m3u.ui.components.AlertDialog
import com.m3u.ui.model.*
import com.m3u.ui.util.EventHandler
import com.m3u.ui.util.LifecycleEffect
import com.m3u.ui.util.isAtTop
import kotlinx.coroutines.flow.MutableStateFlow

@Composable
internal fun FeedRoute(
    url: String,
    navigateToLive: (Int) -> Unit,
    setAppActions: SetActions,
    modifier: Modifier = Modifier,
    viewModel: FeedViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val currentSetAppActions by rememberUpdatedState(setAppActions)
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
                currentSetAppActions(actions)
            }

            Lifecycle.Event.ON_PAUSE -> {
                currentSetAppActions(emptyList())
            }

            else -> {}
        }
    }

    var dialogState: DialogState by remember {
        mutableStateOf(DialogState.Idle)
    }

    EventHandler(state.message) {
        context.toast(it)
    }

    LaunchedEffect(url) {
        viewModel.onEvent(FeedEvent.ObserveFeed(url))
    }

    FeedScreen(
        useCommonUIMode = state.useCommonUIMode,
        rowCount = state.rowCount,
        lives = state.lives,
        scrollUp = state.scrollUp,
        refreshing = state.fetching,
        onRowCount = { viewModel.onEvent(FeedEvent.SetRowCount(it)) },
        onSyncingLatest = { viewModel.onEvent(FeedEvent.FetchFeed) },
        navigateToLive = navigateToLive,
        onLiveAction = { dialogState = DialogState.Menu(it) },
        onScrollUp = { viewModel.onEvent(FeedEvent.ScrollUp) },
        modifier = modifier.fillMaxSize()
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

@OptIn(ExperimentalMaterialApi::class)
@Composable
private fun FeedScreen(
    useCommonUIMode: Boolean,
    rowCount: Int,
    lives: List<Live>,
    scrollUp: Event<Unit>,
    refreshing: Boolean,
    onRowCount: (Int) -> Unit,
    onSyncingLatest: () -> Unit,
    navigateToLive: (Int) -> Unit,
    onLiveAction: (Live) -> Unit,
    onScrollUp: () -> Unit,
    modifier: Modifier = Modifier
) {
    val configuration = LocalConfiguration.current
    val state = rememberPullRefreshState(
        refreshing = refreshing,
        onRefresh = onSyncingLatest
    )
    Box(
        modifier = Modifier.pullRefresh(state)
    ) {
        val isAtTopSource = remember { MutableStateFlow(true) }
        when (configuration.orientation) {
            ORIENTATION_LANDSCAPE -> {
                LandscapeOrientationContent(
                    lives = lives,
                    rowCount = rowCount,
                    scrollUp = scrollUp,
                    onRowCount = onRowCount,
                    isAtTopSource = isAtTopSource,
                    navigateToLive = navigateToLive,
                    onLiveAction = onLiveAction,
                    useCommonUIMode = useCommonUIMode,
                    modifier = modifier
                )
            }

            ORIENTATION_PORTRAIT -> {
                PortraitOrientationContent(
                    lives = lives,
                    rowCount = rowCount,
                    scrollUp = scrollUp,
                    onRowCount = onRowCount,
                    isAtTopSource = isAtTopSource,
                    navigateToLive = navigateToLive,
                    onLiveAction = onLiveAction,
                    modifier = modifier
                )
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
        val isAtTop by isAtTopSource.collectAsStateWithLifecycle()
        @OptIn(ExperimentalAnimationApi::class)
        AnimatedVisibility(
            visible = !isAtTop,
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

@Composable
private fun LandscapeOrientationContent(
    useCommonUIMode: Boolean,
    rowCount: Int,
    lives: List<Live>,
    scrollUp: Event<Unit>,
    isAtTopSource: MutableStateFlow<Boolean>,
    navigateToLive: (Int) -> Unit,
    onLiveAction: (Live) -> Unit,
    onRowCount: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val configuration = LocalConfiguration.current
    val type = configuration.uiMode and UI_MODE_TYPE_MASK

    if (useCommonUIMode || type == UI_MODE_TYPE_NORMAL) {
        val state = rememberLazyGridState()
        LaunchedEffect(state.isAtTop) {
            isAtTopSource.emit(state.isAtTop)
        }
        EventHandler(scrollUp) {
            state.scrollToItem(0)
        }
        val requester = remember { FocusRequester() }
        var lastKeyTime by remember {
            mutableStateOf(0L)
        }
        val minDuration = 200L
        @OptIn(ExperimentalFoundationApi::class)
        LazyVerticalGrid(
            state = state,
            columns = GridCells.Fixed(rowCount + 2),
            modifier = modifier
                .onKeyEvent { event ->
                    val currentTimeMillis = System.currentTimeMillis()
                    when (event.nativeKeyEvent.keyCode) {
                        KeyEvent.KEYCODE_VOLUME_UP -> {
                            if (currentTimeMillis - lastKeyTime >= minDuration) {
                                onRowCount((rowCount - 1).coerceAtLeast(1))
                                lastKeyTime = currentTimeMillis
                            }
                            true
                        }
                        KeyEvent.KEYCODE_VOLUME_DOWN -> {
                            if (currentTimeMillis - lastKeyTime >= minDuration) {
                                onRowCount((rowCount + 1).coerceAtMost(3))
                                lastKeyTime = currentTimeMillis
                            }
                            true
                        }
                        else -> false
                    }
                }
                .focusRequester(requester)
                .focusable()
        ) {
            items(lives, key = { it.id }) { live ->
                LiveItem(
                    live = live,
                    onClick = { navigateToLive(live.id) },
                    onLongClick = { onLiveAction(live) },
                    modifier = Modifier
                        .animateItemPlacement()
                        .fillMaxWidth()
                )
            }
        }
        LaunchedEffect(Unit) {
            requester.requestFocus()
        }
    } else {
        when (type) {
            UI_MODE_TYPE_TELEVISION -> {
                TelevisionUIModeContent(
                    lives = lives,
                    isAtTopSource = isAtTopSource,
                    scrollUp = scrollUp,
                    navigateToLive = navigateToLive,
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PortraitOrientationContent(
    lives: List<Live>,
    rowCount: Int,
    scrollUp: Event<Unit>,
    isAtTopSource: MutableStateFlow<Boolean>,
    navigateToLive: (Int) -> Unit,
    onLiveAction: (Live) -> Unit,
    onRowCount: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val state = rememberLazyGridState()
    LaunchedEffect(state.isAtTop) {
        isAtTopSource.emit(state.isAtTop)
    }
    EventHandler(scrollUp) {
        state.scrollToItem(0)
    }
    val requester = remember { FocusRequester() }
    var lastKeyTime by remember {
        mutableStateOf(0L)
    }
    val minDuration = 200L
    LazyVerticalGrid(
        state = state,
        columns = GridCells.Fixed(rowCount),
        modifier = modifier
            .onKeyEvent { event ->
                val currentTimeMillis = System.currentTimeMillis()
                when (event.nativeKeyEvent.keyCode) {
                    KeyEvent.KEYCODE_VOLUME_UP -> {
                        if (currentTimeMillis - lastKeyTime >= minDuration) {
                            onRowCount((rowCount - 1).coerceAtLeast(1))
                            lastKeyTime = currentTimeMillis
                        }
                        true
                    }
                    KeyEvent.KEYCODE_VOLUME_DOWN -> {
                        if (currentTimeMillis - lastKeyTime >= minDuration) {
                            onRowCount((rowCount + 1).coerceAtMost(3))
                            lastKeyTime = currentTimeMillis
                        }
                        true
                    }
                    else -> false
                }
            }
            .focusRequester(requester)
            .focusable()
            .fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(1.dp)
    ) {
        items(lives, key = { live -> live.id }) { live ->
            LiveItem(
                live = live,
                onClick = { navigateToLive(live.id) },
                onLongClick = { onLiveAction(live) },
                modifier = Modifier
                    .animateItemPlacement()
                    .fillMaxWidth()
            )
        }
    }

    LaunchedEffect(Unit) {
        requester.requestFocus()
    }
}

@Suppress("UNREACHABLE_CODE")
@Composable
private fun TelevisionUIModeContent(
    lives: List<Live>,
    isAtTopSource: MutableStateFlow<Boolean>,
    scrollUp: Event<Unit>,
    navigateToLive: (Int) -> Unit,
    onLiveAction: (Live) -> Unit,
    modifier: Modifier = Modifier
) {
    val state = rememberTvLazyGridState()
    LaunchedEffect(state.isAtTop) {
        isAtTopSource.emit(state.isAtTop)
    }
    EventHandler(scrollUp) {
        state.scrollToItem(0)
    }
    @OptIn(ExperimentalFoundationApi::class)
    TvLazyVerticalGrid(
        state = state,
        columns = TvGridCells.Fixed(4),
        modifier = modifier.fillMaxSize()
    ) {
        items(lives, key = { it.id }) { live ->
            LiveItem(
                live = live,
                onClick = { navigateToLive(live.id) },
                onLongClick = { onLiveAction(live) },
                modifier = Modifier
                    .animateItemPlacement()
                    .fillMaxWidth()
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

@Composable
private fun FeedDialog(
    state: DialogState,
    onUpdate: (DialogState) -> Unit,
    onFavorite: (Int, Boolean) -> Unit,
    onMute: (Int, Boolean) -> Unit,
    onSavePicture: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val title = when (state) {
        DialogState.Idle -> ""
        is DialogState.Menu -> ""
        is DialogState.Favorite -> if (state.live.favourite) stringResource(R.string.dialog_favourite_cancel_title)
        else stringResource(R.string.dialog_favourite_title)
        is DialogState.Mute -> stringResource(R.string.dialog_mute_title)
        is DialogState.SavePicture -> ""
    }

    val content = when (state) {
        DialogState.Idle -> ""
        is DialogState.Menu -> ""
        is DialogState.Favorite -> stringResource(R.string.dialog_favourite_content)
        is DialogState.Mute -> stringResource(R.string.dialog_mute_content)
        is DialogState.SavePicture -> ""
    }
    val confirm = when (state) {
        DialogState.Idle -> null
        is DialogState.Menu -> null
        is DialogState.Favorite -> stringResource(R.string.dialog_favourite_confirm)
        is DialogState.Mute -> stringResource(R.string.dialog_mute_confirm)
        is DialogState.SavePicture -> null
    }
    val dismiss = when (state) {
        DialogState.Idle -> null
        is DialogState.Menu -> null
        is DialogState.Favorite -> stringResource(R.string.dialog_favourite_dismiss)
        is DialogState.Mute -> stringResource(R.string.dialog_mute_dismiss)
        is DialogState.SavePicture -> null
    }

    fun onConfirm() = when (state) {
        DialogState.Idle -> {}
        is DialogState.Menu -> {}
        is DialogState.Favorite -> {
            onUpdate(DialogState.Idle)
            onFavorite(state.live.id, !state.live.favourite)
        }
        is DialogState.Mute -> {
            onUpdate(DialogState.Idle)
            // TODO
            onMute(state.live.id, true)
        }
        is DialogState.SavePicture -> {}
    }

    fun onDismiss() = when (state) {
        DialogState.Idle -> {}
        is DialogState.Menu -> onUpdate(DialogState.Idle)
        is DialogState.Favorite -> onUpdate(DialogState.Menu(state.live))
        is DialogState.Mute -> onUpdate(DialogState.Menu(state.live))
        is DialogState.SavePicture -> {}
    }

    when (state) {
        DialogState.Idle -> {}
        is DialogState.Menu -> {
            Dialog(
                onDismissRequest = { onUpdate(DialogState.Idle) }
            ) {
                Card(
                    backgroundColor = LocalTheme.current.background,
                    contentColor = LocalTheme.current.onBackground,
                    modifier = modifier
                ) {
                    Column {
                        MenuItem(
                            titleResId = if (state.live.favourite) R.string.dialog_favourite_cancel_title
                            else R.string.dialog_favourite_title,
                            onUpdate = {
                                if (state.live.favourite) {
                                    onUpdate(DialogState.Idle)
                                    onFavorite(state.live.id, false)
                                } else {
                                    onUpdate(DialogState.Favorite(state.live))
                                }
                            },
                        )
                        MenuItem(
                            titleResId = R.string.dialog_mute_title,
                            onUpdate = { onUpdate(DialogState.Mute(state.live)) },
                        )

                        MenuItem(
                            titleResId = R.string.dialog_save_picture_title,
                            onUpdate = {
                                onUpdate(DialogState.Idle)
                                onSavePicture(state.live.id)
                            }
                        )
                    }
                }
            }
        }
        else -> {
            AlertDialog(
                title = title,
                text = content,
                confirm = confirm,
                dismiss = dismiss,
                onDismissRequest = { onUpdate(DialogState.Idle) },
                onConfirm = ::onConfirm,
                onDismiss = ::onDismiss,
                modifier = modifier
            )
        }
    }
}

private sealed class DialogState {
    object Idle : DialogState()
    data class Menu(val live: Live) : DialogState()
    data class Favorite(val live: Live) : DialogState()
    data class Mute(val live: Live) : DialogState()
    data class SavePicture(val live: Live) : DialogState()
}

@Composable
private fun MenuItem(
    @StringRes titleResId: Int,
    onUpdate: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onUpdate)
            .padding(LocalSpacing.current.medium)
    ) {
        Text(
            text = stringResource(titleResId),
            style = MaterialTheme.typography.subtitle1,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontWeight = FontWeight.Bold
        )
    }
}