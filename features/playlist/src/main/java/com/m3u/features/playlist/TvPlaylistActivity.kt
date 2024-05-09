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
import com.m3u.ui.Events.enableDPadReaction
import com.m3u.ui.Toolkit
import com.m3u.ui.helper.Helper
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class TvPlaylistActivity : AppCompatActivity() {
    private val helper: Helper = Helper(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        enableDPadReaction()
        super.onCreate(savedInstanceState)
        setContent {
            Toolkit(helper) {
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

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        helper.applyConfiguration()
    }
}