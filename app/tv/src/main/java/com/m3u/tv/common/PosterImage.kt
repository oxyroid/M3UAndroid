package com.m3u.tv.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.m3u.data.database.model.Channel

@Composable
fun PosterImage(
    channel: Channel,
    modifier: Modifier = Modifier,
) {
    AsyncImage(
        modifier = modifier,
        model = ImageRequest.Builder(LocalContext.current)
            .crossfade(true)
            .data(channel.cover)
            .build(),
        contentDescription = channel.title,
        contentScale = ContentScale.Crop
    )
}
