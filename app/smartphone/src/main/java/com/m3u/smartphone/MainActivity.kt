package com.m3u.smartphone

import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.m3u.smartphone.ui.App
import com.m3u.smartphone.ui.AppViewModel
import com.m3u.smartphone.ui.common.helper.Helper
import com.m3u.smartphone.ui.common.internal.Toolkit
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    private val viewModel: AppViewModel by viewModels()

    private val helper: Helper = Helper(this)

    override fun onResume() {
        super.onResume()
        helper.applyConfiguration()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        helper.applyConfiguration()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            Toolkit(helper) {
                App(
                    viewModel = viewModel
                )
            }
        }
    }
}
