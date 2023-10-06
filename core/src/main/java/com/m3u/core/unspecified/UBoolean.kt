package com.m3u.core.unspecified

enum class UBoolean {
    True, False, Unspecified
}

val Boolean.ub: UBoolean get() = if (this) UBoolean.True else UBoolean.False

val UBoolean?.orUnspecified: UBoolean get() = this ?: UBoolean.Unspecified

val UBoolean.isUnspecified: Boolean get() = this == UBoolean.Unspecified
