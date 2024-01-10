package com.m3u.androidApp.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavDestination
import com.m3u.ui.Destination

object AppDefaults {
    @Composable
    fun isBackPressedVisible(currentDestination: NavDestination?): Boolean {
        currentDestination ?: return false
        return !(currentDestination destinationTo Destination.Root::class.java)
    }
}