package com.m3u.ui.helper


import android.app.PictureInPictureParams
import android.content.Context
import android.graphics.Color
import android.graphics.Rect
import android.provider.Settings
import android.view.ViewConfiguration
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.m3u.core.unspecified.UBoolean
import com.m3u.core.unspecified.specified
import com.m3u.core.util.basic.rational
import com.m3u.core.util.context.isDarkMode
import com.m3u.core.util.context.isPortraitMode
import com.m3u.core.wrapper.Message
import com.m3u.data.service.MediaCommand
import com.m3u.data.service.Messager
import com.m3u.data.service.PlayerManager
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class AbstractHelper(
    private val mainDispatcher: CoroutineDispatcher,
    private val playerManager: PlayerManager,
    private val activity: ComponentActivity,
    private val messager: Messager,
    title: MutableState<String> = mutableStateOf(""),
    actions: MutableState<List<Action>> = mutableStateOf(emptyList()),
    fob: MutableState<Fob?> = mutableStateOf(null),
    override val message: StateFlow<Message> = MutableStateFlow(Message.Dynamic.EMPTY)
) : Helper {
    private val controller by lazy {
        WindowInsetsControllerCompat(activity.window, activity.window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    override var title: String by title
    override var actions: List<Action> by actions
    override var fob: Fob? by fob

    init {
        activity.addOnPictureInPictureModeChangedListener { info ->
            isInPipMode = info.isInPictureInPictureMode
        }
    }

    override fun enterPipMode(size: Rect) {
        val params = PictureInPictureParams.Builder()
            .setAspectRatio(size.rational)
            .build()
        if (activity.isInPictureInPictureMode) {
            activity.setPictureInPictureParams(params)
        } else {
            activity.enterPictureInPictureMode(params)
        }
    }

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

    override var isSystemBarUseDarkMode: UBoolean = UBoolean.Unspecified
        set(value) {
            field = value
            val isDark = value.specified ?: activity.resources.configuration.isDarkMode
            activity.enableEdgeToEdge(
                if (isDark) SystemBarStyle.dark(Color.TRANSPARENT)
                else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
                if (isDark) SystemBarStyle.dark(Color.TRANSPARENT)
                else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
            )
        }

    override var onUserLeaveHint: OnUserLeaveHint? = null
    override var onPipModeChanged: OnPipModeChanged? = null
        set(value) {
            if (value != null) activity.addOnPictureInPictureModeChangedListener(value)
            else field?.let {
                activity.removeOnPictureInPictureModeChangedListener(it)
            }
        }

    override var brightness: Float
        get() = Settings.System.getInt(
            activity.contentResolver,
            Settings.System.SCREEN_BRIGHTNESS
        ) / 255f
        set(value) {
            activity.window.attributes = activity.window.attributes.apply {
                screenBrightness = value
            }
        }

    override var isInPipMode: Boolean = false

    override var screenOrientation: Int
        get() = activity.requestedOrientation
        set(value) {
            activity.requestedOrientation = value
        }

    override val windowSizeClass: WindowSizeClass
        @Composable get() = calculateWindowSizeClass(activity)

    override val activityContext: Context
        get() = activity

    override fun toast(message: String) {
        activity.lifecycleScope.launch(mainDispatcher) {
            Toast.makeText(activity, message, Toast.LENGTH_SHORT).show()
        }
    }

    override fun snack(message: String) {
        messager.emit(message)
    }

    override suspend fun play(mediaCommand: MediaCommand) {
        playerManager.play(mediaCommand)
    }

    override suspend fun replay() {
        playerManager.replay()
    }

    fun applyConfiguration() {
        controller.apply {
            when (navigationBarVisibility) {
                UBoolean.True -> show(WindowInsetsCompat.Type.navigationBars())
                UBoolean.False -> hide(WindowInsetsCompat.Type.navigationBars())
                UBoolean.Unspecified -> default(WindowInsetsCompat.Type.navigationBars())
            }
            when (statusBarVisibility) {
                UBoolean.True -> show(WindowInsetsCompat.Type.statusBars())
                UBoolean.False -> hide(WindowInsetsCompat.Type.statusBars())
                UBoolean.Unspecified -> default(WindowInsetsCompat.Type.statusBars())
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