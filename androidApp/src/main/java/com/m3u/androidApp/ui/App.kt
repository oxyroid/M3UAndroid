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
import androidx.compose.material3.rememberModalBottomSheetState
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
    snackHostState: SnackHostState = rememberSnackHostState()
) {
    val spacing = LocalSpacing.current
    val pref = LocalPref.current
    val onBackPressedDispatcherOwner = LocalOnBackPressedDispatcherOwner.current

    val navController = rememberNavController()
    val entry by navController.currentBackStackEntryAsState()

    val isInRootDestination by remember {
        derivedStateOf {
            entry?.destination?.route?.startsWith(ROOT_ROUTE) ?: false
        }
    }
    val actualRootDestination by remember {
        derivedStateOf {
            if (isInRootDestination) viewModel.rootDestination
            else null
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
    val connectBottomSheetValue by viewModel.connectBottomSheetValue.collectAsStateWithLifecycle()
    val searching by remember {
        derivedStateOf {
            with(connectBottomSheetValue) {
                this is ConnectBottomSheetValue.Prepare && searching
            }
        }
    }
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { !searching }
    )

    AppScaffold(
        title = title,
        actions = actions,
        rootDestination = actualRootDestination,
        fob = fob,
        onBackPressed = onBackPressed,
        navigateToRoot = navigateToRootDestination,
        modifier = Modifier
            .fillMaxSize()
            .then(modifier),
    ) { contentPadding ->
        AppNavHost(
            root = actualRootDestination,
            navigateToRoot = navigateToRootDestination,
            contentPadding = contentPadding,
            modifier = Modifier
                .fillMaxSize(),
            navController = navController
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(spacing.small, Alignment.End),
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(contentPadding)
                .padding(spacing.medium)
        ) {
            SnackHost(
                state = snackHostState,
                modifier = Modifier.weight(1f)
            )
            AnimatedVisibility(
                visible = !tv && pref.remoteControl,
                enter = scaleIn(initialScale = 0.65f) + fadeIn(),
                exit = scaleOut(targetScale = 0.65f) + fadeOut(),
            ) {
                FloatingActionButton(
                    elevation = FloatingActionButtonDefaults.elevation(spacing.none),
                    onClick = { viewModel.isConnectSheetVisible = true }
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SettingsRemote,
                        contentDescription = "remote control"
                    )
                }
            }
        }

        ConnectBottomSheet(
            message = snackHostState.message,
            sheetState = sheetState,
            visible = viewModel.isConnectSheetVisible,
            value = connectBottomSheetValue,
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
