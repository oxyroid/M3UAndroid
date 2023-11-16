package com.m3u.features.main

sealed class MainMessage {
    data object ErrorCannotUnsubscribe: MainMessage()
}
