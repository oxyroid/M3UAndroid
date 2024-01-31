package com.m3u.androidApp.ui

import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.m3u.androidApp.ui.scaffold.AppScaffold
import com.m3u.ui.Destination
import com.m3u.ui.helper.LocalHelper

@Composable
fun App(
    modifier: Modifier = Modifier,
    viewModel: AppViewModel = hiltViewModel(),
) {
    val helper = LocalHelper.current
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

    val title: String by viewModel.title.collectAsStateWithLifecycle()
    val actions by viewModel.actions.collectAsStateWithLifecycle()
    val message by helper.message.collectAsStateWithLifecycle()
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

    AppScaffold(
        title = title,
        message = message,
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
            modifier = Modifier.fillMaxSize(),
            navController = navController
        )
    }
}
