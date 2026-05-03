package com.m3u.smartphone.ui.common.connect

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.InternalComposeApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.m3u.data.tv.model.RemoteDirection
import com.m3u.data.tv.model.TvInfo
import com.m3u.i18n.R

@Immutable
sealed class RemoteControlSheetValue {
    @Immutable
    data object Idle : RemoteControlSheetValue()

    @Immutable
    data class Prepare(
        val code: String,
        val searchingOrConnecting: Boolean,
        val subtitle: String? = null,
        val timedOut: Boolean = false,
    ) : RemoteControlSheetValue()

    @Immutable
    data class DPad(
        val tvInfo: TvInfo,
    ) : RemoteControlSheetValue()
}

@OptIn(InternalComposeApi::class)
@Composable
internal fun RemoteControlSheet(
    value: RemoteControlSheetValue,
    visible: Boolean,
    onCode: (String) -> Unit,
    checkTvCodeOnSmartphone: () -> Unit,
    forgetTvCodeOnSmartphone: () -> Unit,
    onRemoteDirection: (RemoteDirection) -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier
) {
    val searchingOrConnecting = value is RemoteControlSheetValue.Prepare && value.searchingOrConnecting
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
                shouldDismissOnBackPress = !searchingOrConnecting
            ),
            modifier = modifier
        ) {
            Column {
                when (value) {
                    is RemoteControlSheetValue.Prepare -> {
                        PrepareContent(
                            code = value.code,
                            searchingOrConnecting = value.searchingOrConnecting,
                            checkTvCodeOnSmartphone = checkTvCodeOnSmartphone,
                            onCode = onCode,
                            subtitle = when {
                                value.timedOut -> stringResource(R.string.ui_remote_control_pair_timeout)
                                else -> value.subtitle ?: stringResource(R.string.ui_remote_control_pair_subtitle)
                            }
                        )
                    }

                    is RemoteControlSheetValue.DPad -> {
                        DPadContent(
                            tvInfo = value.tvInfo,
                            onRemoteDirection = onRemoteDirection,
                            forgetTvCodeOnSmartphone = forgetTvCodeOnSmartphone,
                        )
                    }

                    RemoteControlSheetValue.Idle -> Unit
                }
            }
        }
    }
}
