package com.m3u.material.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsIgnoringVisibility
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.m3u.material.ktx.Edge
import com.m3u.material.ktx.blurEdges
import com.m3u.material.ktx.thenIf
import com.m3u.material.model.LocalSpacing

@Composable
fun BottomSheet(
    sheetState: SheetState,
    visible: Boolean,
    header: @Composable RowScope.() -> Unit,
    body: @Composable ColumnScope.() -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    windowInsets: WindowInsets = WindowInsets.navigationBarsIgnoringVisibility,
    blurBody: Boolean = false
) {
    val spacing = LocalSpacing.current
    if (visible) {
        ModalBottomSheet(
            sheetState = sheetState,
            onDismissRequest = onDismissRequest,
            modifier = modifier,
            windowInsets = WindowInsets(0)
        ) {
            Column(
                modifier = Modifier.padding(windowInsets.asPaddingValues())
            ) {
                Row(
                    content = header,
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(spacing.medium),
                    modifier = Modifier.padding(
                        horizontal = spacing.medium
                    )
                )
                Spacer(modifier = Modifier.height(spacing.small))
                Column(
                    content = body,
                    modifier = Modifier.thenIf(blurBody) {
                        Modifier.blurEdges(
                            edges = listOf(Edge.Top, Edge.Bottom),
                            color = MaterialTheme.colorScheme.background
                        )
                    }
                )
            }
        }
    }
}