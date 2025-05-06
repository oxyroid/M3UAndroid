package com.m3u.smartphone.ui

import android.app.ActivityOptions
import android.content.Intent
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navOptions
import com.m3u.core.architecture.preferences.PreferencesKeys
import com.m3u.core.architecture.preferences.preferenceOf
import com.m3u.smartphone.ui.business.channel.PlayerActivity
import com.m3u.smartphone.ui.common.AppNavHost
import com.m3u.smartphone.ui.common.Scaffold
import com.m3u.smartphone.ui.material.components.Destination
import com.m3u.smartphone.ui.material.components.SnackHost
import com.m3u.smartphone.ui.material.model.LocalSpacing

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
                this != null && destination.route in Destination.Root.entries.map { it.name }
            }
        }
    }

    val onBackPressed: (() -> Unit) = {
        onBackPressedDispatcher.onBackPressed()
    }

    AppImpl(
        navController = navController,
        onBackPressed = onBackPressed.takeUnless { shouldDispatchBackStack },
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
    onBackPressed: (() -> Unit)?,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val spacing = LocalSpacing.current

    val zappingMode by preferenceOf(PreferencesKeys.ZAPPING_MODE)
    val remoteControl by preferenceOf(PreferencesKeys.REMOTE_CONTROL)

    val entry by navController.currentBackStackEntryAsState()

    val rootDestination by remember {
        derivedStateOf {
            Destination.Root.of(entry?.destination?.route)
        }
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

    Scaffold(
        rootDestination = rootDestination,
        onBackPressed = onBackPressed,
        navigateToChannel = navigateToChannel,
        navigateToRootDestination = {
            navController.navigate(it.name, navOptions {
                popUpTo(it.name) {
                    inclusive = true
                }
            })
        },
        modifier = modifier.fillMaxSize()
    ) { contentPadding ->
        AppNavHost(
            navController = navController,
            navigateToRootDestination = { navController.navigate(it.name) },
            navigateToChannel = navigateToChannel,
            contentPadding = contentPadding,
            modifier = Modifier.fillMaxSize()
        )
        // snack-host area
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
        }
    }
}
