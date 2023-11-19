package com.m3u.features.setting

import android.content.res.Configuration
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.rememberScrollableState
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.m3u.core.annotation.ClipMode
import com.m3u.core.annotation.ConnectTimeout
import com.m3u.core.annotation.FeedStrategy
import com.m3u.core.annotation.OnClipMode
import com.m3u.core.annotation.OnFeedStrategy
import com.m3u.features.setting.fragments.MutedLivesFactory
import com.m3u.features.setting.fragments.PreferencesFragment
import com.m3u.features.setting.fragments.ScriptsFragment
import com.m3u.features.setting.fragments.SubscriptionsFragment
import com.m3u.i18n.R.string
import com.m3u.material.model.LocalSpacing
import com.m3u.ui.Destination
import com.m3u.ui.EventHandler
import com.m3u.ui.Fob
import com.m3u.ui.LocalHelper
import com.m3u.ui.ResumeEvent
import androidx.compose.material3.MaterialTheme

typealias NavigateToConsole = () -> Unit
typealias NavigateToAbout = () -> Unit

@Composable
fun SettingRoute(
    modifier: Modifier = Modifier,
    resume: ResumeEvent,
    contentPadding: PaddingValues,
    viewModel: SettingViewModel = hiltViewModel(),
    navigateToConsole: NavigateToConsole,
    navigateToAbout: NavigateToAbout
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val helper = LocalHelper.current
    val context = LocalContext.current

    EventHandler(resume) {
        helper.actions = emptyList()
    }
    EventHandler(message) { value ->
        when (value) {
            SettingMessage.EmptyTitle -> string.feat_setting_error_empty_title
            SettingMessage.EmptyUrl -> string.feat_setting_error_blank_url
            SettingMessage.EmptyFile -> string.feat_setting_error_unselected_file
            SettingMessage.Enqueued -> string.feat_setting_enqueue_subscribe
        }
            .let(context::getString)
            .let(helper::snake)
    }

    val configuration = LocalConfiguration.current
    val type = configuration.uiMode and Configuration.UI_MODE_TYPE_MASK
    val useCommonUIMode = if (type == Configuration.UI_MODE_TYPE_NORMAL) true
    else state.useCommonUIMode
    val useCommonUIModeEnable = (type != Configuration.UI_MODE_TYPE_NORMAL)

    val controller = LocalSoftwareKeyboardController.current
    SettingScreen(
        contentPadding = contentPadding,
        versionName = state.versionName,
        versionCode = state.versionCode,
        title = state.title,
        url = state.url,
        uriFactory = { state.uri },
        feedStrategy = state.feedStrategy,
        godMode = state.godMode,
        clipMode = state.clipMode,
        scrollMode = state.scrollMode,
        connectTimeout = state.connectTimeout,
        useCommonUIMode = useCommonUIMode,
        useCommonUIModeEnable = useCommonUIModeEnable,
        navigateToConsole = navigateToConsole,
        experimentalMode = state.experimentalMode,
        mutedLivesFactory = { state.banneds },
        onGodMode = { state.godMode = !state.godMode },
        onConnectTimeout = { viewModel.onEvent(SettingEvent.OnConnectTimeout) },
        onTitle = { viewModel.onEvent(SettingEvent.OnTitle(it)) },
        onUrl = { viewModel.onEvent(SettingEvent.OnUrl(it)) },
        onSubscribe = {
            controller?.hide()
            viewModel.onEvent(SettingEvent.Subscribe)
        },
        onScrollMode = { state.scrollMode = !state.scrollMode },
        onFeedStrategy = { viewModel.onEvent(SettingEvent.OnSyncMode) },
        onUIMode = { viewModel.onEvent(SettingEvent.OnUseCommonUIMode) },
        onExperimentalMode = { viewModel.onEvent(SettingEvent.OnExperimentalMode) },
        onBanned = { viewModel.onEvent(SettingEvent.OnBanned(it)) },
        onClipMode = { viewModel.onEvent(SettingEvent.OnClipMode) },
        autoRefresh = state.autoRefresh,
        onAutoRefresh = { state.autoRefresh = !state.autoRefresh },
        isSSLVerification = state.isSSLVerification,
        onSSLVerification = { state.isSSLVerification = !state.isSSLVerification },
        fullInfoPlayer = state.fullInfoPlayer,
        onFullInfoPlayer = { state.fullInfoPlayer = !state.fullInfoPlayer },
        initialRootDestination = state.initialRootDestination,
        scrollDefaultDestination = { viewModel.onEvent(SettingEvent.ScrollDefaultDestination) },
        noPictureMode = state.noPictureMode,
        onNoPictureMode = { state.noPictureMode = !state.noPictureMode },
        cinemaMode = state.cinemaMode,
        onCinemaMode = { state.cinemaMode = !state.cinemaMode },
        importJavaScript = { viewModel.onEvent(SettingEvent.ImportJavaScript(it)) },
        navigateToAbout = navigateToAbout,
        localStorage = state.localStorage,
        onLocalStorage = { viewModel.onEvent(SettingEvent.OnLocalStorage) },
        openDocument = { viewModel.onEvent(SettingEvent.OpenDocument(it)) },
        modifier = modifier.fillMaxSize()
    )
}

