package com.m3u.extension.api.workflow

data class Input(
    // the label will be used to display to the user and as a key for subsequent user input.
    // so it should be unique in a workflow.
    val label: String,
    val type: Type,
    val description: String = "",
    val isOptIn: Boolean = false
) {
    sealed class Type
    data class BooleanType(val defaultValue: Boolean = false) : Type()
    data object StringType : Type()
}
