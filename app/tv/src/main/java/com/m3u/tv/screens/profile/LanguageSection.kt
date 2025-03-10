package com.m3u.tv.screens.profile

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.ListItem
import androidx.tv.material3.ListItemDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import androidx.tv.material3.surfaceColorAtElevation
import com.m3u.tv.theme.JetStreamCardShape

@Composable
fun LanguageSection(
    selectedIndex: Int,
    onSelectedIndexChange: (currentIndex: Int) -> Unit
) {
    LazyColumn(modifier = Modifier.padding(horizontal = 72.dp)) {
        item {
            Text(
                text = "LanguageSectionTitle",
                style = MaterialTheme.typography.headlineSmall
            )
        }
        items(1) { index ->
            ListItem(
                modifier = Modifier.padding(top = 16.dp),
                selected = false,
                onClick = { onSelectedIndexChange(index) },
                trailingContent = if (selectedIndex == index) {
                    {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = """stringResource(
                                id = R.string.language_section_listItem_icon_content_description,
                                LanguageSectionItems[index]
                            )"""
                        )
                    }
                } else null,
                headlineContent = {
                    Text(
                        text = "LanguageSectionItems[index]",
                        style = MaterialTheme.typography.titleMedium
                    )
                },
                colors = ListItemDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(4.dp)
                ),
                shape = ListItemDefaults.shape(shape = JetStreamCardShape)
            )
        }
    }
}