@Composable
private fun SettingScreen(
    contentPadding: PaddingValues,
    versionName: String,
    versionCode: Int,
    title: String,
    url: String,
    uriFactory: () -> Uri,
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
    mutedLivesFactory: MutedLivesFactory,
    onBanned: (Int) -> Unit,
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
    initialRootDestination: Int,
    scrollDefaultDestination: () -> Unit,
    noPictureMode: Boolean,
    onNoPictureMode: () -> Unit,
    cinemaMode: Boolean,
    onCinemaMode: () -> Unit,
    importJavaScript: (Uri) -> Unit,
    navigateToAbout: NavigateToAbout,
    localStorage: Boolean,
    onLocalStorage: () -> Unit,
    openDocument: (Uri) -> Unit,
    modifier: Modifier = Modifier
) {
    val helper = LocalHelper.current
    var fragment: SettingFragments by remember { mutableStateOf(SettingFragments.Root) }

    DisposableEffect(fragment) {
        helper.fob = if (fragment == SettingFragments.Root) null
        else Fob(
            rootDestination = Destination.Root.Setting,
            icon = Icons.Rounded.Settings
        ) {
            fragment = SettingFragments.Root
        }

        onDispose {
            helper.fob = null
        }
    }

    Box(
        modifier = Modifier.testTag("features:setting")
    ) {
        val configuration = LocalConfiguration.current
        when (configuration.orientation) {
            Configuration.ORIENTATION_PORTRAIT -> {
                PortraitOrientationContent(
                    contentPadding = contentPadding,
                    versionName = versionName,
                    versionCode = versionCode,
                    fragment = fragment,
                    title = title,
                    url = url,
                    uriFactory = uriFactory,
                    godMode = godMode,
                    connectTimeout = connectTimeout,
                    scrollMode = scrollMode,
                    replaceFragment = { fragment = it },
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
                    mutedLivesFactory = mutedLivesFactory,
                    onBanned = onBanned,
                    autoRefresh = autoRefresh,
                    onAutoRefresh = onAutoRefresh,
                    isSSLVerificationEnabled = isSSLVerification,
                    onSSLVerificationEnabled = onSSLVerification,
                    fullInfoPlayer = fullInfoPlayer,
                    onFullInfoPlayer = onFullInfoPlayer,
                    initialRootDestination = initialRootDestination,
                    onInitialTabIndex = scrollDefaultDestination,
                    noPictureMode = noPictureMode,
                    onNoPictureMode = onNoPictureMode,
                    cinemaMode = cinemaMode,
                    onCinemaMode = onCinemaMode,
                    importJavaScript = importJavaScript,
                    navigateToAbout = navigateToAbout,
                    localStorage = localStorage,
                    onLocalStorage = onLocalStorage,
                    openDocument = openDocument,
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
                    contentPadding = contentPadding,
                    versionName = versionName,
                    versionCode = versionCode,
                    fragment = fragment,
                    title = title,
                    url = url,
                    uriFactory = uriFactory,
                    godMode = godMode,
                    clipMode = clipMode,
                    scrollMode = scrollMode,
                    feedStrategy = feedStrategy,
                    connectTimeout = connectTimeout,
                    useCommonUIMode = useCommonUIMode,
                    useCommonUIModeEnable = useCommonUIModeEnable,
                    replaceFragment = { fragment = it },
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
                    mutedLivesFactory = mutedLivesFactory,
                    onBanned = onBanned,
                    autoRefresh = autoRefresh,
                    onAutoRefresh = onAutoRefresh,
                    isSSLVerificationEnabled = isSSLVerification,
                    onSSLVerificationEnabled = onSSLVerification,
                    fullInfoPlayer = fullInfoPlayer,
                    onFullInfoPlayer = onFullInfoPlayer,
                    initialRootDestination = initialRootDestination,
                    onInitialTabIndex = scrollDefaultDestination,
                    noPictureMode = noPictureMode,
                    onNoPictureMode = onNoPictureMode,
                    cinemaMode = cinemaMode,
                    onCinemaMode = onCinemaMode,
                    importJavaScript = importJavaScript,
                    navigateToAbout = navigateToAbout,
                    localStorage = localStorage,
                    onLocalStorage = onLocalStorage,
                    openDocument = openDocument,
                    modifier = modifier.scrollable(
                        orientation = Orientation.Vertical,
                        state = rememberScrollableState { it }
                    )
                )
            }

            else -> {}
        }
    }
    BackHandler(fragment != SettingFragments.Root) {
        fragment = SettingFragments.Root
    }
}

