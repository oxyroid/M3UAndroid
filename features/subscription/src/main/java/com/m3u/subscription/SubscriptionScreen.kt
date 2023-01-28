package com.m3u.subscription

import android.content.res.Configuration
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.m3u.core.icon.Icon
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
import com.m3u.ui.model.AppAction
import com.m3u.ui.util.EventEffect
import com.m3u.ui.util.LifecycleEffect

@Composable
internal fun SubscriptionRoute(
    url: String,
    navigateToLive: (Int, label: String?) -> Unit,
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

    EventEffect(state.message) {
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
    title: String,
    lives: List<Live>,
    refreshing: Boolean,
    onSyncingLatest: () -> Unit,
    navigateToLive: (Int, label: String?) -> Unit,
    onLiveAction: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column {
        var colorState: Boolean by remember {
            mutableStateOf(false)
        }
        val color1 by animateColorAsState(
            if (colorState) PremiumBrushDefaults.color1()
            else LocalTheme.current.topBar,
            animationSpec = tween(LocalDuration.current.slow)
        )
        val color2 by animateColorAsState(
            if (colorState) PremiumBrushDefaults.color2()
            else LocalTheme.current.topBar,
            animationSpec = tween(LocalDuration.current.slow)
        )
        val contentColor by animateColorAsState(
            if (colorState) PremiumBrushDefaults.contentColor()
            else LocalTheme.current.onTopBar
        )
        val duration = LocalDuration.current
        LaunchedEffect(refreshing) {
            delay(duration.medium.toLong())
            colorState = !refreshing
        }
        val configuration = LocalConfiguration.current
        val density = LocalDensity.current.density

        val radius by animateFloatAsState(
            with(configuration) {
                if (colorState) screenWidthDp * density
                else screenWidthDp * density / 5
            },
            animationSpec = tween(duration.extraSlow)
        )
        val offset by animateOffsetAsState(
            with(configuration) {
                if (colorState) Offset(
                    x = screenWidthDp * density / 3 * 2,
                    y = 0f
                ) else Offset(
                    x = screenWidthDp * density / 2,
                    y = screenWidthDp * density
                )
            },
            animationSpec = tween(duration.medium)
        )
        M3URow(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = premiumBrush(
                        color1 = color1,
                        color2 = color2,
                        center = offset,
                        radius = radius
                    )
                )
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.h6,
                color = contentColor
            )
        }

        Divider()

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