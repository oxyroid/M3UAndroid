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
import com.m3u.features.setting.fragments.MutedStreamHolder
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
    val useCommonUIModeEnable = (type != Configuration.UI_MODE_TYPE_NORMAL)

    val controller = LocalSoftwareKeyboardController.current
    SettingScreen(
        contentPadding = contentPadding,
        versionName = state.versionName,
        versionCode = state.versionCode,
        title = state.title,
        url = state.url,
        uriWrapper = rememberUriWrapper(state.uri),
        useCommonUIModeEnable = useCommonUIModeEnable,
        navigateToConsole = navigateToConsole,
        mutedStreamHolder = { state.banneds },
        onTitle = { viewModel.onEvent(SettingEvent.OnTitle(it)) },
        onUrl = { viewModel.onEvent(SettingEvent.OnUrl(it)) },
        onSubscribe = {
            controller?.hide()
            viewModel.onEvent(SettingEvent.Subscribe)
        },
        onBanned = { viewModel.onEvent(SettingEvent.OnBanned(it)) },
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
    uriWrapper: UriWrapper,
    onTitle: (String) -> Unit,
    onUrl: (String) -> Unit,
    onSubscribe: () -> Unit,
    useCommonUIModeEnable: Boolean,
    mutedStreamHolder: MutedStreamHolder,
    onBanned: (Int) -> Unit,
    importJavaScript: (Uri) -> Unit,
    navigateToConsole: NavigateToConsole,
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
            AnimatedPane(Modifier) {
                PreferencesFragment(
                    fragment = fragment,
                    contentPadding = contentPadding,
                    versionName = versionName,
                    versionCode = versionCode,
                    useCommonUIModeEnable = useCommonUIModeEnable,
                    navigateToPlaylistManagement = {
                        currentPaneDestination = ListDetailPaneScaffoldRole.Detail
                        fragment = SettingFragment.Subscriptions
                    },
                    navigateToScriptManagement = {
                        currentPaneDestination = ListDetailPaneScaffoldRole.Detail
                        fragment = (SettingFragment.Scripts)
                    },
                    navigateToConsole = navigateToConsole,
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
                                streamHolder = mutedStreamHolder,
                                onBanned = onBanned,
                                onTitle = onTitle,
                                onUrl = onUrl,
                                onSubscribe = onSubscribe,
                                localStorage = localStorage,
                                onLocalStorage = onLocalStorage,
                                openDocument = openDocument,
                                modifier = Modifier.fillMaxSize()
                            )

                        }

                        SettingFragment.Scripts -> {
                            ScriptsFragment(
                                contentPadding = contentPadding,
                                importJavaScript = importJavaScript,
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        else -> {}
                    }
                }
            }
        },
        modifier = modifier
            .fillMaxSize()
            .testTag("feature:setting")
    )

    BackHandler(fragment != SettingFragment.Root) {
        fragment = SettingFragment.Root
        currentPaneDestination = ListDetailPaneScaffoldRole.List
    }
}
