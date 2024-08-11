package com.m3u.feature.foryou.components.recommend

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.m3u.core.architecture.preferences.hiltPreferences
import com.m3u.core.util.basic.title
import com.m3u.i18n.R.string
import com.m3u.material.brush.RecommendCardContainerBrush
import com.m3u.material.ktx.tv
import com.m3u.material.model.LocalSpacing
import com.m3u.material.shape.AbsoluteSmoothCornerShape
import com.m3u.ui.FontFamilies
import com.m3u.ui.createPremiumBrush
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.time.Duration.Companion.days
import androidx.tv.material3.Card as TvCard
import androidx.tv.material3.CardDefaults as TvCardDefaults
import androidx.tv.material3.CardScale as TvCardScale
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
            is Recommend.NewRelease -> NewReleaseContent(spec)
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
    val spacing = LocalSpacing.current
    val tv = tv()
    if (!tv) {
        Card(
            shape = AbsoluteSmoothCornerShape(spacing.medium, 65),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurfaceVariant),
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
                }
                .fillMaxHeight()
                .then(modifier),
            content = { content() }
        )
    } else {
        TvCard(
            scale = TvCardScale.None,
            shape = TvCardDefaults.shape(AbsoluteSmoothCornerShape(spacing.medium, 65)),
            onClick = onClick,
            modifier = modifier,
            content = { content() }
        )
    }

}

@Composable
fun UnseenContent(spec: Recommend.UnseenSpec) {
    val context = LocalContext.current
    val spacing = LocalSpacing.current
    val preferences = hiltPreferences()

    val stream = spec.channel
    val duration = remember(stream.seen) {
        Clock.System.now() - Instant.fromEpochMilliseconds(stream.seen)
    }
    val noPictureMode = preferences.noPictureMode

    Box(Modifier.fillMaxSize()) {
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

@Composable
private fun NewReleaseContent(spec: Recommend.NewRelease) {
    val spacing = LocalSpacing.current
    Row(
        Modifier
            .fillMaxSize()
            .background(
                Brush.createPremiumBrush(
                    MaterialTheme.colorScheme.primary,
                    MaterialTheme.colorScheme.tertiary
                )
            )
            .padding(spacing.medium)
    ) {
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onPrimary) {
            Column(
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(string.feat_foryou_new_release).title(),
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1
                    )
                    Icon(imageVector = Icons.Rounded.NewReleases, contentDescription = null)
                }
                Text(
                    text = spec.name,
                    style = MaterialTheme.typography.labelLarge,
                    color = LocalContentColor.current.copy(0.56f),
                )
                Text(
                    text = spec.description,
                    maxLines = 5,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamilies.LexendExa,
                    modifier = Modifier.weight(1f)
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = spec.size.toString(),
                        style = MaterialTheme.typography.labelMedium,
                        color = LocalContentColor.current.copy(0.56f)
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Download,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = LocalContentColor.current.copy(0.56f)
                        )
                        Text(
                            text = spec.downloadCount.toString(),
                            style = MaterialTheme.typography.labelMedium,
                            color = LocalContentColor.current.copy(0.56f)
                        )
                    }
                }
            }
        }
    }
}