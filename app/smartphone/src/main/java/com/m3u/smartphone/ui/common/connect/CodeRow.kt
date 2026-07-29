package com.m3u.smartphone.ui.common.connect

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.m3u.i18n.R.string
import com.m3u.smartphone.ui.material.ktx.rememberUiBidiFormatter
import com.m3u.smartphone.ui.material.model.LocalSpacing

@Composable
internal fun CodeRow(
    code: String,
    length: Int,
    modifier: Modifier = Modifier
) {
    val spacing = LocalSpacing.current
    val bidiFormatter = rememberUiBidiFormatter()
    val element = remember(code) { code.toCharArray().map { it.toString() } }
    val pairingCodeDescription = when {
        code.isBlank() -> stringResource(string.ui_remote_control_pairing_code_empty)
        else -> stringResource(
            string.ui_remote_control_pairing_code,
            bidiFormatter.ltr(code.toCharArray().joinToString(separator = " "))
        )
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .padding(
                    horizontal = spacing.medium,
                    vertical = spacing.medium
                )
                .clearAndSetSemantics {
                    contentDescription = pairingCodeDescription
                },
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(length) { index ->
                CodeField(
                    text = element.getOrNull(index).orEmpty(),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun CodeField(
    text: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .padding(horizontal = 4.dp)
            .heightIn(min = 45.dp)
            .background(
                color = MaterialTheme.colorScheme.onSurface.copy(.05f),
                shape = RoundedCornerShape(6.dp)
            )
            .border(
                BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(.1f)),
                RoundedCornerShape(6.dp)
            )
    ) {
        Text(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(vertical = 4.dp),
            text = text,
            fontSize = 20.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
        if (text.isBlank()) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(start = 12.dp, end = 12.dp, bottom = 13.dp)
                    .height(1.dp)
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.onSurface.copy(.15f))
            )
        }
    }
}
