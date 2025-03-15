package com.m3u.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme
import com.m3u.tv.utils.Helper
import com.m3u.tv.utils.LocalHelper
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val helper = Helper(this)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme()
            ) {
                Box(Modifier.background(MaterialTheme.colorScheme.background)) {
                    CompositionLocalProvider(
                        LocalHelper provides helper,
                        LocalContentColor provides MaterialTheme.colorScheme.onBackground
                    ) {
                        App {
                            onBackPressedDispatcher.onBackPressed()
                        }
                    }
                }
            }
        }
    }
}
