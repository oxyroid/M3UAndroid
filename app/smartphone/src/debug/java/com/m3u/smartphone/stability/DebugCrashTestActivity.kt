package com.m3u.smartphone.stability

import android.app.Activity
import android.os.Bundle

/** Shell-only probe for verifying ACRA persistence and redaction in a debug installation. */
class DebugCrashTestActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        StabilityReporter.setFeature(StabilityFeature.APP)
        throw DebugCrashProbeException("password=must-not-be-stored")
    }
}

private class DebugCrashProbeException(message: String) : RuntimeException(message)
