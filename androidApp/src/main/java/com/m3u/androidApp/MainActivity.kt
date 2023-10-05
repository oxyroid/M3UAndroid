package com.m3u.androidApp

import android.app.PictureInPictureParams
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Rect
import android.os.Bundle
import android.util.Log
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.app.PictureInPictureModeChangedInfo
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.util.Consumer
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.m3u.androidApp.ui.App
import com.m3u.ui.model.Action
import com.m3u.ui.model.Fob
import com.m3u.ui.model.Helper
import dagger.hilt.android.AndroidEntryPoint
import kotlin.reflect.KMutableProperty0

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val controller by lazy {
        WindowInsetsControllerCompat(window, window.decorView).apply {
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
    private var actualOnUserLeaveHint: (() -> Unit)? = null
    private var actualOnPipModeChanged: Consumer<PictureInPictureModeChangedInfo>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        applyConfiguration(resources.configuration)
        setContent {
            App(
                connector = this::createHelper
            )
        }
    }

    private fun createHelper(
        title: Method<String>,
        actions: Method<List<Action>>,
        fob: Method<Fob?>
    ): Helper = object : Helper {
        override fun enterPipMode(size: Rect) {
            val params = PictureInPictureParams.Builder()
                .setAspectRatio(Rational(size.width(), size.height()))
                .build()
            enterPictureInPictureMode(params)
        }

        override var title: String by title
        override var actions: List<Action> by actions
        override var fob: Fob? by fob
        override var statusBarsVisibility: Boolean = true
            set(value) {
                field = value
                controller.apply {
                    if (value) {
                        show(WindowInsetsCompat.Type.statusBars())
                    } else {
                        hide(WindowInsetsCompat.Type.statusBars())
                    }
                }
            }
        override var navigationBarsVisibility: Boolean =
            resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT

        override var darkMode: Boolean =
            resources.configuration.uiMode == Configuration.UI_MODE_NIGHT_YES
            set(value) {
                enableEdgeToEdge(
                    SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { !value },
                    SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { true }
                )
                field = value
            }
        override var onUserLeaveHint: (() -> Unit)? by ::actualOnUserLeaveHint
        override var onPipModeChanged: Consumer<PictureInPictureModeChangedInfo>?
            get() = actualOnPipModeChanged
            set(value) {
                if (value != null) addOnPictureInPictureModeChangedListener(value)
                else actualOnPipModeChanged?.let { removeOnPictureInPictureModeChangedListener(it) }
            }

        override fun detectWindowInsetController(handler: WindowInsetsControllerCompat.() -> Unit) {
            handler(controller)
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        actualOnUserLeaveHint?.invoke()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        applyConfiguration(newConfig)
    }

    private fun applyConfiguration(configuration: Configuration) {
        Log.d(
            "MainActivity",
            "applyConfiguration: ${configuration.orientation == Configuration.ORIENTATION_PORTRAIT}"
        )
        when (configuration.orientation) {
            Configuration.ORIENTATION_PORTRAIT -> {
                controller.show(WindowInsetsCompat.Type.navigationBars())
            }

            Configuration.ORIENTATION_LANDSCAPE -> {
                controller.hide(WindowInsetsCompat.Type.navigationBars())
            }

            else -> {}
        }
    }
}

internal typealias Method<E> = KMutableProperty0<E>