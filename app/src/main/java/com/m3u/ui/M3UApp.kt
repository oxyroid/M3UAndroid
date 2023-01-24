package com.m3u.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hierarchy
import com.m3u.core.icon.Icon
import com.m3u.navigation.M3UNavHost
import com.m3u.navigation.TopLevelDestination
import com.m3u.ui.components.M3UTopBar
import com.m3u.ui.components.basic.*
import com.m3u.ui.local.LocalTheme
import com.m3u.ui.model.GradientColors
import com.m3u.ui.model.LocalGradientColors

@OptIn(ExperimentalComposeUiApi::class, ExperimentalLayoutApi::class)
@Composable
@Preview
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
                Scaffold(
                    modifier = Modifier.semantics {
                        testTagsAsResourceId = true
                    },
                    backgroundColor = Color.Transparent,
                    contentColor = LocalTheme.current.onBackground,
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                    bottomBar = {
                        M3UBottomBar(
                            destination = appState.topLevelDestinations,
                            onNavigateToDestination = appState::navigateToTopLevelDestination,
                            currentDestination = appState.currentDestination,
                            modifier = Modifier.testTag("M3UBottomBar")
                        )
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
                                actions = {
                                    IconButton(
                                        onClick = { /*TODO*/ }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.AccountCircle,
                                            contentDescription = null
                                        )
                                    }
                                }
                            ) { padding ->
                                M3UNavHost(
                                    navController = appState.navController,
                                    onBackClick = appState::onBackClick,
                                    modifier = Modifier.padding(padding)
                                )
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
        contentColor = M3UBottomBarDefaults.navigationContentColor(),
        elevation = 0.dp
    ) {
        destination.forEach { destination ->
            val selected = currentDestination.isTopLevelDestinationInHierarchy(destination)
            M3UNavigationBarItem(
                selected = selected,
                onClick = { onNavigateToDestination(destination) },
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

object M3UBottomBarDefaults {
    @Composable
    fun navigationContentColor() = LocalTheme.current.topBar

    @Composable
    fun navigationSelectedItemColor() = LocalTheme.current.onTopBar

    @Composable
    fun navigationIndicatorColor() = LocalTheme.current.onTopBar
}

private fun NavDestination?.isTopLevelDestinationInHierarchy(destination: TopLevelDestination) =
    this?.hierarchy?.any {
        it.route?.contains(destination.name, true) ?: false
    } ?: false
