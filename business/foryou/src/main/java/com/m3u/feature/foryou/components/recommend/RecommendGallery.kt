package com.m3u.business.foryou.components.recommend

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import com.m3u.core.wrapper.eventOf
import com.m3u.data.database.model.Channel
import com.m3u.data.database.model.Playlist
import com.m3u.material.components.HorizontalPagerIndicator
import com.m3u.material.ktx.pageOffset
import com.m3u.material.model.LocalSpacing
import com.m3u.ui.Events

@Composable
internal fun RecommendGallery(
    specs: List<Recommend.Spec>,
    onPlayChannel: (Channel) -> Unit,
    navigateToPlaylist: (Playlist) -> Unit,
    onSpecChanged: (Recommend.Spec?) -> Unit,
    modifier: Modifier = Modifier
) {
    val spacing = LocalSpacing.current
    val uriHandler = LocalUriHandler.current

    val onClick = { spec: Recommend.Spec ->
        when (spec) {
            is Recommend.UnseenSpec -> {
                onPlayChannel(spec.channel)
            }

            is Recommend.DiscoverSpec -> {
                Events.discoverCategory = eventOf(spec.category)
                navigateToPlaylist(spec.playlist)
            }

            is Recommend.NewRelease -> {
                uriHandler.openUri(spec.url)
            }
        }
    }

    val state = rememberPagerState { specs.size }
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(spacing.medium)
    ) {
        DisposableEffect(state.currentPage) {
            onSpecChanged(specs[state.currentPage])
            onDispose {
                onSpecChanged(null)
            }
        }
        HorizontalPager(
            state = state,
            contentPadding = PaddingValues(horizontal = spacing.medium),
            modifier = Modifier.height(128.dp)
        ) { page ->
            val spec = specs[page]
            val pageOffset = state.pageOffset(page)
            RecommendItem(
                spec = spec,
                pageOffset = pageOffset,
                onClick = { onClick(spec) }
            )
        }
        HorizontalPagerIndicator(
            pagerState = state,
            modifier = Modifier
                .align(Alignment.End)
                .padding(horizontal = spacing.medium),
        )
    }
}
