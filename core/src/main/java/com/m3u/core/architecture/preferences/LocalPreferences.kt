package com.m3u.core.architecture.preferences

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

@Composable
fun hiltPreferences(): Preferences {
    val context = LocalContext.current
    return remember {
        val applicationContext = context.applicationContext ?: throw IllegalStateException()
        EntryPointAccessors
            .fromApplication<PreferencesEntryPoint>(applicationContext)
            .preferences
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
private interface PreferencesEntryPoint {
    val preferences: Preferences
}