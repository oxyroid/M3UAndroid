package com.m3u.smartphone.ui.business.foryou.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.m3u.business.foryou.HomeFeedSection
import com.m3u.business.foryou.HomePlaylistPreview
import com.m3u.data.database.model.Channel
import com.m3u.data.database.model.DataSource
import com.m3u.data.database.model.MediaKinds
import com.m3u.data.database.model.Playlist
import com.m3u.data.database.model.epgUrlsOrXtreamXmlUrl
import com.m3u.data.database.model.refreshable
import com.m3u.data.database.model.type
import com.m3u.i18n.R.string
import com.m3u.smartphone.ui.common.helper.LocalHelper
import com.m3u.smartphone.ui.common.helper.Metadata
import com.m3u.smartphone.ui.common.helper.useRailNav
import com.m3u.smartphone.ui.material.components.PageStateContent
import com.m3u.smartphone.ui.material.ktx.plus
import com.m3u.smartphone.ui.material.model.LocalHazeState
import com.m3u.smartphone.ui.material.model.LocalSpacing
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlin.math.absoluteValue

@Composable
internal fun PlaylistGallery(
    rowCount: Int,
    previews: List<HomePlaylistPreview>,
    feedSections: List<HomeFeedSection>,
    subscribingPlaylistUrls: List<String>,
    refreshingEpgUrls: List<String>,
    onClick: (Playlist) -> Unit,
    onLongClick: (Playlist) -> Unit,
    onPlayChannel: (Channel) -> Unit,
    onAddPlaylist: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    header: (@Composable () -> Unit)? = null,
) {
    val spacing = LocalSpacing.current
    val windowInfo = LocalWindowInfo.current
    val helper = LocalHelper.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val headlineAspectRatio = Metadata.headlineAspectRatio(helper.useRailNav)
    val state = rememberLazyListState()
    val viewportStartOffset by remember {
        derivedStateOf {
            if (state.firstVisibleItemIndex == 0) {
                state.firstVisibleItemScrollOffset
            } else {
                -Int.MAX_VALUE
            }
        }
    }
    LaunchedEffect(windowInfo.containerSize.width) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            snapshotFlow { viewportStartOffset }
                .onEach {
                    Metadata.headlineFraction = it.absoluteValue
                        .times(headlineAspectRatio)
                        .div(windowInfo.containerSize.width)
                        .coerceIn(0f, 1f)
                }
                .onCompletion { Metadata.headlineFraction = 1f }
                .launchIn(this)
        }
    }

    LazyColumn(
        state = state,
        contentPadding = PaddingValues(vertical = spacing.medium) + contentPadding,
        verticalArrangement = Arrangement.spacedBy(32.dp),
        modifier = modifier.hazeSource(LocalHazeState.current),
    ) {
        if (header != null) {
            item(key = "home-up-next") {
                header()
            }
        }

        if (previews.isEmpty()) {
            item(key = "empty-library") {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(horizontal = spacing.medium),
                ) {
                    HomeSectionTitle(
                        title = stringResource(string.feat_foryou_library_title),
                    )
                    PageStateContent(
                        icon = Icons.Rounded.VideoLibrary,
                        title = stringResource(string.feat_foryou_empty_title),
                        description = stringResource(string.feat_foryou_empty_description),
                        actionLabel = stringResource(
                            string.feat_foryou_add_playlist_action
                        ),
                        actionIcon = Icons.Rounded.Add,
                        onAction = onAddPlaylist,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 280.dp),
                    )
                }
            }
        } else {
            items(
                items = feedSections,
                key = HomeFeedSection::id,
            ) { section ->
                HomeChannelShelfSection(
                    section = section,
                    onOpenPlaylist = { onClick(section.playlist) },
                    onPlayChannel = onPlayChannel,
                )
            }

            item(key = "home-library") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    HomeSectionTitle(
                        title = stringResource(string.feat_foryou_library_title),
                        modifier = Modifier.padding(horizontal = spacing.medium),
                    )
                    LazyRow(
                        contentPadding = PaddingValues(
                            start = spacing.medium,
                            end = spacing.medium * 2,
                        ),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(
                            items = previews,
                            key = { preview -> preview.playlist.url },
                        ) { preview ->
                            val playlist = preview.playlist
                            val subscribing =
                                playlist.url in subscribingPlaylistUrls
                            val refreshing = playlist
                                .epgUrlsOrXtreamXmlUrl()
                                .any { it in refreshingEpgUrls }
                            PlaylistItem(
                                label = PlaylistGalleryDefaults.calculateUiTitle(
                                    title = playlist.title,
                                    refreshable = playlist.refreshable,
                                ),
                                type = with(playlist) {
                                    when (source) {
                                        DataSource.M3U -> "$source"
                                        DataSource.Xtream -> "$source $type"
                                        else -> null
                                    }
                                },
                                count = preview.channelCount,
                                compact = rowCount > 1,
                                subscribingOrRefreshing =
                                    subscribing || refreshing,
                                onClick = { onClick(playlist) },
                                onLongClick = { onLongClick(playlist) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeChannelShelfSection(
    section: HomeFeedSection,
    onOpenPlaylist: () -> Unit,
    onPlayChannel: (Channel) -> Unit,
) {
    val spacing = LocalSpacing.current
    val posterLayout = section.channels.any { channel ->
        channel.mediaKind == MediaKinds.MOVIE ||
            channel.mediaKind == MediaKinds.SERIES
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        HomeSectionTitle(
            title = section.title,
            supporting = section.supporting,
            onClick = onOpenPlaylist,
            modifier = Modifier.padding(horizontal = spacing.medium),
        )
        LazyRow(
            contentPadding = PaddingValues(
                start = spacing.medium,
                end = spacing.medium * 2,
            ),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(
                items = section.channels,
                key = { channel -> channel.id },
            ) { channel ->
                HomeChannelCard(
                    channel = channel,
                    posterLayout = posterLayout,
                    onClick = { onPlayChannel(channel) },
                )
            }
        }
    }
}

@Composable
private fun HomeSectionTitle(
    title: String,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    onClick: (() -> Unit)? = null,
) {
    val content = @Composable {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp),
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.semantics { heading() },
                )
                supporting?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (onClick != null) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    if (onClick == null) {
        Column(modifier = modifier) {
            content()
        }
    } else {
        Column(
            modifier = modifier.clickable(
                role = Role.Button,
                onClick = onClick,
            ),
        ) {
            content()
        }
    }
}

private object PlaylistGalleryDefaults {
    @Composable
    fun calculateUiTitle(title: String, refreshable: Boolean): String {
        return title.ifEmpty {
            if (!refreshable) {
                stringResource(string.feat_foryou_imported_playlist_title)
            } else {
                ""
            }
        }
    }
}
