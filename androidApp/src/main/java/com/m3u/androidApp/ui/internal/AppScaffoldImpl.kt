package com.m3u.androidApp.ui.internal

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.InternalComposeApi
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.m3u.androidApp.ui.Items
import com.m3u.androidApp.ui.NavigationItemLayout
import com.m3u.androidApp.ui.TopBarWithContent
import com.m3u.material.ktx.plus
import com.m3u.material.model.LocalHazeState
import com.m3u.ui.Destination
import com.m3u.ui.helper.Action
import com.m3u.ui.helper.Fob
import dev.chrisbanes.haze.hazeChild
import kotlinx.collections.immutable.ImmutableList

@Composable
@InternalComposeApi
fun AppScaffoldImpl(
    rootDestination: Destination.Root?,
    rootDestinations: ImmutableList<Destination.Root>,
    alwaysShowLabel: Boolean,
    fob: Fob?,
    title: String,
    navigateToRoot: (Destination.Root) -> Unit,
    onBackPressed: (() -> Unit)?,
    actions: ImmutableList<Action>,
    content: @Composable BoxScope.(PaddingValues) -> Unit,
    modifier: Modifier = Modifier
) {
    val hazeState = LocalHazeState.current
    val density = LocalDensity.current
    var navigationHeight by remember { mutableStateOf(0.dp) }
    Box(modifier) {
        TopBarWithContent(
            windowInsets = WindowInsets.systemBars.exclude(WindowInsets.navigationBars),
            title = title,
            onBackPressed = onBackPressed,
            actions = actions,
            content = {
                content(it + PaddingValues(bottom = navigationHeight))
            }
        )

        NavigationBar(
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier
                .hazeChild(hazeState)
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .onGloballyPositioned {
                    navigationHeight = with(density) {
                        it.size.height.toDp()
                    }
                }
        ) {
            Items(rootDestinations) { currentRootDestination ->
                NavigationItemLayout(
                    rootDestination = rootDestination,
                    fob = fob,
                    currentRootDestination = currentRootDestination,
                    navigateToRoot = navigateToRoot
                ) { selected: Boolean,
                    onClick: () -> Unit,
                    icon: @Composable () -> Unit,
                    label: @Composable () -> Unit ->
                    NavigationBarItem(
                        selected = selected,
                        onClick = onClick,
                        icon = icon,
                        label = label,
                        alwaysShowLabel = alwaysShowLabel
                    )
                }
            }
        }
    }
}