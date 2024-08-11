package com.m3u.androidApp.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.InternalComposeApi
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.offset
import androidx.compose.ui.util.fastForEach
import androidx.compose.ui.util.fastMap
import androidx.compose.ui.util.fastMaxOfOrNull
import com.m3u.androidApp.ui.internal.TvScaffoldImpl
import com.m3u.androidApp.ui.internal.SmartphoneScaffoldImpl
import com.m3u.androidApp.ui.internal.TabletScaffoldImpl
import com.m3u.material.components.Background
import com.m3u.material.components.Icon
import com.m3u.material.components.IconButton
import com.m3u.material.effects.currentBackStackEntry
import com.m3u.material.ktx.tv
import com.m3u.material.model.LocalHazeState
import com.m3u.material.model.LocalSpacing
import com.m3u.ui.Destination
import com.m3u.ui.FontFamilies
import com.m3u.ui.helper.Fob
import com.m3u.ui.helper.LocalHelper
import com.m3u.ui.helper.Metadata
import com.m3u.ui.helper.useRailNav
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.hazeChild
import androidx.tv.material3.Icon as TvIcon
import androidx.tv.material3.Text as TvText

@Composable
@OptIn(InternalComposeApi::class)
internal fun Scaffold(
    rootDestination: Destination.Root?,
    navigateToRootDestination: (Destination.Root) -> Unit,
    modifier: Modifier = Modifier,
    onBackPressed: (() -> Unit)? = null,
    alwaysShowLabel: Boolean = false,
    content: @Composable BoxScope.(PaddingValues) -> Unit
) {
    val useRailNav = LocalHelper.current.useRailNav
    val tv = tv()

    val hazeState = remember { HazeState() }

    CompositionLocalProvider(LocalHazeState provides hazeState) {
        Background {
            when {
                tv -> {
                    TvScaffoldImpl(
                        rootDestination = rootDestination,
                        navigateToRoot = navigateToRootDestination,
                        onBackPressed = onBackPressed,
                        content = content,
                        modifier = modifier
                    )
                }

                !useRailNav -> {
                    SmartphoneScaffoldImpl(
                        rootDestination = rootDestination,
                        alwaysShowLabel = alwaysShowLabel,
                        navigateToRoot = navigateToRootDestination,
                        onBackPressed = onBackPressed,
                        content = content,
                        modifier = modifier
                    )
                }

                else -> {
                    TabletScaffoldImpl(
                        rootDestination = rootDestination,
                        alwaysShowLabel = alwaysShowLabel,
                        navigateToRoot = navigateToRootDestination,
                        onBackPressed = onBackPressed,
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
internal fun MainContent(
    windowInsets: WindowInsets,
    onBackPressed: (() -> Unit)?,
    content: @Composable BoxScope.(PaddingValues) -> Unit
) {
    val tv = tv()
    val spacing = LocalSpacing.current
    val hazeState = LocalHazeState.current

    val title = Metadata.title
    val subtitle = Metadata.subtitle
    val actions = Metadata.actions

    val backStackEntry by currentBackStackEntry()

    Scaffold(
        topBar = {
            if (!tv) {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(Color.Transparent),
                    windowInsets = windowInsets,
                    title = {
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.defaultMinSize(minHeight = 56.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .padding(horizontal = spacing.medium)
                                    .weight(1f)
                            ) {
                                Text(
                                    text = title,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    fontFamily = FontFamilies.LexendExa
                                )
                                AnimatedVisibility(subtitle.text.isNotEmpty()) {
                                    Text(
                                        text = subtitle,
                                        style = MaterialTheme.typography.titleMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            Row {
                                actions.forEach { action ->
                                    IconButton(
                                        icon = action.icon,
                                        contentDescription = action.contentDescription,
                                        onClick = action.onClick,
                                        enabled = action.enabled
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(spacing.medium))
                        }
                    },
                    navigationIcon = {
                        AnimatedContent(
                            targetState = onBackPressed,
                            label = "app-scaffold-icon"
                        ) { onBackPressed ->
                            if (onBackPressed != null) {
                                IconButton(
                                    icon = backStackEntry?.navigationIcon
                                        ?: Icons.AutoMirrored.Rounded.ArrowBack,
                                    contentDescription = null,
                                    onClick = onBackPressed,
                                    modifier = Modifier.wrapContentSize()
                                )
                            }
                        }
                    },
                    modifier = Modifier
                        .hazeChild(hazeState, style = HazeStyle(blurRadius = 6.dp))
                        .fillMaxWidth()
                )
            }
        },
        contentWindowInsets = windowInsets,
        containerColor = Color.Transparent
    ) { padding ->
        Background {
            Box {
                StarBackground()
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

    val tv = tv()
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
            TvIcon(
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
                TvText(
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

internal enum class ScaffoldContent { Navigation, MainContent }
internal enum class ScaffoldRole { SmartPhone, Tablet, Tv }

@Composable
internal fun ScaffoldLayout(
    role: ScaffoldRole,
    navigation: @Composable () -> Unit,
    mainContent: @Composable (PaddingValues) -> Unit
) {
    SubcomposeLayout { constraints ->
        val layoutWidth = constraints.maxWidth
        val layoutHeight = constraints.maxHeight
        val looseConstraints = constraints.copy(minWidth = 0, minHeight = 0)
        val navigationPlaceables = subcompose(
            slotId = ScaffoldContent.Navigation,
            content = navigation
        )
            .fastMap { it.measure(looseConstraints) }
        val navigationWidth = navigationPlaceables.fastMaxOfOrNull { it.width } ?: 0
        val navigationHeight = navigationPlaceables.fastMaxOfOrNull { it.height } ?: 0
        val mainContentPadding = when (role) {
            ScaffoldRole.SmartPhone -> PaddingValues(
                bottom = navigationHeight.toDp()
            )

            else -> PaddingValues()
        }
        val mainContentPlaceables = subcompose(ScaffoldContent.MainContent) {
            mainContent(mainContentPadding)
        }
            .fastMap {
                when (role) {
                    ScaffoldRole.SmartPhone -> it.measure(looseConstraints)
                    else -> {
                        it.measure(
                            looseConstraints.offset(horizontal = -navigationWidth)
                        )
                    }
                }
            }
        layout(layoutWidth, layoutHeight) {
            when (role) {
                ScaffoldRole.SmartPhone -> {
                    mainContentPlaceables.fastForEach {
                        it.place(0, 0)
                    }
                    navigationPlaceables.fastForEach {
                        it.place(0, layoutHeight - navigationHeight)
                    }
                }

                else -> {
                    // rtl
                    navigationPlaceables.fastForEach {
                        it.placeRelative(0, 0)
                    }
                    mainContentPlaceables.fastForEach {
                        it.placeRelative(navigationWidth, 0)
                    }
                }
            }
        }
    }
}
