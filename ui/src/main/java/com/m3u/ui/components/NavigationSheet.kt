package com.m3u.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.m3u.ui.model.LocalTheme

@Composable
fun NavigationSheet(
    modifier: Modifier = Modifier,
    containerColor: Color = NavigationSheetDefaults.containerColor,
    contentColor: Color = NavigationSheetDefaults.contentColor,
    elevation: Dp = NavigationSheetDefaults.Elevation,
    windowInsets: WindowInsets = NavigationSheetDefaults.windowInsets,
    content: @Composable RowScope.() -> Unit
) {
    Surface(
        color = containerColor,
        contentColor = contentColor,
        elevation = elevation,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(windowInsets)
                .height(80.dp)
                .selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            content = content
        )
    }
}

object NavigationSheetDefaults {
    val Elevation: Dp = 3.dp
    val containerColor: Color @Composable get() = LocalTheme.current.onTopBar
    val contentColor: Color @Composable get() = LocalTheme.current.topBar

    @OptIn(ExperimentalLayoutApi::class)
    val windowInsets: WindowInsets
        @Composable
        get() = WindowInsets.systemBarsIgnoringVisibility
            .only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)
}
