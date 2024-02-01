package com.m3u.androidApp.ui.internal

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import com.m3u.androidApp.ui.Items
import com.m3u.androidApp.ui.NavigationItemLayout
import com.m3u.androidApp.ui.TopBarWithContent
import com.m3u.core.wrapper.Message
import com.m3u.ui.Destination
import com.m3u.ui.helper.Action
import com.m3u.ui.helper.Fob
import kotlinx.collections.immutable.ImmutableList

@Composable
@InternalComposeApi
fun AppScaffoldImpl(
    rootDestination: Destination.Root?,
    rootDestinations: ImmutableList<Destination.Root>,
    alwaysShowLabel: Boolean,
    fob: Fob?,
    title: String,
    message: Message,
    navigateToRoot: (Destination.Root) -> Unit,
    onBackPressed: (() -> Unit)?,
    actions: ImmutableList<Action>,
    content: @Composable (PaddingValues) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier) {
        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            TopBarWithContent(
                message = message,
                windowInsets = WindowInsets.systemBars.exclude(WindowInsets.navigationBars),
                title = title,
                onBackPressed = onBackPressed,
                actions = actions,
                content = content
            )
        }
        NavigationBar(
            containerColor = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.fillMaxWidth()
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