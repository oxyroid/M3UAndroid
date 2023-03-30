package com.m3u.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedCard
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import com.m3u.ui.model.LocalSpacing
import com.m3u.ui.model.LocalTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Selection(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    content: @Composable BoxScope.() -> Unit
) {
    val theme = LocalTheme.current
    Box(
        contentAlignment = Alignment.Center
    ) {
        OutlinedCard(
            colors = CardDefaults.outlinedCardColors(
                containerColor = theme.surface,
                contentColor = theme.onSurface
            ),
            elevation = CardDefaults.outlinedCardElevation(
                defaultElevation = LocalSpacing.current.none
            ),
            modifier = Modifier
                .graphicsLayer {
                    scaleX = 0.8f
                    scaleY = 0.8f
                }
                .padding(LocalSpacing.current.extraSmall)
                .then(modifier),
            onClick = onClick,
            content = {}
        )
        content()
    }
}

@Composable
fun IconSelection(
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Selection(
        onClick = onClick,
        modifier = Modifier
            .aspectRatio(1f)
            .then(modifier)
    ) {
        val theme = LocalTheme.current
        Icon(
            imageVector = Icons.Rounded.Add,
            contentDescription = "",
            tint = theme.onSurface
        )
    }
}
