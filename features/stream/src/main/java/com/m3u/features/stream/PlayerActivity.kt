package com.m3u.features.stream

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import com.m3u.core.Contracts
import com.m3u.core.architecture.dispatcher.Dispatcher
import com.m3u.core.architecture.dispatcher.M3uDispatchers.Main
import com.m3u.core.architecture.preferences.Preferences
import com.m3u.data.database.model.Playlist
import com.m3u.data.database.model.type
import com.m3u.data.repository.playlist.PlaylistRepository
import com.m3u.data.repository.stream.StreamRepository
import com.m3u.data.service.MediaCommand
import com.m3u.data.service.Messager
import com.m3u.data.service.PlayerManager
import com.m3u.data.service.RemoteDirectionService
import com.m3u.ui.EventBus.registerActionEventCollector
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
    lateinit var preferences: Preferences

    @Inject
    lateinit var playerManager: PlayerManager

    @Inject
    lateinit var streamRepository: StreamRepository

    @Inject
    lateinit var playlistRepository: PlaylistRepository

    @Inject
    @Dispatcher(Main)
    lateinit var mainDispatcher: CoroutineDispatcher

    @Inject
    lateinit var messager: Messager

    @Inject
    lateinit var remoteDirectionService: RemoteDirectionService

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        handleIntent(intent)
        setContent {
            Toolkit(
                helper = helper,
                preferences = preferences,
                alwaysUseDarkTheme = true
            ) {
                StreamRoute(
                    onBackPressed = { finish() },
                    viewModel = viewModel
                )
            }
        }
        registerActionEventCollector(remoteDirectionService.actions)
        addOnPictureInPictureModeChangedListener {
            if (!it.isInPictureInPictureMode && lifecycle.currentState !in arrayOf(
                    Lifecycle.State.RESUMED,
                    Lifecycle.State.STARTED
                )
            ) {
                viewModel.destroy()
            }
        }
    }

    private fun playFromShortcuts(streamId: Int) {
        lifecycleScope.launch {
            val stream = streamRepository.get(streamId) ?: return@launch
            val playlist = playlistRepository.get(stream.playlistUrl)
            when {
                // series can not be played from shortcuts
                playlist?.type in Playlist.SERIES_TYPES -> {}
                else -> {
                    helper.play(MediaCommand.Live(stream.id))
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        val shortcutStreamId = intent
            .getIntExtra(Contracts.PLAYER_SHORTCUT_STREAM_ID, -1)
            .takeIf { it != -1 }
        val recently =
            intent.getBooleanExtra(Contracts.PLAYER_SHORTCUT_STREAM_RECENTLY, false)

        shortcutStreamId?.let { playFromShortcuts(it) }
        if (recently) {
            lifecycleScope.launch {
                val stream = streamRepository.getPlayedRecently() ?: return@launch
                playFromShortcuts(stream.id)
            }
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        helper.onUserLeaveHint?.invoke()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        helper.applyConfiguration()
    }

    override fun onResume() {
        super.onResume()
        viewModel.pauseOrContinue(true)
    }

    override fun onPause() {
        super.onPause()
        if (!isInPictureInPictureMode) {
            viewModel.pauseOrContinue(false)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        viewModel.destroy()
    }
}
