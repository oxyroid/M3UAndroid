package com.m3u.features.setting

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.rememberScrollableState
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.m3u.core.annotation.FeedStrategy
import com.m3u.core.annotation.SetStrategy
import com.m3u.core.util.context.toast
import com.m3u.features.setting.components.CheckBoxPreference
import com.m3u.features.setting.components.FoldPreference
import com.m3u.features.setting.components.TextPreference
import com.m3u.ui.components.OuterColumn
import com.m3u.ui.components.TextButton
import com.m3u.ui.components.TextField
import com.m3u.ui.components.WorkInProgressLottie
import com.m3u.ui.model.AppAction
import com.m3u.ui.model.LocalSpacing
import com.m3u.ui.model.LocalTheme
import com.m3u.ui.model.SetActions
import com.m3u.ui.util.EventHandler
import com.m3u.ui.util.LifecycleEffect

@Composable
internal fun SettingRoute(
    modifier: Modifier = Modifier,
    viewModel: SettingViewModel = hiltViewModel(),
    setAppActions: SetActions
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    EventHandler(state.message) {
        context.toast(it)
    }

    val setAppActionsUpdated by rememberUpdatedState(setAppActions)
    LifecycleEffect { event ->
        when (event) {
            Lifecycle.Event.ON_START -> {
                val actions = listOf<AppAction>()
                setAppActionsUpdated(actions)
            }

            Lifecycle.Event.ON_PAUSE -> {
                setAppActionsUpdated(emptyList())
            }

            else -> {}
        }
    }

    SettingScreen(
        subscribeEnable = !state.adding,
        showMutedAsFeed = state.showMutedAsFeed,
        title = state.title,
        url = state.url,
        version = state.version,
        feedStrategy = state.feedStrategy,
        onTitle = { viewModel.onEvent(SettingEvent.OnTitle(it)) },
        onUrl = { viewModel.onEvent(SettingEvent.OnUrl(it)) },
        onSubscribe = { viewModel.onEvent(SettingEvent.OnSubscribe) },
        onSyncMode = { viewModel.onEvent(SettingEvent.OnSyncMode(it)) },
        onShowMuted = { viewModel.onEvent(SettingEvent.OnShowMuted) },
        useCommonUIMode = state.useCommonUIMode,
        onUIMode = { viewModel.onEvent(SettingEvent.OnUIMode) },
        modifier = modifier.fillMaxSize()
    )
}

@Composable
private fun SettingScreen(
    subscribeEnable: Boolean,
    showMutedAsFeed: Boolean,
    version: String,
    title: String,
    url: String,
    @FeedStrategy feedStrategy: Int,
    onTitle: (String) -> Unit,
    onUrl: (String) -> Unit,
    onSubscribe: () -> Unit,
    onSyncMode: SetStrategy,
    useCommonUIMode: Boolean,
    onUIMode: () -> Unit,
    onShowMuted: () -> Unit,
    modifier: Modifier = Modifier
) {
    var fold: Fold by remember { mutableStateOf(Fold.NONE) }
    Box(
        modifier = Modifier.testTag("features:setting")
    ) {
        val configuration = LocalConfiguration.current
        when (configuration.orientation) {
            Configuration.ORIENTATION_PORTRAIT -> {
                PortraitOrientationContent(
                    fold = fold,
                    title = title,
                    url = url,
                    subscribeEnable = subscribeEnable,
                    onFold = { fold = it },
                    onTitle = onTitle,
                    onUrl = onUrl,
                    onSubscribe = onSubscribe,
                    feedStrategy = feedStrategy,
                    onSyncMode = onSyncMode,
                    onShowMuted = onShowMuted,
                    showMutedAsFeed = showMutedAsFeed,
                    version = version,
                    modifier = modifier
                        .fillMaxWidth()
                        .scrollable(
                            orientation = Orientation.Vertical,
                            state = rememberScrollableState { it }
                        )
                )
            }
            Configuration.ORIENTATION_LANDSCAPE -> {
                LandscapeOrientationContent(
                    fold = fold,
                    title = title,
                    url = url,
                    subscribeEnable = subscribeEnable,
                    onFold = { fold = it },
                    onTitle = onTitle,
                    onUrl = onUrl,
                    onSubscribe = onSubscribe,
                    feedStrategy = feedStrategy,
                    onSyncMode = onSyncMode,
                    useCommonUIMode = useCommonUIMode,
                    showMutedAsFeed = showMutedAsFeed,
                    onUIMode = onUIMode,
                    onShowMuted = onShowMuted,
                    version = version,
                    modifier = modifier
                        .scrollable(
                            orientation = Orientation.Vertical,
                            state = rememberScrollableState { it }
                        )
                )
            }
            else -> {}
        }
    }
    BackHandler(fold != Fold.NONE) {
        fold = Fold.NONE
    }
}

