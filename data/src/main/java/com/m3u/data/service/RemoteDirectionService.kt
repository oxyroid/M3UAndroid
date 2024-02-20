package com.m3u.data.service

import android.view.inputmethod.BaseInputConnection
import androidx.compose.runtime.Immutable
import com.m3u.data.television.model.RemoteDirection

@Immutable
interface RemoteDirectionService {
    fun emit(remoteDirection: RemoteDirection)
    fun init(
        connection: BaseInputConnection?,
        onBackPressed: (() -> Unit)?
    )
}