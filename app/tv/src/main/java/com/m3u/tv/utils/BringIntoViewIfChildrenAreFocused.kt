package com.m3u.tv.utils

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.relocation.BringIntoViewResponder
import androidx.compose.foundation.relocation.bringIntoViewResponder
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.debugInspectorInfo
import androidx.compose.ui.unit.toSize

// ToDo: Migrate to Modifier.Node and stop using composed function.
internal fun Modifier.bringIntoViewIfChildrenAreFocused(
    paddingValues: PaddingValues = PaddingValues()
): Modifier = composed(
    inspectorInfo = debugInspectorInfo { name = "bringIntoViewIfChildrenAreFocused" },
    factory = {
        val pxOffset = with(LocalDensity.current) {
            val y = (paddingValues.calculateBottomPadding() - paddingValues.calculateTopPadding())
                .toPx()
            Offset.Zero.copy(y = y)
        }
        var myRect: Rect = Rect.Zero
        val responder = object : BringIntoViewResponder {
            // return the current rectangle and ignoring the child rectangle received.
            @ExperimentalFoundationApi
            override fun calculateRectForParent(localRect: Rect): Rect {
                return myRect
            }

            // The container is not expected to be scrollable. Hence the child is
            // already in view with respect to the container.
            @ExperimentalFoundationApi
            override suspend fun bringChildIntoView(localRect: () -> Rect?) {
            }
        }

        this
            .onSizeChanged {
                val size = it.toSize()
                myRect = Rect(pxOffset, size)
            }
            .bringIntoViewResponder(responder)
    }
)
