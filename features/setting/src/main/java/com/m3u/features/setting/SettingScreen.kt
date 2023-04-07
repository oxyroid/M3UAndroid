package com.m3u.features.setting

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.rememberScrollableState
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.m3u.core.annotation.ClipMode
import com.m3u.core.annotation.ConnectTimeout
import com.m3u.core.annotation.FeedStrategy
import com.m3u.core.annotation.OnClipMode
import com.m3u.core.annotation.OnFeedStrategy
import com.m3u.core.util.context.toast
import com.m3u.core.wrapper.Resource
import com.m3u.data.database.entity.Live
import com.m3u.data.database.entity.Release
import com.m3u.features.setting.components.CheckBoxPreference
import com.m3u.features.setting.components.FoldPreference
import com.m3u.features.setting.components.MutedLiveItem
import com.m3u.features.setting.components.TextPreference
import com.m3u.ui.components.Button
import com.m3u.ui.components.LabelField
import com.m3u.ui.components.OuterColumn
import com.m3u.ui.components.TextButton
import com.m3u.ui.components.WorkInProgressLottie
import com.m3u.ui.model.LocalHelper
import com.m3u.ui.model.LocalSpacing
import com.m3u.ui.model.LocalTheme
import com.m3u.ui.util.EventHandler
import com.m3u.ui.util.RepeatOnCreate

typealias NavigateToConsole = () -> Unit

@Composable
fun SettingRoute(
    modifier: Modifier = Modifier,
    viewModel: SettingViewModel = hiltViewModel(),
    navigateToConsole: NavigateToConsole
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val helper = LocalHelper.current

    EventHandler(state.message) {
        context.toast(it)
    }

    RepeatOnCreate {
        helper.actions()
    }

    val configuration = LocalConfiguration.current
    val type = configuration.uiMode and Configuration.UI_MODE_TYPE_MASK
    val useCommonUIMode = if (type == Configuration.UI_MODE_TYPE_NORMAL) true
    else state.useCommonUIMode
    val useCommonUIModeEnable = (type != Configuration.UI_MODE_TYPE_NORMAL)
    SettingScreen(
        adding = !state.adding,
        title = state.title,
        url = state.url,
        version = state.version,
        release = state.release,
        feedStrategy = state.feedStrategy,
        editMode = state.editMode,
        clipMode = state.clipMode,
        scrollMode = state.scrollMode,
        connectTimeout = state.connectTimeout,
        useCommonUIMode = useCommonUIMode,
        useCommonUIModeEnable = useCommonUIModeEnable,
        navigateToConsole = navigateToConsole,
        experimentalMode = state.experimentalMode,
        mutedLives = state.mutedLives,
        fetchRelease = { viewModel.onEvent(SettingEvent.FetchRelease) },
        onEditMode = { viewModel.onEvent(SettingEvent.OnEditMode) },
        onConnectTimeout = { viewModel.onEvent(SettingEvent.OnConnectTimeout) },
        onTitle = { viewModel.onEvent(SettingEvent.OnTitle(it)) },
        onUrl = { viewModel.onEvent(SettingEvent.OnUrl(it)) },
        onSubscribe = { viewModel.onEvent(SettingEvent.OnSubscribe) },
        onScrollMode = { viewModel.onEvent(SettingEvent.OnScrollMode) },
        onFeedStrategy = { viewModel.onEvent(SettingEvent.OnSyncMode(it)) },
        onUIMode = { viewModel.onEvent(SettingEvent.OnUseCommonUIMode) },
        onExperimentalMode = { viewModel.onEvent(SettingEvent.OnExperimentalMode) },
        onBannedLive = { viewModel.onEvent(SettingEvent.OnBannedLive(it)) },
        onClipMode = { viewModel.onEvent(SettingEvent.OnClipMode(it)) },
        autoRefresh = state.autoRefresh,
        onAutoRefresh = { viewModel.onEvent(SettingEvent.OnAutoRefresh) },
        isSSLVerificationEnabled = state.isSSLVerificationEnabled,
        onSSLVerificationEnabled = { viewModel.onEvent(SettingEvent.OnSSLVerificationEnabled) },
        modifier = modifier.fillMaxSize()
    )
}

