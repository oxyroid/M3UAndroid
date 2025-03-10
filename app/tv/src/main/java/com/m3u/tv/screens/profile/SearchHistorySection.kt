package com.m3u.tv.screens.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.ListItem
import androidx.tv.material3.ListItemDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.m3u.tv.theme.JetStreamCardShape

@Composable
fun SearchHistorySection() {
    LazyColumn(modifier = Modifier.padding(horizontal = 72.dp)) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "SearchHistorySectionTitle",
                    style = MaterialTheme.typography.headlineSmall
                )
                Button(onClick = { /* Clear search history */ }) {
                    Text(text = "SearchHistoryClearAll")
                }
            }
        }
        items(1) { index ->
            ListItem(
                modifier = Modifier.padding(top = 8.dp),
                selected = false,
                onClick = {},
                headlineContent = {
                    Text(
                        text = "SampleSearchHistory[index]",
                        style = MaterialTheme.typography.titleMedium
                    )
                },
                shape = ListItemDefaults.shape(shape = JetStreamCardShape)
            )
        }
    }
}
