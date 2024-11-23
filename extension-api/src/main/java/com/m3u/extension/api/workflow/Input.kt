package com.m3u.extension.api.workflow

data class Input(
    val label: String,
    val value: Value,
    val description: String = "",
    val isOptIn: Boolean = false
) {
    sealed class Value
    data class BooleanValue(val defaultValue: Boolean = false) : Value()
    data object StringValue : Value()
}
