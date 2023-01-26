package com.m3u.subscription

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.*
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.m3u.core.util.toast
import com.m3u.data.entity.Live
import com.m3u.subscription.components.LiveItem
import com.m3u.ui.components.basic.M3URow
import com.m3u.ui.components.basic.premiumBrush
import com.m3u.ui.local.LocalTheme
import com.m3u.ui.util.EventEffect

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
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
private fun SubscriptionScreen(
    title: String,
    lives: List<Live>,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    navigateToLive: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column {
        M3URow(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = premiumBrush()
                )
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.h6,
                color = Color.White
            )
        }

        val state = rememberPullRefreshState(
            refreshing = refreshing,
            onRefresh = onRefresh
        )

        Box(
            modifier = Modifier.pullRefresh(state)
        ) {
            LazyColumn(
                modifier = modifier
                    .fillMaxSize()
            ) {
                items(lives) { live ->
                    LiveItem(
                        live = live,
                        onClick = { navigateToLive(live.id) },
                        modifier = Modifier.fillParentMaxWidth()
                    )
                }
            }
            PullRefreshIndicator(
                refreshing = refreshing,
                state = state,
                modifier = Modifier.align(Alignment.TopCenter),
                scale = true,
                contentColor = LocalTheme.current.tint
            )
        }
    }
}