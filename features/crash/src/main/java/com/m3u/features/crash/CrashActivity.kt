package com.m3u.features.crash

import android.app.PictureInPictureParams
import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Rect
import android.net.Uri
import android.os.Bundle
import android.view.ViewConfiguration
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts.CreateDocument
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.m3u.core.architecture.dispatcher.Dispatcher
import com.m3u.core.architecture.dispatcher.M3uDispatchers
import com.m3u.core.architecture.pref.Pref
import com.m3u.core.unspecified.UBoolean
import com.m3u.core.unspecified.specified
import com.m3u.core.util.basic.rational
import com.m3u.core.util.context.isDarkMode
import com.m3u.core.util.context.isPortraitMode
import com.m3u.core.util.coroutine.getValue
import com.m3u.core.util.coroutine.setValue
import com.m3u.core.wrapper.Message
import com.m3u.data.service.PlayerManager
import com.m3u.data.service.RemoteDirectionService
import com.m3u.data.television.model.RemoteDirection
import com.m3u.ui.Toolkit
import com.m3u.ui.helper.Action
import com.m3u.ui.helper.Fob
import com.m3u.ui.helper.Helper
import com.m3u.ui.helper.OnPipModeChanged
import com.m3u.ui.helper.OnUserLeaveHint
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

internal typealias CreateDocumentCallback = (Uri?) -> Unit

@AndroidEntryPoint
class CrashActivity : ComponentActivity() {
    private val controller by lazy {
        WindowInsetsControllerCompat(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    @Inject
    lateinit var pref: Pref
    private lateinit var launcher: ActivityResultLauncher<String>

    private val helper by lazy {
        helper(
            title = MutableStateFlow(""),
            actions = MutableStateFlow(persistentListOf()),
            fob = MutableStateFlow(null),
            deep = MutableStateFlow(0)
        )
    }

    @Inject
    lateinit var playerManager: PlayerManager

    @Inject
    lateinit var remoteDirectionService: RemoteDirectionService

    @Inject
    @Dispatcher(M3uDispatchers.Main)
    lateinit var mainDispatcher: CoroutineDispatcher


    override fun onCreate(savedInstanceState: Bundle?) {
        INSTANCE = this
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        launcher = registerForActivityResult(CreateDocument("text/*")) { uri ->
            val iterator = callbacks.iterator()
            while (iterator.hasNext()) {
                val callback = iterator.next()
                callback.invoke(uri)
                iterator.remove()
            }
        }
        setContent {
            Toolkit(
                pref = pref,
                helper = helper
            ) {
                CrashApp()
            }
        }
    }

    private val callbacks = mutableListOf<CreateDocumentCallback>()
    private fun addCallback(callback: CreateDocumentCallback) {
        callbacks += callback
    }

    override fun onDestroy() {
        super.onDestroy()
        INSTANCE = null
    }


    private fun helper(
        title: MutableStateFlow<String>,
        actions: MutableStateFlow<ImmutableList<Action>>,
        fob: MutableStateFlow<Fob?>,
        deep: MutableStateFlow<Int>
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
        override var actions: ImmutableList<Action> by actions
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

        override var deep: Int
            get() = deep.value
            set(value) {
                deep.value = value.coerceAtLeast(0)
            }

        override val message: StateFlow<Message> = MutableStateFlow(Message.Dynamic.EMPTY)

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

        override var isInPipMode: Boolean = false

        override var screenOrientation: Int
            get() = this@CrashActivity.requestedOrientation
            set(value) {
                this@CrashActivity.requestedOrientation = value
            }

        override val windowSizeClass: WindowSizeClass
            @Composable get() = calculateWindowSizeClass(activity = this@CrashActivity)

        override val activityContext: Context
            get() = this@CrashActivity

        override val remoteDirection: SharedFlow<RemoteDirection>
            get() = remoteDirectionService.remoteDirection

        override fun toast(message: String) {
            lifecycleScope.launch(mainDispatcher) {
                Toast.makeText(this@CrashActivity, message, Toast.LENGTH_SHORT).show()
            }
        }

        override fun play(url: String) {
            playerManager.play(url)
        }

        override fun replay() {
            playerManager.replay()
        }
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
                    ViewConfiguration.get(this@CrashActivity).hasPermanentMenuKey()
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

    companion object {
        private var INSTANCE: CrashActivity? = null
        internal fun createDocument(extraTitle: String, callback: CreateDocumentCallback) {
            INSTANCE?.addCallback(callback)
            INSTANCE?.launcher?.launch(extraTitle)
        }
    }
}