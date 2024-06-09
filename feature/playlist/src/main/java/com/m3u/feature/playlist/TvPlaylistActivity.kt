package com.m3u.feature.playlist

import android.app.ActivityOptions
import android.content.ComponentName
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.m3u.core.Contracts
import com.m3u.ui.Events.enableDPadReaction
import com.m3u.ui.Toolkit
import com.m3u.ui.helper.Helper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class TvPlaylistActivity : AppCompatActivity() {
    private val helper: Helper = Helper(this)
    private val viewModel: PlaylistViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        enableDPadReaction()
        super.onCreate(savedInstanceState)
        handleIntent(intent)
        setContent {
            Toolkit(helper) {
                PlaylistRoute(
                    viewModel = viewModel,
                    navigateToChannel = ::navigateToChannel
                )
            }
        }
    }

    private fun handleIntent(intent: Intent) {
        val intentAction = intent.action
        if (intentAction == Intent.ACTION_VIEW) {
            val intentData = intent.data
            val pathSegments = intentData?.pathSegments ?: emptyList()
            when (pathSegments.firstOrNull()) {
                "discover" -> {
                    val channelId = pathSegments[1].toIntOrNull() ?: return
                    viewModel.setup(channelId) {
                        lifecycleScope.launch {
                            helper.play(it)
                            navigateToChannel()
                        }
                    }
                }
            }
        }
    }

    private fun navigateToChannel() {
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

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        helper.applyConfiguration()
    }
}