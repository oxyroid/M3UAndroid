package com.m3u.smartphone.ui.business.configuration

import android.Manifest
import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.accompanist.permissions.PermissionStatus
import com.google.accompanist.permissions.rememberPermissionState
import com.m3u.business.playlist.configuration.EpgManifest
import com.m3u.business.playlist.configuration.PlaylistConfigurationState
import com.m3u.business.playlist.configuration.PlaylistConfigurationViewModel
import com.m3u.business.playlist.configuration.PlaylistRefreshStatus
import com.m3u.business.playlist.configuration.PlaylistRemovalState
import com.m3u.business.setting.ProviderDiscoveryState
import com.m3u.core.foundation.wrapper.Resource
import com.m3u.core.foundation.util.basic.PlaylistInputKind
import com.m3u.core.foundation.util.basic.sanitizePlaylistInput
import com.m3u.data.database.model.DataSource
import com.m3u.data.database.model.Playlist
import com.m3u.data.database.model.epgUrlsOrXtreamXmlUrl
import com.m3u.data.database.model.refreshable
import com.m3u.data.parser.xtream.XtreamUserInfo
import com.m3u.data.repository.playlist.PlaylistRepository
import com.m3u.i18n.R.string
import com.m3u.smartphone.ui.business.configuration.components.AutoSyncProgrammesButton
import com.m3u.smartphone.ui.business.configuration.components.EpgManifestGallery
import com.m3u.smartphone.ui.business.configuration.components.RefreshPlaylistButton
import com.m3u.smartphone.ui.business.configuration.components.SyncProgrammesButton
import com.m3u.smartphone.ui.business.configuration.components.XtreamPanel
import com.m3u.smartphone.ui.common.helper.Fob
import com.m3u.smartphone.ui.common.helper.Metadata
import com.m3u.smartphone.ui.material.components.Background
import com.m3u.smartphone.ui.material.components.Destination
import com.m3u.smartphone.ui.material.ktx.plus
import com.m3u.smartphone.ui.material.ktx.rememberUiBidiFormatter
import com.m3u.smartphone.ui.material.ktx.safeDisplayText
import com.m3u.smartphone.ui.material.ktx.safeSourceReference
import kotlinx.datetime.LocalDateTime

private val PlaylistConfigurationMaxWidth = 720.dp

