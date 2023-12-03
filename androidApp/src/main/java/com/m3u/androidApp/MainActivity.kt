@file:SuppressLint("UseHelperIssue")

package com.m3u.androidApp

import android.annotation.SuppressLint
import android.app.PictureInPictureParams
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Rect
import android.os.Bundle
import android.os.Parcelable
import android.util.Log
import android.view.ViewConfiguration
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsCompat.Type.InsetsType
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.m3u.androidApp.ui.App
import com.m3u.androidApp.ui.AppViewModel
import com.m3u.androidApp.ui.rememberAppState
import com.m3u.core.architecture.Logger
import com.m3u.core.unspecified.UBoolean
import com.m3u.core.util.basic.rational
import com.m3u.core.util.context.isDarkMode
import com.m3u.core.util.context.isPortraitMode
import com.m3u.features.live.LiveEvent
import com.m3u.features.live.LiveRoute
import com.m3u.ui.Action
import com.m3u.ui.Destination
import com.m3u.ui.Fob
import com.m3u.ui.Helper
import com.m3u.ui.M3ULocalProvider
import com.m3u.ui.OnPipModeChanged
import com.m3u.ui.OnUserLeaveHint
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize
import javax.inject.Inject
import kotlin.reflect.KMutableProperty0

@Parcelize
sealed class ComposeLaunchMode : Parcelable {
    data object Application : ComposeLaunchMode()
    data class Player(val destination: Destination.Live) : ComposeLaunchMode()
}

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
        helper(
            title = viewModel.title::value,
            actions = viewModel.actions::value,
            fob = viewModel.fob::value
        )
    }

    @Inject
    @Logger.Ui
    lateinit var logger: Logger

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val launchMode = getComposeLaunchMode()
        setContent {
            val appState = rememberAppState(
                pagerState = rememberPagerState { Destination.Root.entries.size }
            )
            when (launchMode) {
                ComposeLaunchMode.Application -> {
                    App(
                        appState = appState,
                        viewModel = viewModel,
                        helper = helper
                    )
                }

                is ComposeLaunchMode.Player -> {
                    val state by viewModel.state.collectAsStateWithLifecycle()
                    M3ULocalProvider(
                        helper = helper,
                        useDynamicColors = state.useDynamicColors
                    ) {
                        LiveRoute(
                            init = LiveEvent.InitOne(launchMode.destination.id),
                            onBackPressed = { finish() }
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        applyConfiguration()
    }

    private fun helper(
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
        override var statusBarVisibility: UBoolean = UBoolean.Unspecified
            set(value) {
                field = value
                applyConfiguration()
            }
        override var navigationBarVisibility: UBoolean = UBoolean.Unspecified
            set(value) {
                field = value
                applyConfiguration()
            }

        override var darkMode: Boolean = resources.configuration.isDarkMode
            set(value) {
                field = value
                enableEdgeToEdge(
                    SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { value },
                    SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { true }
                )
            }

        override var onUserLeaveHint: OnUserLeaveHint? by ::actualOnUserLeaveHint
        override var onPipModeChanged: OnPipModeChanged?
            get() = actualOnPipModeChanged
            set(value) {
                if (value != null) addOnPictureInPictureModeChangedListener(value)
                else actualOnPipModeChanged?.let {
                    removeOnPictureInPictureModeChangedListener(it)
                }
            }

        override var brightness: Float
            get() = window.attributes.screenBrightness
            set(value) {
                Log.e("TAG", "helper: $value")
                window.attributes = window.attributes.apply {
                    screenBrightness = value
                }
            }

        override val windowSizeClass: WindowSizeClass
            @Composable get() = calculateWindowSizeClass(activity = this@MainActivity)

        override fun toast(message: String) {
            lifecycleScope.launch(Dispatchers.Main) {
                Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show()
            }
        }

        override fun snake(message: String) {
            logger.log(message)
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        actualOnUserLeaveHint?.invoke()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        applyConfiguration()
    }

    private fun applyConfiguration() {
        val navigationBarsVisibility = helper.navigationBarVisibility
        val statusBarsVisibility = helper.statusBarVisibility

        controller.apply {
            when (navigationBarsVisibility) {
                UBoolean.True -> show(WindowInsetsCompat.Type.navigationBars())
                UBoolean.False -> hide(WindowInsetsCompat.Type.navigationBars())
                UBoolean.Unspecified -> default(WindowInsetsCompat.Type.navigationBars())
            }
            when (statusBarsVisibility) {
                UBoolean.True -> show(WindowInsetsCompat.Type.statusBars())
                UBoolean.False -> hide(WindowInsetsCompat.Type.statusBars())
                UBoolean.Unspecified -> default(WindowInsetsCompat.Type.statusBars())
            }
        }
    }

    private fun WindowInsetsControllerCompat.default(@InsetsType types: Int) {
        when (types) {
            WindowInsetsCompat.Type.navigationBars() -> {
                val configuration = resources.configuration
                val atBottom =
                    ViewConfiguration.get(this@MainActivity).hasPermanentMenuKey()
                if (configuration.isPortraitMode || !atBottom) {
                    show(WindowInsetsCompat.Type.navigationBars())
                } else {
                    hide(WindowInsetsCompat.Type.navigationBars())
                }
            }

            WindowInsetsCompat.Type.statusBars() -> {
                show(WindowInsetsCompat.Type.statusBars())
            }

            else -> {}
        }
    }

    private fun getComposeLaunchMode(): ComposeLaunchMode {
        return try {
            @Suppress("DEPRECATION")
            checkNotNull(intent.getParcelableExtra(COMPOSE_LAUNCH_MODE))
        } catch (e: Exception) {
            e.printStackTrace()
            ComposeLaunchMode.Application
        }
    }

    companion object {
        const val COMPOSE_LAUNCH_MODE = "compose-launch-mode"
    }
}

typealias Method<E> = KMutableProperty0<E>