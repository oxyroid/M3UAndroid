package com.m3u.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hierarchy
import com.m3u.core.icon.Icon
import com.m3u.navigation.M3UNavHost
import com.m3u.navigation.TopLevelDestination
import com.m3u.ui.components.M3UTopBar
import com.m3u.ui.components.basic.M3UBackground
import com.m3u.ui.components.basic.M3UGradientBackground
import com.m3u.ui.components.basic.M3ULocalProvider
import com.m3u.ui.components.basic.M3UNavigationBar
import com.m3u.ui.local.LocalTheme
import com.m3u.ui.model.GradientColors
import com.m3u.ui.model.LocalGradientColors

@OptIn(
    ExperimentalComposeUiApi::class,
    ExperimentalLayoutApi::class
)
@Composable
fun M3UApp(
    appState: M3UAppState = rememberM3UAppState()
) {
    val shouldShowGradientBackground =
        appState.currentTopLevelDestination == TopLevelDestination.MAIN
    M3ULocalProvider {
        M3UBackground {
            M3UGradientBackground(
                gradientColors = if (shouldShowGradientBackground) {
                    LocalGradientColors.current
                } else {
                    GradientColors()
                }
            ) {
                val snackbarHostState = remember { SnackbarHostState() }
                val isSystemBarVisibility = appState.isSystemBarVisibility
                Scaffold(
                    modifier = Modifier.semantics {
                        testTagsAsResourceId = true
                    },
                    backgroundColor = Color.Transparent,
                    contentColor = LocalTheme.current.onBackground,
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                    bottomBar = {
                    }) { padding ->
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding)
                            .consumeWindowInsets(padding)
                            .windowInsetsPadding(
                                WindowInsets.safeDrawing.only(
                                    WindowInsetsSides.Horizontal
                                )
                            )
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize()
                        ) {
                            val destination = appState.currentTopLevelDestination
                            M3UTopBar(
                                text = destination
                                    ?.titleTextId
                                    ?.let { stringResource(it) }
                                    .orEmpty(),
                                visible = isSystemBarVisibility,
                                actions = {
                                    actions.forEach { action ->
                                        M3UIconButton(
                                            icon = action.icon,
                                            contentDescription = action.contentDescription,
                                            onClick = action.onClick
                                        )
                                    }
                                }
                            ) { padding ->
                                Box(
                                    contentAlignment = Alignment.BottomCenter
                                ) {
                                    val paddingBottom by animateDpAsState(
                                        if (isSystemBarVisibility) 81.dp
                                        else 0.dp
                                    )
                                    val direction = LocalLayoutDirection.current
                                    val leftPadding = padding.calculateLeftPadding(direction)
                                    val topPadding = padding.calculateTopPadding()
                                    val rightPadding = padding.calculateRightPadding(direction)
                                    val bottomPadding =
                                        padding.calculateBottomPadding() + paddingBottom
                                    M3UNavHost(
                                        navController = appState.navController,
                                        navigateToDestination = appState::navigateToDestination,
                                        setAppActions = appState::setActions,
                                        onBackClick = appState::onBackClick,
                                        modifier = Modifier
                                            .padding(
                                                PaddingValues(
                                                    start = leftPadding,
                                                    top = topPadding,
                                                    end = rightPadding,
                                                    bottom = bottomPadding
                                                )
                                            )
                                            .fillMaxSize()
                                    )
                                    M3UBottomBar(
                                        destination = appState.topLevelDestinations,
                                        onNavigateToDestination = appState::navigateToTopLevelDestination,
                                        currentDestination = appState.currentDestination,
                                        modifier = Modifier
                                            .testTag("M3UBottomBar")
                                            .height(paddingBottom)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun M3UBottomBar(
    destination: List<TopLevelDestination>,
    onNavigateToDestination: (TopLevelDestination) -> Unit,
    currentDestination: NavDestination?,
    modifier: Modifier = Modifier
) {
    M3UNavigationBar(
        modifier = modifier,
        containerColor = M3UBottomBarDefaults.navigationBackgroundColor(),
        contentColor = M3UBottomBarDefaults.navigationContentColor(),
        elevation = 0.dp
    ) {
        destination.forEach { destination ->
            val selected = currentDestination.isTopLevelDestinationInHierarchy(destination)
            M3UNavigationBarItem(
                selected = selected,
                onClick = { onNavigateToDestination(destination) },
                tint = M3UBottomBarDefaults.navigationSelectedItemColor(),
                icon = {
                    val icon = if (selected) destination.selectedIcon
                    else destination.unselectedIcon
                    when (icon) {
                        is Icon.ImageVectorIcon -> Icon(
                            imageVector = icon.imageVector,
                            contentDescription = null
                        )
                        is Icon.DrawableResourceIcon -> Icon(
                            painter = painterResource(icon.id),
                            contentDescription = null
                        )
                    }
                }
            )
        }
    }
}


@Composable
private fun M3UNavigationBarItem(
    selected: Boolean,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    label: @Composable (() -> Unit)? = null,
    alwaysShowLabel: Boolean = true,
    tint: Color
) {
    NavigationRailItem(
        selected = selected,
        onClick = onClick,
        icon = icon,
        modifier = modifier,
        enabled = enabled,
        label = label,
        alwaysShowLabel = alwaysShowLabel,
        selectedContentColor = tint,
        interactionSource = remember { MutableInteractionSource() },
    )
}


object M3UBottomBarDefaults {
    @Composable
    fun navigationBackgroundColor() = LocalTheme.current.topBar

    @Composable
    fun navigationContentColor() = LocalTheme.current.onTopBar

    @Composable
    fun navigationSelectedItemColor() = LocalTheme.current.tint

}

private fun NavDestination?.isTopLevelDestinationInHierarchy(destination: TopLevelDestination) =
    this?.hierarchy?.any {
        it.route?.contains(destination.name, true) ?: false
    } ?: false
