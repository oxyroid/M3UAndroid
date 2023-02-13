package com.m3u.app.ui

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material.Icon
import androidx.compose.material.NavigationRailItem
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hierarchy
import com.m3u.app.navigation.NavigateToTopLevelDestination
import com.m3u.app.navigation.TopLevelDestination
import com.m3u.ui.components.NavigationSheet
import com.m3u.ui.model.Icon
import com.m3u.ui.model.LocalSpacing
import com.m3u.ui.model.LocalTheme

@Composable
fun BottomNavigationSheet(
    destinations: List<TopLevelDestination>,
    navigateToTopLevelDestination: NavigateToTopLevelDestination,
    currentNavDestination: NavDestination?,
    modifier: Modifier = Modifier
) {
    NavigationSheet(
        modifier = modifier,
        containerColor = BottomSheetDefaults.navigationBackgroundColor(),
        contentColor = BottomSheetDefaults.navigationContentColor(),
        elevation = LocalSpacing.current.none
    ) {
        destinations.forEach { destination ->
            val selected = currentNavDestination.isTopLevelDestinationInHierarchy(destination)
            NavigationBarItem(
                alwaysShowLabel = false,
                selected = selected,
                onClick = { navigateToTopLevelDestination(destination) },
                tint = BottomSheetDefaults.navigationSelectedItemColor(),
                icon = {
                    val icon = if (selected) destination.selectedIcon
                    else destination.unselectedIcon
                    when (icon) {
                        is Icon.ImageVectorIcon -> Icon(
                            imageVector = icon.imageVector,
                            contentDescription = stringResource(destination.iconTextId)
                        )
                        is Icon.DrawableResourceIcon -> Icon(
                            painter = painterResource(icon.id),
                            contentDescription = stringResource(destination.iconTextId)
                        )
                    }
                },
                label = {
                    Text(text = stringResource(destination.iconTextId))
                }
            )
        }
    }
}


@Composable
private fun NavigationBarItem(
    selected: Boolean,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    label: @Composable (() -> Unit)? = null,
    alwaysShowLabel: Boolean = true,
    tint: Color
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

object BottomSheetDefaults {
    @Composable
    fun navigationBackgroundColor() = Color(0xff000000)

    @Composable
    fun navigationContentColor() = Color(0xFFEEEEEE)

    @Composable
    fun navigationSelectedItemColor() = LocalTheme.current.tint
}

private fun NavDestination?.isTopLevelDestinationInHierarchy(destination: TopLevelDestination) =
    this?.hierarchy?.any {
        it.route?.contains(destination.name, true) ?: false
    } ?: false