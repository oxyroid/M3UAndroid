package com.m3u.androidApp.ui.internal

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
import androidx.compose.ui.Modifier
import com.m3u.androidApp.ui.Items
import com.m3u.androidApp.ui.MainContent
import com.m3u.androidApp.ui.NavigationItemLayout
import com.m3u.androidApp.ui.ScaffoldLayout
import com.m3u.androidApp.ui.ScaffoldRole
import com.m3u.material.components.Background
import com.m3u.material.ktx.plus
import com.m3u.ui.Destination
import com.m3u.ui.helper.Action
import com.m3u.ui.helper.Fob
import com.m3u.ui.helper.Metadata

@Composable
@InternalComposeApi
internal fun TabletScaffoldImpl(
    rootDestination: Destination.Root?,
    alwaysShowLabel: Boolean,
    navigateToRoot: (Destination.Root) -> Unit,
    onBackPressed: (() -> Unit)?,
    content: @Composable BoxScope.(PaddingValues) -> Unit,
    modifier: Modifier = Modifier
) {
    val navigationWindowInsets = NavigationRailDefaults.windowInsets
    val fob = Metadata.fob

    val navigation = @Composable {
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
            windowInsets = WindowInsets.systemBars.exclude(
                navigationWindowInsets.only(WindowInsetsSides.Start)
            ),
            onBackPressed = onBackPressed,
            content = { content(it + contentPadding) }
        )
    }

    Background(modifier) {
        ScaffoldLayout(
            role = ScaffoldRole.Tablet,
            navigation = navigation,
            mainContent = mainContent
        )
    }
}