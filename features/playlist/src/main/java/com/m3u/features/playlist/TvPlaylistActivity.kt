package com.m3u.features.playlist

import android.app.ActivityOptions
import android.content.ComponentName
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Rect
import android.os.Bundle
import android.view.ViewConfiguration
import android.widget.Toast
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.m3u.core.Contracts
import com.m3u.core.architecture.logger.Logger
import com.m3u.core.architecture.pref.Pref
import com.m3u.core.unspecified.UBoolean
import com.m3u.core.unspecified.specified
import com.m3u.core.util.context.isDarkMode
import com.m3u.core.util.context.isPortraitMode
import com.m3u.core.wrapper.Message
import com.m3u.data.service.MessageService
import com.m3u.data.service.PlayerService
import com.m3u.material.model.LocalSpacing
import com.m3u.ui.Action
import com.m3u.ui.AppLocalProvider
import com.m3u.ui.AppSnackHost
import com.m3u.ui.Fob
import com.m3u.ui.Helper
import com.m3u.ui.OnPipModeChanged
import com.m3u.ui.OnUserLeaveHint
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.time.Duration

@AndroidEntryPoint
class TvPlaylistActivity : AppCompatActivity() {
    private val controller by lazy {
        WindowInsetsControllerCompat(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
    private val helper by lazy { helper() }

    @Inject
    lateinit var pref: Pref

    @Inject
    @Logger.Ui
    lateinit var logger: Logger

    @Inject
    lateinit var playerService: PlayerService

    @Inject
    lateinit var messageService: MessageService

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            AppLocalProvider(
                helper = helper,
                pref = pref
            ) {
                val spacing = LocalSpacing.current
                val message by messageService.message.collectAsStateWithLifecycle()
                Box {
                    PlaylistRoute(
                        navigateToStream = {
                            val options = ActivityOptions.makeCustomAnimation(
                                this@TvPlaylistActivity,
                                0,
                                0
                            )
                            startActivity(
                                Intent().apply {
                                    component = ComponentName.createRelative(
                                        this@TvPlaylistActivity,
                                        Contracts.PLAYER_ACTIVITY
                                    )
                                },
                                options.toBundle()
                            )
                        }
                    )
                    AppSnackHost(
                        message = message,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(spacing.medium)
                    )
                }
            }
        }
    }

    private fun helper(): Helper = object : Helper {
        override fun enterPipMode(size: Rect) {
            throw UnsupportedOperationException("TvPlaylistActivity not support PIP mode")
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

        override val isInPipMode: Boolean = false

        override var screenOrientation: Int
            get() = this@TvPlaylistActivity.requestedOrientation
            set(value) {
                this@TvPlaylistActivity.requestedOrientation = value
            }

        override val message: StateFlow<Message> = messageService.message

        override val windowSizeClass: WindowSizeClass
            @Composable get() = calculateWindowSizeClass(activity = this@TvPlaylistActivity)

        override fun toast(message: String) {
            lifecycleScope.launch(Dispatchers.Main) {
                Toast.makeText(this@TvPlaylistActivity, message, Toast.LENGTH_SHORT).show()
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
                duration = when (message.level) {
                    Message.LEVEL_EMPTY -> Duration.ZERO
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
                    ViewConfiguration.get(this@TvPlaylistActivity).hasPermanentMenuKey()
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