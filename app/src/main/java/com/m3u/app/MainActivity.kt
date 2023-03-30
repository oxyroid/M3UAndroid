package com.m3u.app

import android.app.PictureInPictureParams
import android.graphics.Rect
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isUnspecified
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.google.accompanist.systemuicontroller.rememberSystemUiController
import com.m3u.app.navigation.Destination
import com.m3u.app.ui.App
import com.m3u.app.ui.isInDestination
import com.m3u.app.ui.rememberAppState
import com.m3u.ui.M3ULocalProvider
import com.m3u.ui.model.AppAction
import com.m3u.ui.model.Helper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val title = MutableStateFlow("")
    private val actions = MutableStateFlow(emptyList<AppAction>())
    @OptIn(ExperimentalFoundationApi::class)
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
                    actions = actions
                )

                val systemUiController = rememberSystemUiController()
                val systemBarsColor =
                    if (state.currentNavDestination.isInDestination<Destination.Live>()) Color.Black
                    else Color.Unspecified
                val useDarkIcons = !isSystemInDarkTheme()

                DisposableEffect(systemUiController, useDarkIcons, systemBarsColor) {
                    if (systemBarsColor.isUnspecified) {
                        systemUiController.setSystemBarsColor(
                            color = Color.Transparent,
                            darkIcons = useDarkIcons
                        )
                    } else {
                        systemUiController.setSystemBarsColor(
                            color = systemBarsColor
                        )
                    }
                    onDispose {}
                }
                App(state)
            }
        }
    }


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
            WindowInsetsControllerCompat(
                window,
                window.decorView
            ).show(WindowInsetsCompat.Type.systemBars())
        }
    }
}