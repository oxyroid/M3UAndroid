package com.m3u.features.stream.components

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.media3.common.Format
import com.m3u.i18n.R.string
import com.m3u.material.components.OnDismiss
import com.m3u.material.components.mask.MaskState
import com.m3u.material.model.LocalSpacing
import kotlinx.collections.immutable.ImmutableList

@Composable
internal fun FormatsBottomSheet(
    visible: Boolean,
    formats: ImmutableList<Format>,
    format: Format?,
    maskState: MaskState,
    onDismiss: OnDismiss,
    onClick: (Format) -> Unit,
    modifier: Modifier = Modifier
) {
    val spacing = LocalSpacing.current
    val state = rememberModalBottomSheetState()

    LaunchedEffect(visible) {
        if (visible) state.show()
        else state.hide()
    }
    if (visible) {
        ModalBottomSheet(
            sheetState = state,
            onDismissRequest = onDismiss,
            modifier = modifier,
            windowInsets = WindowInsets(0)
        ) {
            LaunchedEffect(Unit) {
                maskState.sleep()
            }
            Text(
                text = stringResource(string.feat_stream_dialog_choose_format),
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = spacing.medium)
            )
            Spacer(modifier = Modifier.height(spacing.medium))
            LazyColumn(
                modifier = Modifier
                    .sizeIn(
                        maxHeight = 320.dp
                    )
            ) {
                items(formats) { current ->
                    FormatItem(
                        format = current,
                        selected = current.id == format?.id,
                        onClick = { onClick(current) }
                    )
                }
                item {
                    Spacer(modifier = Modifier.navigationBarsPadding())
                }
            }
        }
    }
}