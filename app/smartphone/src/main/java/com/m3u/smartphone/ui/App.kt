package com.m3u.smartphone.ui

import android.app.ActivityOptions
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.rounded.SettingsRemote
import androidx.compose.material3.ExpandedFullScreenSearchBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SearchBarState
import androidx.compose.material3.SearchBarValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopSearchBar
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.material3.rememberSearchBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navOptions
import androidx.paging.PagingData
import com.m3u.business.playlist.ChannelWithProgramme
import com.m3u.core.foundation.architecture.preferences.PreferencesKeys
import com.m3u.core.foundation.architecture.preferences.preferenceOf
import com.m3u.data.service.MediaCommand
import com.m3u.data.tv.model.RemoteDirection
import com.m3u.i18n.R.string
import com.m3u.smartphone.ui.business.channel.PlayerActivity
import com.m3u.smartphone.ui.business.playlist.components.ChannelGallery
import com.m3u.smartphone.ui.common.AppNavHost
import com.m3u.smartphone.ui.common.connect.RemoteControlSheet
import com.m3u.smartphone.ui.common.connect.RemoteControlSheetValue
import com.m3u.smartphone.ui.common.helper.LocalHelper
import com.m3u.smartphone.ui.material.components.Destination
import com.m3u.smartphone.ui.material.components.SnackHost
import com.m3u.smartphone.ui.material.model.LocalSpacing
import com.m3u.smartphone.ui.navigation.AppContentInsets
import com.m3u.smartphone.ui.navigation.AppNavigationMode
import com.m3u.smartphone.ui.navigation.FloatingAppNavigationBar
import com.m3u.smartphone.ui.navigation.calculateContentBottomPadding
import com.m3u.smartphone.ui.navigation.calculateLayoutPadding
import com.m3u.smartphone.ui.navigation.resolveAppNavigationMode
import com.m3u.smartphone.ui.navigation.shouldShowBottomEdgeBlur
import com.m3u.smartphone.ui.navigation.shouldShowBottomNavigation
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

