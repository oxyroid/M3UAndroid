package com.m3u.smartphone.ui.business.setting.fragments

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.m3u.business.setting.BackingUpAndRestoringState
import com.m3u.business.setting.PlaylistSubscriptionPhase
import com.m3u.business.setting.PlaylistSubscriptionState
import com.m3u.business.setting.ProviderDiscoveryState
import com.m3u.business.setting.ProviderOperationState
import com.m3u.data.database.model.Channel
import com.m3u.data.database.model.DataSource
import com.m3u.data.database.model.Playlist
import com.m3u.data.repository.provider.ProviderAccountSummary
import com.m3u.data.worker.playlistWorkTag
import com.m3u.i18n.R.plurals
import com.m3u.i18n.R.string
import com.m3u.smartphone.ui.business.configuration.providerDisplayName
import com.m3u.smartphone.ui.business.setting.components.EpgPlaylistItem
import com.m3u.smartphone.ui.business.setting.components.HiddenChannelItem
import com.m3u.smartphone.ui.business.setting.components.HiddenPlaylistGroupItem
import com.m3u.smartphone.ui.material.ktx.UiBidiFormatter
import com.m3u.smartphone.ui.material.ktx.plus
import com.m3u.smartphone.ui.material.ktx.rememberUiBidiFormatter
import com.m3u.smartphone.ui.material.ktx.safeDisplayText
import java.text.Collator
import java.util.Locale

private val PlaylistPageMaxWidth = 720.dp

internal fun playlistTitleComparator(locale: Locale): Comparator<String> {
    val collator = Collator.getInstance(locale).apply {
        strength = Collator.SECONDARY
    }
    return Comparator { left, right ->
        collator.compare(left.safeDisplayText(), right.safeDisplayText())
    }
}

internal fun playlistTitleInLocalizedSentence(
    title: String,
    bidiFormatter: UiBidiFormatter,
): String = bidiFormatter.natural(title.safeDisplayText())

