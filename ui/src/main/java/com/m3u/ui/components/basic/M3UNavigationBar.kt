package com.m3u.ui.components.basic

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.MaterialTheme
import androidx.compose.material.NavigationRailItem
import androidx.compose.material.Surface
import androidx.compose.material.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.m3u.ui.local.LocalTheme

@Composable
fun M3UNavigationBar(
    modifier: Modifier = Modifier,
    containerColor: Color = NavigationBarDefaults.containerColor,
    contentColor: Color = NavigationBarDefaults.contentColor,
    elevation: Dp = NavigationBarDefaults.Elevation,
    windowInsets: WindowInsets = NavigationBarDefaults.windowInsets,
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

object NavigationBarDefaults {
    val Elevation: Dp = 3.dp
    val containerColor: Color @Composable get() = LocalTheme.current.onTopBar
    val contentColor: Color @Composable get() = LocalTheme.current.topBar

    @OptIn(ExperimentalLayoutApi::class)
    val windowInsets: WindowInsets
        @Composable
        get() = WindowInsets.systemBarsIgnoringVisibility
            .only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)
}

@Composable
fun RowScope.M3UNavigationBarItem(
    selected: Boolean,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    label: @Composable (() -> Unit)? = null,
    alwaysShowLabel: Boolean = true,
    tint: Color = LocalTheme.current.primary
) {
    NavigationRailItem(
        selected = selected,
        onClick = onClick,
        icon = icon,
        modifier = modifier,
        enabled = enabled,
        label = label,
        alwaysShowLabel = alwaysShowLabel,
        selectedContentColor = tint,
        interactionSource = remember { MutableInteractionSource() },
    )
}
