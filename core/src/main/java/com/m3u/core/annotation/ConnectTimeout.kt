package com.m3u.core.annotation

import androidx.annotation.IntDef

@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.PROPERTY,
    AnnotationTarget.VALUE_PARAMETER,
    AnnotationTarget.TYPE
)
@Retention(AnnotationRetention.SOURCE)
@IntDef(ConnectTimeout.SHORT, ConnectTimeout.LONG)
annotation class ConnectTimeout {
    companion object {
        const val SHORT = 8000
        const val LONG = 20000
    }
}
