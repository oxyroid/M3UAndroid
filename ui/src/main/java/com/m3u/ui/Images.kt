package com.m3u.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import coil.compose.SubcomposeAsyncImage
import com.m3u.material.model.LocalSpacing

@Composable
fun Image(
    model: Any?,
    modifier: Modifier = Modifier,
    errorPlaceholder: String? = null,
    shape: Shape = RectangleShape,
    contentDescription: String? = null,
    contentScale: ContentScale = ContentScale.Fit,
    transparentPlaceholder: Boolean = false
) {
    val spacing = LocalSpacing.current
    SubcomposeAsyncImage(
        model = model,
        contentDescription = contentDescription,
        modifier = modifier.clip(shape),
        contentScale = contentScale,
        loading = {
            if (!transparentPlaceholder) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    shape = shape,
                    color = MaterialTheme.colorScheme.surfaceContainerLow
                ) {

                }
            }
        },
        error = {
            if (!transparentPlaceholder) {
                Box(
                    modifier = Modifier
                        .clip(shape)
                        .background(MaterialTheme.colorScheme.surfaceContainerLow)
                        .padding(spacing.small),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = errorPlaceholder.orEmpty(),
                        style = MaterialTheme.typography.headlineSmall,
                        fontFamily = FontFamilies.LexendExa,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    )
}
