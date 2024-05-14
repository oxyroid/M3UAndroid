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
import com.m3u.data.database.model.isSeries
import com.m3u.data.repository.playlist.PlaylistRepository
import com.m3u.data.repository.stream.StreamRepository
import com.m3u.data.service.MediaCommand
import com.m3u.ui.Events.enableDPadReaction
import com.m3u.ui.Toolkit
import com.m3u.ui.helper.Helper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class PlayerActivity : ComponentActivity() {
    private val viewModel: StreamViewModel by viewModels()

    private val helper: Helper = Helper(this)

    companion object {
        // FIXME: the property is worked only when activity has one instance at most.
        var isInPipMode: Boolean = false
            private set
    }

    @Inject
    lateinit var streamRepository: StreamRepository

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
                StreamRoute(
                    onBackPressed = { finish() },
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

    private fun playFromShortcuts(streamId: Int) {
        lifecycleScope.launch {
            val stream = streamRepository.get(streamId) ?: return@launch
            val playlist = playlistRepository.get(stream.playlistUrl)
            when {
                // series can not be played from shortcuts
                playlist?.isSeries ?: false -> {}
                else -> {
                    helper.play(MediaCommand.Common(stream.id))
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
