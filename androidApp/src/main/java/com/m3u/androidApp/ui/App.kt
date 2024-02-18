package com.m3u.androidApp.ui

import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SettingsRemote
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.m3u.androidApp.ui.sheet.RemoteControlSheet
import com.m3u.core.architecture.pref.LocalPref
import com.m3u.material.ktx.isTelevision
import com.m3u.material.model.LocalSpacing
import com.m3u.ui.Destination
import com.m3u.ui.FontFamilies
import com.m3u.ui.SnackHost
import com.m3u.ui.SnackHostState
import com.m3u.ui.rememberSnackHostState

@Composable
fun App(
    modifier: Modifier = Modifier,
    viewModel: AppViewModel = hiltViewModel(),
    hostState: SnackHostState = rememberSnackHostState()
) {
    val spacing = LocalSpacing.current
    val pref = LocalPref.current
    val onBackPressedDispatcherOwner = LocalOnBackPressedDispatcherOwner.current

    val navController = rememberNavController()
    val entry by navController.currentBackStackEntryAsState()

    val root by remember {
        derivedStateOf {
            viewModel.rootDestination.takeIf {
                entry?.destination?.route?.startsWith(ROOT_ROUTE) == true
            }
        }
    }

    val tv = isTelevision()

    val title: String by viewModel.title.collectAsStateWithLifecycle()
    val actions by viewModel.actions.collectAsStateWithLifecycle()
    val fob by viewModel.fob.collectAsStateWithLifecycle()
    val deep by viewModel.deep.collectAsStateWithLifecycle()

    val onBackPressed: (() -> Unit)? = {
        onBackPressedDispatcherOwner?.onBackPressedDispatcher?.onBackPressed()
        Unit
    }.takeIf { deep > 0 }

    val navigateToRootDestination = { rootDestination: Destination.Root ->
        viewModel.rootDestination = rootDestination
        navController.popBackStackToRoot()
    }

    // for televisions
    val broadcastCodeOnTelevision by viewModel.broadcastCodeOnTelevision.collectAsStateWithLifecycle()

    // for smartphones
    val remoteControlSheetValue by viewModel.remoteControlSheetValue.collectAsStateWithLifecycle()

    AppScaffold(
        title = title,
        actions = actions,
        rootDestination = root,
        fob = fob,
        onBackPressed = onBackPressed,
        navigateToRoot = navigateToRootDestination,
        modifier = Modifier
            .fillMaxSize()
            .then(modifier),
    ) { contentPadding ->
        AppNavHost(
            root = root,
            navigateToRoot = navigateToRootDestination,
            contentPadding = contentPadding,
            navController = navController,
            modifier = Modifier.fillMaxSize()
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(spacing.small, Alignment.End),
            verticalAlignment = Alignment.Bottom,
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(contentPadding)
                .padding(spacing.medium)
        ) {
            SnackHost(
                state = hostState,
                modifier = Modifier.weight(1f)
            )
            AnimatedVisibility(
                visible = !tv && pref.remoteControl,
                enter = scaleIn(initialScale = 0.65f) + fadeIn(),
                exit = scaleOut(targetScale = 0.65f) + fadeOut(),
            ) {
                FloatingActionButton(
                    elevation = FloatingActionButtonDefaults.elevation(
                        defaultElevation = spacing.none,
                        pressedElevation = spacing.none,
                        focusedElevation = spacing.extraSmall,
                        hoveredElevation = spacing.extraSmall
                    ),
                    onClick = { viewModel.isConnectSheetVisible = true }
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SettingsRemote,
                        contentDescription = "remote control"
                    )
                }
            }
        }

        RemoteControlSheet(
            value = remoteControlSheetValue,
            visible = viewModel.isConnectSheetVisible,
            onCode = { viewModel.code = it },
            checkTelevisionCodeOnSmartphone = viewModel::checkTelevisionCodeOnSmartphone,
            forgetTelevisionCodeOnSmartphone = viewModel::forgetTelevisionCodeOnSmartphone,
            onRemoteDirection = viewModel::onRemoteDirection,
            message = hostState.message,
            onDismissRequest = {
                viewModel.code = ""
                viewModel.isConnectSheetVisible = false
            }
        )

        Crossfade(
            targetState = broadcastCodeOnTelevision,
            label = "broadcast-code-on-television",
            modifier = Modifier
                .padding(spacing.medium)
                .align(Alignment.BottomEnd)
        ) { code ->
            if (code != null) {
                Text(
                    text = code,
                    style = MaterialTheme.typography.headlineMedium,
                    fontFamily = FontFamilies.JetbrainsMono
                )
            }
        }
    }
}
