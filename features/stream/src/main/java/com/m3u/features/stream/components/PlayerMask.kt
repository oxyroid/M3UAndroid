package com.m3u.features.stream.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
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
internal fun PlayerMask(
    state: MaskState,
    header: @Composable RowScope.() -> Unit,
    body: @Composable RowScope.() -> Unit,
    footer: @Composable RowScope.() -> Unit,
    modifier: Modifier = Modifier,
) {
    CompositionLocalProvider(
        LocalContentColor provides Color.White
    ) {
        Box(modifier) {
            MaskPanel(state)
            Mask(
                state = state,
                color = Color.Black.copy(alpha = 0.54f),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(WindowInsets.navigationBars.asPaddingValues())
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
                    modifier = Modifier
                        .padding(
                            WindowInsets.navigationBars
                                .only(WindowInsetsSides.Horizontal)
                                .asPaddingValues()
                        )
                        .align(Alignment.BottomCenter)
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
}
