package com.m3u.features.main

import android.content.res.Configuration
import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.m3u.data.database.entity.Feed
import com.m3u.features.main.components.FeedGallery
import com.m3u.features.main.components.MainDialog
import com.m3u.features.main.components.OnRename
import com.m3u.features.main.components.OnUnsubscribe
import com.m3u.features.main.model.FeedDetail
import com.m3u.material.components.Background
import com.m3u.material.ktx.interceptVolumeEvent
import com.m3u.ui.EventHandler
import com.m3u.ui.LocalHelper
import com.m3u.ui.ResumeEvent

typealias NavigateToFeed = (feed: Feed) -> Unit

@Composable
fun MainRoute(
    navigateToFeed: NavigateToFeed,
    resume: ResumeEvent,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    viewModel: MainViewModel = hiltViewModel()
) {
    val helper = LocalHelper.current
    val state: MainState by viewModel.state.collectAsStateWithLifecycle()
    val feeds by viewModel.feeds.collectAsStateWithLifecycle()
    val rowCount = state.rowCount
    fun onRowCount(target: Int) {
        state.rowCount = target
    }

    EventHandler(resume) {
        helper.actions = emptyList()
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

    MainScreen(
        feedsFactory = { feeds },
        rowCount = rowCount,
        contentPadding = contentPadding,
        navigateToFeed = navigateToFeed,
        unsubscribe = { viewModel.onEvent(MainEvent.Unsubscribe(it)) },
        rename = { feedUrl, target -> viewModel.onEvent(MainEvent.Rename(feedUrl, target)) },
        modifier = modifier
            .fillMaxSize()
            .then(interceptVolumeEventModifier),
    )
}

private typealias FeedsFactory = () -> List<FeedDetail>

@Composable
private fun MainScreen(
    rowCount: Int,
    feedsFactory: FeedsFactory,
    contentPadding: PaddingValues,
    navigateToFeed: NavigateToFeed,
    unsubscribe: OnUnsubscribe,
    rename: OnRename,
    modifier: Modifier = Modifier
) {
    var dialog: MainDialog by remember { mutableStateOf(MainDialog.Idle) }
    val configuration = LocalConfiguration.current

    val actualRowCount = remember(rowCount, configuration.orientation) {
        when (configuration.orientation) {
            Configuration.ORIENTATION_PORTRAIT -> rowCount
            else -> rowCount + 2
        }
    }
    Background {
        FeedGallery(
            rowCount = actualRowCount,
            feedDetailsFactory = feedsFactory,
            navigateToFeed = navigateToFeed,
            onMenu = { dialog = MainDialog.Selections(it) },
            contentPadding = contentPadding,
            modifier = modifier
        )
        MainDialog(
            status = dialog,
            update = { dialog = it },
            unsubscribe = unsubscribe,
            rename = rename
        )
    }

    BackHandler(dialog != MainDialog.Idle) {
        dialog = MainDialog.Idle
    }
}
