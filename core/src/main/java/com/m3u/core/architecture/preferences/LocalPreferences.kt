package com.m3u.core.architecture.preferences

import androidx.compose.runtime.compositionLocalOf

val LocalPreferences = compositionLocalOf<Preferences> { error("Please provide pref.") }