@Composable
private fun PortraitOrientationContent(
    contentPadding: PaddingValues,
    versionName: String,
    versionCode: Int,
    fragment: SettingFragments,
    title: String,
    url: String,
    uriFactory: () -> Uri,
    @FeedStrategy feedStrategy: Int,
    godMode: Boolean,
    @ClipMode clipMode: Int,
    @ConnectTimeout connectTimeout: Int,
    useCommonUIMode: Boolean,
    useCommonUIModeEnable: Boolean,
    onGodMode: () -> Unit,
    onClipMode: OnClipMode,
    onConnectTimeout: () -> Unit,
    mutedLivesFactory: MutedLivesFactory,
    onBanned: (Int) -> Unit,
    replaceFragment: (SettingFragments) -> Unit,
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
    initialRootDestination: Int,
    onInitialTabIndex: () -> Unit,
    noPictureMode: Boolean,
    onNoPictureMode: () -> Unit,
    cinemaMode: Boolean,
    onCinemaMode: () -> Unit,
    importJavaScript: (Uri) -> Unit,
    navigateToAbout: NavigateToAbout,
    localStorage: Boolean,
    onLocalStorage: () -> Unit,
    openDocument: (Uri) -> Unit,
    modifier: Modifier = Modifier
) {
    // TODO: replace with material3-modal-side-sheet.
    Box {
        PreferencesFragment(
            contentPadding = contentPadding,
            versionName = versionName,
            versionCode = versionCode,
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
            onFeedManagement = { replaceFragment(SettingFragments.Subscriptions) },
            onScriptManagement = { replaceFragment(SettingFragments.Scripts) },
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
            initialRootDestination = initialRootDestination,
            onInitialTabIndex = onInitialTabIndex,
            noPictureMode = noPictureMode,
            onNoPictureMode = onNoPictureMode,
            cinemaMode = cinemaMode,
            onCinemaMode = onCinemaMode,
            navigateToAbout = navigateToAbout,
            modifier = modifier
        )

        AnimatedVisibility(
            visible = fragment != SettingFragments.Root,
            enter = slideInHorizontally { it },
            exit = slideOutHorizontally { it }
        ) {
            when (fragment) {
                SettingFragments.Subscriptions -> {
                    SubscriptionsFragment(
                        contentPadding = contentPadding,
                        title = title,
                        url = url,
                        uriFactory = uriFactory,
                        mutedLivesFactory = mutedLivesFactory,
                        onBanned = onBanned,
                        onTitle = onTitle,
                        onUrl = onUrl,
                        onSubscribe = onSubscribe,
                        localStorage = localStorage,
                        onLocalStorage = onLocalStorage,
                        openDocument = openDocument,
                        modifier = modifier.background(MaterialTheme.colorScheme.background)
                    )
                }

                SettingFragments.Scripts -> {
                    ScriptsFragment(
                        contentPadding = contentPadding,
                        importJavaScript = importJavaScript,
                        modifier = modifier.background(MaterialTheme.colorScheme.background)
                    )
                }

                else -> {}
            }
        }
    }
}

