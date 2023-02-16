package com.m3u.app

import android.app.PictureInPictureParams
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.navigation.NavController
import com.google.accompanist.navigation.animation.rememberAnimatedNavController
import com.m3u.app.navigation.Destination
import com.m3u.app.ui.App
import com.m3u.app.ui.isInDestination
import com.m3u.app.ui.rememberAppState
import com.m3u.ui.M3ULocalProvider
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val labelState = mutableStateOf("")
    private val playerRectState = mutableStateOf(Rect())
    private var isInLiveDestination: Boolean = false
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
                        isInLiveDestination = destination.isInDestination<Destination.Live>()
                    }
                    navController.addOnDestinationChangedListener(listener)
                    onDispose {
                        navController.removeOnDestinationChangedListener(listener)
                    }
                }
                App(
                    appState = rememberAppState(
                        navController = navController,
                        label = labelState,
                        playerRect = playerRectState
                    )
                )
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(SAVED_LABEL, labelState.value)
        outState.putParcelable(SAVED_RECT, playerRectState.value)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        labelState.value = savedInstanceState.getString(SAVED_LABEL, "")
        @Suppress("DEPRECATION")
        playerRectState.value = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            savedInstanceState.getParcelable(SAVED_RECT, Rect::class.java) ?: Rect()
        } else savedInstanceState.getParcelable(SAVED_RECT) ?: Rect()
    }

    override fun onUserLeaveHint() {
        val shouldEnterPipMode = isInLiveDestination && isRatioValidated
        if (shouldEnterPipMode) {
            val params = PictureInPictureParams.Builder()
                .setAspectRatio(Rational(rect.width(), rect.height()))
                .build()
            enterPictureInPictureMode(params)
        }
    }

    private val rect: Rect get() = playerRectState.value
    private val isRatioValidated: Boolean get() = !rect.isEmpty

    companion object {
        private const val SAVED_LABEL = "saved:label"
        private const val SAVED_RECT = "saved:rect"
    }
}