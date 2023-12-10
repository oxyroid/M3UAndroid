package com.m3u.core.architecture.configuration

import androidx.compose.runtime.compositionLocalOf

val LocalConfiguration = compositionLocalOf<Configuration> {
    error("No configuration found")
}