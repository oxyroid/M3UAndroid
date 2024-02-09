package com.m3u.features.stream

import android.app.PictureInPictureParams
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Rect
import android.os.Bundle
import android.view.ViewConfiguration
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.lifecycleScope
import com.m3u.core.Contracts
import com.m3u.core.architecture.logger.Logger
import com.m3u.core.architecture.pref.Pref
import com.m3u.core.unspecified.UBoolean
import com.m3u.core.unspecified.specified
import com.m3u.core.util.basic.rational
import com.m3u.core.util.context.isDarkMode
import com.m3u.core.util.context.isPortraitMode
import com.m3u.core.wrapper.Message
import com.m3u.data.local.service.MessageManager
import com.m3u.data.local.service.PlayerManager
import com.m3u.data.repository.StreamRepository
import com.m3u.ui.Toolkit
import com.m3u.ui.helper.Action
import com.m3u.ui.helper.Fob
import com.m3u.ui.helper.Helper
import com.m3u.ui.helper.OnPipModeChanged
import com.m3u.ui.helper.OnUserLeaveHint
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class PlayerActivity : ComponentActivity() {
    private val controller by lazy {
        WindowInsetsControllerCompat(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
    private val helper by lazy { helper() }

    companion object {
        // FIXME: the property is worked only when activity has one instance at most.
        var isInPipMode: Boolean = false
            private set
    }

    @Inject
    lateinit var pref: Pref

    @Inject
    @Logger.Message
    lateinit var messager: Logger

    @Inject
    lateinit var logger: Logger

    @Inject
    lateinit var playerManager: PlayerManager

    @Inject
    lateinit var messageManager: MessageManager

    @Inject
    lateinit var streamRepository: StreamRepository

    private val shortcutStreamUrlLiveData = MutableLiveData<String?>(null)
    private val shortcutRecentlyLiveData = MutableLiveData(false)

    private val viewModel: StreamViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        handleIntent(intent)
        setContent {
            Toolkit(
                helper = helper,
                pref = pref
            ) {
                StreamRoute(
                    onBackPressed = { finish() },
                    viewModel = viewModel
                )
            }
        }
        shortcutStreamUrlLiveData.observe(this) { streamId ->
            if (streamId != null) {
                helper.play(streamId)
            }
        }
        shortcutRecentlyLiveData.observe(this) { recently ->
            if (recently) {
                lifecycleScope.launch {
                    val stream = streamRepository.getPlayedRecently() ?: return@launch
                    helper.play(stream.url)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (intent != null) {
            handleIntent(intent)
        }
    }

    private fun handleIntent(intent: Intent) {
        shortcutStreamUrlLiveData.value = intent
            .getStringExtra(Contracts.PLAYER_SHORTCUT_STREAM_URL)
        shortcutRecentlyLiveData.value = intent
            .getBooleanExtra(Contracts.PLAYER_SHORTCUT_STREAM_RECENTLY, false)
    }

    private fun helper(): Helper = object : Helper {
        init {
            addOnPictureInPictureModeChangedListener { info ->
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
        override var actions: ImmutableList<Action> = persistentListOf()
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

        override val message: StateFlow<Message> = messageManager.message

        override var deep: Int = 0

        override val activityContext: Context
            get() = this@PlayerActivity

        override var darkMode: UBoolean = UBoolean.Unspecified
            set(value) {
                field = value
                val isDark = value.specified ?: resources.configuration.isDarkMode
                enableEdgeToEdge(
                    if (isDark) SystemBarStyle.dark(Color.TRANSPARENT)
                    else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
                    if (isDark) SystemBarStyle.dark(Color.TRANSPARENT)
                    else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                )
            }

        override var onUserLeaveHint: OnUserLeaveHint? = null
        override var onPipModeChanged: OnPipModeChanged? = null
            set(value) {
                if (value != null) addOnPictureInPictureModeChangedListener(value)
                else field?.let {
                    removeOnPictureInPictureModeChangedListener(it)
                }
            }

        override var brightness: Float
            get() = window.attributes.screenBrightness
            set(value) {
                window.attributes = window.attributes.apply {
                    screenBrightness = value
                }
            }

        override val isInPipMode: Boolean get() = PlayerActivity.isInPipMode

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

        override fun play(url: String) {
            playerManager.play(url)
        }

        override fun replay() {
            playerManager.replay()
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        helper.onUserLeaveHint?.invoke()
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

    override fun onPause() {
        super.onPause()
        if (isFinishing) {
            viewModel.release()
            logger.log("destroy")
        }
    }
}
