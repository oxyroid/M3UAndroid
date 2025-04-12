package com.m3u.smartphone.ui.common.internal

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailDefaults
import androidx.compose.material3.NavigationRailItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.InternalComposeApi
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.m3u.core.foundation.ui.composableOf
import com.m3u.smartphone.ui.common.Items
import com.m3u.smartphone.ui.common.MainContent
import com.m3u.smartphone.ui.common.NavigationItemLayout
import com.m3u.smartphone.ui.common.ScaffoldLayout
import com.m3u.smartphone.ui.common.ScaffoldRole
import com.m3u.smartphone.ui.material.components.Background
import com.m3u.smartphone.ui.material.ktx.plus
import com.m3u.smartphone.ui.material.components.Destination
import com.m3u.smartphone.ui.common.helper.Metadata

@Composable
@InternalComposeApi
internal fun TabletScaffoldImpl(
    rootDestination: Destination.Root?,
    alwaysShowLabel: Boolean,
    navigateToRoot: (Destination.Root) -> Unit,
    onBackPressed: (() -> Unit)?,
    navigateToChannel: () -> Unit,
    content: @Composable BoxScope.(PaddingValues) -> Unit,
    modifier: Modifier = Modifier
) {
    val navigationWindowInsets = NavigationRailDefaults.windowInsets
    val fob = Metadata.fob
    var showQuery by remember { mutableStateOf(false) }

    val navigation = composableOf(!showQuery) {
        NavigationRail(
            modifier = Modifier.fillMaxHeight(),
            containerColor = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.onBackground,
            windowInsets = navigationWindowInsets,
            // keep header not null
            header = {}
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
                    NavigationRailItem(
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
            showQuery = showQuery,
            onShowQueryChange = { showQuery = it },
            windowInsets = WindowInsets.systemBars.exclude(
                navigationWindowInsets.only(WindowInsetsSides.Start)
            ),
            onBackPressed = onBackPressed,
            navigateToChannel = navigateToChannel,
            content = { content(it + contentPadding) }
        )
    }

    Background(modifier) {
        ScaffoldLayout(
            role = ScaffoldRole.Tablet,
            navigation = navigation ?: {},
            mainContent = mainContent
        )
    }
}