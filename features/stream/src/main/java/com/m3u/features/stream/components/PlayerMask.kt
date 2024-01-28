package com.m3u.features.stream.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.m3u.material.components.mask.Mask
import com.m3u.material.components.mask.MaskPanel
import com.m3u.material.components.mask.MaskState
import com.m3u.material.ktx.isTelevision
import com.m3u.material.model.LocalSpacing

@Composable
internal fun PlayerMask(
    state: MaskState,
    header: @Composable RowScope.() -> Unit,
    body: @Composable RowScope.() -> Unit,
    footer: @Composable RowScope.() -> Unit,
    modifier: Modifier = Modifier,
    slider: (@Composable () -> Unit)? = null
) {
    val spacing = LocalSpacing.current
    val tv = isTelevision()
    CompositionLocalProvider(
        LocalContentColor provides Color.White,
        androidx.tv.material3.LocalContentColor provides Color.White
    ) {
        Box(modifier) {
            MaskPanel(
                state = state
            )
            Mask(
                state = state,
                color = Color.Black.copy(alpha = 0.54f),
                modifier = Modifier
                    .padding(WindowInsets.systemBars.asPaddingValues())
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter),
                    horizontalArrangement = Arrangement.spacedBy(
                        if (!tv) spacing.none else spacing.medium,
                        Alignment.End
                    ),
                    verticalAlignment = Alignment.Top,
                    content = header
                )
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                    content = body
                )
                Column(
                    modifier = Modifier.align(Alignment.BottomCenter)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(spacing.medium),
                        verticalAlignment = Alignment.Bottom,
                        content = footer
                    )
                    slider?.invoke()
                }
            }
        }
    }
}
