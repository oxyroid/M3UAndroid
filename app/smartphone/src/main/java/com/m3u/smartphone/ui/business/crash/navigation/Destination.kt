package com.m3u.smartphone.ui.business.crash.navigation

internal sealed class Destination {
    data object List : Destination()
    data class Detail(val path: String) : Destination()
}
