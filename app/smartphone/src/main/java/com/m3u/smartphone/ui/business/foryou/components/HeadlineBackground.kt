package com.m3u.smartphone.ui.business.foryou.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.m3u.core.architecture.preferences.PreferencesKeys
import com.m3u.core.architecture.preferences.preferenceOf
import com.m3u.smartphone.ui.material.transformation.BlurTransformation
import com.m3u.smartphone.ui.common.helper.LocalHelper
import com.m3u.smartphone.ui.common.helper.Metadata
import com.m3u.smartphone.ui.common.helper.useRailNav
import kotlin.math.roundToInt

@Composable
internal fun HeadlineBackground(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val helper = LocalHelper.current
    val colorScheme = MaterialTheme.colorScheme

    val darkMode by preferenceOf(PreferencesKeys.DARK_MODE)
    val followSystemTheme by preferenceOf(PreferencesKeys.FOLLOW_SYSTEM_THEME)
    val noPictureMode by preferenceOf(PreferencesKeys.NO_PICTURE_MODE)

    val isSystemInDarkTheme = isSystemInDarkTheme()

    val useDarkTheme by remember {
        derivedStateOf {
            darkMode || (followSystemTheme && isSystemInDarkTheme)
        }
    }

    val url = Metadata.headlineUrl
    val fraction = Metadata.headlineFraction

    val headlineAspectRatio = Metadata.headlineAspectRatio(helper.useRailNav)

    val currentMaskColor by animateColorAsState(
        targetValue = lerp(
            start = if (useDarkTheme) Color.Black.copy(0.56f)
            else Color.White.copy(0.56f),
            stop = colorScheme.surface,
            fraction = fraction
        ),
        label = "headline-background-mask-color",
        animationSpec = tween(800)
    )

    if (!noPictureMode) {
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
                .background(MaterialTheme.colorScheme.background)
                .offset {
                    IntOffset(
                        x = 0,
                        y = ((configuration.screenWidthDp * headlineAspectRatio) * -fraction).roundToInt()
                    )
                }
                .aspectRatio(headlineAspectRatio)
                .graphicsLayer { alpha = 0.99f }
                .drawWithContent {
                    drawContent()
                    if (url.isNotEmpty()) {
                        drawRect(
                            color = currentMaskColor
                        )
                        drawRect(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    Color.Black,
                                    Color.Transparent
                                )
                            ),
                            blendMode = BlendMode.DstIn
                        )
                    }
                }
        )
    }
}