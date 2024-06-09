package com.m3u.feature.playlist.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BrokenImage
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border as TvBorder
import androidx.tv.material3.Card as TvCard
import androidx.tv.material3.CardDefaults as TvCardDefaults
import androidx.tv.material3.Glow as TvGlow
import androidx.tv.material3.MaterialTheme as TvMaterialTheme
import androidx.tv.material3.Text as TvText
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.m3u.core.architecture.preferences.hiltPreferences
import com.m3u.data.database.model.Channel
import com.m3u.material.components.CircularProgressIndicator
import com.m3u.material.components.Icon
import com.m3u.material.ktx.thenIf
import com.m3u.material.model.LocalSpacing
import coil.size.Size as CoilSize

@Composable
internal fun TvChannelItem(
    channel: Channel,
    isVodOrSeriesPlaylist: Boolean,
    isGridLayout: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val preferences = hiltPreferences()
    val spacing = LocalSpacing.current

    val noPictureMode = preferences.noPictureMode
    val isCoverExisted = !channel.cover.isNullOrEmpty()

    TvCard(
        onClick = onClick,
        onLongClick = onLongClick,
        glow = TvCardDefaults.glow(
            TvGlow(
                elevationColor = Color.Transparent,
                elevation = spacing.small
            )
        ),
        scale = TvCardDefaults.scale(
            scale = 0.95f,
            focusedScale = 1.1f
        ),
        border = TvCardDefaults.border(
            if (channel.favourite) TvBorder(
                BorderStroke(3.dp, TvMaterialTheme.colorScheme.border),
            )
            else TvBorder.None
        ),
        modifier = Modifier
            .thenIf(!noPictureMode) {
                if (isGridLayout) Modifier.width(128.dp)
                else Modifier.height(128.dp)
            }
            .then(modifier)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            if (!isCoverExisted || noPictureMode) {
                TvText(
                    text = channel.title,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .widthIn(86.dp)
                        .padding(spacing.medium),
                    maxLines = 1
                )
            } else {
                SubcomposeAsyncImage(
                    model = remember(channel.cover) {
                        ImageRequest.Builder(context)
                            .data(channel.cover)
                            .size(CoilSize.ORIGINAL)
                            .build()
                    },
                    contentScale = if (isGridLayout) ContentScale.FillWidth
                    else ContentScale.FillHeight,
                    contentDescription = channel.title,
                    loading = {
                        Column(
                            verticalArrangement = Arrangement.SpaceAround,
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(spacing.medium)
                        ) {
                            TvText(
                                text = channel.title,
                                maxLines = 1
                            )
                            CircularProgressIndicator()
                        }
                    },
                    error = {
                        Column(
                            verticalArrangement = Arrangement.SpaceAround,
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(spacing.medium)
                        ) {
                            TvText(
                                text = channel.title,
                                maxLines = 1
                            )
                            Icon(
                                imageVector = Icons.Rounded.BrokenImage,
                                contentDescription = null
                            )
                        }
                    },
                    modifier = Modifier.then(
                        if (isGridLayout) Modifier.fillMaxWidth()
                        else Modifier.fillMaxHeight()
                    )
                )
            }
        }
    }
}
