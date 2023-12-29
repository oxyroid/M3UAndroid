package com.m3u.features.foryou.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.util.lerp
import com.m3u.data.database.entity.Stream
import com.m3u.features.foryou.model.Unseens
import com.m3u.material.components.HorizontalPagerIndicator
import com.m3u.material.model.LocalSpacing
import com.m3u.ui.LocalHelper
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.math.absoluteValue
import kotlin.time.Duration.Companion.days

@Composable
internal fun UnseensGallery(
    unseens: Unseens,
    navigateToStream: () -> Unit,
    modifier: Modifier = Modifier
) {
    val spacing = LocalSpacing.current
    val helper = LocalHelper.current

    val streams = unseens.streams
    val state = rememberPagerState { streams.size }
    Column(modifier) {
        HorizontalPager(
            state = state,
            contentPadding = PaddingValues(spacing.medium),
            modifier = Modifier.animateContentSize()
        ) { page ->
            val stream = streams[page]
            val pageOffset =
                ((state.currentPage - page) + state.currentPageOffsetFraction).absoluteValue
            UnseensGalleryItem(
                stream = stream,
                pageOffset = pageOffset,
                onClick = {
                    helper.play(stream.url)
                    navigateToStream()
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

@Composable
private fun UnseensGalleryItem(
    stream: Stream,
    pageOffset: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val spacing = LocalSpacing.current
    Card(
        Modifier
            .graphicsLayer {
                lerp(
                    start = 0.65f,
                    stop = 1f,
                    fraction = 1f - pageOffset.coerceIn(0f, 1f)
                ).also { scale ->
                    scaleX = scale
                    scaleY = scale
                }
                alpha = lerp(
                    start = 0.5f,
                    stop = 1f,
                    fraction = 1f - pageOffset.coerceIn(0f, 1f)
                )
            }
            .then(modifier)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(spacing.medium)
        ) {
            Text(
                text = "favourite that you would see again".uppercase(),
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1
            )
            Text(
                text = remember(stream.seen) {
                    val now = Clock.System.now()
                    val duration = now - Instant.fromEpochMilliseconds(stream.seen)
                    when {
                        duration > 30.days -> "More than 30 days"
                        duration > 1.days -> "${duration.inWholeDays} days"
                        else -> "${duration.inWholeHours} hours"
                    }
                },
                style = MaterialTheme.typography.labelMedium,
            )
            Spacer(modifier = Modifier.height(spacing.extraSmall))
            Text(
                text = stream.title,
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Black,
                maxLines = 1
            )
        }
    }
}