@Composable
internal fun PlaylistManagementOverviewScreen(
    backingUpOrRestoring: BackingUpAndRestoringState,
    playlists: Map<Playlist, Int>?,
    playlistSubscriptionInProgress: Boolean,
    playlistSubscriptionState: PlaylistSubscriptionState,
    epgCount: Int,
    hiddenChannelCount: Int,
    hiddenCategoryCount: Int,
    providerDiscoveryState: ProviderDiscoveryState,
    providerAccountSummaries: List<ProviderAccountSummary>,
    providerOperationState: ProviderOperationState,
    onAddPlaylist: () -> Unit,
    onCancelPlaylistSubscription: () -> Unit,
    onDismissPlaylistSubscription: () -> Unit,
    onRetryPlaylistSubscription: (DataSource) -> Unit,
    onOpenPlaylistConfiguration: (Playlist) -> Unit,
    onOpenEpgSources: () -> Unit,
    onOpenHiddenChannels: () -> Unit,
    onOpenHiddenCategories: () -> Unit,
    onReauthenticateProviderAccount: (ProviderAccountSummary) -> Unit,
    onBackup: () -> Unit,
    onRestore: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    val locales = LocalConfiguration.current.locales
    val currentLocale = if (locales.isEmpty) Locale.getDefault() else locales[0]
    val titleComparator = remember(currentLocale) {
        playlistTitleComparator(currentLocale)
    }
    val orderedPlaylists = playlists?.entries.orEmpty().sortedWith(
        compareBy(titleComparator) { entry -> entry.key.title }
    )
    val accountsRequiringAttention = providerAccountSummaries.filter { account ->
        account.requiresReauthentication
    }
    val operationInProgress = providerOperationState.isBusy
    val backupInProgress =
        backingUpOrRestoring == BackingUpAndRestoringState.BACKING_UP ||
            backingUpOrRestoring == BackingUpAndRestoringState.BOTH
    val restoreInProgress =
        backingUpOrRestoring == BackingUpAndRestoringState.RESTORING ||
            backingUpOrRestoring == BackingUpAndRestoringState.BOTH
    val dataOperationInProgress =
        backingUpOrRestoring != BackingUpAndRestoringState.NONE
    val subscriptionInProgress =
        playlistSubscriptionState.isInProgress ||
            playlistSubscriptionInProgress
    val subscriptionStatusVisible =
        playlistSubscriptionState.phase != PlaylistSubscriptionPhase.IDLE

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("playlist-management-overview"),
        contentPadding = contentPadding + PaddingValues(vertical = 16.dp),
    ) {
        if (accountsRequiringAttention.isNotEmpty()) {
            item(key = "attention-heading") {
                PlaylistPageContent {
                    PlaylistSectionHeading(
                        text = stringResource(string.feat_setting_playlist_needs_attention),
                    )
                }
            }
            items(
                items = accountsRequiringAttention,
                key = { account -> account.playlistUrl },
            ) { account ->
                PlaylistPageContent {
                    ProviderReauthenticationCard(
                        account = account,
                        inProgress = providerOperationState.isReauthenticating(
                            account.playlistUrl
                        ),
                        enabled = !operationInProgress && !dataOperationInProgress,
                        onReauthenticate = {
                            onReauthenticateProviderAccount(account)
                        },
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
            }
        }

        item(key = "add-playlist") {
            PlaylistPageContent {
                PlaylistDestinationRow(
                    headline = stringResource(string.feat_setting_label_add_playlist),
                    supporting = stringResource(
                        string.feat_setting_playlist_add_description
                    ),
                    icon = Icons.Rounded.Add,
                    onClick = onAddPlaylist,
                    emphasized = true,
                    enabled =
                        !dataOperationInProgress &&
                            !operationInProgress &&
                            !subscriptionInProgress &&
                            !subscriptionStatusVisible,
                    modifier = Modifier.testTag("playlist-add-action"),
                )
            }
        }

        item(key = "playlists-heading") {
            PlaylistPageContent {
                PlaylistSectionHeading(
                    text = stringResource(string.feat_setting_playlist_your_playlists),
                )
            }
        }
        if (playlists == null) {
            item(key = "playlists-loading") {
                PlaylistPageContent {
                    PlaylistLoadingState()
                }
            }
        } else {
            if (playlistSubscriptionState.phase != PlaylistSubscriptionPhase.IDLE) {
                item(key = "playlist-subscription-status") {
                    PlaylistPageContent {
                        PlaylistSubscriptionStatusCard(
                            state = playlistSubscriptionState,
                            retryEnabled =
                                !dataOperationInProgress &&
                                    !operationInProgress &&
                                    !playlistSubscriptionInProgress,
                            onCancel = onCancelPlaylistSubscription,
                            onDismiss = onDismissPlaylistSubscription,
                            onRetry = onRetryPlaylistSubscription,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                    }
                }
            } else if (playlistSubscriptionInProgress) {
                item(key = "playlist-update-in-progress") {
                    PlaylistPageContent {
                        PlaylistLoadingState(
                            text = stringResource(
                                string.feat_setting_playlist_update_in_progress
                            ),
                            modifier = Modifier.testTag(
                                "playlist-management-update-in-progress"
                            ),
                        )
                    }
                }
            }
            if (orderedPlaylists.isEmpty() && !subscriptionInProgress) {
                item(key = "playlists-empty") {
                    PlaylistPageContent {
                        PlaylistInlineEmptyState(
                            text = stringResource(
                                string.feat_setting_playlist_no_playlists
                            ),
                        )
                    }
                }
            } else {
                itemsIndexed(
                    items = orderedPlaylists,
                    key = { _, entry -> entry.key.url },
                ) { index, (playlist, channelCount) ->
                    val providerAccount = providerAccountSummaries.firstOrNull { account ->
                        account.playlistUrl == playlist.url
                    }
                    val providerName = providerAccount?.let { account ->
                        providerDisplayName(account, providerDiscoveryState)
                    }
                    PlaylistPageContent {
                        PlaylistSubscriptionRow(
                            playlist = playlist,
                            channelCount = channelCount,
                            providerDisplayName = providerName,
                            enabled = !dataOperationInProgress && !operationInProgress,
                            onClick = { onOpenPlaylistConfiguration(playlist) },
                            modifier = Modifier.testTag(
                                "playlist-management-item:${playlistWorkTag(playlist.url)}"
                            ),
                        )
                        if (index != orderedPlaylists.lastIndex) {
                            PlaylistInsetDivider()
                        }
                    }
                }
            }
        }

        item(key = "content-heading") {
            PlaylistPageContent {
                PlaylistSectionHeading(
                    text = stringResource(
                        string.feat_setting_playlist_content_and_guide
                    ),
                )
            }
        }
        item(key = "epg-sources") {
            PlaylistPageContent {
                PlaylistDestinationRow(
                    headline = stringResource(string.feat_setting_label_epg_playlists),
                    supporting = listOf(
                        stringResource(string.feat_setting_playlist_manage_epg_description),
                        pluralStringResource(
                            plurals.feat_setting_playlist_epg_source_count,
                            epgCount,
                            epgCount,
                        ),
                    ),
                    icon = Icons.Rounded.DateRange,
                    onClick = onOpenEpgSources,
                    modifier = Modifier.testTag("playlist-overview-epg-sources"),
                )
                PlaylistInsetDivider()
            }
        }
        item(key = "hidden-channels") {
            PlaylistPageContent {
                PlaylistDestinationRow(
                    headline = stringResource(string.feat_setting_label_hidden_channels),
                    supporting = listOf(
                        stringResource(
                            string.feat_setting_playlist_restore_hidden_channels_description
                        ),
                        pluralStringResource(
                            plurals.feat_setting_playlist_hidden_channel_count,
                            hiddenChannelCount,
                            hiddenChannelCount,
                        ),
                    ),
                    icon = Icons.Rounded.VisibilityOff,
                    onClick = onOpenHiddenChannels,
                    modifier = Modifier.testTag("playlist-overview-hidden-channels"),
                )
                PlaylistInsetDivider()
            }
        }
        item(key = "hidden-categories") {
            PlaylistPageContent {
                PlaylistDestinationRow(
                    headline = stringResource(
                        string.feat_setting_label_hidden_playlist_groups
                    ),
                    supporting = listOf(
                        stringResource(
                            string.feat_setting_playlist_restore_hidden_categories_description
                        ),
                        pluralStringResource(
                            plurals.feat_setting_playlist_hidden_category_count,
                            hiddenCategoryCount,
                            hiddenCategoryCount,
                        ),
                    ),
                    icon = Icons.AutoMirrored.Rounded.List,
                    onClick = onOpenHiddenCategories,
                    modifier = Modifier.testTag("playlist-overview-hidden-categories"),
                )
            }
        }

        item(key = "data-heading") {
            PlaylistPageContent {
                PlaylistSectionHeading(
                    text = stringResource(
                        string.feat_setting_playlist_data_and_recovery
                    ),
                )
            }
        }
        item(key = "backup") {
            PlaylistPageContent {
                PlaylistDataActionRow(
                    headline = stringResource(string.feat_setting_label_backup),
                    supporting = stringResource(
                        string.feat_setting_playlist_backup_description
                    ),
                    icon = Icons.Rounded.Backup,
                    loading = backupInProgress,
                    enabled = !dataOperationInProgress && !operationInProgress,
                    onClick = onBackup,
                    modifier = Modifier.testTag("playlist-backup-action"),
                )
                PlaylistInsetDivider()
            }
        }
        item(key = "restore") {
            PlaylistPageContent {
                PlaylistDataActionRow(
                    headline = stringResource(string.feat_setting_label_restore),
                    supporting = stringResource(
                        string.feat_setting_playlist_restore_description
                    ),
                    icon = Icons.Rounded.Restore,
                    loading = restoreInProgress,
                    enabled = !dataOperationInProgress && !operationInProgress,
                    onClick = onRestore,
                    modifier = Modifier.testTag("playlist-restore-action"),
                )
            }
        }
    }
}

@Composable
private fun PlaylistSubscriptionStatusCard(
    state: PlaylistSubscriptionState,
    retryEnabled: Boolean,
    onCancel: () -> Unit,
    onDismiss: () -> Unit,
    onRetry: (DataSource) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state.phase == PlaylistSubscriptionPhase.IDLE) return

    val bidiFormatter = rememberUiBidiFormatter()
    val statusText = when (state.phase) {
        PlaylistSubscriptionPhase.IDLE -> return
        PlaylistSubscriptionPhase.ENQUEUED ->
            stringResource(string.feat_setting_subscription_status_queued)
        PlaylistSubscriptionPhase.RUNNING ->
            stringResource(string.feat_setting_subscription_status_running)
        PlaylistSubscriptionPhase.SUCCEEDED ->
            stringResource(string.feat_setting_subscription_status_succeeded)
        PlaylistSubscriptionPhase.FAILED ->
            stringResource(string.feat_setting_subscription_status_failed)
        PlaylistSubscriptionPhase.CANCELLED ->
            stringResource(string.feat_setting_subscription_status_cancelled)
    }
    val sourceText = state.source?.let { source -> stringResource(source.resId) }
    val title = state.title
        ?.safeDisplayText()
        ?.takeIf(String::isNotBlank)
        ?.let(bidiFormatter::natural)
    val isInProgress = state.isInProgress
    val containerColor = when (state.phase) {
        PlaylistSubscriptionPhase.ENQUEUED,
        PlaylistSubscriptionPhase.RUNNING ->
            MaterialTheme.colorScheme.tertiaryContainer
        PlaylistSubscriptionPhase.SUCCEEDED ->
            MaterialTheme.colorScheme.primaryContainer
        PlaylistSubscriptionPhase.FAILED ->
            MaterialTheme.colorScheme.errorContainer
        PlaylistSubscriptionPhase.CANCELLED,
        PlaylistSubscriptionPhase.IDLE ->
            MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val contentColor = when (state.phase) {
        PlaylistSubscriptionPhase.ENQUEUED,
        PlaylistSubscriptionPhase.RUNNING ->
            MaterialTheme.colorScheme.onTertiaryContainer
        PlaylistSubscriptionPhase.SUCCEEDED ->
            MaterialTheme.colorScheme.onPrimaryContainer
        PlaylistSubscriptionPhase.FAILED ->
            MaterialTheme.colorScheme.onErrorContainer
        PlaylistSubscriptionPhase.CANCELLED,
        PlaylistSubscriptionPhase.IDLE ->
            MaterialTheme.colorScheme.onSurface
    }

    Surface(
        color = containerColor,
        contentColor = contentColor,
        shape = MaterialTheme.shapes.extraLarge,
        modifier = modifier
            .fillMaxWidth()
            .testTag("playlist-subscription-status")
            .semantics {
                liveRegion = LiveRegionMode.Polite
                stateDescription = statusText
            },
    ) {
        Column(
            modifier = Modifier.padding(
                start = 16.dp,
                end = 8.dp,
                top = 16.dp,
                bottom = 8.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                when {
                    isInProgress -> CircularProgressIndicator(
                        modifier = Modifier
                            .size(24.dp)
                            .clearAndSetSemantics {},
                        color = contentColor,
                        strokeWidth = 2.dp,
                    )
                    state.phase == PlaylistSubscriptionPhase.SUCCEEDED -> Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = null,
                    )
                    state.phase == PlaylistSubscriptionPhase.FAILED -> Icon(
                        imageVector = Icons.Rounded.ErrorOutline,
                        contentDescription = null,
                    )
                    else -> Icon(
                        imageVector = Icons.Rounded.Info,
                        contentDescription = null,
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    title?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.titleMedium.copy(
                                textDirection = TextDirection.ContentOrLtr,
                            ),
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    sourceText?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(
                    space = 8.dp,
                    alignment = Alignment.End,
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                state.source
                    ?.takeIf {
                        state.phase == PlaylistSubscriptionPhase.FAILED
                    }
                    ?.let { failedSource ->
                        TextButton(
                            onClick = { onRetry(failedSource) },
                            enabled = retryEnabled,
                            modifier = Modifier.testTag(
                                "playlist-subscription-retry"
                            ),
                        ) {
                            Text(stringResource(string.ui_action_retry))
                        }
                    }
                TextButton(
                    onClick = if (isInProgress) onCancel else onDismiss,
                    modifier = Modifier.testTag(
                        if (isInProgress) {
                            "playlist-subscription-cancel"
                        } else {
                            "playlist-subscription-dismiss"
                        }
                    ),
                ) {
                    Text(
                        stringResource(
                            if (isInProgress) {
                                android.R.string.cancel
                            } else {
                                android.R.string.ok
                            }
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun PlaylistSubscriptionRow(
    playlist: Playlist,
    channelCount: Int,
    providerDisplayName: String?,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bidiFormatter = rememberUiBidiFormatter()
    val title = bidiFormatter.natural(playlist.title)
    val source = providerDisplayName
        ?.safeDisplayText()
        ?.takeIf(String::isNotBlank)
        ?.let(bidiFormatter::natural)
        ?: stringResource(playlist.source.resId)
    val count = pluralStringResource(
        plurals.feat_setting_playlist_channel_count,
        channelCount,
        channelCount,
    )
    val sourceIcon = when (playlist.source) {
        DataSource.M3U -> Icons.Rounded.Link
        DataSource.Xtream -> Icons.Rounded.Cloud
        DataSource.Emby, DataSource.Jellyfin, DataSource.Provider ->
            Icons.Rounded.Extension
        else -> Icons.AutoMirrored.Rounded.List
    }

    ListItem(
        headlineContent = {
            Text(
                text = title,
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
                    text = source,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        textDirection = TextDirection.ContentOrLtr,
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = count,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        leadingContent = {
            Icon(
                imageVector = sourceIcon,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
            )
        },
        trailingContent = {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                contentDescription = null,
            )
        },
        colors = ListItemDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .alpha(if (enabled) 1f else 0.38f)
            .clickable(
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            ),
    )
}

@Composable
private fun PlaylistInlineEmptyState(
    text: String,
    modifier: Modifier = Modifier,
) {
    ListItem(
        headlineContent = {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        leadingContent = {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.List,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        colors = ListItemDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.extraLarge),
    )
}

@Composable
private fun PlaylistLoadingState(
    modifier: Modifier = Modifier,
    text: String? = null,
) {
    val stateText = text ?: stringResource(string.ui_state_loading)
    ListItem(
        headlineContent = {
            Text(
                text = stateText,
                style = MaterialTheme.typography.bodyLarge,
            )
        },
        leadingContent = {
            CircularProgressIndicator(
                modifier = Modifier
                    .size(24.dp)
                    .clearAndSetSemantics {},
                strokeWidth = 2.dp,
            )
        },
        colors = ListItemDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.extraLarge)
            .semantics {
                liveRegion = LiveRegionMode.Polite
                stateDescription = stateText
            },
    )
}

@Composable
internal fun EpgSourceListScreen(
    epgs: List<Playlist>,
    onAddEpgSource: () -> Unit,
    onDeleteEpgPlaylist: (String) -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    var pendingDeletion by remember { mutableStateOf<Playlist?>(null) }
    val bidiFormatter = rememberUiBidiFormatter()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("playlist-list:epg-sources"),
        contentPadding = contentPadding + PaddingValues(vertical = 16.dp),
    ) {
        item(key = "add-epg-source") {
            PlaylistPageContent {
                PlaylistDestinationRow(
                    headline = stringResource(
                        string.feat_setting_playlist_add_epg_source
                    ),
                    supporting = stringResource(
                        string.feat_setting_playlist_add_epg_source_description
                    ),
                    icon = Icons.Rounded.Add,
                    onClick = onAddEpgSource,
                    emphasized = true,
                    enabled = enabled,
                    modifier = Modifier.testTag("playlist-add-epg-action"),
                )
            }
        }
        if (epgs.isEmpty()) {
            item(key = "empty") {
                PlaylistEmptyState(
                    text = stringResource(
                        string.feat_setting_playlist_epg_sources_empty
                    ),
                    icon = Icons.Rounded.DateRange,
                )
            }
        } else {
            itemsIndexed(
                items = epgs,
                key = { _, playlist -> playlist.url },
            ) { index, playlist ->
                val titleInAction = playlistTitleInLocalizedSentence(
                    title = playlist.title,
                    bidiFormatter = bidiFormatter,
                )
                val deleteDescription = stringResource(
                    string.feat_setting_playlist_delete_epg_action_description,
                    titleInAction,
                )
                PlaylistPageContent {
                    EpgPlaylistItem(
                        epgPlaylist = playlist,
                        onDeleteEpgPlaylist = { pendingDeletion = playlist },
                        deleteContentDescription = deleteDescription,
                        enabled = enabled,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag(
                                "playlist-epg:${playlistWorkTag(playlist.url)}"
                            ),
                    )
                    if (index != epgs.lastIndex) {
                        PlaylistInsetDivider(startPadding = 16.dp)
                    }
                }
            }
        }
    }

    pendingDeletion?.let { playlist ->
        val title = playlistTitleInLocalizedSentence(
            title = playlist.title,
            bidiFormatter = bidiFormatter,
        )
        AlertDialog(
            onDismissRequest = { pendingDeletion = null },
            title = {
                Text(stringResource(string.feat_setting_playlist_delete_epg_title))
            },
            text = {
                Text(
                    stringResource(
                        string.feat_setting_playlist_delete_epg_message,
                        title,
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingDeletion = null
                        onDeleteEpgPlaylist(playlist.url)
                    },
                    modifier = Modifier.testTag("playlist-delete-epg-confirm"),
                    enabled = enabled,
                ) {
                    Text(stringResource(string.ui_action_delete_epg))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeletion = null }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
            modifier = Modifier.testTag("playlist-delete-epg-dialog"),
        )
    }
}

@Composable
internal fun HiddenChannelListScreen(
    hiddenChannels: List<Channel>,
    onUnhideChannel: (Int) -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    val bidiFormatter = rememberUiBidiFormatter()
    PlaylistManagementList(
        empty = hiddenChannels.isEmpty(),
        emptyText = stringResource(string.feat_setting_playlist_hidden_channels_empty),
        emptyIcon = Icons.Rounded.VisibilityOff,
        testTag = "playlist-list:hidden-channels",
        modifier = modifier,
        contentPadding = contentPadding,
    ) {
        itemsIndexed(
            items = hiddenChannels,
            key = { _, channel -> channel.id },
        ) { index, channel ->
            val titleInAction = bidiFormatter.natural(channel.title)
            PlaylistPageContent {
                HiddenChannelItem(
                    channel = channel,
                    onShow = { onUnhideChannel(channel.id) },
                    showLabel = stringResource(string.feat_setting_playlist_show_action),
                    showContentDescription = stringResource(
                        string.feat_setting_playlist_show_channel_action_description,
                        titleInAction,
                    ),
                    enabled = enabled,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("playlist-hidden-channel:${channel.id}"),
                )
                if (index != hiddenChannels.lastIndex) {
                    PlaylistInsetDivider(startPadding = 16.dp)
                }
            }
        }
    }
}

@Composable
internal fun HiddenCategoryListScreen(
    hiddenCategoriesWithPlaylists: List<Pair<Playlist, String>>,
    onUnhidePlaylistCategory: (playlistUrl: String, category: String) -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    val bidiFormatter = rememberUiBidiFormatter()
    PlaylistManagementList(
        empty = hiddenCategoriesWithPlaylists.isEmpty(),
        emptyText = stringResource(string.feat_setting_playlist_hidden_categories_empty),
        emptyIcon = Icons.AutoMirrored.Rounded.List,
        testTag = "playlist-list:hidden-categories",
        modifier = modifier,
        contentPadding = contentPadding,
    ) {
        itemsIndexed(
            items = hiddenCategoriesWithPlaylists,
            key = { _, (playlist, category) ->
                "${playlist.url}\u0000$category"
            },
        ) { index, (playlist, category) ->
            val categoryInAction = bidiFormatter.natural(category)
            val categoryKey = playlistWorkTag("${playlist.url}\u0000$category")
            PlaylistPageContent {
                HiddenPlaylistGroupItem(
                    playlist = playlist,
                    group = category,
                    onShow = {
                        onUnhidePlaylistCategory(playlist.url, category)
                    },
                    showLabel = stringResource(string.feat_setting_playlist_show_action),
                    showContentDescription = stringResource(
                        string.feat_setting_playlist_show_category_action_description,
                        categoryInAction,
                    ),
                    enabled = enabled,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("playlist-hidden-category:$categoryKey"),
                )
                if (index != hiddenCategoriesWithPlaylists.lastIndex) {
                    PlaylistInsetDivider(startPadding = 16.dp)
                }
            }
        }
    }
}

@Composable
private fun PlaylistDestinationRow(
    headline: String,
    supporting: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    emphasized: Boolean = false,
    enabled: Boolean = true,
) {
    PlaylistDestinationRow(
        headline = headline,
        supporting = listOf(supporting),
        icon = icon,
        onClick = onClick,
        modifier = modifier,
        emphasized = emphasized,
        enabled = enabled,
    )
}

@Composable
private fun PlaylistDestinationRow(
    headline: String,
    supporting: List<String>,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    emphasized: Boolean = false,
    enabled: Boolean = true,
) {
    val shape = MaterialTheme.shapes.extraLarge
    ListItem(
        headlineContent = {
            Text(
                text = headline,
                style = MaterialTheme.typography.titleMedium,
            )
        },
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                supporting.forEach { line ->
                    Text(
                        text = line,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        },
        leadingContent = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
            )
        },
        trailingContent = {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                contentDescription = null,
            )
        },
        colors = ListItemDefaults.colors(
            containerColor = if (emphasized) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            },
            headlineColor = if (emphasized) {
                MaterialTheme.colorScheme.onSecondaryContainer
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            supportingColor = if (emphasized) {
                MaterialTheme.colorScheme.onSecondaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            leadingIconColor = if (emphasized) {
                MaterialTheme.colorScheme.onSecondaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            trailingIconColor = if (emphasized) {
                MaterialTheme.colorScheme.onSecondaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        ),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clip(shape)
            .alpha(if (enabled) 1f else 0.38f)
            .clickable(
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            ),
    )
}

@Composable
private fun PlaylistDataActionRow(
    headline: String,
    supporting: String,
    icon: ImageVector,
    loading: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val loadingDescription = stringResource(string.ui_state_loading)
    ListItem(
        headlineContent = {
            Text(
                text = headline,
                style = MaterialTheme.typography.titleMedium,
            )
        },
        supportingContent = {
            Text(
                text = supporting,
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        leadingContent = {
            Icon(imageVector = icon, contentDescription = null)
        },
        trailingContent = {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .size(24.dp)
                        .clearAndSetSemantics { },
                    strokeWidth = 2.dp,
                )
            }
        },
        colors = ListItemDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .alpha(if (enabled || loading) 1f else 0.38f)
            .clickable(
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .semantics {
                role = Role.Button
                if (loading) {
                    stateDescription = loadingDescription
                    liveRegion = LiveRegionMode.Polite
                }
            },
    )
}

@Composable
private fun PlaylistSectionHeading(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 8.dp)
            .semantics { heading() },
    )
}

@Composable
private fun PlaylistInsetDivider(
    startPadding: Dp = 56.dp,
) {
    HorizontalDivider(
        modifier = Modifier
            .widthIn(max = PlaylistPageMaxWidth)
            .fillMaxWidth()
            .padding(start = startPadding)
    )
}

@Composable
private fun PlaylistPageContent(
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = PlaylistPageMaxWidth)
                .fillMaxWidth(),
        ) {
            content()
        }
    }
}

@Composable
private fun PlaylistManagementList(
    empty: Boolean,
    emptyText: String,
    emptyIcon: ImageVector,
    testTag: String,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit,
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag(testTag),
        contentPadding = contentPadding + PaddingValues(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (empty) {
            item(key = "empty") {
                PlaylistEmptyState(text = emptyText, icon = emptyIcon)
            }
        } else {
            content()
        }
    }
}

@Composable
private fun PlaylistEmptyState(
    text: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = PlaylistPageMaxWidth)
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(40.dp),
            )
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}
