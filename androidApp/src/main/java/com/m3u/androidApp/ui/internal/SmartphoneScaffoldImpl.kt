package com.m3u.androidApp.ui.internal

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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.m3u.androidApp.ui.Items
import com.m3u.androidApp.ui.MainContent
import com.m3u.androidApp.ui.NavigationItemLayout
import com.m3u.androidApp.ui.ScaffoldLayout
import com.m3u.androidApp.ui.ScaffoldRole
import com.m3u.material.components.Background
import com.m3u.material.ktx.plus
import com.m3u.material.model.LocalHazeState
import com.m3u.ui.Destination
import com.m3u.ui.helper.Metadata
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.hazeChild

@Composable
@InternalComposeApi
fun SmartphoneScaffoldImpl(
    rootDestination: Destination.Root?,
    alwaysShowLabel: Boolean,
    navigateToRoot: (Destination.Root) -> Unit,
    onBackPressed: (() -> Unit)?,
    content: @Composable BoxScope.(PaddingValues) -> Unit,
    modifier: Modifier = Modifier
) {
    val hazeState = LocalHazeState.current
    val fob = Metadata.fob

    val navigation = @Composable {
        NavigationBar(
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier
                .hazeChild(hazeState, style = HazeStyle(blurRadius = 6.dp, noiseFactor = 0.4f))
                .fillMaxWidth()
        ) {
            Items { currentRootDestination ->
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
    val mainContent = @Composable { contentPadding: PaddingValues ->
        MainContent(
            windowInsets = WindowInsets.systemBars.exclude(WindowInsets.navigationBars),
            onBackPressed = onBackPressed,
            content = { content(it + contentPadding) }
        )
    }

    Background(modifier) {
        ScaffoldLayout(
            role = ScaffoldRole.SmartPhone,
            mainContent = mainContent,
            navigation = navigation
        )
    }
}
