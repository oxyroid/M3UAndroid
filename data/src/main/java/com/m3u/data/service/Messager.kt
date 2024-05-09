package com.m3u.data.service

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.m3u.core.wrapper.Message
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.StateFlow

interface Messager {
    fun emit(message: Message)
    fun emit(message: String)
    val message: StateFlow<Message>
}

@EntryPoint
@InstallIn(SingletonComponent::class)
private interface MessagerEntryPoint {
    val messager: Messager
}

@Composable
fun collectMessageAsState(): State<Message> {
    val context = LocalContext.current
    return remember {
        val applicationContext = context.applicationContext ?: throw IllegalStateException()
        EntryPointAccessors
            .fromApplication<MessagerEntryPoint>(applicationContext)
            .messager
            .message
    }
        .collectAsState()
}
