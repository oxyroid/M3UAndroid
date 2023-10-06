package com.m3u.core.unspecified

enum class UBoolean {
    True, False, Unspecified
}

val Boolean.ub: UBoolean get() = if (this) UBoolean.True else UBoolean.False

val UBoolean.actual: Boolean?
    get() = when (this) {
        UBoolean.True -> true
        UBoolean.False -> false
        UBoolean.Unspecified -> null
    }

val UBoolean?.orUnspecified: UBoolean get() = this ?: UBoolean.Unspecified

val UBoolean.isUnspecified: Boolean get() = this == UBoolean.Unspecified
