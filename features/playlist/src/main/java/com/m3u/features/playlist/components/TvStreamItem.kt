package com.m3u.features.playlist.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BrokenImage
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.Glow
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import coil.compose.SubcomposeAsyncImage
import com.m3u.data.database.model.Stream
import com.m3u.material.model.LocalSpacing

@Composable
internal fun TvStreamItem(
    stream: Stream,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    noPictureMode: Boolean = false
) {
    val spacing = LocalSpacing.current
    Card(
        onClick = onClick,
        onLongClick = onLongClick,
        glow = CardDefaults.glow(
            Glow(
                elevationColor = Color.Transparent,
                elevation = spacing.small
            )
        ),
        scale = CardDefaults.scale(
            scale = 0.96f,
            focusedScale = 1.1f
        ),
        modifier = Modifier
            .then(
                if (noPictureMode) Modifier
                else Modifier
                    .height(128.dp)
                    .aspectRatio(4 / 3f)
            )
            .then(modifier)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            if (noPictureMode || stream.cover.isNullOrEmpty()) {
                Text(
                    text = stream.title,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .widthIn(86.dp)
                        .padding(spacing.medium),
                    maxLines = 1
                )
            } else {
                SubcomposeAsyncImage(
                    model = stream.cover,
                    contentScale = ContentScale.Crop,
                    contentDescription = stream.title,
                    error = {
                        Column(
                            verticalArrangement = Arrangement.SpaceAround,
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(spacing.medium)
                        ) {
                            Text(
                                text = stream.title,
                                maxLines = 1
                            )
                            Icon(
                                imageVector = Icons.Rounded.BrokenImage,
                                contentDescription = null
                            )
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

