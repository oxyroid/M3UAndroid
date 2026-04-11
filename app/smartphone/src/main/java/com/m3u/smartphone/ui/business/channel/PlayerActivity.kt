package com.m3u.smartphone.ui.business.channel

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.app.PictureInPictureModeChangedInfo
import androidx.core.util.Consumer
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import com.m3u.business.channel.ChannelViewModel
import com.m3u.core.Contracts
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

    companion object {
        // FIXME: the property is worked only when activity has one instance at most.
        var isInPipMode: Boolean = false
            private set
    }

    @Inject
    lateinit var channelRepository: ChannelRepository

    @Inject
    lateinit var playlistRepository: PlaylistRepository

    private val pipListener = Consumer<PictureInPictureModeChangedInfo> {
        isInPipMode = it.isInPictureInPictureMode
        if (!it.isInPictureInPictureMode && lifecycle.currentState !in arrayOf(
                Lifecycle.State.RESUMED,
                Lifecycle.State.STARTED
            )
        ) {
            viewModel.destroy()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        handleIntent(intent)

        // Create ComposeView manually with DisposeOnViewTreeLifecycleDestroyed
        // set BEFORE attaching to the window. This prevents the internal
        // AndroidComposeView from being added to the static composeViews list
        // multiple times during PiP window re-attach cycles.
        val composeView = ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
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
        }
        setContentView(
            composeView,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        addOnPictureInPictureModeChangedListener(pipListener)
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
        removeOnPictureInPictureModeChangedListener(pipListener)
        viewModel.destroy()
        super.onDestroy()
    }
}
