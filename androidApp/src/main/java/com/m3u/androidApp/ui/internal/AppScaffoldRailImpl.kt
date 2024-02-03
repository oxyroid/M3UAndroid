package com.m3u.androidApp.ui.internal

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.InternalComposeApi
import androidx.compose.ui.Modifier
import com.m3u.androidApp.ui.Items
import com.m3u.androidApp.ui.NavigationItemLayout
import com.m3u.androidApp.ui.TopBarWithContent
import com.m3u.ui.Destination
import com.m3u.ui.helper.Action
import com.m3u.ui.helper.Fob
import kotlinx.collections.immutable.ImmutableList

@Composable
@InternalComposeApi
internal fun AppScaffoldRailImpl(
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
    Row(modifier) {
        NavigationRail(
            modifier = Modifier.fillMaxHeight(),
            containerColor = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.onBackground,
            // keep header not null
            header = {}
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
        Box(
            Modifier
                .fillMaxHeight()
                .weight(1f)
        ) {
            TopBarWithContent(
                windowInsets = WindowInsets.systemBars,
                title = title,
                onBackPressed = onBackPressed,
                actions = actions,
                content = content
            )
        }
    }
}