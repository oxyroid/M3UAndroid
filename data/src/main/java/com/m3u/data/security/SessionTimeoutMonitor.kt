package com.m3u.data.security

import kotlinx.coroutines.CoroutineScope
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionTimeoutMonitor @Inject constructor() {
    private val timber = Timber.tag("SessionTimeoutMonitor")

    fun startTimer(scope: CoroutineScope) {
        timber.d("Session timer started")
        // Stub: 6-hour timer implementation would go here
    }

    fun stopTimer() {
        timber.d("Session timer stopped")
        // Stub: Cancel timer implementation would go here
    }
}
