package com.m3u.smartphone.ui.business.playlist.components

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.unit.dp
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.m3u.business.playlist.ChannelWithProgramme
import com.m3u.data.database.model.Channel
import com.m3u.core.foundation.architecture.preferences.PreferencesKeys
import com.m3u.core.foundation.architecture.preferences.preferenceOf
import com.m3u.core.foundation.components.CircularProgressIndicator
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

    val actualRowCount by remember(isVodOrSeriesPlaylist, rowCount) {
        derivedStateOf {
            when {
                noPictureMode -> rowCount
                isVodOrSeriesPlaylist -> rowCount + 2
                else -> rowCount
            }
        }
    }

    val channels = channels.collectAsLazyPagingItems()

    val currentReloadThumbnail by rememberUpdatedState(reloadThumbnail)
    val currentSyncThumbnail by rememberUpdatedState(syncThumbnail)

    Row(
        modifier = modifier
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
            contentPadding = PaddingValues(vertical = spacing.medium) + contentPadding,
            modifier = Modifier
                .fillMaxSize()
                .weight(1f)
        ) {
            items(channels.itemCount, key = channels.itemKey { it.channel.id }) { index ->
                val channelWithProgramme = channels[index]
                if (channelWithProgramme != null) {
                    val channel = channelWithProgramme.channel
                    val loadedUrl: Any? by produceState<Any?>(
                        initialValue = channel.cover,
                        key1 = channel,
                    ) {
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
