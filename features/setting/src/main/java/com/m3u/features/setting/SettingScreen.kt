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
import androidx.compose.foundation.lazy.LazyColumn
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
import com.m3u.core.annotation.ConnectTimeout
import com.m3u.core.annotation.FeedStrategy
import com.m3u.core.annotation.SetStrategy
import com.m3u.core.util.context.toast
import com.m3u.core.wrapper.Resource
import com.m3u.data.entity.GitRelease
import com.m3u.features.setting.components.CheckBoxPreference
import com.m3u.features.setting.components.FoldPreference
import com.m3u.features.setting.components.TextPreference
import com.m3u.ui.components.OuterColumn
import com.m3u.ui.components.TextButton
import com.m3u.ui.components.TextField
import com.m3u.ui.components.WorkInProgressLottie
import com.m3u.ui.model.LocalSpacing
import com.m3u.ui.model.LocalTheme
import com.m3u.ui.model.LocalUtils
import com.m3u.ui.util.EventHandler
import com.m3u.ui.util.LifecycleEffect

@Composable
internal fun SettingRoute(
    modifier: Modifier = Modifier,
    viewModel: SettingViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val utils = LocalUtils.current

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

    val configuration = LocalConfiguration.current
    val type = configuration.uiMode and Configuration.UI_MODE_TYPE_MASK
    val useCommonUIMode = if (type == Configuration.UI_MODE_TYPE_NORMAL) true
    else state.useCommonUIMode
    val useCommonUIModeEnable = (type != Configuration.UI_MODE_TYPE_NORMAL)
    SettingScreen(
        subscribeEnable = !state.adding,
        title = state.title,
        url = state.url,
        version = state.version,
        latestRelease = state.latestRelease,
        fetchLatestRelease = { viewModel.onEvent(SettingEvent.FetchLatestRelease) },
        feedStrategy = state.feedStrategy,
        editMode = state.editMode,
        connectTimeout = state.connectTimeout,
        onEditMode = { viewModel.onEvent(SettingEvent.OnEditMode) },
        onConnectTimeout = { viewModel.onEvent(SettingEvent.OnConnectTimeout) },
        onTitle = { viewModel.onEvent(SettingEvent.OnTitle(it)) },
        onUrl = { viewModel.onEvent(SettingEvent.OnUrl(it)) },
        onSubscribe = { viewModel.onEvent(SettingEvent.OnSubscribe) },
        onSyncMode = { viewModel.onEvent(SettingEvent.OnSyncMode(it)) },
        useCommonUIMode = useCommonUIMode,
        useCommonUIModeEnable = useCommonUIModeEnable,
        onUIMode = { viewModel.onEvent(SettingEvent.OnUIMode) },
        modifier = modifier.fillMaxSize()
    )
}