@Composable
private fun LandscapeOrientationContent(
    contentPadding: PaddingValues,
    versionName: String,
    versionCode: Int,
    fragment: SettingFragments,
    title: String,
    url: String,
    uriFactory: () -> Uri,
    godMode: Boolean,
    @ClipMode clipMode: Int,
    replaceFragment: (SettingFragments) -> Unit,
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
    mutedLivesFactory: MutedLivesFactory,
    onBanned: (Int) -> Unit,
    navigateToConsole: NavigateToConsole,
    experimentalMode: Boolean,
    onExperimentalMode: () -> Unit,
    autoRefresh: Boolean,
    onAutoRefresh: () -> Unit,
    isSSLVerificationEnabled: Boolean,
    onSSLVerificationEnabled: () -> Unit,
    fullInfoPlayer: Boolean,
    onFullInfoPlayer: () -> Unit,
    initialRootDestination: Int,
    onInitialTabIndex: () -> Unit,
    noPictureMode: Boolean,
    onNoPictureMode: () -> Unit,
    cinemaMode: Boolean,
    onCinemaMode: () -> Unit,
    importJavaScript: (Uri) -> Unit,
    navigateToAbout: NavigateToAbout,
    localStorage: Boolean,
    onLocalStorage: () -> Unit,
    openDocument: (Uri) -> Unit,
    modifier: Modifier = Modifier
) {
    val spacing = LocalSpacing.current

    // TODO: replace with material3-modal-side-sheet.
    Row(
        horizontalArrangement = Arrangement.spacedBy(spacing.medium, Alignment.Start),
        modifier = modifier.padding(horizontal = spacing.medium)
    ) {
        PreferencesFragment(
            contentPadding = contentPadding,
            versionName = versionName,
            versionCode = versionCode,
            godMode = godMode,
            clipMode = clipMode,
            onClipMode = onClipMode,
            onFeedManagement = { replaceFragment(SettingFragments.Subscriptions) },
            onScriptManagement = { replaceFragment(SettingFragments.Scripts) },
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
            initialRootDestination = initialRootDestination,
            onInitialTabIndex = onInitialTabIndex,
            noPictureMode = noPictureMode,
            onNoPictureMode = onNoPictureMode,
            cinemaMode = cinemaMode,
            onCinemaMode = onCinemaMode,
            navigateToAbout = navigateToAbout,
            modifier = Modifier
                .fillMaxHeight()
                .weight(1f)
        )

        when (fragment) {
            SettingFragments.Subscriptions -> {
                SubscriptionsFragment(
                    contentPadding = contentPadding,
                    title = title,
                    url = url,
                    uriFactory = uriFactory,
                    mutedLivesFactory = mutedLivesFactory,
                    onBanned = onBanned,
                    onTitle = onTitle,
                    onUrl = onUrl,
                    onSubscribe = onSubscribe,
                    localStorage = localStorage,
                    onLocalStorage = onLocalStorage,
                    openDocument = openDocument,
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight(1f)
                )
            }

            SettingFragments.Scripts -> {
                ScriptsFragment(
                    contentPadding = contentPadding,
                    importJavaScript = importJavaScript,
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight(1f)
                )
            }

            else -> {}
        }
    }
}
