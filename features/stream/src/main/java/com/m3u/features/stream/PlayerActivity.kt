package com.m3u.features.stream

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.lifecycleScope
import com.m3u.core.Contracts
import com.m3u.core.architecture.dispatcher.Dispatcher
import com.m3u.core.architecture.dispatcher.M3uDispatchers.Main
import com.m3u.core.architecture.pref.Pref
import com.m3u.data.repository.StreamRepository
import com.m3u.data.service.Messager
import com.m3u.data.service.PlayerManager
import com.m3u.data.service.RemoteDirectionService
import com.m3u.ui.Toolkit
import com.m3u.ui.helper.AbstractHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class PlayerActivity : ComponentActivity() {
    private val viewModel: StreamViewModel by viewModels()
    private val helper by lazy {
        AbstractHelper(
            activity = this,
            mainDispatcher = mainDispatcher,
            messager = messager,
            playerManager = playerManager
        )
    }

    companion object {
        // FIXME: the property is worked only when activity has one instance at most.
        var isInPipMode: Boolean = false
            private set
    }

    @Inject
    lateinit var pref: Pref

    @Inject
    lateinit var playerManager: PlayerManager

    @Inject
    lateinit var streamRepository: StreamRepository

    @Inject
    @Dispatcher(Main)
    lateinit var mainDispatcher: CoroutineDispatcher

    @Inject
    lateinit var messager: Messager

    @Inject
    lateinit var remoteDirectionService: RemoteDirectionService

    private val shortcutStreamUrlLiveData = MutableLiveData<String?>(null)
    private val shortcutRecentlyLiveData = MutableLiveData(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        handleIntent(intent)
        setContent {
            Toolkit(
                helper = helper,
                pref = pref,
                alwaysUseDarkTheme = true,
                actions = remoteDirectionService.actions
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

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        helper.onUserLeaveHint?.invoke()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        helper.applyConfiguration()
    }

    override fun onPause() {
        super.onPause()
        if (isFinishing) {
            viewModel.release()
        }
    }
}
