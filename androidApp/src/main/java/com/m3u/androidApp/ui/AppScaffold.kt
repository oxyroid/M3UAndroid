package com.m3u.androidApp.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.InternalComposeApi
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEach
import com.m3u.androidApp.ui.internal.AppScaffoldImpl
import com.m3u.androidApp.ui.internal.AppScaffoldRailImpl
import com.m3u.androidApp.ui.internal.AppScaffoldTvImpl
import com.m3u.material.components.Background
import com.m3u.material.components.IconButton
import com.m3u.material.ktx.isTelevision
import com.m3u.material.model.LocalHazeState
import com.m3u.material.model.LocalSpacing
import com.m3u.ui.Destination
import com.m3u.ui.helper.Action
import com.m3u.ui.helper.Fob
import com.m3u.ui.helper.LocalHelper
import com.m3u.ui.helper.useRailNav
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.hazeChild
import kotlinx.collections.immutable.ImmutableList

@Composable
@OptIn(InternalComposeApi::class)
internal fun AppScaffold(
    title: String,
    actions: ImmutableList<Action>,
    rootDestination: Destination.Root?,
    fob: Fob?,
    navigateToRoot: (Destination.Root) -> Unit,
    modifier: Modifier = Modifier,
    onBackPressed: (() -> Unit)? = null,
    alwaysShowLabel: Boolean = false,
    content: @Composable BoxScope.(PaddingValues) -> Unit
) {
    val useRailNav = LocalHelper.current.useRailNav
    val tv = isTelevision()

    val hazeState = remember { HazeState() }

    CompositionLocalProvider(LocalHazeState provides hazeState) {
        Background {
            when {
                tv -> {
                    AppScaffoldTvImpl(
                        rootDestination = rootDestination,
                        fob = fob,
                        title = title,
                        navigateToRoot = navigateToRoot,
                        onBackPressed = onBackPressed,
                        actions = actions,
                        content = content,
                        modifier = modifier
                    )
                }

                !useRailNav -> {
                    AppScaffoldImpl(
                        rootDestination = rootDestination,
                        alwaysShowLabel = alwaysShowLabel,
                        fob = fob,
                        title = title,
                        navigateToRoot = navigateToRoot,
                        onBackPressed = onBackPressed,
                        actions = actions,
                        content = content,
                        modifier = modifier
                    )
                }

                else -> {
                    AppScaffoldRailImpl(
                        rootDestination = rootDestination,
                        alwaysShowLabel = alwaysShowLabel,
                        fob = fob,
                        title = title,
                        navigateToRoot = navigateToRoot,
                        onBackPressed = onBackPressed,
                        actions = actions,
                        content = content,
                        modifier = modifier
                    )
                }
            }
        }
    }
}


@Composable
internal fun Items(
    inner: @Composable (Destination.Root) -> Unit
) {
    Destination.Root.entries.fastForEach { rootDestination ->
        inner(rootDestination)
    }
}

@Composable
internal fun TopBarWithContent(
    windowInsets: WindowInsets,
    title: String,
    onBackPressed: (() -> Unit)?,
    actions: ImmutableList<Action>,
    content: @Composable BoxScope.(PaddingValues) -> Unit
) {
    val tv = isTelevision()
    val spacing = LocalSpacing.current
    val hazeState = LocalHazeState.current

    Scaffold(
        topBar = {
            if (!tv) {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(Color.Transparent),
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
                    modifier = Modifier
                        .hazeChild(hazeState, style = HazeStyle(blurRadius = 6.dp))
                        .fillMaxWidth()
                )
            }
        },
        contentWindowInsets = windowInsets,
        containerColor = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.fillMaxSize()
    ) { padding ->
        Background {
            Box {
                content(padding)
            }
        }
    }
}

@Composable
internal fun NavigationItemLayout(
    rootDestination: Destination.Root?,
    fob: Fob?,
    currentRootDestination: Destination.Root,
    navigateToRoot: (Destination.Root) -> Unit,
    block: @Composable (
        selected: Boolean,
        onClick: () -> Unit,
        icon: @Composable () -> Unit,
        label: @Composable () -> Unit
    ) -> Unit
) {
    val hapticFeedback = LocalHapticFeedback.current

    val tv = isTelevision()
    val usefob = fob?.rootDestination == currentRootDestination
    val selected = usefob || currentRootDestination == rootDestination
    val icon = @Composable {
        if (!tv) {
            Icon(
                imageVector = when {
                    fob != null && usefob -> fob.icon
                    selected -> currentRootDestination.selectedIcon
                    else -> currentRootDestination.unselectedIcon
                },
                contentDescription = null
            )
        } else {
            androidx.tv.material3.Icon(
                imageVector = when {
                    fob != null && usefob -> fob.icon
                    selected -> currentRootDestination.selectedIcon
                    else -> currentRootDestination.unselectedIcon
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
                        else currentRootDestination.iconTextId
                    ).uppercase()
                )
            } else {
                androidx.tv.material3.Text(
                    text = stringResource(
                        if (usefob && fob != null) fob.iconTextId
                        else currentRootDestination.iconTextId
                    ).uppercase()
                )
            }
        }
    }
    val actualOnClick: () -> Unit = if (usefob) {
        { fob?.onClick?.invoke() }
    } else {
        {
            navigateToRoot(currentRootDestination)
            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }
    block(selected, actualOnClick, icon, label)
}
