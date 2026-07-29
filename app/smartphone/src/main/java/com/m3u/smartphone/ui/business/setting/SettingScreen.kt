package com.m3u.smartphone.ui.business.setting

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.Icons
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffold
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.layout.PaneAdaptedValue
import androidx.compose.material3.adaptive.navigation.BackNavigationBehavior
import androidx.compose.material3.adaptive.navigation.ThreePaneScaffoldNavigator
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.m3u.business.playlist.configuration.playlistConfigurationReference
import com.m3u.business.setting.BackingUpAndRestoringState
import com.m3u.business.setting.CodecPackState
import com.m3u.business.setting.ExtensionPluginDiscoveryState
import com.m3u.business.setting.ExtensionPluginOperationState
import com.m3u.business.setting.ExtensionSettingsState
import com.m3u.business.setting.ProviderDiscoveryState
import com.m3u.business.setting.ProviderOperationState
import com.m3u.business.setting.ProviderSubscriptionForm
import com.m3u.business.setting.PlaylistSubscriptionPhase
import com.m3u.business.setting.PlaylistSubscriptionState
import com.m3u.business.setting.SettingProperties
import com.m3u.business.setting.SettingViewModel
import com.m3u.core.foundation.architecture.preferences.ThemePreference
import com.m3u.core.foundation.util.basic.title
import com.m3u.data.database.model.Channel
import com.m3u.data.database.model.ColorScheme
import com.m3u.data.database.model.DataSource
import com.m3u.data.database.model.Playlist
import com.m3u.data.repository.extension.ExtensionSettingEditToken
import com.m3u.data.repository.plugin.InstalledPlugin
import com.m3u.data.repository.plugin.PluginAuthorizationToken
import com.m3u.data.repository.provider.ProviderAccountSummary
import com.m3u.data.worker.abandonPersistedUriPermissionLease
import com.m3u.data.worker.beginPersistedUriPermissionLease
import com.m3u.i18n.R.string
import com.m3u.smartphone.ui.business.configuration.PlaylistConfigurationRoute
import com.m3u.smartphone.ui.business.configuration.providerDisplayName
import com.m3u.smartphone.ui.business.setting.components.CanvasBottomSheet
import com.m3u.smartphone.ui.business.setting.fragments.AppearanceFragment
import com.m3u.smartphone.ui.business.setting.fragments.CodecPackFragment
import com.m3u.smartphone.ui.business.setting.fragments.ExtensionPluginAuthorizationScreen
import com.m3u.smartphone.ui.business.setting.fragments.ExtensionPluginDetailScreen
import com.m3u.smartphone.ui.business.setting.fragments.ExtensionPluginListScreen
import com.m3u.smartphone.ui.business.setting.fragments.ExtensionSettingsScreen
import com.m3u.smartphone.ui.business.setting.fragments.EpgSourceListScreen
import com.m3u.smartphone.ui.business.setting.fragments.HiddenCategoryListScreen
import com.m3u.smartphone.ui.business.setting.fragments.HiddenChannelListScreen
import com.m3u.smartphone.ui.business.setting.fragments.OptionalFragment
import com.m3u.smartphone.ui.business.setting.fragments.PlaylistManagementOverviewScreen
import com.m3u.smartphone.ui.business.setting.fragments.SubscriptionEditorScreen
import com.m3u.smartphone.ui.business.setting.fragments.SubscriptionSourcePickerScreen
import com.m3u.smartphone.ui.business.setting.fragments.providerSourceSelectionKey
import com.m3u.smartphone.ui.business.setting.fragments.resolveExtensionPluginDetailContentState
import com.m3u.smartphone.ui.business.setting.fragments.subscriptionSelectionKey
import com.m3u.smartphone.ui.business.setting.fragments.preferences.PreferencesFragment
import com.m3u.smartphone.ui.common.helper.Fob
import com.m3u.smartphone.ui.common.helper.Metadata
import com.m3u.smartphone.ui.common.internal.Events
import com.m3u.smartphone.ui.material.components.Destination
import com.m3u.smartphone.ui.material.components.EventHandler
import com.m3u.smartphone.ui.material.components.SettingDestination
import com.m3u.smartphone.ui.material.ktx.withoutBidiControls
import com.m3u.smartphone.ui.material.model.LocalHazeState
import com.m3u.smartphone.ui.navigation.AppNavigationMode
import com.m3u.smartphone.ui.navigation.resolveAppNavigationMode
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

