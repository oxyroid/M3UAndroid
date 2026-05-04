package com.m3u.smartphone.ui

import android.app.ActivityOptions
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.rounded.SettingsRemote
import androidx.compose.material3.ExpandedFullScreenSearchBar
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SearchBarValue
import androidx.compose.material3.Text
import androidx.compose.material3.TopSearchBar
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.rememberSearchBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navOptions
import androidx.paging.PagingData
import androidx.paging.map as pagingMap
import com.m3u.business.playlist.ChannelWithProgramme
import com.m3u.core.foundation.architecture.preferences.PreferencesKeys
import com.m3u.core.foundation.architecture.preferences.preferenceOf
import com.m3u.data.database.model.Channel
import com.m3u.data.service.MediaCommand
import com.m3u.data.tv.model.RemoteDirection
import com.m3u.smartphone.ui.business.channel.PlayerActivity
import com.m3u.smartphone.ui.business.playlist.components.ChannelGallery
import com.m3u.smartphone.ui.common.AppNavHost
import com.m3u.smartphone.ui.common.connect.RemoteControlSheet
import com.m3u.smartphone.ui.common.connect.RemoteControlSheetValue
import com.m3u.smartphone.ui.common.helper.LocalHelper
import com.m3u.smartphone.ui.material.components.Destination
import com.m3u.smartphone.ui.material.components.SnackHost
import com.m3u.smartphone.ui.material.model.LocalSpacing
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
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
        modifier = modifier
    )
}

@Composable
private fun AppImpl(
    navController: NavHostController,
    channels: Flow<PagingData<Channel>>,
    isRemoteControlSheetVisible: Boolean,
    remoteControlSheetValue: RemoteControlSheetValue,
    openRemoteControlSheet: () -> Unit,
    onCode: (String) -> Unit,
    checkTvCodeOnSmartphone: () -> Unit,
    forgetTvCodeOnSmartphone: () -> Unit,
    onRemoteDirection: (RemoteDirection) -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val spacing = LocalSpacing.current
    val helper = LocalHelper.current

    val zappingMode by preferenceOf(PreferencesKeys.ZAPPING_MODE)
    val remoteControl by preferenceOf(PreferencesKeys.REMOTE_CONTROL)

    val entry by navController.currentBackStackEntryAsState()

    val currentDestination by remember {
        derivedStateOf {
            Destination.of(entry?.destination?.route)
        }
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
            val options = ActivityOptions.makeCustomAnimation(
                context,
                0,
                0
            )
            context.startActivity(
                Intent(context, PlayerActivity::class.java),
                options.toBundle()
            )
        }
    }

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            Destination.entries.forEach { destination ->
                val isSelected = destination == currentDestination
                item(
                    icon = {
                        Icon(
                            imageVector = when {
                                isSelected -> destination.selectedIcon
                                else -> destination.unselectedIcon
                            },
                            contentDescription = stringResource(destination.iconTextId)
                        )
                    },
                    label = {
                        Text(stringResource(destination.iconTextId))
                    },
                    selected = isSelected,
                    onClick = { navigateToDestination(destination) },
                    alwaysShowLabel = false
                )
            }
        },
        modifier = modifier
    ) {
        Column {
            val coroutineScope = rememberCoroutineScope()
            val searchBarState = rememberSearchBarState()
            val textFieldState = rememberTextFieldState()
            val inputField = @Composable {
                SearchBarDefaults.InputField(
                    searchBarState = searchBarState,
                    textFieldState = textFieldState,
                    onSearch = { coroutineScope.launch { searchBarState.animateToCollapsed() } },
                    placeholder = { Text("Search...") },
                    leadingIcon = {
                        if (searchBarState.currentValue == SearchBarValue.Expanded) {
                            IconButton(
                                onClick = { coroutineScope.launch { searchBarState.animateToCollapsed() } }
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Default.ArrowBack,
                                    contentDescription = "Back"
                                )
                            }
                        } else {
                            Icon(Icons.Default.Search, contentDescription = null)
                        }
                    },
                    trailingIcon = { Icon(Icons.Default.MoreVert, contentDescription = null) },
                )
            }
            TopSearchBar(
                state = searchBarState,
                inputField = inputField
            )
            ExpandedFullScreenSearchBar(
                inputField = inputField,
                state = searchBarState
            ) {
                BackHandler {
                    coroutineScope.launch {
                        searchBarState.animateToCollapsed()
                    }
                }
                val state = rememberLazyStaggeredGridState()
                val channelsWithProgramme = remember(channels) {
                    channels.map { pagingData ->
                        pagingData.pagingMap { channel ->
                            ChannelWithProgramme(
                                channel = channel,
                                programme = null
                            )
                        }
                    }
                }
                ChannelGallery(
                    state = state,
                    rowCount = 1,
                    channels = channelsWithProgramme,
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
                    contentPadding = WindowInsets.ime.asPaddingValues()
                )
            }
            AppNavHost(
                navController = navController,
                navigateToDestination = { navController.navigate(it.name) },
                navigateToChannel = navigateToChannel,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )
            // snack-host area
            Row(
                horizontalArrangement = Arrangement.spacedBy(spacing.small, Alignment.End),
                verticalAlignment = Alignment.Bottom,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(spacing.medium)
            ) {
                SnackHost(Modifier.weight(1f))
                AnimatedVisibility(
                    visible = remoteControl,
                    enter = scaleIn(initialScale = 0.65f) + fadeIn(),
                    exit = scaleOut(targetScale = 0.65f) + fadeOut()
                ) {
                    FloatingActionButton(
                        elevation = FloatingActionButtonDefaults.elevation(
                            defaultElevation = spacing.none,
                            pressedElevation = spacing.none,
                            focusedElevation = spacing.extraSmall,
                            hoveredElevation = spacing.extraSmall
                        ),
                        onClick = openRemoteControlSheet
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.SettingsRemote,
                            contentDescription = stringResource(com.m3u.i18n.R.string.feat_setting_remote_control)
                        )
                    }
                }
            }

            RemoteControlSheet(
                value = remoteControlSheetValue,
                visible = isRemoteControlSheetVisible,
                onCode = onCode,
                checkTvCodeOnSmartphone = checkTvCodeOnSmartphone,
                forgetTvCodeOnSmartphone = forgetTvCodeOnSmartphone,
                onRemoteDirection = onRemoteDirection,
                onDismissRequest = onDismissRequest
            )
        }
    }
}
