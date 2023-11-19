package com.m3u.material.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.m3u.material.model.LocalSpacing

@Composable
fun TextBadge(
    text: String,
    modifier: Modifier = Modifier,
    icon: (@Composable () -> Unit)? = null,
) {
    val spacing = LocalSpacing.current

    Card(
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            icon?.invoke()
            Text(
                text = text.uppercase(),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(
                    horizontal = spacing.extraSmall
                )
            )
        }
    }
}
