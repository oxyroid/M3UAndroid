package com.m3u.core.architecture.preferences

@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.PROPERTY,
    AnnotationTarget.VALUE_PARAMETER,
    AnnotationTarget.TYPE
)
@Retention(AnnotationRetention.SOURCE)
annotation class EpgOffset {
    companion object {
        const val NONE = 0L
        const val MINUS_12H = -43_200_000L
        const val MINUS_6H = -21_600_000L
        const val MINUS_3H = -10_800_000L
        const val MINUS_2H = -7_200_000L
        const val MINUS_1H = -3_600_000L
        const val MINUS_30M = -1_800_000L
        const val PLUS_30M = 1_800_000L
        const val PLUS_1H = 3_600_000L
        const val PLUS_2H = 7_200_000L
        const val PLUS_3H = 10_800_000L
        const val PLUS_6H = 21_600_000L
        const val PLUS_12H = 43_200_000L

        val VALUES = listOf(
            NONE,
            PLUS_30M,
            PLUS_1H,
            PLUS_2H,
            PLUS_3H,
            PLUS_6H,
            PLUS_12H,
            MINUS_30M,
            MINUS_1H,
            MINUS_2H,
            MINUS_3H,
            MINUS_6H,
            MINUS_12H
        )
    }
}