@Composable
private fun PortraitOrientationContent(
    fold: Fold,
    title: String,
    url: String,
    subscribeEnable: Boolean,
    showMutedAsFeed: Boolean,
    onFold: (Fold) -> Unit,
    onTitle: (String) -> Unit,
    onUrl: (String) -> Unit,
    onSubscribe: () -> Unit,
    onShowMuted: () -> Unit,
    feedStrategy: @FeedStrategy Int,
    onSyncMode: SetStrategy,
    version: String,
    modifier: Modifier = Modifier
) {
    Box {
        PreferencesPart(
            version = version,
            onFeedManagement = {
                onFold(Fold.FEED)
            },
            onScriptManagement = {
                onFold(Fold.SCRIPT)
            },
            feedStrategy = feedStrategy,
            onSyncMode = onSyncMode,
            useCommonUIMode = true,
            useCommonUIModeEnable = false,
            showMutedAsFeed = showMutedAsFeed,
            onUIMode = { },
            onShowMuted = onShowMuted,
            modifier = modifier
        )

        AnimatedVisibility(
            visible = fold != Fold.NONE,
            enter = slideInHorizontally { it },
            exit = slideOutHorizontally { it }
        ) {
            when (fold) {
                Fold.FEED -> {
                    FeedManagementPart(
                        title = title,
                        url = url,
                        subscribeEnable = subscribeEnable,
                        onTitle = onTitle,
                        onUrl = onUrl,
                        onSubscribe = onSubscribe,
                        modifier = modifier.background(LocalTheme.current.background)
                    )
                }
                Fold.SCRIPT -> {
                    ScriptManagementPart(
                        modifier = modifier.background(LocalTheme.current.background)
                    )
                }
                else -> {}
            }
        }
    }
}

@Composable
private fun LandscapeOrientationContent(
    fold: Fold,
    title: String,
    url: String,
    subscribeEnable: Boolean,
    onFold: (Fold) -> Unit,
    onTitle: (String) -> Unit,
    onUrl: (String) -> Unit,
    onSubscribe: () -> Unit,
    feedStrategy: @FeedStrategy Int,
    onSyncMode: SetStrategy,
    useCommonUIMode: Boolean,
    showMutedAsFeed: Boolean,
    onUIMode: () -> Unit,
    onShowMuted: () -> Unit,
    version: String,
    modifier: Modifier = Modifier
) {
    val spacing = LocalSpacing.current

    Row(
        horizontalArrangement = Arrangement.spacedBy(spacing.medium, Alignment.Start),
        modifier = modifier.padding(horizontal = spacing.medium)
    ) {
        PreferencesPart(
            version = version,
            onFeedManagement = { onFold(Fold.FEED) },
            onScriptManagement = { onFold(Fold.SCRIPT) },
            feedStrategy = feedStrategy,
            onSyncMode = onSyncMode,
            useCommonUIMode = useCommonUIMode,
            showMutedAsFeed = showMutedAsFeed,
            onUIMode = onUIMode,
            onShowMuted = onShowMuted,
            modifier = Modifier
                .fillMaxHeight()
                .weight(1f)
        )

        when (fold) {
            Fold.FEED -> {
                FeedManagementPart(
                    title = title,
                    url = url,
                    subscribeEnable = subscribeEnable,
                    onTitle = onTitle,
                    onUrl = onUrl,
                    onSubscribe = onSubscribe,
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight(1f)
                )
            }

            Fold.SCRIPT -> {
                ScriptManagementPart(
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight(1f)
                )
            }

            else -> {}
        }
    }
}

