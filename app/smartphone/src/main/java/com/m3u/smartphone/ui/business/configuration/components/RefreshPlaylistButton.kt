package com.m3u.smartphone.ui.business.configuration.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Refresh
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
import androidx.compose.ui.unit.dp
import com.m3u.business.playlist.configuration.PlaylistRefreshStatus
import com.m3u.i18n.R.string

@Composable
internal fun RefreshPlaylistButton(
    status: PlaylistRefreshStatus,
    onRefresh: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val refreshing = status.isInProgress
    val actionLabel = stringResource(
        if (refreshing) {
            string.feat_playlist_configuration_cancel_refresh_playlist
        } else {
            string.feat_playlist_configuration_refresh_playlist
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
        supportingContent = if (refreshing) null else {
            {
                Text(
                    text = terminalDescription ?: stringResource(
                        string.feat_playlist_configuration_refresh_playlist_description
                    ),
                    color = when (status) {
                        PlaylistRefreshStatus.FAILED -> colorScheme.error
                        PlaylistRefreshStatus.SUCCEEDED -> colorScheme.primary
                        else -> Color.Unspecified
                    },
                )
            }
        },
        leadingContent = {
            Icon(
                imageVector = Icons.Rounded.Refresh,
                contentDescription = null,
            )
        },
        trailingContent = {
            if (refreshing) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .size(24.dp)
                        .clearAndSetSemantics {},
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
            headlineColor = if (refreshing) {
                colorScheme.onTertiaryContainer
            } else {
                colorScheme.onSurface
            },
            supportingColor = if (refreshing) {
                colorScheme.onTertiaryContainer
            } else {
                colorScheme.onSurfaceVariant
            },
            leadingIconColor = if (refreshing) {
                colorScheme.onTertiaryContainer
            } else {
                colorScheme.onSurfaceVariant
            },
            containerColor = if (refreshing) {
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
                onClick = if (refreshing) onCancel else onRefresh,
            )
            .semantics {
                role = Role.Button
                if (refreshing) {
                    stateDescription = loadingDescription
                    liveRegion = LiveRegionMode.Polite
                } else if (terminalDescription != null) {
                    stateDescription = terminalDescription
                    liveRegion = LiveRegionMode.Polite
                }
            }
            .testTag("playlist-configuration-refresh"),
    )
}
