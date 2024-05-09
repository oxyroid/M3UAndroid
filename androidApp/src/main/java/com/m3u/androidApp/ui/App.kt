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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import com.m3u.androidApp.ui.sheet.RemoteControlSheetValue
import com.m3u.core.architecture.preferences.hiltPreferences
import com.m3u.data.television.model.RemoteDirection
import com.m3u.material.components.Icon
import com.m3u.material.ktx.isTelevision
import com.m3u.material.model.LocalSpacing
import com.m3u.ui.Destination
import com.m3u.ui.FontFamilies
import com.m3u.ui.LocalNavController
import com.m3u.ui.SnackHost

@Composable
fun App(
    modifier: Modifier = Modifier,
    viewModel: AppViewModel = hiltViewModel(),
) {
    val onBackPressedDispatcher = checkNotNull(
        LocalOnBackPressedDispatcherOwner.current
    ).onBackPressedDispatcher

    val navController = rememberNavController()
    val entry by navController.currentBackStackEntryAsState()

    val shouldDispatchBackStack by remember {
        derivedStateOf {
            with(entry) {
                this != null && destination.route == ROOT_ROUTE
            }
        }
    }

    val onBackPressed: (() -> Unit) = {
        onBackPressedDispatcher.onBackPressed()
    }

    val navigateToRootDestination = { rootDestination: Destination.Root ->
        viewModel.rootDestination = rootDestination
        if (!shouldDispatchBackStack) {
            navController.restoreBackStack()
        }
    }

    // for televisions
    val broadcastCodeOnTelevision by viewModel.broadcastCodeOnTelevision.collectAsStateWithLifecycle()

    // for smartphones
    val remoteControlSheetValue by viewModel.remoteControlSheetValue.collectAsStateWithLifecycle()

    CompositionLocalProvider(
        LocalNavController provides navController
    ) {
        AppImpl(
            rootDestination = viewModel.rootDestination,
            onBackPressed = onBackPressed.takeUnless { shouldDispatchBackStack },
            navigateToRoot = navigateToRootDestination,
            openRemoteControlSheet = { viewModel.isConnectSheetVisible = true },
            onCode = { viewModel.code = it },
            checkTelevisionCodeOnSmartphone = viewModel::checkTelevisionCodeOnSmartphone,
            forgetTelevisionCodeOnSmartphone = viewModel::forgetTelevisionCodeOnSmartphone,
            broadcastCodeOnTelevision = broadcastCodeOnTelevision,
            isRemoteControlSheetVisible = viewModel.isConnectSheetVisible,
            remoteControlSheetValue = remoteControlSheetValue,
            onRemoteDirection = viewModel::onRemoteDirection,
            onDismissRequest = {
                viewModel.code = ""
                viewModel.isConnectSheetVisible = false
            },
            modifier = modifier
        )
    }
}

@Composable
private fun AppImpl(
    rootDestination: Destination.Root,
    isRemoteControlSheetVisible: Boolean,
    remoteControlSheetValue: RemoteControlSheetValue,
    broadcastCodeOnTelevision: String?,
    onBackPressed: (() -> Unit)?,
    navigateToRoot: (Destination.Root) -> Unit,
    openRemoteControlSheet: () -> Unit,
    onCode: (String) -> Unit,
    checkTelevisionCodeOnSmartphone: () -> Unit,
    forgetTelevisionCodeOnSmartphone: () -> Unit,
    onRemoteDirection: (RemoteDirection) -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier
) {
    val spacing = LocalSpacing.current
    val preferences = hiltPreferences()

    val tv = isTelevision()

    Scaffold(
        rootDestination = rootDestination,
        onBackPressed = onBackPressed,
        navigateToRoot = navigateToRoot,
        modifier = Modifier
            .fillMaxSize()
            .then(modifier),
    ) { contentPadding ->
        AppNavHost(
            currentDestination = { rootDestination },
            navigateToRoot = navigateToRoot,
            contentPadding = contentPadding,
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
            SnackHost(Modifier.weight(1f))
            AnimatedVisibility(
                visible = !tv && preferences.remoteControl,
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
                    onClick = openRemoteControlSheet
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
            visible = isRemoteControlSheetVisible,
            onCode = onCode,
            checkTelevisionCodeOnSmartphone = checkTelevisionCodeOnSmartphone,
            forgetTelevisionCodeOnSmartphone = forgetTelevisionCodeOnSmartphone,
            onRemoteDirection = onRemoteDirection,
            onDismissRequest = onDismissRequest
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
