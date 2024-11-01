package com.m3u.extension.api.analyzer

import com.m3u.extension.api.ExtensionIntro

sealed interface Analyzer: ExtensionIntro {
    // priority is only for the current extension.
    val priority: Int
}