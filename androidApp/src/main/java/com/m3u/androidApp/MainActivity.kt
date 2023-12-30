@file:SuppressLint("UseHelperIssue")

package com.m3u.androidApp

import android.annotation.SuppressLint
import android.app.PictureInPictureParams
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Rect
import android.os.Bundle
import android.util.Log
import android.view.ViewConfiguration
import android.widget.Toast
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsCompat.Type.InsetsType
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
import androidx.lifecycle.lifecycleScope
import com.m3u.androidApp.ui.App
import com.m3u.androidApp.ui.AppViewModel
import com.m3u.androidApp.ui.rememberAppState
import com.m3u.core.architecture.logger.Logger
import com.m3u.core.architecture.pref.Pref
import com.m3u.core.unspecified.UBoolean
import com.m3u.core.unspecified.specified
import com.m3u.core.unspecified.unspecifiable
import com.m3u.core.util.basic.rational
import com.m3u.core.util.context.isDarkMode
import com.m3u.core.util.context.isPortraitMode
import com.m3u.core.wrapper.Message
import com.m3u.data.service.PlayerManager
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
import javax.inject.Inject
import kotlin.reflect.KMutableProperty0

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
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
    lateinit var pref: Pref

    @Inject
    @Logger.Ui
    lateinit var logger: Logger

    @Inject
    lateinit var playerManager: PlayerManager

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val pagerState =
                rememberPagerState(pref.rootDestination) { Destination.Root.entries.size }
            val state = rememberAppState(
                pagerState = pagerState
            )
            val scope = rememberCoroutineScope()
            val darkMode = when {
                pref.cinemaMode -> true
                else -> isSystemInDarkTheme()
            }
            DisposableEffect(darkMode, scope) {
                scope.launch {
                    helper.darkMode = darkMode.unspecifiable
                }
                onDispose {}
            }
            M3ULocalProvider(
                helper = helper,
                pref = pref
            ) {
                App(
                    state = state,
                    viewModel = viewModel,
                    helper = helper
                )
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
        init {
            addOnPictureInPictureModeChangedListener { info ->
                isInPipMode = info.isInPictureInPictureMode
            }
        }

        override fun enterPipMode(size: Rect) {
            val params = PictureInPictureParams.Builder()
                .setAspectRatio(size.rational)
                .build()
            if (isInPictureInPictureMode) {
                setPictureInPictureParams(params)
            } else {
                enterPictureInPictureMode(params)
            }
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

        override var darkMode: UBoolean = UBoolean.Unspecified
            set(value) {
                field = value
                enableEdgeToEdge(
                    SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) {
                        value.specified ?: resources.configuration.isDarkMode
                    },
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

        override var isInPipMode: Boolean = false

        override var screenOrientation: Int
            get() = this@MainActivity.requestedOrientation
            set(value) {
                this@MainActivity.requestedOrientation = value
            }

        override val windowSizeClass: WindowSizeClass
            @Composable get() = calculateWindowSizeClass(activity = this@MainActivity)

        override fun toast(message: String) {
            lifecycleScope.launch(Dispatchers.Main) {
                Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show()
            }
        }

        override fun log(message: Message) {
            if (message == Message.Static || message == Message.Dynamic.EMPTY) return
            when (message) {
                is Message.Static -> {
                    logger.log(
                        text = getString(message.resId, message.formatArgs),
                        level = message.level,
                        tag = message.tag
                    )
                }

                is Message.Dynamic -> {
                    logger.log(
                        text = message.value,
                        tag = message.tag,
                        level = message.level
                    )
                }
            }
        }

        override fun play(url: String) {
            playerManager.play(url)
        }

        override fun replay() {
            playerManager.replay()
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
}

private typealias Method<E> = KMutableProperty0<E>