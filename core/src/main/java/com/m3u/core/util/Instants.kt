package com.m3u.core.util

import kotlinx.serialization.json.Json

val json: Json = Json {
    ignoreUnknownKeys = true
}