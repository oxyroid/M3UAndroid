package com.m3u.core.annotation

import androidx.annotation.IntDef

@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.PROPERTY,
    AnnotationTarget.VALUE_PARAMETER,
    AnnotationTarget.TYPE
)
@Retention(AnnotationRetention.SOURCE)
@IntDef(ConnectTimeout.Short, ConnectTimeout.Long)
annotation class ConnectTimeout {
    companion object {
        const val Short = 8000
        const val Long = 20000
    }
}
