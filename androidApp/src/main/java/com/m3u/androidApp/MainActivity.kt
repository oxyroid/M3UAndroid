package com.m3u.androidApp

import android.app.PictureInPictureParams
import android.graphics.Color
import android.graphics.Rect
import android.os.Bundle
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
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            App(
                connector = this::createHelper
            )
        }
    }

    private var onUserLeaveHintCallback: (() -> Unit)? = null

    private fun createHelper(
        title: Method<String>,
        actions: Method<List<Action>>,
        fob: Method<Fob?>
    ): Helper = object : Helper {
        private val controller = WindowInsetsControllerCompat(window, window.decorView).apply {
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        override fun enterPipMode(size: Rect) {
            val params = PictureInPictureParams.Builder()
                .setAspectRatio(Rational(size.width(), size.height()))
                .build()
            enterPictureInPictureMode(params)
        }

        override var title: String by title
        override var actions: List<Action> by actions
        override var fob: Fob? by fob

        override var systemUiVisibility: Boolean = true
            set(value) {
                field = value
                controller.apply {
                    if (value) {
                        show(WindowInsetsCompat.Type.systemBars())
                    } else {
                        hide(WindowInsetsCompat.Type.systemBars())
                    }
                }
            }

        override fun detectWindowInsetController(handler: WindowInsetsControllerCompat.() -> Unit) {
            handler(controller)
        }

        override fun detectDarkMode(handler: () -> Boolean) {
            val darkMode = handler()
            enableEdgeToEdge(
                SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { !darkMode },
                SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { true }
            )
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

internal typealias Method<E> = KMutableProperty0<E>