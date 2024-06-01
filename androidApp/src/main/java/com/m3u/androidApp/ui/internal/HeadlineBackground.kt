package com.m3u.androidApp.ui.internal

import android.util.Log
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.m3u.core.architecture.preferences.hiltPreferences
import com.m3u.material.ktx.BlurTransformation
import com.m3u.material.ktx.Edge
import com.m3u.material.ktx.blurEdge
import com.m3u.ui.helper.Metadata
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlin.math.roundToInt

@Composable
fun HeadlineBackground(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current

    val preferences = hiltPreferences()

    val useDarkTheme =
        preferences.darkMode || (preferences.followSystemTheme && isSystemInDarkTheme())

    val colorScheme = MaterialTheme.colorScheme
    val url = Metadata.headlineUrl
    val fraction = Metadata.headlineFraction

    val currentMaskColor by animateColorAsState(
        targetValue = lerp(
            start = if (useDarkTheme) Color.Black.copy(0.56f)
            else Color.White.copy(0.56f),
            stop = Color.Transparent,
            fraction = fraction
        ),
        label = "scaffold-main-content-mask-color"
    )

    if (!preferences.noPictureMode) {
        LaunchedEffect(Unit) {
            snapshotFlow { Metadata.headlineFraction }
                .onEach { Log.e("TAG", "$it") }
                .launchIn(this)
        }
        AsyncImage(
            model = remember(url) {
                ImageRequest.Builder(context)
                    .data(url)
                    .crossfade(800)
                    .memoryCachePolicy(CachePolicy.DISABLED)
                    .transformations(
                        BlurTransformation(context)
                    )
                    .build()
            },
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier
                .fillMaxWidth()
                .offset {
                    IntOffset(
                        x = 0,
                        y = ((configuration.screenWidthDp * Metadata.HEADLINE_ASPECT_RATIO) * -fraction).roundToInt()
                    )
                }
                .aspectRatio(Metadata.HEADLINE_ASPECT_RATIO)
                .drawWithContent {
                    drawContent()
                    if (url.isNotEmpty()) {
                        drawRect(color = currentMaskColor, size = size)
                    }
                }
                .blurEdge(
                    color = colorScheme.background,
                    edge = Edge.Bottom,
                    dimen = 256f
                )
        )
    }
}