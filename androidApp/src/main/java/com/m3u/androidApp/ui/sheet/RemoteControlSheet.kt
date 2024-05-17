package com.m3u.androidApp.ui.sheet

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBarsIgnoringVisibility
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.InternalComposeApi
import androidx.compose.ui.Modifier
import com.m3u.data.repository.television.UpdateState
import com.m3u.data.television.model.RemoteDirection
import com.m3u.data.television.model.Television

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
        val television: Television,
    ) : RemoteControlSheetValue()

    @Immutable
    data class Update(
        val television: Television,
        val state: UpdateState,
    ) : RemoteControlSheetValue()
}


@OptIn(InternalComposeApi::class)
@Composable
internal fun RemoteControlSheet(
    value: RemoteControlSheetValue,
    visible: Boolean,
    onCode: (String) -> Unit,
    checkTelevisionCodeOnSmartphone: () -> Unit,
    forgetTelevisionCodeOnSmartphone: () -> Unit,
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
            windowInsets = WindowInsets(0),
            onDismissRequest = {
                if (!searchingOrConnecting) onDismissRequest()
            },
            properties = ModalBottomSheetDefaults.properties(
                shouldDismissOnBackPress = false
            )
        ) {
            Column(
                modifier.padding(WindowInsets.navigationBarsIgnoringVisibility.asPaddingValues())
            ) {
                when (value) {
                    is RemoteControlSheetValue.Prepare -> {
                        PrepareContent(
                            code = value.code,
                            searchingOrConnecting = value.searchingOrConnecting,
                            checkTelevisionCodeOnSmartphone = checkTelevisionCodeOnSmartphone,
                            onCode = onCode
                        )
                    }

                    is RemoteControlSheetValue.DPad -> {
                        DPadContent(
                            television = value.television,
                            onRemoteDirection = onRemoteDirection,
                            forgetTelevisionCodeOnSmartphone = forgetTelevisionCodeOnSmartphone,
                        )
                    }

                    is RemoteControlSheetValue.Update -> {

                    }

                    else -> {}
                }
            }
        }
    }
}
