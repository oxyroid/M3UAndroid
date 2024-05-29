package com.m3u.features.foryou.components.recommend

import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.tv.material3.Carousel as TvCarousel
import com.m3u.core.wrapper.eventOf
import com.m3u.data.database.model.Playlist
import com.m3u.data.database.model.Stream
import com.m3u.material.components.HorizontalPagerIndicator
import com.m3u.material.ktx.isTelevision
import com.m3u.material.model.LocalSpacing
import com.m3u.ui.Events
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

@OptIn(ExperimentalAnimationApi::class)
@Composable
internal fun RecommendGallery(
    recommend: Recommend,
    onClickStream: (Stream) -> Unit,
    navigateToPlaylist: (Playlist) -> Unit,
    modifier: Modifier = Modifier
) {
    val spacing = LocalSpacing.current
    val coroutineScope = rememberCoroutineScope()

    val tv = isTelevision()

    if (!tv) {
        val state = rememberPagerState { recommend.size }
        Column(
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(spacing.medium)
        ) {
            HorizontalPager(
                state = state,
                contentPadding = PaddingValues(horizontal = spacing.medium),
                modifier = Modifier.animateContentSize()
            ) { page ->
                val spec = recommend[page]
                val pageOffset =
                    ((state.currentPage - page) + state.currentPageOffsetFraction).absoluteValue
                RecommendItem(
                    spec = spec,
                    pageOffset = pageOffset,
                    onClick = {
                        when (spec) {
                            is Recommend.UnseenSpec -> onClickStream(spec.stream)
                            is Recommend.DiscoverSpec -> {
                                Events.discoverCategory = eventOf(spec.category)
                                navigateToPlaylist(spec.playlist)
                            }
                        }
                    }
                )
            }
            HorizontalPagerIndicator(
                pagerState = state,
                modifier = Modifier
                    .align(Alignment.End)
                    .padding(horizontal = spacing.medium),
            )
        }
    } else {
        TvCarousel(
            itemCount = recommend.size,
            contentTransformEndToStart =
            fadeIn(tween(1000)) togetherWith fadeOut(tween(1000)),
            contentTransformStartToEnd =
            fadeIn(tween(1000)) togetherWith fadeOut(tween(1000)),
            modifier = Modifier
                .padding(spacing.medium)
                .then(modifier)
        ) { index ->
            val spec = recommend[index]
            RecommendItem(
                spec = spec,
                pageOffset = 0f,
                onClick = {
                    when (spec) {
                        is Recommend.UnseenSpec -> {
                            coroutineScope.launch {
                                // TODO
//                                helper.play(spec.stream.id)
//                                navigateToStream()
                            }
                        }

                        is Recommend.DiscoverSpec -> {

                        }
                    }
                },
                modifier = Modifier.animateEnterExit(
                    enter = slideInHorizontally(animationSpec = tween(1000)) { it / 2 },
                    exit = slideOutHorizontally(animationSpec = tween(1000))
                )
            )
        }
    }
}
