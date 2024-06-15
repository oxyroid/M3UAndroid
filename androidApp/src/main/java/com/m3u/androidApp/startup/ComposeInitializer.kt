package com.m3u.androidApp.startup

import android.content.Context
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.ProcessLifecycleInitializer
import androidx.startup.Initializer

@Suppress("Unused")
/**
 * warmup compose component
 *
 * https://medium.com/androiddevelopers/faster-jetpack-compose-view-interop-with-app-startup-and-baseline-profile-8a615e061d14
 */
class ComposeInitializer: Initializer<Unit> {
    override fun create(context: Context) {
        ComposeView(context)
    }

    override fun dependencies(): List<Class<out Initializer<*>>> {
        return listOf(ProcessLifecycleInitializer::class.java)
    }
}