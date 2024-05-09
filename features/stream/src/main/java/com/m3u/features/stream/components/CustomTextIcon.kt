package com.m3u.features.stream.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun CustomTextIcon(
    text: String,
    textColor: Color,
    backgroundColor: Color,
){
    Box(
        modifier = Modifier
            .size(width = 50.dp, height = 24.dp)
            .background(
                color = backgroundColor,
                shape = RoundedCornerShape(8.dp, 8.dp, 8.dp, 8.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = textColor,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}