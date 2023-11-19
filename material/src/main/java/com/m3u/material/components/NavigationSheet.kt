package com.m3u.material.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemBarsIgnoringVisibility
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

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
        tonalElevation = elevation,
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
    val containerColor: Color @Composable get() = MaterialTheme.colorScheme.surface
    val contentColor: Color @Composable get() = MaterialTheme.colorScheme.onSurface

    val windowInsets: WindowInsets
        @Composable
        get() = WindowInsets.systemBarsIgnoringVisibility
            .only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)
}
