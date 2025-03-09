package com.m3u.smartphone.ui.business.crash

import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.m3u.smartphone.ui.common.helper.Helper
import com.m3u.smartphone.ui.common.internal.Events.enableDPadReaction
import com.m3u.smartphone.ui.common.internal.Toolkit
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