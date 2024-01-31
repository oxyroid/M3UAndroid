package com.m3u.androidApp.ui.scaffold

import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.InternalComposeApi
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.util.fastForEach
import com.m3u.core.wrapper.Message
import com.m3u.material.components.Background
import com.m3u.material.components.IconButton
import com.m3u.material.ktx.isTelevision
import com.m3u.material.model.LocalSpacing
import com.m3u.ui.Destination
import com.m3u.ui.M3USnackHost
import com.m3u.ui.helper.Action
import com.m3u.ui.helper.Fob
import com.m3u.ui.helper.LocalHelper
import com.m3u.ui.helper.useRailNav
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toPersistentList

@Composable
@OptIn(InternalComposeApi::class)
internal fun AppScaffold(
    title: String,
    message: Message,
    actions: ImmutableList<Action>,
    rootDestination: Destination.Root?,
    fob: Fob?,
    navigateToRoot: (Destination.Root) -> Unit,
    modifier: Modifier = Modifier,
    onBackPressed: (() -> Unit)? = null,
    alwaysShowLabel: Boolean = false,
    content: @Composable (PaddingValues) -> Unit
) {
    val useRailNav = LocalHelper.current.useRailNav
    val tv = isTelevision()

    val rootDestinations = remember {
        Destination.Root.entries.toPersistentList()
    }

    Background {
        when {
            tv -> {
                AppScaffoldTvImpl(
                    rootDestination = rootDestination,
                    rootDestinations = rootDestinations,
                    fob = fob,
                    title = title,
                    message = message,
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
                    rootDestinations = rootDestinations,
                    alwaysShowLabel = alwaysShowLabel,
                    fob = fob,
                    title = title,
                    message = message,
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
                    rootDestinations = rootDestinations,
                    alwaysShowLabel = alwaysShowLabel,
                    fob = fob,
                    title = title,
                    message = message,
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


@Composable
internal fun Items(
    roots: ImmutableList<Destination.Root>,
    inner: @Composable (Destination.Root) -> Unit
) {
    roots.fastForEach { rootDestination ->
        inner(rootDestination)
    }
}

@Composable
internal fun TopBarWithContent(
    message: Message,
    windowInsets: WindowInsets,
    title: String,
    onBackPressed: (() -> Unit)?,
    actions: ImmutableList<Action>,
    content: @Composable (PaddingValues) -> Unit
) {
    val tv = isTelevision()
    val spacing = LocalSpacing.current

    Scaffold(
        topBar = {
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
        },
        contentWindowInsets = windowInsets,
        modifier = Modifier.fillMaxSize()
    ) { padding ->
        Background {
            Box {
                content(padding)
                M3USnackHost(
                    message = message,
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(padding)
                )
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
