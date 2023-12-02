package com.m3u.features.main

import android.content.res.Configuration
import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Drafts
import androidx.compose.material.icons.rounded.Mail
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.capitalize
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.Locale
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import com.m3u.ui.useRailNav

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
                modifier = Modifier.align(Alignment.Center)
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
private fun FeedGalleryPlaceholder(
    modifier: Modifier = Modifier
) {
    val feedback = LocalHapticFeedback.current
    val spacing = LocalSpacing.current
    val helper = LocalHelper.current
    val useRailNav = helper.useRailNav

    var expanded by rememberSaveable { mutableStateOf(false) }

    val cornerSize by animateDpAsState(
        targetValue = if (expanded) spacing.medium else spacing.large,
        label = "feed-gallery-placeholder-corner-size"
    )

    val elevation by animateDpAsState(
        targetValue = if (expanded) spacing.small else spacing.medium,
        label = "feed-gallery-placeholder-elevation"
    )

    val shape = RoundedCornerShape(cornerSize)
    val combined = Modifier
        .padding(spacing.medium)
        .clip(shape)
        .animateContentSize()
        .toggleable(
            value = expanded,
            role = Role.Checkbox,
            onValueChange = {
                feedback.performHapticFeedback(HapticFeedbackType.LongPress)
                expanded = it
            }
        )
        .semantics(mergeDescendants = true) {}
        .then(modifier)
    ElevatedCard(
        modifier = combined,
        shape = shape,
        colors = CardDefaults.elevatedCardColors(),
        elevation = CardDefaults.elevatedCardElevation(
            defaultElevation = elevation
        )
    ) {
        val innerModifier = Modifier.padding(spacing.medium)
        val icon = @Composable {
            Crossfade(
                targetState = expanded,
                label = "feed-gallery-placeholder-icon"
            ) { expanded ->
                if (expanded) {
                    Icon(
                        imageVector = Icons.Rounded.Drafts,
                        contentDescription = null
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.Mail,
                        contentDescription = null
                    )
                }
            }
        }
        val spacer = @Composable {
            Spacer(modifier = Modifier.size(if (useRailNav) spacing.medium else spacing.small))
        }
        val message = @Composable {
            val text = stringResource(R.string.feat_feed_prompt_add_playlist)
                .capitalize(Locale.current)
            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn() + scaleIn(transformOrigin = TransformOrigin(0f, 0.5f)),
                exit = fadeOut() + scaleOut(transformOrigin = TransformOrigin(0f, 0.5f))
            ) {
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        AnimatedContent(
            targetState = expanded,
            transitionSpec = {
                expandHorizontally(expandFrom = Alignment.Start) togetherWith
                        shrinkHorizontally(shrinkTowards = Alignment.Start)
            },
            label = "feed-gallery-placeholder-content",
            modifier = innerModifier
        ) { expanded ->
            if (expanded) {
                if (useRailNav) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        icon()
                        spacer()
                        message()
                    }
                } else {
                    Column(
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.Start,
                    ) {
                        icon()
                        spacer()
                        message()
                    }
                }
            } else {
                icon()
            }
        }
    }
}
