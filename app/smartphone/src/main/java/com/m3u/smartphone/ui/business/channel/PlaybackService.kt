package com.m3u.smartphone.ui.business.channel

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.Player
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.m3u.core.Contracts
import com.m3u.data.service.PlayerManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class PlaybackService : MediaSessionService() {
    @Inject
    lateinit var playerManager: PlayerManager

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        serviceScope.launch {
            playerManager.player.collectLatest { player ->
                if (player == null) {
                    releaseSession()
                } else {
                    updateSession(player)
                }
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onDestroy() {
        releaseSession()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun updateSession(player: Player) {
        val session = mediaSession
        if (session == null) {
            mediaSession = MediaSession.Builder(this, player)
                .setSessionActivity(createSessionActivity())
                .build()
                .also(::addSession)
        } else if (session.player !== player) {
            session.setPlayer(player)
        }
    }

    private fun releaseSession() {
        mediaSession?.release()
        mediaSession = null
    }

    private fun createSessionActivity(): PendingIntent {
        val intent = Intent(this, PlayerActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(Contracts.PLAYER_SHORTCUT_CHANNEL_RECENTLY, true)
        return PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
