package com.m3u.smartphone.ui.business.setting

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChangeCircle
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffold
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.m3u.business.setting.BackingUpAndRestoringState
import com.m3u.business.setting.SettingProperties
import com.m3u.business.setting.SettingViewModel
import com.m3u.core.architecture.preferences.PreferencesKeys
import com.m3u.core.architecture.preferences.preferenceOf
import com.m3u.core.util.basic.title
import com.m3u.data.database.model.Channel
import com.m3u.data.database.model.ColorScheme
import com.m3u.data.database.model.Playlist
import com.m3u.i18n.R.string
import com.m3u.smartphone.ui.business.setting.components.CanvasBottomSheet
import com.m3u.smartphone.ui.business.setting.fragments.AppearanceFragment
import com.m3u.smartphone.ui.business.setting.fragments.OptionalFragment
import com.m3u.smartphone.ui.business.setting.fragments.SubscriptionsFragment
import com.m3u.smartphone.ui.business.setting.fragments.preferences.PreferencesFragment
import com.m3u.smartphone.ui.common.helper.Fob
import com.m3u.smartphone.ui.common.helper.Metadata
import com.m3u.smartphone.ui.common.internal.Events
import com.m3u.smartphone.ui.material.components.Destination
import com.m3u.smartphone.ui.material.components.EventHandler
import com.m3u.smartphone.ui.material.components.SettingDestination
import com.m3u.smartphone.ui.material.model.LocalHazeState
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.launch

@Composable
fun SettingRoute(
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    viewModel: SettingViewModel = hiltViewModel()
) {
    val controller = LocalSoftwareKeyboardController.current

    val colorSchemes by viewModel.colorSchemes.collectAsStateWithLifecycle()
    val epgs by viewModel.epgs.collectAsStateWithLifecycle()
    val hiddenChannels by viewModel.hiddenChannels.collectAsStateWithLifecycle()
    val hiddenCategoriesWithPlaylists by viewModel.hiddenCategoriesWithPlaylists.collectAsStateWithLifecycle()
    val backingUpOrRestoring by viewModel.backingUpOrRestoring.collectAsStateWithLifecycle()

    val sheetState = rememberModalBottomSheetState()
    var colorScheme: ColorScheme? by remember { mutableStateOf(null) }

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

    with(viewModel.properties) {
        SettingScreen(
            versionName = viewModel.versionName,
            versionCode = viewModel.versionCode,
            backingUpOrRestoring = backingUpOrRestoring,
            epgs = epgs,
            hiddenChannels = hiddenChannels,
            hiddenCategoriesWithPlaylists = hiddenCategoriesWithPlaylists,
            backup = backup,
            restore = restore,
            colorSchemes = colorSchemes,
            openColorScheme = { colorScheme = it },
            restoreSchemes = viewModel::restoreSchemes,
            onClipboard = { viewModel.onClipboard(it) },
            onSubscribe = {
                controller?.hide()
                viewModel.subscribe()
            },
            onUnhideChannel = { viewModel.onUnhideChannel(it) },
            onUnhidePlaylistCategory = { playlistUrl, group ->
                viewModel.onUnhidePlaylistCategory(playlistUrl, group)
            },
            onDeleteEpgPlaylist = { viewModel.deleteEpgPlaylist(it) },
            modifier = modifier.fillMaxSize(),
            contentPadding = contentPadding,
        )
    }

    CanvasBottomSheet(
        sheetState = sheetState,
        colorScheme = colorScheme,
        onApplyColor = { argb, isDark ->
            viewModel.applyColor(colorScheme, argb, isDark)
        },
        onDismissRequest = {
            colorScheme = null
        }
    )
}

