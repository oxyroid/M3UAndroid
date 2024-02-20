package com.m3u.data.service

import androidx.compose.runtime.Immutable
import com.m3u.data.television.model.RemoteDirection
import kotlinx.coroutines.flow.SharedFlow

@Immutable
interface RemoteDirectionService {
    val actions: SharedFlow<Action>
    fun emit(remoteDirection: RemoteDirection)
    sealed class Action {
        data object Back : Action()
        data class Common(val keyCode: Int) : Action()
    }
}