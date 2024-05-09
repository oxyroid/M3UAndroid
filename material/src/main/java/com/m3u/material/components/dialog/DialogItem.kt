@file:Suppress("unused")

package com.m3u.material.components.dialog

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.m3u.material.model.LocalSpacing

@Composable
fun DialogItem(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    contentColor: Color = Color.Unspecified,
    onClick: () -> Unit,
) {
    val theme = MaterialTheme.colorScheme
    val spacing = LocalSpacing.current
    Surface(
        shape = RoundedCornerShape(spacing.medium),
        tonalElevation = 0.dp,
        color = color.takeOrElse { theme.surface },
        contentColor = contentColor.takeOrElse { theme.onSurface },
        modifier = Modifier.semantics(mergeDescendants = true) { }
    ) {
        Row(
            horizontalArrangement = Arrangement.Start,
            modifier = modifier
                .clickable(onClick = onClick)
                .padding(spacing.medium)
                .fillMaxWidth()
        ) {
            Text(
                text = text.uppercase(),
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
fun DialogItem(
    resId: Int,
    color: Color = Color.Unspecified,
    contentColor: Color = Color.Unspecified,
    onClick: () -> Unit,
) {
    DialogItem(
        text = stringResource(id = resId),
        color = color,
        contentColor = contentColor,
        onClick = onClick
    )
}