@Composable
context(_: SettingProperties)
private fun SettingScreen(
    versionName: String,
    versionCode: Int,
    backingUpOrRestoring: BackingUpAndRestoringState,
    onSubscribe: () -> Unit,
    hiddenChannels: List<Channel>,
    hiddenCategoriesWithPlaylists: List<Pair<Playlist, String>>,
    onUnhideChannel: (Int) -> Unit,
    onUnhidePlaylistCategory: (playlistUrl: String, group: String) -> Unit,
    backup: () -> Unit,
    restore: () -> Unit,
    onClipboard: (String) -> Unit,
    colorSchemes: List<ColorScheme>,
    openColorScheme: (ColorScheme) -> Unit,
    restoreSchemes: () -> Unit,
    epgs: List<Playlist>,
    onDeleteEpgPlaylist: (String) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues()
) {
    val coroutineScope = rememberCoroutineScope()

    val defaultTitle = stringResource(string.ui_title_setting)
    val playlistTitle = stringResource(string.feat_setting_playlist_management)
    val appearanceTitle = stringResource(string.feat_setting_appearance)
    val optionalTitle = stringResource(string.feat_setting_optional_features)

    val colorArgb by preferenceOf(PreferencesKeys.COLOR_ARGB)

    val navigator = rememberListDetailPaneScaffoldNavigator<SettingDestination>()
    val destination = navigator.currentDestination?.contentKey ?: SettingDestination.Default

    EventHandler(Events.settingDestination) {
        navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, it)
    }

    LifecycleResumeEffect(destination, defaultTitle, playlistTitle, appearanceTitle) {
        Metadata.title = when (destination) {
            SettingDestination.Default -> defaultTitle
            SettingDestination.Playlists -> playlistTitle
            SettingDestination.Appearance -> appearanceTitle
            SettingDestination.Optional -> optionalTitle
        }
            .title()
            .let(::AnnotatedString)
        Metadata.color = Color.Unspecified
        Metadata.contentColor = Color.Unspecified
        if (destination != SettingDestination.Default) {
            Metadata.fob = Fob(
                destination = Destination.Setting,
                icon = Icons.Rounded.ChangeCircle,
                iconTextId = string.feat_setting_back_home
            ) {
                coroutineScope.launch {
                    navigator.navigateBack()
                }
            }
        }
        onPauseOrDispose {
            Metadata.fob = null
        }
    }

    ListDetailPaneScaffold(
        directive = navigator.scaffoldDirective,
        value = navigator.scaffoldValue,
        listPane = {
            PreferencesFragment(
                fragment = destination,
                contentPadding = contentPadding,
                versionName = versionName,
                versionCode = versionCode,
                navigateToPlaylistManagement = {
                    coroutineScope.launch {
                        navigator.navigateTo(
                            pane = ListDetailPaneScaffoldRole.Detail,
                            contentKey = SettingDestination.Playlists
                        )
                    }
                },
                navigateToThemeSelector = {
                    coroutineScope.launch {
                        navigator.navigateTo(
                            pane = ListDetailPaneScaffoldRole.Detail,
                            contentKey = SettingDestination.Appearance
                        )
                    }
                },
                navigateToOptional = {
                    coroutineScope.launch {
                        navigator.navigateTo(
                            pane = ListDetailPaneScaffoldRole.Detail,
                            contentKey = SettingDestination.Optional
                        )
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        },
        detailPane = {
            when (destination) {
                SettingDestination.Playlists -> {
                    SubscriptionsFragment(
                        backingUpOrRestoring = backingUpOrRestoring,
                        hiddenChannels = hiddenChannels,
                        hiddenCategoriesWithPlaylists = hiddenCategoriesWithPlaylists,
                        onUnhideChannel = onUnhideChannel,
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
                        colorSchemes = colorSchemes,
                        colorArgb = colorArgb,
                        openColorScheme = openColorScheme,
                        restoreSchemes = restoreSchemes,
                        contentPadding = contentPadding,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                SettingDestination.Optional -> {
                    OptionalFragment(
                        contentPadding = contentPadding,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                else -> {}
            }
        },
        modifier = modifier
            .fillMaxSize()
            .hazeSource(LocalHazeState.current)
            .testTag("feature:setting")
    )
    BackHandler(navigator.canNavigateBack()) {
        coroutineScope.launch {
            navigator.navigateBack()
        }
    }
}
