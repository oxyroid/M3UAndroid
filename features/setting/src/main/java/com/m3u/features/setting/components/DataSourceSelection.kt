package com.m3u.features.setting.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.m3u.data.database.model.DataSource
import com.m3u.material.components.ClickableSelection
import com.m3u.material.components.Icon
import kotlinx.collections.immutable.ImmutableList

@Composable
internal fun DataSourceSelection(
    selected: DataSource,
    onSelected: (DataSource) -> Unit,
    supported: ImmutableList<DataSource>,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .wrapContentSize(Alignment.TopStart)
            .then(modifier)
    ) {
        ClickableSelection(
            onClick = { expanded = true },
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(stringResource(selected.resId))
            Icon(
                imageVector = if (expanded) {
                    Icons.Rounded.KeyboardArrowUp
                } else {
                    Icons.Rounded.KeyboardArrowDown
                },
                contentDescription = null
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            supported.forEach { current ->
                DropdownMenuItem(
                    text = { Text(stringResource(current.resId)) },
                    enabled = current.supported,
                    onClick = {
                        onSelected(current)
                        expanded = false
                    }
                )
            }
        }
    }
}