@Composable
private fun SettingScreen(
    adding: Boolean,
    version: String,
    release: Resource<Release>,
    fetchRelease: () -> Unit,
    title: String,
    url: String,
    @FeedStrategy feedStrategy: Int,
    editMode: Boolean,
    @ClipMode clipMode: Int,
    @ConnectTimeout connectTimeout: Int,
    scrollMode: Boolean,
    onEditMode: () -> Unit,
    onConnectTimeout: () -> Unit,
    onTitle: (String) -> Unit,
    onUrl: (String) -> Unit,
    onSubscribe: () -> Unit,
    onScrollMode: () -> Unit,
    onFeedStrategy: OnFeedStrategy,
    useCommonUIMode: Boolean,
    useCommonUIModeEnable: Boolean,
    mutedLives: List<Live>,
    onBannedLive: (Int) -> Unit,
    onUIMode: () -> Unit,
    onClipMode: OnClipMode,
    navigateToConsole: NavigateToConsole,
    experimentalMode: Boolean,
    onExperimentalMode: () -> Unit,
    autoRefresh: Boolean,
    onAutoRefresh: () -> Unit,
    isSSLVerificationEnabled: Boolean,
    onSSLVerificationEnabled: () -> Unit,
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
                    adding = adding,
                    editMode = editMode,
                    connectTimeout = connectTimeout,
                    scrollMode = scrollMode,
                    onFold = { fold = it },
                    onTitle = onTitle,
                    onUrl = onUrl,
                    onSubscribe = onSubscribe,
                    feedStrategy = feedStrategy,
                    clipMode = clipMode,
                    onClipMode = onClipMode,
                    onConnectTimeout = onConnectTimeout,
                    onFeedStrategy = onFeedStrategy,
                    onEditMode = onEditMode,
                    onScrollMode = onScrollMode,
                    version = version,
                    release = release,
                    fetchRelease = fetchRelease,
                    useCommonUIMode = useCommonUIMode,
                    navigateToConsole = navigateToConsole,
                    useCommonUIModeEnable = useCommonUIModeEnable,
                    experimentalMode = experimentalMode,
                    onExperimentalMode = onExperimentalMode,
                    mutedLives = mutedLives,
                    onBannedLive = onBannedLive,
                    autoRefresh = autoRefresh,
                    onAutoRefresh = onAutoRefresh,
                    isSSLVerificationEnabled = isSSLVerificationEnabled,
                    onSSLVerificationEnabled = onSSLVerificationEnabled,
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
                    adding = adding,
                    editMode = editMode,
                    clipMode = clipMode,
                    scrollMode = scrollMode,
                    feedStrategy = feedStrategy,
                    connectTimeout = connectTimeout,
                    useCommonUIMode = useCommonUIMode,
                    useCommonUIModeEnable = useCommonUIModeEnable,
                    version = version,
                    release = release,
                    fetchRelease = fetchRelease,
                    onFold = { fold = it },
                    onTitle = onTitle,
                    onUrl = onUrl,
                    onClipMode = onClipMode,
                    onScrollMode = onScrollMode,
                    onSubscribe = onSubscribe,
                    onFeedStrategy = onFeedStrategy,
                    onEditMode = onEditMode,
                    onConnectTimeout = onConnectTimeout,
                    onUIMode = onUIMode,
                    navigateToConsole = navigateToConsole,
                    experimentalMode = experimentalMode,
                    onExperimentalMode = onExperimentalMode,
                    mutedLives = mutedLives,
                    onBannedLive = onBannedLive,
                    autoRefresh = autoRefresh,
                    onAutoRefresh = onAutoRefresh,
                    isSSLVerificationEnabled = isSSLVerificationEnabled,
                    onSSLVerificationEnabled = onSSLVerificationEnabled,
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
    @ClipMode clipMode: Int,
    @ConnectTimeout connectTimeout: Int,
    useCommonUIMode: Boolean,
    useCommonUIModeEnable: Boolean,
    onEditMode: () -> Unit,
    onClipMode: OnClipMode,
    onConnectTimeout: () -> Unit,
    adding: Boolean,
    mutedLives: List<Live>,
    onBannedLive: (Int) -> Unit,
    onFold: (Fold) -> Unit,
    onTitle: (String) -> Unit,
    onUrl: (String) -> Unit,
    onSubscribe: () -> Unit,
    onFeedStrategy: OnFeedStrategy,
    version: String,
    release: Resource<Release>,
    fetchRelease: () -> Unit,
    navigateToConsole: NavigateToConsole,
    experimentalMode: Boolean,
    onScrollMode: () -> Unit,
    scrollMode: Boolean,
    onExperimentalMode: () -> Unit,
    autoRefresh: Boolean,
    onAutoRefresh: () -> Unit,
    isSSLVerificationEnabled: Boolean,
    onSSLVerificationEnabled: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box {
        PreferencesPart(
            version = version,
            release = release,
            fetchRelease = fetchRelease,
            feedStrategy = feedStrategy,
            useCommonUIMode = useCommonUIMode,
            useCommonUIModeEnable = useCommonUIModeEnable,
            editMode = editMode,
            clipMode = clipMode,
            connectTimeout = connectTimeout,
            onConnectTimeout = onConnectTimeout,
            onFeedStrategy = onFeedStrategy,
            onClipMode = onClipMode,
            onUIMode = { },
            onEditMode = onEditMode,
            onFeedManagement = {
                onFold(Fold.FEED)
            },
            onScriptManagement = {
                onFold(Fold.SCRIPT)
            },
            navigateToConsole = navigateToConsole,
            experimentalMode = experimentalMode,
            onExperimentalMode = onExperimentalMode,
            scrollMode = scrollMode,
            onScrollMode = onScrollMode,
            autoRefresh = autoRefresh,
            onAutoRefresh = onAutoRefresh,
            isSSLVerificationEnabled = isSSLVerificationEnabled,
            onSSLVerificationEnabled = onSSLVerificationEnabled,
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
                        mutedLives = mutedLives,
                        onBannedLive = onBannedLive,
                        adding = adding,
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
    @ClipMode clipMode: Int,
    adding: Boolean,
    onFold: (Fold) -> Unit,
    onTitle: (String) -> Unit,
    onUrl: (String) -> Unit,
    onSubscribe: () -> Unit,
    @FeedStrategy feedStrategy: Int,
    @ConnectTimeout connectTimeout: Int,
    onFeedStrategy: OnFeedStrategy,
    onConnectTimeout: () -> Unit,
    useCommonUIMode: Boolean,
    useCommonUIModeEnable: Boolean,
    scrollMode: Boolean,
    onUIMode: () -> Unit,
    onEditMode: () -> Unit,
    onClipMode: OnClipMode,
    onScrollMode: () -> Unit,
    version: String,
    release: Resource<Release>,
    mutedLives: List<Live>,
    onBannedLive: (Int) -> Unit,
    fetchRelease: () -> Unit,
    navigateToConsole: NavigateToConsole,
    experimentalMode: Boolean,
    onExperimentalMode: () -> Unit,
    autoRefresh: Boolean,
    onAutoRefresh: () -> Unit,
    isSSLVerificationEnabled: Boolean,
    onSSLVerificationEnabled: () -> Unit,
    modifier: Modifier = Modifier
) {
    val spacing = LocalSpacing.current

    Row(
        horizontalArrangement = Arrangement.spacedBy(spacing.medium, Alignment.Start),
        modifier = modifier.padding(horizontal = spacing.medium)
    ) {
        PreferencesPart(
            version = version,
            release = release,
            fetchRelease = fetchRelease,
            editMode = editMode,
            clipMode = clipMode,
            onClipMode = onClipMode,
            onFeedManagement = { onFold(Fold.FEED) },
            onScriptManagement = { onFold(Fold.SCRIPT) },
            feedStrategy = feedStrategy,
            connectTimeout = connectTimeout,
            onFeedStrategy = onFeedStrategy,
            onConnectTimeout = onConnectTimeout,
            useCommonUIMode = useCommonUIMode,
            useCommonUIModeEnable = useCommonUIModeEnable,
            onUIMode = onUIMode,
            onEditMode = onEditMode,
            navigateToConsole = navigateToConsole,
            experimentalMode = experimentalMode,
            onExperimentalMode = onExperimentalMode,
            scrollMode = scrollMode,
            onScrollMode = onScrollMode,
            autoRefresh = autoRefresh,
            onAutoRefresh = onAutoRefresh,
            isSSLVerificationEnabled = isSSLVerificationEnabled,
            onSSLVerificationEnabled = onSSLVerificationEnabled,
            modifier = Modifier
                .fillMaxHeight()
                .weight(1f)
        )

        when (fold) {
            Fold.FEED -> {
                FeedManagementPart(
                    title = title,
                    url = url,
                    mutedLives = mutedLives,
                    onBannedLive = onBannedLive,
                    adding = adding,
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
    @FeedStrategy feedStrategy: Int,
    @ConnectTimeout connectTimeout: Int,
    @ClipMode clipMode: Int,
    useCommonUIMode: Boolean,
    useCommonUIModeEnable: Boolean,
    experimentalMode: Boolean,
    editMode: Boolean,
    scrollMode: Boolean,
    version: String,
    release: Resource<Release>,
    onFeedStrategy: OnFeedStrategy,
    onClipMode: OnClipMode,
    onUIMode: () -> Unit,
    onEditMode: () -> Unit,
    onScrollMode: () -> Unit,
    fetchRelease: () -> Unit,
    onFeedManagement: () -> Unit,
    onScriptManagement: () -> Unit,
    onConnectTimeout: () -> Unit,
    onExperimentalMode: () -> Unit,
    navigateToConsole: NavigateToConsole,
    autoRefresh: Boolean,
    onAutoRefresh: () -> Unit,
    isSSLVerificationEnabled: Boolean,
    onSSLVerificationEnabled: () -> Unit,
    modifier: Modifier = Modifier,
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

                TextPreference(
                    title = stringResource(R.string.sync_mode),
                    content = when (feedStrategy) {
                        FeedStrategy.ALL -> stringResource(R.string.sync_mode_all)
                        FeedStrategy.SKIP_FAVORITE -> stringResource(R.string.sync_mode_skip_favourite)
                        else -> ""
                    },
                    onClick = {
                        val target = when (feedStrategy) {
                            FeedStrategy.ALL -> FeedStrategy.SKIP_FAVORITE
                            else -> FeedStrategy.ALL
                        }
                        onFeedStrategy(target)
                    }
                )
                TextPreference(
                    title = stringResource(R.string.clip_mode),
                    content = when (clipMode) {
                        ClipMode.ADAPTIVE -> stringResource(R.string.clip_mode_adaptive)
                        ClipMode.CLIP -> stringResource(R.string.clip_mode_clip)
                        ClipMode.STRETCHED -> stringResource(R.string.clip_mode_stretched)
                        else -> ""
                    },
                    onClick = {
                        val target = when (clipMode) {
                            ClipMode.ADAPTIVE -> ClipMode.CLIP
                            ClipMode.CLIP -> ClipMode.STRETCHED
                            ClipMode.STRETCHED -> ClipMode.ADAPTIVE
                            else -> ClipMode.ADAPTIVE
                        }
                        onClipMode(target)
                    }
                )


                TextPreference(
                    title = stringResource(R.string.connect_timeout),
                    content = "${connectTimeout / 1000}s",
                    onClick = onConnectTimeout
                )
                CheckBoxPreference(
                    title = stringResource(R.string.auto_refresh),
                    subtitle = stringResource(id = R.string.auto_refresh_description),
                    checked = autoRefresh,
                    onCheckedChange = { newValue ->
                        if (newValue != autoRefresh) {
                            onAutoRefresh()
                        }
                    }
                )
                CheckBoxPreference(
                    title = stringResource(R.string.god_mode),
                    subtitle = stringResource(id = R.string.god_mode_description),
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
            Column(
                modifier = Modifier
                    .padding(spacing.medium)
                    .clip(RoundedCornerShape(spacing.medium)),
                verticalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                CheckBoxPreference(
                    title = stringResource(R.string.experimental_mode),
                    subtitle = stringResource(R.string.experimental_mode_description),
                    checked = experimentalMode,
                    onCheckedChange = { newValue ->
                        if (newValue != experimentalMode) {
                            onExperimentalMode()
                        }
                    }
                )
                AnimatedVisibility(
                    visible = experimentalMode,
                    enter = expandVertically(
                        expandFrom = Alignment.Bottom
                    ),
                    exit = shrinkVertically(
                        shrinkTowards = Alignment.Bottom
                    )
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(1.dp)
                    ) {
                        FoldPreference(
                            title = stringResource(R.string.script_management),
                            enabled = true,
                            onClick = onScriptManagement
                        )
                        FoldPreference(
                            title = stringResource(R.string.console_editor),
                            onClick = navigateToConsole
                        )
                        CheckBoxPreference(
                            title = stringResource(R.string.scroll_mode),
                            checked = scrollMode,
                            onCheckedChange = { newValue ->
                                if (newValue != scrollMode) {
                                    onScrollMode()
                                }
                            }
                        )
                        CheckBoxPreference(
                            title = stringResource(R.string.ssl_verification_enabled),
                            subtitle = stringResource(R.string.ssl_verification_enabled_description),
                            checked = isSSLVerificationEnabled,
                            onCheckedChange = { newValue ->
                                if (newValue != isSSLVerificationEnabled) {
                                    onSSLVerificationEnabled()
                                }
                            }
                        )
                    }
                }
            }
        }
        item {
            when (release) {
                Resource.Loading -> {}
                is Resource.Success -> {
                    val uriHandler = LocalUriHandler.current
                    val remoteVersion = release.data.name
                    val name = if (remoteVersion != version) {
                        stringResource(R.string.label_latest_release_version, remoteVersion)
                    } else {
                        stringResource(R.string.label_same_version)
                    }
                    TextButton(name) {
                        if (remoteVersion == version) {
                            fetchRelease()
                        } else {
                            val url =
                                "https://github.com/thxbrop/M3UAndroid/releases/tag/v$remoteVersion"
                            uriHandler.openUri(url)
                        }
                    }
                }
                is Resource.Failure -> {
                    TextButton(
                        text = stringResource(
                            R.string.failed_latest_release_version,
                            release.message.orEmpty()
                        )
                    ) {
                        fetchRelease()
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
    mutedLives: List<Live>,
    onBannedLive: (Int) -> Unit,
    adding: Boolean,
    onTitle: (String) -> Unit,
    onUrl: (String) -> Unit,
    onSubscribe: () -> Unit,
    modifier: Modifier = Modifier
) {
    val spacing = LocalSpacing.current
    val theme = LocalTheme.current
    val focusRequester = remember { FocusRequester() }
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(spacing.small),
        modifier = modifier.padding(spacing.medium)
    ) {
        if (mutedLives.isNotEmpty()) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(spacing.medium)),
                    verticalArrangement = Arrangement.spacedBy(1.dp)
                ) {
                    Text(
                        text = stringResource(R.string.label_muted_lives),
                        style = MaterialTheme.typography.button,
                        color = theme.onTint,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(theme.tint)
                            .padding(
                                vertical = spacing.extraSmall,
                                horizontal = spacing.medium
                            )
                    )
                    mutedLives.forEach { live ->
                        MutedLiveItem(
                            live = live,
                            onBannedLive = { onBannedLive(live.id) },
                            modifier = Modifier.background(theme.surface)
                        )
                    }
                }
            }
        }

        item {
            LabelField(
                text = title,
                enabled = adding,
                placeholder = stringResource(R.string.placeholder_title).uppercase(),
                onValueChange = onTitle,
                keyboardActions = KeyboardActions(
                    onNext = {
                        focusRequester.requestFocus()
                    }
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }

        item {
            LabelField(
                text = url,
                enabled = adding,
                placeholder = stringResource(R.string.placeholder_url).uppercase(),
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
        }

        item {
            val subscribeTextResId =
                if (adding) R.string.label_subscribe else R.string.label_subscribing
            Button(
                enabled = adding,
                text = stringResource(subscribeTextResId),
                onClick = onSubscribe,
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            val subscribeFromClipboardTextResId =
                if (adding) R.string.label_parse_from_clipboard else R.string.label_subscribing
            val clipboardManager = LocalClipboardManager.current
            TextButton(
                text = stringResource(subscribeFromClipboardTextResId),
                enabled = adding,
                onClick = {
                    val clipboardUrl = clipboardManager.getText()?.text.orEmpty()
                    val clipboardTitle = run {
                        val filePath = clipboardUrl.split("/")
                        val fileSplit = filePath.lastOrNull()?.split(".") ?: emptyList()
                        fileSplit.firstOrNull() ?: "Feed_${System.currentTimeMillis()}"
                    }
                    onTitle(clipboardTitle)
                    onUrl(clipboardUrl)
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
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