@Composable
internal fun PlaylistConfigurationRoute(
    modifier: Modifier = Modifier,
    viewModel: PlaylistConfigurationViewModel = hiltViewModel(),
    contentPadding: PaddingValues = PaddingValues(),
    playlistReference: String? = null,
    providerDisplayNameOverride: String? = null,
    manageAppChrome: Boolean = false,
    onBack: (() -> Unit)? = null,
    onPlaylistRemoved: () -> Unit = {},
) {
    val permissionState = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        rememberPermissionState(Manifest.permission.POST_NOTIFICATIONS)
    } else {
        null
    }

    val state by viewModel.state.collectAsStateWithLifecycle()
    val providerAccountSummary by
        viewModel.providerAccountSummary.collectAsStateWithLifecycle()
    val discoveredProviders by
        viewModel.discoveredProviders.collectAsStateWithLifecycle()
    val manifest by viewModel.manifest.collectAsStateWithLifecycle()
    val playlistRefreshStatus by
        viewModel.playlistRefreshStatus.collectAsStateWithLifecycle()
    val programmeRefreshStatus by
        viewModel.programmeRefreshStatus.collectAsStateWithLifecycle()
    val playlistRemovalState by
        viewModel.playlistRemovalState.collectAsStateWithLifecycle()
    val expired by viewModel.expired.collectAsStateWithLifecycle()
    val xtreamUserInfo by viewModel.xtreamUserInfo.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel, playlistReference) {
        playlistReference?.let(viewModel::openPlaylistReference)
    }

    val localeTag = LocalConfiguration.current.locales[0].toLanguageTag()
    LaunchedEffect(
        viewModel,
        providerDisplayNameOverride,
        providerAccountSummary,
        localeTag,
    ) {
        if (
            providerDisplayNameOverride == null &&
            providerAccountSummary != null
        ) {
            viewModel.refreshProviderCatalog(localeTag)
        }
    }

    val currentOnPlaylistRemoved by rememberUpdatedState(onPlaylistRemoved)
    LaunchedEffect(viewModel, playlistRemovalState) {
        if (playlistRemovalState == PlaylistRemovalState.REMOVED) {
            currentOnPlaylistRemoved()
            viewModel.acknowledgePlaylistRemoval()
        }
    }

    val visibleState = if (
        playlistReference == null ||
        state.playlistReference == playlistReference
    ) {
        state
    } else {
        PlaylistConfigurationState.Loading
    }
    val playlist = (visibleState as? PlaylistConfigurationState.Content)?.playlist
    val resolvedProviderDisplayName = providerDisplayNameOverride
        ?.takeIf(String::isNotBlank)
        ?: providerAccountSummary?.let { account ->
            providerDisplayName(
                account = account,
                discoveryState = discoveredProviders
                    .takeIf { providers -> providers.isNotEmpty() }
                    ?.let { providers ->
                        ProviderDiscoveryState.Ready(providers)
                    },
            )
        }
    val fallbackTitle = stringResource(string.feat_setting_playlist_management)

    if (manageAppChrome) {
        LifecycleResumeEffect(playlist?.title, fallbackTitle, onBack) {
            Metadata.title = AnnotatedString(
                playlist
                    ?.title
                    ?.safeDisplayText()
                    ?.takeIf(String::isNotBlank)
                    ?: fallbackTitle
            )
            Metadata.actions = emptyList()
            Metadata.headlineUrl = ""
            Metadata.color = Color.Unspecified
            Metadata.contentColor = Color.Unspecified
            val backAction = onBack?.let { navigateBack ->
                Fob(
                    destination = Destination.Foryou,
                    icon = Icons.AutoMirrored.Rounded.ArrowBack,
                    iconTextId = string.ui_cd_top_bar_on_back_pressed,
                    onClick = navigateBack,
                )
            }
            Metadata.fob = backAction
            onPauseOrDispose {
                if (Metadata.fob === backAction) {
                    Metadata.fob = null
                }
            }
        }
    }

    when (val currentState = visibleState) {
        PlaylistConfigurationState.Loading -> {
            PlaylistConfigurationLoading(
                modifier = modifier,
                contentPadding = contentPadding,
            )
        }

        is PlaylistConfigurationState.NotFound -> {
            PlaylistConfigurationUnavailable(
                onReturn = onBack ?: onPlaylistRemoved,
                modifier = modifier,
                contentPadding = contentPadding,
            )
        }

        is PlaylistConfigurationState.Content -> {
            PlaylistConfigurationScreen(
                playlist = currentState.playlist,
                providerDisplayName = resolvedProviderDisplayName,
                manifest = manifest,
                playlistRefreshStatus = playlistRefreshStatus,
                programmeRefreshStatus = programmeRefreshStatus,
                removingPlaylist =
                    playlistRemovalState == PlaylistRemovalState.REMOVING,
                playlistRemovalFailed =
                    playlistRemovalState == PlaylistRemovalState.FAILED,
                expired = expired,
                xtreamUserInfo = xtreamUserInfo,
                onUpdatePlaylistTitle = viewModel::onUpdatePlaylistTitle,
                onUpdatePlaylistUserAgent = viewModel::onUpdatePlaylistUserAgent,
                onUpdateEpgPlaylist = viewModel::onUpdateEpgPlaylist,
                onUpdatePlaylistAutoRefreshProgrammes =
                    viewModel::onUpdatePlaylistAutoRefreshProgrammes,
                onRefreshPlaylist = {
                    val refreshUsesForegroundNotification =
                        currentState.playlist.source == DataSource.M3U ||
                            currentState.playlist.source == DataSource.Xtream
                    if (
                        refreshUsesForegroundNotification &&
                        permissionState?.status is PermissionStatus.Denied
                    ) {
                        permissionState.launchPermissionRequest()
                    }
                    viewModel.onRefreshPlaylist()
                },
                onCancelRefreshPlaylist = viewModel::onCancelRefreshPlaylist,
                onSyncProgrammes = {
                    if (permissionState?.status is PermissionStatus.Denied) {
                        permissionState.launchPermissionRequest()
                    }
                    viewModel.onSyncProgrammes()
                },
                onCancelSyncProgrammes = viewModel::onCancelSyncProgrammes,
                onRemovePlaylist = viewModel::onRemovePlaylist,
                onDismissRemovalFailure = viewModel::clearPlaylistRemovalFailure,
                modifier = modifier,
                contentPadding = contentPadding
            )
        }
    }
}

