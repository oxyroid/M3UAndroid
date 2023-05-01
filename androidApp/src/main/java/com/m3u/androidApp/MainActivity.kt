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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.Color
import androidx.core.app.PictureInPictureModeChangedInfo
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.util.Consumer
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.google.accompanist.navigation.animation.rememberAnimatedNavController
import com.google.accompanist.systemuicontroller.rememberSystemUiController
import com.m3u.androidApp.navigation.Destination
import com.m3u.androidApp.ui.App
import com.m3u.androidApp.ui.isInDestinations
import com.m3u.androidApp.ui.rememberAppState
import com.m3u.ui.M3ULocalProvider
import com.m3u.ui.model.AppAction
import com.m3u.ui.model.Helper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val title = MutableStateFlow("")
    private val actions = MutableStateFlow(emptyList<AppAction>())

    @OptIn(ExperimentalFoundationApi::class, ExperimentalAnimationApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            M3ULocalProvider(
                helper = helper
            ) {
                val state = rememberAppState(
                    title = title,
                    actions = actions,
                    navController = rememberAnimatedNavController()
                )
                val currentDestination = state.currentComposableNavDestination
                val systemUiController = rememberSystemUiController()
                val scope = rememberCoroutineScope()
                val useDarkIcons = when {
                    currentDestination.isInDestinations<Destination.Live, Destination.LivePlayList>() -> false
                    else -> !isSystemInDarkTheme()
                }
                DisposableEffect(systemUiController, useDarkIcons, scope) {
                    scope.launch {
                        if (!useDarkIcons) {
                            delay(800)
                        }
                        systemUiController.setSystemBarsColor(
                            color = Color.Transparent,
                            darkIcons = useDarkIcons
                        )
                    }

                    onDispose {}
                }
                App(state)
            }
        }
    }

    private var onUserLeaveHintCallback: (() -> Unit)? = null
    private val helper: Helper = object : Helper() {

        override fun enterPipMode(size: Rect) {
            val params = PictureInPictureParams.Builder()
                .setAspectRatio(Rational(size.width(), size.height()))
                .build()
            enterPictureInPictureMode(params)
        }

        override var title: String
            get() = this@MainActivity.title.value
            set(value) {
                this@MainActivity.title.value = value
            }

        override fun actions(actions: List<AppAction>) {
            if (this@MainActivity.actions.value == actions) return
            lifecycleScope.launch {
                this@MainActivity.actions.emit(actions)
            }
        }

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