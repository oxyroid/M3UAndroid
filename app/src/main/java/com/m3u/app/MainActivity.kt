package com.m3u.app

import android.app.PictureInPictureParams
import android.graphics.Rect
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.m3u.app.ui.App
import com.m3u.app.ui.rememberAppState
import com.m3u.ui.M3ULocalProvider
import com.m3u.ui.model.AppAction
import com.m3u.ui.model.Utils
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity(), Utils {
    private val title = mutableStateOf("")
    private val actions = MutableStateFlow(emptyList<AppAction>())
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            M3ULocalProvider(this) {
                val appState = rememberAppState(
                    title = title,
                    actions = actions
                )
                App(appState)
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(SAVED_LABEL, title.value)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        title.value = savedInstanceState.getString(SAVED_LABEL, "")
    }

    override fun enterPipMode(source: Rect) {
        val params = PictureInPictureParams.Builder()
            .setAspectRatio(Rational(source.width(), source.height()))
            .build()
        enterPictureInPictureMode(params)
    }

    override fun setTitle(title: String) {
        this.title.value = title
    }

    override fun setActions(actions: List<AppAction>) {
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

    companion object {
        private const val SAVED_LABEL = "saved:label"
    }
}