package com.m3u.features.setting

import android.content.res.Configuration
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.adaptive.AnimatedPane
import androidx.compose.material3.adaptive.ListDetailPaneScaffold
import androidx.compose.material3.adaptive.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.calculateListDetailPaneScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.m3u.core.annotation.ClipMode
import com.m3u.core.annotation.ConnectTimeout
import com.m3u.core.annotation.FeedStrategy
import com.m3u.core.annotation.OnClipMode
import com.m3u.core.annotation.OnFeedStrategy
import com.m3u.features.setting.fragments.MutedLiveHolder
import com.m3u.features.setting.fragments.PreferencesFragment
import com.m3u.features.setting.fragments.ScriptsFragment
import com.m3u.features.setting.fragments.SubscriptionsFragment
import com.m3u.ui.Destination
import com.m3u.ui.EventHandler
import com.m3u.ui.Fob
import com.m3u.ui.LocalHelper
import com.m3u.ui.MessageEventHandler
import com.m3u.ui.ResumeEvent

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

    EventHandler(resume) {
        helper.actions = emptyList()
    }

    MessageEventHandler(message)

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
        uriWrapper = rememberUriWrapper(state.uri),
        feedStrategy = state.feedStrategy,
        godMode = state.godMode,
        clipMode = state.clipMode,
        scrollMode = state.scrollMode,
        connectTimeout = state.connectTimeout,
        useCommonUIMode = useCommonUIMode,
        useCommonUIModeEnable = useCommonUIModeEnable,
        navigateToConsole = navigateToConsole,
        experimentalMode = state.experimentalMode,
        mutedLiveHolder = { state.banneds },
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
        useDynamicColors = state.useDynamicColors,
        onUseDynamicColors = { viewModel.onEvent(SettingEvent.OnUseDynamicColors) },
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
    uriWrapper: UriWrapper,
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
    useDynamicColors: Boolean,
    onUseDynamicColors: () -> Unit,
    mutedLiveHolder: MutedLiveHolder,
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
    var fragment: SettingFragment by rememberSaveable { mutableStateOf(SettingFragment.Root) }
    var currentPaneDestination by rememberSaveable {
        mutableStateOf(ListDetailPaneScaffoldRole.List)
    }
    val scaffoldState = calculateListDetailPaneScaffoldState(
        currentPaneDestination = currentPaneDestination
    )

    DisposableEffect(fragment) {
        helper.fob = if (fragment == SettingFragment.Root) null
        else Fob(
            rootDestination = Destination.Root.Setting,
            icon = Icons.Rounded.Settings
        ) {
            fragment = SettingFragment.Root
            currentPaneDestination = ListDetailPaneScaffoldRole.List
        }

        onDispose {
            helper.fob = null
        }
    }

    ListDetailPaneScaffold(
        scaffoldState = scaffoldState,
        listPane = {
            AnimatedPane(Modifier.fillMaxSize()) {
                PreferencesFragment(
                    fragment = fragment,
                    contentPadding = contentPadding,
                    versionName = versionName,
                    versionCode = versionCode,
                    feedStrategy = feedStrategy,
                    useCommonUIMode = useCommonUIMode,
                    useCommonUIModeEnable = useCommonUIModeEnable,
                    useDynamicColors = useDynamicColors,
                    onUseDynamicColors = onUseDynamicColors,
                    godMode = godMode,
                    clipMode = clipMode,
                    connectTimeout = connectTimeout,
                    onConnectTimeout = onConnectTimeout,
                    onFeedStrategy = onFeedStrategy,
                    onClipMode = onClipMode,
                    onUIMode = { },
                    onGodMode = onGodMode,
                    onFeedManagement = {
                        currentPaneDestination = ListDetailPaneScaffoldRole.Detail
                        fragment = SettingFragment.Subscriptions
                    },
                    onScriptManagement = {
                        currentPaneDestination = ListDetailPaneScaffoldRole.Detail
                        fragment = (SettingFragment.Scripts)
                    },
                    navigateToConsole = navigateToConsole,
                    experimentalMode = experimentalMode,
                    onExperimentalMode = onExperimentalMode,
                    scrollMode = scrollMode,
                    onScrollMode = onScrollMode,
                    autoRefresh = autoRefresh,
                    onAutoRefresh = onAutoRefresh,
                    isSSLVerification = isSSLVerification,
                    onSSLVerification = onSSLVerification,
                    fullInfoPlayer = fullInfoPlayer,
                    onFullInfoPlayer = onFullInfoPlayer,
                    initialRootDestination = initialRootDestination,
                    onInitialTabIndex = scrollDefaultDestination,
                    noPictureMode = noPictureMode,
                    onNoPictureMode = onNoPictureMode,
                    cinemaMode = cinemaMode,
                    onCinemaMode = onCinemaMode,
                    navigateToAbout = navigateToAbout,
                    modifier = Modifier.fillMaxSize()
                )
            }
        },
        detailPane = {
            if (fragment != SettingFragment.Root) {
                AnimatedPane(Modifier) {
                    when (fragment) {
                        SettingFragment.Subscriptions -> {
                            SubscriptionsFragment(
                                contentPadding = contentPadding,
                                title = title,
                                url = url,
                                uriWrapper = uriWrapper,
                                mutedLiveHolder = mutedLiveHolder,
                                onBanned = onBanned,
                                onTitle = onTitle,
                                onUrl = onUrl,
                                onSubscribe = onSubscribe,
                                localStorage = localStorage,
                                onLocalStorage = onLocalStorage,
                                openDocument = openDocument,
                                modifier = modifier.fillMaxSize()
                            )

                        }

                        SettingFragment.Scripts -> {
                            ScriptsFragment(
                                contentPadding = contentPadding,
                                importJavaScript = importJavaScript,
                                modifier = modifier.fillMaxSize()
                            )
                        }

                        else -> {}
                    }
                }
            }
        },
        modifier = Modifier
            .fillMaxSize()
            .testTag("feature:setting")
    )

    BackHandler(fragment != SettingFragment.Root) {
        fragment = SettingFragment.Root
        currentPaneDestination = ListDetailPaneScaffoldRole.List
    }
}
