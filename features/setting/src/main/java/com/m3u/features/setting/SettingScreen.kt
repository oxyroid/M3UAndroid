package com.m3u.features.setting

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChangeCircle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.adaptive.AnimatedPane
import androidx.compose.material3.adaptive.HingePolicy
import androidx.compose.material3.adaptive.ListDetailPaneScaffold
import androidx.compose.material3.adaptive.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.PaneScaffoldDirective
import androidx.compose.material3.adaptive.Posture
import androidx.compose.material3.adaptive.ThreePaneScaffoldDestinationItem
import androidx.compose.material3.adaptive.WindowAdaptiveInfo
import androidx.compose.material3.adaptive.allVerticalHingeBounds
import androidx.compose.material3.adaptive.calculateListDetailPaneScaffoldState
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.occludingVerticalHingeBounds
import androidx.compose.material3.adaptive.separatingVerticalHingeBounds
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.m3u.core.architecture.pref.LocalPref
import com.m3u.core.util.basic.title
import com.m3u.data.database.model.ColorPack
import com.m3u.data.database.model.DataSource
import com.m3u.data.database.model.Playlist
import com.m3u.data.database.model.Stream
import com.m3u.features.setting.components.CanvasBottomSheet
import com.m3u.features.setting.fragments.AppearanceFragment
import com.m3u.features.setting.fragments.SubscriptionsFragment
import com.m3u.features.setting.fragments.preferences.PreferencesFragment
import com.m3u.i18n.R.string
import com.m3u.material.ktx.isTelevision
import com.m3u.material.model.LocalHazeState
import com.m3u.ui.Destination
import com.m3u.ui.EventBus
import com.m3u.ui.EventHandler
import com.m3u.ui.LocalVisiblePageInfos
import com.m3u.ui.SettingFragment
import com.m3u.ui.helper.Fob
import com.m3u.ui.helper.LocalHelper
import dev.chrisbanes.haze.HazeDefaults
import dev.chrisbanes.haze.haze
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

