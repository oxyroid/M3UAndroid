package com.m3u.extension.api

import android.util.Log
import kotlin.time.Duration
import kotlin.time.measureTime

object Samplings {
    inline fun <R> measure(key: String, block: () -> R): R {
        val r: R
        val duration = measureTime {
            r = block()
        }
        record(key, duration)
        return r
    }

    fun record(key: String, duration: Duration) {
        val info = infos[key] ?: Info()
        infos[key] = info.copy(
            count = info.count + 1,
            avg = (info.avg + duration) / (info.count + 1)
        )
        Log.d("ExtensionSamplings", "$key-${infos[key]}, +$duration")
    }

    fun separate() {
        Log.d("ExtensionSamplings", "=====================================")
    }

    private val infos = mutableMapOf<String, Info>()

    data class Info(
        val count: Int = 0,
        val avg: Duration = Duration.ZERO
    ) {
        override fun toString(): String = "[$count]avg:$avg"
    }
}