package com.m3u.smartphone.ui.business.channel

import android.content.Intent
import android.content.res.Configuration
import android.graphics.Rect
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import com.m3u.business.channel.ChannelViewModel
import com.m3u.core.Contracts
import com.m3u.core.architecture.preferences.PreferencesKeys
import com.m3u.core.architecture.preferences.Settings
import com.m3u.core.architecture.preferences.flowOf
import com.m3u.data.database.model.isSeries
import com.m3u.data.repository.channel.ChannelRepository
import com.m3u.data.repository.playlist.PlaylistRepository
import com.m3u.data.service.MediaCommand
import com.m3u.smartphone.ui.common.helper.Helper
import com.m3u.smartphone.ui.common.internal.Toolkit
import com.m3u.smartphone.ui.material.components.Background
import com.m3u.smartphone.ui.material.model.LocalHazeState
import dagger.hilt.android.AndroidEntryPoint
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class PlayerActivity : ComponentActivity() {
    private val viewModel: ChannelViewModel by viewModels()

    private val helper: Helper = Helper(this)
    private var backgroundPlayback = true
    private var autoPipOnHome = false

    companion object {
        // FIXME: the property is worked only when activity has one instance at most.
        var isInPipMode: Boolean = false
            private set
    }

    @Inject
    lateinit var channelRepository: ChannelRepository

    @Inject
    lateinit var playlistRepository: PlaylistRepository

    @Inject
    lateinit var settings: Settings

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        lifecycleScope.launch {
            settings.flowOf(PreferencesKeys.BACKGROUND_PLAYBACK).collect { enabled ->
                backgroundPlayback = enabled
            }
        }
        lifecycleScope.launch {
            settings.flowOf(PreferencesKeys.AUTO_PIP_ON_HOME).collect { enabled ->
                autoPipOnHome = enabled
            }
        }
        handleIntent(intent)
        setContent {
            Toolkit(
                helper = helper,
                alwaysUseDarkTheme = true
            ) {
                val hazeState = remember { HazeState() }
                CompositionLocalProvider(LocalHazeState provides hazeState) {
                    Background(
                        color = Color.Black
                    ) {
                        ChannelRoute(
                            viewModel = viewModel
                        )
                    }
                }
            }
        }
        addOnPictureInPictureModeChangedListener {
            isInPipMode = it.isInPictureInPictureMode
            if (!it.isInPictureInPictureMode && lifecycle.currentState !in arrayOf(
                    Lifecycle.State.RESUMED,
                    Lifecycle.State.STARTED
                )
            ) {
                viewModel.destroy()
            }
        }
    }

    private fun playFromShortcuts(channelId: Int) {
        lifecycleScope.launch {
            val channel = channelRepository.get(channelId) ?: return@launch
            val playlist = playlistRepository.get(channel.playlistUrl)
            when {
                // series can not be played from shortcuts
                playlist?.isSeries == true -> {}
                else -> {
                    helper.play(MediaCommand.Common(channel.id))
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        val shortcutChannelId = intent
            .getIntExtra(Contracts.PLAYER_SHORTCUT_CHANNEL_ID, -1)
            .takeIf { it != -1 }
        val recently =
            intent.getBooleanExtra(Contracts.PLAYER_SHORTCUT_CHANNEL_RECENTLY, false)

        shortcutChannelId?.let { playFromShortcuts(it) }
        if (recently) {
            lifecycleScope.launch {
                val channel = channelRepository.getPlayedRecently() ?: return@launch
                playFromShortcuts(channel.id)
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        helper.applyConfiguration()
    }

    override fun onPause() {
        super.onPause()
        val player = viewModel.playerState.value.player
        if (isInPictureInPictureMode || player == null) {
            return
        }
        if (backgroundPlayback) {
            val intent = Intent(this, PlaybackService::class.java)
            if (player.playWhenReady) {
                ContextCompat.startForegroundService(this, intent)
            } else {
                startService(intent)
            }
        } else {
            viewModel.pauseOrContinue(false)
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (!autoPipOnHome || isInPictureInPictureMode) return

        val playerState = viewModel.playerState.value
        if (playerState.player == null) return

        val videoSize = playerState.videoSize
        val pipSize = if (videoSize.width() > 0 && videoSize.height() > 0) {
            videoSize
        } else {
            Rect(0, 0, 1920, 1080)
        }
        runCatching {
            helper.enterPipMode(pipSize)
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        return when (event.keyCode) {
            KeyEvent.KEYCODE_CHANNEL_UP,
            KeyEvent.KEYCODE_PAGE_UP,
            KeyEvent.KEYCODE_MEDIA_NEXT -> {
                if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                    viewModel.getNextChannel()
                }
                true
            }

            KeyEvent.KEYCODE_CHANNEL_DOWN,
            KeyEvent.KEYCODE_PAGE_DOWN,
            KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                    viewModel.getPreviousChannel()
                }
                true
            }

            else -> super.dispatchKeyEvent(event)
        }
    }

    override fun onResume() {
        super.onResume()
        stopService(Intent(this, PlaybackService::class.java))
        if (!backgroundPlayback) {
            viewModel.pauseOrContinue(true)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing && !isChangingConfigurations) {
            viewModel.destroy()
        }
    }
}
