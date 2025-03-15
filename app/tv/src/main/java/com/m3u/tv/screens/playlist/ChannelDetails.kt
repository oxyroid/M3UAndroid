package com.m3u.tv.screens.playlist

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.m3u.data.database.model.Channel
import com.m3u.tv.screens.dashboard.rememberChildPadding
import com.m3u.tv.theme.JetStreamButtonShape
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChannelDetail(
    channel: Channel,
    goToChannelPlayer: () -> Unit
) {
    val childPadding = rememberChildPadding()
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val coroutineScope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(432.dp)
            .bringIntoViewRequester(bringIntoViewRequester)
    ) {
        ChannelImageWithGradients(
            channel = channel,
            modifier = Modifier.fillMaxSize()
        )

        Column(modifier = Modifier.fillMaxWidth(0.55f)) {
            Spacer(modifier = Modifier.height(108.dp))
            Column(
                modifier = Modifier.padding(start = childPadding.start)
            ) {
                ChannelLargeTitle(channelTitle = channel.title)

                Column(
                    modifier = Modifier.alpha(0.75f)
                ) {
                    ChannelDescription(description = channel.category)
                    DotSeparatedRow(
                        modifier = Modifier.padding(top = 20.dp),
                        texts = emptyList()
                    )
                    DirectorScreenplayMusicRow(
                        director = channel.title,
                        screenplay = channel.title,
                        music = channel.title
                    )
                }
                WatchTrailerButton(
                    modifier = Modifier.onFocusChanged {
                        if (it.isFocused) {
                            coroutineScope.launch { bringIntoViewRequester.bringIntoView() }
                        }
                    },
                    goToChannelPlayer = goToChannelPlayer
                )
            }
        }
    }
}

@Composable
private fun WatchTrailerButton(
    modifier: Modifier = Modifier,
    goToChannelPlayer: () -> Unit
) {
    Button(
        onClick = goToChannelPlayer,
        modifier = modifier.padding(top = 24.dp),
        contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
        shape = ButtonDefaults.shape(shape = JetStreamButtonShape)
    ) {
        Icon(
            imageVector = Icons.Outlined.PlayArrow,
            contentDescription = null
        )
        Spacer(Modifier.size(8.dp))
        Text(
            text = "Play Now",
            style = MaterialTheme.typography.titleSmall
        )
    }
}

@Composable
private fun DirectorScreenplayMusicRow(
    director: String,
    screenplay: String,
    music: String
) {
    Row(modifier = Modifier.padding(top = 32.dp)) {
        TitleValueText(
            modifier = Modifier
                .padding(end = 32.dp)
                .weight(1f),
            title = "stringResource(R.string.director)",
            value = director
        )

        TitleValueText(
            modifier = Modifier
                .padding(end = 32.dp)
                .weight(1f),
            title = "stringResource(R.string.screenplay)",
            value = screenplay
        )

        TitleValueText(
            modifier = Modifier.weight(1f),
            title = "stringResource(R.string.music)",
            value = music
        )
    }
}

@Composable
private fun ChannelDescription(description: String) {
    Text(
        text = description,
        style = MaterialTheme.typography.titleSmall.copy(
            fontSize = 15.sp,
            fontWeight = FontWeight.Normal
        ),
        modifier = Modifier.padding(top = 8.dp),
        maxLines = 2
    )
}

@Composable
private fun ChannelLargeTitle(channelTitle: String) {
    Text(
        text = channelTitle,
        style = MaterialTheme.typography.displayMedium.copy(
            fontWeight = FontWeight.Bold
        ),
        maxLines = 1
    )
}

@Composable
private fun ChannelImageWithGradients(
    channel: Channel,
    modifier: Modifier = Modifier,
    gradientColor: Color = MaterialTheme.colorScheme.surface,
) {
    AsyncImage(
        model = ImageRequest.Builder(LocalContext.current)
            .data(channel.cover)
            .crossfade(true)
            .build(),
        contentDescription = channel.title,
        contentScale = ContentScale.Crop,
        modifier = modifier.drawWithContent {
            drawContent()
            drawRect(
                Brush.verticalGradient(
                    colors = listOf(Color.Transparent, gradientColor),
                    startY = 600f
                )
            )
            drawRect(
                Brush.horizontalGradient(
                    colors = listOf(gradientColor, Color.Transparent),
                    endX = 1000f,
                    startX = 300f
                )
            )
            drawRect(
                Brush.linearGradient(
                    colors = listOf(gradientColor, Color.Transparent),
                    start = Offset(x = 500f, y = 500f),
                    end = Offset(x = 1000f, y = 0f)
                )
            )
        }
    )
}
