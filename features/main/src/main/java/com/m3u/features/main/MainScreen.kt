package com.m3u.features.main

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.m3u.core.util.context.toast
import com.m3u.features.main.components.FeedItem
import com.m3u.features.main.model.FeedDetail
import com.m3u.features.main.navgation.NavigateToFeed
import com.m3u.ui.model.LocalSpacing
import com.m3u.ui.model.LocalUtils
import com.m3u.ui.model.SpecialNavigationParam
import com.m3u.ui.util.EventHandler
import com.m3u.ui.util.LifecycleEffect

@Composable
internal fun MainRoute(
    navigateToFeed: NavigateToFeed,
    modifier: Modifier = Modifier,
    viewModel: MainViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val utils = LocalUtils.current
    val state: MainState by viewModel.state.collectAsStateWithLifecycle()
    val feeds: List<FeedDetail> = state.feeds
    val mutedFeed: FeedDetail? = state.mutedFeed

    EventHandler(state.message) {
        context.toast(it)
    }
    LifecycleEffect { event ->
        when (event) {
            Lifecycle.Event.ON_START -> {
                utils.setActions()
            }

            Lifecycle.Event.ON_PAUSE -> {
                utils.setActions()
            }

            else -> {}
        }
    }

    MainScreen(
        modifier = modifier,
        feeds = feeds,
        mutedFeed = mutedFeed,
        navigateToFeed = navigateToFeed
    )
}

@Composable
private fun MainScreen(
    feeds: List<FeedDetail>,
    mutedFeed: FeedDetail?,
    navigateToFeed: NavigateToFeed,
    modifier: Modifier = Modifier
) {
    val configuration = LocalConfiguration.current
    when (configuration.orientation) {
        Configuration.ORIENTATION_PORTRAIT -> {
            PortraitOrientationContent(
                feeds = feeds,
                mutedFeed = mutedFeed,
                navigateToFeed = navigateToFeed,
                modifier = modifier
            )
        }

        Configuration.ORIENTATION_LANDSCAPE -> {
            LandscapeOrientationContent(
                feeds = feeds,
                mutedFeed = mutedFeed,
                navigateToFeed = navigateToFeed,
                modifier = modifier
            )
        }
        else -> {}
    }
}

@Composable
fun PortraitOrientationContent(
    feeds: List<FeedDetail>,
    mutedFeed: FeedDetail?,
    navigateToFeed: NavigateToFeed,
    modifier: Modifier = Modifier
) {
    val spacing = LocalSpacing.current
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(spacing.medium),
        verticalArrangement = Arrangement.spacedBy(spacing.small)
    ) {
        items(feeds) { detail ->
            FeedItem(
                label = detail.feed.title,
                number = detail.count,
                modifier = Modifier.fillParentMaxWidth(),
                onClick = {
                    navigateToFeed(detail.feed.url)
                }
            )
        }
        if (mutedFeed != null) {
            item {
                val title = stringResource(R.string.muted_lives_feed)
                FeedItem(
                    label = title,
                    number = mutedFeed.count,
                    onClick = {
                        navigateToFeed(SpecialNavigationParam.FEED_MUTED_LIVES_URL)
                    }
                )
            }
        }
    }
}

@Composable
private fun LandscapeOrientationContent(
    feeds: List<FeedDetail>,
    mutedFeed: FeedDetail?,
    navigateToFeed: NavigateToFeed,
    modifier: Modifier = Modifier
) {
    val spacing = LocalSpacing.current
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        contentPadding = PaddingValues(spacing.medium),
        verticalArrangement = Arrangement.spacedBy(spacing.medium),
        horizontalArrangement = Arrangement.spacedBy(spacing.medium),
        modifier = modifier.fillMaxSize()
    ) {
        items(feeds) { detail ->
            FeedItem(
                label = detail.feed.title,
                number = detail.count,
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    navigateToFeed(detail.feed.url)
                }
            )
        }
        if (mutedFeed != null) {
            item {
                FeedItem(
                    label = stringResource(R.string.muted_lives_feed),
                    number = mutedFeed.count,
                    onClick = { /*TODO*/ }
                )
            }
        }
    }
}
