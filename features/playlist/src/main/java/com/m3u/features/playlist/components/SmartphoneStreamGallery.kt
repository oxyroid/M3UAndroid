package com.m3u.features.playlist.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.AbsoluteRoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.capitalize
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.paging.compose.LazyPagingItems
import androidx.tv.material3.Text
import com.m3u.core.architecture.preferences.hiltPreferences
import com.m3u.core.util.basic.title
import com.m3u.data.database.model.Programme
import com.m3u.data.database.model.Stream
import com.m3u.i18n.R.string
import com.m3u.material.components.CircularProgressIndicator
import com.m3u.material.components.Icon
import com.m3u.material.ktx.plus
import com.m3u.material.model.LocalSpacing
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.seconds

@Composable
internal fun SmartphoneStreamGallery(
    state: LazyStaggeredGridState,
    rowCount: Int,
    streams: List<Stream>,
    streamPaged: LazyPagingItems<Stream>,
    zapping: Stream?,
    recently: Boolean,
    isVodOrSeriesPlaylist: Boolean,
    onClick: (Stream) -> Unit,
    onLongClick: (Stream) -> Unit,
    getProgrammeCurrently: suspend (channelId: String) -> Programme?,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val spacing = LocalSpacing.current
    val preferences = hiltPreferences()

    val actualRowCount = when {
        preferences.noPictureMode -> rowCount
        isVodOrSeriesPlaylist -> rowCount + 2
        else -> rowCount
    }

    val currentWithProgrammes by rememberUpdatedState(streams)
    val isPagingTipShowing by produceState(false) {
        value = if (!preferences.paging) {
            delay(4.seconds)
            currentWithProgrammes.isEmpty()
        } else false
    }

    LazyVerticalStaggeredGrid(
        state = state,
        columns = StaggeredGridCells.Fixed(actualRowCount),
        verticalItemSpacing = spacing.medium,
        horizontalArrangement = Arrangement.spacedBy(spacing.medium),
        contentPadding = PaddingValues(spacing.medium) + contentPadding,
        modifier = modifier.fillMaxSize()
    ) {
        item(span = StaggeredGridItemSpan.FullLine) {
            PagingTips(isPagingTipShowing)
        }
        if (!preferences.paging) {
            items(
                items = currentWithProgrammes,
                key = { stream -> stream.id }
            ) { stream ->
                SmartphoneStreamItem(
                    stream = stream,
                    getProgrammeCurrently = { getProgrammeCurrently(stream.channelId.orEmpty()) },
                    recently = recently,
                    zapping = zapping?.id == stream.id,
                    isVodOrSeriesPlaylist = isVodOrSeriesPlaylist,
                    onClick = { onClick(stream) },
                    onLongClick = { onLongClick(stream) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        } else {
            items(streamPaged.itemCount) {
                val stream = streamPaged[it]
                if (stream != null) {
                    SmartphoneStreamItem(
                        stream = stream,
                        getProgrammeCurrently = { getProgrammeCurrently(stream.channelId.orEmpty()) },
                        recently = recently,
                        zapping = zapping == stream,
                        isVodOrSeriesPlaylist = isVodOrSeriesPlaylist,
                        onClick = { onClick(stream) },
                        onLongClick = { onLongClick(stream) },
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                    ) {
                        CircularProgressIndicator()
                    }
                }
            }
        }
    }
}

@Composable
private fun PagingTips(
    isPagingTipShowing: Boolean,
    modifier: Modifier = Modifier
) {
    val preferences = hiltPreferences()
    val spacing = LocalSpacing.current
    AnimatedVisibility(
        visible = isPagingTipShowing,
        modifier = modifier
    ) {
        ElevatedCard(
            shape = AbsoluteRoundedCornerShape(spacing.medium)
        ) {
            ListItem(
                headlineContent = {
                    Text(
                        text = stringResource(string.feat_setting_paging).title(),
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                supportingContent = {
                    Text(
                        text = stringResource(string.feat_setting_paging_description)
                            .capitalize(Locale.current),
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                leadingContent = {
                    Icon(
                        imageVector = Icons.Rounded.FlashOn,
                        contentDescription = "tips"
                    )
                },
                trailingContent = {
                    Switch(
                        checked = preferences.paging,
                        onCheckedChange = null
                    )
                },
                colors = ListItemDefaults.colors(MaterialTheme.colorScheme.primaryContainer),
                modifier = Modifier.clickable { preferences.paging = !preferences.paging }
            )
        }
    }
}