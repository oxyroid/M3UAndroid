package com.m3u.androidApp

import android.app.PictureInPictureParams
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Rect
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
import com.m3u.androidApp.ui.App
import com.m3u.androidApp.ui.AppViewModel
import com.m3u.core.util.basic.rational
import com.m3u.core.util.context.isDarkMode
import com.m3u.core.util.context.isPortraitMode
import com.m3u.ui.model.Action
import com.m3u.ui.model.Fob
import com.m3u.ui.model.Helper
import com.m3u.ui.model.OnPipModeChanged
import com.m3u.ui.model.OnUserLeaveHint
import dagger.hilt.android.AndroidEntryPoint
import kotlin.reflect.KMutableProperty0

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val controller by lazy {
        WindowInsetsControllerCompat(window, window.decorView).apply {
            systemBarsBehavior = BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
    private var actualOnUserLeaveHint: OnUserLeaveHint? = null
    private var actualOnPipModeChanged: OnPipModeChanged? = null
    private val viewModel: AppViewModel by viewModels()
    private val helper by lazy {
        createHelper(
            title = viewModel.title::value,
            actions = viewModel.actions::value,
            fob = viewModel.fob::value
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            App(
                viewModel = viewModel,
                helper = helper
            )
        }
    }

    override fun onResume() {
        super.onResume()
        applyConfiguration(resources.configuration)
    }

    private fun createHelper(
        title: Method<String>,
        actions: Method<List<Action>>,
        fob: Method<Fob?>
    ): Helper = object : Helper {
        override fun enterPipMode(size: Rect) {
            val params = PictureInPictureParams.Builder()
                .setAspectRatio(size.rational)
                .build()
            enterPictureInPictureMode(params)
        }

        override var title: String by title
        override var actions: List<Action> by actions
        override var fob: Fob? by fob
        override var statusBarsVisibility: Boolean = true
            set(value) {
                controller.apply {
                    if (value) {
                        show(WindowInsetsCompat.Type.statusBars())
                    } else {
                        hide(WindowInsetsCompat.Type.statusBars())
                    }
                }
                field = value
            }
        override var navigationBarsVisibility: Boolean = resources.configuration.isPortraitMode
            set(value) {
                controller.apply {
                    if (value) {
                        show(WindowInsetsCompat.Type.navigationBars())
                    } else {
                        hide(WindowInsetsCompat.Type.navigationBars())
                    }
                }
                field = value
            }

        override var darkMode: Boolean = resources.configuration.isDarkMode
            set(value) {
                enableEdgeToEdge(
                    SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { value },
                    SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { true }
                )
                field = value
            }

        override var onUserLeaveHint: OnUserLeaveHint? by ::actualOnUserLeaveHint
        override var onPipModeChanged: OnPipModeChanged?
            get() = actualOnPipModeChanged
            set(value) {
                if (value != null) addOnPictureInPictureModeChangedListener(value)
                else actualOnPipModeChanged?.let { removeOnPictureInPictureModeChangedListener(it) }
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

    // FIXME:
    //  1. orientation changing mistake in player screen when mask is sleeping.
    //  2. window inset controller cannot take effect in orientation changing quickly.
    private fun applyConfiguration(configuration: Configuration) {
        helper.navigationBarsVisibility = configuration.isPortraitMode
    }
}

typealias Method<E> = KMutableProperty0<E>