package com.m3u.data.repository.usbkey

/**
 * Health status indicator for encryption system
 */
enum class HealthStatus {
    /** Encryption enabled, USB connected, recently verified */
    HEALTHY,

    /** Encryption enabled, USB disconnected (app locked) */
    WARNING,

    /** Key mismatch, verification failed */
    CRITICAL,

    /** Encryption not enabled */
    DISABLED
}
