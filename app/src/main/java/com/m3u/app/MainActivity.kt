package com.m3u.app

import android.app.PictureInPictureParams
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.runtime.DisposableEffect
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.navigation.NavController
import com.google.accompanist.navigation.animation.rememberAnimatedNavController
import com.m3u.app.navigation.Destination
import com.m3u.app.ui.M3UApp
import com.m3u.app.ui.isInDestination
import com.m3u.app.ui.rememberM3UAppState
import com.m3u.ui.M3ULocalProvider
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private var shouldEnterPip: Boolean = false
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            M3ULocalProvider {
                @OptIn(ExperimentalAnimationApi::class)
                val navController = rememberAnimatedNavController()
                DisposableEffect(navController) {
                    val listener = NavController.OnDestinationChangedListener { _, destination, _ ->
                        shouldEnterPip = destination.isInDestination<Destination.Live>()
                    }
                    navController.addOnDestinationChangedListener(listener)
                    onDispose {
                        navController.removeOnDestinationChangedListener(listener)
                    }
                }
                M3UApp(
                    appState = rememberM3UAppState(
                        navController = navController
                    )
                )
            }
        }
    }

    override fun onUserLeaveHint() {
        if (shouldEnterPip) {
            val params = PictureInPictureParams.Builder()
                .build()
            enterPictureInPictureMode(params)
        }
    }
}