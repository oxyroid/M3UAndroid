package com.m3u.core.architecture.pref

import androidx.compose.runtime.compositionLocalOf
import com.m3u.core.architecture.pref.internal.MockPref

val LocalPref = compositionLocalOf<Pref> { MockPref }
