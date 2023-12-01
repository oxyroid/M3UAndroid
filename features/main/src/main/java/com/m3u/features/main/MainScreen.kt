package com.m3u.features.main

import android.content.res.Configuration
import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.TipsAndUpdates
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.m3u.core.util.basic.title
import com.m3u.data.database.entity.Feed
import com.m3u.features.main.components.FeedGallery
import com.m3u.features.main.components.MainDialog
import com.m3u.features.main.components.OnRename
import com.m3u.features.main.components.OnUnsubscribe
import com.m3u.features.main.model.FeedDetailHolder
import com.m3u.i18n.R
import com.m3u.material.components.Background
import com.m3u.material.ktx.interceptVolumeEvent
import com.m3u.material.model.LocalSpacing
import com.m3u.ui.EventHandler
import com.m3u.ui.LocalHelper
import com.m3u.ui.MessageEventHandler
import com.m3u.ui.ResumeEvent

typealias NavigateToFeed = (feed: Feed) -> Unit
typealias NavigateToSettingSubscription = () -> Unit

@Composable
fun MainRoute(
    navigateToFeed: NavigateToFeed,
    navigateToSettingSubscription: NavigateToSettingSubscription,
    resume: ResumeEvent,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    viewModel: MainViewModel = hiltViewModel()
) {
    val helper = LocalHelper.current
    val message by viewModel.message.collectAsStateWithLifecycle()
    val state: MainState by viewModel.state.collectAsStateWithLifecycle()
    val feedDetailHolder by viewModel.feeds.collectAsStateWithLifecycle()
    val rowCount = state.rowCount
    fun onRowCount(target: Int) {
        state.rowCount = target
    }

    MessageEventHandler(message)

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
        feedDetailHolder = feedDetailHolder,
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

@Composable
private fun MainScreen(
    rowCount: Int,
    feedDetailHolder: FeedDetailHolder,
    contentPadding: PaddingValues,
    navigateToFeed: NavigateToFeed,
    unsubscribe: OnUnsubscribe,
    rename: OnRename,
    modifier: Modifier = Modifier
) {
    var dialog: MainDialog by remember { mutableStateOf(MainDialog.Idle) }
    val configuration = LocalConfiguration.current

    val details = feedDetailHolder.details

    val actualRowCount = remember(rowCount, configuration.orientation) {
        when (configuration.orientation) {
            Configuration.ORIENTATION_PORTRAIT -> rowCount
            else -> rowCount + 2
        }
    }
    Background(modifier) {
        if (details.isNotEmpty()) {
            FeedGallery(
                rowCount = actualRowCount,
                feedDetailHolder = feedDetailHolder,
                navigateToFeed = navigateToFeed,
                onMenu = { dialog = MainDialog.Selections(it) },
                contentPadding = contentPadding,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            FeedGalleryPlaceholder(
                modifier = Modifier.fillMaxSize()
            )
        }
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

@Composable
private fun FeedGalleryPlaceholder(modifier: Modifier = Modifier) {
    val spacing = LocalSpacing.current
    Row(
        modifier.semantics(mergeDescendants = true) { },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Rounded.TipsAndUpdates,
            contentDescription = null
        )
        Spacer(modifier = Modifier.size(spacing.medium))
        val text = stringResource(R.string.feat_feed_prompt_add_playlist).title()
        Text(remember(text) { text })
    }
}
