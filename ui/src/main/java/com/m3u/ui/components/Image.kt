package com.m3u.ui.components

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import coil.compose.SubcomposeAsyncImage
import com.m3u.ui.R
import com.m3u.ui.model.LocalTheme

@Composable
fun Image(
    model: Any?,
    modifier: Modifier = Modifier,
    shape: Shape = RectangleShape,
    contentDescription: String? = null,
    contentScale: ContentScale = ContentScale.Fit
) {
    SubcomposeAsyncImage(
        model = model,
        contentDescription = contentDescription,
        modifier = modifier.clip(shape),
        contentScale = contentScale,
        loading = {
            Surface(
                modifier = Modifier.fillMaxSize(),
                shape = shape,
                color = LocalTheme.current.surface
            ) {

            }
        },
        error = {
            Surface(
                shape = shape,
                color = LocalTheme.current.error,
            ) {
                OuterBox(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    val message =
                        it.result.throwable.message ?: stringResource(R.string.error_unknown)
                    Text(
                        text = message,
                        style = MaterialTheme.typography.h6,
                        color = LocalTheme.current.onError
                    )
                }
            }
        }
    )

}