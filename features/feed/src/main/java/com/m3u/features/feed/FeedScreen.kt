package com.m3u.features.feed

import android.content.res.Configuration.*
import androidx.annotation.StringRes
import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
        lives = state.lives,
        scrollUp = state.scrollUp,
        refreshing = state.fetching,
        onSyncingLatest = { viewModel.onEvent(FeedEvent.FetchFeed) },
        navigateToLive = navigateToLive,
        onLiveAction = { dialogState = DialogState.Menu(it) },
        onScrollUp = { viewModel.onEvent(FeedEvent.ScrollUp) },
        modifier = modifier.fillMaxSize()
    )

    FeedDialog(
        state = dialogState,
        onUpdate = { dialogState = it },
        onFavorite = { viewModel.onEvent(FeedEvent.FavouriteLive(it)) },
        onMute = { viewModel.onEvent(FeedEvent.MuteLive(it)) }
    )
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
private fun FeedScreen(
    useCommonUIMode: Boolean,
    lives: List<Live>,
    scrollUp: Event<Unit>,
    refreshing: Boolean,
    onSyncingLatest: () -> Unit,
    navigateToLive: (Int) -> Unit,
    onLiveAction: (Int) -> Unit,
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
                    scrollUp = scrollUp,
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
                    scrollUp = scrollUp,
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
    lives: List<Live>,
    scrollUp: Event<Unit>,
    isAtTopSource: MutableStateFlow<Boolean>,
    navigateToLive: (Int) -> Unit,
    onLiveAction: (Int) -> Unit,
    useCommonUIMode: Boolean,
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
        LazyVerticalGrid(
            state = state,
            columns = GridCells.Fixed(4),
            modifier = modifier.fillMaxSize()
        ) {
            items(lives) { live ->
                LiveItem(
                    live = live.copy(
                        title = "${live.group} - ${live.title}"
                    ),
                    onClick = { navigateToLive(live.id) },
                    onLongClick = { onLiveAction(live.id) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
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
    scrollUp: Event<Unit>,
    isAtTopSource: MutableStateFlow<Boolean>,
    navigateToLive: (Int) -> Unit,
    onLiveAction: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val state = rememberLazyListState()
    LaunchedEffect(state.isAtTop) {
        isAtTopSource.emit(state.isAtTop)
    }
    val groups = remember(lives) { lives.groupBy { it.group } }
    EventHandler(scrollUp) {
        state.scrollToItem(0)
    }
    LazyColumn(
        state = state,
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(1.dp)
    ) {
        groups.forEach { (group, lives) ->
            stickyHeader {
                Box(
                    modifier = Modifier
                        .fillParentMaxWidth()
                        .background(
                            color = LocalTheme.current.topBarDisable
                        )
                        .padding(
                            horizontal = LocalSpacing.current.medium,
                            vertical = LocalSpacing.current.extraSmall
                        )
                ) {
                    Text(
                        text = group,
                        color = LocalTheme.current.onTopBarDisable,
                        style = MaterialTheme.typography.subtitle2
                    )
                }
            }
            itemsIndexed(lives) { index, live ->
                LiveItem(
                    live = live,
                    onClick = { navigateToLive(live.id) },
                    onLongClick = { onLiveAction(live.id) },
                    modifier = Modifier.fillParentMaxWidth()
                )
                if (index == lives.lastIndex) {
                    Divider(
                        modifier = Modifier.height(LocalSpacing.current.extraSmall)
                    )
                }
            }
        }
    }
}

@Suppress("UNREACHABLE_CODE")
@Composable
private fun TelevisionUIModeContent(
    lives: List<Live>,
    isAtTopSource: MutableStateFlow<Boolean>,
    scrollUp: Event<Unit>,
    navigateToLive: (Int) -> Unit,
    onLiveAction: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    UnsupportedUIModeContent(
        type = UI_MODE_TYPE_TELEVISION,
        modifier = modifier,
        description =
        "Fix when [https://issuetracker.google.com/issues/267058478] is completed."
    )
    // FIXME: https://issuetracker.google.com/issues/267058478
    return
    val state = rememberTvLazyGridState()
    LaunchedEffect(state.isAtTop) {
        isAtTopSource.emit(state.isAtTop)
    }
    EventHandler(scrollUp) {
        state.scrollToItem(0)
    }
    TvLazyVerticalGrid(
        state = state,
        columns = TvGridCells.Fixed(4),
        modifier = modifier.fillMaxSize()
    ) {
        items(lives) { live ->
            LiveItem(
                live = live.copy(
                    title = "${live.group} - ${live.title}"
                ),
                onClick = { navigateToLive(live.id) },
                onLongClick = { onLiveAction(live.id) },
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

@Composable
private fun FeedDialog(
    state: DialogState,
    onUpdate: (DialogState) -> Unit,
    onFavorite: (Int) -> Unit,
    onMute: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val title = when (state) {
        DialogState.Idle -> ""
        is DialogState.Menu -> ""
        is DialogState.Favorite -> stringResource(R.string.dialog_favourite_title)
        is DialogState.Mute -> stringResource(R.string.dialog_mute_title)
    }

    val content = when (state) {
        DialogState.Idle -> ""
        is DialogState.Menu -> ""
        is DialogState.Favorite -> stringResource(R.string.dialog_favourite_content)
        is DialogState.Mute -> stringResource(R.string.dialog_mute_content)
    }
    val confirm = when (state) {
        DialogState.Idle -> null
        is DialogState.Menu -> null
        is DialogState.Favorite -> stringResource(R.string.dialog_favourite_confirm)
        is DialogState.Mute -> stringResource(R.string.dialog_mute_confirm)
    }
    val dismiss = when (state) {
        DialogState.Idle -> null
        is DialogState.Menu -> stringResource(R.string.dialog_menu_dismiss)
        is DialogState.Favorite -> stringResource(R.string.dialog_favourite_dismiss)
        is DialogState.Mute -> stringResource(R.string.dialog_mute_dismiss)
    }

    fun onConfirm() = when (state) {
        DialogState.Idle -> {}
        is DialogState.Menu -> {}
        is DialogState.Favorite -> {
            onUpdate(DialogState.Idle)
            onFavorite(state.id)
        }
        is DialogState.Mute -> {
            onUpdate(DialogState.Idle)
            onMute(state.id)
        }
    }

    fun onDismiss() = when (state) {
        DialogState.Idle -> {}
        is DialogState.Menu -> onUpdate(DialogState.Idle)
        is DialogState.Favorite -> onUpdate(DialogState.Menu(state.id))
        is DialogState.Mute -> onUpdate(DialogState.Menu(state.id))
    }

    when (state) {
        DialogState.Idle -> {}
        is DialogState.Menu -> {
            Dialog(
                onDismissRequest = { onUpdate(DialogState.Idle) }
            ) {
                Card(
                    backgroundColor = LocalTheme.current.background,
                    contentColor = LocalTheme.current.onBackground
                ) {
                    Column {
                        MenuItem(
                            titleResId = R.string.dialog_favourite_title,
                            onUpdate = { onUpdate(DialogState.Favorite(state.id)) },
                        )
                        MenuItem(
                            titleResId = R.string.dialog_mute_title,
                            onUpdate = { onUpdate(DialogState.Mute(state.id)) },
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
    data class Menu(val id: Int) : DialogState()
    data class Favorite(val id: Int) : DialogState()
    data class Mute(val id: Int) : DialogState()
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
        Text(stringResource(titleResId))
    }
}