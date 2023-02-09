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
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.Divider
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
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
import com.m3u.core.util.context.toast
import com.m3u.data.entity.Live
import com.m3u.features.feed.components.LiveItem
import com.m3u.ui.components.Dialog
import com.m3u.ui.model.AppAction
import com.m3u.ui.model.Icon
import com.m3u.ui.model.LocalSpacing
import com.m3u.ui.model.LocalTheme
import com.m3u.ui.model.SetActions
import com.m3u.ui.util.EventHandler
import com.m3u.ui.util.LifecycleEffect

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
        refreshing = state.fetching,
        onSyncingLatest = { viewModel.onEvent(FeedEvent.FetchFeed) },
        navigateToLive = navigateToLive,
        onLiveAction = { dialogState = DialogState.Ready(it) },
        modifier = modifier.fillMaxSize()
    )

    if (dialogState is DialogState.Ready) {
        Dialog(
            title = stringResource(R.string.dialog_favourite_title),
            text = stringResource(R.string.dialog_favourite_content),
            confirm = stringResource(R.string.dialog_favourite_confirm),
            dismiss = stringResource(R.string.dialog_favourite_dismiss),
            onDismissRequest = { dialogState = DialogState.Idle },
            onConfirm = {
                val current = dialogState
                if (current is DialogState.Ready) {
                    viewModel.onEvent(FeedEvent.FavouriteLive(current.id))
                }
                dialogState = DialogState.Idle
            },
            onDismiss = { dialogState = DialogState.Idle }
        )
    }
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
private fun FeedScreen(
    useCommonUIMode: Boolean,
    lives: List<Live>,
    refreshing: Boolean,
    onSyncingLatest: () -> Unit,
    navigateToLive: (Int) -> Unit,
    onLiveAction: (Int) -> Unit,
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
        when (configuration.orientation) {
            ORIENTATION_LANDSCAPE -> {
                LandscapeOrientationContent(
                    lives = lives,
                    navigateToLive = navigateToLive,
                    onLiveAction = onLiveAction,
                    useCommonUIMode = useCommonUIMode,
                    modifier = modifier
                )
            }

            ORIENTATION_PORTRAIT -> {
                PortraitOrientationContent(
                    lives = lives,
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
    }
}

@Composable
private fun LandscapeOrientationContent(
    lives: List<Live>,
    navigateToLive: (Int) -> Unit,
    onLiveAction: (Int) -> Unit,
    useCommonUIMode: Boolean,
    modifier: Modifier = Modifier
) {
    val configuration = LocalConfiguration.current
    val type = configuration.uiMode and UI_MODE_TYPE_MASK
    if (useCommonUIMode || type == UI_MODE_TYPE_NORMAL) {
        LazyVerticalGrid(
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
    navigateToLive: (Int) -> Unit,
    onLiveAction: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val groups = remember(lives) { lives.groupBy { it.group } }
    LazyColumn(
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
    TvLazyVerticalGrid(
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

private sealed class DialogState {
    object Idle : DialogState()
    data class Ready(val id: Int) : DialogState()
}