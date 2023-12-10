package com.m3u.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.m3u.core.architecture.configuration.Configuration
import com.m3u.core.architecture.configuration.LocalConfiguration
import com.m3u.material.components.Background
import com.m3u.material.model.AppTheme

@Composable
fun M3ULocalProvider(
    helper: Helper = EmptyHelper,
    configuration: Configuration,
    content: @Composable () -> Unit
) {
    val prevTypography = MaterialTheme.typography
    val typography = remember(prevTypography) {
        prevTypography.withFontFamily(FontFamilies.GoogleSans)
    }
    CompositionLocalProvider(
        LocalHelper provides helper,
        LocalConfiguration provides configuration
    ) {
        val useDynamicColors by configuration.useDynamicColors
        AppTheme(
            useDynamicColors = useDynamicColors,
            typography = typography
        ) {
            Background {
                content()
            }
        }
    }
}
