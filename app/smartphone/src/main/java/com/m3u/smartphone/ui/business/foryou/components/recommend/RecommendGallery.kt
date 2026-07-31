package com.m3u.smartphone.ui.business.foryou.components.recommend

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.m3u.business.foryou.Recommend
import com.m3u.core.foundation.wrapper.eventOf
import com.m3u.data.database.model.Channel
import com.m3u.data.database.model.Playlist
import com.m3u.i18n.R.string
import com.m3u.smartphone.ui.common.internal.Events
import com.m3u.smartphone.ui.material.components.HorizontalPagerIndicator
import com.m3u.smartphone.ui.material.ktx.pageOffset
import com.m3u.smartphone.ui.material.model.LocalSpacing
import kotlin.math.absoluteValue

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

            is Recommend.CwSpec -> {
                onPlayChannel(spec.channel)
            }

            is Recommend.NewRelease -> {
                uriHandler.openUri(spec.url)
            }
        }
    }

    val state = rememberPagerState { specs.size }
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        DisposableEffect(state.currentPage) {
            onSpecChanged(specs[state.currentPage])
            onDispose {
                onSpecChanged(null)
            }
        }
        Text(
            text = stringResource(string.feat_foryou_up_next_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .padding(horizontal = spacing.medium)
                .semantics { heading() },
        )
        HorizontalPager(
            state = state,
            contentPadding = PaddingValues(horizontal = spacing.medium),
            pageSpacing = 12.dp,
            modifier = Modifier.height(208.dp),
        ) { page ->
            val spec = specs[page]
            val pageOffset = state.pageOffset(page).absoluteValue
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
