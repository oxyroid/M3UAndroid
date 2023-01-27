package com.m3u.subscription

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.AlertDialog
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.m3u.core.util.toast
import com.m3u.data.entity.Live
import com.m3u.subscription.components.LiveItem
import com.m3u.ui.components.basic.M3URow
import com.m3u.ui.components.basic.M3UTextButton
import com.m3u.ui.components.basic.PremiumBrushDefaults
import com.m3u.ui.components.basic.premiumBrush
import com.m3u.ui.local.LocalDuration
import com.m3u.ui.local.LocalSpacing
import com.m3u.ui.local.LocalTheme
import com.m3u.ui.util.EventEffect

sealed class AddToFavouriteState {
    object None : AddToFavouriteState()
    data class Prepared(val id: Int) : AddToFavouriteState()
}

@Composable
internal fun SubscriptionRoute(
    url: String,
    navigateToLive: (Int) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SubscriptionViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val state by viewModel.readable.collectAsStateWithLifecycle()

    val title = state.title
    val lives = state.lives

    val refreshing = state.syncing

    var addToFavouriteState: AddToFavouriteState by remember {
        mutableStateOf(AddToFavouriteState.None)
    }

    EventEffect(state.message) {
        context.toast(it)
    }

    LaunchedEffect(url) {
        viewModel.onEvent(SubscriptionEvent.GetDetails(url))
    }

    SubscriptionScreen(
        title = title,
        lives = lives,
        refreshing = refreshing,
        onRefresh = { viewModel.onEvent(SubscriptionEvent.SyncingLatest) },
        navigateToLive = navigateToLive,
        onLiveAction = {
            addToFavouriteState = AddToFavouriteState.Prepared(it)
        },
        modifier = modifier
    )

    if (addToFavouriteState is AddToFavouriteState.Prepared) {
        AlertDialog(
            onDismissRequest = { addToFavouriteState = AddToFavouriteState.None },
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
                    val s = addToFavouriteState
                    if (s is AddToFavouriteState.Prepared) {
                        viewModel.onEvent(SubscriptionEvent.AddToFavourite(s.id))
                    }
                    addToFavouriteState = AddToFavouriteState.None
                }
            },
            dismissButton = {
                M3UTextButton(textRes = R.string.dialog_favourite_dismiss) {
                    addToFavouriteState = AddToFavouriteState.None
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
    title: String,
    lives: List<Live>,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    navigateToLive: (Int) -> Unit,
    onLiveAction: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column {
        var colorState: Boolean by remember {
            mutableStateOf(false)
        }
        val color1 by animateColorAsState(
            if (colorState) PremiumBrushDefaults.color1
            else LocalTheme.current.topBar,
            animationSpec = tween(LocalDuration.current.slow)
        )
        val color2 by animateColorAsState(
            if (colorState) PremiumBrushDefaults.color2
            else LocalTheme.current.topBar,
            animationSpec = tween(LocalDuration.current.slow)
        )
        val contentColor by animateColorAsState(
            if (colorState) Color.White
            else LocalTheme.current.onTopBar
        )
        LaunchedEffect(refreshing) {
            colorState = !refreshing
        }
        M3URow(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = premiumBrush(
                        color1 = color1,
                        color2 = color2
                    )
                )
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.h6,
                color = contentColor
            )
        }

        val state = rememberPullRefreshState(
            refreshing = refreshing,
            onRefresh = onRefresh
        )

        Box(
            modifier = Modifier.pullRefresh(state)
        ) {
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
                    items(lives) { live ->
                        LiveItem(
                            live = live,
                            onClick = { navigateToLive(live.id) },
                            onLongClick = { onLiveAction(live.id) },
                            modifier = Modifier.fillParentMaxWidth()
                        )
                    }
                }

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
}