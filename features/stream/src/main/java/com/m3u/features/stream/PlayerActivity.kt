package com.m3u.features.stream

import android.app.PictureInPictureParams
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Rect
import android.os.Bundle
import android.util.Log
import android.view.ViewConfiguration
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.m3u.core.architecture.Logger
import com.m3u.core.architecture.pref.Pref
import com.m3u.core.unspecified.UBoolean
import com.m3u.core.unspecified.specified
import com.m3u.core.util.basic.rational
import com.m3u.core.util.context.isDarkMode
import com.m3u.core.util.context.isPortraitMode
import com.m3u.data.service.PlayerManager
import com.m3u.ui.Action
import com.m3u.ui.Fob
import com.m3u.ui.Helper
import com.m3u.ui.M3ULocalProvider
import com.m3u.ui.OnPipModeChanged
import com.m3u.ui.OnUserLeaveHint
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class PlayerActivity : ComponentActivity() {
    private val controller by lazy {
        WindowInsetsControllerCompat(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
    private var actualOnUserLeaveHint: OnUserLeaveHint? = null
    private var actualOnPipModeChanged: OnPipModeChanged? = null
    private val helper by lazy {
        helper()
    }

    companion object {
        // FIXME: the property is worked only when activity has one instance at most.
        var isInPipMode: Boolean = false
            private set
    }

    @Inject
    lateinit var pref: Pref

    @Inject
    @Logger.Ui
    lateinit var logger: Logger

    @Inject
    lateinit var playerManager: PlayerManager

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_USER
        super.onCreate(savedInstanceState)
        setContent {
            M3ULocalProvider(
                helper = helper,
                pref = pref
            ) {
                StreamRoute(
                    onBackPressed = { finish() }
                )
            }
        }
    }

    private fun helper(): Helper = object : Helper {
        init {
            addOnPictureInPictureModeChangedListener { info ->
                isInPipMode = info.isInPictureInPictureMode
                PlayerActivity.isInPipMode = info.isInPictureInPictureMode
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

        override var title: String = ""
        override var actions: List<Action> = emptyList()
        override var fob: Fob? = null
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
            get() = this@PlayerActivity.requestedOrientation
            set(value) {
                this@PlayerActivity.requestedOrientation = value
            }

        override val windowSizeClass: WindowSizeClass
            @Composable get() = calculateWindowSizeClass(activity = this@PlayerActivity)

        override fun toast(message: String) {
            lifecycleScope.launch(Dispatchers.Main) {
                Toast.makeText(this@PlayerActivity, message, Toast.LENGTH_SHORT).show()
            }
        }

        override fun snake(message: String) {
            logger.log(message)
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

    private fun WindowInsetsControllerCompat.default(@WindowInsetsCompat.Type.InsetsType types: Int) {
        when (types) {
            WindowInsetsCompat.Type.navigationBars() -> {
                val configuration = resources.configuration
                val atBottom =
                    ViewConfiguration.get(this@PlayerActivity).hasPermanentMenuKey()
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
