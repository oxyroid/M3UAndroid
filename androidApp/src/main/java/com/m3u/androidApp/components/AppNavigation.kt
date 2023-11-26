package com.m3u.androidApp.components

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.m3u.core.util.basic.title
import com.m3u.material.ktx.animateColor
import com.m3u.material.ktx.animated
import com.m3u.ui.Destination
import com.m3u.ui.Fob
import com.m3u.ui.Navigate

@Composable
fun AppNavigation(
    navigate: Navigate,
    rootDestination: Destination.Root?,
    fob: Fob?,
    useNavRail: Boolean,
    modifier: Modifier = Modifier,
    backgroundColor: Color = AppNavigationDefaults.backgroundColor(),
    selectedColor: Color = AppNavigationDefaults.selectedColor(),
    unselectedColor: Color = AppNavigationDefaults.unselectedColor(),
    fobbedColor: Color = AppNavigationDefaults.fobbedColor(),
) {
    val destinations = Destination.Root.entries
    val actualBackgroundColor by backgroundColor.animated("BottomNavigationSheetBackground")
    val actualContentColor by unselectedColor.animated("BottomNavigationSheetContent")

    when {
        useNavRail -> {
            NavigationRail(
                modifier = modifier,
                containerColor = actualBackgroundColor,
                contentColor = actualContentColor
            ) {
                RailContent(
                    navigate = navigate,
                    destinationsFactory = { destinations },
                    rootDestination = rootDestination,
                    fob = fob,
                    selectedColor = selectedColor,
                    fobbedColor = fobbedColor
                )
            }
        }

        else -> {
            BottomAppBar(
                modifier = modifier,
                containerColor = actualBackgroundColor,
                contentColor = actualContentColor
            ) {
                Content(
                    navigate = navigate,
                    destinationsFactory = { destinations },
                    rootDestination = rootDestination,
                    fob = fob,
                    selectedColor = selectedColor,
                    fobbedColor = fobbedColor
                )
            }
        }
    }
}

@Composable
private fun ColumnScope.RailContent(
    navigate: Navigate,
    destinationsFactory: () -> List<Destination.Root>,
    rootDestination: Destination.Root?,
    fob: Fob?,
    selectedColor: Color,
    fobbedColor: Color,
    modifier: Modifier = Modifier
) {
    val relation = fob?.rootDestination
    val actualActiveDestination = rootDestination ?: relation
    val destinations = destinationsFactory()
    destinations.forEach { default ->
        val fobbed = default == relation
        val selected = default == actualActiveDestination
        val iconTextId = default.iconTextId
        val selectedIcon = fob?.icon.takeIf { fobbed } ?: default.selectedIcon
        val unselectedIcon = fob?.icon.takeIf { fobbed } ?: default.unselectedIcon
        val actualSelectedColor by animateColor("BottomNavigationSheetSelected") {
            if (fobbed) fobbedColor else selectedColor
        }

        RailItem(
            selected = selected,
            onClick = {
                if (fobbed && fob != null) {
                    fob.onClick()
                } else {
                    navigate(default)
                }
            },
            selectedColor = actualSelectedColor,
            contentDestination = stringResource(iconTextId),
            icon = {
                val icon = if (selected) selectedIcon
                else unselectedIcon
                Crossfade(
                    targetState = icon,
                    label = "BottomNavigationSheetIcon"
                ) { actualIcon ->
                    Icon(
                        imageVector = actualIcon,
                        contentDescription = stringResource(iconTextId)
                    )
                }
            },
            label = {
                Text(
                    text = stringResource(iconTextId).title(),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
            }
        )
    }
}

@Composable
private fun RowScope.Content(
    navigate: Navigate,
    destinationsFactory: () -> List<Destination.Root>,
    rootDestination: Destination.Root?,
    fob: Fob?,
    selectedColor: Color,
    fobbedColor: Color,
    modifier: Modifier = Modifier
) {
    val relation = fob?.rootDestination
    val actualActiveDestination = rootDestination ?: relation
    val destinations = destinationsFactory()
    destinations.forEach { default ->
        val fobbed = default == relation
        val selected = default == actualActiveDestination
        val iconTextId = default.iconTextId
        val selectedIcon = fob?.icon.takeIf { fobbed } ?: default.selectedIcon
        val unselectedIcon = fob?.icon.takeIf { fobbed } ?: default.unselectedIcon
        val actualSelectedColor by animateColor("BottomNavigationSheetSelected") {
            if (fobbed) fobbedColor else selectedColor
        }

        NavigationBarItem(
            selected = selected,
            onClick = {
                if (fobbed && fob != null) {
                    fob.onClick()
                } else {
                    navigate(default)
                }
            },
            colors = NavigationBarItemDefaults.colors(
                selectedTextColor = actualSelectedColor,
                selectedIconColor = actualSelectedColor
            ),
            icon = {
                val icon = if (selected) selectedIcon
                else unselectedIcon
                Crossfade(
                    targetState = icon,
                    label = "BottomNavigationSheetIcon"
                ) { actualIcon ->
                    Icon(
                        imageVector = actualIcon,
                        contentDescription = stringResource(iconTextId)
                    )
                }
            },
            label = {
                Text(
                    text = stringResource(iconTextId).title(),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
            }
        )
    }
}

@Composable
private fun RailItem(
    selected: Boolean,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    label: @Composable (() -> Unit)? = null,
    contentDestination: String? = null,
    selectedColor: Color
) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        state = rememberTooltipState(),
        tooltip = {
            PlainTooltip {
                Text(contentDestination.orEmpty())
            }
        }
    ) {
        NavigationRailItem(
            selected = selected,
            onClick = onClick,
            icon = icon,
            modifier = modifier,
            enabled = enabled,
            label = label,
            alwaysShowLabel = false,
            colors = NavigationRailItemDefaults.colors(
                selectedIconColor = selectedColor,
                selectedTextColor = selectedColor
            ),
            interactionSource = remember { MutableInteractionSource() },
        )
    }
}

object AppNavigationDefaults {
    @Composable
    fun backgroundColor() = MaterialTheme.colorScheme.surface

    @Composable
    fun selectedColor() = MaterialTheme.colorScheme.primary

    @Composable
    fun unselectedColor() = MaterialTheme.colorScheme.onSurface

    @Composable
    fun fobbedColor() = MaterialTheme.colorScheme.primary
}