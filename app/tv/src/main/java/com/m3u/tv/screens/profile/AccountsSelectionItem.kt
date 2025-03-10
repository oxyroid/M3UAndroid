package com.m3u.tv.screens.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import androidx.tv.material3.surfaceColorAtElevation

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun AccountsSelectionItem(
    modifier: Modifier = Modifier,
    key: Any?,
    accountsSectionData: AccountsSectionData,
) {
    key(key) {
        Surface(
            onClick = accountsSectionData.onClick,
            modifier = modifier
                .padding(8.dp)
                .fillMaxWidth()
                .aspectRatio(2f),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(4.dp)
            ),
            shape = ClickableSurfaceDefaults.shape(shape = MaterialTheme.shapes.extraSmall),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.Bottom
            ) {
                Text(
                    text = accountsSectionData.title,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontSize = 15.sp
                    )
                )
                Spacer(modifier = Modifier.padding(vertical = 2.dp))
                accountsSectionData.value?.let { nnValue ->
                    Text(
                        text = nnValue,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Normal
                        ),
                        modifier = Modifier.alpha(0.75f)
                    )
                }
            }
        }
    }
}
