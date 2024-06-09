package com.m3u.feature.playlist.components

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BrokenImage
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import coil.size.Size
import com.m3u.core.architecture.preferences.hiltPreferences
import com.m3u.data.database.model.Programme
import com.m3u.data.database.model.Channel
import com.m3u.i18n.R.string
import com.m3u.material.components.CircularProgressIndicator
import com.m3u.material.components.Icon
import com.m3u.material.model.LocalSpacing
import com.m3u.material.shape.AbsoluteSmoothCornerShape
import com.m3u.ui.util.TimeUtils.formatEOrSh
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@Composable
internal fun SmartphoneChannelItem(
    channel: Channel,
    recently: Boolean,
    zapping: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    programme: Programme?,
    modifier: Modifier = Modifier,
    isVodOrSeriesPlaylist: Boolean = true
) {
    val context = LocalContext.current
    val spacing = LocalSpacing.current
    val preferences = hiltPreferences()

    val favourite = channel.favourite

    val recentlyString = stringResource(string.ui_sort_recently)
    val neverPlayedString = stringResource(string.ui_sort_never_played)

    val noPictureMode = preferences.noPictureMode

    val star = remember(favourite) {
        movableContentOf {
            Crossfade(
                targetState = favourite,
                label = "channel-item-favourite"
            ) { favourite ->
                if (favourite) {
                    Icon(
                        imageVector = Icons.Rounded.Star,
                        contentDescription = null,
                        tint = Color(0xffffcd3c)
                    )
                }
            }
        }
    }

    OutlinedCard(
        modifier = Modifier.semantics(mergeDescendants = true) { },
        border = CardDefaults.outlinedCardBorder(zapping),
        colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surfaceContainer),
        shape = AbsoluteSmoothCornerShape(spacing.medium, 65)
    ) {
        when {
            !noPictureMode && isVodOrSeriesPlaylist -> {
                Box(
                    modifier = Modifier
                        .combinedClickable(
                            onClick = onClick,
                            onLongClick = onLongClick
                        )
                        .then(modifier)
                ) {
                    SubcomposeAsyncImage(
                        model = remember(channel.cover) {
                            ImageRequest.Builder(context)
                                .data(channel.cover)
                                .size(Size.ORIGINAL)
                                .build()
                        },
                        contentDescription = channel.title,
                        contentScale = ContentScale.FillWidth,
                        loading = {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(1f)
                            ) {
                                CircularProgressIndicator()
                            }
                        },
                        error = {
                            Column(
                                verticalArrangement = Arrangement.SpaceAround,
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(3 / 4f)
                                    .padding(spacing.medium)
                            ) {
                                Text(
                                    text = channel.title,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Icon(
                                    imageVector = Icons.Rounded.BrokenImage,
                                    contentDescription = null
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (favourite) {
                        Box(
                            modifier = Modifier
                                .padding(spacing.small)
                                .align(Alignment.BottomEnd)
                        ) { star() }
                    }
                }
            }

            else -> {
                ListItem(
                    headlineContent = {
                        Text(
                            text = channel.title.trim(),
                            style = MaterialTheme.typography.titleSmall,
                            overflow = TextOverflow.Ellipsis,
                            maxLines = 1,
                            fontWeight = FontWeight.Bold,
                        )
                    },
                    leadingContent = if (!noPictureMode) {
                        {
                            AsyncImage(
                                model = channel.cover,
                                contentDescription = null,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.size(56.dp)
                            )
                        }
                    } else null,
                    supportingContent = {
                        when {
                            recently -> {
                                Text(
                                    text = remember(channel.seen) {
                                        val now = Clock.System.now()
                                        val instant = Instant.fromEpochMilliseconds(channel.seen)
                                        val duration = now - instant
                                        duration.toComponents { days, hours, minutes, seconds, _ ->
                                            when {
                                                channel.seen == 0L -> neverPlayedString
                                                days > 0 -> days.days.toString()
                                                hours > 0 -> hours.hours.toString()
                                                minutes > 0 -> minutes.minutes.toString()
                                                seconds > 0 -> seconds.seconds.toString()
                                                else -> recentlyString
                                            }
                                        }
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = LocalContentColor.current.copy(0.56f)
                                )
                            }

                            programme != null -> {
                                Text(
                                    text = programme.readText(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = LocalContentColor.current.copy(0.56f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    },
                    trailingContent = {
                        star()
                    },
                    colors = ListItemDefaults.colors(Color.Transparent),
                    modifier = Modifier
                        .combinedClickable(
                            onClick = onClick,
                            onLongClick = onLongClick
                        )
                        .then(modifier)
                )
            }
        }
    }
}

@Composable
internal fun Programme.readText(
    timeColor: Color = MaterialTheme.colorScheme.secondary
): AnnotatedString = buildAnnotatedString {
    val preferences = hiltPreferences()
    val clockMode = preferences.twelveHourClock
    val start = Instant.fromEpochMilliseconds(start)
        .toLocalDateTime(TimeZone.currentSystemDefault())
        .formatEOrSh(clockMode)
    withStyle(
        SpanStyle(color = timeColor, fontWeight = FontWeight.SemiBold)
    ) {
        append("[$start] ")
    }
    append(title)
}