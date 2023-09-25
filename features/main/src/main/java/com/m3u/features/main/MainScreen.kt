package com.m3u.features.main

import android.content.res.Configuration
import android.view.KeyEvent
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.m3u.data.database.entity.Feed
import com.m3u.features.main.components.FeedItem
import com.m3u.features.main.components.MainDialog
import com.m3u.features.main.components.OnRename
import com.m3u.features.main.components.OnUnsubscribe
import com.m3u.features.main.model.FeedDetail
import com.m3u.ui.model.LocalHelper
import com.m3u.ui.model.LocalScalable
import com.m3u.ui.model.LocalSpacing
import com.m3u.ui.model.Scalable
import com.m3u.ui.ktx.interceptVolumeEvent

private typealias showDialog = (Feed) -> Unit
typealias NavigateToFeed = (feed: Feed) -> Unit

@Composable
fun MainRoute(
    navigateToFeed: NavigateToFeed,
    isCurrentPage: Boolean,
    modifier: Modifier = Modifier,
    viewModel: MainViewModel = hiltViewModel()
) {
    val helper = LocalHelper.current
    val state: MainState by viewModel.state.collectAsStateWithLifecycle()
    val rowCount = state.rowCount
    fun onRowCount(target: Int) {
        state.rowCount = target
    }
    LaunchedEffect(isCurrentPage) {
        if (isCurrentPage) {
            helper.actions = emptyList()
        }
    }

    val interceptVolumeEventModifier = remember(state.godMode) {
        if (state.godMode) {
            Modifier.interceptVolumeEvent { event ->
                when (event) {
                    KeyEvent.KEYCODE_VOLUME_UP -> onRowCount((rowCount - 1).coerceAtLeast(1))
                    KeyEvent.KEYCODE_VOLUME_DOWN -> onRowCount((rowCount + 1).coerceAtMost(3))
                }
            }
        } else Modifier
    }

    CompositionLocalProvider(
        LocalScalable provides Scalable(1f / rowCount)
    ) {
        MainScreen(
            feeds = state.feeds,
            rowCount = rowCount,
            navigateToFeed = navigateToFeed,
            unsubscribe = { viewModel.onEvent(MainEvent.UnsubscribeFeedByUrl(it)) },
            rename = { feedUrl, target -> viewModel.onEvent(MainEvent.Rename(feedUrl, target)) },
            modifier = modifier
                .fillMaxSize()
                .then(interceptVolumeEventModifier),
        )
    }
}

@Composable
private fun MainScreen(
    rowCount: Int,
    feeds: List<FeedDetail>,
    navigateToFeed: NavigateToFeed,
    unsubscribe: OnUnsubscribe,
    rename: OnRename,
    modifier: Modifier = Modifier
) {
    var dialog: MainDialog by remember {
        mutableStateOf(MainDialog.Idle)
    }
    val configuration = LocalConfiguration.current

    when (configuration.orientation) {
        Configuration.ORIENTATION_PORTRAIT -> {
            PortraitOrientationContent(
                feeds = feeds,
                navigateToFeed = navigateToFeed,
                showDialog = { dialog = MainDialog.Selections(it) },
                modifier = modifier.fillMaxSize()
            )
        }

        Configuration.ORIENTATION_LANDSCAPE -> {
            LandscapeOrientationContent(
                rowCount = rowCount,
                feeds = feeds,
                navigateToFeed = navigateToFeed,
                showDialog = { dialog = MainDialog.Selections(it) },
                modifier = modifier.fillMaxSize()
            )
        }

        else -> {}
    }

    MainDialog(
        status = dialog,
        update = { dialog = it },
        unsubscribe = unsubscribe,
        rename = rename
    )

    BackHandler(dialog != MainDialog.Idle) {
        dialog = MainDialog.Idle
    }
}

@Composable
fun PortraitOrientationContent(
    feeds: List<FeedDetail>,
    navigateToFeed: NavigateToFeed,
    showDialog: showDialog,
    modifier: Modifier = Modifier
) {
    val spacing = LocalSpacing.current
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(LocalSpacing.current.medium),
        verticalArrangement = Arrangement.spacedBy(spacing.small)
    ) {
        items(
            items = feeds,
            key = { it.feed.url },
            contentType = {}
        ) { detail ->
            FeedItem(
                label = detail.feed.calculateUiTitle(),
                number = detail.count,
                special = detail.feed.isTemplated(),
                modifier = Modifier.fillParentMaxWidth(),
                onClick = {
                    navigateToFeed(detail.feed)
                },
                onLongClick = {
                    showDialog(detail.feed)
                }
            )
        }
    }
}

@Composable
private fun LandscapeOrientationContent(
    rowCount: Int,
    feeds: List<FeedDetail>,
    navigateToFeed: NavigateToFeed,
    showDialog: showDialog,
    modifier: Modifier = Modifier
) {
    val scalable = LocalScalable.current
    val spacing = with(scalable) {
        LocalSpacing.current.scaled
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(rowCount + 2),
        contentPadding = PaddingValues(LocalSpacing.current.medium),
        verticalArrangement = Arrangement.spacedBy(spacing.medium),
        horizontalArrangement = Arrangement.spacedBy(spacing.medium),
        modifier = modifier.fillMaxSize()
    ) {
        items(
            items = feeds,
            key = { it.feed.url },
            contentType = {}
        ) { detail ->
            FeedItem(
                label = detail.feed.calculateUiTitle(),
                number = detail.count,
                special = detail.feed.isTemplated(),
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    navigateToFeed(detail.feed)
                },
                onLongClick = {
                    showDialog(detail.feed)
                }
            )
        }
    }
}

@Composable
private fun Feed.calculateUiTitle(): AnnotatedString {
    return if (!this.isTemplated()) AnnotatedString(this.title)
    else AnnotatedString(
        text = stringResource(R.string.imported_feed_title)
    )
}