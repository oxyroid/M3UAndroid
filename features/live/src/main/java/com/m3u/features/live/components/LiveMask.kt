package com.m3u.features.live.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.m3u.material.components.Mask
import com.m3u.material.components.MaskPanel
import com.m3u.material.components.MaskState
import com.m3u.material.model.LocalSpacing

@Composable
internal fun LiveMask(
    state: MaskState,
    header: @Composable RowScope.() -> Unit,
    body: @Composable RowScope.() -> Unit,
    footer: @Composable RowScope.() -> Unit,
    modifier: Modifier = Modifier,
) {
    CompositionLocalProvider(
        LocalContentColor provides Color.White
    ) {
        MaskPanel(
            state = state,
            modifier = modifier
        )
        Mask(
            state = state,
            backgroundColor = Color.Black.copy(alpha = 0.54f),
            modifier = modifier
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter),
                horizontalArrangement = Arrangement.End,
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
                    horizontalArrangement = Arrangement.spacedBy(LocalSpacing.current.medium),
                    content = footer
                )
            }
        }
    }
}
