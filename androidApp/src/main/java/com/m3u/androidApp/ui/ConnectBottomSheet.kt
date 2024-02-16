package com.m3u.androidApp.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBarsIgnoringVisibility
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetDefaults
import androidx.compose.material3.SheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.InternalComposeApi
import androidx.compose.ui.Modifier
import com.m3u.androidApp.ui.sheet.PrepareSheetContent
import com.m3u.androidApp.ui.sheet.RemoteControlSheetContent
import com.m3u.core.wrapper.Message
import com.m3u.data.television.http.endpoint.SayHello
import com.m3u.data.television.model.RemoteDirection

@Immutable
sealed class ConnectBottomSheetValue {
    @Immutable
    data object Idle : ConnectBottomSheetValue()

    @Immutable
    data class Prepare(
        val code: String,
        val searching: Boolean,
        val onCode: (String) -> Unit,
        val onSearch: () -> Unit
    ) : ConnectBottomSheetValue()

    @Immutable
    data class Remote(
        val television: SayHello.TelevisionInfo,
        val onRemoteDirection: (RemoteDirection) -> Unit,
        val onDisconnect: () -> Unit
    ) : ConnectBottomSheetValue()
}


@OptIn(InternalComposeApi::class)
@Composable
internal fun ConnectBottomSheet(
    message: Message,
    value: ConnectBottomSheetValue,
    visible: Boolean,
    sheetState: SheetState,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (visible) {
        val searching = with(value) { this is ConnectBottomSheetValue.Prepare && searching }
        ModalBottomSheet(
            sheetState = sheetState,
            onDismissRequest = {
                if (!searching) onDismissRequest()
            },
            windowInsets = WindowInsets(0),
            properties = ModalBottomSheetDefaults.properties(
                shouldDismissOnBackPress = false
            )
        ) {
            Column(
                modifier.padding(WindowInsets.navigationBarsIgnoringVisibility.asPaddingValues())
            ) {
                when (value) {
                    is ConnectBottomSheetValue.Prepare -> {
                        PrepareSheetContent(
                            code = value.code,
                            connecting = value.searching,
                            onConnect = value.onSearch,
                            message = message,
                            onCode = value.onCode
                        )
                    }

                    is ConnectBottomSheetValue.Remote -> {
                        RemoteControlSheetContent(
                            television = value.television,
                            onRemoteDirection = value.onRemoteDirection,
                            onDisconnect = value.onDisconnect,
                            message = message,
                        )
                    }

                    else -> {}
                }
            }
        }
    }
}
