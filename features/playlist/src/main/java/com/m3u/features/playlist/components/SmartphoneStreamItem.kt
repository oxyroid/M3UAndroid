package com.m3u.features.playlist.components

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ListItem
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import coil.size.Size
import com.m3u.core.architecture.preferences.LocalPreferences
import com.m3u.data.database.model.Stream
import com.m3u.i18n.R.string
import com.m3u.material.components.CircularProgressIndicator
import com.m3u.material.components.Icon
import com.m3u.material.model.LocalSpacing
import com.m3u.material.shape.AbsoluteSmoothCornerShape
import com.m3u.ui.Image
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@Composable
internal fun SmartphoneStreamItem(
    stream: Stream,
    recently: Boolean,
    zapping: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    isVodOrSeriesPlaylist: Boolean = true
) {
    val context = LocalContext.current
    val spacing = LocalSpacing.current
    val preferences = LocalPreferences.current

    val favourite = stream.favourite

    val recentlyString = stringResource(string.ui_sort_recently)
    val neverPlayedString = stringResource(string.ui_sort_never_played)

    val noPictureMode = preferences.noPictureMode

    val onlyPictureMode = remember(stream.cover, isVodOrSeriesPlaylist, noPictureMode) {
        when {
            noPictureMode -> false
            else -> isVodOrSeriesPlaylist
        }
    }

    val star = remember(favourite) {
        movableContentOf {
            Crossfade(
                targetState = favourite,
                label = "stream-item-favourite"
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
        shape = AbsoluteSmoothCornerShape(spacing.medium, 65),
    ) {
        if (!onlyPictureMode) {
            ListItem(
                headlineContent = {
                    Text(
                        text = stream.title.trim(),
                        style = MaterialTheme.typography.titleSmall,
                        overflow = TextOverflow.Ellipsis,
                        maxLines = 1,
                        fontWeight = FontWeight.Bold,
                    )
                },
                leadingContent = if (!noPictureMode) {
                    {
                        Image(
                            model = stream.cover,
                            transparentPlaceholder = true,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .height(56.dp)
                                .aspectRatio(
                                    if (!isVodOrSeriesPlaylist) 1f else 2 / 3f
                                )
                        )
                    }
                } else null,
                supportingContent = {
                    if (recently) {
                        Text(
                            text = remember(stream.seen) {
                                val now = Clock.System.now()
                                val instant = Instant.fromEpochMilliseconds(stream.seen)
                                val duration = now - instant
                                duration.toComponents { days, hours, minutes, seconds, _ ->
                                    when {
                                        stream.seen == 0L -> neverPlayedString
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
                },
                trailingContent = {
                    star()
                },
                modifier = Modifier
                    .combinedClickable(
                        onClick = onClick,
                        onLongClick = onLongClick
                    )
                    .then(modifier)
            )
        } else {
            Box(
                modifier = Modifier
                    .combinedClickable(
                        onClick = onClick,
                        onLongClick = onLongClick
                    )
                    .then(modifier)
            ) {
                SubcomposeAsyncImage(
                    model = remember(stream.cover) {
                        ImageRequest.Builder(context)
                            .data(stream.cover)
                            .size(Size.ORIGINAL)
                            .build()
                    },
                    contentDescription = stream.title,
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
                    modifier = Modifier
                        .fillMaxWidth()
//                        .aspectRatio(
//                            if (!isVodOrSeriesPlaylist) 4 / 3f else 2 / 3f
//                        )
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
    }
}
