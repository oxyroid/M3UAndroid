package com.m3u.data.model

import androidx.annotation.IntDef

@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.PROPERTY,
    AnnotationTarget.VALUE_PARAMETER,
    AnnotationTarget.TYPE
)
@Retention(AnnotationRetention.SOURCE)
@IntDef(SyncMode.DEFAULT, SyncMode.EXCEPT)
annotation class SyncMode {
    companion object {
        const val DEFAULT = 0
        const val EXCEPT = 1
    }
}