@Composable
fun SettingRoute(
    contentPadding: PaddingValues,
    navigateToAbout: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingViewModel = hiltViewModel()
) {
    val tv = isTelevision()
    val controller = LocalSoftwareKeyboardController.current

    val state by viewModel.state.collectAsStateWithLifecycle()
    val colorPacks by viewModel.colorPacks.collectAsStateWithLifecycle()
    val hiddenStreams by viewModel.hiddenStreams.collectAsStateWithLifecycle()
    val hiddenCategoriesWithPlaylists by viewModel.hiddenCategoriesWithPlaylists.collectAsStateWithLifecycle()
    val backingUpOrRestoring by viewModel.backingUpOrRestoring.collectAsStateWithLifecycle()

    val sheetState = rememberModalBottomSheetState()
    var colorInt: Int? by remember { mutableStateOf(null) }
    var isDark: Boolean? by remember { mutableStateOf(null) }

    val createDocumentLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/*")) { uri ->
            uri ?: return@rememberLauncherForActivityResult
            viewModel.backup(uri)
        }
    val openDocumentLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri ?: return@rememberLauncherForActivityResult
            viewModel.restore(uri)
        }

    val backup = {
        val filename = "Backup_${System.currentTimeMillis()}.txt"
        createDocumentLauncher.launch(filename)
    }
    val restore = {
        openDocumentLauncher.launch(arrayOf("text/*"))
    }

    Box {
        SettingScreen(
            contentPadding = contentPadding,
            versionName = state.versionName,
            versionCode = state.versionCode,
            title = state.title,
            url = state.url,
            uri = state.uri,
            backingUpOrRestoring = backingUpOrRestoring,
            hiddenStreams = hiddenStreams,
            hiddenCategoriesWithPlaylists = hiddenCategoriesWithPlaylists,
            onTitle = { viewModel.onEvent(SettingEvent.OnTitle(it)) },
            onUrl = { viewModel.onEvent(SettingEvent.OnUrl(it)) },
            onSubscribe = {
                controller?.hide()
                viewModel.onEvent(SettingEvent.Subscribe)
            },
            onUnhideStream = { viewModel.onUnhideStream(it) },
            onUnhidePlaylistCategory = { playlistUrl, group ->
                viewModel.onUnhidePlaylistCategory(playlistUrl, group)
            },
            navigateToAbout = navigateToAbout,
            localStorage = state.localStorage,
            onLocalStorage = { viewModel.onEvent(SettingEvent.OnLocalStorage) },
            subscribeForTv = viewModel.forTv,
            onSubscribeForTv = { viewModel.forTv = !viewModel.forTv },
            openDocument = { viewModel.onEvent(SettingEvent.OpenDocument(it)) },
            backup = backup,
            restore = restore,
            onClipboard = { viewModel.onClipboard(it) },
            colorPacks = colorPacks,
            openColorCanvas = { c, i ->
                colorInt = c
                isDark = i
            },
            selected = viewModel.selected,
            onSelected = { viewModel.selected = it },
            basicUrl = viewModel.basicUrl,
            onBasicUrl = { viewModel.basicUrl = it },
            username = viewModel.username,
            onUsername = { viewModel.username = it },
            password = viewModel.password,
            onPassword = { viewModel.password = it },
            modifier = modifier.fillMaxSize()
        )
        if (!tv) {
            CanvasBottomSheet(
                sheetState = sheetState,
                colorInt = colorInt,
                isDark = isDark,
                onDismissRequest = {
                    colorInt = null
                    isDark = null
                }
            )
        }
    }
}

@Composable
private fun SettingScreen(
    contentPadding: PaddingValues,
    versionName: String,
    versionCode: Int,
    title: String,
    url: String,
    uri: Uri,
    backingUpOrRestoring: BackingUpAndRestoringState,
    onTitle: (String) -> Unit,
    onUrl: (String) -> Unit,
    onSubscribe: () -> Unit,
    hiddenStreams: ImmutableList<Stream>,
    hiddenCategoriesWithPlaylists: ImmutableList<Pair<Playlist, String>>,
    onUnhideStream: (Int) -> Unit,
    onUnhidePlaylistCategory: (playlistUrl: String, group: String) -> Unit,
    navigateToAbout: () -> Unit,
    localStorage: Boolean,
    onLocalStorage: (Boolean) -> Unit,
    subscribeForTv: Boolean,
    onSubscribeForTv: () -> Unit,
    openDocument: (Uri) -> Unit,
    backup: () -> Unit,
    restore: () -> Unit,
    onClipboard: (String) -> Unit,
    colorPacks: ImmutableList<ColorPack>,
    openColorCanvas: (Int, Boolean) -> Unit,
    selected: DataSource,
    onSelected: (DataSource) -> Unit,
    basicUrl: String,
    onBasicUrl: (String) -> Unit,
    username: String,
    onUsername: (String) -> Unit,
    password: String,
    onPassword: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val helper = LocalHelper.current
    val pref = LocalPref.current

    val defaultTitle = stringResource(string.ui_title_setting)
    val playlistTitle = stringResource(string.feat_setting_playlist_management)
    val appearanceTitle = stringResource(string.feat_setting_appearance)

    val colorArgb = pref.colorArgb

    var fragment: SettingFragment by remember { mutableStateOf(SettingFragment.Default) }

    EventHandler(EventBus.settingFragment) {
        fragment = it
    }

    val visiblePageInfos = LocalVisiblePageInfos.current
    val pageIndex = remember { Destination.Root.entries.indexOf(Destination.Root.Setting) }
    val isPageInfoVisible = remember(pageIndex, visiblePageInfos) {
        visiblePageInfos.find { it.index == pageIndex } != null
    }

    if (isPageInfoVisible) {
        LifecycleResumeEffect(fragment, defaultTitle, playlistTitle, appearanceTitle, fragment) {
            helper.title = when (fragment) {
                SettingFragment.Default -> defaultTitle
                SettingFragment.Playlists -> playlistTitle
                SettingFragment.Appearance -> appearanceTitle
            }.title()
            if (fragment != SettingFragment.Default) {
                helper.fob = Fob(
                    rootDestination = Destination.Root.Setting,
                    icon = Icons.Rounded.ChangeCircle,
                    iconTextId = string.feat_setting_back_home
                ) {
                    fragment = SettingFragment.Default
                }
            }
            helper.actions = persistentListOf()
            onPauseOrDispose {
                helper.fob = null
            }
        }
    }

    val currentPaneScaffoldRole by remember {
        derivedStateOf {
            when (fragment) {
                SettingFragment.Default -> ListDetailPaneScaffoldRole.List
                else -> ListDetailPaneScaffoldRole.Detail
            }
        }
    }
    val scaffoldState = calculateListDetailPaneScaffoldState(
        currentDestination = ThreePaneScaffoldDestinationItem(currentPaneScaffoldRole, null),
        scaffoldDirective = calculateStandardPaneScaffoldDirective(currentWindowAdaptiveInfo())
    )

    ListDetailPaneScaffold(
        scaffoldState = scaffoldState,
        // we handle the window insets in app scaffold
        windowInsets = WindowInsets(0),
        listPane = {
            PreferencesFragment(
                fragment = fragment,
                contentPadding = contentPadding,
                versionName = versionName,
                versionCode = versionCode,
                navigateToPlaylistManagement = {
                    fragment = SettingFragment.Playlists
                },
                navigateToThemeSelector = {
                    fragment = SettingFragment.Appearance
                },
                navigateToAbout = navigateToAbout,
                modifier = Modifier.fillMaxSize()
            )
        },
        detailPane = {
            if (fragment != SettingFragment.Default) {
                AnimatedPane(Modifier) {
                    when (fragment) {
                        SettingFragment.Playlists -> {
                            SubscriptionsFragment(
                                contentPadding = contentPadding,
                                title = title,
                                url = url,
                                uri = uri,
                                backingUpOrRestoring = backingUpOrRestoring,
                                hiddenStreams = hiddenStreams,
                                hiddenCategoriesWithPlaylists = hiddenCategoriesWithPlaylists,
                                onUnhideStream = onUnhideStream,
                                onUnhidePlaylistCategory = onUnhidePlaylistCategory,
                                onTitle = onTitle,
                                onUrl = onUrl,
                                onSubscribe = onSubscribe,
                                localStorage = localStorage,
                                onLocalStorage = onLocalStorage,
                                subscribeForTv = subscribeForTv,
                                onSubscribeForTv = onSubscribeForTv,
                                openDocument = openDocument,
                                onClipboard = onClipboard,
                                backup = backup,
                                restore = restore,
                                selected = selected,
                                onSelected = onSelected,
                                basicUrl = basicUrl,
                                onBasicUrl = onBasicUrl,
                                username = username,
                                onUsername = onUsername,
                                password = password,
                                onPassword = onPassword,
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        SettingFragment.Appearance -> {
                            AppearanceFragment(
                                colorPacks = colorPacks,
                                colorArgb = colorArgb,
                                openColorCanvas = openColorCanvas,
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
            .haze(
                LocalHazeState.current,
                HazeDefaults.style(MaterialTheme.colorScheme.surface)
            )
            .testTag("feature:setting")
    )
    BackHandler(fragment != SettingFragment.Default) {
        fragment = SettingFragment.Default
    }
}

private fun calculateStandardPaneScaffoldDirective(
    windowAdaptiveInfo: WindowAdaptiveInfo,
    verticalHingePolicy: HingePolicy = HingePolicy.AvoidSeparating
): PaneScaffoldDirective {
    val maxHorizontalPartitions: Int
    val verticalSpacerSize: Dp
    when (windowAdaptiveInfo.windowSizeClass.widthSizeClass) {
        WindowWidthSizeClass.Compact -> {
            maxHorizontalPartitions = 1
            verticalSpacerSize = 0.dp
        }

        WindowWidthSizeClass.Medium -> {
            maxHorizontalPartitions = 1
            verticalSpacerSize = 0.dp
        }

        else -> {
            maxHorizontalPartitions = 2
            verticalSpacerSize = 24.dp
        }
    }
    val maxVerticalPartitions: Int
    val horizontalSpacerSize: Dp

    if (windowAdaptiveInfo.windowPosture.isTabletop) {
        maxVerticalPartitions = 2
        horizontalSpacerSize = 24.dp
    } else {
        maxVerticalPartitions = 1
        horizontalSpacerSize = 0.dp
    }

    return PaneScaffoldDirective(
        // keep no paddings
        PaddingValues(),
        maxHorizontalPartitions,
        verticalSpacerSize,
        maxVerticalPartitions,
        horizontalSpacerSize,
        getExcludedVerticalBounds(windowAdaptiveInfo.windowPosture, verticalHingePolicy)
    )
}

private fun getExcludedVerticalBounds(posture: Posture, hingePolicy: HingePolicy): List<Rect> {
    return when (hingePolicy) {
        HingePolicy.AvoidSeparating -> posture.separatingVerticalHingeBounds
        HingePolicy.AvoidOccluding -> posture.occludingVerticalHingeBounds
        HingePolicy.AlwaysAvoid -> posture.allVerticalHingeBounds
        else -> emptyList()
    }
}