package com.m3u.features.foryou.components.recommend

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.m3u.material.components.HorizontalPagerIndicator
import com.m3u.material.model.LocalSpacing
import com.m3u.ui.LocalHelper
import kotlin.math.absoluteValue

@Composable
internal fun RecommendGallery(
    recommend: Recommend,
    navigateToStream: () -> Unit,
    modifier: Modifier = Modifier
) {
    val spacing = LocalSpacing.current
    val helper = LocalHelper.current

    val state = rememberPagerState { recommend.size }

    Column(modifier) {
        HorizontalPager(
            state = state,
            contentPadding = PaddingValues(spacing.medium),
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
                        is Recommend.UnseenSpec -> {
                            helper.play(spec.stream.url)
                            navigateToStream()
                        }

                        is Recommend.DiscoverSpec -> {

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
}
