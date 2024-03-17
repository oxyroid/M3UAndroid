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
import com.m3u.data.database.model.Playlist
import com.m3u.data.repository.PlaylistRepository
import com.m3u.data.repository.StreamRepository
import com.m3u.data.service.Messager
import com.m3u.data.service.PlayerManagerV2
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
    lateinit var playerManager: PlayerManagerV2

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

    private val shortcutStreamIdLiveData = MutableLiveData<Int?>(null)
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
        shortcutStreamIdLiveData.observe(this) { streamId ->
            streamId ?: return@observe
            play(streamId)
        }
        shortcutRecentlyLiveData.observe(this) { recently ->
            if (recently) {
                lifecycleScope.launch {
                    val stream = streamRepository.getPlayedRecently() ?: return@launch
                    play(stream.id)
                }
            }
        }
    }

    private fun play(streamId: Int) {
        lifecycleScope.launch {
            val stream = streamRepository.get(streamId) ?: return@launch
            val playlist = playlistRepository.get(stream.playlistUrl)
            when {
                playlist?.type in Playlist.SERIES_TYPES -> {} // TODO
                else -> {
                    helper.play(PlayerManagerV2.Input.Live(stream.id))
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
        shortcutStreamIdLiveData.value = intent
            .getIntExtra(Contracts.PLAYER_SHORTCUT_STREAM_ID, -1)
            .takeIf { it != -1 }
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
