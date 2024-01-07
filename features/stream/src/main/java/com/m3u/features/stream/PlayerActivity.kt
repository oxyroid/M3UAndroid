package com.m3u.features.stream

import android.app.PictureInPictureParams
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
import com.m3u.data.repository.StreamRepository
import com.m3u.data.service.PlayerService
import com.m3u.ui.Action
import com.m3u.ui.Fob
import com.m3u.ui.Helper
import com.m3u.ui.M3ULocalProvider
import com.m3u.ui.OnPipModeChanged
import com.m3u.ui.OnUserLeaveHint
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.time.Duration

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
    @Logger.Ui
    lateinit var logger: Logger

    @Inject
    lateinit var playerService: PlayerService

    @Inject
    lateinit var streamRepository: StreamRepository

    private val shortcutStreamUrlLiveData = MutableLiveData<String?>(null)
    private val shortcutRecentlyLiveData = MutableLiveData(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        handleIntent(intent)
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
        shortcutStreamUrlLiveData.observe(this) { url ->
            if (!url.isNullOrEmpty()) {
                helper.play(url)
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
        shortcutStreamUrlLiveData.value =
            intent.getStringExtra(Contracts.PLAYER_SHORTCUT_STREAM_URL)
        shortcutRecentlyLiveData.value =
            intent.getBooleanExtra(Contracts.PLAYER_SHORTCUT_STREAM_RECENTLY, false)
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

        override fun log(message: Message) {
            logger.log(
                text = when {
                    message.level == Message.LEVEL_EMPTY -> ""
                    message is Message.Static -> {
                        val args = message.formatArgs.flatMap {
                            if (it is Array<*>) it.toList()
                            else listOf(it)
                        }
                        getString(message.resId, *args.toTypedArray())
                    }

                    message is Message.Dynamic -> message.value
                    else -> ""
                },
                level = message.level,
                tag = message.tag,
                duration = when {
                    message.level == Message.LEVEL_EMPTY -> Duration.ZERO
                    else -> message.duration
                }
            )
        }

        override fun play(url: String) {
            playerService.play(url)
        }

        override fun replay() {
            playerService.replay()
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
}
