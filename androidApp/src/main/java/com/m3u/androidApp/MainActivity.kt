package com.m3u.androidApp

import android.app.PictureInPictureParams
import android.graphics.Rect
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.Color
import androidx.core.app.PictureInPictureModeChangedInfo
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.util.Consumer
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.google.accompanist.navigation.animation.rememberAnimatedNavController
import com.google.accompanist.systemuicontroller.rememberSystemUiController
import com.m3u.androidApp.navigation.Destination
import com.m3u.androidApp.navigation.destinationTo
import com.m3u.androidApp.ui.App
import com.m3u.androidApp.ui.rememberAppState
import com.m3u.ui.model.AppAction
import com.m3u.ui.model.Helper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalFoundationApi::class, ExperimentalAnimationApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            val state = rememberAppState(
                navController = rememberAnimatedNavController()
            )
            val currentDestination = state.currentComposableNavDestination
            val systemUiController = rememberSystemUiController()
            val scope = rememberCoroutineScope()
            val isPlaying = remember(currentDestination) {
                currentDestination destinationTo Destination.Live::class.java ||
                        currentDestination destinationTo Destination.LivePlayList::class.java
            }
            val useDarkIcons = when {
                isPlaying -> false
                else -> !isSystemInDarkTheme()
            }
            DisposableEffect(
                systemUiController,
                useDarkIcons,
                scope,
                isPlaying
            ) {
                scope.launch {
                    if (isPlaying) {
                        delay(800)
                    }
                    systemUiController.setSystemBarsColor(
                        color = Color.Transparent,
                        darkIcons = useDarkIcons
                    )
                }

                onDispose {}
            }
            App(
                appState = state,
                connector = this::createHelper
            )
        }
    }

    private var onUserLeaveHintCallback: (() -> Unit)? = null
    private fun createHelper(
        setTitle: (String) -> Unit,
        getTitle: () -> String,
        setActions: (List<AppAction>) -> Unit,
        getActions: () -> List<AppAction>,
    ): Helper = object : Helper() {

        override fun enterPipMode(size: Rect) {
            val params = PictureInPictureParams.Builder()
                .setAspectRatio(Rational(size.width(), size.height()))
                .build()
            enterPictureInPictureMode(params)
        }

        override var title: String
            get() = getTitle()
            set(value) = setTitle(value)

        override var actions: List<AppAction>
            get() = getActions()
            set(value) = setActions(value)

        override fun hideSystemUI() {
            WindowInsetsControllerCompat(window, window.decorView).let { controller ->
                controller.hide(WindowInsetsCompat.Type.systemBars())
                controller.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }

        override fun showSystemUI() {
            WindowInsetsControllerCompat(window, window.decorView)
                .show(WindowInsetsCompat.Type.systemBars())
        }

        private var listener: Consumer<PictureInPictureModeChangedInfo>? = null

        override fun registerOnPictureInPictureModeChangedListener(
            consumer: Consumer<PictureInPictureModeChangedInfo>
        ) {
            this@MainActivity.addOnPictureInPictureModeChangedListener(consumer)
            this.listener = consumer
        }

        override fun unregisterOnPictureInPictureModeChangedListener() {
            this.listener?.let { removeOnPictureInPictureModeChangedListener(it) }
        }

        override fun registerOnUserLeaveHintListener(callback: () -> Unit) {
            onUserLeaveHintCallback = callback
        }

        override fun unregisterOnUserLeaveHintListener() {
            onUserLeaveHintCallback = null
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        onUserLeaveHintCallback?.invoke()
    }
}