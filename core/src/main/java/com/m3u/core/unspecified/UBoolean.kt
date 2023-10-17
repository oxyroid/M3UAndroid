package com.m3u.core.unspecified

enum class UBoolean {
    True, False, Unspecified
}

@Deprecated(
    "Replace with Boolean.unspecifiable",
    replaceWith = ReplaceWith(
        "this.unspecifiable",
        "com.m3u.core.unspecified.unspecifiable"
    )
)
val Boolean.u: UBoolean get() = unspecifiable
val Boolean.unspecifiable: UBoolean get() = if (this) UBoolean.True else UBoolean.False

@Deprecated(
    "Replace with UBoolean.specified",
    replaceWith = ReplaceWith(
        "this.specified",
        "com.m3u.core.unspecified.specified"
    )
)
val UBoolean.actual: Boolean? get() = specified

val UBoolean.specified: Boolean?
    get() = when (this) {
        UBoolean.True -> true
        UBoolean.False -> false
        UBoolean.Unspecified -> null
    }

val UBoolean?.orUnspecified: UBoolean get() = this ?: UBoolean.Unspecified

val UBoolean.isUnspecified: Boolean get() = this == UBoolean.Unspecified
