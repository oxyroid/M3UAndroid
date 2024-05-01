package com.m3u.features.playlist.configuration

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.m3u.core.util.basic.title
import com.m3u.data.database.model.DataSource
import com.m3u.data.database.model.Playlist
import com.m3u.data.repository.playlist.PlaylistRepository
import com.m3u.i18n.R.string
import com.m3u.material.components.Background
import com.m3u.material.components.Icon
import com.m3u.material.components.PlaceholderField
import com.m3u.material.components.SelectionsDefaults
import com.m3u.material.ktx.split
import com.m3u.material.model.LocalHazeState
import com.m3u.material.model.LocalSpacing
import com.m3u.material.shape.AbsoluteSmoothCornerShape
import com.m3u.ui.helper.LocalHelper
import dev.chrisbanes.haze.HazeDefaults
import dev.chrisbanes.haze.haze

@Composable
internal fun PlaylistConfigurationRoute(
    modifier: Modifier = Modifier,
    viewModel: PlaylistConfigurationViewModel = hiltViewModel(),
    contentPadding: PaddingValues = PaddingValues()
) {
    val helper = LocalHelper.current
    val playlist by viewModel.playlist.collectAsStateWithLifecycle()
    val manifest by viewModel.manifest.collectAsStateWithLifecycle()

    LifecycleResumeEffect(playlist?.title) {
        helper.title = playlist?.title?.title().orEmpty()
        onPauseOrDispose {
        }
    }

    playlist?.let {
        PlaylistConfigurationScreen(
            playlist = it,
            manifest = manifest,
            onUpdatePlaylistTitle = viewModel::onUpdatePlaylistTitle,
            onUpdatePlaylistUserAgent = viewModel::onUpdatePlaylistUserAgent,
            onUpdateEpgPlaylist = viewModel::onUpdateEpgPlaylist,
            modifier = modifier,
            contentPadding = contentPadding
        )
    }
}

@Composable
private fun PlaylistConfigurationScreen(
    playlist: Playlist,
    manifest: EpgManifest,
    onUpdatePlaylistTitle: (String) -> Unit,
    onUpdatePlaylistUserAgent: (String?) -> Unit,
    onUpdateEpgPlaylist: (PlaylistRepository.UpdateEpgPlaylistUseCase) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues()
) {
    val spacing = LocalSpacing.current

    var title: String by remember(playlist.title) { mutableStateOf(playlist.title) }
    var userAgent: String by remember(playlist.userAgent) { mutableStateOf(playlist.userAgent.orEmpty()) }

    val hasChanged by remember(playlist.title, playlist.userAgent) {
        derivedStateOf { title != playlist.title || userAgent != playlist.userAgent.orEmpty() }
    }
    Background(modifier) {
        Box {
            val (outer, inner) = contentPadding split WindowInsetsSides.Top
            Column(
                verticalArrangement = Arrangement.spacedBy(spacing.small),
                modifier = Modifier
                    .padding(outer)
                    .padding(spacing.medium)
            ) {
                PlaceholderField(
                    text = title,
                    placeholder = stringResource(string.feat_playlist_configuration_title).title(),
                    onValueChange = { title = it },
                )
                PlaceholderField(
                    text = userAgent,
                    placeholder = stringResource(string.feat_playlist_configuration_user_agent).title(),
                    onValueChange = { userAgent = it }
                )
                if (playlist.source == DataSource.M3U) {
                    LazyColumn(
                        contentPadding = inner,
                        verticalArrangement = Arrangement.spacedBy(spacing.medium),
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .haze(
                                LocalHazeState.current,
                                HazeDefaults.style(MaterialTheme.colorScheme.surface)
                            )
                    ) {
                        stickyHeader {
                            Text(
                                text = stringResource(string.feat_playlist_configuration_enabled_epgs).title(),
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(
                                    horizontal = spacing.medium,
                                    vertical = spacing.small
                                )
                            )
                        }
                        items(manifest.entries.toList()) { (epg, associated) ->
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
                                                playlistUrl = playlist.url,
                                                epgUrl = epg.url,
                                                action = !associated
                                            )
                                        )
                                    }
                            )
                        }
                    }
                }
            }

            val fabBottomPadding by animateDpAsState(
                maxOf(
                    WindowInsets.ime.asPaddingValues().calculateBottomPadding(),
                    inner.calculateBottomPadding()
                )
            )
            AnimatedVisibility(
                visible = hasChanged,
                enter = fadeIn() + slideInVertically { it },
                exit = fadeOut() + slideOutVertically { it },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = fabBottomPadding)
            ) {
                FloatingActionButton(
                    onClick = {
                        if (title != playlist.title) onUpdatePlaylistTitle(title)
                        if (userAgent != playlist.userAgent) onUpdatePlaylistUserAgent(userAgent)
                    },
                    modifier = Modifier.padding(spacing.medium)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Save,
                        contentDescription = "apply changes"
                    )
                }
            }
        }
    }
}
