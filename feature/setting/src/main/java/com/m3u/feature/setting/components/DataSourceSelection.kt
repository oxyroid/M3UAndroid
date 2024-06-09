package com.m3u.feature.setting.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.m3u.data.database.model.DataSource
import com.m3u.material.components.ClickableSelection
import com.m3u.material.components.Icon
import com.m3u.material.components.SelectionsDefaults

@Composable
internal fun DataSourceSelection(
    selectedState: MutableState<DataSource>,
    supported: List<DataSource>,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .wrapContentSize(Alignment.TopStart)
            .border(1.dp, LocalContentColor.current.copy(0.38f), SelectionsDefaults.Shape)
            .then(modifier)
    ) {
        ClickableSelection(
            onClick = { expanded = true },
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(stringResource(selectedState.value.resId))
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
                    trailingIcon = {
                        if (selectedState == current) {
                            Icon(
                                imageVector = Icons.Rounded.CheckCircle,
                                contentDescription = null
                            )
                        }
                    },
                    enabled = current.supported,
                    onClick = {
                        selectedState.value = current
                        expanded = false
                    }
                )
            }
        }
    }
}