@Composable
fun SettingRoute(
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    onDetailVisibilityChanged: (Boolean) -> Unit = {},
    viewModel: SettingViewModel = hiltViewModel()
) {
    val controller = LocalSoftwareKeyboardController.current

    val colorSchemes by viewModel.colorSchemes.collectAsStateWithLifecycle()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val playlistSubscriptionInProgress by
        viewModel.playlistSubscriptionInProgress.collectAsStateWithLifecycle()
    val playlistSubscriptionState by
        viewModel.playlistSubscriptionState.collectAsStateWithLifecycle()
    val epgs by viewModel.epgs.collectAsStateWithLifecycle()
    val hiddenChannels by viewModel.hiddenChannels.collectAsStateWithLifecycle()
    val hiddenCategoriesWithPlaylists by viewModel.hiddenCategoriesWithPlaylists.collectAsStateWithLifecycle()
    val backingUpOrRestoring by viewModel.backingUpOrRestoring.collectAsStateWithLifecycle()
    val codecPackState by viewModel.codecPackState.collectAsStateWithLifecycle()
    val extensionPluginDiscoveryState by
        viewModel.extensionPluginDiscoveryState.collectAsStateWithLifecycle()
    val extensionPluginOperationState by
        viewModel.extensionPluginOperationState.collectAsStateWithLifecycle()
    val extensionSettingsState by viewModel.extensionSettingsState.collectAsStateWithLifecycle()
    val extensionPlugins = extensionPluginDiscoveryState.plugins
    val providerDiscoveryState by viewModel.providerDiscoveryState.collectAsStateWithLifecycle()
    val providerAccountSummaries by viewModel.providerAccountSummaries.collectAsStateWithLifecycle()
    val providerSubscriptionForm by viewModel.providerSubscriptionForm.collectAsStateWithLifecycle()
    val providerOperationState by viewModel.providerOperationState.collectAsStateWithLifecycle()
    val localeTag = LocalConfiguration.current.locales[0].toLanguageTag()
    val context = LocalContext.current
    val diagnosticsShareTitle = stringResource(string.feat_setting_extension_diagnostics_share_title)
    val fileAccessFailure = stringResource(string.feat_setting_playlist_file_access_failed)
    val currentDiagnosticsShareTitle by rememberUpdatedState(diagnosticsShareTitle)

    LaunchedEffect(viewModel, localeTag) {
        viewModel.refreshSubscriptionProvidersForLocale(localeTag)
    }
    LaunchedEffect(viewModel, context) {
        viewModel.extensionDiagnostics.collect { payload ->
            context.startActivity(
                Intent.createChooser(
                    Intent(Intent.ACTION_SEND).apply {
                        type = "application/json"
                        putExtra(Intent.EXTRA_TEXT, payload)
                    },
                    currentDiagnosticsShareTitle,
                )
            )
        }
    }

    val sheetState = rememberModalBottomSheetState()
    var colorScheme: ColorScheme? by remember { mutableStateOf(null) }

    val createDocumentLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/*")) { uri ->
            uri ?: return@rememberLauncherForActivityResult
            val permissionTag = beginPersistedUriPermissionLease(context, uri)
            val accessPersisted = runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }.isSuccess
            if (accessPersisted) {
                viewModel.backup(uri)
            } else {
                abandonPersistedUriPermissionLease(context, permissionTag)
                Toast.makeText(context, fileAccessFailure, Toast.LENGTH_LONG).show()
            }
        }
    val openDocumentLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri ?: return@rememberLauncherForActivityResult
            val permissionTag = beginPersistedUriPermissionLease(context, uri)
            val accessPersisted = runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }.isSuccess
            if (accessPersisted) {
                viewModel.restore(uri)
            } else {
                abandonPersistedUriPermissionLease(context, permissionTag)
                Toast.makeText(context, fileAccessFailure, Toast.LENGTH_LONG).show()
            }
        }

    val backup = {
        val filename = context.getString(
            string.feat_setting_playlist_backup_filename,
            System.currentTimeMillis(),
        )
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
            codecPackState = codecPackState,
            playlists = playlists,
            playlistSubscriptionInProgress = playlistSubscriptionInProgress,
            playlistSubscriptionState = playlistSubscriptionState,
            onCancelPlaylistSubscription =
                viewModel::cancelPlaylistSubscription,
            onDismissPlaylistSubscription =
                viewModel::dismissPlaylistSubscriptionStatus,
            epgs = epgs,
            hiddenChannels = hiddenChannels,
            hiddenCategoriesWithPlaylists = hiddenCategoriesWithPlaylists,
            backup = backup,
            restore = restore,
            colorSchemes = colorSchemes,
            openColorScheme = { colorScheme = it },
            onSelectTheme = viewModel::selectTheme,
            restoreSchemes = viewModel::restoreSchemes,
            onClipboard = { viewModel.onClipboard(it) },
            onBeginSubscriptionDraft = viewModel::beginSubscriptionDraft,
            subscriptionAccepted = viewModel.subscriptionAccepted,
            onSubscribe = {
                controller?.hide()
                viewModel.subscribe()
            },
            onUnhideChannel = { viewModel.onUnhideChannel(it) },
            onUnhidePlaylistCategory = { playlistUrl, group ->
                viewModel.onUnhidePlaylistCategory(playlistUrl, group)
            },
            onDeleteEpgPlaylist = { viewModel.deleteEpgPlaylist(it) },
            onInstallCodecPack = viewModel::installCodecPack,
            onDeleteCodecPack = viewModel::deleteCodecPack,
            onRefreshCodecPack = viewModel::refreshCodecPack,
            extensionPlugins = extensionPlugins,
            extensionPluginDiscoveryState = extensionPluginDiscoveryState,
            extensionPluginOperationState = extensionPluginOperationState,
            extensionSettingsState = extensionSettingsState,
            providerDiscoveryState = providerDiscoveryState,
            providerAccountSummaries = providerAccountSummaries,
            providerSubscriptionForm = providerSubscriptionForm,
            providerOperationState = providerOperationState,
            onSelectSubscriptionProviderVariant = viewModel::selectSubscriptionProviderVariant,
            onUpdateSubscriptionProviderSetting = viewModel::updateSubscriptionProviderSetting,
            onRetryProviderDiscovery = viewModel::refreshSubscriptionProviders,
            onReauthenticateProviderAccount = viewModel::reauthenticateProviderAccount,
            onRefreshExtensionPlugins = viewModel::refreshExtensionPlugins,
            onEnableExtensionPlugin = viewModel::enableExtensionPlugin,
            onReauthorizeExtensionPlugin = viewModel::reauthorizeExtensionPlugin,
            onDisableExtensionPlugin = viewModel::disableExtensionPlugin,
            onRevokeExtensionPlugin = viewModel::revokeExtensionPlugin,
            onClearExtensionData = viewModel::clearExtensionData,
            onExportExtensionDiagnostics = viewModel::exportExtensionDiagnostics,
            onOpenExtensionSettings = { extensionId ->
                viewModel.openExtensionSettings(extensionId, localeTag)
            },
            onCloseExtensionSettings = viewModel::closeExtensionSettings,
            onUpdateExtensionSetting = { sectionId, fieldKey, editToken, value ->
                viewModel.updateExtensionSetting(
                    sectionId,
                    fieldKey,
                    editToken,
                    value,
                    localeTag,
                )
            },
            onDetailVisibilityChanged = onDetailVisibilityChanged,
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
    codecPackState: CodecPackState,
    playlists: Map<Playlist, Int>?,
    playlistSubscriptionInProgress: Boolean,
    playlistSubscriptionState: PlaylistSubscriptionState,
    onCancelPlaylistSubscription: () -> Unit,
    onDismissPlaylistSubscription: () -> Unit,
    subscriptionAccepted: Flow<Unit>,
    onSubscribe: () -> Unit,
    hiddenChannels: List<Channel>,
    hiddenCategoriesWithPlaylists: List<Pair<Playlist, String>>,
    onUnhideChannel: (Int) -> Unit,
    onUnhidePlaylistCategory: (playlistUrl: String, group: String) -> Unit,
    backup: () -> Unit,
    restore: () -> Unit,
    onClipboard: (String) -> Unit,
    onBeginSubscriptionDraft: (String, DataSource) -> Unit,
    colorSchemes: List<ColorScheme>,
    openColorScheme: (ColorScheme) -> Unit,
    onSelectTheme: (ThemePreference) -> Unit,
    restoreSchemes: () -> Unit,
    epgs: List<Playlist>,
    onDeleteEpgPlaylist: (String) -> Unit,
    onInstallCodecPack: () -> Unit,
    onDeleteCodecPack: () -> Unit,
    onRefreshCodecPack: () -> Unit,
    extensionPlugins: List<InstalledPlugin>,
    extensionPluginDiscoveryState: ExtensionPluginDiscoveryState,
    extensionPluginOperationState: ExtensionPluginOperationState,
    extensionSettingsState: ExtensionSettingsState,
    providerDiscoveryState: ProviderDiscoveryState,
    providerAccountSummaries: List<ProviderAccountSummary>,
    providerSubscriptionForm: ProviderSubscriptionForm?,
    providerOperationState: ProviderOperationState,
    onSelectSubscriptionProviderVariant: (String, String) -> Unit,
    onUpdateSubscriptionProviderSetting: (String, String?) -> Unit,
    onRetryProviderDiscovery: () -> Unit,
    onReauthenticateProviderAccount: (String) -> Unit,
    onRefreshExtensionPlugins: () -> Unit,
    onEnableExtensionPlugin: (String, String, PluginAuthorizationToken) -> Unit,
    onReauthorizeExtensionPlugin: (String, String, PluginAuthorizationToken) -> Unit,
    onDisableExtensionPlugin: (String) -> Unit,
    onRevokeExtensionPlugin: (String, String, String?) -> Unit,
    onClearExtensionData: (String, String, String?) -> Unit,
    onExportExtensionDiagnostics: (String) -> Unit,
    onOpenExtensionSettings: (String) -> Unit,
    onCloseExtensionSettings: () -> Unit,
    onUpdateExtensionSetting: (String, String, ExtensionSettingEditToken, String?) -> Unit,
    onDetailVisibilityChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues()
) {
    val coroutineScope = rememberCoroutineScope()

    val defaultTitle = stringResource(string.ui_title_setting)
    val playlistTitle = stringResource(string.feat_setting_playlist_management)
    val playlistSourcePickerTitle =
        stringResource(string.feat_setting_playlist_source_picker_title)
    val playlistEditorTitle = stringResource(string.feat_setting_label_add_playlist)
    val playlistEpgEditorTitle =
        stringResource(string.feat_setting_playlist_add_epg_source)
    val playlistReauthenticationTitle =
        stringResource(string.feat_setting_provider_reauthenticate)
    val playlistEpgTitle = stringResource(string.feat_setting_label_epg_playlists)
    val playlistHiddenChannelsTitle =
        stringResource(string.feat_setting_label_hidden_channels)
    val playlistHiddenCategoriesTitle =
        stringResource(string.feat_setting_label_hidden_playlist_groups)
    val extensionPluginsTitle = stringResource(string.feat_setting_extension_plugins)
    val extensionDetailsTitle = stringResource(string.feat_setting_extension_details)
    val extensionAuthorizationTitle =
        stringResource(string.feat_setting_extension_confirm_title)
    val extensionSettingsTitle = stringResource(string.feat_setting_extension_settings)
    val appearanceTitle = stringResource(string.feat_setting_appearance)
    val optionalTitle = stringResource(string.feat_setting_optional_features)
    val codecPackTitle = stringResource(string.feat_setting_codec_pack)

    val navigator = rememberListDetailPaneScaffoldNavigator<SettingDestination>()
    val backNavigationBehavior = BackNavigationBehavior.PopUntilContentChange
    val destination = navigator.currentDestination?.contentKey ?: SettingDestination.Default
    val configurationPlaylist = (
        destination as? SettingDestination.PlaylistConfiguration
    )?.let { configurationDestination ->
        playlists
            ?.keys
            ?.firstOrNull { playlist ->
                playlistConfigurationReference(playlist.url) ==
                    configurationDestination.playlistReference
            }
    }
    val configurationProviderAccount = configurationPlaylist?.let { playlist ->
        providerAccountSummaries.firstOrNull { account ->
            account.playlistUrl == playlist.url
        }
    }
    val configurationProviderDisplayName = configurationProviderAccount?.let { account ->
        providerDisplayName(account, providerDiscoveryState)
    }
    val configurationTitle = configurationPlaylist
        ?.title
        ?.withoutBidiControls()
        ?.takeIf(String::isNotBlank)
        ?: playlistTitle
    val density = LocalDensity.current
    val showPlaylistPaneHeader =
        resolveAppNavigationMode(
            with(density) {
                LocalWindowInfo.current.containerSize.width.toDp()
            }
        ) == AppNavigationMode.SideRail
    val playlistListPaneVisible =
        navigator.scaffoldValue[ListDetailPaneScaffoldRole.List] == PaneAdaptedValue.Expanded
    val currentPlaylistEditorTitle = when {
        destination !is SettingDestination.PlaylistEditor -> playlistEditorTitle
        destination.reauthenticationPlaylistUrl != null ->
            playlistReauthenticationTitle
        destination.sourceKey == DataSource.EPG.subscriptionSelectionKey() ->
            playlistEpgEditorTitle
        else -> playlistEditorTitle
    }

    LaunchedEffect(navigator, subscriptionAccepted) {
        subscriptionAccepted.collect {
            if (
                navigator.currentDestination?.contentKey
                    !is SettingDestination.PlaylistEditor
            ) {
                return@collect
            }
            navigator.returnToPlaylistManagement()
        }
    }

    LaunchedEffect(destination) {
        when (destination) {
            is SettingDestination.ExtensionPluginSettings ->
                onOpenExtensionSettings(destination.extensionId)
            else -> onCloseExtensionSettings()
        }
    }

    LaunchedEffect(destination, onDetailVisibilityChanged) {
        onDetailVisibilityChanged(destination != SettingDestination.Default)
    }
    DisposableEffect(onDetailVisibilityChanged) {
        onDispose { onDetailVisibilityChanged(false) }
    }

    EventHandler(Events.settingDestination) {
        navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, it)
    }

    LifecycleResumeEffect(
        destination,
        extensionPlugins,
        defaultTitle,
        playlistTitle,
        playlistSourcePickerTitle,
        playlistEditorTitle,
        playlistEpgEditorTitle,
        playlistReauthenticationTitle,
        playlistEpgTitle,
        playlistHiddenChannelsTitle,
        playlistHiddenCategoriesTitle,
        extensionPluginsTitle,
        extensionDetailsTitle,
        extensionAuthorizationTitle,
        extensionSettingsTitle,
        appearanceTitle,
        optionalTitle,
        codecPackTitle,
        configurationTitle,
    ) {
        val title = when (destination) {
            SettingDestination.Default -> defaultTitle
            SettingDestination.Playlists -> playlistTitle
            is SettingDestination.PlaylistConfiguration -> configurationTitle
            SettingDestination.PlaylistSourcePicker -> playlistSourcePickerTitle
            is SettingDestination.PlaylistEditor -> currentPlaylistEditorTitle
            SettingDestination.PlaylistEpgSources -> playlistEpgTitle
            SettingDestination.PlaylistHiddenChannels -> playlistHiddenChannelsTitle
            SettingDestination.PlaylistHiddenCategories -> playlistHiddenCategoriesTitle
            SettingDestination.ExtensionPlugins -> extensionPluginsTitle
            is SettingDestination.ExtensionPluginDetails -> extensionDetailsTitle
            is SettingDestination.ExtensionPluginAuthorization ->
                extensionAuthorizationTitle
            is SettingDestination.ExtensionPluginSettings -> extensionSettingsTitle
            SettingDestination.Appearance -> appearanceTitle
            SettingDestination.Optional -> optionalTitle
            SettingDestination.CodecPack -> codecPackTitle
        }
        Metadata.title = (
            if (destination.usesLocalizedStaticTitle()) title.title() else title
        ).let(::AnnotatedString)
        Metadata.actions = emptyList()
        Metadata.color = Color.Unspecified
        Metadata.contentColor = Color.Unspecified
        if (destination != SettingDestination.Default) {
            Metadata.fob = Fob(
                destination = Destination.Setting,
                icon = Icons.AutoMirrored.Rounded.ArrowBack,
                iconTextId = string.ui_cd_top_bar_on_back_pressed,
            ) {
                coroutineScope.launch {
                    navigator.navigateBack(backNavigationBehavior)
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
                codecPackEnabled = codecPackState.enabled,
                navigateToPlaylistManagement = {
                    if (destination != SettingDestination.Playlists) {
                        coroutineScope.launch {
                            navigator.returnToPlaylistManagement()
                        }
                    }
                },
                navigateToExtensionPlugins = {
                    coroutineScope.launch {
                        navigator.navigateTo(
                            pane = ListDetailPaneScaffoldRole.Detail,
                            contentKey = SettingDestination.ExtensionPlugins,
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
                navigateToCodecPack = {
                    coroutineScope.launch {
                        navigator.navigateTo(
                            pane = ListDetailPaneScaffoldRole.Detail,
                            contentKey = SettingDestination.CodecPack
                        )
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        },
        detailPane = {
            when (destination) {
                SettingDestination.Playlists -> {
                    PlaylistDetailPane(
                        showHeader = showPlaylistPaneHeader,
                        title = playlistTitle,
                        onBack = if (
                            showPlaylistPaneHeader && !playlistListPaneVisible
                        ) {
                            {
                                coroutineScope.launch {
                                    navigator.navigateBack(backNavigationBehavior)
                                }
                            }
                        } else {
                            null
                        },
                    ) {
                        PlaylistManagementOverviewScreen(
                            backingUpOrRestoring = backingUpOrRestoring,
                            playlists = playlists,
                            playlistSubscriptionInProgress =
                                playlistSubscriptionInProgress,
                            playlistSubscriptionState =
                                playlistSubscriptionState,
                            onCancelPlaylistSubscription =
                                onCancelPlaylistSubscription,
                            onDismissPlaylistSubscription =
                                onDismissPlaylistSubscription,
                            onRetryPlaylistSubscription = { source ->
                                onDismissPlaylistSubscription()
                                coroutineScope.launch {
                                    navigator.navigateTo(
                                        pane = ListDetailPaneScaffoldRole.Detail,
                                        contentKey =
                                            SettingDestination.PlaylistEditor(
                                                sourceKey =
                                                    source.subscriptionSelectionKey(),
                                            ),
                                    )
                                }
                            },
                            epgCount = epgs.size,
                            hiddenChannelCount = hiddenChannels.size,
                            hiddenCategoryCount = hiddenCategoriesWithPlaylists.size,
                            providerDiscoveryState = providerDiscoveryState,
                            providerAccountSummaries = providerAccountSummaries,
                            providerOperationState = providerOperationState,
                            onAddPlaylist = {
                                coroutineScope.launch {
                                    navigator.navigateTo(
                                        pane = ListDetailPaneScaffoldRole.Detail,
                                        contentKey = SettingDestination.PlaylistSourcePicker,
                                    )
                                }
                            },
                            onOpenPlaylistConfiguration = { playlist ->
                                coroutineScope.launch {
                                    navigator.navigateTo(
                                        pane = ListDetailPaneScaffoldRole.Detail,
                                        contentKey =
                                            SettingDestination.PlaylistConfiguration(
                                                playlistReference =
                                                    playlistConfigurationReference(
                                                        playlist.url
                                                    ),
                                            ),
                                    )
                                }
                            },
                            onOpenEpgSources = {
                                coroutineScope.launch {
                                    navigator.navigateTo(
                                        pane = ListDetailPaneScaffoldRole.Detail,
                                        contentKey = SettingDestination.PlaylistEpgSources,
                                    )
                                }
                            },
                            onOpenHiddenChannels = {
                                coroutineScope.launch {
                                    navigator.navigateTo(
                                        pane = ListDetailPaneScaffoldRole.Detail,
                                        contentKey = SettingDestination.PlaylistHiddenChannels,
                                    )
                                }
                            },
                            onOpenHiddenCategories = {
                                coroutineScope.launch {
                                    navigator.navigateTo(
                                        pane = ListDetailPaneScaffoldRole.Detail,
                                        contentKey = SettingDestination.PlaylistHiddenCategories,
                                    )
                                }
                            },
                            onReauthenticateProviderAccount = { account ->
                                onReauthenticateProviderAccount(account.playlistUrl)
                                coroutineScope.launch {
                                    navigator.navigateTo(
                                        pane = ListDetailPaneScaffoldRole.Detail,
                                        contentKey = SettingDestination.PlaylistEditor(
                                            sourceKey = providerSourceSelectionKey(
                                                providerId = account.providerId.value,
                                                providerKind = account.providerKind.value,
                                            ),
                                            providerId = account.providerId.value,
                                            providerKind = account.providerKind.value,
                                            reauthenticationPlaylistUrl = account.playlistUrl,
                                        ),
                                    )
                                }
                            },
                            onBackup = backup,
                            onRestore = restore,
                            contentPadding = contentPadding,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }

                is SettingDestination.PlaylistConfiguration -> {
                    PlaylistDetailPane(
                        showHeader = showPlaylistPaneHeader,
                        title = configurationTitle,
                        onBack = {
                            coroutineScope.launch {
                                navigator.navigateBack(backNavigationBehavior)
                            }
                        },
                    ) {
                        PlaylistConfigurationRoute(
                            viewModel = hiltViewModel(
                                key = "playlist-configuration:" +
                                    destination.playlistReference,
                            ),
                            playlistReference = destination.playlistReference,
                            providerDisplayNameOverride =
                                configurationProviderDisplayName,
                            onPlaylistRemoved = {
                                coroutineScope.launch {
                                    navigator.returnToPlaylistManagement()
                                }
                            },
                            contentPadding = contentPadding,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }

                SettingDestination.PlaylistSourcePicker -> {
                    PlaylistDetailPane(
                        showHeader = showPlaylistPaneHeader,
                        title = playlistSourcePickerTitle,
                        onBack = {
                            coroutineScope.launch {
                                navigator.navigateBack(backNavigationBehavior)
                            }
                        },
                    ) {
                        SubscriptionSourcePickerScreen(
                            dataOperationInProgress =
                                backingUpOrRestoring != BackingUpAndRestoringState.NONE,
                            subscriptionSubmissionBlocked =
                                playlistSubscriptionInProgress ||
                                    playlistSubscriptionState.phase !=
                                    PlaylistSubscriptionPhase.IDLE,
                            providerDiscoveryState = providerDiscoveryState,
                            providerOperationState = providerOperationState,
                            onSelectSubscriptionProviderVariant =
                                onSelectSubscriptionProviderVariant,
                            onRetryProviderDiscovery = onRetryProviderDiscovery,
                            onOpenEditor = { sourceKey ->
                                coroutineScope.launch {
                                    navigator.navigateTo(
                                        pane = ListDetailPaneScaffoldRole.Detail,
                                        contentKey = SettingDestination.PlaylistEditor(
                                            sourceKey = sourceKey,
                                        ),
                                    )
                                }
                            },
                            contentPadding = contentPadding,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }

                is SettingDestination.PlaylistEditor -> {
                    PlaylistDetailPane(
                        showHeader = showPlaylistPaneHeader,
                        title = currentPlaylistEditorTitle,
                        onBack = {
                            coroutineScope.launch {
                                navigator.navigateBack(backNavigationBehavior)
                            }
                        },
                    ) {
                        SubscriptionEditorScreen(
                            dataOperationInProgress =
                                backingUpOrRestoring != BackingUpAndRestoringState.NONE,
                            subscriptionSubmissionBlocked =
                                playlistSubscriptionInProgress ||
                                    playlistSubscriptionState.phase !=
                                    PlaylistSubscriptionPhase.IDLE,
                            sourceKey = destination.sourceKey,
                            draftKey = destination.draftKey,
                            providerId = destination.providerId,
                            providerKind = destination.providerKind,
                            reauthenticationPlaylistUrl =
                                destination.reauthenticationPlaylistUrl,
                            onClipboard = onClipboard,
                            onBeginSubscriptionDraft = onBeginSubscriptionDraft,
                            onSubscribe = onSubscribe,
                            providerDiscoveryState = providerDiscoveryState,
                            providerSubscriptionForm = providerSubscriptionForm,
                            providerOperationState = providerOperationState,
                            onSelectSubscriptionProviderVariant =
                                onSelectSubscriptionProviderVariant,
                            onUpdateSubscriptionProviderSetting =
                                onUpdateSubscriptionProviderSetting,
                            onRetryProviderDiscovery = onRetryProviderDiscovery,
                            onRetryProviderReauthentication =
                                onReauthenticateProviderAccount,
                            contentPadding = contentPadding,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }

                SettingDestination.PlaylistEpgSources -> {
                    PlaylistDetailPane(
                        showHeader = showPlaylistPaneHeader,
                        title = playlistEpgTitle,
                        onBack = {
                            coroutineScope.launch {
                                navigator.navigateBack(backNavigationBehavior)
                            }
                        },
                    ) {
                        EpgSourceListScreen(
                            epgs = epgs,
                            onAddEpgSource = {
                                coroutineScope.launch {
                                    navigator.navigateTo(
                                        pane = ListDetailPaneScaffoldRole.Detail,
                                        contentKey = SettingDestination.PlaylistEditor(
                                            sourceKey =
                                                DataSource.EPG.subscriptionSelectionKey(),
                                        ),
                                    )
                                }
                            },
                            onDeleteEpgPlaylist = onDeleteEpgPlaylist,
                            enabled = backingUpOrRestoring ==
                                BackingUpAndRestoringState.NONE &&
                                !providerOperationState.isBusy,
                            contentPadding = contentPadding,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }

                SettingDestination.PlaylistHiddenChannels -> {
                    PlaylistDetailPane(
                        showHeader = showPlaylistPaneHeader,
                        title = playlistHiddenChannelsTitle,
                        onBack = {
                            coroutineScope.launch {
                                navigator.navigateBack(backNavigationBehavior)
                            }
                        },
                    ) {
                        HiddenChannelListScreen(
                            hiddenChannels = hiddenChannels,
                            onUnhideChannel = onUnhideChannel,
                            enabled = backingUpOrRestoring ==
                                BackingUpAndRestoringState.NONE &&
                                !providerOperationState.isBusy,
                            contentPadding = contentPadding,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }

                SettingDestination.PlaylistHiddenCategories -> {
                    PlaylistDetailPane(
                        showHeader = showPlaylistPaneHeader,
                        title = playlistHiddenCategoriesTitle,
                        onBack = {
                            coroutineScope.launch {
                                navigator.navigateBack(backNavigationBehavior)
                            }
                        },
                    ) {
                        HiddenCategoryListScreen(
                            hiddenCategoriesWithPlaylists =
                                hiddenCategoriesWithPlaylists,
                            onUnhidePlaylistCategory = onUnhidePlaylistCategory,
                            enabled = backingUpOrRestoring ==
                                BackingUpAndRestoringState.NONE &&
                                !providerOperationState.isBusy,
                            contentPadding = contentPadding,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }

                SettingDestination.ExtensionPlugins -> {
                    ExtensionPluginListScreen(
                        state = extensionPluginDiscoveryState,
                        operationState = extensionPluginOperationState,
                        onRefresh = onRefreshExtensionPlugins,
                        onOpenDetails = { packageName, serviceName ->
                            coroutineScope.launch {
                                navigator.navigateTo(
                                    pane = ListDetailPaneScaffoldRole.Detail,
                                    contentKey = SettingDestination.ExtensionPluginDetails(
                                        packageName = packageName,
                                        serviceName = serviceName,
                                    ),
                                )
                            }
                        },
                        contentPadding = contentPadding,
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                is SettingDestination.ExtensionPluginDetails -> {
                    val detailState = resolveExtensionPluginDetailContentState(
                        discoveryState = extensionPluginDiscoveryState,
                        packageName = destination.packageName,
                        serviceName = destination.serviceName,
                    )
                    ExtensionPluginDetailScreen(
                        state = detailState,
                        operationState = extensionPluginOperationState,
                        onRetryDiscovery = onRefreshExtensionPlugins,
                        onOpenAuthorization = { reauthorize ->
                            coroutineScope.launch {
                                navigator.navigateTo(
                                    pane = ListDetailPaneScaffoldRole.Detail,
                                    contentKey =
                                        SettingDestination.ExtensionPluginAuthorization(
                                            packageName = destination.packageName,
                                            serviceName = destination.serviceName,
                                            reauthorize = reauthorize,
                                        ),
                                )
                            }
                        },
                        onOpenSettings = { extensionId ->
                            coroutineScope.launch {
                                navigator.navigateTo(
                                    pane = ListDetailPaneScaffoldRole.Detail,
                                    contentKey = SettingDestination.ExtensionPluginSettings(
                                        extensionId = extensionId,
                                    ),
                                )
                            }
                        },
                        onDisable = onDisableExtensionPlugin,
                        onRevoke = onRevokeExtensionPlugin,
                        onClearData = onClearExtensionData,
                        onExportDiagnostics = onExportExtensionDiagnostics,
                        contentPadding = contentPadding,
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                is SettingDestination.ExtensionPluginAuthorization -> {
                    val authorizationState =
                        resolveExtensionPluginDetailContentState(
                            discoveryState = extensionPluginDiscoveryState,
                            packageName = destination.packageName,
                            serviceName = destination.serviceName,
                        )
                    ExtensionPluginAuthorizationScreen(
                        state = authorizationState,
                        operationState = extensionPluginOperationState,
                        onRetryDiscovery = onRefreshExtensionPlugins,
                        reauthorize = destination.reauthorize,
                        onAuthorize = { packageName, serviceName, token, reauthorize ->
                            if (reauthorize) {
                                onReauthorizeExtensionPlugin(packageName, serviceName, token)
                            } else {
                                onEnableExtensionPlugin(packageName, serviceName, token)
                            }
                            coroutineScope.launch {
                                navigator.navigateBack(backNavigationBehavior)
                            }
                        },
                        onCancel = {
                            coroutineScope.launch {
                                navigator.navigateBack(backNavigationBehavior)
                            }
                        },
                        contentPadding = contentPadding,
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                is SettingDestination.ExtensionPluginSettings -> {
                    ExtensionSettingsScreen(
                        state = extensionSettingsState,
                        extensionId = destination.extensionId,
                        onRetry = {
                            onOpenExtensionSettings(destination.extensionId)
                        },
                        onUpdate = onUpdateExtensionSetting,
                        contentPadding = contentPadding,
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                SettingDestination.Appearance -> {
                    AppearanceFragment(
                        colorSchemes = colorSchemes,
                        openColorScheme = openColorScheme,
                        onSelectTheme = onSelectTheme,
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

                SettingDestination.CodecPack -> {
                    CodecPackFragment(
                        state = codecPackState,
                        onInstall = onInstallCodecPack,
                        onDelete = onDeleteCodecPack,
                        onRefresh = onRefreshCodecPack,
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
    BackHandler(navigator.canNavigateBack(backNavigationBehavior)) {
        coroutineScope.launch {
            navigator.navigateBack(backNavigationBehavior)
        }
    }
}

@Composable
private fun PlaylistDetailPane(
    showHeader: Boolean,
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Column(modifier = modifier.fillMaxSize()) {
        if (showHeader) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 64.dp)
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (onBack != null) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.sizeIn(
                            minWidth = 48.dp,
                            minHeight = 48.dp,
                        ),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(
                                string.ui_cd_top_bar_on_back_pressed
                            ),
                        )
                    }
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp)
                        .semantics { heading() },
                )
            }
            HorizontalDivider()
        }
        Box(modifier = Modifier.weight(1f)) {
            content()
        }
    }
}

private fun SettingDestination.usesLocalizedStaticTitle(): Boolean = when (this) {
    SettingDestination.Default,
    SettingDestination.Appearance,
    SettingDestination.Optional,
    SettingDestination.CodecPack -> true
    SettingDestination.Playlists,
    is SettingDestination.PlaylistConfiguration,
    SettingDestination.PlaylistSourcePicker,
    is SettingDestination.PlaylistEditor,
    SettingDestination.PlaylistEpgSources,
    SettingDestination.PlaylistHiddenChannels,
    SettingDestination.PlaylistHiddenCategories,
    SettingDestination.ExtensionPlugins,
    is SettingDestination.ExtensionPluginDetails,
    is SettingDestination.ExtensionPluginAuthorization,
    is SettingDestination.ExtensionPluginSettings -> false
}

private suspend fun ThreePaneScaffoldNavigator<SettingDestination>
    .returnToPlaylistManagement() {
    while (
        currentDestination?.contentKey != SettingDestination.Playlists &&
        canNavigateBack(BackNavigationBehavior.PopLatest)
    ) {
        navigateBack(BackNavigationBehavior.PopLatest)
    }
    if (currentDestination?.contentKey != SettingDestination.Playlists) {
        navigateTo(
            pane = ListDetailPaneScaffoldRole.Detail,
            contentKey = SettingDestination.Playlists,
        )
    }
}
