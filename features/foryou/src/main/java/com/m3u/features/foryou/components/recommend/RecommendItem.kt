package com.m3u.features.foryou.components.recommend

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.util.lerp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.m3u.core.architecture.preferences.hiltPreferences
import com.m3u.core.util.basic.title
import com.m3u.i18n.R.string
import com.m3u.material.brush.RecommendCardContainerBrush
import com.m3u.material.ktx.isTelevision
import com.m3u.material.model.LocalSpacing
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.time.Duration.Companion.days
import androidx.tv.material3.Card as TvCard
import androidx.tv.material3.CardDefaults as TvCardDefaults
import androidx.tv.material3.CardScale as TvCardScale
import androidx.tv.material3.Glow as TvGlow
import androidx.tv.material3.LocalContentColor as TvLocalContentColor

@Composable
internal fun RecommendItem(
    spec: Recommend.Spec,
    pageOffset: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    RecommendItemLayout(pageOffset, onClick, modifier) {
        when (spec) {
            is Recommend.UnseenSpec -> UnseenContent(spec)
            is Recommend.DiscoverSpec -> DiscoverContent(spec)
        }
    }
}

@Composable
private fun RecommendItemLayout(
    pageOffset: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val tv = isTelevision()
    if (!tv) {
        Card(
            onClick = onClick,
            modifier = Modifier
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
                .then(modifier),
            content = { content() }
        )
    } else {
        TvCard(
            scale = TvCardScale.None,
            glow = TvCardDefaults.glow(TvGlow.None, TvGlow.None, TvGlow.None),
            onClick = onClick,
            modifier = modifier,
            content = { content() }
        )
    }

}

@Composable
private fun UnseenContent(spec: Recommend.UnseenSpec) {
    val context = LocalContext.current
    val spacing = LocalSpacing.current
    val preferences = hiltPreferences()
    val colorScheme = MaterialTheme.colorScheme

    val stream = spec.stream
    val duration = remember(stream.seen) {
        Clock.System.now() - Instant.fromEpochMilliseconds(stream.seen)
    }
    val noPictureMode = preferences.noPictureMode

    Box(Modifier.fillMaxWidth()) {
        val info = @Composable {
            Column(Modifier.padding(spacing.medium)) {
                Text(
                    text = stringResource(string.feat_foryou_recommend_unseen_label).uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1
                )
                Text(
                    text = when {
                        duration > 30.days -> stringResource(
                            string.feat_foryou_recommend_unseen_more_than_days,
                            30
                        )

                        duration > 1.days -> stringResource(
                            string.feat_foryou_recommend_unseen_days,
                            duration.inWholeDays
                        )

                        else -> stringResource(
                            string.feat_foryou_recommend_unseen_hours,
                            duration.inWholeHours
                        )
                    }.title(),
                    style = MaterialTheme.typography.labelMedium,
                    color = LocalContentColor.current.copy(0.56f)
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
        if (!noPictureMode) {
            val request = remember(stream.cover) {
                ImageRequest.Builder(context)
                    .data(stream.cover.orEmpty())
                    .crossfade(1600)
                    .build()
            }
            AsyncImage(
                model = request,
                contentScale = ContentScale.Crop,
                contentDescription = stream.title,
                modifier = Modifier
                    .fillMaxWidth(0.78f)
                    .matchParentSize()
                    .align(Alignment.TopEnd)
                    .drawWithCache {
                        onDrawWithContent {
                            drawContent()
                            drawRect(brush = RecommendCardContainerBrush(size))
                        }
                    }
            )
            CompositionLocalProvider(
                LocalContentColor provides Color.White,
                TvLocalContentColor provides Color.White,
            ) {
                info()
            }
        } else {
            info()
        }
    }
}

@Composable
private fun DiscoverContent(spec: Recommend.DiscoverSpec) {
    val spacing = LocalSpacing.current
    val playlist = spec.playlist
    val category = spec.category
    Text(
        text = stringResource(string.feat_foryou_recommend_unseen_label).uppercase(),
        style = MaterialTheme.typography.labelLarge,
        maxLines = 1
    )
    Text(
        text = "",
        style = MaterialTheme.typography.labelMedium,
    )
    Spacer(modifier = Modifier.height(spacing.extraSmall))
    Text(
        text = "",
        style = MaterialTheme.typography.displaySmall,
        fontWeight = FontWeight.Black,
        maxLines = 1
    )
}