package com.m3u.androidApp.ui.sheet

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.InternalComposeApi
import androidx.compose.ui.Modifier
import com.m3u.data.leanback.model.Leanback
import com.m3u.data.leanback.model.RemoteDirection

@Immutable
sealed class RemoteControlSheetValue {
    @Immutable
    data object Idle : RemoteControlSheetValue()

    @Immutable
    data class Prepare(
        val code: String,
        val searchingOrConnecting: Boolean,
    ) : RemoteControlSheetValue()

    @Immutable
    data class DPad(
        val leanback: Leanback,
    ) : RemoteControlSheetValue()
}


@OptIn(InternalComposeApi::class)
@Composable
internal fun RemoteControlSheet(
    value: RemoteControlSheetValue,
    visible: Boolean,
    onCode: (String) -> Unit,
    checkLeanbackCodeOnSmartphone: () -> Unit,
    forgetLeanbackCodeOnSmartphone: () -> Unit,
    onRemoteDirection: (RemoteDirection) -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier
) {
    val searchingOrConnecting = with(value) {
        this is RemoteControlSheetValue.Prepare && searchingOrConnecting
    }
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { !searchingOrConnecting }
    )

    if (visible) {
        ModalBottomSheet(
            sheetState = sheetState,
            onDismissRequest = {
                if (!searchingOrConnecting) onDismissRequest()
            },
            properties = ModalBottomSheetProperties(
                shouldDismissOnBackPress = false
            ),
            modifier = modifier
        ) {
            Column {
                when (value) {
                    is RemoteControlSheetValue.Prepare -> {
                        PrepareContent(
                            code = value.code,
                            searchingOrConnecting = value.searchingOrConnecting,
                            checkLeanbackCodeOnSmartphone = checkLeanbackCodeOnSmartphone,
                            onCode = onCode
                        )
                    }

                    is RemoteControlSheetValue.DPad -> {
                        DPadContent(
                            leanback = value.leanback,
                            onRemoteDirection = onRemoteDirection,
                            forgetLeanbackCodeOnSmartphone = forgetLeanbackCodeOnSmartphone,
                        )
                    }

                    else -> {}
                }
            }
        }
    }
}
