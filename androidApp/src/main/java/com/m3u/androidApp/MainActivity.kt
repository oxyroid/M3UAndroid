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
import com.m3u.ui.model.Helper
import com.m3u.ui.model.ScaffoldAction
import com.m3u.ui.model.ScaffoldFob
import dagger.hilt.android.AndroidEntryPoint

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
        setTitle: (String) -> Unit,
        getTitle: () -> String,
        setActions: (List<ScaffoldAction>) -> Unit,
        getActions: () -> List<ScaffoldAction>,
        setFab: (ScaffoldFob?) -> Unit,
        getFab: () -> ScaffoldFob?
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

        override var actions: List<ScaffoldAction>
            get() = getActions()
            set(value) = setActions(value)

        override var fab: ScaffoldFob?
            get() = getFab()
            set(value) = setFab(value)

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