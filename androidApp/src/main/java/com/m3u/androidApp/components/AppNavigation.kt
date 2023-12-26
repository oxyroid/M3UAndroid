package com.m3u.androidApp.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.m3u.core.util.basic.title
import com.m3u.ui.Destination
import com.m3u.ui.Fob
import com.m3u.ui.Navigate
import com.m3u.ui.RootDestinationHolder
import com.m3u.ui.rememberRootDestinationHolder

@Composable
fun AppNavigation(
    navigate: Navigate,
    rootDestination: Destination.Root?,
    fob: Fob?,
    useNavRail: Boolean,
    modifier: Modifier = Modifier,
    color: Color = AppNavigationDefaults.color(),
    selectedColor: Color = AppNavigationDefaults.selectedColor(),
    unselectedColor: Color = AppNavigationDefaults.unselectedColor(),
    fobbedColor: Color = AppNavigationDefaults.fobbedColor(),
) {
    val destinationHolder = rememberRootDestinationHolder(Destination.Root.entries)
    val currentColor by animateColorAsState(
        targetValue = color,
        label = "navigation-color"
    )
    val currentContentColor by animateColorAsState(
        targetValue = unselectedColor,
        label = "navigation-content-color"
    )

    when {
        useNavRail -> {
            NavigationRail(
                modifier = modifier,
                containerColor = currentColor,
                contentColor = currentContentColor
            ) {
                RailContent(
                    navigate = navigate,
                    destinationHolder = destinationHolder,
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
                containerColor = currentColor,
                contentColor = currentContentColor
            ) {
                Content(
                    navigate = navigate,
                    destinationHolder = destinationHolder,
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
private fun RailContent(
    navigate: Navigate,
    destinationHolder: RootDestinationHolder,
    rootDestination: Destination.Root?,
    fob: Fob?,
    selectedColor: Color,
    fobbedColor: Color,
    modifier: Modifier = Modifier
) {
    val relation = fob?.rootDestination
    val actualActiveDestination = rootDestination ?: relation
    val roots = destinationHolder.roots
    Column(modifier) {
        roots.forEach { root ->
            val fobbed = root == relation
            val selected = root == actualActiveDestination
            val iconTextId = root.iconTextId
            val selectedIcon = fob?.icon.takeIf { fobbed } ?: root.selectedIcon
            val unselectedIcon = fob?.icon.takeIf { fobbed } ?: root.unselectedIcon

            val actualSelectedColor by animateColorAsState(
                targetValue = if (fobbed) fobbedColor else selectedColor,
                label = "navigation-rail-content-select-color"
            )

            RailItem(
                selected = selected,
                onClick = {
                    if (fobbed && fob != null) {
                        fob.onClick()
                    } else {
                        navigate(root)
                    }
                },
                selectedColor = actualSelectedColor,
                contentDestination = stringResource(iconTextId),
                icon = {
                    val icon = if (selected) selectedIcon
                    else unselectedIcon
                    Icon(
                        imageVector = icon,
                        contentDescription = stringResource(iconTextId)
                    )
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
}

@Composable
private fun Content(
    navigate: Navigate,
    destinationHolder: RootDestinationHolder,
    rootDestination: Destination.Root?,
    fob: Fob?,
    selectedColor: Color,
    fobbedColor: Color,
    modifier: Modifier = Modifier
) {
    val relation = fob?.rootDestination
    val actualActiveDestination = rootDestination ?: relation
    val roots = destinationHolder.roots
    Row(modifier) {
        roots.forEach { root ->
            val fobbed = root == relation
            val selected = root == actualActiveDestination
            val iconTextId = root.iconTextId
            val selectedIcon = fob?.icon.takeIf { fobbed } ?: root.selectedIcon
            val unselectedIcon = fob?.icon.takeIf { fobbed } ?: root.unselectedIcon

            val actualSelectedColor by animateColorAsState(
                targetValue = if (fobbed) fobbedColor else selectedColor,
                label = "navigation-normal-content-select-color"
            )

            NavigationBarItem(
                selected = selected,
                alwaysShowLabel = false,
                onClick = {
                    if (fobbed && fob != null) {
                        fob.onClick()
                    } else {
                        navigate(root)
                    }
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedTextColor = actualSelectedColor,
                    selectedIconColor = actualSelectedColor,
                    indicatorColor = Color.Transparent
                ),
                icon = {
                    val icon = if (selected) selectedIcon
                    else unselectedIcon
                    Icon(
                        imageVector = icon,
                        contentDescription = stringResource(iconTextId)
                    )
                },
                label = {
                    Text(
                        text = stringResource(iconTextId).title(),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                },
                modifier = Modifier.testTag("destination")
            )
        }
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
            modifier = modifier.testTag("destination"),
            enabled = enabled,
            label = label,
            alwaysShowLabel = false,
            colors = NavigationRailItemDefaults.colors(
                selectedIconColor = selectedColor,
                selectedTextColor = selectedColor,
                indicatorColor = Color.Transparent
            ),
            interactionSource = remember { MutableInteractionSource() },
        )
    }
}

object AppNavigationDefaults {
    @Composable
    fun color() = MaterialTheme.colorScheme.surface

    @Composable
    fun selectedColor() = MaterialTheme.colorScheme.primary

    @Composable
    fun unselectedColor() = MaterialTheme.colorScheme.onSurface

    @Composable
    fun fobbedColor() = MaterialTheme.colorScheme.primary
}