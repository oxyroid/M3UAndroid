package com.m3u.smartphone.ui.business.foryou.components.recommend

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
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
import com.m3u.business.foryou.Recommend
import com.m3u.core.architecture.preferences.PreferencesKeys
import com.m3u.core.architecture.preferences.preferenceOf
import com.m3u.core.foundation.components.AbsoluteSmoothCornerShape
import com.m3u.core.foundation.ui.composableOf
import com.m3u.core.util.basic.title
import com.m3u.i18n.R.string
import com.m3u.smartphone.ui.material.brush.RecommendCardContainerBrush
import com.m3u.smartphone.ui.material.components.FontFamilies
import com.m3u.smartphone.ui.material.components.createPremiumBrush
import com.m3u.smartphone.ui.material.model.LocalSpacing
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.time.Duration.Companion.days
import kotlin.time.DurationUnit
import kotlin.time.toDuration

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
            is Recommend.CwSpec -> CwContent(spec)
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
}

@Composable
private fun RecommendItemContent(
    cover: String,
    primaryContent: @Composable ColumnScope.() -> Unit,
    secondaryContent: (@Composable ColumnScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val context = LocalContext.current
    val spacing = LocalSpacing.current

    val noPictureMode by preferenceOf(PreferencesKeys.NO_PICTURE_MODE)

    Box(Modifier.fillMaxSize()) {
        val info = @Composable {
            Column(Modifier.padding(spacing.medium)) {
                CompositionLocalProvider(
                    LocalTextStyle provides MaterialTheme.typography.titleMedium
                ) {
                    primaryContent()
                }
                CompositionLocalProvider(
                    LocalTextStyle provides MaterialTheme.typography.labelMedium.copy(
                        color = LocalContentColor.current.copy(0.56f)
                    )
                ) {
                    secondaryContent?.invoke(this)
                }
                Spacer(modifier = Modifier.weight(1f))

                CompositionLocalProvider(
                    LocalTextStyle provides MaterialTheme.typography.displaySmall.copy(
                        fontWeight = FontWeight.Black
                    )
                ) {
                    content()
                }
            }
        }
        if (!noPictureMode) {
            val request = remember(cover) {
                ImageRequest.Builder(context)
                    .data(cover)
                    .crossfade(1600)
                    .build()
            }
            AsyncImage(
                model = request,
                contentScale = ContentScale.Crop,
                contentDescription = null,
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
            ) {
                info()
            }
        } else {
            info()
        }
    }
}

@Composable
private fun UnseenContent(spec: Recommend.UnseenSpec) {
    val channel = spec.channel
    val duration = remember(channel.seen) {
        Clock.System.now() - Instant.fromEpochMilliseconds(channel.seen)
    }
    RecommendItemContent(
        cover = spec.channel.cover.orEmpty(),
        primaryContent = {
            Text(
                text = stringResource(string.feat_foryou_recommend_unseen_label).uppercase(),
                maxLines = 1
            )
        },
        secondaryContent = {
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
                }.title()
            )
        },
        content = {
            Text(
                text = channel.title,
                maxLines = 1
            )
        }
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

@Composable
private fun CwContent(spec: Recommend.CwSpec) {
    val channel = spec.channel
    val timezone = remember(spec.position) {
        spec.position.toDuration(DurationUnit.MILLISECONDS).toComponents { h, m, s, _ ->
            listOf(h, m, s)
                .dropWhile { it.toInt() == 0 }
                .joinToString(":") {
                    if (it.toInt() >= 10) it.toString()
                    else "0$it"
                }
        }
    }
    RecommendItemContent(
        cover = channel.cover.orEmpty(),
        primaryContent = {
            Text(
                text = stringResource(string.feat_foryou_recommend_cw_label).uppercase(),
                maxLines = 1
            )
        },
        secondaryContent = composableOf<ColumnScope>(spec.position >= 0L) {
            Text(
                text = timezone,
                maxLines = 1
            )
        },
        content = {
            Text(
                text = channel.title,
                maxLines = 1
            )
        }
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