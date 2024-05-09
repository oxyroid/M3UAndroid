package com.m3u.ui.helper

import android.app.PictureInPictureParams
import android.content.Context
import android.graphics.Color
import android.graphics.Rect
import android.provider.Settings
import android.view.ViewConfiguration
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.core.app.PictureInPictureModeChangedInfo
import androidx.core.util.Consumer
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.m3u.core.util.basic.rational
import com.m3u.core.util.context.isDarkMode
import com.m3u.core.util.context.isPortraitMode
import com.m3u.data.service.MediaCommand
import com.m3u.data.service.PlayerManager
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

typealias OnPipModeChanged = Consumer<PictureInPictureModeChangedInfo>

@Stable
class Helper(private val activity: ComponentActivity) {
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface HelperEntryPoint {
        val playerManager: PlayerManager
    }

    private val controller by lazy {
        WindowInsetsControllerCompat(
            activity.window,
            activity.window.decorView
        ).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    private val entryPoint by lazy {
        EntryPointAccessors.fromApplication<HelperEntryPoint>(activity.applicationContext)
    }
    private val playerManager by lazy { entryPoint.playerManager }

    var statusBarVisibility: Boolean? = null
        set(value) {
            field = value
            applyConfiguration()
        }
    var navigationBarVisibility: Boolean? = null
        set(value) {
            field = value
            applyConfiguration()
        }
    var onPipModeChanged: OnPipModeChanged? = null
        set(value) {
            if (value != null) activity.addOnPictureInPictureModeChangedListener(value)
            else field?.let {
                activity.removeOnPictureInPictureModeChangedListener(it)
            }
        }
    var isSystemBarUseDarkMode: Boolean? = null
        set(value) {
            field = value
            val isDark = value ?: activity.resources.configuration.isDarkMode
            activity.enableEdgeToEdge(
                if (isDark) SystemBarStyle.dark(Color.TRANSPARENT)
                else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
                if (isDark) SystemBarStyle.dark(Color.TRANSPARENT)
                else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
            )
        }
    var brightness: Float
        get() = Settings.System.getInt(
            activity.contentResolver,
            Settings.System.SCREEN_BRIGHTNESS
        ) / 255f
        set(value) {
            activity.window.attributes = activity.window.attributes.apply {
                screenBrightness = value
            }
        }
    var screenOrientation: Int
        get() = activity.requestedOrientation
        set(value) {
            activity.requestedOrientation = value
        }
    val activityContext: Context get() = activity

    val windowSizeClass: WindowSizeClass
        @Composable get() = calculateWindowSizeClass(activity)

    fun enterPipMode(size: Rect) {
        val params = PictureInPictureParams.Builder()
            .setAspectRatio(size.rational)
            .build()
        if (activity.isInPictureInPictureMode) {
            activity.setPictureInPictureParams(params)
        } else {
            activity.enterPictureInPictureMode(params)
        }
    }

    suspend fun play(mediaCommand: MediaCommand) {
        playerManager.play(mediaCommand)
    }

    suspend fun replay() {
        playerManager.replay()
    }

    fun applyConfiguration() {
        controller.apply {
            when (navigationBarVisibility) {
                true -> show(WindowInsetsCompat.Type.navigationBars())
                false -> hide(WindowInsetsCompat.Type.navigationBars())
                null -> default(WindowInsetsCompat.Type.navigationBars())
            }
            when (statusBarVisibility) {
                true -> show(WindowInsetsCompat.Type.statusBars())
                false -> hide(WindowInsetsCompat.Type.statusBars())
                null -> default(WindowInsetsCompat.Type.statusBars())
            }
        }
    }

    private fun WindowInsetsControllerCompat.default(@WindowInsetsCompat.Type.InsetsType types: Int) {
        when (types) {
            WindowInsetsCompat.Type.navigationBars() -> {
                val configuration = activity.resources.configuration
                val atBottom = ViewConfiguration
                    .get(activity)
                    .hasPermanentMenuKey()
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

val Helper.useRailNav: Boolean
    @Composable get() = windowSizeClass.widthSizeClass > WindowWidthSizeClass.Compact

val LocalHelper = staticCompositionLocalOf<Helper> { error("Please provide helper.") }
