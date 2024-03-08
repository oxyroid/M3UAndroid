package com.m3u.features.playlist

import android.app.ActivityOptions
import android.content.ComponentName
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.m3u.core.Contracts
import com.m3u.core.architecture.dispatcher.Dispatcher
import com.m3u.core.architecture.dispatcher.M3uDispatchers.Main
import com.m3u.core.architecture.pref.Pref
import com.m3u.data.service.Messager
import com.m3u.data.service.PlayerManagerV2
import com.m3u.data.service.RemoteDirectionService
import com.m3u.ui.Toolkit
import com.m3u.ui.helper.AbstractHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineDispatcher
import javax.inject.Inject

@AndroidEntryPoint
class TvPlaylistActivity : AppCompatActivity() {
    private val helper by lazy {
        AbstractHelper(
            activity = this,
            mainDispatcher = mainDispatcher,
            messager = messager,
            playerManager = playerManager,
            message = messager.message,
        )
    }

    @Inject
    lateinit var messager: Messager

    @Inject
    lateinit var pref: Pref

    @Inject
    lateinit var playerManager: PlayerManagerV2

    @Inject
    @Dispatcher(Main)
    lateinit var mainDispatcher: CoroutineDispatcher

    @Inject
    lateinit var remoteDirectionService: RemoteDirectionService

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            Toolkit(
                helper = helper,
                pref = pref,
                actions = remoteDirectionService.actions
            ) {
                PlaylistRoute(
                    navigateToStream = ::navigateToStream
                )
            }
        }
    }

    private fun navigateToStream() {
        val options = ActivityOptions.makeCustomAnimation(
            this,
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

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        helper.onUserLeaveHint?.invoke()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        helper.applyConfiguration()
    }
}