package com.m3u.feature.channel

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
import com.m3u.data.database.model.isSeries
import com.m3u.data.repository.playlist.PlaylistRepository
import com.m3u.data.repository.channel.ChannelRepository
import com.m3u.data.service.MediaCommand
import com.m3u.ui.Events.enableDPadReaction
import com.m3u.ui.Toolkit
import com.m3u.ui.helper.Helper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class PlayerActivity : ComponentActivity() {
    private val viewModel: ChannelViewModel by viewModels()

    private val helper: Helper = Helper(this)

    companion object {
        // FIXME: the property is worked only when activity has one instance at most.
        var isInPipMode: Boolean = false
            private set
    }

    @Inject
    lateinit var channelRepository: ChannelRepository

    @Inject
    lateinit var playlistRepository: PlaylistRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        enableDPadReaction()
        super.onCreate(savedInstanceState)
        handleIntent(intent)
        setContent {
            Toolkit(
                helper = helper,
                alwaysUseDarkTheme = true
            ) {
                ChannelRoute(
                    viewModel = viewModel
                )
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
                playlist?.isSeries ?: false -> {}
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
