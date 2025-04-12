package com.m3u.smartphone.ui.common.internal

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
import com.m3u.smartphone.ui.common.Items
import com.m3u.smartphone.ui.common.MainContent
import com.m3u.smartphone.ui.common.NavigationItemLayout
import com.m3u.smartphone.ui.common.ScaffoldLayout
import com.m3u.smartphone.ui.common.ScaffoldRole
import com.m3u.smartphone.ui.common.helper.Metadata
import com.m3u.smartphone.ui.material.components.Background
import com.m3u.smartphone.ui.material.components.Destination
import com.m3u.smartphone.ui.material.ktx.plus
import com.m3u.smartphone.ui.material.model.LocalHazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials

@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
@InternalComposeApi
fun SmartphoneScaffoldImpl(
    rootDestination: Destination.Root?,
    alwaysShowLabel: Boolean,
    navigateToRoot: (Destination.Root) -> Unit,
    onBackPressed: (() -> Unit)?,
    navigateToChannel: () -> Unit,
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
                .hazeEffect(hazeState, HazeMaterials.ultraThin())
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
            content = { content(it + contentPadding) },
            navigateToChannel = navigateToChannel
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
