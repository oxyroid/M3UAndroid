package com.m3u.androidApp.components

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material.Icon
import androidx.compose.material.LocalAbsoluteElevation
import androidx.compose.material.MaterialTheme
import androidx.compose.material.NavigationRailItem
import androidx.compose.material.Text
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.PlainTooltip
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
import com.m3u.ui.Destination
import com.m3u.ui.NavigateTo
import com.m3u.ui.components.NavigationSheet
import com.m3u.ui.ktx.animateColor
import com.m3u.ui.ktx.animated
import com.m3u.ui.model.Fob
import com.m3u.ui.model.LocalTheme

@Composable
fun AppBottomSheet(
    navigateTo: NavigateTo,
    rootDestination: Destination.Root?,
    fob: Fob?,
    modifier: Modifier = Modifier,
    backgroundColor: Color = BottomSheetDefaults.backgroundColor(),
    selectedColor: Color = BottomSheetDefaults.selectedColor(),
    unselectedColor: Color = BottomSheetDefaults.unselectedColor(),
    fobbedColor: Color = BottomSheetDefaults.fobbedColor(),
) {
    val destinations = Destination.Root.entries
    val actualBackgroundColor by backgroundColor.animated("BottomNavigationSheetBackground")
    val actualContentColor by unselectedColor.animated("BottomNavigationSheetContent")

    NavigationSheet(
        modifier = modifier,
        containerColor = actualBackgroundColor,
        contentColor = actualContentColor,
        elevation = LocalAbsoluteElevation.current
    ) {
        val relation = fob?.rootDestination
        val actualActiveDestination = rootDestination ?: relation
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
                        navigateTo(default)
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
                        style = MaterialTheme.typography.caption,
                        fontWeight = FontWeight.Bold
                    )
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NavigationBarItem(
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
            selectedContentColor = selectedColor,
            interactionSource = remember { MutableInteractionSource() },
        )
    }
}

object BottomSheetDefaults {
    fun backgroundColor() = Color.Black

    @Composable
    fun selectedColor() = LocalTheme.current.tint
    fun unselectedColor() = Color(0xFFEEEEEE)

    @Composable
    fun fobbedColor() = LocalTheme.current.onPrimary
}