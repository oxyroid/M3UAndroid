package com.m3u.features.setting

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.adaptive.AnimatedPane
import androidx.compose.material3.adaptive.ListDetailPaneScaffold
import androidx.compose.material3.adaptive.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.calculateListDetailPaneScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.m3u.core.architecture.pref.LocalPref
import com.m3u.core.util.basic.title
import com.m3u.data.database.model.Stream
import com.m3u.features.setting.fragments.AppearanceFragment
import com.m3u.features.setting.fragments.ColorPack
import com.m3u.features.setting.fragments.ScriptsFragment
import com.m3u.features.setting.fragments.SubscriptionsFragment
import com.m3u.features.setting.fragments.preferences.PreferencesFragment
import com.m3u.i18n.R.string
import com.m3u.ui.Destination.Root.Setting.SettingFragment
import com.m3u.ui.EventHandler
import com.m3u.ui.ResumeEvent
import com.m3u.ui.helper.LocalHelper
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

@Composable
fun SettingRoute(
    resume: ResumeEvent,
    contentPadding: PaddingValues,
    navigateToAbout: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingViewModel = hiltViewModel(),
    targetFragment: SettingFragment = SettingFragment.Root
) {
    val title = stringResource(string.ui_title_setting)
    val controller = LocalSoftwareKeyboardController.current

    val state by viewModel.state.collectAsStateWithLifecycle()
    val packs by viewModel.packs.collectAsStateWithLifecycle()
    val banneds by viewModel.banneds.collectAsStateWithLifecycle()
    val helper = LocalHelper.current

    EventHandler(resume, title) {
        helper.title = title.title()
        helper.actions = persistentListOf()
    }

    SettingScreen(
        contentPadding = contentPadding,
        versionName = state.versionName,
        versionCode = state.versionCode,
        title = state.title,
        url = state.url,
        uriWrapper = rememberUriWrapper(state.uri),
        targetFragment = targetFragment,
        banneds = banneds,
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
        packs = packs,
        onArgbMenu = { /*todo*/ },
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
    targetFragment: SettingFragment,
    onTitle: (String) -> Unit,
    onUrl: (String) -> Unit,
    onSubscribe: () -> Unit,
    banneds: ImmutableList<Stream>,
    onBanned: (Int) -> Unit,
    importJavaScript: (Uri) -> Unit,
    navigateToAbout: () -> Unit,
    localStorage: Boolean,
    onLocalStorage: () -> Unit,
    openDocument: (Uri) -> Unit,
    packs: ImmutableList<ColorPack>,
    onArgbMenu: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val helper = LocalHelper.current
    val pref = LocalPref.current

    val rootTitle = stringResource(string.ui_title_setting)
    val playlistTitle = stringResource(string.feat_setting_playlist_management)
    val scriptTitle = stringResource(string.feat_setting_script_management)
    val appearanceTitle = stringResource(string.feat_setting_appearance)

    val colorArgb = pref.colorArgb

    var fragment: SettingFragment by rememberSaveable(targetFragment) {
        mutableStateOf(targetFragment)
    }

    LaunchedEffect(fragment) {
        helper.title = when (fragment) {
            SettingFragment.Root -> rootTitle
            SettingFragment.Playlists -> playlistTitle
            SettingFragment.Scripts -> scriptTitle
            SettingFragment.Appearance -> appearanceTitle
        }.title()
        helper.deep += when (fragment) {
            SettingFragment.Root -> -1
            else -> 1
        }
    }

    var currentPaneDestination by remember(targetFragment) {
        mutableStateOf(
            when (targetFragment) {
                SettingFragment.Root -> ListDetailPaneScaffoldRole.List
                else -> ListDetailPaneScaffoldRole.Detail
            }
        )
    }
    val scaffoldState = calculateListDetailPaneScaffoldState(
        currentPaneDestination = currentPaneDestination
    )

    ListDetailPaneScaffold(
        scaffoldState = scaffoldState,
        windowInsets = WindowInsets(0),
        listPane = {
            PreferencesFragment(
                fragment = fragment,
                contentPadding = contentPadding,
                versionName = versionName,
                versionCode = versionCode,
                navigateToPlaylistManagement = {
                    currentPaneDestination = ListDetailPaneScaffoldRole.Detail
                    fragment = SettingFragment.Playlists
                },
                navigateToScriptManagement = {
                    currentPaneDestination = ListDetailPaneScaffoldRole.Detail
                    fragment = SettingFragment.Scripts
                },
                navigateToThemeSelector = {
                    currentPaneDestination = ListDetailPaneScaffoldRole.Detail
                    fragment = SettingFragment.Appearance
                },
                navigateToAbout = navigateToAbout,
                modifier = Modifier.fillMaxSize()
            )
        },
        detailPane = {
            if (fragment != SettingFragment.Root) {
                AnimatedPane(Modifier) {
                    when (fragment) {
                        SettingFragment.Playlists -> {
                            SubscriptionsFragment(
                                contentPadding = contentPadding,
                                title = title,
                                url = url,
                                uriWrapper = uriWrapper,
                                banneds = banneds,
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

                        SettingFragment.Appearance -> {
                            AppearanceFragment(
                                packs = packs,
                                colorArgb = colorArgb,
                                onArgbMenu = onArgbMenu,
                                contentPadding = contentPadding
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
