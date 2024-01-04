package com.m3u.features.foryou.components.recommend

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.util.lerp
import com.m3u.i18n.R.string
import com.m3u.material.model.LocalSpacing
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.time.Duration.Companion.days

@Composable
internal fun RecommendItem(
    spec: Recommend.Spec,
    pageOffset: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val spacing = LocalSpacing.current
    RecommendItemLayout(pageOffset, modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(spacing.medium)
        ) {
            when (spec) {
                is Recommend.UnseenSpec -> UnseenContent(spec)
                is Recommend.DiscoverSpec -> DiscoverContent(spec)
            }
        }
    }
}

@Composable
private fun RecommendItemLayout(
    pageOffset: Float,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Card(
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
}

@Composable
private fun UnseenContent(spec: Recommend.UnseenSpec) {
    val spacing = LocalSpacing.current
    val stream = spec.stream
    Text(
        text = stringResource(string.feat_foryou_recommend_unseen_label).uppercase(),
        style = MaterialTheme.typography.labelLarge,
        maxLines = 1
    )
    val duration = remember(stream.seen) {
        Clock.System.now() - Instant.fromEpochMilliseconds(stream.seen)
    }
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

            else -> stringResource(string.feat_foryou_recommend_unseen_hours, duration.inWholeHours)
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