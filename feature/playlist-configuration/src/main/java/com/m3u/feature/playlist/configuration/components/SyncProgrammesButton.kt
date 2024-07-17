package com.m3u.feature.playlist.configuration.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.m3u.core.util.basic.title
import com.m3u.i18n.R.string
import com.m3u.material.components.CircularProgressIndicator
import com.m3u.material.components.SelectionsDefaults
import com.m3u.material.model.LocalSpacing
import com.m3u.material.shape.AbsoluteSmoothCornerShape
import kotlinx.datetime.LocalDateTime

@Composable
internal fun SyncProgrammesButton(
    subscribingOrRefreshing: Boolean,
    expired: LocalDateTime?,
    onSyncProgrammes: () -> Unit,
    onCancelSyncProgrammes: () -> Unit,
    modifier: Modifier = Modifier
) {
    val spacing = LocalSpacing.current
    val colorScheme = MaterialTheme.colorScheme
    ListItem(
        headlineContent = {
            Text(
                text = if (!subscribingOrRefreshing) stringResource(string.feat_playlist_configuration_sync_programmes).title()
                else stringResource(string.feat_playlist_configuration_cancel_sync_programmes).title(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = {
            AnimatedContent(
                targetState = subscribingOrRefreshing,
                transitionSpec = {
                    fadeIn() + slideInVertically { it } togetherWith fadeOut() + slideOutVertically { it }
                },
                label = "sync programmes state"
            ) { subscribingOrRefreshing ->
                if (!subscribingOrRefreshing) {
                    Text(
                        text = when (expired) {
                            null -> stringResource(string.feat_playlist_configuration_programmes_expired)
                            else -> stringResource(
                                string.feat_playlist_configuration_programmes_expired_time,
                                expired.toString()
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        trailingContent = {
            if (subscribingOrRefreshing) {
                CircularProgressIndicator()
            }
        },
        colors = ListItemDefaults.colors(
            headlineColor = if (!subscribingOrRefreshing) LocalContentColor.current
            else colorScheme.onTertiaryContainer,
            supportingColor = if (!subscribingOrRefreshing) LocalContentColor.current.copy(0.38f)
            else colorScheme.onTertiaryContainer.copy(0.38f),
            containerColor = if (!subscribingOrRefreshing) colorScheme.surface
            else colorScheme.tertiaryContainer
        ),
        modifier = Modifier
            .border(
                1.dp,
                if (subscribingOrRefreshing) colorScheme.tertiaryContainer
                else LocalContentColor.current.copy(0.38f),
                SelectionsDefaults.Shape
            )
            .clip(AbsoluteSmoothCornerShape(spacing.medium, 65))
            .clickable(
                onClick = if (subscribingOrRefreshing) onCancelSyncProgrammes
                else onSyncProgrammes,
            )
            .then(modifier)
    )
}
