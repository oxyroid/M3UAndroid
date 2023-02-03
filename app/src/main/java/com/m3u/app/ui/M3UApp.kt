package com.m3u.app.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.Icon
import androidx.compose.material.NavigationRailItem
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
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
import com.m3u.app.navigation.Destination
import com.m3u.app.navigation.M3UNavHost
import com.m3u.app.navigation.TopLevelDestination
import com.m3u.features.live.navigation.liveRoute
import com.m3u.features.subscription.navigation.subscriptionRoute
import com.m3u.ui.components.M3UBackground
import com.m3u.ui.components.M3UIconButton
import com.m3u.ui.components.M3UNavigationBar
import com.m3u.ui.components.M3UTopBar
import com.m3u.ui.model.Icon
import com.m3u.ui.model.LocalTheme

@OptIn(
    ExperimentalComposeUiApi::class,
    ExperimentalLayoutApi::class
)
@Composable
fun M3UApp(
    appState: M3UAppState = rememberM3UAppState()
) {
    M3UBackground {
        val isSystemBarVisibility = appState.isSystemBarVisibility
        Scaffold(
            modifier = Modifier.semantics {
                testTagsAsResourceId = true
            },
            backgroundColor = Color.Transparent,
            contentColor = LocalTheme.current.onBackground,
            bottomBar = {}
        ) { padding ->
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
                    val topLevelLabel = appState.currentTopLevelDestination
                        ?.titleTextId
                        ?.let { stringResource(it) }
                    val label by appState.label
                    val actions by appState.appActions

                    val text by remember(topLevelLabel) {
                        derivedStateOf {
                            topLevelLabel ?: label.orEmpty()
                        }
                    }
                    M3UTopBar(
                        text = text,
                        visible = !appState.currentNavDestination.isInDestination<Destination.Live>(),
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
                                setAppActions = appState.setAppActions,
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
                                currentNavDestination = appState.currentNavDestination,
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

@Composable
private fun M3UBottomBar(
    destination: List<TopLevelDestination>,
    onNavigateToDestination: (TopLevelDestination) -> Unit,
    currentNavDestination: NavDestination?,
    modifier: Modifier = Modifier
) {
    M3UNavigationBar(
        modifier = modifier,
        containerColor = M3UBottomBarDefaults.navigationBackgroundColor(),
        contentColor = M3UBottomBarDefaults.navigationContentColor(),
        elevation = 0.dp
    ) {
        destination.forEach { destination ->
            val selected = currentNavDestination.isTopLevelDestinationInHierarchy(destination)
            M3UNavigationBarItem(
                alwaysShowLabel = false,
                selected = selected,
                onClick = { onNavigateToDestination(destination) },
                tint = M3UBottomBarDefaults.navigationSelectedItemColor(),
                icon = {
                    val icon = if (selected) destination.selectedIcon
                    else destination.unselectedIcon
                    when (icon) {
                        is Icon.ImageVectorIcon -> Icon(
                            imageVector = icon.imageVector,
                            contentDescription = stringResource(destination.iconTextId)
                        )

                        is Icon.DrawableResourceIcon -> Icon(
                            painter = painterResource(icon.id),
                            contentDescription = stringResource(destination.iconTextId)
                        )
                    }
                },
                label = {
                    Text(text = stringResource(destination.iconTextId))
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

@Composable
private inline fun <reified D : Destination> NavDestination?.isInDestination(): Boolean {
    val targetRoute = when (D::class.java.name) {
        Destination.Live::class.java.name -> liveRoute
        Destination.Subscription::class.java.name -> subscriptionRoute
        else -> return false
    }
    return (this?.route?.startsWith(targetRoute, ignoreCase = true) ?: false)
}