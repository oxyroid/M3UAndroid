package com.m3u.features.setting

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.MutableState
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
import com.m3u.core.architecture.preferences.hiltPreferences
import com.m3u.core.unit.DataUnit
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
import com.m3u.ui.EventHandler
import com.m3u.ui.Events
import com.m3u.ui.LocalVisiblePageInfos
import com.m3u.ui.SettingDestination
import com.m3u.ui.helper.Fob
import com.m3u.ui.helper.Metadata
import dev.chrisbanes.haze.HazeDefaults
import dev.chrisbanes.haze.haze

@Composable
fun SettingRoute(
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    viewModel: SettingViewModel = hiltViewModel()
) {
    val tv = isTelevision()
    val controller = LocalSoftwareKeyboardController.current

    val colorPacks by viewModel.colorPacks.collectAsStateWithLifecycle()
    val epgs by viewModel.epgs.collectAsStateWithLifecycle()
    val hiddenStreams by viewModel.hiddenStreams.collectAsStateWithLifecycle()
    val hiddenCategoriesWithPlaylists by viewModel.hiddenCategoriesWithPlaylists.collectAsStateWithLifecycle()
    val backingUpOrRestoring by viewModel.backingUpOrRestoring.collectAsStateWithLifecycle()

    val cacheSpace by viewModel.cacheSpace.collectAsStateWithLifecycle()

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

    SettingScreen(
        versionName = viewModel.versionName,
        versionCode = viewModel.versionCode,
        titleState = viewModel.titleState,
        urlState = viewModel.urlState,
        uriState = viewModel.uriState,
        basicUrlState = viewModel.basicUrlState,
        usernameState = viewModel.usernameState,
        passwordState = viewModel.passwordState,
        epgState = viewModel.epgState,
        localStorageState = viewModel.localStorageState,
        selectedState = viewModel.selectedState,
        forTvState = viewModel.forTvState,
        backingUpOrRestoring = backingUpOrRestoring,
        epgs = epgs,
        hiddenStreams = hiddenStreams,
        hiddenCategoriesWithPlaylists = hiddenCategoriesWithPlaylists,
        cacheSpace = cacheSpace,
        backup = backup,
        restore = restore,
        colorPacks = colorPacks,
        openColorCanvas = { c, i ->
            colorInt = c
            isDark = i
        },
        onClipboard = { viewModel.onClipboard(it) },
        onClearCache = { viewModel.clearCache() },
        onSubscribe = {
            controller?.hide()
            viewModel.subscribe()
        },
        onUnhideStream = { viewModel.onUnhideStream(it) },
        onUnhidePlaylistCategory = { playlistUrl, group ->
            viewModel.onUnhidePlaylistCategory(playlistUrl, group)
        },
        onDeleteEpgPlaylist = { viewModel.deleteEpgPlaylist(it) },
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding
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

@Composable
private fun SettingScreen(
    titleState: MutableState<String>,
    urlState: MutableState<String>,
    uriState: MutableState<Uri>,
    selectedState: MutableState<DataSource>,
    basicUrlState: MutableState<String>,
    usernameState: MutableState<String>,
    passwordState: MutableState<String>,
    epgState: MutableState<String>,
    localStorageState: MutableState<Boolean>,
    forTvState: MutableState<Boolean>,
    versionName: String,
    versionCode: Int,
    backingUpOrRestoring: BackingUpAndRestoringState,
    onSubscribe: () -> Unit,
    hiddenStreams: List<Stream>,
    hiddenCategoriesWithPlaylists: List<Pair<Playlist, String>>,
    onUnhideStream: (Int) -> Unit,
    onUnhidePlaylistCategory: (playlistUrl: String, group: String) -> Unit,
    backup: () -> Unit,
    restore: () -> Unit,
    onClipboard: (String) -> Unit,
    colorPacks: List<ColorPack>,
    openColorCanvas: (Int, Boolean) -> Unit,
    cacheSpace: DataUnit,
    onClearCache: () -> Unit,
    epgs: List<Playlist>,
    onDeleteEpgPlaylist: (String) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues()
) {
    val preferences = hiltPreferences()

    val defaultTitle = stringResource(string.ui_title_setting)
    val playlistTitle = stringResource(string.feat_setting_playlist_management)
    val appearanceTitle = stringResource(string.feat_setting_appearance)

    val colorArgb = preferences.argb

    var fragment: SettingDestination by remember { mutableStateOf(SettingDestination.Default) }

    EventHandler(Events.settingDestination) {
        fragment = it
    }

    val visiblePageInfos = LocalVisiblePageInfos.current
    val pageIndex = remember { Destination.Root.entries.indexOf(Destination.Root.Setting) }
    val isPageInfoVisible = remember(pageIndex, visiblePageInfos) {
        visiblePageInfos.find { it.index == pageIndex } != null
    }

    if (isPageInfoVisible) {
        LifecycleResumeEffect(fragment, defaultTitle, playlistTitle, appearanceTitle, fragment) {
            Metadata.title = when (fragment) {
                SettingDestination.Default -> defaultTitle
                SettingDestination.Playlists -> playlistTitle
                SettingDestination.Appearance -> appearanceTitle
            }.title()
            if (fragment != SettingDestination.Default) {
                Metadata.fob = Fob(
                    rootDestination = Destination.Root.Setting,
                    icon = Icons.Rounded.ChangeCircle,
                    iconTextId = string.feat_setting_back_home
                ) {
                    fragment = SettingDestination.Default
                }
            }
            Metadata.actions = emptyList()
            onPauseOrDispose {
                Metadata.fob = null
            }
        }
    }

    val currentPaneScaffoldRole by remember {
        derivedStateOf {
            when (fragment) {
                SettingDestination.Default -> ListDetailPaneScaffoldRole.List
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
                    fragment = SettingDestination.Playlists
                },
                navigateToThemeSelector = {
                    fragment = SettingDestination.Appearance
                },
                cacheSpace = cacheSpace,
                onClearCache = onClearCache,
                modifier = Modifier.fillMaxSize()
            )
        },
        detailPane = {
            if (fragment != SettingDestination.Default) {
                AnimatedPane(Modifier) {
                    when (fragment) {
                        SettingDestination.Playlists -> {
                            SubscriptionsFragment(
                                titleState = titleState,
                                urlState = urlState,
                                uriState = uriState,
                                selectedState = selectedState,
                                basicUrlState = basicUrlState,
                                usernameState = usernameState,
                                passwordState = passwordState,
                                epgState = epgState,
                                localStorageState = localStorageState,
                                forTvState = forTvState,
                                backingUpOrRestoring = backingUpOrRestoring,
                                hiddenStreams = hiddenStreams,
                                hiddenCategoriesWithPlaylists = hiddenCategoriesWithPlaylists,
                                onUnhideStream = onUnhideStream,
                                onUnhidePlaylistCategory = onUnhidePlaylistCategory,
                                onClipboard = onClipboard,
                                onSubscribe = onSubscribe,
                                backup = backup,
                                restore = restore,
                                epgs = epgs,
                                onDeleteEpgPlaylist = onDeleteEpgPlaylist,
                                contentPadding = contentPadding,
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        SettingDestination.Appearance -> {
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
    BackHandler(fragment != SettingDestination.Default) {
        fragment = SettingDestination.Default
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