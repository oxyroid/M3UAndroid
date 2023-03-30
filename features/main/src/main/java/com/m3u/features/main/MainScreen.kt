package com.m3u.features.main

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.m3u.core.util.context.toast
import com.m3u.data.local.entity.Feed
import com.m3u.features.main.components.FeedItem
import com.m3u.features.main.model.FeedDetail
import com.m3u.ui.components.SheetDialog
import com.m3u.ui.components.SheetItem
import com.m3u.ui.components.SheetTitle
import com.m3u.ui.model.LocalHelper
import com.m3u.ui.model.LocalSpacing
import com.m3u.ui.util.EventHandler
import com.m3u.ui.util.RepeatOnCreate

private typealias ShowFeedBottomSheet = (Feed) -> Unit
private typealias UnsubscribeFeedByUrl = (String) -> Unit
typealias NavigateToFeed = (url: String) -> Unit

@Composable
fun MainRoute(
    navigateToFeed: NavigateToFeed,
    modifier: Modifier = Modifier,
    viewModel: MainViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val helper = LocalHelper.current
    val state: MainState by viewModel.state.collectAsStateWithLifecycle()
    val feeds: List<FeedDetail> = state.feeds

    EventHandler(state.message) {
        context.toast(it)
    }
    RepeatOnCreate {
        helper.actions()
    }
    MainScreen(
        modifier = modifier,
        feeds = feeds,
        navigateToFeed = navigateToFeed,
        unsubscribeFeedByUrl = { viewModel.onEvent(MainEvent.UnsubscribeFeedByUrl(it)) }
    )
}

@Composable
private fun MainScreen(
    feeds: List<FeedDetail>,
    navigateToFeed: NavigateToFeed,
    unsubscribeFeedByUrl: UnsubscribeFeedByUrl,
    modifier: Modifier = Modifier
) {
    var feedSheetState: FeedSheetState by remember { mutableStateOf(FeedSheetState.Idle) }
    val configuration = LocalConfiguration.current
    when (configuration.orientation) {
        Configuration.ORIENTATION_PORTRAIT -> {
            PortraitOrientationContent(
                feeds = feeds,
                navigateToFeed = navigateToFeed,
                showFeedBottomSheet = { feedSheetState = FeedSheetState.Existed(it) },
                modifier = modifier
            )
        }

        Configuration.ORIENTATION_LANDSCAPE -> {
            LandscapeOrientationContent(
                feeds = feeds,
                navigateToFeed = navigateToFeed,
                showFeedBottomSheet = { feedSheetState = FeedSheetState.Existed(it) },
                modifier = modifier
            )
        }
        else -> {}
    }
    SheetDialog(
        visible = feedSheetState is FeedSheetState.Existed,
        onDismiss = { feedSheetState = FeedSheetState.Idle },
        verticalArrangement = Arrangement.spacedBy(LocalSpacing.current.medium),
        content = {
            var state = remember { feedSheetState as FeedSheetState.Existed }
            LaunchedEffect(feedSheetState) {
                if (feedSheetState is FeedSheetState.Existed) {
                    state = feedSheetState as FeedSheetState.Existed
                }
            }
            SheetTitle(state.feed.title)
            SheetItem(
                text = stringResource(R.string.unsubscribe_feed),
                onClick = {
                    unsubscribeFeedByUrl(state.feed.url)
                    feedSheetState = FeedSheetState.Idle
                },
                modifier = Modifier.fillMaxWidth()
            )

            val clipboardManager = LocalClipboardManager.current
            SheetItem(
                text = stringResource(R.string.copy_feed_url),
                onClick = {
                    val annotatedString = AnnotatedString(state.feed.url)
                    clipboardManager.setText(annotatedString)
                    feedSheetState = FeedSheetState.Idle
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
    )
    BackHandler(feedSheetState is FeedSheetState.Existed) {
        feedSheetState = FeedSheetState.Idle
    }

}

@Composable
fun PortraitOrientationContent(
    feeds: List<FeedDetail>,
    navigateToFeed: NavigateToFeed,
    showFeedBottomSheet: ShowFeedBottomSheet,
    modifier: Modifier = Modifier
) {
    val spacing = LocalSpacing.current
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(spacing.medium),
        verticalArrangement = Arrangement.spacedBy(spacing.small)
    ) {
        items(
            items = feeds,
            key = { it.feed.url }
        ) { detail ->
            FeedItem(
                label = detail.feed.title,
                number = detail.count,
                modifier = Modifier.fillParentMaxWidth(),
                onClick = {
                    navigateToFeed(detail.feed.url)
                },
                onLongClick = {
                    showFeedBottomSheet(detail.feed)
                }
            )
        }
    }
}

@Composable
private fun LandscapeOrientationContent(
    feeds: List<FeedDetail>,
    navigateToFeed: NavigateToFeed,
    showFeedBottomSheet: ShowFeedBottomSheet,
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
        items(
            items = feeds,
            key = { it.feed.url }
        ) { detail ->
            FeedItem(
                label = detail.feed.title,
                number = detail.count,
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    navigateToFeed(detail.feed.url)
                },
                onLongClick = {
                    showFeedBottomSheet(detail.feed)
                }
            )
        }
    }
}

private sealed class FeedSheetState {
    object Idle : FeedSheetState()
    data class Existed(val feed: Feed) : FeedSheetState()
}