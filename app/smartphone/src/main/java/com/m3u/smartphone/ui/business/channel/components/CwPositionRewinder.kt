package com.m3u.smartphone.ui.business.channel.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.m3u.smartphone.ui.material.components.FontFamilies

@Composable
fun CwPositionRewinder(
    modifier: Modifier = Modifier,
    containerColor: Color = Color.Unspecified,
    contentColor: Color = Color.Unspecified,
    borderColor: Color = Color.Unspecified,
    text: @Composable () -> Unit,
    action: (@Composable RowScope.() -> Unit)? = null,
) {
    val lContainerColor = containerColor.takeOrElse { MaterialTheme.colorScheme.surfaceVariant }
    val lContentColor = contentColor.takeOrElse {
        MaterialTheme.colorScheme.contentColorFor(lContainerColor)
            .takeOrElse { MaterialTheme.colorScheme.onSurfaceVariant }
    }
    val lBorderColor = borderColor.takeOrElse { MaterialTheme.colorScheme.outline }

    CompositionLocalProvider(
        LocalContentColor provides lContentColor,
        LocalTextStyle provides MaterialTheme.typography.bodyMedium.copy(
            fontFamily = FontFamilies.LexendExa
        )
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .drawBehind {
                    drawRoundRect(
                        color = lContainerColor,
                        cornerRadius = CornerRadius(16.dp.toPx())
                    )
                    drawRoundRect(
                        color = lBorderColor,
                        cornerRadius = CornerRadius(16.dp.toPx()),
                        style = Stroke(
                            width = 2.dp.toPx(),
                            cap = Stroke.DefaultCap,
                            miter = Stroke.DefaultMiter,
                            pathEffect = null
                        )
                    )
                }
                .semantics(mergeDescendants = true) {
                    onClick { true }
                }
                .then(modifier),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(Modifier.width(16.dp))
            Row(Modifier.weight(1f)) { text() }
            if (action != null) {
                Spacer(Modifier.size(8.dp))
                action.invoke(this)
                Spacer(Modifier.width(8.dp))
            } else {
                Spacer(Modifier.width(16.dp))
            }
        }
    }
}
