package com.m3u.feature.playlist.configuration.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.m3u.core.util.basic.title
import com.m3u.data.database.model.Playlist
import com.m3u.data.repository.playlist.PlaylistRepository
import com.m3u.feature.playlist.configuration.EpgManifest
import com.m3u.i18n.R.string
import com.m3u.material.components.SelectionsDefaults
import com.m3u.material.model.LocalSpacing
import com.m3u.material.shape.AbsoluteSmoothCornerShape

internal fun LazyListScope.EpgManifestGallery(
    playlistUrl: String,
    manifest: EpgManifest,
    onUpdateEpgPlaylist: (PlaylistRepository.UpdateEpgPlaylistUseCase) -> Unit
) {
    stickyHeader {
        val spacing = LocalSpacing.current
        Text(
            text = stringResource(string.feat_playlist_configuration_enabled_epgs).title(),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier
                .background(MaterialTheme.colorScheme.surface)
                .fillMaxWidth()
                .padding(
                    horizontal = spacing.medium,
                    vertical = spacing.small
                )
        )
    }
    items(manifest.entries.toList()) { (epg, associated) ->
        EpgManifestGalleryItem(
            playlistUrl = playlistUrl,
            epg = epg,
            associated = associated,
            onUpdateEpgPlaylist = onUpdateEpgPlaylist,
        )
    }
}

@Composable
private fun EpgManifestGalleryItem(
    playlistUrl: String,
    epg: Playlist,
    associated: Boolean,
    onUpdateEpgPlaylist: (PlaylistRepository.UpdateEpgPlaylistUseCase) -> Unit,
    modifier: Modifier = Modifier
) {
    val spacing = LocalSpacing.current
    ListItem(
        headlineContent = {
            Text(
                text = epg.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = {
            Text(
                text = epg.url
            )
        },
        trailingContent = {
            Switch(
                checked = associated,
                onCheckedChange = null
            )
        },
        colors = ListItemDefaults.colors(
            supportingColor = MaterialTheme
                .colorScheme
                .onSurfaceVariant.copy(0.38f)
        ),
        modifier = Modifier
            .border(
                1.dp,
                LocalContentColor.current.copy(0.38f),
                SelectionsDefaults.Shape
            )
            .clip(AbsoluteSmoothCornerShape(spacing.medium, 65))
            .clickable {
                onUpdateEpgPlaylist(
                    PlaylistRepository.UpdateEpgPlaylistUseCase(
                        playlistUrl = playlistUrl,
                        epgUrl = epg.url,
                        action = !associated
                    )
                )
            }
            .then(modifier)
    )
}