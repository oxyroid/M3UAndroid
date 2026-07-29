package com.m3u.smartphone.ui.business.configuration.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.m3u.business.playlist.configuration.EpgManifest
import com.m3u.data.database.model.Playlist
import com.m3u.data.repository.playlist.PlaylistRepository
import com.m3u.data.worker.playlistWorkTag
import com.m3u.i18n.R.string
import com.m3u.smartphone.ui.material.ktx.rememberUiBidiFormatter
import com.m3u.smartphone.ui.material.ktx.safeSourceReference
import com.m3u.smartphone.ui.material.ktx.safeDisplayText

@Composable
internal fun EpgManifestGallery(
    playlistUrl: String,
    manifest: EpgManifest,
    onUpdateEpgPlaylist: (PlaylistRepository.EpgPlaylistUseCase) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = stringResource(
                string.feat_playlist_configuration_enabled_epgs
            ),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = 16.dp,
                    end = 16.dp,
                    top = 12.dp,
                    bottom = 8.dp,
                )
                .semantics { heading() },
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            Column {
                if (manifest.isEmpty()) {
                    ListItem(
                        headlineContent = {
                            Text(
                                text = stringResource(
                                    string.feat_setting_playlist_epg_sources_empty
                                ),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        colors = ListItemDefaults.colors(
                            containerColor = Color.Transparent,
                        ),
                    )
                } else {
                    manifest.entries.forEachIndexed { index, (epg, isChecked) ->
                        EpgManifestGalleryItem(
                            playlistUrl = playlistUrl,
                            epg = epg,
                            isChecked = isChecked,
                            onUpdateEpgPlaylist = onUpdateEpgPlaylist,
                        )
                        if (index != manifest.size - 1) {
                            HorizontalDivider(
                                modifier = Modifier.padding(start = 16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EpgManifestGalleryItem(
    playlistUrl: String,
    epg: Playlist,
    isChecked: Boolean,
    onUpdateEpgPlaylist: (PlaylistRepository.EpgPlaylistUseCase) -> Unit,
    modifier: Modifier = Modifier,
) {
    val bidiFormatter = rememberUiBidiFormatter()
    val safeTitle = epg.title.safeDisplayText()
    val safeReference = epg.url.safeSourceReference()
        ?.let(bidiFormatter::ltr)

    ListItem(
        headlineContent = {
            Text(
                text = safeTitle,
                style = MaterialTheme.typography.titleMedium.copy(
                    textDirection = TextDirection.ContentOrLtr,
                ),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = safeReference?.let { reference ->
            {
                Text(
                    text = reference,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        textDirection = TextDirection.Ltr,
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        trailingContent = {
            Switch(
                checked = isChecked,
                onCheckedChange = null,
            )
        },
        colors = ListItemDefaults.colors(
            containerColor = Color.Transparent,
            supportingColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .toggleable(
                value = isChecked,
                role = Role.Switch,
                onValueChange = { checked ->
                    onUpdateEpgPlaylist(
                        PlaylistRepository.EpgPlaylistUseCase.Check(
                            playlistUrl = playlistUrl,
                            epgUrl = epg.url,
                            action = checked,
                        )
                    )
                },
            )
            .testTag("playlist-configuration-epg:${playlistWorkTag(epg.url)}"),
    )
}
