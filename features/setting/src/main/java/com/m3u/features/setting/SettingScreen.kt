package com.m3u.features.setting

import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.provider.Settings
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
import androidx.compose.runtime.LaunchedEffect
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
import com.m3u.data.database.entity.Live
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

typealias NavigateToConsole = () -> Unit

@Composable
fun SettingRoute(
    modifier: Modifier = Modifier,
    isCurrentPage: Boolean,
    viewModel: SettingViewModel = hiltViewModel(),
    navigateToConsole: NavigateToConsole
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val helper = LocalHelper.current

    LaunchedEffect(isCurrentPage) {
        if (isCurrentPage) {
            helper.actions = emptyList()
        }
    }

    val configuration = LocalConfiguration.current
    val type = configuration.uiMode and Configuration.UI_MODE_TYPE_MASK
    val useCommonUIMode = if (type == Configuration.UI_MODE_TYPE_NORMAL) true
    else state.useCommonUIMode
    val useCommonUIModeEnable = (type != Configuration.UI_MODE_TYPE_NORMAL)
    SettingScreen(
        version = state.version,
        adding = !state.adding,
        title = state.title,
        url = state.url,
        feedStrategy = state.feedStrategy,
        godMode = state.godMode,
        clipMode = state.clipMode,
        scrollMode = state.scrollMode,
        connectTimeout = state.connectTimeout,
        useCommonUIMode = useCommonUIMode,
        useCommonUIModeEnable = useCommonUIModeEnable,
        navigateToConsole = navigateToConsole,
        experimentalMode = state.experimentalMode,
        mutedLives = state.mutedLives,
        onGodMode = { state.godMode = !state.godMode },
        onConnectTimeout = { viewModel.onEvent(SettingEvent.OnConnectTimeout) },
        onTitle = { viewModel.onEvent(SettingEvent.OnTitle(it)) },
        onUrl = { viewModel.onEvent(SettingEvent.OnUrl(it)) },
        onSubscribe = { viewModel.onEvent(SettingEvent.OnSubscribe) },
        onScrollMode = { state.scrollMode = !state.scrollMode },
        onFeedStrategy = { viewModel.onEvent(SettingEvent.OnSyncMode) },
        onUIMode = { viewModel.onEvent(SettingEvent.OnUseCommonUIMode) },
        onExperimentalMode = { viewModel.onEvent(SettingEvent.OnExperimentalMode) },
        onBannedLive = { viewModel.onEvent(SettingEvent.OnBannedLive(it)) },
        onClipMode = { viewModel.onEvent(SettingEvent.OnClipMode) },
        autoRefresh = state.autoRefresh,
        onAutoRefresh = { state.autoRefresh = !state.autoRefresh },
        isSSLVerification = state.isSSLVerification,
        onSSLVerification = { state.isSSLVerification = !state.isSSLVerification },
        fullInfoPlayer = state.fullInfoPlayer,
        onFullInfoPlayer = { state.fullInfoPlayer = !state.fullInfoPlayer },
        initialTabTitle = remember(state.initialTabIndex, state.tabTitles) {
            state.tabTitles.getOrNull(state.initialTabIndex).orEmpty()
        },
        onInitialTabIndex = { viewModel.onEvent(SettingEvent.OnInitialTabIndex) },
        noPictureMode = state.noPictureMode,
        onNoPictureMode = { state.noPictureMode = !state.noPictureMode },
        silentMode = state.silentMode,
        onSilentMode = { viewModel.onEvent(SettingEvent.OnSilentMode) },
        cinemaMode = state.cinemaMode,
        onCinemaMode = { state.cinemaMode = !state.cinemaMode },
        modifier = modifier.fillMaxSize()
    )
}

