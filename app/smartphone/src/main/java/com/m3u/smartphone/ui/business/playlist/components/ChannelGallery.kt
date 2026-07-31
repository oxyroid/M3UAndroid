package com.m3u.smartphone.ui.business.playlist.components

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.TvOff
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.m3u.business.playlist.ChannelWithProgramme
import com.m3u.data.database.model.Channel
import com.m3u.data.database.model.MediaKinds
import com.m3u.core.foundation.architecture.preferences.PreferencesKeys
import com.m3u.core.foundation.architecture.preferences.preferenceOf
import com.m3u.core.foundation.components.CircularProgressIndicator
import com.m3u.i18n.R.string
import com.m3u.smartphone.ui.material.components.PageStateContent
import com.m3u.smartphone.ui.material.ktx.plus
import com.m3u.smartphone.ui.material.model.LocalSpacing
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlin.time.Duration.Companion.milliseconds

@Composable
internal fun ChannelGallery(
    state: LazyStaggeredGridState,
    rowCount: Int,
    channels: Flow<PagingData<ChannelWithProgramme>>,
    zapping: Channel?,
    recently: Boolean,
    isVodOrSeriesPlaylist: Boolean,
    onClick: (Channel) -> Unit,
    onLongClick: (Channel) -> Unit,
    reloadThumbnail: suspend (channelUrl: String) -> Uri?,
    syncThumbnail: suspend (channelUrl: String) -> Uri?,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp)
) {
    val spacing = LocalSpacing.current

    val noPictureMode by preferenceOf(PreferencesKeys.NO_PICTURE_MODE)
    val channels = channels.collectAsLazyPagingItems()
    val refreshState = channels.loadState.refresh

    val actualRowCount by remember(isVodOrSeriesPlaylist, rowCount, channels.itemSnapshotList) {
        derivedStateOf {
            val loadedChannels = channels.itemSnapshotList.items
                .map(ChannelWithProgramme::channel)
            val containsPoster = loadedChannels.any { channel ->
                channel.mediaKind == MediaKinds.MOVIE ||
                    channel.mediaKind == MediaKinds.SERIES
            }
            val containsNonPoster = loadedChannels.any { channel ->
                channel.mediaKind != MediaKinds.MOVIE &&
                    channel.mediaKind != MediaKinds.SERIES &&
                    channel.mediaKind != MediaKinds.UNKNOWN
            }
            val posterCategory = containsPoster && !containsNonPoster ||
                loadedChannels.isEmpty() && isVodOrSeriesPlaylist
            when {
                noPictureMode -> rowCount
                posterCategory -> rowCount + 2
                else -> rowCount
            }
        }
    }

    val currentReloadThumbnail by rememberUpdatedState(reloadThumbnail)
    val currentSyncThumbnail by rememberUpdatedState(syncThumbnail)

    Box(modifier = modifier.fillMaxSize()) {
        when {
            refreshState is LoadState.Loading && channels.itemCount == 0 -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(contentPadding),
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(32.dp))
                    Text(
                        text = stringResource(string.ui_state_loading),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = spacing.medium),
                    )
                }
            }

            refreshState is LoadState.Error && channels.itemCount == 0 -> {
                PageStateContent(
                    icon = Icons.Rounded.CloudOff,
                    title = stringResource(string.feat_playlist_load_error_title),
                    description = stringResource(string.feat_playlist_load_error_description),
                    actionLabel = stringResource(string.ui_action_retry),
                    onAction = channels::retry,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(contentPadding),
                )
            }

            refreshState is LoadState.NotLoading && channels.itemCount == 0 -> {
                PageStateContent(
                    icon = Icons.Rounded.TvOff,
                    title = stringResource(string.feat_playlist_empty_category_title),
                    description = stringResource(
                        string.feat_playlist_empty_category_description
                    ),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(contentPadding),
                )
            }

            else -> {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(
                            start = spacing.medium,
                            end = spacing.medium
                        ),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    LazyVerticalStaggeredGrid(
                        state = state,
                        columns = StaggeredGridCells.Fixed(actualRowCount),
                        verticalItemSpacing = spacing.medium,
                        horizontalArrangement = Arrangement.spacedBy(spacing.medium),
                        contentPadding =
                            PaddingValues(vertical = spacing.medium) + contentPadding,
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f)
                    ) {
                        items(
                            channels.itemCount,
                            key = channels.itemKey { it.channel.id }
                        ) { index ->
                            val channelWithProgramme = channels[index]
                            if (channelWithProgramme != null) {
                                val channel = channelWithProgramme.channel
                                val loadedUrl: Any? by produceState<Any?>(
                                    initialValue = channel.cover,
                                    key1 = channel,
                                    key2 = noPictureMode,
                                ) {
                                    if (
                                        noPictureMode ||
                                        channel.url == Channel.URL_DYNAMIC
                                    ) {
                                        return@produceState
                                    }
                                    val default = channel.cover
                                    delay(1200.milliseconds)
                                    val channelUrl = channel.url
                                    val reloaded = currentReloadThumbnail(channelUrl)
                                    if (reloaded == null) {
                                        value = currentSyncThumbnail(channelUrl) ?: default
                                    } else {
                                        value = reloaded
                                        delay(2400.milliseconds)
                                        value = currentSyncThumbnail(channelUrl) ?: default
                                    }
                                }
                                ChannelItem(
                                    channel = channel,
                                    programme = channelWithProgramme.programme,
                                    cover = loadedUrl,
                                    recently = recently,
                                    zapping = zapping == channel,
                                    isVodOrSeriesPlaylist = isVodOrSeriesPlaylist,
                                    onClick = { onClick(channel) },
                                    onLongClick = { onLongClick(channel) },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            } else {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(4 / 3f)
                                ) {
                                    CircularProgressIndicator()
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