@Composable
fun App(
    modifier: Modifier = Modifier,
    viewModel: AppViewModel = hiltViewModel(),
) {
    val navController = rememberNavController()

    AppImpl(
        navController = navController,
        channels = viewModel.channels,
        onSearchQuery = { query -> viewModel.searchQuery.value = query },
        isRemoteControlSheetVisible = viewModel.isConnectSheetVisible,
        remoteControlSheetValue = viewModel.remoteControlSheetValue,
        openRemoteControlSheet = { viewModel.isConnectSheetVisible = true },
        onCode = { viewModel.code = it },
        checkTvCodeOnSmartphone = viewModel::checkTvCodeOnSmartphone,
        forgetTvCodeOnSmartphone = viewModel::forgetTvCodeOnSmartphone,
        onRemoteDirection = viewModel::onRemoteDirection,
        onDismissRequest = {
            viewModel.code = ""
            viewModel.isConnectSheetVisible = false
        },
        modifier = modifier,
    )
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun AppImpl(
    navController: NavHostController,
    channels: Flow<PagingData<ChannelWithProgramme>>,
    onSearchQuery: (String) -> Unit,
    isRemoteControlSheetVisible: Boolean,
    remoteControlSheetValue: RemoteControlSheetValue,
    openRemoteControlSheet: () -> Unit,
    onCode: (String) -> Unit,
    checkTvCodeOnSmartphone: () -> Unit,
    forgetTvCodeOnSmartphone: () -> Unit,
    onRemoteDirection: (RemoteDirection) -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val spacing = LocalSpacing.current
    val helper = LocalHelper.current
    val coroutineScope = rememberCoroutineScope()
    val searchBarState = rememberSearchBarState()
    val textFieldState = rememberTextFieldState()
    val navigationBackdrop = rememberLayerBackdrop()
    var nestedDetailVisible by remember { mutableStateOf(false) }
    val onNestedDetailVisibilityChanged = remember {
        { visible: Boolean -> nestedDetailVisible = visible }
    }

    val zappingMode by preferenceOf(PreferencesKeys.ZAPPING_MODE)
    val remoteControl by preferenceOf(PreferencesKeys.REMOTE_CONTROL)
    val entry by navController.currentBackStackEntryAsState()
    val currentDestination by remember(entry) {
        derivedStateOf {
            Destination.of(entry?.destination?.route)
        }
    }
    val navigationMode = resolveAppNavigationMode(
        with(density) {
            LocalWindowInfo.current.containerSize.width.toDp()
        },
    )
    val searchActive = searchBarState.currentValue != SearchBarValue.Collapsed ||
        searchBarState.targetValue != SearchBarValue.Collapsed
    val imeVisible = WindowInsets.isImeVisible
    val isTopLevelRoute = currentDestination != null && !nestedDetailVisible
    val showBottomNavigation = shouldShowBottomNavigation(
        mode = navigationMode,
        isTopLevelRoute = isTopLevelRoute,
        isSearchActive = searchActive,
        isImeVisible = imeVisible,
    )
    val remoteControlVisible = remoteControl && !searchActive && !imeVisible

    var measuredNavigationHeight by remember { mutableStateOf(64.dp) }
    val safeDrawingPadding = WindowInsets.safeDrawing.asPaddingValues()
    val safeBottomInset = safeDrawingPadding.calculateBottomPadding()
    val layoutPadding = calculateLayoutPadding(
        mode = navigationMode,
        safeStartInset = safeDrawingPadding.calculateStartPadding(layoutDirection),
        safeEndInset = safeDrawingPadding.calculateEndPadding(layoutDirection),
    )
    val contentBottomPadding = calculateContentBottomPadding(
        mode = navigationMode,
        isTopLevelRoute = showBottomNavigation,
        safeBottomInset = safeBottomInset,
        measuredNavigationHeight = measuredNavigationHeight,
        floatingUtilityHeight = if (remoteControlVisible) {
            REMOTE_CONTROL_FAB_SIZE
        } else {
            0.dp
        },
        regularContentSpacing = spacing.medium,
    )
    val contentInsets = AppContentInsets(
        layoutPadding = layoutPadding,
        contentPadding = PaddingValues(bottom = contentBottomPadding),
        navigationClearance = if (showBottomNavigation) {
            safeBottomInset + FLOATING_NAVIGATION_BOTTOM_GAP + measuredNavigationHeight
        } else {
            safeBottomInset
        },
    )

    LaunchedEffect(textFieldState) {
        snapshotFlow { textFieldState.text.toString() }.collect(onSearchQuery)
    }

    val navigateToDestination = { destination: Destination ->
        navController.navigate(destination.name, navOptions {
            popUpTo(destination.name) {
                inclusive = true
            }
        })
    }
    val navigateToChannel: () -> Unit = {
        if (!zappingMode || !PlayerActivity.isInPipMode) {
            val options = ActivityOptions.makeCustomAnimation(context, 0, 0)
            context.startActivity(
                Intent(context, PlayerActivity::class.java),
                options.toBundle(),
            )
        }
    }

    Box(
        modifier = modifier.fillMaxSize(),
    ) {
        when (navigationMode) {
            AppNavigationMode.BottomOverlay -> {
                Surface(
                    color = MaterialTheme.colorScheme.background,
                    contentColor = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier
                        .fillMaxSize()
                        .layerBackdrop(navigationBackdrop),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(contentInsets.layoutPadding),
                    ) {
                        AppContent(
                            navController = navController,
                            channels = channels,
                            searchBarState = searchBarState,
                            textFieldState = textFieldState,
                            navigateToDestination = { navController.navigate(it.name) },
                            navigateToChannel = navigateToChannel,
                            contentPadding = contentInsets.contentPadding,
                            showBottomEdgeBlur = shouldShowBottomEdgeBlur(navigationMode),
                            onNestedDetailVisibilityChanged = onNestedDetailVisibilityChanged,
                        )
                    }
                }
            }

            AppNavigationMode.SideRail -> {
                NavigationSuiteScaffold(
                    navigationSuiteItems = {
                        Destination.entries.forEach { destination ->
                            val selected = destination == currentDestination
                            item(
                                icon = {
                                    Icon(
                                        imageVector = if (selected) {
                                            destination.selectedIcon
                                        } else {
                                            destination.unselectedIcon
                                        },
                                        contentDescription = stringResource(destination.iconTextId),
                                    )
                                },
                                label = {
                                    Text(stringResource(destination.iconTextId))
                                },
                                selected = selected,
                                onClick = { navigateToDestination(destination) },
                                alwaysShowLabel = false,
                            )
                        }
                    },
                    layoutType = NavigationSuiteType.NavigationRail,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(contentInsets.layoutPadding),
                    ) {
                        AppContent(
                            navController = navController,
                            channels = channels,
                            searchBarState = searchBarState,
                            textFieldState = textFieldState,
                            navigateToDestination = { navController.navigate(it.name) },
                            navigateToChannel = navigateToChannel,
                            contentPadding = contentInsets.contentPadding,
                            showBottomEdgeBlur = shouldShowBottomEdgeBlur(navigationMode),
                            onNestedDetailVisibilityChanged = onNestedDetailVisibilityChanged,
                        )
                        AppUtilityLayer(
                            remoteControlVisible = remoteControlVisible,
                            bottomNavigationVisible = false,
                            navigationClearance = contentInsets.navigationClearance,
                            safeBottomInset = safeBottomInset,
                            imeBottomInset = WindowInsets.ime
                                .asPaddingValues()
                                .calculateBottomPadding(),
                            onOpenRemoteControlSheet = openRemoteControlSheet,
                            modifier = Modifier.align(Alignment.BottomCenter),
                        )
                    }
                }
            }
        }

        if (navigationMode == AppNavigationMode.BottomOverlay) {
            AppUtilityLayer(
                remoteControlVisible = remoteControlVisible,
                bottomNavigationVisible = showBottomNavigation,
                navigationClearance = contentInsets.navigationClearance,
                safeBottomInset = safeBottomInset,
                imeBottomInset = WindowInsets.ime.asPaddingValues().calculateBottomPadding(),
                onOpenRemoteControlSheet = openRemoteControlSheet,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(contentInsets.layoutPadding),
            )
        }

        if (navigationMode == AppNavigationMode.BottomOverlay) {
            AnimatedVisibility(
                visible = showBottomNavigation,
                enter = slideInVertically(initialOffsetY = { it / 3 }) +
                    fadeIn() +
                    scaleIn(initialScale = 0.94f),
                exit = slideOutVertically(targetOffsetY = { it / 3 }) +
                    fadeOut() +
                    scaleOut(targetScale = 0.94f),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(
                        start = safeDrawingPadding.calculateStartPadding(layoutDirection),
                        end = safeDrawingPadding.calculateEndPadding(layoutDirection),
                        bottom = safeBottomInset + FLOATING_NAVIGATION_BOTTOM_GAP,
                    ),
            ) {
                FloatingAppNavigationBar(
                    selectedDestination = currentDestination,
                    backdrop = navigationBackdrop,
                    onDestinationSelected = navigateToDestination,
                    onHeightChanged = { measuredNavigationHeight = it },
                )
            }
        }

        RemoteControlSheet(
            value = remoteControlSheetValue,
            visible = isRemoteControlSheetVisible,
            onCode = onCode,
            checkTvCodeOnSmartphone = checkTvCodeOnSmartphone,
            forgetTvCodeOnSmartphone = forgetTvCodeOnSmartphone,
            onRemoteDirection = onRemoteDirection,
            onDismissRequest = onDismissRequest,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppContent(
    navController: NavHostController,
    channels: Flow<PagingData<ChannelWithProgramme>>,
    searchBarState: SearchBarState,
    textFieldState: TextFieldState,
    navigateToDestination: (Destination) -> Unit,
    navigateToChannel: () -> Unit,
    contentPadding: PaddingValues,
    showBottomEdgeBlur: Boolean,
    onNestedDetailVisibilityChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val helper = LocalHelper.current
    val coroutineScope = rememberCoroutineScope()
    val inputField = @Composable {
        SearchBarDefaults.InputField(
            searchBarState = searchBarState,
            textFieldState = textFieldState,
            onSearch = { coroutineScope.launch { searchBarState.animateToCollapsed() } },
            placeholder = { Text(stringResource(string.ui_search_placeholder)) },
            leadingIcon = {
                if (searchBarState.currentValue == SearchBarValue.Expanded) {
                    IconButton(
                        onClick = {
                            coroutineScope.launch {
                                searchBarState.animateToCollapsed()
                            }
                        },
                    ) {
                        Icon(
                            Icons.AutoMirrored.Default.ArrowBack,
                            contentDescription = stringResource(
                                string.ui_cd_top_bar_on_back_pressed,
                            ),
                        )
                    }
                } else {
                    Icon(Icons.Default.Search, contentDescription = null)
                }
            },
            trailingIcon = {
                Icon(Icons.Default.MoreVert, contentDescription = null)
            },
        )
    }

    Column(modifier = modifier.fillMaxSize()) {
        TopSearchBar(
            state = searchBarState,
            inputField = inputField,
            windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top),
        )
        ExpandedFullScreenSearchBar(
            inputField = inputField,
            state = searchBarState,
        ) {
            BackHandler {
                coroutineScope.launch {
                    searchBarState.animateToCollapsed()
                }
            }
            val state = rememberLazyStaggeredGridState()
            ChannelGallery(
                state = state,
                rowCount = 1,
                channels = channels,
                zapping = null,
                recently = false,
                isVodOrSeriesPlaylist = false,
                onClick = { channel ->
                    coroutineScope.launch {
                        helper.play(MediaCommand.Common(channel.id))
                        navigateToChannel()
                    }
                },
                onLongClick = {},
                reloadThumbnail = { null },
                syncThumbnail = { null },
                contentPadding = WindowInsets.ime.asPaddingValues(),
            )
        }
        AppNavHost(
            navController = navController,
            navigateToDestination = navigateToDestination,
            navigateToChannel = navigateToChannel,
            contentPadding = contentPadding,
            showBottomEdgeBlur = showBottomEdgeBlur,
            onNestedDetailVisibilityChanged = onNestedDetailVisibilityChanged,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        )
    }
}

@Composable
private fun AppUtilityLayer(
    remoteControlVisible: Boolean,
    bottomNavigationVisible: Boolean,
    navigationClearance: Dp,
    safeBottomInset: Dp,
    imeBottomInset: Dp,
    onOpenRemoteControlSheet: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalSpacing.current
    val targetBottomPadding = if (bottomNavigationVisible) {
        navigationClearance + FLOATING_NAVIGATION_CONTENT_GAP
    } else {
        maxOf(safeBottomInset, imeBottomInset) + spacing.medium
    }
    val bottomPadding by animateDpAsState(
        targetValue = targetBottomPadding,
        label = "app-utility-bottom-padding",
    )

    Row(
        horizontalArrangement = Arrangement.spacedBy(spacing.small, Alignment.End),
        verticalAlignment = Alignment.Bottom,
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = spacing.medium,
                end = spacing.medium,
                bottom = bottomPadding,
            ),
    ) {
        SnackHost(Modifier.weight(1f))
        AnimatedVisibility(
            visible = remoteControlVisible,
            enter = scaleIn(initialScale = 0.65f) + fadeIn(),
            exit = scaleOut(targetScale = 0.65f) + fadeOut(),
        ) {
            FloatingActionButton(
                elevation = FloatingActionButtonDefaults.elevation(
                    defaultElevation = spacing.none,
                    pressedElevation = spacing.none,
                    focusedElevation = spacing.extraSmall,
                    hoveredElevation = spacing.extraSmall,
                ),
                onClick = onOpenRemoteControlSheet,
            ) {
                Icon(
                    imageVector = Icons.Rounded.SettingsRemote,
                    contentDescription = stringResource(string.feat_setting_remote_control),
                )
            }
        }
    }
}

private val FLOATING_NAVIGATION_BOTTOM_GAP = 12.dp
private val FLOATING_NAVIGATION_CONTENT_GAP = 12.dp
private val REMOTE_CONTROL_FAB_SIZE = 56.dp
