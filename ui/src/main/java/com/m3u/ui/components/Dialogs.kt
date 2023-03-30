@file:Suppress("unused")

package com.m3u.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.m3u.ui.model.LocalSpacing
import com.m3u.ui.model.LocalTheme

@Composable
fun SheetDialog(
    visible: Boolean,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
    modifier: Modifier = Modifier,
    maxHeight: Boolean = false,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top
) {
    val theme = LocalTheme.current
    val spacing = LocalSpacing.current

    if (visible) {
        Dialog(
            onDismissRequest = onDismiss
        ) {
            Surface(
                color = theme.background,
                contentColor = theme.onBackground,
                shape = RoundedCornerShape(LocalSpacing.current.medium),
                border = BorderStroke(2.dp, theme.divider.copy(alpha = 0.45f)),
                elevation = spacing.medium,
                modifier = Modifier
                    .padding(spacing.medium)
                    .then(modifier)
            ) {
                Column(
                    verticalArrangement = verticalArrangement,
                    modifier = Modifier
                        .fillMaxWidth()
                        .let {
                            if (maxHeight) it.fillMaxHeight()
                            else it.wrapContentHeight()
                        }
                        .padding(LocalSpacing.current.medium),
                    content = content
                )
            }
        }
    }
}

@Composable
fun SheetTitle(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = LocalTheme.current.onBackground,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.h6,
        fontWeight = FontWeight.ExtraBold,
        textAlign = TextAlign.Start,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        color = color,
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun SheetItem(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val theme = LocalTheme.current
    val spacing = LocalSpacing.current
    Surface(
        shape = RoundedCornerShape(spacing.medium),
        elevation = 0.dp,
        color = theme.surface,
        contentColor = theme.onSurface,
        onClick = onClick
    ) {
        Row(
            horizontalArrangement = Arrangement.Start,
            modifier = modifier.padding(spacing.medium)
        ) {
            Text(
                text = text.uppercase(),
                style = MaterialTheme.typography.subtitle1,
                maxLines = 1
            )
        }
    }
}

typealias OnDismissRequest = () -> Unit
typealias OnConfirm = () -> Unit
typealias OnDismiss = () -> Unit

@Composable
fun AlertDialog(
    title: String,
    text: String,
    confirm: String?,
    dismiss: String?,
    onDismissRequest: OnDismissRequest,
    onConfirm: OnConfirm,
    onDismiss: OnDismiss,
    modifier: Modifier = Modifier,
    backgroundColor: Color = LocalTheme.current.background,
    contentColor: Color = LocalTheme.current.onBackground,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.h6,
                maxLines = 1
            )
        },
        text = {
            Text(
                text = text,
                style = MaterialTheme.typography.body1
            )
        },
        confirmButton = {
            confirm?.let {
                TextButton(text = it, onClick = onConfirm)
            }
        },
        dismissButton = {
            dismiss?.let {
                TextButton(text = it, onClick = onDismiss)
            }
        },
        backgroundColor = backgroundColor,
        contentColor = contentColor,
        modifier = modifier
    )
}