@Composable
private fun PreferencesPart(
    version: String,
    onFeedManagement: () -> Unit,
    onScriptManagement: () -> Unit,
    feedStrategy: @FeedStrategy Int,
    onSyncMode: SetStrategy,
    useCommonUIMode: Boolean,
    showMutedAsFeed: Boolean,
    onUIMode: () -> Unit,
    onShowMuted: () -> Unit,
    modifier: Modifier = Modifier,
    useCommonUIModeEnable: Boolean = true,
) {
    val spacing = LocalSpacing.current
    OuterColumn(
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.clip(RoundedCornerShape(spacing.medium)),
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            FoldPreference(
                title = stringResource(R.string.feed_management),
                enabled = true,
                onClick = onFeedManagement
            )
            FoldPreference(
                title = stringResource(R.string.script_management),
                enabled = true,
                onClick = onScriptManagement
            )
            TextPreference(
                title = stringResource(R.string.sync_mode),
                content = when (feedStrategy) {
                    FeedStrategy.ALL -> stringResource(R.string.sync_mode_all)
                    FeedStrategy.SKIP_FAVORITE -> stringResource(R.string.sync_mode_skip_favourite)
                    else -> ""
                },
                onClick = {
                    // TODO
                    val target = when (feedStrategy) {
                        FeedStrategy.ALL -> FeedStrategy.SKIP_FAVORITE
                        else -> FeedStrategy.ALL
                    }
                    onSyncMode(target)
                }
            )
            CheckBoxPreference(
                title = stringResource(R.string.show_muted_mode),
                checked = showMutedAsFeed,
                // TODO
                enabled = false,
                onCheckedChange = { newValue ->
                    if (newValue != showMutedAsFeed) {
                        onShowMuted()
                    }
                }
            )
            CheckBoxPreference(
                title = stringResource(R.string.common_ui_mode),
                subtitle = if (useCommonUIModeEnable) stringResource(R.string.common_ui_mode_description)
                else stringResource(R.string.common_ui_mode_disabled_description),
                enabled = useCommonUIModeEnable,
                checked = useCommonUIMode,
                onCheckedChange = { newValue ->
                    if (newValue != useCommonUIMode) {
                        onUIMode()
                    }
                }
            )
        }
        val uriHandler = LocalUriHandler.current
        TextButton(
            text = stringResource(R.string.label_app_version, version),
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) {
            val url = "https://github.com/thxbrop/M3UAndroid/releases/tag/v$version"
            uriHandler.openUri(url)
        }
    }
}

@Composable
private fun FeedManagementPart(
    title: String,
    url: String,
    subscribeEnable: Boolean,
    onTitle: (String) -> Unit,
    onUrl: (String) -> Unit,
    onSubscribe: () -> Unit,
    modifier: Modifier = Modifier
) {
    val spacing = LocalSpacing.current
    OuterColumn(
        verticalArrangement = Arrangement.spacedBy(spacing.small),
        modifier = modifier
    ) {
        val focusRequester = remember { FocusRequester() }
        TextField(
            text = title,
            enabled = subscribeEnable,
            placeholder = stringResource(R.string.placeholder_title),
            onValueChange = onTitle,
            keyboardActions = KeyboardActions(
                onNext = {
                    focusRequester.requestFocus()
                }
            ),
            modifier = Modifier.fillMaxWidth()
        )
        TextField(
            text = url,
            enabled = subscribeEnable,
            placeholder = stringResource(R.string.placeholder_url),
            onValueChange = onUrl,
            keyboardActions = KeyboardActions(
                onDone = {
                    onSubscribe()
                }
            ),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
        )
        val buttonTextResId = if (subscribeEnable) R.string.label_subscribe
        else R.string.label_subscribing
        TextButton(
            enabled = subscribeEnable,
            text = stringResource(buttonTextResId),
            onClick = { onSubscribe() },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun ScriptManagementPart(
    modifier: Modifier = Modifier
) {
    OuterColumn(
        modifier = modifier
    ) {
        WorkInProgressLottie(
            modifier = Modifier
                .aspectRatio(1f)
                .fillMaxWidth()
        )
    }
}

private enum class Fold {
    NONE, FEED, SCRIPT
}