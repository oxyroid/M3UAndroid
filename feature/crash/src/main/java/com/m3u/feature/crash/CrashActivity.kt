package com.m3u.feature.crash

import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.m3u.ui.Events.enableDPadReaction
import com.m3u.ui.Toolkit
import com.m3u.ui.helper.Helper
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class CrashActivity : ComponentActivity() {
    private val helper: Helper = Helper(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        enableDPadReaction()
        super.onCreate(savedInstanceState)
        setContent {
            Toolkit(helper) {
                CrashApp()
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        helper.applyConfiguration()
    }
}