@Composable
private fun PlaylistConfigurationLoading(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    val loadingLabel = stringResource(string.ui_state_loading)
    Background(modifier) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .testTag("playlist-configuration-loading")
                .semantics {
                    liveRegion = LiveRegionMode.Polite
                    stateDescription = loadingLabel
                },
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .size(32.dp)
                        .clearAndSetSemantics {},
                    strokeWidth = 3.dp,
                )
                Text(
                    text = loadingLabel,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun PlaylistConfigurationUnavailable(
    onReturn: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    Background(modifier) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .testTag("playlist-configuration-unavailable"),
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = MaterialTheme.shapes.extraLarge,
                modifier = Modifier
                    .widthIn(max = 480.dp)
                    .fillMaxWidth(),
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.padding(24.dp),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.List,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(40.dp),
                    )
                    Text(
                        text = stringResource(
                            string.feat_playlist_configuration_unavailable_title
                        ),
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(
                        text = stringResource(
                            string.feat_playlist_configuration_unavailable_description
                        ),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(
                        onClick = onReturn,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("playlist-configuration-return"),
                    ) {
                        Text(
                            stringResource(
                                string.feat_playlist_configuration_return_to_management
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaylistConfigurationScreen(
    playlist: Playlist,
    providerDisplayName: String?,
    manifest: EpgManifest,
    playlistRefreshStatus: PlaylistRefreshStatus,
    programmeRefreshStatus: PlaylistRefreshStatus,
    removingPlaylist: Boolean,
    playlistRemovalFailed: Boolean,
    expired: LocalDateTime?,
    xtreamUserInfo: Resource<XtreamUserInfo>,
    onUpdatePlaylistTitle: (String) -> Unit,
    onUpdatePlaylistUserAgent: (String?) -> Unit,
    onUpdateEpgPlaylist: (PlaylistRepository.EpgPlaylistUseCase) -> Unit,
    onUpdatePlaylistAutoRefreshProgrammes: () -> Unit,
    onRefreshPlaylist: () -> Unit,
    onCancelRefreshPlaylist: () -> Unit,
    onSyncProgrammes: () -> Unit,
    onCancelSyncProgrammes: () -> Unit,
    onRemovePlaylist: () -> Unit,
    onDismissRemovalFailure: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues()
) {
    val persistedTitle = remember(playlist.title) {
        playlist.title.sanitizePlaylistInput(PlaylistInputKind.TITLE)
    }
    val persistedUserAgent = remember(playlist.userAgent) {
        playlist.userAgent.orEmpty().sanitizePlaylistInput(
            PlaylistInputKind.USER_AGENT
        )
    }
    var title by remember(playlist.title) { mutableStateOf(persistedTitle) }
    var userAgent by remember(playlist.userAgent) {
        mutableStateOf(persistedUserAgent)
    }
    var showRemoveConfirmation by remember(playlist.url) {
        mutableStateOf(false)
    }
    val normalizedTitle by remember {
        derivedStateOf { title.trim() }
    }
    val titleInvalid by remember {
        derivedStateOf { normalizedTitle.isEmpty() }
    }
    val hasChanged by remember(persistedTitle, persistedUserAgent) {
        derivedStateOf {
            normalizedTitle != persistedTitle || userAgent != persistedUserAgent
        }
    }

    Background(modifier) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .testTag("playlist-configuration"),
            contentPadding = contentPadding + PaddingValues(
                horizontal = 16.dp,
                vertical = 16.dp,
            ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "identity-and-details") {
                PlaylistDetailsSection(
                    playlist = playlist,
                    providerDisplayName = providerDisplayName,
                    title = title,
                    userAgent = userAgent,
                    saveEnabled = hasChanged && !titleInvalid,
                    titleInvalid = titleInvalid,
                    onTitleChange = {
                        title = it.sanitizePlaylistInput(PlaylistInputKind.TITLE)
                    },
                    onUserAgentChange = {
                        userAgent = it.sanitizePlaylistInput(
                            PlaylistInputKind.USER_AGENT
                        )
                    },
                    onSave = {
                        if (normalizedTitle != persistedTitle) {
                            onUpdatePlaylistTitle(normalizedTitle)
                        }
                        if (userAgent != persistedUserAgent) {
                            onUpdatePlaylistUserAgent(userAgent)
                        }
                    },
                    modifier = Modifier.configurationPageWidth(),
                )
            }

            if (
                playlist.refreshable ||
                playlist.epgUrlsOrXtreamXmlUrl().isNotEmpty()
            ) {
                item(key = "updates") {
                    ConfigurationSection(
                        heading = stringResource(
                            string.feat_playlist_configuration_updates
                        ),
                        modifier = Modifier.configurationPageWidth(),
                    ) {
                        if (playlist.refreshable) {
                            RefreshPlaylistButton(
                                status = playlistRefreshStatus,
                                onRefresh = onRefreshPlaylist,
                                onCancel = onCancelRefreshPlaylist,
                            )
                        }
                        if (playlist.epgUrlsOrXtreamXmlUrl().isNotEmpty()) {
                            if (playlist.refreshable) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(start = 56.dp)
                                )
                            }
                            SyncProgrammesButton(
                                status = programmeRefreshStatus,
                                expired = expired,
                                onSyncProgrammes = onSyncProgrammes,
                                onCancelSyncProgrammes = onCancelSyncProgrammes,
                            )
                            HorizontalDivider(
                                modifier = Modifier.padding(start = 56.dp)
                            )
                            AutoSyncProgrammesButton(
                                checked = playlist.autoRefreshProgrammes,
                                onCheckedChange =
                                    onUpdatePlaylistAutoRefreshProgrammes,
                            )
                        }
                    }
                }
            }

            if (playlist.source == DataSource.M3U) {
                item(key = "epg-manifest") {
                    EpgManifestGallery(
                        playlistUrl = playlist.url,
                        manifest = manifest,
                        onUpdateEpgPlaylist = onUpdateEpgPlaylist,
                        modifier = Modifier.configurationPageWidth(),
                    )
                }
            }

            if (playlist.source == DataSource.Xtream) {
                item(key = "xtream-account") {
                    Column(
                        modifier = Modifier.configurationPageWidth(),
                    ) {
                        ConfigurationSectionHeading(
                            text = stringResource(DataSource.Xtream.resId),
                        )
                        XtreamPanel(
                            info = xtreamUserInfo,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            item(key = "remove-source") {
                ConfigurationSection(
                    heading = stringResource(
                        string.feat_playlist_configuration_remove_section
                    ),
                    modifier = Modifier.configurationPageWidth(),
                ) {
                    PlaylistRemovalRow(
                        enabled = !removingPlaylist,
                        onClick = {
                            onDismissRemovalFailure()
                            showRemoveConfirmation = true
                        },
                    )
                }
            }
        }
    }

    if (showRemoveConfirmation) {
        val title = rememberUiBidiFormatter().natural(playlist.title)
        val loadingDescription = stringResource(string.ui_state_loading)
        AlertDialog(
            onDismissRequest = {
                if (!removingPlaylist) {
                    onDismissRemovalFailure()
                    showRemoveConfirmation = false
                }
            },
            title = {
                Text(
                    stringResource(
                        string.feat_playlist_configuration_remove_playlist_title
                    )
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        stringResource(
                            string.feat_playlist_configuration_remove_playlist_message,
                            title,
                        )
                    )
                    if (playlistRemovalFailed) {
                        Text(
                            text = stringResource(string.ui_error_unknown),
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.testTag(
                                "playlist-configuration-remove-error"
                            ),
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = onRemovePlaylist,
                    enabled = !removingPlaylist,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                    modifier = Modifier.testTag(
                        "playlist-configuration-remove-confirm"
                    ).semantics {
                        if (removingPlaylist) {
                            liveRegion = LiveRegionMode.Polite
                            stateDescription = loadingDescription
                        }
                    },
                ) {
                    if (removingPlaylist) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .size(18.dp)
                                .clearAndSetSemantics {},
                            color = LocalContentColor.current,
                            strokeWidth = 2.dp,
                        )
                        Spacer(Modifier.size(8.dp))
                    }
                    Text(
                        stringResource(
                            string.feat_playlist_configuration_remove_playlist
                        )
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        onDismissRemovalFailure()
                        showRemoveConfirmation = false
                    },
                    enabled = !removingPlaylist,
                ) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
            modifier = Modifier.testTag("playlist-configuration-remove-dialog"),
        )
    }
}

@Composable
private fun PlaylistRemovalRow(
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val actionLabel = stringResource(
        string.feat_playlist_configuration_remove_playlist
    )
    ListItem(
        headlineContent = {
            Text(
                text = actionLabel,
            )
        },
        supportingContent = {
            Text(
                text = stringResource(
                    string.feat_playlist_configuration_remove_playlist_description
                ),
            )
        },
        leadingContent = {
            Icon(
                imageVector = Icons.Rounded.DeleteOutline,
                contentDescription = null,
            )
        },
        colors = ListItemDefaults.colors(
            containerColor = Color.Transparent,
            headlineColor = MaterialTheme.colorScheme.error,
            supportingColor = MaterialTheme.colorScheme.onSurfaceVariant,
            leadingIconColor = MaterialTheme.colorScheme.error,
        ),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .alpha(if (enabled) 1f else 0.38f)
            .clickable(
                enabled = enabled,
                role = Role.Button,
                onClickLabel = actionLabel,
                onClick = onClick,
            )
            .semantics { role = Role.Button }
            .testTag("playlist-configuration-remove"),
    )
}

@Composable
private fun PlaylistDetailsSection(
    playlist: Playlist,
    providerDisplayName: String?,
    title: String,
    userAgent: String,
    saveEnabled: Boolean,
    titleInvalid: Boolean,
    onTitleChange: (String) -> Unit,
    onUserAgentChange: (String) -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bidiFormatter = rememberUiBidiFormatter()
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val sourceLabel = providerDisplayName
        ?.takeIf(String::isNotBlank)
        ?: stringResource(playlist.source.resId)
    val sourceIcon = playlist.source.configurationIcon()
    val displayReference = remember(playlist.url, playlist.source) {
        if (
            playlist.source == DataSource.Provider ||
            playlist.source == DataSource.Emby ||
            playlist.source == DataSource.Jellyfin
        ) {
            null
        } else {
            playlist.url.safeSourceReference()
        }
    }
    val titleLabel = stringResource(
        string.feat_playlist_configuration_title
    )
    val titleError = stringResource(string.feat_setting_error_empty_title)
    val userAgentLabel = stringResource(
        string.feat_playlist_configuration_user_agent
    )
    val saveLabel = stringResource(string.ui_action_save_changes)

    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column {
            ListItem(
                headlineContent = {
                    Text(
                        text = playlist.title.safeDisplayText(),
                        style = MaterialTheme.typography.titleMedium.copy(
                            textDirection = TextDirection.ContentOrLtr,
                        ),
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                supportingContent = {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = sourceLabel,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                textDirection = TextDirection.ContentOrLtr,
                            ),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        displayReference?.let { reference ->
                            Text(
                                text = bidiFormatter.ltr(reference),
                                style = MaterialTheme.typography.bodySmall.copy(
                                    textDirection = TextDirection.Ltr,
                                ),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                },
                leadingContent = {
                    Icon(
                        imageVector = sourceIcon,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                    )
                },
                colors = ListItemDefaults.colors(
                    containerColor = Color.Transparent,
                    supportingColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    leadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 72.dp)
                    .testTag("playlist-configuration-identity"),
            )

            HorizontalDivider(modifier = Modifier.padding(start = 56.dp))

            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = onTitleChange,
                    label = { Text(titleLabel) },
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        textDirection = TextDirection.ContentOrLtr,
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    singleLine = true,
                    isError = titleInvalid,
                    supportingText = if (titleInvalid) {
                        { Text(titleError) }
                    } else {
                        null
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("playlist-configuration-title"),
                )
                OutlinedTextField(
                    value = userAgent,
                    onValueChange = onUserAgentChange,
                    label = { Text(userAgentLabel) },
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        textDirection = TextDirection.Ltr,
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("playlist-configuration-user-agent"),
                )
                Button(
                    onClick = {
                        onSave()
                        focusManager.clearFocus(force = true)
                        keyboardController?.hide()
                    },
                    enabled = saveEnabled,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("playlist-configuration-save"),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Save,
                        contentDescription = null,
                    )
                    Text(
                        text = saveLabel,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ConfigurationSection(
    heading: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = modifier) {
        ConfigurationSectionHeading(text = heading)
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            Column(content = content)
        }
    }
}

@Composable
private fun ConfigurationSectionHeading(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp)
            .semantics { heading() },
    )
}

private fun Modifier.configurationPageWidth(): Modifier = widthIn(
    max = PlaylistConfigurationMaxWidth,
).fillMaxWidth()

private fun DataSource.configurationIcon(): ImageVector = when (this) {
    DataSource.M3U -> Icons.Rounded.Link
    DataSource.EPG -> Icons.Rounded.DateRange
    DataSource.Xtream -> Icons.Rounded.Cloud
    DataSource.Emby, DataSource.Jellyfin, DataSource.Provider ->
        Icons.Rounded.Extension
    else -> Icons.AutoMirrored.Rounded.List
}
