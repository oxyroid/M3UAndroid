package com.m3u.tv

import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT)
        )
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = TvColors.Focus,
                    onPrimary = TvColors.OnFocus,
                    secondary = TvColors.Accent,
                    background = TvColors.Background,
                    onBackground = TvColors.TextPrimary,
                    surface = TvColors.Surface,
                    onSurface = TvColors.TextPrimary,
                    surfaceVariant = TvColors.SurfaceRaised,
                    onSurfaceVariant = TvColors.TextSecondary
                )
            ) {
                Box(Modifier.background(MaterialTheme.colorScheme.background)) {
                    App(onBackPressed = onBackPressedDispatcher::onBackPressed)
                }
            }
        }
    }
}
