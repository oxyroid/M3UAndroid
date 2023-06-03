package com.m3u.androidApp

import android.content.Context
import com.m3u.androidApp.navigation.TopLevelDestination
import com.m3u.core.architecture.Publisher
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class AppPublisher @Inject constructor(
    @ApplicationContext private val context: Context
) : Publisher {
    override val applicationID: String = BuildConfig.APPLICATION_ID
    override val versionName: String = BuildConfig.VERSION_NAME
    override val debug: Boolean = BuildConfig.DEBUG
    override val destinationsCount: Int get() = TopLevelDestination.values().size
    override fun getDestination(index: Int): String {
        val resId = TopLevelDestination.values()[index].iconTextId
        return context.getString(resId)
    }
}