package com.m3u.subscription

import android.content.res.Configuration
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.m3u.core.model.Icon
import com.m3u.core.util.toast
import com.m3u.data.entity.Live
import com.m3u.subscription.components.LiveItem
import com.m3u.ui.components.M3UTextButton
import com.m3u.ui.local.LocalSpacing
import com.m3u.ui.local.LocalTheme
import com.m3u.ui.model.AppAction
import com.m3u.ui.util.EventHandler
import com.m3u.ui.util.LifecycleEffect

@Composable
internal fun SubscriptionRoute(
    url: String,
    navigateToLive: (Int) -> Unit,
    setAppActions: (List<AppAction>) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SubscriptionViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val state by viewModel.readable.collectAsStateWithLifecycle()

    val setAppActionsUpdated by rememberUpdatedState(setAppActions)
    LifecycleEffect { event ->
        when (event) {
            Lifecycle.Event.ON_START -> {
                val actions = listOf(
                    AppAction(
                        icon = Icon.ImageVectorIcon(Icons.Rounded.Refresh),
                        contentDescription = "refresh",
                        onClick = {
                            viewModel.onEvent(SubscriptionEvent.SyncingLatest)
                        }
                    )
                )
                setAppActionsUpdated(actions)
            }

            Lifecycle.Event.ON_PAUSE -> {
                setAppActionsUpdated(emptyList())
            }

            else -> {}
        }
    }

    val lives by remember {
        derivedStateOf {
            state.lives
        }
    }
    val refreshing by remember {
        derivedStateOf {
            state.syncing
        }
    }

    var addToFavourite: AddToFavouriteState by remember {
        mutableStateOf(AddToFavouriteState.None)
    }

    EventHandler(state.message) {
        context.toast(it)
    }

    LaunchedEffect(url) {
        viewModel.onEvent(SubscriptionEvent.GetDetails(url))
    }

    SubscriptionScreen(
        lives = lives,
        refreshing = refreshing,
        onSyncingLatest = { viewModel.onEvent(SubscriptionEvent.SyncingLatest) },
        navigateToLive = navigateToLive,
        onLiveAction = { addToFavourite = AddToFavouriteState.Prepared(it) },
        modifier = modifier
    )

    if (addToFavourite is AddToFavouriteState.Prepared) {
        AlertDialog(
            onDismissRequest = { addToFavourite = AddToFavouriteState.None },
            title = {
                Text(
                    text = stringResource(R.string.dialog_favourite_title),
                    style = MaterialTheme.typography.h6,
                    maxLines = 1
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.dialog_favourite_content),
                    style = MaterialTheme.typography.body1
                )
            },
            confirmButton = {
                M3UTextButton(textRes = R.string.dialog_favourite_confirm) {
                    val s = addToFavourite
                    if (s is AddToFavouriteState.Prepared) {
                        viewModel.onEvent(SubscriptionEvent.AddToFavourite(s.id))
                    }
                    addToFavourite = AddToFavouriteState.None
                }
            },
            dismissButton = {
                M3UTextButton(textRes = R.string.dialog_favourite_dismiss) {
                    addToFavourite = AddToFavouriteState.None
                }
            },
            backgroundColor = LocalTheme.current.background,
            contentColor = LocalTheme.current.onBackground,
            modifier = Modifier.padding(LocalSpacing.current.medium)
        )
    }
}

@OptIn(ExperimentalMaterialApi::class, ExperimentalFoundationApi::class)
@Composable
private fun SubscriptionScreen(
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
            Configuration.ORIENTATION_LANDSCAPE -> {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(lives) { live ->
                        LiveItem(
                            live = live,
                            onClick = { navigateToLive(live.id) },
                            onLongClick = { onLiveAction(live.id) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            Configuration.ORIENTATION_PORTRAIT -> {
                val groups = remember(lives) {
                    lives.groupBy { it.group }
                }
                LazyColumn(
                    modifier = modifier.fillMaxSize()
                ) {
                    groups.forEach { (group, lives) ->
                        stickyHeader {
                            Box(
                                modifier = Modifier
                                    .fillParentMaxWidth()
                                    .background(
                                        color = LocalTheme.current.surface
                                    )
                                    .padding(
                                        horizontal = LocalSpacing.current.medium,
                                        vertical = LocalSpacing.current.extraSmall
                                    )
                            ) {
                                Text(
                                    text = group,
                                    color = LocalTheme.current.onSurface,
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

sealed class AddToFavouriteState {
    object None : AddToFavouriteState()
    data class Prepared(val id: Int) : AddToFavouriteState()
}