@Composable
private fun SettingScreen(
    version: String,
    adding: Boolean,
    title: String,
    url: String,
    @FeedStrategy feedStrategy: Int,
    godMode: Boolean,
    @ClipMode clipMode: Int,
    @ConnectTimeout connectTimeout: Int,
    scrollMode: Boolean,
    onGodMode: () -> Unit,
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
    isSSLVerification: Boolean,
    onSSLVerification: () -> Unit,
    fullInfoPlayer: Boolean,
    onFullInfoPlayer: () -> Unit,
    initialTabTitle: String,
    onInitialTabIndex: () -> Unit,
    noPictureMode: Boolean,
    onNoPictureMode: () -> Unit,
    silentMode: Boolean,
    onSilentMode: () -> Unit,
    cinemaMode: Boolean,
    onCinemaMode: () -> Unit,
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
                    version = version,
                    fold = fold,
                    title = title,
                    url = url,
                    adding = adding,
                    godMode = godMode,
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
                    onGodMode = onGodMode,
                    onScrollMode = onScrollMode,
                    useCommonUIMode = useCommonUIMode,
                    navigateToConsole = navigateToConsole,
                    useCommonUIModeEnable = useCommonUIModeEnable,
                    experimentalMode = experimentalMode,
                    onExperimentalMode = onExperimentalMode,
                    mutedLives = mutedLives,
                    onBannedLive = onBannedLive,
                    autoRefresh = autoRefresh,
                    onAutoRefresh = onAutoRefresh,
                    isSSLVerificationEnabled = isSSLVerification,
                    onSSLVerificationEnabled = onSSLVerification,
                    fullInfoPlayer = fullInfoPlayer,
                    onFullInfoPlayer = onFullInfoPlayer,
                    initialTabTitle = initialTabTitle,
                    onInitialTabIndex = onInitialTabIndex,
                    noPictureMode = noPictureMode,
                    onNoPictureMode = onNoPictureMode,
                    silentMode = silentMode,
                    onSilentMode = onSilentMode,
                    cinemaMode = cinemaMode,
                    onCinemaMode = onCinemaMode,
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
                    version = version,
                    fold = fold,
                    title = title,
                    url = url,
                    adding = adding,
                    godMode = godMode,
                    clipMode = clipMode,
                    scrollMode = scrollMode,
                    feedStrategy = feedStrategy,
                    connectTimeout = connectTimeout,
                    useCommonUIMode = useCommonUIMode,
                    useCommonUIModeEnable = useCommonUIModeEnable,
                    onFold = { fold = it },
                    onTitle = onTitle,
                    onUrl = onUrl,
                    onClipMode = onClipMode,
                    onScrollMode = onScrollMode,
                    onSubscribe = onSubscribe,
                    onFeedStrategy = onFeedStrategy,
                    onGodMode = onGodMode,
                    onConnectTimeout = onConnectTimeout,
                    onUIMode = onUIMode,
                    navigateToConsole = navigateToConsole,
                    experimentalMode = experimentalMode,
                    onExperimentalMode = onExperimentalMode,
                    mutedLives = mutedLives,
                    onBannedLive = onBannedLive,
                    autoRefresh = autoRefresh,
                    onAutoRefresh = onAutoRefresh,
                    isSSLVerificationEnabled = isSSLVerification,
                    onSSLVerificationEnabled = onSSLVerification,
                    fullInfoPlayer = fullInfoPlayer,
                    onFullInfoPlayer = onFullInfoPlayer,
                    initialTabTitle = initialTabTitle,
                    onInitialTabIndex = onInitialTabIndex,
                    noPictureMode = noPictureMode,
                    onNoPictureMode = onNoPictureMode,
                    silentMode = silentMode,
                    onSilentMode = onSilentMode,
                    cinemaMode = cinemaMode,
                    onCinemaMode = onCinemaMode,
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
    version: String,
    fold: Fold,
    title: String,
    url: String,
    @FeedStrategy feedStrategy: Int,
    godMode: Boolean,
    @ClipMode clipMode: Int,
    @ConnectTimeout connectTimeout: Int,
    useCommonUIMode: Boolean,
    useCommonUIModeEnable: Boolean,
    onGodMode: () -> Unit,
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
    navigateToConsole: NavigateToConsole,
    experimentalMode: Boolean,
    onScrollMode: () -> Unit,
    scrollMode: Boolean,
    onExperimentalMode: () -> Unit,
    autoRefresh: Boolean,
    onAutoRefresh: () -> Unit,
    isSSLVerificationEnabled: Boolean,
    onSSLVerificationEnabled: () -> Unit,
    fullInfoPlayer: Boolean,
    onFullInfoPlayer: () -> Unit,
    initialTabTitle: String,
    onInitialTabIndex: () -> Unit,
    noPictureMode: Boolean,
    onNoPictureMode: () -> Unit,
    silentMode: Boolean,
    onSilentMode: () -> Unit,
    cinemaMode: Boolean,
    onCinemaMode: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box {
        PreferencesPart(
            version = version,
            feedStrategy = feedStrategy,
            useCommonUIMode = useCommonUIMode,
            useCommonUIModeEnable = useCommonUIModeEnable,
            godMode = godMode,
            clipMode = clipMode,
            connectTimeout = connectTimeout,
            onConnectTimeout = onConnectTimeout,
            onFeedStrategy = onFeedStrategy,
            onClipMode = onClipMode,
            onUIMode = { },
            onGodMode = onGodMode,
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
            fullInfoPlayer = fullInfoPlayer,
            onFullInfoPlayer = onFullInfoPlayer,
            initialTabTitle = initialTabTitle,
            onInitialTabIndex = onInitialTabIndex,
            noPictureMode = noPictureMode,
            onNoPictureMode = onNoPictureMode,
            silentMode = silentMode,
            onSilentMode = onSilentMode,
            cinemaMode = cinemaMode,
            onCinemaMode = onCinemaMode,
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
    version: String,
    fold: Fold,
    title: String,
    url: String,
    godMode: Boolean,
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
    onGodMode: () -> Unit,
    onClipMode: OnClipMode,
    onScrollMode: () -> Unit,
    mutedLives: List<Live>,
    onBannedLive: (Int) -> Unit,
    navigateToConsole: NavigateToConsole,
    experimentalMode: Boolean,
    onExperimentalMode: () -> Unit,
    autoRefresh: Boolean,
    onAutoRefresh: () -> Unit,
    isSSLVerificationEnabled: Boolean,
    onSSLVerificationEnabled: () -> Unit,
    fullInfoPlayer: Boolean,
    onFullInfoPlayer: () -> Unit,
    initialTabTitle: String,
    onInitialTabIndex: () -> Unit,
    noPictureMode: Boolean,
    onNoPictureMode: () -> Unit,
    silentMode: Boolean,
    onSilentMode: () -> Unit,
    cinemaMode: Boolean,
    onCinemaMode: () -> Unit,
    modifier: Modifier = Modifier
) {
    val spacing = LocalSpacing.current

    Row(
        horizontalArrangement = Arrangement.spacedBy(spacing.medium, Alignment.Start),
        modifier = modifier.padding(horizontal = spacing.medium)
    ) {
        PreferencesPart(
            version = version,
            godMode = godMode,
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
            onGodMode = onGodMode,
            navigateToConsole = navigateToConsole,
            experimentalMode = experimentalMode,
            onExperimentalMode = onExperimentalMode,
            scrollMode = scrollMode,
            onScrollMode = onScrollMode,
            autoRefresh = autoRefresh,
            onAutoRefresh = onAutoRefresh,
            isSSLVerificationEnabled = isSSLVerificationEnabled,
            onSSLVerificationEnabled = onSSLVerificationEnabled,
            fullInfoPlayer = fullInfoPlayer,
            onFullInfoPlayer = onFullInfoPlayer,
            initialTabTitle = initialTabTitle,
            onInitialTabIndex = onInitialTabIndex,
            noPictureMode = noPictureMode,
            onNoPictureMode = onNoPictureMode,
            silentMode = silentMode,
            onSilentMode = onSilentMode,
            cinemaMode = cinemaMode,
            onCinemaMode = onCinemaMode,
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
    version: String,
    @FeedStrategy feedStrategy: Int,
    @ConnectTimeout connectTimeout: Int,
    @ClipMode clipMode: Int,
    useCommonUIMode: Boolean,
    useCommonUIModeEnable: Boolean,
    experimentalMode: Boolean,
    godMode: Boolean,
    scrollMode: Boolean,
    fullInfoPlayer: Boolean,
    onFeedStrategy: OnFeedStrategy,
    onClipMode: OnClipMode,
    onUIMode: () -> Unit,
    onGodMode: () -> Unit,
    onScrollMode: () -> Unit,
    onFeedManagement: () -> Unit,
    onScriptManagement: () -> Unit,
    onConnectTimeout: () -> Unit,
    onExperimentalMode: () -> Unit,
    onFullInfoPlayer: () -> Unit,
    navigateToConsole: NavigateToConsole,
    autoRefresh: Boolean,
    onAutoRefresh: () -> Unit,
    isSSLVerificationEnabled: Boolean,
    onSSLVerificationEnabled: () -> Unit,
    initialTabTitle: String,
    onInitialTabIndex: () -> Unit,
    noPictureMode: Boolean,
    onNoPictureMode: () -> Unit,
    silentMode: Boolean,
    onSilentMode: () -> Unit,
    cinemaMode: Boolean,
    onCinemaMode: () -> Unit,
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
                    onClick = onFeedStrategy
                )
                TextPreference(
                    title = stringResource(R.string.clip_mode),
                    content = when (clipMode) {
                        ClipMode.ADAPTIVE -> stringResource(R.string.clip_mode_adaptive)
                        ClipMode.CLIP -> stringResource(R.string.clip_mode_clip)
                        ClipMode.STRETCHED -> stringResource(R.string.clip_mode_stretched)
                        else -> ""
                    },
                    onClick = onClipMode
                )
                TextPreference(
                    title = stringResource(R.string.connect_timeout),
                    content = "${connectTimeout / 1000}s",
                    onClick = onConnectTimeout
                )
                TextPreference(
                    title = stringResource(R.string.initial_tab),
                    content = initialTabTitle,
                    onClick = onInitialTabIndex
                )
                CheckBoxPreference(
                    title = stringResource(R.string.auto_refresh),
                    subtitle = stringResource(R.string.auto_refresh_description),
                    checked = autoRefresh,
                    onCheckedChange = { newValue ->
                        if (newValue != autoRefresh) {
                            onAutoRefresh()
                        }
                    }
                )
                CheckBoxPreference(
                    title = stringResource(R.string.no_picture_mode),
                    subtitle = stringResource(R.string.no_picture_mode_description),
                    checked = noPictureMode,
                    onCheckedChange = { newValue ->
                        if (newValue != noPictureMode) {
                            onNoPictureMode()
                        }
                    }
                )
                CheckBoxPreference(
                    title = stringResource(R.string.full_info_player),
                    subtitle = stringResource(R.string.full_info_player_description),
                    checked = fullInfoPlayer,
                    onCheckedChange = { newValue ->
                        if (newValue != fullInfoPlayer) {
                            onFullInfoPlayer()
                        }
                    }
                )
                CheckBoxPreference(
                    title = stringResource(R.string.silent_mode),
                    subtitle = stringResource(R.string.silent_mode_description),
                    checked = silentMode,
                    onCheckedChange = { newValue ->
                        if (newValue != silentMode) {
                            onSilentMode()
                        }
                    }
                )
                CheckBoxPreference(
                    title = stringResource(R.string.god_mode),
                    subtitle = stringResource(R.string.god_mode_description),
                    checked = godMode,
                    onCheckedChange = { newValue ->
                        if (newValue != godMode) {
                            onGodMode()
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
                        CheckBoxPreference(
                            title = stringResource(R.string.cinema_mode),
                            subtitle = stringResource(R.string.cinema_mode_description),
                            checked = cinemaMode,
                            onCheckedChange = { newValue ->
                                if (newValue != cinemaMode) {
                                    onCinemaMode()
                                }
                            }
                        )
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
            Column(
                modifier = Modifier
                    .padding(spacing.medium)
                    .clip(RoundedCornerShape(spacing.medium)),
                verticalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                val context = LocalContext.current
                FoldPreference(
                    title = stringResource(R.string.system_setting),
                    onClick = {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", context.packageName, null)
                        }
                        context.startActivity(intent)
                    }
                )
                FoldPreference(
                    title = stringResource(R.string.app_version),
                    subtitle = version,
                    enabled = false,
                    onClick = {}
                )
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