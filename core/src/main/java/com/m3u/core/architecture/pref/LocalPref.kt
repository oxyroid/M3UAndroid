package com.m3u.core.architecture.pref

import androidx.compose.runtime.compositionLocalOf

val LocalPref = compositionLocalOf<Pref> { error("Please provide pref.") }
