package com.m3u.smartphone.ui.business.configuration.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import com.m3u.business.playlist.configuration.PlaylistRefreshStatus
import com.m3u.i18n.R.string
import com.m3u.smartphone.ui.material.ktx.rememberUiBidiFormatter
import kotlinx.datetime.LocalDateTime

@Composable
internal fun SyncProgrammesButton(
    status: PlaylistRefreshStatus,
    expired: LocalDateTime?,
    onSyncProgrammes: () -> Unit,
    onCancelSyncProgrammes: () -> Unit,
    modifier: Modifier = Modifier
) {
    val subscribingOrRefreshing = status.isInProgress
    val bidiFormatter = rememberUiBidiFormatter()
    val actionLabel = stringResource(
        if (subscribingOrRefreshing) {
            string.feat_playlist_configuration_cancel_sync_programmes
        } else {
            string.feat_playlist_configuration_sync_programmes
        }
    )
    val loadingDescription = stringResource(string.ui_state_loading)
    val terminalDescription = when (status) {
        PlaylistRefreshStatus.SUCCEEDED -> stringResource(
            string.feat_playlist_configuration_update_succeeded
        )
        PlaylistRefreshStatus.FAILED -> stringResource(
            string.feat_playlist_configuration_update_failed
        )
        PlaylistRefreshStatus.CANCELLED -> stringResource(
            string.feat_setting_subscription_status_cancelled
        )
        else -> null
    }
    val colorScheme = MaterialTheme.colorScheme

    ListItem(
        headlineContent = {
            Text(
                text = actionLabel,
            )
        },
        supportingContent = if (!subscribingOrRefreshing) {
            {
                Text(
                    text = terminalDescription ?: when (expired) {
                        null -> stringResource(
                            string.feat_playlist_configuration_programmes_expired
                        )

                        else -> stringResource(
                            string.feat_playlist_configuration_programmes_expired_time,
                            bidiFormatter.ltr(expired.toString()),
                        )
                    },
                    style = MaterialTheme.typography.bodyMedium.copy(
                        textDirection = TextDirection.ContentOrLtr,
                    ),
                    color = when (status) {
                        PlaylistRefreshStatus.FAILED -> colorScheme.error
                        PlaylistRefreshStatus.SUCCEEDED -> colorScheme.primary
                        else -> Color.Unspecified
                    },
                )
            }
        } else {
            null
        },
        leadingContent = {
            Icon(
                imageVector = Icons.Rounded.Sync,
                contentDescription = null,
            )
        },
        trailingContent = {
            if (subscribingOrRefreshing) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .size(24.dp)
                        .clearAndSetSemantics { },
                    strokeWidth = 2.dp,
                )
            } else if (status == PlaylistRefreshStatus.SUCCEEDED) {
                Icon(
                    imageVector = Icons.Rounded.CheckCircle,
                    contentDescription = null,
                    tint = colorScheme.primary,
                )
            } else if (status == PlaylistRefreshStatus.FAILED) {
                Icon(
                    imageVector = Icons.Rounded.ErrorOutline,
                    contentDescription = null,
                    tint = colorScheme.error,
                )
            } else if (status == PlaylistRefreshStatus.CANCELLED) {
                Icon(
                    imageVector = Icons.Rounded.Info,
                    contentDescription = null,
                    tint = colorScheme.onSurfaceVariant,
                )
            }
        },
        colors = ListItemDefaults.colors(
            headlineColor = if (subscribingOrRefreshing) {
                colorScheme.onTertiaryContainer
            } else {
                colorScheme.onSurface
            },
            supportingColor = if (subscribingOrRefreshing) {
                colorScheme.onTertiaryContainer
            } else {
                colorScheme.onSurfaceVariant
            },
            leadingIconColor = if (subscribingOrRefreshing) {
                colorScheme.onTertiaryContainer
            } else {
                colorScheme.onSurfaceVariant
            },
            containerColor = if (subscribingOrRefreshing) {
                colorScheme.tertiaryContainer
            } else {
                Color.Transparent
            },
        ),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clickable(
                role = Role.Button,
                onClickLabel = actionLabel,
                onClick = if (subscribingOrRefreshing) {
                    onCancelSyncProgrammes
                } else {
                    onSyncProgrammes
                },
            )
            .semantics {
                role = Role.Button
                if (subscribingOrRefreshing) {
                    stateDescription = loadingDescription
                    liveRegion = LiveRegionMode.Polite
                } else if (terminalDescription != null) {
                    stateDescription = terminalDescription
                    liveRegion = LiveRegionMode.Polite
                }
            }
            .testTag("playlist-configuration-sync"),
    )
}
