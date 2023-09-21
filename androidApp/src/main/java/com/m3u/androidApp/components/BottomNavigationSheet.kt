package com.m3u.androidApp.components

//import androidx.compose.material3.PlainTooltip
//import androidx.compose.material3.TooltipBox
//import androidx.compose.material3.TooltipDefaults
//import androidx.compose.material3.rememberTooltipState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material.Icon
import androidx.compose.material.LocalAbsoluteElevation
import androidx.compose.material.MaterialTheme
import androidx.compose.material.NavigationRailItem
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.m3u.androidApp.navigation.NavigateToTopLevelDestination
import com.m3u.androidApp.navigation.TopLevelDestination
import com.m3u.ui.components.NavigationSheet
import com.m3u.ui.model.LocalTheme
import com.m3u.ui.util.animated

@Composable
fun BottomNavigationSheet(
    destinations: List<TopLevelDestination>,
    navigateToTopLevelDestination: NavigateToTopLevelDestination,
    index: Int,
    modifier: Modifier = Modifier,
    backgroundColor: Color = BottomSheetDefaults.navigationBackgroundColor(),
    contentColor: Color = BottomSheetDefaults.navigationContentColor(),
    selectedColor: Color = BottomSheetDefaults.navigationSelectedColor(),
) {
    val actualBackgroundColor by backgroundColor.animated("BottomNavigationSheetBackground")
    val actualContentColor by contentColor.animated("BottomNavigationSheetContent")
    val actualSelectedColor by selectedColor.animated("BottomNavigationSheetSelected")
    NavigationSheet(
        modifier = modifier,
        containerColor = actualBackgroundColor,
        contentColor = actualContentColor,
        elevation = LocalAbsoluteElevation.current
    ) {
        destinations.forEachIndexed { i, destination ->
            val selected = i == index
            NavigationBarItem(
                selected = selected,
                onClick = {
                    navigateToTopLevelDestination(destination)
                },
                tint = actualSelectedColor,
                contentDestination = stringResource(destination.iconTextId),
                icon = {
                    val icon = if (selected) destination.selectedIcon
                    else destination.unselectedIcon
                    Icon(
                        imageVector = icon,
                        contentDescription = stringResource(destination.iconTextId)
                    )
                },
                label = {
                    Text(
                        text = stringResource(destination.iconTextId),
                        style = MaterialTheme.typography.caption,
                        fontWeight = FontWeight.Bold
                    )
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
    contentDestination: String? = null,
    tint: Color
) {
    // TODO: Enabled when [https://issuetracker.google.com/issues/299500338] fixed.
//    TooltipBox(
//        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
//        state = rememberTooltipState(),
//        tooltip = {
//            PlainTooltip {
//                Text(contentDestination.orEmpty())
//            }
//        }
//    ) {
    NavigationRailItem(
        selected = selected,
        onClick = onClick,
        icon = icon,
        modifier = modifier,
        enabled = enabled,
        label = label,
        alwaysShowLabel = false,
        selectedContentColor = tint,
        interactionSource = remember { MutableInteractionSource() },
    )
//    }
}

object BottomSheetDefaults {
    @Composable
    fun navigationBackgroundColor() = Color.Black

    @Composable
    fun navigationContentColor() = Color(0xFFEEEEEE)

    @Composable
    fun navigationSelectedColor() = LocalTheme.current.tint
}