@Composable
private fun SettingScreen(
    subscribeEnable: Boolean,
    version: String,
    latestRelease: Resource<GitRelease>,
    fetchLatestRelease: () -> Unit,
    title: String,
    url: String,
    @FeedStrategy feedStrategy: Int,
    editMode: Boolean,
    @ConnectTimeout connectTimeout: Int,
    onEditMode: () -> Unit,
    onConnectTimeout: () -> Unit,
    onTitle: (String) -> Unit,
    onUrl: (String) -> Unit,
    onSubscribe: () -> Unit,
    onSyncMode: SetStrategy,
    useCommonUIMode: Boolean,
    useCommonUIModeEnable: Boolean,
    onUIMode: () -> Unit,
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
                    editMode = editMode,
                    connectTimeout = connectTimeout,
                    subscribeEnable = subscribeEnable,
                    onFold = { fold = it },
                    onTitle = onTitle,
                    onUrl = onUrl,
                    onSubscribe = onSubscribe,
                    feedStrategy = feedStrategy,
                    onConnectTimeout = onConnectTimeout,
                    onSyncMode = onSyncMode,
                    onEditMode = onEditMode,
                    version = version,
                    latestRelease = latestRelease,
                    fetchLatestRelease = fetchLatestRelease,
                    useCommonUIMode = useCommonUIMode,
                    useCommonUIModeEnable = useCommonUIModeEnable,
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
                    editMode = editMode,
                    subscribeEnable = subscribeEnable,
                    feedStrategy = feedStrategy,
                    connectTimeout = connectTimeout,
                    useCommonUIMode = useCommonUIMode,
                    useCommonUIModeEnable = useCommonUIModeEnable,
                    version = version,
                    latestRelease = latestRelease,
                    fetchLatestRelease = fetchLatestRelease,
                    onFold = { fold = it },
                    onTitle = onTitle,
                    onUrl = onUrl,
                    onSubscribe = onSubscribe,
                    onSyncMode = onSyncMode,
                    onEditMode = onEditMode,
                    onConnectTimeout = onConnectTimeout,
                    onUIMode = onUIMode,
                    modifier = modifier.scrollable(
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
    @FeedStrategy feedStrategy: Int,
    editMode: Boolean,
    @ConnectTimeout connectTimeout: Int,
    useCommonUIMode: Boolean,
    useCommonUIModeEnable: Boolean,
    onEditMode: () -> Unit,
    onConnectTimeout: () -> Unit,
    subscribeEnable: Boolean,
    onFold: (Fold) -> Unit,
    onTitle: (String) -> Unit,
    onUrl: (String) -> Unit,
    onSubscribe: () -> Unit,
    onSyncMode: SetStrategy,
    version: String,
    latestRelease: Resource<GitRelease>,
    fetchLatestRelease: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box {
        PreferencesPart(
            version = version,
            latestRelease = latestRelease,
            fetchLatestRelease = fetchLatestRelease,
            feedStrategy = feedStrategy,
            useCommonUIMode = useCommonUIMode,
            useCommonUIModeEnable = useCommonUIModeEnable,
            editMode = editMode,
            connectTimeout = connectTimeout,
            onConnectTimeout = onConnectTimeout,
            onSyncMode = onSyncMode,
            onUIMode = { },
            onEditMode = onEditMode,
            onFeedManagement = {
                onFold(Fold.FEED)
            },
            onScriptManagement = {
                onFold(Fold.SCRIPT)
            },
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
    editMode: Boolean,
    subscribeEnable: Boolean,
    onFold: (Fold) -> Unit,
    onTitle: (String) -> Unit,
    onUrl: (String) -> Unit,
    onSubscribe: () -> Unit,
    @FeedStrategy feedStrategy: Int,
    @ConnectTimeout connectTimeout: Int,
    onSyncMode: SetStrategy,
    onConnectTimeout: () -> Unit,
    useCommonUIMode: Boolean,
    useCommonUIModeEnable: Boolean,
    onUIMode: () -> Unit,
    onEditMode: () -> Unit,
    version: String,
    latestRelease: Resource<GitRelease>,
    fetchLatestRelease: () -> Unit,
    modifier: Modifier = Modifier
) {
    val spacing = LocalSpacing.current

    Row(
        horizontalArrangement = Arrangement.spacedBy(spacing.medium, Alignment.Start),
        modifier = modifier.padding(horizontal = spacing.medium)
    ) {
        PreferencesPart(
            version = version,
            latestRelease = latestRelease,
            fetchLatestRelease = fetchLatestRelease,
            editMode = editMode,
            onFeedManagement = { onFold(Fold.FEED) },
            onScriptManagement = { onFold(Fold.SCRIPT) },
            feedStrategy = feedStrategy,
            connectTimeout = connectTimeout,
            onSyncMode = onSyncMode,
            onConnectTimeout = onConnectTimeout,
            useCommonUIMode = useCommonUIMode,
            useCommonUIModeEnable = useCommonUIModeEnable,
            onUIMode = onUIMode,
            onEditMode = onEditMode,
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
    latestRelease: Resource<GitRelease>,
    fetchLatestRelease: () -> Unit,
    onFeedManagement: () -> Unit,
    onScriptManagement: () -> Unit,
    @FeedStrategy feedStrategy: Int,
    @ConnectTimeout connectTimeout: Int,
    editMode: Boolean,
    onSyncMode: SetStrategy,
    useCommonUIMode: Boolean,
    onUIMode: () -> Unit,
    onEditMode: () -> Unit,
    onConnectTimeout: () -> Unit,
    modifier: Modifier = Modifier,
    useCommonUIModeEnable: Boolean,
) {
    val spacing = LocalSpacing.current
    LazyColumn(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Column(
                modifier = Modifier
                    .padding(spacing.medium)
                    .clip(RoundedCornerShape(spacing.medium)),
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
                TextPreference(
                    title = stringResource(R.string.connect_timeout),
                    content = "${connectTimeout / 1000}s",
                    onClick = onConnectTimeout
                )
                CheckBoxPreference(
                    title = stringResource(R.string.edit_mode),
                    subtitle = stringResource(id = R.string.edit_mode_description),
                    checked = editMode,
                    onCheckedChange = { newValue ->
                        if (newValue != editMode) {
                            onEditMode()
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
        }
        item {
            val uriHandler = LocalUriHandler.current
            TextButton(
                text = stringResource(R.string.label_app_version, version),
            ) {
                val url = "https://github.com/thxbrop/M3UAndroid/releases/tag/v$version"
                uriHandler.openUri(url)
            }
        }
        item {
            when (latestRelease) {
                Resource.Loading -> {
                    TextButton(stringResource(R.string.fetching_latest)) {}
                }
                is Resource.Success -> {
                    val uriHandler = LocalUriHandler.current
                    val remoteVersion = latestRelease.data.name
                    val name = if (remoteVersion != version) {
                        stringResource(R.string.label_latest_release_version, remoteVersion)
                    } else {
                        stringResource(R.string.label_same_version)
                    }

                    TextButton(name) {
                        if (remoteVersion == version) {
                            fetchLatestRelease()
                        } else {
                            val url = "https://github.com/thxbrop/M3UAndroid/releases/tag/v$name"
                            uriHandler.openUri(url)
                        }
                    }
                }
                is Resource.Failure -> {
                    TextButton(
                        text = stringResource(
                            R.string.failed_latest_release_version,
                            latestRelease.message.orEmpty()
                        )
                    ) {
                        fetchLatestRelease()
                    }
                }
            }
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