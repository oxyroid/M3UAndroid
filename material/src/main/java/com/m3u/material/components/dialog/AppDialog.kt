package com.m3u.material.components.dialog

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.m3u.material.model.LocalSpacing

@Composable
fun AppDialog(
    visible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    border: BorderStroke = BorderStroke(
        2.dp,
        MaterialTheme.colorScheme.outline
    ),
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    content: @Composable ColumnScope.() -> Unit,
) {
    val theme = MaterialTheme.colorScheme
    val spacing = LocalSpacing.current

    if (visible) {
        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(
                usePlatformDefaultWidth = false
            )
        ) {
            Surface(
                color = theme.background,
                contentColor = theme.onBackground,
                shape = RoundedCornerShape(LocalSpacing.current.medium),
                border = border,
                tonalElevation = spacing.medium,
                modifier = Modifier
                    .padding(spacing.medium)
                    .fillMaxWidth()
                    .wrapContentSize()
                    .animateContentSize()
                    .then(modifier)
            ) {
                Column(
                    verticalArrangement = verticalArrangement,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(spacing.medium),
                    content = content
                )
            }
        }
    }
}

typealias OnDismiss = () -> Unit
