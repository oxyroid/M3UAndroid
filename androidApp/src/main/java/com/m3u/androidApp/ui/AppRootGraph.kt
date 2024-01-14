package com.m3u.androidApp.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import com.m3u.core.wrapper.Message
import com.m3u.material.components.Background
import com.m3u.material.components.IconButton
import com.m3u.material.ktx.isTelevision
import com.m3u.material.model.LocalSpacing
import com.m3u.ui.Action
import com.m3u.ui.AppSnackHost
import com.m3u.ui.Destination
import com.m3u.ui.Fob
import com.m3u.ui.LocalHelper
import com.m3u.ui.useRailNav
import kotlinx.collections.immutable.ImmutableList

@Composable
internal fun AppRootGraph(
    title: String,
    message: Message,
    actions: ImmutableList<Action>,
    currentRootDestination: Destination.Root?,
    rootDestinations: ImmutableList<Destination.Root>,
    fob: Fob?,
    navigateToRoot: (Destination.Root) -> Unit,
    modifier: Modifier = Modifier,
    onBackPressed: (() -> Unit)? = null,
    alwaysShowLabel: Boolean = false,
    content: @Composable (PaddingValues) -> Unit
) {
    val spacing = LocalSpacing.current
    val useRailNav = LocalHelper.current.useRailNav
    val tv = isTelevision()

    val items: @Composable (inner: @Composable (Destination.Root) -> Unit) -> Unit = { inner ->
        rootDestinations.forEach { rootDestination ->
            inner(rootDestination)
        }
    }

    val topBar = @Composable {
        if (!tv) {
            TopAppBar(
                title = {
                    Text(
                        text = title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = spacing.medium)
                    )
                },
                navigationIcon = {
                    if (onBackPressed != null) {
                        IconButton(
                            icon = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = null,
                            onClick = onBackPressed,
                            modifier = Modifier.wrapContentSize()
                        )
                    }
                },
                actions = {
                    actions.forEach { action ->
                        IconButton(
                            icon = action.icon,
                            contentDescription = action.contentDescription,
                            onClick = action.onClick
                        )
                    }
                    Spacer(modifier = Modifier.width(spacing.medium))
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    val topBarWithContent = @Composable { windowInsets: WindowInsets ->
        Scaffold(
            topBar = topBar,
            contentWindowInsets = windowInsets,
            modifier = Modifier.fillMaxSize()
        ) { padding ->
            Background {
                Box {
                    content(padding)
                    AppSnackHost(
                        message = message,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(spacing.small)
                            .align(Alignment.BottomCenter)
                            .padding(padding)
                    )
                }
            }
        }
    }

    Background {
        if (tv) {
            Row(modifier) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(
                        spacing.medium,
                        Alignment.CenterVertically
                    ),
                    modifier = Modifier
                        .fillMaxHeight()
                        .padding(spacing.medium)
                ) {
                    items {
                        NavigationItemLayout(
                            currentRootDestination = currentRootDestination,
                            fob = fob,
                            rootDestination = it,
                            navigateToRoot = navigateToRoot
                        ) { selected: Boolean,
                            onClick: () -> Unit,
                            icon: @Composable () -> Unit,
                            _: @Composable () -> Unit ->
                            val source = remember { MutableInteractionSource() }
                            val focused by source.collectIsFocusedAsState()
                            val currentContainerColor by with(androidx.tv.material3.MaterialTheme.colorScheme) {
                                animateColorAsState(
                                    targetValue = when {
                                        selected -> inverseSurface
                                        focused -> primaryContainer.copy(0.67f)
                                        else -> background
                                    },
                                    label = "scaffold-navigation-container"
                                )
                            }
                            val currentContentColor by with(androidx.tv.material3.MaterialTheme.colorScheme) {
                                animateColorAsState(
                                    targetValue = when {
                                        selected -> inverseOnSurface
                                        focused -> onPrimaryContainer
                                        else -> onBackground
                                    },
                                    label = "scaffold-navigation-content"
                                )
                            }
                            Card(
                                onClick = onClick,
                                colors = CardDefaults.colors(
                                    containerColor = currentContainerColor,
                                    contentColor = currentContentColor
                                ),
                                interactionSource = source,
                                shape = CardDefaults.shape(CircleShape, CircleShape, CircleShape),
                                border = CardDefaults.border(Border.None, Border.None, Border.None),
                                scale = CardDefaults.scale(
                                    scale = if (selected) 1.1f else 1f,
                                    focusedScale = if (selected) 1.2f else 1.1f
                                ),
                                content = {
                                    Box(modifier = Modifier.padding(spacing.medium)) { icon() }
                                }
                            )
                        }
                    }
                }
                Box(
                    Modifier
                        .fillMaxHeight()
                        .weight(1f)
                ) { topBarWithContent(WindowInsets.systemBars) }
            }
        } else {
            val currentContainerColor by animateColorAsState(
                targetValue = MaterialTheme.colorScheme.background,
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                label = "scaffold-navigation-container"
            )
            val currentContentColor by animateColorAsState(
                targetValue = MaterialTheme.colorScheme.onBackground,
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                label = "scaffold-navigation-content"
            )

            if (!useRailNav) {
                Column(modifier) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) { topBarWithContent(WindowInsets.systemBars.exclude(WindowInsets.navigationBars)) }
                    NavigationBar(
                        containerColor = currentContainerColor,
                        contentColor = currentContentColor,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items {
                            NavigationItemLayout(
                                currentRootDestination = currentRootDestination,
                                fob = fob,
                                rootDestination = it,
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
            } else {
                Row(modifier) {
                    NavigationRail(
                        containerColor = currentContainerColor,
                        contentColor = currentContentColor,
                        modifier = Modifier.fillMaxHeight(),
                        // keep header not null
                        header = {}
                    ) {
                        items {
                            NavigationItemLayout(
                                currentRootDestination = currentRootDestination,
                                fob = fob,
                                rootDestination = it,
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
                    ) { topBarWithContent(WindowInsets.systemBars) }
                }
            }
        }
    }
}

@Composable
private inline fun NavigationItemLayout(
    currentRootDestination: Destination.Root?,
    fob: Fob?,
    rootDestination: Destination.Root,
    crossinline navigateToRoot: (Destination.Root) -> Unit,
    block: @Composable (
        selected: Boolean,
        onClick: () -> Unit,
        icon: @Composable () -> Unit,
        label: @Composable () -> Unit
    ) -> Unit
) {
    val tv = isTelevision()
    val usefob = fob?.rootDestination == rootDestination
    val selected = usefob || rootDestination == currentRootDestination
    val icon = @Composable {
        if (!tv) {
            Icon(
                imageVector = when {
                    fob != null && usefob -> fob.icon
                    selected -> rootDestination.selectedIcon
                    else -> rootDestination.unselectedIcon
                },
                contentDescription = null
            )
        } else {
            androidx.tv.material3.Icon(
                imageVector = when {
                    fob != null && usefob -> fob.icon
                    selected -> rootDestination.selectedIcon
                    else -> rootDestination.unselectedIcon
                },
                contentDescription = null
            )
        }
    }
    val label: @Composable () -> Unit = remember(usefob, fob) {
        @Composable {
            if (!tv) {
                Text(
                    text = stringResource(
                        if (usefob && fob != null) fob.iconTextId
                        else rootDestination.iconTextId
                    )
                )
            } else {
                androidx.tv.material3.Text(
                    text = stringResource(
                        if (usefob && fob != null) fob.iconTextId
                        else rootDestination.iconTextId
                    )
                )
            }
        }
    }
    val actualOnClick: () -> Unit = if (usefob) {
        { fob?.onClick?.invoke() }
    } else {
        { navigateToRoot(rootDestination) }
    }
    block(selected, actualOnClick, icon, label)
}
