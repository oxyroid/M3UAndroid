package com.m3u.features.crash

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.m3u.core.architecture.configuration.Configuration
import com.m3u.ui.M3ULocalProvider
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class CrashActivity : ComponentActivity() {
    @Inject
    lateinit var configuration: Configuration
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            M3ULocalProvider(configuration = configuration) {
                CrashApp()
            }
        }
    }
}